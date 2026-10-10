#include "mediaFacts.h"

#include <QDateTime>
#include <QImageReader>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcess>
#include <QStandardPaths>
#include <QTimeZone>

#include <libexif/exif-data.h>
#include <sys/xattr.h>

namespace {

const QStringList PHOTO_SUFFIXES = {
    QStringLiteral("jpg"), QStringLiteral("jpeg"), QStringLiteral("png"), QStringLiteral("webp"), QStringLiteral("gif"),
    QStringLiteral("bmp"), QStringLiteral("tif"), QStringLiteral("tiff"), QStringLiteral("heic"), QStringLiteral("heif"),
    QStringLiteral("avif"), QStringLiteral("jxl"),
};
const QStringList VIDEO_SUFFIXES = {
    QStringLiteral("mp4"), QStringLiteral("mov"), QStringLiteral("m4v"), QStringLiteral("mkv"), QStringLiteral("webm"),
    QStringLiteral("avi"), QStringLiteral("3gp"),
};
const char *TAGS_ATTRIBUTE = "user.xdg.tags";
const QString FAVORITE_TAG = QStringLiteral("favorite");

QString suffixOf(const QString &fileName)
{
    const int dot = fileName.lastIndexOf(QLatin1Char('.'));
    return dot < 0 ? QString() : fileName.mid(dot + 1).toLower();
}

QString entryText(ExifEntry *entry)
{
    if (!entry)
        return {};
    char buffer[128] = {};
    exif_entry_get_value(entry, buffer, sizeof(buffer));
    return QString::fromUtf8(buffer).trimmed();
}

// Degrees, minutes and seconds as three rationals, signed by the hemisphere letter beside them.
double coordinate(ExifContent *gps, ExifTag valueTag, ExifTag referenceTag, ExifByteOrder order)
{
    ExifEntry *value = exif_content_get_entry(gps, valueTag);
    if (!value || value->format != EXIF_FORMAT_RATIONAL || value->components < 3)
        return std::numeric_limits<double>::quiet_NaN();
    double parts[3] = {};
    for (int index = 0; index < 3; ++index) {
        const ExifRational rational = exif_get_rational(value->data + index * exif_format_get_size(EXIF_FORMAT_RATIONAL), order);
        parts[index] = rational.denominator == 0 ? 0 : double(rational.numerator) / rational.denominator;
    }
    double degrees = parts[0] + parts[1] / 60.0 + parts[2] / 3600.0;
    const QString reference = entryText(exif_content_get_entry(gps, referenceTag));
    if (reference.startsWith(QLatin1Char('S')) || reference.startsWith(QLatin1Char('W')))
        degrees = -degrees;
    return degrees;
}

void readExif(const QString &path, MediaItem &item)
{
    ExifData *data = exif_data_new_from_file(path.toLocal8Bit().constData());
    if (!data)
        return;
    const ExifByteOrder order = exif_data_get_byte_order(data);
    QString taken = entryText(exif_content_get_entry(data->ifd[EXIF_IFD_EXIF], EXIF_TAG_DATE_TIME_ORIGINAL));
    if (taken.isEmpty())
        taken = entryText(exif_content_get_entry(data->ifd[EXIF_IFD_0], EXIF_TAG_DATE_TIME));
    // EXIF keeps the camera's wall clock with no zone, which is what a person remembers it by.
    const QDateTime when = QDateTime::fromString(taken.left(19), QStringLiteral("yyyy:MM:dd HH:mm:ss"));
    if (when.isValid())
        item.timestamp = when.toMSecsSinceEpoch();
    ExifContent *gps = data->ifd[EXIF_IFD_GPS];
    if (gps && gps->count > 0) {
        const double latitude = coordinate(gps, static_cast<ExifTag>(EXIF_TAG_GPS_LATITUDE), static_cast<ExifTag>(EXIF_TAG_GPS_LATITUDE_REF), order);
        const double longitude = coordinate(gps, static_cast<ExifTag>(EXIF_TAG_GPS_LONGITUDE), static_cast<ExifTag>(EXIF_TAG_GPS_LONGITUDE_REF), order);
        // 0,0 is what a phone writes when it had no fix.
        if (!std::isnan(latitude) && !std::isnan(longitude) && (latitude != 0 || longitude != 0)) {
            item.latitude = latitude;
            item.longitude = longitude;
        }
    }
    exif_data_unref(data);
}

QStringList readTags(const QString &path)
{
    char buffer[1024];
    const ssize_t length = getxattr(path.toLocal8Bit().constData(), TAGS_ATTRIBUTE, buffer, sizeof(buffer));
    if (length <= 0)
        return {};
    return QString::fromUtf8(buffer, int(length)).split(QLatin1Char(','), Qt::SkipEmptyParts);
}

} // namespace

