# Planet Record Attempt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let players tap any already-visited planet on the main menu to pay diamonds and attempt a speed record, with full state preservation of their main-game progress.

**Architecture:** Replay mode is a flag + snapshot pattern inside `ShipData`. `startReplay()` snapshots all mutable lab/flight state into backup fields, clears it for a fresh run, and sets the replay planet's gravity. `endReplay()` restores the snapshot. `BridgeFlightScreen` and `EngineeringLabScreen` check `isReplayMode` at exactly two branch points each. The main menu gains tap detection for past planets plus two new Scene2D overlay tables.

**Tech Stack:** LibGDX 1.12.1, Box2D, Scene2D (Table/TextButton/Label), ShapeRenderer, BitmapFont.

---

## Files Modified

| File | What changes |
|---|---|
| `src/com/odyssey/ShipData.java` | New replay fields, `startReplay()`, `endReplay()`, `abandonReplay()`, persistence |
| `src/com/odyssey/screen/BridgeFlightScreen.java` | Use replay planet index in `buildSectorDistances()` and `finish()` |
| `src/com/odyssey/screen/EngineeringLabScreen.java` | Skip `consumeLife()` when `isReplayMode` |
| `src/com/odyssey/screen/MainMenuScreen.java` | Past-planet tap, record dialogue overlay, result overlay |

---

## Task 1 — ShipData: replay state fields and lifecycle methods

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

### Background
`ShipData` is the single source of truth for all game state. We add three kinds of fields:
1. Active replay metadata (`isReplayMode`, `replayPlanetIndex`)
2. Backup of all lab/flight fields that will be cleared for the replay
3. Result data communicated back to `MainMenuScreen` after the run

The backup approach ensures `onPause()` → `save()` → app-killed → `load()` always produces intact main-progress state. If `isReplayMode` is persisted and is true on load, `abandonReplay()` restores the backup immediately.

- [ ] **Step 1.1 — Add replay flag and metadata fields**

In `ShipData.java`, after line 120 (the last `saved*` field), add:

```java
// ── Replay mode ───────────────────────────────────────────────────────────
public boolean isReplayMode      = false;
public int     replayPlanetIndex = 0;

// Pending result for MainMenuScreen to display after a replay run
public boolean pendingReplayResult      = false;
public boolean pendingReplayIsNewRecord = false;
public float   pendingReplayTime        = 0f;
public int     pendingReplayPlanetIdx   = 0;
```

- [ ] **Step 1.2 — Add backup scalar fields**

Immediately after the fields above:

```java
// Backup of main-progress state snapshotted before a replay starts.
// Persisted so app-kill mid-replay can be recovered on next launch.
public float   rb_totalJoules            = 0f;
public float   rb_powerGenerated         = 0f;
public float   rb_energyAtLastLaunch     = 0f;
public float   rb_accumulatedDist        = 0f;
public int     rb_sectorReached          = -1;
public long    rb_flightStartTimeMs      = 0L;
public float   rb_planetGravityMultiplier = 1.0f;
// Saved lab arrays
public float[] rb_savedBumpers           = new float[0];
public float[] rb_savedAttractors        = new float[0];
public float[] rb_savedIcicleNodes       = new float[0];
public float[] rb_savedTeslaCoils        = new float[0];
public float[] rb_savedSpringPads        = new float[0];
public float[] rb_savedPortalPairs       = new float[0];
public float[] rb_savedRelayNodes        = new float[0];
public boolean[] rb_savedMilestoneAchieved = new boolean[6];
// Saved lab scalars
public int     rb_savedBallCount                      = 0;
public int     rb_savedKineticBladeCount              = 0;
public int     rb_savedHubUpgradeTier                 = 0;
public int     rb_savedFrostheimDecision              = 0;
public int     rb_savedGravShiftStep                  = 0;
public float   rb_savedTeslaHarvestRate               = 15f;
public boolean rb_savedFrostheimCpI                   = false;
public boolean rb_savedFrostheimCpII                  = false;
public boolean rb_savedFrostheimCpIII                 = false;
public boolean rb_savedFrostheimIcicleUnlocked        = false;
public boolean rb_savedEmberHeavyChassis              = false;
public boolean rb_savedEmberMagneticRim               = false;
public boolean rb_savedPortalBidirectional            = false;
public boolean rb_savedEmberSpinReversed              = false;
public boolean rb_savedEmberThirdInternUnlocked       = false;
public boolean rb_savedFrostheimThirdInternUnlocked   = false;
public boolean rb_savedFrostheimArmBumpersActive      = false;
```

