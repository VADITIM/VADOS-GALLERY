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
        versionCode = 166
        versionName = "1.1.24"
    }

    signingConfigs {
        // Debug builds only: one committed key, so a local debug build installs over the last one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The release key never enters the repository, which is public; CI writes it out from the repository's secrets and points these variables at it.
        create("release") {
            System.getenv("RELEASE_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // The build to install. A debug build runs Compose unoptimised and debuggable, which is where most scroll jank on a large grid comes from; this one is shrunk and optimised by R8 and signed with the dedicated release key once it is set up (debug key until then).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // On hold: until the release key's secrets exist, releases keep the committed debug key so they still install over the last one.
            signingConfig = signingConfigs.getByName(if (System.getenv("RELEASE_KEYSTORE") != null) "release" else "debug")
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
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
}

kotlin {
    jvmToolchain(17)
}
