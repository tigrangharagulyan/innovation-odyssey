# Maze Rings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace flight-based planet progression with a physics destruction mechanic — 3 concentric rings inside the centrifuge drum, each with HP; orbs destroy rings by bouncing into them; 200 hits to center = planet conquered, transition directly to arrival screen.

**Architecture:** Three layers: (1) ShipData adds `RingHitData` and `CenterHitData` inner classes; (2) `EnergyContactListener` detects ring/center contacts and decrements HP; (3) `EngineeringLabScreen` spawns rings + center body on `show()`, checks HP every physics step and destroys bodies when HP=0, hides structure buttons, draws ring visuals, triggers planet win.

**Tech Stack:** LibGDX 1.12.1, Box2D (ChainShape.createLoop for rings, CircleShape for center), Scene2D, ShapeRenderer.

---

## Files Modified

| File | Changes |
|------|---------|
| `ShipData.java` | Add `RingHitData`, `CenterHitData` inner classes |
| `EnergyContactListener.java` | Detect ring/center contacts, decrement HP |
| `EngineeringLabScreen.java` | Spawn rings+center, destroy in stepPhysics, drawRings(), hide structure buttons, triggerPlanetWin() |

---

## Task 1 — ShipData: RingHitData and CenterHitData

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

### Background
Ring bodies need to track HP and their index (0=outer, 1=middle, 2=inner). The contact listener will read these from body userData. A `readyToDestroy` flag avoids queuing issues — contact listener sets it, stepPhysics acts on it.

- [ ] **Step 1.1 — Add RingHitData and CenterHitData after AttractorHitData**

Find the closing `}` of `AttractorHitData` (around line 28). Add immediately after:

```java
    public static final class RingHitData {
        public final int ringIndex;   // 0=outer, 1=middle, 2=inner
        public final int maxHits;
        public int       hitsRemaining;
        public boolean   readyToDestroy = false;
        public long      lastHitMs      = 0L;
        public RingHitData(int ringIndex, int maxHits) {
            this.ringIndex     = ringIndex;
            this.maxHits       = maxHits;
            this.hitsRemaining = maxHits;
        }
    }

    public static final class CenterHitData {
        public static final int MAX_HITS = 200;
        public int     hitsRemaining = MAX_HITS;
        public boolean readyToDestroy = false;
        public long    lastHitMs     = 0L;
    }
```

- [ ] **Step 1.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.3 — Commit**

```bash
git add src/com/odyssey/ShipData.java
git commit -m "feat(maze): add RingHitData and CenterHitData inner classes to ShipData"
```

---

## Task 2 — EnergyContactListener: ring and center hit detection

**Files:**
- Modify: `src/com/odyssey/physics/EnergyContactListener.java`

### Background
The contact listener already classifies bodies. We add detection for `RingHitData` and `CenterHitData`. When an intern (InternBallData or PELLET) contacts a ring or center, decrement HP. Flag `readyToDestroy` when HP hits 0. A 120ms cooldown per ring prevents one bounce from counting as multiple hits.

- [ ] **Step 2.1 — Add ring/center hit detection at START of beginContact()**

At the very beginning of `beginContact()`, after the sensor check (line ~27), add:

```java
        // ---- Ring and center hit detection ----
        boolean aIsRing   = bodyA.getUserData() instanceof ShipData.RingHitData;
        boolean bIsRing   = bodyB.getUserData() instanceof ShipData.RingHitData;
        boolean aIsCenter = bodyA.getUserData() instanceof ShipData.CenterHitData;
        boolean bIsCenter = bodyB.getUserData() instanceof ShipData.CenterHitData;

        if ((aIsRing || bIsRing || aIsCenter || bIsCenter)) {
            boolean _aInt = bodyA.getUserData() instanceof ShipData.InternBallData
                         || "PELLET".equals(bodyA.getUserData());
            boolean _bInt = bodyB.getUserData() instanceof ShipData.InternBallData
                         || "PELLET".equals(bodyB.getUserData());
            if (_aInt || _bInt) {
                long _now = System.currentTimeMillis();
                if (aIsRing) {
                    ShipData.RingHitData _rhd = (ShipData.RingHitData) bodyA.getUserData();
                    if (!_rhd.readyToDestroy && _now - _rhd.lastHitMs > 120L) {
                        _rhd.lastHitMs = _now;
                        if (--_rhd.hitsRemaining <= 0) _rhd.readyToDestroy = true;
                    }
                }
                if (bIsRing) {
                    ShipData.RingHitData _rhd = (ShipData.RingHitData) bodyB.getUserData();
                    if (!_rhd.readyToDestroy && _now - _rhd.lastHitMs > 120L) {
                        _rhd.lastHitMs = _now;
                        if (--_rhd.hitsRemaining <= 0) _rhd.readyToDestroy = true;
                    }
                }
                if (aIsCenter) {
                    ShipData.CenterHitData _chd = (ShipData.CenterHitData) bodyA.getUserData();
                    if (!_chd.readyToDestroy && _now - _chd.lastHitMs > 80L) {
                        _chd.lastHitMs = _now;
                        if (--_chd.hitsRemaining <= 0) _chd.readyToDestroy = true;
                    }
                }
                if (bIsCenter) {
                    ShipData.CenterHitData _chd = (ShipData.CenterHitData) bodyB.getUserData();
                    if (!_chd.readyToDestroy && _now - _chd.lastHitMs > 80L) {
                        _chd.lastHitMs = _now;
                        if (--_chd.hitsRemaining <= 0) _chd.readyToDestroy = true;
                    }
                }
            }
            return;  // ring/center contacts don't generate SP/energy
        }
```

- [ ] **Step 2.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2.3 — Commit**

```bash
git add src/com/odyssey/physics/EnergyContactListener.java
git commit -m "feat(maze): ring and center hit detection in EnergyContactListener"
```

---

## Task 3 — EngineeringLabScreen: spawn rings and center

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
Rings are static `ChainShape.createLoop()` bodies positioned at concentric radii. They are spawned at screen start (or after each planet). The center is a static `CircleShape` body. Both live in arrays: `rings[]` (Body[3]) and `centerBody` (Body).

- [ ] **Step 3.1 — Add ring/center body fields**

Find the `private final Array<Body> bumpers` declaration (line ~338). Add after it:

```java
    // Maze rings — 3 concentric ring bodies + center target
    private final Body[]   rings       = new Body[3];
    private Body           centerBody  = null;
    private static final float[] RING_RADII   = {2.0f, 1.3f, 0.7f};
    private static final int[]   RING_MAX_HITS = {30, 50, 80};
```

- [ ] **Step 3.2 — Add spawnRings() method**

Add after `spawnCentrifugeBumper()` (around line 2077):