namespace MediaFacts {

bool isMedia(const QString &fileName)
{
    const QString suffix = suffixOf(fileName);
    return PHOTO_SUFFIXES.contains(suffix) || VIDEO_SUFFIXES.contains(suffix);
}

bool isVideo(const QString &fileName)
{
    return VIDEO_SUFFIXES.contains(suffixOf(fileName));
}

MediaItem read(const QFileInfo &info)
{
    MediaItem item;
    item.path = info.absoluteFilePath();
    item.folder = info.absolutePath();
    item.size = info.size();
    item.modified = info.lastModified().toMSecsSinceEpoch();
    item.timestamp = item.modified;
    item.isVideo = isVideo(info.fileName());
    if (!item.isVideo) {
        QImageReader reader(item.path);
        const QSize stored = reader.size();
        const bool isTurned = reader.transformation() & QImageIOHandler::TransformationRotate90;
        item.width = isTurned ? stored.height() : stored.width();
        item.height = isTurned ? stored.width() : stored.height();
        const QString suffix = suffixOf(info.fileName());
        if (suffix == QLatin1String("jpg") || suffix == QLatin1String("jpeg") || suffix == QLatin1String("tif") || suffix == QLatin1String("tiff"))
            readExif(item.path, item);
    }
    item.isFavorite = readFavorite(item.path);
    return item;
}

bool probeVideo(MediaItem &item)
{
    static const QString ffprobe = QStandardPaths::findExecutable(QStringLiteral("ffprobe"));
    if (ffprobe.isEmpty())
        return false;
    QProcess process;
    process.start(ffprobe, {QStringLiteral("-v"), QStringLiteral("error"), QStringLiteral("-print_format"), QStringLiteral("json"),
                            QStringLiteral("-select_streams"), QStringLiteral("v:0"),
                            QStringLiteral("-show_entries"), QStringLiteral("format=duration:format_tags=creation_time:stream=width,height:stream_tags=rotate:stream_side_data=rotation"),
                            item.path});
    if (!process.waitForFinished(6000) || process.exitCode() != 0)
        return false;
    const QJsonObject root = QJsonDocument::fromJson(process.readAllStandardOutput()).object();
    const QJsonObject format = root.value(QStringLiteral("format")).toObject();
    item.durationMs = qint64(format.value(QStringLiteral("duration")).toString().toDouble() * 1000.0);
    const QDateTime created = QDateTime::fromString(format.value(QStringLiteral("tags")).toObject().value(QStringLiteral("creation_time")).toString(), Qt::ISODateWithMs);
    // A creation time of the epoch is a camera that never set its clock.
    if (created.isValid() && created.toSecsSinceEpoch() > 86400)
        item.timestamp = created.toMSecsSinceEpoch();
    const QJsonArray streams = root.value(QStringLiteral("streams")).toArray();
    if (!streams.isEmpty()) {
        const QJsonObject stream = streams.first().toObject();
        int rotation = stream.value(QStringLiteral("tags")).toObject().value(QStringLiteral("rotate")).toString().toInt();
        for (const QJsonValue &side : stream.value(QStringLiteral("side_data_list")).toArray())
            if (side.toObject().contains(QStringLiteral("rotation")))
                rotation = side.toObject().value(QStringLiteral("rotation")).toInt();
        const bool isTurned = qAbs(rotation) % 180 == 90;
        const int width = stream.value(QStringLiteral("width")).toInt();
        const int height = stream.value(QStringLiteral("height")).toInt();
        item.width = isTurned ? height : width;
        item.height = isTurned ? width : height;
    }
    return true;
}

bool readFavorite(const QString &path)
{
    return readTags(path).contains(FAVORITE_TAG);
}

bool writeFavorite(const QString &path, bool isFavorite)
{
    QStringList tags = readTags(path);
    tags.removeAll(FAVORITE_TAG);
    if (isFavorite)
        tags.append(FAVORITE_TAG);
    const QByteArray localPath = path.toLocal8Bit();
    if (tags.isEmpty())
        return removexattr(localPath.constData(), TAGS_ATTRIBUTE) == 0 || errno == ENODATA;
    const QByteArray value = tags.join(QLatin1Char(',')).toUtf8();
    return setxattr(localPath.constData(), TAGS_ATTRIBUTE, value.constData(), size_t(value.size()), 0) == 0;
}

} // namespace MediaFacts
