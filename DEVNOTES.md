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

## Toolchain pins & pairings (state after the 2026-07 migration)

Current stack: Gradle 9.4.1, AGP 9.2.0 (built-in Kotlin), Kotlin 2.4.0,
KSP 2.3.9, Hilt 2.60, Room 2.8.4, Compose BOM 2026.06.01, SSHJ 0.40.0,
BouncyCastle 1.84, compileSdk 37 / targetSdk 35 / minSdk 26, Java 21.

Hard-won pairings:
- **Hilt ≥2.59 requires AGP ≥9.0, and Hilt ≤2.58 cannot read Kotlin 2.4
  metadata.** Kotlin 2.4 + Hilt 2.60 + AGP 9 must move together; the last
  pre-AGP-9 combination is Kotlin 2.3.x + Hilt 2.58.
- **AGP 9 rejects the `org.jetbrains.kotlin.android` plugin** (built-in
  Kotlin). The Kotlin version is pinned via the Compose Compiler plugin
  (`org.jetbrains.kotlin.plugin.compose`) in the catalog; the
  `kotlin { compilerOptions {} }` block still works.
- KSP versions are decoupled from Kotlin since the 2.x line (e.g. KSP 2.3.9
  works with Kotlin 2.4.0). Room < 2.7 has no KSP2 support.
- Verify a Kotlin version is actually published (marker pom on Maven
  Central) before pinning — maven-metadata.xml lists versions whose
  artifacts 404 (2.4.10/2.4.20 at migration time).
- Room gradle plugin and room-compiler share one catalog version key.
- The `uk.uuid.slf4j:slf4j-android` provider (needed since SSHJ 0.40 uses
  slf4j-api 2.x) must stay excluded from unit-test classpaths — its
  ServiceLoader init calls real `android.util.Log` and kills mockk on the
  JVM (see the `configurations.configureEach` block in app/build.gradle.kts).
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

## Validation playbook

| Check | When |
|---|---|
| `./gradlew lint testDebugUnitTest assembleDebug` | every code change (floor) |
| + `assembleRelease` + mapping.txt canary | any change to deps, compiler, AGP/R8, or proguard rules |
| `git diff app/schemas/` must be empty | any Room/processor change (schema drift = stop) |
| `./gradlew clean …` first | processor or toolchain swaps (stale outputs lie) |
| On-device release SSH exec | AGP/R8 major bumps, SSHJ/BC bumps |
