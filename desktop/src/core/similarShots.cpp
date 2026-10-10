#include "similarShots.h"

#include <QDataStream>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QImageReader>
#include <QSaveFile>
#include <QStandardPaths>
#include <QtConcurrent/QtConcurrentMap>
#include <QtConcurrent/QtConcurrentRun>

#include <bit>

namespace {

// Two shots this close in time can be one stack; a run keeps growing while each next shot is this close to the one before.
constexpr qint64 WINDOW_MS = 8000;
// How many of the 64 bits may differ for two shots to count as the same picture.
constexpr int MAXIMUM_DIFFERENCE = 12;
constexpr quint32 FILE_MAGIC = 0x56475331; // "VGS1"

QString filePath()
{
    return QStandardPaths::writableLocation(QStandardPaths::GenericCacheLocation) + QStringLiteral("/vados/gallery/similar.dat");
}

bool isClose(const MediaItem *first, const MediaItem *second)
{
    return first && second && !first->isVideo && !second->isVideo && second->timestamp - first->timestamp <= WINDOW_MS;
}

// A difference hash: the picture shrunk to 9×8 greys, one bit per pair of side-by-side pixels saying which is brighter. Light, crop and small moves barely change it; a different picture changes it a lot.
quint64 hashOf(const QString &path)
{
    QImageReader reader(path);
    reader.setAutoTransform(true);
    const QSize size = reader.size();
    if (size.isValid())
        reader.setScaledSize(size.scaled(96, 96, Qt::KeepAspectRatio));
    const QImage picture = reader.read();
    if (picture.isNull())
        return 0;
    const QImage small = picture.scaled(9, 8, Qt::IgnoreAspectRatio, Qt::SmoothTransformation).convertToFormat(QImage::Format_Grayscale8);
    quint64 hash = 0;
    for (int y = 0; y < 8; ++y) {
        const uchar *line = small.constScanLine(y);
        for (int x = 0; x < 8; ++x)
            hash = (hash << 1) | (line[x] > line[x + 1] ? 1 : 0);
    }
    // A hash of zero means "not read"; a flat picture that hashes to it simply never stacks.
    return hash;
}

} // namespace

SimilarShots::SimilarShots(QObject *parent)
    : QObject(parent)
{
    connect(&m_watcher, &QFutureWatcher<QHash<QString, quint64>>::finished, this, [this] {
        const QHash<QString, quint64> fresh = m_watcher.result();
        for (auto entry = fresh.cbegin(); entry != fresh.cend(); ++entry)
            m_hashes.insert(entry.key(), entry.value());
        save();
        if (!fresh.isEmpty())
            emit changed();
        if (!m_waiting.isEmpty()) {
            const QVector<MediaItem> waiting = m_waiting;
            m_waiting.clear();
            index(waiting);
        }
    });
}

SimilarShots *SimilarShots::instance()
{
    static SimilarShots *shots = new SimilarShots;
    return shots;
}

QString SimilarShots::keyOf(const MediaItem &item)
{
    return item.path + QLatin1Char('|') + QString::number(item.modified);
}

bool SimilarShots::isAlike(const MediaItem &first, const MediaItem &second) const
{
    if (first.isVideo || second.isVideo || second.timestamp - first.timestamp > WINDOW_MS)
        return false;
    const quint64 firstHash = m_hashes.value(keyOf(first));
    const quint64 secondHash = m_hashes.value(keyOf(second));
    if (firstHash == 0 || secondHash == 0)
        return false;
    return std::popcount(firstHash ^ secondHash) <= MAXIMUM_DIFFERENCE;
}

QVector<QPair<int, int>> SimilarShots::runsOf(const QVector<MediaItem> &items) const
{
    QVector<QPair<int, int>> runs;
    if (m_hashes.isEmpty())
        return runs;
    int start = 0;
    for (int index = 1; index <= items.size(); ++index) {
        const bool isLinked = index < items.size() && isAlike(items.at(index - 1), items.at(index));
        if (isLinked)
            continue;
        if (index - start >= 2)
            runs.append({start, index - 1});
        start = index;
    }
    return runs;
}

void SimilarShots::index(const QVector<MediaItem> &items)
{
    load();
    if (m_watcher.isRunning()) {
        m_waiting = items;
        return;
    }
    // Only photos with another photo within the window are worth hashing.
    QVector<MediaItem> unread;
    for (int index = 0; index < items.size(); ++index) {
        const MediaItem &item = items.at(index);
        if (item.isVideo || m_hashes.contains(keyOf(item)))
            continue;
        const MediaItem *before = index > 0 ? &items.at(index - 1) : nullptr;
        const MediaItem *after = index + 1 < items.size() ? &items.at(index + 1) : nullptr;
        if (isClose(before, &item) || isClose(&item, after))
            unread.append(item);
    }
    if (unread.isEmpty())
        return;
    m_watcher.setFuture(QtConcurrent::run([unread] {
        QHash<QString, quint64> hashes;
        const QVector<QPair<QString, quint64>> read = QtConcurrent::blockingMapped<QVector<QPair<QString, quint64>>>(unread, [](const MediaItem &item) {
            return QPair<QString, quint64>(SimilarShots::keyOf(item), hashOf(item.path));
        });
        for (const auto &pair : read)
            hashes.insert(pair.first, pair.second);
        return hashes;
    }));
}

void SimilarShots::load()
{
    if (m_isLoaded)
        return;
    m_isLoaded = true;
    QFile file(filePath());
    if (!file.open(QIODevice::ReadOnly))
        return;
    QDataStream stream(&file);
    quint32 magic = 0;
    stream >> magic;
    if (magic == FILE_MAGIC)
        stream >> m_hashes;
}

void SimilarShots::save() const
{
    QDir().mkpath(QFileInfo(filePath()).absolutePath());
    QSaveFile file(filePath());
    if (!file.open(QIODevice::WriteOnly))
        return;
    QDataStream stream(&file);
    stream << FILE_MAGIC << m_hashes;
    file.commit();
}
