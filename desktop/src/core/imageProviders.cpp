#include "imageProviders.h"

#include "privateVault.h"

#include <QCryptographicHash>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QImageReader>
#include <QPainter>
#include <QProcess>
#include <QRunnable>
#include <QStandardPaths>
#include <QSvgRenderer>
#include <QUrl>
#include <QUrlQuery>

namespace {

// The freedesktop buckets: a grid of few columns asks for a larger one, so a big tile is never a stretched small picture.
struct Bucket {
    const char *name;
    int edge;
};
constexpr Bucket BUCKETS[] = {{"normal", 128}, {"large", 256}, {"x-large", 512}, {"xx-large", 1024}};

Bucket bucketFor(const QSize &requested)
{
    const int edge = qMax(requested.width(), requested.height());
    for (const Bucket &bucket : BUCKETS)
        if (edge <= bucket.edge)
            return bucket;
    return BUCKETS[3];
}

QString thumbnailRoot()
{
    return QStandardPaths::writableLocation(QStandardPaths::GenericCacheLocation) + QStringLiteral("/thumbnails");
}

QImage readCached(const QString &cachePath, qint64 modifiedSeconds)
{
    QImageReader reader(cachePath);
    if (!reader.canRead())
        return {};
    // A thumbnail older than its file is a picture of something that is no longer there.
    if (reader.text(QStringLiteral("Thumb::MTime")).toLongLong() != modifiedSeconds)
        return {};
    return reader.read();
}

// Decoded small and upright, straight from the file; a video's frame comes from ffmpeg, read from its output pipe so nothing is written beside it.
QImage decode(const QString &path, int edge)
{
    QImage image;
    QImageReader reader(path);
    reader.setAutoTransform(true);
    if (reader.canRead()) {
        QSize original = reader.size();
        if (reader.transformation() & QImageIOHandler::TransformationRotate90)
            original.transpose();
        if (original.isValid() && (original.width() > edge || original.height() > edge)) {
            QSize scaled = original.scaled(edge, edge, Qt::KeepAspectRatio);
            // The reader scales before it turns, so it is asked for the stored shape.
            if (reader.transformation() & QImageIOHandler::TransformationRotate90)
                scaled.transpose();
            reader.setScaledSize(scaled);
        }
        image = reader.read();
        return image;
    }
    static const QString ffmpeg = QStandardPaths::findExecutable(QStringLiteral("ffmpeg"));
    if (ffmpeg.isEmpty())
        return {};
    for (const QString &position : {QStringLiteral("1"), QStringLiteral("0")}) {
        QProcess process;
        process.start(ffmpeg, {QStringLiteral("-v"), QStringLiteral("error"), QStringLiteral("-ss"), position, QStringLiteral("-i"), path,
                               QStringLiteral("-frames:v"), QStringLiteral("1"),
                               QStringLiteral("-vf"), QStringLiteral("scale='if(gt(iw,ih),%1,-2)':'if(gt(iw,ih),-2,%1)'").arg(edge),
                               QStringLiteral("-f"), QStringLiteral("image2pipe"), QStringLiteral("-vcodec"), QStringLiteral("png"), QStringLiteral("-")});
        if (!process.waitForFinished(10000) || process.exitCode() != 0)
            continue;
        image.loadFromData(process.readAllStandardOutput(), "PNG");
        if (!image.isNull())
            break;
    }
    return image;
}

QImage generate(const QString &path, const QString &uri, qint64 modifiedSeconds, const QString &cachePath, int edge)
{
    QImage image = decode(path, edge);
    if (image.isNull())
        return {};
    image.setText(QStringLiteral("Thumb::URI"), uri);
    image.setText(QStringLiteral("Thumb::MTime"), QString::number(modifiedSeconds));
    image.setText(QStringLiteral("Thumb::Size"), QString::number(QFileInfo(path).size()));
    QDir().mkpath(QFileInfo(cachePath).absolutePath());
    const QString temporary = cachePath + QStringLiteral(".tmp");
    if (image.save(temporary, "PNG")) {
        QFile::setPermissions(temporary, QFileDevice::ReadOwner | QFileDevice::WriteOwner);
        QFile::remove(cachePath);
        QFile::rename(temporary, cachePath);
    }
    return image;
}

class ThumbnailResponse : public QQuickImageResponse, public QRunnable {
public:
    ThumbnailResponse(QString path, QSize requestedSize, bool isPrivate)
        : m_path(std::move(path))
        , m_requestedSize(requestedSize)
        , m_isPrivate(isPrivate)
    {
        setAutoDelete(false);
    }

