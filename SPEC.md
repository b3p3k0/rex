# Rex — Remote Exec for Android (GPL-3.0)
Short: Android app to store hosts and commands, run them over SSH, and stream output. Security first. Minimal logging. No exports.

## License
GPL-3.0. Include the full GPL-3.0 text as `LICENSE`. All source files must carry a GPL header.

---

## 0) Environment Bootstrap (Linux/macOS)

> Requirements: JDK 17, Android cmdline-tools, Android Studio (latest), Git.

**Linux (Ubuntu/Debian-ish)**
```bash
# Java 17
sudo apt update && sudo apt install -y openjdk-17-jdk unzip git

# Android SDK base (install under $HOME/Android)
mkdir -p "$HOME/Android/cmdline-tools" && cd "$HOME/Android"
curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdtools.zip
unzip -q cmdtools.zip && rm cmdtools.zip
mv cmdline-tools cmdline-tools/latest

# SDK env
export ANDROID_HOME="$HOME/Android"
export ANDROID_SDK_ROOT="$HOME/Android"
export PATH="$HOME/Android/cmdline-tools/latest/bin:$HOME/Android/platform-tools:$PATH"

# SDK components (accept licenses, install API 35 + build-tools 35.x)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "cmdline-tools;latest"
```

**macOS (Homebrew)**
```bash
brew install openjdk@17 git
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
brew install --cask android-studio
# First run Android Studio once to initialize SDK, then:
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

**Repo skeleton and build (after sources exist)**
```bash
git init rex && cd rex
# when sources are added:
./gradlew lint test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 1) Product Scope (V1, Final)
- TOFU allowed; pin host key after first accept.
- Freeform commands; per-command confirmation optional.
- Unlock via device PIN/passcode; no biometrics.
- No proxy/jump in v1.
- Logs store metadata only; no stdout/stderr persistence.
- No export in v1. No MDM.
- minSdk 26, targetSdk 35. Kotlin, Compose M3, Hilt, Room, Coroutines, AndroidX Security Crypto, SSHJ.

---

## 2) Security Model
- **Keys at rest**: OpenSSH Ed25519 PEM encrypted with random DEK (AES-GCM). DEK wrapped by non-exportable KEK in Android Keystore (StrongBox when available).
- **Unlock**: device credentials via `KeyguardManager` (session TTL default 5 min).
- **Host verification**: strict host key checking; pin SHA256 fingerprint after TOFU; mismatch blocks.
- **Logs**: metadata only; redacted summaries; FIFO eviction by size/age/count.
- **UI privacy**: screenshots disabled on sensitive screens by default; clipboard off by default.

---

## 3) Data Model (Room)
```
hosts(id UUID PK, nickname TEXT, hostname TEXT, port INT=22, username TEXT,
      auth_method TEXT="key", key_blob_id TEXT FK, connect_timeout_ms INT=8000,
      read_timeout_ms INT=15000, strict_host_key BOOL=true,
      pinned_host_key_fingerprint TEXT?, created_at LONG, updated_at LONG)

commands(id UUID PK, name TEXT, command TEXT, require_confirmation BOOL=true,
         default_timeout_ms INT=15000, allow_pty BOOL=false, created_at LONG, updated_at LONG)

host_commands(id UUID PK, host_id FK, command_id FK, sort_index INT, created_at LONG)

key_blobs(id UUID PK, alg TEXT="ed25519",
          enc_blob BLOB, enc_blob_iv BLOB, enc_blob_tag BLOB,
          public_key_openssh TEXT, created_at LONG)

logs(id UUID PK, ts LONG, host_nickname TEXT, command_name TEXT,
     exit_code INT?, duration_ms INT?, bytes_stdout INT, bytes_stderr INT,
     status TEXT ENUM("OK","FAILED","CANCELLED","TIMEOUT","HOSTKEY_MISMATCH","AUTH_FAILED","DNS","REFUSED","IO"),
     message_redacted TEXT?, idx_seq LONG)
```

---

