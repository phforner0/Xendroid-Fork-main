---
title: Settings and effects
description: How global and per-game settings work, the levels, when each value takes effect, presets, image effects and frame generation.
section: usar
order: 3
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/settings/SettingsSchema.kt, app/src/main/java/xendroid/compose/settings/SettingCatalog.kt, app/src/main/java/xendroid/compose/ui/settings/XdSettings.kt, app/src/main/res/values/strings_settings.xml, emulator-core/src/main/assets/config/default_config.toml, app/src/main/java/xendroid/compose/EmulatorHostActivity.kt, app/src/main/java/xendroid/compose/core/FrameGenerationTarget.kt, app/src/main/java/xendroid/compose/core/LsfgAssets.kt]
pt: ajustes
---

The app has **{{contagens.ajustes}} emulator settings** in eight groups. All of them are listed, with default, options and TOML key, in the [settings reference](doc:settings-reference). This page explains how they work.

{{> ajustes-resumo}}

## Global and per game

- **Settings** changes the **global** settings, which apply to every game. Each row says whether the value is the default or has changed, and how many games use a different value.
- A game's **Details** changes the settings **for that game**. A game value wins over the global one; whatever the game does not change follows the global value. Each row says where the value comes from (“This game” or “Global”) and has **Back to global**.

Per-game settings are stored in the `config/<TITLE ID>.config.toml` file in the [data folder](doc:games-and-files#config-files), with only the keys that change. The core also applies [automatic fixes](doc:compatibility#automatic-fixes) to some games, with the same priority as a game setting; a value you put in the game's file wins over the fix.

## Levels: Essential, Advanced and All {#levels}

**Settings → App → Interface → Settings shown** chooses how many settings appear:

- **Essential** ({{contagens.essencial}}): what changes the picture and performance;
- **Advanced** ({{contagens.avancado}}): also compatibility, input and system;
- **All** ({{contagens.ajustes}}): every setting the core reads, debugging included.

The search in each tab looks at the name, the description and the TOML key (for example `GPU.framerate_limit`), shows up to five results from other tabs and says how many results the level hid.

## When a value takes effect

Almost every setting takes effect **the next time the game starts**: the core reads the configuration when the game begins. Some (FPS limit, scaling and sharpening, on-screen controls and volume) also change **live** from the [in-game menu](doc:interface#in-game-menu). Settings that depend on another one say why when they do not apply (for example, “Only with Force maximum GPU clocks on.”).

## Presets

In a game's **Details**, **Presets** applies several settings at once, with Undo:

{{> predefinicoes}}

## Image

### Resolution scale

**Resolution scale** renders the game at 1×, 2× or 3× the native resolution, in width and height. Whole steps only. Each step costs GPU time and memory: in a game that already does not reach the FPS limit at 1×, raising the scale lowers the FPS even more.

**Display mode reported to game** is not a scaler: it only tells the game what the TV is. Most games render at a fixed size and ignore it.

### Output scaling and sharpening

The effect applied to the final image when it is scaled up to the phone's screen:

| Effect | What it does |
|---|---|
| Bilinear | simple scaling, the default |
| AMD CAS | adaptive sharpening; “CAS: extra sharpness” increases it |
| FSR 1 | AMD scaling with sharpening; “FSR: sharpness reduction” softens it |
| SGSR | Snapdragon Game Super Resolution, by Qualcomm; experimental |
| Lanczos | sharp scaling, without halos |
| CRT | tube TV look, with scanlines |

**Antialiasing** (FXAA or FXAA extreme) smooths edges and combines with CAS or FSR. **Reduce color banding** adds fine noise so gradients do not show steps on 8-bit screens. These four also change from the in-game menu, right away.

### Aspect ratio

**Widescreen** tells games the screen is 16:9 instead of 4:3. **Present letterbox** keeps the game's aspect ratio instead of stretching it. In the in-game menu, the screen mode chooses between Fit, Fill, Stretch and Integer.

## Performance

- **Frame rate limit**: Unlimited, 30, 45, 60, 90 or 120. The console never went above 60 FPS; above the screen's refresh rate, the extra frames are only heat and battery drain.
- **Cap guest display refresh (VSync)**: keeps the game's vblanks at the console's 50/60 Hz.
- **Asynchronous shader compilation**: compiles new shaders in the background. No stutter in a new scene, but the first frames may miss an object.
- **Force maximum GPU clocks** (with a custom driver): more speed, more heat and battery drain.

::: versao desde=515736309
The core now skips, by default, draws whose shaders are still compiling (“Shaders without stutter” in the in-game menu): a brief pop-in of new objects instead of a stutter.
:::

## Frame generation {#frame-generation}

Frame generation creates intermediate frames between the game's frames. It is **experimental**, starts **off every time a game starts** and is not saved per game. It is in the in-game menu → **Image → Frame generation**:

- **Win-FG 2×**: doubles the frames, with the Quality, Balanced and Performance presets.
- **Native LSFG**: requires your own `Lossless.dll` (from Lossless Scaling, which you need to own). **Import my Lossless.dll** reads the file and builds a shader cache locally, in the app's private storage; the temporary DLL is deleted and nothing from it goes into the APK. The **LSFG multiplier** goes from 2× to 4×, and the **LSFG target** (60, 90, 120 FPS or the screen's refresh rate) picks the smallest multiplier that reaches it.

When you turn it on, the app caps the game at the screen's refresh rate divided by the multiplier and asks the display for the refresh rate it needs; when you turn it off, everything goes back. The app's own note warns that pacing and latency on the device have not been validated yet.

::: versao desde=4a74c69c5
Frame generation appears in every published version and in both settings modes. Up to build 35 it only worked in debug builds.
:::

## See and try

The [simulator](ferramenta:settings) has the same {{contagens.ajustes}} settings, with levels, search and filters, and shows the `config/<TITLE ID>.config.toml` file that each change would generate for a game.
