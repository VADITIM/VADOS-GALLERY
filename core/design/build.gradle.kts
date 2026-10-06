plugins {
    alias(libs.plugins.vados.android.library)
}

dependencies {
    implementation(project(":core:settings"))
    implementation(libs.haze)
}
