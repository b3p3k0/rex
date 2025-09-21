# Rex ProGuard Rules

# SSHJ
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }

# Suppress warnings
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**