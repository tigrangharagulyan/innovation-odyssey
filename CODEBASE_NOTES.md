# Innovation Odyssey — Deep Codebase Notes

> Last updated: May 2026. These notes are for AI/dev context, not end-user docs.

---

## What This Game Is

**Innovation Odyssey** is a LibGDX 1.12.1 + Box2D idle physics game. The core loop:

1. **Engineering Lab (physics sandbox)** — player places bouncy interns (orbs) and bumpers inside a spinning centrifuge drum. Collisions generate *Crystals (Space Points / ❅/◆)* and *Joules (Energy/J)*
2. **Bridge Flight (passive idle)** — snapshot the lab's JPS, launch toward a planet. Energy generated powers the rocket toward checkpoints.
3. **Galactic Map** — choose next destination planet (5 total), each with different gravity
4. **Planet Arrival** — prestige reward, multiplier boosts, new Lab unlocks

---

## File Map

```
src/com/odyssey/
├── OdysseyGame.java          - Game entry, screen routing, skin, fade transition
├── GameState.java            - Enum: MAIN_MENU, ENGINEERING_LAB, BRIDGE_FLIGHT,
│                               INTERN_DEPLOY, GALACTIC_MAP, NOVA_TERRA_ARRIVAL
├── ShipData.java             - Singleton global state + persistence (LibGDX Preferences)
├── OdysseyTheme.java         - Color palette constants
├── SoundManager.java         - Singleton sound player (hire/collision/bumper/milestone)
├── DesktopLauncher.java      - Desktop entry (LWJGL3)
├── TeaVMLauncher.java        - Web entry (excluded from desktop/android builds)
│
├── physics/
│   └── EnergyContactListener.java  - Box2D ContactListener, awards crystals/joules on hit
│
└── screen/
    ├── MainMenuScreen.java         - Galactic map overview + rocket tap to enter Lab
    ├── EngineeringLabScreen.java   - THE main screen (physics, UI, upgrades, perks)
    ├── BridgeFlightScreen.java     - Animated rocket flight to checkpoint/planet
    ├── GalacticMapScreen.java      - Planet selection UI (scene2d cards)
    ├── InternDeployScreen.java     - Post-arrival animation: interns deployed, recruits join
    └── NovaTerraArrivalScreen.java - Arrival reward claim + gravity toggle
```

---

## OdysseyGame.java — Entry Point

- Extends LibGDX `Game`
- `buildSkin()` constructs ONE shared `Skin` used by all screens:
  - Font: Exo2.ttf at sizes 13, 14, 17, 24, 38
  - NinePatches from `ui/` folder: card_large, card_medium, button_primary/secondary/disabled, badge_panel
  - Sci-fi tile button styles: `tile_locked/available/buyable/active/go/golocked` + `_dn` variants
  - Button colors driven by `setColor()` tinting, not separate drawables
- Transition: `transitionTo(GameState)` — 0.28s fade to black, switches screen, fades back in
- Screens are lazy singletons; `EngineeringLabScreen` can be disposed/rebuilt via `forceRebuildLab()` or `resetLabScreen()`
- `pause()` / `dispose()` → calls `labScreen.snapshotState()` then `ShipData.get().save()`

**Screen routing:**
```
MAIN_MENU → ENGINEERING_LAB ↔ BRIDGE_FLIGHT → INTERN_DEPLOY → ENGINEERING_LAB
                                            → NOVA_TERRA_ARRIVAL → ENGINEERING_LAB
ENGINEERING_LAB → GALACTIC_MAP → ENGINEERING_LAB
```

---

## ShipData.java — Global State Singleton

`ShipData.get()` is the single source of truth for ALL game state.

