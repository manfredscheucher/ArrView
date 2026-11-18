package org.bytefred.arrview.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.triangleFlip
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The canvas and the keyboard driven like a user: digon clicks in both models, the refusal, the
 * face menus, hover, wheel zoom, panning, the model chips, the shortcuts (CanvasUiTest has the
 * triangle click and Make outer face).
 */
@OptIn(ExperimentalTestApi::class)
class CanvasInteractionUiTest {
    private val start = Examples.start.arrangement()

    /** The NonKrupp: three digons, model 1 and model 2. */
    private val nonKrupp = Examples.byName("n3-intersecting-nonkrupp")!!.arrangement(0)

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    /** Shows the app with [arr] loaded and waits for its fitted drawing in [model]. */
    private fun ComposeUiTest.show(s: AppState, view: ViewTransform, arr: Arrangement = start, model: DrawModel = DrawModel.MODEL1) {
        setContent { App(s, view, DialogKind.NONE) }
        runOnIdle {
            if (arr != s.arrangement) s.load("test", arr)
            s.changeModel(model)
        }
        awaitFitted(s, view)
    }

    private fun ComposeUiTest.awaitFitted(s: AppState, view: ViewTransform) =
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null && view.fittedRequest == s.fitRequest }

    private fun ComposeUiTest.keys(block: KeyInjectionScope.() -> Unit) {
        onNodeWithTag("canvas").performKeyInput(block)
        waitForIdle()
    }

    @Test
    fun clickOnAModel1TouchingPointCollapsesTheDigon() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, nonKrupp)
        val d = s.currentDrawing!!
        assertEquals(DrawModel.MODEL1, d.model)
        val (f, at) = d.touchings.entries.first()
        // within 10 px of the dot
        onNodeWithTag("canvas").performMouseInput { click(view.toScreen(at) + Offset(6f, 0f)) }
        waitForIdle()
        assertEquals(nonKrupp.digonCollapse(f), s.arrangement)
        assertEquals("Collapsed a digon", s.status?.text)
        // no longer intersecting: drawn in model 2, the model 1 chip is off
        assertEquals(DrawModel.MODEL2, s.model)
        onNodeWithText("Model 1").assertIsNotEnabled()
    }

    @Test
    fun clickOnAModel2DigonCollapsesItOrIsRefused() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, nonKrupp, DrawModel.MODEL2)
        val digon = s.bounded(2)
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, digon)) }
        waitForIdle()
        assertEquals(nonKrupp.digonCollapse(digon), s.arrangement)
        // two circles: every collapse would make them disjoint
        runOnIdle { s.load("two", Arrangement.two()) }
        awaitFitted(s, view)
        val d = s.currentDrawing!!
        onNodeWithTag("canvas").performMouseInput { click(view.at(d, s.bounded(2))) }
        waitForIdle()
        assertEquals(Arrangement.two(), s.arrangement)
        assertTrue(onNodeWithTag("toolbar-status").text()!!.endsWith("the arrangement would fall apart"))
    }

    @Test
    fun faceMenusOfTriangleDigonAndOuterFace() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, nonKrupp, DrawModel.MODEL2)
        var d = s.currentDrawing!!
        // a digon in model 2: collapse, or draw it outside
        onNodeWithTag("canvas").performMouseInput { rightClick(view.at(d, s.bounded(2))) }
        onNodeWithText("Digon").assertExists()
        menuItem("Make outer face").assertExists()
        menuItem("Flip triangle").assertDoesNotExist()
        val digon = s.bounded(2)
        menuItem("Collapse digon").performClick()
        waitForIdle()
        assertEquals(nonKrupp.digonCollapse(digon), s.arrangement)
        // a triangle: flip
        runOnIdle { s.undo() }
        awaitFitted(s, view)
        d = s.currentDrawing!!
        val t = s.bounded(3)
        onNodeWithTag("canvas").performMouseInput { rightClick(view.at(d, t)) }
        onNodeWithText("Triangle").assertExists()
        menuItem("Flip triangle").performClick()
        waitForIdle()
        assertEquals(nonKrupp.triangleFlip(t), s.arrangement)
        // the outer face (any point outside the drawing): no Make outer face
        runOnIdle { s.undo() }
        awaitFitted(s, view)
        onNodeWithTag("canvas").performMouseInput { rightClick(Offset(3f, 3f)) }
        onNodeWithText("(outer face)", substring = true).assertExists()
        menuItem("Make outer face").assertDoesNotExist()
    }

    @Test
    fun aModel1TouchingPointCannotBeTheOuterFace() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, nonKrupp)
        val at = s.currentDrawing!!.touchings.values.first()
        onNodeWithTag("canvas").performMouseInput { rightClick(view.toScreen(at)) }
        onNodeWithText("Digon").assertExists()
        menuItem("Collapse digon").assertExists()
        menuItem("Make outer face").assertDoesNotExist()
    }

    @Test
    fun hoverMarksTheFaceUnderTheMouse() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val d = s.currentDrawing!!
        val t = s.bounded(3)
        // the first event of a mouse coming in is an Enter, the hover follows the moves
        onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d, t) - Offset(1f, 0f)); moveTo(view.at(d, t)) }
        waitForIdle()
        assertEquals(t, s.hoverFace)
        val q = s.bounded(4)
        onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d, q)) }
        waitForIdle()
        assertEquals(q, s.hoverFace)
        // the mouse leaves the canvas
        onNodeWithTag("canvas").performMouseInput { moveTo(Offset(-20f, -20f)) }
        waitForIdle()
        assertNull(s.hoverFace)
        onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d, t) - Offset(1f, 0f)); moveTo(view.at(d, t)) }
        waitForIdle()
        assertEquals(t, s.hoverFace)
        // a click changes the arrangement: the hover is gone
        onNodeWithTag("canvas").performMouseInput { click(view.at(d, t)) }
        waitForIdle()
        assertNull(s.hoverFace)
    }

    @Test
    fun wheelZoomsAndDragPansWithoutClicking() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val d = s.currentDrawing!!
        val scale = view.scale
        val at = view.at(d, s.bounded(3))
        onNodeWithTag("canvas").performMouseInput { moveTo(at); scroll(-1f) }
        waitForIdle()
        assertEquals(scale * 1.15.pow(1), view.scale, 1e-9)
        // the point under the mouse stays where it is
        assertEquals(at.x.toDouble(), view.toScreen(d.inside(s.bounded(3))).x.toDouble(), 0.5)
        val ox = view.ox
        val oy = view.oy
        // a drag that starts on a triangle pans and does not flip it
        onNodeWithTag("canvas").performMouseInput {
            press()
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 30f))
            release()
        }
        waitForIdle()
        assertEquals(ox + 80, view.ox, 0.5)
        assertEquals(oy + 30, view.oy, 0.5)
        assertEquals(start, s.arrangement)
    }

    @Test
    fun toolbarModelChipsSwitchTheDrawingAlsoAfterEdits() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, s.bounded(3))) }
        waitForIdle()
        val flipped = s.arrangement
        assertNotEquals(start, flipped)
        onNodeWithText("Model 2").performClick()
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing?.model == DrawModel.MODEL2 }
        assertEquals(flipped, s.currentDrawing!!.arrangement)
        onNodeWithText("Model 1").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing?.model == DrawModel.MODEL1 }
        // undo keeps the chosen model
        runOnIdle { s.undo() }
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing?.arrangement == start }
        assertEquals(DrawModel.MODEL1, s.currentDrawing!!.model)
    }

    @Test
    fun clicksWaitForTheDrawingOfTheCurrentArrangement() = runComposeUiTest {
        // a layout dispatcher that can be held: the drawing stays at the previous arrangement
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
        val held = ConcurrentLinkedQueue<Runnable>()
        val hold = AtomicBoolean(false)
        val layout = Executor { r -> if (hold.get()) held.add(r) else pool.execute(r) }.asCoroutineDispatcher()
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default), layout)
        val view = ViewTransform()
        show(s, view)
        val d = s.currentDrawing!!
        val triangles = start.faces.indices.filter { start.faces[it].size == 3 && it != d.outerFace }
        hold.set(true)
        onNodeWithTag("canvas").performMouseInput { click(view.at(d, triangles[0])) }
        waitForIdle()
        val flipped = start.triangleFlip(triangles[0])
        assertEquals(flipped, s.arrangement)
        assertNull(s.currentDrawing)
        // the old drawing is still shown: a click on it does nothing, Export SVG waits
        onNodeWithTag("canvas").performMouseInput { click(view.at(d, triangles[1])) }
        waitForIdle()
        assertEquals(flipped, s.arrangement)
        onNodeWithTag("menu-Arrangement").performClick()
        menuItem("Export SVG").assertIsNotEnabled()
        hold.set(false)
        while (held.isNotEmpty()) pool.execute(held.poll())
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }
        menuItem("Export SVG").assertIsEnabled()
    }

    @Test
    fun loadFitsTheNewDrawingNotThePreviousOne() = runComposeUiTest {
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
        val held = ConcurrentLinkedQueue<Runnable>()
        val hold = AtomicBoolean(false)
        val layout = Executor { r -> if (hold.get()) held.add(r) else pool.execute(r) }.asCoroutineDispatcher()
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default), layout)
        val view = ViewTransform()
        show(s, view)
        val pappus = Examples.byName("n9-great-nonr-pappus")!!.arrangement(0)
        hold.set(true)
        runOnIdle { s.load("pappus", pappus) }
        waitForIdle()
        hold.set(false)
        while (held.isNotEmpty()) pool.execute(held.poll())
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing?.arrangement == pappus }
        waitForIdle()
        val expected = ViewTransform().apply { size = view.size; fit(s.currentDrawing!!.bounds) }
        assertEquals(expected.scale, view.scale, 1e-9)
        assertEquals(expected.ox, view.ox, 1e-6)
        assertEquals(expected.oy, view.oy, 1e-6)
    }

    @Test
    fun undoRedoShortcuts() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        runOnIdle { s.flipTriangle(s.bounded(3)) }
        val flipped = s.arrangement
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        assertEquals(start, s.arrangement)
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Y) } }
        assertEquals(flipped, s.arrangement)
        // Cmd works like Ctrl; Ctrl+Shift+Z is redo too
        keys { withKeyDown(Key.MetaLeft) { pressKey(Key.Z) } }
        assertEquals(start, s.arrangement)
        keys { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) } } }
        assertEquals(flipped, s.arrangement)
        keys { withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) } } }
        assertEquals(flipped, s.arrangement, "nothing left to redo")
        assertFalse(s.canRedo)
    }

    @Test
    fun zoomAndFitKeys() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val fitted = view.scale
        for (key in listOf(Key.Plus, Key.Equals, Key.NumPadAdd)) {
            val before = view.scale
            keys { pressKey(key) }
            assertEquals(before * ZOOM_STEP, view.scale, 1e-9, key.toString())
        }
        for (key in listOf(Key.Minus, Key.NumPadSubtract)) {
            val before = view.scale
            keys { pressKey(key) }
            assertEquals(before / ZOOM_STEP, view.scale, 1e-9, key.toString())
        }
        for (key in listOf(Key.Zero, Key.MoveHome)) {
            keys { pressKey(Key.Plus) }
            assertNotEquals(fitted, view.scale)
            keys { pressKey(key) }
            assertEquals(fitted, view.scale, 1e-9, key.toString())
        }
    }

    @Test
    fun shortcutsAreOffInAMenuAndInATextField() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        runOnIdle { s.flipTriangle(s.bounded(3)) }
        val flipped = s.arrangement
        val scale = view.scale
        // a menu is open: only Escape, which closes it
        onNodeWithTag("menu-View").performClick()
        waitForIdle()
        onNodeWithTag("menu-bar").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Plus) }
        waitForIdle()
        assertEquals(flipped, s.arrangement)
        assertEquals(scale, view.scale)
        assertEquals("View", s.openMenu)
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertNull(s.openMenu)
        // typing in a field
        onNodeWithText("Layout").performClick()
        onNodeWithTag("eps").performClick()
        waitForIdle()
        assertTrue(s.textFieldsFocused > 0)
        onNodeWithTag("eps").performKeyInput { pressKey(Key.Minus); pressKey(Key.Zero) }
        waitForIdle()
        assertEquals(scale, view.scale)
        assertEquals(flipped, s.arrangement)
        assertNotNull(s.currentDrawing)
    }

    @Test
    fun aCanvasPressWhileAMenuIsOpenOnlyClosesIt() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val at = view.at(s.currentDrawing!!, s.bounded(3))
        onNodeWithTag("menu-Edit").performClick()
        waitForIdle()
        onNodeWithTag("canvas").performMouseInput { click(at) }
        waitForIdle()
        assertNull(s.openMenu)
        assertEquals(start, s.arrangement)
        // the next click flips
        onNodeWithTag("canvas").performMouseInput { click(at) }
        waitForIdle()
        assertNotEquals(start, s.arrangement)
    }

    @Test
    fun ctrlClickIsARightClickOnlyOnTheMac() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val t = s.bounded(3)
        onNodeWithTag("canvas").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, t)) }
        onNodeWithTag("canvas").performKeyInput { keyUp(Key.CtrlLeft) }
        waitForIdle()
        if (ctrlClickIsRightClick) {
            onNodeWithText("Triangle").assertExists()
            assertEquals(start, s.arrangement)
        } else {
            assertEquals(start.triangleFlip(t), s.arrangement)
        }
    }

    @Test
    fun pinchZooms() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val scale = view.scale
        val c = Offset(view.size.width / 2f, view.size.height / 2f)
        onNodeWithTag("canvas").performTouchInput {
            down(0, c - Offset(50f, 0f))
            down(1, c + Offset(50f, 0f))
            moveTo(1, c + Offset(150f, 0f))
            up(0)
            up(1)
        }
        waitForIdle()
        // the fingers moved apart from 100 to 200 px
        assertEquals(scale * 2, view.scale, scale * 0.01)
        assertEquals(start, s.arrangement, "a pinch is no click")
    }

    @Test
    fun draggingTheDividerResizesTheWidgetColumn() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        assertTrue(s.wideWeights.isEmpty())
        val canvasWidth = onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot.width
        onNodeWithTag("divider-0").performMouseInput { moveTo(center); press(); moveBy(Offset(-100f, 0f)); release() }
        waitForIdle()
        assertTrue(s.wideWeights.isNotEmpty())
        assertEquals(canvasWidth - 100f, onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot.width, 2f)
    }
}
