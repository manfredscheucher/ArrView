package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The colour-palette generators (ported from hex610 + the generalised rainbow3). Each takes (count, index)
 * and divides the interval by count. Invariants every scheme must hold: full alpha, in ARGB range,
 * deterministic, neighbouring indices distinct, and — the user's hard rule — NEVER pure black.
 */
class ColorPaletteTest {

    private val counts = listOf(1, 2, 3, 4, 7, 8, 16, 40)

    @Test
    fun allSchemesFullyOpaqueAndInRange() {
        for (p in ColorPalette.entries) for (n in counts) for (i in 0 until n) {
            val c = p.color(n, i)
            assertEquals(0xFF, ((c ushr 24) and 0xFF).toInt(), "$p[$i/$n] must be opaque")
            assertTrue(c in 0xFF000000..0xFFFFFFFF, "$p[$i/$n] out of ARGB range: $c")
        }
    }

    @Test
    fun deterministic() {
        for (p in ColorPalette.entries) for (n in counts) for (i in 0 until n) {
            assertEquals(p.color(n, i), p.color(n, i), "$p[$i/$n] must be stable")
        }
    }

    @Test
    fun neverPureBlack() {
        // The user's explicit rule: real rainbows, no black tile. Check the RGB is never 0,0,0.
        for (p in ColorPalette.entries) for (n in counts) for (i in 0 until n) {
            val rgb = p.color(n, i) and 0xFFFFFF
            assertNotEquals(0x000000L, rgb, "$p[$i/$n] must not be pure black")
        }
    }

    @Test
    fun neighboursDifferForRainbowSchemes() {
        val rainbows = listOf(
            ColorPalette.RAINBOW, ColorPalette.RAINBOW2,
            ColorPalette.RAINBOW3_2, ColorPalette.RAINBOW3_K,
        )
        for (p in rainbows) for (n in listOf(4, 8, 16, 40)) {
            for (i in 0 until n - 1) {
                assertNotEquals(p.color(n, i), p.color(n, i + 1), "$p neighbours $i/$i+1 of $n must differ")
            }
        }
    }

    @Test
    fun rainbow3TwoGroupsAlternateLightnessBands() {
        // k=2: even indices land in the LOW lightness band, odd indices in the HIGH band. So for any
        // adjacent pair the perceived lightness must jump across the mid-line (strong contrast).
        val n = 8
        val mid = (ColorPalette.DEFAULT_LMIN + ColorPalette.DEFAULT_LMAX) / 2.0
        for (i in 0 until n) {
            val l = lightnessOf(ColorPalette.RAINBOW3_2.color(n, i))
            if (i % 2 == 0) assertTrue(l <= mid + 0.01, "even index $i should be in low band (l=$l, mid=$mid)")
            else assertTrue(l >= mid - 0.01, "odd index $i should be in high band (l=$l, mid=$mid)")
        }
    }

    @Test
    fun rainbow2LightnessFallsMonotonically() {
        // rainbow2 goes bright→dark across the range (the user's spec).
        val n = 10
        var prev = 2.0
        for (i in 0 until n) {
            val l = lightnessOf(ColorPalette.RAINBOW2.color(n, i))
            assertTrue(l <= prev + 0.001, "rainbow2 lightness should not increase at $i (was $prev, now $l)")
            prev = l
        }
    }

    @Test
    fun fromIdRoundTripsAndFallsBack() {
        for (p in ColorPalette.entries) assertEquals(p, ColorPalette.fromId(p.id))
        assertEquals(ColorPalette.DEFAULT, ColorPalette.fromId("bogus"))
        assertEquals(ColorPalette.DEFAULT, ColorPalette.fromId(null))
    }

    /** Perceived lightness proxy: (max+min)/2 of the normalised RGB channels (the HSL L the scheme used). */
    private fun lightnessOf(argb: Long): Double {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return (maxOf(r, g, b) + minOf(r, g, b)) / 2.0
    }
}
