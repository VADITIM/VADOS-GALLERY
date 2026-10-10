#include "duplicateFinder.h"

#include "library.h"
#include "privateVault.h"

#include <QDataStream>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QImageReader>
#include <QMutexLocker>
#include <QSaveFile>
#include <QStandardPaths>
#include <QThreadPool>
#include <QtConcurrent/QtConcurrentMap>
#include <QtConcurrent/QtConcurrentRun>

namespace {

// Raised whenever the print changes what it reads, so old prints are read again rather than compared with new ones.
constexpr quint32 FORMAT = 0x56474431; // "VGD1"
constexpr int PRINT_PIXELS = 128;
constexpr int FINE_PIXELS = 512;
constexpr int SAVE_EVERY = 400;

QImage decoded(const QString &path, int box)
{
    QImageReader reader(path);
    reader.setAutoTransform(true);
    QSize size = reader.size();
    if (size.isValid() && (size.width() > box || size.height() > box)) {
        size = size.scaled(box, box, Qt::KeepAspectRatio);
        if (reader.transformation() & QImageIOHandler::TransformationRotate90)
            size.transpose();
        reader.setScaledSize(size);
    }
    return reader.read();
}

// The copy to keep: the most pixels, then the largest file (the least compressed), then one from the camera, then the oldest, which is most likely the original.
bool isBetter(const MediaItem &left, const MediaItem &right)
{
    const qint64 leftPixels = qint64(left.width) * left.height;
    const qint64 rightPixels = qint64(right.width) * right.height;
    if (leftPixels != rightPixels)
        return leftPixels > rightPixels;
    if (left.size != right.size)
        return left.size > right.size;
    const auto isCamera = [](const MediaItem &item) {
        const QString folder = item.folder.section(QLatin1Char('/'), -1).toLower();
        return folder == QLatin1String("camera") || folder == QLatin1String("dcim");
    };
    if (isCamera(left) != isCamera(right))
        return isCamera(left);
    return left.timestamp < right.timestamp;
}

QDataStream &operator<<(QDataStream &stream, const ImagePrint &print)
{
    for (quint64 shape : print.shapes)
        stream << shape;
    for (quint32 colour : print.colours)
        stream << colour;
    return stream << print.detail << print.ratio;
}

QDataStream &operator>>(QDataStream &stream, ImagePrint &print)
{
    for (quint64 &shape : print.shapes)
        stream >> shape;
    for (quint32 &colour : print.colours)
        stream >> colour;
    return stream >> print.detail >> print.ratio;
}

} // namespace

DuplicateFinder::DuplicateFinder(QObject *parent)
    : QObject(parent)
{
    connect(&m_watcher, &QFutureWatcher<SearchResult>::finished, this, [this] {
        const SearchResult result = m_watcher.result();
        if (result.isStopped)
            return;
        m_groups.clear();
        for (const QVector<MediaItem> &group : result.groups) {
            QVariantList items;
            for (const MediaItem &item : group) {
                QVariantMap map = item.toVariant();
                map.insert(QStringLiteral("album"), Library::instance()->displayName(item.folder));
                items.append(map);
            }
            m_groups.append(QVariant(items));
        }
        report(QStringLiteral("done"), m_totalCount, m_totalCount);
        emit groupsChanged();
    });
}

void DuplicateFinder::report(const QString &stage, int read, int total)
{
    m_stage = stage;
    m_readCount = read;
    m_totalCount = total;
    m_progress = total > 0 ? double(read) / total : 1;
    emit progressChanged();
}

void DuplicateFinder::stop()
{
    if (m_isStopped)
        m_isStopped->store(true);
}

void DuplicateFinder::find(const QString &scope, const QString &strictness)
{
    stop();
    m_watcher.waitForFinished();
    const bool isPrivate = scope == QLatin1String("private");
    QVector<MediaItem> photos;
    for (const MediaItem &item : Library::instance()->itemsFor(isPrivate ? QStringLiteral("private-recent") : QStringLiteral("recent")))
        if (!item.isVideo)
            photos.append(item);
    const Strictness chosen = strictness == QLatin1String("exact") ? Strictness::exact() : strictness == QLatin1String("loose") ? Strictness::loose() : Strictness::close();
    // Private's prints stay in the private folder, never in the app's own files.
    const QString indexPath = isPrivate ? PrivateVault::instance()->root() + QStringLiteral("/.duplicates")
                                        : QStandardPaths::writableLocation(QStandardPaths::GenericCacheLocation) + QStringLiteral("/vados/gallery/prints.dat");
    m_isStopped = std::make_shared<std::atomic_bool>(false);
    m_groups.clear();
    emit groupsChanged();
    report(QStringLiteral("reading"), 0, int(photos.size()));
    m_watcher.setFuture(QtConcurrent::run(&DuplicateFinder::search, this, photos, chosen, indexPath, m_isStopped));
}

