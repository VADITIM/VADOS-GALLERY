package com.vaditim.gallery.backup

import android.content.Context
import android.content.Intent
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

// What the app remembers that a reinstall would wipe, in one JSON file the user keeps: the preference files (settings, covers, review progress) and the caches that take minutes to rebuild. Private photos are not in it; they already live outside the app.
object Backup {
    private const val FORMAT = 1
    private val PREFERENCE_FILES = listOf("settings", "covers", "review")
    private val DATA_FILES = listOf("similar.tsv", "locations.tsv", "place-names.tsv")

    fun write(context: Context, output: OutputStream) {
        val preferences = JSONObject()
        for (name in PREFERENCE_FILES) {
            val entries = JSONObject()
            for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                val typed = when (value) {
                    is Boolean -> JSONObject().put("type", "boolean").put("value", value)
                    is Int -> JSONObject().put("type", "int").put("value", value)
                    is Long -> JSONObject().put("type", "long").put("value", value)
                    is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                    is String -> JSONObject().put("type", "string").put("value", value)
                    is Set<*> -> JSONObject().put("type", "set").put("value", JSONArray(value.map { it.toString() }))
                    else -> continue
                }
                entries.put(key, typed)
            }
            preferences.put(name, entries)
        }
        val files = JSONObject()
        for (name in DATA_FILES) File(context.filesDir, name).takeIf { it.isFile }?.let { files.put(name, it.readText()) }
        val root = JSONObject().put("format", FORMAT).put("preferences", preferences).put("files", files)
        output.write(root.toString().toByteArray())
    }

    // Reads the whole file before touching anything, so a file that is not a backup changes nothing. Returns whether it was applied.
    fun restore(context: Context, input: InputStream): Boolean {
        val preferences = HashMap<String, Map<String, Any>>()
        val files = HashMap<String, String>()
        try {
            val root = JSONObject(input.readBytes().decodeToString())
            if (root.optInt("format") != FORMAT) return false
            val storedPreferences = root.getJSONObject("preferences")
            for (name in PREFERENCE_FILES) {
                val entries = storedPreferences.optJSONObject(name) ?: continue
                preferences[name] = entries.keys().asSequence().associateWith { key ->
                    val typed = entries.getJSONObject(key)
                    val value = typed.get("value")
                    when (typed.getString("type")) {
                        "boolean" -> typed.getBoolean("value")
                        "int" -> typed.getInt("value")
                        "long" -> typed.getLong("value")
                        "float" -> typed.getDouble("value").toFloat()
                        "string" -> typed.getString("value")
                        "set" -> (value as JSONArray).let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                        else -> error("unknown type")
                    }
                }
            }
            val storedFiles = root.optJSONObject("files")
            for (name in DATA_FILES) storedFiles?.optString(name, null)?.let { files[name] = it }
        } catch (_: Exception) {
            return false
        }
        for ((name, entries) in preferences) {
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for ((key, value) in entries) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                }
            }
            // Written to disk before the app restarts, which is what reads it back.
            editor.commit()
        }
        for ((name, text) in files) File(context.filesDir, name).writeText(text)
        return true
    }

    // Settings and the indexes are read once at start, so a restored backup shows only in a fresh process.
    fun restart(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Process.killProcess(Process.myPid())
    }
}
