# Development

## Prerequisites

- JDK 21
- Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`; `ANDROID_HOME` set (e.g. `export ANDROID_HOME=$HOME/android-sdk`)
- For the SSH tests: OpenSSH server binaries (`/usr/sbin/sshd`, `ssh-keygen`, `sftp-server`) and `tmux` — the tests skip themselves without sshd

## Build

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew versionApks            # signed release APKs, renamed with the version:
                                 # app/build/outputs/apk/versioned/LinodeManager-<version>-<abi>.apk
```

Release builds are minified with R8 and split per ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) plus a `universal` APK.

### Signing

The release signing key lives outside git. Point `local.properties` (git-ignored) at it:

```properties
keystore.path=linode.keystore
keystore.password=…
keystore.alias=linode
```

All releases must be signed with the **same key**, or Android refuses to update in place (users would have to uninstall and lose their data). Debug builds use the debug key, so switching a phone between a debug and a release build requires an uninstall.

## Tests

```bash
./gradlew testDebugUnitTest
```

Every test class runs in its own JVM (`forkEvery = 1`) because some install global JCE providers.

| Suite | What it covers |
|---|---|
| `SshClientTest` | Starts a throwaway **OpenSSH server** as the current user on a random localhost port (`TestSshd`) and exercises host-key trust and MITM refusal, key auth, pty shell and resize, 1 MB streaming, exec, tmux, herdr (fallback when missing, and the real `herdr session list` if installed — read-only), SFTP upload, friendly errors. |
| `ConscryptCipherMatrixTest` | The same server with **Android's provider order** (Conscrypt, then BouncyCastle): every offered cipher must work, default negotiation must work, and `chacha20-poly1305` must stay excluded while it's broken. |
| `MlKemFallbackTest` | Post-quantum KEX failure under that provider stack falls back to curve25519. |
| `ScoCursorTranslatorTest` | Terminal byte-stream rewrite, including sequences split across reads. |
| `TmuxTest`, `MultiplexerTest`, `SessionGateTest` | tmux/herdr commands and session-list parsing, saved-profile compatibility, reconnect backoff, generation guard. |
| `DeviceKeyStoreTest` | Reading keys in the v1.x obfuscated format and the current format. |
| `ScreenshotTest` | **Robolectric + Roborazzi**: renders every screen against a mock API (MockWebServer) at phone, small-phone and tablet sizes, light and dark, and after an upgrade from v1 data. PNGs go to `app/build/screenshots/` — look at them after UI changes. |
| `IconRenderTest` | Renders the adaptive launcher icon under circle/squircle/rounded-square/teardrop masks and the themed (monochrome) variant into `app/build/icon/`. |

