#!/usr/bin/env python3
"""The notes of a Xendroid+ release: the GitHub release page and what the app's update card
shows.

Each pull request merged since the previous release can carry its notes in its description,
in English and in Brazilian Portuguese, between markers (.github/pull_request_template.md has
them):

    <!-- release-notes:en -->
    A sentence or two on what this brings, for players.
    - A highlight, one line each: the update card lists them.
    ### 🎞️ Frame generation
    - What changed, for the player. Callouts (> ✅ **Tested** on ...) and images go here too.
    ### 🛠️ Fixed
    - ...
    ### ⚠️ Still open
    - ...
    <!-- /release-notes:en -->
    <!-- release-notes:pt-BR -->
    (the same, in Portuguese)
    <!-- /release-notes:pt-BR -->

Sections of the same name from several pull requests are merged, Fixed after the others and
Still open closing the page; HTML comments inside a block are hints, not notes. A pull request
without notes is listed by its title under Other changes, and one language stands in for the
other when only one is written.

The page is in English, with the Portuguese in a collapsed block. Its summary and highlights
come first and end at the **Full Changelog** line, where the update card of the builds before
these notes stops reading; the hidden update-summary blocks after it are what later builds show,
in the phone's language (updater.kt, updateNotes).

usage: release_notes.py --repo OWNER/REPO --tag TAG --sha SHA --build N
                        [--previous TAG] [--run-url URL] [--apk FILE]
Prints the notes. Needs gh, authenticated (GH_TOKEN in Actions). Without --apk the file comes
from the release's own assets, for notes written again for a published release.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field

LANGUAGES = ("en", "pt-BR")
# A block holds no other opening marker: one quoted in the description's prose (`<!-- ...`)
# does not swallow the text up to the real block.
BLOCK = re.compile(r"<!--\s*release-notes:(en|pt-BR)\s*-->((?:(?!<!--\s*release-notes:).)*?)"
                   r"<!--\s*/release-notes:\1\s*-->", re.S)
COMMENT = re.compile(r"<!--.*?-->", re.S)
FIXED = {"fixed", "fixes", "corrigido", "correções", "correcoes"}
STILL_OPEN = {"still open", "known issues", "ainda em aberto", "problemas conhecidos"}
WORDS = {
    "en": {"new": "What's new", "open": "⚠️ Still open", "other": "Other changes"},
    "pt-BR": {"new": "Novidades", "open": "⚠️ Ainda em aberto", "other": "Outras mudanças"},
}


@dataclass
class Notes:
    """One language of one pull request's notes."""
    summary: list = field(default_factory=list)     # paragraphs
    highlights: list = field(default_factory=list)  # one line each
    callouts: list = field(default_factory=list)    # quote lines before the first section
    sections: list = field(default_factory=list)    # (heading, lines)


@dataclass
class PullRequest:
    number: int
    title: str
    author: str
    url: str
    commits: list   # (sha, subject), the first ones
    notes: dict     # language -> Notes
    commit_total: int = 0


@dataclass
class Apk:
    name: str
    size: int
    sha256: str
    url: str


def parse(text):
    """A release-notes block: summary, highlights and callouts, then ### sections."""
    notes, paragraph, section = Notes(), [], None

    def end_paragraph():
        if paragraph:
            notes.summary.append(" ".join(paragraph))
            paragraph.clear()

    for line in COMMENT.sub("", text).splitlines():
        line = line.rstrip()
        if line.startswith("### "):
            end_paragraph()
            section = (line[4:].strip(), [])
            notes.sections.append(section)
        elif section is not None:
            section[1].append(line)
        elif not line.strip():
            end_paragraph()
        elif line.lstrip().startswith(("- ", "* ")):
            end_paragraph()
            notes.highlights.append(line.lstrip()[2:].strip())
        elif line.lstrip().startswith(">"):
            end_paragraph()
            notes.callouts.append(line.strip())
        else:
            paragraph.append(line.strip())
    end_paragraph()
    for _, lines in notes.sections:
        while lines and not lines[0].strip():
            lines.pop(0)
        while lines and not lines[-1].strip():
            lines.pop()
    empty = not (notes.summary or notes.highlights or notes.callouts or notes.sections)
    return None if empty else notes


def notes_of(body):
    """The release notes in a pull request's description, by language."""
    found = {}
    for language, text in BLOCK.findall(body or ""):
        notes = parse(text)
        if notes:
            found[language] = notes
    return found


def key(heading):
    """Section names compared without their emoji, case or punctuation."""
    return re.sub(r"[^\w ]+", "", heading.lower()).strip()


