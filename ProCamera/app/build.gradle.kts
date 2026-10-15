plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI sets VERSION_CODE to the build number so every APK installs over the previous one.
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.dronedirector.acluxpro"
    compileSdk = 36

    lint {
        // The audio gain stage uses a CameraX hook that is still marked library-internal.
        disable += "RestrictedApi"
        checkReleaseBuilds = false
    }

    defaultConfig {
        applicationId = "com.dronedirector.acluxpro"
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
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
