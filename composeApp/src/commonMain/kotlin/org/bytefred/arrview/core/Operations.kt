package org.bytefred.arrview.core

import kotlin.math.abs

// Properties, class tests and flips of arrangements.

/** True if circles [c] and [d] cross. */
fun Arrangement.crosses(c: Int, d: Int): Boolean = c != d && list(c).any { abs(it) == d + 1 }

/** The intersection graph: all pairs `(c, d)`, `c < d`, of circles that cross. */
fun Arrangement.intersectionGraph(): List<Pair<Int, Int>> =
    (0 until n).flatMap { c -> (c + 1 until n).filter { d -> crosses(c, d) }.map { d -> c to d } }

/** Number of pairs of circles that do not cross. */
val Arrangement.nonIntersectingPairCount: Int get() = n * (n - 1) / 2 - size / 4

/** True if there is no digon. */
val Arrangement.isDigonFree: Boolean get() = digonCount == 0

/**
 * Krupp test for three pairwise crossing circles (ported from `test_antigreatcircles.sage`):
 * the crossings with [b] and with [c] interleave on circle [a]. The Krupp is the arrangement of
 * three great circles (eight triangles).
 */
fun Arrangement.isKrupp(a: Int, b: Int, c: Int): Boolean {
    val l = list(a)
    var b1 = l.indexOf(b + 1)
    var b2 = l.indexOf(-(b + 1))
    var c1 = l.indexOf(c + 1)
    var c2 = l.indexOf(-(c + 1))
    require(b1 >= 0 && c1 >= 0) { "circles ${b + 1} and ${c + 1} must both cross circle ${a + 1}" }
    if (b1 > b2) b1 = b2.also { b2 = b1 }
    if (c1 > c2) c1 = c2.also { c2 = c1 }
    if (b1 > c1) {
        b1 = c1.also { c1 = b1 }
        b2 = c2.also { c2 = b2 }
    }
    return b2 in (c1 + 1) until c2
}

/**
 * The triples of pairwise crossing circles, counted as (Krupp, NonKrupp). An intersecting
 * arrangement of three pseudocircles is one of the two.
 */
val Arrangement.kruppTriples: Pair<Int, Int>
    get() {
        var krupp = 0
        var nonKrupp = 0
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n) {
            if (!crosses(a, b) || !crosses(a, c) || !crosses(b, c)) continue
            if (isKrupp(a, b, c)) krupp++ else nonKrupp++
        }
        return krupp to nonKrupp
    }

/** Pseudo great-circle arrangement: intersecting and every triple is a Krupp (`test_greatcircles.sage`). */
val Arrangement.isGreat: Boolean
    get() {
        if (!isIntersecting) return false
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n) {
            if (!isKrupp(a, b, c)) return false
        }
        return true
    }

/**
 * Cylindrical: there are two faces separated by every circle (their side vectors are
 * complementary), as in `model2_program/test_cylindrical_proper.sage`.
 */
val Arrangement.isCylindrical: Boolean
    get() {
        // side vector of every face relative to face 0, by walking over arcs (each arc toggles
        // the side of its circle)
        val sides = arrayOfNulls<BooleanArray>(faceCount)
        sides[0] = BooleanArray(n)
        val queue = ArrayDeque(listOf(0))
        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            for (a in faces[f]) {
                val g = faceOfDart[alpha(a)]
                if (sides[g] != null) continue
                sides[g] = sides[f]!!.copyOf().also { s ->
                    val c = circleOfCrossing(a / 2)
                    s[c] = !s[c]
                }
                queue.addLast(g)
            }
        }
        val keys = sides.map { s -> s!!.joinToString("") { if (it) "1" else "0" } }.toSet()
        return keys.any { key -> key.map { if (it == '1') '0' else '1' }.joinToString("") in keys }
    }

/** Dart [a] of a face lies on circle `circleOfDart(a)`. */
fun Arrangement.circleOfDart(a: Int): Int = circleOfCrossing(a / 2)

/** The circles along face [f] (one per side). */
fun Arrangement.faceCircles(f: Int): List<Int> = faces[f].map(::circleOfDart)

/**
 * Triangle flip of the triangular face [f] (ported from rusty-arrangements
 * `flip_potential.rs::flip`): every side of the triangle is moved over the opposite crossing,
 * i.e. on each of the three circles the two crossings at the triangle are swapped.
 */
fun Arrangement.triangleFlip(f: Int): Arrangement {
    val face = faces[f]
    require(face.size == 3) { "face $f is not a triangle (it has ${face.size} sides)" }
    val lists = lists.map { it.toMutableList() }
    for (a in face) {
        val k = a / 2
        val c = circleOfCrossing(k)
        val len = lengthOf(c)
        val i = k - offset(c)
        val j = if (a % 2 == 0) (i + 1) % len else (i + len - 1) % len
        val list = lists[c]
        list[i] = list[j].also { list[j] = list[i] }
    }
    return Arrangement.of(lists)
}

/**
 * Digon collapse of the digon face [f] (`digon_collapse` in `model2basics.sage`): the digon flips
 * over and its two circles no longer cross; the digon and the two faces opposite to it at its
 * corners merge into one face. In the sequences, the two crossings of the pair are removed.
 * Returns null if the result would not be connected (as the sage code refuses then).
 */
