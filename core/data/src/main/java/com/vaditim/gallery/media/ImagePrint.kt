package com.vaditim.gallery.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// What a picture looks like, boiled down from its pixels alone, so two copies of one picture are found whatever their names, sizes, dates or compression. Kept free of Android so the matching can be tried on a desktop against real photos.
class ImagePrint(
    // A 64-bit DCT hash of the picture as it is, and turned a quarter, a half and three quarters clockwise, so a copy saved turned still matches.
    val shapes: LongArray,
    // The average colour of each cell of a 4×4 grid (0xRRGGBB), which the grey hash cannot see: a black-and-white copy is not a duplicate.
    val colours: IntArray,
    // The picture in 16×16 greys, for a last close look at two candidates; two screenshots of one app share their layout but not this.
    val detail: ByteArray,
    // The long side over the short side of the pixels it was read from.
    val ratio: Float,
)

// How alike two pictures must be to count as the same: an exact copy at any size or compression, a copy slightly edited, or merely the same scene.
// `maxFineDifference` is the most any patch of the two close looks may differ, on the 0–255 grey scale: two screenshots of one form with other numbers in it differ only in a few patches, and a lot there.
// `maxSharpPatch` and `maxSharpMean` are the sharp look only an exact copy gets (see sharpDifference): two shots of the same clouds a moment apart pass every other test.
enum class Strictness(
    val maxShapeBits: Int,
    val maxColourDifference: Int,
    val minDetailMatch: Float,
    val maxRatioDifference: Float,
    val maxFineDifference: Float,
    val maxSharpPatch: Float = Float.POSITIVE_INFINITY,
    val maxSharpMean: Float = Float.POSITIVE_INFINITY,
) {
    EXACT(4, 4, 0.97f, 0.02f, 8f, 6f, 2f),
    CLOSE(10, 18, 0.90f, 0.03f, 20f),
    LOOSE(14, 28, 0.80f, 0.06f, 32f),
    ;

    val looksSharp: Boolean get() = maxSharpPatch.isFinite()
}

object ImagePrints {
    private const val SIDE = 32
    private const val LOW = 8
    private const val COLOUR_SIDE = 4
    private const val DETAIL_SIDE = 16
    // Below this spread of greys a picture is flat (a black frame, a blank page) and its detail says nothing.
    private const val FLAT_SPREAD = 3f

    // cos(π(2x+1)u / 2N) for the eight lowest frequencies, the only ones the hash keeps.
    private val cosines = Array(LOW) { u -> FloatArray(SIDE) { x -> cos(PI * (2 * x + 1) * u / (2 * SIDE)).toFloat() } }

    // `pixels` are ARGB, row by row, at any size; they are averaged down to 32×32, so the source's own scaling barely matters.
    fun of(pixels: IntArray, width: Int, height: Int): ImagePrint {
        val red = FloatArray(SIDE * SIDE)
        val green = FloatArray(SIDE * SIDE)
        val blue = FloatArray(SIDE * SIDE)
        for (cellY in 0 until SIDE) {
            val top = cellY * height / SIDE
            val bottom = max(top + 1, (cellY + 1) * height / SIDE)
            for (cellX in 0 until SIDE) {
                val left = cellX * width / SIDE
                val right = max(left + 1, (cellX + 1) * width / SIDE)
                var sumRed = 0L
                var sumGreen = 0L
                var sumBlue = 0L
                for (y in top until min(bottom, height)) {
                    for (x in left until min(right, width)) {
                        val pixel = pixels[y * width + x]
                        sumRed += pixel shr 16 and 0xFF
                        sumGreen += pixel shr 8 and 0xFF
                        sumBlue += pixel and 0xFF
                    }
                }
                val count = ((min(bottom, height) - top) * (min(right, width) - left)).coerceAtLeast(1)
                val index = cellY * SIDE + cellX
                red[index] = sumRed.toFloat() / count
                green[index] = sumGreen.toFloat() / count
                blue[index] = sumBlue.toFloat() / count
            }
        }
        val grey = FloatArray(SIDE * SIDE) { 0.299f * red[it] + 0.587f * green[it] + 0.114f * blue[it] }
        val shapes = LongArray(4) { turns -> shapeOf(turned(grey, SIDE, turns)) }
        val block = SIDE / COLOUR_SIDE
        val colours = IntArray(COLOUR_SIDE * COLOUR_SIDE) { cell ->
            val cellX = cell % COLOUR_SIDE
            val cellY = cell / COLOUR_SIDE
            var sumRed = 0f
            var sumGreen = 0f
            var sumBlue = 0f
            for (y in cellY * block until (cellY + 1) * block) for (x in cellX * block until (cellX + 1) * block) {
                sumRed += red[y * SIDE + x]
                sumGreen += green[y * SIDE + x]
                sumBlue += blue[y * SIDE + x]
            }
            val area = block * block
            ((sumRed / area).toInt() shl 16) or ((sumGreen / area).toInt() shl 8) or (sumBlue / area).toInt()
        }
        val step = SIDE / DETAIL_SIDE
        val detail = ByteArray(DETAIL_SIDE * DETAIL_SIDE) { cell ->
            val cellX = cell % DETAIL_SIDE
            val cellY = cell / DETAIL_SIDE
            var sum = 0f
            for (y in cellY * step until (cellY + 1) * step) for (x in cellX * step until (cellX + 1) * step) sum += grey[y * SIDE + x]
            (sum / (step * step)).toInt().coerceIn(0, 255).toByte()
        }
        return ImagePrint(shapes, colours, detail, max(width, height).toFloat() / min(width, height).coerceAtLeast(1))
    }

