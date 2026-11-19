package org.bytefred.arrview.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** A point of the drawing (y up). */
data class Point(val x: Double, val y: Double) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)
    operator fun times(s: Double) = Point(x * s, y * s)
    fun dot(o: Point) = x * o.x + y * o.y
    val length: Double get() = hypot(x, y)

    /** This vector scaled to length 1, or the zero vector. */
    fun normalized(): Point = length.let { if (it > 0.0 && it.isFinite()) Point(x / it, y / it) else ZERO }

    companion object {
        val ZERO = Point(0.0, 0.0)
    }
}

/** A cubic Bezier piece. */
data class Cubic(val p0: Point, val c1: Point, val c2: Point, val p3: Point) {
    fun at(t: Double): Point {
        val s = 1 - t
        return p0 * (s * s * s) + c1 * (3 * s * s * t) + c2 * (3 * s * t * t) + p3 * (t * t * t)
    }

    fun reversed() = Cubic(p3, c2, c1, p0)

    /** The part from parameter [a] to [b] (`0 <= a < b <= 1`), exact (de Casteljau). */
    fun part(a: Double, b: Double): Cubic {
        if (a <= 0.0 && b >= 1.0) return this
        val right = if (a <= 0.0) this else split(a).second
        return if (b >= 1.0) right else right.split((b - a) / (1 - a)).first
    }

    /** The two halves at parameter [t] (de Casteljau). */
    fun split(t: Double): Pair<Cubic, Cubic> {
        fun lerp(p: Point, q: Point) = p + (q - p) * t
        val a = lerp(p0, c1)
        val b = lerp(c1, c2)
        val c = lerp(c2, p3)
        val d = lerp(a, b)
        val e = lerp(b, c)
        val m = lerp(d, e)
        return Cubic(p0, a, d, m) to Cubic(m, e, c, p3)
    }
}

/**
 * The closed uniform cubic B-spline with control points [ctrl] (Ipe's closed spline, path operator
 * `u`) as cubic Bezier pieces, by the exact conversion: piece `i` is the span of
 * `ctrl[i], .., ctrl[i + 3]` (indices cyclic) and starts at `(ctrl[i] + 4 ctrl[i + 1] + ctrl[i + 2]) / 6`.
 */
fun closedUniformBSpline(ctrl: List<Point>): List<Cubic> {
    val m = ctrl.size
    return List(m) { i ->
        val p0 = ctrl[i]
        val p1 = ctrl[(i + 1) % m]
        val p2 = ctrl[(i + 2) % m]
        val p3 = ctrl[(i + 3) % m]
        Cubic((p0 + p1 * 4.0 + p2) * (1.0 / 6), (p1 * 2.0 + p2) * (1.0 / 3), (p1 + p2 * 2.0) * (1.0 / 3), (p1 + p2 * 4.0 + p3) * (1.0 / 6))
    }
}

/** The two drawings of an arrangement. */
enum class DrawModel(val label: String) {
    /** The primal graph: crossings are the vertices, a digon's two arcs are one edge, so its curves touch (intersecting only). */
    MODEL1("Model 1"),

    /** The vertex-edge-face graph: digons are real 2-gons (always available). */
    MODEL2("Model 2"),
}

/** Model 1 needs an intersecting arrangement with a face that is not a digon (not n = 2). */
val Arrangement.hasModel1: Boolean get() = isIntersecting && faces.any { it.size >= 3 }

/** Whether face [f] can be the outer face in [model] (model 1 draws digons as points). */
fun Arrangement.canBeOuter(f: Int, model: DrawModel): Boolean =
    f in faces.indices && (model == DrawModel.MODEL2 || faces[f].size >= 3)

/**
 * A key of face [f] that survives operations elsewhere in the arrangement: its sides as
 * (circle, crossing entry, direction), sorted. A face whose sides did not change has the same key
 * after a flip.
 */
fun Arrangement.faceKey(f: Int): String =
    faces[f].map { a -> Triple(circleOfDart(a), entry(a / 2), a % 2) }
        .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        .joinToString(";") { "${it.first},${it.second},${it.third}" }

