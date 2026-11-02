plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ru.voiceactivation.next"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.voiceactivation.next.test"
        minSdk = 28
        targetSdk = 35
        versionCode = 10
        versionName = "1.9.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    testImplementation("junit:junit:4.13.2")
}
