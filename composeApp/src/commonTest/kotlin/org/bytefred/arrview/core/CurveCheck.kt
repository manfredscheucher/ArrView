package org.bytefred.arrview.core

import kotlin.math.max
import kotlin.math.min

/**
 * Geometric checks of drawn closed curves (test helper): every curve is flattened into a closed
 * polyline with [samples] points per Bezier piece.
 *
 * - [selfCrossings]: proper crossings of two non-adjacent segments of one curve.
 * - [contacts]: how two curves meet. Vertices of one curve closer than [near] (relative to the size
 *   of the drawing) to the other form contact zones; a zone is a crossing if the curve changes
 *   sides of the other one across it (inside / outside, the other one is a closed curve), else a
 *   touching. Segments away from zones count their proper crossings.
 */
class CurveCheck(curves: List<List<Cubic>>, samples: Int = 64, near: Double = 1e-5) {
    val finite: Boolean = curves.all { pieces -> pieces.all { c -> listOf(c.p0, c.c1, c.c2, c.p3).all { it.x.isFinite() && it.y.isFinite() } } }

    /** Per curve the polyline points (closed: the last connects to the first) and per piece its point range. */
    private val polylines: List<List<Point>> = curves.map { pieces ->
        buildList { for (c in pieces) for (s in 0 until samples) add(c.at(s.toDouble() / samples)) }
    }

    private val size: Double = run {
        val all = polylines.flatten()
        if (all.isEmpty() || !finite) 1.0
        else max(all.maxOf { it.x } - all.minOf { it.x }, all.maxOf { it.y } - all.minOf { it.y }).coerceAtLeast(1e-12)
    }

    private val tol = near * size

