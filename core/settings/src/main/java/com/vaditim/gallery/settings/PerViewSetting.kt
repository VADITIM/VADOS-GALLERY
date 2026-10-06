package com.vaditim.gallery.settings

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

// A setting every view keeps apart, stored as "key.VIEW"; each is Compose state, so a change repaints whatever reads it.
class PerViewSetting<T>(
    private val key: String,
    private val default: (SettingsView) -> T,
    private val read: SharedPreferences.(view: SettingsView, storedKey: String) -> T,
    private val write: SharedPreferences.Editor.(storedKey: String, value: T) -> Unit,
) {
    private val values = mutableStateMapOf<SettingsView, T>()

    operator fun get(view: SettingsView): T = values[view] ?: default(view)

    fun load(store: PreferenceStore) {
        for (view in SettingsView.entries) values[view] = store.preferences.read(view, keyOf(view))
    }

    fun set(store: PreferenceStore, view: SettingsView, value: T) {
        values[view] = value
        store.edit { write(keyOf(view), value) }
    }

    private fun keyOf(view: SettingsView) = "$key.${view.name}"
}
