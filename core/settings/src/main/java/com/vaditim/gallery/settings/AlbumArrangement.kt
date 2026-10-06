package com.vaditim.gallery.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// How the user has arranged what they have: orders, groups, names and the albums made inside Favorites. Apart from Settings, which is how things look.
object AlbumArrangement {
    // Albums by folder path, private groups by name, Favorites albums by name.
    val albumOrder = ArrangedOrder("albumOrder")
    val groupOrder = ArrangedOrder("groupOrder")
    val favoriteAlbumOrder = ArrangedOrder("favoriteAlbumOrder")
    // The album groups of Albums (by folder path) and of Favorites (by album name); kept while grouping is off, so turning it back on restores them.
    val albumStacks = AlbumStacks("albumStacks")
    val favoriteStacks = AlbumStacks("favoriteStacks")
    val favoriteAlbums = FavoriteAlbums("favoriteAlbums", favoriteStacks)
    val albumNames = AlbumNames("albumNames")

    fun init(store: PreferenceStore) {
        listOf(albumOrder, groupOrder, favoriteAlbumOrder, albumStacks, favoriteStacks, favoriteAlbums, albumNames).forEach { it.load(store) }
    }
}

// One stored piece of the arrangement; loaded once at start, written on every change.
abstract class StoredValue {
    protected lateinit var store: PreferenceStore
        private set

    fun load(store: PreferenceStore) {
        this.store = store
        read()
    }

    protected abstract fun read()
}

// An order the user dragged things into; things never arranged keep their default order after the arranged ones.
class ArrangedOrder(private val key: String) : StoredValue() {
    var names by mutableStateOf<List<String>>(emptyList())
        private set

    override fun read() {
        names = store.lines(key)
    }

    fun update(names: List<String>) {
        this.names = names
        store.putLines(key, names)
    }

    fun <T> arrange(items: List<T>, keyOf: (T) -> String): List<T> {
        if (names.isEmpty()) return items
        val positions = names.withIndex().associate { (index, name) -> name to index }
        return items.sortedBy { positions[keyOf(it)] ?: Int.MAX_VALUE }
    }

    // The thing renamed keeps its place.
    fun rename(old: String, new: String) = update(names.map { if (it == old) new else it }.distinct())
}

// Named groups of albums in their order, each holding its albums by key.
class AlbumStacks(private val key: String) : StoredValue() {
    var all by mutableStateOf<List<AlbumStack>>(emptyList())
        private set

    override fun read() {
        all = store.lines(key).map { line ->
            val parts = line.split('\t')
            AlbumStack(parts.first(), parts.drop(1).filter { it.isNotEmpty() })
        }
    }

    fun update(stacks: List<AlbumStack>) {
        all = stacks.filter { it.paths.isNotEmpty() }
        store.putLines(key, all.map { (listOf(it.name) + it.paths).joinToString("\t") })
    }

    fun named(name: String?): AlbumStack? = all.firstOrNull { it.name == name }

    fun holding(albumKey: String): AlbumStack? = all.firstOrNull { albumKey in it.paths }

    fun add(albumKeys: Collection<String>, stackName: String) = update(albumKeys.fold(all) { stacks, albumKey -> stacks.withAlbum(albumKey, stackName) })

    fun remove(albumKeys: Collection<String>) = update(albumKeys.fold(all) { stacks, albumKey -> stacks.withoutAlbum(albumKey) })

    fun delete(name: String?) = update(all.filter { it.name != name })

    fun rename(old: String, new: String) = update(all.renamed(old, new))

    // An album renamed stays in its group under its new name.
    fun renameAlbum(old: String, new: String) = update(all.map { stack -> stack.copy(paths = stack.paths.map { if (it == old) new else it }.distinct()) })
}

// Albums made inside Favorites only, each holding favourited photos by id; they never touch the folders.
class FavoriteAlbums(private val key: String, private val stacks: AlbumStacks) : StoredValue() {
    var all by mutableStateOf<List<FavoriteAlbum>>(emptyList())
        private set

    override fun read() {
        all = store.lines(key).map { line ->
            val parts = line.split('\t')
            FavoriteAlbum(parts.first(), parts.drop(1).mapNotNull { it.toLongOrNull() })
        }
    }

    fun update(albums: List<FavoriteAlbum>) {
        all = albums.filter { it.ids.isNotEmpty() }
        store.putLines(key, all.map { (listOf(it.name) + it.ids.map(Long::toString)).joinToString("\t") })
        // A group keeps only albums that still exist.
        val names = all.map { it.name }.toSet()
        if (stacks.all.any { stack -> stack.paths.any { it !in names } }) stacks.update(stacks.all.map { stack -> stack.copy(paths = stack.paths.filter { it in names }) })
    }

    fun addPhotos(albumName: String, ids: List<Long>) = update(all.withPhotos(albumName, ids))

    fun delete(names: Collection<String>) = update(all.filter { it.name !in names })
}

// An album's name as shown, by folder path; renaming only ever changes this, never the folder, so nothing that saves into it is thrown off.
class AlbumNames(private val key: String) : StoredValue() {
    var byPath by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    override fun read() {
        byPath = store.lines(key).mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }

    // A name equal to the folder's own clears the override, so the album follows the folder again.
    fun rename(path: String, folderName: String, name: String) {
        val clean = AlbumStack.cleanName(name).ifEmpty { return }
        byPath = if (clean == folderName) byPath - path else byPath + (path to clean)
        store.putLines(key, byPath.entries.map { "${it.key}\t${it.value}" })
    }
}
