plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    kotlin("kapt") version "1.9.25" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
