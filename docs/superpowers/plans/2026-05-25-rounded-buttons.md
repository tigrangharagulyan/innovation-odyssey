# Rounded Buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all rectangular button drawables with rounded-corner versions (r=22 for NinePatch, r=14 for ShapeRenderer) across every screen in Innovation Odyssey.

**Architecture:** Add a `makeRoundedBtn(Color fill, Color border, int r)` helper to `OdysseyGame.buildSkin()` (mirrors the existing `makeSciBtn` pattern); replace the 1×1 white-pixel TextButtonStyle drawables with white rounded NinePatches; replace the 6 tile PNG NinePatch loads with programmatic rounded equivalents; update the 3 ShapeRenderer-drawn buttons in `MainMenuScreen` with a polygon-based rounded-rect helper.

**Tech Stack:** LibGDX 1.12.1, Pixmap, NinePatch, NinePatchDrawable, ShapeRenderer.

---

## Files Modified

- `src/com/odyssey/OdysseyGame.java` — add `makeRoundedBtn`, replace default style, replace tile styles
- `src/com/odyssey/screen/MainMenuScreen.java` — add `drawShapeRoundedRect`, update 3 draw methods

---

### Task 1: Add `makeRoundedBtn` helper to `OdysseyGame.java`

**Files:**
- Modify: `src/com/odyssey/OdysseyGame.java` (after `makeSciBtn` method, around line 283)

- [ ] **Step 1: Add the helper method**

Insert the following method immediately after the closing brace of `makeSciBtn` (after line 282):

```java
/** Generates a rounded-rectangle NinePatch drawable for TextButton styles.
 *  fill  – interior colour (use Color.WHITE for styles that rely on setColor() tinting)
 *  border – 2-pixel outer ring colour
 *  r     – corner radius in Pixmap pixels */
private static NinePatchDrawable makeRoundedBtn(Color fill, Color border, int r) {
    int SIZE = 64;
    Pixmap pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();
    fillRoundedRect(pm, 0,     0,     SIZE,     SIZE,     r,     border);
    fillRoundedRect(pm, 2,     2,     SIZE - 4, SIZE - 4, r - 2, fill);
    Texture tex = new Texture(pm);
    pm.dispose();
    int m = r + 2; // NinePatch margin: preserves corners, stretches flat centre
    return new NinePatchDrawable(new NinePatch(tex, m, m, m, m));
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew desktop:compileJava 2>&1 | grep -E "error:|warning:|BUILD"
```
Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 3: Commit**

```bash
git add src/com/odyssey/OdysseyGame.java
git commit -m "feat: add makeRoundedBtn helper for programmatic rounded NinePatches"
```

---

### Task 2: Replace default and toggle TextButtonStyles with rounded drawables

These two styles drive all 58+ TextButtons outside the Engineering Lab tile system. They rely on per-frame `setColor(OdysseyTheme.BTN_*)` tinting, so the Pixmap must be white — the rounded shape is the only change.

**Files:**
- Modify: `src/com/odyssey/OdysseyGame.java` lines 172–192

- [ ] **Step 1: Replace the default TextButtonStyle block**

Find the block at lines 172–181:
```java
        TextButton.TextButtonStyle btn = new TextButton.TextButtonStyle();
        btn.font              = font;
        btn.up                = s.newDrawable("white", OdysseyTheme.PANEL_BG);
        btn.over              = s.newDrawable("white", OdysseyTheme.BTN_AVAILABLE);
        btn.down              = s.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        btn.disabled          = s.newDrawable("white", OdysseyTheme.BTN_LOCKED);
        btn.fontColor         = OdysseyTheme.TEXT_PRI;
        btn.downFontColor     = OdysseyTheme.TEXT_PRI;
        btn.disabledFontColor = OdysseyTheme.TEXT_DIM;
        s.add("default", btn);
```

