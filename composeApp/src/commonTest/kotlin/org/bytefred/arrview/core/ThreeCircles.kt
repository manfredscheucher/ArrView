package org.bytefred.arrview.core

/** The named arrangements of 3 pseudocircles, as test fixtures (no example group of their own). */
object ThreeCircles {
    val krupp: Arrangement = Arrangement.krupp()
    val nonKrupp: Arrangement = Arrangement.parse("[[2, -2, 3, -3], [-1, 1, 3, -3], [-1, 1, 2, -2]]")
    /** Circle 2 crosses circles 1 and 3, which do not cross. */
    val chain: Arrangement = Arrangement.parse("[[2, -2], [1, -1, 3, -3], [2, -2]]")
}
