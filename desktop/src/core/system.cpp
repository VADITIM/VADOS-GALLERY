#include "system.h"

#include "clipDevice.h"
#include "mediaFacts.h"

#include <QClipboard>
#include <QCoreApplication>
#include <QDBusConnection>
#include <QDBusMessage>
#include <QDateTime>
#include <QDesktopServices>
#include <QDir>
#include <QDrag>
#include <QFile>
#include <QFileInfo>
#include <QGuiApplication>
#include <QImageReader>
#include <QLocale>
#include <QMimeData>
#include <QMimeDatabase>
#include <QPixmap>
#include <QProcess>
#include <QSaveFile>
#include <QStandardPaths>
#include <QUrl>

#ifdef GALLERY_HAS_MULTIMEDIA
#include <QMediaPlayer>
#endif

System::System(QObject *parent)
    : QObject(parent)
{
    // GNOME's switch is the one the GTK side of this desktop already honours, so the app follows it rather than inventing a second one.
    QProcess gsettings;
    gsettings.start(QStringLiteral("gsettings"), {QStringLiteral("get"), QStringLiteral("org.gnome.desktop.interface"), QStringLiteral("enable-animations")});
    if (gsettings.waitForFinished(600))
        m_prefersReducedMotion = gsettings.readAllStandardOutput().trimmed() == "false";
}

bool System::hasVideoPlayback() const
{
#ifdef GALLERY_HAS_MULTIMEDIA
    return true;
#else
    return false;
#endif
}

QString System::version() const
{
    return QCoreApplication::applicationVersion();
}

// #region ── sharing ─────────────────────────────────────────────────────────────────────────

void System::share(const QStringList &paths)
{
    if (paths.isEmpty())
        return;
    auto *data = new QMimeData;
    QList<QUrl> urls;
    QByteArray gnome = "copy";
    for (const QString &path : paths) {
        urls.append(QUrl::fromLocalFile(path));
        gnome += '\n' + QUrl::fromLocalFile(path).toEncoded();
    }
    data->setUrls(urls);
    data->setData(QStringLiteral("x-special/gnome-copied-files"), gnome);
    if (paths.size() == 1) {
        QImageReader reader(paths.first());
        reader.setAutoTransform(true);
        const QImage image = reader.read();
        if (!image.isNull())
            data->setImageData(image);
    }
    QGuiApplication::clipboard()->setMimeData(data);
}

void System::showInFolder(const QStringList &paths)
{
    QStringList uris;
    for (const QString &path : paths)
        uris.append(QString::fromUtf8(QUrl::fromLocalFile(path).toEncoded()));
    QDBusMessage call = QDBusMessage::createMethodCall(QStringLiteral("org.freedesktop.FileManager1"), QStringLiteral("/org/freedesktop/FileManager1"),
                                                       QStringLiteral("org.freedesktop.FileManager1"), QStringLiteral("ShowItems"));
    call << uris << QString();
    if (!QDBusConnection::sessionBus().send(call) && !paths.isEmpty())
        QDesktopServices::openUrl(QUrl::fromLocalFile(QFileInfo(paths.first()).absolutePath()));
}

void System::openExternally(const QString &path)
{
    QDesktopServices::openUrl(QUrl::fromLocalFile(path));
}

