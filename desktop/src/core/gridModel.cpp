#include "gridModel.h"

#include "library.h"
#include "privateVault.h"
#include "settings.h"
#include "similarShots.h"

#include <QDateTime>
#include <QLocale>
#include <QTimer>

namespace {

constexpr qint64 TRASH_KEEP_MS = 30LL * 24 * 60 * 60 * 1000;

QDate dayOf(qint64 millis)
{
    return QDateTime::fromMSecsSinceEpoch(millis).date();
}

QString weekLabel(const QDate &day)
{
    return QStringLiteral("CW%1").arg(day.weekNumber());
}

// 05/10/26 - CW41
QString dayStamp(const QDate &day)
{
    return day.toString(QStringLiteral("dd/MM/yy")) + QStringLiteral(" - ") + weekLabel(day);
}

QString durationText(qint64 milliseconds)
{
    const qint64 seconds = milliseconds / 1000;
    if (seconds >= 3600)
        return QStringLiteral("%1:%2:%3").arg(seconds / 3600).arg((seconds / 60) % 60, 2, 10, QLatin1Char('0')).arg(seconds % 60, 2, 10, QLatin1Char('0'));
    return QStringLiteral("%1:%2").arg(seconds / 60).arg(seconds % 60, 2, 10, QLatin1Char('0'));
}

} // namespace

MediaGridModel::MediaGridModel(QObject *parent)
    : QAbstractListModel(parent)
{
    m_dateGroups = {QStringLiteral("days"), QStringLiteral("months"), QStringLiteral("years")};
    connect(Library::instance(), &Library::changed, this, &MediaGridModel::scheduleRebuild);
    connect(Settings::instance(), &Settings::isTrashByDeletionChanged, this, [this] {
        if (isTrash())
            scheduleRebuild();
    });
    connect(SimilarShots::instance(), &SimilarShots::changed, this, [this] {
        if (m_isStacking)
            scheduleRebuild();
    });
    connect(PrivateVault::instance(), &PrivateVault::changed, this, [this] {
        if (m_source.startsWith(QLatin1String("private")))
            scheduleRebuild();
    });
}

void MediaGridModel::componentComplete()
{
    m_isComplete = true;
    rebuild();
}

// #region ── properties ──────────────────────────────────────────────────────────────────────

void MediaGridModel::setSource(const QString &source)
{
    if (m_source == source)
        return;
    m_source = source;
    emit sourceChanged();
    if (m_isComplete)
        rebuild();
}

void MediaGridModel::setColumns(int columns)
{
    columns = qBound(1, columns, 6);
    if (m_columns == columns)
        return;
    m_columns = columns;
    emit columnsChanged();
    if (m_isComplete)
        rebuild();
}

void MediaGridModel::setDateGroups(const QStringList &groups)
{
    if (m_dateGroups == groups)
        return;
    m_dateGroups = groups;
    emit layoutChanged();
    scheduleRebuild();
}

void MediaGridModel::setHasHeaders(bool hasHeaders)
{
    if (m_hasHeaders == hasHeaders)
        return;
    m_hasHeaders = hasHeaders;
    emit layoutChanged();
    scheduleRebuild();
}

void MediaGridModel::setHasDayStamps(bool hasDayStamps)
{
    if (m_hasDayStamps == hasDayStamps)
        return;
    m_hasDayStamps = hasDayStamps;
    emit layoutChanged();
    scheduleRebuild();
}

void MediaGridModel::setFavoritesOnly(bool isFavoritesOnly)
{
    if (m_isFavoritesOnly == isFavoritesOnly)
        return;
    m_isFavoritesOnly = isFavoritesOnly;
    emit layoutChanged();
    if (m_isComplete)
        rebuild();
}

void MediaGridModel::setStacking(bool isStacking)
{
    if (m_isStacking == isStacking)
        return;
    m_isStacking = isStacking;
    emit layoutChanged();
    scheduleRebuild();
}

