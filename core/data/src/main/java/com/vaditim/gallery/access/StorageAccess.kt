package com.vaditim.gallery.access

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings

data class AccessState(val hasFileAccess: Boolean, val canManageMedia: Boolean)

// Two special permissions, both granted on a system settings page rather than a runtime dialog. File access is required; media management only removes the confirmation popup from favourite, move and delete.
object StorageAccess {
    fun read(context: Context): AccessState = AccessState(
        hasFileAccess = Environment.isExternalStorageManager(),
        canManageMedia = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context),
    )

    fun fileAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun mediaManagementIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }
}
