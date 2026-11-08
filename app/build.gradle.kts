plugins {
    id("com.android.application") version "8.11.0"
    id("org.jetbrains.kotlin.android") version "2.1.20"
}
android {
    namespace = "com.drivemate.app"
    compileSdk = 36
    useLibrary("android.car")
    defaultConfig {
        applicationId = "com.drivemate.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
}
kotlin { jvmToolchain(17) }