void MediaGridModel::setOpenStacks(const QStringList &stacks)
{
    if (m_openStacks == stacks)
        return;
    m_openStacks = stacks;
    emit layoutChanged();
    scheduleRebuild();
}

void MediaGridModel::setExcludedFolder(const QString &folder)
{
    if (m_excludedFolder == folder)
        return;
    m_excludedFolder = folder;
    emit layoutChanged();
    scheduleRebuild();
}

void MediaGridModel::reload()
{
    rebuild();
}

void MediaGridModel::scheduleRebuild()
{
    if (!m_isComplete || m_isRebuildScheduled)
        return;
    m_isRebuildScheduled = true;
    QTimer::singleShot(0, this, [this] {
        m_isRebuildScheduled = false;
        rebuild();
    });
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── building ────────────────────────────────────────────────────────────────────────

void MediaGridModel::rebuild()
{
    emit aboutToRebuild();
    beginResetModel();
    m_items = m_source.isEmpty() ? QVector<MediaItem>() : Library::instance()->itemsFor(m_source);
    if (m_isFavoritesOnly || !m_excludedFolder.isEmpty()) {
        QVector<MediaItem> kept;
        for (const MediaItem &item : std::as_const(m_items))
            if ((!m_isFavoritesOnly || item.isFavorite) && item.folder != m_excludedFolder)
                kept.append(item);
        m_items = kept;
    }
    m_rows.clear();
    m_tiles.clear();
    m_rowOfTile.clear();
    m_stackPaths.clear();
    m_tileOfItem.assign(m_items.size(), -1);
    m_stampOfItem.assign(m_items.size(), QDate());
    m_indexOfPath.clear();
    m_indexOfPath.reserve(m_items.size());
    m_monthCounts.clear();

    // A trash lying by deletion has the day each photo went there as its only header.
    const bool isByDeletion = isTrash() && Settings::instance()->isTrashByDeletion();
    QStringList groups = m_hasHeaders ? m_dateGroups : QStringList();
    if (isByDeletion)
        groups = {QStringLiteral("days")};
    const bool isYears = groups.contains(QLatin1String("years"));
    const bool isMonths = groups.contains(QLatin1String("months"));
    const bool isWeeks = groups.contains(QLatin1String("weeks"));
    const bool isDays = groups.contains(QLatin1String("days"));
    const bool isStamping = m_hasDayStamps && m_hasHeaders && !isWeeks && !isDays && m_columns <= 3 && !isTrash();

    // Every grid but the trash folds similar shots, when the view asks for it; a folded stack shows only its newest shot.
    QVector<int> runOfItem(m_items.size(), -1);
    QVector<QPair<int, int>> runs;
    if (m_isStacking && !isTrash()) {
        runs = SimilarShots::instance()->runsOf(m_items);
        for (int run = 0; run < runs.size(); ++run) {
            QStringList paths;
            for (int index = runs.at(run).first; index <= runs.at(run).second; ++index) {
                runOfItem[index] = run;
                paths.append(m_items.at(index).path);
            }
            m_stackPaths.insert(m_items.at(runs.at(run).second).path, paths);
        }
    }

    int currentYear = -1;
    int currentMonth = -1;
    int currentWeek = -1;
    QDate currentDay;
    const auto header = [this](const QString &level, const QString &label, const QString &key) {
        m_rows.append({0, level, label, key, 0, 0});
    };
    int line = -1;
    for (int index = 0; index < m_items.size(); ++index) {
        const MediaItem &item = m_items.at(index);
        m_indexOfPath.insert(item.path, index);
        const QDate day = dayOf(item.timestamp);
        const int monthKey = day.year() * 12 + day.month();
        ++m_monthCounts[monthKey];
        const int run = runOfItem.at(index);
        const QString stackKey = run >= 0 ? m_items.at(runs.at(run).second).path : QString();
        const bool isOpenStack = run >= 0 && m_openStacks.contains(stackKey);
        if (run >= 0 && !isOpenStack && index != runs.at(run).second)
            continue;
        int weekYear = 0;
        const int week = day.weekNumber(&weekYear) + weekYear * 100;
        // A week is cut where a larger group starts, so a header never stands inside a week.
        bool isCut = false;
        if (day.year() != currentYear) {
            if (isYears) {
                header(QStringLiteral("year"), QString::number(day.year()), QStringLiteral("year-%1").arg(day.year()));
                isCut = true;
            }
            currentYear = day.year();
        }
        if (monthKey != currentMonth) {
            if (isMonths) {
                header(QStringLiteral("month"), QLocale(QLocale::English).toString(day, QStringLiteral("MMMM yy")), QStringLiteral("month-%1").arg(monthKey));
                isCut = true;
            }
            currentMonth = monthKey;
        }
        if (isWeeks && (week != currentWeek || isCut)) {
            header(QStringLiteral("week"), weekLabel(day), QStringLiteral("week-%1-%2").arg(monthKey).arg(week));
            isCut = true;
        }
        currentWeek = week;
        if (isDays && day != currentDay) {
            header(QStringLiteral("day"), QLocale(QLocale::English).toString(day, QStringLiteral("ddd dd/MM")).toUpper(), QStringLiteral("day-%1").arg(day.toJulianDay()));
            isCut = true;
        }
        if (isStamping && day != currentDay)
            m_stampOfItem[index] = day;
        currentDay = day;
        if (isCut)
            line = -1;
        if (line < 0 || m_rows.at(line).tileCount >= m_columns || line != m_rows.size() - 1) {
            m_rows.append({1, QString(), QString(), item.path, int(m_tiles.size()), 0});
            line = int(m_rows.size() - 1);
        }
        Tile tile;
        tile.item = index;
        if (run >= 0) {
            tile.stackSize = runs.at(run).second - runs.at(run).first + 1;
            tile.stackPosition = isOpenStack ? index - runs.at(run).first + 1 : 0;
            tile.stackKey = stackKey;
        }
        m_tileOfItem[index] = int(m_tiles.size());
        m_tiles.append(tile);
        m_rowOfTile.append(line);
        ++m_rows[line].tileCount;
    }
    // The shots of a folded stack stand at its cover's tile.
    for (const auto &run : std::as_const(runs)) {
        const int cover = m_tileOfItem.at(run.second);
        for (int index = run.first; index < run.second; ++index)
            if (m_tileOfItem.at(index) < 0)
                m_tileOfItem[index] = cover;
    }
    endResetModel();
    ++m_revision;
    emit rebuilt();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── reading ─────────────────────────────────────────────────────────────────────────

int MediaGridModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : int(m_rows.size());
}

QVariant MediaGridModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() >= m_rows.size())
        return {};
    const Row &row = m_rows.at(index.row());
    switch (role) {
    case KindRole: return row.kind;
    case LevelRole: return row.level;
    case LabelRole: return row.label;
    case FirstRole: return row.first;
    case TileCountRole: return row.tileCount;
    case KeyRole: return row.key;
    default: return {};
    }
}

