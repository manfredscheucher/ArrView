package org.bytefred.arrview.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class LayoutTest {
    private val start = Examples.start.arrangement()

    private fun ArrangementLayout.positions(settings: LayoutSettings = LayoutSettings()) = IteratedTutte(problem, settings).run()

    private fun assertFinite(d: Drawing, what: String) {
        for (c in d.circles.flatten()) for (p in listOf(c.p0, c.c1, c.c2, c.p3)) {
            assertTrue(p.x.isFinite() && p.y.isFinite(), "$what: $p")
        }
        assertEquals(d.arrangement.n, d.circles.size, what)
    }

    @Test
    fun outerFaceIsAConvexRegularPolygon() {
        for (model in DrawModel.entries) {
            val layout = ArrangementLayout(start, model, start.defaultOuterFace(model))
            val pos = layout.positions()
            val outer = layout.problem.outer
            val pts = outer.map { Point(pos[2 * it], pos[2 * it + 1]) }
            for (p in pts) assertEquals(1.0, p.length, 1e-12, "on the unit circle")
            // convex: consecutive turns all in the same direction
            val turns = pts.indices.map { i ->
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val c = pts[(i + 2) % pts.size]
                (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            }
            assertTrue(turns.all { it > 0 } || turns.all { it < 0 }, "$model: $turns")
            // regular: equal angles between consecutive corners
            val angles = pts.map { atan2(it.y, it.x) }
            for (i in angles.indices) {
                val step = (angles[(i + 1) % angles.size] - angles[i] + 4 * PI) % (2 * PI)
                assertEquals(2 * PI / pts.size, step, 1e-9)
            }
        }
    }

    @Test
    fun interiorVerticesAreInsideTheOuterPolygon() {
        val layout = ArrangementLayout(start, DrawModel.MODEL1, start.defaultOuterFace(DrawModel.MODEL1))
        val pos = layout.positions()
        val outer = layout.problem.outer.map { Point(pos[2 * it], pos[2 * it + 1]) }
        for (v in 0 until layout.problem.vertexCount) {
            if (v in layout.problem.outer) continue
            assertTrue(Drawing.contains(outer, Point(pos[2 * v], pos[2 * v + 1])), "vertex $v")
        }
    }

    @Test
    fun iterationsStopAtTheLimit() {
        val layout = ArrangementLayout(start, DrawModel.MODEL1, 0)
        val t = IteratedTutte(layout.problem, LayoutSettings(maxIterations = 3, eps = 0.0))
        t.run()
        assertEquals(3, t.iteration)
        // two normalized non-negative vectors differ by at most sqrt(2)
        val once = IteratedTutte(layout.problem, LayoutSettings(maxIterations = 50, eps = 2.0))
        once.run()
        assertEquals(1, once.iteration)
    }

    @Test
    fun everySmallExampleLaysOutWithinTheTimeBudget() {
        val clock = TimeSource.Monotonic.markNow()
        var count = 0
        for (set in Examples.sets.filter { it.n <= 5 }) {
            for (i in 0 until set.size) {
                val arr = set.arrangement(i)
                for (model in DrawModel.entries) {
                    if (model == DrawModel.MODEL1 && !arr.hasModel1) continue
                    val d = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).layout(LayoutSettings())
                    assertFinite(d, "${set.memberName(i)} $model")
                    count++
                }
            }
        }
        assertTrue(count > 1000, "$count layouts")
        assertTrue(clock.elapsedNow() < 60.seconds, "took ${clock.elapsedNow()}")
    }

    @Test
    fun everyFaceCanBeOuter() {
        for (model in DrawModel.entries) {
            for (f in start.faces.indices) {
                val d = ArrangementLayout(start, model, f).layout(LayoutSettings(maxIterations = 10))
                assertFinite(d, "$model face $f")
                assertEquals(f, d.outerFace)
            }
        }
    }

    @Test
    fun twoCirclesAndDigonsDrawInModel2() {
        val two = Arrangement.two()
        assertFalse(two.hasModel1)
        val d = ArrangementLayout(two, DrawModel.MODEL2, two.defaultOuterFace(DrawModel.MODEL2)).layout(LayoutSettings())
        assertFinite(d, "n = 2")
        // every bounded face is a real 2-gon with positive area
        for (f in two.faces.indices) if (f != d.outerFace) assertTrue(abs(Drawing.area(d.polygons[f])) > 1e-3, "face $f")
    }

    @Test
    fun model1DrawsDigonsAsTouchingsThatCanBeHit() {
        val arr = Examples.byName("n3-intersecting-all")!!.let { s -> (0 until s.size).map(s::arrangement) }.first { it.digonCount > 0 }
        val d = ArrangementLayout(arr, DrawModel.MODEL1, arr.defaultOuterFace(DrawModel.MODEL1)).layout(LayoutSettings())
        assertEquals(arr.digonCount, d.touchings.size)
        for ((f, at) in d.touchings) {
            assertEquals(f, d.faceAt(at, 0.01))
            // both circles of the digon pass through the touching point
            for (c in arr.faceCircles(f)) assertTrue(d.circles[c].any { (it.p0 - at).length < 1e-12 }, "circle $c")
        }
    }

    @Test
    fun faceHitTestFindsEveryBoundedFace() {
        for (model in DrawModel.entries) {
            val d = ArrangementLayout(start, model, start.defaultOuterFace(model)).layout(LayoutSettings())
            for (f in start.faces.indices) {
                if (f == d.outerFace) continue
                val poly = d.polygons[f]
                assertTrue(abs(Drawing.area(poly)) > 1e-6, "$model face $f has an area")
                // a point just inside: the centroid of a triangle fan at a vertex
                val inner = innerPoint(poly)
                assertEquals(f, d.faceAt(inner, 0.0), "$model face $f")
            }
            assertEquals(d.outerFace, d.faceAt(Point(5.0, 5.0), 0.0))
        }
    }

    /** A point inside a simple polygon: the midpoint of the longest horizontal chord through its centre height. */
    private fun innerPoint(poly: List<Point>): Point {
        val y = (poly.minOf { it.y } + poly.maxOf { it.y }) / 2 + 1e-7
        val xs = poly.indices.mapNotNull { i ->
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if ((a.y > y) != (b.y > y)) a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y) else null
        }.sorted()
        val (l, r) = xs.chunked(2).filter { it.size == 2 }.maxBy { it[1] - it[0] }
        return Point((l + r) / 2, y)
    }

    @Test
    fun faceKeysSurviveAFlipElsewhere() {
        val t = start.faces.indices.first { start.faces[it].size == 3 }
        val flipped = start.triangleFlip(t)
        // the faces without a corner at the triangle's crossings
        val vertex = PrimalGraph(start).vertexOfCrossing
        fun corners(f: Int) = start.faces[f].map { vertex[it / 2] }.toSet()
        val untouched = start.faces.indices.filter { f -> corners(f).none { it in corners(t) } }
        assertTrue(untouched.isNotEmpty())
        for (f in untouched) {
            val g = flipped.faceWithKey(start.faceKey(f))
            assertTrue(g != null && flipped.faces[g].size == start.faces[f].size, "face $f")
        }
    }
}