## 4) Execution Flow
1. User taps **Run** on a Host•Command mapping.
2. If session locked → prompt device credentials.
3. Connect with timeouts → TOFU if no pin → pin on accept → verify thereafter.
4. Decrypt key → auth → exec (non-PTY default) → stream stdout/stderr to terminal pane.
5. On completion record metadata log → show exit code and duration.
6. Cancel closes channel and disconnects; mark `CANCELLED`.

---

## 5) UI/UX (Authoritative)
- **Design system**: Material 3. No hardcoded colors. Meet WCAG AA.
- **Layout**: 16dp outer padding, 8dp inner spacing, min 48dp touch targets.
- **Copy**: short, literal; banners for errors; dialogs for TOFU and destructive confirms.
- **Accessibility**: content descriptions for all icons; predictable focus; TalkBack-friendly badges.

### Main Table
```
AppBar: Rex                                [⚙]
[ Search…                                                ]
[All] [Pinned] [Unpinned]
──────────────────────────────────────────────────────────
 host-a • restart-nginx                     [Run]  📌  ✓
 host-b • df-check                          [Run]  ⚠   ✗
 host-c • pull-logs                         [Run]
[ + ]
```
- Row tap → details; long-press → Edit Host / Edit Command / View Logs / Remove Mapping.
- Badges: 📌 pinned, ⚠ unpinned, ✓ OK, ✗ Failed, ⏱ Timeout.
- `+` bottom sheet: Add host / Add command / Map host ↔ command.

### Add/Edit Host
```
Nickname [____]  Hostname [____]  Port [22]  Username [____]
Key: [Import] [Generate] [Delete]
Strict host key [ON]  Connect [8000ms]  Read [15000ms]
Pinned: SHA256:AbCd... (read-only if set)
[Cancel] [Save]
```

### Add/Edit Command
- Name, command string, require confirmation [on], PTY [off], default timeout.

### Session Screen
```
HostA • restart-nginx
┌───────────────────────────────────────────────┐
$ sudo systemctl restart nginx
… live stdout/stderr …
└───────────────────────────────────────────────┘
Elapsed 00:12   Exit —                 [Cancel] [Copy]
```
- Copy enabled only if user turned on clipboard in Settings; auto-clear 60s.

### TOFU Dialog
- “First-time host. Verify and trust this host key?”
- Show host, algo, SHA256 fp. Actions: **Trust & Pin** / **Cancel**.

### Logs
- List: ts, host, command, status icon, duration, exit code.
- Filters by host, status, date.

### Settings
- Session lock TTL (1–30 min; default 5).
- Disable screenshots [on].
- Allow copying output [off] with 60s auto-clear.
- Log retention caps (14d / 5 MB / 10k max caps; user may reduce).
- Key management: Import, Generate Ed25519, Delete.
- About: version, GPL-3.0, notices.

---

## 6) UI Copy + Icons (single source of truth)
```yaml
app_title: "Rex"
actions:
  run: "Run"
  stop: "Stop"
  cancel: "Cancel"
  close: "Close"
  trust_and_pin: "Trust & Pin"
  add_host: "Add host"
  add_command: "Add command"
  map_host_command: "Map host ↔ command"
banners:
  auth_failed: "Authentication failed."
  hostkey_mismatch: "Host key mismatch."
  timeout: "Timed out."
  dns: "Hostname not found."
  refused: "Connection refused."
dialogs:
  confirm_run_title: "Run command?"
  confirm_run_body: "This will execute on {host}:\n{command}"
  tofu_title: "First-time host"
  tofu_body: "Verify and trust this host key?\nHost: {host}\nAlgo: {alg}\nSHA256: {fp}"
terminal:
  copy_enabled_notice: "Copied. Will clear in 60 seconds."
settings_labels:
  ttl: "Session lock timeout"
  screenshots: "Disable screenshots"
  clipboard: "Allow copying command output"
  log_retention: "Log retention"
icons:
  pinned: "ic_pinned"
  unpinned: "ic_unpinned"
  ok: "ic_ok"
  failed: "ic_failed"
  timeout: "ic_timeout"
```

---

