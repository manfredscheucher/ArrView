package org.bytefred.arrview.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Ported unchanged from blurred-memory (`shared/.../ColorPalettes.kt`, docs/11-color-palettes.md);
 * ArrView uses count = n and index = circle index.
 *
 * The colour schemes, ported from hex610's renderer and generalised. Every generator takes the SAME two
 * parameters:
 *
 *   count : how many distinct colours the caller needs
 *   index : which one to compute, 0 until count
 *
 * so the colour interval is divided by `count` and each `index` gets its slice. Lightness bounds
 * ([lmin]/[lmax]) are parameters (not hard-coded) with hex610-like defaults, so they can be tuned in one
 * place. Pure Kotlin (no Compose): colours travel as 0xAARRGGBB Longs; the UI just wraps them in a
 * Color. All schemes sweep the FULL 360° hue wheel and NEVER emit black (real rainbows).
 */
enum class ColorPalette {
    /** rainbow: hue sweeps the full wheel by index, fixed mid lightness. */
    RAINBOW,

    /** rainbow2: hue by index AND lightness falling monotonically [lmax]→[lmin] (bright→dark) across the range. */
    RAINBOW2,

    /** rainbow3-2: hue by index; lightness alternates across 2 interleaved bands (k=2). */
    RAINBOW3_2,

    /** rainbow3-k: hue by index; lightness split into k=ceil(sqrt(count)) interleaved bands. */
    RAINBOW3_K,
    ;

    /** A short, stable id for persistence (survives enum reordering). */
    val id: String get() = name

    /** The user-facing name ("rainbow", "rainbow2", "rainbow3-2", "rainbow3-k"). */
    val displayName: String get() = when (this) {
        RAINBOW -> "rainbow"
        RAINBOW2 -> "rainbow2"
        RAINBOW3_2 -> "rainbow3-2"
        RAINBOW3_K -> "rainbow3-k"
    }

    /**
     * The colour for [index] of [count], as 0xAARRGGBB (full alpha). [lmin]/[lmax] are the lightness
     * bounds the schemes spread within (0..1).
     */
    fun color(count: Int, index: Int, lmin: Double = DEFAULT_LMIN, lmax: Double = DEFAULT_LMAX): Long {
        val n = max(count, 1)
        val i = index.coerceIn(0, n - 1)
        return when (this) {
            RAINBOW -> rainbowColor(n, i, (lmin + lmax) / 2.0)
            RAINBOW2 -> rainbow2Color(n, i, lmin, lmax)
            RAINBOW3_2 -> rainbow3Color(n, i, k = 2, lmin = lmin, lmax = lmax)
            RAINBOW3_K -> rainbow3Color(n, i, k = ceil(sqrt(n.toDouble())).toInt(), lmin = lmin, lmax = lmax)
        }
    }

    companion object {
        /** Default lightness window for the schemes (hex610 used ~0.16..0.78). */
        const val DEFAULT_LMIN = 0.30
        const val DEFAULT_LMAX = 0.75

        val DEFAULT = RAINBOW2

        /** Resolve a persisted [id] back to a palette, falling back to [DEFAULT] for an unknown id. */
        fun fromId(id: String?): ColorPalette = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Fixed saturation for the rainbow schemes (hex610 used ~0.92..1.0). */
private const val SATURATION = 0.90

/** Full-wheel hue by index: 0 → red, sweeping the whole 360° as index→count. */
private fun hueFor(count: Int, index: Int): Double =
    if (count <= 1) 0.0 else index.toDouble() / count * 360.0

/** hex610 rainbow: hue by index, fixed [light]. */
private fun rainbowColor(count: Int, index: Int, light: Double): Long =
    hslToArgb(hueFor(count, index), SATURATION, light)

/** rainbow2: hue by index, lightness FALLING monotonically [lmax]→[lmin] (bright→dark) across the range. */
private fun rainbow2Color(count: Int, index: Int, lmin: Double, lmax: Double): Long {
    val frac = if (count <= 1) 0.0 else index.toDouble() / (count - 1)
    val light = lmax - (lmax - lmin) * frac
    return hslToArgb(hueFor(count, index), SATURATION, light)
}

/**
 * rainbow3, generalised over [k] lightness groups. The lightness window [lmin]..[lmax] is split into [k]
 * equal bands; the [count] colours are round-robin'd across the groups so neighbouring indices land in
 * DIFFERENT bands — a strong lightness contrast between neighbours even where their hues are close.
 *
 * Derivation (recorded in docs/11-color-palettes.md):
 *   N = ceil(count/k) * k           -> round count up to a multiple of k (some high indices may be unused)
 *   m = N / k                        -> colours per group
 *   g = index mod k                  -> which group (band) this index falls in     [0..k-1]
 *   p = index div k                  -> position within the group                  [0..m-1]
 *   light = lmin + (lmax-lmin) * (g + p/(m-1)) / k
 * so each group's p sweeps its own band bottom→top, and the k interleaved groups tile the whole window.
 * Hue stays monotonic in the raw index (full wheel), independent of the lightness grouping.
 */
private fun rainbow3Color(count: Int, index: Int, k: Int, lmin: Double, lmax: Double): Long {
    val kk = k.coerceAtLeast(1)
    val n = ceil(count.toDouble() / kk).toInt() * kk   // round up to a multiple of k
    val m = (n / kk).coerceAtLeast(1)                  // colours per group
    val g = index % kk
    val p = index / kk
    val within = if (m <= 1) 0.0 else p.toDouble() / (m - 1)   // 0..1 within the band
    val light = lmin + (lmax - lmin) * (g + within) / kk
    return hslToArgb(hueFor(count, index), SATURATION, light)
}

// ── colour math ────────────────────────────────────────────────────────────────────────────────────

private fun argb(r: Int, g: Int, b: Int): Long =
    (0xFFL shl 24) or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)

/** HSL (h in degrees, s/l in 0..1) → 0xAARRGGBB, full alpha. Mirrors hex610's hslToRgb. */
private fun hslToArgb(hue: Double, saturation: Double, lightness: Double): Long {
    val h = ((hue % 360) + 360) % 360
    val s = saturation.coerceIn(0.0, 1.0)
    val l = lightness.coerceIn(0.0, 1.0)
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60.0) % 2 - 1))
    val mm = l - c / 2
    val (r1, g1, b1) = when {
        h < 60 -> Triple(c, x, 0.0)
        h < 120 -> Triple(x, c, 0.0)
        h < 180 -> Triple(0.0, c, x)
        h < 240 -> Triple(0.0, x, c)
        h < 300 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    return argb(
        ((r1 + mm) * 255).toInt(),
        ((g1 + mm) * 255).toInt(),
        ((b1 + mm) * 255).toInt(),
    )
}
