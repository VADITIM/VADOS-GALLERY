package com.vaditim.gallery.components

// A file's size the way the details and the duplicates show it: megabytes with one decimal, kilobytes below that.
fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "%d KB".format(bytes / 1000)
