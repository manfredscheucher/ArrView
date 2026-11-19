package org.bytefred.arrview.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Settings of the iterated Tutte embedding. */
data class LayoutSettings(
    /** At most this many Tutte embeddings are computed (Manfred: 50; the sage programs: 100). */
    val maxIterations: Int = 50,
    /** The iteration stops when the normalized weight vector changes by less than this. */
    val eps: Double = 1e-8,
)

/**
 * A plane graph to embed: simple [edges] (`edges[2i]`, `edges[2i + 1]`), all faces as vertex
 * cycles, the outer one included (sage's `G2.faces()`; their areas drive the weights), and the
 * [outer] face cycle, which is pinned on a regular polygon. [areaFactor] scales the area term of
 * the weights (draw7.sage: 1, the model 2 program draw3.sage: 0.001). [multiplicity] says how often
 * an edge is listed in the weight-change vector: draw7.sage builds it over the edges of the
 * multigraph `G`, where a digon is a double edge (model 1).
 */
class TutteProblem(
    val vertexCount: Int,
    val edges: IntArray,
    val faces: List<IntArray>,
    val outer: IntArray,
    val areaFactor: Double,
    val multiplicity: IntArray = IntArray(edges.size / 2) { 1 },
) {
    val edgeCount: Int get() = edges.size / 2
}

/**
 * The iterated weighted Tutte embedding of Felsner and Scheucher ("Arrangements of Pseudocircles:
 * Triangles and Drawings", Section "Iterated Tutte Embeddings"), ported from the sage programs
 * `program_py3/draw7.sage` (model 1) and `model2_program/draw3.sage` (model 2). The outer face is
 * a regular polygon on the unit circle. Iteration 1 is the plain Tutte embedding (all weights
 * equal, `1e-6`); iteration `j > 1` sets the weight of edge `uv` to
 * `w + 0.1 (|uv|^4 - w) + areaFactor * sum over the two faces f of uv of (j A(f))^4`
 * from its previous weight `w` and the previous embedding, where `A(f)` is the area of the convex
 * hull of the face's vertices (scipy's `ConvexHull(...).volume`; the outer face counts too). It
 * stops when the normalized weight vector changes by less than [LayoutSettings.eps] (before
 * that embedding is computed, as the sage `break`) or after [LayoutSettings.maxIterations]
 * embeddings.
 *
 * This is the sage code, not the paper's formula `p(A(f1)) + p(A(f2)) + q(|uv| / j)` with
 * `p(x) = x^4`, `q(x) = x^2 / 10`: taken literally (outer polygon on the unit circle), that one
 * collapses faces to zero area (see doc/SPEC.md).
 *
 * The linear system (sage: `M.pseudoinverse() * b`) is solved with conjugate gradients, warm-started
 * from the previous embedding; `TutteSolveTest` checks that it gives the direct solution.
 *
 * One [step] computes one embedding, so a caller can stop in between (cancellation).
 */
class IteratedTutte(private val problem: TutteProblem, private val settings: LayoutSettings) {
    private val v = problem.vertexCount
    private val edges = problem.edges

    /** `x` at `2i`, `y` at `2i + 1`; the last finished embedding. */
    val positions = DoubleArray(2 * v)

    /** Number of embeddings computed so far. */
    var iteration = 0
        private set

    var done = false
        private set

    private val fixed = BooleanArray(v)
    private var weights = DoubleArray(problem.edgeCount) { INITIAL_WEIGHT }

    /** The weights of the last embedding (tests). */
    internal val currentWeights: DoubleArray get() = weights

    // the faces of every edge, as indices into problem.faces
    private val edgeFaces: List<IntArray>

    init {
        val outer = problem.outer
        val l = outer.size
        // as in draw7.sage: the corners of a regular l-gon, starting at angle pi/l + pi/2
        for ((i, u) in outer.withIndex()) {
            val angle = PI / l + PI / 2 + 2 * PI * i / l
            positions[2 * u] = cos(angle)
            positions[2 * u + 1] = sin(angle)
            fixed[u] = true
        }
        val edgeIndex = HashMap<Long, Int>()
        for (e in 0 until problem.edgeCount) edgeIndex[key(edges[2 * e], edges[2 * e + 1])] = e
        val faces = List(problem.edgeCount) { mutableListOf<Int>() }
        for ((f, cycle) in problem.faces.withIndex()) {
            for (i in cycle.indices) {
                edgeIndex[key(cycle[i], cycle[(i + 1) % cycle.size])]?.let { faces[it].add(f) }
            }
        }
        edgeFaces = faces.map { it.toIntArray() }
    }

    private fun key(a: Int, b: Int): Long = minOf(a, b).toLong() * v + maxOf(a, b)

    /** Computes the next embedding; returns true when the iteration has finished. */
    fun step(): Boolean {
        if (done) return true
        val j = iteration + 1
        if (j > 1) {
            val next = nextWeights(j)
            if (weightChange(weights, next) < settings.eps) {
                done = true
                return true
            }
            weights = next
        }
        iteration = j
        if (!solve()) done = true
        if (iteration >= settings.maxIterations) done = true
        return done
    }

    /** Runs all remaining steps. */
    fun run(): DoubleArray {
        while (!step()) Unit
        return positions
    }

    /** The weights of iteration [j] from the previous weights and the current embedding. */
    private fun nextWeights(j: Int): DoubleArray {
        val areas = DoubleArray(problem.faces.size) { f -> convexHullArea(problem.faces[f]) }
        val w = DoubleArray(problem.edgeCount) { e ->
            val a = edges[2 * e]
            val b = edges[2 * e + 1]
            val dx = positions[2 * a] - positions[2 * b]
            val dy = positions[2 * a + 1] - positions[2 * b + 1]
            // sage: mypoly(dist2(u, v)) with mypoly(x) = x^2 and dist2 the squared length
            val dist2 = dx * dx + dy * dy
            var sum = weights[e] + SMOOTHING * (dist2 * dist2 - weights[e])
            for (f in edgeFaces[e]) sum += problem.areaFactor * fourth(j * areas[f])
            sum
        }
        return w
    }

