package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The OKLCH rainbow schemes: colours equal to the reference `scripts/probe/colour_schemes_preview.py`
 * (values computed with it), and the invariants kept from blurred-memory's ColorPaletteTest.
 */
class ColorPaletteTest {
    private val counts = listOf(1, 2, 3, 4, 7, 8, 9, 16, 40, 42)

    private fun hex(c: Long) = "#" + (c and 0xFFFFFF).toString(16).padStart(6, '0')

    @Test
    fun matchesTheReferenceScript() {
        // (n, i) -> rainbow_ok, rainbow2_ok, rainbow3 with k = 2, rainbow3k_ok of the script
        val reference = mapOf(
            (1 to 0) to listOf("#ce514d", "#f97770", "#90101a", "#90101a"),
            (6 to 0) to listOf("#ce514d", "#f97770", "#90101a", "#90101a"),
            (6 to 3) to listOf("#0099ad", "#00869a", "#00a7bb", "#008094"),
            (9 to 4) to listOf("#009d8d", "#009384", "#007c6e", "#009384"),
            (16 to 15) to listOf("#ca4f78", "#8c0e45", "#f4759c", "#f4759c"),
            (42 to 17) to listOf("#009d73", "#009c71", "#00a77b", "#009269"),
        )
        val schemes = listOf(ColorPalette.RAINBOW, ColorPalette.RAINBOW2, ColorPalette.RAINBOW3_2, ColorPalette.RAINBOW3_K)
        for ((ni, expected) in reference) {
            val (n, i) = ni
            assertEquals(expected, schemes.map { hex(it.color(n, i)) }, "n = $n, i = $i")
        }
    }

    @Test
    fun conversion() {
        // white and black in OKLCH
        assertEquals(0xFFFFFFFFL, oklchToArgb(1.0, 0.0, 0.0))
        assertEquals(0xFF000000L, oklchToArgb(0.0, 0.0, 0.0))
    }

    @Test
    fun allSchemesFullyOpaqueDeterministicAndNeverBlack() {
        for (p in ColorPalette.entries) for (n in counts) for (i in 0 until n) {
            val c = p.color(n, i)
            assertEquals(0xFF, ((c ushr 24) and 0xFF).toInt(), "$p[$i/$n] must be opaque")
            assertEquals(c, p.color(n, i))
            assertNotEquals(0L, c and 0xFFFFFF, "$p[$i/$n] must not be black")
        }
    }

    @Test
    fun neighboursDiffer() {
        for (p in ColorPalette.entries) for (n in listOf(4, 8, 16, 40)) for (i in 0 until n - 1) {
            assertNotEquals(p.color(n, i), p.color(n, i + 1), "$p neighbours $i/${i + 1} of $n")
        }
    }

    @Test
    fun lightnessStructure() {
        for (n in counts) for (i in 0 until n) {
            assertEquals(ColorPalette.L_RAINBOW, ColorPalette.RAINBOW.lightness(n, i))
            for (p in ColorPalette.entries) assertTrue(p.lightness(n, i) in ColorPalette.LMIN..ColorPalette.LMAX)
            // rainbow2 falls from LMAX to LMIN
            if (i > 0) assertTrue(ColorPalette.RAINBOW2.lightness(n, i) < ColorPalette.RAINBOW2.lightness(n, i - 1))
        }
        assertEquals(ColorPalette.LMAX, ColorPalette.RAINBOW2.lightness(9, 0))
        assertEquals(ColorPalette.LMIN, ColorPalette.RAINBOW2.lightness(9, 8), 1e-12)
        // rainbow3-2: even indices in the lower band, odd ones in the upper band
        val mid = (ColorPalette.LMIN + ColorPalette.LMAX) / 2
        for (i in 0 until 8) {
            val l = ColorPalette.RAINBOW3_2.lightness(8, i)
            if (i % 2 == 0) assertTrue(l <= mid + 1e-12, "$i: $l") else assertTrue(l >= mid - 1e-12, "$i: $l")
        }
    }
}
