package org.bytefred.arrview.core

/**
 * An undirected (multi)graph on the vertices `0 until vertexCount`, as read from or written to
 * sparse6. Edges are pairs `(u, v)` with `u <= v`.
 */
data class SimpleGraphData(val vertexCount: Int, val edges: List<Pair<Int, Int>>)

/**
 * The sparse6 format of nauty (see `formats.txt` of nauty): ':' + N(n) + the edge bit vector.
 * Loops and multiple edges are allowed. The decoder is ported from rusty-arrangements
 * (`scripts/probe/model2.rs`), the encoder follows nauty (and SageMath's `sparse6_string`),
 * including the padding special case, so that encoding a decoded string gives it back.
 */
object Sparse6 {
    private const val HEADER = ">>sparse6<<"

    /**
     * Decodes one sparse6 string (an optional `>>sparse6<<` header, then ':').
     * @throws IllegalArgumentException if the string is not sparse6
     */
    fun decode(text: String): SimpleGraphData {
        val line = text.trim().removePrefix(HEADER)
        require(line.startsWith(":")) { "a sparse6 string starts with ':'" }
        val bytes = IntArray(line.length - 1) { idx ->
            val ch = line[idx + 1].code
            require(ch in 63..126) { "invalid sparse6 character '${line[idx + 1]}' at position ${idx + 2}" }
            ch - 63
        }
        require(bytes.isNotEmpty()) { "sparse6 string without vertex count" }
        val n: Int
        val restStart: Int
        if (bytes[0] < 63) {
            n = bytes[0]
            restStart = 1
        } else if (bytes.size >= 4 && bytes[1] < 63) {
            n = (bytes[1] shl 12) or (bytes[2] shl 6) or bytes[3]
            restStart = 4
        } else {
            require(bytes.size >= 8) { "sparse6 string too short for its vertex count" }
            var value = 0L
            for (idx in 2 until 8) value = (value shl 6) or bytes[idx].toLong()
            require(value <= Int.MAX_VALUE) { "too many vertices" }
            n = value.toInt()
            restStart = 8
        }
        val k = bitsFor(n)
        val bits = IntArray((bytes.size - restStart) * 6)
        for (idx in restStart until bytes.size) {
            for (b in 0 until 6) bits[(idx - restStart) * 6 + b] = (bytes[idx] shr (5 - b)) and 1
        }
        val edges = mutableListOf<Pair<Int, Int>>()
        var v = 0
        var pos = 0
        while (pos + 1 + k <= bits.size) {
            val b = bits[pos]
            var x = 0
            for (idx in pos + 1 until pos + 1 + k) x = (x shl 1) or bits[idx]
            pos += 1 + k
            if (b == 1) v++
            if (x >= n || v >= n) break
            if (x > v) v = x else edges.add(x to v)
        }
        return SimpleGraphData(n, edges)
    }

    /** Encodes a (multi)graph as sparse6 (with ':' but without header). */
    fun encode(graph: SimpleGraphData): String {
        val n = graph.vertexCount
        val edges = graph.edges
            .map { (a, b) -> if (a <= b) a to b else b to a }
            .onEach { (a, b) -> require(a >= 0 && b < n) { "edge ($a, $b) out of range" } }
            .sortedWith(compareBy({ it.second }, { it.first }))
        val k = bitsFor(n)
        val bits = mutableListOf<Int>()
        fun put(b: Int, x: Int) {
            bits.add(b)
            for (idx in k - 1 downTo 0) bits.add((x shr idx) and 1)
        }
        var v = 0
        for ((u, w) in edges) {
            when {
                w == v -> put(0, u)
                w == v + 1 -> {
                    put(1, u)
                    v = w
                }
                else -> {
                    put(1, w)
                    v = w
                    put(0, u)
                }
            }
        }
        val padding = (6 - bits.size % 6) % 6
        // nauty: if n = 2^k (k < 6), the last edge ends at n - 2 and there are at least k + 1
        // padding bits, then 1-padding would decode to an extra edge; pad with 0 then 1s
        if (k < 6 && n == (1 shl k) && v == n - 2 && padding >= k + 1) {
            bits.add(0)
            repeat(padding - 1) { bits.add(1) }
        } else {
            repeat(padding) { bits.add(1) }
        }
        val sb = StringBuilder(":")
        appendSize(sb, n)
        for (idx in bits.indices step 6) {
            var value = 0
            for (b in 0 until 6) value = (value shl 1) or bits[idx + b]
            sb.append((value + 63).toChar())
        }
        return sb.toString()
    }

    /** Number of bits to write `n - 1` (0 for n <= 1). */
    private fun bitsFor(n: Int): Int {
        var k = 0
        while ((1L shl k) < n) k++
        return k
    }

    private fun appendSize(sb: StringBuilder, n: Int) {
        when {
            n < 63 -> sb.append((n + 63).toChar())
            n < 258048 -> {
                sb.append('~')
                for (shift in intArrayOf(12, 6, 0)) sb.append((((n shr shift) and 63) + 63).toChar())
            }
            else -> {
                sb.append("~~")
                for (shift in intArrayOf(30, 24, 18, 12, 6, 0)) sb.append((((n shr shift) and 63) + 63).toChar())
            }
        }
    }
}