/** The face with [key] (see [faceKey]), or null. */
fun Arrangement.faceWithKey(key: String): Int? = faces.indices.firstOrNull { faceKey(it) == key }

/**
 * A finished drawing: the closed curve of every circle, the boundary of every bounded face and,
 * in model 1, the touching points of the digons. The outer face is the unbounded region.
 */
class Drawing(
    val arrangement: Arrangement,
    val model: DrawModel,
    val outerFace: Int,
    /** The closed curve of every circle (the B-spline as Bezier pieces). */
    val circles: List<List<Cubic>>,
    /** The boundary of every face (indices of [Arrangement.faces]); empty for the outer face and model 1 digons. */
    val faces: List<List<Cubic>>,
    /** Model 1: the point where the two circles of a digon touch, per digon face. */
    val touchings: Map<Int, Point>,
    /** Where the label of every circle goes: a point on its curve and the outward direction there. */
    val labels: List<Pair<Point, Point>>,
    /** The boundary of the outer face (for highlighting it). */
    val outerBoundary: List<Cubic> = emptyList(),
    /** The curve of every arc, from crossing k to the next one on its circle. */
    val arcs: List<List<Cubic>> = emptyList(),
) {
    /** The face boundaries as polygons (for hit tests and fills of the hover highlight). */
    val polygons: List<List<Point>> by lazy { faces.map { flatten(it) } }

    /** Smallest and largest coordinates of the curves: (minX, minY, maxX, maxY). */
    val bounds: DoubleArray by lazy {
        val all = circles.flatten().flatMap { listOf(it.p0, it.c1, it.c2, it.p3) }
        if (all.isEmpty()) doubleArrayOf(-1.0, -1.0, 1.0, 1.0)
        else doubleArrayOf(all.minOf { it.x }, all.minOf { it.y }, all.maxOf { it.x }, all.maxOf { it.y })
    }

    /**
     * The face at [p]: a model 1 digon within [touchRadius] of its touching point, else the
     * bounded face containing [p], else the outer face.
     */
    fun faceAt(p: Point, touchRadius: Double): Int {
        touchings.entries.minByOrNull { (it.value - p).length }?.let { (f, at) -> if ((at - p).length <= touchRadius) return f }
        var best: Int? = null
        var bestArea = Double.MAX_VALUE
        for ((f, poly) in polygons.withIndex()) {
            if (poly.size < 3 || !contains(poly, p)) continue
            val a = abs(area(poly))
            if (a < bestArea) { best = f; bestArea = a }
        }
        return best ?: outerFace
    }

    /** Points along all curves (for distances to the drawing). */
    val curvePoints: List<Point> by lazy { circles.flatMap { flatten(it) } }

    private val interiorPoints = HashMap<Int, Point?>()

    /**
     * A point well inside the bounded face [f]: the point of a grid over the face farthest from its
     * boundary (a model 1 digon: its touching point); null for the outer face.
     */
    fun interiorPoint(f: Int): Point? = interiorPoints.getOrPut(f) {
        touchings[f]?.let { return@getOrPut it }
        val poly = polygons.getOrNull(f)?.takeIf { it.size >= 3 } ?: return@getOrPut null
        val minX = poly.minOf { it.x }
        val maxX = poly.maxOf { it.x }
        val minY = poly.minOf { it.y }
        val maxY = poly.maxOf { it.y }
        var best: Point? = null
        var bestDist = -1.0
        for (gx in 1 until GRID) for (gy in 1 until GRID) {
            val p = Point(minX + (maxX - minX) * gx / GRID, minY + (maxY - minY) * gy / GRID)
            if (!contains(poly, p)) continue
            val d = poly.indices.minOf { distanceToSegment(p, poly[it], poly[(it + 1) % poly.size]) }
            if (d > bestDist) { best = p; bestDist = d }
        }
        best ?: widestChordMiddle(poly)
    }

    /** The middle of the longest horizontal chord through the middle of the polygon's height. */
    private fun widestChordMiddle(poly: List<Point>): Point {
        val y = (poly.minOf { it.y } + poly.maxOf { it.y }) / 2
        val xs = poly.indices.mapNotNull { i ->
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if ((a.y > y) != (b.y > y)) a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y) else null
        }.sorted()
        val chord = xs.chunked(2).filter { it.size == 2 }.maxByOrNull { it[1] - it[0] } ?: return poly.first()
        return Point((chord[0] + chord[1]) / 2, y)
    }

    companion object {
        /** Grid steps per axis for [interiorPoint]. */
        private const val GRID = 24

        fun distanceToSegment(p: Point, a: Point, b: Point): Double {
            val ab = b - a
            val len2 = ab.dot(ab)
            val t = if (len2 > 0.0) ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0) else 0.0
            return (p - (a + ab * t)).length
        }

        /** Points per cubic piece when flattening. */
        private const val SAMPLES = 12

        fun flatten(pieces: List<Cubic>): List<Point> = buildList {
            for (c in pieces) for (i in 0 until SAMPLES) add(c.at(i.toDouble() / SAMPLES))
        }

        /** Even-odd point in polygon test. */
        fun contains(poly: List<Point>, p: Point): Boolean {
            var inside = false
            var j = poly.size - 1
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[j]
                if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
                j = i
            }
            return inside
        }

        fun area(poly: List<Point>): Double {
            var s = 0.0
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                s += a.x * b.y - b.x * a.y
            }
            return s / 2
        }
    }
}

