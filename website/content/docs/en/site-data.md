---
title: Where the site's data comes from
description: Which site data is read from the code and which is written by hand, how the stable and development versions are kept apart, what the build checks, and how the site is published and maintained.
navTitle: Site data
section: projeto
order: 3
pt: dados-do-site
---

This site is generated from the Xendroid+ repository, in two languages: English at the root of the address and Portuguese under `pt-br/`. Every page exists in both, with the same content, and the language switch in the header leads to the same page in the other language. What can be read from the code is read from the code on every build; what needs explaining is written by hand, in Markdown, and checked against the code when possible. Nothing guarantees that hand-written text stays correct after the app changes: that is why each page says which version it was reviewed against and warns you when the code it cites has changed since then.

## Two versions

- **Stable**: the commit of the latest release published on GitHub, currently {{estavel.nome}} (`{{estavel.commit}}`). It is the version the home page offers for download and the basis for the documentation and the simulator.
- **Development**: the `main` commit the site is generated from (`{{dev.commit}}`).

The build reads the two separately: the stable version in a worktree of its own, at the release commit. When both give the same data (`main` only changed the site, a workflow or code comments), the site shows a single version and says so. When the data changed, the settings reference lists the differences, documentation passages marked with a version say from which build they apply, and the data of both is published in `dados/estavel.json` and `dados/desenvolvimento.json`.

## What is read from the code

{{> fontes}}

The setting texts are the app's own: English pages show the app's English strings (`app/src/main/res/values/`), and Portuguese pages, under `pt-br/`, show its Brazilian Portuguese strings (`app/src/main/res/values-pt-rBR/`); when the app has no translation, the text appears in English, marked as such. Release summaries come from each release's `update-summary:en` block on English pages and from its `update-summary:pt-BR` block on Portuguese pages, and the performance table comes from `README.md` on English pages and from `README.pt-BR.md` on Portuguese pages. The Xenia title database bundled with the core is used only to give a Title ID a name: its compatibility status is for Xenia on PC and does not apply to Xendroid+.

## What is written by hand

- The documentation pages, in `website/content/docs/en/*.md` (English) and `website/content/docs/*.md` (Portuguese).
- The home page texts and the screenshot captions, in `website/scripts/site/landing.mjs`, `website/content/prints.mjs` and `website/content/prints.en.mjs`.
- The simulator's annotations and example data (games, sessions, profiles), marked as examples in the tool itself. The simulator is written in Portuguese; the build generates the English version by replacing each text with the one in the `website/content/simulador.en.json` dictionary, or with the app's own English text when it is the same label as in the app, and stops if any text lacks a translation.

Each documentation page states in its front matter the commit it was reviewed against (`conferido`) and lists the app files it describes (`fontes`). If any of those files changed between the review and the stable version, the page shows a warning at the top and the build records the case. Values such as the version, the APK size, the package and the counts enter the pages by reference to the data (`\{{estavel.build}}`, for example), not written into the text.

## What the build checks {#build-checks}

On every build, the site checks:

- that each app setting has a cvar in the core, in the same section, and that the values the app writes are of a type the core accepts;
- that the keys of the `default_config.toml` template exist in the core;
- that the automatic per-game fixes point to cvars that exist;
- that each patch file has the same Title ID as its file name;
- that every internal link, anchor, cited repository file, screenshot and simulator screen exists;
- that every page has its version in the other language and that the two point to each other.

A problem that makes the site wrong (data that could not be read, a broken link) stops publishing. An inconsistency in the app itself does not stop the site: it goes into the list below, which is a snapshot of the code of {{estavel.nome}}.

{{> consistencia}}

## Publishing {#publishing}

The site is published to GitHub Pages by the `.github/workflows/pages.yml` workflow, which runs:

- on every push to `main` that changes the site or a file it reads: the app, the core, the patches, the READMEs and the documents;
- when the APK workflow finishes successfully on `main`, which covers new releases (releases created by the CI itself do not trigger other workflows);
- once a day, to pick up releases published or edited by hand;
- on demand, from the Actions tab.

In a pull request, the workflow only builds and checks the site, without publishing it. The GitHub Actions token is used only during the build, to read the releases; it never reaches the pages.

## Maintaining the site

- **Build and view locally**: in `website/`, run `npm ci`, `npm run build` and `npm run serve`, which serves the site at the same path as GitHub Pages.
- **Check**: `npm run check` looks for broken links and resources; `npm test` runs the browser tests (layout, keyboard, search, simulator).
- **A new page** is two Markdown files, one in `website/content/docs/en/` (English) and one in `website/content/docs/` (Portuguese), with title, description, section and order in their front matter; the English one also has `pt: <Portuguese file name>`, which pairs the two.
- **After reviewing a page** against the current code, update its `conferido`.

The full maintenance guide is in the [site README](repo:website/README.md).
