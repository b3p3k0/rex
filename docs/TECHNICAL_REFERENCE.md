# Rex — Technical Reference

Developer documentation for building Rex from source and understanding its internals. For the user guide, see the [README](../README.md). For working maintainer notes (toolchain pins, R8 rules, validation playbook), see [DEVNOTES.md](../DEVNOTES.md).

## Build Requirements

- **JDK 21** - Required for Android development
- **Android SDK** - platform 37 (compileSdk); Gradle installs it on demand
- **Android Studio** - Latest version recommended

### Quick Setup (Ubuntu/Debian)

```bash
# Install Java 21
sudo apt update && sudo apt install -y openjdk-21-jdk

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
sdkmanager "platform-tools" "platforms;android-37"
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
- **Target**: minSdk 26, targetSdk 35, compileSdk 37 (Android 8.0+)
- **Language**: Kotlin with Jetpack Compose
- **Database**: Room with schema version 4
- **Dependency Injection**: Hilt
- **SSH Library**: SSHJ 0.40.0

### Key Components

- **Core**: Error mapping, redaction, result types
- **Crypto**: Android Keystore KEK/DEK management, AES-GCM encryption
- **SSH**: Host key verification, SSHJ client with timeouts and streaming
- **Data**: Room entities for hosts, commands, mappings, keys, and logs
- **UI**: Compose screens with Material 3 theming

## Security Model

- **Keys**: Ed25519 private keys encrypted with random DEK, wrapped by Android Keystore KEK
- **Host verification**: Strict host key checking with SHA256 fingerprint pinning; trust-on-first-use requires explicit user confirmation of the fingerprint
- **Session management**: Device credential unlock with configurable TTL (default 5 min)
- **Privacy**: Screenshot blocking, optional clipboard with 60s auto-clear
- **Logging**: Metadata only with sensitive data redaction

## License

GPL-3.0-only - See [LICENSE](../LICENSE) for full text.