    QQuickTextureFactory *textureFactory() const override { return QQuickTextureFactory::textureFactoryForImage(m_image); }
    QString errorString() const override { return m_image.isNull() ? QStringLiteral("no thumbnail") : QString(); }

    void run() override
    {
        const QFileInfo info(m_path);
        if (info.exists() && !m_path.startsWith(thumbnailRoot())) {
            const Bucket bucket = bucketFor(m_requestedSize.isValid() ? m_requestedSize : QSize(256, 256));
            if (m_isPrivate) {
                // Nothing private is written to a cache: decoded in memory, every time.
                m_image = decode(m_path, bucket.edge);
            } else {
                const QString uri = QString::fromUtf8(QUrl::fromLocalFile(m_path).toEncoded());
                const QString hash = QString::fromLatin1(QCryptographicHash::hash(uri.toUtf8(), QCryptographicHash::Md5).toHex());
                const qint64 modifiedSeconds = info.lastModified().toSecsSinceEpoch();
                // The bucket asked for, else any larger one another app already made.
                for (const Bucket &each : BUCKETS) {
                    if (each.edge < bucket.edge)
                        continue;
                    m_image = readCached(thumbnailRoot() + QLatin1Char('/') + QLatin1String(each.name) + QLatin1Char('/') + hash + QStringLiteral(".png"), modifiedSeconds);
                    if (!m_image.isNull())
                        break;
                }
                if (m_image.isNull())
                    m_image = generate(m_path, uri, modifiedSeconds, thumbnailRoot() + QLatin1Char('/') + QLatin1String(bucket.name) + QLatin1Char('/') + hash + QStringLiteral(".png"), bucket.edge);
            }
            if (!m_image.isNull() && m_requestedSize.isValid() && (m_image.width() > m_requestedSize.width() * 1.5 || m_image.height() > m_requestedSize.height() * 1.5))
                m_image = m_image.scaled(m_requestedSize, Qt::KeepAspectRatioByExpanding, Qt::SmoothTransformation);
        }
        emit finished();
    }

private:
    QString m_path;
    QSize m_requestedSize;
    bool m_isPrivate = false;
    QImage m_image;
};

} // namespace

// #region ── thumbnails ──────────────────────────────────────────────────────────────────────

ThumbnailProvider::ThumbnailProvider()
{
    // Decoding is disk- and CPU-bound; a handful at once keeps a fast scroll from queueing hundreds of decodes behind the ones in view.
    m_pool.setMaxThreadCount(qMax(2, QThread::idealThreadCount() / 2));
}

QQuickImageResponse *ThumbnailProvider::requestImageResponse(const QString &id, const QSize &requestedSize)
{
    const QString path = QUrl::fromPercentEncoding(id.toUtf8());
    auto *response = new ThumbnailResponse(path, requestedSize, PrivateVault::instance()->contains(path));
    m_pool.start(response);
    return response;
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── glyphs ──────────────────────────────────────────────────────────────────────────

GlyphProvider::GlyphProvider()
    : QQuickImageProvider(QQuickImageProvider::Image)
{
}

QImage GlyphProvider::requestImage(const QString &id, QSize *size, const QSize &requestedSize)
{
    const QString name = id.section(QLatin1Char('?'), 0, 0);
    const QUrlQuery query(id.section(QLatin1Char('?'), 1));
    const QString ink = QLatin1Char('#') + query.queryItemValue(QStringLiteral("ink"));
    QFile file(QStringLiteral(":/qt/qml/Gallery/assets/glyphs/%1.svg").arg(name));
    if (!file.open(QIODevice::ReadOnly))
        return {};
    QByteArray svg = file.readAll();
    svg.replace("currentColor", ink.toUtf8());
    QSvgRenderer renderer(svg);
    // A glyph that is not square (share, pin) sits centred in its box rather than stretched to it.
    renderer.setAspectRatioMode(Qt::KeepAspectRatio);
    // A glyph laid out before its size is known asks for 0×0; it gets a default size rather than a null image.
    const QSize target = requestedSize.width() > 0 && requestedSize.height() > 0 ? requestedSize : QSize(48, 48);
    QImage image(target, QImage::Format_ARGB32_Premultiplied);
    image.fill(Qt::transparent);
    QPainter painter(&image);
    painter.setRenderHint(QPainter::Antialiasing);
    renderer.render(&painter);
    painter.end();
    if (size)
        *size = target;
    return image;
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
