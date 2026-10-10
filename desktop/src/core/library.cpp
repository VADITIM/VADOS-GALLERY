#include "library.h"

#include "mediaFacts.h"
#include "placeNames.h"
#include "privateVault.h"
#include "settings.h"
#include "similarShots.h"

#include <QDataStream>
#include <QDateTime>
#include <QDir>
#include <QDirIterator>
#include <QFile>
#include <QFileInfo>
#include <QJSEngine>
#include <QSaveFile>
#include <QStandardPaths>
#include <QStorageInfo>
#include <QUrl>
#include <QtConcurrent/QtConcurrentMap>
#include <QtConcurrent/QtConcurrentRun>

#include <algorithm>
#include <unistd.h>

// The index on disk; found by the QHash streaming operators, so it stands outside the anonymous namespace.
QDataStream &operator<<(QDataStream &stream, const CachedFacts &facts)
{
    return stream << facts.size << facts.modified << facts.timestamp << facts.width << facts.height << facts.durationMs
                  << facts.latitude << facts.longitude << facts.hasLocation << facts.isProbed << facts.isMotion;
}

QDataStream &operator>>(QDataStream &stream, CachedFacts &facts)
{
    return stream >> facts.size >> facts.modified >> facts.timestamp >> facts.width >> facts.height >> facts.durationMs
                  >> facts.latitude >> facts.longitude >> facts.hasLocation >> facts.isProbed >> facts.isMotion;
}

namespace {

constexpr quint32 CACHE_MAGIC = 0x56474932; // "VGI2"
constexpr int RESCAN_DELAY_MS = 350;
constexpr int SAVE_DELAY_MS = 2000;

QString cachePath()
{
    return QStandardPaths::writableLocation(QStandardPaths::GenericCacheLocation) + QStringLiteral("/vados/gallery/index.dat");
}

bool isOlder(const MediaItem &left, const MediaItem &right)
{
    if (left.timestamp != right.timestamp)
        return left.timestamp < right.timestamp;
    return left.path < right.path;
}

bool isUnder(const QString &path, const QString &folder)
{
    return path == folder || path.startsWith(folder + QLatin1Char('/'));
}

CachedFacts factsOf(const MediaItem &item, bool isProbed)
{
    CachedFacts facts;
    facts.size = item.size;
    facts.modified = item.modified;
    facts.timestamp = item.timestamp;
    facts.width = item.width;
    facts.height = item.height;
    facts.durationMs = item.durationMs;
    facts.hasLocation = item.hasLocation();
    facts.latitude = facts.hasLocation ? item.latitude : 0;
    facts.longitude = facts.hasLocation ? item.longitude : 0;
    facts.isProbed = isProbed;
    facts.isMotion = item.isMotion;
    return facts;
}

MediaItem itemFrom(const QFileInfo &info, const QHash<QString, CachedFacts> &cache, QHash<QString, CachedFacts> *learned)
{
    const QString path = info.absoluteFilePath();
    const auto cached = cache.constFind(path);
    if (cached != cache.cend() && cached->size == info.size() && cached->modified == info.lastModified().toMSecsSinceEpoch()) {
        MediaItem item;
        item.path = path;
        item.folder = info.absolutePath();
        item.size = cached->size;
        item.modified = cached->modified;
        item.timestamp = cached->timestamp;
        item.width = cached->width;
        item.height = cached->height;
        item.durationMs = cached->durationMs;
        item.isVideo = MediaFacts::isVideo(info.fileName());
        item.isMotion = cached->isMotion;
        if (cached->hasLocation) {
            item.latitude = cached->latitude;
            item.longitude = cached->longitude;
        }
        item.isFavorite = MediaFacts::readFavorite(path);
        if (learned)
            learned->insert(path, *cached);
        return item;
    }
    MediaItem item = MediaFacts::read(info);
    if (learned)
        learned->insert(path, factsOf(item, !item.isVideo));
    return item;
}

// The trashes that can hold something from the library: the home one, and the one at the top of every other disk a library folder is on (freedesktop trash spec).
QStringList trashFolders(const QStringList &roots)
{
    const QString home = QStandardPaths::writableLocation(QStandardPaths::GenericDataLocation) + QStringLiteral("/Trash");
    QStringList folders = {home};
    const QString homeDevice = QStorageInfo(QDir::homePath()).device();
    for (const QString &root : roots) {
        const QStorageInfo storage(root);
        if (!storage.isValid() || storage.device() == homeDevice)
            continue;
        const QString topdir = storage.rootPath() + QStringLiteral("/.Trash-") + QString::number(getuid());
        if (!folders.contains(topdir) && QFileInfo::exists(topdir))
            folders.append(topdir);
    }
    return folders;
}

QHash<QString, QString> readTrashInfo(const QString &infoPath)
{
    QHash<QString, QString> values;
    QFile file(infoPath);
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
        return values;
    while (!file.atEnd()) {
        const QString line = QString::fromUtf8(file.readLine()).trimmed();
        const int equals = line.indexOf(QLatin1Char('='));
        if (equals > 0)
            values.insert(line.left(equals), line.mid(equals + 1));
    }
    return values;
}

} // namespace

