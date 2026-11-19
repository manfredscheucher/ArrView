package org.bytefred.arrview.core

/**
 * The symmetries of an arrangement: the automorphisms of its map (the primal graph on the sphere,
 * mirror images included), as permutations of the darts. They keep the circles, since a circle
 * goes straight through every crossing. An automorphism is fixed by the image of one dart and
 * whether it reverses the orientation, so trying every dart as the image of dart 0 finds all of
 * them (each try fails fast or succeeds after one pass over the darts).
 */
internal object Symmetry {
    fun automorphisms(arr: Arrangement): List<IntArray> {
        val darts = arr.dartCount
        if (darts == 0) return emptyList()
        fun sigmaInverse(a: Int) = arr.sigma(arr.sigma(arr.sigma(a)))
        val result = ArrayList<IntArray>()
        for (mirror in listOf(false, true)) {
            for (target in 0 until darts) {
                tryMap(arr, target, mirror, ::sigmaInverse)?.let(result::add)
            }
        }
        return result
    }

    /** The automorphism mapping dart 0 to [target] (reversing the orientation if [mirror]), or null. */
    private fun tryMap(arr: Arrangement, target: Int, mirror: Boolean, sigmaInverse: (Int) -> Int): IntArray? {
        val darts = arr.dartCount
        val phi = IntArray(darts) { -1 }
        val used = BooleanArray(darts)
        phi[0] = target
        used[target] = true
        val stack = ArrayDeque(listOf(0))
        while (stack.isNotEmpty()) {
            val a = stack.removeLast()
            val b = phi[a]
            val pairs = arrayOf(
                arr.alpha(a) to arr.alpha(b),
                arr.sigma(a) to if (mirror) sigmaInverse(b) else arr.sigma(b),
            )
            for ((x, y) in pairs) {
                if (phi[x] == -1) {
                    if (used[y]) return null
                    phi[x] = y
                    used[y] = true
                    stack.addLast(x)
                } else if (phi[x] != y) {
                    return null
                }
            }
        }
        return phi.takeIf { it.all { d -> d >= 0 } }
    }
}

/**
 * The map automorphisms of plane graph [g] (mirror images included), as permutations of its darts:
 * `phi(reverse a) = reverse phi(a)` and `phi(next around a) = next around phi(a)` (the previous one
 * for a mirror image). Every one is fixed by the image of dart 0 and the orientation, so every dart
 * is tried as that image (a breadth-first pass, which fails early when the neighbourhoods differ).
 * For a 3-connected graph these are all its graph automorphisms (Whitney), which sage counts.
 */
fun mapAutomorphisms(g: PlaneGraph): List<IntArray> {
    val darts = g.dartCount
    if (darts == 0) return emptyList()
    val prevAround = IntArray(darts)
    for (a in 0 until darts) prevAround[g.nextAround(a)] = a
    val result = ArrayList<IntArray>()
    val phi = IntArray(darts)
    val used = BooleanArray(darts)
    val queue = IntArray(darts)
    for (mirror in listOf(false, true)) {
        for (target in 0 until darts) {
            phi.fill(-1)
            used.fill(false)
            phi[0] = target
            used[target] = true
            var head = 0
            var tail = 0
            queue[tail++] = 0
            var ok = true
            while (ok && head < tail) {
                val a = queue[head++]
                val b = phi[a]
                for (step in 0..1) {
                    val x = if (step == 0) a xor 1 else g.nextAround(a)
                    val y = if (step == 0) b xor 1 else if (mirror) prevAround[b] else g.nextAround(b)
                    if (phi[x] == -1) {
                        if (used[y]) { ok = false; break }
                        phi[x] = y
                        used[y] = true
                        queue[tail++] = x
                    } else if (phi[x] != y) { ok = false; break }
                }
            }
            if (ok && tail == darts) result += phi.copyOf()
        }
    }
    return result
}

/** [g] without the edges in [remove] (rotations kept). */
internal fun PlaneGraph.withoutEdges(remove: Set<Int>): PlaneGraph {
    val keep = (0 until edgeCount).filter { it !in remove }
    val newIndex = IntArray(edgeCount) { -1 }.also { idx -> keep.forEachIndexed { i, e -> idx[e] = i } }
    val ends = IntArray(2 * keep.size)
    for ((i, e) in keep.withIndex()) {
        val (a, b) = edge(e)
        ends[2 * i] = a
        ends[2 * i + 1] = b
    }
    val rotation = rotation.map { r -> r.filter { newIndex[it / 2] >= 0 }.map { 2 * newIndex[it / 2] + it % 2 }.toIntArray() }
    return PlaneGraph(vertexCount, ends, rotation)
}

