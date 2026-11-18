package org.bytefred.arrview.core

/**
 * The groups of bundled examples, in display order (as on the pseudocircles website: the database,
 * then the triangle pages). All but [SINGLETONS] are shown under "Sets".
 */
enum class ExampleGroup(val title: String) {
    CONNECTED("Connected"),
    INTERSECTING("Intersecting"),
    DIGON_FREE("Digon-free intersecting"),
    GREAT("Great pseudocircles"),
    MIN_TRIANGLES("Minimum number of triangles"),
    MAX_TRIANGLES("Maximum number of triangles"),
    FUREDI_PALASTI("Füredi–Palásti construction"),
    SINGLETONS("Singletons"),
}

/** The pseudocircles website (most of the bundled data comes from it). */
const val PSEUDOCIRCLES_WEBSITE = "https://www3.math.tu-berlin.de/diskremath/pseudocircles/"

/** A page of the pseudocircles website, titled as on its main page. */
enum class WebPage(val title: String, show: String) {
    DATABASE("The database of pseudocircles", "pseudocircles"),
    TRIANGLES("Triangular cells", "triangles"),
    NO_MORE_CIRCLES("No more circle representations", "nomorecircles"),
    ;

    val url: String = "$PSEUDOCIRCLES_WEBSITE?show=$show"
}

/** The papers the sets come from (DOIs as on the website). */
enum class Paper(val title: String, val url: String) {
    TRIANGLES(
        "Felsner, Scheucher: Arrangements of Pseudocircles: Triangles and Drawings",
        "https://doi.org/10.1007/s00454-020-00173-4",
    ),
    CIRCULARIZABILITY(
        "Felsner, Scheucher: Arrangements of Pseudocircles: On Circularizability",
        "https://doi.org/10.1007/s00454-019-00077-y",
    ),
}

/**
 * A bundled list of arrangements (often just one). Members are addressed by their index
 * `0 until size` and decoded on demand, so large lists cost nothing until a member is shown.
 *
 * Names follow `n<N>-<class>-<description>` with the class `great`, `digonfree` (intersecting and
 * digon-free), `intersecting` or `connected`. A member of a list is named `<name> #<index + 1>`.
 */
