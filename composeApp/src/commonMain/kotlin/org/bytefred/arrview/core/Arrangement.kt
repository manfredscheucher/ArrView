package org.bytefred.arrview.core

import kotlin.math.abs

/** Input that does not describe a valid arrangement; the message says why. */
class InvalidArrangementException(message: String) : IllegalArgumentException(message)

/**
 * A connected arrangement of pseudocircles on the sphere (every two pseudocircles cross exactly
 * twice or not at all, the union of the pseudocircles is connected), stored as its intersection
 * scheme: for every circle the cyclic sequence of signed crossings. Ported from
 * rusty-arrangements (`src/arrangement.rs`).
 *
 * Conventions:
 * - circles are `0 until n` in the API and `1..n` in the entries and the text format,
 * - every circle carries an orientation,
 * - entry `+(d+1)` in the list of circle `c` means that circle `d` crosses `c` from the right
 *   side of `c` to its left side, `-(d+1)` from left to right. So `sign_c(d) = -sign_d(c)` at the
 *   same crossing: entry `+k` in list `i` belongs to the same crossing as entry `-i` in list `k`
 *   (Linhart–Ortner, Definition 2.4 (iii)). Reading the signs the other way round (as
 *   Linhart–Ortner do) gives the mirror image, which is the same arrangement here.
 *
 * Internally the lists are concatenated into [entries]; the list of circle `c` is
 * `entries[offset(c) until offset(c + 1)]`. A *crossing* is an index `k` into [entries]. A
 * *dart* is `2k + dir`: it leaves crossing `k` along its circle, forward for `dir = 0`, backward
 * for `dir = 1`.
 *
 * Instances are immutable and always valid (see [LinhartOrtner]). Equality compares the lists
 * literally; use [canonical] to compare up to relabelling, reorientation and mirroring.
 */
