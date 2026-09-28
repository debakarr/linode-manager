# Architecture

A single-module Android app (`app/`), Kotlin + Jetpack Compose, no backend. It talks to the Linode API v4 over HTTPS and to servers over SSH.

```
com.linode.manager
├── LinodeApp / MainActivity      app entry; crash log; theme
├── AppContainer                  manual dependency container (one per process)
├── data
│   ├── remote/                   Retrofit API (LinodeApi) + Gson models
│   ├── repository/               LinodeRepository, ApiResult (typed errors)
│   ├── TokenStore, DeviceKeyStore, SshKeyGen, AppSettings, CrashLog
│   └── ssh/                      SSH client, terminal sessions, service, tmux/herdr
└── ui
    ├── LinodeRoot                sign-in gate, app shell, navigation
    ├── components/               shared design system
    ├── theme/                    colours, typography, shapes, status colours
    └── screens/…                 one package per area, screen + ViewModel
```

## Dependencies & state

`AppContainer` is created once in `LinodeApp` and passed to screens. It owns:

| Object | Responsibility |
|---|---|
| `LinodeRepository` | Every API call, returning `ApiResult.Ok` / `ApiResult.Err(message, code)` — never throwing. 401/403 become an *Access denied* dialog, not a sign-out. |
| `TokenStore` | The API token, in `EncryptedSharedPreferences` (plain private prefs if the Keystore is broken). |
| `DeviceKeyStore` | Device SSH key pairs (encrypted). JSON field names are pinned with `@SerializedName`; a shape-based parser also reads the obfuscated format written by v1.x. |
| `HostKeyStore` | Trusted host keys per `host[:port]` (encrypted). |
| `SshProfileStore` | Last connection settings per Linode (no secrets). |
| `SshSessionManager` | All open terminal sessions (see below). |
| `AppSettings` | Theme mode. |

Screens use `ViewModel`s holding Compose `mutableStateOf` state; there's no Flow/LiveData layer. ViewModels are keyed per resource (e.g. `detail-<id>`).

## UI system

`ui/components` is the design system every screen is built from:

- **`ScreenScaffold`** — the only scaffold screens use: top bar (title, subtitle, back, actions), snackbar host, FAB, bottom bar.
- **Insets are handled once.** The app shell's `Scaffold` uses `contentWindowInsets = WindowInsets(0)` and passes `consumeWindowInsets(pad)` to the NavHost, so nested screen scaffolds never double-pad or underlap the navigation bar. Forms add `imePadding()`. On screens ≥ 600 dp wide a `NavigationRail` replaces the bottom bar.
- Cards: `SectionCard`, `ResourceCard`; rows: `InfoRow` (wraps, never overflows, long-press to copy); status: `StatusBadge`, `StatusDot`, `Pill`.
- Actions: `OverflowMenu`, `ConfirmDialog`, `TypeToConfirmDialog` (deletes), `PickerField`/`PickerSheet` (searchable bottom-sheet pickers instead of typed IDs).
- `Fmt` formats dates, sizes and money consistently; `MetricChart` draws the 24 h charts.
- The theme defines every Material 3 colour role in both light and dark, so no surface falls back to baseline tones on any device.

## SSH stack

```
TerminalScreen (Compose)
   │  termlib Terminal composable — renders, IME input, selection, zoom
   ▼
TerminalSession ─────────── one per Linode, lives in SshSessionManager (app scope)
   │  • libvterm emulator (termlib)          • single ordered writer for keystrokes
   │  • reader loop → ScoCursorTranslator → emulator
   │  • keepalive watchdog, reconnect with backoff, tmux/herdr, SFTP upload
   ▼
SshClient / SshShell ────── blocking wrapper over ConnectBot sshlib
   │  host-key verification, auth, pty shell, exec, SFTP, ping
   ▼
sshlib (transport, crypto)  ◀── cipher/KEX policy below
```

- **Why sshlib + termlib:** they are the SSH and terminal engines of ConnectBot, the most widely used Android SSH client. v1.x used JSch plus a hand-written VT100 renderer and SpongyCastle crypto glue, which produced provider-dependent integrity failures on phones.
- **Session lifetime:** sessions live in `SshSessionManager`, not in a screen, so navigating away doesn't disconnect. While any session is live, `SshService` (a `specialUse` foreground service) keeps the process alive and shows the *SSH connected* notification. A `ConnectivityManager` callback nudges sessions when the network changes.
- **Generations:** every connect/reopen takes a new `SessionGate` generation; callbacks from an older reader or handshake are dropped, so a late "closed" can never tear down a newer, healthy connection.
- **Liveness:** a channel ping every 15 s; no reply in 12 s closes the link, which wakes the reader and triggers reconnect. The exit status is awaited briefly after EOF so a normal `exit` isn't mistaken for a drop.
- **tmux / herdr** (`Multiplexer`) is the channel's command (`tmux new-session -A -s NAME` or `herdr --session NAME`, with `~/.local/bin` prepended to PATH, falling back to a login shell), not typed into a shell, so it survives reconnects cleanly.
- **Host key flow:** an unknown key makes the verifier reject and raises `HostKeyUnknownException`; the UI asks, stores the key and reconnects. This avoids blocking inside the handshake (which has its own timeout).

### Crypto policy (important)

Android's first JCE provider is **Conscrypt**, and sshlib takes some primitives from whatever provider comes first:

- `chacha20-poly1305@openssh.com` fails every packet with Conscrypt (raw `ChaCha20` differs from SSH's construction), so `SshClient.PREFERRED_CIPHERS` offers only **AES-GCM and AES-CTR**.
- The ML-KEM hybrid key exchange can fail on some provider mixes; `SshClient.connect` retries once with classic KEX (`curve25519-sha256`) and remembers that for the process.

Both behaviours are pinned by tests that install Conscrypt + BouncyCastle in Android's order and run against a real OpenSSH server (`ConscryptCipherMatrixTest`, `MlKemFallbackTest`). **Run them before changing ciphers/KEX or upgrading sshlib.**

### Terminal rendering notes

- The terminal uses a **bundled JetBrains Mono** (`res/font`). termlib sizes cells from `measureText("M")`; with the generic `MONOSPACE` typeface, some OEM font themes substituted a proportional font and spread text out.
- **`ScoCursorTranslator`** rewrites bare `ESC[s` / `ESC[u` to `ESC 7` / `ESC 8`, because libvterm ignores the SCO forms; without it, opencode left the shell prompt drawn over old output after exiting. Parameterised forms (kitty keyboard `ESC[?u`, `ESC[>1u`, …) pass through.

## Persistence formats

Anything written with Gson must have **stable field names** — R8 renames fields otherwise (v2.0.0 crashed reading keys saved by v1.x for exactly this reason). Keep `@SerializedName` on persisted classes and `-keep` rules in `proguard-rules.pro`.

## Crash reporting

`CrashLog` installs an uncaught-exception handler that writes the stack trace (plus app/Android version and device model) to private storage. On the next launch `LinodeRoot` shows it with a **Copy** button. Nothing is sent anywhere.
