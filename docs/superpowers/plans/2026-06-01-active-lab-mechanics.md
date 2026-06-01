# Active Lab Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make EngineeringLabScreen actively engaging via curling-style structure launch, per-structure harvest nodes (10 hits → glow → tap to collect joules + diamonds), and a dual fuel system (passive drain + launch cost).

**Architecture:** Three layered systems. Fuel gates launches (ShipData fields + drain in render loop). Harvest adds `hitCount` to existing `BumperHitData`/`AttractorHitData` userdata; contact listener increments it; render pass draws glow; touchDown collects. Launch converts existing drag-drop to velocity-based spawn: structure body spawns dynamic at button world position, flies into drum, converts to static when settled.

**Tech Stack:** LibGDX 1.12.1, Box2D, Scene2D, ShapeRenderer, BitmapFont (existing project stack).

---

## Files Modified

| File | What changes |
|------|-------------|
| `src/com/odyssey/ShipData.java` | Add `labFuel`, `lastFuelRegenMs`, `lastDailyFuelMs`; fuel methods; persist/load |
| `src/com/odyssey/physics/EnergyContactListener.java` | Increment `hitCount` on structure-intern contact |
| `src/com/odyssey/screen/EngineeringLabScreen.java` | Fuel bar UI, fuel drain, curling launch, harvest tap & glow render pass |

---

## Task 1 — ShipData: add fuel fields and methods

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

### Background
`ShipData` is the single mutable-state singleton. Fuel is a player resource like `diamonds` — must persist across sessions. Three new fields + four new methods. No changes to existing fields.

- [ ] **Step 1.1 — Add fuel fields after the `diamonds` field (line ~193)**

Find the block:
```java
public int     diamonds        = 0;
public boolean unlimitedLives  = false;
```

Add immediately after `unlimitedLives`:
```java
// Fuel — gates structure launches and powers the drum
public int  labFuel            = 300;
public int  labFuelMax         = 300;
public long lastFuelRegenMs    = 0L;   // ms epoch; for offline regen calc
public long lastDailyFuelMs    = 0L;   // ms epoch; for daily free fuel
```

- [ ] **Step 1.2 — Add fuel helpers after `consumeLife()` (line ~332)**

Add these four methods after the closing `}` of `consumeLife()`:

```java
/** Returns false if insufficient fuel; otherwise deducts and returns true. */
public boolean spendFuel(int amount) {
    if (labFuel < amount) return false;
    labFuel -= amount;
    return true;
}

/** Award fuel, clamped to max. */
public void addFuel(int amount) {
    labFuel = Math.min(labFuelMax, labFuel + amount);
}

/**
 * Call once per show() — adds offline regen since last session close.
 * +1 fuel per minute while app was closed, capped at labFuelMax.
 */
public void regenOfflineFuel() {
    if (lastFuelRegenMs == 0L) { lastFuelRegenMs = System.currentTimeMillis(); return; }
    long now = System.currentTimeMillis();
    long elapsedMinutes = (now - lastFuelRegenMs) / 60_000L;
    if (elapsedMinutes > 0) {
        addFuel((int) Math.min(elapsedMinutes, labFuelMax));
        lastFuelRegenMs = now;
    }
}

/**
 * Call once per show() — grants +100 fuel if more than 24 h since last grant.
 */
public void claimDailyFuel() {
    long now = System.currentTimeMillis();
    if (now - lastDailyFuelMs >= 86_400_000L) {
        addFuel(100);
        lastDailyFuelMs = now;
    }
}
```

- [ ] **Step 1.3 — Reset fuel in `reset()` (line ~236)**

Inside `reset()`, after `diamonds = 5000;`, add:
```java
labFuel         = 300;
lastFuelRegenMs = 0L;
lastDailyFuelMs = 0L;
```

- [ ] **Step 1.4 — Persist fuel in `save()` (line ~550)**

Inside `save()`, after `p.putInteger("diamonds", diamonds);`, add:
```java
p.putInteger("labFuel",         labFuel);
p.putLong("lastFuelRegenMs",    lastFuelRegenMs);
p.putLong("lastDailyFuelMs",    lastDailyFuelMs);
```

- [ ] **Step 1.5 — Load fuel in `load()` (line ~666)**

Inside `load()`, after the line that loads `diamonds`, add:
```java
labFuel         = p.getInteger("labFuel",         300);
lastFuelRegenMs = p.getLong("lastFuelRegenMs",    0L);
lastDailyFuelMs = p.getLong("lastDailyFuelMs",    0L);
```

