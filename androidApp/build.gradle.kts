plugins {
    // AGP 9 : le support Kotlin est intégré, on n'applique plus kotlin-android.
    // Le code composable vit dans :shared (androidMain) — ce module reste du Kotlin pur.
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.lumen.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.lumen"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