class Arrangement private constructor(
    /** Number of pseudocircles. */
    val n: Int,
    private val entries: IntArray,
    private val offsets: IntArray,
) {
    /** Number of crossing entries (twice the number of crossings). */
    val size: Int get() = entries.size

    /** Number of crossings (vertices of the primal graph). */
    val crossingCount: Int get() = entries.size / 2

    /** Index of the first crossing of circle [c] in the entries; `offset(n)` is the total. */
    fun offset(c: Int): Int = offsets[c]

    /** Number of crossings on circle [c]. */
    fun lengthOf(c: Int): Int = offsets[c + 1] - offsets[c]

    /** The signed entry of crossing [k]. */
    fun entry(k: Int): Int = entries[k]

    /** The circle that crossing [k] lies on (the list it belongs to). */
    fun circleOfCrossing(k: Int): Int = circleOfEntry[k]

    private val circleOfEntry: IntArray by lazy {
        IntArray(entries.size).also { result ->
            for (c in 0 until n) for (k in offsets[c] until offsets[c + 1]) result[k] = c
        }
    }

    /** The other circle at crossing [k]. */
    fun otherCircle(k: Int): Int = circleOf(entries[k])

    /** The cyclic crossing sequence of circle [c] (signed labels `±(d+1)`). */
    fun list(c: Int): List<Int> = (offsets[c] until offsets[c + 1]).map { entries[it] }

    /** All crossing sequences. */
    val lists: List<List<Int>> get() = (0 until n).map(::list)

    /** The next crossing after [k] along its circle. */
    fun nextOnCircle(k: Int): Int {
        val c = circleOfCrossing(k)
        return if (k + 1 == offsets[c + 1]) offsets[c] else k + 1
    }

    /** The crossing before [k] along its circle. */
    fun previousOnCircle(k: Int): Int {
        val c = circleOfCrossing(k)
        return if (k == offsets[c]) offsets[c + 1] - 1 else k - 1
    }

    /**
     * Partner table: crossing `k` (on circle `c`) is the same point as crossing `partner[k]` on
     * circle `otherCircle(k)`.
     */
    val partners: IntArray by lazy { computePartners(n, entries, offsets) }

    /** Number of darts (`2 * size`). */
    val dartCount: Int get() = 2 * entries.size

    /** `alpha(a)`: the same edge as dart [a] in the opposite direction. */
    fun alpha(a: Int): Int {
        val k = a / 2
        return if (a % 2 == 0) 2 * nextOnCircle(k) + 1 else 2 * previousOnCircle(k)
    }

    /**
     * `sigma(a)`: the counter-clockwise successor of dart [a] at its crossing. For
     * `sign_c(d) = +1` the ccw order is `c_fwd, d_fwd, c_bwd, d_bwd`, for `sign_c(d) = -1` it is
     * `c_fwd, d_bwd, c_bwd, d_fwd`.
     */
    fun sigma(a: Int): Int {
        val k = a / 2
        val p = partners[k]
        val positive = entries[k] > 0
        // the successor of c_fwd is d_fwd (positive) or d_bwd; of c_bwd the other one of d
        return if (a % 2 == 0) {
            if (positive) 2 * p else 2 * p + 1
        } else {
            if (positive) 2 * p + 1 else 2 * p
        }
    }

    /**
     * Face successors (the face algorithm of Linhart–Ortner, Section 3):
     * `phi[a] = sigma(alpha(a))` is the next dart along the face to the right of dart `a`.
     */
    val faceSuccessors: IntArray by lazy { IntArray(dartCount) { sigma(alpha(it)) } }

    /** Faces as dart orbits of [faceSuccessors] in boundary order; dart `a` belongs to the face to its right. */
    val faces: List<IntArray> by lazy { orbits(faceSuccessors) }

    /** `faceOfDart[a]`: index into [faces] of the face to the right of dart `a`. */
    val faceOfDart: IntArray by lazy {
        val result = IntArray(dartCount)
        faces.forEachIndexed { f, orbit -> for (a in orbit) result[a] = f }
        result
    }

    /** Number of faces. */
    val faceCount: Int get() = faces.size

    /** Face sizes (number of sides), parallel to [faces]. */
    val faceSizes: IntArray get() = IntArray(faces.size) { faces[it].size }

    /**
     * The p-vector: `pVector[k]` is the number of faces with `k` sides, from `k = 0` up to the
     * largest face (the format of the old `*.stat.txt` files).
     */
    val pVector: IntArray by lazy {
        val sizes = faceSizes
        val result = IntArray((sizes.maxOrNull() ?: 0) + 1)
        for (s in sizes) result[s]++
        result
    }

    /** Number of digons (`p_2`). */
    val digonCount: Int get() = pVector.getOrElse(2) { 0 }

    /** Number of triangles (`p_3`). */
    val triangleCount: Int get() = pVector.getOrElse(3) { 0 }

    /** True if every two circles cross (every list has `2(n-1)` entries). */
    val isIntersecting: Boolean get() = entries.size == 2 * n * (n - 1)

    /** The canonical form: equal for two arrangements iff they are isomorphic (incl. mirror). */
    val canonical: Arrangement by lazy { Canonical.of(this) }

    /** The symmetries as dart permutations (see [Symmetry]). */
    val automorphisms: List<IntArray> by lazy { Symmetry.automorphisms(this) }

    /** True if [other] is the same arrangement up to relabelling, reorientation and mirroring. */
    fun isIsomorphicTo(other: Arrangement): Boolean = canonical == other.canonical

    override fun equals(other: Any?): Boolean =
        other is Arrangement && n == other.n && entries.contentEquals(other.entries) &&
            offsets.contentEquals(other.offsets)

    override fun hashCode(): Int = entries.contentHashCode() * 31 + n

    /** The text format `{1: [2, -2], 2: [1, -1]}`. */
    override fun toString(): String = format(lists)

    companion object {
        /** The signed label `±(d+1)` of circle `d`. */
        fun circleOf(entry: Int): Int = abs(entry) - 1

        /**
         * Builds an arrangement from its crossing sequences (entries `±(d+1)`, lists of circles
         * `0 until n`), checking validity (see [LinhartOrtner]).
         * @throws InvalidArrangementException with the reason if the input is not valid
         */
        fun of(lists: List<List<Int>>): Arrangement {
            LinhartOrtner.checkShape(lists)?.let { throw InvalidArrangementException(it) }
            val arrangement = unchecked(lists)
            LinhartOrtner.checkTopology(arrangement)?.let { throw InvalidArrangementException(it) }
            return arrangement
        }

        /** Like [of], but returns null instead of throwing. */
        fun ofOrNull(lists: List<List<Int>>): Arrangement? =
            try {
                of(lists)
            } catch (_: InvalidArrangementException) {
                null
            }

        /**
         * Wraps lists that already passed [LinhartOrtner.checkShape] (or are known to be valid)
         * without the topological checks.
         */
        internal fun unchecked(lists: List<List<Int>>): Arrangement {
            val offsets = IntArray(lists.size + 1)
            for (c in lists.indices) offsets[c + 1] = offsets[c] + lists[c].size
            val entries = IntArray(offsets[lists.size])
            var k = 0
            for (list in lists) for (e in list) entries[k++] = e
            return Arrangement(lists.size, entries, offsets)
        }

        /** Parses the text format `{1: [2, -2], 2: [1, -1]}` (see [parseLists]) and checks validity. */
        fun parse(text: String): Arrangement = of(parseLists(text))

        /**
         * Parses the text format `{1: [2, -2, 3, -3], 2: [...], ...}` into lists without checking
         * validity. The keys must be `1..n`, each once, in any order; whitespace and line breaks
         * are free. Also accepts a bare list of lists `[[2, -2], [1, -1]]`.
         */
        fun parseLists(text: String): List<List<Int>> = SequenceParser(text).parse()

        /** The text format of the given lists. */
        fun format(lists: List<List<Int>>): String =
            lists.withIndex().joinToString(", ", "{", "}") { (c, list) ->
                "${c + 1}: " + list.joinToString(", ", "[", "]")
            }

        /** The unique arrangement of two pseudocircles. */
        fun two(): Arrangement = parse("{1: [2, -2], 2: [1, -1]}")

        /** The Krupp: three great circles. */
        fun krupp(): Arrangement = parse("{1: [2, 3, -2, -3], 2: [1, -3, -1, 3], 3: [1, 2, -1, -2]}")

        private fun computePartners(n: Int, entries: IntArray, offsets: IntArray): IntArray {
            // pos[(d * n + c) * 2 + s]: index of entry c+1 with sign s (0: +, 1: -) in list d
            val pos = IntArray(n * n * 2)
            for (d in 0 until n) {
                for (k in offsets[d] until offsets[d + 1]) {
                    val e = entries[k]
                    pos[(d * n + circleOf(e)) * 2 + if (e < 0) 1 else 0] = k
                }
            }
            val partner = IntArray(entries.size)
            for (c in 0 until n) {
                for (k in offsets[c] until offsets[c + 1]) {
                    val e = entries[k]
                    // sign_d(c) = -sign_c(d)
                    partner[k] = pos[(circleOf(e) * n + c) * 2 + if (e > 0) 1 else 0]
                }
            }
            return partner
        }
    }
}

