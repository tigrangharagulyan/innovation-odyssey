# Innovation Odyssey — Design Direction

> Living design doc for the reimagined idle game on branch `feat/active-lab-mechanics`.

## Core Identity
Physics-bounce idle + **active Dota-style skills** + persistent meta growth. Unique hook: **spinning centrifuge drum** (centrifugal force as a mechanic) + active skills on Box2D orbs. The individual parts are borrowed; the *combination* is fresh.

Closest references:
- **Ballionaire** (PC) — physics objects + draft synergies + run meta
- **Archero / Survivor.io** (mobile) — loadout + draft + meta perks
- **Idle Breakout** (mobile) — orb economy, buy more balls

## The 30-Second Loop (the thing that must feel good)
- **Risk:** currently likely "watch 90%, tap 10%" = idler, not active game.
- **Fix direction:** mana refills fast enough to tap every ~5–8s; each tap visibly spikes income; real choice = "spend mana now (small gain) or save for OVERLOAD combo (big gain)."
- **Test:** when playtesting, am I *tapping* or *staring*? Staring = loop is broken.

## Progression Structure
```
Start: 1 orb
Earn money -> SPEND CHOICE (same currency = real tension):
   - upgrade existing skill (TALL / deep)
   - unlock new orb         (WIDE / broad)
Planets 1-5: collect orbs (the "campaign")
Pre-planet: SETUP/LOADOUT screen — pick N orbs, see planet modifier, adapt
```
- **Tall vs Wide tension** drives engagement — but only if money is **scarce** enough that choosing one stings.
- **Same currency for skill-vs-orb** = every coin is a choice.
- **Planet modifier must drive loadout** (e.g. high gravity -> bring heavy FROST), else setup is cosmetic.
- **Concern:** 1-orb start may feel empty -> make orb #1 skills instantly fun (DASH), unlock orb #2 *fast* (planet 1 clear).

## Onboarding Flow (continuous model — first 10 minutes)
"Hook, Loop, Progress." Strip all late-game clutter at start: one orb, one skill, one choice. Reveal complexity slowly.

**Phase 1 — Humble beginning (Planet 1, min 1)**
- Screen opens: **1 orb already bouncing** in the drum (continuous, no launch).
- Bottom deck empty except **one flashing button: [DASH]** (punchy starter, NOT a defensive skill).
- Other 3 skill slots + rest of roster = locked / greyed with lock icon.
- Player taps DASH -> orb rockets forward, plows rings/bumpers, **SP scatters**.
- Mana refills -> tap again. They learn: *tap = power spike*.

**Phase 2 — First taste (the hook)**
- Switch to Upgrades tab, ~120 SP collected.
- **Only option:** DASH Rank 1->2 for 100 SP. Buy.
- Back in drum: DASH faster, bigger impact, ~2x income on tap. *Felt* the upgrade.

**Phase 3 — Crossroads (~350 SP)**
- Upgrades tab evolved. Brutal choice (same currency = it stings):
  - **Go Tall:** DASH R3, or unlock 2nd skill OVERLOAD (~300 SP)
  - **Go Wide:** unlock 2nd orb SPARK (diff weight/skill) + 1 orb slot (~350 SP)
- Wide = new toy immediately bouncing alongside. Tall = workhorse becomes a monster.

**Continuous-model notes:**
- Income comes from continuous bouncing; **tap = burst multiplier on top**, not the only income.
- Starter skill must be punchy (DASH / drill), never defensive Shield — first taste = power.
- "Add slot" = more orbs in the drum (not more launches).

## The 30s Rhythm Fix (continuous = passive risk)
- Mana refills every ~5–8s -> tap that often.
- **Each tap = visible SP burst** (number pops, combo meter).
- Choice each window: tap small skill now, or bank mana for OVERLOAD combo.