```java
    private void spawnRings() {
        // Spawn 3 concentric ring bodies and center target
        for (int i = 0; i < 3; i++) {
            if (rings[i] != null) { world.destroyBody(rings[i]); rings[i] = null; }
            float r = RING_RADII[i];
            int segments = 36;
            float[] verts = new float[segments * 2];
            for (int s = 0; s < segments; s++) {
                float ang = s * com.badlogic.gdx.math.MathUtils.PI2 / segments;
                verts[s * 2]     = CENTRIFUGE_CX + com.badlogic.gdx.math.MathUtils.cos(ang) * r;
                verts[s * 2 + 1] = CENTRIFUGE_CY + com.badlogic.gdx.math.MathUtils.sin(ang) * r;
            }
            com.badlogic.gdx.physics.box2d.ChainShape chain = new com.badlogic.gdx.physics.box2d.ChainShape();
            chain.createLoop(verts);
            com.badlogic.gdx.physics.box2d.BodyDef bd = new com.badlogic.gdx.physics.box2d.BodyDef();
            bd.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody;
            com.badlogic.gdx.physics.box2d.FixtureDef fd = new com.badlogic.gdx.physics.box2d.FixtureDef();
            fd.shape       = chain;
            fd.restitution = 1.20f;
            fd.friction    = 0f;
            Body b = world.createBody(bd);
            b.createFixture(fd);
            b.setUserData(new ShipData.RingHitData(i, RING_MAX_HITS[i]));
            chain.dispose();
            rings[i] = b;
        }
        // Center target
        if (centerBody != null) { world.destroyBody(centerBody); centerBody = null; }
        com.badlogic.gdx.physics.box2d.BodyDef cbd = new com.badlogic.gdx.physics.box2d.BodyDef();
        cbd.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody;
        com.badlogic.gdx.physics.box2d.CircleShape cs = new com.badlogic.gdx.physics.box2d.CircleShape();
        cs.setRadius(0.30f);
        cs.setPosition(new com.badlogic.gdx.math.Vector2(CENTRIFUGE_CX, CENTRIFUGE_CY));
        com.badlogic.gdx.physics.box2d.FixtureDef cfd = new com.badlogic.gdx.physics.box2d.FixtureDef();
        cfd.shape = cs; cfd.restitution = 1.10f; cfd.friction = 0f;
        centerBody = world.createBody(cbd);
        centerBody.createFixture(cfd);
        centerBody.setUserData(new ShipData.CenterHitData());
        cs.dispose();
    }
```

- [ ] **Step 3.3 — Call spawnRings() from show()**

Find `show()` in `EngineeringLabScreen`. After `restoreState()` is called (or equivalent early init), add:

```java
        spawnRings();
```

- [ ] **Step 3.4 — Clean up rings in the two clearLab sites**

Find the two body-destruction blocks (lines ~7047 and ~7146) that currently start with bumpers cleanup. Before the bumpers loop in each, add:

```java
        for (int _ri = 0; _ri < 3; _ri++) {
            if (rings[_ri] != null) { world.destroyBody(rings[_ri]); rings[_ri] = null; }
        }
        if (centerBody != null) { world.destroyBody(centerBody); centerBody = null; }
```

- [ ] **Step 3.5 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.6 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(maze): spawnRings() creates 3 concentric ring bodies + center target"
```

---

## Task 4 — EngineeringLabScreen: ring destruction in stepPhysics

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
After `world.step()` it's safe to destroy bodies. Iterate `rings[]`, check `readyToDestroy`, destroy and null the slot. Same for `centerBody`. When center is destroyed, call `triggerPlanetWin()`.

- [ ] **Step 4.1 — Add ring/center destruction at end of stepPhysics()**

In `stepPhysics()`, find the existing curling body settle loop (around line 8369). After that block, add:

```java
        // ---- Ring destruction ----
        for (int _ri = 0; _ri < 3; _ri++) {
            if (rings[_ri] == null) continue;
            Object _ud = rings[_ri].getUserData();
            if (_ud instanceof ShipData.RingHitData
                    && ((ShipData.RingHitData) _ud).readyToDestroy) {
                world.destroyBody(rings[_ri]);
                rings[_ri] = null;
                SoundManager.get().playMilestone();
                triggerShake(4f, 0.08f);
                showCeleb("RING DESTROYED", "Layer " + (_ri + 1) + " cleared!");
            }
        }
        // ---- Center destruction → planet win ----
        if (centerBody != null && centerBody.getUserData() instanceof ShipData.CenterHitData) {
            if (((ShipData.CenterHitData) centerBody.getUserData()).readyToDestroy) {
                world.destroyBody(centerBody);
                centerBody = null;
                triggerPlanetWin();
            }
        }
```

- [ ] **Step 4.2 — Add triggerPlanetWin() method**

Add near the end of the class (before the final `}`):

```java
    private void triggerPlanetWin() {
        ShipData sd = ShipData.get();
        sd.markArrival(0f, 0f);
        sd.claimArrivalReward();
        SoundManager.get().playMilestone();
        triggerShake(6f, 0.12f);
        game.transitionTo(GameState.NOVA_TERRA_ARRIVAL);
    }