// #region ── lifecycle ───────────────────────────────────────────────────────────────────────

Library::Library(QObject *parent)
    : QObject(parent)
{
    m_rescanTimer.setSingleShot(true);
    m_rescanTimer.setInterval(RESCAN_DELAY_MS);
    connect(&m_rescanTimer, &QTimer::timeout, this, [this] {
        const QStringList folders(m_pendingFolders.cbegin(), m_pendingFolders.cend());
        m_pendingFolders.clear();
        QStringList libraryFolders;
        bool isTrashTouched = false;
        for (const QString &folder : folders) {
            if (folder.contains(QStringLiteral("/Trash")) || folder.contains(QStringLiteral("/.Trash-")))
                isTrashTouched = true;
            else
                libraryFolders.append(folder);
        }
        if (isTrashTouched)
            rescanTrash();
        if (!libraryFolders.isEmpty())
            rescan(libraryFolders);
    });
    m_saveTimer.setSingleShot(true);
    m_saveTimer.setInterval(SAVE_DELAY_MS);
    connect(&m_saveTimer, &QTimer::timeout, this, &Library::saveCache);
    connect(&m_watcher, &QFileSystemWatcher::directoryChanged, this, [this](const QString &folder) {
        m_pendingFolders.insert(folder);
        m_rescanTimer.start();
    });
    connect(&m_scanWatcher, &QFutureWatcher<ScanResult>::finished, this, &Library::onScanned);
    connect(&m_probeWatcher, &QFutureWatcher<QVector<MediaItem>>::finished, this, &Library::onProbed);
}

Library *Library::instance()
{
    static Library *library = new Library;
    return library;
}

Library *Library::create(QQmlEngine *, QJSEngine *engine)
{
    engine->setObjectOwnership(instance(), QJSEngine::CppOwnership);
    return instance();
}

void Library::start()
{
    readRoots();
    loadCache();
    m_trash = readTrash();
    scan(m_roots, true);
}

void Library::readRoots()
{
    QStringList roots = Settings::instance()->libraryFolders();
    const QString fromEnvironment = qEnvironmentVariable("VADOS_GALLERY_ROOTS");
    if (!fromEnvironment.isEmpty())
        roots = fromEnvironment.split(QLatin1Char(':'), Qt::SkipEmptyParts);
    if (roots.isEmpty()) {
        // The desktop's own picture and video folders, the way the phone's library is DCIM and Pictures.
        for (const auto location : {QStandardPaths::PicturesLocation, QStandardPaths::MoviesLocation}) {
            const QString folder = QStandardPaths::writableLocation(location);
            if (!folder.isEmpty() && folder != QDir::homePath() && QFileInfo(folder).isDir() && !roots.contains(folder))
                roots.append(folder);
        }
    }
    for (QString &root : roots)
        root = QDir::cleanPath(QDir(root).absolutePath());
    if (roots != m_roots) {
        m_roots = roots;
        emit rootsChanged();
    }
}

QString Library::newAlbumParent() const
{
    const QString pictures = QStandardPaths::writableLocation(QStandardPaths::PicturesLocation);
    for (const QString &root : m_roots)
        if (root == pictures)
            return root;
    return m_roots.isEmpty() ? pictures : m_roots.first();
}

void Library::addLibraryFolder(const QString &folder)
{
    QStringList folders = m_roots;
    const QString clean = QDir::cleanPath(folder);
    if (folders.contains(clean))
        return;
    folders.append(clean);
    Settings::instance()->setLibraryFolders(folders);
    readRoots();
    scan(m_roots, true);
}