/**
 * Lays out an arrangement in model 1 or model 2 with a given outer face: [problem] is the graph to
 * embed with [IteratedTutte], [drawing] turns its positions into curves. Ported from
 * `program_py3/draw7.sage` (model 1) and `model2_program/draw3.sage` (model 2).
 *
 * Model 1 embeds the primal graph `G2` (crossings; sage's `G2 = Graph(G)` without multiple edges,
 * so the two arcs of a digon are one edge) and draws the curves of the multigraph `G`. draw7.sage
 * itself refuses arrangements with digons (`assert(2 not in deg)`); ArrView runs the same code on
 * them, so the two curves of a digon share a piece (they touch). Model 2 embeds the
 * vertex-edge-face graph without the outer face's vertex, as draw3.sage does; the curves run along
 * the crossing and arc vertices.
 *
 * The curves are the B-splines of `graph_2_ipe` (draw7.sage lines 54-81, draw3.sage lines 71-100):
 * every circle is a closed uniform B-spline whose control points are, per vertex `v1` on it with
 * neighbours `v0`, `v2` on the circle, `xl = v1 + l0 (v0 - v1)`, `v1`, `xr = v1 + l2 (v2 - v1)`
 * with `l0 = sqrt(min(distances[v1]) / |v0 - v1|^2) / 3` (`l2` with `v2`); `distances[v]` holds
 * the squared lengths of the circle edges at `v`.
 */
class ArrangementLayout(val arrangement: Arrangement, val model: DrawModel, val outerFace: Int) {
    private val arr = arrangement
    private val primal = PrimalGraph(arr)
    private val crossings = primal.graph.vertexCount

    init {
        require(model == DrawModel.MODEL2 || arr.hasModel1) { "model 1 needs an intersecting arrangement with n >= 3" }
        require(arr.canBeOuter(outerFace, model)) { "face $outerFace cannot be the outer face in ${model.label}" }
    }

    private fun vertexOfCrossing(k: Int) = primal.vertexOfCrossing[k]

    // model 2 vertex numbering: crossings, then arcs, then the faces without the outer one
    private fun arcVertex(k: Int) = crossings + k

    private fun faceVertex(f: Int) = crossings + arr.size + if (f < outerFace) f else f - 1

    val problem: TutteProblem = when (model) {
        DrawModel.MODEL1 -> model1Problem()
        DrawModel.MODEL2 -> model2Problem()
    }

    private fun model1Problem(): TutteProblem {
        // G2: one edge per pair of adjacent crossings; multiplicity: the arcs of G on it
        val count = LinkedHashMap<Pair<Int, Int>, Int>()
        for (k in 0 until arr.size) {
            val a = vertexOfCrossing(k)
            val b = vertexOfCrossing(arr.nextOnCircle(k))
            val e = minOf(a, b) to maxOf(a, b)
            count[e] = (count[e] ?: 0) + 1
        }
        fun cycle(f: Int) = arr.faces[f].map { vertexOfCrossing(it / 2) }.toIntArray()
        // the faces of G2: all but the digons, the outer one included
        val faces = arr.faces.indices.filter { arr.faces[it].size >= 3 }.map(::cycle)
        return TutteProblem(
            crossings, count.keys.flatMap { listOf(it.first, it.second) }.toIntArray(), faces, cycle(outerFace),
            areaFactor = 1.0, multiplicity = count.values.toIntArray(),
        )
    }