```

- [ ] **Step 4.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.4 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(maze): ring and center destruction in stepPhysics; triggerPlanetWin() transitions to arrival"
```

---

## Task 5 — EngineeringLabScreen: draw rings

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
Draw each ring as a circle outline with ShapeRenderer. Color shifts from cyan (full HP) to red (low HP). A brief white pulse on hit (read `lastHitMs`). Draw center as a glowing filled circle with hit counter text.

- [ ] **Step 5.1 — Add drawRings() method**

Add near other draw helpers (e.g., after `drawHarvestGlows()`):

```java
    private void drawRings() {
        long _now = System.currentTimeMillis();
        batch.end();

        Gdx.gl.glLineWidth(3f);
        shapeR.setProjectionMatrix(renderCam.combined);
        shapeR.begin(ShapeRenderer.ShapeType.Line);

        for (int _ri = 0; _ri < 3; _ri++) {
            if (rings[_ri] == null) continue;
            ShipData.RingHitData _rhd = (ShipData.RingHitData) rings[_ri].getUserData();
            float _frac  = Math.max(0f, (float) _rhd.hitsRemaining / _rhd.maxHits);
            float _hitAge = Math.min(1f, (float)(_now - _rhd.lastHitMs) / 300f);
            // Color: cyan at full HP → orange → red at low HP, white flash on hit
            float _r = _frac < 0.5f ? 1f : 2f - _frac * 2f;
            float _g = _frac < 0.5f ? _frac * 2f : 1f;
            float _pulse = 1f - (1f - _hitAge) * 0.8f;  // white flash fades to base color
            shapeR.setColor(
                Math.min(1f, _r + (1f - _pulse)),
                Math.min(1f, _g + (1f - _pulse)),
                Math.min(1f, 0.8f + (1f - _pulse)),
                0.85f);
            shapeR.circle(CENTRIFUGE_CX * PPM, CENTRIFUGE_CY * PPM, RING_RADII[_ri] * PPM, 48);
        }

        // Center target
        if (centerBody != null) {
            shapeR.end();
            shapeR.begin(ShapeRenderer.ShapeType.Filled);
            ShipData.CenterHitData _chd = (ShipData.CenterHitData) centerBody.getUserData();
            float _cfrac = Math.max(0f, (float) _chd.hitsRemaining / ShipData.CenterHitData.MAX_HITS);
            float _chitAge = Math.min(1f, (float)(_now - _chd.lastHitMs) / 200f);
            float _pulse2 = 0.55f + 0.45f * MathUtils.sin(animTime * 5f);
            shapeR.setColor(
                Math.min(1f, 1f - _cfrac * 0.5f + (1f - _chitAge) * 0.5f),
                Math.min(1f, _cfrac + (1f - _chitAge) * 0.3f),
                Math.min(1f, _cfrac * 0.5f + 0.5f),
                0.85f * _pulse2);
            shapeR.circle(CENTRIFUGE_CX * PPM, CENTRIFUGE_CY * PPM, 0.30f * PPM, 24);
        }

        shapeR.end();
        Gdx.gl.glLineWidth(1f);
        batch.begin();

        // Center hit counter text
        if (centerBody != null) {
            ShipData.CenterHitData _chd = (ShipData.CenterHitData) centerBody.getUserData();
            floatFont.getData().setScale(0.80f);
            floatFont.setColor(1f, 0.85f, 0.20f, 0.90f);
            String _txt = String.valueOf(_chd.hitsRemaining);
            floatLayout.setText(floatFont, _txt);
            floatFont.draw(batch, _txt,
                CENTRIFUGE_CX * PPM - floatLayout.width * 0.5f,
                CENTRIFUGE_CY * PPM + floatLayout.height * 0.5f);
            floatFont.getData().setScale(1f);
        }
    }
```

