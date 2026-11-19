package org.bytefred.arrview.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ArrView solves the Tutte system with warm-started conjugate gradients; sage takes
 * `M.pseudoinverse() * b`. For the regular system both are the direct solution, compared here after
 * every iteration with Gaussian elimination on sage's matrix.
 */
class TutteSolveTest {
    @Test
    fun conjugateGradientsGiveTheDirectSolution() {
        val cases = listOf(Examples.start.arrangement(), Arrangement.krupp(), Arrangement.two()) +
            Examples.byName("n4-connected-all")!!.let { s -> listOf(s.arrangement(0), s.arrangement(s.size - 1)) } +
            // larger ones: up to 290 vertices (Pappus, model 2)
            listOf("n9-great-nonr-pappus", "n8-great-all", "n12-great-fueredi-palasti").map { Examples.byName(it)!!.arrangement(0) }
        for (arr in cases) for (model in DrawModel.entries) {
            if (model == DrawModel.MODEL1 && !arr.hasModel1) continue
            val problem = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).problem
            val tutte = IteratedTutte(problem, LayoutSettings(maxIterations = 50, eps = 0.0))
            while (!tutte.step()) check(tutte.positions, directSolve(problem, tutte.currentWeights), "$model ${arr.lists} #${tutte.iteration}")
            check(tutte.positions, directSolve(problem, tutte.currentWeights), "$model ${arr.lists} last")
        }
    }

    private fun check(cg: DoubleArray, direct: DoubleArray, what: String) {
        val scale = direct.maxOf { abs(it) }
        val err = cg.indices.maxOf { abs(cg[it] - direct[it]) }
        assertTrue(err <= 1e-9 * scale, "$what: difference $err")
    }

    /** sage's `tutte_layout`: rows of the outer vertices are the identity, else `sum w` on the diagonal and `-w` per neighbour. */
    private fun directSolve(p: TutteProblem, w: DoubleArray): DoubleArray {
        val n = p.vertexCount
        val l = p.outer.size
        val m = Array(n) { DoubleArray(n) }
        val b = Array(n) { DoubleArray(2) }
        val fixed = BooleanArray(n)
        for ((i, v) in p.outer.withIndex()) {
            val a = kotlin.math.PI / l + kotlin.math.PI / 2 + 2 * kotlin.math.PI * i / l
            m[v][v] = 1.0
            b[v][0] = kotlin.math.cos(a)
            b[v][1] = kotlin.math.sin(a)
            fixed[v] = true
        }
        for (e in 0 until p.edgeCount) {
            val u = p.edges[2 * e]
            val v = p.edges[2 * e + 1]
            for ((x, y) in listOf(u to v, v to u)) if (!fixed[x]) {
                m[x][x] += w[e]
                m[x][y] -= w[e]
            }
        }
        // Gaussian elimination with partial pivoting
        for (col in 0 until n) {
            val piv = (col until n).maxBy { abs(m[it][col]) }
            m[col] = m[piv].also { m[piv] = m[col] }
            b[col] = b[piv].also { b[piv] = b[col] }
            for (r in col + 1 until n) {
                val f = m[r][col] / m[col][col]
                if (f == 0.0) continue
                for (c in col until n) m[r][c] -= f * m[col][c]
                for (k in 0..1) b[r][k] -= f * b[col][k]
            }
        }
        val x = DoubleArray(2 * n)
        for (r in n - 1 downTo 0) for (k in 0..1) {
            var s = b[r][k]
            for (c in r + 1 until n) s -= m[r][c] * x[2 * c + k]
            x[2 * r + k] = s / m[r][r]
        }
        return x
    }
}
