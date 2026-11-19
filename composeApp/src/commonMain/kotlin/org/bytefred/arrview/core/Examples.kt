package org.bytefred.arrview.core

/** The pseudocircles website (most of the bundled data comes from it). */
const val PSEUDOCIRCLES_WEBSITE = "https://www3.math.tu-berlin.de/diskremath/pseudocircles/"

/** A page of the pseudocircles website, titled as in its menu. */
enum class WebPage(val title: String, show: String) {
    INFORMATION("Some more information", "information"),
    DATABASE("The database of pseudocircles", "pseudocircles"),
    TRIANGLES("Triangular cells", "triangles"),
    NO_MORE_CIRCLES("No more circle representations", "nomorecircles"),
    COLORINGS("Triangle-saturated arrangements and colorings", "colorings"),
    DIGON_GRAPHS("(Trianglefree) digongraphs", "digongraphs"),
    ;

    val url: String = "$PSEUDOCIRCLES_WEBSITE?show=$show"
}

/** The papers the examples come from (DOIs as on the website, arXiv where there is no DOI there). */
enum class Paper(val title: String, val url: String) {
    TRIANGLES(
        "Felsner, Scheucher: Arrangements of Pseudocircles: Triangles and Drawings",
        "https://doi.org/10.1007/s00454-020-00173-4",
    ),
    CIRCULARIZABILITY(
        "Felsner, Scheucher: Arrangements of Pseudocircles: On Circularizability",
        "https://doi.org/10.1007/s00454-019-00077-y",
    ),
    COLORING(
        "Chiu, Felsner, Scheucher, Schröder, Steiner, Vogtenhuber: Coloring circle arrangements: New 4-chromatic planar graphs",
        "https://arxiv.org/abs/2205.08181",
    ),
}

/**
 * The groups of bundled examples by purpose, in display order: the menu of the pseudocircles
 * website (database, triangles, no more circles, colorings, digongraphs), then the named
 * arrangements of 3 pseudocircles. Every group has a description and at least one link.
 */
enum class ExampleGroup(val title: String, val description: String, val page: WebPage?, val paper: Paper?) {
    DATABASE(
        "Database",
        "All arrangements of n pseudocircles for small n: connected, intersecting, digon-free and great.",
        WebPage.DATABASE, Paper.CIRCULARIZABILITY,
    ),
    MIN_TRIANGLES(
        "Minimum number of triangles",
        "Digon-free arrangements with the fewest triangles.",
        WebPage.TRIANGLES, Paper.TRIANGLES,
    ),
    MAX_TRIANGLES(
        "Maximum number of triangles",
        "Arrangements with the most triangles; for n = 8, 9 maybe not the maximum.",
        WebPage.TRIANGLES, Paper.TRIANGLES,
    ),
    FUREDI_PALASTI(
        "Füredi–Palásti construction",
        "Circle arrangements whose share of triangles tends to 2/3 of n².",
        WebPage.TRIANGLES, Paper.TRIANGLES,
    ),
    NON_CIRCULARIZABLE(
        "Non-circularizable arrangements",
        "Arrangements of pseudocircles that cannot be drawn with circles.",
        WebPage.NO_MORE_CIRCLES, Paper.CIRCULARIZABILITY,
    ),
    COLORINGS(
        "Triangle-saturated arrangements and colorings",
        "Every crossing is on a triangle; the crossings are shown in their 3 colours. " +
            "Koester's arrangement needs 4.",
        WebPage.COLORINGS, Paper.COLORING,
    ),
    DIGON_GRAPHS(
        "Digongraphs",
        "Intersecting arrangements with all types of digongraphs (the circles, joined where they form a digon).",
        WebPage.DIGON_GRAPHS, null,
    ),
    THREE(
        "Krupp, NonKrupp, 3-Chain",
        "The named arrangements of 3 pseudocircles.",
        WebPage.INFORMATION, null,
    ),
}

/**
 * A bundled list of arrangements (often just one). Members are addressed by their index
 * `0 until size` and decoded on demand, so large lists cost nothing until a member is shown.
 *
 * Names follow `n<N>-<class>-<description>` with the class `great`, `digonfree` (intersecting and
 * digon-free), `intersecting` or `connected`; they name a loaded arrangement. A member of a list is
 * named `<name> #<index + 1>`. The dialog shows the [label].
 */
