---
title: Compatibility and limitations
description: The fixes the core applies on its own per game, the project's compatibility notes, how to rate a game on your device, and what still limits Xendroid+.
section: referencia
order: 2
conferido: bd1d2a8d8
fontes: [emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc, GAME_COMPAT.md, app/src/main/java/xendroid/compose/compatibility/CompatibilityStore.kt, app/build.gradle, docs/ui-redesign/app.md]
pt: compatibilidade
---

Xendroid+ has no public compatibility list. What runs, and how it runs, depends on the game, the device, the GPU driver and the app version. This page gathers what the project documents.

## Automatic per-game fixes {#automatic-fixes}

The core ships **{{contagens.correcoes}} settings for {{contagens.jogosCorrecoes}} games** (`game_quirks.cc`). They apply when the title starts, with the priority of a game's own setting: above the global config and below the `config/<TITLE ID>.config.toml` file. You don't need to configure anything, and a value you set in the game's file wins over the fix. The emulator log records each one as `Game quirk for <TITLE ID>: <cvar>`.

{{> correcoes}}

Most of them fix hangs and rendering glitches on the Turnip driver or save GPU or CPU time; the comments on each entry, in [game_quirks.cc](repo:emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc), give the reason and the measurement the author made. Game names come from the repository's patch files or, when there is no patch, from the Xenia title database bundled with the core, used here only for the name.

## Project notes per game

[GAME_COMPAT.md](repo:GAME_COMPAT.md) documents two games in more detail:

- **Ninja Gaiden II** (`544307D5`): playable with two settings in the game's file, `clear_memory_page_state = true` (brings back the character models) and `depth_float24_convert_in_pixel_shader = true` (prevents GPU hangs on Adreno), both in the `[GPU]` section.
- **Fable II** (`4D5307F1`): runs with no special settings since June 2026, with known issues (see-through ground in some places, occasional failures while loading, and audio dropouts).

The same document has notes for drivers and devices: `vulkan_mid_frame_submission_draws` on some Adreno 830 devices, a global `depth_float24_convert_in_pixel_shader` on drivers that fail on the float32 path, and `vulkan_dynamic_constant_buffers` turned off on Qualcomm's drivers.

::: aviso Outdated parts of GAME_COMPAT.md
- The correct path for each game's file is `{{app.configJogo}}`; the document cites a different package and a different folder.
- The Fable II patch that the document says is in the repository (`patches/4D5307F1.patch.toml`) does not exist; the catalog has other patches for this game.
- The default of `vulkan_mid_frame_submission_draws` is now 1300, not 0.
:::

## Rate a game on your device

In Details → ⋮ menu → **Rate compatibility**, choose how the game ran: doesn't boot, boots with no picture, intro or menus only, in-game with problems, or playable. The rating stays on your device only, with the app version, the GPU, the driver and the date, and nothing goes over the network. It is included in the [settings backup](doc:games-and-files#settings-backup).

## Known limitations

- **Experimental.** Some games run well; others still have glitches or don't start.
- **Custom drivers only on Adreno** with KGSL. On other GPUs, only the system driver.
- **Frame generation** is experimental, starts off every time a game starts, and LSFG needs your own `Lossless.dll`. Frame pacing and latency on a device have not been validated yet.
- **Phone as controller** is experimental and only works on the local network.
- **Android 10** only opens games launched from a frontend.
- **Community settings** and the **compatibility catalog** exist in the code but are turned off in the published builds: none of them has a server configured.
- **Recommended profiles per game**: the list that ships with the app is empty.
- **New interface** (library, Details, in-game menu and the other screens): the redesign document keeps a list, per batch, of items still to be checked on a device. See [docs/ui-redesign/app.md](repo:docs/ui-redesign/app.md).

## Proposals left out of the app

Three proposals from the interface prototype were left out, with no planned date:

- a key map for each controller;
- finding the game on the network and scanning a QR code with the phone as controller;
- browsing for the disc file when disc swap finds none (today, Cancel is the way out, and the disc shows up once it is placed in a games folder).

## Help with reports

The repository has an issue template for performance reports, which asks for CPU, GPU, manufacturer, Android version, game and notes. Also include the app version (in **About**), the driver and the Title ID, and attach the [session diagnostic](doc:troubleshooting#share-a-diagnostic). Community discussion happens on [Discord]({{discord}}).