- [ ] **Step 1.3 — Add `startReplay(int planetIdx)` method**

Add after `consumeLife()` (around line 252):

```java
/**
 * Enter replay mode for a previously-visited planet.
 * Snapshots all mutable lab/flight state into rb_* fields,
 * clears them for a fresh run, sets replay planet gravity,
 * and deducts diamond cost (50 × (planetIdx+1)).
 */
public void startReplay(int planetIdx) {
    // --- snapshot scalars ---
    rb_totalJoules             = totalJoules;
    rb_powerGenerated          = powerGenerated;
    rb_energyAtLastLaunch      = energyAtLastLaunch;
    rb_accumulatedDist         = accumulatedDist;
    rb_sectorReached           = sectorReached;
    rb_flightStartTimeMs       = flightStartTimeMs;
    rb_planetGravityMultiplier = planetGravityMultiplier;
    // --- snapshot arrays ---
    rb_savedBumpers             = savedBumpers.clone();
    rb_savedAttractors          = savedAttractors.clone();
    rb_savedIcicleNodes         = savedIcicleNodes.clone();
    rb_savedTeslaCoils          = savedTeslaCoils.clone();
    rb_savedSpringPads          = savedSpringPads.clone();
    rb_savedPortalPairs         = savedPortalPairs.clone();
    rb_savedRelayNodes          = savedRelayNodes.clone();
    rb_savedMilestoneAchieved   = savedMilestoneAchieved.clone();
    // --- snapshot lab scalars ---
    rb_savedBallCount                   = savedBallCount;
    rb_savedKineticBladeCount           = savedKineticBladeCount;
    rb_savedHubUpgradeTier              = savedHubUpgradeTier;
    rb_savedFrostheimDecision           = savedFrostheimDecision;
    rb_savedGravShiftStep               = savedGravShiftStep;
    rb_savedTeslaHarvestRate            = savedTeslaHarvestRate;
    rb_savedFrostheimCpI                = savedFrostheimCpI;
    rb_savedFrostheimCpII               = savedFrostheimCpII;
    rb_savedFrostheimCpIII              = savedFrostheimCpIII;
    rb_savedFrostheimIcicleUnlocked     = savedFrostheimIcicleUnlocked;
    rb_savedEmberHeavyChassis           = savedEmberHeavyChassis;
    rb_savedEmberMagneticRim            = savedEmberMagneticRim;
    rb_savedPortalBidirectional         = savedPortalBidirectional;
    rb_savedEmberSpinReversed           = savedEmberSpinReversed;
    rb_savedEmberThirdInternUnlocked    = savedEmberThirdInternUnlocked;
    rb_savedFrostheimThirdInternUnlocked = savedFrostheimThirdInternUnlocked;
    rb_savedFrostheimArmBumpersActive   = savedFrostheimArmBumpersActive;

    // --- clear lab/flight state for fresh replay ---
    savedBumpers             = new float[0];
    savedAttractors          = new float[0];
    savedIcicleNodes         = new float[0];
    savedTeslaCoils          = new float[0];
    savedSpringPads          = new float[0];
    savedPortalPairs         = new float[0];
    savedRelayNodes          = new float[0];
    savedMilestoneAchieved   = new boolean[6];
    savedBallCount           = 0;
    savedKineticBladeCount   = 0;
    savedHubUpgradeTier      = 0;
    savedFrostheimDecision   = 0;
    savedGravShiftStep       = 0;
    savedTeslaHarvestRate    = 15f;
    savedFrostheimCpI        = false;
    savedFrostheimCpII       = false;
    savedFrostheimCpIII      = false;
    savedFrostheimIcicleUnlocked  = false;
    savedEmberHeavyChassis   = false;
    savedEmberMagneticRim    = false;
    savedPortalBidirectional = false;
    savedEmberSpinReversed   = false;
    savedEmberThirdInternUnlocked    = false;
    savedFrostheimThirdInternUnlocked = false;
    savedFrostheimArmBumpersActive   = false;
    totalJoules          = 0f;
    energyAtLastLaunch   = powerGenerated; // delta starts at 0 for replay launch
    accumulatedDist      = 0f;
    sectorReached        = -1;
    flightStartTimeMs    = 0L;
    planetGravityMultiplier = PLANETS[planetIdx].gravityMultiplier;

    // --- deduct diamond cost ---
    diamonds -= 50 * (planetIdx + 1);

    // --- enter replay mode ---
    isReplayMode      = true;
    replayPlanetIndex = planetIdx;
}
```

