#include "imagePrint.h"

#include <QHash>

#include <algorithm>
#include <bit>
#include <cmath>
#include <numeric>

namespace {

constexpr int SIDE = 32;
constexpr int LOW = 8;
constexpr int COLOUR_SIDE = 4;
constexpr int DETAIL_SIDE = 16;
// Below this spread of greys a picture is flat (a black frame, a blank page) and its detail says nothing.
constexpr float FLAT_SPREAD = 3.f;
constexpr int FINE_SIDE = 128;
// Small patches, so a changed number or word fills most of one instead of fading into its surroundings.
constexpr int FINE_PATCH = 4;
constexpr int SHARP_SIDE = 256;
// The same share of the picture as a fine patch.
constexpr int SHARP_PATCH = 8;

// cos(π(2x+1)u / 2N) for the eight lowest frequencies, the only ones the hash keeps.
const std::array<std::array<float, SIDE>, LOW> &cosines()
{
    static const auto table = [] {
        std::array<std::array<float, SIDE>, LOW> values {};
        for (int u = 0; u < LOW; ++u)
            for (int x = 0; x < SIDE; ++x)
                values[u][x] = float(std::cos(M_PI * (2 * x + 1) * u / (2 * SIDE)));
        return values;
    }();
    return table;
}

// Where the value at (x, y) of a square grid turned clockwise `turns` quarters comes from.
int sourceOf(int x, int y, int side, int turns)
{
    switch (turns & 3) {
    case 0: return y * side + x;
    case 1: return (side - 1 - x) * side + y;
    case 2: return (side - 1 - y) * side + (side - 1 - x);
    default: return x * side + (side - 1 - y);
    }
}

QVector<float> turned(const QVector<float> &values, int side, int turns)
{
    if (turns == 0)
        return values;
    QVector<float> result(values.size());
    for (int index = 0; index < values.size(); ++index)
        result[index] = values[sourceOf(index % side, index / side, side, turns)];
    return result;
}

// A perceptual hash: the 8×8 lowest frequencies of the picture's DCT, one bit each for being above their median.
quint64 shapeOf(const QVector<float> &grey)
{
    const auto &cos = cosines();
    std::array<float, SIDE * LOW> rows {};
    for (int y = 0; y < SIDE; ++y)
        for (int u = 0; u < LOW; ++u) {
            float sum = 0;
            for (int x = 0; x < SIDE; ++x)
                sum += grey[y * SIDE + x] * cos[u][x];
            rows[y * LOW + u] = sum;
        }
    std::array<float, LOW * LOW> low {};
    for (int v = 0; v < LOW; ++v)
        for (int u = 0; u < LOW; ++u) {
            float sum = 0;
            for (int y = 0; y < SIDE; ++y)
                sum += rows[y * LOW + u] * cos[v][y];
            low[v * LOW + u] = sum;
        }
    std::array<float, LOW * LOW> sorted = low;
    std::sort(sorted.begin(), sorted.end());
    const float median = (sorted[LOW * LOW / 2 - 1] + sorted[LOW * LOW / 2]) / 2.f;
    quint64 hash = 0;
    for (float value : low)
        hash = (hash << 1) | (value > median ? 1 : 0);
    return hash;
}

// The picture averaged down to side×side cells of each channel.
void averaged(const QImage &picture, int side, QVector<float> &red, QVector<float> &green, QVector<float> &blue)
{
    const QImage image = picture.convertToFormat(QImage::Format_RGB32);
    const int width = image.width();
    const int height = image.height();
    red.assign(side * side, 0);
    green.assign(side * side, 0);
    blue.assign(side * side, 0);
    for (int cellY = 0; cellY < side; ++cellY) {
        const int top = cellY * height / side;
        const int bottom = std::min(height, std::max(top + 1, (cellY + 1) * height / side));
        for (int cellX = 0; cellX < side; ++cellX) {
            const int left = cellX * width / side;
            const int right = std::min(width, std::max(left + 1, (cellX + 1) * width / side));
            qint64 sumRed = 0, sumGreen = 0, sumBlue = 0;
            for (int y = top; y < bottom; ++y) {
                const QRgb *line = reinterpret_cast<const QRgb *>(image.constScanLine(y));
                for (int x = left; x < right; ++x) {
                    sumRed += qRed(line[x]);
                    sumGreen += qGreen(line[x]);
                    sumBlue += qBlue(line[x]);
                }
            }
            const int count = std::max(1, (bottom - top) * (right - left));
            const int index = cellY * side + cellX;
            red[index] = float(sumRed) / count;
            green[index] = float(sumGreen) / count;
            blue[index] = float(sumBlue) / count;
        }
    }
}

QVector<float> greysOf(const QImage &picture, int side)
{
    QVector<float> red, green, blue;
    averaged(picture, side, red, green, blue);
    QVector<float> grey(side * side);
    for (int index = 0; index < grey.size(); ++index)
        grey[index] = 0.299f * red[index] + 0.587f * green[index] + 0.114f * blue[index];
    return grey;
}

// A 3×3 average, so a copy saved small and its sharp original agree, while text that changed still changes a patch's tone.
QVector<float> softened(const QVector<float> &values, int side)
{
    QVector<float> result(values.size());
    for (int index = 0; index < values.size(); ++index) {
        const int x = index % side;
        const int y = index / side;
        float sum = 0;
        int count = 0;
        for (int nearY = std::max(0, y - 1); nearY <= std::min(side - 1, y + 1); ++nearY)
            for (int nearX = std::max(0, x - 1); nearX <= std::min(side - 1, x + 1); ++nearX) {
                sum += values[nearY * side + nearX];
                ++count;
            }
        result[index] = sum / count;
    }
    return result;
}

QByteArray bytesOf(const QVector<float> &values)
{
    QByteArray bytes(values.size(), Qt::Uninitialized);
    for (int index = 0; index < values.size(); ++index)
        bytes[index] = char(std::clamp(int(std::lround(values[index])), 0, 255));
    return bytes;
}

std::pair<float, float> toneOf(const QByteArray &look)
{
    float sum = 0;
    for (char value : look)
        sum += uchar(value);
    const float mean = sum / look.size();
    float squares = 0;
    for (char value : look) {
        const float offset = uchar(value) - mean;
        squares += offset * offset;
    }
    return {mean, std::sqrt(squares / look.size())};
}

// The largest patch difference and the mean difference of two looks, the second turned and evened by `gain`.
std::pair<float, float> differences(const QByteArray &first, const QByteArray &second, int side, int patch, int turns, float firstMean, float secondMean, float gain)
{
    const int patches = side / patch;
    float largest = 0;
    float total = 0;
    for (int patchY = 0; patchY < patches; ++patchY)
        for (int patchX = 0; patchX < patches; ++patchX) {
            float sum = 0;
            for (int y = patchY * patch; y < (patchY + 1) * patch; ++y)
                for (int x = patchX * patch; x < (patchX + 1) * patch; ++x) {
                    const float one = uchar(first[y * side + x]) - firstMean;
                    const float other = uchar(second[sourceOf(x, y, side, turns)]) - secondMean;
                    sum += std::abs(one - other * gain);
                }
            total += sum;
            largest = std::max(largest, sum / (patch * patch));
        }
    return {largest, total / (side * side)};
}

float fineDifference(const QByteArray &first, const QByteArray &second, int turns)
{
    const auto [firstMean, firstSpread] = toneOf(first);
    const auto [secondMean, secondSpread] = toneOf(second);
    const float gain = firstSpread >= FLAT_SPREAD && secondSpread >= FLAT_SPREAD ? firstSpread / secondSpread : 1.f;
    return differences(first, second, FINE_SIDE, FINE_PATCH, turns, firstMean, secondMean, gain).first;
}

std::pair<float, float> sharpDifference(const QByteArray &first, const QByteArray &second, int turns)
{
    return differences(first, second, SHARP_SIDE, SHARP_PATCH, turns, toneOf(first).first, toneOf(second).first, 1.f);
}

std::pair<int, int> closestTurn(const ImagePrint &first, const ImagePrint &second)
{
    int bestTurns = 0;
    int bestBits = 65;
    for (int turns = 0; turns < 4; ++turns) {
        const int bits = std::popcount(first.shapes[0] ^ second.shapes[turns]);
        if (bits < bestBits) {
            bestBits = bits;
            bestTurns = turns;
        }
    }
    return {bestBits, bestTurns};
}

float colourDifference(const ImagePrint &first, const ImagePrint &second, int turns)
{
    int sum = 0;
    for (int index = 0; index < 16; ++index) {
        const quint32 one = first.colours[index];
        const quint32 other = second.colours[sourceOf(index % COLOUR_SIDE, index / COLOUR_SIDE, COLOUR_SIDE, turns)];
        sum += std::abs(int((one >> 16) & 0xFF) - int((other >> 16) & 0xFF)) + std::abs(int((one >> 8) & 0xFF) - int((other >> 8) & 0xFF)) + std::abs(int(one & 0xFF) - int(other & 0xFF));
    }
    return sum / (16 * 3.f);
}

// The correlation of the two 16×16 grey pictures, 1 for the same picture under any change of brightness or contrast.
float detailMatch(const ImagePrint &first, const ImagePrint &second, int turns)
{
    const int count = int(first.detail.size());
    float firstMean = 0, secondMean = 0;
    for (int index = 0; index < count; ++index) {
        firstMean += uchar(first.detail[index]);
        secondMean += uchar(second.detail[sourceOf(index % DETAIL_SIDE, index / DETAIL_SIDE, DETAIL_SIDE, turns)]);
    }
    firstMean /= count;
    secondMean /= count;
    float product = 0, firstSquares = 0, secondSquares = 0;
    for (int index = 0; index < count; ++index) {
        const float one = uchar(first.detail[index]) - firstMean;
        const float other = uchar(second.detail[sourceOf(index % DETAIL_SIDE, index / DETAIL_SIDE, DETAIL_SIDE, turns)]) - secondMean;
        product += one * other;
        firstSquares += one * one;
        secondSquares += other * other;
    }
    const float firstSpread = std::sqrt(firstSquares / count);
    const float secondSpread = std::sqrt(secondSquares / count);
    // Two flat pictures are as alike as their colours say; a flat one and a detailed one are not.
    if (firstSpread < FLAT_SPREAD || secondSpread < FLAT_SPREAD)
        return firstSpread < FLAT_SPREAD && secondSpread < FLAT_SPREAD ? 1.f : 0.f;
    return product / std::sqrt(firstSquares * secondSquares);
}

std::optional<int> sameTurn(const ImagePrint &first, const ImagePrint &second, const Strictness &strictness)
{
    const auto [bits, turns] = closestTurn(first, second);
    if (bits > strictness.maxShapeBits)
        return std::nullopt;
    if (colourDifference(first, second, turns) > strictness.maxColourDifference)
        return std::nullopt;
    if (detailMatch(first, second, turns) < strictness.minDetailMatch)
        return std::nullopt;
    return turns;
}

} // namespace

