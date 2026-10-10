#pragma once

#include <QFile>
#include <QIODevice>

// Only a motion photo's clip, read out of the photo's own file, so the player sees a plain MP4 and nothing is copied to disk (ClipDataSource in MotionPhoto.kt).
class ClipDevice : public QIODevice {
    Q_OBJECT

public:
    ClipDevice(const QString &path, qint64 start, qint64 length, QObject *parent = nullptr);

    bool open(OpenMode mode) override;
    void close() override;
    bool isSequential() const override { return false; }
    qint64 size() const override { return m_length; }
    bool seek(qint64 position) override;

protected:
    qint64 readData(char *data, qint64 maximum) override;
    qint64 writeData(const char *, qint64) override { return -1; }

private:
    QFile m_file;
    qint64 m_start;
    qint64 m_length;
};