- [ ] **Step 1.4 — Add `endReplay(float elapsedSeconds)` method**

Add after `startReplay()`:

```java
/**
 * Exit replay mode after a successful arrival.
 * Records the time if it beats the current best, sets pendingReplay* for display,
 * and fully restores main-progress state from rb_* snapshot.
 */
public void endReplay(float elapsedSeconds) {
    // record time
    boolean isNew = elapsedSeconds < bestArrivalTimes[replayPlanetIndex];
    if (isNew) bestArrivalTimes[replayPlanetIndex] = elapsedSeconds;

    // set result for MainMenuScreen
    pendingReplayResult      = true;
    pendingReplayIsNewRecord = isNew;
    pendingReplayTime        = elapsedSeconds;
    pendingReplayPlanetIdx   = replayPlanetIndex;

    // restore all fields
    _restoreReplayBackup();
    isReplayMode = false;
}

/**
 * Abandon replay (e.g., app killed mid-replay and restarted).
 * Restores main-progress state without recording any result.
 */
public void abandonReplay() {
    _restoreReplayBackup();
    isReplayMode = false;
}

private void _restoreReplayBackup() {
    totalJoules             = rb_totalJoules;
    powerGenerated          = rb_powerGenerated;
    energyAtLastLaunch      = rb_energyAtLastLaunch;
    accumulatedDist         = rb_accumulatedDist;
    sectorReached           = rb_sectorReached;
    flightStartTimeMs       = rb_flightStartTimeMs;
    planetGravityMultiplier = rb_planetGravityMultiplier;
    savedBumpers            = rb_savedBumpers;
    savedAttractors         = rb_savedAttractors;
    savedIcicleNodes        = rb_savedIcicleNodes;
    savedTeslaCoils         = rb_savedTeslaCoils;
    savedSpringPads         = rb_savedSpringPads;
    savedPortalPairs        = rb_savedPortalPairs;
    savedRelayNodes         = rb_savedRelayNodes;
    savedMilestoneAchieved  = rb_savedMilestoneAchieved;
    savedBallCount                    = rb_savedBallCount;
    savedKineticBladeCount            = rb_savedKineticBladeCount;
    savedHubUpgradeTier               = rb_savedHubUpgradeTier;
    savedFrostheimDecision            = rb_savedFrostheimDecision;
    savedGravShiftStep                = rb_savedGravShiftStep;
    savedTeslaHarvestRate             = rb_savedTeslaHarvestRate;
    savedFrostheimCpI                 = rb_savedFrostheimCpI;
    savedFrostheimCpII                = rb_savedFrostheimCpII;
    savedFrostheimCpIII               = rb_savedFrostheimCpIII;
    savedFrostheimIcicleUnlocked      = rb_savedFrostheimIcicleUnlocked;
    savedEmberHeavyChassis            = rb_savedEmberHeavyChassis;
    savedEmberMagneticRim             = rb_savedEmberMagneticRim;
    savedPortalBidirectional          = rb_savedPortalBidirectional;
    savedEmberSpinReversed            = rb_savedEmberSpinReversed;
    savedEmberThirdInternUnlocked     = rb_savedEmberThirdInternUnlocked;
    savedFrostheimThirdInternUnlocked = rb_savedFrostheimThirdInternUnlocked;
    savedFrostheimArmBumpersActive    = rb_savedFrostheimArmBumpersActive;
}
```

- [ ] **Step 1.5 — Persist replay state in `save()` and `load()`**

In `save()` (around line 360), before `p.flush()`, add:

