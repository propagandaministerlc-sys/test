package app.ultragrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionLayoutTest {

    @Test
    fun everyTemplateTilesTheCanvas() {
        LayoutTemplate.entries.forEach { t ->
            val geometry = LayoutMath.geometry(t.create())
            val area = geometry.frames.values.sumOf { (it.width * it.height).toDouble() }
            assertEquals(t.name, 1.0, area, 1e-4)
            geometry.frames.values.forEach { r ->
                assertTrue(t.name, r.left >= -1e-6f && r.top >= -1e-6f && r.right <= 1.000001f && r.bottom <= 1.000001f)
                assertTrue(t.name, r.width > 0f && r.height > 0f)
            }
        }
    }

    @Test
    fun frameIdsAreNumberedInOrder() {
        LayoutTemplate.entries.forEach { t ->
            assertEquals(t.name, (0 until t.frameCount).toList(), LayoutMath.frameIds(t.create()))
        }
    }

    @Test
    fun tapInFrameCentreFindsThatFrame() {
        LayoutTemplate.entries.forEach { t ->
            val g = LayoutMath.geometry(t.create())
            g.frames.forEach { (id, r) -> assertEquals(t.name, id, g.frameAt(r.centerX, r.centerY)) }
        }
    }

    @Test
    fun movingADividerResizesOnlyItsNeighbours() {
        val root = LayoutTemplate.GRID_1x3.create()
        val before = LayoutMath.geometry(root)
        val first = before.dividers.first { it.index == 0 }
        val moved = LayoutMath.geometry(LayoutMath.moveDivider(root, first.splitId, 0, 0.25f))
        assertEquals(0.25f, moved.frames.getValue(0).bottom, 1e-5f)
        assertEquals(0.25f, moved.frames.getValue(1).top, 1e-5f)
        // The third row did not move.
        assertEquals(before.frames.getValue(2).top, moved.frames.getValue(2).top, 1e-5f)
        assertEquals(1f, moved.frames.getValue(2).bottom, 1e-5f)
    }

    @Test
    fun dividerStopsAtMinimumFrameSize() {
        val root = LayoutTemplate.GRID_2x1.create()
        val d = LayoutMath.geometry(root).dividers.single()
        val moved = LayoutMath.geometry(LayoutMath.moveDivider(root, d.splitId, 0, 0.01f))
        assertEquals(LayoutMath.MIN_FRAME, moved.frames.getValue(0).width, 1e-5f)
    }

    @Test
    fun snapsToQuarterOfItsSplit() {
        val g = LayoutMath.geometry(LayoutTemplate.GRID_2x1.create())
        val d = g.dividers.single()
        val s = LayoutMath.snap(0.262f, d, g.dividers)
        assertEquals(SnapKind.QUARTER, s.kind)
        assertEquals(1, s.quarter)
        assertEquals(0.25f, s.position, 1e-6f)
        assertEquals(SnapKind.NONE, LayoutMath.snap(0.36f, d, g.dividers).kind)
    }

    @Test
    fun snapsToMatchingDividerInAnotherRow() {
        val root = LayoutTemplate.STAGGER.create()
        val g = LayoutMath.geometry(root)
        val vertical = g.dividers.filter { it.axis == Axis.ROW }
        val top = vertical.first { it.crossStart == 0f }
        val bottom = vertical.first { it != top }
        // Drag the lower divider close to the upper one (0.63): it should line up exactly.
        val s = LayoutMath.snap(top.position - 0.012f, bottom, g.dividers)
        assertEquals(SnapKind.MATCH, s.kind)
        assertEquals(top.position, s.position, 1e-6f)
    }

    @Test
    fun cropIsClampedSoTheFrameStaysCovered() {
        val c = FrameCrop(zoom = 9f, offsetX = 10f, offsetY = -10f).clamped(imageAspect = 1.5f, frameAspect = 1f)
        assertEquals(FrameCrop.MAX_ZOOM, c.zoom, 0f)
        val crop = CropMath.crop(1.5f, 1f, c.zoom, c.offsetX, c.offsetY)
        assertTrue(crop.left >= -1e-5f && crop.left + crop.width <= 1.00001f)
        assertTrue(crop.top >= -1e-5f && crop.top + crop.height <= 1.00001f)
    }

    @Test
    fun innerEdgesAreInsetByHalfTheGap() {
        val m = CanvasMapping(1000f, 1000f, CompositionStyle(gap = 0.02f, outerBorder = false))
        val left = m.frameRect(FRect(0f, 0f, 0.5f, 1f))
        assertEquals(0f, left.left, 1e-3f)
        assertEquals(490f, left.right, 1e-3f)
        val bordered = CanvasMapping(1000f, 1000f, CompositionStyle(gap = 0.02f, outerBorder = true))
        assertEquals(20f, bordered.frameRect(FRect(0f, 0f, 0.5f, 1f)).left, 1e-3f)
        assertNotNull(bordered)
    }
}
