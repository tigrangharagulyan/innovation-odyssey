# Active Lab Mechanics — Design Spec
**Date:** 2026-06-01

## Overview

Three interconnected systems that make EngineeringLabScreen actively engaging rather than passive:

1. **Drag-launch placement** — curling-style structure throwing from upgrade buttons
2. **Structure harvest nodes** — structures glow after 10 hits; tap to collect joules + space coins
3. **Dual fuel system** — passive drain (drum) + burst spend (launches); gates both interactions

---

## 1. Drag-Launch Placement

### Interaction
- Upgrade buttons shift ~80px up from current bottom position
- Each button responds to drag gesture (touch/mouse down → drag → release)
- **During drag:** dotted trajectory arc renders from button origin toward drag direction; arc length encodes launch power (distance dragged, capped at screen width × 0.4)
- **On release:** structure body spawns at button world position with computed velocity vector
- Structure flies into centrifuge drum, bounces off walls/interns, settles by friction/restitution
- Arc is approximate — ignores drum rotation intentionally (skill = fun)

### Miss handling
- If structure exits play area without entering drum → despawn
- Joule cost refunded; fuel cost NOT refunded
- No penalty beyond fuel — encourages retry

### Physics
- Structure spawns as dynamic body for ~2 seconds (slides and bounces)
- After 2s or when velocity < threshold → converted to static body at current position
- Hit counter initialized to 0 on placement

---

## 2. Structure Harvest Nodes

### Hit counting
- Every physics contact between an intern body and a placed structure increments that structure's `hitCount`
- At `hitCount == 10` → structure enters **charged** state: glows (pulsing white/gold overlay), pauses hit counting
- `hitCount` does NOT increment past 10 — accumulation pauses until collected

### Collection
- Player taps a charged (glowing) structure → collect reward → reset `hitCount = 0` → glow clears, counting resumes
- **Reward formula:**
  - Joules: `currentJPS × 30` (30 seconds of current income)
  - Space coins (diamonds): `1 + floor(structureIndex / 5)` (scales slightly with how many structures placed)
- Visual: floating "+NJ / +N◆" text pops up from structure position, fades over 1.2s

### Visual states
| State | Appearance |
|-------|-----------|
| Normal (0–9 hits) | Standard texture, no overlay |
| Charged (10 hits) | Pulsing gold glow ring, scale breathes ±5% |
| Just collected | Brief white flash, ring fades over 0.3s |

---

## 3. Dual Fuel System

### Fuel economy
| Source | Amount |
|--------|--------|
| Starting fuel | 300 |
| Max capacity | 300 |
| Passive drain | −1/sec while lab screen is open |
| Launch cost | −10 per structure thrown |
| Offline regen | +1/min while app closed (cap 300) |
| Watch ad | +150 instantly |
| Spend 50 diamonds | Full refill (300) |
| Daily free | +100 on first `EngineeringLabScreen.show()` each calendar day |

### At fuel = 0
- Drum slows to 20% angular velocity (does not stop)
- All launch buttons grey out with "OUT" badge
- Fuel bar pulses red

### Fuel bar UI
- Horizontal bar above upgrade buttons
- Color: green (>60%) → yellow (20–60%) → red (<20%)
- Shows numeric value on tap (small tooltip, 2s auto-hide)

---

## ShipData Changes

### New fields
```java
public int   labFuel            = 300;         // current fuel units
public long  lastFuelRegenMs    = 0L;           // for offline regen calc
public long  lastDailyFuelMs    = 0L;           // for daily free fuel
```

### New method
```java
public void claimDailyFuel()   // +100 if >24h since lastDailyFuelMs
public void regenOfflineFuel() // called on show(); clamps to 300
public boolean spendFuel(int amount) // returns false if insufficient
```

---

## EngineeringLabScreen Changes

### New per-structure state
```java
// Parallel arrays to existing savedBumpers etc.
// hitCount[i] for structure i; serialized to ShipData on snapshotState()
private int[] bumperHitCounts;
private int[] attractorHitCounts;
// etc. for each structure type
```

### New render pass
- After drawing structures, iterate charged ones → draw pulsing glow ring (ShapeRenderer circle, animated alpha)
- Floating reward text: small pool of `FloatingLabel` objects (position, text, age, max 8 simultaneous)

### Input changes
- `touchDown` checks charged structures first (priority over drag-start)
- Drag detection: `touchDown` on button → set `draggingButton = buttonIndex`, record start pos
- `touchDragged` → update arc
- `touchUp` → if drag distance > 20px, execute launch; else treat as normal button tap (open upgrade panel)

---

## Files Modified

| File | Changes |
|------|---------|
| `ShipData.java` | labFuel fields, fuel methods, hitCount persistence |
| `EngineeringLabScreen.java` | Drag-launch, harvest tap, fuel bar UI, hit counting in contact listener |
| `physics/EnergyContactListener.java` | Increment hitCount on structure contact |

---

## Out of Scope (this spec)

- Ads integration (watch ad for fuel) — designed here as placeholder button; wired in Subsystem 4
- IAP diamond purchase for fuel refill — economy designed; store integration in Subsystem 4
- Structure-type-specific tap abilities — deferred, all structures use same harvest behavior for now
