#include "mediaEditor.h"

#include "library.h"

#include <QBuffer>
#include <QDateTime>
#include <QFile>
#include <QFileInfo>
#include <QImageReader>
#include <QPainter>
#include <QPainterPath>
#include <QSaveFile>
#include <QTransform>

#include <libexif/exif-data.h>

namespace {

QString freeCopyName(const QString &path)
{
    const QFileInfo info(path);
    const QString base = info.absolutePath() + QLatin1Char('/') + info.completeBaseName() + QStringLiteral("_crop");
    QString candidate = base + QStringLiteral(".jpg");
    for (int number = 2; QFileInfo::exists(candidate); ++number)
        candidate = base + QStringLiteral("_%1.jpg").arg(number);
    return candidate;
}

// The original's EXIF (its date, its place, its camera) carried into the copy, upright now that the turn is in the pixels, and sized to the copy.
QByteArray exifFor(const QString &original, const QSize &size)
{
    ExifData *data = exif_data_new_from_file(original.toLocal8Bit().constData());
    if (!data)
        return {};
    const ExifByteOrder order = exif_data_get_byte_order(data);
    if (ExifEntry *orientation = exif_content_get_entry(data->ifd[EXIF_IFD_0], EXIF_TAG_ORIENTATION))
        exif_set_short(orientation->data, order, 1);
    if (ExifEntry *width = exif_content_get_entry(data->ifd[EXIF_IFD_EXIF], EXIF_TAG_PIXEL_X_DIMENSION)) {
        if (width->format == EXIF_FORMAT_LONG)
            exif_set_long(width->data, order, ExifLong(size.width()));
        else if (width->format == EXIF_FORMAT_SHORT)
            exif_set_short(width->data, order, ExifShort(size.width()));
    }
    if (ExifEntry *height = exif_content_get_entry(data->ifd[EXIF_IFD_EXIF], EXIF_TAG_PIXEL_Y_DIMENSION)) {
        if (height->format == EXIF_FORMAT_LONG)
            exif_set_long(height->data, order, ExifLong(size.height()));
        else if (height->format == EXIF_FORMAT_SHORT)
            exif_set_short(height->data, order, ExifShort(size.height()));
    }
    // The embedded preview would show the uncropped picture.
    exif_data_unset_option(data, EXIF_DATA_OPTION_FOLLOW_SPECIFICATION);
    if (data->ifd[EXIF_IFD_1]) {
        while (data->ifd[EXIF_IFD_1]->count > 0)
            exif_content_remove_entry(data->ifd[EXIF_IFD_1], data->ifd[EXIF_IFD_1]->entries[0]);
    }
    if (data->data) {
        free(data->data);
        data->data = nullptr;
        data->size = 0;
    }
    unsigned char *blob = nullptr;
    unsigned int length = 0;
    exif_data_save_data(data, &blob, &length);
    QByteArray exif(reinterpret_cast<const char *>(blob), int(length));
    free(blob);
    exif_data_unref(data);
    return exif;
}

// A JPEG with an APP1 segment put in right after its start marker.
QByteArray withExif(const QByteArray &jpeg, const QByteArray &exif)
{
    if (exif.isEmpty() || exif.size() > 65000 || jpeg.size() < 2)
        return jpeg;
    QByteArray segment;
    segment.append(char(0xFF));
    segment.append(char(0xE1));
    const int length = int(exif.size()) + 2;
    segment.append(char((length >> 8) & 0xFF));
    segment.append(char(length & 0xFF));
    segment.append(exif);
    return jpeg.left(2) + segment + jpeg.mid(2);
}

} // namespace

MediaEditor::MediaEditor(QObject *parent)
    : QObject(parent)
{
}

QString MediaEditor::saveCrop(const QString &path, double left, double top, double width, double height, int quarterTurns, const QVariantList &strokes)
{
    QImageReader reader(path);
    reader.setAutoTransform(true);
    QImage picture = reader.read();
    if (picture.isNull())
        return {};
    const int turns = ((quarterTurns % 4) + 4) % 4;
    if (turns != 0)
        picture = picture.transformed(QTransform().rotate(90.0 * turns), Qt::SmoothTransformation);
    picture = picture.convertToFormat(QImage::Format_RGB32);

    // The lines go onto the whole turned picture first, so the frame cuts them exactly where it cuts the photo.
    if (!strokes.isEmpty()) {
        QPainter painter(&picture);
        painter.setRenderHint(QPainter::Antialiasing);
        for (const QVariant &value : strokes) {
            const QVariantMap stroke = value.toMap();
            const QVariantList points = stroke.value(QStringLiteral("points")).toList();
            if (points.isEmpty())
                continue;
            QPen pen(QColor(stroke.value(QStringLiteral("color")).toString()));
            pen.setWidthF(qMax(1.0, stroke.value(QStringLiteral("width")).toDouble() * picture.width()));
            pen.setCapStyle(Qt::RoundCap);
            pen.setJoinStyle(Qt::RoundJoin);
            painter.setPen(pen);
            QPainterPath line;
            for (int index = 0; index < points.size(); ++index) {
                const QVariantList point = points.at(index).toList();
                const QPointF at(point.value(0).toDouble() * picture.width(), point.value(1).toDouble() * picture.height());
                if (index == 0)
                    line.moveTo(at);
                else
                    line.lineTo(at);
            }
            if (points.size() == 1)
                line.lineTo(line.currentPosition() + QPointF(0.01, 0));
            painter.drawPath(line);
        }
    }

    const QRect frame = QRectF(left * picture.width(), top * picture.height(), width * picture.width(), height * picture.height()).toAlignedRect().intersected(picture.rect());
    if (frame.isEmpty())
        return {};
    const QImage cropped = picture.copy(frame);

    QByteArray jpeg;
    QBuffer buffer(&jpeg);
    buffer.open(QIODevice::WriteOnly);
    if (!cropped.save(&buffer, "JPEG", 95))
        return {};
    const QString target = freeCopyName(path);
    QSaveFile file(target);
    if (!file.open(QIODevice::WriteOnly))
        return {};
    file.write(withExif(jpeg, exifFor(path, cropped.size())));
    if (!file.commit())
        return {};
    // Dated as the original, so the copy sorts beside it.
    QFile written(target);
    if (written.open(QIODevice::ReadWrite)) {
        written.setFileTime(QFileInfo(path).lastModified(), QFileDevice::FileModificationTime);
        written.close();
    }
    Library::instance()->rescan({QFileInfo(target).absolutePath()});
    return target;
}
