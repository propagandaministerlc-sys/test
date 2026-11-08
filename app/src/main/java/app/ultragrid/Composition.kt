package app.ultragrid

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Layout model for compositions ("Grid" and "Bento").
 *
 * A layout is a tree: a [SplitNode] places its children side by side ([Axis.ROW])
 * or on top of each other ([Axis.COLUMN]) with relative weights; a [FrameNode]
 * holds one photo. The boundaries between siblings are the draggable dividers.
 *
 * All geometry here is in "content space": fractions 0..1 of the canvas, with the
 * frames tiling it exactly. Spacing and rounded corners are applied when drawing.
 */
enum class Axis {
    /** Children left to right; dividers are vertical lines. */
    ROW,

    /** Children top to bottom; dividers are horizontal lines. */
    COLUMN,
}

sealed interface LayoutNode

data class FrameNode(val id: Int) : LayoutNode

data class SplitNode(
    val id: Int,
    val axis: Axis,
    val weights: List<Float>,
    val children: List<LayoutNode>,
) : LayoutNode {
    init {
        require(children.size >= 2 && weights.size == children.size)
    }
}

data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    fun start(axis: Axis) = if (axis == Axis.ROW) left else top
    fun end(axis: Axis) = if (axis == Axis.ROW) right else bottom
}

/** The boundary between child [index] and [index] + 1 of split [splitId]. */
data class Divider(
    val splitId: Int,
    val index: Int,
    val axis: Axis,
    /** x for [Axis.ROW], y for [Axis.COLUMN]. */
    val position: Float,
    /** Extent along the other axis. */
    val crossStart: Float,
    val crossEnd: Float,
    /** Extent of the whole split along [axis]; quarter snapping is relative to it. */
    val parentStart: Float,
    val parentEnd: Float,
    /** Allowed range, so neither neighbour gets smaller than the minimum size. */
    val minPosition: Float,
    val maxPosition: Float,
)

data class LayoutGeometry(val frames: Map<Int, FRect>, val dividers: List<Divider>) {
    fun frameAt(x: Float, y: Float): Int? = frames.entries.firstOrNull { it.value.contains(x, y) }?.key
}

enum class SnapKind { NONE, QUARTER, MATCH }

data class SnapResult(val position: Float, val kind: SnapKind, val quarter: Int = 0)

object LayoutMath {
    /** Smallest frame edge, as a fraction of the canvas. */
    const val MIN_FRAME = 0.1f

    /** How close (canvas fraction) a divider must come to a snap position. */
    const val SNAP_DISTANCE = 0.02f

    fun geometry(root: LayoutNode, minFrame: Float = MIN_FRAME): LayoutGeometry {
        val frames = LinkedHashMap<Int, FRect>()
        val dividers = mutableListOf<Divider>()
        fun visit(node: LayoutNode, r: FRect) {
            when (node) {
                is FrameNode -> frames[node.id] = r
                is SplitNode -> {
                    val axis = node.axis
                    val start = r.start(axis)
                    val length = r.end(axis) - start
                    val total = node.weights.sum()
                    var cursor = start
                    val bounds = node.weights.map { w ->
                        val s = cursor
                        cursor += length * w / total
                        s to cursor
                    }
                    node.children.forEachIndexed { i, child ->
                        val (s, e) = bounds[i]
                        visit(child, if (axis == Axis.ROW) FRect(s, r.top, e, r.bottom) else FRect(r.left, s, r.right, e))
                    }
                    for (i in 0 until node.children.size - 1) {
                        dividers += Divider(
                            splitId = node.id,
                            index = i,
                            axis = axis,
                            position = bounds[i].second,
                            crossStart = if (axis == Axis.ROW) r.top else r.left,
                            crossEnd = if (axis == Axis.ROW) r.bottom else r.right,
                            parentStart = start,
                            parentEnd = start + length,
                            minPosition = bounds[i].first + minFrame,
                            maxPosition = bounds[i + 1].second - minFrame,
                        )
                    }
                }
            }
        }
        visit(root, FRect(0f, 0f, 1f, 1f))
        return LayoutGeometry(frames, dividers)
    }

    fun frameIds(root: LayoutNode): List<Int> = when (root) {
        is FrameNode -> listOf(root.id)
        is SplitNode -> root.children.flatMap { frameIds(it) }
    }

