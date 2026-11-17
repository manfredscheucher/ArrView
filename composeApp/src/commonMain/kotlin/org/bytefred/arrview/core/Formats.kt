package org.bytefred.arrview.core

/** File extensions offered when opening an arrangement file. */
val LOAD_EXTENSIONS = listOf("txt", "enc", "mod1s6", "mod2s6", "s6")

/**
 * Reads an arrangement from text: intersection sequences (`{1: [2, -2], ...}`) or the first
 * sparse6 line (model 1, else model 2).
 * @throws InvalidArrangementException with the reason if neither works
 */
fun readArrangement(text: String): Arrangement {
    val trimmed = text.trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return Arrangement.parse(trimmed)
    val line = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        ?: throw InvalidArrangementException("the text is empty")
    val model1 = try {
        return Model1.decode(line)
    } catch (e: InvalidArrangementException) {
        e.message
    }
    try {
        return Model2.decode(line)
    } catch (e: InvalidArrangementException) {
        throw InvalidArrangementException("neither model 1 ($model1) nor model 2 (${e.message})")
    }
}
