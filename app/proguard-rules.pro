# Rex ProGuard Rules

# SSHJ - Core library classes and algorithm registry
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }

# SSHJ - Algorithm factory pattern (critical for cipher negotiation)
-keep class * implements net.schmizz.sshj.common.Factory$Named
-keep class * extends net.schmizz.sshj.common.Factory
-keep interface net.schmizz.sshj.common.Factory$Named
-keep interface net.schmizz.sshj.common.Factory

# SSHJ - Configuration classes with algorithm registration
-keep class net.schmizz.sshj.DefaultConfig { *; }
-keep class net.schmizz.sshj.AndroidConfig { *; }
-keep class net.schmizz.sshj.ConfigImpl { *; }

# Prevent R8 from optimizing away static initializers that register algorithms
-keepclassmembers class net.schmizz.sshj.** {
    static <clinit>();
}
-keepclassmembers class com.hierynomus.sshj.** {
    static <clinit>();
}

# BouncyCastle Provider - Required by SSHJ for cipher implementations
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.crypto.engines.** { *; }
-keep class org.bouncycastle.crypto.modes.** { *; }
-keep class org.bouncycastle.crypto.paddings.** { *; }
-keep class org.bouncycastle.crypto.params.** { *; }
-keep class org.bouncycastle.crypto.util.** { *; }

# SSHJ - Cipher implementations (dynamically loaded)
-keep class * extends net.schmizz.sshj.transport.cipher.Cipher
-keep class net.schmizz.sshj.transport.cipher.** { *; }

# SSHJ - Key exchange and MAC algorithms
-keep class * extends net.schmizz.sshj.transport.kex.KeyExchange
-keep class net.schmizz.sshj.transport.kex.** { *; }
-keep class * extends net.schmizz.sshj.transport.mac.MAC
-keep class net.schmizz.sshj.transport.mac.** { *; }

# Suppress warnings for missing GSSAPI classes (not available on Android)
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**