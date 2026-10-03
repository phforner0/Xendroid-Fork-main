package xendroid.compose.settings

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AppLanguageTest {
    @Test fun nothingChosenFollowsThePhone() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(" , "))
    }

    @Test fun theFirstLanguageTheAppHasWins() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-GB"))
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.fromTags("pt-BR"))
        // Any Portuguese reads the Brazilian texts, the only Portuguese ones.
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.fromTags("pt-PT"))
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.fromTags("pt-BR,en"))
        // German first, which the app lacks: Android shows the next one, English.
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("de-DE,en-US"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("fr,ja"))
    }

    @Test fun everyChoiceIsALanguageTheAppShipsAndOffersTheSystem() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "xml/locales_config.xml"))
        val nodes = doc.getElementsByTagName("locale")
        val offered = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }.toSet()
        val chosen = AppLanguage.entries.mapNotNull { it.tag }.toSet()
        assertEquals(offered, chosen)
        // English lives in values/, every other language in its own folder.
        for (tag in chosen - "en") {
            val locale = Locale.forLanguageTag(tag)
            val folder = File(res, "values-${locale.language}" + if (locale.country.isEmpty()) "" else "-r${locale.country}")
            assertTrue("$folder", File(folder, "strings.xml").isFile)
        }
        AppLanguage.entries.forEach { assertEquals(it == AppLanguage.SYSTEM, it.autonym == null) }
    }
}
