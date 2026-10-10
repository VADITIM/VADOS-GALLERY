#pragma once

#include <QObject>
#include <QVariant>
#include <QtQml/qqmlregistration.h>

// Everything that reaches outside the window: sharing (the clipboard, where every desktop chat and editor reads from), showing a file in the file manager, dragging photos out, and the facts the details sheet shows.
class System : public QObject {
    Q_OBJECT
    QML_ELEMENT
    QML_SINGLETON
    Q_PROPERTY(bool prefersReducedMotion READ prefersReducedMotion CONSTANT)
    Q_PROPERTY(bool hasVideoPlayback READ hasVideoPlayback CONSTANT)
    Q_PROPERTY(QString version READ version CONSTANT)

public:
    explicit System(QObject *parent = nullptr);

    bool prefersReducedMotion() const { return m_prefersReducedMotion; }
    bool hasVideoPlayback() const;
    QString version() const;

    // The photos onto the clipboard as files, and a single photo as its picture too, so pasting into a chat sends the photo itself.
    Q_INVOKABLE void share(const QStringList &paths);
    // Asks the file manager (VAD/OS Files, through org.freedesktop.FileManager1) to open the folder with these selected.
    Q_INVOKABLE void showInFolder(const QStringList &paths);
    Q_INVOKABLE void openExternally(const QString &path);
    // Blocks in a nested loop until the drop lands, as every toolkit drag does; answers the Qt.DropAction the target chose.
    Q_INVOKABLE int startDrag(const QStringList &paths, const QString &thumbnail);
    Q_INVOKABLE QVariantMap details(const QString &path) const;
    Q_INVOKABLE QString formatSize(qint64 bytes) const;
    Q_INVOKABLE QString formatDate(qint64 milliseconds) const;
    Q_INVOKABLE QString formatDuration(qint64 milliseconds, bool hasHundredths = false) const;
    Q_INVOKABLE QStringList localPaths(const QVariantList &urls) const;
    Q_INVOKABLE bool exists(const QString &path) const;
    Q_INVOKABLE QString fileUrl(const QString &path) const;
    // A still of a video at a position, saved beside it as a full-size JPEG dated at the video's time plus the position.
    Q_INVOKABLE QString saveFrame(const QString &videoPath, qint64 positionMs, qint64 videoTimestamp);
    Q_INVOKABLE bool writeTextFile(const QString &path, const QString &text) const;
    // Hands a motion photo's clip to a MediaPlayer, read straight out of the photo's file; false when there is no clip or no player.
    Q_INVOKABLE bool playClip(QObject *player, const QString &path);
    Q_INVOKABLE QString readTextFile(const QString &path) const;

private:
    bool m_prefersReducedMotion = false;
};