```java
p.putBoolean("isReplayMode",    isReplayMode);
p.putInteger("replayPlanetIdx", replayPlanetIndex);
if (isReplayMode) {
    p.putFloat("rb_totalJoules",            rb_totalJoules);
    p.putFloat("rb_powerGenerated",         rb_powerGenerated);
    p.putFloat("rb_energyAtLastLaunch",     rb_energyAtLastLaunch);
    p.putFloat("rb_accumulatedDist",        rb_accumulatedDist);
    p.putInteger("rb_sectorReached",        rb_sectorReached);
    p.putLong("rb_flightStartTimeMs",       rb_flightStartTimeMs);
    p.putFloat("rb_planetGravityMult",      rb_planetGravityMultiplier);
    p.putString("rb_bumpers",   floatsToString(rb_savedBumpers));
    p.putString("rb_attractors",floatsToString(rb_savedAttractors));
    p.putString("rb_icicles",   floatsToString(rb_savedIcicleNodes));
    p.putString("rb_tesla",     floatsToString(rb_savedTeslaCoils));
    p.putString("rb_springs",   floatsToString(rb_savedSpringPads));
    p.putString("rb_portals",   floatsToString(rb_savedPortalPairs));
    p.putString("rb_relays",    floatsToString(rb_savedRelayNodes));
    p.putInteger("rb_ballCount",            rb_savedBallCount);
    p.putInteger("rb_kineticBladeCount",    rb_savedKineticBladeCount);
    p.putInteger("rb_hubTier",              rb_savedHubUpgradeTier);
    p.putInteger("rb_fhDecision",           rb_savedFrostheimDecision);
    p.putInteger("rb_gravShiftStep",        rb_savedGravShiftStep);
    p.putFloat("rb_teslaRate",              rb_savedTeslaHarvestRate);
    p.putBoolean("rb_fhCpI",   rb_savedFrostheimCpI);
    p.putBoolean("rb_fhCpII",  rb_savedFrostheimCpII);
    p.putBoolean("rb_fhCpIII", rb_savedFrostheimCpIII);
    p.putBoolean("rb_fhIcicle",rb_savedFrostheimIcicleUnlocked);
    p.putBoolean("rb_emHeavy", rb_savedEmberHeavyChassis);
    p.putBoolean("rb_emMagnet",rb_savedEmberMagneticRim);
    p.putBoolean("rb_portalBidir",   rb_savedPortalBidirectional);
    p.putBoolean("rb_emSpinRev",     rb_savedEmberSpinReversed);
    p.putBoolean("rb_emThirdIntern", rb_savedEmberThirdInternUnlocked);
    p.putBoolean("rb_fhThirdIntern", rb_savedFrostheimThirdInternUnlocked);
    p.putBoolean("rb_fhArmBumpers",  rb_savedFrostheimArmBumpersActive);
    // milestone array: store as comma-separated ints
    StringBuilder msb = new StringBuilder();
    for (int i = 0; i < rb_savedMilestoneAchieved.length; i++) {
        if (i > 0) msb.append(',');
        msb.append(rb_savedMilestoneAchieved[i] ? 1 : 0);
    }
    p.putString("rb_milestones", msb.toString());
}
```

In `load()` (around line 438), after loading the existing fields, add:

