package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Conversions and flips on all arrangements with up to 5 circles. */
class RoundTripTest {
    private fun members(name: String): List<Arrangement> =
        assertNotNull(Examples.byName(name), name).let { s -> (0 until s.size).map(s::arrangement) }

    private val intersecting = (2..5).associateWith { members("n$it-intersecting-all") }
    private val connected = (3..5).associateWith { members("n$it-connected-all") }

    @Test
    fun sequencesModel1Model2() {
        for (arr in intersecting.values.flatten()) {
            assertEquals(arr, Arrangement.parse(arr.toString()))
            val model1 = Model1.encode(arr)
            val model2 = Model2.encode(arr)
            assertEquals(arr.canonical, Model1.decode(model1).canonical)
            assertEquals(arr.canonical, Model2.decode(model2).canonical)
            // model 1 sparse6 -> model 2 sparse6 -> model 1 sparse6, and back
            val viaModel2 = Model1.encode(Model2.decode(Model2.encode(Model1.decode(model1))))
            assertEquals(arr.canonical, Model1.decode(viaModel2).canonical)
            assertEquals(arr.canonical, Model1.decode(Model1.encode(Model2.decode(model2))).canonical)
        }
        for (arr in connected.values.flatten()) {
            assertEquals(arr, Arrangement.parse(arr.toString()))
            assertEquals(arr.canonical, Model2.decode(Model2.encode(arr)).canonical)
        }
    }

    @Test
    fun canonicalFormIsInvariant() {
        for (arr in connected.getValue(4) + connected.getValue(5).take(100)) {
            val canon = arr.canonical
            assertEquals(canon, canon.canonical)
            assertEquals(canon, arr.mirrored().canonical)
            assertEquals(canon, arr.reversed(arr.n - 1).relabelled((0 until arr.n).reversed().toList()).canonical)
        }
    }

    @Test
    fun triangleFlipStaysInTheClassAndTwiceIsIdentity() {
        for ((n, list) in intersecting) {
            val all = list.map { it.canonical }.toSet()
            for (arr in list) {
                for (f in arr.faces.indices.filter { arr.faces[it].size == 3 }) {
                    val flipped = arr.triangleFlip(f)
                    assertTrue(flipped.canonical in all, "flip of an arrangement of $n circles is not in the list")
                    // the flipped triangle is a face again; flipping it gives the arrangement back
                    val back = flipped.faces.indices.filter { flipped.faces[it].size == 3 }.map(flipped::triangleFlip)
                    assertTrue(arr in back, "flip of $arr at face $f cannot be undone")
                }
            }
        }
    }

    @Test
    fun digonCollapseIsValidOrRefused() {
        var collapsed = 0
        var refused = 0
        for ((n, list) in connected) {
            val all = list.map { it.canonical }.toSet()
            for (arr in list) {
                for (f in arr.faces.indices.filter { arr.faces[it].size == 2 }) {
                    val result = arr.digonCollapse(f)
                    if (result == null) {
                        refused++
                        continue
                    }
                    collapsed++
                    assertEquals(arr.crossingCount - 2, result.crossingCount)
                    assertEquals(arr.faceCount - 2, result.faceCount)
                    assertTrue(result.canonical in all, "digon collapse of an arrangement of $n circles is not in the list")
                }
            }
        }
        assertTrue(collapsed > 0 && refused > 0)
    }
}
