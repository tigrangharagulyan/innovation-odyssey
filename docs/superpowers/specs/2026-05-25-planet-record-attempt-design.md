# Planet Record Attempt — Design Spec
**Date:** 2026-05-25  
**Branch:** feat/ui-polish-neon-dark  

---

## Overview

Players can tap any planet they have already visited (index < `currentPlanetIndex`) to open a
record-attempt dialogue. They pay a diamond fee, get a fresh lab at that planet's gravity, and
try to reach the planet as fast as possible. Their main-game progress is fully preserved and
restored when the replay ends. No lives are consumed during a replay run.

---

## 1. Diamond Cost

`cost = 50 × (planetIndex + 1)`

| Planet | Index | Cost |
|---|---|---|
| Solara | 0 | 50 💎 |
| Nova Terra | 1 | 100 💎 |
| Frostheim | 2 | 150 💎 |
| Cryon Reach | 3 | 200 💎 |
| Helios Forge | 4 | 250 💎 |

---

## 2. Touch Detection (MainMenuScreen)

In `touchDown`, after the existing rocket hit-test and before the current-planet arrival tap,
add a loop over all `i < currentIdx` (previously visited planets):

```
for i in 0 ..< currentIdx:
    circle hit-test at (NX[i], NY[i]) with radius NR[i]
    → if hit: open record dialogue for planet i
```

Tapping future planets (index > currentIdx) or the current planet's node (when not
`arrivalReady`) continues to do nothing.

---

## 3. Record Dialogue (MainMenuScreen)

A Scene2D `Table` overlay, same pattern as `shopOverlay`:

- **Header**: planet name (`ShipData.PLANETS[i].name`)
- **Best time**: formatted as `M:SS.s` — "No record yet" if `bestArrivalTimes[i] == Float.MAX_VALUE`
- **Cost**: "50 💎" / "100 💎" etc.
- **TRY FOR RECORD** button — disabled (greyed) if `sd.diamonds < cost`
- **CANCEL** button

Tapping **TRY FOR RECORD**:
1. `ShipData.get().startReplay(planetIdx)` — deducts diamonds, snapshots state, sets up replay
2. `game.forceRebuildLab()` — ensures lab rebuilds for replay planet's gravity
3. `game.transitionTo(GameState.ENGINEERING_LAB)`

Tapping **CANCEL**: hides the overlay, no state change.

---

## 4. ShipData Changes

### New fields

```java
public boolean isReplayMode       = false;
public int     replayPlanetIndex  = 0;

// Snapshot of main-progress lab state
public float[] replayBackupBumpers;
public int     replayBackupInternCount;
public int     replayBackupCyberInternCount;
// ... (one backup field per saved* lab field)
public float   replayBackupTotalJoules;
public float   replayBackupAccumulatedDist;
public int     replayBackupSectorReached;
public long    replayBackupFlightStartTimeMs;
public float   replayBackupPlanetGravityMultiplier;

// Result communicated back to MainMenuScreen
public boolean pendingReplayResult      = false;
public boolean pendingReplayIsNewRecord = false;
public float   pendingReplayTime        = 0f;
public int     pendingReplayPlanetIdx   = 0;
```

### `startReplay(int planetIdx)`

1. Snapshot all mutable lab/flight fields into `replayBackup*`
2. Clear `savedBumpers`, intern counts, `totalJoules = 0`, `accumulatedDist = 0`,
   `sectorReached = -1`, `flightStartTimeMs = 0`
3. Set `planetGravityMultiplier = PLANETS[planetIdx].gravityMultiplier`
4. Deduct `50 × (planetIdx + 1)` from `diamonds`
5. Set `isReplayMode = true`, `replayPlanetIndex = planetIdx`

### `endReplay(float elapsedSeconds)`

1. Check/update `bestArrivalTimes[replayPlanetIndex]`; set `pendingReplayIsNewRecord`
2. Set `pendingReplayTime = elapsedSeconds`, `pendingReplayPlanetIdx = replayPlanetIndex`,
   `pendingReplayResult = true`
3. Restore all `replayBackup*` fields back to their live counterparts
4. Set `isReplayMode = false`

### Persistence

`isReplayMode` is NOT persisted. If the app is killed mid-replay, on restart the player
returns to main menu with their original state intact (the save always reflects main progress,
never the replay's cleared state). `pendingReplayResult` is also not persisted (result
dismissed on next launch if app was killed before showing).

`replayBackup*` fields are persisted as `"replayBackup_*"` keys — ensures state can be
restored if `onPause()` fires mid-replay.

---

## 5. EngineeringLabScreen Changes

In the LAUNCH button handler, guard `consumeLife()` with:

```java
if (!sd.isReplayMode) {
    sd.consumeLife();
}
```

No other lab changes. Gravity and cleared structures come automatically from `startReplay`
having set `planetGravityMultiplier` and cleared `saved*` fields before `forceRebuildLab()`.

---

## 6. BridgeFlightScreen Changes

### Threshold / route selection

Where the flight reads `sd.currentPlanetIndex` to select energy checkpoint thresholds and
total route distance, replace with:

```java
int flightTarget = sd.isReplayMode ? sd.replayPlanetIndex : sd.currentPlanetIndex;
```

Use `flightTarget` for checkpoint array selection and `PLANETS[flightTarget].distanceKm`.

### On arrival (replay mode)

In `finish()`, after the existing arrived-branch, add:

```java
if (sd.isReplayMode) {
    float elapsed = (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f;
    sd.endReplay(elapsed);
    game.transitionTo(GameState.MAIN_MENU);
    return;
}
```

This fires before the normal `markArrival` / `claimArrivalReward` path, so main progress is
never touched.

### On checkpoint (replay mode)

No change needed — `totalJoules = 0` + back to lab already happens, and the lab skips
`consumeLife()`.

---

## 7. Result Display (MainMenuScreen)

After returning to main menu, `show()` checks `sd.pendingReplayResult`. If true, build and
show a lightweight Scene2D overlay:

- Planet name
- Time: `M:SS` formatted
- "NEW RECORD!" (gold) or `"Best: M:SS"` (dim) depending on `pendingReplayIsNewRecord`
- **CLOSE** button → clears `pendingReplayResult`, hides overlay

The overlay is built fresh in `show()`, same as `rankPopup`.

---

## 8. GameState / Screen Wiring

No new `GameState` values needed. `forceRebuildLab()` already exists in `OdysseyGame` for
rebuilding the lab with new gravity — called before transitioning to `ENGINEERING_LAB` in the
replay start path.

---

## Out of Scope

- Leaderboard integration for replay times (replay times already go into `bestArrivalTimes`,
  which feeds the existing leaderboard — no extra work)
- Partial-replay cancel button in the lab (player can only abandon by killing the app; state
  is restored on restart via persisted backup fields)
- Visual indicator on visited planet nodes (e.g., crown icon for record holder) — future work
