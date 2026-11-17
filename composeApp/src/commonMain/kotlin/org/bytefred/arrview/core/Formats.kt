package org.bytefred.arrview.core

/** File extensions offered when opening an arrangement file. */
val LOAD_EXTENSIONS = listOf("json", "txt", "enc", "mod1s6", "mod2s6", "s6")

/** The text forms of an arrangement (Import / Export, the Sequences and Encoding widgets). */
enum class TextFormat(val label: String) {
    SEQUENCES("Sequences"),
    MODEL1("Model 1 sparse6"),
    MODEL2("Model 2 sparse6");

    /** Model 1 exists only for intersecting arrangements. */
    fun availableFor(arr: Arrangement): Boolean = this != MODEL1 || arr.isIntersecting

    /** [arr] in this format; model 1 needs an intersecting arrangement ([availableFor]). */
    fun write(arr: Arrangement): String = when (this) {
        SEQUENCES -> sequencesText(arr)
        MODEL1 -> Model1.encode(arr)
        MODEL2 -> Model2.encode(arr)
    }

    /**
     * Reads [text] in this format.
     * @throws InvalidArrangementException with the reason if it is not a valid arrangement
     */
    fun read(text: String): Arrangement = when (this) {
        SEQUENCES -> Arrangement.parse(text)
        MODEL1 -> Model1.decode(text.trim())
        MODEL2 -> Model2.decode(text.trim())
    }
}

/**
 * The sequences in the text format with one circle per line, the keys right-aligned:
 * `{1: [2, -2],` / ` 2: [1, -1]}`.
 */
fun sequencesText(arr: Arrangement): String {
    val width = arr.n.toString().length
    return arr.lists.withIndex().joinToString(",\n ", "{", "}") { (c, list) ->
        "${(c + 1).toString().padStart(width)}: " + list.joinToString(", ", "[", "]")
    }
}

/** The formats of a saved file. */
enum class FileFormat(val extension: String) { JSON("json"), TXT("txt") }

/** File content: JSON with the name and the sequences (one list per circle), or the sequences as text. */
fun arrangementFile(name: String, arr: Arrangement, format: FileFormat): String = when (format) {
    FileFormat.JSON -> buildString {
        append("{\n  \"name\": ${jsonString(name)},\n  \"sequences\": [\n")
        append(arr.lists.joinToString(",\n") { list -> "    " + list.joinToString(", ", "[", "]") })
        append("\n  ]\n}\n")
    }
    FileFormat.TXT -> sequencesText(arr) + "\n"
}

/** An arrangement read from text, with the name a JSON file gives it. */
class NamedArrangement(val arrangement: Arrangement, val name: String?)

/**
 * Reads an arrangement from text: a JSON file (`{"name": ..., "sequences": [[...], ...]}`),
 * intersection sequences (`{1: [2, -2], ...}`) or the first sparse6 line (model 1, else model 2).
 * @throws InvalidArrangementException with the reason if none of them works
 */
fun readArrangement(text: String): NamedArrangement {
    val trimmed = text.trim()
    // a JSON object has quoted keys, the sequences have numbers
    if (trimmed.startsWith("{") && trimmed.drop(1).trimStart().startsWith("\"")) return readJson(trimmed)
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return NamedArrangement(Arrangement.parse(trimmed), null)
    val line = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        ?: throw InvalidArrangementException("the text is empty")
    val model1 = try {
        return NamedArrangement(Model1.decode(line), null)
    } catch (e: InvalidArrangementException) {
        e.message
    }
    try {
        return NamedArrangement(Model2.decode(line), null)
    } catch (e: InvalidArrangementException) {
        throw InvalidArrangementException("neither model 1 ($model1) nor model 2 (${e.message})")
    }
}

private fun readJson(text: String): NamedArrangement {
    val root = parseJson(text) as? Map<*, *> ?: throw InvalidArrangementException("JSON: expected an object")
    val name = root["name"]?.let { it as? String ?: throw InvalidArrangementException("JSON: \"name\" must be a string") }
    val sequences = root["sequences"] as? List<*>
        ?: throw InvalidArrangementException("JSON: expected \"sequences\": [[...], ...], one list per circle")
    val lists = sequences.mapIndexed { c, list ->
        (list as? List<*>)?.map { e ->
            (e as? Long)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                ?: throw InvalidArrangementException("JSON: circle ${c + 1}: $e is not an integer")
        } ?: throw InvalidArrangementException("JSON: the sequence of circle ${c + 1} is not a list")
    }
    return NamedArrangement(Arrangement.of(lists), name)
}
