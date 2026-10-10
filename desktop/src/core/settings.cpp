#include "settings.h"

#include <QJSEngine>

namespace {

const QString VIEWS = QStringLiteral("views/");
const QString FOLDERS = QStringLiteral("folders/");
const QString NAMES = QStringLiteral("albumNames");
const QString COVERS = QStringLiteral("covers");
const QString ORDER = QStringLiteral("albumOrder");

// QSettings keys cannot carry a slash as data; a folder's path is stored percent-encoded.
QString keyOf(const QString &text)
{
    return QString::fromLatin1(text.toUtf8().toPercentEncoding());
}

} // namespace

Settings::Settings(QObject *parent)
    : QObject(parent)
    , m_store(QStringLiteral("vados"), QStringLiteral("gallery"))
{
}

Settings *Settings::instance()
{
    static Settings *settings = new Settings;
    return settings;
}

Settings *Settings::create(QQmlEngine *, QJSEngine *engine)
{
    engine->setObjectOwnership(instance(), QJSEngine::CppOwnership);
    return instance();
}

void Settings::bump()
{
    ++m_revision;
    emit revisionChanged();
}

// #region ── per view ────────────────────────────────────────────────────────────────────────

QVariant Settings::fallback(const QString &view, const QString &key) const
{
    if (key == QLatin1String("columns"))
        return view == QLatin1String("recent") ? 5 : 3;
    if (key == QLatin1String("dateGroups"))
        return QStringList{QStringLiteral("days"), QStringLiteral("months"), QStringLiteral("years")};
    if (key == QLatin1String("headers"))
        return true;
    if (key == QLatin1String("stackSimilar"))
        return false;
    if (key == QLatin1String("albumColumns"))
        return 3;
    if (key == QLatin1String("groupedAlbums"))
        return view == QLatin1String("favorites");
    return {};
}

QVariant Settings::viewValue(const QString &view, const QString &key) const
{
    return m_store.value(VIEWS + view + QLatin1Char('/') + key, fallback(view, key));
}

void Settings::setViewValue(const QString &view, const QString &key, const QVariant &value)
{
    if (viewValue(view, key) == value)
        return;
    m_store.setValue(VIEWS + view + QLatin1Char('/') + key, value);
    bump();
}

bool Settings::hasOwnSettings(const QString &folderKey) const
{
    return !folderKey.isEmpty() && m_store.value(FOLDERS + keyOf(folderKey) + QStringLiteral("/own"), false).toBool();
}

void Settings::setOwnSettings(const QString &folderKey, const QString &view, bool isOwn)
{
    if (folderKey.isEmpty() || hasOwnSettings(folderKey) == isOwn)
        return;
    const QString base = FOLDERS + keyOf(folderKey) + QLatin1Char('/');
    // Turned on, an album starts from what it showed; its own values are kept when it is turned off, for next time.
    if (isOwn && !m_store.contains(base + QStringLiteral("columns"))) {
        m_store.setValue(base + QStringLiteral("columns"), viewValue(QStringLiteral("recent"), QStringLiteral("columns")));
        m_store.setValue(base + QStringLiteral("dateGroups"), viewValue(QStringLiteral("recent"), QStringLiteral("dateGroups")));
        m_store.setValue(base + QStringLiteral("headers"), viewValue(QStringLiteral("recent"), QStringLiteral("headers")));
    }
    Q_UNUSED(view)
    m_store.setValue(base + QStringLiteral("own"), isOwn);
    bump();
}

QVariant Settings::gridValue(const QString &view, const QString &folderKey, const QString &key) const
{
    const bool isPhotoKey = key == QLatin1String("columns") || key == QLatin1String("dateGroups") || key == QLatin1String("headers");
    if (!folderKey.isEmpty() && isPhotoKey) {
        if (hasOwnSettings(folderKey))
            return m_store.value(FOLDERS + keyOf(folderKey) + QLatin1Char('/') + key, viewValue(QStringLiteral("recent"), key));
        return viewValue(QStringLiteral("recent"), key);
    }
    return viewValue(view, key);
}

void Settings::setGridValue(const QString &view, const QString &folderKey, const QString &key, const QVariant &value)
{
    const bool isPhotoKey = key == QLatin1String("columns") || key == QLatin1String("dateGroups") || key == QLatin1String("headers");
    if (!folderKey.isEmpty() && isPhotoKey) {
        // An album without its own settings changes Recent's, which it follows.
        if (!hasOwnSettings(folderKey)) {
            setViewValue(QStringLiteral("recent"), key, value);
            return;
        }
        const QString stored = FOLDERS + keyOf(folderKey) + QLatin1Char('/') + key;
        if (m_store.value(stored) == value)
            return;
        m_store.setValue(stored, value);
        bump();
        return;
    }
    setViewValue(view, key, value);
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── arrangement ─────────────────────────────────────────────────────────────────────

QString Settings::albumName(const QString &folder) const
{
    return m_store.value(NAMES).toMap().value(folder).toString();
}

void Settings::setAlbumName(const QString &folder, const QString &name)
{
    QVariantMap names = m_store.value(NAMES).toMap();
    // Giving an album its folder's own name again clears the rename.
    if (name.isEmpty() || name == folder.section(QLatin1Char('/'), -1))
        names.remove(folder);
    else
        names.insert(folder, name);
    m_store.setValue(NAMES, names);
    bump();
}

QString Settings::cover(const QString &albumKey) const
{
    return m_store.value(COVERS).toMap().value(albumKey).toString();
}

void Settings::setCover(const QString &albumKey, const QString &path)
{
    QVariantMap covers = m_store.value(COVERS).toMap();
    covers.insert(albumKey, path);
    m_store.setValue(COVERS, covers);
    bump();
}

QStringList Settings::albumOrder() const
{
    return m_store.value(ORDER).toStringList();
}

void Settings::setAlbumOrder(const QStringList &order)
{
    m_store.setValue(ORDER, order);
    bump();
}

void Settings::followMovedFolder(const QString &from, const QString &to)
{
    QVariantMap names = m_store.value(NAMES).toMap();
    if (names.contains(from))
        names.insert(to, names.take(from));
    m_store.setValue(NAMES, names);
    QStringList order = albumOrder();
    const int at = order.indexOf(from);
    if (at >= 0)
        order[at] = to;
    m_store.setValue(ORDER, order);
    bump();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
// #region ── backup ──────────────────────────────────────────────────────────────────────────

QVariantMap Settings::snapshot() const
{
    QVariantMap values;
    for (const QString &key : m_store.allKeys())
        values.insert(key, m_store.value(key));
    return values;
}

void Settings::restoreSnapshot(const QVariantMap &values)
{
    m_store.clear();
    for (auto entry = values.cbegin(); entry != values.cend(); ++entry)
        m_store.setValue(entry.key(), entry.value());
    m_store.sync();
    bump();
}

// #endregion ─────────────────────────────────────────────────────────────────────────────────
