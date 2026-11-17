package org.bytefred.arrview.core

import kotlin.math.abs

/**
 * Validity of intersection schemes after J. Linhart and R. Ortner, "On the combinatorial
 * structure of arrangements of oriented pseudocircles", EJC 11 (2004), #R30. Every check returns
 * null if it passes, otherwise a human-readable reason naming the condition and the circles.
 *
 * Checked in this order:
 * 1. Definition 2.4 (i)–(iii): allowed entries, no repeated entries, `+k` in list `i` iff `-i`
 *    in list `k`.
 * 2. Definition 2.5 (v), pure: `+k` occurs in a list iff `-k` does (so two circles cross twice
 *    or not at all).
 * 3. Definition 2.4 (iv): any two crossings are joined by a walk (the arrangement is connected).
 * 4. Theorem 5.4 / Definition 5.1: the scheme is consistent.
 * 5. Theorem 4.3 for genus 0 (= Theorem 4.5 (i)): Euler characteristic `V - E + F = 2` of the
 *    cell complex from the face algorithm ([Arrangement.faces]).
 *
 * On the sphere, 5 alone decides: a scheme passing 1 and 3 is representable on the sphere iff
 * `V - E + F = 2` (Thm. 4.3), and then every pseudocircle separates, so 2 and 4 hold as well. They
 * are checked first because their reasons are more specific. Theorem 4.5 (ii) ("some crossing is
 * not within any circle") is not checked: it characterizes drawings in the plane with all circles
 * oriented counterclockwise, while here the orientations are arbitrary.
 *
 * Circles are reported 1-based, as in the text format.
 */
object LinhartOrtner {
    /** The full check of [lists] (entries `±(d+1)`); null if valid. */
    fun check(lists: List<List<Int>>): String? =
        checkShape(lists) ?: checkTopology(Arrangement.unchecked(lists))

