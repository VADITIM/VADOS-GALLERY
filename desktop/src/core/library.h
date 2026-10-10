#pragma once

#include "mediaItem.h"

#include <QFileSystemWatcher>
#include <QFutureWatcher>
#include <QHash>
#include <QObject>
#include <QSet>
#include <QTimer>
#include <QVector>
#include <QtQml/qqmlregistration.h>

class QQmlEngine;
class QJSEngine;
class PrivateVault;

// What the index remembers of a file between runs, so a second start reads no EXIF and starts no ffprobe.
struct CachedFacts {
    qint64 size = 0;
    qint64 modified = 0;
    qint64 timestamp = 0;
    int width = 0;
    int height = 0;
    qint64 durationMs = 0;
    double latitude = 0;
    double longitude = 0;
    bool hasLocation = false;
    bool isProbed = false;
    bool isMotion = false;
};

// Everything the gallery shows, in one place: every photo and video under the library folders (the desktop's MediaStore), its albums, the trash, and lists of files opened from other apps.
// Every grid reads it through a source key: "recent", "favorites", "album:<folder>", "trash", "list:<n>", and Private's own (privateVault.h).
class Library : public QObject {
    Q_OBJECT
    QML_ELEMENT
    QML_SINGLETON

    Q_PROPERTY(int revision READ revision NOTIFY changed)
    Q_PROPERTY(bool isScanning READ isScanning NOTIFY scanningChanged)
    Q_PROPERTY(bool isReady READ isReady NOTIFY scanningChanged)
    Q_PROPERTY(int count READ count NOTIFY changed)
    Q_PROPERTY(int trashCount READ trashCount NOTIFY changed)
    Q_PROPERTY(QStringList roots READ roots NOTIFY rootsChanged)
    Q_PROPERTY(QString newAlbumParent READ newAlbumParent NOTIFY rootsChanged)

public:
    static Library *instance();
    static Library *create(QQmlEngine *, QJSEngine *);

    void start();
    void setVault(PrivateVault *vault) { m_vault = vault; }

    int revision() const { return m_revision; }
    bool isScanning() const { return m_isScanning; }
    bool isReady() const { return m_isReady; }
    int count() const { return int(m_items.size()); }
    int trashCount() const { return int(m_trash.size()); }
    QStringList roots() const { return m_roots; }
    QString newAlbumParent() const;

    // The items of a source, oldest first, the order of every grid.
    QVector<MediaItem> itemsFor(const QString &source) const;
    const MediaItem *find(const QString &path) const;

    Q_INVOKABLE QVariantList albums() const;
    // Every place photos were taken, by city, most photos first: [{ key: "location:…", name, country, count, cover, … }].
    Q_INVOKABLE QVariantList locations() const;
    Q_INVOKABLE QVariantMap placeOf(const QString &path) const;
    // The albums made inside Favorites, as covers: only their photos that are still favourites, and none left empty.
    Q_INVOKABLE QVariantList favoriteAlbums() const;
    Q_INVOKABLE QVariantMap album(const QString &folder) const;
    Q_INVOKABLE int countFor(const QString &source) const;
    Q_INVOKABLE QStringList pathsFor(const QString &source) const;
    Q_INVOKABLE QVariantMap item(const QString &path) const;
    Q_INVOKABLE QString displayName(const QString &folder) const;
    Q_INVOKABLE bool isAlbum(const QString &folder) const { return m_folders.contains(folder); }
    Q_INVOKABLE QString originalAlbumName(const QString &trashPath) const;
    // A file handed in by another app, with the files it should be swiped between; answers the source to open it in.
    Q_INVOKABLE QString openFiles(const QString &path, const QStringList &siblings);
    Q_INVOKABLE void addLibraryFolder(const QString &folder);
    Q_INVOKABLE void removeLibraryFolder(const QString &folder);
    Q_INVOKABLE void refresh();

    // Called by MediaActions once a change on disk is done, so the grids follow at once rather than waiting for the watcher.
    void applyFavorite(const QStringList &paths, bool isFavorite);
    void rescan(const QStringList &folders);
    void rescanTrash();
    void forget(const QStringList &paths);
    void notifyChanged();

signals:
    void changed();
    void scanningChanged();
    void rootsChanged();

private:
    struct ScanResult {
        QVector<MediaItem> items;
        QHash<QString, CachedFacts> facts;
        QStringList folders;
        QStringList scannedRoots;
    };

    void readRoots();
    void scan(const QStringList &folders, bool isFull);
    void onScanned();
    void probeVideos();
    void onProbed();
    void rebuildIndexes();
    void loadCache();
    void saveCache();
    void watchFolders(const QStringList &folders);
    QVector<MediaItem> readTrash() const;
    static ScanResult scanWorker(QStringList folders, QHash<QString, CachedFacts> cache);

    QStringList m_roots;
    QVector<MediaItem> m_items;
    QVector<MediaItem> m_trash;
    QHash<QString, int> m_indexOfPath;
    // Every folder holding photos, with how many and the newest; the albums are made from this.
    QHash<QString, QVector<int>> m_folders;
    QHash<QString, CachedFacts> m_cache;
    QVector<QVector<MediaItem>> m_lists;

    QFileSystemWatcher m_watcher;
    QSet<QString> m_pendingFolders;
    QTimer m_rescanTimer;
    QTimer m_saveTimer;
    QFutureWatcher<ScanResult> m_scanWatcher;
    QStringList m_queuedFolders;
    bool m_isQueuedFull = false;
    QFutureWatcher<QVector<MediaItem>> m_probeWatcher;
    PrivateVault *m_vault = nullptr;

    int m_revision = 0;
    bool m_isScanning = false;
    bool m_isReady = false;

private:
    // Private, so the engine takes the shared instance from create() instead of constructing one of its own.
    explicit Library(QObject *parent = nullptr);
};
