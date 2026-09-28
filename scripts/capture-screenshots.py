#!/usr/bin/env python3
"""
Capture documentation screenshots from a phone connected over adb.

Design notes (learned the hard way on a Redmi 3S / MIUI):
  * Synthetic taps are unreliable while the app is still settling, so every
    screen starts from a cold start and waits generously.
  * Text lookups must be exact and scoped: "Connect" is a substring of
    "Reconnect automatically", and the dashboard has a card titled "Account"
    that shadows the Account tab.
  * A screen is only saved when a marker string proves we are on it, so a
    silent mis-navigation can never put the wrong picture in the docs.
  * One screen per invocation style ("cold start -> tap -> capture") is far
    more reliable than wandering around the app.

Usage:
    python3 scripts/capture-screenshots.py --theme dark --all
    python3 scripts/capture-screenshots.py --theme light 02-dashboard 03-linodes
    python3 scripts/capture-screenshots.py --list
"""
import argparse
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "images")
PKG = "com.linode.manager"
ACTIVITY = f"{PKG}/.MainActivity"
# Labels of the resources the docs screenshots are taken against. Point
# these at your own demo account:
#   LINODE=web-01 FIREWALL=web-fw python3 scripts/capture-screenshots.py --all
LINODE = os.environ.get("LINODE", "web-01")
FIREWALL = os.environ.get("FIREWALL", "web-fw")
SUFFIX = ""                    # "" for light, "-dark" for dark (set in main)


# ------------------------------------------------------------------ adb glue
def adb(*args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout).stdout


def nodes():
    """Current window hierarchy as a list of {text, desc, clickable, cx, cy, bounds}."""
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            break
        time.sleep(1)
    else:
        return []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        m = n.get("bounds", "")
        if not m.startswith("["):
            continue
        a, _, rest = m[1:].partition("][")
        x1, y1 = (int(v) for v in a.split(","))
        x2, y2 = (int(v) for v in rest.rstrip("]").split(","))
        out.append({
            "text": n.get("text", ""), "desc": n.get("content-desc", ""),
            "clickable": n.get("clickable") == "true",
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2, "bounds": (x1, y1, x2, y2),
        })
    return out


def texts():
    return [n["text"] for n in nodes() if n["text"]]


def has(s):
    return any(s in t for t in texts())


def find(text, exact=True, below=None, above=None):
    """Find a node by text or content description (icon buttons only have the latter)."""
    for n in nodes():
        label = n["text"] or n["desc"]
        if not label:
            continue
        if (label == text) if exact else (text.lower() in label.lower()):
            if below is not None and n["bounds"][1] <= below:
                continue
            if above is not None and n["bounds"][3] >= above:
                continue
            return n
    return None


def tap(node, wait=3.0):
    adb("shell", "input", "tap", str(node["cx"]), str(node["cy"]))
    time.sleep(wait)


def tap_text(text, exact=True, wait=3.0, **kw):
    dismiss_system_dialogs()
    n = find(text, exact=exact, **kw)
    if not n:
        raise LookupError(f"no node matching {text!r}")
    tap(n, wait)
    dismiss_system_dialogs()
    return n


def swipe(x1, y1, x2, y2, ms=600, wait=1.2):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(wait)


SYSTEM_DIALOGS = ("Use USB for", "USB preferences", "Allow access to phone data")


def dismiss_system_dialogs():
    """
    Dismiss MIUI's USB-mode panel if it covers the app.

    It pops up unprompted over whatever is on screen (it has interrupted a
    file picker mid-flow), and pressing Back leaves the current USB mode
    alone, which matters because changing it drops adb.
    """
    for _ in range(3):
        if not any(has(d) for d in SYSTEM_DIALOGS):
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.5)


def cold_start():
    """
    Restart the app and land on the dashboard.

    A cold start is the only state in which the tab bar responds reliably,
    and the graph's start destination is the dashboard.
    """
    dismiss_system_dialogs()
    # The document picker is a separate app: it sits on top and survives a
    # force-stop of ours, so close it too or the dashboard never appears.
    adb("shell", "am", "force-stop", "com.android.documentsui")
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.5)
    adb("shell", "am", "start", "-n", ACTIVITY)
    for _ in range(20):
        time.sleep(1)
        if has("Recent activity") and has("Account"):
            time.sleep(3.0)          # let the dashboard finish loading
            return
    raise RuntimeError("app did not reach the dashboard")


def tab(name):
    """Tap a bottom-navigation tab (label must be in the bottom bar)."""
    n = find(name, exact=True, below=None)
    if not n or n["bounds"][1] < 1100:
        # The dashboard also has an "Account" card, so fall back to a
        # bottom-bar node whose centre is in the navigation area.
        cands = [x for x in nodes() if x["text"] == name and x["bounds"][1] >= 1100]
        if not cands:
            raise LookupError(f"tab {name} not found")
        n = cands[0]
    tap(n, wait=3.5)


