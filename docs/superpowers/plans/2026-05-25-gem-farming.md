# Gem Farming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SP/crystal offline farming with a passive gem farming system where each visited planet earns diamonds at a fixed rate (1–5 gems/hr), capped at 24 hours.

**Architecture:** Three files change — `ShipData` owns all farming math and state, `EngineeringLabScreen` calls claim on entry and removes the harvest modal, `MainMenuScreen` replaces orbiting dots + SP rate label with a gem rate label per planet.

**Tech Stack:** LibGDX 1.12.1, Java, LibGDX Preferences for persistence.

---

## File Map

| File | Change |
|---|---|
| `src/com/odyssey/ShipData.java` | Add gem farming fields/methods; remove old farming fields/methods |
| `src/com/odyssey/screen/EngineeringLabScreen.java` | Swap claim call; remove harvest modal; remove farming timestamp at launch |
| `src/com/odyssey/screen/MainMenuScreen.java` | Replace `drawFarmingDots()` and SP rate label with gem rate label |

---

## Task 1: ShipData — add gem farming constants and fields

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

- [ ] **Step 1: Add constant and field**

Find the block with `FARM_RATE_PER_INTERN` (around line 134) and the fields `internsLeftOnPlanet` / `lastFarmingTimestamp` (around line 130–131). Add the new constant and field directly below, leaving the old ones in place for now:

```java
// Gem farming — fixed gems/hr per planet index (Solara=1 … Helios Forge=5)
public static final int[] GEM_FARM_RATES = {1, 2, 3, 4, 5};
public long lastGemFarmTimestamp = 0L;   // ms epoch; 0 = uninitialised
```

- [ ] **Step 2: Add helper methods**

Add three new methods just before the `// ---- Persistence` comment (currently around line 273):

```java
/** Total gem/hr from all planets unlocked so far. 0 if farming not yet active. */
public int gemFarmRatePerHour() {
    if (arrivalsCompleted < 1) return 0;
    int total = 0;
    int unlocked = Math.min(currentPlanetIndex + 1, GEM_FARM_RATES.length);
    for (int i = 0; i < unlocked; i++) total += GEM_FARM_RATES[i];
    return total;
}

/** Storage cap: 24 hours of the current total rate. */
public int gemFarmCap() {
    return gemFarmRatePerHour() * 24;
}

/**
 * Claim gems accumulated since last call. Call from EngineeringLabScreen.show().
 * Initialises the timestamp on first call once farming is active.
 * Returns the number of whole gems awarded (0 if farming not yet active).
 */
public int claimGemFarming() {
    if (arrivalsCompleted < 1) return 0;
    long now = System.currentTimeMillis();
    if (lastGemFarmTimestamp == 0L) {
        lastGemFarmTimestamp = now;
        return 0;
    }
    float elapsedHrs = (now - lastGemFarmTimestamp) / 3_600_000f;
    int cap    = gemFarmCap();
    int earned = (int) Math.min(gemFarmRatePerHour() * elapsedHrs, cap);
    if (earned > 0) {
        addDiamonds(earned);
        lastGemFarmTimestamp = now;
    }
    return earned;
}
```

- [ ] **Step 3: Build**

```
./gradlew android:assembleDebug 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

---

## Task 2: ShipData — wire save / load / reset for the new field

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

- [ ] **Step 1: Add to save()**

In `save()`, find the line `p.putLong("lastFarmingTimestamp", lastFarmingTimestamp);` (around line 301) and add the new key directly after it:

```java
p.putLong("lastGemFarmTimestamp", lastGemFarmTimestamp);
```

- [ ] **Step 2: Add to load()**

In the load block, find `lastFarmingTimestamp = p.getLong("lastFarmingTimestamp", 0L);` (around line 371) and add directly after:

```java
lastGemFarmTimestamp = p.getLong("lastGemFarmTimestamp", 0L);
```

- [ ] **Step 3: Add to reset()**

In `reset()`, find `lastFarmingTimestamp = 0L;` (around line 176) and add directly after:

```java
lastGemFarmTimestamp = 0L;
```

- [ ] **Step 4: Build**

```
./gradlew android:assembleDebug 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

