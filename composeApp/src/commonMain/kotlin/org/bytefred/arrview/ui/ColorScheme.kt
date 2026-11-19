package org.bytefred.arrview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.arrview.core.ColorPalette

/**
 * The colours of the circles (Settings widget): Standard, ArrView's fixed list, or one of the
 * rainbow schemes rainbow and rainbow2 in OKLCH (Manfred, 2026-11-19: only these three), computed from n ([ColorPalette.color] with count = n, index = circle).
 */
enum class ColorScheme(val label: String, private val palette: ColorPalette?) {
    STANDARD("Standard", null),
    RAINBOW("rainbow", ColorPalette.RAINBOW),
    RAINBOW2("rainbow2", ColorPalette.RAINBOW2),
    ;

    /** The colour of circle [c] of [n]. */
    fun color(c: Int, n: Int): Color =
        palette?.let { Color(it.color(n, c).toInt()) } ?: STANDARD_COLORS[c % STANDARD_COLORS.size]

    companion object {
        val DEFAULT = STANDARD
    }
}

/** The Standard colours (circle i gets colour i mod size). */
private val STANDARD_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF7CB342), Color(0xFF3949AB),
    Color(0xFFFFB300), Color(0xFF546E7A),
)
