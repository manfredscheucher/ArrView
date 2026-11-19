package org.bytefred.arrview.core

/** PROBE: does classic converge when it runs longer, and where does the accelerated limit scheme land? */
fun limitMain() {
    val cases = listOf(
        Examples.start.arrangement() to DrawModel.MODEL1, Examples.start.arrangement() to DrawModel.MODEL2,
        Examples.byName("n9-great-nonr-pappus")!!.arrangement(0) to DrawModel.MODEL2,
        Examples.byName("n5-intersecting-all")!!.arrangement(0) to DrawModel.MODEL2,
        Examples.byName("n5-connected-all")!!.arrangement(0) to DrawModel.MODEL1,
    )
    for ((arr, model) in cases) {
        val layout = ArrangementLayout(arr, model, arr.defaultOuterFace(model))
        fun ctrl(s: LayoutSettings, face: ((Int) -> Int)? = null, dist: Boolean = true): Pair<Int, List<List<Point>>> {
            val t = IteratedTutte(layout.problem, s, face, dist, true, 5)
            val p = t.run()
            return t.iteration to layout.controlPoints(p)
        }
        fun dev(a: List<List<Point>>, b: List<List<Point>>) = a.indices.maxOf { c -> a[c].indices.maxOf { k -> (a[c][k] - b[c][k]).length } }
        val c = LayoutImplementation.CLASSIC
        val runs = listOf(50, 100, 200, 400, 1000).map { it to ctrl(LayoutSettings(it, 0.0, c)).second }
        val last = runs.last().second
        val limit = ctrl(LayoutSettings(300, 1e-13, LayoutImplementation.OPTIMIZED), { 1 }, false)
        println("${arr.lists.size} $model: classic eps0 dev to it1000: " + runs.joinToString { "${it.first}:%.1e".format(dev(it.second, last)) } +
            " | limitScheme(${limit.first} it) vs 1000: %.1e".format(dev(limit.second, last)) +
            " | classic100/1e-10 stops at ${ctrl(LayoutSettings(100, 1e-10, c)).first}")
    }
}
