package org.bytefred.arrview.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.triangleFlip
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The real app driven by clicks: header, toolbar status, and faces on the canvas. */
@OptIn(ExperimentalTestApi::class)
class CanvasUiTest {
    private val start = Examples.start.arrangement()

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun headerAndToolbarStatus() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        // the desktop build shows the app name without the web subtitle
        onNodeWithText("ArrView").assertIsDisplayed()
        val status = onNodeWithTag("toolbar-status")
        assertEquals("n6-digonfree-nonr-8-triangles${STATUS_SEPARATOR}n = 6", status.text())
        s.showInfo("Saved x.txt")
        waitForIdle()
        assertEquals("n6-digonfree-nonr-8-triangles${STATUS_SEPARATOR}n = 6${STATUS_SEPARATOR}Saved x.txt", status.text())
    }

    @Test
    fun clickOnATriangleFlipsIt() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        setContent { App(s, view, DialogKind.NONE) }
        waitUntil(timeoutMillis = 10_000) { s.drawing?.arrangement == start && view.fittedRequest == s.fitRequest }
        val d = s.drawing!!
        val t = start.faces.indices.first { start.faces[it].size == 3 && it != d.outerFace }
        onNodeWithTag("canvas").performTouchInput { click(view.at(d, t)) }
        waitForIdle()
        assertEquals(start.triangleFlip(t), s.arrangement)
        waitUntil(timeoutMillis = 10_000) { s.drawing?.arrangement == s.arrangement }
        assertEquals("n6-digonfree-nonr-8-triangles (modified)${STATUS_SEPARATOR}n = 6${STATUS_SEPARATOR}Flipped a triangle", onNodeWithTag("toolbar-status").text())
        // Edit, Undo: back to the start
        onNodeWithTag("menu-Edit").performClick()
        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertEquals(start, s.arrangement)
    }

    @Test
    fun rightClickMakesAFaceTheOuterFace() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        setContent { App(s, view, DialogKind.NONE) }
        waitUntil(timeoutMillis = 10_000) { s.drawing?.arrangement == start && view.fittedRequest == s.fitRequest }
        val d = s.drawing!!
        val f = start.faces.indices.first { start.faces[it].size == 4 && it != d.outerFace }
        onNodeWithTag("canvas").performMouseInput { rightClick(view.at(d, f)) }
        onNodeWithText("Face with 4 sides").assertIsDisplayed()
        onNodeWithText("Make outer face").performClick()
        waitUntil(timeoutMillis = 10_000) { s.outerFace == f }
        assertNotEquals(d.outerFace, f)
        assertEquals(start, s.arrangement, "only the view changed")
        // an undo step (Manfred, 2026-11-19)
        assertTrue(s.canUndo, "the outer face is an undo step")
        onNodeWithTag("menu-Edit").performClick()
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 10_000) { s.outerFace == d.outerFace }
    }
}
