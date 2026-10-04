package com.vaditim.gallery.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vaditim.gallery.vas.Motion

// The phone's own vibrator rather than view haptics, which One UI mutes or flattens to one buzz depending on system settings.
object Haptics {
    // Two short clicks: a photo has landed somewhere else, gone to the trash, or been favourited.
    fun confirm(context: Context) {
        val vibrator = vibratorOf(context) ?: return
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val effect = if (vibrator.areAllPrimitivesSupported(click)) {
            VibrationEffect.startComposition()
                .addPrimitive(click, CONFIRM_STRENGTH)
                .addPrimitive(click, CONFIRM_STRENGTH, Motion.HAPTIC_CONFIRM_GAP_MS)
                .compose()
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, CLICK_MS, Motion.HAPTIC_CONFIRM_GAP_MS.toLong(), CLICK_MS), -1)
        }
        vibrator.vibrate(effect)
    }

    // One light tick for every photo or cover that joins or leaves a selection.
    fun tick(context: Context) {
        val vibrator = vibratorOf(context) ?: return
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val effect = if (vibrator.areAllPrimitivesSupported(tick)) {
            VibrationEffect.startComposition().addPrimitive(tick, TICK_STRENGTH).compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
        vibrator.vibrate(effect)
    }

    private fun vibratorOf(context: Context): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }

    private const val CONFIRM_STRENGTH = 0.8f
    private const val TICK_STRENGTH = 0.6f
    private const val CLICK_MS = 18L
}
