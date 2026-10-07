---
title: Games, folders and files
description: Supported formats, game folders, updates and DLC, patches, configuration files, data folder, settings backup and external frontends.
navTitle: Games and files
section: usar
order: 2
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/data/GameFormat.kt, app/src/main/java/xendroid/compose/data/LibraryWalker.kt, app/src/main/java/xendroid/compose/data/GameLibraryRepository.kt, app/src/main/java/xendroid/compose/ui/content/InstallContentViewModel.kt, app/src/main/java/xendroid/compose/saves/ContentTrash.kt, app/src/main/java/xendroid/compose/patches/PatchStore.kt, app/src/main/java/xendroid/compose/settings/ConfigStore.kt, app/src/main/java/xendroid/compose/bundle/DataBundle.kt, app/src/main/java/xendroid/compose/bundle/DataBundleIo.kt, app/src/main/java/xendroid/compose/core/FrontendLaunch.kt, app/build.gradle]
pt: jogos-e-arquivos
---

## Game formats {#game-formats}

| Format | How the library recognizes it |
|---|---|
| ISO | `.iso` file (disc image) |
| ZAR | `.zar` file (compressed disc) |
| GOD | file with no extension and a Games on Demand header |
| XBLA | file with no extension and an arcade game STFS header |
| XEX folder | a folder with `default.xex`; its contents are not scanned |

Files with no extension have their header read first: DLC, title updates, profiles and saves are rejected as games, and only full-game types get in (Games on Demand, Xbox 360 title, arcade, demo and XNA community games).

## Game folders

The library searches each game folder and the folders inside it, up to 12 levels and 100,000 entries. Hidden folders and the `*.data` folders of GOD games are skipped; a folder inside another one is scanned only once. If the search stops at the limit, the app warns that the list is incomplete.

In **library ⋮ menu → Game folders** (Android 11 or newer):

- add several folders and see how many games each one has;
- the first folder receives the full games installed from packages (**Install here** changes which one it is);
- removing a folder only stops the search there; the files stay where they are;
- **Create standard folders** creates `XenDroid/Games`, `XenDroid/TU` and `XenDroid/DLC` in internal storage (the games folder name follows the app language).

**Games no longer in the library** lists games seen before that the search no longer finds, with the reason (file moved or deleted, folder unavailable, file outside the folders). **Where is it now?** opens the folder browser near the old path; **Remove from the list** only hides the game.

### Compress to .zar

Only for ISO games, from **Details** (⋮ menu or **Saves and data**). The `.zar` is created in a temporary file, verified and copied next to the `.iso`. The original `.iso` stays intact until the app asks whether it can delete it; closing the question keeps the `.iso`.

## Updates and DLC {#updates-and-dlc}

The **Content** area shows what is installed per game (DLC and title updates, with sizes) and installs new packages:

1. In **Content → Install**, the app lists the packages found in Downloads and in the **content folders** (including subfolders), or you choose the file.
2. It checks which game the package belongs to and its type: DLC, title update or full game. Packages are recognized by the `CON `, `LIVE` or `PIRS` header.
3. The content applies the next time the game starts. A full game (XBLA, GOD) is copied to the first game folder.

Before installing, the app checks that there is enough free space with a 10% margin. When opened from a game, it accepts only DLC and title updates for that game.

Removing content sends it to a **trash with a 4 GiB quota** shared by all games, where you can restore it or delete it for good. Nothing is deleted automatically; if the trash is full, the app asks you to empty it or delete the item for good. The game files in your folders are never touched.

## Patches {#patches}

The APK includes the {{contagens.arquivosPatch}} patch files from the Xenia Canary Game Patches catalog, for {{contagens.jogosPatches}} games, all turned off. In **Details**, **Patches and content** shows the game's patches in three groups:

- **For your version**: the file contains the hash of the executable the game loaded in the last session;
- **For other versions**: they apply to another version of the game (another title update, for example) and will not be applied to yours;
- **No version stated**.

Turning on a patch copies the file to the app's patches folder and changes only that patch's `is_enabled` line. Patches apply from the next time the game starts, and only if they match your game's version. The **Apply patches** setting (Console and system) turns all of them on or off. You can also import a `.patch.toml` file of your own, for the same Title ID, which the app checks before accepting it.

::: nota Three files with a mismatched Title ID
In the catalog, `534507D4 - Syndicate` has `title_id` 45410923 and the two `464507E1 - Farming Simulator` files have 464507DC. The app lists patches by file name and the core applies them by content, so these three may not show up under the expected game. The site checks this on every build (see [Site data](doc:site-data#code-warnings)).
:::

## Configuration files {#config-files}

The app stores settings in the files the emulator core reads:

{{> pastas}}

- The **global config** receives the settings from **Settings**. On first launch, the app creates it from the `default_config.toml` template included in the APK.
- Each game with its own settings has a `config/<TITLE ID>.config.toml` file, with only the keys that change. The Title ID is in uppercase.
- The priority order, from weakest to strongest: core default, global config, [automatic per-game fixes](doc:compatibility#automatic-fixes) and the game's file.

A game file has this format, which the [simulator](ferramenta:game) generates the same way:

```toml
# Game-specific config overrides
# Title ID: 4D5309C9

[Display]
postprocess_scaling_and_sharpening = 'fsr'

[GPU]
framerate_limit = 30
```

Each key goes in the section (`[GPU]`, `[Display]`…) where the core looks for it; in the wrong section, or with a value of the wrong type, the core ignores the value. The app writes text in single quotes, numbers without quotes and `true`/`false` for on and off.

::: dica Editing by hand
The data folder appears in the Android file manager via **Settings → App → Data and backup → Open in the file manager**. With no game open, you can copy a `.config.toml` file into the `config` folder. The app rewrites only the keys you change in it and keeps the ones it does not know.
:::

## Settings backup {#settings-backup}

**Settings → App → Data and backup → Back up or move settings** exports a ZIP (`xendroid-settings-<date>.zip`) with:

- the global config and the per-game configs;
- the touch controls layout;
- favorites, library order and collections;
- your compatibility notes.

Saves, profiles, games, drivers, LSFG data, logs and play history are **not** included. Paths that only work on the original device (storage folders and the custom driver) are left out too. When you import, the app first shows what will change and keeps a backup of the current settings, so you can undo it.

## External frontends {#external-frontends}

Other apps (ES-DE, Daijishō, Beacon and similar) can open a game directly in Xendroid+. The component is `xendroid.compose.EmulatorHostActivity`, in the installed package (`{{app.pacote}}` in the published APK), with the action `xendroid.intent.action.xendroid` or `android.intent.action.VIEW`. The game is passed, in this order:

1. in the `game_uri` string extra;
2. in the `AutoStartFile` extra (the Dolphin convention);
3. in the intent's data URI.

The value can be an absolute path (the safest option), `file://` or `content://`. For example, with adb:

```sh
adb shell am start -n {{app.pacote}}/xendroid.compose.EmulatorHostActivity \
  -a xendroid.intent.action.xendroid \
  --es game_uri '/storage/emulated/0/Games/Xbox 360/Game.iso'
```

One process runs only one game; another request while a game is open goes to the existing session. Frontends cannot pass the [Start with…](doc:interface#start-with) options. On Android 10, this is the only way to open games.

::: aviso Package in the frontend document
The [repository's integration document](repo:docs/frontend-integration.md) uses the package `xendroid.compose`, which no published version uses. Use the installed package, as in the example above. This site has not tested this path with a frontend.
:::