- [ ] **Step 5.2 — Call drawRings() in render()**

Find where `drawBumpers()` is called in `render()` (line ~3949). Add immediately after:

```java
        drawRings();
```

- [ ] **Step 5.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5.4 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(maze): drawRings() — ring outlines with HP color, center hit counter"
```

---

## Task 6 — EngineeringLabScreen: hide structure buttons

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
`btnBumper` and `btnGravityWell` are in a 3-cell `tileRow` with `btnAdd`. We hide them and expand `btnAdd` to fill the row. Don't delete the buttons (they still have listeners that won't fire when invisible).

- [ ] **Step 6.1 — Hide bumper and gravity buttons in show()**

In `show()`, after the existing setup calls, add:

```java
        // Maze mode — structure placement replaced by ring mechanic
        if (btnBumper     != null) btnBumper.setVisible(false);
        if (btnGravityWell != null) btnGravityWell.setVisible(false);
```

- [ ] **Step 6.2 — Hide launch buttons**

Also in `show()`, ensure launch buttons are hidden (they may already be conditionally shown — just force hide):

```java
        if (btnFlight    != null) btnFlight.setVisible(false);
        if (btnJumpReady != null) btnJumpReady.setVisible(false);
```

- [ ] **Step 6.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6.4 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(maze): hide bumper/gravity/launch buttons — maze mode replaces structure placement"
```

---

## Task 7 — Install and verify

- [ ] **Step 7.1 — Full build and install**

```bash
./gradlew android:copyAndroidNatives android:installDebug 2>&1 | tail -6
```
Expected: `Installed on 1 device.`

- [ ] **Step 7.2 — Verification checklist**

Open Engineering Lab and verify:
- [ ] Three ring outlines visible inside centrifuge drum (cyan circles)
- [ ] Slingshot an orb into drum → orb bounces off outermost ring
- [ ] After ~30 bounces off Ring 0 → Ring 0 disappears with shake + celebratory toast
- [ ] Orb now reaches Ring 1 (middle ring)
- [ ] After Ring 1 cleared → Ring 2 visible
- [ ] After Ring 2 cleared → orb reaches center (small glowing circle)
- [ ] Center hit counter decrements from 200 with each orb contact
- [ ] At 0 → transition to arrival screen (planet conquered)
- [ ] No crash during extended play
- [ ] Bumper and gravity buttons hidden
- [ ] Launch button hidden

- [ ] **Step 7.3 — Commit any fixes**

```bash
git add -p
git commit -m "fix(maze): <describe fix>"
```

---

## Notes for Implementer

- `CENTRIFUGE_CX = 4.0f`, `CENTRIFUGE_CY = 8.5f`, `PPM = 60f` — static constants at top of EngineeringLabScreen (lines ~47–49).
- `ChainShape.createLoop(float[] vertices)` takes flat `[x0, y0, x1, y1, ...]` array in WORLD coordinates. The loop is closed automatically.
- `shapeR` is the ShapeRenderer field (line 250). `animTime` is animation accumulator (line 398). `floatFont` is the font used for floating numbers. `floatLayout` is the GlyphLayout for it.
- `showCeleb(String title, String body)` shows a brief toast notification — search for existing usages to find the exact signature.
- `SoundManager.get().playMilestone()` plays the milestone sound.
- `triggerShake(float magnitude, float duration)` triggers screen shake.
- `markArrival(float routeDistance, float energySpent)` — pass `0f, 0f` since there's no flight.
- The ring bodies at radii 2.0, 1.3, 0.7 use `restitution = 1.20f` to keep orbs bouncing energetically. If orbs lose energy too fast, increase to 1.30f.
- `rings[]` is `Body[3]` — a fixed-size array, not a LibGDX `Array<Body>`. Access with `rings[i]`, check for null before use.
- The center body at radius 0.30f uses `CircleShape` positioned at `(CENTRIFUGE_CX, CENTRIFUGE_CY)` via `cs.setPosition()`.
