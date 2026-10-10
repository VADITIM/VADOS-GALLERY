#pragma once

#include <QMetaType>
#include <QString>
#include <QVariantMap>

#include <cmath>
#include <limits>

// One photo or video as every screen sees it. Copied freely: the strings are shared, so a copy is a handful of pointers.
struct MediaItem {
    QString path;
    // The folder the file sits in, which is its album; for something in a trash, the folder it was trashed from.
    QString folder;
    // When it was taken (EXIF, the video's creation time), else when the file was last written; milliseconds since the epoch.
    qint64 timestamp = 0;
    qint64 modified = 0;
    qint64 size = 0;
    // Upright, after the EXIF turn, so a frame can take the photo's shape before the photo has decoded.
    int width = 0;
    int height = 0;
    qint64 durationMs = 0;
    bool isVideo = false;
    bool isFavorite = false;
    // Only for something in a trash: when it went there and where it came from.
    qint64 trashedAt = 0;
    QString originalPath;
    double latitude = std::numeric_limits<double>::quiet_NaN();
    double longitude = std::numeric_limits<double>::quiet_NaN();

    bool hasLocation() const { return !std::isnan(latitude) && !std::isnan(longitude); }

    QVariantMap toVariant() const
    {
        return {
            {QStringLiteral("path"), path},
            {QStringLiteral("folder"), folder},
            {QStringLiteral("timestamp"), timestamp},
            {QStringLiteral("size"), size},
            {QStringLiteral("width"), width},
            {QStringLiteral("height"), height},
            {QStringLiteral("durationMs"), durationMs},
            {QStringLiteral("isVideo"), isVideo},
            {QStringLiteral("isFavorite"), isFavorite},
            {QStringLiteral("trashedAt"), trashedAt},
            {QStringLiteral("originalPath"), originalPath},
        };
    }
};

Q_DECLARE_METATYPE(MediaItem)
