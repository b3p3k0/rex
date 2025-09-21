# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Core Development Tasks
```bash
# Build debug APK and run tests
./gradlew lint test assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests on connected device
./gradlew connectedDebugAndroidTest

# Check code style and run all checks
./gradlew check
```

### Project Requirements
- **JDK 21** (Ubuntu 24.04 LTS default) - Critical requirement, not JDK 17
- **Android SDK API 35** with build-tools 35.0.0
- **Material3 Theme System**: Uses `Theme.Material3.DayNight.NoActionBar` parent theme
- **Compose Compiler**: Version 1.5.15 compatible with Kotlin 1.9.25
- Room schema exports are tracked in git at `app/schemas/`

### Version Synchronization & Compatibility
- Keep Room plugin version in `build.gradle.kts` synchronized with room-compiler version in `app/build.gradle.kts` (currently 2.6.1)
- **Kotlin ↔ Compose Compiler**: Current pairing is Kotlin 1.9.25 ↔ Compose Compiler 1.5.15
- **Theme Dependencies**: Both AppCompat 1.7.0 and Material 1.12.0 required alongside Compose BOM 2024.09.01
- **Material3 Colors**: XML colors.xml must mirror Compose ColorScheme for consistency

## Architecture Overview

### Security-First Design
Rex is a GPL-3.0 licensed Android app for secure SSH command execution with these core security principles:
- **KEK/DEK Pattern**: Private keys encrypted with random DEK, wrapped by non-exportable Android Keystore KEK
- **TOFU Host Verification**: Trust On First Use with SHA256 fingerprint pinning
- **Minimal Logging**: Metadata-only logs with automatic sensitive data redaction
- **Session Security**: Device credential unlock with configurable TTL (default 5 min)

### Core Components

#### Data Layer (`app/src/main/java/dev/rex/app/data/`)
- **Database**: Room with 5 entities (hosts, commands, host_commands, key_blobs, logs)
- **Crypto**: Android Keystore KEK/DEK encryption with AES-GCM
- **SSH**: SSHJ-based client with host key verification and command streaming
- **Repositories**: Abstraction layer for data access (hosts, commands, logs, keys)

#### Core Primitives (`app/src/main/java/dev/rex/app/core/`)
- **ExecTypes.kt**: Sealed interfaces for execution status and error handling
- **ErrorMapper.kt**: Table-driven exception mapping to user-friendly messages
- **Redactor.kt**: Regex-based sensitive data redaction (passwords, tokens, hex)

#### Dependency Injection (`app/src/main/java/dev/rex/app/di/`)
- **Hilt modules**: DatabaseModule, CryptoModule for clean dependency management

### Key Interfaces

```kotlin
// Crypto layer contracts
interface KeystoreManager {
    suspend fun wrapDek(rawDek: ByteArray): WrappedKey
    suspend fun unwrapDek(wrapped: WrappedKey): ByteArray
}

interface KeyVault {
    suspend fun importPrivateKeyPem(pem: ByteArray): KeyBlobId
    suspend fun generateEd25519(): Pair<KeyBlobId, String>
    suspend fun decryptPrivateKey(id: KeyBlobId): ByteArray
}

// SSH layer contracts  
interface SshClient : AutoCloseable {
    suspend fun connect(host: String, port: Int, timeoutsMs: Pair<Int, Int>, expectedPin: HostPin?): HostPin
    suspend fun authUsernameKey(username: String, privateKeyPem: ByteArray)
    fun exec(command: String, pty: Boolean = false): Flow<ByteString>
}
```

### Data Model (Room Schema v1)
- `hosts`: Connection details with auth method and host key pinning
- `commands`: Named command templates with confirmation and timeout settings  
- `host_commands`: Many-to-many mapping with sort order
- `key_blobs`: Encrypted private keys with DEK/KEK wrapping
- `logs`: Execution metadata with redacted summaries and FIFO eviction

## Testing Strategy

### Unit Tests Location
- Core utilities: `app/src/test/java/dev/rex/app/core/`
- Crypto functions: `app/src/test/java/dev/rex/app/data/crypto/`
- SSH components: `app/src/test/java/dev/rex/app/data/ssh/`

### Test Focus Areas
- Crypto wrap/unwrap round-trips and GCM tag validation
- Host key fingerprint computation and verification  
- Redaction pattern matching and sensitive data detection
- Error mapping table completeness
- Repository FIFO eviction logic

### Running Single Tests
```bash
# Run specific test class
./gradlew :app:testDebugUnitTest --tests="dev.rex.app.core.RedactorTest"

# Run specific test method
./gradlew :app:testDebugUnitTest --tests="dev.rex.app.core.RedactorTest.redacts password patterns"
```

## Development Notes

### Security Requirements
- All source files must include GPL-3.0 headers
- Use "Rex Maintainers (b3p3k0)" for authorship
- Namespace: `dev.rex.app` (not com.github.*)
- Never log sensitive data - use Redactor.redact() for any output

### Room Database
- Schema exports enabled and committed to git
- Database version 1 with migration framework ready
- Use repository pattern for data access abstraction

### SSH Implementation
- SSHJ 0.38.0 for SSH connectivity
- Timeouts: connect (8000ms), read (15000ms) defaults
- Stream output using Flow<ByteString> for real-time display
- TOFU flow: accept unknown hosts, pin on first connection

### Error Handling
Use ErrorMapper for consistent exception-to-user-message mapping:
- UnknownHostException → "Hostname not found"
- ConnectException → "Connection refused"  
- SocketTimeoutException → "Timed out"
- AuthException → "Authentication failed"

## Quality Assurance

### Automated Checks
The `rex_dev_setup.sh --launch` script includes quality gates:
- `./gradlew lint` - Code style and resource validation
- Build verification before emulator launch
- CI-ready command sequence for future automation

### Theme Validation
- All XML theme references use Material3 hierarchy
- Colors.xml provides Material3 seed color system (#6750A4)
- AppCompat/Material dependencies ensure proper fallback support
- Transparent status bar configuration for modern UI