Replace with:
```java
        // White rounded NinePatch — setColor() per frame still drives visual state
        Color wBorder = new Color(0.55f, 0.55f, 0.55f, 1f);
        NinePatchDrawable roundedWhite = makeRoundedBtn(Color.WHITE, wBorder, 22);
        TextButton.TextButtonStyle btn = new TextButton.TextButtonStyle();
        btn.font              = font;
        btn.up                = roundedWhite;
        btn.over              = roundedWhite;
        btn.down              = roundedWhite;
        btn.disabled          = makeRoundedBtn(Color.WHITE, new Color(0.30f, 0.30f, 0.30f, 1f), 22);
        btn.fontColor         = OdysseyTheme.TEXT_PRI;
        btn.downFontColor     = OdysseyTheme.TEXT_PRI;
        btn.disabledFontColor = OdysseyTheme.TEXT_DIM;
        s.add("default", btn);
```

- [ ] **Step 2: Replace the toggle TextButtonStyle block**

Find the block at lines 184–192:
```java
        TextButton.TextButtonStyle tog = new TextButton.TextButtonStyle();
        tog.font             = font;
        tog.up               = s.newDrawable("white", OdysseyTheme.PANEL_BG);
        tog.over             = s.newDrawable("white", OdysseyTheme.BTN_AVAILABLE);
        tog.down             = s.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        tog.checked          = s.newDrawable("white", OdysseyTheme.BTN_BUYABLE);
        tog.fontColor        = OdysseyTheme.TEXT_PRI;
        tog.checkedFontColor = OdysseyTheme.TEXT_PRI;
        s.add("toggle", tog);
```

Replace with:
```java
        TextButton.TextButtonStyle tog = new TextButton.TextButtonStyle();
        tog.font             = font;
        tog.up               = roundedWhite;
        tog.over             = roundedWhite;
        tog.down             = roundedWhite;
        tog.checked          = roundedWhite;
        tog.fontColor        = OdysseyTheme.TEXT_PRI;
        tog.checkedFontColor = OdysseyTheme.TEXT_PRI;
        s.add("toggle", tog);
```

- [ ] **Step 3: Build and run desktop to verify**

```bash
./gradlew run
```

Open the Engineering Lab — all standard TextButtons (BUY INTERN, BUMPER, GO, etc. that use the default style) should now have rounded corners. GalacticMap planet selection tabs should also be rounded. No crashes.

- [ ] **Step 4: Commit**

```bash
git add src/com/odyssey/OdysseyGame.java
git commit -m "feat: replace flat TextButton drawables with rounded NinePatches (r=22)"
```

---

### Task 3: Replace tile PNG NinePatch loading with programmatic rounded drawables

The 6 tile styles (`tile_locked`, `tile_available`, `tile_buyable`, `tile_active`, `tile_go`, `tile_golocked`) load from PNG files. Replace with `makeRoundedBtn` calls using matching colour schemes.

**Files:**
- Modify: `src/com/odyssey/OdysseyGame.java` lines 201–221

- [ ] **Step 1: Replace the tile NinePatch block**

Find the entire block at lines 201–221:
```java
        // Sci-fi tile buttons — loaded from generated PNG assets
        int M = 40; // NinePatch corner margin (matches 32px corner radius + some padding)
        NinePatch npLocked   = new NinePatch(new Texture("ui/btn_locked.png"),    M,M,M,M);
        NinePatch npAvail    = new NinePatch(new Texture("ui/btn_available.png"), M,M,M,M);
        NinePatch npBuyable  = new NinePatch(new Texture("ui/btn_buyable.png"),   M,M,M,M);
        NinePatch npActive   = new NinePatch(new Texture("ui/btn_active.png"),    M,M,M,M);
        NinePatch npGo       = new NinePatch(new Texture("ui/btn_go.png"),        M,M,M,M);
        NinePatch npGoLocked = new NinePatch(new Texture("ui/btn_golocked.png"),  M,M,M,M);
        s.add("tile_locked",       new NinePatchDrawable(npLocked));
        s.add("tile_available",    new NinePatchDrawable(npAvail));
        s.add("tile_buyable",      new NinePatchDrawable(npBuyable));
        s.add("tile_active",       new NinePatchDrawable(npActive));
        s.add("tile_go",           new NinePatchDrawable(npGo));
        s.add("tile_golocked",     new NinePatchDrawable(npGoLocked));
        // Pressed variants — slightly brightened via color tint in makeTileStyle
        s.add("tile_locked_dn",    new NinePatchDrawable(npLocked));
        s.add("tile_available_dn", new NinePatchDrawable(npAvail));
        s.add("tile_buyable_dn",   new NinePatchDrawable(npBuyable));
        s.add("tile_active_dn",    new NinePatchDrawable(npActive));
        s.add("tile_go_dn",        new NinePatchDrawable(npGo));
        s.add("tile_golocked_dn",  new NinePatchDrawable(npGoLocked));
```

