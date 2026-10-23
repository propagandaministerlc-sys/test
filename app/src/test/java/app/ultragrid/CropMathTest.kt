package app.ultragrid

import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {
    private val eps = 1e-5f

    @Test
    fun wideImageFillsHeightAndCentersHorizontally() {
        val crop = CropMath.crop(imageAspect = 2f, frameAspect = 1f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(0.5f, crop.width, eps)
        assertEquals(1f, crop.height, eps)
        assertEquals(0.25f, crop.left, eps)
        assertEquals(0f, crop.top, eps)
    }

    @Test
    fun tallImageFillsWidth() {
        val crop = CropMath.crop(imageAspect = 0.5f, frameAspect = 1f, zoom = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(1f, crop.width, eps)
        assertEquals(0.5f, crop.height, eps)
        assertEquals(0.25f, crop.top, eps)
    }

    @Test
    fun maxOffsetPushesCropToImageEdge() {
        val (w, _) = CropMath.cropSize(imageAspect = 2f, frameAspect = 1f, zoom = 1f)
        val max = CropMath.maxOffset(w)
        val right = CropMath.crop(2f, 1f, 1f, offsetX = max, offsetY = 0f)
        assertEquals(0f, right.left, eps)
        val left = CropMath.crop(2f, 1f, 1f, offsetX = -max, offsetY = 0f)
        assertEquals(1f, left.left + left.width, eps)
    }

    @Test
    fun zoomShrinksCrop() {
        val crop = CropMath.crop(imageAspect = 1f, frameAspect = 1f, zoom = 2f, offsetX = 0f, offsetY = 0f)
        assertEquals(0.5f, crop.width, eps)
        assertEquals(0.25f, crop.left, eps)
    }

    @Test
    fun postOrderStartsBottomRight() {
        assertEquals(9, CropMath.postOrder(row = 0, column = 0, rows = 3))
        assertEquals(1, CropMath.postOrder(row = 2, column = 2, rows = 3))
        assertEquals(4, CropMath.postOrder(row = 1, column = 2, rows = 3))
    }
}
