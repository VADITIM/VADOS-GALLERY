#include "privateVault.h"

#include "mediaFacts.h"
#include "settings.h"

#include <QCryptographicHash>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJSEngine>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QRandomGenerator>
#include <QSaveFile>

#include <algorithm>

namespace {

constexpr int PIN_ROUNDS = 120000;
constexpr qint64 TRASH_KEEP_MS = 30LL * 24 * 60 * 60 * 1000;

QByteArray stretch(const QString &pin, const QByteArray &salt)
{
    QByteArray digest = salt + pin.toUtf8();
    for (int round = 0; round < PIN_ROUNDS; ++round)
        digest = QCryptographicHash::hash(digest + salt, QCryptographicHash::Sha256);
    return digest;
}

bool isOlder(const MediaItem &left, const MediaItem &right)
{
    return left.timestamp != right.timestamp ? left.timestamp < right.timestamp : left.path < right.path;
}

} // namespace

PrivateVault::PrivateVault(QObject *parent)
    : QObject(parent)
{
    const QString fromEnvironment = qEnvironmentVariable("VADOS_GALLERY_PRIVATE");
    m_root = fromEnvironment.isEmpty() ? QDir::homePath() + QStringLiteral("/.vados-private") : fromEnvironment;
}

PrivateVault *PrivateVault::instance()
{
    static PrivateVault *vault = new PrivateVault;
    return vault;
}

PrivateVault *PrivateVault::create(QQmlEngine *, QJSEngine *engine)
{
    engine->setObjectOwnership(instance(), QJSEngine::CppOwnership);
    return instance();
}

// #region ── lock ────────────────────────────────────────────────────────────────────────────

bool PrivateVault::hasPin() const
{
    return QFileInfo::exists(m_root + QStringLiteral("/.lock"));
}

void PrivateVault::setPin(const QString &pin)
{
    if (pin.isEmpty())
        return;
    QDir().mkpath(m_root);
    // No other gallery may index what is in here.
    QFile marker(m_root + QStringLiteral("/.nomedia"));
    if (!marker.exists() && marker.open(QIODevice::WriteOnly))
        marker.close();
    QByteArray salt(16, Qt::Uninitialized);
    QRandomGenerator::system()->fillRange(reinterpret_cast<quint32 *>(salt.data()), salt.size() / 4);
    QSaveFile file(m_root + QStringLiteral("/.lock"));
    if (!file.open(QIODevice::WriteOnly))
        return;
    file.write(salt.toHex() + '\n' + stretch(pin, salt).toHex() + '\n');
    file.commit();
    QFile::setPermissions(m_root + QStringLiteral("/.lock"), QFileDevice::ReadOwner | QFileDevice::WriteOwner);
    m_isUnlocked = true;
    reload();
    emit lockChanged();
}

bool PrivateVault::unlock(const QString &pin)
{
    QFile file(m_root + QStringLiteral("/.lock"));
    if (!file.open(QIODevice::ReadOnly))
        return false;
    const QList<QByteArray> lines = file.readAll().split('\n');
    if (lines.size() < 2)
        return false;
    const QByteArray salt = QByteArray::fromHex(lines.at(0));
    if (stretch(pin, salt).toHex() != lines.at(1))
        return false;
    m_isUnlocked = true;
    reload();
    emit lockChanged();
    return true;
}

