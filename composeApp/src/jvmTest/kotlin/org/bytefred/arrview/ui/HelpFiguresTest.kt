package org.bytefred.arrview.ui

import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The Help figures show the whole dual and primal-dual graph: the outer cell's vertex and its edges, without crossings. */
class HelpFiguresTest {
    private val d = figureDrawing
    private val outer = outerRoutes(d)

    private fun segments(poly: List<Point>) = poly.zipWithNext()

    /** Proper crossing of the segments [a] and [b] (touching ends do not count). */
    private fun cross(a: Pair<Point, Point>, b: Pair<Point, Point>): Boolean {
        fun orient(p: Point, q: Point, r: Point) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
        val d1 = orient(b.first, b.second, a.first)
        val d2 = orient(b.first, b.second, a.second)
        val d3 = orient(a.first, a.second, b.first)
        val d4 = orient(a.first, a.second, b.second)
        return d1 * d2 < 0 && d3 * d4 < 0
    }

    @Test
    fun everyArcOfTheOuterCellHasAnEdge() {
        val arr = d.arrangement
        val outerArcs = (0 until arr.size).filter { k -> d.outerFace in listOf(arr.faceOfDart[2 * k], arr.faceOfDart[arr.alpha(2 * k)]) }
        assertEquals(outerArcs.toSet(), outer.edges.keys)
        for (route in outer.edges.values) assertEquals(outer.vertex, route.last())
    }

    @Test
    fun theOuterEdgesCrossNeitherEachOtherNorTheCurves() {
        val curves = d.circles.flatMap { segments(Drawing.flatten(it) + it.last().p3) }
        val routes = outer.edges.entries.toList()
        for ((k, route) in routes) {
            // the first piece starts on the curve, at the arc's middle: checked from just after it
            val (m, q) = route[0] to route[1]
            val pieces = listOf(Point(m.x + (q.x - m.x) * 0.01, m.y + (q.y - m.y) * 0.01) to q) + segments(route).drop(1)
            for (s in pieces) assertFalse(curves.any { cross(s, it) }, "edge of arc $k crosses a curve")
            for ((j, other) in routes) if (j != k) {
                assertFalse(segments(route).any { s -> segments(other).any { cross(s, it) } }, "edges of arcs $k and $j cross")
            }
        }
    }
}