    // A perceptual hash: the 8×8 lowest frequencies of the picture's DCT, one bit each for being above their median. Scaling, compression and light changes move the fine detail, not these.
    private fun shapeOf(grey: FloatArray): Long {
        val rows = FloatArray(SIDE * LOW)
        for (y in 0 until SIDE) for (u in 0 until LOW) {
            var sum = 0f
            for (x in 0 until SIDE) sum += grey[y * SIDE + x] * cosines[u][x]
            rows[y * LOW + u] = sum
        }
        val low = FloatArray(LOW * LOW)
        for (v in 0 until LOW) for (u in 0 until LOW) {
            var sum = 0f
            for (y in 0 until SIDE) sum += rows[y * LOW + u] * cosines[v][y]
            low[v * LOW + u] = sum
        }
        val sorted = low.sortedArray()
        val median = (sorted[LOW * LOW / 2 - 1] + sorted[LOW * LOW / 2]) / 2f
        var hash = 0L
        for (value in low) hash = (hash shl 1) or if (value > median) 1L else 0L
        return hash
    }

    // Where the value at (x, y) of a square grid turned clockwise `turns` quarters comes from.
    private fun sourceOf(x: Int, y: Int, side: Int, turns: Int): Int = when (turns and 3) {
        0 -> y * side + x
        1 -> (side - 1 - x) * side + y
        2 -> (side - 1 - y) * side + (side - 1 - x)
        else -> x * side + (side - 1 - y)
    }

    private fun turned(values: FloatArray, side: Int, turns: Int): FloatArray =
        if (turns == 0) values else FloatArray(values.size) { values[sourceOf(it % side, it / side, side, turns)] }

    // How many hash bits apart two pictures are at the turn that brings them closest, with that turn.
    fun closestTurn(first: ImagePrint, second: ImagePrint): Pair<Int, Int> {
        var bestTurns = 0
        var bestBits = Int.MAX_VALUE
        for (turns in 0 until 4) {
            val bits = java.lang.Long.bitCount(first.shapes[0] xor second.shapes[turns])
            if (bits < bestBits) {
                bestBits = bits
                bestTurns = turns
            }
        }
        return bestBits to bestTurns
    }

    // The mean difference of the grid's colours, per channel, on the 0–255 scale.
    fun colourDifference(first: ImagePrint, second: ImagePrint, turns: Int): Float {
        var sum = 0
        for (index in first.colours.indices) {
            val one = first.colours[index]
            val other = second.colours[sourceOf(index % COLOUR_SIDE, index / COLOUR_SIDE, COLOUR_SIDE, turns)]
            sum += abs((one shr 16 and 0xFF) - (other shr 16 and 0xFF)) + abs((one shr 8 and 0xFF) - (other shr 8 and 0xFF)) + abs((one and 0xFF) - (other and 0xFF))
        }
        return sum / (first.colours.size * 3f)
    }

    // The correlation of the two 16×16 grey pictures, 1 for the same picture under any change of brightness or contrast.
    fun detailMatch(first: ImagePrint, second: ImagePrint, turns: Int): Float {
        val count = first.detail.size
        var firstMean = 0f
        var secondMean = 0f
        for (index in 0 until count) {
            firstMean += first.detail[index].toInt() and 0xFF
            secondMean += second.detail[sourceOf(index % DETAIL_SIDE, index / DETAIL_SIDE, DETAIL_SIDE, turns)].toInt() and 0xFF
        }
        firstMean /= count
        secondMean /= count
        var product = 0f
        var firstSquares = 0f
        var secondSquares = 0f
        for (index in 0 until count) {
            val one = (first.detail[index].toInt() and 0xFF) - firstMean
            val other = (second.detail[sourceOf(index % DETAIL_SIDE, index / DETAIL_SIDE, DETAIL_SIDE, turns)].toInt() and 0xFF) - secondMean
            product += one * other
            firstSquares += one * one
            secondSquares += other * other
        }
        val firstSpread = sqrt(firstSquares / count)
        val secondSpread = sqrt(secondSquares / count)
        // Two flat pictures are as alike as their colours say; a flat one and a detailed one are not.
        if (firstSpread < FLAT_SPREAD || secondSpread < FLAT_SPREAD) return if (firstSpread < FLAT_SPREAD && secondSpread < FLAT_SPREAD) 1f else 0f
        return product / sqrt(firstSquares * secondSquares)
    }