- [ ] **Step 1.6 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.7 — Commit**

```bash
git add src/com/odyssey/ShipData.java
git commit -m "feat(fuel): add labFuel fields and spendFuel/addFuel/regenOfflineFuel/claimDailyFuel to ShipData"
```

---

## Task 2 — ShipData: add hitCount to BumperHitData and AttractorHitData

**Files:**
- Modify: `src/com/odyssey/ShipData.java` (inner classes at top of file, lines ~16–26)

### Background
`BumperHitData` is the userdata object stored on every standard bumper body. `AttractorHitData` is on attractor/gravity-well bodies. We add `hitCount` (capped at 10) and `harvestPending` flag to each. The contact listener will increment `hitCount`; the render pass will read `harvestPending`; the tap handler will clear both.

- [ ] **Step 2.1 — Add hitCount and harvestPending to BumperHitData**

Find `BumperHitData` (line ~16):
```java
public static final class BumperHitData {
    public boolean isArmBumper   = false;
    public boolean isValleyBlade = false;
    public long    lastHitMs     = 0L;
}
```

Replace with:
```java
public static final class BumperHitData {
    public boolean isArmBumper    = false;
    public boolean isValleyBlade  = false;
    public long    lastHitMs      = 0L;
    public int     hitCount       = 0;    // counts intern contacts; caps at 10
    public boolean harvestPending = false; // true when hitCount reached 10
}
```

- [ ] **Step 2.2 — Add hitCount and harvestPending to AttractorHitData**

Find `AttractorHitData` (line ~24):
```java
public static final class AttractorHitData {
    public long lastHitMs = 0L;
}
```

Replace with:
```java
public static final class AttractorHitData {
    public long    lastHitMs      = 0L;
    public int     hitCount       = 0;
    public boolean harvestPending = false;
}
```

- [ ] **Step 2.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2.4 — Commit**

```bash
git add src/com/odyssey/ShipData.java
git commit -m "feat(harvest): add hitCount and harvestPending to BumperHitData and AttractorHitData"
```

---

## Task 3 — EnergyContactListener: increment hitCount on structure contacts

**Files:**
- Modify: `src/com/odyssey/physics/EnergyContactListener.java`

### Background
`beginContact()` already classifies every contact. Standard bumpers reach the `bumperHit` branch (line ~147); attractors reach `attractorHit`. We add a hit counter increment in both branches, guarded by `!harvestPending` so charged structures stop counting until collected.

- [ ] **Step 3.1 — Increment hitCount in the bumper branch**

Find the bumper branch in `beginContact()` (line ~147):
```java
} else if (bumperHit) {
    // ---- Standard bumper or gravity-well / Tesla-Coil core contact ----
    float bonus     = attractorHit ? SPARK_GRAVITY * sd.gravityMult : sd.bumperSparkValue * sd.bumperMult;
    int   colorType = attractorHit ? 2 : 3;
    sd.addCrystals(bonus);
    sd.pendingBumperSounds++;
    queueFloatNum(contact, bodyA, bodyB, bonus, colorType, sd);
    // Stamp hit time so each body's renderer can drive its own flash animation
    if (aIsStdBumper) ((ShipData.BumperHitData)   bodyA.getUserData()).lastHitMs = System.currentTimeMillis();
    if (bIsStdBumper) ((ShipData.BumperHitData)   bodyB.getUserData()).lastHitMs = System.currentTimeMillis();
    if (attractorHit) {
        long ts = System.currentTimeMillis();
        if (bodyA.getUserData() instanceof ShipData.AttractorHitData)
            ((ShipData.AttractorHitData) bodyA.getUserData()).lastHitMs = ts;
        if (bodyB.getUserData() instanceof ShipData.AttractorHitData)
            ((ShipData.AttractorHitData) bodyB.getUserData()).lastHitMs = ts;
    }
```

