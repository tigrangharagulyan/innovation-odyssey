# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run desktop (macOS requires -XstartOnFirstThread, gradle handles this automatically)
./gradlew run

# Build desktop JAR
./gradlew build

# Build Android APK
./gradlew android:assembleDebug

# Copy Android native .so files before packaging
./gradlew android:copyAndroidNatives
```

No test suite exists — verify changes by running the desktop launcher.

## Architecture

**Innovation Odyssey** is a LibGDX 1.12.1 + Box2D idle physics game targeting desktop (LWJGL3) and Android. Source lives in `src/` (shared) and `android/` (platform wrapper). Assets live in `assets/` and are loaded as internal files at runtime.

### State Machine

`GameState` enum drives screen transitions. `OdysseyGame` (extends `Game`) owns all screens as lazy singletons and routes between them via `transitionTo(GameState)`, which plays a 0.28 s fade-out/in. Screens are never destroyed mid-session except `EngineeringLabScreen`, which can be rebuilt via `forceRebuildLab()` when planet gravity changes.

```
MAIN_MENU → ENGINEERING_LAB ↔ BRIDGE_FLIGHT → GALACTIC_MAP → NOVA_TERRA_ARRIVAL
```

### Global State: `ShipData`

Singleton (`ShipData.get()`) holds all mutable economy state: joules, crystals, planet index, structure positions, milestone flags. Persisted to LibGDX `Preferences` (`odyssey_save`) on `pause()` and `dispose()`. Structure positions are stored as flat `float[]` arrays (e.g., `savedBumpers = [x0,y0, x1,y1, ...]`).

### Physics Layer (EngineeringLabScreen)

- `PPM = 60f` pixels-per-meter. World coords × 60 = screen pixels.
- Centrifuge drum: 36-segment `ChainShape` circle at `(4.0, 8.5)` with `r=3.0` meters, kinematic body with angular velocity.
- Interns: dynamic circle bodies (`r=0.25f`, restitution `0.90f`). Cyber-interns: `r=0.38f`, density `2.5f`.
- Bumpers: static circle bodies (`r=0.20f`, restitution `1.40f`).
- Energy generation happens in `EnergyContactListener.beginContact()` using `(massA × velA + massB × velB) × avgRestitution`, doubled if either body has userdata `"BUMPER"`.
- `snapshotState()` saves current structure/intern counts into `ShipData` before any screen rebuild; `restoreState()` re-spawns them on the next `show()`.

### Skin & Theme

`OdysseyGame.buildSkin()` constructs a single LibGDX `Skin` used by all screens. Colors are defined in `OdysseyTheme` — reference those constants rather than hardcoding `Color` values. Button visual states are driven by `setColor()` tinting on a white base drawable using `OdysseyTheme.BTN_*` constants.

### Planet Progression

`ShipData.PLANETS[]` defines 5 planets (Solara → Helios Forge) with increasing distance, gravity multiplier, and farming capacity. Arriving at a planet increments `arrivalsCompleted`, applies `claimArrivalReward()` multiplier boosts, and sets `planetGravityMultiplier` which affects Box2D world gravity on the next lab rebuild.

### Multi-Platform Notes

- `TeaVMLauncher.java` is excluded from desktop and Android builds (TeaVM web subproject, not wired in this repo's gradle).
- `DesktopLauncher.java` excluded from Android build.
- Android shares `src/` sources via `rootProject.file('src')` in `android/build.gradle`.
