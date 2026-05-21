# Innovation Odyssey — UI Polish Design Spec
Date: 2026-05-20  
Goal: Ship-quality + demo-ready. Full polish pass in 3 independent phases.

---

## Decisions Made

- **Palette**: Neon Dark — near-black `#080810` base, blue `#2255CC` energy accent, gold `#CC9900` SP, green `#00FF44` launch-ready
- **Fonts**: `Rajdhani` (labels/buttons) + `Share Tech Mono` (values/HUD/symbols) — loaded via Google Fonts at runtime
- **Button style**: Chamfered corners (`clip-path polygon`), state via left-border color + background tint, no emojis — text symbols only (`[+]` `>>` `□` `◎`)
- **No emoji anywhere in UI**

---

## Phase 1 — Skin (foundation)

### OdysseyTheme.java (new)
Single source of truth. All screens import from here.

```java
// Colors — LibGDX Color instances
SPACE_BG      = new Color(0.031f, 0.031f, 0.063f, 1f)   // #080810
PANEL_BG      = new Color(0.043f, 0.051f, 0.098f, 1f)   // #0B0D1A
PANEL_BORDER  = new Color(0.071f, 0.094f, 0.165f, 1f)   // #12182A
ACCENT_E      = new Color(0.133f, 0.333f, 0.800f, 1f)   // #2255CC
ACCENT_SP     = new Color(0.800f, 0.600f, 0.000f, 1f)   // #CC9900
ACCENT_GO     = new Color(0.000f, 1.000f, 0.267f, 1f)   // #00FF44
ACCENT_WARN   = new Color(1.000f, 0.420f, 0.000f, 1f)   // #FF6B00
TEXT_PRI      = new Color(0.784f, 0.847f, 0.941f, 1f)   // #C8D8F0
TEXT_DIM      = new Color(0.227f, 0.290f, 0.416f, 1f)   // #3A4A6A

// Button state colors (for setColor calls)
BTN_LOCKED    // background: SPACE_BG, text: invisible (~0.1 alpha)
BTN_AVAILABLE // background: PANEL_BG, left-border: PANEL_BORDER
BTN_BUYABLE   // background: PANEL_BG, left-border: ACCENT_E
BTN_ACTIVE    // background: PANEL_BG, left-border+top: ACCENT_E, text blink
BTN_GO        // background: dark green, left-border: ACCENT_GO, pulsing
BTN_GO_LOCKED // background: very dark green, no border
```

### Skin rebuild (OdysseyGame.java)
- Replace current `button_primary` drawables with chamfered-look via `NinePatch` or programmatic `Pixmap`
- Two font slots in skin: `font` (Rajdhani-like fallback or scaled BitmapFont) and `mono` (Share Tech Mono or monospaced fallback)
- Disabled drawable: 10% alpha overlay

### Button symbols (no emoji)
| Button | Symbol | Label |
|--------|--------|-------|
| Hire Orb | `[+]` | HIRE ORB |
| Bumper | `[ ]` | BUMPER |
| Gravity / Cryo / Blade | `(o)` | context label |
| Launch | `>>` | LAUNCH |

### HUD strip (top of EngineeringLabScreen)
Always-visible bar above centrifuge area:
```
[SOLARA] ━━━━━━━━━╸░░░ 3.2/8.0KE  54 E/s  4,230 SP
```
- Planet name left (ACCENT_E color)
- Thin 3px energy progress bar (fill = ACCENT_E, cursor = bright tick)
- Energy value, E/s rate, SP balance right
- Background: PANEL_BG, bottom border: PANEL_BORDER

### Perk readout strip (below buttons)
4 small cells showing live multiplier values: Coll / Wall / Boost / Bump  
Active (>base): ACCENT_E label. At base: TEXT_DIM.

---

## Phase 2 — Juice (feedback)

### ScreenShake.java (new)
```java
// Usage: ScreenShake.get().trigger(intensity, durationSec)
// intensity 4f = milestone, 8f = checkpoint, 2f = intern hire
// Applies offset to renderCam each render() call, decays exponentially
```
- Used in: EngineeringLabScreen (milestone/checkpoint notifications), BridgeFlightScreen (landing)
- Does NOT affect Box2D world — camera offset only

### SoundManager.java (new)
Pooled `Sound` assets, max 4 simultaneous channels.
```
sounds/hire.ogg        — intern hired
sounds/collision.ogg   — intern-intern hit (throttled: max 1/200ms)
sounds/bumper.ogg      — bumper hit
sounds/milestone.ogg   — ring-speed milestone unlocked
sounds/checkpoint.ogg  — checkpoint reached (flight landing)
sounds/launch.ogg      — flight initiated
```
- All sounds optional (graceful no-op if file missing)
- Master volume in ShipData (persisted)

### Floating numbers upgrade
Current: basic white text.  
New:
- Color by source: SP=`#CC9900`, Energy=`#4488FF`, Gravity/special=`#00CCAA`
- Size by magnitude: base 14px, scales up to 22px for large hits
- Arc trajectory: slight horizontal drift + vertical rise
- Fade: sharp appear, slow fade over 1.2s

### Screen transitions
`OdysseyGame.transitionTo()` wraps screen change with 0.25s black fade-out → fade-in.  
Implemented via `ShapeRenderer` full-screen overlay with alpha tween.

---

## Phase 3 — UX Clarity

### Milestone celebration overlay
Full-screen pop on checkpoint/perk unlock (replaces current notification toast):
- Dark overlay `#000000CC`
- Centered card: perk name (large), description, stat changes
- Animated: slides up from bottom in 0.3s
- Dismiss: tap anywhere or 3s auto-dismiss
- Does NOT block physics (game continues behind overlay)

### Dismissable tutorial hints
Replace blocking tutorial steps with inline tooltip bubbles:
- Small arrow pointing to relevant button
- Tap anywhere to dismiss
- Never shown twice (flag in ShipData)
- Max 1 hint visible at a time

### Main menu polish
- Planet nodes: animated pulse ring (scale 1.0→1.08→1.0, 2s loop) on current active planet
- Progress ring: partial circle arc showing checkpoint progress per planet
- Path line: animated dash travel from reached node toward next

---

## File Change Map

| File | Change |
|------|--------|
| `OdysseyTheme.java` | NEW — color/font constants |
| `ScreenShake.java` | NEW — camera shake utility |
| `SoundManager.java` | NEW — pooled audio |
| `OdysseyGame.java` | Skin rebuild, fade transition wrapper |
| `EngineeringLabScreen.java` | HUD strip, button reskin, perk strip, shake calls, sound calls, upgraded floats, hint system |
| `BridgeFlightScreen.java` | Reskin, shake on landing, sound on launch |
| `MainMenuScreen.java` | Planet pulse animation, progress arcs, path animation |
| `NovaTerraArrivalScreen.java` | Reskin to match palette |

---

## Out of Scope
- No new screens
- No monetization hooks
- No save/load changes
- No gameplay balance changes
- No new planet content
