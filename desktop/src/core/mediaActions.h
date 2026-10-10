#pragma once

#include <QObject>
#include <QStringList>
#include <QtQml/qqmlregistration.h>

#include <functional>

class QQmlEngine;
class QJSEngine;

// Every change to a photo goes through here: favourite, trash, restore, delete for good, move, and in and out of Private (the desktop's MediaActions.kt).
// Each reversible change offers its undo in the pill above the bar; deleting for good and hiding into Private are confirmed before they run, and have none.
class MediaActions : public QObject {
    Q_OBJECT
    QML_NAMED_ELEMENT(Actions)
    QML_SINGLETON

    Q_PROPERTY(bool canUndo READ canUndo NOTIFY undoChanged)

public:
    static MediaActions *instance();
    static MediaActions *create(QQmlEngine *, QJSEngine *);

    bool canUndo() const { return bool(m_undo); }

    Q_INVOKABLE void setFavorite(const QStringList &paths, bool isFavorite);
    Q_INVOKABLE void toggleFavorite(const QString &path);
    Q_INVOKABLE void trash(const QStringList &paths);
    Q_INVOKABLE void restore(const QStringList &trashPaths);
    Q_INVOKABLE void restoreTo(const QStringList &trashPaths, const QString &folder);
    Q_INVOKABLE void deleteForever(const QStringList &paths);
    Q_INVOKABLE void emptyTrash();
    Q_INVOKABLE void move(const QStringList &paths, const QString &folder);
    // A new album is a folder under the pictures folder; it shows once something is in it.
    Q_INVOKABLE QString createAlbum(const QString &name);
    Q_INVOKABLE void deleteAlbum(const QString &folder);
    Q_INVOKABLE void renameAlbum(const QString &folder, const QString &name);
    Q_INVOKABLE void setCover(const QString &albumKey, const QString &path);
    // Favorites' own albums: a photo is in one at most, so adding to one takes it out of any other.
    Q_INVOKABLE void addToFavoriteAlbum(const QStringList &paths, const QString &name);
    Q_INVOKABLE void removeFavoriteAlbums(const QStringList &names);
    Q_INVOKABLE void renameFavoriteAlbum(const QString &from, const QString &to);

    Q_INVOKABLE void hide(const QStringList &paths, const QString &group);
    Q_INVOKABLE void hideAlbum(const QString &folder, const QString &group);
    Q_INVOKABLE void unhide(const QStringList &paths, const QString &folder);
    Q_INVOKABLE void moveToGroup(const QStringList &paths, const QString &group);
    Q_INVOKABLE void trashPrivate(const QStringList &paths);
    Q_INVOKABLE void restorePrivate(const QStringList &trashPaths, const QString &group = QString());
    Q_INVOKABLE void deletePrivateForever(const QStringList &trashPaths);
    Q_INVOKABLE void emptyPrivateTrash();
    Q_INVOKABLE QString createGroup(const QString &name);
    Q_INVOKABLE void renameGroup(const QString &from, const QString &to);
    Q_INVOKABLE void deleteGroup(const QString &name);
    Q_INVOKABLE void moveGroupOut(const QString &name, const QString &folder);

    Q_INVOKABLE void undo();

signals:
    // What happened, for the pill above the bar; `canUndo` says whether it carries an undo button.
    void offered(const QString &message, bool isUndoable);
    void failed(const QString &message);
    void undoChanged();

private:
    // Rename where it can; across disks, copy, check the size, and only then remove the original.
    static QString moveFile(const QString &source, const QString &folder);
    static QString freeName(const QString &folder, const QString &fileName);
    void offer(const QString &message, std::function<void()> undo);
    void afterLibraryChange(const QStringList &folders);
    void afterVaultChange();
    // A favourite moved on disk stays in the Favorites album it was in.
    static void followMovedPaths(const QList<QPair<QString, QString>> &moved);

    std::function<void()> m_undo;

private:
    // Private, so the engine takes the shared instance from create() instead of constructing one of its own.
    explicit MediaActions(QObject *parent = nullptr);
};
