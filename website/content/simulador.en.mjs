// English texts of content/simulador.mjs (same ids and order).

export const SIM_GROUP_TITLES_EN = [
  'Library and game',
  'Settings and drivers',
  'Running a game',
  'Controls',
  'Profiles and saves',
  'Content and diagnostics',
  'First run and app',
  'Game panels',
];

export const SIM_SCREENS_EN = {
  library: {
    what: 'Your game library. In touch mode: a cover grid in three sizes, search by name or Title ID, sorting, filters always in view and, in landscape, the selected game\'s panel with Play and the quick settings. In controller mode: a cover carousel with tabs switched by LB and RB and the color taken from the cover.',
    where: 'The app\'s home screen; in the rail, Games.',
    c: 'Cover carousel, tabs with LB/RB, Y marks a favorite, X opens the details, Start (≡) opens the app menu.',
  },
  game: {
    what: 'A game\'s details: Play and Start with… always in view, the overview, this game\'s settings by group (with a switch to the global ones), session performance, patches and content, saves and data. The TOML button shows the game\'s configuration file in the format the core reads.',
    where: 'Library → tap a game (in landscape, Details in the side panel; or a long press).',
    c: 'Vertical menu (Play, Start with…, Quick settings, All settings, Performance, Patches and content, Saves and data, Compatibility); ◀ ▶ changes values.',
  },
  settings: {
    what: 'The settings that apply to every game: a summary of what is not at its default and of the games with settings of their own, the emulator\'s eight settings groups and the app\'s own options (interface, language, data and backup, updates, community, diagnostics).',
    where: 'Rail → Settings (at the bottom); with a controller, Start menu → Settings.',
    c: 'Vertical menu with the same groups; settings in ◀ ▶ rows, LB/RB switch groups.',
  },
  drivers: {
    what: 'The GPU driver manager: the chosen driver and the one the last session loaded, the suggestion for your GPU, the installed ones with the result of the SHA-256 check, the ones available from the sources, the sources and the Turnip flags.',
    where: 'Rail → Drivers (in landscape); Start menu with a controller; in portrait, Settings → Summary → Driver → Manage drivers.',
    c: 'Same sections in the vertical menu; A uses or downloads the focused driver.',
  },
  loading: {
    what: 'A game loading: the launch steps with the time each one took, the count of pipelines created and the driver, limit and scale the game is launching with. The first time a game launches, a notice says it takes longer.',
    where: 'Play, in the library or in the details.',
    c: 'Same; B goes back to the library.',
  },
  launchfail: {
    what: 'When the game does not launch: the reason in one sentence, the last lines of the log and the next step for that reason (try with the system driver, open the saves, try again). The Reason selector, in the simulator bar, switches the example.',
    where: 'Shows up on its own when a game fails to launch.',
    c: 'Same, with A on the main button and B to go back.',
  },
  ingame: {
    what: 'The in-game menu: a panel over the game with five categories (Image, Performance, HUD, Controls and Session), each row with its value, changed right there. What you change is kept for the game, and can be undone or used in every game.',
    where: 'With the game running: swipe in from the left edge, tap Back or press the controller\'s Guide button.',
    c: 'Larger panel; ↑ ↓ move between rows, ◀ ▶ change the value, A activates, LB/RB switch category, B closes.',
  },
  hud: {
    what: 'The performance HUD over the game: FPS only, metrics or the full panel (pacing, work, heat and settings in effect), in a vertical box or a horizontal bar, and the heat warning. Layout, detail and warning change with the selectors in the simulator bar; in the app, in the in-game menu → HUD.',
    where: 'In-game menu → HUD → Show the HUD.',
    c: 'Same (the HUD does not change with the mode).',
  },
  controls: {
    what: 'The Controls area: who plays as P1 to P4, the touch options and saved layouts, each physical controller with its own vibration, Vibration and motion, phones as controllers and the four tools.',
    where: 'Rail → Controls.',
    c: 'Same sections in the vertical menu.',
  },
  keymap: {
    what: 'Key mapping: a drawn controller and the list of the 16 buttons with each one\'s key. Capture changes a button in one tap; a key already in use swaps places.',
    where: 'Controls → Key mapping.',
    c: 'A picks the button, Y clears, X swaps A/B and X/Y.',
  },
  touchedit: {
    what: 'The touch controls editor: dragging with snap to grid, the panel of the selected control (size, dead zone, show or hide), undo, saved layouts and the general options.',
    where: 'Controls → Touch editor, or in-game menu → Controls → Edit the layout.',
    c: 'LB/RB pick the control, the D-pad moves it one grid step, A opens the panel.',
  },
  padtest: {
    what: 'The controller test: the drawn controller lights up what is pressed, with the sticks and the core\'s dead zone, the triggers, the gyroscope and vibration. Nothing reaches a game.',
    where: 'Controls → Test controllers, or Settings → Diagnostics and tests.',
    c: 'Hold B for a second to leave.',
  },
  phonepad: {
    what: 'Phone as controller, on the phone that will play as P2 to P4: game address, 6-digit code, name shown and vibration. The code 482913 connects in the example.',
    where: 'Controls → Phone as controller. On the phone running the game: in-game menu → Controls → Phone controllers.',
    c: 'Same.',
  },
  profiles: {
    what: 'Profiles: cards with avatar, gamertag, language and region, who is P1, who plays as P2 to P4, asking who plays before each game and the profile trash.',
    where: 'Rail → Profiles.',
    c: 'Large avatars in a row; A opens the profile\'s options.',
  },
  saves: {
    what: 'A game\'s saves by profile (gamertag, size and last save), export, import with a review before restoring, and the synchronization folder with its backups.',
    where: 'Game details → Saves and data → Open saves.',
    c: 'Same sections in the vertical menu.',
  },
  content: {
    what: 'The installed content of every game (DLC and title updates, with sizes), the trash with its quota and installing the packages found in Downloads and in the content folders.',
    where: 'Rail → Content; from the details, only that game\'s.',
    c: 'Same sections in the vertical menu.',
  },
  diagnostics: {
    what: 'Saved sessions with how each one ended and why, the session summary and, before sharing, what goes in the file and what is left out.',
    where: 'Settings → Diagnostics and tests → Diagnostics; game details → Saves and data.',
    c: 'Same sections in the vertical menu.',
  },
  compare: {
    what: 'Compare runs: a game\'s runs marked A or B, the verdict recalculated on the spot with the warnings (order, warm-up, short run, more than one change) and the difference per pair. The numbers in this example are from a made-up game.',
    where: 'Settings → Diagnostics and tests → Compare runs.',
    c: 'Same sections in the vertical menu.',
  },
  firstrun: {
    what: 'The first-run assistant, in five steps with Skip and Back: This phone (Vulkan GPU, 64-bit ARM, Android, folder), Your games, Language and region, Profile and How to use.',
    where: 'Opens on its own the first time; later, library ⋮ menu → Setup assistant, or About.',
    c: 'Each step starts on the main button.',
  },
  folders: {
    what: 'Game folders: how many games each one has, the one that receives installs, those unavailable right now, add, scan again and remove with Undo (the files are never touched).',
    where: 'Library ⋮ menu → Game folders (Android 11 or newer).',
    c: 'Same actions in a list.',
  },
  browse: {
    what: 'The folder browser: internal storage and SD cards, the path as clickable steps, how many games each folder has before you choose, and New folder. It also picks a file (a package in Content or a game that moved).',
    where: 'When adding a game folder, choosing a package or looking for a game that moved.',
    c: 'Same.',
  },
  missing: {
    what: 'Games no longer in the library: saved cover, reason, time played and the previous path; “Where is it now?” opens the browser near the old path.',
    where: 'Library ⋮ menu → Games no longer in the library (only shown when there are any).',
    c: 'Same.',
  },
  novulkan: {
    what: 'The screen for a device without a Vulkan GPU: why, the checks, Quit and Copy the device data.',
    where: 'Shows in place of the library when the device has no Vulkan.',
    c: 'Same.',
  },
  update: {
    what: 'App updates: installed version, channel, last check and the update card with the summary and the steps download, check the SHA-256 and install. Here the previous build plays the installed one, and the one offered is the published stable release, with that release\'s own title, size, SHA-256 and summary. The State selector, in the simulator bar, switches the example.',
    where: 'Settings → Updates; library ⋮ menu → Check for updates; About.',
    c: 'Same.',
  },
  about: {
    what: 'About: the version, the device in a table with Copy all, shortcuts to updates, diagnostics and the assistant, credits and licenses.',
    where: 'Settings → About; Start menu with a controller.',
    c: 'Same.',
  },
  msgbox: {
    what: 'When the game asks a question: a panel that says which game is asking, the text (which scrolls when long) and the options as full-width rows. The game waits until you answer.',
    where: 'Shows up on its own during play.',
    c: 'Button hints at the bottom; B reminds you that the game is waiting.',
  },
  keyboard: {
    what: 'When the game asks for text: the request, the field with the limit counted the way the game counts it, and the grid of letters and symbols with the command keys in English.',
    where: 'Shows up on its own during play (gamertags, save names).',
    c: 'Xbox 360 shortcuts in view: A types, X deletes, Y space, LB/RB cursor, L3 Shift, R3 symbols, ≡ done.',
  },
  discswap: {
    what: 'When the game asks for another disc: the requested disc highlighted and already selected, the discs found with each one\'s file, the previous one marked, and Cancel last.',
    where: 'Shows up on its own in multi-disc games.',
    c: 'Button hints at the bottom.',
  },
};
