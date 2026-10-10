#pragma once

#include <QObject>
#include <QStringList>
#include <QtQml/qqmlregistration.h>

class QQmlEngine;
class QJSEngine;

// One gallery per session, reachable as org.vados.Gallery on the session bus: VAD/OS Files opens a photo here with the files it was listed between, and a second launch hands its files to the first instead of opening another window.
class GalleryService : public QObject {
    Q_OBJECT
    Q_CLASSINFO("D-Bus Interface", "org.vados.Gallery")
    QML_NAMED_ELEMENT(Service)
    QML_SINGLETON

    // Started by D-Bus activation, the window waits hidden until something is asked of it.
    Q_PROPERTY(bool isStartedHidden READ isStartedHidden CONSTANT)

public:
    static GalleryService *instance();
    static GalleryService *create(QQmlEngine *, QJSEngine *);

    // False when another instance already owns the name.
    bool registerOnBus();
    // Hands a request to the instance that owns the name; false when there is none.
    static bool forward(const QString &path, const QStringList &siblings);

    bool isStartedHidden() const { return m_isStartedHidden; }
    void setStartedHidden(bool isHidden) { m_isStartedHidden = isHidden; }
    // A file named on the command line of the first instance, delivered once QML is ready for it.
    void setPendingOpen(const QString &path) { m_pendingPath = path; }
    Q_INVOKABLE QString takePendingOpen();

public slots:
    Q_SCRIPTABLE void Open(const QString &path, const QStringList &siblings);
    Q_SCRIPTABLE void Show();

signals:
    void openRequested(const QString &path, const QStringList &siblings);
    void showRequested();

private:
    explicit GalleryService(QObject *parent = nullptr);

    bool m_isStartedHidden = false;
    QString m_pendingPath;
};
