#include "mediaActions.h"

#include "library.h"
#include "mediaFacts.h"
#include "privateVault.h"
#include "settings.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJSEngine>
#include <QSet>
#include <QStandardPaths>
#include <QStorageInfo>
#include <QRegularExpression>
#include <QUrl>

#include <unistd.h>

namespace {

QString countText(int count)
{
    return count == 1 ? QStringLiteral("1 photo") : QStringLiteral("%1 photos").arg(count);
}

QString trashInfoFor(const QString &trashPath)
{
    // <trash>/files/<name> pairs with <trash>/info/<name>.trashinfo.
    const QFileInfo info(trashPath);
    return info.dir().absolutePath().chopped(5) + QStringLiteral("info/") + info.fileName() + QStringLiteral(".trashinfo");
}

QString originalPathOf(const QString &trashPath)
{
    QFile file(trashInfoFor(trashPath));
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
        return {};
    while (!file.atEnd()) {
        const QString line = QString::fromUtf8(file.readLine()).trimmed();
        if (!line.startsWith(QLatin1String("Path=")))
            continue;
        QString original = QUrl::fromPercentEncoding(line.mid(5).toUtf8());
        // A trash at the top of another disk stores the path relative to that disk.
        if (!original.startsWith(QLatin1Char('/')))
            original = QFileInfo(trashPath).dir().absolutePath().chopped(6) + QStringLiteral("/../") + original;
        return QDir::cleanPath(original);
    }
    return {};
}

QStringList foldersOf(const QStringList &paths)
{
    QStringList folders;
    for (const QString &path : paths) {
        const QString folder = QFileInfo(path).absolutePath();
        if (!folders.contains(folder))
            folders.append(folder);
    }
    return folders;
}

} // namespace

MediaActions::MediaActions(QObject *parent)
    : QObject(parent)
{
}

MediaActions *MediaActions::instance()
{
    static MediaActions *actions = new MediaActions;
    return actions;
}

MediaActions *MediaActions::create(QQmlEngine *, QJSEngine *engine)
{
    engine->setObjectOwnership(instance(), QJSEngine::CppOwnership);
    return instance();
}

// #region ── plumbing ────────────────────────────────────────────────────────────────────────

QString MediaActions::freeName(const QString &folder, const QString &fileName)
{
    QString candidate = folder + QLatin1Char('/') + fileName;
    if (!QFileInfo::exists(candidate))
        return candidate;
    const QFileInfo info(fileName);
    const QString base = info.completeBaseName();
    const QString suffix = info.suffix().isEmpty() ? QString() : QLatin1Char('.') + info.suffix();
    for (int number = 2;; ++number) {
        candidate = folder + QLatin1Char('/') + base + QStringLiteral(" (%1)").arg(number) + suffix;
        if (!QFileInfo::exists(candidate))
            return candidate;
    }
}

QString MediaActions::moveFile(const QString &source, const QString &folder)
{
    QDir().mkpath(folder);
    const QString target = freeName(folder, QFileInfo(source).fileName());
    if (QFile::rename(source, target))
        return target;
    // The original is never removed before the copy is complete.
    if (!QFile::copy(source, target))
        return {};
    if (QFileInfo(target).size() != QFileInfo(source).size()) {
        QFile::remove(target);
        return {};
    }
    QFile(target).setFileTime(QFileInfo(source).lastModified(), QFileDevice::FileModificationTime);
    MediaFacts::writeFavorite(target, MediaFacts::readFavorite(source));
    QFile::remove(source);
    return target;
}

void MediaActions::offer(const QString &message, std::function<void()> undo)
{
    m_undo = std::move(undo);
    emit undoChanged();
    emit offered(message, bool(m_undo));
}

void MediaActions::undo()
{
    if (!m_undo)
        return;
    const auto undo = std::move(m_undo);
    m_undo = nullptr;
    emit undoChanged();
    undo();
}

void MediaActions::afterLibraryChange(const QStringList &folders)
{
    Library::instance()->rescan(folders);
    Library::instance()->rescanTrash();
}

