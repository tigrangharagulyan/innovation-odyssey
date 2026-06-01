# Maze Rings System — Design Spec
**Date:** 2026-06-01

## Overview

Replace the flight-based planet progression with a physics destruction mechanic. Three concentric ring walls sit inside the centrifuge drum. Orbs bounce off rings, each contact reduces that ring's HP. When HP reaches zero the ring disappears. After all rings are cleared, orbs can hit the center target. 200 center hits = planet conquered → direct arrival screen (no flight).

---

## Ring Layout

Centrifuge is at `(CENTRIFUGE_CX=4.0, CENTRIFUGE_CY=8.5)` with outer radius `R=3.0` world units.

| Ring | Radius | HP | Notes |
|------|--------|----|-------|
| Ring 0 (outer) | 2.0m | 30 hits | First barrier |
| Ring 1 (middle) | 1.3m | 50 hits | Accessible after Ring 0 gone |
| Ring 2 (inner) | 0.7m | 80 hits | Accessible after Ring 1 gone |
| Center target | 0.3m | 200 hits | Always present, win on 0 |

Rings are **static** Box2D bodies with `ChainShape.createLoop()` — closed loop collides from both sides. Center target is a small static circle body.

---

## Hit Data Classes (in ShipData)

```java
public static final class RingHitData {
    public int hitsRemaining;
    public final int maxHits;
    public final int ringIndex;  // 0, 1, 2
    public RingHitData(int maxHits, int ringIndex) {
        this.maxHits = maxHits;
        this.hitsRemaining = maxHits;
        this.ringIndex = ringIndex;
    }
}

public static final class CenterHitData {
    public int hitsRemaining = 200;
    public static final int MAX_HITS = 200;
}
```

---

## Contact Detection

In `EnergyContactListener.beginContact()`:
- If one body has `RingHitData` and the other is an intern (`InternBallData` or pellet) → decrement `hitsRemaining`. Floor at 0.
- If one body has `CenterHitData` and the other is an intern → decrement `hitsRemaining`. Floor at 0.
- Ring/center cannot destroy themselves from inside `beginContact` (Box2D locked during step). Enqueue bodies with `hitsRemaining == 0` into `ShipData.pendingRingDestructions` (`Array<Body>`).

---

## Ring Destruction (stepPhysics)

After `world.step()`, in `EngineeringLabScreen.stepPhysics()`:
```
for each body in pendingRingDestructions:
    world.destroyBody(body)
    remove from rings[] array
    showCeleb("RING DESTROYED", "Layer N cleared!")
    play milestone sound
pendingRingDestructions.clear()
```

Center destruction check (also after world.step()):
```
if centerBody != null && ((CenterHitData) centerBody.getUserData()).hitsRemaining <= 0:
    world.destroyBody(centerBody)
    centerBody = null
    triggerPlanetWin()
```

---

## Planet Win Flow

`triggerPlanetWin()` in `EngineeringLabScreen`:
```java
private void triggerPlanetWin() {
    ShipData sd = ShipData.get();
    sd.markArrival(0f, 0f);   // distance/days = 0 (no flight)
    sd.claimArrivalReward();
    SoundManager.get().playMilestone();
    game.transitionTo(GameState.NOVA_TERRA_ARRIVAL);
}
```

---

## Persistence

Ring hit progress is **not persisted** across app sessions. On `show()`, rings reset to full HP. This keeps sessions fresh and avoids complex state management.

---

## UI Changes

**Remove** from bottom panel:
- `btnBumper` (structure placement button)
- `btnGravityWell` (gravity well button)
- `btnGravShift` / `btnGravCenter`
- `btnFlight` / `btnJumpReady` (launch buttons)

**Add** to render pass:
- Per-ring HP bar: thin arc overlay on each ring, color shifts green→red as HP drops
- Center HP counter: large number above center, color pulses as it decreases
- Ring destruction flash: white flash + shake on each ring cleared

**Keep** in bottom panel:
- `btnAdd` (hire orb / slingshot)
- Stats strip (SP, speed)
- Skill row (to be added in next iteration)

---

## Visual Ring Rendering

Draw in a dedicated `drawRings()` method called in `render()` after `drawBumpers()`.

Each ring:
- **Base ring**: `shapeR.circle()` with color that shifts white→orange→red as HP drops: `hpFrac = hitsRemaining / maxHits`
- **HP bar arc**: partial arc drawn with multiple small line segments showing remaining HP fraction
- **Damage pulse**: flash white briefly on each hit (`lastHitMs` stamp in RingHitData)

Center target:
- Pulsing circle, color shifts based on HP
- Number overlay showing remaining hits

---

## Files Modified

| File | Changes |
|------|---------|
| `ShipData.java` | Add `RingHitData`, `CenterHitData`, `pendingRingDestructions` field |
| `EnergyContactListener.java` | Detect ring/center contacts, decrement HP, enqueue destroyed bodies |
| `EngineeringLabScreen.java` | Spawn rings+center on show(), destroy in stepPhysics(), drawRings(), remove structure buttons, triggerPlanetWin() |

---

## Out of Scope (this iteration)

- Orb skill system (next)
- Per-planet different ring counts/HP
- Maze visual polish (cracks, particles)
- Ring respawn / difficulty scaling
- Frostheim/EmberIV planet ring variants
