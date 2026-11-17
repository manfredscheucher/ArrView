package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinhartOrtnerTest {
    private fun reason(text: String): String =
        LinhartOrtner.check(Arrangement.parseLists(text)) ?: error("$text was accepted")

    private fun assertReason(text: String, vararg parts: String) {
        val reason = reason(text)
        for (part in parts) assertTrue(part in reason, "expected '$part' in: $reason")
    }

    @Test
    fun validArrangementsPass() {
        assertNull(LinhartOrtner.check(Arrangement.krupp().lists))
        assertNull(LinhartOrtner.check(Arrangement.parseLists("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")))
    }

    @Test
    fun tooFewCircles() = assertReason("{1: []}", "at least 2")

    @Test
    fun forbiddenEntry() {
        assertReason("{1: [1, -1], 2: []}", "circle 1", "entry 1", "Def. 2.4 (i)")
        assertReason("{1: [3, -3], 2: [1, -1]}", "circle 1", "entry 3", "Def. 2.4 (i)")
        assertReason("{1: [0], 2: [1, -1]}", "entry 0", "Def. 2.4 (i)")
    }

    @Test
    fun repeatedEntry() = assertReason("{1: [2, 2], 2: [-1, -1]}", "circle 1", "entry 2 occurs twice", "Def. 2.4 (ii)")

    @Test
    fun unmatchedEntry() =
        assertReason("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2]}", "circles 2 and 3 do not match", "Def. 2.4 (iii)")

    @Test
    fun notPure() = assertReason("{1: [2], 2: [-1]}", "circle 1", "+2 without -2", "Def. 2.5 (v)")

    @Test
    fun circleWithoutCrossings() =
        assertReason("{1: [2, -2], 2: [1, -1], 3: []}", "circle 3 crosses no other circle", "Def. 2.4 (iv)")

    @Test
    fun disconnected() =
        assertReason(
            "{1: [2, -2], 2: [1, -1], 3: [4, -4], 4: [3, -3]}",
            "not connected",
            "circles {1, 2} do not cross circles {3, 4}",
            "Def. 2.4 (iv)",
        )

    @Test
    fun inconsistentScheme() {
        // the Krupp with the crossings of circle 3 in another order: circle 1 does not separate
        assertReason(
            "{1: [2, 3, -2, -3], 2: [1, -3, -1, 3], 3: [1, -2, 2, -1]}",
            "inconsistent at circle 1",
            "Def. 5.1, Thm. 5.4",
        )
        // its Euler characteristic is not 2 either (a scheme failing check 4 always fails check 5)
        val reason = LinhartOrtner.checkEuler(Arrangement.unchecked(Arrangement.parseLists(
            "{1: [2, 3, -2, -3], 2: [1, -3, -1, 3], 3: [1, -2, 2, -1]}",
        )))
        assertNotNull(reason)
        assertTrue("Thm. 4.3" in reason && "genus 2" in reason, reason)
    }

    /**
     * All 24^3 schemes of three pairwise crossing circles: exactly the Krupp and the NonKrupp (up
     * to isomorphism) are accepted, every other scheme is inconsistent (and so has Euler
     * characteristic other than 2; for three circles, every consistent scheme is spherical).
     */
    @Test
    fun allSchemesOfThreeCircles() {
        fun permutations(items: List<Int>): List<List<Int>> =
            if (items.isEmpty()) listOf(emptyList())
            else items.flatMap { x -> permutations(items - x).map { listOf(x) + it } }
        val choices = (0 until 3).map { i -> permutations((0 until 3).filter { it != i }.flatMap { listOf(it + 1, -(it + 1)) }) }
        val accepted = mutableSetOf<Arrangement>()
        val reasons = mutableMapOf<String, Int>()
        for (a in choices[0]) for (b in choices[1]) for (c in choices[2]) {
            val lists = listOf(a, b, c)
            val reason = LinhartOrtner.check(lists)
            if (reason == null) {
                accepted.add(Arrangement.of(lists).canonical)
            } else {
                val key = listOf("Def. 5.1", "Thm. 4.3").firstOrNull { it in reason } ?: reason
                reasons[key] = (reasons[key] ?: 0) + 1
            }
        }
        val nonKrupp = Arrangement.krupp().triangleFlip(0)
        assertEquals(setOf(Arrangement.krupp().canonical, nonKrupp.canonical), accepted)
        assertEquals(setOf("Def. 5.1"), reasons.keys, reasons.toString())
    }
}