```java
isReplayMode      = p.getBoolean("isReplayMode",    false);
replayPlanetIndex = p.getInteger("replayPlanetIdx", 0);
if (isReplayMode) {
    rb_totalJoules            = p.getFloat("rb_totalJoules",        0f);
    rb_powerGenerated         = p.getFloat("rb_powerGenerated",     0f);
    rb_energyAtLastLaunch     = p.getFloat("rb_energyAtLastLaunch", 0f);
    rb_accumulatedDist        = p.getFloat("rb_accumulatedDist",    0f);
    rb_sectorReached          = p.getInteger("rb_sectorReached",   -1);
    rb_flightStartTimeMs      = p.getLong("rb_flightStartTimeMs",  0L);
    rb_planetGravityMultiplier = p.getFloat("rb_planetGravityMult", 1.0f);
    rb_savedBumpers           = stringToFloats(p.getString("rb_bumpers",    ""));
    rb_savedAttractors        = stringToFloats(p.getString("rb_attractors", ""));
    rb_savedIcicleNodes       = stringToFloats(p.getString("rb_icicles",    ""));
    rb_savedTeslaCoils        = stringToFloats(p.getString("rb_tesla",      ""));
    rb_savedSpringPads        = stringToFloats(p.getString("rb_springs",    ""));
    rb_savedPortalPairs       = stringToFloats(p.getString("rb_portals",    ""));
    rb_savedRelayNodes        = stringToFloats(p.getString("rb_relays",     ""));
    rb_savedBallCount                   = p.getInteger("rb_ballCount",         0);
    rb_savedKineticBladeCount           = p.getInteger("rb_kineticBladeCount", 0);
    rb_savedHubUpgradeTier              = p.getInteger("rb_hubTier",           0);
    rb_savedFrostheimDecision           = p.getInteger("rb_fhDecision",        0);
    rb_savedGravShiftStep               = p.getInteger("rb_gravShiftStep",     0);
    rb_savedTeslaHarvestRate            = p.getFloat("rb_teslaRate",          15f);
    rb_savedFrostheimCpI                = p.getBoolean("rb_fhCpI",   false);
    rb_savedFrostheimCpII               = p.getBoolean("rb_fhCpII",  false);
    rb_savedFrostheimCpIII              = p.getBoolean("rb_fhCpIII", false);
    rb_savedFrostheimIcicleUnlocked     = p.getBoolean("rb_fhIcicle",false);
    rb_savedEmberHeavyChassis           = p.getBoolean("rb_emHeavy", false);
    rb_savedEmberMagneticRim            = p.getBoolean("rb_emMagnet",false);
    rb_savedPortalBidirectional         = p.getBoolean("rb_portalBidir",   false);
    rb_savedEmberSpinReversed           = p.getBoolean("rb_emSpinRev",     false);
    rb_savedEmberThirdInternUnlocked    = p.getBoolean("rb_emThirdIntern", false);
    rb_savedFrostheimThirdInternUnlocked = p.getBoolean("rb_fhThirdIntern",false);
    rb_savedFrostheimArmBumpersActive   = p.getBoolean("rb_fhArmBumpers",  false);
    String msStr = p.getString("rb_milestones", "");
    if (!msStr.isEmpty()) {
        String[] parts = msStr.split(",");
        for (int i = 0; i < parts.length && i < rb_savedMilestoneAchieved.length; i++)
            rb_savedMilestoneAchieved[i] = parts[i].equals("1");
    }
    // App was killed mid-replay — silently abandon and restore main progress
    abandonReplay();
}
```

- [ ] **Step 1.6 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.7 — Commit**

```bash
git add src/com/odyssey/ShipData.java
git commit -m "feat(replay): add replay state fields and startReplay/endReplay/abandonReplay to ShipData"
```

---

## Task 2 — BridgeFlightScreen: use replay planet index

**Files:**
- Modify: `src/com/odyssey/screen/BridgeFlightScreen.java:207-213` (buildSectorDistances)
- Modify: `src/com/odyssey/screen/BridgeFlightScreen.java:520-544` (finish)

- [ ] **Step 2.1 — Fix `buildSectorDistances()` to use replay planet when in replay mode**

Current code at line 208:
```java
int pidx = ShipData.get().currentPlanetIndex;
```

Replace with:
```java
ShipData _sd = ShipData.get();
int pidx = _sd.isReplayMode ? _sd.replayPlanetIndex : _sd.currentPlanetIndex;
```

- [ ] **Step 2.2 — Fix `finish()` for replay mode**

Current arrived-branch in `finish()` (lines 522–537):
```java
if (arrived) {
    sd.markArrival(totalRoute, totalRoute / ENERGY_AU_SCALE);
    // Record leaderboard arrival time before claimArrivalReward resets the timer
    if (sd.flightStartTimeMs != 0L) {
        float elapsed = (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f;
        int pIdx = sd.currentPlanetIndex;
        if (elapsed < sd.bestArrivalTimes[pIdx]) {
            sd.bestArrivalTimes[pIdx] = elapsed;
        }
        sd.pendingRankResult = com.odyssey.FakeLeaderboard.getRank(pIdx, sd.bestArrivalTimes[pIdx]);
        sd.pendingRankPlanet = pIdx;
        sd.save();
    }
    sd.claimArrivalReward();
    SoundManager.get().playMilestone();
    game.transitionTo(GameState.INTERN_DEPLOY);
```

