package org.bytefred.arrview.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Point
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.sequencesText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Refit on resize (PoCoView's semantics): every canvas size change refits the drawing until the user
 * zooms or pans; then the view is kept. Fit and loading bring the automatic fit back.
 */
@OptIn(ExperimentalTestApi::class)
class RefitOnResizeUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun ComposeUiTest.start(s: AppState, view: ViewTransform) {
        setContent { App(s, view, DialogKind.NONE) }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null && view.fittedRequest == s.fitRequest }
    }

    /** The drawing's bounding box lies inside the canvas. */
    private fun assertInside(s: AppState, view: ViewTransform) {
        val (minX, minY, maxX, maxY) = s.drawing!!.bounds.toList()
        for (p in listOf(Point(minX, minY), Point(maxX, maxY))) {
            val q = view.toScreen(p)
            assertTrue(q.x in 0f..view.size.width.toFloat() && q.y in 0f..view.size.height.toFloat(), "$q outside ${view.size}")
        }
    }

    /** The view is exactly a fresh fit of the drawing to the current canvas size. */
    private fun assertFitted(s: AppState, view: ViewTransform) {
        val fresh = ViewTransform().apply { size = view.size; fit(s.drawing!!.bounds) }
        assertEquals(fresh.scale, view.scale, 1e-9)
        assertEquals(fresh.ox, view.ox, 1e-6)
        assertEquals(fresh.oy, view.oy, 1e-6)
    }

    @Test
    fun openingAndClosingWidgetsRefits() = runDesktopComposeUiTest(1200, 800) {
        val s = state()
        val view = ViewTransform()
        start(s, view)
        val initial = view.size
        for (w in Widget.entries) {
            onNodeWithTag("widget-chip-${w.name}").performClick()
            waitForIdle()
            assertInside(s, view)
            assertFitted(s, view)
        }
        assertNotEquals(initial, view.size)
        runOnIdle { s.openWidgets.clear() }
        waitForIdle()
        assertTrue(view.size.width > initial.width)
        assertFitted(s, view)
        // portrait: the widgets below the canvas
        runOnIdle { s.toggleWidget(Widget.PROPERTIES); s.layoutMode = LayoutMode.PORTRAIT }
        waitForIdle()
        assertInside(s, view)
        assertFitted(s, view)
    }

    @Test
    fun afterAZoomOrPanTheViewIsKept() = runDesktopComposeUiTest(1200, 800) {
        val s = state()
        val view = ViewTransform()
        start(s, view)
        val canvas = onNodeWithTag("canvas")
        // a wheel zoom, then a widget opens: same scale, no refit
        canvas.performMouseInput { moveTo(center); scroll(-1f) }
        waitForIdle()
        assertTrue(view.userAdjusted)
        val scale = view.scale
        onNodeWithTag("widget-chip-${Widget.ENCODING.name}").performClick()
        waitForIdle()
        assertEquals(scale, view.scale)
        // Fit brings the automatic fit back
        runOnIdle { s.requestFit() }
        waitUntil(timeoutMillis = 10_000) { view.fittedRequest == s.fitRequest }
        assertFalse(view.userAdjusted)
        onNodeWithTag("widget-chip-${Widget.ENCODING.name}").performClick()
        waitForIdle()
        assertFitted(s, view)
        // a pan (dragging the canvas), then a widget closes: kept
        canvas.performMouseInput { moveTo(center); press(); moveBy(Offset(30f, 15f)); moveBy(Offset(30f, 15f)); release() }
        waitForIdle()
        assertTrue(view.userAdjusted)
        val ox = view.ox
        onNodeWithTag("widget-chip-${Widget.PROPERTIES.name}").performClick()
        waitForIdle()
        assertNotEquals(ox, view.ox, "the view keeps its centre: shifted by half the size change")
        val keptScale = view.scale
        runOnIdle { s.toggleWidget(Widget.TRIANGLES) }
        waitForIdle()
        assertEquals(keptScale, view.scale)
        // loading an example: fitted again, and auto-fit is back
        runOnIdle { s.load("again", s.arrangement) }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null && view.fittedRequest == s.fitRequest }
        assertFalse(view.userAdjusted)
        runOnIdle { s.toggleWidget(Widget.TRIANGLES) }
        waitForIdle()
        assertFitted(s, view)
    }

    @Test
    fun theZoomKeyCountsAsZoom() = runDesktopComposeUiTest(1200, 800) {
        val s = state()
        val view = ViewTransform()
        start(s, view)
        onNodeWithTag("menu-View").performClick()
        onNodeWithText("Zoom in").performClick()
        waitForIdle()
        assertTrue(view.userAdjusted)
        val scale = view.scale
        runOnIdle { s.toggleWidget(Widget.SETTINGS) }
        waitForIdle()
        assertEquals(scale, view.scale)
        // an edit (a flip) does not count and does not refit
        val before = s.arrangement
        val t = s.arrangement.faces.indices.first { s.arrangement.faces[it].size == 3 }
        runOnIdle { s.flipTriangle(t) }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }
        assertTrue(view.userAdjusted)
        assertEquals(scale, view.scale)
        // Apply in the Encoding widget fits (as PoCoView's Apply): auto-fit again
        runOnIdle { s.drafts[TextFormat.SEQUENCES] = sequencesText(before) }
        runOnIdle { s.applyDraft(TextFormat.SEQUENCES) }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null && view.fittedRequest == s.fitRequest }
        assertFalse(view.userAdjusted)
        assertFitted(s, view)
    }
}