Add after the last `lastHitMs` stamp lines (before the closing `}`):
```java
    // Harvest node: increment hitCount up to 10 for standard bumpers and attractors
    if (aIsIntern || bIsIntern) {
        if (aIsStdBumper) {
            ShipData.BumperHitData bhd = (ShipData.BumperHitData) bodyA.getUserData();
            if (!bhd.harvestPending && ++bhd.hitCount >= 10) bhd.harvestPending = true;
        }
        if (bIsStdBumper) {
            ShipData.BumperHitData bhd = (ShipData.BumperHitData) bodyB.getUserData();
            if (!bhd.harvestPending && ++bhd.hitCount >= 10) bhd.harvestPending = true;
        }
        if (attractorHit) {
            if (bodyA.getUserData() instanceof ShipData.AttractorHitData) {
                ShipData.AttractorHitData ahd = (ShipData.AttractorHitData) bodyA.getUserData();
                if (!ahd.harvestPending && ++ahd.hitCount >= 10) ahd.harvestPending = true;
            }
            if (bodyB.getUserData() instanceof ShipData.AttractorHitData) {
                ShipData.AttractorHitData ahd = (ShipData.AttractorHitData) bodyB.getUserData();
                if (!ahd.harvestPending && ++ahd.hitCount >= 10) ahd.harvestPending = true;
            }
        }
    }
```

- [ ] **Step 3.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.3 — Commit**

```bash
git add src/com/odyssey/physics/EnergyContactListener.java
git commit -m "feat(harvest): increment hitCount in EnergyContactListener; set harvestPending at 10"
```

---

## Task 4 — EngineeringLabScreen: fuel drain and daily/offline regen

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
Fuel drains at 1/sec while the lab is open. Drain happens in `render()`. Regen and daily grant happen once on `show()`. When fuel ≤ 0, drum speed clamps to 20%.

- [ ] **Step 4.1 — Add fuel drain field**

Find the field block near `tutorialStep` (line ~408):
```java
private int   tutorialStep    = 0;
```

Add after it:
```java
private float fuelDrainAccum  = 0f;   // accumulator for 1-per-second fuel drain
```

- [ ] **Step 4.2 — Call regen and daily fuel in show()**

Find `show()` in `EngineeringLabScreen`. Locate the line where `ShipData.get()` is first called in `show()` (it calls `restoreState()` or similar). Add at the start of `show()`, before the existing code:

```java
ShipData.get().regenOfflineFuel();
ShipData.get().claimDailyFuel();
```

- [ ] **Step 4.3 — Drain fuel in render()**

In `render(float delta)`, at line 3799 where `animTime += delta;` appears, add immediately after it:

```java
// Fuel passive drain — 1 unit per second
fuelDrainAccum += delta;
if (fuelDrainAccum >= 1f) {
    fuelDrainAccum -= 1f;
    ShipData _fsd = ShipData.get();
    if (_fsd.labFuel > 0) _fsd.labFuel--;
}
```

Then find lines 7771–7775 where the centrifuge body angular velocity is applied each frame:
```java
float cur = centrifugeBody.getAngularVelocity();
if (cur < bodyTargetRPM)
    centrifugeBody.setAngularVelocity(Math.min(cur + accel * delta, bodyTargetRPM));
else if (cur > bodyTargetRPM)
    centrifugeBody.setAngularVelocity(Math.max(cur - accel * delta, bodyTargetRPM));
```

Replace with:
```java
float cur = centrifugeBody.getAngularVelocity();
// Clamp target to 20% when out of fuel
float _fuelMult = ShipData.get().labFuel <= 0 ? 0.20f : 1.0f;
float _effTarget = bodyTargetRPM * _fuelMult;
if (cur < _effTarget)
    centrifugeBody.setAngularVelocity(Math.min(cur + accel * delta, _effTarget));
else if (cur > _effTarget)
    centrifugeBody.setAngularVelocity(Math.max(cur - accel * delta, _effTarget));
```