    /**
     * Snaps a proposed divider position to a quarter of its split or to another
     * divider on the same axis ("matching"), whichever is closer and within reach.
     */
    fun snap(proposed: Float, divider: Divider, all: List<Divider>, distance: Float = SNAP_DISTANCE): SnapResult {
        val clamped = proposed.coerceIn(divider.minPosition, max(divider.minPosition, divider.maxPosition))
        var best = SnapResult(clamped, SnapKind.NONE)
        var bestDistance = distance
        val parentLength = divider.parentEnd - divider.parentStart
        for (q in 1..3) {
            val p = divider.parentStart + parentLength * q / 4f
            val d = abs(p - proposed)
            if (d <= bestDistance && p in divider.minPosition..divider.maxPosition) {
                best = SnapResult(p, SnapKind.QUARTER, q)
                bestDistance = d
            }
        }
        for (other in all) {
            if (other.axis != divider.axis) continue
            if (other.splitId == divider.splitId && other.index == divider.index) continue
            val p = other.position
            val d = abs(p - proposed)
            // A quarter wins ties: it is the more deliberate choice.
            if (d < bestDistance && p in divider.minPosition..divider.maxPosition) {
                best = SnapResult(p, SnapKind.MATCH)
                bestDistance = d
            }
        }
        return best
    }

    /** Returns a copy of [root] with the given divider moved to [position] (content space). */
    fun moveDivider(root: LayoutNode, splitId: Int, index: Int, position: Float): LayoutNode {
        val geometry = geometry(root)
        val divider = geometry.dividers.firstOrNull { it.splitId == splitId && it.index == index } ?: return root
        val target = position.coerceIn(divider.minPosition, max(divider.minPosition, divider.maxPosition))
        fun rebuild(node: LayoutNode): LayoutNode = when (node) {
            is FrameNode -> node
            is SplitNode -> if (node.id == splitId) {
                val total = node.weights.sum()
                val length = divider.parentEnd - divider.parentStart
                val starts = node.weights.runningFold(0f) { acc, w -> acc + w }.map {
                    divider.parentStart + length * it / total
                }
                val pairStart = starts[index]
                val combined = (node.weights[index] + node.weights[index + 1]) / total
                val first = ((target - pairStart) / length).coerceIn(0f, combined)
                val weights = node.weights.map { it / total }.toMutableList()
                weights[index] = first
                weights[index + 1] = combined - first
                node.copy(weights = weights)
            } else {
                node.copy(children = node.children.map(::rebuild))
            }
        }
        return rebuild(root)
    }
}