The terminal itself (termlib's native libvterm) can't run on the JVM, so terminal behaviour needs a real device.

## Testing on a phone

An emulator needs KVM; on machines without it, use a real phone over USB:

```bash
adb devices                                   # authorise the prompt on the phone
adb -s <serial> shell svc power stayon usb    # keep the screen on while plugged in
adb -s <serial> install -r app/build/outputs/apk/versioned/LinodeManager-<v>-arm64-v8a.apk
adb -s <serial> logcat -b crash               # crashes
```

Use `install -r` with a release-signed build to keep the phone's data (token, keys, trusted hosts).

## Documentation screenshots

Two sources, both real UI:

**1. On-device captures** (`docs/images/NN-*.png`, and `NN-*-dark.png` for dark theme):

```bash
python3 scripts/capture-screenshots.py --list          # what can be captured
python3 scripts/capture-screenshots.py --theme light 02-dashboard 03-linodes
python3 scripts/capture-screenshots.py --theme dark  --all
```

An emulator needs KVM; on machines without it, use a real phone over USB and keep
the screen on (`adb shell svc power stayon true`). The script cold-starts the app
before each screen — synthetic taps are unreliable while the app is still settling —
and refuses to save unless a marker string proves the expected screen is up, so a
mis-navigation can never put the wrong picture in the docs.

**2. Rendered screenshots** for screens that need data a given account doesn't have
(sign-in, and a populated Volumes tab / create wizard):

```bash
./gradlew :app:testDebugUnitTest --tests "com.linode.manager.ui.ScreenshotTest"
# PNGs land in app/build/screenshots/
```

`01-login.png` comes from there; copy it into `docs/images/`. All other images are
real device captures.

### Redacting account details

Screenshots taken against a live account show its addresses, identifiers and key
fingerprints. Before committing new ones, cover them up:

```bash
python3 scripts/redaction_spec.py                  # writes /tmp/opencode/redact/spec.txt
cd tools/redact && javac Redact.java Rows.java Cols.java Cover.java
java Redact /tmp/opencode/redact/spec.txt          # applies the boxes in place
java Cover  /tmp/opencode/redact/spec.txt <pristine-copies-dir>   # lists rows still exposed
```

`Redact` fills rectangles; `Rows`, `Cols` and `Cover` measure the pixels so the
boxes land on real text rows instead of eyeballed coordinates (`Cover` reports
text rows the spec does not fully cover — run it against pristine copies of the
originals, for example `git show HEAD:docs/images/<name>.png`). Update the
regions in `scripts/redaction_spec.py` when a screen's layout changes.

The launcher icon images (`docs/images/icon.png`, `icon-variants.png`) come from
`IconRenderTest` (`app/build/icon/`).

## Continuous integration

| Workflow | Runs on | What it does |
|---|---|---|
| [`ci.yml`](../.github/workflows/ci.yml) | every push to `main` and every PR | `ktlintCheck`, `lintDebug`, `testDebugUnitTest`, `assembleDebug`; uploads the reports as an artifact |
| [`release.yml`](../.github/workflows/release.yml) | a `v*.*.*` tag (or manual dispatch) | derives the version from the tag, re-runs the checks, builds signed APKs and publishes a GitHub Release with all five attached |

Both run on a stock GitHub runner: JDK 21, `platforms;android-37.0`, `build-tools;37.0.0`.
The SSH tests start a throwaway `sshd` on localhost and skip themselves if the
runner has none, so a green run still means the transport works against OpenSSH.

### Release signing secrets

`release.yml` needs the release keystore as repository secrets (Settings → Secrets
and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 linode.keystore` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEYSTORE_ALIAS` | the key alias (e.g. `linode`) |
| `KEY_PASSWORD` | the key password (same as the keystore password for PKCS#12) |

The keystore itself is never committed — `.gitignore` excludes `*.keystore`, and
the same key must be used for every release or Android refuses to update in place.

### Versioning

The **git tag is the single source of truth**. `versionName` comes straight from
it and `versionCode` is derived monotonically:

```
v2.1.3  →  versionName 2.1.3,  versionCode 20103   (major*10000 + minor*100 + patch)
```

Nothing needs bumping by hand, and two releases can never share a version code.
For a local build the values default to whatever is in `app/build.gradle.kts`, or
you can pass them explicitly:

```bash
./gradlew versionApks -PversionName=2.1.3 -PversionCode=20103
```

APKs are **not** stored in the repository. A release attaches them to the GitHub
Release; the `releases/` folder is gone.

## Releasing

1. Add a [CHANGELOG](../CHANGELOG.md) entry and commit it.
2. Smoke-test on a phone (`install -r` a release-signed build): sign in, Account,
   a Linode, Open terminal, a TUI.
3. Tag and push — CI does the rest:
   ```bash
   git tag -a v2.1.0 -m "Linode Manager v2.1.0"
   git push origin main v2.1.0
   ```
4. Watch the run: `gh run watch`. The APKs end up on the release, and
   `apksigner verify --print-certs` is checked in CI before publishing.

## Project conventions

- Screens are built only from `ui/components` — don't add ad-hoc cards, dialogs or scaffolds. See [Architecture → UI system](ARCHITECTURE.md#ui-system).
- Every destructive or billable action goes through `ConfirmDialog`; deletes use `TypeToConfirmDialog`.
- Persisted Gson classes need `@SerializedName` and a keep rule. See [Architecture → Persistence formats](ARCHITECTURE.md#persistence-formats).
- Don't change SSH ciphers/KEX without running the Conscrypt tests.