    private fun model2Problem(): TutteProblem {
        val edges = ArrayList<Int>()
        for (k in 0 until arr.size) {
            val arc = arcVertex(k)
            edges += listOf(vertexOfCrossing(k), arc, vertexOfCrossing(arr.nextOnCircle(k)), arc)
            for (f in listOf(arr.faceOfDart[2 * k], arr.faceOfDart[arr.alpha(2 * k)])) {
                if (f != outerFace) edges += listOf(arc, faceVertex(f))
            }
        }
        val outer = arr.faces[outerFace].flatMap { a ->
            listOf(arcVertex(arr.arcOfDart(a)), vertexOfCrossing(arr.faceSuccessors[a] / 2))
        }.toIntArray()
        // the faces of G2: a quadrangle face vertex - arc - crossing - arc per corner of a bounded face, and the outer face
        val faces = arrayListOf(outer)
        for ((f, darts) in arr.faces.withIndex()) {
            if (f == outerFace) continue
            for (i in darts.indices) {
                val a = darts[i]
                val b = darts[(i + 1) % darts.size]
                faces += intArrayOf(faceVertex(f), arcVertex(arr.arcOfDart(a)), vertexOfCrossing(b / 2), arcVertex(arr.arcOfDart(b)))
            }
        }
        return TutteProblem(crossings + arr.size + arr.faceCount - 1, edges.toIntArray(), faces, outer, areaFactor = 0.001)
    }

    /**
     * The vertex sequence of every circle (sage's `compute_pseudocircles`): model 1 its crossings,
     * model 2 its crossings and arc vertices alternately, in circle order.
     */
    private val sequences: List<IntArray> = (0 until arr.n).map { c ->
        (arr.offset(c) until arr.offset(c + 1)).flatMap { k ->
            if (model == DrawModel.MODEL1) listOf(vertexOfCrossing(k)) else listOf(vertexOfCrossing(k), arcVertex(k))
        }.toIntArray()
    }

    /** Position of crossing entry [k] in the sequence of its circle. */
    private fun indexInSequence(k: Int): Int = (k - arr.offset(arr.circleOfCrossing(k))) * if (model == DrawModel.MODEL1) 1 else 2

    /** Computes the iterated Tutte embedding and the drawing at once. */
    fun layout(settings: LayoutSettings): Drawing = drawing(IteratedTutte(problem, settings).run())

    /** The B-spline of every circle for the [problem]'s vertex [positions] (`x` at `2i`, `y` at `2i + 1`), as Bezier pieces. */
    fun curves(positions: DoubleArray): List<List<Cubic>> {
        fun at(v: Int) = Point(positions[2 * v], positions[2 * v + 1])
        val distances = HashMap<Int, MutableList<Double>>()
        for (seq in sequences) {
            for (i in seq.indices) {
                val p0 = at(seq[(i - 1 + seq.size) % seq.size])
                val p1 = at(seq[i])
                val d2 = (p0 - p1).dot(p0 - p1)
                distances.getOrPut(seq[(i - 1 + seq.size) % seq.size]) { ArrayList() }.add(d2)
                distances.getOrPut(seq[i]) { ArrayList() }.add(d2)
            }
        }
        return sequences.map { seq ->
            val ctrl = ArrayList<Point>(3 * seq.size)
            for (i in seq.indices) {
                val x0 = at(seq[(i - 1 + seq.size) % seq.size])
                val x1 = at(seq[i])
                val x2 = at(seq[(i + 1) % seq.size])
                val d0 = (x0 - x1).dot(x0 - x1)
                val d2 = (x2 - x1).dot(x2 - x1)
                val dmin = distances.getValue(seq[i]).min()
                val lmbd0 = sqrt(dmin / d0) / 3
                val lmbd2 = sqrt(dmin / d2) / 3
                ctrl += x1 + (x0 - x1) * lmbd0
                ctrl += x1
                ctrl += x1 + (x2 - x1) * lmbd2
            }
            closedUniformBSpline(ctrl)
        }
    }