---

## Task 3: ShipData — remove old farming fields and methods

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

- [ ] **Step 1: Remove old fields and constant**

Delete these three lines (around lines 130–134):

```java
public int[]  internsLeftOnPlanet  = new int[PLANETS.length];
public long   lastFarmingTimestamp = 0L;
```
and
```java
public static final float FARM_RATE_PER_INTERN = 2f; // SP/s per deployed intern
```

- [ ] **Step 2: Remove old reset lines**

In `reset()`, delete:
```java
lastFarmingTimestamp    = 0L;
```
and the intern loop:
```java
for (int i = 0; i < internsLeftOnPlanet.length; i++) internsLeftOnPlanet[i] = 0;
```

- [ ] **Step 3: Remove claimOfflineFarming() method**

Delete the entire method (around lines 257–271):
```java
/** Returns SP earned offline since last claim, then resets the timestamp. */
public float claimOfflineFarming() {
    if (lastFarmingTimestamp == 0L) return 0f;
    long now = System.currentTimeMillis();
    float elapsed = (now - lastFarmingTimestamp) / 1000f;
    lastFarmingTimestamp = now;
    float total = 0f;
    for (int i = 0; i < PLANETS.length; i++) {
        if (internsLeftOnPlanet[i] <= 0) continue;
        float rate = internsLeftOnPlanet[i] * FARM_RATE_PER_INTERN;
        float cap  = PLANETS[i].maxFarmingStorage;
        total = Math.min(total + rate * elapsed, total + cap);
    }
    return total;
}
```

- [ ] **Step 4: Remove old save/load/reset lines for internsLeftOnPlanet and lastFarmingTimestamp**

In `save()`, delete:
```java
p.putLong("lastFarmingTimestamp",   lastFarmingTimestamp);
```
and:
```java
for (int i = 0; i < PLANETS.length; i++)
    p.putInteger("internsLeft_" + i, internsLeftOnPlanet[i]);
```

In `load()`, delete:
```java
lastFarmingTimestamp    = p.getLong("lastFarmingTimestamp",   0L);
```
and:
```java
for (int i = 0; i < PLANETS.length; i++)
    internsLeftOnPlanet[i] = p.getInteger("internsLeft_" + i, 0);
```

- [ ] **Step 5: Remove stale internsLeftOnPlanet reset in claimArrivalReward()**

In `claimArrivalReward()` (around line 472), delete:
```java
internsLeftOnPlanet[currentPlanetIndex] = 0;  // clear stale intern count for this planet
```

- [ ] **Step 6: Build — expect errors in EngineeringLabScreen and MainMenuScreen**

```
./gradlew android:assembleDebug 2>&1 | grep "error:"
```
Expected: compile errors referencing `internsLeftOnPlanet`, `lastFarmingTimestamp`, `FARM_RATE_PER_INTERN`, `claimOfflineFarming` in the screen files — these will be fixed in subsequent tasks.

---

## Task 4: EngineeringLabScreen — replace farming claim, remove harvest modal

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

- [ ] **Step 1: Replace claimOfflineFarming() method body**

Find the method `claimOfflineFarming()` (around line 3639):
```java
private void claimOfflineFarming() {
    float earned = ShipData.get().claimOfflineFarming();
    if (earned > 0f) {
        ShipData.get().addCrystals(earned);
        showNotif("FARMING INCOME", "+" + (int)earned + " SP from deployed interns");
    }
}
```

Replace it with:
```java
private void claimGemFarming() {
    int earned = ShipData.get().claimGemFarming();
    if (earned > 0) {
        showNotif("GEM FARMS", "+" + earned + " gems from planetary farms");
    }
}
```

- [ ] **Step 2: Update the call site in show()**

In `show()` (around line 3469), replace:
```java
claimOfflineFarming();
claimPendingRecruits();
checkOfflineHarvestProgress();
```
with:
```java
claimGemFarming();
claimPendingRecruits();
```

