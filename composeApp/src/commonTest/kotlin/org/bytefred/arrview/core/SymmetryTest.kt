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
    fun theDefaultOuterFaceIsALargestFace() {
        for (set in Examples.sets.filter { it.n <= 6 }) {
            val arr = set.arrangement(0)
            for (model in DrawModel.entries) {
                if (model == DrawModel.MODEL1 && !arr.hasModel1) continue
                val f = arr.defaultOuterFace(model)
                assertTrue(arr.canBeOuter(f, model), set.name)
                assertEquals(arr.faces.filter { model == DrawModel.MODEL2 || it.size >= 3 }.maxOf { it.size }, arr.faces[f].size, set.name)
            }
        }
    }
}
