package org.bytefred.arrview.core

/** The pseudocircles website (most of the bundled data comes from it). */
const val PSEUDOCIRCLES_WEBSITE = "https://www3.math.tu-berlin.de/diskremath/pseudocircles/"

/** A link of a supergroup, a group or a set: a website page ("Website: ...") or a paper ("Paper: ..."). */
data class ExampleLink(val title: String, val url: String)

/**
 * A top-level node of the examples tree (`composeApp/examples/index.json`, `"supergroups"`), e.g.
 * Database or Non-circularizable. [id] is unique among the supergroups.
 */
class ExampleSupergroup(
    val id: String,
    val title: String,
    /** One or two short sentences, or null. */
    val description: String?,
    /** Shown below its header; its groups and sets do not repeat them. */
    val links: List<ExampleLink>,
) {
    override fun toString() = id
}

/**
 * A group of bundled examples inside its [supergroup] (in display order, as in the index). [id] is
 * unique among all groups (the loader checks it, see [Examples.group]).
 */
class ExampleGroup(
    val supergroup: ExampleSupergroup,
    val id: String,
    val title: String,
    /** One or two short sentences, or null. */
    val description: String?,
    /** Its own links (none repeats a link of the supergroup). */
    val links: List<ExampleLink>,
) {
    /** The links shown above its sets, together with the supergroup's. */
    val allLinks: List<ExampleLink> get() = (supergroup.links + links).distinct()

    override fun toString() = "${supergroup.id}/$id"
}

/**
 * A bundled file of arrangements (often just one), PoCoView's example format: a JSON object with
 * the metadata and the arrangement's encoding (`"sequences"`, `"mod1s6"` or `"mod2s6"`, maybe
 * `"crossingColors"`), or for several the list `"arrangements"` of such objects. Members are
 * addressed by their index `0 until size` and decoded on demand.
 *
 * Names follow `n<N>-<class>-<description>` with the class `great`, `digonfree` (intersecting and
 * digon-free), `intersecting` or `connected`; they name a loaded arrangement. A member of a list is
 * named `<name> #<index + 1>`. The dialog shows the [label]. A file listed in several groups gives one
 * [ExampleSet] per listing (with that [group]), so a selection belongs to one leaf.
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
    /** Its website page and paper (where they are the group's or supergroup's too, the dialog shows them only there). */
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

/**
 * The catalogue of bundled examples (`composeApp/examples/index.json`): supergroups, their groups, the
 * sets of each group. Data provenance and the schema: `doc/DEVELOPMENT.md`.
 */
object Examples {
    /** The supergroups in display order. */
    val supergroups: List<ExampleSupergroup>

    /** All groups, supergroup after supergroup, in display order. */
    val groups: List<ExampleGroup>

    /** Every listing of a file: the sets of every group, in the order of the index. */
    private val listings: List<ExampleSet>

    /** All sets, one per file (the first listing of a file listed twice), in the order of the index. */
    val sets: List<ExampleSet>

    init {
        val index = jsonObject(parseJson(bundled(INDEX)), INDEX)
        val supergroups = ArrayList<ExampleSupergroup>()
        val groups = ArrayList<ExampleGroup>()
        val files = ArrayList<Pair<ExampleGroup, String>>()
        for (s in index.list("supergroups", INDEX)) {
            val so = jsonObject(s, INDEX)
            val sg = ExampleSupergroup(so.string("id", INDEX), so.string("title", INDEX), so.optString("description", INDEX), links(so, INDEX))
            require(supergroups.none { it.id == sg.id }) { "$INDEX: supergroup id '${sg.id}' twice" }
            supergroups += sg
            for (g in so.list("groups", INDEX)) {
                val go = jsonObject(g, INDEX)
                val group = ExampleGroup(sg, go.string("id", INDEX), go.string("title", INDEX), go.optString("description", INDEX), links(go, INDEX))
                groups += group
                val paths = go.list("files", INDEX).map { it as? String ?: error("$INDEX: file names must be strings") }
                require(paths.size == paths.toSet().size) { "$INDEX: a file twice in '$group'" }
                for (p in paths) files += group to p
            }
        }
        checkGroupIds(groups)
        // a file listed twice is parsed once; its "group" names one of the groups or supergroups listing it
        val parsed = HashMap<String, Map<*, *>>()
        val listings = files.map { (group, path) ->
            val o = parsed.getOrPut(path) { jsonObject(parseJson(bundled(path)), path) }
            val titles = files.filter { it.second == path }.flatMap { listOf(it.first.title, it.first.supergroup.title) }
            require(o["group"] in titles) { "$path: \"group\" is none of $titles" }
            parseSet(path, o, group)
        }
        this.supergroups = supergroups
        this.groups = groups
        this.listings = listings
        this.sets = listings.distinctBy { it.path }
    }

    /** The index file: the supergroups with their groups and files. */
    const val INDEX = "index.json"

    /** One listing of an example file ([o], parsed) in [group]: the metadata and the members (decoded on demand). */
    private fun parseSet(path: String, o: Map<*, *>, group: ExampleGroup): ExampleSet {
        val n = (o["n"] as? Long)?.toInt() ?: error("$path: \"n\" must be a number")
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

    private fun Map<*, *>.optString(key: String, file: String): String? =
        if (this[key] == null) null else this[key] as? String ?: error("$file: \"$key\" must be a string")

    private fun Map<*, *>.list(key: String, file: String): List<*> = this[key] as? List<*> ?: error("$file: \"$key\" must be a list")

    /** The optional "links" of [o]. */
    private fun links(o: Map<*, *>, file: String): List<ExampleLink> = (o["links"]?.let { o.list("links", file) } ?: emptyList<Any?>()).map {
        val l = jsonObject(it, file)
        ExampleLink(l.string("title", file), l.string("url", file))
    }

    /** The supergroup with the given [id]. */
    fun supergroup(id: String): ExampleSupergroup = supergroups.first { it.id == id }

    /** Group ids are unique among all groups, not only within a supergroup ([group] looks a group up by its id alone). */
    internal fun checkGroupIds(groups: List<ExampleGroup>) {
        val twice = groups.groupBy { it.id }.values.firstOrNull { it.size > 1 } ?: return
        error("$INDEX: group id '${twice[0].id}' twice (in ${twice.joinToString { "'${it.supergroup.id}'" }})")
    }

    /** The group with the given [id] (group ids are unique among all groups, see [checkGroupIds]). */
    fun group(id: String): ExampleGroup = groups.single { it.id == id }

    /** The groups of [supergroup], in display order. */
    fun groupsIn(supergroup: ExampleSupergroup): List<ExampleGroup> = groups.filter { it.supergroup == supergroup }

    /** The set with the given [name] (its first listing), or null. */
    fun byName(name: String): ExampleSet? = sets.firstOrNull { it.name == name }

    /** The sets of [group], in catalogue order. */
    fun inGroup(group: ExampleGroup): List<ExampleSet> = listings.filter { it.group == group }

    /** The example shown at start (to be confirmed by Manfred, see doc/SPEC.md). */
    val start: ExampleRef get() = ExampleRef(byName("n6-digonfree-nonr-8-triangles")!!, 0)
}