class ExampleSet internal constructor(
    val name: String,
    val group: ExampleGroup,
    /** Number of pseudocircles of every member. */
    val n: Int,
    /** Short, understandable without the description (the tree shows it). */
    val label: String,
    /** One or two short sentences. */
    val description: String,
    /** Where the data comes from (a path below `~/github/research/pca/pseudocircles/`), null if typed in. */
    val source: String?,
    /** The members: a file below `composeApp/examples/` or inline sequences in the text format. */
    internal val data: Data,
    /** The page of the pseudocircles website with this set, if any. */
    val page: WebPage? = group.page,
    /** The paper the set comes from, if any. */
    val paper: Paper? = group.paper,
    /**
     * A file below `composeApp/examples/` with the colour class (0, 1, 2) of every crossing entry of
     * every member, in the shape of the sequences (one line per member), or null.
     */
    internal val coloursPath: String? = null,
) {
    internal sealed interface Data {
        /** One arrangement per line: `.seq` intersection sequences, `.mod2s6` model 2 sparse6, others model 1 sparse6. */
        class File(val path: String) : Data

        /** Intersection sequences in the text format, one arrangement. */
        class Sequences(val text: String) : Data
    }

    private val lines: List<String> by lazy {
        when (data) {
            is Data.File -> bundledLines(data.path)
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
            (data as Data.File).path.endsWith(".seq") -> Arrangement.parse(text)
            data.path.endsWith(".mod2s6") -> Model2.decode(text)
            else -> Model1.decode(text)
        }
    }

    /** Whether the members come with a colouring of their crossings. */
    val hasColours: Boolean get() = coloursPath != null

    /** The colour class (0, 1, 2) of every crossing entry of member [index], or null. */
    fun colours(index: Int): IntArray? =
        coloursPath?.let { path -> Arrangement.parseLists(bundledLines(path)[index]).flatten().toIntArray() }

    /** The name of member [index]: the set's name, with `#<index + 1>` for lists. */
    fun memberName(index: Int): String = if (size == 1) name else "$name #${index + 1}"

    /** The position of member [index] in the list, like `#17 of 278`. */
    fun position(index: Int): String = "#${index + 1} of $size"
}