def capture(slug, theme):
    path = os.path.join(OUT, f"{slug}{SUFFIX}.png")
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    if len(data) < 20000:
        raise RuntimeError(f"screenshot looks empty ({len(data)} bytes)")
    with open(path, "wb") as f:
        f.write(data)
    print(f"  saved {slug}{SUFFIX}.png ({len(data)} bytes)")


def shot(slug, marker, theme):
    """Capture only after [marker] is on screen."""
    for _ in range(4):
        if has(marker):
            break
        time.sleep(1.5)
    else:
        raise RuntimeError(f"{marker!r} not on screen; refusing to save {slug}.png")
    capture(slug, theme)


# ------------------------------------------------------------------- screens
def s_dashboard(theme):
    cold_start()
    shot("02-dashboard", "Recent activity", theme)


def s_linodes(theme):
    cold_start()
    tab("Linodes")
    shot("03-linodes", "Search label", theme)


def s_linode_detail(theme):
    cold_start()
    tab("Linodes")
    tap_text(LINODE)
    shot("04-linode-detail", "Summary", theme)


def _detail_tab(theme, which, slug, marker):
    cold_start()
    tab("Linodes")
    tap_text(LINODE)
    tap_text(which)
    shot(slug, marker, theme)


def s_linode_network(theme):
    _detail_tab(theme, "Network", "05-linode-network", "IPv4")


def s_linode_metrics(theme):
    _detail_tab(theme, "Metrics", "06-linode-metrics", "Peak")


def s_linode_storage(theme):
    _detail_tab(theme, "Storage", "07-linode-storage", "Configuration")


def s_linode_manage(theme):
    _detail_tab(theme, "Manage", "08-linode-manage", "Resize")


def s_create_linode(theme):
    cold_start()
    tab("Linodes")
    # The Create FAB exposes no text or content description to accessibility
    # (uiautomator sees an unlabelled clickable box bottom-right), so tap it
    # by position: it sits above the navigation bar.
    fab = find("Create")
    if fab:
        tap(fab, wait=4)
    else:
        adb("shell", "input", "tap", "598", "1072")
        time.sleep(4)
    shot("09-create-linode", "Location", theme)


def s_volumes(theme):
    cold_start()
    tab("Volumes")
    shot("10-volumes", "Create", theme)


def s_volumes_new(theme):
    """The create-volume sheet: what the Volumes feature actually looks like."""
    cold_start()
    tab("Volumes")
    tap_text("Create volume")
    shot("23-volume-create", "Label", theme)


def s_network(theme):
    cold_start()
    tab("Network")
    shot("11-network", "Balancers", theme)


def s_network_domains(theme):
    cold_start()
    tab("Network")
    tap_text("Domains")
    shot("12-network-domains", "Domains", theme)


def s_firewall(theme):
    cold_start()
    tab("Network")
    tap_text(FIREWALL)
    shot("13-firewall-editor", "Default policies", theme)


def s_account(theme):
    cold_start()
    tab("Account")
    shot("14-account", "SSH keys on this device", theme)


def s_account_appearance(theme):
    cold_start()
    tab("Account")
    for _ in range(4):
        if find("Appearance"):
            break
        swipe(360, 1000, 360, 500)
    shot("15-account-appearance", "Appearance", theme)


def _ssh_setup(theme):
    """Reach the SSH setup form (closing a live session first if needed)."""
    cold_start()
    tab("Linodes")
    tap_text(LINODE)
    tap_text("Open terminal", wait=4)
    if has("Sign in with"):
        return
    # A session is live: "Resume terminal" opened the terminal instead.
    for _ in range(4):
        if has("Ctrl"):
            break
        time.sleep(1)
    adb("shell", "input", "tap", "674", "84")      # overflow menu
    time.sleep(2.5)
    tap_text("Close session", wait=2.5)
    tap_text("Close", wait=4)                      # confirm dialog
    tap_text("Open terminal", wait=4.5)
    if not has("Sign in with"):
        raise RuntimeError("could not reach the SSH setup form")


def s_ssh_setup(theme):
    _ssh_setup(theme)
    shot("16-ssh-setup", "Sign in with", theme)


def _terminal(theme):
    _ssh_setup(theme)
    for _ in range(5):
        if find("Connect"):
            break
        swipe(360, 1000, 360, 700)
    tap_text("Connect", wait=10)
    for _ in range(15):
        if has("Esc") and has("Ctrl"):
            return
        time.sleep(1)
    raise RuntimeError("terminal did not come up")


