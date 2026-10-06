#!/usr/bin/env python3
"""release_notes.py without the network: blocks parsed and merged, the page's order, the
fallbacks, and what the update cards of earlier and later builds read."""
import re
import unittest

from release_notes import PullRequest, Apk, notes_of, render

FG = """Description for reviewers.
<!-- release-notes:en -->
<!-- hint for the author, not a note -->
Frame generation works in every build.
- Win-FG doubles a 30 fps game to 60.
- The HUD shows the generated rate.
> ✅ **Nothing to set up.** Turn it on in the in-game menu.
### 🎞️ Frame generation
- Win-FG no longer hangs the Adreno 825.
- LSFG runs at 29 to 57-58 fps.
### 🛠️ Fixed
- A real frame was lost when a generated one came late.
### ⚠️ Still open
- LSFG needs the player's Lossless.dll.
<!-- /release-notes:en -->
<!-- release-notes:pt-BR -->
A geração de quadros funciona em qualquer build.
- O Win-FG dobra um jogo de 30 fps para 60.
### 🎞️ Geração de quadros
- O Win-FG não trava mais o Adreno 825.
<!-- /release-notes:pt-BR -->
"""

AUDIO = """<!-- release-notes:en -->
- Audio gaps fade instead of clicking.
### 🛠️ Fixed
- OpenSL ES: the media player played noise.
### 🔊 Audio
- The AAudio stream reopens on a new output device.
<!-- /release-notes:en -->
"""


def pr(number, body, title="A change", commits=(("a" * 40, "Commit subject"),)):
    return PullRequest(number, title, "phforner0", f"https://github.com/o/r/pull/{number}", list(commits), notes_of(body))


