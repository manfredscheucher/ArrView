package org.bytefred.arrview.core

import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrangementTest {
    private val four =
        "{1: [2, -2, -4, 3, -3, 4], 2: [1, -4, 4, -1, 3, -3], 3: [1, -1, 4, 2, -2, -4], 4: [1, 2, -2, -1, 3, -3]}"

    @Test
    fun parseAndFormatRoundTrip() {
        val arr = Arrangement.parse(four)
        assertEquals(four, arr.toString())
        assertEquals(4, arr.n)
        assertTrue(arr.isIntersecting)
        assertEquals(arr, Arrangement.parse(arr.toString()))
    }

    @Test
    fun parseIsLenient() {
        val arr = Arrangement.parse(" {2:[1,-1],\n 1: [ 2 , -2 ] } ")
        assertEquals("{1: [2, -2], 2: [1, -1]}", arr.toString())
        assertEquals(arr, Arrangement.parse("[[2, -2], [1, -1]]"))
    }

    @Test
    fun parseErrorsAreReadable() {
        val missingBracket = assertFailsWith<InvalidArrangementException> { Arrangement.parse("{1: [2, -2], 2: [1, -1}") }
        assertTrue("expected ',' or ']'" in missingBracket.message!!, missingBracket.message)
        val badKey = assertFailsWith<InvalidArrangementException> { Arrangement.parse("{1: [2, -2], 3: [1, -1]}") }
        assertTrue("1..2" in badKey.message!!, badKey.message)
        assertFailsWith<InvalidArrangementException> { Arrangement.parse("") }
        assertFailsWith<InvalidArrangementException> { Arrangement.parse("{1: [2, x]}") }
    }

    @Test
    fun twoCirclesHaveFourDigons() {
        val arr = Arrangement.two()
        assertEquals(4, arr.faceCount)
        assertContentEquals(intArrayOf(0, 0, 4), arr.pVector)
        assertEquals(2, arr.crossingCount)
    }

    @Test
    fun kruppHasEightTriangles() {
        val arr = Arrangement.krupp()
        assertContentEquals(intArrayOf(0, 0, 0, 8), arr.pVector)
        assertTrue(arr.isGreat)
        assertTrue(arr.isDigonFree)
        assertTrue(arr.isCylindrical)
    }

    @Test
    fun chainOfThreeIsConnectedButNotIntersecting() {
        val arr = Arrangement.parse("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")
        assertFalse(arr.isIntersecting)
        assertEquals(6, arr.faceCount)
        assertEquals(1, arr.nonIntersectingPairCount)
        assertEquals(listOf(0 to 1, 1 to 2), arr.intersectionGraph())
        assertFalse(arr.isGreat)
    }

    @Test
    fun facesCoverEveryDartOnce() {
        val arr = Arrangement.parse(four)
        val seen = arr.faces.flatMap { it.toList() }.sorted()
        assertEquals((0 until arr.dartCount).toList(), seen)
        assertEquals(arr.crossingCount + 2, arr.faceCount)
        for (a in 0 until arr.dartCount) {
            assertEquals(a, arr.alpha(arr.alpha(a)))
            // sigma has order 4 at every crossing
            assertEquals(a, arr.sigma(arr.sigma(arr.sigma(arr.sigma(a)))))
        }
    }

    @Test
    fun partnersAreAnInvolution() {
        val arr = Arrangement.parse(four)
        for (k in 0 until arr.size) {
            val p = arr.partners[k]
            assertEquals(k, arr.partners[p])
            assertEquals(arr.circleOfCrossing(k), arr.otherCircle(p))
            assertEquals(-arr.entry(k).sign, arr.entry(p).sign)
        }
    }

    @Test
    fun graphsAreSpherical() {
        for (text in listOf(four, "{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}", "{1: [2, -2], 2: [1, -1]}")) {
            val arr = Arrangement.parse(text)
            val primal = PrimalGraph(arr)
            assertEquals(2, primal.graph.eulerCharacteristic)
            assertEquals(arr.faceSizes.sorted(), primal.graph.faces.map { it.size }.sorted())
            val vef = VertexEdgeFaceGraph(primal)
            assertEquals(2, vef.graph.eulerCharacteristic)
            // all faces of the vertex-edge-face graph are quadrangles crossing-arc-face-arc
            assertTrue(vef.graph.faces.all { it.size == 4 })
            for (f in vef.graph.faces.indices) {
                val vertices = vef.graph.faceVertices(f)
                assertEquals(1, vertices.count(vef::isCrossing))
                assertEquals(1, vertices.count(vef::isFace))
            }
        }
    }
}
