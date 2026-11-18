package org.bytefred.arrview.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.CellActions
import org.bytefred.arrview.core.DigonCreate
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.defaultOuterFace
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.digonCreate
import org.bytefred.arrview.core.digonCreates
import org.bytefred.arrview.core.sidePosition
import org.bytefred.arrview.core.triangleFlip
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Digon create and the actions in the graph views, driven by real clicks: canvas (one create,
 * the create menu, the "!" on hover, menu hover highlight), intersection graph (unique pair,
 * menu), triangle hypergraph (direct flip, menu), undo after each.
 */
@OptIn(ExperimentalTestApi::class)
class ActionsUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    /** Connected (not all intersecting) arrangements: they have digon creates. */
    private val pool: List<Arrangement> by lazy {
        listOf("n4-connected-all", "n5-connected-all").flatMap { name ->
            val set = Examples.byName(name)!!
            (0 until set.size).map(set::arrangement)
        }
    }

    private fun ComposeUiTest.show(s: AppState, view: ViewTransform, arr: Arrangement, model: DrawModel = DrawModel.MODEL2, widget: Widget? = null) {
        setContent { App(s, view, DialogKind.NONE) }
        runOnIdle {
            s.load("test", arr)
            s.changeModel(model)
            if (widget != null && widget !in s.openWidgets) s.toggleWidget(widget)
        }
        awaitFitted(s, view)
    }

    private fun ComposeUiTest.awaitFitted(s: AppState, view: ViewTransform) =
        waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null && view.fittedRequest == s.fitRequest }

    /** An arrangement of the pool with a bounded face (model 2 default outer face) with [creates] creates. */
    private fun withCreateFace(creates: (Int) -> Boolean): Pair<Arrangement, Int> =
        findFace(outer = false) { actions, f -> creates(actions.createsIn(f).size) }

    /** The pool, then arrangements with one or two digons created (cells where a circle has several sides). */
    private val candidates: Sequence<Arrangement>
        get() {
            fun created(a: Arrangement) = a.digonCreates().asSequence().map(a::digonCreate)
            return pool.asSequence() + pool.asSequence().flatMap(::created) + pool.asSequence().flatMap(::created).flatMap(::created)
        }

    /**
     * An arrangement and a face where [ok] holds: the outer face of the model 2 drawing if [outer] is
     * true, a bounded one if false, any face if null.
     */
    private fun findFace(outer: Boolean?, ok: (CellActions, Int) -> Boolean): Pair<Arrangement, Int> {
        for (arr in candidates) {
            val o = arr.defaultOuterFace(DrawModel.MODEL2)
            val actions = CellActions(arr)
            val f = arr.faces.indices.firstOrNull { (outer == null || (it == o) == outer) && ok(actions, it) } ?: continue
            return arr to f
        }
        error("no such arrangement")
    }

    /** The creates of face [f] by pair. */
    private fun CellActions.pairsIn(f: Int) = createsIn(f).groupBy { it.i to it.j }

    /** Points at a menu entry and checks what it highlights in the canvas. */
    private fun ComposeUiTest.hoverEntry(s: AppState, label: String, expected: Highlight) {
        menuItem(label).performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(expected, s.menuHighlight, label)
    }

    /** The top of a menu entry on the screen. */
    private fun ComposeUiTest.top(label: String): Float = menuItem(label).fetchSemanticsNode().boundsInRoot.top

    /** An option of [ops] whose star label is unique, with that label. */
    private fun AppState.uniqueOption(ops: List<DigonCreate>): Pair<DigonCreate, String> {
        val labels = ops.map { createNode(it).label }
        val k = labels.indices.first { i -> labels.count { it == labels[i] } == 1 }
        return ops[k] to labels[k]
    }

    private fun ComposeUiTest.canvasSize(): Size =
        onNodeWithTag("canvas").fetchSemanticsNode().size.let { Size(it.width.toFloat(), it.height.toFloat()) }

    /** Ctrl+Z on the canvas gives [arr] back. */
    private fun ComposeUiTest.undoGivesBack(s: AppState, arr: Arrangement) {
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        waitForIdle()
        assertEquals(arr, s.arrangement)
    }

    /** Number of pixels of the "!" badge colour in the canvas. */
    private fun ComposeUiTest.markPixels(): Int {
        val map = onNodeWithTag("canvas").captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until map.width) for (y in 0 until map.height) if (close(map[x, y], MARK_FILL)) count++
        return count
    }

    @Test
    fun canvasClickOnAFaceWithOneCreate() = runComposeUiTest {
        val (arr, f) = withCreateFace { it == 1 }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        val op = s.actions.createsIn(f).single()
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, f)) }
        waitForIdle()
        assertEquals(arr.digonCreate(op), s.arrangement)
        assertTrue(s.status!!.text.startsWith("Created a digon of ${op.i + 1} and ${op.j + 1}"))
        undoGivesBack(s, arr)
    }

    private fun close(a: Color, b: Color) = abs(a.red - b.red) < 0.05f && abs(a.green - b.green) < 0.05f && abs(a.blue - b.blue) < 0.05f

    // ---- intersection graph ------------------------------------------------------------

    /** A point on the edge of pair (i, j) that is farther than the hit distance from every other striped pair. */
    private fun ComposeUiTest.pairPoint(arr: Arrangement, actions: CellActions, size: Size, i: Int, j: Int): Offset? {
        val circle = with(density) { vertexCircle(arr.n, size) }
        val hit = with(density) { 8.dp.toPx() } + 3f
        val others = (0 until arr.n).flatMap { a -> (a + 1 until arr.n).map { b -> a to b } }
            .filter { (a, b) -> (a to b) != (i to j) && actions.pairActionCount(a, b) > 0 }
        for (k in 3..17) {
            val p = circle.at(i) + (circle.at(j) - circle.at(i)) * (k / 20f)
            if (others.all { (a, b) -> segmentDistance(p, circle.at(a), circle.at(b)) > hit }) return p
        }
        return null
    }

    private fun ComposeUiTest.graphSize(tag: String): Size =
        onNodeWithTag(tag).fetchSemanticsNode().size.let { Size(it.width.toFloat(), it.height.toFloat()) }

    /** An arrangement and pair with [count] actions and a clean point on its edge. */
    private fun ComposeUiTest.findPair(size: Size, count: (Int) -> Boolean, creates: Boolean): Triple<Arrangement, Pair<Int, Int>, Offset> =
        findPair(size) { actions, i, j ->
            val c = actions.pairActionCount(i, j)
            count(c) && c > 0 && actions.createsOf(i, j).isNotEmpty() == creates
        }

    /** An arrangement and pair where [ok] holds, with a clean point on its edge. */
    private fun ComposeUiTest.findPair(size: Size, ok: (CellActions, Int, Int) -> Boolean): Triple<Arrangement, Pair<Int, Int>, Offset> {
        for (arr in candidates) {
            val actions = CellActions(arr)
            for (i in 0 until arr.n) for (j in i + 1 until arr.n) {
                if (!ok(actions, i, j)) continue
                pairPoint(arr, actions, size, i, j)?.let { return Triple(arr, i to j, it) }
            }
        }
        error("no such pair")
    }

    @Test
    fun canvasMenuOfPairsWithSubmenusOfStarredOptions() = runComposeUiTest {
        // a cell with several pairs, one of them with several options (a circle with several sides on it)
        val (arr, f) = findFace(outer = false) { a, f -> a.pairsIn(f).size >= 2 && a.pairsIn(f).values.any { it.size >= 2 } }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        val pairs = s.actions.pairsIn(f).toSortedMap(compareBy({ it.first }, { it.second }))
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, f)) }
        waitForIdle()
        assertEquals(arr, s.arrangement)
        onNodeWithText("Create a digon").assertExists()
        for ((i, j) in pairs.keys) menuItem("${i + 1} ${j + 1}").assertExists()
        onNodeWithTag("action-submenu").assertDoesNotExist()
        // pointing at a pair with several options opens its submenu: the cell with two stars
        val (pair, ops) = pairs.entries.first { it.value.size >= 2 }
        hoverEntry(s, "${pair.first + 1} ${pair.second + 1}", Highlight(setOf(f)))
        onNodeWithTag("action-submenu").assertExists()
        for (l in menuLabels(ops.map { s.createNode(it).label })) {
            menuItem(l).assertExists()
            assertEquals(2, l.count { it == '*' }, l)
            assertTrue('[' !in l, l)
        }
        // pointing at an option highlights the cell and its two sides; a click creates the digon
        val (op, label) = s.uniqueOption(ops)
        hoverEntry(s, label, Highlight(setOf(f), setOf(op.dartI, op.dartJ)))
        menuItem(label).performClick()
        waitForIdle()
        assertNull(s.menuHighlight)
        assertEquals(arr.digonCreate(op), s.arrangement)
        undoGivesBack(s, arr)
        // a pair with one option is that option: a click on the pair creates it
        pairs.entries.firstOrNull { it.value.size == 1 }?.let { (p, single) ->
            onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, f)) }
            waitForIdle()
            menuItem("${p.first + 1} ${p.second + 1}").performClick()
            waitForIdle()
            assertEquals(arr.digonCreate(single.single()), s.arrangement)
            undoGivesBack(s, arr)
        }
    }

    @Test
    fun rightClickMenuListsTheCreatesOfTheFace() = runComposeUiTest {
        // a cell with a pair with one option and a pair with several (a submenu)
        val (arr, f) = findFace(outer = false) { a, f -> a.pairsIn(f).values.any { it.size == 1 } && a.pairsIn(f).values.any { it.size >= 2 } }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        val pairs = s.actions.pairsIn(f)
        fun rightClick() {
            onNodeWithTag("canvas").performMouseInput { rightClick(view.at(s.currentDrawing!!, f)) }
            waitForIdle()
        }
        rightClick()
        assertTrue(arr.faces[f].size >= 4)
        onNodeWithText("Face with ${arr.faces[f].size} sides").assertExists()
        menuItem("Make outer face").assertExists()
        for ((i, j) in pairs.keys) menuItem("Create digon ${i + 1} ${j + 1}").assertExists()
        // the entries top to bottom: creates by pair, then Make outer face
        val labels = pairs.keys.sortedWith(compareBy({ it.first }, { it.second })).map { (i, j) -> "Create digon ${i + 1} ${j + 1}" } + "Make outer face"
        assertEquals(labels, labels.sortedBy { top(it) })
        // a pair with one option: a click creates it
        val (single, one) = pairs.entries.first { it.value.size == 1 }
        menuItem("Create digon ${single.first + 1} ${single.second + 1}").performClick()
        waitForIdle()
        assertEquals(arr.digonCreate(one.single()), s.arrangement)
        undoGivesBack(s, arr)
        // a pair with several: a submenu with the starred options along the circles
        rightClick()
        val (pair, ops) = pairs.entries.first { it.value.size >= 2 }
        hoverEntry(s, "Create digon ${pair.first + 1} ${pair.second + 1}", Highlight(setOf(f)))
        onNodeWithTag("action-submenu").assertExists()
        val ordered = ops.sortedWith(compareBy({ arr.sidePosition(it.dartI) }, { arr.sidePosition(it.dartJ) }))
        val optionLabels = menuLabels(ordered.map { s.createNode(it).label })
        for (l in optionLabels) assertEquals(2, l.count { it == '*' }, l)
        assertEquals(optionLabels, optionLabels.sortedBy { top(it) })
        val (op, label) = s.uniqueOption(ordered)
        hoverEntry(s, label, Highlight(setOf(f), setOf(op.dartI, op.dartJ)))
        menuItem(label).performClick()
        waitForIdle()
        assertEquals(arr.digonCreate(op), s.arrangement)
        undoGivesBack(s, arr)
    }

    @Test
    fun escapeClosesTheMenu() = runComposeUiTest {
        val (arr, f) = withCreateFace { it >= 2 }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, f)) }
        waitForIdle()
        onNodeWithTag("action-menu").assertExists()
        onNodeWithTag("action-menu").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("action-menu").assertDoesNotExist()
        assertEquals(arr, s.arrangement)
    }

    @Test
    fun aClickOutsideTheMenuOnlyClosesIt() = runComposeUiTest {
        // a bounded cell with a menu, and an outer face that would act on a click
        val (arr, f) = findFace(outer = false) { a, f ->
            a.createsIn(f).size >= 2 && a.arrangement.let { arr -> a.hasAction(arr.defaultOuterFace(DrawModel.MODEL2)) }
        }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, f)) }
        waitForIdle()
        onNodeWithTag("action-menu").assertExists()
        onNodeWithTag("canvas").performMouseInput { click(Offset(6f, 6f)) }
        waitForIdle()
        onNodeWithTag("action-menu").assertDoesNotExist()
        assertEquals(arr, s.arrangement)
    }

    @Test
    fun outerTriangleFlipsWithTheMarkInACorner() = runComposeUiTest {
        // the Krupp: every face is a triangle, the outer one too
        val krupp = Arrangement.krupp()
        val s = state()
        val view = ViewTransform()
        show(s, view, krupp, DrawModel.MODEL1)
        val d = s.currentDrawing!!
        val outer = s.outerFace!!
        val size = canvasSize()
        val corner = assertNotNull(outerMarkCorner(d, view, size.width, size.height))
        // one of the four corners, CORNER_INSET from the edges
        assertTrue(corner.x == CORNER_INSET || corner.x == size.width - CORNER_INSET)
        assertTrue(corner.y == CORNER_INSET || corner.y == size.height - CORNER_INSET)
        onNodeWithTag("canvas").performMouseInput { moveTo(Offset(5f, 5f)); moveTo(Offset(6f, 6f)) }
        waitForIdle()
        assertEquals(outer, s.hoverFace)
        val map = onNodeWithTag("canvas").captureToImage().toPixelMap()
        assertTrue(close(map[(corner.x + 6f).toInt(), corner.y.toInt()], MARK_FILL))
        onNodeWithTag("canvas").performMouseInput { click(Offset(6f, 6f)) }
        waitForIdle()
        assertEquals(krupp.triangleFlip(outer), s.arrangement)
        undoGivesBack(s, krupp)
    }

    @Test
    fun outerFaceCreatesOnClick() = runComposeUiTest {
        // a face with one create, drawn as the outer face
        val (arr, outer) = findFace(outer = false) { a, f -> a.createsIn(f).size == 1 }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        runOnIdle { s.makeOuterFace(outer) }
        waitUntil(timeoutMillis = 10_000) { s.outerFace == outer }
        val op1 = s.actions.createsIn(outer).single()
        onNodeWithTag("canvas").performMouseInput { moveTo(Offset(5f, 5f)); moveTo(Offset(6f, 6f)) }
        waitForIdle()
        assertTrue(markPixels() > 0)
        onNodeWithTag("canvas").performMouseInput { click(Offset(6f, 6f)) }
        waitForIdle()
        assertEquals(arr.digonCreate(op1), s.arrangement)
        undoGivesBack(s, arr)
        // several: the menu, entries marked "(outer face)"
        val (arr2, outer2) = findFace(outer = true) { a, f -> a.pairsIn(f).size >= 2 && a.pairsIn(f).values.any { it.size >= 2 } }
        runOnIdle { s.load("test2", arr2) }
        awaitFitted(s, view)
        assertEquals(outer2, s.outerFace)
        onNodeWithTag("canvas").performMouseInput { click(Offset(6f, 6f)) }
        waitForIdle()
        onNodeWithTag("action-menu").assertExists()
        // open the submenu of a pair with several options, if the menu has pairs
        val pairs = s.actions.pairsIn(outer2)
        val ops = pairs.values.first { it.size >= 2 }
        hoverEntry(s, circlesLabel(listOf(ops[0].i, ops[0].j)), Highlight(setOf(outer2)))
        onNodeWithTag("action-submenu").assertExists()
        val (op, label) = s.uniqueOption(ops)
        assertTrue(label.endsWith("(outer face)"), label)
        hoverEntry(s, label, Highlight(setOf(outer2), setOf(op.dartI, op.dartJ)))
        menuItem(label).performClick()
        waitForIdle()
        assertEquals(arr2.digonCreate(op), s.arrangement)
    }

    @Test
    fun exclamationMarkOnHoverOnlyWhereAClickActs() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        val start = Examples.start.arrangement()
        show(s, view, start, DrawModel.MODEL1)
        val d = s.currentDrawing!!
        fun markAt(f: Int): Boolean {
            // the first event of a mouse coming in is an Enter, the hover follows the moves
            onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d, f) - Offset(1f, 0f)); moveTo(view.at(d, f)) }
            waitForIdle()
            assertEquals(f, s.hoverFace)
            val p = view.toScreen(d.interiorPoint(f)!!) + Offset(6f, 0f)
            val pixel = onNodeWithTag("canvas").captureToImage().toPixelMap()[p.x.toInt(), p.y.toInt()]
            return close(pixel, MARK_FILL)
        }
        // a triangle flips: "!"; a bigger face of a digon-free intersecting arrangement does nothing: none
        assertTrue(markAt(s.bounded(3)))
        val big = start.faces.indices.first { start.faces[it].size >= 4 && it != s.outerFace }
        assertTrue(!markAt(big))
        // the outer face (no triangle, nothing to do): no "!" anywhere
        assertTrue(!s.actions.hasAction(s.outerFace!!))
        onNodeWithTag("canvas").performMouseInput { moveTo(Offset(4f, 4f)); moveTo(Offset(5f, 5f)) }
        waitForIdle()
        assertEquals(s.outerFace, s.hoverFace)
        assertEquals(0, markPixels())
        // a face with a digon create
        val (arr, f) = withCreateFace { it == 1 }
        runOnIdle { s.load("test", arr); s.changeModel(DrawModel.MODEL2) }
        awaitFitted(s, view)
        val d2 = s.currentDrawing!!
        onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d2, f) - Offset(1f, 0f)); moveTo(view.at(d2, f)) }
        waitForIdle()
        val p = view.toScreen(d2.interiorPoint(f)!!) + Offset(6f, 0f)
        assertTrue(close(onNodeWithTag("canvas").captureToImage().toPixelMap()[p.x.toInt(), p.y.toInt()], MARK_FILL))
        // two circles: a digon whose collapse is refused gets no "!"
        runOnIdle { s.load("two", Arrangement.two()) }
        awaitFitted(s, view)
        val d3 = s.currentDrawing!!
        val digon = s.bounded(2)
        onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d3, digon) - Offset(1f, 0f)); moveTo(view.at(d3, digon)) }
        waitForIdle()
        assertEquals(digon, s.hoverFace)
        assertEquals(0, markPixels())
    }

    @Test
    fun graphMenuOfCellsWithSubmenus() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, pool.first(), widget = Widget.INTERSECTION_GRAPH)
        val size = graphSize("intersection-graph")
        // a pair with several cells, one of them with several options, whose cells along circle i
        // are not in face order (so the menu order really comes from the ordering)
        val (arr, pair, at) = findPair(size) { a, i, j ->
            val byFace = a.createsOf(i, j).groupBy { it.face }
            byFace.size >= 2 && byFace.values.any { it.size >= 2 } && byFace.keys.toList() != byFace.keys.sorted()
        }
        runOnIdle { s.load("test", arr) }
        awaitFitted(s, view)
        val (i, j) = pair
        onNodeWithTag("intersection-graph").performMouseInput { click(at) }
        waitForIdle()
        assertEquals(arr, s.arrangement)
        onNodeWithText("Create a digon of ${i + 1} ${j + 1}").assertExists()
        val byFace = s.actions.createsOf(i, j).groupBy { it.face }
        val (f, ops) = byFace.entries.first { it.value.size >= 2 }
        // the cell entry: its description without stars; pointing at it opens its options
        val cellLabels = menuLabels(byFace.map { (g, o) -> o.singleOrNull()?.let { s.createNode(it).label } ?: s.cellLabel(g) })
        val cellLabel = cellLabels[byFace.keys.indexOf(f)]
        assertTrue('*' !in cellLabel, cellLabel)
        // the cells top to bottom along circle i: by the first side of i (in its list order) where a create starts
        val pos = { op: DigonCreate -> arr.sidePosition(op.dartI) to arr.sidePosition(op.dartJ) }
        val raw = arr.digonCreates().filter { it.i == i && it.j == j }
        val alongI = raw.sortedWith(compareBy({ arr.sidePosition(it.dartI) }, { arr.sidePosition(it.dartJ) })).map { it.face }.distinct()
        assertEquals(alongI, byFace.keys.toList())
        assertEquals(cellLabels, cellLabels.sortedBy { top(it) }, "cells in the menu along circle ${i + 1}")
        hoverEntry(s, cellLabel, Highlight(setOf(f)))
        onNodeWithTag("action-submenu").assertExists()
        // the options of the cell top to bottom along circle i, then j
        assertEquals(ops.sortedWith(compareBy({ pos(it).first }, { pos(it).second })), ops)
        val optionLabels = menuLabels(ops.map { s.createNode(it).label })
        assertEquals(optionLabels, optionLabels.sortedBy { top(it) }, "options in the submenu along the circles")
        val (op, label) = s.uniqueOption(ops)
        hoverEntry(s, label, Highlight(setOf(f), setOf(op.dartI, op.dartJ)))
        menuItem(label).performClick()
        waitForIdle()
        assertEquals(arr.digonCreate(op), s.arrangement)
        undoGivesBack(s, arr)
    }

    @Test
    fun graphMenuOfOneCellShowsItsOptions() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, pool.first(), widget = Widget.INTERSECTION_GRAPH)
        val size = graphSize("intersection-graph")
        val (arr, pair, at) = findPair(size) { a, i, j -> a.createsOf(i, j).size >= 2 && a.createsOf(i, j).map { it.face }.toSet().size == 1 }
        runOnIdle { s.load("test", arr) }
        awaitFitted(s, view)
        val (i, j) = pair
        onNodeWithTag("intersection-graph").performMouseInput { click(at) }
        waitForIdle()
        onNodeWithTag("action-submenu").assertDoesNotExist()
        val (op, label) = s.uniqueOption(s.actions.createsOf(i, j))
        assertEquals(2, label.count { it == '*' })
        menuItem(label).performClick()
        waitForIdle()
        assertEquals(arr.digonCreate(op), s.arrangement)
        undoGivesBack(s, arr)
    }

    @Test
    fun hypergraphMenuOfTriplesWithSubmenusOfCells() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        val start = Examples.start.arrangement()
        show(s, view, start, widget = Widget.TRIANGLE_HYPERGRAPH)
        val size = graphSize("triangle-hypergraph")
        val at = assertNotNull(hyperPoint(start, size) { hits, _ -> hits.size >= 2 })
        assertEquals("${start.triangleCount} triangles on ${s.actions.triples.size} triples", onNodeWithTag("triangle-hypergraph-count").text())
        onNodeWithTag("triangle-hypergraph").performMouseInput { click(at) }
        waitForIdle()
        assertEquals(start, s.arrangement)
        // several triples: the plain title, the triples ascending as group entries
        onNodeWithText("Flip a triangle").assertExists()
        val circle = with(density) { vertexCircle(start.n, size) }
        val hits = s.actions.triples.filterKeys { inTriangle(at, circle.at(it[0]), circle.at(it[1]), circle.at(it[2])) }
        for (t in hits.keys) {
            assertEquals(t.sorted(), t)
            menuItem(circlesLabel(t)).assertExists()
        }
        // the start example: every triple has two triangles, so each triple opens a submenu
        val (t, faces) = hits.entries.first()
        assertEquals(2, faces.size)
        hoverEntry(s, circlesLabel(t), Highlight(faces.toSet()))
        onNodeWithTag("action-submenu").assertExists()
        val labels = menuLabels(faces.map { s.cellLabel(it) })
        hoverEntry(s, labels[0], Highlight(setOf(faces[0])))
        menuItem(labels[0]).performClick()
        waitForIdle()
        assertEquals(start.triangleFlip(faces[0]), s.arrangement)
        undoGivesBack(s, start)
    }

    @Test
    fun hypergraphClickOnATripleWithSeveralTrianglesListsItsCells() = runComposeUiTest {
        // the Krupp: one triple with 8 triangles, all described "1 2 3" or "1 3 2": numbered entries
        val krupp = Arrangement.krupp()
        val s = state()
        val view = ViewTransform()
        show(s, view, krupp, widget = Widget.TRIANGLE_HYPERGRAPH)
        val size = graphSize("triangle-hypergraph")
        val circle = with(density) { vertexCircle(3, size) }
        onNodeWithTag("triangle-hypergraph").performMouseInput { click((circle.at(0) + circle.at(1) + circle.at(2)) / 3f) }
        waitForIdle()
        assertEquals(krupp, s.arrangement)
        // one triple: the title names it (ascending), the entries are the oriented faces, numbered
        assertEquals("possible triangle flips (1): 1 2 3 (x8)", onNodeWithTag("possible-flips").text())
        onNodeWithText("Flip a triangle of 1 2 3").assertExists()
        onNodeWithTag("action-submenu").assertDoesNotExist()
        val faces = s.actions.triples.getValue(listOf(0, 1, 2))
        val labels = menuLabels(faces.map { s.cellLabel(it) })
        assertEquals(8, labels.size)
        assertTrue("1 2 3 (1)" in labels || "1 3 2 (1)" in labels, "$labels")
        assertTrue(labels.all { Regex("""1 (2 3|3 2) (\(\d\)|\(outer face\))""").matches(it) }, "$labels")
        for (l in labels) menuItem(l).assertExists()
        menuItem(labels[2]).performClick()
        waitForIdle()
        assertEquals(krupp.triangleFlip(faces[2]), s.arrangement)
        undoGivesBack(s, krupp)
    }

    @Test
    fun listsOfPossibleActionsBelowTheGraphs() = runComposeUiTest {
        // circle 1 crosses four small circles 2..5, which do not cross each other: every pair of
        // them can create a digon inside or outside circle 1; no collapse (it would cut a circle off)
        val star = Arrangement.parse("{1: [2, -2, 3, -3, 4, -4, 5, -5], 2: [-1, 1], 3: [-1, 1], 4: [-1, 1], 5: [-1, 1]}")
        val s = state()
        val view = ViewTransform()
        show(s, view, star, widget = Widget.INTERSECTION_GRAPH)
        runOnIdle { s.toggleWidget(Widget.TRIANGLE_HYPERGRAPH) }
        waitForIdle()
        assertEquals("possible digon creates (6): 2 3 (x2), 2 4 (x2), 2 5 (x2), 3 4 (x2), 3 5 (x2), 4 5 (x2)", onNodeWithTag("possible-creates").text())
        assertEquals("possible digon collapses (0): none", onNodeWithTag("possible-collapses").text())
        assertEquals("possible triangle flips (0): none", onNodeWithTag("possible-flips").text())
        // a create of 2 and 3 by a click on its stripe: the lists follow
        val size = graphSize("intersection-graph")
        val at = assertNotNull(pairPoint(star, s.actions, size, 1, 2))
        onNodeWithTag("intersection-graph").performMouseInput { click(at) }
        waitForIdle()
        menuLabels(s.pairEntries(1, 2).map { it.label }).first().let { menuItem(it).performClick() }
        waitForIdle()
        assertNotEquals(star, s.arrangement)
        val a = s.actions
        assertEquals(possibleList("digon creates", a.createPairs), onNodeWithTag("possible-creates").text())
        assertEquals(possibleList("digon collapses", a.collapsePairs), onNodeWithTag("possible-collapses").text())
        assertEquals(possibleList("triangle flips", a.triangleTriples), onNodeWithTag("possible-flips").text())
        // the created pair crosses now: 2 3 is no create any more but a collapse; and 1 2, 1 3 can
        // collapse too, since 2 and 3 stay connected through each other
        assertTrue("2 3" !in onNodeWithTag("possible-creates").text()!!.substringAfter(": "))
        assertEquals("possible digon collapses (3): 1 2, 1 3, 2 3", onNodeWithTag("possible-collapses").text())
        // the start example: no creates or collapses, triangles on 4 triples with 2 each
        runOnIdle { s.load("start", Examples.start.arrangement()) }
        awaitFitted(s, view)
        assertEquals("possible digon creates (0): none", onNodeWithTag("possible-creates").text())
        val flips = onNodeWithTag("possible-flips").text()!!
        assertTrue(Regex("""possible triangle flips \(4\): \d \d \d \(x2\)(, \d \d \d \(x2\)){3}""").matches(flips), flips)
    }

    /** The list entry of [c] (0-based circles) in the list [tag]. */
    private fun ComposeUiTest.entry(tag: String, c: List<Int>) = onNodeWithTag("$tag-${c.joinToString("-") { "${it + 1}" }}")

    @Test
    fun listEntriesActLikeTheGraph() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        // a unique create and a collapse: an arrangement with a create pair of one option and a collapsible digon
        val arr = candidates.first { a -> CellActions(a).let { x -> x.createPairs.any { it.second == 1 } && x.collapsePairs.isNotEmpty() } }
        show(s, view, arr, widget = Widget.INTERSECTION_GRAPH)
        val a = s.actions
        val (single, _) = a.createPairs.first { it.second == 1 }
        entry("possible-creates", single).assertExists()
        // pointing at an entry highlights the pair in the graph and its cell on the canvas
        entry("possible-creates", single).performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(single, s.graphHighlight)
        assertEquals(Highlight(setOf(a.createsOf(single[0], single[1]).single().face)), s.menuHighlight)
        entry("possible-creates", single).performMouseInput { click(center) }
        waitForIdle()
        assertEquals(arr.digonCreate(a.createsOf(single[0], single[1]).single()), s.arrangement)
        // Ctrl+Z right after the click (the entry does not take the focus)
        undoGivesBack(s, arr)
        val (collapse, _) = s.actions.collapsePairs.first()
        val digon = s.actions.collapsesOf(collapse[0], collapse[1]).single()
        entry("possible-collapses", collapse).performMouseInput { click(center) }
        waitForIdle()
        assertEquals(arr.digonCollapse(digon), s.arrangement)
        undoGivesBack(s, arr)
    }

    @Test
    fun anAmbiguousListEntryOpensTheMenu() = runComposeUiTest {
        // the star: every create pair has two cells (inside and outside circle 1)
        val star = Arrangement.parse("{1: [2, -2, 3, -3, 4, -4, 5, -5], 2: [-1, 1], 3: [-1, 1], 4: [-1, 1], 5: [-1, 1]}")
        val s = state()
        val view = ViewTransform()
        show(s, view, star, widget = Widget.INTERSECTION_GRAPH)
        onNodeWithTag("possible-creates-2-3").assertExists()
        assertEquals("2 3 (x2)", onNodeWithTag("possible-creates-2-3").text())
        onNodeWithTag("possible-creates-2-3").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(star, s.arrangement)
        onNodeWithText("Create a digon of 2 3").assertExists()
        val nodes = s.pairEntries(1, 2)
        val labels = menuLabels(nodes.map { it.label })
        hoverEntry(s, labels[1], nodes[1].highlight)
        menuItem(labels[1]).performClick()
        waitForIdle()
        assertEquals(star.digonCreate(s.let { CellActions(star).createsOf(1, 2)[1] }), s.arrangement)
        undoGivesBack(s, star)
        // after a change (and undo) the entries still open their menus, and Exit clears the highlight
        onNodeWithTag("possible-creates-2-3").performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(listOf(1, 2), s.graphHighlight)
        onNodeWithTag("possible-creates-2-3").performMouseInput { moveTo(Offset(-50f, -50f)) }
        waitForIdle()
        assertNull(s.graphHighlight)
        assertNull(s.menuHighlight)
        onNodeWithTag("possible-creates-2-3").performMouseInput { click(center) }
        waitForIdle()
        onNodeWithText("Create a digon of 2 3").assertExists()
    }

    @Test
    fun tripleListEntriesFlipOrOpenTheMenu() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        // a triple with one triangle flips directly
        val arr = candidates.first { a -> CellActions(a).triangleTriples.any { it.second == 1 } }
        show(s, view, arr, widget = Widget.TRIANGLE_HYPERGRAPH)
        val (t, _) = s.actions.triangleTriples.first { it.second == 1 }
        entry("possible-flips", t).performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(t, s.graphHighlight)
        entry("possible-flips", t).performMouseInput { click(center) }
        waitForIdle()
        assertEquals(arr.triangleFlip(CellActions(arr).triples.getValue(t).single()), s.arrangement)
        undoGivesBack(s, arr)
        // the start example: every triple has two triangles, the entry opens the menu of its cells
        val start = Examples.start.arrangement()
        runOnIdle { s.load("start", start) }
        awaitFitted(s, view)
        val (t2, m) = s.actions.triangleTriples.first()
        assertEquals(2, m)
        assertEquals("${circlesLabel(t2)} (x2)", entry("possible-flips", t2).text())
        entry("possible-flips", t2).performMouseInput { click(center) }
        waitForIdle()
        assertEquals(t2.sorted(), t2)
        onNodeWithText("Flip a triangle of ${circlesLabel(t2)}").assertExists()
        val faces = s.actions.triples.getValue(t2)
        val labels = menuLabels(faces.map { s.cellLabel(it) })
        menuItem(labels[1]).performClick()
        waitForIdle()
        assertEquals(start.triangleFlip(faces[1]), s.arrangement)
        undoGivesBack(s, start)
    }

    @Test
    fun graphClickOnAUniquePairFlipsIt() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, pool.first(), widget = Widget.INTERSECTION_GRAPH)
        val size = graphSize("intersection-graph")
        for (creates in listOf(true, false)) {
            val (arr, pair, at) = findPair(size, { it == 1 }, creates)
            runOnIdle { s.load("test", arr) }
            awaitFitted(s, view)
            onNodeWithTag("intersection-graph").performMouseInput { click(at) }
            waitForIdle()
            val (i, j) = pair
            val expected = if (creates) arr.digonCreate(CellActions(arr).createsOf(i, j).single())
            else arr.digonCollapse(CellActions(arr).collapsesOf(i, j).single())
            assertEquals(expected, s.arrangement, "pair ${i + 1} ${j + 1}, creates = $creates")
            undoGivesBack(s, arr)
        }
    }

    @Test
    fun stripesAreTwiceAsStrongForAmbiguousPairs() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, pool.first(), widget = Widget.INTERSECTION_GRAPH)
        val size = graphSize("intersection-graph")
        // an arrangement with a unique and an ambiguous pair, both with a clean point on the edge
        val (arr, points) = pool.firstNotNullOf { a ->
            val actions = CellActions(a)
            val pairs = (0 until a.n).flatMap { i -> (i + 1 until a.n).map { j -> i to j } }
            val single = pairs.filter { (i, j) -> actions.pairActionCount(i, j) == 1 }.firstNotNullOfOrNull { (i, j) -> pairPoint(a, actions, size, i, j)?.let { it to (i to j) } }
            val double = pairs.filter { (i, j) -> actions.pairActionCount(i, j) >= 2 }.firstNotNullOfOrNull { (i, j) -> pairPoint(a, actions, size, i, j)?.let { it to (i to j) } }
            if (single != null && double != null) a to listOf(single, double) else null
        }
        runOnIdle { s.load("test", arr) }
        awaitFitted(s, view)
        val circle = with(density) { vertexCircle(arr.n, size) }
        val map = onNodeWithTag("intersection-graph").captureToImage().toPixelMap()
        // 3 px beside the edge, inside the 10 dp stripe
        val (single, double) = points.map { (p, pair) ->
            val dir = circle.at(pair.second) - circle.at(pair.first)
            val normal = Offset(-dir.y, dir.x) / dir.getDistance()
            val q = p + normal * 3f
            map[q.x.toInt(), q.y.toInt()]
        }
        // yellow: blue lower than red and green, stronger (lower blue) when drawn twice
        assertTrue(single.blue < 0.8f && single.red > 0.9f, "$single")
        assertTrue(double.blue < single.blue - 0.1f, "$double vs $single")
    }

    @Test
    fun graphStripesAndClicksNeedAnAction() = runComposeUiTest {
        // two circles: both collapses are refused, so nothing is striped and a click does nothing
        val s = state()
        val view = ViewTransform()
        show(s, view, Arrangement.two(), widget = Widget.INTERSECTION_GRAPH)
        val size = graphSize("intersection-graph")
        val circle = with(density) { vertexCircle(2, size) }
        onNodeWithTag("intersection-graph").performMouseInput { click((circle.at(0) + circle.at(1)) / 2f) }
        waitForIdle()
        assertEquals(Arrangement.two(), s.arrangement)
        assertEquals("1 of 1 pairs cross", onNodeWithTag("intersection-graph-count").text())
    }

    // ---- triangle hypergraph -------------------------------------------------------------

    /** A point of the hypergraph canvas covered by triples that satisfy [ok] (hits, cells per hit). */
    private fun ComposeUiTest.hyperPoint(arr: Arrangement, size: Size, ok: (List<List<Int>>, Map<List<Int>, List<Int>>) -> Boolean): Offset? {
        val circle = with(density) { vertexCircle(arr.n, size) }
        val triples = CellActions(arr).triples
        for (gx in 1 until 40) for (gy in 1 until 40) {
            val p = Offset(size.width * gx / 40, size.height * gy / 40)
            val hits = triples.keys.filter { t -> inTriangle(p, circle.at(t[0]), circle.at(t[1]), circle.at(t[2])) }
            // away from the triangle sides, so the click is not on a border
            val clear = triples.keys.all { t ->
                (0 until 3).all { k -> segmentDistance(p, circle.at(t[k]), circle.at(t[(k + 1) % 3])) > 3f }
            }
            if (clear && hits.isNotEmpty() && ok(hits, triples)) return p
        }
        return null
    }

    @Test
    fun hypergraphClickOnASingleTriangleFlipsIt() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, Examples.start.arrangement(), widget = Widget.TRIANGLE_HYPERGRAPH)
        val size = graphSize("triangle-hypergraph")
        val (arr, at) = (listOf(Examples.start.arrangement()) + pool).firstNotNullOf { a ->
            hyperPoint(a, size) { hits, triples -> hits.size == 1 && triples.getValue(hits[0]).size == 1 }?.let { a to it }
        }
        runOnIdle { s.load("test", arr) }
        awaitFitted(s, view)
        val circle = with(density) { vertexCircle(arr.n, size) }
        val t = s.actions.triples.keys.single { inTriangle(at, circle.at(it[0]), circle.at(it[1]), circle.at(it[2])) }
        onNodeWithTag("triangle-hypergraph").performMouseInput { click(at) }
        waitForIdle()
        assertEquals(arr.triangleFlip(CellActions(arr).triples.getValue(t).single()), s.arrangement)
        undoGivesBack(s, arr)
    }

    @Test
    fun tripleTriangleCountsAndTransparentFills() {
        // the Krupp: every triple of its 3 circles bounds 8 triangles; the NonKrupp has 2 triangles
        assertEquals(mapOf(listOf(0, 1, 2) to 8), CellActions(Arrangement.krupp()).triples.mapValues { it.value.size })
        // the fill is transparent, so overlaps add up
        assertTrue(HYPEREDGE_FILL.alpha < 0.5f && STRIPE_COLOR.alpha < 0.5f)
    }
}
