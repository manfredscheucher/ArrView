package org.bytefred.arrview.core

/** The groups of bundled examples, in display order. */
enum class ExampleGroup(val title: String) {
    GREAT("Pseudo great circles"),
    DIGON_FREE("Intersecting, digon-free"),
    INTERSECTING("Intersecting"),
    CONNECTED("Connected"),
    MIN_TRIANGLES("Triangle minimizing"),
    MAX_TRIANGLES("Triangle maximizing"),
    FUREDI_PALASTI("Füredi–Palásti construction"),
    NAMED("Named arrangements"),
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
    ) = ExampleSet(name, group, n, description, source, ExampleSet.Data.File(path))

    private fun sequences(name: String, group: ExampleGroup, description: String, text: String) =
        ExampleSet(name, group, Arrangement.parseLists(text).size, description, null, ExampleSet.Data.Sequences(text))

    /** Triangle files: `<all|some><n>_<df|digon>_<k>_triangles`. */
    private fun triangles(group: ExampleGroup, folder: String, source: String, stem: String, ext: String): ExampleSet {
        val (scope, n, kind, k) = Regex("""(all|some)(\d+)_(df|digon)_(\d+)_triangles""").matchEntire(stem)!!.destructured
        val what = if (kind == "df") "digon-free intersecting" else "intersecting"
        val which = if (scope == "all") "All" else "Some"
        return file(
            name = "n$n-${if (kind == "df") "digonfree" else "intersecting"}-$k-triangles",
            group = group,
            n = n.toInt(),
            description = "$which $what arrangements of $n pseudocircles with $k triangles.",
            source = "$source/$stem.${if (ext == "mod1s6") "txt" else ext}",
            path = "$folder/$stem.$ext",
        )
    }

    val sets: List<ExampleSet> = buildList {
        for (n in 3..8) {
            add(
                file(
                    "n$n-great-all", ExampleGroup.GREAT, n,
                    "All arrangements of $n pseudo great circles (every three circles form a Krupp).",
                    "$GREAT/all$n.enc.greatcircles.enc", "great/all$n.enc.greatcircles.enc",
                ),
            )
        }
        for (n in 3..6) {
            add(
                file(
                    "n$n-digonfree-all", ExampleGroup.DIGON_FREE, n,
                    "All digon-free intersecting arrangements of $n pseudocircles.",
                    "$ABSTRACT/all${n}_digonfree.enc", "digonfree/all${n}_digonfree.enc",
                ),
            )
        }
        for (n in 2..5) {
            add(
                file(
                    "n$n-intersecting-all", ExampleGroup.INTERSECTING, n,
                    "All intersecting arrangements of $n pseudocircles.",
                    "$ABSTRACT/all$n.enc", "intersecting/all$n.enc",
                ),
            )
        }
        for (n in 3..5) {
            add(
                file(
                    "n$n-connected-all", ExampleGroup.CONNECTED, n,
                    "All connected arrangements of $n pseudocircles.",
                    "$CONNECTED/all$n.mod2s6", "connected/all$n.mod2s6",
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
                    "Füredi–Palásti construction with $n circles (circularizable, p3/n² tends to 2/3).",
                    "$FUREDI_PALASTI/fueredi_palasti_$n.enc", "fueredipalasti/fueredi_palasti_$n.enc",
                ),
            )
        }
        add(sequences("n3-great-krupp", ExampleGroup.NAMED, "The Krupp: three great circles.", Arrangement.krupp().toString()))
        add(
            sequences(
                "n3-intersecting-nonkrupp", ExampleGroup.NAMED,
                "The NonKrupp: the other intersecting arrangement of three circles.",
                "{1: [2, -2, 3, -3], 2: [-1, 1, 3, -3], 3: [-1, 1, 2, -2]}",
            ),
        )
        add(
            sequences(
                "n3-connected-chain", ExampleGroup.NAMED,
                "The 3-Chain: circle 2 crosses circles 1 and 3, which do not cross.",
                "{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}",
            ),
        )
        add(
            file(
                "n5-intersecting-nonr-1", ExampleGroup.NAMED, 5, "Non-circularizable, intersecting.",
                "$NOMORECIRCLES/connected/n5_nonr_number1_intersecting.mod2s6",
                "named/n5_nonr_number1_intersecting.mod2s6",
            ),
        )
        for (k in 2..4) {
            add(
                file(
                    "n5-connected-nonr-$k", ExampleGroup.NAMED, 5, "Non-circularizable, connected.",
                    "$NOMORECIRCLES/connected/n5_nonr_number$k.mod2s6", "named/n5_nonr_number$k.mod2s6",
                ),
            )
        }
        add(
            file(
                "n6-digonfree-nonr-8-triangles", ExampleGroup.NAMED, 6,
                "Non-circularizable, digon-free, 8 triangles (the only digon-free one of 6 circles with 8).",
                "$NOMORECIRCLES/intersecting/digonfree/n6_nonr_number1_8triangles.mod2s6",
                "named/n6_nonr_number1_8triangles.mod2s6",
            ),
        )
        for (k in 2..3) {
            add(
                file(
                    "n6-digonfree-nonr-$k", ExampleGroup.NAMED, 6, "Non-circularizable, digon-free.",
                    "$NOMORECIRCLES/intersecting/digonfree/n6_nonr_number$k.mod2s6", "named/n6_nonr_number$k.mod2s6",
                ),
            )
        }
        add(
            file(
                "n6-intersecting-nonr-all-triangles-krupp", ExampleGroup.NAMED, 6,
                "Non-circularizable, intersecting (\"all triangles Krupp\", number 2).",
                "computations/results/n6_nonr_all_triangles_krupp_number2.enc",
                "named/n6_nonr_all_triangles_krupp_number2.enc",
            ),
        )
        add(
            file(
                "n6-intersecting-nonr-edelsbrunner-ramos", ExampleGroup.NAMED, 6,
                "Non-circularizable, intersecting (Edelsbrunner–Ramos).",
                "computations/results/n6_nonr_edelsbrunnerramos.enc", "named/n6_nonr_edelsbrunnerramos.enc",
            ),
        )
        add(
            file(
                "n9-great-nonr-pappus", ExampleGroup.NAMED, 9,
                "Non-circularizable, pseudo great circles (from the non-Pappus arrangement of 9 pseudolines).",
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
