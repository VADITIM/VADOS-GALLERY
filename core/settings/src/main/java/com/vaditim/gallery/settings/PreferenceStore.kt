package com.vaditim.gallery.settings

import android.content.Context
import android.content.SharedPreferences

// The one preference file every store writes into; the backup copies it by this name, so the keys inside it never move.
class PreferenceStore(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun lines(key: String): List<String> = preferences.getString(key, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    fun putLines(key: String, lines: List<String>) = edit { putString(key, lines.joinToString("\n")) }

    fun edit(change: SharedPreferences.Editor.() -> Unit) = preferences.edit().apply(change).apply()

    companion object {
        const val FILE_NAME = "settings"
    }
}
