package org.bytefred.arrview.core

import kotlin.math.abs
import kotlin.math.hypot

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
}

/** The two drawings of an arrangement. */
enum class DrawModel(val label: String) {
    /** The primal graph: crossings are the vertices, digons are drawn as touchings (intersecting only). */
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
 * A finished drawing: smooth closed curves per circle, the boundary of every bounded face and,
 * in model 1, the touching points of the digons. The outer face is the unbounded region.
 */
class Drawing(
    val arrangement: Arrangement,
    val model: DrawModel,
    val outerFace: Int,
    /** The closed curve of every circle. */
    val circles: List<List<Cubic>>,
    /** The boundary of every face (indices of [Arrangement.faces]); empty for the outer face and model 1 digons. */
    val faces: List<List<Cubic>>,
    /** Model 1: the point where the two circles of a digon touch, per digon face. */
    val touchings: Map<Int, Point>,
    /** Where the label of every circle goes: a point on its curve and the outward direction there. */
    val labels: List<Pair<Point, Point>>,
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

    companion object {
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
 * embed with [IteratedTutte], [drawing] turns its positions into curves.
 *
 * Model 1 embeds the primal graph (crossings), the two arcs of a digon as one edge; the curves of
 * a digon then touch at the middle of that edge. Model 2 embeds the vertex-edge-face graph without
 * the outer face's vertex, as `model2_program/draw3.sage` does; the curves run through the crossing
 * and arc vertices.
 *
 * The curves are smooth closed curves through the crossings (see doc/SPEC.md): the tangent at a
 * crossing is parallel to the line through its two neighbours on the circle (model 2: the arc
 * vertices), shortened by the turn there; each arc between two crossings is one cubic Bezier
 * piece with handles of a third of its chord.
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
        val edges = LinkedHashSet<Pair<Int, Int>>()
        for (k in 0 until arr.size) {
            val a = vertexOfCrossing(k)
            val b = vertexOfCrossing(arr.nextOnCircle(k))
            edges.add(minOf(a, b) to maxOf(a, b))
        }
        fun cycle(f: Int) = arr.faces[f].map { vertexOfCrossing(it / 2) }.toIntArray()
        val faces = arr.faces.indices.filter { it != outerFace && arr.faces[it].size >= 3 }.map(::cycle)
        return TutteProblem(crossings, edges.flatMap { listOf(it.first, it.second) }.toIntArray(), faces, cycle(outerFace), areaFactor = 1.0)
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
        val faces = ArrayList<IntArray>()
        for ((f, darts) in arr.faces.withIndex()) {
            if (f == outerFace) continue
            for (i in darts.indices) {
                val a = darts[i]
                val b = darts[(i + 1) % darts.size]
                faces += intArrayOf(faceVertex(f), arcVertex(arr.arcOfDart(a)), vertexOfCrossing(b / 2), arcVertex(arr.arcOfDart(b)))
            }
        }
        val outer = arr.faces[outerFace].flatMap { a ->
            listOf(arcVertex(arr.arcOfDart(a)), vertexOfCrossing(arr.faceSuccessors[a] / 2))
        }.toIntArray()
        return TutteProblem(crossings + arr.size + arr.faceCount - 1, edges.toIntArray(), faces, outer, areaFactor = 0.001)
    }

    /** The digons whose two crossings are drawn as one touching point (model 1). */
    private val digons: List<Int> =
        if (model == DrawModel.MODEL1) arr.faces.indices.filter { arr.faces[it].size == 2 } else emptyList()

    /** Computes the iterated Tutte embedding and the drawing at once. */
    fun layout(settings: LayoutSettings): Drawing = drawing(IteratedTutte(problem, settings).run())

    /** The drawing for the [problem]'s vertex [positions] (`x` at `2i`, `y` at `2i + 1`). */
    fun drawing(positions: DoubleArray): Drawing {
        fun at(vertex: Int) = Point(positions[2 * vertex], positions[2 * vertex + 1]).takeIf { it.x.isFinite() && it.y.isFinite() } ?: Point.ZERO
        // model 1: both crossings of a digon are drawn at the middle of its edge
        val merged = HashMap<Int, Int>()
        val touchings = HashMap<Int, Point>()
        val midpoints = HashMap<Int, Point>()
        val touchAxis = HashMap<Int, Point>()
        for (f in digons) {
            val (a, b) = arr.faces[f].map { vertexOfCrossing(it / 2) }
            val pa = at(a)
            val pb = at(b)
            merged[b] = a
            midpoints[a] = (pa + pb) * 0.5
            touchings[f] = (pa + pb) * 0.5
            touchAxis[a] = pb - pa
        }
        // the point sequence of every circle: vertex ids (model 1: digon pairs merged)
        val sequences = (0 until arr.n).map { c ->
            val seq = ArrayList<Int>()
            for (k in arr.offset(c) until arr.offset(c + 1)) {
                val x = vertexOfCrossing(k).let { merged[it] ?: it }
                if (seq.lastOrNull() != x) seq += x
                if (model == DrawModel.MODEL2) seq += arcVertex(k)
            }
            if (seq.size > 1 && seq.first() == seq.last()) seq.removeAt(seq.size - 1)
            seq
        }
        val pointOf = HashMap<Int, Point>()
        for (seq in sequences) for (x in seq) pointOf[x] = midpoints[x] ?: at(x)
        // the tangent at a point is parallel to the line through its neighbours on the circle and
        // shrinks with the turn there (a sharp turn stays sharp); one cubic from every crossing to
        // the next (model 2: the arc vertex in between only steers the tangents, except on a
        // digon, whose two sides must stay apart: there the curve runs through it)
        val digonArcs = (0 until arr.size).filter { k ->
            arr.faces[arr.faceOfDart[2 * k]].size == 2 || arr.faces[arr.faceOfDart[arr.alpha(2 * k)]].size == 2
        }.toSet()
        val circles = ArrayList<List<Cubic>>()
        val arcPieces = arrayOfNulls<List<Cubic>>(arr.size)
        for ((c, seq) in sequences.withIndex()) {
            val tangents = seq.indices.map { i ->
                val prev = pointOf.getValue(seq[(i - 1 + seq.size) % seq.size])
                val here = pointOf.getValue(seq[i])
                val next = pointOf.getValue(seq[(i + 1) % seq.size])
                val through = next - prev
                touchAxis[seq[i]]?.let { axis -> (if (axis.dot(through) < 0) axis * -1.0 else axis).normalized() }
                    ?: (through.normalized() * ((1 + (here - prev).normalized().dot((next - here).normalized())) / 2))
            }
            val stops = seq.indices.filter { i ->
                model == DrawModel.MODEL1 || i % 2 == 0 || arr.offset(c) + i / 2 in digonArcs
            }
            val pieces = stops.indices.map { s ->
                val i = stops[s]
                val j = stops[(s + 1) % stops.size]
                val a = pointOf.getValue(seq[i])
                val b = pointOf.getValue(seq[j])
                val h = (b - a).length / 3
                Cubic(a, a + tangents[i] * h, b - tangents[j] * h, b)
            }
            circles += pieces
            // the pieces of every arc (crossing k to the next one on its circle; none inside a model 1 digon)
            var s = 0
            for (k in arr.offset(c) until arr.offset(c + 1)) {
                val start = vertexOfCrossing(k).let { merged[it] ?: it }
                val end = vertexOfCrossing(arr.nextOnCircle(k)).let { merged[it] ?: it }
                while (seq[stops[s]] != start) s = (s + 1) % stops.size
                val arc = ArrayList<Cubic>()
                if (start != end) {
                    var t = s
                    do {
                        arc += pieces[t]
                        t = (t + 1) % stops.size
                    } while (seq[stops[t]] != end)
                }
                arcPieces[k] = arc
            }
        }
        val faces = arr.faces.mapIndexed { f, darts ->
            if (f == outerFace || f in touchings) emptyList()
            else darts.flatMap { a ->
                if (a % 2 == 0) arcPieces[a / 2]!! else arcPieces[arr.arcOfDart(a)]!!.reversed().map { it.reversed() }
            }
        }
        return Drawing(arr, model, outerFace, circles, faces, touchings, labels(circles))
    }

    /**
     * Per circle a label position on its curve and the outward direction there: the middle of a
     * piece between two points, the one farthest from the centre of the drawing that keeps a
     * distance from the labels placed before.
     */
    private fun labels(circles: List<List<Cubic>>): List<Pair<Point, Point>> {
        val all = circles.flatten().map { it.p0 }
        if (all.isEmpty()) return circles.map { Point.ZERO to Point.ZERO }
        val centre = Point(all.sumOf { it.x } / all.size, all.sumOf { it.y } / all.size)
        val separation = LABEL_SEPARATION * all.maxOf { (it - centre).length }
        val placed = ArrayList<Point>()
        return circles.map { pieces ->
            val candidates = pieces.map { it.at(0.5) }
                .sortedByDescending { (it - centre).length }
            val at = candidates.firstOrNull { p -> placed.all { (it - p).length >= separation } } ?: candidates.first()
            placed += at
            at to (at - centre).normalized()
        }
    }

    private companion object {
        /** Least distance between two labels, relative to the radius of the drawing. */
        const val LABEL_SEPARATION = 0.15
    }
}
