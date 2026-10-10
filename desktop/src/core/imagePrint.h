#pragma once

#include <QByteArray>
#include <QImage>
#include <QVector>

#include <array>
#include <functional>
#include <optional>

// What a picture looks like, boiled down from its pixels alone, so two copies of one picture are found whatever their names, sizes, dates or compression (ImagePrint.kt).
struct ImagePrint {
    // A 64-bit DCT hash of the picture as it is, and turned a quarter, a half and three quarters clockwise, so a copy saved turned still matches.
    std::array<quint64, 4> shapes {};
    // The average colour of each cell of a 4×4 grid (0xRRGGBB), which the grey hash cannot see: a black-and-white copy is not a duplicate.
    std::array<quint32, 16> colours {};
    // The picture in 16×16 greys, for a last close look at two candidates.
    QByteArray detail;
    // The long side over the short side of the pixels it was read from.
    float ratio = 1;
    bool isValid() const { return detail.size() == 256; }
};

// How alike two pictures must be to count as the same: an exact copy at any size or compression, a copy slightly edited, or merely the same scene.
struct Strictness {
    int maxShapeBits;
    int maxColourDifference;
    float minDetailMatch;
    float maxRatioDifference;
    float maxFineDifference;
    float maxSharpPatch;
    float maxSharpMean;
    bool looksSharp() const { return maxSharpPatch < 1e9f; }

    static Strictness exact() { return {4, 4, 0.97f, 0.02f, 8.f, 6.f, 2.f}; }
    static Strictness close() { return {10, 18, 0.90f, 0.03f, 20.f, 1e10f, 1e10f}; }
    static Strictness loose() { return {14, 28, 0.80f, 0.06f, 32.f, 1e10f, 1e10f}; }
};

namespace ImagePrints {

ImagePrint of(const QImage &picture);
// The close look: the picture in 128×128 greys, softened once.
QByteArray fineOf(const QImage &picture);
// The sharp look, for an exact copy only: 256×256 greys as they are.
QByteArray sharpOf(const QImage &picture);

// Every set of at least two pictures that are the same at this strictness, as index lists into the prints given. `fineOf` and `sharpOf` are asked only for pictures already paired up; `isStopped` is asked between pictures.
QVector<QVector<int>> groupsOfSame(const QVector<ImagePrint> &prints, const QVector<float> &ratios, const std::function<QByteArray(int)> &fineOf,
                                   const std::function<QByteArray(int)> &sharpOf, const Strictness &strictness, const std::function<bool()> &isStopped);

} // namespace ImagePrints
