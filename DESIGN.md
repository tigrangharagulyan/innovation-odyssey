# Innovation Odyssey — Design Direction

> Living design doc for the reimagined idle game on branch `feat/active-lab-mechanics`.

## Core Identity — PURE IDLE (pivot 2026-06-03)
Physics-bounce **pure idle** + persistent meta growth. Unique hook: **spinning centrifuge drum** (centrifugal force as a mechanic) on real Box2D orbs. **No active skills, no tapping in the arena.** Each orb has ONE always-on **passive** that defines it. The arena is a satisfying screensaver that pays out; the *game* is the build (loadout + upgrades + synergies).

> PIVOT NOTE: earlier this branch had active Dota-style skills (DASH, OVERLOAD, REV POL, SNOW WAVE) + mana. Those are being removed. The cool visuals (drill, snow wave) survive as **auto-firing passive effects on a timer**, not tap skills. Mana system deleted.

Closest references:
- **Idle Breakout** (mobile) — orb economy, buy more balls, pure idle
- **Ballionaire** (PC) — physics + synergy builds + run meta
- **Spinner / centrifuge idle** clones — passive payout from physics

## Where Engagement Lives (no arena tapping)
- **In-arena interaction: zero.** Watch orbs bounce, passives auto-fire.
- **Build interaction: everything.** Pick orbs (loadout), upgrade passive ranks, unlock orbs, chase passive synergies, draft center-break perks.
- The "is it fun?" test moves from "am I tapping?" to **"is the next upgrade/unlock decision tempting?"**

## Passive System (replaces active skills)
- **Each orb = 1 unique always-on passive** (its identity).
- **SP upgrades the passive rank 1->3** (keeps the tall/wide economy intact).
- **No mana, no cooldowns, no buttons.**
- Passive draft per orb (current identities converted):

| Orb | Passive (always on) | Rank scales |
|-----|--------------------|-------------|
| **SPARK** | Fast & charged — every hit +energy; periodic auto-dash burst | dash freq / energy % |
| **BLAZE** | Overload aura — periodically grows drill, shoves nearby orbs; wall hit = self speed boost | aura freq / push force |
| **FROST** | Cryo field — slows nearby orbs + heavier hits (more energy per collision) | slow radius / mass |

## Progression Structure
```
Start: 1 orb
Earn money -> SPEND CHOICE (same currency = real tension):
   - upgrade existing orb's PASSIVE rank (TALL / deep)
   - unlock new orb                      (WIDE / broad)
Planets 1-5: collect orbs (the "campaign")
Pre-planet: SETUP/LOADOUT confirm — pick N orbs, see planet modifier, adapt
```
- **Tall vs Wide tension** drives engagement — but only if money is **scarce** enough that choosing one stings.
- **Same currency for passive-vs-orb** = every coin is a choice.
- **Planet modifier must drive loadout** (e.g. high gravity -> bring heavy FROST), else setup is cosmetic.
- **Concern:** 1-orb start may feel empty -> make orb #1 passive instantly satisfying, unlock orb #2 *fast* (planet 1 clear).

## Spawn & Loadout Model
- **Auto-spawn, no slingshot.** Equipped orbs appear in the drum on planet entry and bounce forever. Drag-to-launch removed (legacy from the old placement game).
- **Loadout editable in two places:** a Loadout section in the Upgrades tab (swap anytime) AND a pre-planet confirm step that shows the planet modifier + current loadout before entry.
- **Slots** = how many orbs you field at once (1 -> 5), grown via crystals/perks.

## Onboarding Flow (pure idle — first 10 minutes)
"Hook, Loop, Progress." Strip all late-game clutter at start: one orb, one passive, one choice. Reveal complexity slowly. The hook is the *upgrade*, not a tap.

**Phase 1 — Humble beginning (Planet 1, min 1)**
- Screen opens: **1 orb already bouncing** in the drum, its passive visibly firing on a timer (e.g. SPARK auto-dash burst).
- No skill buttons. Bottom shows only **SP ticking up** + a pulsing "Upgrades" tab badge.
- Player watches energy scatter, gets pulled to the Upgrades tab.

**Phase 2 — First taste (the hook)**
- Switch to Upgrades tab, ~120 SP collected.
- **Only option:** upgrade SPARK passive Rank 1->2 for 100 SP. Buy.
- Back in drum: dashes fire more often / harder, ~2x income. *Felt* the upgrade — the passive visibly leveled.

**Phase 3 — Crossroads (~350 SP)**
- Upgrades tab evolved. Brutal choice (same currency = it stings):
  - **Go Tall:** SPARK passive R3 (~300 SP) — workhorse becomes a monster
  - **Go Wide:** unlock 2nd orb BLAZE (diff weight + diff passive) + 1 orb slot (~350 SP)
- Wide = new orb immediately bouncing alongside, its passive auto-firing too. Tall = one orb dominates.