    /** Area of the convex hull of the vertices of [cycle] (0 if they are collinear, where scipy raises and sage skips the face). */
    private fun convexHullArea(cycle: IntArray): Double =
        convexHullArea(cycle.map { Point(positions[2 * it], positions[2 * it + 1]) })

    /**
     * Solves the Tutte system (every free vertex at the weighted barycentre of its neighbours)
     * with Jacobi-preconditioned conjugate gradients, starting from the current positions.
     * Returns false (and keeps the positions) if the result is not finite.
     */
    private fun solve(): Boolean {
        val diag = DoubleArray(v)
        for (e in 0 until problem.edgeCount) {
            diag[edges[2 * e]] += weights[e]
            diag[edges[2 * e + 1]] += weights[e]
        }
        val result = positions.copyOf()
        for (coord in 0..1) {
            // b: the pull of the fixed neighbours
            val b = DoubleArray(v)
            for (e in 0 until problem.edgeCount) {
                val a = edges[2 * e]
                val c = edges[2 * e + 1]
                if (!fixed[a] && fixed[c]) b[a] += weights[e] * positions[2 * c + coord]
                if (!fixed[c] && fixed[a]) b[c] += weights[e] * positions[2 * a + coord]
            }
            val x = DoubleArray(v) { if (fixed[it]) 0.0 else positions[2 * it + coord] }
            conjugateGradients(x, b, diag)
            for (i in 0 until v) if (!fixed[i]) result[2 * i + coord] = x[i]
        }
        if (result.any { !it.isFinite() }) return false
        result.copyInto(positions)
        return true
    }

    /** `A x` for the free vertices: `diag x_i - sum of w_ij x_j` over free neighbours `j`. */
    private fun multiply(x: DoubleArray, diag: DoubleArray, out: DoubleArray) {
        for (i in 0 until v) out[i] = if (fixed[i]) 0.0 else diag[i] * x[i]
        for (e in 0 until problem.edgeCount) {
            val a = edges[2 * e]
            val c = edges[2 * e + 1]
            if (fixed[a] || fixed[c]) continue
            out[a] -= weights[e] * x[c]
            out[c] -= weights[e] * x[a]
        }
    }

    private fun conjugateGradients(x: DoubleArray, b: DoubleArray, diag: DoubleArray) {
        val r = DoubleArray(v)
        multiply(x, diag, r)
        for (i in 0 until v) r[i] = if (fixed[i]) 0.0 else b[i] - r[i]
        val z = DoubleArray(v) { if (fixed[it] || diag[it] <= 0.0) 0.0 else r[it] / diag[it] }
        val p = z.copyOf()
        val ap = DoubleArray(v)
        var rz = dot(r, z)
        val bNorm = sqrt(dot(b, b)).coerceAtLeast(1e-300)
        val maxSteps = 4 * v + 100
        for (step in 0 until maxSteps) {
            if (sqrt(dot(r, r)) <= CG_TOLERANCE * bNorm) return
            multiply(p, diag, ap)
            val pap = dot(p, ap)
            if (pap <= 0.0 || !pap.isFinite()) return
            val alpha = rz / pap
            for (i in 0 until v) {
                x[i] += alpha * p[i]
                r[i] -= alpha * ap[i]
            }
            for (i in 0 until v) z[i] = if (fixed[i] || diag[i] <= 0.0) 0.0 else r[i] / diag[i]
            val rzNext = dot(r, z)
            val beta = rzNext / rz
            rz = rzNext
            for (i in 0 until v) p[i] = z[i] + beta * p[i]
        }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private companion object {
        const val CG_TOLERANCE = 1e-13

        /** Weights of the first (plain) embedding. */
        const val INITIAL_WEIGHT = 1e-6

        /** How fast a weight follows `|uv|^4` (sage's `eps = 0.1`). */
        const val SMOOTHING = 0.1

        fun fourth(x: Double) = x * x * x * x
    }

    /** `|w0 / |w0| - w1 / |w1||` over the edges with their [TutteProblem.multiplicity], as in draw7.sage. */
    private fun weightChange(w0: DoubleArray, w1: DoubleArray): Double {
        val m = problem.multiplicity
        var n0 = 0.0
        var n1 = 0.0
        for (i in w0.indices) {
            n0 += m[i] * w0[i] * w0[i]
            n1 += m[i] * w1[i] * w1[i]
        }
        n0 = sqrt(n0)
        n1 = sqrt(n1)
        var s = 0.0
        for (i in w0.indices) {
            val d = w0[i] / n0 - w1[i] / n1
            s += m[i] * d * d
        }
        return sqrt(s)
    }
}

/** Area of the convex hull of [points] (Andrew's monotone chain); 0 for fewer than 3 points or collinear ones. */
fun convexHullArea(points: List<Point>): Double {
    val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (pts.size < 3) return 0.0
    fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val hull = ArrayList<Point>()
    for (pass in 0..1) {
        val start = hull.size
        for (p in if (pass == 0) pts else pts.asReversed()) {
            while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.size - 1)
            hull.add(p)
        }
        hull.removeAt(hull.size - 1)
    }
    var s = 0.0
    for (i in hull.indices) {
        val a = hull[i]
        val b = hull[(i + 1) % hull.size]
        s += a.x * b.y - b.x * a.y
    }
    return abs(s) / 2
}
