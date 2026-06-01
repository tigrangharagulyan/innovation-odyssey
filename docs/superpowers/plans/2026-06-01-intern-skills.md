# Intern Skills System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the RPM-triggered milestone perk system with an intern skill system — 6 skills chosen before launch, applied to intern physics and behavior, displayed in the perk row.

**Architecture:** Three layers: (1) ShipData adds `InternSkill` enum + `InternBallData` userdata class replacing `"INTERN_NORMAL"` String; (2) `spawnBall` reads `selectedInternSkill` and applies physics per skill; (3) UI replaces the perkStrip table with 6 skill buttons. Milestone trigger code removed from stepPhysics; structure unlocks switch from sectorReached to arrivalsCompleted.

**Tech Stack:** LibGDX 1.12.1, Box2D, Scene2D, existing ShapeRenderer + SpriteBatch pipeline.

---

## Files Modified

| File | Changes |
|------|---------|
| `src/com/odyssey/ShipData.java` | Add `InternSkill` enum, `InternBallData` class, `selectedInternSkill` field, remove `savedMilestoneAchieved` |
| `src/com/odyssey/physics/EnergyContactListener.java` | Update intern detection to use `InternBallData`, add ELECTRIC/GIANT bonuses |
| `src/com/odyssey/screen/EngineeringLabScreen.java` | internCap→3, structure unlocks, spawnBall with skill, skill UI row, drawInterns colors, TRIPLE spawn, remove milestone triggers |

---

## Task 1 — ShipData: InternSkill enum + InternBallData class

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

### Background
Currently interns use `"INTERN_NORMAL"` or `"INTERN_CYBER"` String as body userData. We replace this with `InternBallData` which carries the skill. The contact listener currently identifies interns by checking `instanceof String && startsWith("INTERN")` — this must change once we switch userdata type.

- [ ] **Step 1.1 — Add InternSkill enum inside ShipData class (after BumperHitData)**

Find the closing `}` of `AttractorHitData` (around line 28). Add immediately after:

```java
    public enum InternSkill {
        NONE,     // standard blue
        SPEEDY,   // yellow  — 1.8× restitution, 0.5× density, 1.5× kick speed
        TRIPLE,   // green   — spawns 3 small interns
        GIANT,    // red     — 2× radius, 3× density, 2× energy per hit
        ELECTRIC, // cyan    — +1 diamond per bumper/wall contact
        HEAVY     // orange  — 3× density, 1.2× radius
    }

    public static final class InternBallData {
        public InternSkill skill;
        public long        lastHitMs = 0L;
        public InternBallData(InternSkill s) { this.skill = s; }
    }
```

- [ ] **Step 1.2 — Add selectedInternSkill transient field**

Find the `// Lives & monetisation` block (around line 189). Add before `public int lives`:

```java
    // Intern skill — selected before launch, transient (not persisted)
    public InternSkill selectedInternSkill = InternSkill.NONE;
```

- [ ] **Step 1.3 — Remove savedMilestoneAchieved from field declarations**

Find and delete this field declaration (around line 121):
```java
    public boolean[] savedMilestoneAchieved      = new boolean[6];
```

Replace with nothing (delete line).

- [ ] **Step 1.4 — Remove savedMilestoneAchieved from reset()**

Find in `reset()`:
```java
        for (int i = 0; i < savedMilestoneAchieved.length; i++) savedMilestoneAchieved[i] = false;
```
Delete that line.

- [ ] **Step 1.5 — Remove savedMilestoneAchieved from save()**

In `save()`, find and delete these lines:
```java
        StringBuilder msb = new StringBuilder();
        for (int i = 0; i < savedMilestoneAchieved.length; i++) {
            if (i > 0) msb.append(',');
            msb.append(savedMilestoneAchieved[i] ? 1 : 0);
        }
        p.putString("milestones", msb.toString());
```

- [ ] **Step 1.6 — Remove savedMilestoneAchieved from load()**

In `load()`, find and delete:
```java
        String ms = p.getString("milestones", "");
        if (!ms.isEmpty()) {
            String[] parts = ms.split(",");
            for (int i = 0; i < parts.length && i < savedMilestoneAchieved.length; i++)
                savedMilestoneAchieved[i] = parts[i].equals("1");
        }
```

