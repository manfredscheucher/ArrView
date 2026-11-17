package org.bytefred.arrview.core

/**
 * The symmetries of an arrangement: the automorphisms of its map (the primal graph on the sphere,
 * mirror images included), as permutations of the darts. They keep the circles, since a circle
 * goes straight through every crossing. An automorphism is fixed by the image of one dart and
 * whether it reverses the orientation, so trying every dart as the image of dart 0 finds all of
 * them (each try fails fast or succeeds after one pass over the darts).
 */
internal object Symmetry {
    fun automorphisms(arr: Arrangement): List<IntArray> {
        val darts = arr.dartCount
        if (darts == 0) return emptyList()
        fun sigmaInverse(a: Int) = arr.sigma(arr.sigma(arr.sigma(a)))
        val result = ArrayList<IntArray>()
        for (mirror in listOf(false, true)) {
            for (target in 0 until darts) {
                tryMap(arr, target, mirror, ::sigmaInverse)?.let(result::add)
            }
        }
        return result
    }

    /** The automorphism mapping dart 0 to [target] (reversing the orientation if [mirror]), or null. */
    private fun tryMap(arr: Arrangement, target: Int, mirror: Boolean, sigmaInverse: (Int) -> Int): IntArray? {
        val darts = arr.dartCount
        val phi = IntArray(darts) { -1 }
        val used = BooleanArray(darts)
        phi[0] = target
        used[target] = true
        val stack = ArrayDeque(listOf(0))
        while (stack.isNotEmpty()) {
            val a = stack.removeLast()
            val b = phi[a]
            val pairs = arrayOf(
                arr.alpha(a) to arr.alpha(b),
                arr.sigma(a) to if (mirror) sigmaInverse(b) else arr.sigma(b),
            )
            for ((x, y) in pairs) {
                if (phi[x] == -1) {
                    if (used[y]) return null
                    phi[x] = y
                    used[y] = true
                    stack.addLast(x)
                } else if (phi[x] != y) {
                    return null
                }
            }
        }
        return phi.takeIf { it.all { d -> d >= 0 } }
    }
}

/**
 * The face drawn outside by default: a largest face, and among those one with the most symmetries
 * (automorphisms of the arrangement mapping the face to itself, as `draw7.sage` chooses); the
 * first of several. In model 1 a digon cannot be outside.
 */
fun Arrangement.defaultOuterFace(model: DrawModel): Int {
    val allowed = faces.indices.filter { canBeOuter(it, model) }
    val largest = allowed.maxOf { faces[it].size }
    val candidates = allowed.filter { faces[it].size == largest }
    if (candidates.size == 1) return candidates[0]
    val arcsOf = faces.map { darts -> darts.map(::arcOfDart).toSet() }
    val symmetries = candidates.associateWith { f ->
        automorphisms.count { phi -> faces[f].all { a -> arcOfDart(phi[a]) in arcsOf[f] } }
    }
    return candidates.maxBy { symmetries.getValue(it) }
}

/** The arc (crossing `k` to the next one on its circle) that dart [a] runs along. */
fun Arrangement.arcOfDart(a: Int): Int = if (a % 2 == 0) a / 2 else previousOnCircle(a / 2)
