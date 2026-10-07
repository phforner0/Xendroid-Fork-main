---
title: GPU drivers
description: System driver or Turnip, where to switch, how packages are downloaded and checked, and the Turnip flags.
section: usar
order: 4
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/ui/drivers/DriversScreen.kt, app/src/main/java/xendroid/compose/driver/DriverSources.kt, app/src/main/java/xendroid/compose/driver/DriverPackageInstaller.kt, app/src/main/java/xendroid/compose/driver/DriverSuggestion.kt, app/src/main/java/xendroid/compose/driver/CustomDrivers.kt, app/src/main/java/xendroid/compose/settings/TurnipFlags.kt, app/src/main/res/values/strings_app.xml]
pt: drivers
---

Xendroid+ renders with Vulkan. By default it uses the **system Vulkan driver**, which comes with Android and is always available. On Adreno GPUs you can switch to a custom driver such as **Turnip** (Mesa's open-source Vulkan driver for Adreno), which is the focus of the project's optimizations.

::: nota Adreno only
Custom drivers only load on Adreno GPUs with Qualcomm's KGSL kernel driver. On other devices the Drivers screen says that every game uses the system driver.
:::

{{> print id=drivers legenda="GPU drivers: the selected one and the one the last session loaded, installed, available to download, sources and Turnip options."}}

## Where to switch

- **Drivers** area (in the rail, in landscape; in the Start menu, with a controller; in portrait, via **Settings → Summary → Driver → Manage drivers**). The choice applies to **every game from the next start**; the previous one stays one tap away, and **Use the system driver** goes back to the default.
- **Per game**: in **Details** → **Driver and Vulkan**.
- **For one launch only**: in **Details** → [Start with…](doc:interface#start-with) → **Driver**.

The **In use** section shows the selected driver and the one the last session actually loaded. If a custom driver did not load (file missing or refused), it warns that the system driver was used. When a game does not start with a custom driver, the failure screen offers **Try with the system driver** for that launch only.

## Download and install

- **Sources**: GitHub repositories with driver releases. The default source is `K11MCH1/AdrenoToolsDrivers`; you can have up to 8. The app reads published releases (it ignores drafts and pre-releases) and only `.zip` files.
- **Suggestion for this GPU**: based on the file and release names, the exact model comes before the family (for example, a7xx), always from the same family and newest first. The app warns that it is a choice based on names, not a guarantee.
- **Import ZIP**: installs a package you already have.

Every package is checked before installing:

1. the **SHA-256**, when the release publishes one: the downloaded file has to match; the badge says “SHA-256 published” or “No checksum published”;
2. the library has to be Vulkan for 64-bit ARM;
3. the ZIP has size limits (64 MB compressed, 256 MB uncompressed) and must name the library in `meta.json` or contain a single `.so`.

The package in use cannot be removed. Removing a package deletes its files; to use it again, download or import it again.

## Turnip options {#turnip-options}

The Turnip flags (the `TU_DEBUG` variable) are passed to the driver when the next game starts. They apply to every game, and each game can have its own in **Details**. Qualcomm drivers ignore these flags. The Xendroid+ default is **sysmem**.

{{> turnip-flags}}

`sysmem` and `gmem` are mutually exclusive. Flags that the app version does not know are left as they are. The last line of the screen shows the final value, such as `TU_DEBUG=sysmem`.

## When to switch

- **Start with the system driver.** The first-launch assistant reminds you that it is enough to get started.
- A recent Turnip is usually the path for the project's optimizations on Adreno 7xx and 8xx, and the [published measurements](doc:performance#published-measurements) were taken with Turnip.
- If the game closes or freezes with a custom driver, try the system driver. The app suggests this by itself after a native crash with a custom driver (see [end-of-session suggestions](doc:performance#end-of-session-suggestions)).

The [simulator](ferramenta:drivers) shows the Drivers screen with the sample catalog and the real flags.
