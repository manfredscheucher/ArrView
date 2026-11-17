package org.bytefred.arrview.core

/**
 * Model 1 (the `.enc` / `.mod1s6` files): the dual graph of an intersecting arrangement as
 * sparse6. Vertices are the faces of the arrangement, edges the arcs (joining the two faces on
 * either side); the faces of the dual graph are the crossings, all quadrangles. Decoding follows
 * `color_graph` / `compute_fingerprint` of the old sage code (`program/basics.py`): the circles
 * are traced by going straight through the quadrangles.
 */
object Model1 {
    /** The dual graph (only for intersecting arrangements). */
    fun dualGraph(arr: Arrangement): SimpleGraphData {
        require(arr.isIntersecting) { "model 1 is only defined for intersecting arrangements" }
        val face = arr.faceOfDart
        val edges = (0 until arr.size).map { k -> face[2 * k] to face[arr.alpha(2 * k)] }
        return SimpleGraphData(arr.faceCount, edges)
    }

    /** Model 1 sparse6 string of an intersecting arrangement. */
    fun encode(arr: Arrangement): String = Sparse6.encode(dualGraph(arr))

    /**
     * Decodes a model 1 sparse6 string.
     * @throws InvalidArrangementException if it is not the dual graph of a valid arrangement
     */
    fun decode(sparse6: String): Arrangement {
        val graph = try {
            Sparse6.decode(sparse6)
        } catch (e: IllegalArgumentException) {
            throw InvalidArrangementException("not a sparse6 string: ${e.message}")
        }
        val n = graph.vertexCount
        val adjacency = List(n) { mutableSetOf<Int>() }
        for ((u, v) in graph.edges) {
            if (u == v) throw InvalidArrangementException("model 1: the dual graph has a loop at vertex $u")
            if (!adjacency[u].add(v)) throw InvalidArrangementException("model 1: the dual graph has a multiple edge $u-$v")
            adjacency[v].add(u)
        }
        val faces = try {
            PlanarEmbedding.faces(n, adjacency)
        } catch (e: IllegalArgumentException) {
            throw InvalidArrangementException("model 1: ${e.message}")
        }
        faces.firstOrNull { it.size != 4 }?.let {
            throw InvalidArrangementException("model 1: the dual graph is not a quadrangulation (face of size ${it.size})")
        }
        return Arrangement.of(traceCircles(faces))
    }

    /**
     * The crossing sequences from the faces of the dual quadrangulation. Face `X = [f0, f1, f2,
     * f3]` is a crossing; its half-edge `h_i` is the arc crossed by the dual edge `f_i f_(i+1)`,
     * and `h_i`, `h_(i+2)` belong to the same circle.
     */
    private fun traceCircles(faces: List<List<Int>>): List<List<Int>> {
        // directed dual edge (x, y) -> (crossing, i) with face[i] = x, face[i + 1] = y
        val position = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        for ((x, face) in faces.withIndex()) {
            for (i in 0 until 4) position[face[i] to face[(i + 1) % 4]] = x to i
        }
        // circle and direction (true: leaving forward) of every half-edge
        val circleOf = Array(faces.size) { IntArray(4) { -1 } }
        val forward = Array(faces.size) { BooleanArray(4) }
        val walks = mutableListOf<List<Pair<Int, Int>>>()
        for (x0 in faces.indices) {
            for (i0 in 0 until 4) {
                if (circleOf[x0][i0] >= 0) continue
                val c = walks.size
                val walk = mutableListOf<Pair<Int, Int>>()
                var x = x0
                var i = i0
                do {
                    if (circleOf[x][i] >= 0 || circleOf[x][(i + 2) % 4] >= 0) {
                        throw InvalidArrangementException("model 1: a pseudocircle crosses itself")
                    }
                    circleOf[x][i] = c
                    forward[x][i] = true
                    circleOf[x][(i + 2) % 4] = c
                    walk.add(x to i)
                    // leave through h_i into the other crossing of this arc, enter through h_j there
                    val face = faces[x]
                    val (y, j) = position.getValue(face[(i + 1) % 4] to face[i])
                    x = y
                    i = (j + 2) % 4
                } while (x != x0 || i != i0)
                walks.add(walk)
            }
        }
        return walks.map { walk ->
            walk.map { (x, i) ->
                val d = circleOf[x][(i + 1) % 4]
                // ccw order c_fwd, d_fwd, c_bwd, d_bwd gives sign +1
                if (forward[x][(i + 1) % 4]) d + 1 else -(d + 1)
            }
        }
    }
}

/**
 * Model 2 (the `.mod2s6` files): the vertex-edge-face graph ([VertexEdgeFaceGraph]) as sparse6,
 * with marker leaves telling the vertex types (`model2basics.sage`). The NEW encoding (written
 * here, used by the website) hangs one leaf at every face vertex. The OLD encoding (read only,
 * used in `model2_results/`) adds hubs VV, EE, FF adjacent to all crossing, arc and face vertices,
 * marked by 1, 2 and 3 leaves. Decoding is ported from rusty-arrangements
 * (`scripts/probe/model2.rs`): the embedding is recovered by orienting the face boundaries
 * consistently, the circles are traced by going straight at every crossing.
 */
object Model2 {
    /** The vertex-edge-face graph plus one marker leaf per face vertex (NEW encoding). */
    fun graph(arr: Arrangement): SimpleGraphData {
        val vef = VertexEdgeFaceGraph(PrimalGraph(arr))
        val g = vef.graph
        val faceBase = vef.crossingCount + vef.arcCount
        val markers = (0 until vef.faceCount).map { f -> faceBase + f to g.vertexCount + f }
        return SimpleGraphData(g.vertexCount + vef.faceCount, g.edges + markers)
    }

