package app.ultragrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageTemplateTest {
    @Test
    fun cellsStayInsideCanvas() {
        CollageTemplate.entries.forEach { template ->
            template.cells.forEach { cell ->
                assertTrue(template.name, cell.left >= 0f && cell.top >= 0f)
                assertTrue(template.name, cell.right <= 1f && cell.bottom <= 1f)
                assertTrue(template.name, cell.left < cell.right && cell.top < cell.bottom)
            }
        }
    }

    @Test
    fun tapInCellCenterFindsThatCell() {
        CollageTemplate.entries.forEach { template ->
            template.cells.forEachIndexed { index, cell ->
                val x = (cell.left + cell.right) / 2f
                val y = (cell.top + cell.bottom) / 2f
                assertEquals(template.name, index, template.cellAt(x, y))
            }
        }
    }

    @Test
    fun cellsCoverWholeCanvas() {
        CollageTemplate.entries.forEach { template ->
            val area = template.cells.sumOf { ((it.right - it.left) * (it.bottom - it.top)).toDouble() }
            assertEquals(template.name, 1.0, area, 1e-4)
        }
    }
}
