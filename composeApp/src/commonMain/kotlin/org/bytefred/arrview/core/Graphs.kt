package org.bytefred.arrview.core

/**
 * The primal graph of an arrangement: vertices are the crossings, edges the arcs between
 * consecutive crossings of a circle (digons give double edges), with the rotation system of the
 * arrangement. Edge `k` is the arc of circle `circleOfEdge(k)` from crossing `k` to the next
 * crossing along the circle, so the edges of circle `c` in circle order are
 * `arrangement.offset(c) until arrangement.offset(c + 1)`. The faces correspond one-to-one to
 * [Arrangement.faces] (as dart orbits, possibly in another order).
 */
class PrimalGraph(val arrangement: Arrangement) {
    /** Vertex of each crossing entry (both entries of a crossing give the same vertex). */
    val vertexOfCrossing: IntArray

    /** For each vertex its two crossing entries (on the two circles through it). */
    val crossingsOfVertex: List<IntArray>

    /** The embedded graph. */
    val graph: PlaneGraph

    init {
        val arr = arrangement
        val partners = arr.partners
        vertexOfCrossing = IntArray(arr.size)
        val crossings = mutableListOf<IntArray>()
        for (k in 0 until arr.size) {
            if (k < partners[k]) {
                vertexOfCrossing[k] = crossings.size
                vertexOfCrossing[partners[k]] = crossings.size
                crossings.add(intArrayOf(k, partners[k]))
            }
        }
        crossingsOfVertex = crossings
        val ends = IntArray(2 * arr.size)
        for (k in 0 until arr.size) {
            ends[2 * k] = vertexOfCrossing[k]
            ends[2 * k + 1] = vertexOfCrossing[arr.nextOnCircle(k)]
        }
        // arrangement dart 2k (forward from k) is edge k forward; dart 2k + 1 (backward from k)
        // is the edge before k, reversed
        fun planeDart(a: Int): Int = if (a % 2 == 0) a else 2 * arr.previousOnCircle(a / 2) + 1
        val rotation = crossings.map { (k, _) ->
            val forward = 2 * k
            IntArray(4).also { r ->
                var a = forward
                for (i in 0 until 4) {
                    r[i] = planeDart(a)
                    a = arr.sigma(a)
                }
            }
        }
        graph = PlaneGraph(crossings.size, ends, rotation)
    }

    /** The circle edge [e] belongs to. */
    fun circleOfEdge(e: Int): Int = arrangement.circleOfCrossing(e)

    /** The two circles through vertex [v]. */
    fun circlesAt(v: Int): Pair<Int, Int> {
        val (k, p) = crossingsOfVertex[v]
        return arrangement.circleOfCrossing(k) to arrangement.circleOfCrossing(p)
    }
}

/**
 * The vertex-edge-face graph (the primal-dual graph of model 2): one vertex per crossing, per arc
 * and per face of the arrangement; every arc vertex is adjacent to its two crossings and its two
 * faces (no crossing-face edges). Vertices `0 until V` are the crossings (numbered as in
 * [PrimalGraph]), `V until V + E` the arcs (arc `e` = primal edge `e`), `V + E until V + E + F`
 * the faces (face `f` = `primal.graph.faces[f]`). Digons appear as real 2-gons (a face vertex of
 * degree 2).
 */
class VertexEdgeFaceGraph(val primal: PrimalGraph) {
    val crossingCount: Int = primal.graph.vertexCount
    val arcCount: Int = primal.graph.edgeCount
    val faceCount: Int = primal.graph.faces.size

    /** The embedded graph. */
    val graph: PlaneGraph

    init {
        val g = primal.graph
        val arcBase = crossingCount
        val faceBase = crossingCount + arcCount
        // per arc e four edges: 4e: tail - arc, 4e+1: head - arc, 4e+2: arc - face right of 2e,
        // 4e+3: arc - face right of 2e+1
        val ends = IntArray(8 * arcCount)
        for (e in 0 until arcCount) {
            val arc = arcBase + e
            val edges = arrayOf(
                g.tail(2 * e) to arc,
                g.head(2 * e) to arc,
                arc to faceBase + g.faceOfDart[2 * e],
                arc to faceBase + g.faceOfDart[2 * e + 1],
            )
            for ((j, edge) in edges.withIndex()) {
                ends[2 * (4 * e + j)] = edge.first
                ends[2 * (4 * e + j) + 1] = edge.second
            }
        }
        val rotation = ArrayList<IntArray>(crossingCount + arcCount + faceCount)
        for (v in 0 until crossingCount) {
            rotation.add(g.rotation[v].map { a -> 2 * (4 * (a / 2) + a % 2) }.toIntArray())
        }
        for (e in 0 until arcCount) {
            // ccw around the arc's midpoint: head, face left of 2e, tail, face right of 2e
            rotation.add(intArrayOf(2 * (4 * e + 1) + 1, 2 * (4 * e + 3), 2 * (4 * e) + 1, 2 * (4 * e + 2)))
        }
        for (face in g.faces) {
            // the face is walked clockwise, so ccw around its centre is the reverse order
            rotation.add(face.reversed().map { a -> 2 * (4 * (a / 2) + 2 + a % 2) + 1 }.toIntArray())
        }
        graph = PlaneGraph(crossingCount + arcCount + faceCount, ends, rotation)
    }

    fun isCrossing(v: Int): Boolean = v < crossingCount

    fun isArc(v: Int): Boolean = v in crossingCount until crossingCount + arcCount

    fun isFace(v: Int): Boolean = v >= crossingCount + arcCount
}