fun Arrangement.digonCollapse(f: Int): Arrangement? {
    val face = faces[f]
    require(face.size == 2) { "face $f is not a digon (it has ${face.size} sides)" }
    val c = circleOfDart(face[0])
    val d = circleOfDart(face[1])
    val lists = lists.mapIndexed { i, list ->
        when (i) {
            c -> list.filter { abs(it) != d + 1 }
            d -> list.filter { abs(it) != c + 1 }
            else -> list
        }
    }
    if (!isConnected(lists)) return null
    return Arrangement.of(lists)
}

/**
 * A digon create: circles [i] and [j] (`i < j`), which do not cross, both have a side on face
 * [face]; [dartI] is that side of [i], [dartJ] that side of [j] (darts of the face). Pushing the
 * side of [i] across the side of [j] through the face gives two new crossings and a new digon.
 */
data class DigonCreate(val face: Int, val i: Int, val j: Int, val dartI: Int, val dartJ: Int)

/**
 * All digon creates of the arrangement: for every face, every pair of non-crossing circles with
 * a side on it and every choice of those sides (by face, then by the positions of the sides).
 */
fun Arrangement.digonCreates(): List<DigonCreate> = faces.indices.flatMap(::digonCreatesIn)

/** The digon creates through face [f] (see [digonCreates]). */
fun Arrangement.digonCreatesIn(f: Int): List<DigonCreate> {
    val face = faces[f]
    val result = ArrayList<DigonCreate>()
    for (x in face.indices) for (y in x + 1 until face.size) {
        val a = face[x]
        val b = face[y]
        val c = circleOfDart(a)
        val d = circleOfDart(b)
        if (c == d || crosses(c, d)) continue
        result += if (c < d) DigonCreate(f, c, d, a, b) else DigonCreate(f, d, c, b, a)
    }
    return result
}

/**
 * Digon create (the inverse of [digonCollapse]): the side [DigonCreate.dartI] of circle `i` is
 * pushed through the face across the side [DigonCreate.dartJ] of circle `j`. Each of the two sides
 * gets the two new crossings, in list order `-o, +o` with `o` the other circle's label, negated
 * if the other circle's side runs against its orientation (a backward dart).
 */
fun Arrangement.digonCreate(op: DigonCreate): Arrangement {
    val (f, i, j, dartI, dartJ) = op
    require(faceOfDart[dartI] == f && faceOfDart[dartJ] == f) { "the sides are not on face $f" }
    require(circleOfDart(dartI) == i && circleOfDart(dartJ) == j && i != j) { "the sides are not on circles ${i + 1} and ${j + 1}" }
    require(!crosses(i, j)) { "circles ${i + 1} and ${j + 1} cross already" }
    val lists = lists.map { it.toMutableList() }
    fun insert(dart: Int, other: Int, otherDart: Int) {
        val k = dart / 2
        val c = circleOfCrossing(k)
        val p = k - offset(c)
        val s = if (otherDart % 2 == 0) 1 else -1
        // a forward dart runs from crossing p to p + 1, a backward one from p - 1 to p
        lists[c].addAll(if (dart % 2 == 0) p + 1 else p, listOf(-s * (other + 1), s * (other + 1)))
    }
    insert(dartI, j, dartJ)
    insert(dartJ, i, dartI)
    return Arrangement.of(lists)
}

/** Whether the circles of [lists] form a connected intersection graph. */
private fun isConnected(lists: List<List<Int>>): Boolean {
    if (lists.any { it.isEmpty() }) return false
    val reached = BooleanArray(lists.size)
    val stack = ArrayDeque(listOf(0))
    reached[0] = true
    while (stack.isNotEmpty()) {
        for (e in lists[stack.removeLast()]) {
            val d = abs(e) - 1
            if (!reached[d]) {
                reached[d] = true
                stack.addLast(d)
            }
        }
    }
    return reached.all { it }
}

/** All arrangements reachable by one triangle flip (one per triangle). */
fun Arrangement.triangleFlips(): List<Arrangement> =
    faces.indices.filter { faces[it].size == 3 }.map(::triangleFlip)

/**
 * The sub-arrangement of the given circles (relabelled `0 until circles.size` in the given
 * order). Throws [InvalidArrangementException] if it is not connected.
 */
fun Arrangement.subArrangement(circles: List<Int>): Arrangement {
    val newLabel = IntArray(n) { -1 }
    circles.forEachIndexed { i, c -> newLabel[c] = i }
    return Arrangement.of(
        circles.map { c ->
            list(c).filter { newLabel[abs(it) - 1] >= 0 }.map { e -> (if (e > 0) 1 else -1) * (newLabel[abs(e) - 1] + 1) }
        },
    )
}

/** The same arrangement with circle `c` renamed to `perm[c]`. */
fun Arrangement.relabelled(perm: List<Int>): Arrangement {
    require(perm.sorted() == (0 until n).toList()) { "not a permutation of 0..${n - 1}" }
    val lists = MutableList(n) { emptyList<Int>() }
    for (c in 0 until n) {
        lists[perm[c]] = list(c).map { e -> (if (e > 0) 1 else -1) * (perm[abs(e) - 1] + 1) }
    }
    return Arrangement.of(lists)
}

/** The same arrangement with the orientation of circle [c] reversed. */
fun Arrangement.reversed(c: Int): Arrangement =
    Arrangement.of(
        lists.mapIndexed { i, list ->
            if (i == c) list.reversed().map { -it } else list.map { if (abs(it) == c + 1) -it else it }
        },
    )

/** The mirror image (all signs negated). */
fun Arrangement.mirrored(): Arrangement = Arrangement.of(lists.map { list -> list.map { -it } })