**Pure-idle notes:**
- All income is continuous bouncing + passive auto-effects. No taps, no bursts-on-demand.
- First passive must be *visibly* satisfying (SPARK auto-dash, sparks fly) so minute-1 reads as "alive".
- "Add slot" = more orbs in the drum.

## The Idle Engagement Check (no tapping = build must carry it)
- Every ~30-60s the player should have a **tempting upgrade decision** waiting (SP banked toward next rank, or crystals toward next orb).
- **Each upgrade visibly changes the arena** (passive bigger/faster/brighter) so progress is *seen*, not just a number.
- The hook is "one more upgrade", not "one more tap". Pace SP/crystal income so a decision is always ~near.

## Screen Structure — Two Tabs
```
[ ARENA tab ]            [ UPGRADES tab ]
orbs bounce in drum      shop / progression
passives auto-fire       spend SP / crystals
watch SP tick            Tall vs Wide choices
```
**Upgrades tab sections** (unlock progressively so minute-1 isn't overwhelming):
1. **Passives** — per equipped orb, rank 1->3, SP cost (Go Tall)
2. **Roster** — locked/unlocked orbs, crystal cost to unlock (Go Wide)
3. **Slots** — buy extra orb slots (crystals)
4. **Loadout** — pick which N orbs go in drum; mirrored in pre-planet confirm
5. **Perks** — view earned center-break perks (read-only)

Early game shows only **Passives**; other sections reveal as unlocked.

## Upgrade Dimensions (5, mapped to 3 currencies)
| Currency | Buys | Dimension |
|----------|------|-----------|
| **Joules** (soft) | fuel/numbers, nothing permanent | — |
| **SP** | passive ranks (1->3) | 1. Passive Rank |
| **Crystals** (hard) | orb unlocks + orb slots | 2. Roster, 3. Slots |
| **Perks** (earned) | center-break draft | 4. Meta Perks |
| Planets gate all | clear to advance | 5. World |

Keep readable: **SP->passives, Crystals->orbs/slots, Perks->meta, Planets->gates.** Idle games die with too many currencies.

## Orb Roster
- **12 orbs total**, ~9 locked at start, unlock over planets 1–5 as collection.
- **Loadout system** (pick 3–4 of 12), NOT all-at-once (chaos / perf / clutter).
- Each orb = id, color, radius, **1 passive** (rank 1->3), unlock cost (data-driven OrbRegistry — DONE for data; passive field TBD).

## Center-Break Perks (PERSISTENT — cozy idle growth)
Draft **1 of 3** each center break. ~24 perk pool.

**Economy**
- Overcharge — +15% joules
- Bumper Mastery — bumper hits 2x -> 2.5x
- Compound — +2% joules per planet cleared (scales late)
- First Strike — first hit each launch = 3x

**Orb power**
- Twin Core — +1 orb slot (RARE)
- Passive Boost — all orb passives +1 effective rank (capped 3)
- Rapid Pulse — all passive auto-timers fire 25% faster
- Wider Field — passive effect radii +30%

**Physics / feel**
- Hyperspin — drum +20% spin (more centrifugal energy)
- Elastic — +0.15 restitution all orbs
- Heavy Core — orbs +30% mass (harder hits, slower)
- Low-G Mastery — gravity effect -25% (longer airtime)

**Passive-specific (build-defining)**
- Marked Field — SPARK auto-markers become permanent bumpers
- Drill Master — BLAZE overload aura always at max
- Frostbite — FROST cryo field slows 50% more + bigger radius
- Echo — every passive auto-effect fires twice

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

## Legacy Active Skills (being removed in pivot)
These were built earlier this branch as TAP skills + mana. The pivot converts each orb's identity into ONE auto-firing passive; the rest are dropped. Visuals (drill, snow wave, volcano) are reused as passive effects.
- **SPARK**: DASH, MARKER (volcano bumper), OVERDRIVE, SPLIT
- **BLAZE**: SHIELD, MAGNET, OVERLOAD (spinning drill), REV POL
- **FROST**: ATTACH, ICE RUSH, BIG, SNOW WAVE (spiral pull)

## Implementation Status
- [x] Phase 1 — OrbRegistry data-driven (stats/skills/costs/textures)
- [x] Phase 2A — OrbType enum -> registry index
- [ ] Phase 2B — DROPPED (was dynamic roster UI on legacy buttons)
- [ ] PIVOT build (current): delete active skills + mana; add 1 passive/orb (auto-fire, rank 1-3); SP upgrades passive; auto-spawn loadout; Upgrades tab (Passives/Roster/Slots/Loadout/Perks); pre-planet confirm

## Open Questions / Next Steps
- Lock the 3 starter passives' exact behavior + rank scaling numbers
- Draft planet-cost curve numbers
- Design the "new wall per planet" mechanics
- Pick the 12-orb roster + their passives
