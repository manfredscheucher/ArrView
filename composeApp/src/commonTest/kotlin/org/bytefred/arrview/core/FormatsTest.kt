package org.bytefred.arrview.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormatsTest {
    private val kruppModel1 = ":GgXeBQAQAN"
    private val nonKruppModel1 = ":GsQLGGs_RR"

    @Test
    fun sparse6DecodesKnownStrings() {
        // the Krupp's dual graph is the cube
        val cube = Sparse6.decode(kruppModel1)
        assertEquals(8, cube.vertexCount)
        assertEquals(12, cube.edges.size)
        val degrees = IntArray(8).also { d -> for ((u, v) in cube.edges) { d[u]++; d[v]++ } }
        assertTrue(degrees.all { it == 3 })
        val two = Sparse6.decode(":CoKN")
        assertEquals(4, two.vertexCount)
        assertEquals(4, two.edges.size)
    }

    @Test
    fun sparse6RoundTripsKnownStrings() {
        for (s in listOf(kruppModel1, nonKruppModel1, ":CoKN")) {
            assertEquals(s, Sparse6.encode(Sparse6.decode(s)))
        }
        assertEquals(Sparse6.decode(kruppModel1), Sparse6.decode(">>sparse6<<$kruppModel1"))
    }

    @Test
    fun sparse6RoundTripsRandomMultigraphs() {
        val random = Random(42)
        // n = 2^k exercises nauty's padding special case
        for (n in listOf(1, 2, 3, 4, 5, 7, 8, 9, 16, 17, 31, 32, 33, 62, 63, 64, 100)) {
            repeat(30) {
                val m = random.nextInt(0, 3 * n + 1)
                val edges = List(m) {
                    val u = random.nextInt(n)
                    val v = random.nextInt(n)
                    minOf(u, v) to maxOf(u, v)
                }.sortedWith(compareBy({ it.second }, { it.first }))
                val graph = SimpleGraphData(n, edges)
                val decoded = Sparse6.decode(Sparse6.encode(graph))
                assertEquals(graph, decoded, "n=$n edges=$edges")
            }
        }
    }

    @Test
    fun sparse6RejectsGarbage() {
        assertFailsWith<IllegalArgumentException> { Sparse6.decode("GgXeBQAQAN") }
        assertFailsWith<IllegalArgumentException> { Sparse6.decode(":G g") }
    }

    @Test
    fun model1DecodesKruppAndNonKrupp() {
        val krupp = Model1.decode(kruppModel1)
        assertEquals(3, krupp.n)
        assertContentEquals(intArrayOf(0, 0, 0, 8), krupp.pVector)
        assertTrue(krupp.isGreat)
        assertTrue(krupp.isIsomorphicTo(Arrangement.krupp()))
        val nonKrupp = Model1.decode(nonKruppModel1)
        assertContentEquals(intArrayOf(0, 0, 3, 2, 3), nonKrupp.pVector)
        assertFalse(nonKrupp.isGreat)
        assertFalse(nonKrupp.isKrupp(0, 1, 2))
    }

    @Test
    fun model1RoundTrip() {
        for (s in listOf(kruppModel1, nonKruppModel1, ":CoKN")) {
            val arr = Model1.decode(s)
            assertEquals(arr.canonical, Model1.decode(Model1.encode(arr)).canonical)
        }
    }

    @Test
    fun model1RejectsNonArrangements() {
        // model 1 is only for intersecting arrangements
        assertFailsWith<IllegalArgumentException> { Model1.encode(Arrangement.parse("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")) }
        // K4 is planar but its faces are triangles
        val k4 = Sparse6.encode(SimpleGraphData(4, listOf(0 to 1, 0 to 2, 0 to 3, 1 to 2, 1 to 3, 2 to 3)))
        val error = assertFailsWith<InvalidArrangementException> { Model1.decode(k4) }
        assertTrue("quadrangulation" in error.message!!, error.message)
        // K5 is not planar
        val k5 = Sparse6.encode(SimpleGraphData(5, (0 until 5).flatMap { u -> (u + 1 until 5).map { u to it } }))
        assertTrue("not planar" in assertFailsWith<InvalidArrangementException> { Model1.decode(k5) }.message!!)
    }

    @Test
    fun model2RejectsNonArrangements() {
        val k4 = Sparse6.encode(SimpleGraphData(4, listOf(0 to 1, 0 to 2, 0 to 3, 1 to 2, 1 to 3, 2 to 3)))
        val error = assertFailsWith<InvalidArrangementException> { Model2.decode(k4) }
        assertTrue("model 2" in error.message!!, error.message)
        assertTrue("sparse6" in assertFailsWith<InvalidArrangementException> { Model2.decode("hello") }.message!!)
    }

    @Test
    fun model2RoundTrip() {
        val chain = Arrangement.parse("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")
        for (arr in listOf(Arrangement.two(), Arrangement.krupp(), chain, Model1.decode(nonKruppModel1))) {
            val s = Model2.encode(arr)
            val back = Model2.decode(s)
            assertEquals(arr.canonical, back.canonical)
        }
    }
}
