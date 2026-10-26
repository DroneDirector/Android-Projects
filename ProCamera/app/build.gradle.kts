plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI sets VERSION_CODE to the build number so every APK installs over the previous one.
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.dronedirector.veyrocutpro"
    // AI models and soundtracks are downloaded on first launch (not in the APK).
    androidResources { noCompress += listOf("onnx", "tflite") }
    // One APK per processor type: each phone gets only its own native code (smaller download).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    compileSdk = 36
    // Automatic RAW sample tests (app/src/test): run on the computer, with Robolectric standing in for Android.
    testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true; all { it.maxHeapSize = "4g"; it.systemProperty("rawSamples", System.getenv("RAW_SAMPLES") ?: ""); it.systemProperty("rawReport", System.getenv("RAW_REPORT") ?: ""); System.getenv("LIBRAW_HOST")?.let { p -> it.systemProperty("java.library.path", p) } } } }
    // Several native libraries ship the same C++ runtime; one copy is enough.
    packaging {
        jniLibs { pickFirsts += listOf("**/libc++_shared.so") }
        resources { excludes += listOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module") }
    }

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
        // Native code (RNNoise, whisper.cpp, morph, DeepFilterNet): phones + the x86_64 emulator — see splits below.
        externalNativeBuild { cmake { cppFlags += "" } }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
            // Full native debug symbols in a separate zip (not in the APK) for Play Console crash reports.
            ndk { debugSymbolLevel = "FULL" }
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
    // Lottie animations as layers (Apache 2.0).
    implementation("com.airbnb.android:lottie:6.6.0")
    // Caption translation on the phone (ML Kit, free).
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.ai.edge.litert:litert:2.1.0")
    // Magic Mask: ONNX Runtime (MIT) runs MobileSAM (Apache 2.0) on the phone.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    implementation("androidx.security:security-crypto:1.0.0")
    // Network storage (NAS / SMB shares): smbj (Apache 2.0) and smbj-rpc for the share list (BSD 3-Clause).
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.rapid7.client:dcerpc:0.12.13")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.heifwriter:heifwriter:1.0.0")
    // On-device face detection for the privacy face blur (ML Kit, bundled model; free for commercial apps).
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    // Portrait mode: any subject (pets, flowers, rocks, branches…) — ML Kit via Google Play services; free for commercial apps.
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    // Library AI search: what is in each photo (ML Kit image labelling via Google Play services — small, free).
    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")
    // JPEG XL export (Apache 2.0 / BSD-3; libjxl BSD-3, Brotli MIT).
    implementation("io.github.awxkee:jxl-coder:2.6.1")
    // Tests only (not in the app): JUnit (EPL) and Robolectric (MIT) for the automatic RAW sample tests.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // Video colour grading (export with a LUT).
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Watch remote (Wear OS): talking to the AlcorCut Pro watch app.
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    // Chromecast: Google's Cast library (free to use; Google Play services) and AndroidX MediaRouter (Apache 2.0).
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