- [ ] **Step 4.4 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.5 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(fuel): drain 1 fuel/sec in render(), clamp drum to 20% at empty, call regen/daily on show()"
```

---

## Task 5 — EngineeringLabScreen: fuel bar UI

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
A horizontal fuel bar sits above the upgrade buttons. It's drawn with `ShapeRenderer` — same pattern as energy bars elsewhere in the screen. Color shifts green→yellow→red. When fuel = 0, launch buttons are greyed and show "OUT".

- [ ] **Step 5.1 — Draw fuel bar in the HUD render pass**

Find `drawHudBar()` at line 5150. Add a call to a new `drawFuelBar()` method right after `drawHudBar()` (line 3871):

In the `render()` method, after `drawHudBar();`, add:
```java
drawFuelBar();
```

Then add the new method near `drawHudBar()` (after its closing `}`):

```java
private void drawFuelBar() {
    ShipData _sd = ShipData.get();
    float fuelFrac = Math.max(0f, Math.min(1f, _sd.labFuel / (float) _sd.labFuelMax));
    float barW = RENDER_W - 20f;
    float barX = 10f;
    float barY = 148f;  // sits just above the 3-button upgrade row
    float barH = 8f;

    batch.end();

    shapeR.setProjectionMatrix(renderCam.combined);
    shapeR.begin(ShapeRenderer.ShapeType.Filled);
    // Track
    shapeR.setColor(0.10f, 0.10f, 0.15f, 0.80f);
    shapeR.rect(barX, barY, barW, barH);
    // Fill — green→yellow→red
    float r     = fuelFrac < 0.5f ? 1f - (fuelFrac * 2f - 1f) * (fuelFrac * 2f - 1f) : 1f; // approx
    float g     = fuelFrac < 0.5f ? fuelFrac * 2f : 1f;
    float pulse = (_sd.labFuel <= 0) ? (0.5f + 0.5f * MathUtils.sin(animTime * 6f)) : 1f;
    shapeR.setColor(r * pulse, g * pulse, 0.05f, 0.90f);
    shapeR.rect(barX, barY, barW * fuelFrac, barH);
    shapeR.end();

    batch.begin();
}
```

Note: `shapeR` is the ShapeRenderer field (line 250). `animTime` is the animation accumulator (line 398). The `batch.end()` / `batch.begin()` sandwich is required because ShapeRenderer and SpriteBatch can't both be active simultaneously — this is the same pattern used elsewhere in the file.

- [ ] **Step 5.2 — Grey out launch buttons when fuel = 0**

In the `updateUi()` or equivalent method that sets button text/style (search for `btnBumper.setText`), add fuel check:

```java
// Grey out structure buttons when fuel is empty
ShipData _fusd = ShipData.get();
boolean fuelEmpty = _fusd.labFuel <= 0;
if (fuelEmpty) {
    btnBumper.setText("OUT");
    btnGravityWell.setText("OUT");
} // existing setText logic runs in else branches below — wrap existing bumper/gravity setText in else blocks
```

The exact integration point depends on the existing if/else chain — look at how `btnBumper.setText` is currently called (line ~4049) and add `fuelEmpty` as an additional guard.

- [ ] **Step 5.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5.4 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(fuel): draw fuel bar above buttons, grey out structure buttons when fuel=0"
```

---

## Task 6 — EngineeringLabScreen: cost fuel on structure launch

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
Every structure placement costs 10 fuel. Current placement happens in `touchUp` handlers (lines ~2982–3019 for bumpers). We gate each placement with `spendFuel(10)`. If insufficient fuel, placement is refused silently (fuel bar is already flashing red).

- [ ] **Step 6.1 — Gate bumper placement with fuel check**

Find the bumper `touchUp` handler (line ~2981). The spawn line is:
```java
if (bumpers.size < maxBumpersAllowed() && sd2.spendCrystals(bumperCost())) {
    spawnCentrifugeBumper(wx, wy);
}
```

Replace with:
```java
if (bumpers.size < maxBumpersAllowed() && sd2.spendFuel(10) && sd2.spendCrystals(bumperCost())) {
    spawnCentrifugeBumper(wx, wy);
} else if (sd2.labFuel < 10) {
    sd2.spendFuel(0); // no-op, just ensures no fuel was taken
}
```

- [ ] **Step 6.2 — Gate gravity well placement with fuel check**

Find the gravity well `touchUp` handler (same pattern, ~line 3086):
```java
if (attractors.size < maxGravityAllowed() && sd2.spendCrystals(gravityCost())) {
    spawnAttractorBumper(wx, wy);
}
```

Replace with:
```java
if (attractors.size < maxGravityAllowed() && sd2.spendFuel(10) && sd2.spendCrystals(gravityCost())) {
    spawnAttractorBumper(wx, wy);
}
```

- [ ] **Step 6.3 — Gate Frostheim icicle placement with fuel check**

Same pattern in the icicle branch:
```java
if (icicleNodes.size < maxIcicleNodesAllowed() && sd2.spendCrystals(icicileCost())) {
    spawnIcicleNode(wx, wy);
}
```

Replace with:
```java
if (icicleNodes.size < maxIcicleNodesAllowed() && sd2.spendFuel(10) && sd2.spendCrystals(icicileCost())) {
    spawnIcicleNode(wx, wy);
}
```