class ExampleSet internal constructor(
    val name: String,
    val group: ExampleGroup,
    /** Number of pseudocircles of every member. */
    val n: Int,
    /** One short sentence. */
    val description: String,
    /** Where the data comes from (a path below `~/github/research/pca/pseudocircles/`), null if typed in. */
    val source: String?,
    /** The members: a file below `composeApp/examples/` or inline sequences in the text format. */
    internal val data: Data,
    /** The page of the pseudocircles website with this set, if any. */
    val page: WebPage? = null,
    /** The paper the set comes from, if any. */
    val paper: Paper? = null,
) {
    internal sealed interface Data {
        /** One sparse6 string per line; `.mod2s6` files are model 2, all others model 1. */
        class File(val path: String) : Data

        /** Intersection sequences in the text format, one arrangement. */
        class Sequences(val text: String) : Data
    }

    private val lines: List<String> by lazy {
        when (data) {
            is Data.File -> {
                val chunks = GeneratedExamples.FILES[data.path] ?: error("example file ${data.path} is not bundled")
                chunks.joinToString("").lines().filter { it.isNotBlank() }
            }
            is Data.Sequences -> listOf(data.text)
        }
    }

    /** Number of members. */
    val size: Int get() = lines.size

    /** The stored text of member [index] (a sparse6 string or sequences). */
    fun text(index: Int): String = lines[index]

    /** Member [index], decoded. */
    fun arrangement(index: Int): Arrangement {
        val text = lines[index]
        return when {
            data is Data.Sequences -> Arrangement.parse(text)
            (data as Data.File).path.endsWith(".mod2s6") -> Model2.decode(text)
            else -> Model1.decode(text)
        }
    }

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

/** The catalogue of bundled examples. Data provenance: `doc/DEVELOPMENT.md`. */
object Examples {
    private const val GREAT = "computations/results/great"
    private const val ABSTRACT = "computations/results/abstract"
    private const val CONNECTED = "website/arrangements/pseudocircles/connected"
    private const val MIN_TRIANGLES = "website/arrangements/triangles/digonfree_minimal_triangles"
    private const val MAX_TRIANGLES = "website/arrangements/triangles/digonfree_maximal_triangles"
    private const val FUREDI_PALASTI = "website/arrangements/triangles/fueredi_palasti_construction"
    private const val NOMORECIRCLES = "website/arrangements/nomorecircles"

    private fun file(
        name: String,
        group: ExampleGroup,
        n: Int,
        description: String,
        source: String,
        path: String,
        page: WebPage? = null,
        paper: Paper? = null,
    ) = ExampleSet(name, group, n, description, source, ExampleSet.Data.File(path), page, paper)

    private fun sequences(name: String, description: String, text: String) =
        ExampleSet(
            name, ExampleGroup.SINGLETONS, Arrangement.parseLists(text).size, description, null,
            ExampleSet.Data.Sequences(text),
        )

    /** Triangle files: `<all|some><n>_<df|digon>_<k>_triangles`. */
    private fun triangles(group: ExampleGroup, folder: String, source: String, stem: String, ext: String): ExampleSet {
        val (scope, n, kind, k) = Regex("""(all|some)(\d+)_(df|digon)_(\d+)_triangles""").matchEntire(stem)!!.destructured
        val what = if (kind == "df") "digon-free intersecting" else "intersecting"
        val which = if (scope == "all") "All" else "Some"
        // as on the website: the minima are minimal; the maxima of n = 8, 9 "might be optimal"
        val count = when {
            group == ExampleGroup.MIN_TRIANGLES -> "the minimum of $k triangles"
            scope == "some" -> "$k triangles (maybe the maximum)"
            else -> "$k triangles"
        }
        return file(
            name = "n$n-${if (kind == "df") "digonfree" else "intersecting"}-$k-triangles",
            group = group,
            n = n.toInt(),
            description = "$which $what arrangements of $n pseudocircles with $count.",
            source = "$source/$stem.${if (ext == "mod1s6") "txt" else ext}",
            path = "$folder/$stem.$ext",
            page = WebPage.TRIANGLES,
            paper = Paper.TRIANGLES,
        )
    }

    val sets: List<ExampleSet> = buildList {
        for (n in 3..5) {
            add(
                file(
                    "n$n-connected-all", ExampleGroup.CONNECTED, n, "All connected arrangements of $n pseudocircles.",
                    "$CONNECTED/all$n.mod2s6", "connected/all$n.mod2s6", WebPage.DATABASE, Paper.CIRCULARIZABILITY,
                ),
            )
        }
        for (n in 2..5) {
            add(
                file(
                    "n$n-intersecting-all", ExampleGroup.INTERSECTING, n,
                    "All intersecting arrangements of $n pseudocircles.",
                    "$ABSTRACT/all$n.enc", "intersecting/all$n.enc", WebPage.DATABASE, Paper.CIRCULARIZABILITY,
                ),
            )
        }
        for (n in 3..6) {
            add(
                file(
                    "n$n-digonfree-all", ExampleGroup.DIGON_FREE, n,
                    "All digon-free intersecting arrangements of $n pseudocircles.",
                    "$ABSTRACT/all${n}_digonfree.enc", "digonfree/all${n}_digonfree.enc",
                    WebPage.DATABASE, Paper.CIRCULARIZABILITY,
                ),
            )
        }
        // the website has the numbers (the table of the paper), not the files
        for (n in 3..8) {
            add(
                file(
                    "n$n-great-all", ExampleGroup.GREAT, n, "All arrangements of $n great pseudocircles.",
                    "$GREAT/all$n.enc.greatcircles.enc", "great/all$n.enc.greatcircles.enc", paper = Paper.CIRCULARIZABILITY,
                ),
            )
        }
        for (
            stem in listOf(
                "all3_df_8", "all4_df_8", "all5_df_8", "all6_df_8", "all7_df_10", "all8_df_11", "all9_df_12",
                "some10_df_14", "some11_df_15", "some12_df_16", "some13_df_18", "some14_df_19",
            )
        ) {
            add(triangles(ExampleGroup.MIN_TRIANGLES, "mintriangles", MIN_TRIANGLES, "${stem}_triangles", "mod1s6"))
        }
        for (
            stem in listOf(
                "all5_df_12", "all5_digon_13", "all6_df_20", "all7_df_28", "all7_df_29", "some8_df_37", "some9_df_48",
            )
        ) {
            add(triangles(ExampleGroup.MAX_TRIANGLES, "maxtriangles", MAX_TRIANGLES, "${stem}_triangles", "enc"))
        }
        for (n in 6..42 step 6) {
            add(
                file(
                    "n$n-great-fueredi-palasti", ExampleGroup.FUREDI_PALASTI, n,
                    "The Füredi–Palásti arrangement of $n circles (p3/n² tends to 2/3).",
                    "$FUREDI_PALASTI/fueredi_palasti_$n.enc", "fueredipalasti/fueredi_palasti_$n.enc",
                    WebPage.TRIANGLES, Paper.TRIANGLES,
                ),
            )
        }
        add(sequences("n3-great-krupp", "Three great circles (the Krupp).", Arrangement.krupp().toString()))
        add(
            sequences(
                "n3-intersecting-nonkrupp", "The other intersecting arrangement of three circles (the NonKrupp).",
                "{1: [2, -2, 3, -3], 2: [-1, 1, 3, -3], 3: [-1, 1, 2, -2]}",
            ),
        )
        add(
            sequences(
                "n3-connected-chain", "Circle 2 crosses circles 1 and 3, which do not cross (the 3-Chain).",
                "{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}",
            ),
        )
        fun noMoreCircles(name: String, n: Int, description: String, file: String) = file(
            name, ExampleGroup.SINGLETONS, n, description, "$NOMORECIRCLES/$file", "named/${file.substringAfterLast('/')}",
            WebPage.NO_MORE_CIRCLES, Paper.CIRCULARIZABILITY,
        )
        add(
            noMoreCircles(
                "n5-intersecting-nonr-1", 5, "The only non-circularizable intersecting arrangement of at most 5 pseudocircles.",
                "connected/n5_nonr_number1_intersecting.mod2s6",
            ),
        )
        for (k in 2..4) {
            add(
                noMoreCircles(
                    "n5-connected-nonr-$k", 5, "A non-circularizable connected arrangement of 5 pseudocircles.",
                    "connected/n5_nonr_number$k.mod2s6",
                ),
            )
        }
        add(
            noMoreCircles(
                "n6-digonfree-nonr-8-triangles", 6,
                "A non-circularizable digon-free intersecting arrangement of 6 pseudocircles, " +
                    "the only digon-free one with 8 triangles.",
                "intersecting/digonfree/n6_nonr_number1_8triangles.mod2s6",
            ),
        )
        for (k in 2..3) {
            add(
                noMoreCircles(
                    "n6-digonfree-nonr-$k", 6, "A non-circularizable digon-free intersecting arrangement of 6 pseudocircles.",
                    "intersecting/digonfree/n6_nonr_number$k.mod2s6",
                ),
            )
        }
        add(
            file(
                "n6-intersecting-nonr-all-triangles-krupp", ExampleGroup.SINGLETONS, 6,
                "A non-circularizable intersecting arrangement of 6 pseudocircles (\"all triangles Krupp\", number 2).",
                "computations/results/n6_nonr_all_triangles_krupp_number2.enc",
                "named/n6_nonr_all_triangles_krupp_number2.enc",
            ),
        )
        add(
            file(
                "n6-intersecting-nonr-edelsbrunner-ramos", ExampleGroup.SINGLETONS, 6,
                "A non-circularizable intersecting arrangement of 6 pseudocircles (Edelsbrunner and Ramos).",
                "computations/results/n6_nonr_edelsbrunnerramos.enc", "named/n6_nonr_edelsbrunnerramos.enc",
            ),
        )
        add(
            file(
                "n9-great-nonr-pappus", ExampleGroup.SINGLETONS, 9,
                "A non-circularizable arrangement of 9 great pseudocircles, " +
                    "from the non-Pappus arrangement of 9 pseudolines.",
                "computations/results/n9_nonr_pappus.enc", "named/n9_nonr_pappus.enc",
            ),
        )
    }

    /** The set with the given [name], or null. */
    fun byName(name: String): ExampleSet? = sets.firstOrNull { it.name == name }

    /** The sets of [group], in catalogue order. */
    fun inGroup(group: ExampleGroup): List<ExampleSet> = sets.filter { it.group == group }

    /** The example shown at start (to be confirmed by Manfred, see doc/SPEC.md). */
    val start: ExampleRef get() = ExampleRef(byName("n6-digonfree-nonr-8-triangles")!!, 0)
}