- [ ] **Step 3: Remove checkOfflineHarvestProgress() and showHarvestModal()**

Delete both methods in their entirety (around lines 7290–7375):

```java
private void checkOfflineHarvestProgress() { ... }
private void showHarvestModal(final float rawYield, float elapsedSec) { ... }
```

- [ ] **Step 4: Remove internsLeftOnPlanet and lastFarmingTimestamp assignments at launch**

There are two launch spots. Find and delete these two lines in each:
```java
sd2.internsLeftOnPlanet[sd2.currentPlanetIndex] = balls.size;
sd2.lastFarmingTimestamp = System.currentTimeMillis();
```
(around lines 2635–2636 and 3103–3104)

- [ ] **Step 5: Build**

```
./gradlew android:assembleDebug 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL` (or only MainMenuScreen errors remaining)

---

## Task 5: MainMenuScreen — replace farming dots and SP rate label with gem rate label

**Files:**
- Modify: `src/com/odyssey/screen/MainMenuScreen.java`

- [ ] **Step 1: Remove drawFarmingDots() method and its call**

Delete the entire `drawFarmingDots()` method (around lines 397–418):
```java
private void drawFarmingDots() {
    ...
}
```

In `render()`, delete the call to it (around line 293):
```java
drawFarmingDots();
```

- [ ] **Step 2: Replace the SP rate label block with a gem rate label**

In `drawLabels()`, find the intern rate block (around lines 855–863):
```java
int internCount = sd.internsLeftOnPlanet[i];
if (internCount > 0) {
    int ratePerHr = (int)(internCount * ShipData.FARM_RATE_PER_INTERN * 3600f);
    String rateStr = ratePerHr >= 1000
        ? String.format("+%d,%03d/HR", ratePerHr / 1000, ratePerHr % 1000)
        : String.format("+%d/HR", ratePerHr);
    smallFont.setColor(0.18f, 1f, 0.52f, 0.88f);
    smallFont.draw(batch, rateStr, cx - 64f, cy + r + 28f, 128f, Align.center, false);
}
```

Replace with:
```java
// Show gem farm rate for each planet once farming is active (arrivalsCompleted >= 1)
if (ShipData.get().arrivalsCompleted >= 1 && i < ShipData.GEM_FARM_RATES.length && i <= currentIdx) {
    int rate = ShipData.GEM_FARM_RATES[i];
    String rateStr = "+" + rate + " gem/hr";
    smallFont.getData().setScale(0.85f);
    smallFont.setColor(1.00f, 0.82f, 0.20f, 0.88f);
    smallFont.draw(batch, rateStr, cx - 64f, cy + r + 28f, 128f, Align.center, false);
    smallFont.getData().setScale(1.00f);
}
```

- [ ] **Step 3: Build and install**

```
./gradlew android:assembleDebug 2>&1 | grep -E "error:|BUILD"
~/Library/Android/sdk/platform-tools/adb install -r android/build/outputs/apk/debug/android-debug.apk
```
Expected: `BUILD SUCCESSFUL` then `Success`

---

## Task 6: Smoke test on device

- [ ] **Step 1: Launch app, open Engineering Lab** — verify no harvest modal appears, no crash
- [ ] **Step 2: Check Main Menu** — verify no orbiting dots; if `arrivalsCompleted >= 1`, amber `+N gem/hr` labels appear under unlocked planet names
- [ ] **Step 3: Check gem counter** — verify gems tick up correctly when re-entering lab after time away (test by temporarily setting `lastGemFarmTimestamp` to `now - 3_600_000` in a debug build, or just wait a minute and confirm at least 1 gem if on Solara)
- [ ] **Step 4: Commit**

```bash
git add src/com/odyssey/ShipData.java \
        src/com/odyssey/screen/EngineeringLabScreen.java \
        src/com/odyssey/screen/MainMenuScreen.java \
        docs/superpowers/specs/2026-05-25-gem-farming-design.md \
        docs/superpowers/plans/2026-05-25-gem-farming.md
git commit -m "feat: replace SP offline farming with passive gem farming per planet"
```