### Key Economy Fields
| Field | Meaning |
|-------|---------|
| `totalJoules` | Current energy (spendable, consumed by upgrades) |
| `crystals` | Space Points / ❅ / ◆ (second currency from collisions) |
| `currentJPS` | Live joules-per-second readout |
| `savedFlightJPS` | Snapshot JPS locked when flight launched |
| `powerGenerated` | Total cumulative energy ever produced (used for flight progress) |
| `accumulatedDist` | Progress along current route (energy units) |
| `sectorReached` | Last checkpoint index reached (-1 = none, 0/1/2 = CP I/II/III) |
| `bumperEnergyMult` | Multiplier on bumper spark value |
| `collisionEnergyMult` | Multiplier on intern-intern collision sparks |
| `wallEnergyMult` | Multiplier on wall-hit sparks (≥2 also adds 8J/hit) |
| `internBoostStrength` | Impulse strength on intern-intern contact |
| `planetGravityMultiplier` | Applied to Box2D world gravity |

### 5 Planets (PLANETS[])
| Index | Name | Distance | Gravity | Atmosphere |
|-------|------|----------|---------|------------|
| 0 | Solara | 1000f | 1.0G | Stable |
| 1 | Nova Terra / Ember IV | 2500f | 1.6G | Volcanic |
| 2 | Frostheim | 5000f | 0.4G | Frozen |
| 3 | Cryon Reach | 7600f | 0.7G | Frozen |
| 4 | Helios Forge | 12000f | 2.2G | Volatile |

### Persistence
- Saved to LibGDX `Preferences("odyssey_save")`
- `save()` / `load()` on every `pause()` / `dispose()`
- Float arrays stored as comma-separated strings: `savedBumpers`, `savedAttractors`, `savedIcicleNodes`, `savedTeslaCoils`, `savedSpringPads`, `savedPortalPairs`, `savedRelayNodes`
- Milestone flags: `savedMilestoneAchieved[6]`, plus planet-specific CP flags

### Arrival reward
`claimArrivalReward()` gives: +0.5 bumperEnergyMult, +0.25 collisionEnergyMult, +0.15 wallEnergyMult, +0.15 internBoostStrength, and bonus joules (150 + arrivals×50)

### Offline Farming
`internsLeftOnPlanet[i]` — interns deployed on each planet
Rate: `FARM_RATE_PER_INTERN = 2f` SP/s/intern
`claimOfflineFarming()` calculates elapsed time since `lastFarmingTimestamp`, caps by `PLANETS[i].maxFarmingStorage`

---

## EngineeringLabScreen.java — Main Physics Screen

This is the largest file (~3000+ lines). Key architecture:

### Physics Constants
```java
PPM = 60f          // pixels per meter
WORLD_W = 8.0f     // 480/60
WORLD_H = 14.23f   // 854/60
GRAVITY = -4.5f    // base gravity (multiplied by planetGravityMultiplier)
CENTRIFUGE_CX = 4.0f   // drum center X (world)
CENTRIFUGE_CY = 8.5f   // drum center Y (world)
CENTRIFUGE_R = 3.2f    // drum radius
CENTRIFUGE_SEGS = 36   // polygon segments
CENTRIFUGE_RPM_BASE = 1.5f
CENTRIFUGE_RPM_MAX = 10.5f
CENTRIFUGE_RPM_ACCEL = 0.18f
```

### Body Types
| Type | Radius | Density | Restitution | UserData |
|------|--------|---------|-------------|---------|
| Intern (standard) | 0.25f | 1.0f | 0.90f | "INTERN_NORMAL" or "INTERN_\*" |
| Intern (Ember/cyber) | 0.38f | 2.5f | 0.90f | starts with "INTERN" |
| Bumper | 0.20f | static | 1.40f | `BumperHitData` instance |
| Gravity Well / Attractor | varies | static/kinematic | — | `AttractorHitData` |
| Icicle Node (Frostheim) | 0.20f | sensor | — | "ICICLE" |
| Tesla Coil (Frostheim) | varies | static | — | fixture: "TESLA_COIL_CORE" |
| Spring Pad (Ember IV) | — | static | 2.25f | fixture: "SPRING_PAD" |
| Kinetic Blade (Ember IV) | rect | static | — | "KINETIC_BLADE" |
| Snow Pellet (Frostheim) | 0.13f | dynamic | — | CAT_PELLET filter |
| Arm Bumper (Frostheim) | — | static | — | CAT_ARM_BUMPER filter |