Replace with:
```java
        // Sci-fi tile buttons — programmatic rounded NinePatches (r=22)
        // Each state has a fill colour from OdysseyTheme + a slightly lighter border.
        // _dn (pressed) variants darken the fill by 25%.
        Color bLocked   = new Color(0.14f, 0.14f, 0.24f, 1f);
        Color bAvail    = new Color(0.32f, 0.36f, 0.58f, 1f);
        Color bBuyable  = OdysseyTheme.ACCENT_E;
        Color bActive   = new Color(0.45f, 0.65f, 1.00f, 1f);
        Color bGo       = OdysseyTheme.ACCENT_GO;
        Color bGoLocked = new Color(0.12f, 0.30f, 0.14f, 1f);

        NinePatchDrawable npLocked   = makeRoundedBtn(OdysseyTheme.BTN_LOCKED,    bLocked,   22);
        NinePatchDrawable npAvail    = makeRoundedBtn(OdysseyTheme.BTN_AVAILABLE, bAvail,    22);
        NinePatchDrawable npBuyable  = makeRoundedBtn(OdysseyTheme.BTN_BUYABLE,   bBuyable,  22);
        NinePatchDrawable npActive   = makeRoundedBtn(OdysseyTheme.BTN_ACTIVE,    bActive,   22);
        NinePatchDrawable npGo       = makeRoundedBtn(OdysseyTheme.BTN_GO,        bGo,       22);
        NinePatchDrawable npGoLocked = makeRoundedBtn(OdysseyTheme.BTN_GO_LOCKED, bGoLocked, 22);

        // Pressed variants: darken fill by 25%
        Color lockedDn   = darken(OdysseyTheme.BTN_LOCKED,    0.75f);
        Color availDn    = darken(OdysseyTheme.BTN_AVAILABLE,  0.75f);
        Color buyableDn  = darken(OdysseyTheme.BTN_BUYABLE,    0.75f);
        Color activeDn   = darken(OdysseyTheme.BTN_ACTIVE,     0.75f);
        Color goDn       = darken(OdysseyTheme.BTN_GO,         0.75f);
        Color goLockedDn = darken(OdysseyTheme.BTN_GO_LOCKED,  0.75f);

        s.add("tile_locked",       npLocked);
        s.add("tile_available",    npAvail);
        s.add("tile_buyable",      npBuyable);
        s.add("tile_active",       npActive);
        s.add("tile_go",           npGo);
        s.add("tile_golocked",     npGoLocked);
        s.add("tile_locked_dn",    makeRoundedBtn(lockedDn,   bLocked,   22));
        s.add("tile_available_dn", makeRoundedBtn(availDn,    bAvail,    22));
        s.add("tile_buyable_dn",   makeRoundedBtn(buyableDn,  bBuyable,  22));
        s.add("tile_active_dn",    makeRoundedBtn(activeDn,   bActive,   22));
        s.add("tile_go_dn",        makeRoundedBtn(goDn,       bGo,       22));
        s.add("tile_golocked_dn",  makeRoundedBtn(goLockedDn, bGoLocked, 22));
```

- [ ] **Step 2: Add the `darken` helper method** (insert after `makeRoundedBtn`):

```java
/** Returns a new Color with r/g/b multiplied by factor (alpha unchanged). */
private static Color darken(Color c, float factor) {
    return new Color(c.r * factor, c.g * factor, c.b * factor, c.a);
}
```

- [ ] **Step 3: Build and run desktop to verify**