void PrivateVault::lock()
{
    if (!m_isUnlocked)
        return;
    m_isUnlocked = false;
    m_groups.clear();
    m_trash.clear();
    m_favorites.clear();
    ++m_revision;
    emit lockChanged();
    emit changed();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── reading ─────────────────────────────────────────────────────────────────────────

QString PrivateVault::relative(const QString &path) const
{
    return QDir(m_root).relativeFilePath(path);
}

QString PrivateVault::groupOf(const QString &path) const
{
    return contains(path) ? relative(path).section(QLatin1Char('/'), 0, 0) : QString();
}

void PrivateVault::reload()
{
    m_groups.clear();
    if (!m_isUnlocked)
        return;
    readFavorites();
    readTrashIndex();
    purgeOldTrash();
    const QFileInfoList folders = QDir(m_root).entryInfoList(QDir::Dirs | QDir::NoDotAndDotDot);
    for (const QFileInfo &folder : folders) {
        QVector<MediaItem> items;
        const QFileInfoList files = QDir(folder.absoluteFilePath()).entryInfoList(QDir::Files | QDir::NoDotAndDotDot);
        for (const QFileInfo &file : files) {
            if (!MediaFacts::isMedia(file.fileName()))
                continue;
            MediaItem item = MediaFacts::read(file);
            if (item.isVideo)
                MediaFacts::probeVideo(item);
            item.isFavorite = m_favorites.contains(relative(item.path));
            items.append(item);
        }
        std::sort(items.begin(), items.end(), isOlder);
        m_groups.insert(folder.fileName(), items);
    }
    m_trash.clear();
    const QFileInfoList trashed = QDir(trashFolder()).entryInfoList(QDir::Files | QDir::NoDotAndDotDot);
    for (const QFileInfo &file : trashed) {
        if (!MediaFacts::isMedia(file.fileName()))
            continue;
        MediaItem item = MediaFacts::read(file);
        const QVariantMap record = m_trashIndex.value(file.fileName());
        item.trashedAt = record.value(QStringLiteral("trashedAt")).toLongLong();
        item.folder = groupFolder(record.value(QStringLiteral("group")).toString());
        item.originalPath = item.folder + QLatin1Char('/') + file.fileName();
        m_trash.append(item);
    }
    std::sort(m_trash.begin(), m_trash.end(), isOlder);
    if (m_todaysSelection.isEmpty())
        pickTodaysSelection();
    ++m_revision;
    emit changed();
}

QVector<MediaItem> PrivateVault::itemsFor(const QString &source) const
{
    if (!m_isUnlocked)
        return {};
    if (source.startsWith(QLatin1String("private:")))
        return m_groups.value(source.mid(8));
    QVector<MediaItem> items;
    if (source == QLatin1String("private-recent") || source == QLatin1String("private-favorites")) {
        const bool isFavoritesOnly = source == QLatin1String("private-favorites");
        for (const QVector<MediaItem> &group : m_groups)
            for (const MediaItem &item : group)
                if (!isFavoritesOnly || item.isFavorite)
                    items.append(item);
        std::sort(items.begin(), items.end(), isOlder);
    } else if (source == QLatin1String("private-trash")) {
        items = m_trash;
    }
    return items;
}

QVariantMap PrivateVault::item(const QString &path) const
{
    for (const QVector<MediaItem> &group : m_groups)
        for (const MediaItem &item : group)
            if (item.path == path)
                return item.toVariant();
    for (const MediaItem &item : m_trash)
        if (item.path == path)
            return item.toVariant();
    return {};
}

QStringList PrivateVault::groupNames() const
{
    QStringList names = m_groups.keys();
    names.sort(Qt::CaseInsensitive);
    // Groups dragged into an order come in it; ones never arranged keep theirs after them.
    const QStringList order = Settings::instance()->order(QStringLiteral("private"));
    std::stable_sort(names.begin(), names.end(), [&](const QString &left, const QString &right) {
        const qsizetype leftAt = order.indexOf(left);
        const qsizetype rightAt = order.indexOf(right);
        return (leftAt < 0 ? order.size() : leftAt) < (rightAt < 0 ? order.size() : rightAt);
    });
    return names;
}

QVariantList PrivateVault::groups() const
{
    QVariantList list;
    if (!m_isUnlocked)
        return list;
    for (const QString &name : groupNames()) {
        const QVector<MediaItem> &items = m_groups[name];
        QString cover = items.isEmpty() ? QString() : items.last().path;
        bool isCoverVideo = !items.isEmpty() && items.last().isVideo;
        // The cover is remembered inside the group's own folder, as on the phone.
        QFile coverFile(groupFolder(name) + QStringLiteral("/.cover"));
        if (coverFile.open(QIODevice::ReadOnly)) {
            const QString chosen = groupFolder(name) + QLatin1Char('/') + QString::fromUtf8(coverFile.readAll()).trimmed();
            for (const MediaItem &item : items)
                if (item.path == chosen) {
                    cover = chosen;
                    isCoverVideo = item.isVideo;
                }
        }
        list.append(QVariantMap{
            {QStringLiteral("key"), QStringLiteral("private:") + name},
            {QStringLiteral("folder"), groupFolder(name)},
            {QStringLiteral("name"), name},
            {QStringLiteral("count"), int(items.size())},
            {QStringLiteral("cover"), cover},
            {QStringLiteral("isCoverVideo"), isCoverVideo},
            {QStringLiteral("newest"), items.isEmpty() ? 0 : items.last().timestamp},
        });
    }
    return list;
}

QVariantMap PrivateVault::todaysSelection() const
{
    return m_todaysSelection.isEmpty() ? QVariantMap() : item(m_todaysSelection);
}

// A new pick every time Private is entered, from the private favourites.
void PrivateVault::pickTodaysSelection()
{
    const QVector<MediaItem> favorites = itemsFor(QStringLiteral("private-favorites"));
    m_todaysSelection = favorites.isEmpty() ? QString() : favorites.at(QRandomGenerator::global()->bounded(int(favorites.size()))).path;
    ++m_revision;
    emit changed();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── favourites and trash index ──────────────────────────────────────────────────────

void PrivateVault::readFavorites()
{
    m_favorites.clear();
    QFile file(m_root + QStringLiteral("/.favorites"));
    if (!file.open(QIODevice::ReadOnly))
        return;
    for (const QJsonValue &value : QJsonDocument::fromJson(file.readAll()).array())
        m_favorites.insert(value.toString());
}

void PrivateVault::writeFavorites() const
{
    QJsonArray array;
    for (const QString &path : m_favorites)
        array.append(path);
    QSaveFile file(m_root + QStringLiteral("/.favorites"));
    if (file.open(QIODevice::WriteOnly)) {
        file.write(QJsonDocument(array).toJson(QJsonDocument::Compact));
        file.commit();
    }
}

void PrivateVault::setFavorite(const QString &path, bool isFavorite)
{
    if (isFavorite)
        m_favorites.insert(relative(path));
    else
        m_favorites.remove(relative(path));
    writeFavorites();
    for (QVector<MediaItem> &group : m_groups)
        for (MediaItem &item : group)
            if (item.path == path)
                item.isFavorite = isFavorite;
    ++m_revision;
    emit changed();
}

void PrivateVault::readTrashIndex()
{
    m_trashIndex.clear();
    QFile file(trashFolder() + QStringLiteral("/index.json"));
    if (!file.open(QIODevice::ReadOnly))
        return;
    const QJsonObject object = QJsonDocument::fromJson(file.readAll()).object();
    for (auto entry = object.constBegin(); entry != object.constEnd(); ++entry)
        m_trashIndex.insert(entry.key(), entry.value().toObject().toVariantMap());
}

void PrivateVault::writeTrashIndex() const
{
    QDir().mkpath(trashFolder());
    QJsonObject object;
    for (auto entry = m_trashIndex.cbegin(); entry != m_trashIndex.cend(); ++entry)
        object.insert(entry.key(), QJsonObject::fromVariantMap(entry.value()));
    QSaveFile file(trashFolder() + QStringLiteral("/index.json"));
    if (file.open(QIODevice::WriteOnly)) {
        file.write(QJsonDocument(object).toJson(QJsonDocument::Compact));
        file.commit();
    }
}

void PrivateVault::recordTrashed(const QString &trashPath, const QString &group, bool wasFavorite)
{
    m_trashIndex.insert(QFileInfo(trashPath).fileName(), {{QStringLiteral("group"), group},
                                                          {QStringLiteral("trashedAt"), QDateTime::currentMSecsSinceEpoch()},
                                                          {QStringLiteral("wasFavorite"), wasFavorite}});
    writeTrashIndex();
}

QVariantMap PrivateVault::trashRecord(const QString &trashPath) const
{
    return m_trashIndex.value(QFileInfo(trashPath).fileName());
}

void PrivateVault::forgetTrashed(const QString &trashPath)
{
    m_trashIndex.remove(QFileInfo(trashPath).fileName());
    writeTrashIndex();
}

// Photos are kept 30 days and deleted for good the next time Private is read.
void PrivateVault::purgeOldTrash()
{
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    bool isChanged = false;
    for (auto entry = m_trashIndex.begin(); entry != m_trashIndex.end();) {
        if (now - entry.value().value(QStringLiteral("trashedAt")).toLongLong() > TRASH_KEEP_MS) {
            QFile::remove(trashFolder() + QLatin1Char('/') + entry.key());
            entry = m_trashIndex.erase(entry);
            isChanged = true;
        } else {
            ++entry;
        }
    }
    if (isChanged)
        writeTrashIndex();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── groups ──────────────────────────────────────────────────────────────────────────

QString PrivateVault::createGroup(const QString &name)
{
    const QString clean = name.trimmed().replace(QLatin1Char('/'), QLatin1Char('-'));
    if (clean.isEmpty() || clean.startsWith(QLatin1Char('.')))
        return {};
    QDir().mkpath(groupFolder(clean));
    if (!m_groups.contains(clean))
        m_groups.insert(clean, {});
    ++m_revision;
    emit changed();
    return groupFolder(clean);
}

// Groups are folders nothing else writes into, so they are renamed on disk.
bool PrivateVault::renameGroup(const QString &from, const QString &to)
{
    const QString clean = to.trimmed().replace(QLatin1Char('/'), QLatin1Char('-'));
    if (clean.isEmpty() || clean == from || m_groups.contains(clean))
        return false;
    if (!QDir().rename(groupFolder(from), groupFolder(clean)))
        return false;
    QSet<QString> renamed;
    for (const QString &favorite : std::as_const(m_favorites))
        renamed.insert(favorite.startsWith(from + QLatin1Char('/')) ? clean + favorite.mid(from.size()) : favorite);
    m_favorites = renamed;
    writeFavorites();
    reload();
    return true;
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