Replace with:
```java
if (arrived) {
    if (sd.isReplayMode) {
        // Replay: record time, restore main state, return to main menu
        float elapsed = sd.flightStartTimeMs != 0L
            ? (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f
            : 0f;
        sd.endReplay(elapsed);
        sd.save();
        SoundManager.get().playMilestone();
        game.transitionTo(GameState.MAIN_MENU);
        return;
    }
    sd.markArrival(totalRoute, totalRoute / ENERGY_AU_SCALE);
    // Record leaderboard arrival time before claimArrivalReward resets the timer
    if (sd.flightStartTimeMs != 0L) {
        float elapsed = (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f;
        int pIdx = sd.currentPlanetIndex;
        if (elapsed < sd.bestArrivalTimes[pIdx]) {
            sd.bestArrivalTimes[pIdx] = elapsed;
        }
        sd.pendingRankResult = com.odyssey.FakeLeaderboard.getRank(pIdx, sd.bestArrivalTimes[pIdx]);
        sd.pendingRankPlanet = pIdx;
        sd.save();
    }
    sd.claimArrivalReward();
    SoundManager.get().playMilestone();
    game.transitionTo(GameState.INTERN_DEPLOY);
```

- [ ] **Step 2.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2.4 — Commit**

```bash
git add src/com/odyssey/screen/BridgeFlightScreen.java
git commit -m "feat(replay): use replayPlanetIndex in BridgeFlightScreen; return to menu on replay arrival"
```

---

## Task 3 — EngineeringLabScreen: skip consumeLife() in replay mode

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java:3097-3103`

- [ ] **Step 3.1 — Guard consumeLife() call**

Current code at line 3097–3103:
```java
ShipData sd2 = ShipData.get();
// Lives gate
if (!sd2.canPlay()) {
    if (livesBlockTable != null) livesBlockTable.setVisible(true);
    return;
}
sd2.consumeLife();
```

Replace with:
```java
ShipData sd2 = ShipData.get();
if (!sd2.isReplayMode) {
    // Lives gate — skipped during replay (diamonds already paid as entry fee)
    if (!sd2.canPlay()) {
        if (livesBlockTable != null) livesBlockTable.setVisible(true);
        return;
    }
    sd2.consumeLife();
}
```

- [ ] **Step 3.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.3 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(replay): skip consumeLife() in replay mode"
```

---

## Task 4 — MainMenuScreen: detect taps on past planets

**Files:**
- Modify: `src/com/odyssey/screen/MainMenuScreen.java` — `touchDown` handler and new field

- [ ] **Step 4.1 — Add replay dialogue field declaration**

Near the other Stage/Table fields (around line 80), add:
```java
private Table replayDialogue;   // record-attempt overlay for past planets
private Table replayResultOverlay; // result shown after returning from a replay
```

- [ ] **Step 4.2 — Add past-planet hit-test in touchDown**

In the `touchDown` InputAdapter (around line 121), after the current-planet arrival hit-test block (lines 130–137) and before the NEW GAME button hit-test, add:

```java
// Past-planet tap: open record attempt dialogue
for (int pi = 0; pi < currentIdx; pi++) {
    float pcx = NX[pi], pcy = NY[pi], pr = NR[pi];
    float pdx = tv.x - pcx, pdy = tv.y - pcy;
    if (pdx*pdx + pdy*pdy < pr*pr) {
        openReplayDialogue(pi);
        return true;
    }
}
```

- [ ] **Step 4.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.4 — Commit**

```bash
git add src/com/odyssey/screen/MainMenuScreen.java
git commit -m "feat(replay): detect past-planet taps in MainMenuScreen"
```

---

## Task 5 — MainMenuScreen: build record attempt dialogue

**Files:**
- Modify: `src/com/odyssey/screen/MainMenuScreen.java`

The dialogue is a Scene2D `Table` overlay built in `openReplayDialogue(int pi)`, following the exact same pattern as `shopOverlay` (dark background, centered, Scene2D touch).

- [ ] **Step 5.1 — Add `openReplayDialogue(int pi)` method**

Add after `addShopCard()` (around line 288):

