import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.rex.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.rex.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "com.google.dagger.hilt.android.testing.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseSigningConfigured = signingConfigs.create("release").run {
        val keystorePath = project.findProperty("REX_RELEASE_KEYSTORE")?.toString()
        val keystorePassword = project.findProperty("REX_RELEASE_KEYSTORE_PASSWORD")?.toString()
        val keyAlias = project.findProperty("REX_RELEASE_KEY_ALIAS")?.toString()
        val keyPassword = project.findProperty("REX_RELEASE_KEY_PASSWORD")?.toString()

        val hasAllCredentials = listOf(keystorePath, keystorePassword, keyAlias, keyPassword)
            .all { !it.isNullOrBlank() }

        if (hasAllCredentials && keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        } else {
            println("Warning: Release signing properties are missing; release APK will remain unsigned.")
        }

        hasAllCredentials
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE*,NOTICE*}"
        }
    }
    
    lint {
        checkTestSources = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Kotlin + Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Core Android UI
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Activity + Lifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Biometric authentication
    implementation(libs.androidx.biometric)

    // DataStore preferences
    implementation(libs.androidx.datastore.preferences)

    // SSHJ
    implementation(libs.sshj)
    implementation(libs.slf4j.android)
    implementation(libs.bouncycastle)

    // Ed25519 cryptography
    implementation(libs.eddsa)

    // OkIO for ByteString
    implementation(libs.okio)

    // Logging
    implementation(libs.androidx.tracing.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.eddsa)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
