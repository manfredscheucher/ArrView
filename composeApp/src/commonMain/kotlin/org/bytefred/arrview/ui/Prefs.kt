package org.bytefred.arrview.ui

/**
 * Small persistent settings (browser localStorage / Java preferences), as in PoCoView. Failures
 * are swallowed: a preference that cannot be read or written falls back to its default.
 */
expect fun loadPref(key: String): String?

expect fun savePref(key: String, value: String)

/** Forgets a stored setting, so its default applies again. */
expect fun removePref(key: String)

// the remembered display settings (AppState loads them at start and saves them when they change)
internal const val LAYOUT_PREF = "layout"
internal const val OPEN_WIDGETS_PREF = "openWidgets"
internal const val WIDE_WEIGHTS_PREF = "wideWeights"
internal const val STACKED_WEIGHTS_PREF = "stackedWeights"
internal const val MODEL_PREF = "model"

// stored values are checked: anything unreadable gives the default (null) or is clamped, so a
// corrupt store can never break the GUI

internal inline fun <reified E : Enum<E>> enumPref(key: String): E? =
    loadPref(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } }

/** Divider shares as "canvas=2.0,PROPERTIES=1.0" (the canvas has the key null). */
internal fun encodeWeights(weights: Map<Widget?, Float>): String =
    weights.entries.joinToString(",") { (k, v) -> "${k?.name ?: CANVAS_KEY}=$v" }

internal fun decodeWeights(s: String): Map<Widget?, Float> = buildMap {
    for (part in s.split(',')) {
        val kv = part.split('=')
        if (kv.size != 2) continue
        val key = if (kv[0] == CANVAS_KEY) null else Widget.entries.firstOrNull { it.name == kv[0] } ?: continue
        val w = kv[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: continue
        put(key, w.coerceIn(MIN_WEIGHT, MAX_WEIGHT))
    }
}

private const val CANVAS_KEY = "canvas"

// a stored share outside this range would hide a panel or squeeze the others away
private const val MIN_WEIGHT = 0.01f
private const val MAX_WEIGHT = 100f
