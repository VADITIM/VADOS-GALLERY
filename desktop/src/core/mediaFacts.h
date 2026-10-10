#pragma once

#include "mediaItem.h"

#include <QFileInfo>
#include <QString>

// What a file says about itself: whether it is a photo or a video, when it was taken, its upright size, where, and whether it is a favourite.
namespace MediaFacts {

bool isMedia(const QString &fileName);
bool isVideo(const QString &fileName);

// The quick facts: the file's own dates and size, the picture's header and its EXIF. Videos get their length and date from ffprobe later (probeVideo), since that starts a process.
MediaItem read(const QFileInfo &info);
// Fills a video's length, creation time and upright size; false when ffprobe is missing or the file is not readable.
bool probeVideo(MediaItem &item);

// Where a motion photo's clip lies inside its file: its first byte and its length; a length of 0 when there is none.
struct Clip {
    qint64 start = 0;
    qint64 length = 0;
};
Clip findClip(const QString &path);

// A favourite is the tag "favorite" in user.xdg.tags, the freedesktop attribute other file managers read, so it travels with the file through any rename or move on the same disk.
bool readFavorite(const QString &path);
bool writeFavorite(const QString &path, bool isFavorite);

} // namespace MediaFacts