### Collision Filter Bits
```java
CAT_DEFAULT    = 0x0001  // everything normal
CAT_PELLET     = 0x0002  // snow pellets
CAT_ARM_BUMPER = 0x0004  // Frostheim arm-tip bumpers
MASK_PELLET     skips arm bumpers
MASK_ARM_BUMPER skips pellets
```

### Placement Modes (PLACE_* constants)
```java
PLACE_NONE = 0, PLACE_BUMPER = 1, PLACE_GRAVITY = 2, PLACE_BLADE = 3,
PLACE_INTERN = 5, PLACE_RELAY = 6, PLACE_PORTAL = 7
```

### Caps
```java
MAX_BODIES  = 30   // total physics bodies
MAX_INTERNS = 12   // intern orbs
MAX_BUMPERS = 5    // standard bumpers
MAX_GRAVITY_WELLS = 4
MAX_RELAY_NODES = 4
MAX_PORTAL_PAIRS = 3
MAX_SPRING_PADS = 8
```

### Cost Tables (Crystals ❅/◆)
**Interns (Solara):** [80, 500, 1200, 3000, 7500, 30000, 40000, 50000, 150000, 200000]
**Interns (Ember IV):** [1500, 2400, 5000, 8000, 15000, 30000, 55000, 180000, 375000, 420000]
**Interns (Frostheim):** [240, 1500, 3600, 9000, 22500, 90000, 120000, 150000, 450000, 600000]
**Bumpers:** [500, 2500, 25000, 30000, 100000] (crystals)
**Gravity Wells:** [3000, 15000, 150000] (crystals)
**Icicle Nodes:** [2400, 6000, 24000, 60000, 450000]
**Tesla Coils:** [15000, 36000, 105000, 900000]
**Spring Pads (Ember IV):** [300, 600, 1200, 2400, 4800, 9600, 19200, 38400]
**Kinetic Blades:** [1500, 12000, 30000]
**Ember Gravity Wells:** [5000, 12000, 30000]
**Hub Upgrades:** [5000, 15000, 50000]

### Checkpoints (Energy thresholds by planet)
**Solara:** [2000, 10000, 60000, 200000]
**Ember IV:** [5000, 60000, 300000, 650000]
**Frostheim:** [4000, 24000, 120000, 150000]

### Ring-Speed Milestones (Solara)
```java
MILESTONE_RPMS  = {5.25f, 3.5f, 6.75f, 7.5f, 9.5f, 99f}
MILESTONE_NAMES = {"Elastic Walls", "Speed Keep", "Wall ×3", "Hit ×2", "Bumper ×3", "-"}
// Index 0 (Elastic Walls) only unlocked at CP I, not by ring speed
```

### Planet-Level Flags (what changes per planet)
- **Solara (idx=0):** Standard centrifuge circle, standard interns, bumpers, gravity wells, milestones
- **Ember IV (idx=1):** Square drum (RECT_HW), Kinetic Blades, Spring Pads, Resonance Relays, Phase Portals, 2-state Cybernetic Hub, reversed spin perk, gravity shift perk. Perks: Speed Keep, Wall Energy, Gravity Shift, Portal Sync, Reverse Field
- **Frostheim (idx=2):** Snowflake drum shape (216-segment), Icicle Nodes (orb-splitters), Tesla Coils (spiral slingshots), Snow Pellets, Arm Bumpers (3rd intern perk), hub gravity pull/push cycle, CP III decision (cryo→tesla, tesla→cryo, or +2 interns)

### Rendering Pipeline (per frame)
1. Clear + glEnable(BLEND)
2. Draw background texture (genBackground — procedurally generated per planet, cached)
3. Draw centrifuge ring texture
4. Draw gravity field auras (attractors)
5. Draw portals, relay nodes (Ember IV)
6. Draw bumpers (BumperHitData flash on hit)
7. Draw interns (animated astronaut sprites)
8. Draw blades, spring pads, icicle nodes, tesla coils
9. Draw floating numbers (damage floaters from EnergyContactListener queue)
10. Draw perk popup if active
11. Draw scene2d UI stage (buttons, labels, panels)

