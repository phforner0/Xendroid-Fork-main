---
title: Use the simulator
description: What the interface simulator shows, which of its data is real and which is an example, and how to use the keyboard, a controller and the per-game config file.
navTitle: Simulator
section: usar
order: 7
pt: simulador
---

The [simulator](ferramenta:library) is the Xendroid+ interface running in your browser: the app's screens in touch and controller modes, in landscape or portrait. Use it to get to know the app before installing, to find where a setting is, and to build a game's config file. It doesn't run games, doesn't download anything and doesn't touch your device.

## What is real and what is an example

Taken from the code of {{estavel.nome}}, read when the site is built:

- the {{contagens.ajustes}} settings, with group, level, texts, options, defaults and which ones apply live;
- the presets, the settings pinned to the quick settings panel, the Turnip flags and the controller buttons;
- the keys and types the core reads, used to build each game's `.config.toml` file;
- the patches and automatic fixes for the library's games, from the catalog that ships in the APK;
- the screen texts, copied from the app in English;
- in the updater, the stable release: the APK's title, size and SHA-256, and the English summary that the release itself publishes;
- the list of licenses the app shows in **About**.

These are examples:

- the library's games are the ones measured in the README, with their real Title IDs, but format, size, sessions, saves, collections and covers are illustrative. The median FPS of the last session follows the README's performance table; the other numbers don't;
- no real game gets a compatibility rating. Failures, interrupted sessions and the A/B comparison use fictional games (Title IDs `FFFF0001` to `FFFF0004`);
- the covers are placeholders with the game's initials, and the in-game screen shows a neutral frame instead of the picture;
- profiles, drivers (Turnip A to F), the GPU, the device data, folders and files.

The simulator bar has the **Screen** selector and, on some screens, example selectors (for example, the updater state, or the SD card inserted or removed).

## Annotations

The **Annotations** button marks on the screen what each part does, in three colors:

- **How the app does it**: the app's behavior on this screen, with the code file that implements it;
- **Different in the app**: where the real app differs from what the simulator shows;
- **Simulation only**: what exists only to make the demo work.

Below the device, each screen says where it is in the app, how it changes in controller mode, and which real screenshots of it are in the [interface documentation](doc:interface).

## Keyboard, controller and touch

Click the device screen, or reach it with <kbd>Tab</kbd>, to use the keyboard; <kbd>Tab</kbd> leaves it.

| Key | On a controller | What it does |
|---|---|---|
| <kbd>←</kbd> <kbd>→</kbd> <kbd>↑</kbd> <kbd>↓</kbd> | D-pad | moves the focus |
| <kbd>Enter</kbd> | A | opens, selects or changes the value |
| <kbd>Esc</kbd> | B | goes back |
| <kbd>Q</kbd> <kbd>E</kbd> | LB and RB | switches tab or section |
| <kbd>F</kbd> | Y | favorites the game |
| <kbd>I</kbd> | X | opens Details |
| <kbd>M</kbd> | Start | opens the menu |
| <kbd>/</kbd> | — | searches games |

A controller connected to the computer works through the browser's Gamepad API. In **Automatic**, the simulator switches to controller mode when one shows up, as the app does. **Landscape**, **Portrait** and **Full screen** change the device.

## A game's config file

In a game's Details, change settings in **This game** and open **TOML**. The simulator shows the file the app would write to `{{app.pastaConfigJogos}}/<TITLE ID>.config.toml`, with only the keys that change and in the format the core reads. You can change the Title ID (8 hexadecimal digits, other than `00000000`), and copy or download the file. When no key changes, there is no file, as in the app. The format and each key are covered in [Settings](doc:settings) and in the [reference](doc:settings-reference).

## Direct links and what is saved

The address keeps the open screen: [simulator#drivers](ferramenta:drivers) opens straight to Drivers, and [simulator#update](ferramenta:update) to the updater. The mode, the orientation and the annotations are saved in this browser. The settings, profiles and folders you change last only while the page is open: reloading brings back the examples.

::: nota Without JavaScript
The simulator needs JavaScript. Without it, the real screenshots of each screen are in the [interface documentation](doc:interface).
:::
