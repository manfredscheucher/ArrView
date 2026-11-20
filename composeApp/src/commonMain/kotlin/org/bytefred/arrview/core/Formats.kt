package org.bytefred.arrview.core

/** File extensions offered when opening an arrangement file. */
val LOAD_EXTENSIONS = listOf("json", "txt", "enc", "mod1s6", "mod2s6", "s6")

/** The text forms of an arrangement (Import / Export, the Encoding widget). */
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

/**
 * The JSON keys of an arrangement's encoding; an arrangement object has exactly one of them:
 * `"sequences": [[...], ...]` (one list per circle), `"mod1s6"` or `"mod2s6"` (a sparse6 string).
 */
enum class JsonEncoding(val key: String) { SEQUENCES("sequences"), MODEL1("mod1s6"), MODEL2("mod2s6") }

/** JSON key of the colours of the crossings: one colour word per entry of the sequences (e.g. "R", "G", "B"). */
const val CROSSING_COLORS_KEY = "crossingColors"

/** A colour word survives every format: one word, no separators (PoCoView's colour tokens). */
fun isColorToken(s: String): Boolean = s.isNotEmpty() && !Regex("[\\s,;\"]").containsMatchIn(s)

/** Lists of numbers or strings as JSON arrays: `[[1, -2], [2, -1]]`. */
private fun jsonLists(lists: List<List<Any>>, indent: String?): String {
    fun one(list: List<Any>) = list.joinToString(", ", "[", "]") { if (it is String) jsonString(it) else it.toString() }
    return if (indent == null) lists.joinToString(", ", "[", "]") { one(it) }
    else lists.joinToString(",\n", "[\n", "\n${indent.dropLast(2)}]") { indent + one(it) }
}

/** [colors] (one per crossing entry, in the order of [arr]'s sequences) cut into one list per circle. */
private fun colorLists(arr: Arrangement, colors: List<String>): List<List<String>> =
    (0 until arr.n).map { c -> colors.subList(arr.offset(c), arr.offset(c) + arr.lengthOf(c)) }

/**
 * The encoding (and the crossing colours) of one arrangement as JSON members `"key": value`; on one
 * line, or pretty with one circle per line after [indent].
 */
private fun encodingMembers(arr: Arrangement, colors: List<String>?, indent: String?): List<String> = buildList {
    add("\"${JsonEncoding.SEQUENCES.key}\": " + jsonLists(arr.lists, indent))
    if (colors != null) add("\"$CROSSING_COLORS_KEY\": " + jsonLists(colorLists(arr, colors), indent))
}

/**
 * File content: JSON with the name, n and the sequences (one list per circle), plus the colours of the
 * crossings if given; or the sequences as text.
 */
fun arrangementFile(name: String, arr: Arrangement, format: FileFormat, colors: List<String>? = null): String = when (format) {
    FileFormat.JSON -> (listOf("\"name\": ${jsonString(name)}", "\"n\": ${arr.n}") + encodingMembers(arr, colors, "    "))
        .joinToString(",\n", "{\n", "\n}\n") { "  $it" }
    FileFormat.TXT -> sequencesText(arr) + "\n"
}

/**
 * File content of several arrangements (a set, as the example files): JSON, an object with the name,
 * n (if all have the same) and `"arrangements"`, one object per line; text, the sequences one after
 * the other, separated by an empty line. One arrangement: [arrangementFile]. [colors] gives the
 * crossing colours of member i, or null.
 */
fun arrangementsFile(
    name: String,
    arrs: List<Arrangement>,
    format: FileFormat,
    colors: (Int) -> List<String>? = { null },
): String {
    if (arrs.size == 1) return arrangementFile(name, arrs[0], format, colors(0))
    return when (format) {
        FileFormat.JSON -> buildString {
            append("{\n  \"name\": ${jsonString(name)},\n")
            arrs.map { it.n }.distinct().singleOrNull()?.let { append("  \"n\": $it,\n") }
            append("  \"arrangements\": [\n")
            append(arrs.withIndex().joinToString(",\n") { (i, arr) -> "    {" + encodingMembers(arr, colors(i), null).joinToString(", ") + "}" })
            append("\n  ]\n}\n")
        }
        FileFormat.TXT -> arrs.joinToString("\n") { arrangementFile(name, it, format) }
    }
}

