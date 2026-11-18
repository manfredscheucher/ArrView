package org.bytefred.arrview.core

/**
 * Face [f] described by the circles of its sides (labels `1..n`) in cyclic boundary order,
 * separated by spaces, starting at the lexicographically smallest rotation, e.g. `1 2 1 3`. Sides
 * whose dart is in [marked] get a star: `1* 2 1 3*`.
 */
fun Arrangement.cellDescription(f: Int, marked: Set<Int> = emptySet()): String {
    val darts = faces[f]
    val labels = darts.map { circleOfDart(it) + 1 }
    val start = labels.indices.minWithOrNull { a, b ->
        for (t in labels.indices) {
            val x = labels[(a + t) % labels.size]
            val y = labels[(b + t) % labels.size]
            if (x != y) return@minWithOrNull x.compareTo(y)
        }
        0
    } ?: 0
    return labels.indices.joinToString(" ") { t ->
        val s = (start + t) % labels.size
        if (darts[s] in marked) "${labels[s]}*" else "${labels[s]}"
    }
}

/** Position of side (dart) [a] along its circle: the index of its arc (a crossing to the next one) in the circle's list. */
fun Arrangement.sidePosition(a: Int): Int = arcOfDart(a) - offset(circleOfDart(a))

/**
 * The order of digon creates of one pair i < j: along circle i (its list order, from its first
 * side), by the side of i that is pushed, then along circle j by the side of j.
 */
val Arrangement.createOrder: Comparator<DigonCreate>
    get() = compareBy({ sidePosition(it.dartI) }, { sidePosition(it.dartJ) })

/**
 * The flips that can be done in an arrangement, by face, by pair of circles and by triple of
 * circles: triangle flips, digon collapses that keep the arrangement connected, digon creates.
 * Computed once per arrangement.
 */
class CellActions(val arrangement: Arrangement) {
    private val arr = arrangement

    /** All digon creates ([Arrangement.digonCreates]). */
    val creates: List<DigonCreate> = arr.digonCreates()

    private val createsByFace: Map<Int, List<DigonCreate>> = creates.groupBy { it.face }

    /** The digons whose collapse is allowed (the arrangement stays connected). */
    val collapsible: List<Int> = arr.faces.indices.filter { arr.faces[it].size == 2 && arr.digonCollapse(it) != null }

    /** The triangles (every one can be flipped). */
    val triangles: List<Int> = arr.faces.indices.filter { arr.faces[it].size == 3 }

    /** The digon creates through face [f]. */
    fun createsIn(f: Int): List<DigonCreate> = createsByFace[f].orEmpty()

    /** Whether face [f] can be flipped, collapsed or used for a digon create. */
    fun hasAction(f: Int): Boolean = arr.faces.getOrNull(f)?.let { face ->
        face.size == 3 || f in collapsible || createsIn(f).isNotEmpty()
    } == true

    private val createsByPair: Map<Pair<Int, Int>, List<DigonCreate>> =
        creates.groupBy { it.i to it.j }.mapValues { (_, ops) -> ops.sortedWith(arr.createOrder) }

    private val collapsesByPair: Map<Pair<Int, Int>, List<Int>> = collapsible.groupBy { f ->
        val (c, d) = arr.faceCircles(f)
        minOf(c, d) to maxOf(c, d)
    }

    /** The digon creates of circles [i] < [j], in [Arrangement.createOrder]. */
    fun createsOf(i: Int, j: Int): List<DigonCreate> = createsByPair[i to j].orEmpty()

    /** The collapsible digons of circles [i] < [j]. */
    fun collapsesOf(i: Int, j: Int): List<Int> = collapsesByPair[i to j].orEmpty()

    /** Number of flips of the pair [i] < [j] (creates or collapses; a pair has only one kind). */
    fun pairActionCount(i: Int, j: Int): Int = createsOf(i, j).size + collapsesOf(i, j).size

    /** The triangles per triple of circles (sorted `a < b < c`), in face order. */
    val triples: Map<List<Int>, List<Int>> = triangles.groupBy { f -> arr.faceCircles(f).sorted() }

    /** The pairs i < j with a digon create and their numbers of options, by i, then j. */
    val createPairs: List<Pair<List<Int>, Int>> = sortedCounts(createsByPair.map { (p, ops) -> p.toList() to ops.size })

    /** The pairs i < j with a collapsible digon and their numbers of digons (always 1), by i, then j. */
    val collapsePairs: List<Pair<List<Int>, Int>> = sortedCounts(collapsesByPair.map { (p, fs) -> p.toList() to fs.size })

    /** The triples with a triangle and their numbers of triangles, sorted numerically. */
    val triangleTriples: List<Pair<List<Int>, Int>> = sortedCounts(triples.map { (t, fs) -> t to fs.size })

    /** The pairs with an action (the striped, clickable ones of the intersection graph), by i, then j. */
    val actionPairs: List<Pair<Int, Int>> = (createPairs + collapsePairs).map { (p, _) -> p[0] to p[1] }.sortedWith(compareBy({ it.first }, { it.second }))
}

/** [entries] sorted lexicographically on their circle numbers. */
private fun sortedCounts(entries: List<Pair<List<Int>, Int>>): List<Pair<List<Int>, Int>> =
    entries.sortedWith { (a, _), (b, _) ->
        (0 until minOf(a.size, b.size)).firstOrNull { a[it] != b[it] }?.let { a[it].compareTo(b[it]) } ?: a.size.compareTo(b.size)
    }
