package com.tonapps.uikit.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlassColorMathTest {

    @Test
    fun regularMiddleCompositeMatchesFigmaInEveryTheme() {
        assertMiddleComposite(GlassStyle.Regular, BLUE_CONTENT, BLUE_PAGE, 0x1A232F)
        assertMiddleComposite(GlassStyle.Regular, DARK_CONTENT, DARK_PAGE, 0x121215)
        assertMiddleComposite(GlassStyle.Regular, LIGHT_CONTENT, LIGHT_PAGE, 0xFCFCFD)
    }

    @Test
    fun prominentMiddleMatchesFigmaInEveryTheme() {
        assertMiddleComposite(GlassStyle.Prominent, BLUE_ACCENT, BLUE_PAGE, 0x2E87EC)
        assertMiddleComposite(GlassStyle.Prominent, BLUE_ACCENT, DARK_PAGE, 0x2D86EB)
        assertMiddleComposite(GlassStyle.Prominent, LIGHT_ACCENT, LIGHT_PAGE, 0x0079FE)
    }

    @Test
    fun blueRegularTopAndBottomCompositesMatchFigma() {
        val stops = GlassColorMath.fillStops(GlassStyle.Regular, BLUE_CONTENT, BLUE_PAGE)

        assertClose(0x121B27, GlassColorMath.over(stops.first(), BLUE_PAGE))
        assertClose(0x262F3B, GlassColorMath.over(stops.last(), BLUE_PAGE))
    }

    @Test
    fun blueProminentTopAndBottomMatchFigma() {
        val stops = GlassColorMath.fillStops(GlassStyle.Prominent, BLUE_ACCENT, BLUE_PAGE)

        assertClose(0x267FE4, GlassColorMath.over(stops.first(), BLUE_PAGE))
        assertClose(0x3A93F8, GlassColorMath.over(stops.last(), BLUE_PAGE))
    }

    @Test
    fun lightRegularBottomIsClampedToWhite() {
        val stops = GlassColorMath.fillStops(GlassStyle.Regular, LIGHT_CONTENT, LIGHT_PAGE)

        assertEquals(0xCCFFFFFF.toInt(), stops.last())
        assertClose(0xFCFCFD, GlassColorMath.over(stops.last(), LIGHT_PAGE))
    }

    @Test
    fun regularStopsAreTranslucentAndProminentStopsOpaque() {
        val regular = GlassColorMath.fillStops(GlassStyle.Regular, BLUE_CONTENT, BLUE_PAGE)
        val prominent = GlassColorMath.fillStops(GlassStyle.Prominent, BLUE_ACCENT, BLUE_PAGE)

        assertEquals(GlassColorMath.fillPositions().size, regular.size)
        assertEquals(GlassColorMath.fillPositions().size, prominent.size)
        regular.forEach { assertEquals(0xCC, it ushr 24) }
        prominent.forEach { assertEquals(0xFF, it ushr 24) }
    }

    private fun assertMiddleComposite(style: GlassStyle, tint: Int, page: Int, expected: Int) {
        val stops = GlassColorMath.fillStops(style, tint, page)
        assertClose(expected, GlassColorMath.over(stops[2], page))
    }

    private fun assertClose(expected: Int, actual: Int) {
        val message = "expected #%06X, was #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF)
        for (shift in intArrayOf(16, 8, 0)) {
            val delta = abs(((expected ushr shift) and 0xFF) - ((actual ushr shift) and 0xFF))
            assertTrue(message, delta <= 1)
        }
    }

    private companion object {
        const val BLUE_PAGE = 0xFF10161F.toInt()
        const val BLUE_CONTENT = 0xFF1D2633.toInt()
        const val BLUE_ACCENT = 0xFF3C95FA.toInt()
        const val DARK_PAGE = 0xFF000000.toInt()
        const val DARK_CONTENT = 0xFF17171A.toInt()
        const val LIGHT_PAGE = 0xFFEFEEF3.toInt()
        const val LIGHT_CONTENT = 0xFFFFFFFF.toInt()
        const val LIGHT_ACCENT = 0xFF007AFF.toInt()
    }
}
