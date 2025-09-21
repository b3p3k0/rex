# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# SSHJ
-dontwarn net.schmizz.sshj.**
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }

# BouncyCastle (used by SSHJ)
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# EdDSA (used by SSHJ)
-dontwarn net.i2p.crypto.eddsa.**
-keep class net.i2p.crypto.eddsa.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembernames class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkIO
-dontwarn okio.**
-dontwarn javax.annotation.**