```bash
./gradlew run
```

Open the Engineering Lab. The tile action buttons (BUY INTERN, BUMPER, etc.) should now show clean rounded corners. No PNG-load exceptions. No crashes.

- [ ] **Step 4: Commit**

```bash
git add src/com/odyssey/OdysseyGame.java
git commit -m "feat: replace tile PNG NinePatches with programmatic rounded drawables"
```

---

### Task 4: Round the ShapeRenderer buttons in `MainMenuScreen`

The three ShapeRenderer-drawn buttons (NEW GAME, SHOP, LEADERBOARD) need a polygon-based rounded-rect helper. Replace the `sr.rect(...)` calls in their draw methods.

**Files:**
- Modify: `src/com/odyssey/screen/MainMenuScreen.java`

- [ ] **Step 1: Add the `drawShapeRoundedRect` helper**

Add this private static method anywhere in `MainMenuScreen` (e.g., just before `drawNewGameButton`):

```java
/**
 * Draws a filled or outlined rounded rectangle using the active ShapeRenderer begin/end block.
 * Call inside a begin(Filled) or begin(Line) block.
 * r    – corner radius in virtual units
 * segs – segments per corner arc (8 is smooth enough at these sizes)
 */
private static void drawShapeRoundedRect(ShapeRenderer sr,
                                          float x, float y, float w, float h,
                                          float r, int segs) {
    int total = segs * 4;
    float[] v = new float[total * 2];
    int vi = 0;
    // bottom-left arc: 180° → 270°
    for (int i = 0; i < segs; i++) {
        float a = (float) Math.toRadians(180.0 + i * 90.0 / (segs - 1));
        v[vi++] = x + r + r * MathUtils.cos(a);
        v[vi++] = y + r + r * MathUtils.sin(a);
    }
    // bottom-right arc: 270° → 360°
    for (int i = 0; i < segs; i++) {
        float a = (float) Math.toRadians(270.0 + i * 90.0 / (segs - 1));
        v[vi++] = x + w - r + r * MathUtils.cos(a);
        v[vi++] = y + r + r * MathUtils.sin(a);
    }
    // top-right arc: 0° → 90°
    for (int i = 0; i < segs; i++) {
        float a = (float) Math.toRadians(0.0 + i * 90.0 / (segs - 1));
        v[vi++] = x + w - r + r * MathUtils.cos(a);
        v[vi++] = y + h - r + r * MathUtils.sin(a);
    }
    // top-left arc: 90° → 180°
    for (int i = 0; i < segs; i++) {
        float a = (float) Math.toRadians(90.0 + i * 90.0 / (segs - 1));
        v[vi++] = x + r + r * MathUtils.cos(a);
        v[vi++] = y + h - r + r * MathUtils.sin(a);
    }
    sr.polygon(v);
}
```

Also add the `MathUtils` import at the top of the file if not already present:
```java
import com.badlogic.gdx.math.MathUtils;
```

- [ ] **Step 2: Update `drawNewGameButton()`**

Find lines 1094–1101:
```java
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.70f, 0.10f, 0.10f, 0.88f);
        sr.rect(NG_X, NG_Y, NG_W, NG_H);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(1f, 0.35f, 0.35f, 0.90f);
        sr.rect(NG_X, NG_Y, NG_W, NG_H);
        sr.end();
```

Replace with:
```java
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.70f, 0.10f, 0.10f, 0.88f);
        drawShapeRoundedRect(sr, NG_X, NG_Y, NG_W, NG_H, 14f, 8);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(1f, 0.35f, 0.35f, 0.90f);
        drawShapeRoundedRect(sr, NG_X, NG_Y, NG_W, NG_H, 14f, 8);
        sr.end();
```

- [ ] **Step 3: Update `drawShopButton()`**

In `drawShopButton()`, find and replace all five `sr.rect(SH_X..., sy..., SH_W..., SH_H...)` calls:

