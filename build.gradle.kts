buildscript {
    dependencies {
        // AGP 9 builds Kotlin in and ships with an older Kotlin Gradle plugin;
        // pin it so the compiler matches the compose/serialization plugins below.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