- [ ] **Step 6.4 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6.5 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(fuel): deduct 10 fuel per structure placement (bumper, gravity, icicle)"
```

---

## Task 7 — EngineeringLabScreen: harvest glow render pass

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
After all structure bodies are drawn, a second pass renders a pulsing gold ring over any structure with `harvestPending = true`. Uses `ShapeRenderer` — same pattern as bumper hit-flash elsewhere. `stateTime` drives the pulse.

- [ ] **Step 7.1 — Add harvest glow render pass**

Find the section in `render()` that draws bumpers (search for `texBumper` draw). After the existing bumper draw loop (still inside a `batch.begin()` block), add a new method call:

```java
drawHarvestGlows();
```

Add the new method near the other draw helpers:

```java
private void drawHarvestGlows() {
    boolean hasCharged = false;
    for (int _i = 0; _i < bumpers.size; _i++) {
        Body _b = bumpers.items[_i];
        if (_b.getUserData() instanceof ShipData.BumperHitData
                && ((ShipData.BumperHitData) _b.getUserData()).harvestPending) {
            hasCharged = true; break;
        }
    }
    if (!hasCharged) {
        for (int _i = 0; _i < attractors.size; _i++) {
            Body _b = attractors.items[_i];
            if (_b.getUserData() instanceof ShipData.AttractorHitData
                    && ((ShipData.AttractorHitData) _b.getUserData()).harvestPending) {
                hasCharged = true; break;
            }
        }
    }
    if (!hasCharged) return;

    batch.end();

    Gdx.gl.glLineWidth(2.5f);
    shapeR.setProjectionMatrix(renderCam.combined);
    shapeR.begin(ShapeRenderer.ShapeType.Line);
    float _pulse = 0.55f + 0.45f * MathUtils.sin(animTime * 4f);

    for (int _i = 0; _i < bumpers.size; _i++) {
        Body _b = bumpers.items[_i];
        if (!(_b.getUserData() instanceof ShipData.BumperHitData)) continue;
        if (!((ShipData.BumperHitData) _b.getUserData()).harvestPending) continue;
        float _bx = _b.getPosition().x * PPM;
        float _by = _b.getPosition().y * PPM;
        shapeR.setColor(1.0f, 0.82f, 0.1f, _pulse);
        shapeR.circle(_bx, _by, BUMPER_RADIUS * PPM * 1.6f, 20);
    }
    for (int _i = 0; _i < attractors.size; _i++) {
        Body _b = attractors.items[_i];
        if (!(_b.getUserData() instanceof ShipData.AttractorHitData)) continue;
        if (!((ShipData.AttractorHitData) _b.getUserData()).harvestPending) continue;
        float _bx = _b.getPosition().x * PPM;
        float _by = _b.getPosition().y * PPM;
        shapeR.setColor(1.0f, 0.82f, 0.1f, _pulse);
        shapeR.circle(_bx, _by, BUMPER_RADIUS * PPM * 2.0f, 24);
    }
    shapeR.end();
    Gdx.gl.glLineWidth(1f);

    batch.begin();
}

- [ ] **Step 7.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7.3 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(harvest): render pulsing gold glow ring on charged structures"
```

---

## Task 8 — EngineeringLabScreen: harvest tap collection

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
`touchDown` in `InputAdapter` (line ~3413) currently only handles placement. We add a first-priority check: if the tap falls within a charged structure's radius, collect the reward, reset the structure, and return `true` (consume event). Reward = `currentJPS × 30` joules + `1 + floor(structureIndex / 5)` diamonds.

- [ ] **Step 8.1 — Add harvest tap check at start of touchDown**

Find `InputAdapter.touchDown` (line ~3414). The method starts with:
```java
@Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
    if (btn == 1) { placementMode = PLACE_NONE; return true; }
    if (btn != 0 || placementMode == PLACE_NONE) return false;
    touchWorld.set(sx, sy, 0);
    physViewport.unproject(touchWorld);
    float wx = touchWorld.x, wy = touchWorld.y;
