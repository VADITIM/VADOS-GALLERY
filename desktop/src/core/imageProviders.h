#pragma once

#include <QQuickAsyncImageProvider>
#include <QQuickImageProvider>
#include <QThreadPool>

// Thumbnails through the freedesktop cache (~/.cache/thumbnails), so a folder another file manager already thumbnailed opens with its pictures in place, and the ones made here are reused by them.
class ThumbnailProvider : public QQuickAsyncImageProvider {
public:
    ThumbnailProvider();
    QQuickImageResponse *requestImageResponse(const QString &id, const QSize &requestedSize) override;

private:
    QThreadPool m_pool;
};

// Line glyphs from the bundled SVGs, re-inked per request: `image://glyph/heart?ink=ff3b4e`. A glyph never carries a colour of its own — the caller passes the accent it inherited.
class GlyphProvider : public QQuickImageProvider {
public:
    GlyphProvider();
    QImage requestImage(const QString &id, QSize *size, const QSize &requestedSize) override;
};
