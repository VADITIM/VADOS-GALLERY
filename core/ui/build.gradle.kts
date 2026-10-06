plugins {
    alias(libs.plugins.vados.android.library)
}

dependencies {
    implementation(libs.coil.video)
    implementation(project(":core:settings"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.haze)
}
