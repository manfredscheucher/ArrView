package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationsTest {
    private val four = Arrangement.parse(
        "{1: [2, -2, -4, 3, -3, 4], 2: [1, -4, 4, -1, 3, -3], 3: [1, -1, 4, 2, -2, -4], 4: [1, 2, -2, -1, 3, -3]}",
    )
    private val chain = Arrangement.parse("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")

    @Test
    fun canonicalIsInvariantUnderRelabelReverseMirror() {
        for (arr in listOf(four, chain, Arrangement.krupp())) {
            val canon = arr.canonical
            assertEquals(canon, canon.canonical)
            assertEquals(canon, arr.mirrored().canonical)
            for (c in 0 until arr.n) assertEquals(canon, arr.reversed(c).canonical)
            val perm = (0 until arr.n).reversed().toList()
            assertEquals(canon, arr.relabelled(perm).canonical)
            assertEquals(canon, arr.relabelled(perm).reversed(0).mirrored().canonical)
        }
        assertNotEquals(four.canonical, Arrangement.krupp().canonical)
    }

    @Test
    fun triangleFlipTwiceIsIdentity() {
        for (arr in listOf(four, Arrangement.krupp())) {
            for (f in arr.faces.indices.filter { arr.faces[it].size == 3 }) {
                val flipped = arr.triangleFlip(f)
                assertEquals(arr.crossingCount, flipped.crossingCount)
                val back = flipped.faces.indices
                    .filter { flipped.faces[it].size == 3 }
                    .map(flipped::triangleFlip)
                assertTrue(arr in back, "flip of face $f cannot be undone")
            }
        }
    }

    @Test
    fun kruppTriangleFlipGivesNonKrupp() {
        val krupp = Arrangement.krupp()
        val flipped = krupp.triangleFlip(0)
        assertContentEquals(intArrayOf(0, 0, 3, 2, 3), flipped.pVector)
    }

    @Test
    fun digonCollapse() {
        // two circles: collapsing would disconnect them
        val two = Arrangement.two()
        for (f in two.faces.indices) assertNull(two.digonCollapse(f))
        // a chain 1-2-3: collapsing a digon of 1 and 2 disconnects circle 1
        for (f in chain.faces.indices.filter { chain.faces[it].size == 2 }) assertNull(chain.digonCollapse(f))
        // the non-Krupp of three circles: a digon collapse leaves a chain
        val nonKrupp = Arrangement.krupp().triangleFlip(0)
        val digons = nonKrupp.faces.indices.filter { nonKrupp.faces[it].size == 2 }
        assertEquals(3, digons.size)
        for (f in digons) {
            val collapsed = nonKrupp.digonCollapse(f)!!
            assertEquals(nonKrupp.crossingCount - 2, collapsed.crossingCount)
            assertEquals(nonKrupp.faceCount - 2, collapsed.faceCount)
            assertTrue(collapsed.isIsomorphicTo(chain))
        }
    }

    @Test
    fun kruppTest() {
        val krupp = Arrangement.krupp()
        for (a in 0 until 3) {
            val (b, c) = (0 until 3).filter { it != a }
            assertTrue(krupp.isKrupp(a, b, c))
            assertTrue(!krupp.triangleFlip(0).isKrupp(a, b, c))
        }
    }

    @Test
    fun subArrangement() {
        val sub = four.subArrangement(listOf(0, 1, 2))
        assertEquals(3, sub.n)
        assertTrue(sub.isIntersecting)
    }
}
