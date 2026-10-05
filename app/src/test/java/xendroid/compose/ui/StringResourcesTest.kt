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

    /** Every strings*.xml of [dir]: the redesign keeps its texts in strings_xd.xml. */
    private fun files(dir: String): List<File> =
        File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().sortedBy { it.name }

    private fun strings(dir: String): Map<String, Pair<String, Boolean>> = files(dir).flatMap { file ->
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).map { i ->
            val node = nodes.item(i) as Element
            node.getAttribute("name") to (node.textContent to (node.getAttribute("formatted") != "false"))
        }
    }.toMap()

    /** Plural name to its items by quantity ("one", "other"…). */
    private fun plurals(dir: String): Map<String, Map<String, String>> = files(dir).flatMap { file ->
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("plurals")
        (0 until nodes.length).map { i ->
            val node = nodes.item(i) as Element
            val items = node.getElementsByTagName("item")
            node.getAttribute("name") to (0 until items.length).associate { j ->
                val item = items.item(j) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
    }.toMap()

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

    /** aapt drops spaces at either end of a text, so a separator there silently disappears. */
    @Test fun noTextStartsOrEndsWithASpace() {
        for (dir in listOf("values", "values-pt-rBR")) {
            for ((name, value) in strings(dir)) {
                assertTrue("$dir/$name starts or ends with a space", value.first == value.first.trim())
            }
        }
    }

    /** aapt folds a line break typed inside a text into a space; a new line is written \n. */
    @Test fun noTextHasARawLineBreak() {
        for (dir in listOf("values", "values-pt-rBR")) {
            for ((name, value) in strings(dir)) {
                assertTrue("$dir/$name has a raw line break (write \\n)", !value.first.contains('\n'))
            }
        }
    }

    /** U02: a count's word agrees with it in both languages ("1 game", "2 games"); every form
     *  has the same placeholders, so whatever form Android picks formats the same arguments. */
    @Test fun pluralsHaveTheFormsEachLanguageNeeds() {
        val english = plurals("values")
        val portuguese = plurals("values-pt-rBR")
        assertTrue("there are plurals", english.isNotEmpty())
        assertEquals(english.keys, portuguese.keys)
        for ((name, forms) in english) {
            val pt = portuguese.getValue(name)
            assertTrue("$name: en needs one and other", forms.keys.containsAll(listOf("one", "other")))
            assertTrue("$name: pt-BR needs one and other", pt.keys.containsAll(listOf("one", "other")))
            val expected = placeholders(forms.getValue("other"))
            for ((dir, items) in listOf("values" to forms, "values-pt-rBR" to pt)) {
                for ((quantity, text) in items) {
                    val where = "$dir/$name[$quantity]"
                    assertTrue("$where is empty", text.isNotBlank())
                    assertEquals("$where: placeholders", expected, placeholders(text))
                    if (expected.size > 1) assertTrue("$where: use %1\$s, %2\$s…", expected.all { it.contains('$') })
                    assertTrue("$where has a stray %", !text.replace(placeholder, "").contains('%'))
                    assertTrue("$where has an unescaped apostrophe", !Regex("(?<!\\\\)'").containsMatchIn(text))
                    assertTrue("$where starts or ends with a space", text == text.trim())
                    assertTrue("$where has a raw line break", !text.contains('\n'))
                }
            }
        }
    }

    @Test fun theAppDeclaresTheLanguagesItShips() {
        val config = File(res, "xml/locales_config.xml").readText()
        assertTrue(config.contains("android:name=\"en\"") && config.contains("android:name=\"pt-BR\""))
        assertTrue(File(res, "values-pt-rBR/strings.xml").isFile)
    }
}