/** The non-blank lines of a bundled file. */
private fun bundledLines(path: String): List<String> {
    val chunks = GeneratedExamples.FILES[path] ?: error("example file $path is not bundled")
    return chunks.joinToString("").lines().filter { it.isNotBlank() }
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
    private const val COLORINGS = "website/arrangements/colorings/triangle_sat"
    private const val DIGONGRAPHS = "website/arrangements/digongraphs/data"

    private fun file(
        name: String,
        group: ExampleGroup,
        n: Int,
        label: String,
        description: String,
        source: String,
        path: String,
        page: WebPage? = group.page,
        paper: Paper? = group.paper,
        colours: String? = null,
    ) = ExampleSet(name, group, n, label, description, source, ExampleSet.Data.File(path), page, paper, colours)

    private fun sequences(name: String, label: String, description: String, text: String) =
        ExampleSet(
            name, ExampleGroup.THREE, Arrangement.parseLists(text).size, label, description, null,
            ExampleSet.Data.Sequences(text), page = null,
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
            label = "n = $n: $k triangles" + if (kind == "df") "" else ", with digons",
            description = "$which $what arrangements of $n pseudocircles with $count.",
            source = "$source/$stem.${if (ext == "mod1s6") "txt" else ext}",
            path = "$folder/$stem.$ext",
        )
    }

    val sets: List<ExampleSet> = buildList {
        // the database: one class after the other, as in the table of the website
        val database = ExampleGroup.DATABASE
        for (n in 3..5) {
            add(
                file(
                    "n$n-connected-all", database, n, "Connected, n = $n", "All connected arrangements of $n pseudocircles.",
                    "$CONNECTED/all$n.mod2s6", "connected/all$n.mod2s6",
                ),
            )
        }
        for (n in 2..5) {
            add(
                file(
                    "n$n-intersecting-all", database, n, "Intersecting, n = $n",
                    "All intersecting arrangements of $n pseudocircles.",
                    "$ABSTRACT/all$n.enc", "intersecting/all$n.enc",
                ),
            )
        }
        for (n in 3..6) {
            add(
                file(
                    "n$n-digonfree-all", database, n, "Digon-free intersecting, n = $n",
                    "All digon-free intersecting arrangements of $n pseudocircles.",
                    "$ABSTRACT/all${n}_digonfree.enc", "digonfree/all${n}_digonfree.enc",
                ),
            )
        }
        // the website has the numbers (the table of the paper), not the files
        for (n in 3..8) {
            add(
                file(
                    "n$n-great-all", database, n, "Great, n = $n", "All arrangements of $n great pseudocircles.",
                    "$GREAT/all$n.enc.greatcircles.enc", "great/all$n.enc.greatcircles.enc", page = null,
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
                    "n$n-great-fueredi-palasti", ExampleGroup.FUREDI_PALASTI, n, "n = $n: ${2 * n * (n - 3) / 3} triangles",
                    "The Füredi–Palásti arrangement of $n circles.",
                    "$FUREDI_PALASTI/fueredi_palasti_$n.enc", "fueredipalasti/fueredi_palasti_$n.enc",
                ),
            )
        }
        // the sections of the website's page, then the ones from the computations
        val nonr = ExampleGroup.NON_CIRCULARIZABLE
        fun noMoreCircles(name: String, n: Int, label: String, description: String, file: String) = file(
            name, nonr, n, label, description, "$NOMORECIRCLES/$file", "named/${file.substringAfterLast('/')}",
        )
        add(
            noMoreCircles(
                "n5-intersecting-nonr-1", 5, "n = 5, no. 1 (intersecting)",
                "The only non-circularizable intersecting arrangement of at most 5 pseudocircles.",
                "connected/n5_nonr_number1_intersecting.mod2s6",
            ),
        )
        for (k in 2..4) {
            add(
                noMoreCircles(
                    "n5-connected-nonr-$k", 5, "n = 5, no. $k", "A non-circularizable connected arrangement of 5 pseudocircles.",
                    "connected/n5_nonr_number$k.mod2s6",
                ),
            )
        }
        add(
            noMoreCircles(
                "n6-digonfree-nonr-8-triangles", 6, "n = 6 digon-free, no. 1 (8 triangles)",
                "A non-circularizable digon-free intersecting arrangement of 6 pseudocircles, the only one with 8 triangles.",
                "intersecting/digonfree/n6_nonr_number1_8triangles.mod2s6",
            ),
        )
        for (k in 2..3) {
            add(
                noMoreCircles(
                    "n6-digonfree-nonr-$k", 6, "n = 6 digon-free, no. $k",
                    "A non-circularizable digon-free intersecting arrangement of 6 pseudocircles.",
                    "intersecting/digonfree/n6_nonr_number$k.mod2s6",
                ),
            )
        }
        add(
            file(
                "n6-intersecting-nonr-edelsbrunner-ramos", nonr, 6, "n = 6, Edelsbrunner–Ramos",
                "A non-circularizable intersecting arrangement of 6 pseudocircles (Edelsbrunner and Ramos).",
                "computations/results/n6_nonr_edelsbrunnerramos.enc", "named/n6_nonr_edelsbrunnerramos.enc",
                // on the website as n6_edelsbrunner_nonr (isomorphic), "Additional intersecting arrangements"
            ),
        )
        for (k in 2..3) {
            add(
                noMoreCircles(
                    "n6-intersecting-nonr-sym6-$k", 6, "n = 6, sym6_$k",
                    "A non-circularizable intersecting arrangement of 6 pseudocircles.",
                    "intersecting/additional/n6_sym6_$k.mod2s6",
                ),
            )
        }
        for (sym in listOf("4_1", "4_2", "8_1", "8_2", "24")) {
            add(
                noMoreCircles(
                    "n6-connected-nonr-sym${sym.replace('_', '-')}", 6, "n = 6 connected, sym$sym",
                    "A non-circularizable digon-free connected arrangement of 6 pseudocircles.",
                    "connected/additional/n6_connected_digonfree_sym$sym.mod2s6",
                ),
            )
        }
        add(
            file(
                "n6-intersecting-nonr-all-triangles-krupp", nonr, 6, "n = 6, all triangles Krupp",
                "A non-circularizable intersecting arrangement of 6 pseudocircles; the circles of every triangle form a Krupp.",
                "computations/results/n6_nonr_all_triangles_krupp_number2.enc",
                "named/n6_nonr_all_triangles_krupp_number2.enc", page = null,
            ),
        )
        add(
            file(
                "n9-great-nonr-pappus", nonr, 9, "n = 9 great, non-Pappus",
                "A non-circularizable arrangement of 9 great pseudocircles, " +
                    "from the non-Pappus arrangement of 9 pseudolines.",
                "computations/results/n9_nonr_pappus.enc", "named/n9_nonr_pappus.enc", page = null,
            ),
        )
        // the website's files, with the colourings of its drawings; Koester's is not on the website
        fun saturated(name: String, n: Int, label: String, description: String, stem: String) = file(
            name, ExampleGroup.COLORINGS, n, label, description, "$COLORINGS/$stem.enc_1_1tutte7.ipe",
            "colorings/$stem.seq", colours = "colorings/$stem.colours",
        )
        add(
            saturated(
                "n6-great-triangle-saturated-1", 6, "n = 6 great, no. 1",
                "A triangle-saturated arrangement of 6 great pseudocircles.", "n6_triangle_sat_no1_great",
            ),
        )
        add(
            saturated(
                "n6-digonfree-triangle-saturated-2", 6, "n = 6, no. 2",
                "A triangle-saturated arrangement of 6 pseudocircles.", "n6_triangle_sat_no2",
            ),
        )
        add(
            saturated(
                "n7-digonfree-triangle-saturated-1", 7, "n = 7, no. 1",
                "A triangle-saturated arrangement of 7 pseudocircles.", "n7_triangle_sat_no1",
            ),
        )
        add(
            saturated(
                "n9-digonfree-triangle-saturated-1", 9, "n = 9, no. 1",
                "A triangle-saturated arrangement of 9 pseudocircles.", "n9_triangle_sat_no1",
            ),
        )
        add(
            saturated(
                "n10-great-triangle-saturated-1", 10, "n = 10 great, no. 1",
                "A triangle-saturated arrangement of 10 great pseudocircles.", "n10_triangle_sat_no1_great",
            ),
        )
        add(
            saturated(
                "n16-great-triangle-saturated-simmons", 16, "n = 16 great, Simmons",
                "Simmons' triangle-saturated arrangement of 16 great pseudocircles.", "n16_triangle_sat_Simmons",
            ),
        )
        add(
            file(
                "n7-connected-koester", ExampleGroup.COLORINGS, 7, "Koester's arrangement",
                "Koester's arrangement of 7 circles: all pairs but one cross, and its crossings need 4 colours.",
                "computations/biconnected_results/n8_koester.enc", "colorings/n8_koester.seq", page = null,
            ),
        )
        // the website's files; n = 8 has two examples, the others all types
        fun digongraphs(name: String, n: Int, label: String, description: String, stem: String) = file(
            name, ExampleGroup.DIGON_GRAPHS, n, label, description, "$DIGONGRAPHS/$stem.enc", "digongraphs/$stem.enc",
        )
        for (n in 4..7) {
            add(
                digongraphs(
                    "n$n-intersecting-digongraphs", n, "n = $n: all types",
                    "Intersecting arrangements of $n pseudocircles, one for every type of digongraph.",
                    "n${n}_all_types_of_digongraphs",
                ),
            )
        }
        add(
            digongraphs(
                "n7-intersecting-trianglefree-digongraphs", 7, "n = 7: all triangle-free types",
                "Intersecting arrangements of 7 pseudocircles, one for every type of triangle-free digongraph.",
                "n7_all_types_of_trianglefree_digongraphs",
            ),
        )
        add(
            digongraphs(
                "n8-intersecting-nonplanar-digongraph", 8, "n = 8: triangle-free, non-planar",
                "Intersecting arrangements of 8 pseudocircles with a triangle-free non-planar digongraph.",
                "n8_digongraph_trianglefree_nonplanar",
            ),
        )
        add(sequences("n3-great-krupp", "Krupp", "The Krupp: 3 great circles.", Arrangement.krupp().toString()))
        add(
            sequences(
                "n3-intersecting-nonkrupp", "NonKrupp", "The NonKrupp: the other intersecting arrangement of 3 circles.",
                "{1: [2, -2, 3, -3], 2: [-1, 1, 3, -3], 3: [-1, 1, 2, -2]}",
            ),
        )
        add(
            sequences(
                "n3-connected-chain", "3-Chain", "The 3-Chain: circle 2 crosses circles 1 and 3, which do not cross.",
                "{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}",
            ),
        )
    }

    /** The set with the given [name], or null. */
    fun byName(name: String): ExampleSet? = sets.firstOrNull { it.name == name }

    /** The sets of [group], in catalogue order. */
    fun inGroup(group: ExampleGroup): List<ExampleSet> = sets.filter { it.group == group }

    /** The bundled colourings by arrangement (exactly as stored, so an edited one has none). */
    private val colourings: Map<Arrangement, IntArray> by lazy {
        buildMap {
            for (s in sets) if (s.hasColours) for (i in 0 until s.size) put(s.arrangement(i), s.colours(i)!!)
        }
    }

    /** The colour class (0, 1, 2) of every crossing entry of [arr] if it is a bundled coloured example. */
    fun crossingColours(arr: Arrangement): IntArray? = colourings[arr]

    /** The example shown at start (to be confirmed by Manfred, see doc/SPEC.md). */
    val start: ExampleRef get() = ExampleRef(byName("n6-digonfree-nonr-8-triangles")!!, 0)
}
