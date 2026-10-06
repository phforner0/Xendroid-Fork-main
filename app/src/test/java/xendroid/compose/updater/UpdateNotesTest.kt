package xendroid.compose.updater

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateNotesTest {
    // GitHub's generated notes, as builds 35 and 37 were published.
    private val generated = """
        ## What's Changed
        * Geração de quadros em qualquer build by @phforner0 in https://github.com/phforner0/Xendroid-Plus/pull/17
        * Áudio sem estalos by @someone-else in https://github.com/phforner0/Xendroid-Plus/pull/18

        ## New Contributors
        * @someone-else made their first contribution in https://github.com/phforner0/Xendroid-Plus/pull/18


        **Full Changelog**: https://github.com/phforner0/Xendroid-Plus/compare/a...b
    """.trimIndent()

    // The start of notes from tools/release_notes.py.
    private val written = """
        Frame generation works in every build.

        * Win-FG doubles a 30 fps game to 60 (#17)
        * The HUD shows the generated rate.

        **Full Changelog**: https://github.com/phforner0/Xendroid-Plus/compare/a...b

        <!-- update-summary:en
        * Win-FG doubles a 30 fps game to 60
        * The HUD shows the generated rate.
        -->
        <!-- update-summary:pt-BR
        * O Win-FG dobra um jogo de 30 fps para 60
        * O HUD mostra a taxa gerada.
        -->

        ## What's new

        ### 🎞️ Frame generation
    """.trimIndent()

    @Test fun generatedNotesListThePullRequestTitles() {
        assertEquals(listOf("Geração de quadros em qualquer build", "Áudio sem estalos"), updateNotes(generated, "en-US"))
    }

    @Test fun writtenNotesShowTheSummaryInTheAppsLanguage() {
        val portuguese = listOf("O Win-FG dobra um jogo de 30 fps para 60", "O HUD mostra a taxa gerada.")
        val english = listOf("Win-FG doubles a 30 fps game to 60", "The HUD shows the generated rate.")
        assertEquals(portuguese, updateNotes(written, "pt-BR"))
        assertEquals(portuguese, updateNotes(written, "pt-PT"))  // the language, from another region
        assertEquals(english, updateNotes(written, "en-US"))
        assertEquals(english, updateNotes(written, "fr-FR"))  // English for every other language
    }

    @Test fun otherWrittenNotesAreReadAsTextUpToTheFullChangelog() {
        val notes = """
            <p align="center"><img src="https://img.shields.io/badge/x-y-blue" alt="x"></p>

            Builds faster.

            - **Quick:** shaders [compile](https://example.com/a) in the `background`.
            > ✅ Nothing to set up.
            ### Fixed
            <details><summary>Every change</summary>
            - hidden
            </details>

            **Full Changelog**: https://example.com/compare
            - after the cut
        """.trimIndent()
        assertEquals(listOf("Builds faster.", "Quick: shaders compile in the background.", "✅ Nothing to set up."),
            updateNotes(notes, "en"))
    }
}
