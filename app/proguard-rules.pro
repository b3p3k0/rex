# Rex ProGuard Rules

# SSHJ
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }

# Suppress warnings for missing GSSAPI classes (not available on Android)
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**