QHash<int, QByteArray> MediaGridModel::roleNames() const
{
    return {{KindRole, "kind"}, {LevelRole, "level"}, {LabelRole, "label"}, {FirstRole, "first"}, {TileCountRole, "tileCount"}, {KeyRole, "key"}};
}

QVariantMap MediaGridModel::tile(int tileIndex) const
{
    if (tileIndex < 0 || tileIndex >= m_tiles.size())
        return {};
    const Tile &shown = m_tiles.at(tileIndex);
    const int index = shown.item;
    const MediaItem &item = m_items.at(index);
    QVariantMap tile = item.toVariant();
    tile.insert(QStringLiteral("index"), index);
    tile.insert(QStringLiteral("stackSize"), shown.stackSize);
    tile.insert(QStringLiteral("stackPosition"), shown.stackPosition);
    tile.insert(QStringLiteral("stackKey"), shown.stackKey);
    tile.insert(QStringLiteral("duration"), item.isVideo && item.durationMs > 0 ? durationText(item.durationMs) : QString());
    const QDate stamp = m_stampOfItem.value(index);
    tile.insert(QStringLiteral("stamp"), stamp.isValid() ? dayStamp(stamp) : QString());
    // In the shared trash the desktop keeps nothing for a fixed time, so only Private's trash counts days down.
    if (m_source == QLatin1String("private-trash") && item.trashedAt > 0) {
        const qint64 left = (item.trashedAt + TRASH_KEEP_MS - QDateTime::currentMSecsSinceEpoch()) / (24LL * 60 * 60 * 1000);
        tile.insert(QStringLiteral("daysLeft"), QStringLiteral("%1d").arg(qMax<qint64>(0, left)));
    }
    return tile;
}

