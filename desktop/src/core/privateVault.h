#pragma once

#include "mediaItem.h"

#include <QHash>
#include <QObject>
#include <QSet>
#include <QVector>
#include <QtQml/qqmlregistration.h>

class QQmlEngine;
class QJSEngine;

// Private: as many hidden albums ("groups") as wanted, in plain files under ~/.vados-private/<group>/, outside every library folder and hidden, so no other gallery lists them.
// It keeps its own favourites (.favorites) and its own trash (.trash), so nothing private ever reaches the library's favourites or the shared trash.
// The phone unlocks it with the fingerprint; the desktop with a PIN set on first use, kept salted and hashed in the vault itself so it survives a reinstall like the photos do.
class PrivateVault : public QObject {
    Q_OBJECT
    QML_NAMED_ELEMENT(Vault)
    QML_SINGLETON

    Q_PROPERTY(bool isUnlocked READ isUnlocked NOTIFY lockChanged)
    Q_PROPERTY(bool hasPin READ hasPin NOTIFY lockChanged)
    Q_PROPERTY(int revision READ revision NOTIFY changed)
    Q_PROPERTY(QString root READ root CONSTANT)

public:
    static PrivateVault *instance();
    static PrivateVault *create(QQmlEngine *, QJSEngine *);

    bool isUnlocked() const { return m_isUnlocked; }
    bool hasPin() const;
    int revision() const { return m_revision; }
    QString root() const { return m_root; }

    Q_INVOKABLE bool unlock(const QString &pin);
    Q_INVOKABLE void setPin(const QString &pin);
    Q_INVOKABLE void lock();

    Q_INVOKABLE QVariantList groups() const;
    Q_INVOKABLE QStringList groupNames() const;
    Q_INVOKABLE int trashCount() const { return m_isUnlocked ? int(m_trash.size()) : 0; }
    Q_INVOKABLE QVariantMap todaysSelection() const;
    Q_INVOKABLE void pickTodaysSelection();

    QVector<MediaItem> itemsFor(const QString &source) const;
    QVariantMap item(const QString &path) const;
    bool contains(const QString &path) const { return path.startsWith(m_root + QLatin1Char('/')); }
    QString groupFolder(const QString &group) const { return m_root + QLatin1Char('/') + group; }
    QString groupOf(const QString &path) const;

    // Used by MediaActions, which owns every change to a photo.
    bool isFavorite(const QString &path) const { return m_favorites.contains(relative(path)); }
    void setFavorite(const QString &path, bool isFavorite);
    QString createGroup(const QString &name);
    bool renameGroup(const QString &from, const QString &to);
    QString trashFolder() const { return m_root + QStringLiteral("/.trash"); }
    void recordTrashed(const QString &trashPath, const QString &group, bool wasFavorite);
    QVariantMap trashRecord(const QString &trashPath) const;
    void forgetTrashed(const QString &trashPath);
    void reload();

signals:
    void lockChanged();
    void changed();

private:
    QString relative(const QString &path) const;
    void readFavorites();
    void writeFavorites() const;
    void readTrashIndex();
    void writeTrashIndex() const;
    void purgeOldTrash();

    QString m_root;
    bool m_isUnlocked = false;
    int m_revision = 0;
    QHash<QString, QVector<MediaItem>> m_groups;
    QVector<MediaItem> m_trash;
    QSet<QString> m_favorites;
    QHash<QString, QVariantMap> m_trashIndex;
    QString m_todaysSelection;

private:
    // Private, so the engine takes the shared instance from create() instead of constructing one of its own.
    explicit PrivateVault(QObject *parent = nullptr);
};
