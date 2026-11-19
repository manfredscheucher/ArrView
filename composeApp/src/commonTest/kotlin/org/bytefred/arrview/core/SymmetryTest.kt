package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymmetryTest {
    @Test
    fun kruppHasTheSymmetriesOfTheOctahedron() {
        val krupp = Arrangement.krupp()
        assertEquals(48, krupp.automorphisms.size)
        for (phi in krupp.automorphisms) {
            assertEquals(phi.size, phi.toSet().size, "a permutation")
            for (a in phi.indices) assertEquals(phi[krupp.alpha(a)], krupp.alpha(phi[a]))
        }
    }

    @Test
    fun twoCirclesHaveTheSymmetriesOfTheSquare() {
        // 4 darts per crossing, 2 crossings, mirror images: 16
        assertEquals(16, Arrangement.two().automorphisms.size)
    }

    @Test
    fun theDefaultOuterFaceHasTheMostSymmetries() {
        for (set in Examples.sets.filter { it.n <= 6 }) {
            val arr = set.arrangement(0)
            for (model in DrawModel.entries) {
                if (model == DrawModel.MODEL1 && !arr.hasModel1) continue
                val f = arr.defaultOuterFace(model)
                assertTrue(arr.canBeOuter(f, model), set.name)
                val sym = arr.outerFaceSymmetries(model)
                assertEquals(sym.values.max(), sym.getValue(f), set.name)
                // the first of the most symmetric ones
                assertEquals(sym.keys.first { sym.getValue(it) == sym.values.max() }, f, set.name)
            }
        }
    }

    @Test
    fun kruppOuterFaceIsATriangleWithItsSixSymmetries() {
        // model 1: G2 is the octahedron; a triangle face is fixed by 6 automorphisms (all faces alike)
        val krupp = Arrangement.krupp()
        val sym = krupp.outerFaceSymmetries(DrawModel.MODEL1)
        assertEquals(8, sym.size)
        assertTrue(sym.values.all { it == 6 }, "$sym")
    }

    /**
     * The map automorphism counts equal sage's graph automorphism group orders (draw7.sage: `G2`
     * with a pendant edge at every vertex of the face; draw3.sage: the vertex-edge-face graph
     * without the face's vertex), counted here by a plain search over vertex maps.
     */
    @Test
    fun symmetriesAreTheGraphAutomorphismCounts() {
        val sets = listOf("n3-intersecting-all", "n4-intersecting-all", "n3-connected-all", "n4-connected-all")
        val arrangements = sets.flatMap { name -> Examples.byName(name)!!.let { s -> (0 until s.size).map(s::arrangement) } } +
            listOf("n5-connected-all", "n5-intersecting-all").flatMap { name -> Examples.byName(name)!!.let { s -> (0 until s.size step 25).map(s::arrangement) } } +
            Examples.start.arrangement() + Arrangement.two()
        var checked = 0
        for (arr in arrangements) {
            val primal = PrimalGraph(arr)
            if (arr.hasModel1) {
                val g2 = primal.graph.withoutEdges(arr.digonEdges())
                for ((f, sym) in arr.outerFaceSymmetries(DrawModel.MODEL1)) {
                    val adj = adjacency(g2)
                    for (v in arr.faces[f].map { primal.vertexOfCrossing[it / 2] }.toSet()) {
                        adj += mutableSetOf(v)
                        adj[v] += adj.size - 1
                    }
                    assertEquals(countAutomorphisms(adj), sym, "model 1, ${arr.lists}, face $f")
                    checked++
                }
            }
            val vef = VertexEdgeFaceGraph(primal)
            for ((f, sym) in arr.outerFaceSymmetries(DrawModel.MODEL2)) {
                val vf = vef.crossingCount + vef.arcCount + primal.graph.faceOfDart[arr.planeDartOf(arr.faces[f][0])]
                assertEquals(countAutomorphisms(adjacency(vef.graph.withoutVertex(vf))), sym, "model 2, ${arr.lists}, face $f")
                checked++
            }
        }
        assertTrue(checked > 300, "$checked faces")
    }

    private fun adjacency(g: PlaneGraph): MutableList<MutableSet<Int>> =
        MutableList(g.vertexCount) { v -> g.neighbours(v).toMutableSet() }

    /** Number of automorphisms of the simple graph [adj] (connected), by backtracking in BFS order. */
    private fun countAutomorphisms(adj: List<Set<Int>>): Int {
        val n = adj.size
        val order = ArrayList<Int>()
        val parent = IntArray(n) { -1 }
        val seen = BooleanArray(n)
        seen[0] = true
        order += 0
        var i = 0
        while (i < order.size) {
            for (u in adj[order[i]]) if (!seen[u]) { seen[u] = true; parent[u] = order[i]; order += u }
            i++
        }
        require(order.size == n) { "connected" }
        val image = IntArray(n) { -1 }
        val used = BooleanArray(n)
        fun extend(pos: Int): Int {
            if (pos == n) return 1
            val v = order[pos]
            val candidates = if (parent[v] >= 0) adj[image[parent[v]]] else (0 until n).toSet()
            var count = 0
            for (w in candidates) {
                if (used[w] || adj[w].size != adj[v].size) continue
                // edges to the vertices mapped so far must be kept, non-edges too
                val consistent = (0 until pos).all { p -> val u = order[p]; (u in adj[v]) == (image[u] in adj[w]) }
                if (!consistent) continue
                image[v] = w
                used[w] = true
                count += extend(pos + 1)
                used[w] = false
                image[v] = -1
            }
            return count
        }
        return extend(0)
    }
}
