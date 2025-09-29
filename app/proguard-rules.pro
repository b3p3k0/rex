# Rex ProGuard Rules

# SSHJ - Keep all SSH classes and algorithms
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }

# SSHJ Transport - Ensure cipher and algorithm lists are preserved
-keep class net.schmizz.sshj.transport.cipher.** { *; }
-keep class net.schmizz.sshj.transport.kex.** { *; }
-keep class net.schmizz.sshj.transport.mac.** { *; }
-keep class net.schmizz.sshj.transport.compression.** { *; }
-keep class net.schmizz.sshj.transport.random.** { *; }
-keep class net.schmizz.sshj.signature.** { *; }

# SSHJ Algorithm Registry - Critical for cipher negotiation
-keep class net.schmizz.sshj.DefaultConfig { *; }
-keep class net.schmizz.sshj.ConfigImpl { *; }
-keepclassmembers class * {
    @net.schmizz.sshj.common.Factory *;
}

# Suppress warnings for missing GSSAPI classes (not available on Android)
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**