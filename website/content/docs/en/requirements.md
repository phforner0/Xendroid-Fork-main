---
title: Device requirements
description: The Android version, processor, Vulkan GPU, drivers and permissions Xendroid+ uses, and what the app checks on first launch.
section: comecar
order: 3
conferido: e179885e7
fontes: [app/build.gradle, app/src/main/AndroidManifest.xml, app/src/main/java/xendroid/compose/ui/library/FirstRun.kt, app/src/main/java/xendroid/compose/driver/CustomDrivers.kt, app/src/main/java/xendroid/compose/core/AllFilesAccess.kt, README.md]
pt: requisitos
---

{{> requisitos}}

## Android

The app installs on Android {{app.androidMin}} (API {{app.apiMin}}) and up, but the full flow needs **Android 11 or newer**:

- The library reads game folders by their real path, with the **All Files Access** permission, which only exists from Android 11 on.
- On Android 10, the app cannot set game folders: the library shows “Setting a game folder requires Android 11 or newer” and the folder items disappear from the menu. Games opened from an [external frontend](doc:games-and-files#external-frontends) still work.
- The controls option “Unbuffered input” also works only on Android 11 and up.

## Processor

**64-bit ARM** only (`{{app.abi}}`). The emulator core is compiled only for this architecture, and the APK declares only it: Android does not install it on 32-bit or x86 devices.

## GPU and Vulkan {#gpu-and-vulkan}

The GPU must support **Vulkan**. With no Vulkan device at all, the library turns into the “This device has no Vulkan GPU” screen, with the checks and a button to copy the device data; nothing is deleted, and if an Android update brings Vulkan, the app works again.

The project focuses on **Adreno 7xx and 8xx** GPUs with the **Turnip** driver, and the README recommends a **Snapdragon 8 Gen 2 or newer**. The [published measurements](doc:performance#published-measurements) were taken on a POCO F7 (Snapdragon 8s Gen 4, Adreno 825).

### Custom drivers

Drivers like Turnip load only on **Adreno GPUs with Qualcomm's KGSL kernel driver**: the app checks whether the device has `/dev/kgsl-3d0`. On other devices every game uses the system Vulkan driver, and the GPU drivers screen says so. See [GPU drivers](doc:drivers).

## What the app checks on first launch

The first step of the setup wizard shows four checks:

| Check | Blocks? | What it says |
|---|---|---|
| Vulkan GPU | yes | with no Vulkan device, games do not run on this phone |
| 64-bit ARM | yes | the core is built for arm64-v8a only |
| Android | no | on Android 10, warns that choosing a game folder needs Android 11 |
| Game folder | no | says whether a folder is already set |

## Permissions

| Permission | What for |
|---|---|
| All Files Access | reading game folders by path, creating the default folders |
| Install unknown apps | installing the updates the app downloads itself |
| Internet and network state | checking for updates, downloading drivers, phone as controller on the local network |
| Vibration | vibrating the phone for the touch controls |

Legacy storage access (read and write) is only requested on the Android versions that still use that model.

## Heat and battery

Emulating an Xbox 360 is demanding on the GPU and the CPU. The app warns you when the phone gets close to its heat limit and when it starts slowing down; the HUD shows temperatures and battery. Lower FPS limits, a 1× resolution scale and not forcing maximum GPU clocks keep the device cooler.

## What is not specified

The project does not publish a list of tested devices or a minimum amount of RAM. The only published measurements are the ones in the README, on a single device.
