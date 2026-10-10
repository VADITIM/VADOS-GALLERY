#pragma once

#include "mediaItem.h"

#include <QFutureWatcher>
#include <QHash>
#include <QObject>
#include <QVector>

// Shots taken seconds apart that look alike (a burst, ten tries at the same picture) stack into one tile (SimilarShots.kt). Each candidate's look is a 64-bit difference hash of a small decode, worked out once in the background and kept on disk. Private photos are never hashed.
class SimilarShots : public QObject {
    Q_OBJECT

public:
    static SimilarShots *instance();

    // Runs of at least two neighbouring items that look alike, as [first, last] index pairs into `items`.
    QVector<QPair<int, int>> runsOf(const QVector<MediaItem> &items) const;
    // Hashes, in the background, the photos that have another photo close enough in time to stack with.
    void index(const QVector<MediaItem> &items);
    // A photo is known by its path and when it was last written, so a changed file is hashed again.
    static QString keyOf(const MediaItem &item);

signals:
    // A batch of new hashes is in: grids that stack restack.
    void changed();

private:
    explicit SimilarShots(QObject *parent = nullptr);
    bool isAlike(const MediaItem &first, const MediaItem &second) const;
    void load();
    void save() const;

    QHash<QString, quint64> m_hashes;
    QFutureWatcher<QHash<QString, quint64>> m_watcher;
    QVector<MediaItem> m_waiting;
    bool m_isLoaded = false;
};