    // The turn at which two prints could be the same picture, or null when even the coarse look tells them apart.
    fun sameTurn(first: ImagePrint, second: ImagePrint, strictness: Strictness): Int? {
        val (bits, turns) = closestTurn(first, second)
        if (bits > strictness.maxShapeBits) return null
        if (colourDifference(first, second, turns) > strictness.maxColourDifference) return null
        return turns.takeIf { detailMatch(first, second, turns) >= strictness.minDetailMatch }
    }

    // The close look: the picture in 128×128 greys, softened once here rather than on every comparison, read from a larger thumbnail only for pictures the prints already pair up.
    fun fineOf(pixels: IntArray, width: Int, height: Int): ByteArray {
        val softGreys = softened(greysOf(pixels, width, height, FINE_SIDE), FINE_SIDE)
        return ByteArray(softGreys.size) { softGreys[it].roundToInt().coerceIn(0, 255).toByte() }
    }

    // The sharp look, for an exact copy only: the picture in 256×256 greys as they are, read only for pictures the close look already pairs up.
    fun sharpOf(pixels: IntArray, width: Int, height: Int): ByteArray {
        val greys = greysOf(pixels, width, height, SHARP_SIDE)
        return ByteArray(greys.size) { greys[it].roundToInt().coerceIn(0, 255).toByte() }
    }

    // The picture averaged down to `side`×`side` greys.
    private fun greysOf(pixels: IntArray, width: Int, height: Int, side: Int): FloatArray {
        val greys = FloatArray(side * side)
        for (cellY in 0 until side) {
            val top = cellY * height / side
            val bottom = min(height, max(top + 1, (cellY + 1) * height / side))
            for (cellX in 0 until side) {
                val left = cellX * width / side
                val right = min(width, max(left + 1, (cellX + 1) * width / side))
                var sum = 0f
                for (y in top until bottom) for (x in left until right) {
                    val pixel = pixels[y * width + x]
                    sum += 0.299f * (pixel shr 16 and 0xFF) + 0.587f * (pixel shr 8 and 0xFF) + 0.114f * (pixel and 0xFF)
                }
                greys[cellY * side + cellX] = sum / ((bottom - top) * (right - left)).coerceAtLeast(1)
            }
        }
        return greys
    }

    // The largest mean difference of any 4×4 patch once the second is turned and brought to the first's brightness and contrast. A copy differs a little everywhere; a different picture of the same layout differs a lot somewhere.
    fun fineDifference(first: ByteArray, second: ByteArray, turns: Int): Float {
        val (firstMean, firstSpread) = toneOf(first)
        val (secondMean, secondSpread) = toneOf(second)
        val gain = if (firstSpread >= FLAT_SPREAD && secondSpread >= FLAT_SPREAD) firstSpread / secondSpread else 1f
        return differences(first, second, FINE_SIDE, FINE_PATCH, turns, firstMean, secondMean, gain).first
    }

    // How far apart two sharp looks are: the largest mean difference of any 8×8 patch, and the mean over the whole picture. Only the overall brightness is evened out, for a copy saved in another colour space; nothing is softened and contrast is kept, so a second shot a moment later, its clouds moved a pixel, shows at their edges.
    fun sharpDifference(first: ByteArray, second: ByteArray, turns: Int): Pair<Float, Float> =
        differences(first, second, SHARP_SIDE, SHARP_PATCH, turns, toneOf(first).first, toneOf(second).first, 1f)

    // The mean and spread of a look's greys.
    private fun toneOf(look: ByteArray): Pair<Float, Float> {
        var sum = 0f
        for (value in look) sum += value.toInt() and 0xFF
        val mean = sum / look.size
        var squares = 0f
        for (value in look) {
            val offset = (value.toInt() and 0xFF) - mean
            squares += offset * offset
        }
        return mean to sqrt(squares / look.size)
    }

