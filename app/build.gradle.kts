import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.gangitano.karooridereplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "it.gangitano.karooridereplay"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // We deliberately request ACCESS_MOCK_LOCATION in the main manifest, NOT
    // in src/debug/. The whole purpose of this extension is to provide mock
    // GPS data — it IS the feature, not a debugging shortcut. The standard
    // lint rule assumes mock-location permission is only ever for tests; we
    // suppress that rule rather than ship a debug-only APK no rider could
    // use.
    lint {
        disable += "MockLocation"
    }
}

// AGP 9 has no public API for the APK file name; the legacy variant API this
// used is gone. VariantOutputImpl is internal, but it is the documented-by-usage
// way to keep the stable `karoo-ride-replay.apk` name the README and releases use.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as com.android.build.api.variant.impl.VariantOutputImpl)
                .outputFileName.set("karoo-ride-replay.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Karoo Extension SDK — 1.1.8+ for the CLIMB / NavigationState APIs that
    // consumer extensions are tested against.
    implementation("io.hammerhead:karoo-ext:1.1.9")

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Navigation (for the simple multi-screen UI)
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // FIT parsing — Garmin's official open FIT SDK, published to Maven
    // Central. Records, GPS coords, power, HR, cadence, speed at 1 Hz.
    // https://central.sonatype.com/artifact/com.garmin/fit
    implementation("com.garmin:fit:21.218.0")

    // JVM unit tests (engine logic, virtual-device lifecycle).
    testImplementation("junit:junit:4.13.2")
    // Virtual time for the device lifecycle tests (800 ms search delay, state changes).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
