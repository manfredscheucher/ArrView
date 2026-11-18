package org.bytefred.arrview.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.triangleFlip
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** A long press on a touch screen opens the face menu like a right-click (web gestures). */
@OptIn(ExperimentalTestApi::class)
class LongPressUiTest {
    private val start = Examples.start.arrangement()
    private val web = MenuGestures(rightClick = true, longPress = true)

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun ComposeUiTest.show(s: AppState, view: ViewTransform, gestures: MenuGestures = web) {
        setContent {
            CompositionLocalProvider(LocalMenuGestures provides gestures) { App(s, view, DialogKind.NONE) }
        }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null && view.fittedRequest == s.fitRequest }
    }

    @Test
    fun longPressOnATriangleOpensItsMenuAndTheReleaseDoesNotFlip() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val t = s.bounded(3)
        onNodeWithTag("canvas").performTouchInput { longClick(view.at(s.currentDrawing!!, t)) }
        waitForIdle()
        onNodeWithText("Triangle").assertExists()
        assertEquals(start, s.arrangement)
        menuItem("Flip triangle").performClick()
        waitForIdle()
        assertEquals(start.triangleFlip(t), s.arrangement)
    }

    @Test
    fun longPressOnTheOuterFaceOpensItsMenu() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput { longClick(Offset(3f, 3f)) }
        waitForIdle()
        onNodeWithText("(outer face)", substring = true).assertExists()
        assertEquals(start, s.arrangement)
    }

    @Test
    fun pressThenMovePansAndHoldingAfterwardsOpensNoMenu() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val at = view.at(s.currentDrawing!!, s.bounded(3))
        val ox = view.ox
        val oy = view.oy
        onNodeWithTag("canvas").performTouchInput {
            down(at)
            moveBy(Offset(40f, 0f))
            // held still well past the long-press timeout while panning
            advanceEventTime(2 * viewConfiguration.longPressTimeoutMillis)
            moveBy(Offset(40f, 30f))
            up()
        }
        waitForIdle()
        onNodeWithText("Triangle").assertDoesNotExist()
        assertEquals(ox + 80, view.ox, 0.5)
        assertEquals(oy + 30, view.oy, 0.5)
        assertEquals(start, s.arrangement)
    }

    @Test
    fun aHoldJustBelowTheTimeoutIsATap() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val t = s.bounded(3)
        onNodeWithTag("canvas").performTouchInput {
            down(view.at(s.currentDrawing!!, t))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis - 50)
            up()
        }
        waitForIdle()
        onNodeWithText("Triangle").assertDoesNotExist()
        assertEquals(start.triangleFlip(t), s.arrangement)
    }

    @Test
    fun aShortTapStillFlips() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val t = s.bounded(3)
        onNodeWithTag("canvas").performTouchInput { click(view.at(s.currentDrawing!!, t)) }
        waitForIdle()
        onNodeWithText("Triangle").assertDoesNotExist()
        assertEquals(start.triangleFlip(t), s.arrangement)
    }

    @Test
    fun onTheDesktopALongPressIsAnOrdinaryClick() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        assertFalse(platformMenuGestures.longPress)
        show(s, view, platformMenuGestures)
        val t = s.bounded(3)
        onNodeWithTag("canvas").performTouchInput { longClick(view.at(s.currentDrawing!!, t)) }
        waitForIdle()
        onNodeWithText("Triangle").assertDoesNotExist()
        assertEquals(start.triangleFlip(t), s.arrangement)
    }
}
