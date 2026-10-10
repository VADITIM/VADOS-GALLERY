#include "clipDevice.h"

ClipDevice::ClipDevice(const QString &path, qint64 start, qint64 length, QObject *parent)
    : QIODevice(parent)
    , m_file(path)
    , m_start(start)
    , m_length(length)
{
}

bool ClipDevice::open(OpenMode mode)
{
    if (mode & QIODevice::WriteOnly)
        return false;
    if (!m_file.open(QIODevice::ReadOnly))
        return false;
    m_file.seek(m_start);
    return QIODevice::open(QIODevice::ReadOnly);
}

void ClipDevice::close()
{
    m_file.close();
    QIODevice::close();
}

bool ClipDevice::seek(qint64 position)
{
    if (position < 0 || position > m_length)
        return false;
    QIODevice::seek(position);
    return m_file.seek(m_start + position);
}

qint64 ClipDevice::readData(char *data, qint64 maximum)
{
    const qint64 left = m_length - (m_file.pos() - m_start);
    if (left <= 0)
        return -1;
    return m_file.read(data, qMin(maximum, left));
}
