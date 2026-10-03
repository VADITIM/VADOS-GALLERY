package com.vaditim.gallery.media

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

// Where a photo was taken: its coordinates, and the city and country they fall in once the geocoder has named them.
data class Place(val latitude: Double, val longitude: Double, val city: String?, val country: String?) {
    val label: String? get() = listOfNotNull(city, country).joinToString(", ").ifEmpty { null }
}

// Photos carry no city, only GPS in their EXIF (or a location atom in a video), so each one is read once and remembered on disk. Coordinates are named per ~1 km cell, so a few hundred photos from one trip cost one geocoder call.
class LocationIndex(private val context: Context) {
    private val coordinatesFile = File(context.filesDir, "locations.tsv")
    private val namesFile = File(context.filesDir, "place-names.tsv")

    // Media id to coordinates; a null value means the file was read and has no location.
    private val coordinates = HashMap<Long, Pair<Double, Double>?>()
    private val names = HashMap<String, Pair<String?, String?>>()
    private var isLoaded = false
    private val lock = Mutex()

    // Reads every item not read before, names every cell not named before, and hands back the complete map after each batch.
    suspend fun index(items: List<MediaItem>, canReadLocation: Boolean, onUpdate: (Map<Long, Place>) -> Unit): Unit = withContext(Dispatchers.IO) { lock.withLock {
        load()
        onUpdate(snapshot())
        // Without the media-location permission the system strips GPS from what it returns, so nothing read now may be remembered as "no location".
        if (!canReadLocation) return@withLock
        val unread = items.filter { it.id !in coordinates && it.uri.scheme == "content" }
        unread.chunked(BATCH).forEach { batch ->
            batch.forEach { item -> coordinates[item.id] = read(item) }
            nameCells()
            save()
            onUpdate(snapshot())
        }
        if (unread.isEmpty() && nameCells()) {
            save()
            onUpdate(snapshot())
        }
    } }

    private fun snapshot(): Map<Long, Place> = buildMap {
        coordinates.forEach { (id, point) ->
            if (point != null) {
                val name = names[cellOf(point.first, point.second)]
                put(id, Place(point.first, point.second, name?.first, name?.second))
            }
        }
    }

    // Returns whether any new cell got a name.
    private suspend fun nameCells(): Boolean {
        if (!Geocoder.isPresent()) return false
        val geocoder = Geocoder(context, Locale.getDefault())
        var isChanged = false
        coordinates.values.filterNotNull().map { cellOf(it.first, it.second) to it }.distinctBy { it.first }.forEach { (cell, point) ->
            if (cell in names) return@forEach
            val address = lookUp(geocoder, point.first, point.second) ?: return@forEach
            names[cell] = (address.locality ?: address.subAdminArea ?: address.adminArea) to address.countryName
            isChanged = true
        }
        return isChanged
    }

    private suspend fun lookUp(geocoder: Geocoder, latitude: Double, longitude: Double): Address? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (continuation.isActive) continuation.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }
    } catch (_: Exception) {
        null
    }

    private fun read(item: MediaItem): Pair<Double, Double>? = try {
        val original = MediaStore.setRequireOriginal(item.uri)
        if (item.isVideo) readVideo(original) else readPhoto(original)
    } catch (_: Exception) {
        null
    }

    private fun readPhoto(uri: android.net.Uri): Pair<Double, Double>? =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = FloatArray(2)
            if (ExifInterface(stream).getLatLong(output) && !(output[0] == 0f && output[1] == 0f)) output[0].toDouble() to output[1].toDouble() else null
        }

    // Videos keep it as ISO 6709, e.g. "+52.5200+013.4050/".
    private fun readVideo(uri: android.net.Uri): Pair<Double, Double>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) ?: return null
            val match = ISO_6709.find(value) ?: return null
            match.groupValues[1].toDouble() to match.groupValues[2].toDouble()
        } finally {
            retriever.release()
        }
    }

    private fun cellOf(latitude: Double, longitude: Double): String = "%.2f,%.2f".format(Locale.ROOT, latitude, longitude)

    private fun load() {
        if (isLoaded) return
        isLoaded = true
        if (coordinatesFile.exists()) coordinatesFile.forEachLine { line ->
            val parts = line.split('\t')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@forEachLine
            val latitude = parts.getOrNull(1)?.toDoubleOrNull()
            val longitude = parts.getOrNull(2)?.toDoubleOrNull()
            coordinates[id] = if (latitude != null && longitude != null) latitude to longitude else null
        }
        if (namesFile.exists()) namesFile.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size == 3) names[parts[0]] = parts[1].ifEmpty { null } to parts[2].ifEmpty { null }
        }
    }

    private fun save() {
        coordinatesFile.writeText(buildString {
            coordinates.forEach { (id, point) -> append(id).append('\t').append(point?.first ?: "").append('\t').append(point?.second ?: "").append('\n') }
        })
        namesFile.writeText(buildString {
            names.forEach { (cell, name) -> append(cell).append('\t').append(name.first.orEmpty()).append('\t').append(name.second.orEmpty()).append('\n') }
        })
    }

    private companion object {
        const val BATCH = 200
        val ISO_6709 = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")
    }
}

// A city and every photo taken in it, oldest first like every list in this app.
data class LocationGroup(val city: String, val country: String?, val items: List<MediaItem>) {
    val key: String get() = "$city|${country.orEmpty()}"
    val cover: MediaItem get() = items.last()
}

fun groupByCity(library: List<MediaItem>, places: Map<Long, Place>): List<LocationGroup> =
    library
        .mapNotNull { item -> places[item.id]?.city?.let { city -> Triple(city, places[item.id]?.country, item) } }
        .groupBy { it.first to it.second }
        .map { (place, entries) -> LocationGroup(place.first, place.second, entries.map { it.third }) }
        .sortedByDescending { it.items.size }
