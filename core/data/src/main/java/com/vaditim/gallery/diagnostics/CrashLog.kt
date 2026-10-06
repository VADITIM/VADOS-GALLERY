package com.vaditim.gallery.diagnostics

import android.content.Context
import java.io.File

// There is no logcat on the phone this app is built for, so an uncaught exception is written to a file and shown on the next launch, where it can be shared.
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { file.writeText("VADOS Gallery $version\n${throwable.stackTraceToString()}") }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
