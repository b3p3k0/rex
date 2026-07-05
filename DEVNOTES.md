# DEVNOTES

Working notes for maintainers and AI agents. Read this before touching the
build, proguard rules, or tests. Keep entries terse; delete what stops being
true.

## R8 / SSHJ cipher stripping (recurred twice — do not re-learn this)

**Symptom:** release builds fail SSH negotiation ("no common ciphers") while
debug builds work. Nothing logs the cause.

**Root cause:** SSHJ registers ciphers/KEX/MACs through `Factory$Named`
implementations and static initializers; BouncyCastle registers JCA/JCE
algorithms reflectively. R8 sees all of it as unreachable and strips it.

**Protection:** `app/proguard-rules.pro` keeps (1) SSHJ factory
implementations, (2) `<clinit>` static initializers on SSHJ packages,
(3) `org.bouncycastle.jcajce.provider.**`. All three blocks are load-bearing.

**Canary:** after any release build,
`grep -c '^net\.schmizz\.sshj\.transport\.cipher' app/build/outputs/mapping/release/mapping.txt`
must be > 0 (baseline: 22). CI enforces this. If it drops, fix with
*additive* `-keep` rules; never disable minification.

**Real verification:** install the release APK and run one SSH command.
mapping.txt proves classes survived, not that negotiation works.

## Unit-test environment rules

- **Never `mockkStatic` JDK core classes** (`System`, `Class`, …). On JDK 18+
  it recurses in `System.getSecurityManager` until StackOverflow, and the
  error spam deadlocks the Gradle test worker — the build hangs forever
  rather than failing. Mock time by injecting or capturing arguments.
- `android.util.Log` and `android.util.Base64` are throwing stubs in unit
  tests: mock `Log` per test class (see `GatekeeperTest`), shim `Base64` to
  `java.util.Base64` (see `HostKeyVerifierTest`).
- ViewModel tests need `Dispatchers.setMain(UnconfinedTestDispatcher())` in
  `@Before` and `resetMain()` in `@After`.
- Repository mocks must honor write contracts: `assignKeyToHost` persists
  `keyProvisionStatus = "pending"`, so `getHostById` must return the updated
  host after it is called (ViewModels reload after mutations).

## Toolchain pins & pairings

- Kotlin and KSP versions move in lockstep (`<kotlin>-<ksp>` artifact naming).
- Room < 2.7 does not support KSP2; Room and Kotlin 2.4 must be bumped
  together.
- Room gradle plugin and room-compiler share one version; single-source them
  in the version catalog.
- This machine: system Java is too new for the wrapper; build with
  `JAVA_HOME=/opt/android-studio/jbr` (JBR 21) and `ANDROID_HOME=~/Android/Sdk`.

## Release signing

`REX_RELEASE_KEYSTORE`, `REX_RELEASE_KEYSTORE_PASSWORD`,
`REX_RELEASE_KEY_ALIAS`, `REX_RELEASE_KEY_PASSWORD` gradle properties
(user-level `~/.gradle/gradle.properties`; keystore at
`~/.keystores/rex-release.keystore`, PKCS12, alias `rex`). Missing
properties → unsigned release with a console warning, not a failure. CI
builds unsigned on purpose.

## Known placeholders and open issues

- `KeyVaultImpl.extractPublicKeyFromPem` fabricates the public key for
  imported PEM keys (content-hash placeholder) — imported keys cannot
  authenticate. Documented in SECURITY.md; needs a real parser.
- Password auth for command execution is unimplemented
  (`SessionViewModel`, "password" branch).
- TOFU first-connection fingerprint is `println`'d, not surfaced in UI
  (`SshjClient.connect`).
- **Logging posture violation:** `SessionViewModel` (`emitFinalState`,
  `executeCommandWithTimeout`) and `SshjClient.exec` log full command
  output to logcat via `Log.d`. The app's stated posture is metadata-only
  logging. Sweep these before any release-hardening pass.

## Validation playbook

| Check | When |
|---|---|
| `./gradlew lint testDebugUnitTest assembleDebug` | every code change (floor) |
| + `assembleRelease` + mapping.txt canary | any change to deps, compiler, AGP/R8, or proguard rules |
| `git diff app/schemas/` must be empty | any Room/processor change (schema drift = stop) |
| `./gradlew clean …` first | processor or toolchain swaps (stale outputs lie) |
| On-device release SSH exec | AGP/R8 major bumps, SSHJ/BC bumps |
