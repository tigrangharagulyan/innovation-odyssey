# Intern Skills System — Design Spec
**Date:** 2026-06-01

## Overview

Replace the RPM-triggered milestone perk system with an intern skill system. Before launching an intern via slingshot, player picks a skill from a row of buttons (where perk buttons currently sit). The intern enters the drum with that skill applied — different color, different physics, different behavior. Max 3 interns in the centrifuge at once.

---

## What Gets Removed

| Removed | Replacement |
|---------|------------|
| `milestoneAchieved[]` field and RPM-trigger logic | Intern skill selection |
| `perkButtons[]` row | Skill selection button row (same position) |
| Perk icon textures (`texPerkSpeed`, `texPerkElas`, etc.) | Skill icons (programmatic) |
| `savedMilestoneAchieved[]` in ShipData | Removed |
| Sector-gated structure unlocks (bumpers after CP I, gravity after CP II) | Bumpers unlock at planet 1, gravity wells at planet 2 — based on `arrivalsCompleted` |

BridgeFlightScreen sectors remain as visual progress markers. They no longer gate ability unlocks.

---

## Intern Cap

- Max interns in drum: **3** (flat cap, no scaling)
- `internCap()` returns 3 always (for Solara/standard planets)
- Milestone-based cap increases removed

---

## InternSkill Enum

Lives in `ShipData.java`:

```java
public enum InternSkill {
    NONE,      // standard intern — blue
    SPEEDY,    // yellow — 1.8× restitution, 0.5× density, 1.5× kick speed
    TRIPLE,    // green  — spawns 3 small interns (radius 0.15f) at launch
    GIANT,     // red    — 2× radius, 3× density, 2× energy per contact
    ELECTRIC,  // cyan   — +5 diamonds per bumper/wall contact
    HEAVY      // orange — 3× density, 1.2× radius, massive collision impulse
}
```

---

## InternBallData (userData object)

New inner class in `ShipData.java` — replaces `"INTERN_NORMAL"` String as body userData:

```java
public static final class InternBallData {
    public InternSkill skill;
    public long        lastHitMs = 0L;  // for sound/animation throttling
    public int         gemCount  = 0;   // diamonds earned lifetime (Electric)
}
```

Contact listener and draw code check `body.getUserData() instanceof ShipData.InternBallData` to identify interns.

---

## Skill Costs (SP / crystals)

| Skill | Cost |
|-------|------|
| NONE | Free |
| SPEEDY | 50 SP |
| TRIPLE | 200 SP |
| GIANT | 300 SP |
| ELECTRIC | 150 SP |
| HEAVY | 250 SP |

Cost deducted at launch time (not at skill selection). If player can't afford selected skill, downgrade to NONE silently.

---

## Physics per Skill

| Skill | Radius | Density | Restitution | Kick Speed | Special |
|-------|--------|---------|-------------|------------|---------|
| NONE | 0.25f | 1.0f | 0.90f | 3.0 m/s | — |
| SPEEDY | 0.25f | 0.5f | 1.62f | 5.0 m/s | — |
| TRIPLE | 0.15f | 1.0f | 0.90f | 4.0 m/s | 3× bodies spawned |
| GIANT | 0.50f | 3.0f | 0.90f | 2.5 m/s | energy events × 2 |
| ELECTRIC | 0.25f | 1.0f | 0.90f | 3.0 m/s | +5 diamonds per struct contact |
| HEAVY | 0.30f | 3.0f | 0.85f | 4.5 m/s | separation impulse × 3 |

---

## Intern Colors

Applied via `batch.setColor` in `drawInterns()`:

| Skill | RGB |
|-------|-----|
| NONE | 0.35, 0.80, 1.0 (original blue) |
| SPEEDY | 1.0, 0.90, 0.15 (yellow) |
| TRIPLE | 0.25, 1.0, 0.40 (green) |
| GIANT | 1.0, 0.25, 0.25 (red) |
| ELECTRIC | 0.20, 0.95, 1.0 (cyan) |
| HEAVY | 1.0, 0.55, 0.10 (orange) |

---

## TRIPLE Behavior

When a TRIPLE intern's flying phase enters the drum boundary:
- Instead of `spawnBall(x, y)` once, spawn 3 balls with TRIPLE `InternBallData`
- Each spawned at slight offset: `(x ± 0.15, y)` and `(x, y + 0.15)`
- Each gets kick velocity in spread directions (±20° from entry velocity)
- Counts as 3 interns toward the cap (so can only fire TRIPLE if `balls.size == 0`)

---

## ELECTRIC Behavior

In `EnergyContactListener.beginContact()`, when an ELECTRIC intern contacts a bumper or wall:
- `sd.diamonds += 1` (one diamond per hit, not 5 — keeps economy balanced)
- Queue a small `+1◆` float label (colorType 5)

---

## GIANT Behavior

In `EnergyContactListener.beginContact()`, for GIANT intern contacts:
- Energy award × 2 for all contact types involving this intern

---

## UI: Skill Row

Replaces `perkButtons[]` in the existing perk button row layout.

6 buttons: NONE, SPEEDY, TRIPLE, GIANT, ELECTRIC, HEAVY

Each button:
- Small square tile (same style as current perk tiles)
- Shows skill color dot + abbreviated name ("SPD", "3×", "BIG", "⚡", "HVY")
- Tapping selects it (highlighted border), deselects others
- Selected skill persists until changed

Selected skill shown on slingshot ghost: flying intern uses skill color.

---

## ShipData Changes

- Add `InternSkill` enum
- Add `InternBallData` inner class
- Remove `savedMilestoneAchieved[]`, `boolean[] milestoneAchieved`
- Add `public InternSkill selectedInternSkill = InternSkill.NONE;` (transient, not persisted)

---

## Files Modified

| File | Changes |
|------|---------|
| `ShipData.java` | Add InternSkill enum, InternBallData class, remove milestone fields |
| `EngineeringLabScreen.java` | Remove milestone system, add skill buttons, apply skill physics in spawnBall, TRIPLE logic, color in drawInterns |
| `EnergyContactListener.java` | Check InternBallData for ELECTRIC/GIANT bonuses, update intern detection |

---

## Out of Scope

- Persisting selected skill across sessions (transient only)
- Frostheim/EmberIV planet-specific intern variants (keep existing for those planets)
- Skill upgrade tiers
- IAP skill unlocks (economy design only, no store integration)
