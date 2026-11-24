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
            // both circles of the digon pass through the touching point (the middle of their shared piece)
            for (c in arr.faceCircles(f)) {
                val pts = Drawing.flatten(d.circles[c])
                assertTrue(pts.indices.minOf { Drawing.distanceToSegment(at, pts[it], pts[(it + 1) % pts.size]) } < 1e-9, "circle $c")
            }
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

    /** The Bezier pieces of [closedUniformBSpline] are the uniform cubic B-spline: its basis functions at every t. */
    @Test
    fun bSplineToBezierIsExact() {
        val ctrl = listOf(Point(0.0, 0.0), Point(2.0, 1.0), Point(3.0, -1.0), Point(1.0, -2.0), Point(-1.0, 0.5))
        val pieces = closedUniformBSpline(ctrl)
        assertEquals(ctrl.size, pieces.size)
        for (i in ctrl.indices) for (t in listOf(0.0, 0.1, 0.37, 0.5, 0.9, 1.0)) {
            val p = List(4) { ctrl[(i + it) % ctrl.size] }
            val s = 1 - t
            val b = listOf(s * s * s / 6, (3 * t * t * t - 6 * t * t + 4) / 6, (-3 * t * t * t + 3 * t * t + 3 * t + 1) / 6, t * t * t / 6)
            val expected = p.indices.fold(Point.ZERO) { acc, k -> acc + p[k] * b[k] }
            assertEquals(0.0, (pieces[i].at(t) - expected).length, 1e-12, "piece $i, t = $t")
        }
        // closed: every piece ends where the next starts
        for (i in pieces.indices) assertEquals(0.0, (pieces[i].p3 - pieces[(i + 1) % pieces.size].p0).length, 1e-12)
    }

    /**
     * The control points of every curve are draw7.sage's: per vertex v1 of the circle (neighbours v0,
     * v2) `xl = v1 + l0 (v0 - v1)`, `v1`, `xr = v1 + l2 (v2 - v1)`, `l = sqrt(min(distances[v1]) / d) / 3`,
     * recomputed here from the positions and the circles' crossing orders.
     */
    @Test
    fun curvesHaveTheControlPointsOfTheSagePrograms() {
        for (arr in listOf(start, ThreeCircles.nonKrupp)) for (model in DrawModel.entries) {
            val layout = ArrangementLayout(arr, model, arr.defaultOuterFace(model))
            val pos = layout.positions(LayoutSettings(maxIterations = 7))
            fun at(v: Int) = Point(pos[2 * v], pos[2 * v + 1])
            val primal = PrimalGraph(arr)
            val crossings = primal.graph.vertexCount
            // sage's compute_pseudocircles: model 1 the crossings of the circle, model 2 crossings and arc vertices
            val seqs = (0 until arr.n).map { c ->
                (arr.offset(c) until arr.offset(c + 1)).flatMap { k ->
                    if (model == DrawModel.MODEL1) listOf(primal.vertexOfCrossing[k]) else listOf(primal.vertexOfCrossing[k], crossings + k)
                }
            }
            val distances = HashMap<Int, MutableList<Double>>()
            for (seq in seqs) for (i in seq.indices) {
                val a = at(seq[(i - 1 + seq.size) % seq.size])
                val b = at(seq[i])
                val d2 = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
                distances.getOrPut(seq[(i - 1 + seq.size) % seq.size]) { ArrayList() } += d2
                distances.getOrPut(seq[i]) { ArrayList() } += d2
            }
            val curves = layout.curves(pos)
            for ((c, seq) in seqs.withIndex()) {
                val ctrl = seq.indices.flatMap { i ->
                    val x0 = at(seq[(i - 1 + seq.size) % seq.size])
                    val x1 = at(seq[i])
                    val x2 = at(seq[(i + 1) % seq.size])
                    val m = distances.getValue(seq[i]).min()
                    val l0 = kotlin.math.sqrt(m / ((x0.x - x1.x) * (x0.x - x1.x) + (x0.y - x1.y) * (x0.y - x1.y))) / 3
                    val l2 = kotlin.math.sqrt(m / ((x2.x - x1.x) * (x2.x - x1.x) + (x2.y - x1.y) * (x2.y - x1.y))) / 3
                    listOf(Point(x1.x + l0 * (x0.x - x1.x), x1.y + l0 * (x0.y - x1.y)), x1, Point(x1.x + l2 * (x2.x - x1.x), x1.y + l2 * (x2.y - x1.y)))
                }
                val expected = closedUniformBSpline(ctrl)
                assertEquals(expected.size, curves[c].size)
                for ((a, b) in expected.zip(curves[c])) {
                    for ((p, q) in listOf(a.p0 to b.p0, a.c1 to b.c1, a.c2 to b.c2, a.p3 to b.p3)) assertEquals(0.0, (p - q).length, 1e-12, "$model circle $c")
                }
            }
        }
    }

    /**
     * The weights of iteration 2 as draw7.sage / draw3.sage compute them from iteration 1: `w + 0.1
     * (dist2^2 - w)` plus `factor * (2 A(f))^4` for both faces f of the edge, `A` the convex hull area,
     * the outer face included.
     */
    @Test
    fun weightsOfTheSecondIteration() {
        for (model in DrawModel.entries) {
            val layout = ArrangementLayout(start, model, start.defaultOuterFace(model))
            val p = layout.problem
            val tutte = IteratedTutte(p, LayoutSettings(maxIterations = 2, eps = 0.0))
            tutte.step()
            val pos = tutte.positions.copyOf()
            val w1 = tutte.currentWeights.copyOf()
            tutte.step()
            val factor = if (model == DrawModel.MODEL1) 1.0 else 0.001
            fun pt(v: Int) = Point(pos[2 * v], pos[2 * v + 1])
            for (e in 0 until p.edgeCount) {
                val a = p.edges[2 * e]
                val b = p.edges[2 * e + 1]
                val d2 = (pt(a) - pt(b)).dot(pt(a) - pt(b))
                var expected = w1[e] + 0.1 * (d2 * d2 - w1[e])
                for (f in p.faces) for (i in f.indices) {
                    val u = f[i]
                    val v = f[(i + 1) % f.size]
                    if ((u == a && v == b) || (u == b && v == a)) {
                        val q = 2 * convexHullArea(f.map(::pt))
                        expected += factor * q * q * q * q
                    }
                }
                assertEquals(expected, tutte.currentWeights[e], 1e-12 * expected, "$model edge $e")
            }
            // the outer face is one of the faces
            assertTrue(p.faces.any { it.contentEquals(p.outer) }, "$model")
        }
    }

    @Test
    fun convexHullAreas() {
        assertEquals(1.0, convexHullArea(listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0), Point(0.0, 1.0), Point(0.5, 0.5))), 1e-15)
        // a non-convex face: its hull
        assertEquals(1.0, convexHullArea(listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(0.5, 0.2), Point(1.0, 1.0), Point(0.0, 1.0))), 1e-15)
        assertEquals(0.0, convexHullArea(listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(2.0, 2.0))))
        assertEquals(0.0, convexHullArea(listOf(Point(0.0, 0.0), Point(1.0, 1.0))))
    }
}
