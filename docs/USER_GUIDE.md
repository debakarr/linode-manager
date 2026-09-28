# User guide

A tour of every screen. For the terminal, see the **[SSH & terminal guide](SSH.md)**.

> Screenshots come from the app running on a real phone against a real account,
> and from the app's own render tests for screens that need data a given
> account doesn't have (sign-in, the create wizard, a populated Volumes tab).
> Where a screen is shown in both themes, the dark version is marked **dark**.

- [Signing in](#signing-in)
- [Home](#home)
- [Linodes](#linodes)
- [A Linode's detail screen](#a-linodes-detail-screen)
- [Creating a Linode](#creating-a-linode)
- [Volumes](#volumes)
- [Network](#network)
- [Account](#account)
- [Appearance](#appearance)
- [Activity](#activity)
- [Permissions and access-denied messages](#permissions-and-access-denied-messages)
- [If the app closes unexpectedly](#if-the-app-closes-unexpectedly)

---

## Signing in

<img src="images/01-login.png" width="260" align="right" alt="Sign-in screen">

The app uses a **Personal Access Token** from your Linode account.

1. In Cloud Manager, open your profile → **API Tokens** → **Create a Personal Access Token** (the *Open Cloud Manager tokens* link in the app takes you there).
2. Choose scopes. Read-only is enough to browse; anything you want to *change* from the phone (power actions, firewalls, volumes…) needs *Read/Write* for that area.
3. Paste the token and tap **Sign in**. The app verifies it against `api.linode.com` before saving it.

The token is stored encrypted on this phone only. **Sign out** (Account tab) removes it and closes any open terminals.

<br clear="right">

## Home

<img src="images/02-dashboard.png" width="260" align="right" alt="Home tab">

An overview of the account:

- **Terminals** — any SSH sessions you have open, with a shortcut back into each.
- **Counts** — Linodes, volumes, firewalls and domains. Tap *Linodes* to jump to the list.
- **Notifications** — maintenance windows and other notices from Linode.
- **Account** — running vs. stopped Linodes, balance, uninvoiced charges and this month's network transfer.
- **Linodes** — the first five, with status. *View all* opens the Linodes tab.
- **Recent activity** — the latest account events. *View all* opens the full [activity feed](#activity).

Pull down (or tap ↻) to refresh.

<br clear="right">

## Linodes

<img src="images/03-linodes.png" width="260" align="right" alt="Linodes tab">

- **Search** by label, IP address, region or tag.
- **Filter** by status with the chips under the search box (only statuses you actually have are shown).
- Each card shows region, CPU/RAM/disk, status, tags and the primary IP. A *Terminal open* pill means you have a live SSH session to it; the Linodes tab badge counts open terminals.
- The **⋮** menu on a card offers *Open terminal* and the power actions that make sense for its state (*Reboot*, *Power off* or *Boot*). Power actions always ask for confirmation.
- **Create** (bottom right) starts the [create flow](#creating-a-linode).

<br clear="right">

## A Linode's detail screen

<img src="images/04-linode-detail.png" width="260" align="right" alt="Linode detail">

The header shows the status and size, with:

- **Open terminal** (or **Resume terminal** if a session is already open) — see [SSH & terminal](SSH.md).
- **↻ Reboot** and **⏻ Power off** when running, **▶ Boot** when stopped. Each asks first.

The top-right **⋮** menu has **Rename** and **Delete Linode**.

Tabs:

| Tab | Contents |
|---|---|
| **Overview** | Plan, region, image, vCPUs, memory, storage, transfer, created date, Linode ID; backups, shutdown watchdog, disk encryption and attached Cloud Firewalls; tags |
| **Network** | Public IPv4 with reverse DNS, private IPv4, IPv6 (SLAAC and link-local). **Long-press any address to copy it.** |
| **Storage** | Disks, configuration profiles and attached volumes |
| **Metrics** | 24-hour CPU, network in/out and disk I/O charts, each with *now*, *average* and *peak* |
| **Manage** | Resize, rebuild, reset root password, take snapshot, enable backups, delete |

<br clear="right">

<img src="images/05-linode-network.png" width="260" align="right" alt="Network tab">

**Network** lists every address the Linode has. The reverse-DNS name is shown next to the public IPv4; IPv6 addresses are grouped by type.

<br clear="right">

<img src="images/06-linode-metrics.png" width="260" align="right" alt="Metrics tab">

**Metrics** charts the last 24 hours. Hover-free readouts above each chart give the current value, the 24-hour average and the peak.

New Linodes take a few minutes before metrics appear.

<br clear="right">

<img src="images/07-linode-storage.png" width="260" align="right" alt="Storage tab">

**Storage** lists the Linode's disks and configuration profiles, and any Block Storage volumes attached to it.

<br clear="right">

<img src="images/08-linode-manage.png" width="260" align="right" alt="Manage tab">

**Manage actions**

- **Resize** — pick a new plan from the list (grouped by class, with prices). The Linode powers off, migrates and boots again.
- **Rebuild** — pick an image and set a new root password (11+ characters), then type the Linode's label to confirm. *This erases all disks.*
- **Reset root password** — the Linode must be powered off first.
- **Take snapshot** — a manual backup of all disks (replaces the previous manual snapshot).
- **Enable backups** — a paid add-on; the app asks first.
- **Delete** — permanent; you must type the Linode's label.

<br clear="right">

## Creating a Linode

<img src="images/09-create-linode.png" width="260" align="right" alt="Create Linode">

1. **Region** and **Image** — searchable pickers.
2. **Plan** — choose a class (Nanode, Standard, Dedicated, …), then a plan card. Prices are per month.
3. **Details** — optional label (auto-generated if empty) and comma-separated tags.
4. **Access** — root password (11+ characters; the 🎲 button generates a strong one — save it somewhere safe), SSH keys from your profile to install, and optional **Backups** (the add-on price is shown).

The bar at the bottom shows the monthly total. Tap **Create** and you land on the new Linode while it provisions.

> Tip: add your phone's device key to your profile first (Account → SSH keys on this device → ⋮ → *Upload to Linode profile*), then tick it here — the new Linode will accept terminal logins from this phone straight away.

<br clear="right">

## Volumes

<img src="images/10-volumes.png" width="260" align="right" alt="Volumes tab">

Block Storage volumes with region, size, status and where they're attached. The
account used for these screenshots has no volumes yet, so the tab shows its empty
state; once volumes exist they appear as cards with a status chip and their
attachment.

- **Create volume** — label, size (10 GB minimum), and either a region or a Linode to attach to (the region follows the Linode).
- **⋮ → Attach to Linode** — only Linodes in the volume's region are offered.
- **⋮ → Detach** — unmount the volume inside the Linode first; the app reminds you.
- **⋮ → Resize** — volumes can only grow.
- **⋮ → Delete** — only for detached volumes; permanent.

<br clear="right">

## Network

<img src="images/11-network.png" width="260" align="right" alt="Network tab">

Four tabs:

- **Firewalls** — each Cloud Firewall with its rule counts, default policies and attached services. Tap one to open the [editor](#firewall-editor). **Create** adds a new one.
- **Domains** — tap a domain to expand its DNS records (long-press a value to copy). **Create** adds a domain (needs an SOA email).
- **Balancers** — NodeBalancers with their IPv4 address and hostname.
- **Kubernetes** — LKE clusters with version and status.

Deleting a firewall, domain, NodeBalancer or cluster requires typing its name.

<br clear="right">

<img src="images/12-network-domains.png" width="260" align="right" alt="DNS domains">

**Domains** shows each zone with its status and record count; tap one to expand the records inline.

<br clear="right">

### Firewall editor

<img src="images/13-firewall-editor.png" width="260" align="right" alt="Firewall editor">

- **Overview** — rename, enable/disable (a disabled firewall lets all traffic through), then *Save overview*.
- **Default policies** — what happens to traffic that matches no rule (inbound usually *DROP*, outbound *ACCEPT*).
- **Inbound / Outbound rules** — each with label, action, protocol, ports (TCP/UDP), IPv4 and IPv6 sources/destinations and a description. New rules start with *anywhere* (`0.0.0.0/0`, `::/0`). Up to 25 rules in total. Tap **Save rules & policies** to apply.
- **Protected services** — remove a Linode/NodeBalancer, or pick a Linode and **Attach**.

> If an app on your Linode listens on a non-standard port (e.g. SSH on 2222), it must be allowed here or connections time out.

<br clear="right">

## Account

<img src="images/14-account.png" width="260" align="right" alt="Account tab">

- **Profile** — username, email, company, timezone.
- **Billing** — balance, uninvoiced charges, this month's transfer and recent invoices.
- **SSH keys on this device** — **Generate** creates a 3072-bit RSA key pair on the phone; the private key never leaves it. Each key's **⋮** menu: *View public key*, *Copy public key*, *Upload to Linode profile*, *Delete*.
- **SSH keys on your profile** — keys stored in your Linode profile (offered when creating Linodes). **Add** pastes a public key; **⋮** copies or removes one.
- **Support tickets** — recent tickets and their status.
- **Appearance** — *System*, *Light* or *Dark*.

The app version is shown at the bottom.

<br clear="right">

## Appearance

<img src="images/15-account-appearance.png" width="260" align="right" alt="Appearance settings">

**Appearance** follows the phone by default; pick *Light* or *Dark* to override it. The
choice applies immediately across the whole app and is remembered.

<br clear="right">

<img src="images/15-account-appearance-dark.png" width="260" align="right" alt="Appearance settings in dark theme">

The same screen in **dark** theme. Every screen is built from the same components, so
light and dark stay consistent — including the terminal, which always uses its own
fixed dark palette so that colours and contrast match a desktop terminal.

<br clear="right">

## Activity

From Home → *Recent activity* → **View all**: every account event (boots, snapshots, firewall changes…) with who did it, when, and progress for running jobs. Scroll to load older events.

## Every screen in light and dark

The theme is set in [Appearance](#appearance) (*System*, *Light* or *Dark*) and applies
immediately across the app. Every screen below is shown in both.

The **terminal** keeps its own fixed dark palette in both themes, so colours and
contrast match what a program expects from a desktop terminal.

| Screen | Light | Dark |
|---|---|---|
| **Home** | <img src="images/02-dashboard.png" width="230" alt="Home (light)"> | <img src="images/02-dashboard-dark.png" width="230" alt="Home (dark)"> |
| **Linodes** | <img src="images/03-linodes.png" width="230" alt="Linodes (light)"> | <img src="images/03-linodes-dark.png" width="230" alt="Linodes (dark)"> |
| **Linode detail — Overview** | <img src="images/04-linode-detail.png" width="230" alt="Linode detail — Overview (light)"> | <img src="images/04-linode-detail-dark.png" width="230" alt="Linode detail — Overview (dark)"> |
| **Linode detail — Network** | <img src="images/05-linode-network.png" width="230" alt="Linode detail — Network (light)"> | <img src="images/05-linode-network-dark.png" width="230" alt="Linode detail — Network (dark)"> |
| **Linode detail — Metrics** | <img src="images/06-linode-metrics.png" width="230" alt="Linode detail — Metrics (light)"> | <img src="images/06-linode-metrics-dark.png" width="230" alt="Linode detail — Metrics (dark)"> |
| **Linode detail — Storage** | <img src="images/07-linode-storage.png" width="230" alt="Linode detail — Storage (light)"> | <img src="images/07-linode-storage-dark.png" width="230" alt="Linode detail — Storage (dark)"> |
| **Linode detail — Manage** | <img src="images/08-linode-manage.png" width="230" alt="Linode detail — Manage (light)"> | <img src="images/08-linode-manage-dark.png" width="230" alt="Linode detail — Manage (dark)"> |
| **Create a Linode** | <img src="images/09-create-linode.png" width="230" alt="Create a Linode (light)"> | <img src="images/09-create-linode-dark.png" width="230" alt="Create a Linode (dark)"> |
| **Volumes** | <img src="images/10-volumes.png" width="230" alt="Volumes (light)"> | <img src="images/10-volumes-dark.png" width="230" alt="Volumes (dark)"> |
| **Network — Firewalls** | <img src="images/11-network.png" width="230" alt="Network — Firewalls (light)"> | <img src="images/11-network-dark.png" width="230" alt="Network — Firewalls (dark)"> |
| **Network — DNS domains** | <img src="images/12-network-domains.png" width="230" alt="Network — DNS domains (light)"> | <img src="images/12-network-domains-dark.png" width="230" alt="Network — DNS domains (dark)"> |
| **Cloud Firewall editor** | <img src="images/13-firewall-editor.png" width="230" alt="Cloud Firewall editor (light)"> | <img src="images/13-firewall-editor-dark.png" width="230" alt="Cloud Firewall editor (dark)"> |
| **Account** | <img src="images/14-account.png" width="230" alt="Account (light)"> | <img src="images/14-account-dark.png" width="230" alt="Account (dark)"> |
| **Appearance** | <img src="images/15-account-appearance.png" width="230" alt="Appearance (light)"> | <img src="images/15-account-appearance-dark.png" width="230" alt="Appearance (dark)"> |
| **SSH — connect form** | <img src="images/16-ssh-setup.png" width="230" alt="SSH — connect form (light)"> | <img src="images/16-ssh-setup-dark.png" width="230" alt="SSH — connect form (dark)"> |
| **SSH — terminal** | <img src="images/17-terminal.png" width="230" alt="SSH — terminal (light)"> | <img src="images/17-terminal-dark.png" width="230" alt="SSH — terminal (dark)"> |
| **SSH — server sessions** | <img src="images/18-terminal-sessions.png" width="230" alt="SSH — server sessions (light)"> | <img src="images/18-terminal-sessions-dark.png" width="230" alt="SSH — server sessions (dark)"> |
| **SSH — upload a file** | <img src="images/19-upload.png" width="230" alt="SSH — upload a file (light)"> | <img src="images/19-upload-dark.png" width="230" alt="SSH — upload a file (dark)"> |
| **SSH — session log** | <img src="images/20-session-log.png" width="230" alt="SSH — session log (light)"> | <img src="images/20-session-log-dark.png" width="230" alt="SSH — session log (dark)"> |

*The sign-in screen is not shown in dark: capturing it means signing out first,
which would discard the session that produced these screenshots.*

## Permissions and access-denied messages

If your token lacks a scope, the app does **not** sign you out. You'll see *Access denied* with the HTTP code, the API's reason, and the scope that's needed (e.g. `volumes:read_only`).

- **Check token** confirms whether the token itself is still valid (it just needs more scopes).
- **Sign in with a different token** returns you to the sign-in screen.

## If the app closes unexpectedly

The next time you open it, a dialog shows the error report. Tap **Copy** and send it along so it can be fixed.