/**
 * An arrangement read from text, with the name a JSON object gives it and the colours of its crossings
 * (one colour word per entry of its sequences, in their order), if the object has them.
 */
class NamedArrangement(val arrangement: Arrangement, val name: String?, val colors: List<String>? = null)

/** What a file holds: its arrangements and the name of the whole set, if a JSON set object gives one. */
class ArrangementFile(val name: String?, val arrangements: List<NamedArrangement>)

/**
 * Reads an arrangement from text: JSON (an arrangement object, see [readArrangementFile]; of a set the
 * first), intersection sequences (`{1: [2, -2], ...}`) or the first sparse6 line (model 1, else model 2).
 * @throws InvalidArrangementException with the reason if none of them works
 */
fun readArrangement(text: String): NamedArrangement {
    val trimmed = text.trim()
    if (isJson(trimmed)) return readArrangementFile(trimmed).arrangements.first()
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

/** A JSON object or a list of them: quoted keys after the brace (the sequences have numbers). */
private fun isJson(trimmed: String): Boolean =
    trimmed.startsWith("{") && trimmed.drop(1).trimStart().startsWith("\"") ||
        trimmed.startsWith("[") && trimmed.drop(1).trimStart().startsWith("{")

/** [readArrangementFile] without the set's name. */
fun readArrangements(text: String): List<NamedArrangement> = readArrangementFile(text).arrangements

/**
 * Reads all arrangements of a text (a set, PoCoView's multi-set files), format detected:
 *  - JSON: an arrangement object `{"name": ..., "n": ..., "sequences": [[...], ...]}` (or `"mod1s6"` /
 *    `"mod2s6"` instead of `"sequences"`, optionally `"crossingColors"`; other keys are ignored), a set
 *    object `{"name": ..., "arrangements": [{...}, ...]}`, one object per line, or a list of objects;
 *  - the sequences of several arrangements one after the other;
 *  - one sparse6 string per line;
 * anything else as [readArrangement]. One arrangement gives a list of one.
 * @throws InvalidArrangementException with the reason (and the number of the failing one) if one is not valid
 */
fun readArrangementFile(text: String): ArrangementFile {
    val trimmed = text.trim()
    if (isJson(trimmed)) return readJsonFile(trimmed)
    // the sequences of several arrangements: top level { ... } blocks
    if (trimmed.startsWith("{")) {
        val blocks = braceBlocks(trimmed)
        if (blocks.size > 1) return ArrangementFile(null, blocks.mapIndexed { i, b -> numbered(i, blocks.size) { NamedArrangement(Arrangement.parse(b), null) } })
    }
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) return ArrangementFile(null, lines.mapIndexed { i, line -> numbered(i, lines.size) { readArrangement(line) } })
    }
    return ArrangementFile(null, listOf(readArrangement(text)))
}

/** [read] with "arrangement i of m: " before its error. */
private fun <T> numbered(i: Int, m: Int, read: () -> T): T =
    try {
        read()
    } catch (e: InvalidArrangementException) {
        throw InvalidArrangementException("arrangement ${i + 1} of $m: ${e.message}")
    }

/** The top-level `{ ... }` blocks of [text] (text between them must be blank). */
private fun braceBlocks(text: String): List<String> {
    val blocks = ArrayList<String>()
    var depth = 0
    var start = -1
    for ((i, ch) in text.withIndex()) {
        when {
            ch == '{' -> { if (depth == 0) start = i; depth++ }
            ch == '}' -> { depth--; if (depth == 0) blocks += text.substring(start, i + 1) }
            depth == 0 && !ch.isWhitespace() -> return listOf(text)
        }
    }
    return if (depth == 0) blocks else listOf(text)
}

