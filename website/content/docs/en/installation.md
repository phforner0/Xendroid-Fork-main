---
title: Install and update
description: Download the published APK, check its SHA-256, install it and keep the app up to date from within Xendroid+.
section: comecar
order: 2
conferido: 29ef9e7c7
fontes: [app/build.gradle, app/src/main/java/xendroid/compose/updater/updater.kt, app/src/main/java/xendroid/compose/updater/ReleaseFeed.kt, app/src/main/java/xendroid/compose/updater/ReleaseTags.kt, app/src/main/java/xendroid/compose/updater/UpdateInstaller.kt, app/src/main/java/xendroid/compose/updater/UpdateScreen.kt, .github/workflows/XenDroid.yml]
pt: instalacao
---

## Download {#download}

Each published version is in the project's [GitHub releases]({{repo}}/releases), with a single APK named `XenDroid_Release_<commit>.apk`. The current stable version is **{{estavel.nome}}**, from {{estavel.data}}:

| What | Value |
|---|---|
| File | `{{estavel.apk.nome}}` |
| Size | {{estavel.apk.tamanho}} |
| SHA-256 | `{{estavel.apk.sha256}}` |
| Package | `{{app.pacote}}` |

The APK only includes the core for 64-bit ARM (`{{app.abi}}`) and requires Android {{app.androidMin}} or newer; on other devices Android refuses to install it. See the [requirements](doc:requirements).

## Check the SHA-256

GitHub publishes the SHA-256 of each APK. Checking the downloaded file ensures it is the same one the project published:

```sh
# Linux
sha256sum XenDroid_Release_*.apk
# macOS
shasum -a 256 XenDroid_Release_*.apk
# Windows (PowerShell or cmd)
certutil -hashfile XenDroid_Release_<commit>.apk SHA256
```

The result must match the one in the table above (or the one on the release page).

## Install

1. Open the APK on the phone, from the browser or the file manager.
2. Android asks you to allow that app to install apps from outside the store. Allow it for that app only.
3. Confirm the installation.

Installing over an earlier version keeps your data (found games, settings, profiles and saves), as long as both are signed with the same key. Uninstalling the app deletes its data folder (`{{app.pastaDados}}`), including the saves and the per-game settings: back up first (see [settings backup](doc:games-and-files#settings-backup) and [saves](doc:interface#profiles-and-saves)).

::: nota Test package
Pull request builds produce another package, `{{app.pacoteTeste}}`, which installs alongside the regular app and is debuggable. It is meant for testing and does not check for updates.
:::

## Update from the app

The published package (`{{app.pacote}}`) looks for new versions in the GitHub releases and only installs what passes three checks:

1. the download must match the published SHA-256; without a SHA-256, the app only offers the release page, so you can install it by hand;
2. the new version must be the same package and have a higher build number;
3. it must be signed with the same key; if it isn't, the app explains that Android would refuse the update.

{{> print id=atualizador legenda="App updates: the installed version, the channel and the update card with the steps download, check the SHA-256 and install."}}

### Channels

In **Settings → App → Updates**:

- **Stable** (default): offers the latest published version.
- **Preview**: also offers previews, which may be less tested.
- **Off**: never checks.

### When the app checks

- When the app opens, at most once every 5 minutes, and it only shows something if there is a new version. It does not check in debug builds or when the app was opened by a frontend.
- Whenever you ask: library ⋮ menu → **Check for updates**, on the **About** screen or in **Settings → App → Updates → Check now**.

**Skip this version** makes the app stop offering that version and any earlier one.

### Permission to install

The first update asks for the **Install unknown apps** permission for Xendroid+. The app opens the system screen; allow it and tap **Download and install** again. Android's installer always asks for the final confirmation.

::: versao desde=7104f6341
The update card shows the summary of changes in the app's language, taken from the release notes.
:::

### Versions the app does not offer

- Old releases tagged `XenDroid-<commit>`, without a build number: they never show up as an update. To move off them, install a new version by hand.
- Builds you compiled yourself (build number 1): they never get an offer.

::: aviso Repository checked
The app looks for releases in `{{app.repoAtualizacoes}}`, the repository's previous name; the releases now live in `phforner0/Xendroid-Plus`. GitHub usually redirects the old name to the new one, but this site could not verify that. If **Check for updates** doesn't find a version that is already in the releases, download the APK from the releases page.
:::

## What each version changed

The notes for each version, with the English summary, are in [What's new and release history](doc:changelog).
