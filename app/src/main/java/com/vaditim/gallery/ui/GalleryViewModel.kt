package com.vaditim.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaditim.gallery.access.AccessState
import com.vaditim.gallery.access.StorageAccess
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.CoverStore
import com.vaditim.gallery.media.LocationGroup
import com.vaditim.gallery.media.LocationIndex
import com.vaditim.gallery.media.Place
import com.vaditim.gallery.media.SamsungTrash
import com.vaditim.gallery.media.groupByCity
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.collectLatest
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MediaRepository
import com.vaditim.gallery.media.groupIntoAlbums
import com.vaditim.gallery.vault.PrivateContents
import com.vaditim.gallery.vault.PrivateVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MediaRepository(application)

    private val mutableAccess = MutableStateFlow(StorageAccess.read(application))
    val access: StateFlow<AccessState> = mutableAccess

    @OptIn(ExperimentalCoroutinesApi::class)
    val library: StateFlow<List<MediaItem>> =
        mutableAccess
            .map { it.hasFileAccess }
            .distinctUntilChanged()
            .flatMapLatest { hasFileAccess -> if (hasFileAccess) repository.observeLibrary() else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val samsungTrash = SamsungTrash(application)

    // Samsung's trash folder sends no change notice, so it is re-read on every MediaStore change, every return to the app and after this app's own restores.
    private val samsungTrashVersion = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trash: StateFlow<List<MediaItem>> =
        mutableAccess
            .map { it.hasFileAccess }
            .distinctUntilChanged()
            .flatMapLatest { hasFileAccess -> if (hasFileAccess) combine(repository.observeTrash(), samsungTrashVersion) { items, _ -> items } else flowOf(emptyList()) }
            .map { items ->
                repository.deleteExpired(items)
                val now = System.currentTimeMillis()
                val samsungItems = runCatching { samsungTrash.read() }.getOrDefault(emptyList())
                (items.filter { it.expiresMillis == 0L || it.expiresMillis > now } + samsungItems).sortedBy { it.timestampMillis }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val coverStore = CoverStore(application)
    private val coverIds = MutableStateFlow(coverStore.read())

    val albums: StateFlow<List<Album>> =
        combine(library, coverIds) { items, covers -> groupIntoAlbums(items, covers) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<MediaItem>> =
        library
            .map { items -> items.filter { it.isFavorite } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val locationIndex = LocationIndex(application)
    private val mutablePlaces = MutableStateFlow<Map<Long, Place>>(emptyMap())
    val places: StateFlow<Map<Long, Place>> = mutablePlaces
    private val canReadLocation = MutableStateFlow(hasLocationPermission())

    val locations: StateFlow<List<LocationGroup>> =
        combine(library, mutablePlaces) { items, places -> groupByCity(items, places) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Re-run whenever the library changes or the location permission arrives; only photos not read before cost anything.
        viewModelScope.launch {
            combine(library, canReadLocation) { items, canRead -> items to canRead }.collectLatest { (items, canRead) ->
                if (items.isNotEmpty()) locationIndex.index(items, canRead) { mutablePlaces.value = it }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        getApplication<Application>().checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun refreshLocationPermission() {
        canReadLocation.value = hasLocationPermission()
    }

    val vault = PrivateVault(application)

    private val mutablePrivate = MutableStateFlow(PrivateContents(emptyList(), emptyList()))
    val privateContents: StateFlow<PrivateContents> = mutablePrivate

    private val mutableIsPrivateUnlocked = MutableStateFlow(false)
    val isPrivateUnlocked: StateFlow<Boolean> = mutableIsPrivateUnlocked

    // Read from disk rather than observed: nothing but this app writes into the private folder, so a re-read after each of its own changes is the whole story.
    fun refreshPrivate() {
        if (!mutableAccess.value.hasFileAccess) return
        viewModelScope.launch { mutablePrivate.value = vault.read() }
    }

    fun setAlbumCover(albumId: Long, item: MediaItem) {
        coverStore.set(albumId, item.id)
        coverIds.value = coverStore.read()
    }

    fun setGroupCover(groupName: String, item: MediaItem) {
        viewModelScope.launch {
            vault.setCover(groupName, item)
            mutablePrivate.value = vault.read()
        }
    }

    fun unlockPrivate() {
        mutableIsPrivateUnlocked.value = true
        refreshPrivate()
    }

    fun lockPrivate() {
        mutableIsPrivateUnlocked.value = false
    }

    // Both permissions are granted on a settings page outside the app, so they are re-read every time the app comes back to the front.
    fun refreshTrash() {
        samsungTrashVersion.value++
    }

    fun refreshAccess() {
        mutableAccess.value = StorageAccess.read(getApplication())
        refreshTrash()
        refreshLocationPermission()
        refreshPrivate()
    }
}
