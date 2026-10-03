package com.vaditim.gallery.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// The fingerprint, with the phone's PIN as the fallback the system offers when the finger fails — the same unlock as the lock screen, so Private is never harder to open than the phone itself.
object PrivateLock {
    fun unlock(context: Context, onUnlocked: () -> Unit) {
        val activity = context.findActivity() ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            },
        )
        val information = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Private")
            .setSubtitle("Unlock with your fingerprint")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(information)
    }

    private fun Context.findActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
