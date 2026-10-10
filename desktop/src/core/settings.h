#pragma once

#include <QObject>
#include <QSettings>
#include <QStringList>
#include <QVariantMap>
#include <QtQml/qqmlregistration.h>

class QQmlEngine;
class QJSEngine;

// The one storage wrapper every persisted choice goes through: how things look (one value, or one per view) and how the user arranged things (names, covers, order).
// Stored in ~/.config/vados/gallery.conf. The fresh-install values are the phone's (docs/SPEC.md, Fresh install settings).
class Settings : public QObject {
    Q_OBJECT
    QML_ELEMENT
    QML_SINGLETON

    // Bumped on every per-view or arrangement change, so a binding that reads one of them through a function re-evaluates.
    Q_PROPERTY(int revision READ revision NOTIFY revisionChanged)

#define GALLERY_SETTING(Type, name, Name, fallback)                                                 \
    Q_PROPERTY(Type name READ name WRITE set##Name NOTIFY name##Changed)                             \
public:                                                                                            \
    Type name() const { return m_store.value(QStringLiteral(#name), fallback).value<Type>(); }       \
    void set##Name(const Type &value)                                                              \
    {                                                                                              \
        if (name() == value)                                                                       \
            return;                                                                                \
        m_store.setValue(QStringLiteral(#name), value);                                            \
        emit name##Changed();                                                                      \
    }                                                                                              \
    Q_SIGNAL void name##Changed();                                                                 \
                                                                                                   \
private:

    // Where navigation lives on a desktop: the sidebar, the bubble at the foot (the phone's pop bar), or both.
    GALLERY_SETTING(QString, navigation, Navigation, QStringLiteral("both"))
    GALLERY_SETTING(bool, isFolderLabelTop, IsFolderLabelTop, true)
    GALLERY_SETTING(double, blur, Blur, 50.0)
    GALLERY_SETTING(double, glassOpacity, GlassOpacity, 0.85)
    // The ground's brightness from black (0) to a dark charcoal (1), which is 64 of 255.
    GALLERY_SETTING(double, groundBrightness, GroundBrightness, 17.5 / 64.0)
    GALLERY_SETTING(bool, hasDayStamps, HasDayStamps, false)
    GALLERY_SETTING(bool, isAutoplay, IsAutoplay, true)
    GALLERY_SETTING(bool, isTrashByDeletion, IsTrashByDeletion, false)
    GALLERY_SETTING(bool, isFavoritesAsAlbums, IsFavoritesAsAlbums, false)
    GALLERY_SETTING(bool, hasTodaysSelection, HasTodaysSelection, true)
    GALLERY_SETTING(QStringList, libraryFolders, LibraryFolders, QStringList())
    GALLERY_SETTING(int, windowWidth, WindowWidth, 1400)
    GALLERY_SETTING(int, windowHeight, WindowHeight, 900)
    GALLERY_SETTING(double, sidebarWidthRem, SidebarWidthRem, 11.0)
    GALLERY_SETTING(double, userScale, UserScale, 1.0)

#undef GALLERY_SETTING

public:
    // One instance for C++ and QML alike; the engine is handed it rather than making its own.
    static Settings *instance();
    static Settings *create(QQmlEngine *, QJSEngine *);

    int revision() const { return m_revision; }

    // A setting kept per view (recent, albums, favorites, locations, trash, private), and for an album that has its own settings, per folder.
    Q_INVOKABLE QVariant viewValue(const QString &view, const QString &key) const;
    Q_INVOKABLE void setViewValue(const QString &view, const QString &key, const QVariant &value);
    // An album follows Recent's photo settings until it is given its own.
    Q_INVOKABLE bool hasOwnSettings(const QString &folderKey) const;
    Q_INVOKABLE void setOwnSettings(const QString &folderKey, const QString &view, bool isOwn);
    // The photo-grid settings that apply in a view, through a folder's own when it has them.
    Q_INVOKABLE QVariant gridValue(const QString &view, const QString &folderKey, const QString &key) const;
    Q_INVOKABLE void setGridValue(const QString &view, const QString &folderKey, const QString &key, const QVariant &value);

    // The arrangement: names shown for folders, the cover chosen for each album, and the order albums were dragged into.
    Q_INVOKABLE QString albumName(const QString &folder) const;
    Q_INVOKABLE void setAlbumName(const QString &folder, const QString &name);
    Q_INVOKABLE QString cover(const QString &albumKey) const;
    Q_INVOKABLE void setCover(const QString &albumKey, const QString &path);
    Q_INVOKABLE QStringList albumOrder() const;
    Q_INVOKABLE void setAlbumOrder(const QStringList &order);
    // The order dragged into on a shelf ("albums" by folder, "private" by group name, "favorites" by album name), groups and albums alike; what was never arranged keeps its default order after it.
    Q_INVOKABLE QStringList order(const QString &shelf) const;
    Q_INVOKABLE void setOrder(const QString &shelf, const QStringList &order);
    // The groups of a shelf, kept while grouping is off so turning it back on restores them: [{ name, keys: [album key, …] }].
    Q_INVOKABLE QVariantList stacks(const QString &shelf) const;
    Q_INVOKABLE void setStacks(const QString &shelf, const QVariantList &stacks);
    // The albums made inside Favorites, each holding favourites by path: [{ name, paths: […] }]. They never touch the folders.
    Q_INVOKABLE QVariantList favoriteAlbums() const;
    Q_INVOKABLE void setFavoriteAlbums(const QVariantList &albums);

    // Keeps names, covers and order with a folder that moved (into Private and back, or renamed on disk).
    void followMovedFolder(const QString &from, const QString &to);

    // Every stored value, for the backup; and taking one back.
    Q_INVOKABLE QVariantMap snapshot() const;
    Q_INVOKABLE void restoreSnapshot(const QVariantMap &values);

signals:
    void revisionChanged();

private:
    QVariant fallback(const QString &view, const QString &key) const;
    void bump();

    mutable QSettings m_store;
    int m_revision = 0;

private:
    // Private, so the engine takes the shared instance from create() instead of constructing one of its own.
    explicit Settings(QObject *parent = nullptr);
};
