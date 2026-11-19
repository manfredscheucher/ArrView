package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The triangle-saturated examples are read from the website's coloured drawings (`.ipe`); they are
 * the same arrangements as the website's model 1 files (test resources `data/colorings/`).
 */
class ExampleColoursDataTest {
    @Test
    fun drawingsAreTheWebsiteArrangements() {
        val sets = Examples.inGroup(ExampleGroup.COLORINGS).filter { it.hasColours }
        assertEquals(6, sets.size)
        for (s in sets) {
            val stem = s.source!!.substringAfterLast('/').substringBefore(".enc")
            val text = assertNotNull(javaClass.getResource("/data/colorings/$stem.enc"), stem).readText().trim()
            assertEquals(Model1.decode(text).canonical, s.arrangement(0).canonical, s.name)
        }
    }
}