### Intern Animations
3-frame astronaut sprite procedurally generated via `genAstronautFrame()` (big helmet + visor + arms + legs), colored per planet: blue=Solara, orange=Ember IV, cyan=Frostheim, etc.

### Screen Shake
Triggered by large collision events. `shakeTimer`, `shakeDuration=0.05f`, `shakeMag=4f`

### JPS Calculation
Polled every second (`jpsTimer`): `currentJPS = (totalJoules - lastJoules) / elapsed`

### Snapshot / Restore
- `snapshotState()` — writes current intern/bumper/attractor positions into ShipData flat arrays
- `restoreState()` — spawns them back from ShipData (called in `show()`)

---

## EnergyContactListener.java — Collision Awards

Called by Box2D on every `beginContact`. Awards:

| Collision | Award | Notes |
|-----------|-------|-------|
| Intern vs Intern | `20 × collisionEnergyMult` crystals | + mutual separation impulse (internBoostStrength) |
| Intern vs Bumper | `bumperSparkValue × bumperMult` crystals | BumperHitData timestamp updated for flash |
| Intern vs Attractor/Tesla | `50 × gravityMult` crystals | AttractorHitData timestamp |
| Intern vs Wall | `1 × wallEnergyMult` crystals | if wallEnergyMult ≥ 2f, also +8J |
| Intern vs Kinetic Blade | +35J | Ember IV only |
| Intern vs Spring Pad | +25 crystals | Ember IV only |
| Icicle contacts | Skipped here | Handled in stepPhysics() |

Queues floating numbers via `sd.pendingContactEvents` (consumed in render loop).
Pauses all earnings when `sd.placingStructure == true`.

---

## BridgeFlightScreen.java — Flight Animation

- Renders star parallax, rocket animation, route line with checkpoint nodes
- `resetFlight()` called each time we enter:
  - Reads `sd.powerGenerated - sd.energyAtLastLaunch` as new energy this run
  - Advances `sd.accumulatedDist` by that amount (capped at next checkpoint)
  - Sets `rocketTargetX` proportionally
- Checkpoints energies per planet:
  - Solara: [2000, 10000, 60000, 200000]
  - Ember IV: [5000, 60000, 300000, 250000]
  - Frostheim+: [6000, 24000, 120000, 150000]
- On finish (button tap):
  - If arrived: `sd.markArrival()` + `claimArrivalReward()` → `INTERN_DEPLOY`
  - Else: reset `totalJoules = 0`, return to `ENGINEERING_LAB`

---

## InternDeployScreen.java — Post-Arrival Animation

- Shows previous planet → new planet with rocket flying between
- Animates deployed interns orbiting old planet, recruits flying up from new planet
- Sequence: interns deploy → rocket flies → 2 recruits join crew → tap to continue
- `applyFarming()`: sets `sd.pendingNewRecruits += 2` and starts `lastFarmingTimestamp`
- On tap: `game.resetLabScreen()` → `ENGINEERING_LAB`

---

## MainMenuScreen.java — Galaxy Map Overview

- Rendered with `ShapeRenderer` + `SpriteBatch` (no Scene2D)
- 5 planet nodes (+ 2 locked) in zigzag layout
- Rocket sits on current planet, advances along path by sectorReached
- Tap rocket → Engineering Lab
- Tap arrival-ready planet → Nova Terra Arrival screen
- "NEW GAME" button (bottom-right) → `ShipData.get().reset()`
- Displays: crystal count, SP/HR income from farming, energy total, arrivals count

---

## NovaTerraArrivalScreen.java

- Shows arrived planet name, gravity bar, toggle gravity on/off
- `sd.gravityEnabled` persists — if disabled, Lab uses 0 gravity
- "Claim Prestige Reward" → `sd.claimArrivalReward()` + rebuild lab

---

## GalacticMapScreen.java

- Scene2D cards with planet list (toggle buttons)
- "Set Jump" → `sd.commitSelectedPlanet()` + `resetLabScreen()` → `ENGINEERING_LAB`
- Shows planet distance (light-years), gravity, atmosphere, reward, buildings

