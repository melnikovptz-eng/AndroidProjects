plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.voiceactivation.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.voiceactivation.demo"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // sherpa-onnx v2.0.1: JVM API plus bundled Android ARM64 native runtime.
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.1")
}