/** [g] without vertex [v] and its edges (the vertices after it move down by one). */
internal fun PlaneGraph.withoutVertex(v: Int): PlaneGraph {
    val g = withoutEdges(rotation[v].map { it / 2 }.toSet())
    fun renumber(u: Int) = if (u > v) u - 1 else u
    val ends = IntArray(g.dartCount) { renumber(g.tail(it)) }
    return PlaneGraph(vertexCount - 1, ends, g.rotation.filterIndexed { u, _ -> u != v })
}

/** One primal edge of every digon: without them the primal graph is draw7.sage's simple graph `G2`. */
internal fun Arrangement.digonEdges(): Set<Int> =
    faces.filter { it.size == 2 }.map { planeDartOf(it[0]) / 2 }.toSet()

/** The primal graph's dart along the same arc as arrangement dart [a] (see [PrimalGraph]). */
internal fun Arrangement.planeDartOf(a: Int): Int = if (a % 2 == 0) a else 2 * previousOnCircle(a / 2) + 1

/** The vertex map of automorphism [phi] of [g]: `v -> tail(phi(first dart at v))`. */
private fun vertexMap(g: PlaneGraph, phi: IntArray): IntArray = IntArray(g.vertexCount) { v -> g.tail(phi[g.rotation[v][0]]) }

/**
 * The face drawn outside by default: the one whose drawing has the most symmetries, the first of
 * several (sage takes the first one in its own face order, which ArrView cannot reproduce).
 *
 * Model 1 (draw7.sage lines 304-327): per face of the simple primal graph `G2` (digons are no
 * faces there) the order of the automorphism group of `G2` with a pendant edge at every vertex of
 * the face, i.e. the automorphisms of `G2` mapping the face's vertex set to itself.
 *
 * Model 2 (draw3.sage lines 172-191): per face vertex `vf` the order of the automorphism group of
 * the vertex-edge-face graph without `vf`. That graph's outer face has twice the face's sides, its
 * other faces four, so for a face of 3 or more sides its automorphisms are those of the whole graph
 * that fix `vf`; for a digon they are counted on the graph without `vf` itself.
 *
 * Graph automorphisms are counted as map automorphisms ([mapAutomorphisms]), equal for 3-connected
 * graphs; `SymmetryTest` compares them with a plain graph automorphism count on the small examples.
 */
fun Arrangement.defaultOuterFace(model: DrawModel): Int {
    val sym = outerFaceSymmetries(model)
    val max = sym.values.max()
    return sym.keys.first { sym.getValue(it) == max }
}

/** The symmetry count of every face that can be outer in [model] (see [defaultOuterFace]). */
internal fun Arrangement.outerFaceSymmetries(model: DrawModel): Map<Int, Int> {
    val primal = PrimalGraph(this)
    return when (model) {
        DrawModel.MODEL1 -> {
            // G2: the primal graph with one edge of every digon dropped
            val g = primal.graph
            val g2 = g.withoutEdges(digonEdges())
            val auts = mapAutomorphisms(g2).map { vertexMap(g2, it) }
            faces.indices.filter { canBeOuter(it, model) }.associateWith { f ->
                val vertices = faces[f].map { primal.vertexOfCrossing[it / 2] }.toSet()
                auts.count { map -> vertices.all { map[it] in vertices } }
            }
        }
        DrawModel.MODEL2 -> {
            val vef = VertexEdgeFaceGraph(primal)
            val g = vef.graph
            val faceBase = vef.crossingCount + vef.arcCount
            val auts = mapAutomorphisms(g).map { vertexMap(g, it) }
            faces.indices.associateWith { f ->
                val vf = faceBase + primal.graph.faceOfDart[planeDartOf(faces[f][0])]
                if (faces[f].size == 2) mapAutomorphisms(g.withoutVertex(vf)).size else auts.count { it[vf] == vf }
            }
        }
    }
}

/** The arc (crossing `k` to the next one on its circle) that dart [a] runs along. */
fun Arrangement.arcOfDart(a: Int): Int = if (a % 2 == 0) a / 2 else previousOnCircle(a / 2)
