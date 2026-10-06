plugins {
    alias(libs.plugins.vados.android.feature)
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
}