void Library::removeLibraryFolder(const QString &folder)
{
    QStringList folders = m_roots;
    folders.removeAll(folder);
    Settings::instance()->setLibraryFolders(folders);
    readRoots();
    scan(m_roots, true);
}

void Library::refresh()
{
    m_trash = readTrash();
    scan(m_roots, true);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── scanning ────────────────────────────────────────────────────────────────────────

Library::ScanResult Library::scanWorker(QStringList folders, QHash<QString, CachedFacts> cache)
{
    ScanResult result;
    result.scannedRoots = folders;
    QSet<QString> seen;
    for (const QString &folder : folders) {
        if (!QFileInfo(folder).isDir())
            continue;
        result.folders.append(folder);
        // Hidden folders are never entered: thumbnails, app data, and Private, which is hidden on purpose.
        QDirIterator iterator(folder, QDir::Files | QDir::Dirs | QDir::NoDotAndDotDot | QDir::NoSymLinks, QDirIterator::Subdirectories);
        while (iterator.hasNext()) {
            iterator.next();
            const QFileInfo info = iterator.fileInfo();
            if (info.isDir()) {
                result.folders.append(info.absoluteFilePath());
                continue;
            }
            if (!MediaFacts::isMedia(info.fileName()) || seen.contains(info.absoluteFilePath()))
                continue;
            seen.insert(info.absoluteFilePath());
            result.items.append(itemFrom(info, cache, &result.facts));
        }
    }
    return result;
}

void Library::scan(const QStringList &folders, bool isFull)
{
    if (m_scanWatcher.isRunning()) {
        for (const QString &folder : folders)
            if (!m_queuedFolders.contains(folder))
                m_queuedFolders.append(folder);
        m_isQueuedFull = m_isQueuedFull || isFull;
        return;
    }
    if (!m_isScanning) {
        m_isScanning = true;
        emit scanningChanged();
    }
    m_scanWatcher.setProperty("isFull", isFull);
    m_scanWatcher.setFuture(QtConcurrent::run(&Library::scanWorker, folders, m_cache));
}

void Library::onScanned()
{
    const ScanResult result = m_scanWatcher.result();
    const bool isFull = m_scanWatcher.property("isFull").toBool();
    if (isFull) {
        m_items = result.items;
    } else {
        m_items.erase(std::remove_if(m_items.begin(), m_items.end(), [&](const MediaItem &item) {
            for (const QString &folder : result.scannedRoots)
                if (isUnder(item.path, folder))
                    return true;
            return false;
        }), m_items.end());
        m_items.append(result.items);
    }
    for (auto fact = result.facts.cbegin(); fact != result.facts.cend(); ++fact)
        m_cache.insert(fact.key(), fact.value());
    std::sort(m_items.begin(), m_items.end(), isOlder);
    rebuildIndexes();
    watchFolders(result.folders);
    m_saveTimer.start();

    if (!m_queuedFolders.isEmpty()) {
        const QStringList queued = m_queuedFolders;
        const bool isQueuedFull = m_isQueuedFull;
        m_queuedFolders.clear();
        m_isQueuedFull = false;
        notifyChanged();
        scan(isQueuedFull ? m_roots : queued, isQueuedFull);
        return;
    }
    m_isScanning = false;
    m_isReady = true;
    emit scanningChanged();
    notifyChanged();
    probeVideos();
    SimilarShots::instance()->index(m_items);
}

void Library::rescan(const QStringList &folders)
{
    QStringList inside;
    for (const QString &folder : folders)
        for (const QString &root : m_roots)
            if (isUnder(folder, root) && !inside.contains(folder))
                inside.append(folder);
    if (!inside.isEmpty())
        scan(inside, false);
}

void Library::watchFolders(const QStringList &folders)
{
    QStringList fresh;
    const QStringList watched = m_watcher.directories();
    for (const QString &folder : folders)
        if (!watched.contains(folder))
            fresh.append(folder);
    if (!fresh.isEmpty())
        m_watcher.addPaths(fresh);
}

// Videos are measured after the grid is up: their length and date come from a process each, which a first look should not wait on.
void Library::probeVideos()
{
    if (m_probeWatcher.isRunning())
        return;
    QVector<MediaItem> videos;
    for (const MediaItem &item : std::as_const(m_items))
        if (item.isVideo && !m_cache.value(item.path).isProbed)
            videos.append(item);
    if (videos.isEmpty())
        return;
    m_probeWatcher.setFuture(QtConcurrent::run([videos]() mutable {
        QVector<MediaItem> probed = QtConcurrent::blockingMapped(videos, [](MediaItem item) {
            MediaFacts::probeVideo(item);
            return item;
        });
        return probed;
    }));
}

void Library::onProbed()
{
    const QVector<MediaItem> probed = m_probeWatcher.result();
    for (const MediaItem &video : probed) {
        m_cache.insert(video.path, factsOf(video, true));
        const auto index = m_indexOfPath.constFind(video.path);
        if (index == m_indexOfPath.cend())
            continue;
        MediaItem &item = m_items[*index];
        item.durationMs = video.durationMs;
        item.timestamp = video.timestamp;
        item.width = video.width;
        item.height = video.height;
        item.latitude = video.latitude;
        item.longitude = video.longitude;
    }
    std::sort(m_items.begin(), m_items.end(), isOlder);
    rebuildIndexes();
    m_saveTimer.start();
    notifyChanged();
}

void Library::rebuildIndexes()
{
    m_indexOfPath.clear();
    m_folders.clear();
    m_indexOfPath.reserve(m_items.size());
    for (int index = 0; index < m_items.size(); ++index) {
        m_indexOfPath.insert(m_items.at(index).path, index);
        m_folders[m_items.at(index).folder].append(index);
    }
}

void Library::notifyChanged()
{
    ++m_revision;
    emit changed();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── cache ───────────────────────────────────────────────────────────────────────────

void Library::loadCache()
{
    QFile file(cachePath());
    if (!file.open(QIODevice::ReadOnly))
        return;
    QDataStream stream(&file);
    quint32 magic = 0;
    stream >> magic;
    if (magic != CACHE_MAGIC)
        return;
    stream >> m_cache;
}

void Library::saveCache()
{
    QDir().mkpath(QFileInfo(cachePath()).absolutePath());
    // Only what still exists is kept, so the index does not grow with every file ever seen.
    QHash<QString, CachedFacts> kept;
    for (const MediaItem &item : std::as_const(m_items))
        if (m_cache.contains(item.path))
            kept.insert(item.path, m_cache.value(item.path));
    for (const MediaItem &item : std::as_const(m_trash))
        if (m_cache.contains(item.path))
            kept.insert(item.path, m_cache.value(item.path));
    QSaveFile file(cachePath());
    if (!file.open(QIODevice::WriteOnly))
        return;
    QDataStream stream(&file);
    stream << CACHE_MAGIC << kept;
    file.commit();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── trash ───────────────────────────────────────────────────────────────────────────

QVector<MediaItem> Library::readTrash() const
{
    QVector<MediaItem> items;
    for (const QString &trash : trashFolders(m_roots)) {
        const QString topdir = trash.endsWith(QStringLiteral("/Trash")) ? QString() : QFileInfo(trash).absolutePath();
        const QFileInfoList files = QDir(trash + QStringLiteral("/files")).entryInfoList(QDir::Files | QDir::Hidden | QDir::NoDotAndDotDot);
        for (const QFileInfo &info : files) {
            if (!MediaFacts::isMedia(info.fileName()))
                continue;
            MediaItem item = itemFrom(info, m_cache, nullptr);
            const auto values = readTrashInfo(trash + QStringLiteral("/info/") + info.fileName() + QStringLiteral(".trashinfo"));
            QString original = QUrl::fromPercentEncoding(values.value(QStringLiteral("Path")).toUtf8());
            if (!original.isEmpty() && !original.startsWith(QLatin1Char('/')) && !topdir.isEmpty())
                original = topdir + QLatin1Char('/') + original;
            item.originalPath = original;
            item.folder = original.isEmpty() ? QString() : QFileInfo(original).absolutePath();
            const QDateTime deleted = QDateTime::fromString(values.value(QStringLiteral("DeletionDate")), Qt::ISODate);
            item.trashedAt = deleted.isValid() ? deleted.toMSecsSinceEpoch() : item.modified;
            items.append(item);
        }
    }
    std::sort(items.begin(), items.end(), isOlder);
    return items;
}

void Library::rescanTrash()
{
    m_trash = readTrash();
    for (const QString &trash : trashFolders(m_roots))
        if (!m_watcher.directories().contains(trash + QStringLiteral("/files")) && QFileInfo::exists(trash + QStringLiteral("/files")))
            m_watcher.addPath(trash + QStringLiteral("/files"));
    notifyChanged();
}

QString Library::originalAlbumName(const QString &trashPath) const
{
    for (const MediaItem &item : m_trash)
        if (item.path == trashPath)
            return item.folder.isEmpty() ? QString() : displayName(item.folder);
    return {};
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── reading ─────────────────────────────────────────────────────────────────────────

QVector<MediaItem> Library::itemsFor(const QString &source) const
{
    if (source == QLatin1String("recent"))
        return m_items;
    if (source == QLatin1String("favorites")) {
        QVector<MediaItem> favorites;
        for (const MediaItem &item : m_items)
            if (item.isFavorite)
                favorites.append(item);
        return favorites;
    }
    if (source.startsWith(QLatin1String("album:"))) {
        QVector<MediaItem> items;
        for (const int index : m_folders.value(source.mid(6)))
            items.append(m_items.at(index));
        return items;
    }
    if (source == QLatin1String("trash")) {
        if (!Settings::instance()->isTrashByDeletion())
            return m_trash;
        // Ordered by deletion, the trash takes the day each photo went there as its date, so its order, headers and timeline follow it.
        QVector<MediaItem> items = m_trash;
        for (MediaItem &item : items)
            item.timestamp = item.trashedAt;
        std::sort(items.begin(), items.end(), isOlder);
        return items;
    }
    if (source.startsWith(QLatin1String("list:"))) {
        const int index = source.mid(5).toInt();
        if (index < 0 || index >= m_lists.size())
            return {};
        QVector<MediaItem> items;
        // A file trashed or moved away while its list is open leaves it.
        for (const MediaItem &item : m_lists.at(index)) {
            const MediaItem *known = find(item.path);
            if (known)
                items.append(*known);
            else if (QFileInfo::exists(item.path))
                items.append(item);
        }
        return items;
    }
    if (source.startsWith(QLatin1String("location:"))) {
        const QString key = source.mid(9);
        QVector<MediaItem> items;
        for (const MediaItem &item : m_items)
            if (item.hasLocation() && PlaceNames::shared().placeOf(item.latitude, item.longitude).key == key)
                items.append(item);
        return items;
    }
    if (source.startsWith(QLatin1String("favorite-album:"))) {
        const QString name = source.mid(15);
        QSet<QString> paths;
        for (const QVariant &album : Settings::instance()->favoriteAlbums())
            if (album.toMap().value(QStringLiteral("name")).toString() == name)
                for (const QVariant &path : album.toMap().value(QStringLiteral("paths")).toList())
                    paths.insert(path.toString());
        QVector<MediaItem> items;
        for (const MediaItem &item : m_items)
            if (item.isFavorite && paths.contains(item.path))
                items.append(item);
        return items;
    }
    if (source.startsWith(QLatin1String("private")) && m_vault)
        return m_vault->itemsFor(source);
    return {};
}

const MediaItem *Library::find(const QString &path) const
{
    const auto index = m_indexOfPath.constFind(path);
    return index == m_indexOfPath.cend() ? nullptr : &m_items.at(*index);
}

int Library::countFor(const QString &source) const
{
    if (source == QLatin1String("recent"))
        return int(m_items.size());
    if (source.startsWith(QLatin1String("album:")))
        return int(m_folders.value(source.mid(6)).size());
    return int(itemsFor(source).size());
}

QStringList Library::pathsFor(const QString &source) const
{
    QStringList paths;
    for (const MediaItem &item : itemsFor(source))
        paths.append(item.path);
    return paths;
}

QVariantMap Library::item(const QString &path) const
{
    if (const MediaItem *known = find(path))
        return known->toVariant();
    for (const MediaItem &trashed : m_trash)
        if (trashed.path == path)
            return trashed.toVariant();
    if (m_vault) {
        const QVariantMap hidden = m_vault->item(path);
        if (!hidden.isEmpty())
            return hidden;
    }
    const QFileInfo info(path);
    return info.exists() ? MediaFacts::read(info).toVariant() : QVariantMap();
}

QString Library::displayName(const QString &folder) const
{
    const QString renamed = Settings::instance()->albumName(folder);
    if (!renamed.isEmpty())
        return renamed;
    return folder.section(QLatin1Char('/'), -1);
}

QVariantMap Library::album(const QString &folder) const
{
    const QVector<int> indexes = m_folders.value(folder);
    if (indexes.isEmpty())
        return {};
    const MediaItem &newest = m_items.at(indexes.last());
    QString cover = Settings::instance()->cover(QStringLiteral("album:") + folder);
    bool isCoverVideo = newest.isVideo;
    // A chosen cover counts only while it is still in the album.
    const MediaItem *chosen = cover.isEmpty() ? nullptr : find(cover);
    if (chosen && chosen->folder == folder)
        isCoverVideo = chosen->isVideo;
    else
        cover = newest.path;
    return {
        {QStringLiteral("key"), QStringLiteral("album:") + folder},
        {QStringLiteral("folder"), folder},
        {QStringLiteral("name"), displayName(folder)},
        {QStringLiteral("folderName"), folder.section(QLatin1Char('/'), -1)},
        {QStringLiteral("count"), int(indexes.size())},
        {QStringLiteral("cover"), cover},
        {QStringLiteral("isCoverVideo"), isCoverVideo},
        {QStringLiteral("newest"), newest.timestamp},
    };
}

QVariantList Library::albums() const
{
    QList<QVariantMap> albums;
    for (auto folder = m_folders.cbegin(); folder != m_folders.cend(); ++folder)
        albums.append(album(folder.key()));
    // Camera first, then everything else by its most recent photo.
    const auto isCamera = [](const QVariantMap &album) {
        const QString name = album.value(QStringLiteral("folderName")).toString().toLower();
        return name == QLatin1String("camera") || name == QLatin1String("dcim");
    };
    std::sort(albums.begin(), albums.end(), [&](const QVariantMap &left, const QVariantMap &right) {
        if (isCamera(left) != isCamera(right))
            return isCamera(left);
        return left.value(QStringLiteral("newest")).toLongLong() > right.value(QStringLiteral("newest")).toLongLong();
    });
    // Albums the user dragged into an order come in that order; ones never arranged keep the default order after them.
    const QStringList order = Settings::instance()->albumOrder();
    std::stable_sort(albums.begin(), albums.end(), [&](const QVariantMap &left, const QVariantMap &right) {
        const qsizetype leftAt = order.indexOf(left.value(QStringLiteral("folder")).toString());
        const qsizetype rightAt = order.indexOf(right.value(QStringLiteral("folder")).toString());
        const qsizetype leftRank = leftAt < 0 ? order.size() : leftAt;
        const qsizetype rightRank = rightAt < 0 ? order.size() : rightAt;
        return leftRank < rightRank;
    });
    QVariantList list;
    for (const QVariantMap &album : albums)
        list.append(album);
    return list;
}

QVariantList Library::locations() const
{
    struct Gathered {
        PlaceNames::Place place;
        int count = 0;
        const MediaItem *newest = nullptr;
    };
    QHash<QString, Gathered> places;
    for (const MediaItem &item : m_items) {
        if (!item.hasLocation())
            continue;
        const PlaceNames::Place place = PlaceNames::shared().placeOf(item.latitude, item.longitude);
        Gathered &gathered = places[place.key];
        gathered.place = place;
        ++gathered.count;
        gathered.newest = &item;
    }
    QList<Gathered> sorted = places.values();
    std::sort(sorted.begin(), sorted.end(), [](const Gathered &left, const Gathered &right) { return left.count > right.count; });
    QVariantList list;
    for (const Gathered &gathered : sorted)
        list.append(QVariantMap{
            {QStringLiteral("key"), QStringLiteral("location:") + gathered.place.key},
            {QStringLiteral("folder"), gathered.place.key},
            {QStringLiteral("name"), gathered.place.city},
            {QStringLiteral("country"), gathered.place.country},
            {QStringLiteral("count"), gathered.count},
            {QStringLiteral("cover"), gathered.newest->path},
            {QStringLiteral("isCoverVideo"), gathered.newest->isVideo},
            {QStringLiteral("newest"), gathered.newest->timestamp},
            {QStringLiteral("isLocation"), true},
        });
    return list;
}

QVariantMap Library::placeOf(const QString &path) const
{
    const MediaItem *item = find(path);
    if (!item || !item->hasLocation())
        return {};
    const PlaceNames::Place place = PlaceNames::shared().placeOf(item->latitude, item->longitude);
    return {{QStringLiteral("city"), place.city}, {QStringLiteral("country"), place.country},
            {QStringLiteral("latitude"), item->latitude}, {QStringLiteral("longitude"), item->longitude}};
}

QVariantList Library::favoriteAlbums() const
{
    QList<QVariantMap> covers;
    for (const QVariant &stored : Settings::instance()->favoriteAlbums()) {
        const QString name = stored.toMap().value(QStringLiteral("name")).toString();
        const QString key = QStringLiteral("favorite-album:") + name;
        const QVector<MediaItem> items = itemsFor(key);
        if (items.isEmpty())
            continue;
        QString cover = Settings::instance()->cover(key);
        bool isCoverVideo = items.last().isVideo;
        bool isChosen = false;
        for (const MediaItem &item : items)
            if (item.path == cover) {
                isChosen = true;
                isCoverVideo = item.isVideo;
            }
        if (!isChosen)
            cover = items.last().path;
        covers.append({{QStringLiteral("key"), key}, {QStringLiteral("folder"), name}, {QStringLiteral("name"), name},
                       {QStringLiteral("count"), int(items.size())}, {QStringLiteral("cover"), cover}, {QStringLiteral("isCoverVideo"), isCoverVideo},
                       {QStringLiteral("newest"), items.last().timestamp}, {QStringLiteral("isFavoriteAlbum"), true}});
    }
    const QStringList order = Settings::instance()->order(QStringLiteral("favorites"));
    std::stable_sort(covers.begin(), covers.end(), [&](const QVariantMap &left, const QVariantMap &right) {
        const qsizetype leftAt = order.indexOf(left.value(QStringLiteral("name")).toString());
        const qsizetype rightAt = order.indexOf(right.value(QStringLiteral("name")).toString());
        return (leftAt < 0 ? order.size() : leftAt) < (rightAt < 0 ? order.size() : rightAt);
    });
    QVariantList list;
    for (const QVariantMap &cover : covers)
        list.append(cover);
    return list;
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── changes ─────────────────────────────────────────────────────────────────────────

void Library::applyFavorite(const QStringList &paths, bool isFavorite)
{
    for (const QString &path : paths) {
        const auto index = m_indexOfPath.constFind(path);
        if (index != m_indexOfPath.cend())
            m_items[*index].isFavorite = isFavorite;
        for (QVector<MediaItem> &list : m_lists)
            for (MediaItem &item : list)
                if (item.path == path)
                    item.isFavorite = isFavorite;
    }
    notifyChanged();
}

void Library::forget(const QStringList &paths)
{
    const QSet<QString> gone(paths.cbegin(), paths.cend());
    m_items.erase(std::remove_if(m_items.begin(), m_items.end(), [&](const MediaItem &item) { return gone.contains(item.path); }), m_items.end());
    rebuildIndexes();
    notifyChanged();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── files from other apps ───────────────────────────────────────────────────────────

QString Library::openFiles(const QString &path, const QStringList &siblings)
{
    const QFileInfo opened(path);
    const QString absolute = opened.absoluteFilePath();
    // Opened without a list, a photo already in an album opens inside it, so swiping reaches its neighbours.
    if (siblings.isEmpty() && find(absolute))
        return QStringLiteral("album:") + opened.absolutePath();
    QStringList paths;
    if (siblings.isEmpty()) {
        const QFileInfoList entries = QDir(opened.absolutePath()).entryInfoList(QDir::Files | QDir::NoDotAndDotDot);
        for (const QFileInfo &entry : entries)
            if (MediaFacts::isMedia(entry.fileName()))
                paths.append(entry.absoluteFilePath());
    } else {
        for (const QString &sibling : siblings)
            if (MediaFacts::isMedia(sibling))
                paths.append(QFileInfo(sibling).absoluteFilePath());
    }
    if (!paths.contains(absolute))
        paths.append(absolute);
    QVector<MediaItem> list;
    list.reserve(paths.size());
    for (const QString &each : paths) {
        if (const MediaItem *known = find(each))
            list.append(*known);
        else
            list.append(itemFrom(QFileInfo(each), m_cache, nullptr));
    }
    // A folder listed by this app goes in the gallery's own order; a list handed over by a file manager keeps the order it was shown in there.
    if (siblings.isEmpty())
        std::sort(list.begin(), list.end(), isOlder);
    m_lists.append(list);
    return QStringLiteral("list:") + QString::number(m_lists.size() - 1);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