namespace ImagePrints {

ImagePrint of(const QImage &picture)
{
    ImagePrint print;
    if (picture.isNull())
        return print;
    QVector<float> red, green, blue;
    averaged(picture, SIDE, red, green, blue);
    QVector<float> grey(SIDE * SIDE);
    for (int index = 0; index < grey.size(); ++index)
        grey[index] = 0.299f * red[index] + 0.587f * green[index] + 0.114f * blue[index];
    for (int turns = 0; turns < 4; ++turns)
        print.shapes[turns] = shapeOf(turned(grey, SIDE, turns));
    const int block = SIDE / COLOUR_SIDE;
    for (int cell = 0; cell < 16; ++cell) {
        const int cellX = cell % COLOUR_SIDE;
        const int cellY = cell / COLOUR_SIDE;
        float sumRed = 0, sumGreen = 0, sumBlue = 0;
        for (int y = cellY * block; y < (cellY + 1) * block; ++y)
            for (int x = cellX * block; x < (cellX + 1) * block; ++x) {
                sumRed += red[y * SIDE + x];
                sumGreen += green[y * SIDE + x];
                sumBlue += blue[y * SIDE + x];
            }
        const float area = block * block;
        print.colours[cell] = (quint32(sumRed / area) << 16) | (quint32(sumGreen / area) << 8) | quint32(sumBlue / area);
    }
    const int step = SIDE / DETAIL_SIDE;
    print.detail.resize(DETAIL_SIDE * DETAIL_SIDE);
    for (int cell = 0; cell < DETAIL_SIDE * DETAIL_SIDE; ++cell) {
        const int cellX = cell % DETAIL_SIDE;
        const int cellY = cell / DETAIL_SIDE;
        float sum = 0;
        for (int y = cellY * step; y < (cellY + 1) * step; ++y)
            for (int x = cellX * step; x < (cellX + 1) * step; ++x)
                sum += grey[y * SIDE + x];
        print.detail[cell] = char(std::clamp(int(sum / (step * step)), 0, 255));
    }
    print.ratio = float(std::max(picture.width(), picture.height())) / std::max(1, std::min(picture.width(), picture.height()));
    return print;
}

QByteArray fineOf(const QImage &picture)
{
    return bytesOf(softened(greysOf(picture, FINE_SIDE), FINE_SIDE));
}

QByteArray sharpOf(const QImage &picture)
{
    return bytesOf(greysOf(picture, SHARP_SIDE));
}

QVector<QVector<int>> groupsOfSame(const QVector<ImagePrint> &prints, const QVector<float> &ratios, const std::function<QByteArray(int)> &fineOf,
                                   const std::function<QByteArray(int)> &sharpOf, const Strictness &strictness, const std::function<bool()> &isStopped)
{
    QVector<int> indices;
    for (int index = 0; index < prints.size(); ++index)
        if (prints[index].isValid())
            indices.append(index);
    // Only pictures of about the same shape are compared, which keeps a large library to a fraction of every pair.
    QVector<int> byRatio = indices;
    std::sort(byRatio.begin(), byRatio.end(), [&](int left, int right) { return ratios[left] < ratios[right]; });
    QVector<int> parent(prints.size());
    std::iota(parent.begin(), parent.end(), 0);
    const auto root = [&parent](int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    };
    for (int position = 0; position < byRatio.size(); ++position) {
        if (isStopped())
            return {};
        const int first = byRatio[position];
        const float widest = ratios[first] * (1.f + strictness.maxRatioDifference);
        for (int next = position + 1; next < byRatio.size() && ratios[byRatio[next]] <= widest; ++next) {
            const int second = byRatio[next];
            const quint64 shape = prints[first].shapes[0];
            // The hash test runs over every pair of like shape, so it is kept to bare bit counts before the slower checks.
            bool isNear = false;
            for (int turns = 0; turns < 4 && !isNear; ++turns)
                isNear = std::popcount(shape ^ prints[second].shapes[turns]) <= strictness.maxShapeBits;
            if (!isNear || root(first) == root(second))
                continue;
            const std::optional<int> turns = sameTurn(prints[first], prints[second], strictness);
            if (!turns)
                continue;
            const QByteArray firstFine = fineOf(first);
            const QByteArray secondFine = fineOf(second);
            if (firstFine.isEmpty() || secondFine.isEmpty() || fineDifference(firstFine, secondFine, *turns) > strictness.maxFineDifference)
                continue;
            if (strictness.looksSharp()) {
                const QByteArray firstSharp = sharpOf(first);
                const QByteArray secondSharp = sharpOf(second);
                if (firstSharp.isEmpty() || secondSharp.isEmpty())
                    continue;
                const auto [patch, mean] = sharpDifference(firstSharp, secondSharp, *turns);
                if (patch > strictness.maxSharpPatch || mean > strictness.maxSharpMean)
                    continue;
            }
            parent[root(first)] = root(second);
        }
    }
    QHash<int, QVector<int>> sets;
    for (int index : indices)
        sets[root(index)].append(index);
    QVector<QVector<int>> groups;
    for (auto set = sets.cbegin(); set != sets.cend(); ++set)
        if (set->size() >= 2) {
            QVector<int> sorted = *set;
            std::sort(sorted.begin(), sorted.end());
            groups.append(sorted);
        }
    std::sort(groups.begin(), groups.end(), [](const QVector<int> &left, const QVector<int> &right) { return left.first() < right.first(); });
    return groups;
}

} // namespace ImagePrints
