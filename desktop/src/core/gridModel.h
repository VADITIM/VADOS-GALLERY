#pragma once

#include "mediaItem.h"

#include <QAbstractListModel>
#include <QDate>
#include <QQmlParserStatus>
#include <QVector>
#include <QtQml/qqmlregistration.h>

// Every photo grid: the items of one source cut into rows, with a header in front of the first photo of each year, month, week and day it is cut into (docs/SPEC.md, Photo layout).
// A row is either a header or one line of tiles; the viewer, the selection and review read the same items through it, so an index means the same photo everywhere.
class MediaGridModel : public QAbstractListModel, public QQmlParserStatus {
    Q_OBJECT
    Q_INTERFACES(QQmlParserStatus)
    QML_ELEMENT

    Q_PROPERTY(QString source READ source WRITE setSource NOTIFY sourceChanged)
    Q_PROPERTY(int columns READ columns WRITE setColumns NOTIFY columnsChanged)
    Q_PROPERTY(QStringList dateGroups READ dateGroups WRITE setDateGroups NOTIFY layoutChanged)
    Q_PROPERTY(bool hasHeaders READ hasHeaders WRITE setHasHeaders NOTIFY layoutChanged)
    Q_PROPERTY(bool hasDayStamps READ hasDayStamps WRITE setHasDayStamps NOTIFY layoutChanged)
    Q_PROPERTY(bool isFavoritesOnly READ isFavoritesOnly WRITE setFavoritesOnly NOTIFY layoutChanged)
    // Leaves out what is already in a folder: adding to an album offers only what is not there yet.
    Q_PROPERTY(QString excludedFolder READ excludedFolder WRITE setExcludedFolder NOTIFY layoutChanged)
    Q_PROPERTY(int count READ count NOTIFY rebuilt)
    Q_PROPERTY(int revision READ revision NOTIFY rebuilt)
    Q_PROPERTY(bool isTrash READ isTrash NOTIFY sourceChanged)

public:
    enum Role { KindRole = Qt::UserRole + 1, LevelRole, LabelRole, FirstRole, TileCountRole, KeyRole };
    Q_ENUM(Role)

    explicit MediaGridModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;
    void classBegin() override {}
    void componentComplete() override;

    QString source() const { return m_source; }
    void setSource(const QString &source);
    int columns() const { return m_columns; }
    void setColumns(int columns);
    QStringList dateGroups() const { return m_dateGroups; }
    void setDateGroups(const QStringList &groups);
    bool hasHeaders() const { return m_hasHeaders; }
    void setHasHeaders(bool hasHeaders);
    bool hasDayStamps() const { return m_hasDayStamps; }
    void setHasDayStamps(bool hasDayStamps);
    bool isFavoritesOnly() const { return m_isFavoritesOnly; }
    void setFavoritesOnly(bool isFavoritesOnly);
    QString excludedFolder() const { return m_excludedFolder; }
    void setExcludedFolder(const QString &folder);
    int count() const { return int(m_items.size()); }
    int revision() const { return m_revision; }
    bool isTrash() const { return m_source == QLatin1String("trash") || m_source == QLatin1String("private-trash"); }

    const QVector<MediaItem> &items() const { return m_items; }

    Q_INVOKABLE QVariantMap tile(int index) const;
    Q_INVOKABLE QVariantMap item(int index) const;
    Q_INVOKABLE QString pathAt(int index) const;
    Q_INVOKABLE int indexOfPath(const QString &path) const;
    Q_INVOKABLE int rowOfItem(int index) const;
    Q_INVOKABLE QStringList paths(const QVariantList &indexes) const;
    Q_INVOKABLE QStringList allPaths() const;
    // The month of the photos at the top of a row, as the top pill shows it (October 26), and how many photos of this grid were taken in it.
    Q_INVOKABLE QVariantMap monthAtRow(int row) const;
    // Every month in the grid with the row it starts on, oldest first, for the timeline.
    Q_INVOKABLE QVariantList months() const;
    Q_INVOKABLE void reload();

signals:
    void sourceChanged();
    void columnsChanged();
    void layoutChanged();
    // Around every rebuild, so the grid can keep the photo it was showing where it was.
    void aboutToRebuild();
    void rebuilt();

private:
    struct Row {
        int kind = 0;
        QString level;
        QString label;
        QString key;
        int first = 0;
        int tileCount = 0;
    };
    void rebuild();
    void scheduleRebuild();

    QString m_source;
    int m_columns = 3;
    QStringList m_dateGroups;
    bool m_hasHeaders = true;
    bool m_hasDayStamps = false;
    bool m_isFavoritesOnly = false;
    QString m_excludedFolder;
    bool m_isComplete = false;
    bool m_isRebuildScheduled = false;
    int m_revision = 0;

    QVector<MediaItem> m_items;
    QVector<Row> m_rows;
    QVector<int> m_rowOfItem;
    QVector<QDate> m_stampOfItem;
    QHash<QString, int> m_indexOfPath;
    QHash<int, int> m_monthCounts;
};