## 7) Redaction Rules (fast regex set)
```json
{
  "patterns": [
    "(?i)(password|passwd|pwd)\\s*=\\s*[^\\s]+",
    "(?i)(token|apikey|api_key|secret|authorization)\\s*[:=]\\s*[^\\s]+",
    "(?i)bearer\\s+[a-z0-9\\.\\-_]+",
    "\\b[0-9a-f]{32,}\\b"
  ],
  "replacement": "[REDACTED]"
}
```

---

## 8) Error Mapping Table
```csv
exception,exec_error,user_message
UnknownHostException,DNS,Hostname not found.
ConnectException,REFUSED,Connection refused.
SocketTimeoutException,TIMEOUT,Timed out.
HostKeyMismatchException,HOSTKEY_MISMATCH,Host key mismatch.
AuthException,AUTH_FAILED,Authentication failed.
IOException,IO,Input/output error.
```

---

## 9) Dev Fixtures (seed data)
```json
{
  "hosts": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "nickname": "web-1",
      "hostname": "web1.example.net",
      "port": 22,
      "username": "deploy",
      "auth_method": "key",
      "key_blob_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "strict_host_key": true,
      "pinned_host_key_fingerprint": "SHA256:Qm9ndXMuLi5ub3RBY3R1YWw="
    }
  ],
  "commands": [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "name": "restart-nginx",
      "command": "sudo systemctl restart nginx",
      "require_confirmation": true,
      "default_timeout_ms": 15000,
      "allow_pty": false
    }
  ],
  "host_commands": [
    {
      "id": "55555555-5555-5555-5555-555555555555",
      "host_id": "11111111-1111-1111-1111-111111111111",
      "command_id": "33333333-3333-3333-3333-333333333333",
      "sort_index": 1
    }
  ]
}
```

---

## 10) Module Layout (target)
```
:app
  di/                   // Hilt modules
  data/
    db/                 // Room entities, DAO, migrations
    repo/               // HostsRepo, CommandsRepo, LogsRepo, KeysRepo
    crypto/             // KeystoreManager, KeyWrap, Redactor
    ssh/                // SshClient, HostKeyVerifier, CommandExecutor
  ui/
    screens/            // MainTable, HostEdit, CommandEdit, Session, Logs, Settings
    components/         // MappingRow, Dialogs, TerminalPane, etc.
    state/              // ViewModels
  core/                 // Result types, error mapper, validators, time providers
  testing/              // Fakes, stubs
```

---

## 11) Compose Navigation Graph
```
"main_table" (start)
"host_edit?hostId={uuid}"
"command_edit?commandId={uuid}"
"mapping_edit?hostId={uuid}&commandId={uuid}"
"session?mappingId={uuid}"
"logs?hostId={uuid}&status={enum}&fromTs={long}&toTs={long}"
"settings"
Dialog: "tofu_dialog?hostId={uuid}&fingerprint={str}&alg={str}"
```

---

## 12) Core Interfaces (keep stable)
```kotlin
// crypto
interface KeystoreManager {
    suspend fun ensureKeys()
    suspend fun wrapDek(rawDek: ByteArray): WrappedKey
    suspend fun unwrapDek(wrapped: WrappedKey): ByteArray
}
data class WrappedKey(val iv: ByteArray, val tag: ByteArray, val ciphertext: ByteArray)

interface KeyVault {
    suspend fun importPrivateKeyPem(pem: ByteArray): KeyBlobId
    suspend fun generateEd25519(): Pair<KeyBlobId, String> // OpenSSH public key
    suspend fun deleteKey(id: KeyBlobId)
    suspend fun decryptPrivateKey(id: KeyBlobId): ByteArray // caller must zeroize
}
@JvmInline value class KeyBlobId(val id: String)

// ssh
data class HostPin(val alg: String, val sha256: String)

interface HostKeyVerifier {
    fun computeFingerprint(pubKey: ByteArray): HostPin
    fun verifyPinned(expected: HostPin, actual: HostPin): Boolean
}

sealed interface ExecStatus {
    data object Connecting: ExecStatus
    data object Running: ExecStatus
    data class Completed(val exitCode: Int): ExecStatus
    data class Failed(val reason: ExecError): ExecStatus
    data object Cancelled: ExecStatus
}
enum class ExecError { DNS, REFUSED, TIMEOUT, HOSTKEY_MISMATCH, AUTH_FAILED, IO }

interface SshClient : AutoCloseable {
    suspend fun connect(
        host: String,
        port: Int,
        timeoutsMs: Pair<Int, Int>,
        expectedPin: HostPin?
    ): HostPin
    suspend fun authUsernameKey(username: String, privateKeyPem: ByteArray)
    fun exec(command: String, pty: Boolean = false): kotlinx.coroutines.flow.Flow<okio.ByteString>
    suspend fun waitExitCode(timeoutMs: Int?): Int
    suspend fun cancel()
}
```

