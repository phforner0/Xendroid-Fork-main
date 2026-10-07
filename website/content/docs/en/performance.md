---
title: Performance and measurements
description: What each session records, how to read performance in a game's Details, comparing runs, the suggestions after a session, and the measurements the project publishes.
section: usar
order: 6
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/sessions/Benchmark.kt, app/src/main/java/xendroid/compose/sessions/SessionAdvice.kt, app/src/main/java/xendroid/compose/ui/game/GameCards.kt, app/src/main/res/values/strings_advice.xml, README.md]
pt: desempenho
---

## What each session records

Every time a game runs, the app keeps the numbers of that session: FPS for each second, frame time, time to the first frame, pipelines created, late audio, battery temperature, the driver used and the settings in effect.

In the game's Details → **Performance**, the session shown appears at the top (date, duration and FPS); a tap lists all of the game's sessions, with how each one ended, median and low FPS, the frame time p99 and the driver. The charts show how many seconds the game spent at each FPS and how many frames took each frame time, with the run's timeline (stutters, heat warnings, pauses, controllers).

{{> print id=ficha-desempenho legenda="Performance in Details: the session shown and the FPS and frame time charts."}}

The [HUD](doc:interface#hud) shows the same numbers live, while you play.

## Compare runs {#compare-runs}

**Settings → App → Diagnostics and tests → Compare runs** compares runs of the same game marked A and B, to measure **a single change** (driver, FPS limit, frame generation, vblank cap or display refresh). It only gives a result when the comparison is fair:

- an order that cancels out the device warming up: at least **A B B A** (four runs);
- each run with at least 30 measured seconds;
- a similar starting battery temperature across runs (at most 3 °C apart);
- at most one difference between A and B, and a single configuration on each side.

The verdict (“B is faster in every pair”) only appears when all pairs agree. Frames made by frame generation don't count toward FPS. To line up the same part of the game, use **in-game menu → Session → Mark a scene**.

{{> print id=comparar legenda="Compare runs: runs A and B, the result per pair and the warnings."}}

## Suggestions after a session {#end-of-session-suggestions}

When you return to the app after a session with something to improve, the “How did … go” sheet sums up the session and offers up to three suggestions, each with the reason in numbers and **Apply to this game** (with Undo):

| Suggestion | When it appears |
|---|---|
| Try the phone's own driver | the game closed with a native crash while using a custom driver |
| Back to the global settings | the game never showed a frame and has settings of its own |
| Create pipelines on more threads | 500 or more pipelines created, with frames of 50 ms or more and fewer than 5 threads |
| Resolution scale 1x | median FPS below 80 % of the limit with the scale above 1x |
| A steady 30 FPS | limit of 60, median between 34 and 52, and the slowest 5 % below 40 |
| A deeper audio buffer | more than 1 % of the audio blocks filled in because they were late |
| A lower FPS limit | battery at 45 °C or more, or the phone at its heat limit, with a limit above 30 |

Suggestions only appear for sessions that failed or lasted more than a minute; performance suggestions appear at most once a day per game. You can mute one suggestion, one game or all of them, and **Settings → App → Interface → Suggestions after a session** chooses between Always, After errors and Never.

## Published measurements {#published-measurements}

{{> desempenho-readme}}

These measurements are the project author's, on a single device. The methods, the results of each change and the optimization plans are in [performance-tests/](repo:performance-tests/). To measure on your device, use the sessions and Compare runs described above.