```java
/** Builds and shows the record-attempt dialogue for planet at index pi. */
private void openReplayDialogue(int pi) {
    // Tear down any previous instance
    if (replayDialogue != null) { replayDialogue.remove(); replayDialogue = null; }

    ShipData sd   = ShipData.get();
    String planet = ShipData.PLANETS[pi].name;
    int cost      = 50 * (pi + 1);
    boolean canAfford = sd.diamonds >= cost;

    // Best time text
    String bestText;
    if (sd.bestArrivalTimes[pi] == Float.MAX_VALUE) {
        bestText = "No record yet";
    } else {
        int totalSecs = (int) sd.bestArrivalTimes[pi];
        bestText = String.format("Best: %d:%02d", totalSecs / 60, totalSecs % 60);
    }

    replayDialogue = new Table();
    replayDialogue.setFillParent(true);
    replayDialogue.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
    replayDialogue.background(game.skin.newDrawable("white",
        new com.badlogic.gdx.graphics.Color(0f, 0.03f, 0.12f, 0.95f)));
    replayDialogue.center();

    Label title = new Label(planet, game.skin);
    title.setColor(0.35f, 1.00f, 0.85f, 1f);
    title.setFontScale(1.6f);
    replayDialogue.add(title).padBottom(8f).row();

    Label bestLbl = new Label(bestText, game.skin);
    bestLbl.setColor(0.70f, 0.80f, 0.90f, 0.90f);
    replayDialogue.add(bestLbl).padBottom(4f).row();

    Label costLbl = new Label("Cost: " + cost + " \u25C6", game.skin);
    costLbl.setColor(canAfford ? new com.badlogic.gdx.graphics.Color(0.38f, 0.92f, 1f, 1f)
                               : new com.badlogic.gdx.graphics.Color(0.55f, 0.55f, 0.55f, 0.70f));
    replayDialogue.add(costLbl).padBottom(24f).row();

    TextButton tryBtn = new TextButton("TRY FOR RECORD", game.skin);
    tryBtn.setColor(canAfford ? com.odyssey.OdysseyTheme.BTN_NORMAL
                              : com.odyssey.OdysseyTheme.BTN_LOCKED);
    tryBtn.setDisabled(!canAfford);
    if (canAfford) {
        final int planetIdx = pi;
        tryBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                replayDialogue.remove();
                replayDialogue = null;
                ShipData.get().startReplay(planetIdx);
                game.forceRebuildLab();
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
    }
    replayDialogue.add(tryBtn).width(260f).height(52f).padBottom(10f).row();

    TextButton cancelBtn = new TextButton("CANCEL", game.skin);
    cancelBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
        @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
            replayDialogue.remove();
            replayDialogue = null;
        }
    });
    replayDialogue.add(cancelBtn).width(200f).height(48f).row();

    stage.addActor(replayDialogue);
}
```

- [ ] **Step 5.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5.3 — Commit**

```bash
git add src/com/odyssey/screen/MainMenuScreen.java
git commit -m "feat(replay): add openReplayDialogue() to MainMenuScreen"
```

---

## Task 6 — MainMenuScreen: replay result overlay

**Files:**
- Modify: `src/com/odyssey/screen/MainMenuScreen.java`

When returning from a replay run, `MainMenuScreen.show()` is called. If `sd.pendingReplayResult` is set, build and display the result overlay.

- [ ] **Step 6.1 — Add `buildReplayResultOverlay()` method**

Add after `openReplayDialogue()`:

```java
/** Builds and shows the replay result overlay. Clears pendingReplayResult. */
private void buildReplayResultOverlay() {
    ShipData sd = ShipData.get();
    if (!sd.pendingReplayResult) return;
    sd.pendingReplayResult = false;

    if (replayResultOverlay != null) { replayResultOverlay.remove(); replayResultOverlay = null; }

    String planet = ShipData.PLANETS[sd.pendingReplayPlanetIdx].name;
    int totalSecs = (int) sd.pendingReplayTime;
    String timeStr = String.format("%d:%02d", totalSecs / 60, totalSecs % 60);

    replayResultOverlay = new Table();
    replayResultOverlay.setFillParent(true);
    replayResultOverlay.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
    replayResultOverlay.background(game.skin.newDrawable("white",
        new com.badlogic.gdx.graphics.Color(0f, 0.03f, 0.12f, 0.95f)));
    replayResultOverlay.center();

    Label planetLbl = new Label(planet, game.skin);
    planetLbl.setColor(0.35f, 1.00f, 0.85f, 1f);
    planetLbl.setFontScale(1.5f);
    replayResultOverlay.add(planetLbl).padBottom(12f).row();

    if (sd.pendingReplayIsNewRecord) {
        Label newRec = new Label("NEW RECORD!", game.skin);
        newRec.setColor(1.00f, 0.82f, 0.10f, 1f);
        newRec.setFontScale(1.8f);
        replayResultOverlay.add(newRec).padBottom(8f).row();
    }

    Label timeLbl = new Label(timeStr, game.skin);
    timeLbl.setColor(1f, 1f, 1f, 0.95f);
    timeLbl.setFontScale(2.0f);
    replayResultOverlay.add(timeLbl).padBottom(6f).row();

    if (!sd.pendingReplayIsNewRecord) {
        float best = sd.bestArrivalTimes[sd.pendingReplayPlanetIdx];
        int bestSecs = (int) best;
        String bestStr = best == Float.MAX_VALUE ? "—"
            : String.format("Best: %d:%02d", bestSecs / 60, bestSecs % 60);
        Label bestLbl = new Label(bestStr, game.skin);
        bestLbl.setColor(0.55f, 0.65f, 0.75f, 0.85f);
        replayResultOverlay.add(bestLbl).padBottom(20f).row();
    } else {
        replayResultOverlay.add(new Label("", game.skin)).padBottom(20f).row();
    }

    TextButton closeBtn = new TextButton("CLOSE", game.skin);
    closeBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
        @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
            replayResultOverlay.remove();
            replayResultOverlay = null;
        }
    });
    replayResultOverlay.add(closeBtn).width(200f).height(52f).row();

    stage.addActor(replayResultOverlay);
}
```

- [ ] **Step 6.2 — Call `buildReplayResultOverlay()` from `show()`**

In `MainMenuScreen.show()`, after the existing rank-popup setup (around line 285), add:

```java
buildReplayResultOverlay();
```

- [ ] **Step 6.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6.4 — Build and install APK**

```bash
./gradlew android:copyAndroidNatives android:installDebug 2>&1 | tail -8
```
Expected: `Installed on 1 device.`

- [ ] **Step 6.5 — Manual verification checklist**

On the Pixel 4a (must have at least 1 planet visited to test):
- [ ] Tap a visited planet → record dialogue appears with planet name, best time (or "No record yet"), diamond cost, TRY / CANCEL
- [ ] With insufficient diamonds: TRY button is grey / disabled
- [ ] CANCEL dismisses the dialogue without changing state
- [ ] With sufficient diamonds: tap TRY → diamonds deducted, lab opens fresh (no bumpers from main game), lab gravity matches replay planet
- [ ] From the replay lab, LAUNCH → flight uses correct planet thresholds
- [ ] Reach final sector → transitions to main menu, result overlay appears (first run: "No record yet" → shows new time as "NEW RECORD!")
- [ ] CLOSE dismisses result overlay; main game lab state is intact (original bumpers, gravity, flight progress preserved)
- [ ] Hit a checkpoint (not arrival) in replay flight → back to lab, no life deducted
- [ ] Kill app while in replay lab → relaunch → main menu shows with original state intact (no result overlay, no lost progress)

- [ ] **Step 6.6 — Commit**

```bash
git add src/com/odyssey/screen/MainMenuScreen.java
git commit -m "feat(replay): add buildReplayResultOverlay() and wire into show()"
```

---

## Notes for Implementer

- `floatsToString()` and `stringToFloats()` already exist in ShipData — check the existing save code (around line 320) to find the exact method signatures and use the same pattern.
- `OdysseyTheme.BTN_NORMAL` and `BTN_LOCKED` are `Color` constants defined in `src/com/odyssey/OdysseyTheme.java` — import if needed.
- `game.forceRebuildLab()` is already defined in `OdysseyGame.java` — it sets `labScreen = null` so the next `transitionTo(ENGINEERING_LAB)` rebuilds from current ShipData state.
- The `\u25C6` in the cost label is a filled diamond ◆ character (matches the gem icon style).
- `Label.setFontScale()` is not a real LibGDX method — use `label.getStyle().font.getData().setScale()` per-font if needed, or just use the skin's font sizes as-is and omit scale calls if they cause issues. Alternatively use the existing `smallFont`/`bodyFont` drawn via SpriteBatch like other screens do.