---

## 13) Validation & Danger Prompts
- Block NULL bytes in command strings.
- If risky tokens detected (`rm -rf`, `:(){ :|:& };:`, `dd of=/dev`, `mkfs`, `shred`, `> /etc/`, `sudo -S`), force confirm even if confirmation off.
- Host fields: hostname/IP required; port 1–65535; username non-empty.

---

## 14) Tests

### Unit
- Crypto wrap/unwrap round-trip; GCM tag mismatch; zeroization.
- Host key fingerprint compute and equality.
- Redactor patterns.
- Error mapping table.
- LogsRepo eviction by size/age/count.

### Instrumented
- Device credential unlock flow.
- SSH E2E with test server (e.g., Apache MINA sshd):
  - TOFU accept → pin stored.
  - Mismatch blocks.
  - Auth fail maps to `AUTH_FAILED`.
  - Timeout maps to `TIMEOUT`.
  - Normal exec streams and completes with exit code.

### UI (Compose)
- Main table search and run/stop transitions.
- TOFU dialog content and acceptance.
- Error banners copy.
- Settings toggles affect behavior (FLAG_SECURE, clipboard).
- Logs filters and pagination.
- Snapshot tests light/dark, large fonts.

---

## 15) Build Tasks (Claude order)
```
T1: Create :core contracts + error mapper (table-driven).
T2: KeystoreManager + KeyVault (AES-GCM wrap/unwrap) + tests.
T3: HostKeyVerifier (OpenSSH SHA256) + tests.
T4: SshClient via SSHJ (connect/auth/exec/cancel) with timeouts.
T5: Room schema, DAOs, Repos; schema version = 1; migrations defined.
T6: Redactor with provided regex set + tests.
T7: ViewModels (MainTable, HostEdit, CommandEdit, Mapping, Session, Logs, Settings).
T8: Compose screens + Navigation graph + snackbar host.
T9: Device-credential prompt integration; “details behind credential” flow.
T10: Logs ring buffer with size/age/count caps; tests.
T11: UI tests + snapshots; lint; detekt (optional).
T12: R8/Proguard rules for SSHJ and coroutines; verify release build.
```

---

## 16) Definition of Done
- Runs on API 26 and 35 emulators.
- Key import/generate works; public key display OK.
- Add/Edit Host & Command; create mapping; persistence verified.
- Run streams output; Cancel responsive ≤1s; exit code shown.
- TOFU pins; mismatch blocks with correct banner.
- Session lock after TTL; credential prompt works.
- Clipboard policy respected; auto-clear 60s.
- Screenshots blocked on sensitive screens by default.
- Logs metadata only with FIFO eviction.
- Unit/UI tests pass; lint clean; R8 rules validated.

---

## 17) Useful one-liners (dev)
**Create a debug keystore for signing (if needed by CI)**
```bash
keytool -genkeypair -v -storetype PKCS12 -keystore debug.keystore -storepass android \
 -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
 -dname "CN=Android Debug,O=Android,C=US"
```

**Gradle build + install to connected device**
```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Run unit + instrumented tests**
```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

**Accept SDK licenses non-interactively**
```bash
yes | sdkmanager --licenses >/dev/null
```

---

## 18) Compliance
- Repo includes `LICENSE` (GPL-3.0), this `SPEC.md`, `README.md`, `SECURITY.md`.
- Each source file includes a GPL-3.0 header.
- Third-party notices documented (SSHJ, AndroidX, etc.).