    /** Checks 1–3 (the combinatorial conditions on the lists). */
    fun checkShape(lists: List<List<Int>>): String? {
        val n = lists.size
        if (n < 2) return "an arrangement needs at least 2 pseudocircles, found $n"
        // plus[i][k] / minus[i][k]: entry +(k+1) / -(k+1) occurs in list i
        val plus = Array(n) { BooleanArray(n) }
        val minus = Array(n) { BooleanArray(n) }
        for ((i, list) in lists.withIndex()) {
            for (e in list) {
                val k = abs(e) - 1
                if (e == 0 || k >= n || k == i) {
                    return "circle ${i + 1}: entry $e is not allowed (Linhart–Ortner Def. 2.4 (i): " +
                        "the entries of list ${i + 1} are ±1..±$n without ±${i + 1})"
                }
                val seen = if (e > 0) plus[i] else minus[i]
                if (seen[k]) {
                    return "circle ${i + 1}: entry $e occurs twice (Linhart–Ortner Def. 2.4 (ii))"
                }
                seen[k] = true
            }
        }
        for (i in 0 until n) {
            for (k in 0 until n) {
                if (plus[i][k] != minus[k][i]) {
                    val reason = if (plus[i][k]) {
                        "+${k + 1} is in list ${i + 1}, but -${i + 1} is not in list ${k + 1}"
                    } else {
                        "-${i + 1} is in list ${k + 1}, but +${k + 1} is not in list ${i + 1}"
                    }
                    return "circles ${i + 1} and ${k + 1} do not match: $reason (Linhart–Ortner Def. 2.4 (iii))"
                }
            }
        }
        for (i in 0 until n) {
            for (k in 0 until n) {
                if (plus[i][k] != minus[i][k]) {
                    val (has, lacks) = if (plus[i][k]) "+${k + 1}" to "-${k + 1}" else "-${k + 1}" to "+${k + 1}"
                    return "circle ${i + 1}: entry $has without $lacks, so circles ${i + 1} and ${k + 1} " +
                        "do not cross exactly twice (Linhart–Ortner Def. 2.5 (v), pure)"
                }
            }
        }
        // Def. 2.4 (iv): with (iii), crossings are joined by walks iff the circles are joined by
        // crossings (the intersection graph is connected) and every circle has a crossing.
        for (i in 0 until n) {
            if (lists[i].isEmpty()) {
                return "circle ${i + 1} crosses no other circle, the arrangement is not connected " +
                    "(Linhart–Ortner Def. 2.4 (iv))"
            }
        }
        val reached = BooleanArray(n)
        val stack = ArrayDeque(listOf(0))
        reached[0] = true
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            for (d in 0 until n) {
                if (plus[c][d] && !reached[d]) {
                    reached[d] = true
                    stack.addLast(d)
                }
            }
        }
        if (!reached.all { it }) {
            val (a, b) = (0 until n).partition { reached[it] }
            return "the arrangement is not connected: circles ${labels(a)} do not cross circles ${labels(b)} " +
                "(Linhart–Ortner Def. 2.4 (iv))"
        }
        return null
    }

    /** Checks 4 and 5 on an arrangement whose lists passed [checkShape]. */
    fun checkTopology(arrangement: Arrangement): String? =
        checkConsistency(arrangement) ?: checkEuler(arrangement)

    private fun checkConsistency(arr: Arrangement): String? {
        for (j in 0 until arr.n) {
            val side = sides(arr, j)
            val component = componentsAvoiding(arr, j)
            val insideOf = mutableMapOf<Int, Int>()
            val outsideOf = mutableMapOf<Int, Int>()
            for (k in 0 until arr.size) {
                when (side[k]) {
                    INSIDE -> insideOf.getOrPut(component[k]) { k }
                    OUTSIDE -> outsideOf.getOrPut(component[k]) { k }
                }
            }
            for ((comp, k) in insideOf) {
                val other = outsideOf[comp] ?: continue
                return "inconsistent at circle ${j + 1}: ${describe(arr, k)} (between +${j + 1} and -${j + 1}) " +
                    "and ${describe(arr, other)} (between -${j + 1} and +${j + 1}) are joined by a walk " +
                    "that avoids circle ${j + 1} (Linhart–Ortner Def. 5.1, Thm. 5.4)"
            }
        }
        return null
    }

    /** Check 5 alone (internal for tests: after check 4 it never failed on the schemes tried). */
    internal fun checkEuler(arr: Arrangement): String? {
        val v = arr.crossingCount
        val e = arr.size
        val f = arr.faceCount
        val chi = v - e + f
        if (chi == 2) return null
        val genus = if (chi % 2 == 0 && chi < 2) ", it needs a surface of genus ${(2 - chi) / 2}" else ""
        return "Euler characteristic V - E + F = $v - $e + $f = $chi is not 2, so the scheme cannot be " +
            "drawn on the sphere$genus (Linhart–Ortner Thm. 4.3)"
    }

    private const val INSIDE = 1
    private const val OUTSIDE = 2

    /**
     * For circle [j]: whether each entry lies strictly between `+(j+1)` and `-(j+1)` (in that
     * cyclic order) in its list ([INSIDE]), strictly between `-(j+1)` and `+(j+1)` ([OUTSIDE]),
     * or neither (0: an entry `±(j+1)` itself, or a list without circle j).
     */
    private fun sides(arr: Arrangement, j: Int): IntArray {
        val side = IntArray(arr.size)
        for (c in 0 until arr.n) {
            val off = arr.offset(c)
            val len = arr.lengthOf(c)
            val start = (0 until len).firstOrNull { arr.entry(off + it) == j + 1 } ?: continue
            var current = INSIDE
            for (step in 1 until len) {
                val k = off + (start + step) % len
                val e = arr.entry(k)
                if (e == -(j + 1)) current = OUTSIDE else side[k] = current
            }
        }
        return side
    }

    /**
     * Connected components of the walk graph of Definition 2.3 (entries joined to their list
     * neighbours and to their partner) without the entries `±(j+1)`. Removed entries get -1.
     */
    private fun componentsAvoiding(arr: Arrangement, j: Int): IntArray {
        val parent = IntArray(arr.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var y = x
            while (parent[y] != r) {
                val next = parent[y]
                parent[y] = r
                y = next
            }
            return r
        }
        fun allowed(k: Int) = abs(arr.entry(k)) != j + 1
        fun union(a: Int, b: Int) {
            if (allowed(a) && allowed(b)) parent[find(a)] = find(b)
        }
        val partners = arr.partners
        for (k in 0 until arr.size) {
            union(k, arr.nextOnCircle(k))
            union(k, partners[k])
        }
        return IntArray(arr.size) { if (allowed(it)) find(it) else -1 }
    }

    private fun describe(arr: Arrangement, k: Int): String {
        val e = arr.entry(k)
        return "entry ${if (e > 0) "+" else ""}$e in list ${arr.circleOfCrossing(k) + 1}"
    }

    private fun labels(circles: List<Int>): String = circles.joinToString(", ", "{", "}") { "${it + 1}" }
}
