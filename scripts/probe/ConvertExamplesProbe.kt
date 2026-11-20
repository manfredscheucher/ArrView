package org.bytefred.arrview.core

import java.io.File
import kotlin.test.Test

/** One-off: writes composeApp/examples/ as JSON from the old catalogue (then removed). */
class ConvertExamplesProbe {
    private fun links(page: WebPage?, paper: Paper?): String {
        val l = listOfNotNull(
            page?.let { "{\"title\": ${jsonString("Website: " + it.title)}, \"url\": ${jsonString(it.url)}}" },
            paper?.let { "{\"title\": ${jsonString("Paper: " + it.title)}, \"url\": ${jsonString(it.url)}}" },
        )
        return if (l.isEmpty()) "[]" else l.joinToString(",\n", "[\n", "\n  ]") { "    $it" }
    }

    private fun lists(l: List<List<Any>>, indent: String?): String {
        fun one(x: List<Any>) = x.joinToString(", ", "[", "]") { if (it is String) jsonString(it) else it.toString() }
        return if (indent == null) l.joinToString(", ", "[", "]") { one(it) }
        else l.joinToString(",\n", "[\n", "\n  ]") { indent + one(it) }
    }

    private val tokens = listOf("R", "G", "B")

    @Test
    fun convert() {
        val root = File("examples")
        val index = StringBuilder("{\n  \"categories\": [\n")
        val groups = ExampleGroup.entries
        for ((gi, g) in groups.withIndex()) {
            val files = ArrayList<String>()
            for (s in Examples.inGroup(g)) {
                val path = when (val d = s.data) {
                    is ExampleSet.Data.File -> d.path.substringBeforeLast('/') + "/" + d.path.substringAfterLast('/').substringBefore('.') + ".json"
                    is ExampleSet.Data.Sequences -> "three/" + mapOf(
                        "n3-great-krupp" to "n3_krupp", "n3-intersecting-nonkrupp" to "n3_nonkrupp", "n3-connected-chain" to "n3_chain",
                    ).getValue(s.name) + ".json"
                }
                files += path
                val key = when (val d = s.data) {
                    is ExampleSet.Data.Sequences -> "sequences"
                    is ExampleSet.Data.File -> when {
                        d.path.endsWith(".seq") -> "sequences"
                        d.path.endsWith(".mod2s6") -> "mod2s6"
                        else -> "mod1s6"
                    }
                }
                fun members(i: Int, indent: String?): List<String> = buildList {
                    if (key == "sequences") {
                        val lists = Arrangement.parseLists(s.text(i))
                        add("\"sequences\": " + lists(lists, indent))
                        s.colours(i)?.let { c ->
                            var k = 0
                            val cl = lists.map { l -> l.map { tokens[c[k++]] } }
                            add("\"crossingColors\": " + lists(cl, indent))
                        }
                    } else add("\"$key\": ${jsonString(s.text(i))}")
                }
                val out = StringBuilder("{\n")
                out.append("  \"name\": ${jsonString(s.name)},\n")
                out.append("  \"n\": ${s.n},\n")
                out.append("  \"group\": ${jsonString(g.title)},\n")
                out.append("  \"label\": ${jsonString(s.label)},\n")
                out.append("  \"description\": ${jsonString(s.description)},\n")
                out.append("  \"links\": ${links(s.page, s.paper)},\n")
                out.append("  \"source\": ${s.source?.let(::jsonString) ?: "null"},\n")
                if (s.size == 1) out.append(members(0, "    ").joinToString(",\n") { "  $it" })
                else out.append("  \"arrangements\": [\n" + (0 until s.size).joinToString(",\n") { "    {" + members(it, null).joinToString(", ") + "}" } + "\n  ]")
                out.append("\n}\n")
                File(root, path).apply { parentFile.mkdirs() }.writeText(out.toString())
            }
            index.append("    {\n")
            index.append("      \"id\": ${jsonString(g.name.lowercase().replace('_', '-'))},\n")
            index.append("      \"title\": ${jsonString(g.title)},\n")
            index.append("      \"description\": ${jsonString(g.description)},\n")
            index.append("      \"links\": ${links(g.page, g.paper).replace("\n", "\n    ")},\n")
            index.append("      \"files\": [\n" + files.joinToString(",\n") { "        ${jsonString(it)}" } + "\n      ]\n")
            index.append("    }" + (if (gi < groups.size - 1) "," else "") + "\n")
        }
        index.append("  ]\n}\n")
        File(root, "index.json").writeText(index.toString())
    }
}
