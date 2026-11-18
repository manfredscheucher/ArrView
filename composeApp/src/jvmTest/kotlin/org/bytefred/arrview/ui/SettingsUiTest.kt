package org.bytefred.arrview.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.ArrangementLayout
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.defaultOuterFace
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The Settings widget by clicks: colour scheme dropdown, line width slider, Reset, remembered settings. */
@OptIn(ExperimentalTestApi::class)
class SettingsUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun close(a: Color, b: Color) = abs(a.red - b.red) < 0.03f && abs(a.green - b.green) < 0.03f && abs(a.blue - b.blue) < 0.03f

    /** The colour of the intersection graph's vertex dots, as drawn. */
    private fun ComposeUiTest.vertexColors(n: Int): List<Color> {
        val node = onNodeWithTag("intersection-graph")
        val size = node.fetchSemanticsNode().size.let { Size(it.width.toFloat(), it.height.toFloat()) }
        val circle = with(density) { vertexCircle(n, size) }
        val map = node.captureToImage().toPixelMap()
        return (0 until n).map { c -> circle.at(c).let { map[it.x.toInt(), it.y.toInt()] } }
    }

    /** Pixels of the canvas that are not the background. */
    private fun ComposeUiTest.inkedPixels(): Int {
        val map = onNodeWithTag("canvas").captureToImage().toPixelMap()
        val bg = map[1, 1]
        var count = 0
        for (x in 0 until map.width step 2) for (y in 0 until map.height step 2) if (!close(map[x, y], bg)) count++
        return count
    }

    @Test
    fun colourSchemeLineWidthAndReset() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle {
            s.toggleWidget(Widget.PROPERTIES)
            s.toggleWidget(Widget.SETTINGS)
            s.toggleWidget(Widget.INTERSECTION_GRAPH)
        }
        awaitDrawing(s)
        waitForIdle()
        val n = s.arrangement.n
        onNodeWithText("Display").assertExists()
        onNodeWithText("Layout").assertExists()
        onNodeWithText("Reset to defaults").assertIsNotEnabled()
        // the default rainbow2 colours the graph's vertices
        assertEquals(ColorScheme.RAINBOW2, s.colorScheme)
        for ((c, color) in vertexColors(n).withIndex()) assertTrue(close(color, ColorScheme.RAINBOW2.color(c, n)), "vertex $c: $color")
        // the dropdown: rainbow3-k
        onNodeWithTag("color-scheme").performClick()
        waitForIdle()
        for (scheme in ColorScheme.entries) menuItem(scheme.label).assertExists()
        menuItem("rainbow3-k").performClick()
        waitForIdle()
        assertEquals(ColorScheme.RAINBOW3_K, s.colorScheme)
        for ((c, color) in vertexColors(n).withIndex()) assertTrue(close(color, ColorScheme.RAINBOW3_K.color(c, n)), "vertex $c: $color")
        assertNotEquals(ColorScheme.RAINBOW2.color(1, n), ColorScheme.RAINBOW3_K.color(1, n))
        // the slider: far right is 8 px, the curves get wider
        val before = inkedPixels()
        onNodeWithTag("line-width").performTouchInput { swipe(center, percentOffset(1f, 0.5f)) }
        waitForIdle()
        assertTrue(s.lineWidth > 7.5f, "line width ${s.lineWidth}")
        assertTrue(inkedPixels() > before, "wider curves")
        onNodeWithText("Line width: ${oneDecimal(s.lineWidth)} px").assertExists()
        assertEquals("3.0", oneDecimal(3f))
        assertEquals("0.5", oneDecimal(0.5f))
        onNodeWithTag("line-width").performTouchInput { swipe(center, percentOffset(0f, 0.5f)) }
        waitForIdle()
        assertTrue(s.lineWidth < 1f, "line width ${s.lineWidth}")
        // Reset to defaults: everything in the widget
        runOnIdle { s.changeModel(DrawModel.MODEL2); s.changeLayoutSettings(LayoutSettings(9, 1e-3)) }
        onNodeWithText("Reset to defaults").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(ColorScheme.DEFAULT, s.colorScheme)
        assertEquals(AppState.DEFAULT_LINE_WIDTH, s.lineWidth)
        assertEquals(DrawModel.MODEL1, s.preferredModel)
        assertEquals(LayoutSettings(), s.layoutSettings)
        onNodeWithText("Reset to defaults").assertIsNotEnabled()
    }

    @Test
    fun schemeAndLineWidthAreRemembered() {
        val s = state()
        s.colorScheme = ColorScheme.RAINBOW3_2
        s.lineWidth = 5.5f
        s.saveDisplayPrefs()
        val t = state()
        assertEquals(ColorScheme.RAINBOW3_2, t.colorScheme)
        assertEquals(5.5f, t.lineWidth)
        // corrupt or out of range values: default or clamped
        savePref(COLOR_SCHEME_PREF, "PURPLE")
        savePref(LINE_WIDTH_PREF, "99")
        val u = state()
        assertEquals(ColorScheme.DEFAULT, u.colorScheme)
        assertEquals(8f, u.lineWidth)
        savePref(LINE_WIDTH_PREF, "NaN")
        assertEquals(AppState.DEFAULT_LINE_WIDTH, state().lineWidth)
    }

    @Test
    fun anOldStoredLayoutWidgetOpensSettings() {
        savePref(OPEN_WIDGETS_PREF, "LAYOUT,PROPERTIES")
        assertEquals(listOf(Widget.SETTINGS, Widget.PROPERTIES), state().openWidgets.toList())
    }

    @Test
    fun svgUsesTheSchemeAndTheLineWidth() {
        val arr = Examples.byName("n9-great-nonr-pappus")!!.arrangement(0)
        val d = ArrangementLayout(arr, DrawModel.MODEL2, arr.defaultOuterFace(DrawModel.MODEL2)).layout(LayoutSettings())
        val svg = drawingSvg(d, ColorScheme.RAINBOW3_K, 4.5f)
        for (c in 0 until arr.n) assertTrue("stroke=\"${ColorScheme.RAINBOW3_K.color(c, arr.n).toHex()}\" stroke-width=\"4.5\"" in svg, "circle $c")
    }

    @Test
    fun standardIsTheOldFixedList() {
        assertEquals(Color(0xFFE53935), ColorScheme.STANDARD.color(0, 5))
        assertEquals(ColorScheme.STANDARD.color(0, 20), ColorScheme.STANDARD.color(12, 20))
        // the rainbow schemes depend on n
        assertNotEquals(ColorScheme.RAINBOW.color(1, 4), ColorScheme.RAINBOW.color(1, 9))
    }
}
