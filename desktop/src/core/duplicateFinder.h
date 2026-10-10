#pragma once

#include "imagePrint.h"
#include "mediaItem.h"

#include <QFutureWatcher>
#include <QHash>
#include <QMutex>
#include <QObject>
#include <QVariantList>
#include <QtQml/qqmlregistration.h>

#include <atomic>
#include <memory>

// Finds the same picture saved more than once, by its pixels alone (Duplicates.kt). Every photo's print is read once and kept on disk; only pictures the prints pair up are looked at closely.
// The library and Private each keep their own prints: Private's live in the private folder itself, and its photos are decoded in memory only.
class DuplicateFinder : public QObject {
    Q_OBJECT
    QML_NAMED_ELEMENT(Duplicates)
    QML_SINGLETON

    Q_PROPERTY(QString stage READ stage NOTIFY progressChanged)
    Q_PROPERTY(double progress READ progress NOTIFY progressChanged)
    Q_PROPERTY(int readCount READ readCount NOTIFY progressChanged)
    Q_PROPERTY(int totalCount READ totalCount NOTIFY progressChanged)
    Q_PROPERTY(QVariantList groups READ groups NOTIFY groupsChanged)

public:
    explicit DuplicateFinder(QObject *parent = nullptr);

    QString stage() const { return m_stage; }
    double progress() const { return m_progress; }
    int readCount() const { return m_readCount; }
    int totalCount() const { return m_totalCount; }
    QVariantList groups() const { return m_groups; }

    // `scope` is "library" or "private"; `strictness` is "exact", "close" or "loose". A search already running is stopped first.
    Q_INVOKABLE void find(const QString &scope, const QString &strictness);
    Q_INVOKABLE void stop();

signals:
    void progressChanged();
    void groupsChanged();

private:
    struct StoredPrint {
        qint64 size = 0;
        ImagePrint print;
    };
    struct SearchResult {
        QVector<QVector<MediaItem>> groups;
        bool isStopped = false;
    };

    void report(const QString &stage, int read, int total);
    SearchResult search(QVector<MediaItem> photos, Strictness strictness, QString indexPath, std::shared_ptr<std::atomic_bool> isStopped);
    void loadIndex(const QString &path);
    void saveIndex(const QString &path, const QVector<MediaItem> &photos);

    QString m_stage = QStringLiteral("done");
    double m_progress = 0;
    int m_readCount = 0;
    int m_totalCount = 0;
    QVariantList m_groups;
    QHash<QString, StoredPrint> m_prints;
    QString m_loadedIndex;
    QMutex m_lock;
    std::shared_ptr<std::atomic_bool> m_isStopped;
    QFutureWatcher<SearchResult> m_watcher;
};
