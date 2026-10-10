#include <QDir>
#include <QDirIterator>
#include <QFileInfo>
#include <QFontDatabase>
#include <QGuiApplication>
#include <QLoggingCategory>
#include <QQmlApplicationEngine>
#include <QQmlComponent>
#include <QQuickStyle>
#include <QUrl>

#include "galleryService.h"
#include "imageProviders.h"
#include "library.h"
#include "privateVault.h"

namespace {

// Fonts are registered once, at the root, never by a component (VAS typography).
void registerFonts()
{
    QDirIterator bundled(QStringLiteral(":/assets/fonts"), {QStringLiteral("*.ttf"), QStringLiteral("*.otf")}, QDir::Files);
    while (bundled.hasNext())
        QFontDatabase::addApplicationFont(bundled.next());
}

QString fileFromArguments(const QStringList &arguments)
{
    for (int index = 1; index < arguments.size(); ++index) {
        const QString argument = arguments.at(index);
        if (argument.startsWith(QLatin1Char('-')))
            continue;
        const QUrl url(argument);
        const QFileInfo info(url.isLocalFile() ? url.toLocalFile() : argument);
        if (info.isFile())
            return info.absoluteFilePath();
    }
    return QString();
}

} // namespace

int main(int argumentCount, char *argumentValues[])
{
    // Hyprland draws no decorations, and Qt's fallback client-side frame is a light bar that is not part of this design.
    qputenv("QT_WAYLAND_DISABLE_WINDOWDECORATION", "1");
    QLoggingCategory::setFilterRules(QStringLiteral("qt.qpa.wayland.textinput=false"));
    QGuiApplication application(argumentCount, argumentValues);
    application.setApplicationName(QStringLiteral("VAD/OS Gallery"));
    application.setApplicationVersion(QStringLiteral(GALLERY_VERSION));
    application.setOrganizationName(QStringLiteral("vados"));
    // The Wayland app_id, so Hyprland window rules can target `class:^(vados-gallery)$`.
    application.setDesktopFileName(QStringLiteral("vados-gallery"));
    QQuickStyle::setStyle(QStringLiteral("Basic"));

    const QStringList arguments = application.arguments();
    const QString file = fileFromArguments(arguments);
    const bool isActivated = arguments.contains(QStringLiteral("--dbus-service"));

    // A second launch hands its file to the gallery already running and leaves.
    GalleryService *service = GalleryService::instance();
    if (!service->registerOnBus()) {
        GalleryService::forward(file, {});
        return 0;
    }
    service->setStartedHidden(isActivated);
    service->setPendingOpen(file);

    registerFonts();
    Library::instance()->setVault(PrivateVault::instance());
    Library::instance()->start();

    QQmlApplicationEngine engine;
    engine.addImageProvider(QStringLiteral("thumbnail"), new ThumbnailProvider);
    engine.addImageProvider(QStringLiteral("glyph"), new GlyphProvider);
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreationFailed, &application, [] { QCoreApplication::exit(1); }, Qt::QueuedConnection);
    engine.loadFromModule("Gallery", "Main");

    // Development only: a scripted run that drives the window and saves frames, for checking motion and layout without anyone's hands on the pointer.
    const QString rehearsal = qEnvironmentVariable("VADOS_REHEARSAL");
    if (!rehearsal.isEmpty() && !engine.rootObjects().isEmpty()) {
        QQmlComponent component(&engine, QUrl::fromLocalFile(rehearsal));
        QObject *script = component.createWithInitialProperties({{QStringLiteral("window"), QVariant::fromValue(engine.rootObjects().first())}});
        if (!script)
            qWarning().noquote() << component.errorString();
    }
    return application.exec();
}