def s_terminal(theme):
    _terminal(theme)
    shot("17-terminal", "Ctrl", theme)


def s_terminal_sessions(theme):
    _terminal(theme)
    adb("shell", "input", "tap", "674", "84")
    time.sleep(2.5)
    tap_text("Server sessions", wait=3.5)
    shot("18-terminal-sessions", "Server sessions", theme)


def _pick_phone_file():
    """
    Choose a file in Android's document picker.

    A file is pushed to Downloads first so the picker always has something to
    select; the upload sheet is only worth a screenshot once it shows a chosen
    file (otherwise the Upload button is disabled).
    """
    adb("push", "/tmp/opencode/upload-demo.txt", "/sdcard/Download/upload-demo.txt")
    tap_text("Choose a file on this phone", wait=6)
    # The picker opens on "Recent" (often empty); open the roots drawer and go
    # to Downloads, where the demo file was just pushed.
    for attempt in range(3):
        n = find("Show roots")
        if n:
            tap(n, wait=2.5)
        for label in ("Downloads", "Download"):
            d = find(label)
            if d:
                tap(d, wait=3.5)
                break
        f = find("upload-demo.txt")   # exact: a container also matches the prefix
        if f:
            tap(f, wait=7)
            dismiss_system_dialogs()
            if not any(has(d) for d in SYSTEM_DIALOGS):
                return
        dismiss_system_dialogs()
        time.sleep(2)
    raise RuntimeError("could not select the demo file in the picker")


def s_upload(theme):
    _terminal(theme)
    adb("shell", "input", "tap", "674", "84")
    time.sleep(2.5)
    tap_text("Upload file", wait=4)
    _pick_phone_file()
    # The sheet now shows the selected file with Upload enabled.
    shot("19-upload", "Upload", theme)


def s_session_log(theme):
    _terminal(theme)
    adb("shell", "input", "tap", "674", "84")
    time.sleep(2.5)
    tap_text("Session log", wait=3)
    shot("20-session-log", "Session log", theme)


SCREENS = {
    "02-dashboard": s_dashboard,
    "03-linodes": s_linodes,
    "04-linode-detail": s_linode_detail,
    "05-linode-network": s_linode_network,
    "06-linode-metrics": s_linode_metrics,
    "07-linode-storage": s_linode_storage,
    "08-linode-manage": s_linode_manage,
    "09-create-linode": s_create_linode,
    "10-volumes": s_volumes,
    "23-volume-create": s_volumes_new,
    "11-network": s_network,
    "12-network-domains": s_network_domains,
    "13-firewall-editor": s_firewall,
    "14-account": s_account,
    "15-account-appearance": s_account_appearance,
    "16-ssh-setup": s_ssh_setup,
    "17-terminal": s_terminal,
    "18-terminal-sessions": s_terminal_sessions,
    "19-upload": s_upload,
    "20-session-log": s_session_log,
}

# Screens that are rendered by the app's own Robolectric test instead, because
# they need data this account does not have (or a sign-out):
#   01-login, 06-create-linode, 07-volumes  ->  ./gradlew :app:testDebugUnitTest
#                                                --tests '*ScreenshotTest*'
# and copied from app/build/screenshots/.


def set_theme(theme):
    """Switch the app's own Appearance setting (Account -> Appearance)."""
    cold_start()
    tab("Account")
    for _ in range(5):
        if find("Appearance"):
            break
        swipe(360, 1000, 360, 500)
    n = find(theme)
    if not n:
        raise RuntimeError(f"could not find the {theme} theme option")
    tap(n, wait=3.5)
    print(f"  theme set to {theme}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--theme", default="light", choices=["light", "dark"])
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("names", nargs="*")
    args = ap.parse_args()

    if args.list:
        for k in SCREENS:
            print(k)
        return

    names = list(SCREENS) if args.all else args.names
    if not names:
        ap.error("give screen names or --all (see --list)")

    ok, failed = [], []
    global SUFFIX
    SUFFIX = "-dark" if args.theme == "dark" else ""
    set_theme("Dark" if args.theme == "dark" else "Light")
    for name in names:
        fn = SCREENS.get(name)
        if not fn:
            print(f"unknown screen {name}")
            failed.append(name)
            continue
        slug = name
        print(f"{name} -> {slug}")
        try:
            fn(args.theme)
            ok.append(slug)
        except Exception as e:
            print(f"  !! {name}: {e}")
            failed.append(slug)
    print(f"\n{len(ok)} captured, {len(failed)} failed")
    if failed:
        print("failed: " + ", ".join(failed))
        sys.exit(1)


if __name__ == "__main__":
    main()