```

Replace with:
```java
@Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
    if (btn == 1) { placementMode = PLACE_NONE; return true; }
    // ---- Harvest tap: check charged structures before placement ----
    touchWorld.set(sx, sy, 0);
    physViewport.unproject(touchWorld);
    float wx = touchWorld.x, wy = touchWorld.y;
    ShipData _hsd = ShipData.get();
    float _tapR2 = (BUMPER_RADIUS * 2.5f) * (BUMPER_RADIUS * 2.5f);
    for (int _i = 0; _i < bumpers.size; _i++) {
        Body _b = bumpers.items[_i];
        if (!(_b.getUserData() instanceof ShipData.BumperHitData)) continue;
        ShipData.BumperHitData _bhd = (ShipData.BumperHitData) _b.getUserData();
        if (!_bhd.harvestPending) continue;
        float _dx = _b.getPosition().x - wx, _dy = _b.getPosition().y - wy;
        if (_dx * _dx + _dy * _dy < _tapR2) {
            float _joules = _hsd.currentJPS * 30f;
            int   _gems   = 1 + _i / 5;
            _hsd.addJoules(_joules);
            _hsd.diamonds += _gems;
            _bhd.harvestPending = false;
            _bhd.hitCount = 0;
            queueHarvestPop(_b.getPosition().x * PPM, _b.getPosition().y * PPM, _joules, _gems);
            return true;
        }
    }
    for (int _i = 0; _i < attractors.size; _i++) {
        Body _b = attractors.items[_i];
        if (!(_b.getUserData() instanceof ShipData.AttractorHitData)) continue;
        ShipData.AttractorHitData _ahd = (ShipData.AttractorHitData) _b.getUserData();
        if (!_ahd.harvestPending) continue;
        float _dx = _b.getPosition().x - wx, _dy = _b.getPosition().y - wy;
        if (_dx * _dx + _dy * _dy < _tapR2) {
            float _joules = _hsd.currentJPS * 30f;
            int   _gems   = 1 + _i / 5;
            _hsd.addJoules(_joules);
            _hsd.diamonds += _gems;
            _ahd.harvestPending = false;
            _ahd.hitCount = 0;
            queueHarvestPop(_b.getPosition().x * PPM, _b.getPosition().y * PPM, _joules, _gems);
            return true;
        }
    }
    if (btn != 0 || placementMode == PLACE_NONE) return false;
```

- [ ] **Step 8.2 — Add queueHarvestPop() method**

`FloatEntry` (line 316) is the floating number struct: `wx, wy, value, colorType, age, driftX`. `activeFloats` (line 324) is the array. Add this method near the other private helpers:

```java
/** Add a floating "+NJ +N◆" harvest reward label directly to activeFloats. */
private void queueHarvestPop(float px, float py, float joules, int gems) {
    FloatEntry fe = new FloatEntry();
    fe.wx        = px / PPM;
    fe.wy        = py / PPM;
    fe.value     = joules;
    fe.colorType = 4;      // harvest gold — handled in drawFloatNumbers
    fe.age       = 0f;
    fe.driftX    = 0f;
    activeFloats.add(fe);
    // Second entry for the gem count — small offset so they don't overlap
    FloatEntry fe2 = new FloatEntry();
    fe2.wx        = px / PPM + 0.3f;
    fe2.wy        = py / PPM - 0.2f;
    fe2.value     = gems;
    fe2.colorType = 5;     // gem cyan — handled in drawFloatNumbers
    fe2.age       = 0f;
    fe2.driftX    = 0.05f;
    activeFloats.add(fe2);
}
```

- [ ] **Step 8.3 — Handle colorType 4 and 5 in drawFloatNumbers()**

Find `drawFloatNumbers()` at line 5168. The switch on `colorType` is:
```java
Color c = switch (fe.colorType) {
    case 0  -> OdysseyTheme.FLOAT_E;
    case 1  -> OdysseyTheme.FLOAT_SP;
    case 2  -> OdysseyTheme.FLOAT_SPECIAL;
    default -> OdysseyTheme.FLOAT_BUMPER;
};
```

Replace with:
```java
Color c = switch (fe.colorType) {
    case 0  -> OdysseyTheme.FLOAT_E;
    case 1  -> OdysseyTheme.FLOAT_SP;
    case 2  -> OdysseyTheme.FLOAT_SPECIAL;
    case 4  -> new Color(1.0f, 0.85f, 0.10f, 1f);  // harvest joules — gold
    case 5  -> new Color(0.35f, 1.00f, 0.90f, 1f); // harvest gems  — cyan
    default -> OdysseyTheme.FLOAT_BUMPER;
};
```

Also update the `text` formatting line below it (line 5192) to handle gems (integer, not float):
```java
String text = fe.colorType == 5
    ? "+" + (int) fe.value + "◆"
    : "+" + (int) fe.value;
```

- [ ] **Step 8.4 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8.5 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(harvest): tap charged structure to collect joules+diamonds, queue floating reward pop"
```

---

## Task 9 — Run and verify

- [ ] **Step 9.1 — Run desktop build**

```bash
./gradlew run 2>&1 | tail -20
```

- [ ] **Step 9.2 — Manual verification checklist**

