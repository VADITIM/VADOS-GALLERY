#include "galleryService.h"

#include <QDBusConnection>
#include <QDBusConnectionInterface>
#include <QDBusMessage>
#include <QDBusReply>
#include <QJSEngine>

namespace {

const QString SERVICE = QStringLiteral("org.vados.Gallery");
const QString PATH = QStringLiteral("/org/vados/Gallery");

} // namespace

GalleryService::GalleryService(QObject *parent)
    : QObject(parent)
{
}

GalleryService *GalleryService::instance()
{
    static GalleryService *service = new GalleryService;
    return service;
}

GalleryService *GalleryService::create(QQmlEngine *, QJSEngine *engine)
{
    engine->setObjectOwnership(instance(), QJSEngine::CppOwnership);
    return instance();
}

bool GalleryService::registerOnBus()
{
    QDBusConnection bus = QDBusConnection::sessionBus();
    if (!bus.isConnected())
        return true;
    if (!bus.registerService(SERVICE))
        return false;
    bus.registerObject(PATH, this, QDBusConnection::ExportScriptableSlots);
    return true;
}

bool GalleryService::forward(const QString &path, const QStringList &siblings)
{
    QDBusConnection bus = QDBusConnection::sessionBus();
    if (!bus.isConnected() || !bus.interface()->isServiceRegistered(SERVICE))
        return false;
    QDBusMessage call = path.isEmpty() ? QDBusMessage::createMethodCall(SERVICE, PATH, SERVICE, QStringLiteral("Show"))
                                       : QDBusMessage::createMethodCall(SERVICE, PATH, SERVICE, QStringLiteral("Open"));
    if (!path.isEmpty())
        call << path << siblings;
    const QDBusMessage reply = bus.call(call, QDBus::Block, 3000);
    return reply.type() == QDBusMessage::ReplyMessage;
}

QString GalleryService::takePendingOpen()
{
    const QString path = m_pendingPath;
    m_pendingPath.clear();
    return path;
}

void GalleryService::Open(const QString &path, const QStringList &siblings)
{
    emit openRequested(path, siblings);
}

void GalleryService::Show()
{
    emit showRequested();
}