## Screen Structure — Two Tabs
```
[ ARENA tab ]            [ UPGRADES tab ]
orbs bounce in drum      shop / progression
tap skills               spend SP / crystals
watch SP tick            Tall vs Wide choices
```
**Upgrades tab sections** (unlock progressively so minute-1 isn't overwhelming):
1. **Skills** — per equipped orb, rank 1->3, SP cost (Go Tall)
2. **Roster** — locked/unlocked orbs, crystal cost to unlock (Go Wide)
3. **Slots** — buy extra orb slots (crystals)
4. **Loadout** — pick which N orbs go in drum; pre-planet setup lives here
5. **Perks** — view earned center-break perks (read-only)

Early game shows only **Skills**; other sections reveal as unlocked.

## Upgrade Dimensions (5, mapped to 3 currencies)
| Currency | Buys | Dimension |
|----------|------|-----------|
| **Joules** (soft) | fuel/numbers, nothing permanent | — |
| **SP** | skill ranks (1->3) | 1. Skill Rank |
| **Crystals** (hard) | orb unlocks + orb slots | 2. Roster, 3. Slots |
| **Perks** (earned) | center-break draft | 4. Meta Perks |
| Planets gate all | clear to advance | 5. World |

Keep readable: **SP->skills, Crystals->orbs/slots, Perks->meta, Planets->gates.** Idle games die with too many currencies.

## Orb Roster
- **12 orbs total**, ~9 locked at start, unlock over planets 1–5 as collection.
- **Loadout system** (pick 3–4 of 12), NOT all-at-once (chaos / perf / clutter).
- Each orb = id, color, radius, 4 skills, unlock cost (data-driven registry — current 3-type hardcode must become a table).

## Center-Break Perks (PERSISTENT — cozy idle growth)
Draft **1 of 3** each center break. ~24 perk pool.

**Economy**
- Overcharge — +15% joules
- Bumper Mastery — bumper hits 2x -> 2.5x
- Compound — +2% joules per planet cleared (scales late)
- First Strike — first hit each launch = 3x

**Orb power**
- Twin Core — +1 orb slot (RARE)
- Skill Surge — all skills -20% mana cost
- Quick Charge — mana regen +30%
- Rank Boost — all skills +1 effective rank (capped 3)

**Physics / feel**
- Hyperspin — drum +20% spin (more centrifugal energy)
- Elastic — +0.15 restitution all orbs
- Heavy Core — orbs +30% mass (harder hits, slower)
- Low-G Mastery — gravity effect -25% (longer airtime)

**Skill-specific (build-defining)**
- Marked Field — MARKER bumpers permanent, no hit limit
- Drill Master — OVERLOAD drill always on, no mana
- Frostbite — FROST GRAVITY pulls 50% stronger
- Echo — every skill activates twice

**Meta / risk**
- Glass Cannon — +40% joules, but -1 orb slot
- Snowball — +5% joules per perk owned
- Reroll Token — redraft the center perk choices once

**Rules:** mix flat + build-defining; rares (Twin Core, Echo) = excitement spikes; synergy bait (Drill Master + Hyperspin) = replay; gate defining perks behind planet depth.

## Solving "Persist Trivializes Late Game"
1. **Planets scale exponential (~1.8x each), perks scale linear** -> always slightly behind -> cozy treadmill.
2. **Soft caps** on stacking %-perks (diminishing returns).
3. **Gate defining perks by depth** (Twin Core planet 5+, Echo planet 8+) -> power unfolds slowly.
4. **Each planet = NEW mechanic wall** perks don't auto-solve (P6: energy decays unless fast; P7: only bumper hits count) -> power isn't the only answer.
5. **Lean into crushing old planets** -> they become auto-farm income while you push frontier. That's the idle reward, not a bug.

## Current Skills (implemented)
- **SPARK** (purple): DASH (burst fwd), MARKER (wall hit -> volcano bumper), OVERDRIVE (min speed), SPLIT (extra orbs)
- **BLAZE** (orange): SHIELD, MAGNET (pull orbs), OVERLOAD (spinning drill, wall hit = speed boost), REV POL (multi-pulse pull to center)
- **FROST** (cyan): ATTACH (park+spin wall), ICE RUSH (detach charged strike), BIG (2x physics size + dmg), SNOW WAVE (spiral inward pull)

## Open Questions / Next Steps
- Draft planet-cost curve numbers
- Design the "new wall per planet" mechanics
- Pick the 12-orb roster + their skills
- Refactor 3-type hardcode -> data-driven orb registry (big code task)