Open Engineering Lab and verify:
- [ ] Fuel bar visible above upgrade buttons, starts at 300 (green)
- [ ] Fuel drains ~1/sec while lab is open
- [ ] Place a bumper → fuel decreases by 10
- [ ] Fuel bar turns yellow below 60%, red below 20%
- [ ] At fuel = 0 → drum slows to ~20% speed, "OUT" appears on structure buttons
- [ ] Let an intern bounce off a bumper 10+ times → bumper develops gold pulsing ring
- [ ] Tap the glowing bumper → joules and diamonds added to totals, ring disappears
- [ ] Close and reopen the lab → fuel has regenerated (up to 1/min offline)
- [ ] First open of a new calendar day → +100 fuel granted

- [ ] **Step 9.3 — Commit any fixes found during testing**

```bash
git add -p
git commit -m "fix(active-lab): <describe fix>"
```

---

---

## Task 10 — EngineeringLabScreen: curling-style launch (dynamic → static settle)

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
Currently, dragging from a button places the structure statically at the release point. The curling enhancement: on touchUp, spawn a **dynamic** body at the **button origin** with a velocity vector toward the release point. After 2 seconds or when speed drops below a threshold, the dynamic body is destroyed and replaced with a static body at its current position. This gives a satisfying "throw and watch it settle" experience.

The existing `dragStageX/Y` fields are updated on every `touchDragged` — they hold the current release point. We add `dragOriginStageX/Y` to record where the drag started (the button center).

- [ ] **Step 10.1 — Add launch origin and settling tracker fields**

Find the `dragMode` / `dragStageX` field declarations (around line 410):
```java
private int   dragMode    = PLACE_NONE;
private float dragStageX  = 0f;
private float dragStageY  = 0f;
```

Add after them:
```java
private float dragOriginStageX = 0f;  // button center at drag start (stage coords)
private float dragOriginStageY = 0f;
// Dynamic "curling" bodies settling to static — (body, timer) pairs
private final com.badlogic.gdx.utils.Array<Body>  curlingBodies  = new com.badlogic.gdx.utils.Array<>();
private final com.badlogic.gdx.utils.Array<Float> curlingTimers  = new com.badlogic.gdx.utils.Array<>();
private static final float CURLING_SETTLE_TIME = 2.0f;   // seconds before force-settle
private static final float CURLING_SETTLE_SPEED = 0.3f;  // m/s threshold for early settle
```

- [ ] **Step 10.2 — Capture drag origin at touchDown**

In `btnBumper.addListener` (line ~2946), in `touchDown`, after setting `dragMode = PLACE_BUMPER;`:
```java
dragOriginStageX = event.getStageX();
dragOriginStageY = event.getStageY();
```

Do the same in `btnGravityWell.addListener` `touchDown` after `dragMode = PLACE_GRAVITY;` (line ~3033):
```java
dragOriginStageX = event.getStageX();
dragOriginStageY = event.getStageY();
```

- [ ] **Step 10.3 — Replace static spawn with dynamic launch in btnBumper touchUp**

In `btnBumper` `touchUp` (line ~2981), find the bumper spawn block:
```java
} else if (!isEmberIV()) {
    if (bumpers.size < maxBumpersAllowed() && sd2.spendCrystals(bumperCost())) {
        spawnCentrifugeBumper(wx, wy);
    }
}
```

Replace with:
```java
} else if (!isEmberIV()) {
    if (bumpers.size < maxBumpersAllowed() && sd2.spendFuel(10) && sd2.spendCrystals(bumperCost())) {
        // Curling launch: spawn dynamic at button origin, velocity toward release point
        float originWX = dragOriginStageX / PPM;
        float originWY = (dragOriginStageY + 80f) / PPM;
        float releaseWX = wx;
        float releaseWY = wy;
        float dvx = releaseWX - originWX;
        float dvy = releaseWY - originWY;
        float dist = (float) Math.sqrt(dvx * dvx + dvy * dvy);
        float launchSpeed = Math.min(dist * 4f, 12f);  // scale with drag distance, cap at 12 m/s
        if (dist > 0.01f) { dvx /= dist; dvy /= dist; }
        launchCurlingBumper(originWX, originWY, dvx * launchSpeed, dvy * launchSpeed);
    }
}
```

- [ ] **Step 10.4 — Add launchCurlingBumper() method**

Add after `spawnCentrifugeBumper()` (line ~2073):