- [ ] **Step 1.7 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL` (there will be errors in EngineeringLabScreen referencing `savedMilestoneAchieved` — that's OK, fix in later tasks)

If errors are ONLY about `savedMilestoneAchieved` in EngineeringLabScreen, continue. If errors are in ShipData itself, fix first.

- [ ] **Step 1.8 — Commit**

```bash
git add src/com/odyssey/ShipData.java
git commit -m "feat(skills): add InternSkill enum, InternBallData class, selectedInternSkill field; remove savedMilestoneAchieved"
```

---

## Task 2 — EnergyContactListener: InternBallData detection + skill bonuses

**Files:**
- Modify: `src/com/odyssey/physics/EnergyContactListener.java`

### Background
Currently intern detection is:
```java
boolean aIsIntern = bodyA.getUserData() instanceof String
                    && (((String) bodyA.getUserData()).startsWith("INTERN")
                        || "PELLET".equals(bodyA.getUserData()));
```
After Task 1, intern bodies carry `InternBallData` as userData. Pellets still use String `"PELLET"`. Update detection and add ELECTRIC/GIANT bonuses.

- [ ] **Step 2.1 — Update intern detection**

Find lines 33-38 (the aIsIntern / bIsIntern declarations). Replace with:

```java
        boolean aIsIntern = (bodyA.getUserData() instanceof ShipData.InternBallData)
                         || ("PELLET".equals(bodyA.getUserData()));
        boolean bIsIntern = (bodyB.getUserData() instanceof ShipData.InternBallData)
                         || ("PELLET".equals(bodyB.getUserData()));
