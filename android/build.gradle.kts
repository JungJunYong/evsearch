buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // FCM (google-services.json이 있을 때만 app 모듈에서 조건부 적용)
        classpath("com.google.gms:google-services:4.4.2")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.ksp) apply false
}
