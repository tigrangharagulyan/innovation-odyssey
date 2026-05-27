# Gem Farming — Design Spec
**Date:** 2026-05-25

## Overview

Replace the existing SP/crystal offline farming system with a passive gem farming system. Each planet the player has landed on earns diamonds (gems) continuously at a fixed rate. Farming activates after the first arrival (Nova Terra landing) and accumulates offline up to a 24-hour cap.

---

## Farming Rates

| Planet         | Index | Gem Rate  | Cumulative Total |
|----------------|-------|-----------|-----------------|
| Solara         | 0     | 1 gem/hr  | 1 gem/hr        |
| Nova Terra     | 1     | 2 gems/hr | 3 gems/hr       |
| Frostheim      | 2     | 3 gems/hr | 6 gems/hr       |
| Cryon Reach    | 3     | 4 gems/hr | 10 gems/hr      |
| Helios Forge   | 4     | 5 gems/hr | 15 gems/hr      |

Rates are fixed — intern count has no effect. 12 interns are narratively auto-stationed per planet on arrival; no player action required.

---

## Activation

- Farming does **not** start until `arrivalsCompleted >= 1` (first Nova Terra landing).
- Once active, all unlocked planets (index 0 through `currentPlanetIndex`) contribute simultaneously.
- "Unlocked" means the player has arrived at that planet (`arrivalsCompleted > planetIndex`, i.e. planet index < `currentPlanetIndex + 1`). Solara (index 0) is always unlocked once farming starts.

---

## Storage Cap

- Cap = `currentTotalRatePerHour * 24` gems.
- Example: player is on Frostheim (rate = 6/hr) → cap = 144 gems.
- Cap updates as the player reaches new planets.

---

## Claim Flow

1. Player opens the Engineering Lab (enters `EngineeringLabScreen.show()`).
2. `claimGemFarming()` is called on `ShipData`:
   - Compute elapsed seconds since `lastGemFarmTimestamp`.
   - Compute `earned = floor(totalRatePerSecond * elapsed)`.
   - Clamp `earned` to the 24-hour cap.
   - Add earned amount to `diamonds` via `addDiamonds(earned)`.
   - Update `lastGemFarmTimestamp = System.currentTimeMillis()`.
3. If `earned > 0`, show a HUD notification: `"+N GEMS from planetary farms"`.
4. No choice modal — gems land in the wallet automatically.

---

## ShipData Changes

### Fields to add
```java
public long lastGemFarmTimestamp;   // ms epoch; 0 = not yet started
```

### Constants to add
```java
public static final int[] GEM_FARM_RATE = {1, 2, 3, 4, 5}; // gems/hr per planet index
```

### Methods to add
```java
/** Total gem/hr from all unlocked planets. Returns 0 if farming not yet active. */
public int gemFarmRatePerHour()

/** Gems currently banked per 24-hr cap. */
public int gemFarmCap()

/** Claim accumulated gems; call on EngineeringLabScreen.show(). Returns gems awarded. */
public int claimGemFarming()
```

### Fields to remove
```java
public int[]  internsLeftOnPlanet;   // remove
public long   lastFarmingTimestamp;  // remove
```

### Methods to remove
```java
claimOfflineFarming()          // remove
checkOfflineHarvestProgress()  // remove (harvest modal)
```

### Save/Load
- Remove keys: `"internsLeftOnPlanet_0"` … `"internsLeftOnPlanet_4"`, `"lastFarmingTimestamp"`
- Add key: `"lastGemFarmTimestamp"` (Long, default 0)

---

## EngineeringLabScreen Changes

- Remove `checkOfflineHarvestProgress()` call and method.
- Remove harvest modal (`decisionTable` if it is exclusively used for this — check first).
- In `show()`, call `claimGemFarming()` and show notification if earned > 0.
- Remove any HUD or UI that shows intern-on-planet count.

---

## MainMenuScreen Changes

### Remove
- `drawFarmingDots()` method and its call in `render()`.
- `+N/HR` SP label drawn per planet in `drawLabels()`.

### Add
- Under each unlocked planet name, draw a small amber gem rate label: `+N 💎/hr` where N = `GEM_FARM_RATE[i]`.
- Locked / not-yet-visited planets show nothing.
- The label for the current planet (latest arrival) shows the incremental rate for that planet, not the cumulative total — cumulative total shown in the top stats panel as a summary line (optional, low priority).

---

## What Is NOT Changed

- In-lab SP/energy generation from physics collisions — unchanged.
- `diamonds` field and `addDiamonds()` — already exist, just gets new input source.
- Lives system — unchanged.
- All other `ShipData` fields unrelated to farming.

---

## Out of Scope (future)

- Intern count scaling farm rate.
- Planet-specific gem type or bonus resources.
- Farm boost tokens from active lab play.