    /** The drawing for the [problem]'s vertex [positions] (`x` at `2i`, `y` at `2i + 1`). */
    fun drawing(positions: DoubleArray): Drawing {
        val circles = curves(positions)
        // vertex k of a sequence has the control points 3k .. 3k + 2; the curve is nearest to it at
        // the start of piece 3k, its knot
        val digonFaces = if (model == DrawModel.MODEL1) arr.faces.indices.filter { arr.faces[it].size == 2 } else emptyList()
        val inDigon = BooleanArray(arr.size)
        for (f in digonFaces) for (a in arr.faces[f]) {
            inDigon[a / 2] = true
            inDigon[arr.partners[a / 2]] = true
        }
        // where every crossing is on the curve of its circle (a curve parameter: piece + t)
        val param = DoubleArray(arr.size)
        for (k in 0 until arr.size) {
            val p = arr.partners[k]
            if (p < k) continue
            val ik = indexInSequence(k)
            val ip = indexInSequence(p)
            val hit = if (inDigon[k]) null else crossingNear(
                circles[arr.circleOfCrossing(k)], ik, circles[arr.circleOfCrossing(p)], ip,
                Point(positions[2 * vertexOfCrossing(k)], positions[2 * vertexOfCrossing(k) + 1]),
            )
            param[k] = hit?.first ?: (3.0 * ik)
            param[p] = hit?.second ?: (3.0 * ip)
        }
        val arcs = (0 until arr.size).map { k ->
            subCurve(circles[arr.circleOfCrossing(k)], param[k], param[arr.nextOnCircle(k)])
        }
        fun boundary(darts: IntArray) = darts.flatMap { a ->
            if (a % 2 == 0) arcs[a / 2] else arcs[arr.arcOfDart(a)].reversed().map { it.reversed() }
        }
        // model 1: the two curves of a digon share the span of its two crossings and their inner
        // control points; they touch in its middle
        val touchings = digonFaces.associateWith { f ->
            val k = arr.arcOfDart(arr.faces[f].first())
            val c = arr.circleOfCrossing(k)
            circles[c][(3 * indexInSequence(k) + 1) % circles[c].size].at(0.5)
        }
        val faces = arr.faces.mapIndexed { f, darts -> if (f == outerFace || f in touchings) emptyList() else boundary(darts) }
        return Drawing(arr, model, outerFace, circles, faces, touchings, labels(arcs), boundary(arr.faces[outerFace]), arcs)
    }

    /**
     * Per circle a label position on its curve and the outward direction there: the middle of a
     * longer arc between two crossings, the one farthest from the centre of the drawing that
     * keeps a distance from the labels placed before.
     */
    private fun labels(arcs: List<List<Cubic>>): List<Pair<Point, Point>> {
        val all = arcs.mapNotNull { it.firstOrNull()?.p0 }
        if (all.isEmpty()) return List(arr.n) { Point.ZERO to Point.ZERO }
        val centre = Point(all.sumOf { it.x } / all.size, all.sumOf { it.y } / all.size)
        val separation = LABEL_SEPARATION * all.maxOf { (it - centre).length }
        val placed = ArrayList<Point>()
        return (0 until arr.n).map { c ->
            val own = (arr.offset(c) until arr.offset(c + 1)).map { arcs[it] }.filter { it.isNotEmpty() }
            fun chord(arc: List<Cubic>) = (arc.last().p3 - arc.first().p0).length
            // the middles of the longer arcs, farthest out first
            val longest = own.maxOfOrNull(::chord) ?: 0.0
            val candidates = own.filter { chord(it) >= LONG_ARC * longest }.map(::middle)
                .sortedByDescending { (it - centre).length }
            val at = candidates.firstOrNull { p -> placed.all { (it - p).length >= separation } } ?: candidates.firstOrNull() ?: Point.ZERO
            placed += at
            at to (at - centre).normalized()
        }
    }