QVariantMap MediaGridModel::item(int index) const
{
    return index < 0 || index >= m_items.size() ? QVariantMap() : m_items.at(index).toVariant();
}

QString MediaGridModel::pathAt(int index) const
{
    return index < 0 || index >= m_items.size() ? QString() : m_items.at(index).path;
}

int MediaGridModel::indexOfPath(const QString &path) const
{
    return m_indexOfPath.value(path, -1);
}

int MediaGridModel::rowOfItem(int index) const
{
    const int tile = tileOfItem(index);
    return tile < 0 ? -1 : m_rowOfTile.at(tile);
}

int MediaGridModel::itemOfTile(int tileIndex) const
{
    return tileIndex < 0 || tileIndex >= m_tiles.size() ? -1 : m_tiles.at(tileIndex).item;
}

int MediaGridModel::tileOfItem(int index) const
{
    return index < 0 || index >= m_tileOfItem.size() ? -1 : m_tileOfItem.at(index);
}

QStringList MediaGridModel::stackPaths(const QString &stackKey) const
{
    return m_stackPaths.value(stackKey);
}

QStringList MediaGridModel::paths(const QVariantList &indexes) const
{
    QStringList list;
    for (const QVariant &index : indexes) {
        const int at = index.toInt();
        if (at >= 0 && at < m_items.size())
            list.append(m_items.at(at).path);
    }
    return list;
}

QStringList MediaGridModel::allPaths() const
{
    QStringList list;
    list.reserve(m_items.size());
    for (const MediaItem &item : m_items)
        list.append(item.path);
    return list;
}

QVariantMap MediaGridModel::monthAtRow(int row) const
{
    if (m_rows.isEmpty() || m_items.isEmpty())
        return {};
    row = qBound(0, row, int(m_rows.size()) - 1);
    // A header at the top belongs to the photos under it.
    while (row < m_rows.size() - 1 && m_rows.at(row).kind == 0)
        ++row;
    const int first = m_tiles.value(m_rows.at(row).first).item;
    const QDate day = dayOf(m_items.value(first).timestamp);
    const int count = m_monthCounts.value(day.year() * 12 + day.month());
    return {{QStringLiteral("label"), QLocale(QLocale::English).toString(day, QStringLiteral("MMMM yy"))},
            {QStringLiteral("count"), count},
            {QStringLiteral("key"), day.year() * 12 + day.month()}};
}

QVariantList MediaGridModel::months() const
{
    QVariantList list;
    int current = -1;
    for (int index = 0; index < m_items.size(); ++index) {
        const QDate day = dayOf(m_items.at(index).timestamp);
        const int key = day.year() * 12 + day.month();
        if (key == current)
            continue;
        current = key;
        // A month header and a week header can both stand in front of the month's first photo; a jump lands with them in view.
        int row = rowOfItem(index);
        if (row < 0)
            continue;
        while (row > 0 && m_rows.at(row - 1).kind == 0)
            --row;
        list.append(QVariantMap{{QStringLiteral("year"), day.year()}, {QStringLiteral("month"), day.month()},
                                {QStringLiteral("row"), row}, {QStringLiteral("index"), index}});
    }
    return list;
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
