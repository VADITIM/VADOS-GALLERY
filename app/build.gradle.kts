plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vaditim.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vaditim.gallery"
        // Android 11 is the floor because favourites and the trash live in MediaStore columns (IS_FAVORITE, IS_TRASHED) that only exist from API 30.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    // One debug key committed to the repository, so every CI build installs over the last one with `adb install -r` instead of failing on a changed signature.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // The build to install. A debug build runs Compose unoptimised and debuggable, which is where most scroll jank on a large grid comes from; this one is shrunk and optimised by R8, and signed with the same committed key so it installs over either build.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.haze)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
}

kotlin {
    jvmToolchain(17)
}
