package xendroid.compose.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * U02: every language has every text, with the same placeholders, so switching the phone to
 * Portuguese never shows a missing string or crashes a String.format.
 */
class StringResourcesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun strings(dir: String): Map<String, Pair<String, Boolean>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i) as Element
            node.getAttribute("name") to (node.textContent to (node.getAttribute("formatted") != "false"))
        }
    }

    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXc%]")

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test fun portugueseHasEveryTextWithTheSamePlaceholders() {
        val english = strings("values")
        val portuguese = strings("values-pt-rBR")
        assertEquals(english.keys, portuguese.keys)
        for ((name, en) in english) {
            val pt = portuguese.getValue(name)
            assertTrue("$name is empty", en.first.isNotBlank() && pt.first.isNotBlank())
            assertEquals("$name: formatted", en.second, pt.second)
            if (en.second) assertEquals("$name: placeholders", placeholders(en.first), placeholders(pt.first))
        }
    }

    @Test fun formattedTextsUsePositionalPlaceholdersAndEscapes() {
        for (dir in listOf("values", "values-pt-rBR")) {
            for ((name, value) in strings(dir)) {
                val (text, formatted) = value
                if (!formatted) continue
                val found = placeholders(text).filter { it != "%%" }
                // Several arguments must say which is which, or a translation cannot reorder them.
                if (found.size > 1) assertTrue("$dir/$name: use %1\$s, %2\$s…", found.all { it.contains('$') })
                // A lone % that is not a placeholder breaks String.format at run time.
                val stray = text.replace(placeholder, "")
                assertTrue("$dir/$name has a stray %", !stray.contains('%'))
                assertTrue("$dir/$name has an unescaped apostrophe", !Regex("(?<!\\\\)'").containsMatchIn(text))
            }
        }
    }

    @Test fun theAppDeclaresTheLanguagesItShips() {
        val config = File(res, "xml/locales_config.xml").readText()
        assertTrue(config.contains("android:name=\"en\"") && config.contains("android:name=\"pt-BR\""))
        assertTrue(File(res, "values-pt-rBR/strings.xml").isFile)
    }
}