DuplicateFinder::SearchResult DuplicateFinder::search(QVector<MediaItem> photos, Strictness strictness, QString indexPath, std::shared_ptr<std::atomic_bool> isStopped)
{
    // The search runs below the screen's priority, so what moves on screen while it reads and compares keeps its frames.
    QThread::currentThread()->setPriority(QThread::LowPriority);
    loadIndex(indexPath);
    // A file whose size changed was edited in place, so its old print no longer says what it shows.
    QVector<MediaItem> unread;
    {
        QMutexLocker locker(&m_lock);
        for (const MediaItem &item : photos)
            if (!m_prints.contains(item.path) || m_prints.value(item.path).size != item.size)
                unread.append(item);
    }
    std::atomic_int done(int(photos.size() - unread.size()));
    for (int start = 0; start < unread.size(); start += SAVE_EVERY) {
        if (isStopped->load())
            return {{}, true};
        const QVector<MediaItem> batch = unread.mid(start, SAVE_EVERY);
        QtConcurrent::blockingMap(batch, [&](const MediaItem &item) {
            if (isStopped->load())
                return;
            const ImagePrint print = ImagePrints::of(decoded(item.path, PRINT_PIXELS));
            {
                QMutexLocker locker(&m_lock);
                m_prints.insert(item.path, {item.size, print});
            }
            const int count = ++done;
            if (count % 20 == 0)
                QMetaObject::invokeMethod(this, [this, count, total = int(photos.size())] { report(QStringLiteral("reading"), count, total); }, Qt::QueuedConnection);
        });
        saveIndex(indexPath, photos);
    }
    QMetaObject::invokeMethod(this, [this, total = int(photos.size())] { report(QStringLiteral("comparing"), total, total); }, Qt::QueuedConnection);

    QVector<ImagePrint> prints(photos.size());
    QVector<float> ratios(photos.size());
    {
        QMutexLocker locker(&m_lock);
        for (int index = 0; index < photos.size(); ++index) {
            prints[index] = m_prints.value(photos[index].path).print;
            const MediaItem &item = photos[index];
            ratios[index] = item.width > 0 && item.height > 0 ? float(std::max(item.width, item.height)) / std::min(item.width, item.height) : prints[index].ratio;
        }
    }
    QHash<int, QByteArray> fines;
    QHash<int, QByteArray> sharps;
    const auto fineOf = [&](int index) {
        if (!fines.contains(index))
            fines.insert(index, ImagePrints::fineOf(decoded(photos[index].path, FINE_PIXELS)));
        return fines.value(index);
    };
    const auto sharpOf = [&](int index) {
        if (!sharps.contains(index))
            sharps.insert(index, ImagePrints::sharpOf(decoded(photos[index].path, FINE_PIXELS)));
        return sharps.value(index);
    };
    const QVector<QVector<int>> sets = ImagePrints::groupsOfSame(prints, ratios, fineOf, sharpOf, strictness, [&] { return isStopped->load(); });
    if (isStopped->load())
        return {{}, true};
    SearchResult result;
    for (const QVector<int> &set : sets) {
        QVector<MediaItem> group;
        for (int index : set)
            group.append(photos[index]);
        std::sort(group.begin(), group.end(), isBetter);
        result.groups.append(group);
    }
    // The sets with the most to free come first.
    const auto freed = [](const QVector<MediaItem> &group) {
        qint64 bytes = 0;
        for (int index = 1; index < group.size(); ++index)
            bytes += group[index].size;
        return bytes;
    };
    std::sort(result.groups.begin(), result.groups.end(), [&](const QVector<MediaItem> &left, const QVector<MediaItem> &right) { return freed(left) > freed(right); });
    return result;
}

void DuplicateFinder::loadIndex(const QString &path)
{
    QMutexLocker locker(&m_lock);
    if (m_loadedIndex == path)
        return;
    m_loadedIndex = path;
    m_prints.clear();
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly))
        return;
    QDataStream stream(&file);
    quint32 format = 0;
    stream >> format;
    if (format != FORMAT)
        return;
    qint32 count = 0;
    stream >> count;
    for (int index = 0; index < count && !stream.atEnd(); ++index) {
        QString key;
        StoredPrint stored;
        stream >> key >> stored.size >> stored.print;
        m_prints.insert(key, stored);
    }
}

// Only photos still there are written back, so deleted ones drop out of the file.
void DuplicateFinder::saveIndex(const QString &path, const QVector<MediaItem> &photos)
{
    QMutexLocker locker(&m_lock);
    QDir().mkpath(QFileInfo(path).absolutePath());
    QSaveFile file(path);
    if (!file.open(QIODevice::WriteOnly))
        return;
    QDataStream stream(&file);
    QVector<const MediaItem *> kept;
    for (const MediaItem &item : photos)
        if (m_prints.contains(item.path))
            kept.append(&item);
    stream << FORMAT << qint32(kept.size());
    for (const MediaItem *item : kept) {
        const StoredPrint stored = m_prints.value(item->path);
        stream << item->path << stored.size << stored.print;
    }
    file.commit();
}