```java
/**
 * Spawn a dynamic bumper body at (wx, wy) with initial velocity (vx, vy).
 * Tracked in curlingBodies/curlingTimers; stepPhysics() settles it to static.
 */
private void launchCurlingBumper(float wx, float wy, float vx, float vy) {
    BodyDef bd = new BodyDef();
    bd.type = BodyDef.BodyType.DynamicBody;
    bd.position.set(wx, wy);
    CircleShape circle = new CircleShape();
    circle.setRadius(bumperCoreR);
    FixtureDef fd = new FixtureDef();
    fd.shape       = circle;
    fd.restitution = 0.50f;   // lower restitution while in flight so it doesn't bounce forever
    fd.friction    = 0.3f;
    fd.density     = 1.0f;
    Body body = world.createBody(bd);
    body.createFixture(fd);
    body.setUserData(new ShipData.BumperHitData());
    body.setLinearVelocity(vx, vy);
    circle.dispose();
    curlingBodies.add(body);
    curlingTimers.add(0f);
}
```

- [ ] **Step 10.5 — Settle curling bodies in stepPhysics()**

`stepPhysics()` is called every frame from `render()` (line 3805: `stepPhysics(delta)`). Find `stepPhysics` method and add at its end, before the closing `}`:

```java
// ---- Settle curling bodies ----
for (int _ci = curlingBodies.size - 1; _ci >= 0; _ci--) {
    Body _cb = curlingBodies.items[_ci];
    float _ct = curlingTimers.get(_ci) + delta;
    curlingTimers.set(_ci, _ct);
    float _speed = _cb.getLinearVelocity().len();
    boolean _settled = _ct >= CURLING_SETTLE_TIME || _speed < CURLING_SETTLE_SPEED;
    if (_settled) {
        float _fx = _cb.getPosition().x;
        float _fy = _cb.getPosition().y;
        world.destroyBody(_cb);
        curlingBodies.removeIndex(_ci);
        curlingTimers.removeIndex(_ci);
        // Clamp to drum interior before placing static
        float _dx = _fx - CENTRIFUGE_CX, _dy = _fy - CENTRIFUGE_CY;
        float _d2 = _dx * _dx + _dy * _dy;
        float _maxR = CENTRIFUGE_R * 0.90f;
        if (_d2 > _maxR * _maxR) {
            float _d = (float) Math.sqrt(_d2);
            _fx = CENTRIFUGE_CX + _dx / _d * _maxR;
            _fy = CENTRIFUGE_CY + _dy / _d * _maxR;
        }
        spawnCentrifugeBumper(_fx, _fy);
    }
}
```

- [ ] **Step 10.6 — Clean up curling bodies on screen dispose/reset**

Find `clearLab()` or the method that calls `world.destroyBody` for `bumpers` (search for `bumpers` iteration that calls `destroyBody`). Add before the bumpers loop:

```java
for (int _ci = 0; _ci < curlingBodies.size; _ci++)
    world.destroyBody(curlingBodies.items[_ci]);
curlingBodies.clear();
curlingTimers.clear();
```

- [ ] **Step 10.7 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10.8 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(curling): drag-launch bumpers as dynamic bodies that settle to static after 2s"
```

---

## Notes for Implementer

- `shapeR` is the ShapeRenderer field (line 250). `animTime` is the animation time accumulator (line 398). These are the correct variable names — not `shapeRenderer` or `stateTime`.
- `shapeR` must be in a `begin()`/`end()` pair. Never nest two `begin()` calls. The `batch.end()` / `batch.begin()` sandwich pattern is required when mixing SpriteBatch and ShapeRenderer — see `drawFuelBar()` and `drawHarvestGlows()` in this plan.
- `physViewport.unproject(touchWorld)` converts screen coords to Box2D world coords. `touchWorld` is a `Vector3` field — reuse it, don't allocate.
- Structure arrays (`bumpers`, `attractors`) use `com.badlogic.gdx.utils.Array<Body>` — iterate as `bumpers.items[i]` with `bumpers.size`.
- `currentJPS` in ShipData is the joules-per-second rate. It may be near-zero early game — floor harvest reward at `Math.max(10f, currentJPS * 30f)` to keep it non-trivial.
- `RENDER_W = 480f`, `RENDER_H = 854f`, `PPM = 60f` — all defined as static constants at the top of the class.
- Dynamic bodies in the `curlingBodies` array still fire `beginContact` events — but `BumperHitData.harvestPending` starts false and `hitCount` starts 0, so no glow appears during flight. This is intentional.
