# Changelog

APKs for every version are on the [Releases](https://github.com/debakarr/linode-manager/releases) page.

## 2.0.6 — 2026-09-28

No app behaviour changes: this release is the repository, tooling and documentation
overhaul, republished through the new CI pipeline so the APKs and the source match.

- **CI and release automation.** `ci.yml` runs ktlint, Android Lint, the unit tests and a debug build on every push and PR; `release.yml` builds signed APKs from a tag and attaches them to a GitHub Release. **The version now comes from the git tag** (`v2.1.3` → versionName 2.1.3, versionCode 20103), so there is nothing to bump by hand.
- **APKs are no longer committed.** The `releases/` folder (349 MB across the history) is gone; release assets live on the GitHub Release only.
- **Kotlin style is enforced** with ktlint and a `.editorconfig` that exempts `@Composable` functions from the lowerCamelCase rule, since Compose names them in PascalCase by convention. Existing violations fixed (dangling KDoc blocks, `Format.kt` → `Fmt.kt`, over-long lines).
- **Account details redacted from the screenshots** — the server address and reverse DNS, Linode ID, firewall/host labels, SSH key fingerprints, account email and a support-ticket title, in both themes. Tooling in `tools/redact/` plus `scripts/redaction_spec.py`.

- **Real documentation screenshots.** All 18 placeholder images are replaced with captures from a release build on a real phone, plus rendered screenshots from the app's own test suite for screens that need data an account may not have (sign-in, create wizard, a populated Volumes tab). New screenshots for the Linode Network/Storage/Manage tabs, DNS domains and the Appearance setting; SSH screenshots renumbered under `16-`…`22-`.
- **`scripts/capture-screenshots.py`** replaces the old guided shell script: it cold-starts the app per screen (synthetic taps are unreliable while the app settles), matches text exactly, and refuses to save an image unless a marker string proves the right screen is up. Supports `--theme light|dark` for both-theme coverage.
- Documentation corrections: the SSH *Address* field is a picker when a Linode has more than one address (it is only free-text for a single address), and the user guide now covers the Storage/Network/Manage tabs, DNS domains and the appearance setting.
- **Light and dark for every screen** (`NN-slug.png` / `NN-slug-dark.png`), collected in a gallery at the end of the user guide. The terminal keeps its own fixed dark palette in both themes. The capture script gained `--theme dark`, a guard for MIUI's USB panel (it pops up over the app and drops adb if you change the mode), and a file-picker flow so the upload screenshot shows a file actually chosen.

## 2.0.5 — 2026-09-28

- **herdr support:** under *Keep session on the server* choose Off, tmux or **herdr**. The terminal runs inside a named herdr session (`herdr --session <name>`), re-attaches after drops, and **Server sessions** lists, switches, creates and detaches herdr sessions (Ctrl+B q). Works with herdr installed in `~/.local/bin`.
- Removed debug-only code, and the HTTP request logging that wrote API request lines to logcat.

## 2.0.4 — 2026-09-27

- New app icon: a terminal window on brand green, as an adaptive icon with an Android 13+ themed (monochrome) variant.
- Documentation rewritten: README, [user guide](docs/USER_GUIDE.md), [SSH & terminal guide](docs/SSH.md), [architecture](docs/ARCHITECTURE.md), [development](docs/DEVELOPMENT.md); screenshot placeholders plus a capture script.

## 2.0.3 — 2026-09-27

- Terminal text no longer looks spread out (`p e r m i t`) on phones with custom system fonts (seen on Xiaomi HyperOS). The app bundles **JetBrains Mono** instead of relying on the phone's "monospace" font.

## 2.0.2 — 2026-09-27

- After quitting opencode (and similar TUIs), the shell prompt no longer lands on top of old output. The terminal engine ignored the `ESC[s`/`ESC[u` cursor save/restore codes; they're now translated.

## 2.0.1 — 2026-09-27

- Fixed the app closing when opening **Account** or the **SSH** screen for users who had generated device keys in v1.x. Those keys are recovered and re-saved in a stable format.
- If the app crashes, the next launch shows a copyable error report.

## 2.0.0 — 2026-09-27

**SSH rebuilt**
- New engines: ConnectBot **sshlib** (SSH/SFTP) and **termlib** (libvterm terminal) replace JSch, the SpongyCastle crypto glue and the custom renderer.
- Type straight into the terminal (no separate input box or line/raw toggle); pinch to zoom, select & copy, full-screen TUIs.
- Root cause of the v1.x *MAC incorrect* disconnects: with Android's Conscrypt provider, `chacha20-poly1305` fails integrity checks. Only AES-GCM/AES-CTR are offered now; post-quantum ML-KEM falls back to curve25519 when needed.
- Sessions survive navigation, app switches and screen-off (foreground notification with *Disconnect all*); keepalive watchdog, instant re-check on network change, auto-reconnect with backoff.
- tmux as the session command (attach-or-create, session browser, detach); SFTP upload with folder browser; host keys trusted on first use and pinned.

**Redesigned UI**
- One design system across all screens, full light/dark colour roles, Appearance setting.
- Mobile layout fixes: insets handled once (no double gaps or content under system bars), keyboard never covers inputs, no overflowing rows on narrow phones, navigation rail on tablets/landscape.
- Confirmation for every destructive or billable action; type-to-confirm for deletes.
- Searchable pickers instead of typed IDs (regions, images, plans, Linodes).
- New Linode detail (quick actions, metrics charts, rename), Create flow with price summary and password generator, infinite-scroll activity feed.
- Fixed: backup add-on price was never shown.

## 1.4.1 / 1.4.0 — 2026-09-12

- tmux sessions (attach/create/browse/detach) and auto-reconnect with backoff.
- Session-generation guard so stale readers can't kill healthy sessions; strict-crypto toggle; stream-before-connect and MAC fixes; rx forensics in the session log.

## 1.3.x — 2026-09-07 to 2026-09-11

- **1.3.5** Deterministic SSH crypto via SpongyCastle JSch glue (EtM flag fix), oracle-pinned tests.
- **1.3.4** Forced CTR ciphers after GCM tags were rejected server-side; serialised channel writes.
- **1.3.3** SSH forensics: JSch internals and byte counters in the session log, 15 s keepalives.
- **1.3.2** Fixed a cursor crash on full-width lines and alt-screen scrollback leak; session-log viewer.
- **1.3.1** Fixed a race that corrupted the screen and killed the reader while typing.
- **1.3.0** CR for Enter (TUIs), raw keystroke mode, pty that fits the screen, orientation lock, SFTP file upload.

## 1.2.x — 2026-09-07

- **1.2.1** Fixed a blank terminal (reader buffer bug); explicit disconnect on back; 45 s connect timeout.
- **1.2.0** First SSH console: key/password auth, trust-on-first-use host keys, xterm-compatible rendering, extra-keys bar.

## 1.1.0 — 2026-09-07

- Firewall view & edit (rules, policies, devices).
- Access-denied dialog instead of surprise sign-outs on 401/403.
- On-device SSH key generation with one-tap upload to the Linode profile.

## 1.0.0 — 2026-09-07

- First release: token sign-in, dashboard, Linodes (power, resize, rebuild, snapshots, backups, create), volumes, firewalls, DNS domains, NodeBalancers, LKE, account, billing, SSH keys, activity feed.