    private companion object {
        /** Least distance between two labels, relative to the radius of the drawing. */
        const val LABEL_SEPARATION = 0.15

        /** Labels go on arcs at least this long (chord), relative to the longest arc of the circle. */
        const val LONG_ARC = 0.5

        /** Points per Bezier piece when looking for crossings. */
        const val SAMPLES = 24

        /** The point halfway along [arc] (by the length of its flattening). */
        fun middle(arc: List<Cubic>): Point {
            val pts = Drawing.flatten(arc) + arc.last().p3
            val lengths = pts.zipWithNext { a, b -> (b - a).length }
            var rest = lengths.sum() / 2
            for ((i, l) in lengths.withIndex()) {
                if (rest <= l && l > 0.0) return pts[i] + (pts[i + 1] - pts[i]) * (rest / l)
                rest -= l
            }
            return pts.first()
        }

        /** Point of [curve] at parameter [s] (piece index + t, cyclic). */
        fun pointAt(curve: List<Cubic>, s: Double): Point {
            val m = curve.size
            val w = ((s % m) + m) % m
            val i = w.toInt().coerceAtMost(m - 1)
            return curve[i].at(w - i)
        }

        /** The part of the closed [curve] from parameter [from] forward to [to] (cyclic), as Bezier pieces. */
        fun subCurve(curve: List<Cubic>, from: Double, to: Double): List<Cubic> {
            val m = curve.size
            val a = ((from % m) + m) % m
            var b = ((to % m) + m) % m
            if (b <= a) b += m
            val result = ArrayList<Cubic>()
            var i = a.toInt()
            while (i < b) {
                val lo = maxOf(a, i.toDouble()) - i
                val hi = minOf(b, i + 1.0) - i
                if (hi > lo) result += curve[i % m].part(lo, hi)
                i++
            }
            return result
        }

        /**
         * The crossing of [c] and [d] near a vertex: [ic] and [id] are its positions in their
         * sequences; only the pieces from the previous vertex's knot to the next one's are searched
         * (flattened). Of several intersections the one nearest to [vertex]; as curve parameters
         * (on [c], on [d]), or null if the curves do not cross there.
         */
        fun crossingNear(c: List<Cubic>, ic: Int, d: List<Cubic>, id: Int, vertex: Point): Pair<Double, Double>? {
            fun window(curve: List<Cubic>, i: Int): List<Pair<Point, Double>> {
                val m = curve.size
                val first = 3 * i - 3
                return buildList {
                    for (j in 0 until 6) {
                        val piece = first + j
                        for (s in 0 until SAMPLES) {
                            val t = s.toDouble() / SAMPLES
                            add(curve[((piece % m) + m) % m].at(t) to piece + t)
                        }
                    }
                    add(curve[(((first + 6) % m) + m) % m].p0 to first + 6.0)
                }
            }
            val wc = window(c, ic)
            val wd = window(d, id)
            var best: Pair<Double, Double>? = null
            var bestDist = Double.MAX_VALUE
            for (i in 0 until wc.size - 1) {
                val (a, sa) = wc[i]
                val (b, sb) = wc[i + 1]
                for (j in 0 until wd.size - 1) {
                    val (p, sp) = wd[j]
                    val (q, sq) = wd[j + 1]
                    val (u, v) = segmentIntersection(a, b, p, q) ?: continue
                    val at = a + (b - a) * u
                    val dist = (at - vertex).length
                    if (dist < bestDist) {
                        bestDist = dist
                        best = (sa + (sb - sa) * u) to (sp + (sq - sp) * v)
                    }
                }
            }
            return best
        }

        /** Where segments ab and pq cross, as parameters along both (`0 <= u, v < 1`), or null. */
        fun segmentIntersection(a: Point, b: Point, p: Point, q: Point): Pair<Double, Double>? {
            val r = b - a
            val s = q - p
            val den = r.x * s.y - r.y * s.x
            if (den == 0.0) return null
            val ap = p - a
            val u = (ap.x * s.y - ap.y * s.x) / den
            val v = (ap.x * r.y - ap.y * r.x) / den
            return if (u >= 0.0 && u < 1.0 && v >= 0.0 && v < 1.0) u to v else null
        }
    }
}