/** Per-frame crop: zoom >= 1 and pan offsets in frame fractions, as in [CropMath]. */
data class FrameCrop(val zoom: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    /** Keeps the frame fully covered by the image. */
    fun clamped(imageAspect: Float, frameAspect: Float): FrameCrop {
        val z = zoom.coerceIn(1f, MAX_ZOOM)
        val (w, h) = CropMath.cropSize(imageAspect, frameAspect, z)
        val mx = CropMath.maxOffset(w)
        val my = CropMath.maxOffset(h)
        return FrameCrop(z, offsetX.coerceIn(-mx, mx), offsetY.coerceIn(-my, my))
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}

enum class LayoutFamily(val label: String) { GRID("Grid"), BENTO("Bento") }

enum class LayoutTemplate(val label: String, val family: LayoutFamily, private val build: Builder.() -> LayoutNode) {
    GRID_2x1("2 × 1", LayoutFamily.GRID, { row(frame(), frame()) }),
    GRID_1x2("1 × 2", LayoutFamily.GRID, { column(frame(), frame()) }),
    GRID_1x3("1 × 3", LayoutFamily.GRID, { column(frame(), frame(), frame()) }),
    GRID_2x2("2 × 2", LayoutFamily.GRID, { column(row(frame(), frame()), row(frame(), frame())) }),
    GRID_2x3("2 × 3", LayoutFamily.GRID, { column(row(frame(), frame()), row(frame(), frame()), row(frame(), frame())) }),
    GRID_3x3("3 × 3", LayoutFamily.GRID, {
        column(row(frame(), frame(), frame()), row(frame(), frame(), frame()), row(frame(), frame(), frame()))
    }),
    FEATURE("Feature", LayoutFamily.BENTO, { row(listOf(0.62f, 0.38f), frame(), column(frame(), frame())) }),
    STAGGER("Stagger", LayoutFamily.BENTO, {
        column(
            listOf(0.6f, 0.4f),
            row(listOf(0.63f, 0.37f), frame(), frame()),
            row(listOf(0.43f, 0.57f), frame(), frame()),
        )
    }),
    MOSAIC("Mosaic", LayoutFamily.BENTO, {
        column(
            listOf(0.36f, 0.32f, 0.32f),
            row(listOf(0.66f, 0.34f), frame(), frame()),
            row(listOf(0.28f, 0.72f), frame(), frame()),
            frame(),
        )
    }),
    STORY("Story", LayoutFamily.BENTO, {
        row(
            column(listOf(0.33f, 0.67f), frame(), frame()),
            column(listOf(0.33f, 0.335f, 0.335f), row(frame(), frame()), frame(), frame()),
        )
    }),
    HERO("Hero", LayoutFamily.BENTO, { column(listOf(0.62f, 0.38f), frame(), row(frame(), frame(), frame())) }),
    OFFSET("Offset", LayoutFamily.BENTO, {
        row(column(listOf(0.6f, 0.4f), frame(), frame()), column(listOf(0.4f, 0.6f), frame(), frame()))
    }),
    ;

    fun create(): LayoutNode = Builder().build()

    val frameCount: Int by lazy { LayoutMath.frameIds(create()).size }

    /** Creates nodes; frame ids follow construction order, which is the reading order. */
    class Builder {
        private var nextFrame = 0
        private var nextSplit = 1000

        fun frame() = FrameNode(nextFrame++)

        fun row(vararg children: LayoutNode) = row(List(children.size) { 1f }, *children)
        fun column(vararg children: LayoutNode) = column(List(children.size) { 1f }, *children)

        fun row(weights: List<Float>, vararg children: LayoutNode) =
            SplitNode(nextSplit++, Axis.ROW, normalize(weights), children.toList())

        fun column(weights: List<Float>, vararg children: LayoutNode) =
            SplitNode(nextSplit++, Axis.COLUMN, normalize(weights), children.toList())

        private fun normalize(w: List<Float>): List<Float> {
            val s = w.sum()
            return w.map { it / s }
        }
    }
}

/** Output sizes. Width is always 1080 px, Instagram's native width. */
enum class CompositionFormat(val label: String, val caption: String, val width: Int, val height: Int) {
    FEED("4:5", "Feed", 1080, 1350),
    STORY("9:16", "Story", 1080, 1920),
    SQUARE("1:1", "Square", 1080, 1080),
    LANDSCAPE("1.91:1", "Landscape", 1080, 566),
    PROFILE("3:4", "Profile", 1080, 1440),
    ;

    val aspect: Float get() = width.toFloat() / height
}

/** Spacing and corner settings, as fractions of the canvas's shorter side. */
data class CompositionStyle(
    val gap: Float = 0.012f,
    val cornerRadius: Float = 0.02f,
    val outerBorder: Boolean = false,
    val background: Int = BACKGROUND_DARK,
) {
    companion object {
        const val BACKGROUND_DARK = 0xFF0B0D0F.toInt()
        const val BACKGROUND_BLACK = 0xFF000000.toInt()
        const val BACKGROUND_WHITE = 0xFFFFFFFF.toInt()
        const val BACKGROUND_PAPER = 0xFFF3F4F4.toInt()
        const val BACKGROUND_SLATE = 0xFFE1E5EC.toInt()
        val BACKGROUNDS = listOf(BACKGROUND_DARK, BACKGROUND_BLACK, BACKGROUND_WHITE, BACKGROUND_PAPER, BACKGROUND_SLATE)
        const val MAX_GAP = 0.05f
        const val MAX_RADIUS = 0.08f
    }
}

/** Converts between content space and pixels for a canvas of [width] × [height]. */
class CanvasMapping(val width: Float, val height: Float, style: CompositionStyle) {
    private val shortSide = min(width, height)
    val gap = style.gap * shortSide
    val radius = style.cornerRadius * shortSide
    private val margin = if (style.outerBorder) gap else 0f
    private val contentWidth = width - 2 * margin
    private val contentHeight = height - 2 * margin

    /** Pixel rectangle of a frame, inset by half the gap on inner edges. */
    fun frameRect(r: FRect): FRect {
        val half = gap / 2f
        fun inset(edge: Float) = if (edge <= EDGE || edge >= 1f - EDGE) 0f else half
        return FRect(
            margin + r.left * contentWidth + inset(r.left),
            margin + r.top * contentHeight + inset(r.top),
            margin + r.right * contentWidth - inset(r.right),
            margin + r.bottom * contentHeight - inset(r.bottom),
        )
    }

    fun toContentX(px: Float) = (px - margin) / contentWidth
    fun toContentY(px: Float) = (px - margin) / contentHeight
    fun toPixelX(c: Float) = margin + c * contentWidth
    fun toPixelY(c: Float) = margin + c * contentHeight

    private companion object {
        const val EDGE = 1e-4f
    }
}