/** Orbits of a permutation, each in iteration order, ordered by their smallest element. */
internal fun orbits(perm: IntArray): List<IntArray> {
    val done = BooleanArray(perm.size)
    val result = mutableListOf<IntArray>()
    for (start in perm.indices) {
        if (done[start]) continue
        val orbit = mutableListOf<Int>()
        var a = start
        while (!done[a]) {
            done[a] = true
            orbit.add(a)
            a = perm[a]
        }
        result.add(orbit.toIntArray())
    }
    return result
}

/** Hand-written parser for the sequence format, with readable error messages. */
private class SequenceParser(private val text: String) {
    private var pos = 0

    fun parse(): List<List<Int>> {
        skipSpace()
        val result = when (peek()) {
            '{' -> parseDict()
            '[' -> parseList { parseList { parseInt() } }
            else -> fail("expected '{' at the start")
        }
        skipSpace()
        if (pos < text.length) fail("unexpected '${text[pos]}' after the end")
        if (result.isEmpty()) throw InvalidArrangementException("no pseudocircles given")
        return result
    }

    private fun parseDict(): List<List<Int>> {
        expect('{')
        val byKey = mutableMapOf<Int, List<Int>>()
        skipSpace()
        if (peek() != '}') {
            while (true) {
                skipSpace()
                val keyPos = pos
                val key = parseInt()
                skipSpace()
                expect(':')
                skipSpace()
                val list = parseList { parseInt() }
                if (key in byKey) {
                    pos = keyPos
                    fail("circle $key is given twice")
                }
                byKey[key] = list
                skipSpace()
                if (peek() == ',') {
                    pos++
                    continue
                }
                break
            }
        }
        skipSpace()
        expect('}')
        val n = byKey.size
        for (key in byKey.keys) {
            if (key !in 1..n) {
                throw InvalidArrangementException(
                    "circle labels must be 1..$n, but $key is given",
                )
            }
        }
        return (1..n).map { byKey.getValue(it) }
    }

    private fun <T> parseList(item: () -> T): List<T> {
        expect('[')
        val result = mutableListOf<T>()
        skipSpace()
        if (peek() == ']') {
            pos++
            return result
        }
        while (true) {
            skipSpace()
            result.add(item())
            skipSpace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return result
                }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun parseInt(): Int {
        val start = pos
        if (peek() == '+' || peek() == '-') pos++
        while (pos < text.length && text[pos].isDigit()) pos++
        val token = text.substring(start, pos)
        if (token.isEmpty() || token == "+" || token == "-") fail("expected a number")
        return token.toIntOrNull() ?: fail("number $token is too large")
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun skipSpace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun expect(c: Char) {
        if (peek() != c) fail("expected '$c'")
        pos++
    }

    private fun fail(message: String): Nothing {
        val found = peek()?.let { "'$it'" } ?: "end of input"
        throw InvalidArrangementException("$message at position ${pos + 1} (found $found)")
    }
}
