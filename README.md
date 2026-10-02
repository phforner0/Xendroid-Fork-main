<p align="center">
  <img src="https://github.com/user-attachments/assets/74d8342e-bbb7-4219-bd59-aa7eacb12b34" width="180" alt="XenDroid Logo">
</p>
**XenDroid Fork** is an open-source Android emulator project built upon the original XenDroid codebase. This custom release focuses on providing players with enhanced control over frame rates and clear, real-time hardware diagnostic monitoring directly during gameplay.

**Key Features:**

* **Unified In-Game Menu:** Graphics, HUD, Controls and Session pages, Back/Guide access, a touch handle, shoulder-button navigation and a selected row that scrolls into view. Tabs and Continue/Exit actions remain visible on short screens.
* **Scoped FPS Configuration:** **Unlimited, 30, 45, 60, 90 and 120 FPS**, applied live to the session. Explicit actions save the current limit globally or for the active Title ID, or remove just the FPS override to inherit the global setting. Config edits preserve unknown TOML entries and use atomic, locked updates.
* **Configurable Performance HUD:** Compact FPS/frame-time view or detailed metrics with selectable rows for CPU, GPU, RAM and temperatures. `Vulkan submitted` counts accepted submissions to the compositor, **not measured display scanout**. Unavailable GPU counters report N/A.
* **Library Shortcuts:** Search by name/Title ID, persistent favorites, sorting, visible focus and controller shortcuts for launch/details/favorites. New focus restoration and controller behavior are awaiting additional device validation.
* **Adaptive Touch Sticks (Opt-In):** Enable per game in Controls to place sticks near their saved anchors at touch-down; fixed buttons take priority. Off by default; physical multitouch validation is pending.
* **Diagnostics and Drivers:** Share a redacted, bounded diagnostics ZIP, including previous sessions, through Android's share sheet. Driver downloads verify a published SHA-256 when available, and the manager can return to the previous selection separately for global and per-game settings.
* **Data Safety:** Deleting a profile moves it, with every save it holds, to a restorable trash after showing which games are affected; interrupted save restores are rolled back before a game boots, and a launch that cannot safely start explains why. Awaiting device validation.
* **Play History:** Each game run is recorded until it ends (or is closed as interrupted after a crash), giving a "Recently played" sort and play time in the game sheet, with the last run's guest FPS and frame-time percentiles, the Vulkan driver it actually ran on and a timeline of host events (pauses, stalls, heat, controllers, errors).
* **Compatibility Notes:** Rate how a game runs on the current build yourself; each result keeps the build, GPU, driver and disc it was seen on. Nothing is guessed or sent anywhere.
* **Performance Enhancements:** Core code adjustments aim to provide smoother frame delivery compared to the stock version, helping to reduce stutters in supported scenarios.

Implementation status, APK provenance and pending validation are recorded in
[docs/experiencia-em-jogo-status.md](docs/experiencia-em-jogo-status.md).
The consolidated roadmap is
[docs/plano-evolucao-cinco-referencias.md](docs/plano-evolucao-cinco-referencias.md),
with research across X360 Mobile, Bannerlator, DroidDeck, Eden and GameHub,
prioritized implementation phases and validation criteria. The
[initial plan](docs/plano-experiencia-em-jogo.md) preserves earlier decisions.
**Experimental frame generation is now integrated:** Win-FG 2× and user-imported
LSFG Native 2×/3×/4×. Both start disabled; software Vulkan synthesis tests passed,
but Android display cadence, gameplay artifacts, latency and performance still
require device validation. Import your own `Lossless.dll`; no DLL or extracted
LSFG shaders are packaged in the APK. See [third-party notices](THIRD-PARTY-NOTICES.md).

The current build also includes save/profile backup and restore with a recovery
journal, immutable driver-package installation, per-session diagnostics, live
display/scaling and audio controls, and opt-in external-display, gyro, energy and
backup-provider features. Their device/service validation status is recorded in
the execution document linked above.

**Experimental Notice & Requirements:**

Please note that this project is currently in an **experimental state**. Because of ongoing optimizations and deep system tweaks, performance, frame consistency, and stability will vary significantly depending on the specific game, application, or your device's hardware. Certain titles may run noticeably smoother, while others might encounter compatibility issues or visual bugs.

* **Recommended Hardware:** For optimal performance and stable high frame rates, a **Snapdragon 8 Gen 2 or higher** processor (with Turnip/Adreno drivers) is strongly recommended.

Legal Disclaimer & Anti-Piracy Policy:

XenDroid Fork is strictly an educational, open-source project designed for software development and performance testing purposes.

This project does not condone, support, or promote software piracy in any form.

No copyrighted files, ROMs, ISOs, game titles, or proprietary system keys are included with this software or provided by the developer.

Users are legally required to dump and use only their own legally acquired game backups and files from hardware they physically own.
<img width="2048" height="922" alt="image" src="https://github.com/user-attachments/assets/50309880-9595-4547-abc8-0027263937cc" />
## Credits
* Original XenDroid project by **[rfandango](https://github.com/rfandango)**.*
## Discord
https://discord.gg/AT8Bswv62
