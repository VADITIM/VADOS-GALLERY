plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

// Compile-only: the root build puts these plugins on the classpath once, and every module shares that one copy.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "vados.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "vados.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
