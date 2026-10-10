#pragma once

#include <QObject>
#include <QVariantList>
#include <QtQml/qqmlregistration.h>

// Crop and drawing, saved as a copy beside the original with the original's date and location; the original is never changed (MediaEditor.kt).
class MediaEditor : public QObject {
    Q_OBJECT
    QML_ELEMENT
    QML_SINGLETON

public:
    explicit MediaEditor(QObject *parent = nullptr);

    // `crop` is the frame in the turned picture, as shares of its width and height; `strokes` are the pencil's lines in the same shares:
    // [{ color, width (a share of the picture's width), points: [[x, y], …] }]. Answers the copy's path, or nothing when it could not be written.
    Q_INVOKABLE QString saveCrop(const QString &path, double left, double top, double width, double height, int quarterTurns, const QVariantList &strokes);
};