---

## OdysseyTheme.java — Color Constants

```java
SPACE_BG     = #080810  (very dark navy)
PANEL_BG     = #0B0D1A
ACCENT_E     = #2255CC  (blue — energy)
ACCENT_SP    = #CC9900  (gold — space points)
ACCENT_GO    = #00FF44  (green — go/buy)
ACCENT_WARN  = #FF6B00  (orange — warning)
TEXT_PRI     = #C8D8F0  (light blue-white)
TEXT_DIM     = #3A4A6A

// Float overlay colors:
FLOAT_SP     = gold    (space points hits)
FLOAT_E      = blue    (energy hits)
FLOAT_SPECIAL= teal    (gravity/special)
FLOAT_BUMPER = violet  (bumper hits)
```

---

## SoundManager.java

Singleton. Sounds: hire.wav, collision.wav, bumper.wav, milestone.wav, checkpoint.wav, launch.wav
Collision throttled to max 1 per 200ms to prevent audio spam.

---

## Assets Structure

```
assets/
├── fonts/Exo2.ttf
├── ui/
│   ├── card_large.png, card_medium.png
│   ├── button_primary.png, button_secondary.png, button_disabled.png
│   ├── badge_panel.png
│   ├── btn_locked/available/buyable/active/go/golocked.png  (sci-fi tile buttons)
├── sounds/
│   ├── hire.wav, collision.wav, bumper.wav, milestone.wav, checkpoint.wav, launch.wav
├── backgrounds/
│   ├── map_bg.png, arrival_bg.png
├── engineering/
│   └── (planet-themed textures, if any)
```

Most textures in EngineeringLabScreen are **procedurally generated** at runtime via Pixmap — background, ring, intern sprites, bumpers, grav fields, blade textures, perk icons, etc.

---

## Key Patterns & Gotchas

### PPM Coordinate System
- Physics world: meters (e.g., centrifuge center at 4.0, 8.5)
- Screen pixels: multiply by PPM=60 (center at 240px, 510px)
- Always multiply world coords × PPM for drawing

### Centrifuge Shapes Per Planet
- Solara/Nova Terra(Ember): **Circle** (36-segment ChainShape)
- Ember IV: **Square/Rect** (`RECT_HW` constant, not a circle)
- Frostheim: **Snowflake** (216-segment, alternating arm/valley radii)

### Lab Rebuild
When planet changes (from Galactic Map "Set Jump"), `resetLabScreen()` disposes and nulls `labScreen`. Next visit creates fresh instance. `snapshotState()` is called before to preserve intern/bumper positions.

### Dual Currency
- **Joules (J)** — Energy. Generated by wall hits (with perk) and kinetic blade hits. Spent on planet travel. Shown as blue.
- **Crystals (❅/◆/SP)** — Space Points. Generated by all collisions. Spent to buy interns, bumpers, upgrades. Shown as gold.
- The naming is a bit inconsistent in code (`addCrystals`, `addJoules`, `sd.crystals`, `sd.totalJoules`).

### Sound Dispatch
Sounds queued via `sd.pendingBumperSounds` / `sd.pendingCollisionSounds` in EnergyContactListener (Box2D thread-safe), dispatched from render thread in EngineeringLabScreen.

### Tutorial
`tutorialStep` 0-3: 0=intro overlay, 1=hire-intern callout, 2=launch callout, 3=done
Hints: launchHintShown, bumperHintShown, gravityHintShown, perkIconHintShown

### Farming System
- Interns deployed on a planet stay there permanently earning crystals offline
- Rate: 2 SP/s per intern, capped by planet's `maxFarmingStorage`
- Claimed lazily via `claimOfflineFarming()` next time app opens

---

## Build Commands

```bash
./gradlew run          # Desktop run (macOS: handles -XstartOnFirstThread)
./gradlew build        # Build desktop JAR
./gradlew android:assembleDebug   # Android APK
./gradlew android:copyAndroidNatives  # Copy .so files
```

No test suite. Verify by running desktop launcher.