int System::startDrag(const QStringList &paths, const QString &thumbnail)
{
    if (paths.isEmpty())
        return Qt::IgnoreAction;
    auto *data = new QMimeData;
    QList<QUrl> urls;
    for (const QString &path : paths)
        urls.append(QUrl::fromLocalFile(path));
    data->setUrls(urls);
    auto *drag = new QDrag(qApp);
    drag->setMimeData(data);
    // The picture under the pointer is the photo itself, read small and upright.
    QImageReader reader(thumbnail);
    reader.setAutoTransform(true);
    const QSize size = reader.size();
    if (size.isValid())
        reader.setScaledSize(size.scaled(96, 96, Qt::KeepAspectRatio));
    const QPixmap picture = QPixmap::fromImage(reader.read());
    if (!picture.isNull()) {
        drag->setPixmap(picture);
        drag->setHotSpot(QPoint(picture.width() / 2, picture.height() / 2));
    }
    return drag->exec(Qt::CopyAction | Qt::MoveAction | Qt::LinkAction, Qt::CopyAction);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── facts ───────────────────────────────────────────────────────────────────────────

QVariantMap System::details(const QString &path) const
{
    if (path.isEmpty())
        return {};
    const QFileInfo info(path);
    return {
        {QStringLiteral("name"), info.fileName()},
        {QStringLiteral("folder"), info.absolutePath()},
        {QStringLiteral("size"), formatSize(info.size())},
        {QStringLiteral("type"), QMimeDatabase().mimeTypeForFile(info).comment()},
    };
}

QString System::formatSize(qint64 bytes) const
{
    return QLocale(QLocale::English).formattedDataSize(bytes, 1, QLocale::DataSizeTraditionalFormat);
}

// The date as the viewer shows it: weekday, day, month, year, time and the calendar week.
QString System::formatDate(qint64 milliseconds) const
{
    const QDateTime when = QDateTime::fromMSecsSinceEpoch(milliseconds);
    return QLocale(QLocale::English).toString(when, QStringLiteral("ddd dd MMM yyyy · HH:mm")).toUpper() + QStringLiteral(" · CW%1").arg(when.date().weekNumber());
}

QString System::formatDuration(qint64 milliseconds, bool hasHundredths) const
{
    const qint64 total = qMax<qint64>(0, milliseconds);
    const qint64 seconds = total / 1000;
    QString text = seconds >= 3600
        ? QStringLiteral("%1:%2:%3").arg(seconds / 3600).arg((seconds / 60) % 60, 2, 10, QLatin1Char('0')).arg(seconds % 60, 2, 10, QLatin1Char('0'))
        : QStringLiteral("%1:%2").arg(seconds / 60).arg(seconds % 60, 2, 10, QLatin1Char('0'));
    if (hasHundredths)
        text += QStringLiteral(".%1").arg((total % 1000) / 10, 2, 10, QLatin1Char('0'));
    return text;
}

QStringList System::localPaths(const QVariantList &urls) const
{
    QStringList paths;
    for (const QVariant &url : urls) {
        const QUrl parsed = url.toUrl();
        if (parsed.isLocalFile())
            paths.append(parsed.toLocalFile());
    }
    return paths;
}

bool System::exists(const QString &path) const
{
    return QFileInfo::exists(path);
}

QString System::fileUrl(const QString &path) const
{
    return QUrl::fromLocalFile(path).toString();
}

QString System::saveFrame(const QString &videoPath, qint64 positionMs, qint64 videoTimestamp)
{
    const QString ffmpeg = QStandardPaths::findExecutable(QStringLiteral("ffmpeg"));
    if (ffmpeg.isEmpty())
        return {};
    const QFileInfo video(videoPath);
    QString target = video.absolutePath() + QLatin1Char('/') + video.completeBaseName() + QStringLiteral("_frame_%1.jpg").arg(positionMs);
    QProcess process;
    process.start(ffmpeg, {QStringLiteral("-y"), QStringLiteral("-v"), QStringLiteral("error"), QStringLiteral("-ss"), QString::number(positionMs / 1000.0, 'f', 3),
                           QStringLiteral("-i"), videoPath, QStringLiteral("-frames:v"), QStringLiteral("1"), QStringLiteral("-q:v"), QStringLiteral("2"), target});
    if (!process.waitForFinished(20000) || process.exitCode() != 0)
        return {};
    // Dated at the video's time plus the position, so it sorts beside the video.
    QFile file(target);
    if (file.open(QIODevice::ReadWrite)) {
        file.setFileTime(QDateTime::fromMSecsSinceEpoch(videoTimestamp + positionMs), QFileDevice::FileModificationTime);
        file.close();
    }
    return target;
}

bool System::playClip(QObject *player, const QString &path)
{
#ifdef GALLERY_HAS_MULTIMEDIA
    auto *mediaPlayer = qobject_cast<QMediaPlayer *>(player);
    if (!mediaPlayer)
        return false;
    const MediaFacts::Clip clip = MediaFacts::findClip(path);
    if (clip.length <= 0)
        return false;
    auto *device = new ClipDevice(path, clip.start, clip.length, mediaPlayer);
    if (!device->open(QIODevice::ReadOnly)) {
        delete device;
        return false;
    }
    mediaPlayer->setSourceDevice(device, QUrl(QStringLiteral("clip.mp4")));
    mediaPlayer->play();
    return true;
#else
    Q_UNUSED(player)
    Q_UNUSED(path)
    return false;
#endif
}

bool System::writeTextFile(const QString &path, const QString &text) const
{
    QDir().mkpath(QFileInfo(path).absolutePath());
    QSaveFile file(path);
    if (!file.open(QIODevice::WriteOnly))
        return false;
    file.write(text.toUtf8());
    return file.commit();
}

QString System::readTextFile(const QString &path) const
{
    QFile file(path);
    return file.open(QIODevice::ReadOnly) ? QString::fromUtf8(file.readAll()) : QString();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