**Glow layers** (lines 1013–1017 — the loop body):
```java
            sr.setColor(1.00f, 0.70f, 0.08f, 0.018f * g * pulse);
            sr.rect(SH_X - ex, sy - ex, SH_W + ex*2f, SH_H + ex*2f);
```
Replace with:
```java
            sr.setColor(1.00f, 0.70f, 0.08f, 0.018f * g * pulse);
            drawShapeRoundedRect(sr, SH_X - ex, sy - ex, SH_W + ex*2f, SH_H + ex*2f, 14f + ex, 8);
```

**Dark fill** (line 1020):
```java
        sr.rect(SH_X, sy, SH_W, SH_H);
```
Replace with:
```java
        drawShapeRoundedRect(sr, SH_X, sy, SH_W, SH_H, 14f, 8);
```

**Top highlight band** (line 1023): leave as `sr.rect` (it's a thin inner accent strip, not a button border).

**Border line 1** (line 1040):
```java
        sr.rect(SH_X, sy, SH_W, SH_H);
```
Replace with:
```java
        drawShapeRoundedRect(sr, SH_X, sy, SH_W, SH_H, 14f, 8);
```

**Border line 2** (line 1042):
```java
        sr.rect(SH_X + 1f, sy + 1f, SH_W - 2f, SH_H - 2f);
```
Replace with:
```java
        drawShapeRoundedRect(sr, SH_X + 1f, sy + 1f, SH_W - 2f, SH_H - 2f, 13f, 8);
```

- [ ] **Step 4: Update `drawLeaderboardButton()`**

In `drawLeaderboardButton()`, find and replace `sr.rect` calls:

**Glow layers** (lines 1068–1070 — loop body):
```java
            sr.setColor(0.22f, 0.72f, 1.00f, 0.016f * g * pulse);
            sr.rect(LB_X - ex, ly - ex, LB_W + ex * 2f, LB_H + ex * 2f);
```
Replace with:
```java
            sr.setColor(0.22f, 0.72f, 1.00f, 0.016f * g * pulse);
            drawShapeRoundedRect(sr, LB_X - ex, ly - ex, LB_W + ex*2f, LB_H + ex*2f, 14f + ex, 8);
```

**Dark fill** (line 1072):
```java
        sr.rect(LB_X, ly, LB_W, LB_H);
```
Replace with:
```java
        drawShapeRoundedRect(sr, LB_X, ly, LB_W, LB_H, 14f, 8);
```

**Top highlight band** (line 1074): leave as `sr.rect`.

**Border line** (line 1079):
```java
        sr.rect(LB_X, ly, LB_W, LB_H);
```
Replace with:
```java
        drawShapeRoundedRect(sr, LB_X, ly, LB_W, LB_H, 14f, 8);
```

- [ ] **Step 5: Build and run desktop to verify**

```bash
./gradlew run
```

On the Main Menu, the NEW GAME, SHOP, and LEADERBOARD buttons should all have rounded corners. No crashes.

- [ ] **Step 6: Commit**

```bash
git add src/com/odyssey/screen/MainMenuScreen.java
git commit -m "feat: rounded corners on ShapeRenderer buttons in MainMenuScreen (r=14)"
```

---

### Task 5: Install on Pixel 4a and full visual verification

- [ ] **Step 1: Build and install APK**

```bash
./gradlew android:copyAndroidNatives android:installDebug
```
Expected: `Installed on 1 device.` / `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify all screens on device**

Check each screen:
1. **Main Menu** — NEW GAME (bottom-right), SHOP (center), RANKS/LEADERBOARD (left) — all rounded
2. **Engineering Lab** — all tile TextButtons (BUY INTERN, BUMPER, GO, etc.) — rounded, all states (locked/available/buyable/go)
3. **Galactic Map** — planet selection toggle buttons — rounded
4. **Leaderboard** — tab buttons and BACK button — rounded
5. **Nova Terra Arrival** — all 7 action buttons — rounded
6. **No visual regressions** on panels, progress bars, popups

- [ ] **Step 3: Commit if any last-minute tweaks were needed, then final commit**

```bash
git add -A
git commit -m "fix: post-install visual tweaks for rounded buttons"
```
(Skip this step if no tweaks were needed.)
