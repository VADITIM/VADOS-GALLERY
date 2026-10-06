plugins {
    alias(libs.plugins.vados.android.feature)
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.haze)
}
