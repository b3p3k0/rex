# Rex — Remote Exec for Android

Android app to store hosts and commands, run them over SSH, and stream output. Security first. Minimal logging. No exports.

## Features

- **Secure SSH execution**: Store hosts and commands, execute over SSH with streaming output
- **Host key pinning**: TOFU (Trust On First Use) with SHA256 fingerprint verification
- **Encrypted storage**: Private keys encrypted with Android Keystore (non-exportable KEK)
- **Execution logs**: View command history with filtering by host, status, and search
- **Retention management**: Configurable log eviction policies (count/age/size) with manual purge
- **Minimal logging**: Metadata-only logs with automatic redaction and FIFO eviction
- **Material Design 3**: Modern Android UI with accessibility support
- **Device security**: PIN/passcode unlock, FLAG_SECURE on sensitive screens

## Build Requirements

- **JDK 17** - Required for Android development
- **Android SDK** - API 35 with build-tools 35.0.0
- **Android Studio** - Latest version recommended

### Quick Setup (Ubuntu/Debian)

```bash
# Install Java 17
sudo apt update && sudo apt install -y openjdk-17-jdk

# Install Android SDK
mkdir -p "$HOME/Android/cmdline-tools" && cd "$HOME/Android"
curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdtools.zip
unzip -q cmdtools.zip && rm cmdtools.zip
mv cmdline-tools cmdline-tools/latest

# Setup environment
export ANDROID_HOME="$HOME/Android"
export PATH="$HOME/Android/cmdline-tools/latest/bin:$HOME/Android/platform-tools:$PATH"

# Accept licenses and install components
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## Build & Install

```bash
# Build debug APK
./gradlew lint test assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

- **Package**: `dev.rex.app` 
- **Target**: API 26-35 (Android 8.0+)
- **Language**: Kotlin with Jetpack Compose
- **Database**: Room with schema version 1
- **Dependency Injection**: Hilt
- **SSH Library**: SSHJ 0.38.0

### Key Components

- **Core**: Error mapping, redaction, result types
- **Crypto**: Android Keystore KEK/DEK management, AES-GCM encryption
- **SSH**: Host key verification, SSHJ client with timeouts and streaming
- **Data**: Room entities for hosts, commands, mappings, keys, and logs
- **UI**: Compose screens with Material 3 theming

## Security Model

- **Keys**: Ed25519 private keys encrypted with random DEK, wrapped by Android Keystore KEK
- **Host verification**: Strict host key checking with SHA256 fingerprint pinning
- **Session management**: Device credential unlock with configurable TTL (default 5 min)
- **Privacy**: Screenshot blocking, optional clipboard with 60s auto-clear
- **Logging**: Metadata only with sensitive data redaction

## License

GPL-3.0-only - See [LICENSE](LICENSE) for full text.

---

*Rex Maintainers (b3p3k0)*