def with_reference(lines, pr):
    """The pull request's number after each top-level bullet that does not name it (GitHub
    links a bare #N in release notes)."""
    out = []
    for line in lines:
        if line.startswith(("- ", "* ")) and f"#{pr.number}" not in line and f"/pull/{pr.number}" not in line:
            line = f"{line} (#{pr.number})"
        out.append(line)
    return out


@dataclass
class Merged:
    summary: list
    highlights: list
    callouts: list
    sections: list      # (heading, lines), in page order
    still_open: list
    other: list         # pull requests without notes, as bullets


def merge(prs, language):
    """One language's notes of every pull request, oldest first, as one page."""
    fallback = LANGUAGES[1] if language == LANGUAGES[0] else LANGUAGES[0]
    summary, highlights, callouts, other, still_open = [], [], [], [], []
    sections = {}
    # Bullets name their pull request when several wrote notes; with one, it would only repeat.
    several = sum(1 for pr in prs if pr.notes) > 1
    for pr in prs:
        notes = pr.notes.get(language) or pr.notes.get(fallback)
        if notes is None:
            other.append(f"- {pr.title} (#{pr.number})")
            continue
        summary += notes.summary
        highlights += notes.highlights
        callouts += [c for c in notes.callouts if c not in callouts]
        for heading, lines in notes.sections:
            lines = with_reference(lines, pr) if several else lines
            if key(heading) in STILL_OPEN:
                still_open += lines
                continue
            entry = sections.setdefault(key(heading), (heading, []))
            entry[1].extend(lines)
    ordered = [v for k, v in sections.items() if k not in FIXED] + [v for k, v in sections.items() if k in FIXED]
    if not (summary or highlights):
        # No pull request wrote notes: their titles are the highlights, and nothing else repeats them.
        highlights, other = [f"{pr.title} (#{pr.number})" for pr in prs], []
    return Merged(summary, highlights, callouts, ordered, still_open, other)


def plain(text):
    """Markdown down to what the update card shows: no links, images, emphasis or code marks."""
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"\(#\d+\)", "", text)
    text = text.replace("**", "").replace("__", "").replace("`", "")
    return re.sub(r"\s+", " ", text).strip()


def update_summary(page):
    lines = page.highlights or page.summary
    return [plain(line) for line in lines if plain(line)]


def release_label(tag):
    match = re.match(r"XenDroid-v(\d+)-", tag or "")
    return f"build {match.group(1)}" if match else (tag or "the start")


def mebibytes(size):
    return f"{size / (1024 * 1024):.1f} MiB"


def render(prs, repo, tag, sha, build, previous, run_url, apk, commit_count):
    server = f"https://github.com/{repo}"
    en, pt = merge(prs, "en"), merge(prs, "pt-BR")
    out = []
    # Summary and highlights: everything the update card of earlier builds reads.
    out += [paragraph + "\n" for paragraph in en.summary]
    out += [f"* {line}" for line in en.highlights] + [""]
    compare = f"{server}/compare/{previous}...{tag}" if previous else f"{server}/commits/{tag}"
    out += [f"**Full Changelog**: {compare}", ""]
    for language, page in (("en", en), ("pt-BR", pt)):
        out += [f"<!-- update-summary:{language}"] + [f"* {line}" for line in update_summary(page)] + ["-->"]
    out.append("")
    if en.callouts:
        out += en.callouts + [""]
    if en.sections or en.other:
        out += [f"## {WORDS['en']['new']}", ""]
    for heading, lines in en.sections:
        out += [f"### {heading}", ""] + lines + [""]
    if en.other:
        out += [f"### {WORDS['en']['other']}", ""] + en.other + [""]
    if en.still_open:
        out += [f"## {WORDS['en']['open']}", ""] + en.still_open + [""]

    # The same in Portuguese, collapsed (when any pull request wrote notes).
    if any(pr.notes for pr in prs):
        out += portuguese(pt)
    out += every_change(prs, server, previous, commit_count)

    # Where it comes from.
    built = f"[Xendroid+ #{build}]({run_url})" if run_url else f"build {build}"
    out += ["---", "", f"Built by {built} from [`{sha[:8]}`]({server}/commit/{sha}), signed with the release key.", ""]
    if apk:
        out += ["| File | Size | SHA-256 |", "|---|---|---|",
                f"| [`{apk.name}`]({apk.url}) | {mebibytes(apk.size)} | `{apk.sha256}` |", ""]
    return "\n".join(out).rstrip() + "\n"