class ReleaseNotesTest(unittest.TestCase):
    def page(self, prs, apk=None):
        return render(prs, "o/r", "XenDroid-v39-abcdef12", "abcdef12" + "0" * 32, "39",
                      "XenDroid-v37-710b1e81", "https://github.com/o/r/actions/runs/1", apk, 7)

    def test_blocks_are_parsed_without_hints(self):
        notes = notes_of(FG)
        self.assertEqual(set(notes), {"en", "pt-BR"})
        en = notes["en"]
        self.assertEqual(en.summary, ["Frame generation works in every build."])
        self.assertEqual(en.highlights, ["Win-FG doubles a 30 fps game to 60.", "The HUD shows the generated rate."])
        self.assertEqual([h for h, _ in en.sections], ["🎞️ Frame generation", "🛠️ Fixed", "⚠️ Still open"])
        self.assertFalse(any("hint" in line for _, lines in en.sections for line in lines))
        # A block left as the template's hints is no notes at all.
        self.assertEqual(notes_of("<!-- release-notes:en -->\n<!-- write here -->\n<!-- /release-notes:en -->"), {})

    def test_the_page_order_and_references(self):
        page = self.page([pr(17, FG), pr(18, AUDIO), pr(19, "No notes here.", title="Internal cleanup")])
        en = page.split("<details>")[0]
        # Summary and highlights first, then the cut for earlier builds' update card.
        head, rest = en.split("**Full Changelog**: https://github.com/o/r/compare/XenDroid-v37-710b1e81...XenDroid-v39-abcdef12")
        self.assertTrue(head.startswith("Frame generation works in every build.\n\n* Win-FG doubles"))
        self.assertIn("* Audio gaps fade instead of clicking.", head)
        self.assertNotIn("##", head)
        # Sections merged across pull requests, Fixed after the others, Still open last.
        order = re.findall(r"^#{2,3} (.+)$", rest, re.M)
        self.assertEqual(order, ["What's new", "🎞️ Frame generation", "🔊 Audio", "🛠️ Fixed", "Other changes", "⚠️ Still open"])
        fixed = rest.split("### 🛠️ Fixed")[1].split("###")[0]
        self.assertIn("- A real frame was lost when a generated one came late. (#17)", fixed)
        self.assertIn("- OpenSL ES: the media player played noise. (#18)", fixed)
        self.assertIn("- Internal cleanup (#19)", rest.split("### Other changes")[1])
        self.assertIn("> ✅ **Nothing to set up.**", rest)

    def test_a_marker_quoted_in_the_description_is_not_a_block(self):
        body = ("The notes go between `<!-- release-notes:en -->` and its closing marker.\n"
                "- not a highlight\n" + FG)
        en = notes_of(body)["en"]
        self.assertEqual(en.summary, ["Frame generation works in every build."])
        self.assertNotIn("not a highlight", en.highlights)

    def test_one_pull_request_names_none(self):
        page = self.page([pr(17, FG)])
        self.assertIn("- Win-FG no longer hangs the Adreno 825.\n", page)
        self.assertNotIn("(#17)", page.split("<summary><b>📜")[0])

    def test_portuguese_falls_back_to_english(self):
        page = self.page([pr(17, FG), pr(18, AUDIO)])
        pt = page.split("<summary><b>🇧🇷 Em português</b></summary>")[1].split("</details>")[0]
        self.assertIn("A geração de quadros funciona em qualquer build.", pt)
        self.assertIn("#### 🎞️ Geração de quadros", pt)
        # The audio change has no Portuguese: its English stands in.
        self.assertIn("- Audio gaps fade instead of clicking.", pt)
        self.assertIn("#### 🔊 Audio", pt)

    def test_update_summaries_are_plain_text(self):
        page = self.page([pr(17, FG)])
        en = re.search(r"<!-- update-summary:en\n(.*?)-->", page, re.S).group(1)
        pt = re.search(r"<!-- update-summary:pt-BR\n(.*?)-->", page, re.S).group(1)
        self.assertEqual(en, "* Win-FG doubles a 30 fps game to 60.\n* The HUD shows the generated rate.\n")
        self.assertEqual(pt, "* O Win-FG dobra um jogo de 30 fps para 60.\n")
        # Earlier builds' update card stops at the Full Changelog line, before them.
        self.assertLess(page.index("**Full Changelog**"), page.index("<!-- update-summary"))

    def test_without_notes_the_titles_are_the_highlights(self):
        page = self.page([pr(20, "", title="Fix the thing"), pr(21, None, title="Tune the other")])
        self.assertTrue(page.startswith("* Fix the thing (#20)\n* Tune the other (#21)\n"))
        # Said once: no Other changes section, no Portuguese copy of the same titles.
        self.assertEqual(page.count("Fix the thing"), 4)  # highlight, two update summaries, every change
        self.assertNotIn("Other changes", page)
        self.assertNotIn("Em português", page)

    def test_long_pull_requests_list_forty_commits(self):
        commits = [(f"{i:040x}", f"Commit {i}") for i in range(100)]
        many = PullRequest(16, "Big", "phforner0", "https://github.com/o/r/pull/16", commits, {}, 137)
        page = self.page([many])
        self.assertIn("  - Commit 39 ·", page)
        self.assertNotIn("  - Commit 40 ·", page)
        self.assertIn("  - and 97 more commits", page)

    def test_every_change_and_the_build(self):
        apk = Apk("XenDroid_Release_abcdef12.apk", 42095473, "d" * 64, "https://github.com/o/r/releases/download/t/x.apk")
        page = self.page([pr(17, FG, title="Frame generation", commits=[("b" * 40, "Win-FG: no textureSize()")])], apk)
        self.assertIn("<summary><b>📜 Every change since build 37</b> · 1 pull request, 7 commits</summary>", page)
        self.assertIn("- [#17](https://github.com/o/r/pull/17) Frame generation · @phforner0", page)
        self.assertIn("  - Win-FG: no textureSize() · [`bbbbbbbb`](https://github.com/o/r/commit/" + "b" * 40 + ")", page)
        self.assertIn("Built by [Xendroid+ #39](https://github.com/o/r/actions/runs/1) from [`abcdef12`]", page)
        self.assertIn("| [`XenDroid_Release_abcdef12.apk`](https://github.com/o/r/releases/download/t/x.apk) | 40.1 MiB | `" + "d" * 64 + "` |", page)


if __name__ == "__main__":
    unittest.main()