    /** Bounding boxes of runs of [BLOCK] segments, for pruning. */
    private class Block(val from: Int, val to: Int, val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

    private val blocks: List<List<Block>> = polylines.map { pts ->
        val m = pts.size
        (0 until m step BLOCK).map { from ->
            val to = min(from + BLOCK, m)
            val ps = (from..to).map { pts[it % m] }
            Block(from, to, ps.minOf { it.x }, ps.minOf { it.y }, ps.maxOf { it.x }, ps.maxOf { it.y })
        }
    }

    private fun overlap(a: Block, b: Block, margin: Double) =
        a.minX <= b.maxX + margin && b.minX <= a.maxX + margin && a.minY <= b.maxY + margin && b.minY <= a.maxY + margin

    private fun seg(c: Int, i: Int): Pair<Point, Point> {
        val pts = polylines[c]
        return pts[i % pts.size] to pts[(i + 1) % pts.size]
    }

    /** Number of proper crossings of non-adjacent segments of curve [c]. */
    fun selfCrossings(c: Int): Int = selfCrossingPoints(c).size

    /** Where non-adjacent segments of curve [c] cross (a start point of one of them). */
    fun selfCrossingPoints(c: Int): List<Point> {
        val m = polylines[c].size
        val found = ArrayList<Point>()
        val bs = blocks[c]
        for (x in bs.indices) for (y in x until bs.size) {
            if (!overlap(bs[x], bs[y], 0.0)) continue
            for (i in bs[x].from until bs[x].to) for (j in bs[y].from until bs[y].to) {
                if (j <= i) continue
                if (j == i + 1 || (i == 0 && j == m - 1)) continue
                val (a, b) = seg(c, i)
                val (p, q) = seg(c, j)
                if (properCrossing(a, b, p, q)) found += a
            }
        }
        return found
    }

    data class Contact(val crossings: Int, val touchings: Int)

    /** How curves [a] and [b] meet (see the class comment). */
    fun contacts(a: Int, b: Int): Contact {
        val pts = polylines[a]
        val m = pts.size
        val isNear = BooleanArray(m) { distance(pts[it], b) < tol }
        if (isNear.all { it }) return Contact(0, 1)
        var crossings = 0
        var touchings = 0
        // proper crossings of segments with both ends away from the other curve
        for (i in 0 until m) {
            if (isNear[i] || isNear[(i + 1) % m]) continue
            val (p, q) = seg(a, i)
            for (blk in blocks[b]) {
                if (!overlap(Block(0, 0, min(p.x, q.x), min(p.y, q.y), max(p.x, q.x), max(p.y, q.y)), blk, 0.0)) continue
                for (j in blk.from until blk.to) {
                    val (r, s) = seg(b, j)
                    if (properCrossing(p, q, r, s)) crossings++
                }
            }
        }
        // contact zones: maximal runs of near vertices (cyclic)
        val start = (0 until m).first { !isNear[it] }
        var i = 0
        while (i < m) {
            val idx = (start + i) % m
            if (!isNear[idx]) { i++; continue }
            val before = (start + i - 1 + m) % m
            var j = i
            while (j < m && isNear[(start + j) % m]) j++
            val after = (start + j) % m
            if (inside(pts[before], b) != inside(pts[after], b)) crossings++ else touchings++
            i = j
        }
        return Contact(crossings, touchings)
    }

    private fun distance(p: Point, c: Int): Double {
        var best = Double.MAX_VALUE
        for (blk in blocks[c]) {
            if (p.x < blk.minX - tol || p.x > blk.maxX + tol || p.y < blk.minY - tol || p.y > blk.maxY + tol) continue
            for (j in blk.from until blk.to) {
                val (r, s) = seg(c, j)
                best = min(best, Drawing.distanceToSegment(p, r, s))
            }
        }
        return best
    }

    /** Even-odd test against the closed polyline of curve [c]. */
    private fun inside(p: Point, c: Int): Boolean = Drawing.contains(polylines[c], p)

    companion object {
        private const val BLOCK = 16

        private fun orient(a: Point, b: Point, c: Point): Double = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

        /**
         * Segments ab and pq cross in a single interior point of both. Orientations within rounding
         * noise count as zero (collinear segments, as on a straight piece of a B-spline, never cross).
         */
        fun properCrossing(a: Point, b: Point, p: Point, q: Point): Boolean {
            if (max(a.x, b.x) < min(p.x, q.x) || max(p.x, q.x) < min(a.x, b.x)) return false
            if (max(a.y, b.y) < min(p.y, q.y) || max(p.y, q.y) < min(a.y, b.y)) return false
            val noise = 1e-12 * (b - a).length * (q - p).length
            val o1 = orient(a, b, p)
            val o2 = orient(a, b, q)
            val o3 = orient(p, q, a)
            val o4 = orient(p, q, b)
            return ((o1 > noise && o2 < -noise) || (o1 < -noise && o2 > noise)) && ((o3 > noise && o4 < -noise) || (o3 < -noise && o4 > noise))
        }

        /** Contact zones this close (relative to the drawing) are below a pixel: a model 1 digon drawn as such a tiny digon looks like a touching. */
        const val PIXEL = 1e-3

        /**
         * The geometric defects of [d]: non-finite points, self-crossing curves, pairs that meet
         * otherwise than the arrangement says (two crossings for crossing circles, none otherwise).
         * Model 1 draws a digon's curves along a shared piece: they touch there (no crossing), or
         * they cross twice so close to it that it is a touching on the screen (seen with contact
         * zones of [PIXEL]); the latter are listed in [Result.subPixelDigons], not as defects.
         */
        fun check(d: Drawing): Result {
            val check = CurveCheck(d.circles)
            if (!check.finite) return Result(listOf("non-finite points"), emptyList())
            val coarse by lazy { CurveCheck(d.circles, near = PIXEL) }
            val arr = d.arrangement
            val defects = ArrayList<String>()
            val subPixel = ArrayList<String>()
            for (c in 0 until arr.n) check.selfCrossings(c).let { if (it > 0) defects += "curve ${c + 1} crosses itself ($it)" }
            val digonPairs = arr.faces.indices.filter { arr.faces[it].size == 2 }.map { arr.faceCircles(it).sorted() }
            for (i in 0 until arr.n) for (j in i + 1 until arr.n) {
                val got = check.contacts(i, j)
                val pair = "curves ${i + 1}, ${j + 1}"
                when {
                    !arr.crosses(i, j) -> if (got != Contact(0, 0)) defects += "$pair: $got, expected no contact"
                    d.model == DrawModel.MODEL1 && listOf(i, j) in digonPairs -> when {
                        got == Contact(0, 1) -> Unit
                        got.crossings == 2 && coarse.contacts(i, j) == Contact(0, 1) -> subPixel += "$pair: $got"
                        else -> defects += "$pair: $got (pixel scale: ${coarse.contacts(i, j)}), expected a touching"
                    }
                    else -> if (got != Contact(2, 0)) defects += "$pair: $got, expected 2 crossings"
                }
            }
            return Result(defects, subPixel)
        }

        /** The defects of [d] (see [check]). */
        fun defects(d: Drawing): List<String> = check(d).defects
    }

    class Result(val defects: List<String>, val subPixelDigons: List<String>)
}