void MediaActions::afterVaultChange()
{
    PrivateVault::instance()->reload();
    Library::instance()->notifyChanged();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── favourites ──────────────────────────────────────────────────────────────────────

void MediaActions::setFavorite(const QStringList &paths, bool isFavorite)
{
    PrivateVault *vault = PrivateVault::instance();
    QStringList libraryPaths;
    int failures = 0;
    for (const QString &path : paths) {
        // A private favourite lives in Private's own list, so it never shows among the library's favourites.
        if (vault->contains(path)) {
            vault->setFavorite(path, isFavorite);
            continue;
        }
        if (MediaFacts::writeFavorite(path, isFavorite))
            libraryPaths.append(path);
        else
            ++failures;
    }
    if (!libraryPaths.isEmpty())
        Library::instance()->applyFavorite(libraryPaths, isFavorite);
    if (failures > 0)
        emit failed(QStringLiteral("This disk cannot keep favourites"));
}

void MediaActions::toggleFavorite(const QString &path)
{
    PrivateVault *vault = PrivateVault::instance();
    const bool isFavorite = vault->contains(path) ? vault->isFavorite(path) : MediaFacts::readFavorite(path);
    setFavorite({path}, !isFavorite);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── trash ───────────────────────────────────────────────────────────────────────────

void MediaActions::trash(const QStringList &paths)
{
    QStringList trashed;
    for (const QString &path : paths) {
        QString inTrash;
        if (QFile::moveToTrash(path, &inTrash))
            trashed.append(inTrash);
    }
    if (trashed.isEmpty()) {
        emit failed(QStringLiteral("Could not move to the trash"));
        return;
    }
    Library::instance()->forget(paths);
    afterLibraryChange(foldersOf(paths));
    offer(QStringLiteral("%1 moved to the trash").arg(countText(int(trashed.size()))), [this, trashed] { restore(trashed); });
}

void MediaActions::restore(const QStringList &trashPaths)
{
    QStringList restored;
    QStringList folders;
    for (const QString &trashPath : trashPaths) {
        const QString original = originalPathOf(trashPath);
        // A file whose origin cannot be read goes to a Restored album, as Samsung's trash does.
        const QString folder = original.isEmpty() ? Library::instance()->newAlbumParent() + QStringLiteral("/Restored") : QFileInfo(original).absolutePath();
        QDir().mkpath(folder);
        QString target = original.isEmpty() || QFileInfo::exists(original) ? freeName(folder, QFileInfo(trashPath).fileName()) : original;
        if (!QFile::rename(trashPath, target)) {
            target = moveFile(trashPath, folder);
            if (target.isEmpty())
                continue;
        }
        QFile::remove(trashInfoFor(trashPath));
        restored.append(target);
        if (!folders.contains(folder))
            folders.append(folder);
    }
    afterLibraryChange(folders);
    if (!restored.isEmpty())
        offer(QStringLiteral("%1 restored").arg(countText(int(restored.size()))), nullptr);
}

void MediaActions::restoreTo(const QStringList &trashPaths, const QString &folder)
{
    QStringList restored;
    for (const QString &trashPath : trashPaths) {
        const QString target = moveFile(trashPath, folder);
        if (target.isEmpty())
            continue;
        QFile::remove(trashInfoFor(trashPath));
        restored.append(target);
    }
    afterLibraryChange({folder});
    if (!restored.isEmpty())
        offer(QStringLiteral("%1 restored to %2").arg(countText(int(restored.size())), Library::instance()->displayName(folder)), nullptr);
}

void MediaActions::deleteForever(const QStringList &paths)
{
    int deleted = 0;
    for (const QString &path : paths) {
        if (!QFile::remove(path))
            continue;
        QFile::remove(trashInfoFor(path));
        ++deleted;
    }
    Library::instance()->rescanTrash();
    if (deleted > 0)
        offer(QStringLiteral("%1 deleted").arg(countText(deleted)), nullptr);
}

void MediaActions::emptyTrash()
{
    QStringList paths;
    for (const MediaItem &item : Library::instance()->itemsFor(QStringLiteral("trash")))
        paths.append(item.path);
    deleteForever(paths);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── albums ──────────────────────────────────────────────────────────────────────────

void MediaActions::move(const QStringList &paths, const QString &folder)
{
    QList<QPair<QString, QString>> moved;
    for (const QString &path : paths) {
        if (QFileInfo(path).absolutePath() == folder)
            continue;
        const QString target = moveFile(path, folder);
        if (!target.isEmpty())
            moved.append({path, target});
    }
    if (moved.isEmpty())
        return;
    QStringList sources;
    for (const auto &pair : moved)
        sources.append(pair.first);
    followMovedPaths(moved);
    Library::instance()->forget(sources);
    afterLibraryChange(foldersOf(sources) << folder);
    offer(QStringLiteral("%1 moved to %2").arg(countText(int(moved.size())), Library::instance()->displayName(folder)), [this, moved] {
        QStringList folders;
        for (const auto &pair : moved) {
            const QString back = QFileInfo(pair.first).absolutePath();
            QDir().mkpath(back);
            if (!QFile::rename(pair.second, pair.first))
                moveFile(pair.second, back);
            if (!folders.contains(back))
                folders.append(back);
        }
        afterLibraryChange(folders << QFileInfo(moved.first().second).absolutePath());
    });
}

QString MediaActions::createAlbum(const QString &name)
{
    const QString clean = name.trimmed().replace(QLatin1Char('/'), QLatin1Char('-'));
    if (clean.isEmpty() || clean.startsWith(QLatin1Char('.')))
        return {};
    const QString folder = Library::instance()->newAlbumParent() + QLatin1Char('/') + clean;
    QDir().mkpath(folder);
    return folder;
}

void MediaActions::deleteAlbum(const QString &folder)
{
    QStringList paths;
    for (const MediaItem &item : Library::instance()->itemsFor(QStringLiteral("album:") + folder))
        paths.append(item.path);
    trash(paths);
    // The folder itself goes once nothing else is in it.
    QDir().rmdir(folder);
}

void MediaActions::renameAlbum(const QString &folder, const QString &name)
{
    // Only the name shown changes; the folder keeps its own, so other apps keep saving into it.
    Settings::instance()->setAlbumName(folder, name.trimmed());
    Library::instance()->notifyChanged();
}

void MediaActions::setCover(const QString &albumKey, const QString &path)
{
    if (albumKey.startsWith(QLatin1String("private:"))) {
        QFile file(PrivateVault::instance()->groupFolder(albumKey.mid(8)) + QStringLiteral("/.cover"));
        if (file.open(QIODevice::WriteOnly))
            file.write(QFileInfo(path).fileName().toUtf8());
        afterVaultChange();
    } else {
        Settings::instance()->setCover(albumKey, path);
        Library::instance()->notifyChanged();
    }
    offer(QStringLiteral("Set as cover"), nullptr);
}

void MediaActions::addToFavoriteAlbum(const QStringList &paths, const QString &name)
{
    const QSet<QString> adding(paths.cbegin(), paths.cend());
    QVariantList albums = Settings::instance()->favoriteAlbums();
    bool isFound = false;
    for (QVariant &album : albums) {
        QVariantMap map = album.toMap();
        QStringList kept;
        for (const QVariant &path : map.value(QStringLiteral("paths")).toList())
            if (!adding.contains(path.toString()))
                kept.append(path.toString());
        if (map.value(QStringLiteral("name")).toString() == name) {
            kept.append(paths);
            isFound = true;
        }
        map.insert(QStringLiteral("paths"), kept);
        album = map;
    }
    if (!isFound)
        albums.append(QVariantMap{{QStringLiteral("name"), name}, {QStringLiteral("paths"), paths}});
    Settings::instance()->setFavoriteAlbums(albums);
    Library::instance()->notifyChanged();
    offer(QStringLiteral("%1 added to %2").arg(countText(int(paths.size())), name), nullptr);
}

void MediaActions::removeFavoriteAlbums(const QStringList &names)
{
    QVariantList albums;
    for (const QVariant &album : Settings::instance()->favoriteAlbums())
        if (!names.contains(album.toMap().value(QStringLiteral("name")).toString()))
            albums.append(album);
    Settings::instance()->setFavoriteAlbums(albums);
    Library::instance()->notifyChanged();
}

void MediaActions::renameFavoriteAlbum(const QString &from, const QString &to)
{
    const QString clean = to.trimmed();
    if (clean.isEmpty())
        return;
    QVariantList albums = Settings::instance()->favoriteAlbums();
    for (QVariant &album : albums) {
        QVariantMap map = album.toMap();
        if (map.value(QStringLiteral("name")).toString() == from)
            map.insert(QStringLiteral("name"), clean);
        album = map;
    }
    Settings::instance()->setFavoriteAlbums(albums);
    // It keeps its place and its group.
    QStringList order = Settings::instance()->order(QStringLiteral("favorites"));
    order.replaceInStrings(QRegularExpression(QStringLiteral("^") + QRegularExpression::escape(from) + QStringLiteral("$")), clean);
    Settings::instance()->setOrder(QStringLiteral("favorites"), order);
    Library::instance()->notifyChanged();
}

void MediaActions::followMovedPaths(const QList<QPair<QString, QString>> &moved)
{
    QHash<QString, QString> renamed;
    for (const auto &pair : moved)
        renamed.insert(pair.first, pair.second);
    QVariantList albums = Settings::instance()->favoriteAlbums();
    bool isChanged = false;
    for (QVariant &album : albums) {
        QVariantMap map = album.toMap();
        QStringList paths;
        for (const QVariant &path : map.value(QStringLiteral("paths")).toList()) {
            const QString next = renamed.value(path.toString(), path.toString());
            isChanged = isChanged || next != path.toString();
            paths.append(next);
        }
        map.insert(QStringLiteral("paths"), paths);
        album = map;
    }
    if (isChanged)
        Settings::instance()->setFavoriteAlbums(albums);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── private ─────────────────────────────────────────────────────────────────────────

void MediaActions::hide(const QStringList &paths, const QString &group)
{
    PrivateVault *vault = PrivateVault::instance();
    const QString folder = vault->createGroup(group);
    if (folder.isEmpty())
        return;
    QStringList hidden;
    for (const QString &path : paths) {
        const bool wasFavorite = MediaFacts::readFavorite(path);
        const QString target = moveFile(path, folder);
        if (target.isEmpty())
            continue;
        // A favourite stays one, but only inside Private.
        if (wasFavorite) {
            MediaFacts::writeFavorite(target, false);
            vault->setFavorite(target, true);
        }
        hidden.append(path);
    }
    Library::instance()->forget(hidden);
    afterLibraryChange(foldersOf(hidden));
    afterVaultChange();
    if (!hidden.isEmpty())
        offer(QStringLiteral("%1 moved to Private").arg(countText(int(hidden.size()))), nullptr);
}

void MediaActions::hideAlbum(const QString &folder, const QString &group)
{
    QStringList paths;
    for (const MediaItem &item : Library::instance()->itemsFor(QStringLiteral("album:") + folder))
        paths.append(item.path);
    hide(paths, group);
    QDir().rmdir(folder);
}

void MediaActions::unhide(const QStringList &paths, const QString &folder)
{
    PrivateVault *vault = PrivateVault::instance();
    QStringList moved;
    for (const QString &path : paths) {
        const bool wasFavorite = vault->isFavorite(path);
        const QString target = moveFile(path, folder);
        if (target.isEmpty())
            continue;
        // Back out of Private, a private favourite becomes an ordinary one again.
        if (wasFavorite) {
            vault->setFavorite(path, false);
            MediaFacts::writeFavorite(target, true);
        }
        moved.append(target);
    }
    afterVaultChange();
    afterLibraryChange({folder});
    if (!moved.isEmpty())
        offer(QStringLiteral("%1 moved to %2").arg(countText(int(moved.size())), Library::instance()->displayName(folder)), nullptr);
}

void MediaActions::moveToGroup(const QStringList &paths, const QString &group)
{
    PrivateVault *vault = PrivateVault::instance();
    const QString folder = vault->createGroup(group);
    QList<QPair<QString, QString>> moved;
    for (const QString &path : paths) {
        if (QFileInfo(path).absolutePath() == folder)
            continue;
        const bool wasFavorite = vault->isFavorite(path);
        const QString target = moveFile(path, folder);
        if (target.isEmpty())
            continue;
        if (wasFavorite) {
            vault->setFavorite(path, false);
            vault->setFavorite(target, true);
        }
        moved.append({path, target});
    }
    afterVaultChange();
    if (moved.isEmpty())
        return;
    offer(QStringLiteral("%1 moved to %2").arg(countText(int(moved.size())), group), [this, moved] {
        PrivateVault *vault = PrivateVault::instance();
        for (const auto &pair : moved) {
            const bool wasFavorite = vault->isFavorite(pair.second);
            QFile::rename(pair.second, pair.first);
            if (wasFavorite) {
                vault->setFavorite(pair.second, false);
                vault->setFavorite(pair.first, true);
            }
        }
        afterVaultChange();
    });
}

void MediaActions::trashPrivate(const QStringList &paths)
{
    PrivateVault *vault = PrivateVault::instance();
    QList<QPair<QString, QString>> trashed;
    for (const QString &path : paths) {
        const bool wasFavorite = vault->isFavorite(path);
        const QString group = vault->groupOf(path);
        const QString target = moveFile(path, vault->trashFolder());
        if (target.isEmpty())
            continue;
        if (wasFavorite)
            vault->setFavorite(path, false);
        vault->recordTrashed(target, group, wasFavorite);
        trashed.append({path, target});
    }
    afterVaultChange();
    if (trashed.isEmpty())
        return;
    QStringList trashPaths;
    for (const auto &pair : trashed)
        trashPaths.append(pair.second);
    offer(QStringLiteral("%1 moved to Private's trash").arg(countText(int(trashed.size()))), [this, trashPaths] { restorePrivate(trashPaths); });
}

void MediaActions::restorePrivate(const QStringList &trashPaths, const QString &group)
{
    PrivateVault *vault = PrivateVault::instance();
    int restored = 0;
    for (const QString &trashPath : trashPaths) {
        const QVariantMap record = vault->trashRecord(trashPath);
        // Back to the album it left, made again if it is gone, and a favourite again if it was one.
        const QString into = group.isEmpty() ? record.value(QStringLiteral("group")).toString() : group;
        const QString folder = vault->createGroup(into.isEmpty() ? QStringLiteral("Restored") : into);
        const QString target = moveFile(trashPath, folder);
        if (target.isEmpty())
            continue;
        if (record.value(QStringLiteral("wasFavorite")).toBool())
            vault->setFavorite(target, true);
        vault->forgetTrashed(trashPath);
        ++restored;
    }
    afterVaultChange();
    if (restored > 0)
        offer(QStringLiteral("%1 restored").arg(countText(restored)), nullptr);
}

void MediaActions::deletePrivateForever(const QStringList &trashPaths)
{
    PrivateVault *vault = PrivateVault::instance();
    int deleted = 0;
    for (const QString &trashPath : trashPaths) {
        if (!QFile::remove(trashPath))
            continue;
        vault->forgetTrashed(trashPath);
        ++deleted;
    }
    afterVaultChange();
    if (deleted > 0)
        offer(QStringLiteral("%1 deleted").arg(countText(deleted)), nullptr);
}

void MediaActions::emptyPrivateTrash()
{
    QStringList paths;
    for (const MediaItem &item : PrivateVault::instance()->itemsFor(QStringLiteral("private-trash")))
        paths.append(item.path);
    deletePrivateForever(paths);
}

QString MediaActions::createGroup(const QString &name)
{
    return PrivateVault::instance()->createGroup(name);
}

void MediaActions::renameGroup(const QString &from, const QString &to)
{
    if (!PrivateVault::instance()->renameGroup(from, to))
        emit failed(QStringLiteral("That name is taken"));
}

void MediaActions::deleteGroup(const QString &name)
{
    PrivateVault *vault = PrivateVault::instance();
    QStringList paths;
    for (const MediaItem &item : vault->itemsFor(QStringLiteral("private:") + name))
        paths.append(item.path);
    if (!paths.isEmpty())
        trashPrivate(paths);
    const QString folder = vault->groupFolder(name);
    QFile::remove(folder + QStringLiteral("/.cover"));
    QDir().rmdir(folder);
    afterVaultChange();
}

void MediaActions::moveGroupOut(const QString &name, const QString &folder)
{
    PrivateVault *vault = PrivateVault::instance();
    QStringList paths;
    for (const MediaItem &item : vault->itemsFor(QStringLiteral("private:") + name))
        paths.append(item.path);
    unhide(paths, folder);
    // The emptied group is removed.
    const QString groupFolder = vault->groupFolder(name);
    QFile::remove(groupFolder + QStringLiteral("/.cover"));
    QDir().rmdir(groupFolder);
    afterVaultChange();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
