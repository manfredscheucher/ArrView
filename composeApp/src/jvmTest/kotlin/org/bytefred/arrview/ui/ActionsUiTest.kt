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
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.CellActions
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.defaultOuterFace
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.digonCreate
import org.bytefred.arrview.core.triangleFlip
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private fun withCreateFace(creates: (Int) -> Boolean): Pair<Arrangement, Int> {
        for (arr in pool) {
            val outer = arr.defaultOuterFace(DrawModel.MODEL2)
            val actions = CellActions(arr)
            val f = arr.faces.indices.firstOrNull { it != outer && creates(actions.createsIn(it).size) } ?: continue
            return arr to f
        }
        error("no such arrangement")
    }

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

    @Test
    fun canvasClickOnAFaceWithSeveralCreatesOpensAMenu() = runComposeUiTest {
        val (arr, f) = withCreateFace { it >= 2 }
        val s = state()
        val view = ViewTransform()
        show(s, view, arr)
        val options = s.actions.createsIn(f)
        onNodeWithTag("canvas").performMouseInput { click(view.at(s.currentDrawing!!, f)) }
        waitForIdle()
        assertEquals(arr, s.arrangement)
        onNodeWithText("Create a digon").assertExists()
        val labels = menuLabels(s.createEntries(f))
        assertEquals(options.size, labels.size)
        for (l in labels) menuItem(l).assertExists()
        val label = labels.last()
        assertTrue(Regex("""\d \d: .*\[\d+].*\[\d+].*""").matches(label), label)
        // pointing at an entry highlights its cell in the canvas
        menuItem(label).performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(f, s.menuHighlight)
        menuItem(label).performClick()
        waitForIdle()
        assertNull(s.menuHighlight)
        assertEquals(arr.digonCreate(options.sortedWith(compareBy({ it.i }, { it.j })).last()), s.arrangement)
        undoGivesBack(s, arr)
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
        // a face with a digon create
        val (arr, f) = withCreateFace { it == 1 }
        runOnIdle { s.load("test", arr); s.changeModel(DrawModel.MODEL2) }
        awaitFitted(s, view)
        val d2 = s.currentDrawing!!
        onNodeWithTag("canvas").performMouseInput { moveTo(view.at(d2, f) - Offset(1f, 0f)); moveTo(view.at(d2, f)) }
        waitForIdle()
        val p = view.toScreen(d2.interiorPoint(f)!!) + Offset(6f, 0f)
        assertTrue(close(onNodeWithTag("canvas").captureToImage().toPixelMap()[p.x.toInt(), p.y.toInt()], MARK_FILL))
        // the outer face: no "!" anywhere, and a click there does nothing although it has creates
        assertTrue(s.actions.createsIn(s.outerFace!!).isNotEmpty())
        onNodeWithTag("canvas").performMouseInput { moveTo(Offset(4f, 4f)); moveTo(Offset(5f, 5f)) }
        waitForIdle()
        assertEquals(s.outerFace, s.hoverFace)
        assertEquals(0, markPixels())
        onNodeWithTag("canvas").performMouseInput { click(Offset(5f, 5f)) }
        waitForIdle()
        assertEquals(arr, s.arrangement)
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
    private fun ComposeUiTest.findPair(size: Size, count: (Int) -> Boolean, creates: Boolean): Triple<Arrangement, Pair<Int, Int>, Offset> {
        for (arr in pool) {
            val actions = CellActions(arr)
            for (i in 0 until arr.n) for (j in i + 1 until arr.n) {
                val c = actions.pairActionCount(i, j)
                if (!count(c) || c == 0 || (actions.createsOf(i, j).isNotEmpty() != creates)) continue
                pairPoint(arr, actions, size, i, j)?.let { return Triple(arr, i to j, it) }
            }
        }
        error("no such pair")
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
    fun graphClickOnAnAmbiguousPairOpensAMenu() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, pool.first(), widget = Widget.INTERSECTION_GRAPH)
        val size = graphSize("intersection-graph")
        val (arr, pair, at) = findPair(size, { it >= 2 }, creates = true)
        runOnIdle { s.load("test", arr) }
        awaitFitted(s, view)
        val (i, j) = pair
        onNodeWithTag("intersection-graph").performMouseInput { click(at) }
        waitForIdle()
        assertEquals(arr, s.arrangement)
        onNodeWithText("Create a digon of ${i + 1} ${j + 1}").assertExists()
        assertEquals("Create a digon of ${i + 1} ${j + 1}", s.pairHeading(i, j))
        val op = s.actions.createsOf(i, j).first()
        val label = menuLabels(s.pairEntries(i, j)).first()
        menuItem(label).performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(op.face, s.menuHighlight)
        menuItem(label).performClick()
        waitForIdle()
        assertEquals(arr.digonCreate(op), s.arrangement)
        undoGivesBack(s, arr)
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
    fun hypergraphClickOnSeveralTrianglesOpensAMenu() = runComposeUiTest {
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
        onNodeWithText("Flip a triangle").assertExists()
        val circle = with(density) { vertexCircle(start.n, size) }
        val hits = s.actions.triples.filterKeys { inTriangle(at, circle.at(it[0]), circle.at(it[1]), circle.at(it[2])) }
        val entries = hits.flatMap { (t, faces) -> s.triangleEntries(t, faces) }
        val first = entries.first()
        val label = menuLabels(entries).first()
        menuItem(label).performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(first.face, s.menuHighlight)
        menuItem(label).performClick()
        waitForIdle()
        assertEquals(start.triangleFlip(first.face), s.arrangement)
        undoGivesBack(s, start)
    }

    @Test
    fun hypergraphClickOnATripleWithSeveralTrianglesOpensAMenu() = runComposeUiTest {
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
        onNodeWithText("Flip a triangle").assertExists()
        val entries = s.triangleEntries(listOf(0, 1, 2), s.actions.triples.getValue(listOf(0, 1, 2)))
        val labels = menuLabels(entries)
        assertEquals(8, labels.size)
        assertTrue(labels.all { Regex("""1 2 3: 1 (2 3|3 2) (\(\d\)|\(outer face\))""").matches(it) }, "$labels")
        for (l in labels) menuItem(l).assertExists()
        assertTrue(labels.any { it.endsWith("(1)") })
        menuItem(labels[2]).performClick()
        waitForIdle()
        assertEquals(krupp.triangleFlip(entries[2].face), s.arrangement)
        undoGivesBack(s, krupp)
    }

    @Test
    fun tripleTriangleCountsAndTransparentFills() {
        // the Krupp: every triple of its 3 circles bounds 8 triangles; the NonKrupp has 2 triangles
        assertEquals(mapOf(listOf(0, 1, 2) to 8), CellActions(Arrangement.krupp()).triples.mapValues { it.value.size })
        // the fill is transparent, so overlaps add up
        assertTrue(HYPEREDGE_FILL.alpha < 0.5f && STRIPE_COLOR.alpha < 0.5f)
    }
}
