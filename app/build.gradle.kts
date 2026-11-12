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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
kotlin { jvmToolchain(17) }




dependencies {
 implementation("ai.pleos.playground:Vehicle:2.3.2")
 implementation("ai.pleos.playground:NaviHelper:3.0.5")
 implementation("ai.pleos.playground:TextToSpeech:3.3.1")
 constraints {
  // Pleos TextToSpeech pulls JNA 5.13.0 via universal-access; its x86_64 library is not 16 KB aligned, 5.16.0+ is.
  implementation("net.java.dev.jna:jna:5.17.0")
 }
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
