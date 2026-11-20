package org.bytefred.arrview.core

/** The pseudocircles website (most of the bundled data comes from it). */
const val PSEUDOCIRCLES_WEBSITE = "https://www3.math.tu-berlin.de/diskremath/pseudocircles/"

/** A link of a group or a set: a website page ("Website: ...") or a paper ("Paper: ..."). */
data class ExampleLink(val title: String, val url: String)

/**
 * A group of bundled examples by purpose, as listed in `composeApp/examples/index.json` (in display
 * order: the menu of the pseudocircles website, then the named arrangements of 3 pseudocircles).
 */
class ExampleGroup(
    /** Short key (e.g. `database`), also the key of the expanded group in the Examples dialog. */
    val id: String,
    val title: String,
    /** One or two short sentences. */
    val description: String,
    val links: List<ExampleLink>,
) {
    override fun toString() = id
}

/**
 * A bundled file of arrangements (often just one), PoCoView's example format: a JSON object with
 * the metadata and the arrangement's encoding (`"sequences"`, `"mod1s6"` or `"mod2s6"`, maybe
 * `"crossingColors"`), or for several the list `"arrangements"` of such objects. Members are
 * addressed by their index `0 until size` and decoded on demand.
 *
 * Names follow `n<N>-<class>-<description>` with the class `great`, `digonfree` (intersecting and
 * digon-free), `intersecting` or `connected`; they name a loaded arrangement. A member of a list is
 * named `<name> #<index + 1>`. The dialog shows the [label].
 */
class ExampleSet internal constructor(
    /** The file below `composeApp/examples/`. */
    val path: String,
    val name: String,
    val group: ExampleGroup,
    /** Number of pseudocircles of every member. */
    val n: Int,
    /** Short, understandable without the description (the tree shows it). */
    val label: String,
    /** One or two short sentences. */
    val description: String,
    /** Its website page and paper (where they are the group's too, the dialog shows them only there). */
    val links: List<ExampleLink>,
    /**
     * Where the data comes from (a path below `~/github/research/pca/pseudocircles/`, `data/` below
     * `~/github/research/pca/`), null if typed in.
     */
    val source: String?,
    /** The arrangement objects of the members. */
    private val members: List<Map<*, *>>,
) {
    private val decoded = HashMap<Int, NamedArrangement>()

    private fun member(index: Int): NamedArrangement = decoded.getOrPut(index) {
        try {
            jsonArrangement(members[index], n)
        } catch (e: InvalidArrangementException) {
            error("$path, arrangement ${index + 1}: ${e.message}")
        }
    }

    /** Number of members. */
    val size: Int get() = members.size

    /** The stored encoding of member [index]: a sparse6 string, or the sequences in the text format. */
    fun text(index: Int): String {
        val o = members[index]
        return (o[JsonEncoding.MODEL1.key] ?: o[JsonEncoding.MODEL2.key]) as? String ?: member(index).arrangement.toString()
    }

    /** Member [index], decoded. */
    fun arrangement(index: Int): Arrangement = member(index).arrangement

    /** Whether the members come with colours of their crossings. */
    val hasColors: Boolean get() = members.any { CROSSING_COLORS_KEY in it }

    /** The colour word of every crossing entry of member [index] (in the order of its sequences), or null. */
    fun colors(index: Int): List<String>? = member(index).colors

    /** The name of member [index]: the set's name, with `#<index + 1>` for lists. */
    fun memberName(index: Int): String = if (size == 1) name else "$name #${index + 1}"

    /** The position of member [index] in the list, like `#17 of 278`. */
    fun position(index: Int): String = "#${index + 1} of $size"
}

/** A member of an [ExampleSet]. */
data class ExampleRef(val set: ExampleSet, val index: Int) {
    val name: String get() = set.memberName(index)

    fun arrangement(): Arrangement = set.arrangement(index)
}

/** The catalogue of bundled examples (`composeApp/examples/index.json`). Data provenance: `doc/DEVELOPMENT.md`. */
object Examples {
    /** The groups in display order. */
    val groups: List<ExampleGroup>

    /** All sets, group after group, in the order of the index. */
    val sets: List<ExampleSet>

    init {
        val index = jsonObject(parseJson(bundled(INDEX)), INDEX)
        val groups = ArrayList<ExampleGroup>()
        val sets = ArrayList<ExampleSet>()
        for (c in index.list("categories", INDEX)) {
            val cat = jsonObject(c, INDEX)
            val group = ExampleGroup(cat.string("id", INDEX), cat.string("title", INDEX), cat.string("description", INDEX), links(cat, INDEX))
            groups += group
            for (f in cat.list("files", INDEX)) sets += parseSet(f as? String ?: error("$INDEX: file names must be strings"), group)
        }
        this.groups = groups
        this.sets = sets
    }

    /** The index file: the groups with their files. */
    const val INDEX = "index.json"

    /** One example file of [group]: the metadata and the members (decoded on demand). */
    private fun parseSet(path: String, group: ExampleGroup): ExampleSet {
        val o = jsonObject(parseJson(bundled(path)), path)
        val n = (o["n"] as? Long)?.toInt() ?: error("$path: \"n\" must be a number")
        require(o["group"] == group.title) { "$path: \"group\" is not ${group.title}" }
        val members = o["arrangements"]?.let { list ->
            (list as? List<*>)?.map { jsonObject(it, path) }?.takeIf { it.isNotEmpty() }
                ?: error("$path: \"arrangements\" must be a list of objects")
        } ?: listOf(o)
        return ExampleSet(
            path, o.string("name", path), group, n, o.string("label", path), o.string("description", path),
            links(o, path), o["source"] as String?, members,
        )
    }

    private fun bundled(path: String): String =
        GeneratedExamples.FILES[path]?.joinToString("") ?: error("example file $path is not bundled")

    private fun jsonObject(v: Any?, file: String): Map<*, *> = v as? Map<*, *> ?: error("$file: expected a JSON object")

    private fun Map<*, *>.string(key: String, file: String): String = this[key] as? String ?: error("$file: \"$key\" must be a string")

    private fun Map<*, *>.list(key: String, file: String): List<*> = this[key] as? List<*> ?: error("$file: \"$key\" must be a list")

    private fun links(o: Map<*, *>, file: String): List<ExampleLink> = o.list("links", file).map {
        val l = jsonObject(it, file)
        ExampleLink(l.string("title", file), l.string("url", file))
    }

    /** The group with the given [id]. */
    fun group(id: String): ExampleGroup = groups.first { it.id == id }

    /** The set with the given [name], or null. */
    fun byName(name: String): ExampleSet? = sets.firstOrNull { it.name == name }

    /** The sets of [group], in catalogue order. */
    fun inGroup(group: ExampleGroup): List<ExampleSet> = sets.filter { it.group == group }

    /** The example shown at start (to be confirmed by Manfred, see doc/SPEC.md). */
    val start: ExampleRef get() = ExampleRef(byName("n6-digonfree-nonr-8-triangles")!!, 0)
}