/** JSON: one value (an object, a set object or a list of objects), else one object per line. */
private fun readJsonFile(text: String): ArrangementFile {
    val whole = try {
        parseJson(text)
    } catch (e: InvalidArrangementException) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // the first line no complete JSON value: most likely one broken multi-line object
        if (lines.size < 2 || runCatching { parseJson(lines[0]) }.isFailure) throw e
        return ArrangementFile(null, lines.mapIndexed { i, line -> numbered(i, lines.size) { jsonArrangement(jsonObject(parseJson(line)), null) } })
    }
    if (whole is List<*>) {
        if (whole.isEmpty()) throw InvalidArrangementException("JSON: the list is empty")
        return ArrangementFile(null, whole.mapIndexed { i, item -> numbered(i, whole.size) { jsonArrangement(jsonObject(item), null) } })
    }
    val root = jsonObject(whole)
    val members = root["arrangements"] ?: return ArrangementFile(null, listOf(jsonArrangement(root, null)))
    val name = jsonName(root)
    val n = jsonN(root)
    if (members !is List<*> || members.isEmpty()) throw InvalidArrangementException("JSON: \"arrangements\" must be a list of objects")
    return ArrangementFile(name, members.mapIndexed { i, item -> numbered(i, members.size) { jsonArrangement(jsonObject(item), n) } })
}

private fun jsonObject(v: Any?): Map<*, *> = v as? Map<*, *> ?: throw InvalidArrangementException("JSON: expected an object")

private fun jsonName(o: Map<*, *>): String? =
    o["name"]?.let { it as? String ?: throw InvalidArrangementException("JSON: \"name\" must be a string") }

private fun jsonN(o: Map<*, *>): Int? =
    o["n"]?.let { (it as? Long)?.takeIf { n -> n in 0..100_000 }?.toInt() ?: throw InvalidArrangementException("JSON: \"n\" must be a number") }

/** One arrangement object: its encoding, name, n (checked, as [setN] of its set) and crossing colours. */
internal fun jsonArrangement(o: Map<*, *>, setN: Int?): NamedArrangement {
    val name = jsonName(o)
    val keys = JsonEncoding.entries.filter { it.key in o }
    val encoding = keys.singleOrNull() ?: throw InvalidArrangementException(
        "JSON: expected one of " + JsonEncoding.entries.joinToString(", ") { "\"${it.key}\"" } +
            if (keys.isEmpty()) "" else ", not several",
    )
    val value = o[encoding.key]
    val arr = when (encoding) {
        JsonEncoding.SEQUENCES -> {
            val sequences = value as? List<*>
                ?: throw InvalidArrangementException("JSON: expected \"sequences\": [[...], ...], one list per circle")
            Arrangement.of(
                sequences.mapIndexed { c, list ->
                    (list as? List<*>)?.map { e ->
                        (e as? Long)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                            ?: throw InvalidArrangementException("JSON: circle ${c + 1}: $e is not an integer")
                    } ?: throw InvalidArrangementException("JSON: the sequence of circle ${c + 1} is not a list")
                },
            )
        }
        JsonEncoding.MODEL1, JsonEncoding.MODEL2 -> {
            val s6 = value as? String ?: throw InvalidArrangementException("JSON: \"${encoding.key}\" must be a string")
            if (encoding == JsonEncoding.MODEL1) Model1.decode(s6.trim()) else Model2.decode(s6.trim())
        }
    }
    for (n in listOfNotNull(jsonN(o), setN)) {
        if (n != arr.n) throw InvalidArrangementException("JSON: \"n\" is $n, but there are ${arr.n} circles")
    }
    val colors = o[CROSSING_COLORS_KEY]?.let { v ->
        if (encoding != JsonEncoding.SEQUENCES) throw InvalidArrangementException("JSON: \"$CROSSING_COLORS_KEY\" needs \"sequences\"")
        val lists = v as? List<*>
        if (lists == null || lists.size != arr.n || lists.withIndex().any { (c, l) -> (l as? List<*>)?.size != arr.lengthOf(c) }) {
            throw InvalidArrangementException("JSON: \"$CROSSING_COLORS_KEY\" must have the shape of the sequences")
        }
        lists.flatMap { it as List<*> }.map { t ->
            (t as? String)?.takeIf(::isColorToken)
                ?: throw InvalidArrangementException("JSON: a crossing colour must be one word in quotes, not $t")
        }
    }
    return NamedArrangement(arr, name, colors)
}
