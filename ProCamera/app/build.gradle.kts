plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI sets VERSION_CODE to the build number so every APK installs over the previous one.
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.dronedirector.veyrocutpro"
    compileSdk = 36

    lint {
        // The audio gain stage uses a CameraX hook that is still marked library-internal.
        disable += "RestrictedApi"
        checkReleaseBuilds = false
    }

    defaultConfig {
        applicationId = "com.dronedirector.veyrocutpro"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    // A fixed key kept in the repo so updates install over earlier builds.
    signingConfigs {
        create("procam") {
            storeFile = file("../keystore/procam.jks")
            storePassword = "procam123"
            keyAlias = "procam"
            keyPassword = "procam123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("procam")
        }
        debug {
            signingConfig = signingConfigs.getByName("procam")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Live streaming over RTMP / RTMPS / SRT
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")

    val camerax = "1.7.0-alpha03"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.ai.edge.litert:litert:2.1.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.heifwriter:heifwriter:1.0.0")
    // On-device face detection for the privacy face blur (ML Kit, bundled model; free for commercial apps).
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    // Portrait mode: any subject (pets, flowers, rocks, branches…) — ML Kit via Google Play services; free for commercial apps.
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    // Video colour grading (export with a LUT).
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
