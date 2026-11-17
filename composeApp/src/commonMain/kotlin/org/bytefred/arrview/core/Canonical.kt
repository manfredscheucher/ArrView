package org.bytefred.arrview.core

/**
 * Canonical form under relabelling, reorientation and mirroring. Ported from rusty-arrangements
 * (`src/canonical.rs`, the relabelling of `CanonBuf::relabel` / `compare_start`), without its
 * invariant keys: the canonical code is the lexicographic minimum over all starts of the list
 * lengths in label order followed by the concatenated lists.
 *
 * A start is a crossing `i` on a circle `s`, a direction `rs` along `s` and a mirror flag `m`
 * (`±1`). Given a start, everything else is forced: `s` gets label 1 and the circles are walked
 * in label order, each from its start in its direction; a circle met for the first time gets the
 * next free label, is oriented such that it crosses the walked circle from right to left there
 * (sign `+1`), and its list starts at that crossing. In a connected arrangement the walk reaches
 * every circle. Transformed signs: `sign'(c, d) = sign(c, d) * r_c * r_d * m` with `r = -1` for
 * reversed circles and `m = -1` for the mirror.
 */
internal object Canonical {
    fun of(arr: Arrangement): Arrangement {
        val relabelling = Relabelling(arr)
        val best = IntArray(arr.size)
        val code = IntArray(arr.size)
        var bestLengths = IntArray(0)
        var first = true
        for (s in 0 until arr.n) {
            for (i in 0 until arr.lengthOf(s)) {
                for (rs in intArrayOf(1, -1)) {
                    for (m in intArrayOf(1, -1)) {
                        relabelling.code(s, i, rs, m, code)
                        val lengths = relabelling.lengthsInLabelOrder()
                        val order = if (first) -1 else compare(lengths, bestLengths).takeIf { it != 0 } ?: compare(code, best)
                        if (order < 0) {
                            code.copyInto(best)
                            bestLengths = lengths
                            first = false
                        }
                    }
                }
            }
        }
        var k = 0
        val lists = bestLengths.map { len -> List(len) { best[k++] } }
        return Arrangement.unchecked(lists)
    }

    /** Lexicographic comparison of two arrays of the same size. */
    private fun compare(a: IntArray, b: IntArray): Int {
        for (k in a.indices) {
            if (a[k] != b[k]) return a[k].compareTo(b[k])
        }
        return 0
    }

    /** The relabelling forced by a start (buffers reused over all starts). */
    private class Relabelling(private val arr: Arrangement) {
        private val n = arr.n
        private val partners = arr.partners
        private val label = IntArray(n)
        private val rev = IntArray(n)
        private val start = IntArray(n)
        private val circleAt = IntArray(n)

        /** Writes the code of start ([s], [i], [rs], [m]) into [out]. */
        fun code(s: Int, i: Int, rs: Int, m: Int, out: IntArray) {
            label.fill(-1)
            label[s] = 0
            circleAt[0] = s
            rev[s] = rs
            start[s] = i
            var next = 1
            var idx = 0
            for (j in 0 until n) {
                // connected: circle j is labelled before the walk reaches it
                val c = circleAt[j]
                val off = arr.offset(c)
                val len = arr.lengthOf(c)
                val rc = rev[c]
                for (k in 0 until len) {
                    val p = off + step(start[c], k, rc, len)
                    val e = arr.entry(p)
                    val d = Arrangement.circleOf(e)
                    val sign = if (e > 0) 1 else -1
                    if (label[d] < 0) {
                        label[d] = next
                        circleAt[next] = d
                        next++
                        // make sign'(c, d) = +1
                        rev[d] = sign * rc * m
                        start[d] = partners[p] - arr.offset(d)
                    }
                    out[idx++] = sign * rc * rev[d] * m * (label[d] + 1)
                }
            }
        }

        /** List lengths in label order for the last [code]. */
        fun lengthsInLabelOrder(): IntArray = IntArray(n) { arr.lengthOf(circleAt[it]) }

        private fun step(i: Int, k: Int, r: Int, len: Int): Int =
            if (r > 0) (i + k) % len else (i + len - k % len) % len
    }
}
