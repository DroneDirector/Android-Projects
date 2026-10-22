plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The watch app has its own version codes (Google Play needs them different from the phone app's).
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.dronedirector.veyrocutpro.wear"
    compileSdk = 36

    defaultConfig {
        // Same app ID and signing key as the phone app, so the two can talk to each other.
        applicationId = "com.dronedirector.veyrocutpro"
        minSdk = 30
        targetSdk = 35
        versionCode = 1_000_000_000 + ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    signingConfigs {
        create("procam") {
            storeFile = file("../keystore/procam.jks")
            storePassword = "procam123"
            keyAlias = "procam"
            keyPassword = "procam123"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("procam") }
        debug { signingConfig = signingConfigs.getByName("procam") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
