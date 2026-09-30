plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// `-Pforge.preview` (the CI build): the real app is signed with the maintainer's own debug key,
// which CI doesn't have, so a CI APK could never update it. Preview builds get their own package
// id, label and a committed preview-only key instead — they install beside the real app, leave
// its data alone, and successive previews update each other.
val preview = providers.gradleProperty("forge.preview").isPresent

android {
    namespace = "com.forge.workout"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.forge.workout"
        // 28: ImageDecoder / AnimatedImageDrawable for the exercise GIFs.
        minSdk = 28
        targetSdk = 36
        // Bump both on every release — otherwise an installed build is unidentifiable.
        versionCode = 8
        versionName = "1.7"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    signingConfigs {
        create("preview") {
            storeFile = file("preview.keystore")
            storePassword = "android"
            keyAlias = "forgepreview"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so a release build installs directly from the CLI.
            signingConfig = signingConfigs.getByName(if (preview) "preview" else "debug")
            if (preview) {
                applicationIdSuffix = ".preview"
                versionNameSuffix = "-preview"
                manifestPlaceholders["appLabel"] = "Forge Preview"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.health.connect)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
