package org.bytefred.arrview.core

/**
 * A connected graph embedded on the sphere, given by a rotation system. Multiple edges are
 * allowed (digons give double edges in the primal graph).
 *
 * Edge `e` joins [tail]`(2e)` and [head]`(2e)`. Dart `2e` runs along `e` from its first to its
 * second end, dart `2e + 1` back. [rotation]`[v]` lists the darts leaving `v` in
 * counter-clockwise order. Faces are traversed with `next(a) = ccw successor of reverse(a)` at
 * the head of `a`, so every dart belongs to the face on its right (the faces are walked
 * clockwise).
 */
class PlaneGraph(
    val vertexCount: Int,
    /** `ends[2e]`, `ends[2e + 1]`: the two ends of edge `e`. */
    private val ends: IntArray,
    /** Outgoing darts of every vertex in counter-clockwise order. */
    val rotation: List<IntArray>,
) {
    val edgeCount: Int get() = ends.size / 2

    val dartCount: Int get() = ends.size

    /** The vertex dart [a] leaves. */
    fun tail(a: Int): Int = ends[a]

    /** The vertex dart [a] enters. */
    fun head(a: Int): Int = ends[a xor 1]

    /** The two ends of edge [e]. */
    fun edge(e: Int): Pair<Int, Int> = ends[2 * e] to ends[2 * e + 1]

    /** The neighbours of [v] in counter-clockwise order (with repetitions for multiple edges). */
    fun neighbours(v: Int): List<Int> = rotation[v].map(::head)

    private val rotationIndex: IntArray by lazy {
        IntArray(dartCount).also { index -> for (r in rotation) r.forEachIndexed { i, a -> index[a] = i } }
    }

    /** Counter-clockwise successor of dart [a] around its tail. */
    fun nextAround(a: Int): Int {
        val r = rotation[tail(a)]
        return r[(rotationIndex[a] + 1) % r.size]
    }

    /** The next dart along the face to the right of dart [a]. */
    fun nextInFace(a: Int): Int = nextAround(a xor 1)

    /** Faces as dart cycles (face to the right of each dart). */
    val faces: List<IntArray> by lazy { orbits(IntArray(dartCount) { nextInFace(it) }) }

    /** `faceOfDart[a]`: index into [faces] of the face to the right of dart `a`. */
    val faceOfDart: IntArray by lazy {
        IntArray(dartCount).also { result -> faces.forEachIndexed { f, orbit -> for (a in orbit) result[a] = f } }
    }

    /** The vertices around face [f] in boundary order (the tails of its darts). */
    fun faceVertices(f: Int): List<Int> = faces[f].map(::tail)

    /** `V - E + F`; 2 for a valid embedding of a connected graph on the sphere. */
    val eulerCharacteristic: Int get() = vertexCount - edgeCount + faces.size

    /** The edge list (each edge once, as given). */
    val edges: List<Pair<Int, Int>> get() = (0 until edgeCount).map(::edge)
}
