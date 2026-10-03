package com.vaditim.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaditim.gallery.access.AccessState
import com.vaditim.gallery.access.StorageAccess
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MediaRepository
import com.vaditim.gallery.media.groupIntoAlbums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MediaRepository(application.contentResolver)

    private val mutableAccess = MutableStateFlow(StorageAccess.read(application))
    val access: StateFlow<AccessState> = mutableAccess

    @OptIn(ExperimentalCoroutinesApi::class)
    val library: StateFlow<List<MediaItem>> =
        mutableAccess
            .map { it.hasFileAccess }
            .distinctUntilChanged()
            .flatMapLatest { hasFileAccess -> if (hasFileAccess) repository.observeLibrary() else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> =
        library
            .map { groupIntoAlbums(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<MediaItem>> =
        library
            .map { items -> items.filter { it.isFavorite } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Both permissions are granted on a settings page outside the app, so they are re-read every time the app comes back to the front.
    fun refreshAccess() {
        mutableAccess.value = StorageAccess.read(getApplication())
    }
}