def portuguese(pt):
    out = ["<details>", "<summary><b>🇧🇷 Em português</b></summary>", ""]
    out += [paragraph + "\n" for paragraph in pt.summary]
    out += [f"- {line}" for line in pt.highlights] + [""]
    if pt.callouts:
        out += pt.callouts + [""]
    for heading, lines in pt.sections:
        out += [f"#### {heading}", ""] + lines + [""]
    if pt.other:
        out += [f"#### {WORDS['pt-BR']['other']}", ""] + pt.other + [""]
    if pt.still_open:
        out += [f"#### {WORDS['pt-BR']['open']}", ""] + pt.still_open + [""]
    return out + ["</details>", ""]


def every_change(prs, server, previous, commit_count):
    """Every pull request and its commits, collapsed."""
    count = f"{len(prs)} pull request{'s' if len(prs) != 1 else ''}"
    if commit_count:
        count += f", {commit_count} commit{'s' if commit_count != 1 else ''}"
    out = ["<details>", f"<summary><b>📜 Every change since {release_label(previous)}</b> · {count}</summary>", ""]
    for pr in prs:
        out.append(f"- [#{pr.number}]({pr.url}) {pr.title} · @{pr.author}")
        for commit_sha, subject in pr.commits[:40]:
            out.append(f"  - {subject} · [`{commit_sha[:8]}`]({server}/commit/{commit_sha})")
        total = max(pr.commit_total, len(pr.commits))
        if total > 40:
            out.append(f"  - and {total - 40} more commits")
    return out + ["", "</details>", ""]


def gh(*args):
    result = subprocess.run(["gh", *args], check=True, capture_output=True, text=True)
    return json.loads(result.stdout) if result.stdout.strip() else None


def previous_release(repo, tag):
    """The release before TAG: the newest other one, or the one after it when TAG exists."""
    releases = [r for r in gh("api", f"repos/{repo}/releases?per_page=50") if not r["draft"]]
    releases.sort(key=lambda r: r["created_at"], reverse=True)
    tags = [r["tag_name"] for r in releases]
    if tag in tags:
        later = tags[tags.index(tag) + 1:]
        return later[0] if later else None
    return tags[0] if tags else None


def pull_requests(repo, tag, sha, previous):
    """The pull requests GitHub's own release notes would list, oldest merge first."""
    args = ["api", "-X", "POST", f"repos/{repo}/releases/generate-notes",
            "-f", f"tag_name={tag}", "-f", f"target_commitish={sha}"]
    if previous:
        args += ["-f", f"previous_tag_name={previous}"]
    numbers = list(dict.fromkeys(int(n) for n in re.findall(r"/pull/(\d+)", gh(*args)["body"])))
    prs = []
    for number in numbers:
        pr = gh("api", f"repos/{repo}/pulls/{number}")
        commits = gh("api", f"repos/{repo}/pulls/{number}/commits?per_page=100")
        prs.append((pr.get("merged_at") or "", PullRequest(
            number, pr["title"].strip(), pr["user"]["login"], pr["html_url"],
            [(c["sha"], c["commit"]["message"].splitlines()[0]) for c in commits],
            notes_of(pr.get("body")), pr.get("commits", 0))))
    return [pr for _, pr in sorted(prs, key=lambda item: item[0])]


def commit_count(repo, previous, sha):
    if not previous:
        return 0
    return gh("api", f"repos/{repo}/compare/{previous}...{sha}")["total_commits"]


def apk_of(repo, tag, path):
    if path:
        digest = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                digest.update(chunk)
        name = os.path.basename(path)
        return Apk(name, os.path.getsize(path), digest.hexdigest(),
                   f"https://github.com/{repo}/releases/download/{tag}/{name}")
    release = gh("api", f"repos/{repo}/releases/tags/{tag}")
    for asset in release["assets"]:
        if asset["name"].endswith(".apk"):
            sha256 = (asset.get("digest") or "").removeprefix("sha256:")
            return Apk(asset["name"], asset["size"], sha256, asset["browser_download_url"])
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--build", required=True)
    parser.add_argument("--previous")
    parser.add_argument("--run-url")
    parser.add_argument("--apk")
    args = parser.parse_args()
    previous = args.previous or previous_release(args.repo, args.tag)
    prs = pull_requests(args.repo, args.tag, args.sha, previous)
    sys.stdout.write(render(prs, args.repo, args.tag, args.sha, args.build, previous, args.run_url,
                            apk_of(args.repo, args.tag, args.apk), commit_count(args.repo, previous, args.sha)))


if __name__ == "__main__":
    main()