```

- [ ] **Step 2.2 — Add ELECTRIC diamond bonus in the wall/bumper branches**

After the existing `bumperHit` branch body (around line 153, after the `pendingBumperSounds++` block), locate where `addCrystals(bonus)` is called. Find the EXISTING `} else if (aIsIntern || bIsIntern) {` wall branch (line ~186). Inside that branch, AFTER the existing `sd.addCrystals(wallGain)` block, add:

```java
            // ELECTRIC intern: +1 diamond per contact
            ShipData.InternBallData _eabd = (bodyA.getUserData() instanceof ShipData.InternBallData)
                ? (ShipData.InternBallData) bodyA.getUserData() : null;
            ShipData.InternBallData _ebbd = (bodyB.getUserData() instanceof ShipData.InternBallData)
                ? (ShipData.InternBallData) bodyB.getUserData() : null;
            ShipData.InternSkill _skill = _eabd != null ? _eabd.skill
                                        : _ebbd != null ? _ebbd.skill : ShipData.InternSkill.NONE;
            if (_skill == ShipData.InternSkill.ELECTRIC) {
                sd.diamonds += 1;
                sd.pendingContactEvents.add(new float[]{
                    (_eabd != null ? bodyA.getPosition().x : bodyB.getPosition().x),
                    (_eabd != null ? bodyA.getPosition().y : bodyB.getPosition().y),
                    1f, 5f
                });
            }
```

Also add ELECTRIC bonus inside the `bumperHit` branch, and GIANT energy multiplier. Find the `bumperHit` branch:

```java
        } else if (bumperHit) {
            float bonus     = attractorHit ? SPARK_GRAVITY * sd.gravityMult : sd.bumperSparkValue * sd.bumperMult;
```

Replace with:

```java
        } else if (bumperHit) {
            // GIANT intern doubles all energy awards
            ShipData.InternBallData _gabd = (bodyA.getUserData() instanceof ShipData.InternBallData)
                ? (ShipData.InternBallData) bodyA.getUserData() : null;
            ShipData.InternBallData _gbbd = (bodyB.getUserData() instanceof ShipData.InternBallData)
                ? (ShipData.InternBallData) bodyB.getUserData() : null;
            boolean _isGiant = (_gabd != null && _gabd.skill == ShipData.InternSkill.GIANT)
                             || (_gbbd != null && _gbbd.skill == ShipData.InternSkill.GIANT);
            boolean _isElectric = (_gabd != null && _gabd.skill == ShipData.InternSkill.ELECTRIC)
                                || (_gbbd != null && _gbbd.skill == ShipData.InternSkill.ELECTRIC);
            float _giantMult = _isGiant ? 2f : 1f;
            float bonus = (attractorHit ? SPARK_GRAVITY * sd.gravityMult : sd.bumperSparkValue * sd.bumperMult) * _giantMult;
            if (_isElectric) sd.diamonds += 1;
```

- [ ] **Step 2.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL` (EngineeringLabScreen errors still OK if present)

- [ ] **Step 2.4 — Commit**

```bash
git add src/com/odyssey/physics/EnergyContactListener.java
git commit -m "feat(skills): update intern detection to InternBallData; ELECTRIC +1 diamond, GIANT 2x energy"
```

---

## Task 3 — EngineeringLabScreen: internCap + structure unlocks

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
`internCap()` currently scales 4→6→9→12 based on sectorReached. Replace with flat 3. `maxBumpersAllowed()` and `gravityUnlocked()` currently gate on sectorReached — change to use `arrivalsCompleted` (0 = no planets visited, 1 = arrived at first planet, etc.).

- [ ] **Step 3.1 — Replace internCap() with flat cap 3**

Find `internCap()` at line ~8679:
```java
    private int internCap() {
        int sr = ShipData.get().sectorReached;
        if (isEmberIV()) { ... }
        if (isFrostheim()) { ... }
        if (sr >= 2) return 12;
        if (sr >= 1) return 9;
        if (sr >= 0) return 6;
        return 4;
    }
```

Replace the entire method body:
```java
    private int internCap() {
        if (isEmberIV() || isFrostheim()) return 3;
        return 3;
    }
```

- [ ] **Step 3.2 — Update maxBumpersAllowed() to use arrivalsCompleted**

Find `maxBumpersAllowed()` at line ~8699:
```java
    private int maxBumpersAllowed() {
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 5;
        if (sr >= 1) return 3;
        if (sr >= 0) return 2;
        return 0;
    }
```

Replace with:
```java
    private int maxBumpersAllowed() {
        // Bumpers always available (arrival 0 = first planet = Solara)
        return MAX_BUMPERS;
    }
```

- [ ] **Step 3.3 — Update maxGravityAllowed() to use arrivalsCompleted**

Find `maxGravityAllowed()`:
```java
    private int maxGravityAllowed() {
        int sr = ShipData.get().sectorReached;
        if (isEmberIV()) { ... }
        if (sr >= 2) return 3;
        if (sr >= 1) return 2;
        return 0;
    }
```

Replace:
```java
    private int maxGravityAllowed() {
        if (isEmberIV() || isFrostheim()) return 3;
        // Gravity wells unlock after first planet arrival
        return ShipData.get().arrivalsCompleted >= 1 ? 3 : 0;
    }
```

- [ ] **Step 3.4 — Update gravityUnlocked()**

Find `gravityUnlocked()`:
```java
    private boolean gravityUnlocked() {
        if (isFrostheim()) return true;
        if (isEmberIV())   return ShipData.get().sectorReached >= 0;
        return ShipData.get().sectorReached >= 1;
    }
```

Replace:
```java
    private boolean gravityUnlocked() {
        if (isEmberIV() || isFrostheim()) return true;
        return ShipData.get().arrivalsCompleted >= 1;
    }
```

- [ ] **Step 3.5 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```
Expected: compile errors only about `savedMilestoneAchieved` / `milestoneAchieved`. Not about the methods we just changed.

- [ ] **Step 3.6 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(skills): internCap=3, bumpers always available, gravity unlocks at arrival 1"
```

---

## Task 4 — EngineeringLabScreen: spawnBall with skill

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
`spawnBall(float x, float y)` currently sets `"INTERN_NORMAL"` as body userData and applies constant physics. We add an overload that accepts `InternSkill` and applies per-skill physics. The no-arg version delegates to the skill version using `ShipData.get().selectedInternSkill`.

- [ ] **Step 4.1 — Add skill-aware spawnBall overload**

Find the existing `spawnBall(float x, float y)` method (line ~2046). Replace it entirely with:

```java
    private void spawnBall(float x, float y) {
        spawnBall(x, y, ShipData.get().selectedInternSkill);
    }

    private void spawnBall(float x, float y, ShipData.InternSkill skill) {
        if (balls.size >= internCap()) return;
        boolean fh = isFrostheim();

        // Physics params per skill
        float radius, density, restitution, kickSpd;
        switch (skill) {
            case SPEEDY:
                radius = BALL_RADIUS; density = 0.5f; restitution = 1.62f; kickSpd = 5.0f;
                break;
            case GIANT:
                radius = BALL_RADIUS * 2f; density = 3.0f; restitution = currentBallRestitution; kickSpd = 2.5f;
                break;
            case HEAVY:
                radius = BALL_RADIUS * 1.2f; density = 3.0f; restitution = 0.85f; kickSpd = 4.5f;
                break;
            default: // NONE, ELECTRIC, TRIPLE sub-balls
                radius = BALL_RADIUS; density = BALL_DENSITY; restitution = currentBallRestitution;
                kickSpd = fh ? 4.5f : 3.0f;
                break;
        }

        BodyDef bd = new BodyDef();
        bd.type           = BodyDef.BodyType.DynamicBody;
        bd.position.set(x, y);
        bd.linearDamping  = fh ? frostheimBallDamping : 0f;
        bd.angularDamping = fh ? frostheimBallDamping : 0f;

        CircleShape circle = new CircleShape();
        circle.setRadius(isEmberIV() ? EMBER_BALL_RADIUS : radius);

        FixtureDef fd  = new FixtureDef();
        fd.shape       = circle;
        fd.density     = density;
        fd.restitution = restitution;
        fd.friction    = 0.2f;
        Body body = world.createBody(bd);
        body.setBullet(true);
        body.createFixture(fd);
        body.setUserData(new ShipData.InternBallData(skill));
        float kickAngle = MathUtils.random(MathUtils.PI2);
        body.setLinearVelocity(MathUtils.cos(kickAngle) * kickSpd, MathUtils.sin(kickAngle) * kickSpd);
        circle.dispose();
        balls.add(body);
        ballLastHitMs.put(body, System.currentTimeMillis());
    }
```

- [ ] **Step 4.2 — Fix existing code that checks for "INTERN_NORMAL" / "INTERN_CYBER" string userData**

Search the file for `"INTERN_NORMAL"` and `"INTERN_CYBER"` references and update them:

```bash
grep -n '"INTERN_NORMAL"\|"INTERN_CYBER"\|INTERN_NORMAL\|INTERN_CYBER' src/com/odyssey/screen/EngineeringLabScreen.java
```

For each occurrence that SETS userData (`setUserData("INTERN_NORMAL")`): the new `spawnBall` handles this — delete those lines if any remain outside of spawnBall.

For each occurrence that READS userData (checks equality): replace with `instanceof ShipData.InternBallData` check. Example:
- `"INTERN_NORMAL".equals(body.getUserData())` → `body.getUserData() instanceof ShipData.InternBallData`
- `"INTERN_CYBER".equals(body.getUserData())` → `body.getUserData() instanceof ShipData.InternBallData`

- [ ] **Step 4.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```
Expected: remaining errors only about `milestoneAchieved` / `savedMilestoneAchieved`.

- [ ] **Step 4.4 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(skills): spawnBall(x,y,skill) — applies skill physics; InternBallData replaces INTERN_NORMAL string"
```

---

## Task 5 — EngineeringLabScreen: TRIPLE spawn in flying intern update

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
The flying intern update (in render loop) calls `spawnBall(flyingInternWX, flyingInternWY)` when the sprite crosses the drum boundary. For TRIPLE skill, spawn 3 small balls at slightly offset positions instead of 1.

- [ ] **Step 5.1 — Replace flying intern spawn with skill-aware spawn**

Find in the render loop (around line 3940 — the flying intern update block):
```java
            if (_idx * _idx + _idy * _idy < (CENTRIFUGE_R * 0.80f) * (CENTRIFUGE_R * 0.80f)) {
                spawnBall(flyingInternWX, flyingInternWY);
                // Override random kick with entry velocity
                if (!balls.isEmpty()) {
                    balls.get(balls.size - 1).setLinearVelocity(flyingInternVX * 0.6f, flyingInternVY * 0.6f);
                }
                internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                internAddedTimer    = INTERN_ADDED_HOLD;
                hireIdleTimer       = 0f;
                SoundManager.get().playHire();
                triggerShake(2f, 0.06f);
                flyingInternActive = false;
```

Replace with:
```java
            if (_idx * _idx + _idy * _idy < (CENTRIFUGE_R * 0.80f) * (CENTRIFUGE_R * 0.80f)) {
                ShipData.InternSkill _sk = ShipData.get().selectedInternSkill;
                if (_sk == ShipData.InternSkill.TRIPLE) {
                    // Spawn up to min(3, cap-balls.size) small interns in spread directions
                    int _toSpawn = Math.min(3, internCap() - balls.size);
                    float[] _angles = {0f, (float)(Math.PI * 2f/3f), (float)(Math.PI * 4f/3f)};
                    for (int _ti = 0; _ti < _toSpawn; _ti++) {
                        float _ox = flyingInternWX + MathUtils.cos(_angles[_ti]) * 0.15f;
                        float _oy = flyingInternWY + MathUtils.sin(_angles[_ti]) * 0.15f;
                        spawnBall(_ox, _oy, ShipData.InternSkill.NONE);
                        if (!balls.isEmpty()) {
                            float _ka = _angles[_ti] + (float)Math.PI;
                            balls.get(balls.size - 1).setLinearVelocity(
                                MathUtils.cos(_ka) * 4.5f, MathUtils.sin(_ka) * 4.5f);
                        }
                    }
                } else {
                    spawnBall(flyingInternWX, flyingInternWY, _sk);
                    if (!balls.isEmpty()) {
                        balls.get(balls.size - 1).setLinearVelocity(flyingInternVX * 0.6f, flyingInternVY * 0.6f);
                    }
                }
                internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                internAddedTimer    = INTERN_ADDED_HOLD;
                hireIdleTimer       = 0f;
                SoundManager.get().playHire();
                triggerShake(2f, 0.06f);
                flyingInternActive = false;
```

- [ ] **Step 5.2 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```

- [ ] **Step 5.3 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(skills): TRIPLE spawn — 3 interns on drum entry; skill-aware flying intern update"
```

---

## Task 6 — EngineeringLabScreen: drawInterns skill colors

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
`drawInterns()` currently draws all interns in a fixed blue-cyan color. We need to read the skill from `InternBallData` and set a skill-appropriate color.

- [ ] **Step 6.1 — Add skill color helper method**

Add before `drawInterns()` (line ~6905):

```java
    private void setInternSkillColor(ShipData.InternSkill skill, float alpha) {
        switch (skill) {
            case SPEEDY:   batch.setColor(1.0f, 0.90f, 0.15f, alpha); break;
            case TRIPLE:   batch.setColor(0.25f, 1.0f, 0.40f, alpha); break;
            case GIANT:    batch.setColor(1.0f, 0.25f, 0.25f, alpha); break;
            case ELECTRIC: batch.setColor(0.20f, 0.95f, 1.0f, alpha); break;
            case HEAVY:    batch.setColor(1.0f, 0.55f, 0.10f, alpha); break;
            default:       batch.setColor(0.35f, 0.80f, 1.0f, alpha); break; // NONE = blue
        }
    }
```

- [ ] **Step 6.2 — Use skill color in drawInterns loop**

Inside `drawInterns()` at line ~6960, the loop starts with:
```java
        for (int i = 0, n = balls.size; i < n; i++) {
            Body    body  = balls.get(i);
            Vector2 pos   = body.getPosition();
            Vector2 vel   = body.getLinearVelocity();
            boolean cyber = "INTERN_CYBER".equals(body.getUserData());
```

Replace with:
```java
        for (int i = 0, n = balls.size; i < n; i++) {
            Body    body  = balls.get(i);
            Vector2 pos   = body.getPosition();
            Vector2 vel   = body.getLinearVelocity();
            boolean cyber = false; // replaced by skill system
            ShipData.InternSkill _iSkill = (body.getUserData() instanceof ShipData.InternBallData)
                ? ((ShipData.InternBallData) body.getUserData()).skill
                : ShipData.InternSkill.NONE;
```

Then find the two `batch.setColor` calls that used `cyber` (lines ~6988 and ~6995):
```java
                if (cyber) batch.setColor(0.20f * glow, 0.75f * glow, 1.00f * glow, 0.65f);
                else       batch.setColor(1.00f * glow, 0.55f * glow, 0.10f * glow, 0.65f);
```
and
```java
                if (cyber) batch.setColor(0.70f, 0.95f, 1.00f, 0.90f);
                else       batch.setColor(1.00f, 0.88f, 0.50f, 0.90f);
```

Replace BOTH with `setInternSkillColor(_iSkill, 0.90f)`. The method sets color based on skill — same call works for glow and core (slight alpha difference is acceptable).

- [ ] **Step 6.3 — Update flying intern slingshot ghost color**

In `drawPlacementPreview()` PLACE_INTERN branch (drawInterns method area), the flying intern preview (`flyingInternActive` block in `drawInterns()`) currently uses `0.35f, 0.80f, 1f`. Replace with `setInternSkillColor(ShipData.get().selectedInternSkill, 0.92f)`.

Also update the slingshot rubber band color in `drawPlacementPreview()` PLACE_INTERN branch to use the skill color. Find `shapeR.setColor(0.35f, 0.80f, 1f, 0.80f)` in that branch and replace with a skill-tinted color (same skill lookup).

- [ ] **Step 6.4 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```

- [ ] **Step 6.5 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(skills): intern color matches selected skill; flying ghost uses skill color"
```

---

## Task 7 — EngineeringLabScreen: skill selection UI row

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
The perkStrip (lines 2823–2878) is the existing 5-slot icon row above the stats bar. We replace it with 6 skill buttons. The new row uses the same `panel.add(skillStrip).growX()...` call at the same position. We add `skillButtons[]` field and a `selectedSkillBtn` reference for highlight tracking.

- [ ] **Step 7.1 — Add skill button field declarations**

Find the `private TextButton[] perkButtons;` declaration (line ~331). Add after it:
```java
    private TextButton[] skillButtons;      // skill selection row
    private int          selectedSkillIdx = 0;  // index into InternSkill.values()
```

- [ ] **Step 7.2 — Replace perkStrip construction with skill row**

Find the perkStrip block (lines 2823–2878):
```java
        // Perk strip — just above stats row, inside bottom panel
        Table perkStrip = new Table();
        perkStrip.setBackground(...);
        ...
        panel.add(perkStrip).growX().padBottom(3f).row();
```

Replace the ENTIRE perkStrip block (from `Table perkStrip = new Table()` to `panel.add(perkStrip)...row()`) with:

```java
        // Skill selection row — replaces perk strip
        Table skillStrip = new Table();
        skillStrip.setBackground(new NinePatchDrawable(game.skin.get("rounded_dark", NinePatch.class)));
        skillStrip.defaults().padTop(1f).padBottom(1f);

        String[] skillLabels = {"NONE", "SPD", "3×", "BIG", "⚡", "HVY"};
        ShipData.InternSkill[] skillValues = ShipData.InternSkill.values();
        skillButtons = new TextButton[skillLabels.length];

        TextButton.TextButtonStyle skillNorm = new TextButton.TextButtonStyle();
        skillNorm.font = game.skin.getFont("font");
        skillNorm.up   = game.skin.newDrawable("white", new Color(0.06f, 0.09f, 0.20f, 0.85f));
        skillNorm.down = game.skin.newDrawable("white", new Color(0.12f, 0.20f, 0.40f, 0.95f));
        skillNorm.over = skillNorm.down;
        skillNorm.fontColor = new Color(0.70f, 0.72f, 0.90f, 1f);

        TextButton.TextButtonStyle skillAct = new TextButton.TextButtonStyle();
        skillAct.font = game.skin.getFont("font");
        skillAct.up   = game.skin.newDrawable("white", new Color(0.10f, 0.30f, 0.65f, 0.95f));
        skillAct.down = skillAct.up;
        skillAct.over = skillAct.up;
        skillAct.fontColor = new Color(1f, 0.95f, 0.30f, 1f);

        for (int i = 0; i < skillLabels.length; i++) {
            final int idx = i;
            TextButton btn = new TextButton(skillLabels[i], i == 0 ? skillAct : skillNorm);
            btn.getLabel().setFontScale(0.62f);
            btn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor a) {
                    selectedSkillIdx = idx;
                    ShipData.get().selectedInternSkill = skillValues[idx];
                    for (int j = 0; j < skillButtons.length; j++)
                        skillButtons[j].setStyle(j == idx ? skillAct : skillNorm);
                }
            });
            skillButtons[i] = btn;
            skillStrip.add(btn).expandX().height(28f);
        }
        panel.add(skillStrip).growX().padBottom(3f).row();
```

- [ ] **Step 7.3 — Compile check**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|BUILD"
```
Expected: remaining errors about `milestoneAchieved` only.

- [ ] **Step 7.4 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(skills): replace perk strip with skill selection row — 6 skills, tap to select"
```

---

## Task 8 — EngineeringLabScreen: remove milestone trigger system

**Files:**
- Modify: `src/com/odyssey/screen/EngineeringLabScreen.java`

### Background
The `milestoneAchieved[]` array and MILESTONE_RPMS trigger code in `stepPhysics` gives physics multiplier boosts at certain RPMs. With the skill system, these are replaced by intern skills. Remove the trigger logic and the `milestoneAchieved` field. The `wallEnergyMult`, `bumperMult`, etc. in ShipData still exist but are no longer boosted by milestones — they retain their initial values from ShipData defaults.

- [ ] **Step 8.1 — Remove milestoneAchieved field declaration**

Find (line ~414):
```java
    private final boolean[] milestoneAchieved = new boolean[6];
```
Delete this line.

- [ ] **Step 8.2 — Remove MILESTONE_RPMS, MILESTONE_NAMES, MILESTONE_DESCS, EMBER_MILESTONE_RPMS constants**

Find (lines ~135-136):
```java
    private static final float[]  MILESTONE_RPMS  = {5.25f, 3.5f, 6.75f, 7.5f, 9.5f, 99f};
    private static final String[] MILESTONE_NAMES = {"Elastic Walls", "Speed Keep", "Wall ×3", "Hit ×2", "Bumper ×3", "-"};
```
And the MILESTONE_DESCS array nearby. Also find and delete:
```java
    private static final float[] EMBER_MILESTONE_RPMS = {5.5f, 6.5f, 7.5f};
```
Delete all four.

- [ ] **Step 8.3 — Remove milestone trigger code in stepPhysics**

In `stepPhysics()`, search for `milestoneAchieved` references and delete all if-blocks that set `milestoneAchieved[i] = true` and call `showCeleb(...)`. These are blocks like:
```java
        if (targetRPM >= MILESTONE_RPMS[0] && !milestoneAchieved[0]) {
            milestoneAchieved[0] = true;
            sd.wallEnergyMult += 1.0f;
            showCeleb("PERK UNLOCKED", ...);
        }
```
Delete all such blocks for all 6 milestones.

Also delete the sector-arrival milestone triggers (the `sr >= 2 && !milestoneAchieved[2]` blocks around line 3850):
```java
        if (sr >= 2 && !milestoneAchieved[2]) { milestoneAchieved[2] = true; ... }
        if (sr >= 2 && !milestoneAchieved[3]) { milestoneAchieved[3] = true; ... }
        if (sr >= 3 && !milestoneAchieved[5]) { milestoneAchieved[5] = true; ... }
```

- [ ] **Step 8.4 — Remove perk icon update calls and fields**

Remove the `updatePerkIcon(...)` calls in `updateUi()` (around lines 4132–4148). These reference `milestoneAchieved[i]`.

Remove the following field declarations (they referenced perk icon Images):
```java
    private com.badlogic.gdx.scenes.scene2d.ui.Image perkIconSpeed, perkIconElas, perkIconWall, perkIconColl, perkIconBump;
    private Label[] perkDescLabels;
```
And `lockSpeedImg`, `lockCollImg`, `lockWallImg`, `lockBoostImg`, `lockBumpImg` Image fields.

- [ ] **Step 8.5 — Remove perkButtons field and rightPerksTable if unused**

Find `private TextButton[] perkButtons;` (line ~331) — delete.
Find `private Label perksHeader;` and `rightPerksTable` field — delete if only used in perk construction.

- [ ] **Step 8.6 — Remove savedMilestoneAchieved references in snapshotState/restoreState**

Find in `snapshotState()`:
```java
        System.arraycopy(milestoneAchieved, 0, sd.savedMilestoneAchieved, 0, milestoneAchieved.length);
```
Delete.

Find in `restoreState()`:
```java
        System.arraycopy(sd.savedMilestoneAchieved, 0, milestoneAchieved, 0, milestoneAchieved.length);
```
Delete.

- [ ] **Step 8.7 — Compile check — must be clean**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL` with no errors.

If there are remaining references to removed fields, find and remove them:
```bash
grep -n "milestoneAchieved\|MILESTONE_RPMS\|MILESTONE_NAMES\|perkButtons\|perkDescLabels\|perkIconSpeed\|lockSpeedImg" src/com/odyssey/screen/EngineeringLabScreen.java
```

- [ ] **Step 8.8 — Commit**

```bash
git add src/com/odyssey/screen/EngineeringLabScreen.java
git commit -m "feat(skills): remove milestone trigger system, perk icons, savedMilestoneAchieved references"
```

---

## Task 9 — Run and verify

- [ ] **Step 9.1 — Full build**

```bash
./gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9.2 — Install on device**

```bash
./gradlew android:copyAndroidNatives android:installDebug 2>&1 | tail -6
```
Expected: `Installed on 1 device.`

- [ ] **Step 9.3 — Verification checklist**

Open Engineering Lab and verify:
- [ ] Skill row visible above stats bar: NONE, SPD, 3×, BIG, ⚡, HVY buttons
- [ ] Tap SPD → button highlights yellow, NONE deselects
- [ ] Slingshot an intern → flying orb is YELLOW (SPD color)
- [ ] Intern enters drum → yellow physics ball, bounces faster than default
- [ ] Select TRIPLE → slingshot → flying orb is GREEN → enters drum → 3 green interns spawn
- [ ] Select GIANT → slingshot → large red intern visible in drum
- [ ] Select ELECTRIC → intern bounces → diamonds increment on each wall/bumper contact
- [ ] Max 3 interns in drum — 4th hire button shows FULL / disabled
- [ ] Bumper button active without needing CP I
- [ ] Gravity well button: active after arriving at first planet (arrivalsCompleted ≥ 1)
- [ ] No crash after 2+ minutes of play (milestone trigger code gone)

- [ ] **Step 9.4 — Commit any fixes**

```bash
git add -p
git commit -m "fix(skills): <describe fix>"
```

---

## Notes for Implementer

- `ShipData.InternSkill.values()` returns array in declaration order: `[NONE, SPEEDY, TRIPLE, GIANT, ELECTRIC, HEAVY]` — matches `skillLabels[]` in Task 7.
- `BALL_RADIUS = 0.25f` (line 59), `BALL_DENSITY = 1.0f` (line 62), `MAX_BUMPERS = 5` (line 93). These are static constants at the top of EngineeringLabScreen.
- `currentBallRestitution` (line 415) is a float field that may change during play — use it as the base restitution for NONE/ELECTRIC skills.
- The `drawInterns()` loop iterates `balls` — a `Array<Body>`. Access elements with `.get(i)`, NOT `.items[i]` (Android ART ClassCastException).
- `CENTRIFUGE_RPM_BASE = 1.5f` (line 95) is used in the TRIPLE spawn's `internAddedNewSpeed` calculation.
- If `makePerkSlot()`, `makeDotSep()`, `updatePerkIcon()` methods are only called from the perk construction block, they can be removed entirely in Task 8. If called elsewhere, keep them.
- `rebuildPerksTable()` method may reference `perkButtons` — check and remove if only used for milestone display.