    // The largest patch difference and the mean difference of two looks, the second turned and evened by `gain`; nothing is allocated, since it runs for every candidate pair.
    private fun differences(first: ByteArray, second: ByteArray, side: Int, patch: Int, turns: Int, firstMean: Float, secondMean: Float, gain: Float): Pair<Float, Float> {
        val patches = side / patch
        var largest = 0f
        var total = 0f
        for (patchY in 0 until patches) for (patchX in 0 until patches) {
            var sum = 0f
            for (y in patchY * patch until (patchY + 1) * patch) for (x in patchX * patch until (patchX + 1) * patch) {
                val one = (first[y * side + x].toInt() and 0xFF) - firstMean
                val other = (second[sourceOf(x, y, side, turns)].toInt() and 0xFF) - secondMean
                sum += abs(one - other * gain)
            }
            total += sum
            largest = max(largest, sum / (patch * patch))
        }
        return largest to total / (side * side)
    }

    // A 3×3 average, so a copy saved small (its edges gone soft) and its sharp original agree, while text that changed still changes a patch's tone.
    private fun softened(values: FloatArray, side: Int): FloatArray = FloatArray(values.size) { index ->
        val x = index % side
        val y = index / side
        var sum = 0f
        var count = 0
        for (nearY in max(0, y - 1)..min(side - 1, y + 1)) for (nearX in max(0, x - 1)..min(side - 1, x + 1)) {
            sum += values[nearY * side + nearX]
            count++
        }
        sum / count
    }

    private const val FINE_SIDE = 128
    // Small patches, so a changed number or word fills most of one instead of fading into its surroundings.
    private const val FINE_PATCH = 4
    private const val SHARP_SIDE = 256
    // The same share of the picture as a fine patch.
    private const val SHARP_PATCH = 8

    // Every set of at least two pictures that are the same at this strictness, each set in the order given. Only pictures of about the same shape are compared, which keeps a large library to a fraction of every pair; `fineOf` is asked only for pictures the prints pair up, `sharpOf` only for an exact search and pictures the close look pairs up, and a picture without one pairs with nothing. `onStep` runs between pictures, so a search no longer wanted can stop there.
    fun <T> groupsOfSame(items: List<T>, printOf: (T) -> ImagePrint?, ratioOf: (T) -> Float?, fineOf: (T) -> ByteArray?, sharpOf: (T) -> ByteArray?, strictness: Strictness, onStep: () -> Unit = {}): List<List<T>> {
        val indices = items.indices.filter { printOf(items[it]) != null }
        val prints = arrayOfNulls<ImagePrint>(items.size)
        val ratios = FloatArray(items.size)
        indices.forEach { index ->
            val print = printOf(items[index])!!
            prints[index] = print
            ratios[index] = ratioOf(items[index])?.takeIf { it >= 1f } ?: print.ratio
        }
        val byRatio = indices.sortedBy { ratios[it] }
        val parent = IntArray(items.size) { it }
        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        // The hash test runs over every pair of like shape, so it is kept to bare bit counts before the slower checks.
        val turnedShapes = Array(4) { LongArray(items.size) }
        indices.forEach { index -> for (turns in 0 until 4) turnedShapes[turns][index] = prints[index]!!.shapes[turns] }
        val shapes = turnedShapes[0]
        for (position in byRatio.indices) {
            onStep()
            val first = byRatio[position]
            val widest = ratios[first] * (1f + strictness.maxRatioDifference)
            var next = position + 1
            while (next < byRatio.size && ratios[byRatio[next]] <= widest) {
                val second = byRatio[next]
                next++
                val shape = shapes[first]
                val isNear = java.lang.Long.bitCount(shape xor turnedShapes[0][second]) <= strictness.maxShapeBits ||
                    java.lang.Long.bitCount(shape xor turnedShapes[1][second]) <= strictness.maxShapeBits ||
                    java.lang.Long.bitCount(shape xor turnedShapes[2][second]) <= strictness.maxShapeBits ||
                    java.lang.Long.bitCount(shape xor turnedShapes[3][second]) <= strictness.maxShapeBits
                if (!isNear || root(first) == root(second)) continue
                val turns = sameTurn(prints[first]!!, prints[second]!!, strictness) ?: continue
                val firstFine = fineOf(items[first]) ?: continue
                val secondFine = fineOf(items[second]) ?: continue
                if (fineDifference(firstFine, secondFine, turns) > strictness.maxFineDifference) continue
                if (strictness.looksSharp) {
                    val firstSharp = sharpOf(items[first]) ?: continue
                    val secondSharp = sharpOf(items[second]) ?: continue
                    val (patch, mean) = sharpDifference(firstSharp, secondSharp, turns)
                    if (patch > strictness.maxSharpPatch || mean > strictness.maxSharpMean) continue
                }
                parent[root(first)] = root(second)
            }
        }
        return indices.groupBy { root(it) }.values.filter { it.size >= 2 }.map { it.sorted() }.sortedBy { it.first() }.map { group -> group.map { items[it] } }
    }
}