    /** Model 2 sparse6 string (NEW encoding). */
    fun encode(arr: Arrangement): String = Sparse6.encode(graph(arr))

    /**
     * Decodes a model 2 sparse6 string in the NEW or the OLD encoding.
     * @throws InvalidArrangementException if it does not describe a valid arrangement
     */
    fun decode(sparse6: String): Arrangement {
        val graph = try {
            Sparse6.decode(sparse6)
        } catch (e: IllegalArgumentException) {
            throw InvalidArrangementException("not a sparse6 string: ${e.message}")
        }
        return try {
            Arrangement.of(sequences(graph))
        } catch (e: IllegalStateException) {
            throw InvalidArrangementException("model 2: ${e.message}")
        } catch (_: NoSuchElementException) {
            throw InvalidArrangementException("model 2: not a vertex-edge-face graph")
        }
    }

    private fun sequences(graph: SimpleGraphData): List<List<Int>> {
        val n = graph.vertexCount
        val adj = List(n) { mutableListOf<Int>() }
        for ((u, v) in graph.edges) {
            adj[u].add(v)
            adj[v].add(u)
        }
        val marker = BooleanArray(n) { adj[it].size == 1 }
        val leafHubs = (0 until n).filter { marker[it] }.map { adj[it][0] }
        val hubs = leafHubs.distinct()
        val isFace: BooleanArray
        val isArc: BooleanArray
        if (leafHubs.size == 6 && hubs.size == 3) {
            // OLD encoding: remove the hubs VV, EE, FF (with 1, 2, 3 leaves)
            fun hubWith(leaves: Int) = hubs.firstOrNull { h -> adj[h].count { marker[it] } == leaves }
                ?: error("hub with $leaves leaves missing (old encoding)")
            val ee = hubWith(2)
            val ff = hubWith(3)
            isFace = BooleanArray(n) { it in adj[ff] && !marker[it] }
            isArc = BooleanArray(n) { it in adj[ee] && !marker[it] }
            for (list in adj) list.removeAll { it in hubs || marker[it] }
        } else {
            isFace = BooleanArray(n) { v -> adj[v].any { marker[it] } }
            isArc = BooleanArray(n) { v -> !marker[v] && !isFace[v] && adj[v].any { isFace[it] } }
        }
        val arcs = (0 until n).filter { isArc[it] }
        check(arcs.isNotEmpty()) { "no arc vertices" }
        // ends[e]: the two crossings of arc e, sides[e]: its two faces
        val ends = arcs.associateWith { e -> adj[e].filter { !isFace[it] } }
        val sides = arcs.associateWith { e -> adj[e].filter { isFace[it] } }
        for (e in arcs) check(ends.getValue(e).size == 2 && sides.getValue(e).size == 2) { "arc vertex $e has wrong neighbours" }

        // a dart is (arc, tail crossing); next[d] = following dart of the face on the side
        // fixed per face by a consistent orientation
        fun other(e: Int, t: Int): Int = ends.getValue(e).let { if (it[0] == t) it[1] else it[0] }
        val next = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        val oriented = HashSet<Int>()
        val first = arcs[0]
        val todo = ArrayDeque(listOf(sides.getValue(first)[0] to (first to ends.getValue(first)[0])))
        while (todo.isNotEmpty()) {
            val (f, start) = todo.removeLast()
            if (!oriented.add(f)) continue
            var d = start
            do {
                val (e, t) = d
                val h = other(e, t)
                val g = sides.getValue(e).let { if (it[0] == f) it[1] else it[0] }
                if (g !in oriented) todo.addLast(g to (e to h))
                val e2 = adj[f].firstOrNull { a -> a != e && isArc[a] && h in ends.getValue(a) }
                    ?: error("face boundary is not a cycle")
                val d2 = e2 to h
                next[d] = d2
                d = d2
            } while (d != start)
        }
        // sigma(d) = next(alpha(d)), straight on = sigma^2
        fun sigma(d: Pair<Int, Int>): Pair<Int, Int> =
            next[d.first to other(d.first, d.second)] ?: error("embedding is incomplete")
        fun straight(d: Pair<Int, Int>) = sigma(sigma(d))
        val circleOfDart = HashMap<Pair<Int, Int>, Pair<Int, Boolean>>()
        val circles = mutableListOf<List<Pair<Int, Int>>>()
        for (e in arcs) {
            val (a, b) = ends.getValue(e)
            if ((e to a) in circleOfDart || (e to b) in circleOfDart) continue
            val c = circles.size
            val darts = mutableListOf<Pair<Int, Int>>()
            var d = e to a
            do {
                check(d !in circleOfDart) { "a pseudocircle crosses itself" }
                circleOfDart[d] = c to true
                circleOfDart[d.first to other(d.first, d.second)] = c to false
                darts.add(d)
                d = straight(d.first to other(d.first, d.second))
            } while (d.first != e)
            circles.add(darts)
        }
        return circles.mapIndexed { c, darts ->
            darts.map { d ->
                // crossing at the tail of d: the ccw successor of c_fwd is d_fwd iff sign +1
                val (dc, fwd) = circleOfDart.getValue(sigma(d))
                check(dc != c) { "a pseudocircle crosses itself" }
                if (fwd) dc + 1 else -(dc + 1)
            }
        }
    }
}
