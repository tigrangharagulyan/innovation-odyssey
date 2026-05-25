# Leaderboard Design — Innovation Odyssey
**Date:** 2026-05-25

## Overview

Add a per-planet fake leaderboard to Innovation Odyssey that ranks players by real-world time to complete each planet's flight. No backend or networking is required. Fake static rivals provide a competitive target. The leaderboard is accessible from the main menu and appears as a popup after each planet arrival.

---

## Goals

- Add competitive motivation without any server infrastructure.
- Track the player's personal best arrival time (real-world seconds) per planet.
- Show the player their rank among 20 static fake rivals per planet.
- Surface the leaderboard in two places: post-arrival popup and a dedicated screen.

---

## Data Layer

### ShipData changes

| Field | Type | Default | Persisted | Purpose |
|---|---|---|---|---|
| `flightStartTimeMs` | `long` | `0` | No (transient) | Epoch ms when current flight began |
| `bestArrivalTimes` | `float[5]` | `Float.MAX_VALUE` each | Yes (`odyssey_save`) | Player's best real-world arrival time (seconds) per planet index |

**Timer lifecycle:**
1. `BridgeFlightScreen.show()` sets `ShipData.get().flightStartTimeMs = System.currentTimeMillis()`.
2. `NovaTerraArrivalScreen.show()` computes `elapsed = (System.currentTimeMillis() - flightStartTimeMs) / 1000f`.
3. If `elapsed < bestArrivalTimes[currentPlanetIndex]`, update and persist.
4. Reset `flightStartTimeMs = 0` after recording.

### FakeLeaderboard class

- Location: `src/com/odyssey/FakeLeaderboard.java`
- Contains a static `Rival` inner record: `String name, float timeSeconds`.
- Contains `Map<Integer, List<Rival>> RIVALS` — 20 rivals per planet index (0–4).
- Time distribution per planet:
  - Top 5 rivals: very fast (hard to beat on first attempts).
  - Middle 10: moderate (beatable with optimization).
  - Bottom 5: slow (beatable on early runs).
  - Times scale with planet distance — Helios Forge rivals have longer absolute times.
- `public static int getRank(int planetIndex, float playerTimeSeconds)`: merges player into the sorted list and returns 1-based rank (lower time = better rank).
- `public static List<Entry> getBoard(int planetIndex, float playerTimeSeconds)`: returns sorted list of `Entry` (name, time, isPlayer) for display, including the player row.

---

## Screens & UI

### GameState enum

Add `LEADERBOARD` to `GameState`.

### OdysseyGame

- Add `leaderboardScreen` lazy singleton (same pattern as existing screens).
- Route `LEADERBOARD` in `transitionTo()`.

### LeaderboardScreen (new)

- 5 planet tabs at the top. Tab labels: planet names from `ShipData.PLANETS[]`.
- Tabs for planets the player has not yet visited are grayed out and non-interactive. "Visited" means `bestArrivalTimes[i] < Float.MAX_VALUE` (i.e., the player has completed at least one arrival at that planet). Planet 0 (Solara) tab is always active so players can see what they're working toward.
- Active tab: highlighted with `OdysseyTheme` accent color.
- Table body (scrollable `ScrollPane`):
  - Columns: Rank | Name | Time (mm:ss format)
  - Player row: distinct highlight color (e.g. `OdysseyTheme.GOLD` or similar).
  - If player has no time for the selected planet: player row shows `-- : --` at the bottom.
- Back button: returns to `MAIN_MENU`.

### MainMenuScreen

- Add a "LEADERBOARD" button using existing `OdysseyTheme.BTN_*` button style.
- Triggers `transitionTo(GameState.LEADERBOARD)`.

### NovaTerraArrivalScreen — post-arrival popup

- After the existing reward content renders, display a panel overlay:
  - Title: "Planet Leaderboard — [Planet Name]"
  - Shows ranks #(playerRank-2) through #(playerRank+2), clamped to board bounds — player's row highlighted.
  - Two buttons: "View Full Board" (→ `LEADERBOARD` screen) and "Continue" (dismisses overlay, existing flow resumes).
- Popup appears only when player has a valid recorded time (not if `flightStartTimeMs` was 0).

---

## Time Display Format

Times are stored as `float` seconds. Display as `mm:ss` (e.g. 125.4s → "2:05"). No millisecond precision shown.

---

## Persistence

`bestArrivalTimes` is stored in `odyssey_save` as five separate preference keys:
- `bestArrivalTime_0` through `bestArrivalTime_4`
- Loaded in `ShipData.load()`, saved in `ShipData.save()`.
- Default value: `Float.MAX_VALUE` (displayed as `-- : --`).

---

## Files Changed / Created

| File | Change |
|---|---|
| `src/com/odyssey/ShipData.java` | Add `flightStartTimeMs`, `bestArrivalTimes[]`, load/save logic |
| `src/com/odyssey/GameState.java` | Add `LEADERBOARD` |
| `src/com/odyssey/OdysseyGame.java` | Add `leaderboardScreen` singleton + routing |
| `src/com/odyssey/FakeLeaderboard.java` | New — rivals data + rank/board methods |
| `src/com/odyssey/screen/LeaderboardScreen.java` | New — full leaderboard screen |
| `src/com/odyssey/screen/MainMenuScreen.java` | Add leaderboard button |
| `src/com/odyssey/screen/BridgeFlightScreen.java` | Set `flightStartTimeMs` on `show()` |
| `src/com/odyssey/screen/NovaTerraArrivalScreen.java` | Record best time + show popup |

---

## Out of Scope

- Real online backend or player accounts.
- Rivals that change over time.
- Cheating prevention / time validation.
- Leaderboard for JPS, crystals, or other metrics.
