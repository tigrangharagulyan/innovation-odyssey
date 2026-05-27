# Leaderboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-planet fake leaderboard ranked by real-world flight time, visible as a post-arrival popup and a dedicated screen from the main menu.

**Architecture:** `FakeLeaderboard` holds 20 static rivals per planet; `ShipData` tracks the flight start timestamp and the player's best arrival time per planet (persisted); `LeaderboardScreen` shows a tabbed sorted table; `NovaTerraArrivalScreen` shows a rank popup after landing; `MainMenuScreen` gets a leaderboard button.

**Tech Stack:** LibGDX 1.12.1 — Scene2D (Stage/Table/Label/TextButton/ScrollPane), ShapeRenderer, LibGDX Preferences

---

## File Map

| Action | File |
|---|---|
| Modify | `src/com/odyssey/GameState.java` |
| Modify | `src/com/odyssey/ShipData.java` |
| Create | `src/com/odyssey/FakeLeaderboard.java` |
| Modify | `src/com/odyssey/OdysseyGame.java` |
| Create | `src/com/odyssey/screen/LeaderboardScreen.java` |
| Modify | `src/com/odyssey/screen/BridgeFlightScreen.java` |
| Modify | `src/com/odyssey/screen/NovaTerraArrivalScreen.java` |
| Modify | `src/com/odyssey/screen/MainMenuScreen.java` |

---

## Task 1: Add LEADERBOARD to GameState

**Files:**
- Modify: `src/com/odyssey/GameState.java`

- [ ] **Step 1: Add the enum value**

Replace the file content with:

```java
package com.odyssey;

public enum GameState {
    MAIN_MENU,
    ENGINEERING_LAB,
    BRIDGE_FLIGHT,
    INTERN_DEPLOY,
    GALACTIC_MAP,
    NOVA_TERRA_ARRIVAL,
    LEADERBOARD
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL (or only pre-existing errors, not errors about LEADERBOARD)

- [ ] **Step 3: Commit**

```bash
git add src/com/odyssey/GameState.java
git commit -m "feat: add LEADERBOARD to GameState"
```

---

## Task 2: Add timer and best-time fields to ShipData

**Files:**
- Modify: `src/com/odyssey/ShipData.java`

- [ ] **Step 1: Add fields after the `lastGemFarmTimestamp` field (line ~134)**

Insert these two fields after `public long lastGemFarmTimestamp = 0L;`:

```java
// Leaderboard — flight timer and per-planet personal bests
public long    flightStartTimeMs  = 0L;          // epoch ms when current flight began; 0 = not started
public float[] bestArrivalTimes   = new float[]{  // seconds; Float.MAX_VALUE = no time yet
    Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE
};
```

- [ ] **Step 2: Reset the new fields in `reset()` — add after `pendingContactEvents.clear();`**

```java
flightStartTimeMs = 0L;
for (int i = 0; i < bestArrivalTimes.length; i++) bestArrivalTimes[i] = Float.MAX_VALUE;
```

- [ ] **Step 3: Also reset `flightStartTimeMs` in `claimArrivalReward()` — add at the end of that method**

```java
flightStartTimeMs = 0L;  // next planet gets a fresh timer
```

- [ ] **Step 4: Save the new fields in `save()` — add before `p.flush();`**

```java
for (int i = 0; i < bestArrivalTimes.length; i++)
    p.putFloat("bestArrivalTime_" + i, bestArrivalTimes[i]);
```

- [ ] **Step 5: Load the new fields in `load()` — add after the `unlimitedLives` load line**

```java
for (int i = 0; i < bestArrivalTimes.length; i++)
    bestArrivalTimes[i] = p.getFloat("bestArrivalTime_" + i, Float.MAX_VALUE);
```

- [ ] **Step 6: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/com/odyssey/ShipData.java
git commit -m "feat: add flightStartTimeMs and bestArrivalTimes to ShipData"
```

---

## Task 3: Create FakeLeaderboard

**Files:**
- Create: `src/com/odyssey/FakeLeaderboard.java`

- [ ] **Step 1: Create the file**

```java
package com.odyssey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static fake rivals for the per-planet leaderboard.
 * Times are in real-world seconds. Lower = better (faster arrival).
 */
public final class FakeLeaderboard {

    public static final class Entry {
        public final String  name;
        public final float   timeSeconds;
        public final boolean isPlayer;

        public Entry(String name, float timeSeconds, boolean isPlayer) {
            this.name        = name;
            this.timeSeconds = timeSeconds;
            this.isPlayer    = isPlayer;
        }
    }

    // 20 rivals per planet index (0=Solara … 4=Helios Forge).
    // Times in seconds. Top 5 fast, mid 10 moderate, bottom 5 slow.
    private static final String[][] NAMES = {
        // Planet 0 — Solara
        { "StarForge-7","CaptainNova","IonDrift-X","NebulaPilot","VoidWatcher",
          "OrbitalSam","CosmicAce","GalaxyRun","PulseDrive","SolarFlare",
          "CoreBreaker","CrystalEdge","WarpRookie","StarSeeker","NovaTech",
          "SlowBurn-9","DustCloud","RetroThrust","GraviTrek","IceCrystal" },
        // Planet 1 — Nova Terra
        { "HeliosBlazer","CometRider","MagmaCore","FusionPilot","IronForge-1",
          "LavaDrift","PlasmaAce","ThermalX","FlareChaser","VulcanStar",
          "MoltBurst","ForgeRunner","EmberCross","HeatSeeker","SlagPilot",
          "ColdStart-3","SlowMagma","VolcanSlow","GraviWait","IonCrawl" },
        // Planet 2 — Frostheim
        { "CryoStar","IceForge-4","FrostPilot","NordDrift","PolarAce",
          "BlizzardX","GlacierRun","FrostCore","SnowChaser","ArcticFlare",
          "IceBurst","CryoEdge","FrostRook","ShiveringX","NordStar",
          "SlowFreeze","ArcticCrawl","IceDrift-9","ColdRunner","FrostWait" },
        // Planet 3 — Cryon Reach
        { "QuantumAce","FusionReach","CryonEdge","DeepPilot","VoidForge-2",
          "DarkMatter","QuantumX","ReachRunner","NullDrift","StarVault",
          "CryonFlare","DeepCore","VoidBurst","NullChaser","QuantumStar",
          "SlowReach","DriftWait","NullCrawl","DeepSlow","VoidWander" },
        // Planet 4 — Helios Forge
        { "HeliosAce","ForgeGod-1","StellarEdge","OverdriveX","NovaForge",
          "PlasmaStar","CoreForge","StellarCore","HeliosPilot","StarAnvil",
          "ForgeMaster","HeliosRun","StellarFlare","AnvilDrift","PlasmaRook",
          "SlowForge","AnvilCrawl","StellarSlow","HeliosWait","CoreDrift" }
    };

    // Times in seconds per planet. Row: [top5 fast, mid10 moderate, bottom5 slow]
    private static final float[][] TIMES = {
        // Solara (first planet — shorter absolute times)
        { 390f, 435f, 480f, 545f, 610f,
          720f, 840f, 960f, 1080f, 1260f, 1440f, 1680f, 1920f, 2100f, 2280f,
          2700f, 3000f, 3600f, 4200f, 5400f },
        // Nova Terra
        { 900f, 1020f, 1140f, 1320f, 1500f,
          1800f, 2100f, 2400f, 2700f, 3000f, 3600f, 4200f, 4800f, 5400f, 6000f,
          7200f, 8400f, 9600f, 10800f, 14400f },
        // Frostheim
        { 1800f, 2100f, 2400f, 2700f, 3000f,
          3600f, 4200f, 4800f, 5400f, 6600f, 7800f, 9000f, 10200f, 12000f, 14400f,
          18000f, 21600f, 25200f, 28800f, 36000f },
        // Cryon Reach
        { 3600f, 4200f, 4800f, 5400f, 6000f,
          7200f, 8400f, 9600f, 10800f, 12600f, 14400f, 16200f, 18000f, 21600f, 25200f,
          32400f, 39600f, 46800f, 54000f, 72000f },
        // Helios Forge
        { 7200f, 8400f, 9600f, 10800f, 12000f,
          14400f, 16800f, 19200f, 21600f, 25200f, 28800f, 32400f, 36000f, 43200f, 50400f,
          64800f, 79200f, 93600f, 108000f, 144000f }
    };

    /**
     * Returns all 20 rivals for a planet, sorted ascending by time (fastest first).
     * The player's row is injected if playerTimeSeconds < Float.MAX_VALUE.
     *
     * @param planetIndex     0–4
     * @param playerTimeSeconds player's best time in seconds, or Float.MAX_VALUE if none
     * @return sorted list including the player row (if valid time exists)
     */
    public static List<Entry> getBoard(int planetIndex, float playerTimeSeconds) {
        List<Entry> list = new ArrayList<>();
        String[] names = NAMES[planetIndex];
        float[]  times = TIMES[planetIndex];
        for (int i = 0; i < names.length; i++) {
            list.add(new Entry(names[i], times[i], false));
        }
        if (playerTimeSeconds < Float.MAX_VALUE) {
            list.add(new Entry("YOU", playerTimeSeconds, true));
        }
        Collections.sort(list, (a, b) -> Float.compare(a.timeSeconds, b.timeSeconds));
        return list;
    }

    /**
     * Returns the player's 1-based rank on the given planet board.
     * Returns -1 if the player has no time yet.
     */
    public static int getRank(int planetIndex, float playerTimeSeconds) {
        if (playerTimeSeconds >= Float.MAX_VALUE) return -1;
        List<Entry> board = getBoard(planetIndex, playerTimeSeconds);
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).isPlayer) return i + 1;
        }
        return -1;
    }

    /** Formats seconds as "m:ss" (e.g. 125.4 → "2:05"). */
    public static String formatTime(float seconds) {
        if (seconds >= Float.MAX_VALUE) return "--:--";
        int totalSecs = (int) seconds;
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private FakeLeaderboard() {}
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/com/odyssey/FakeLeaderboard.java
git commit -m "feat: add FakeLeaderboard with static rivals and rank helpers"
```

---

## Task 4: Create LeaderboardScreen

**Files:**
- Create: `src/com/odyssey/screen/LeaderboardScreen.java`

- [ ] **Step 1: Create the file**

```java
package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.FakeLeaderboard;
import com.odyssey.FakeLeaderboard.Entry;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.OdysseyTheme;
import com.odyssey.ShipData;

import java.util.List;

public class LeaderboardScreen extends ScreenAdapter {

    private static final Color PLAYER_ROW_COLOR = new Color(0.22f, 1.00f, 0.52f, 1f);
    private static final Color RIVAL_COLOR       = new Color(0.75f, 0.85f, 1.00f, 0.90f);
    private static final Color RANK_COLOR        = new Color(1.00f, 0.82f, 0.20f, 1f);
    private static final Color TAB_ACTIVE        = new Color(0.22f, 0.72f, 1.00f, 1f);
    private static final Color TAB_LOCKED        = new Color(0.30f, 0.30f, 0.38f, 0.60f);
    private static final Color TAB_UNLOCKED      = new Color(0.55f, 0.65f, 0.80f, 1f);

    private final OdysseyGame game;
    private final Stage       stage;
    private int               activePlanet = 0;

    // Kept so we can rebuild the board table when tab changes
    private Table             boardTable;
    private ScrollPane        scrollPane;
    private Table             root;

    public LeaderboardScreen(OdysseyGame game) {
        this.game  = game;
        this.stage = new Stage(new ScreenViewport());
        buildUi();
    }

    private void buildUi() {
        root = new Table();
        root.setFillParent(true);
        root.pad(16f);
        root.top();

        // ── Title ──────────────────────────────────────────────────────────────
        Label title = new Label("LEADERBOARD", game.skin, "title");
        root.add(title).center().padBottom(14f).row();

        // ── Planet tabs ────────────────────────────────────────────────────────
        Table tabs = new Table();
        for (int i = 0; i < ShipData.PLANETS.length; i++) {
            final int idx = i;
            boolean visited = ShipData.get().bestArrivalTimes[i] < Float.MAX_VALUE;
            boolean isActive = (i == activePlanet);

            TextButton tab = new TextButton(ShipData.PLANETS[i].name, game.skin);
            tab.getLabel().setFontScale(0.65f);
            if (isActive) {
                tab.setColor(TAB_ACTIVE);
            } else if (visited) {
                tab.setColor(TAB_UNLOCKED);
            } else {
                tab.setColor(TAB_LOCKED);
                tab.setDisabled(true);
            }

            tab.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    if (!actor.isDisabled()) {
                        activePlanet = idx;
                        refreshBoard();
                    }
                }
            });

            tabs.add(tab).width(84f).height(48f).pad(0f, 2f, 0f, 2f);
        }
        root.add(tabs).center().padBottom(10f).row();

        // ── Board table inside a scroll pane ──────────────────────────────────
        boardTable = new Table();
        scrollPane = new ScrollPane(boardTable, game.skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        root.add(scrollPane).expand().fill().padBottom(12f).row();

        // ── Back button ───────────────────────────────────────────────────────
        TextButton backBtn = new TextButton("BACK", game.skin);
        backBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.MAIN_MENU);
            }
        });
        root.add(backBtn).width(260f).height(64f).row();

        stage.addActor(root);
        refreshBoard();
    }

    private void refreshBoard() {
        boardTable.clear();

        ShipData sd = ShipData.get();
        float playerTime = sd.bestArrivalTimes[activePlanet];
        List<Entry> entries = FakeLeaderboard.getBoard(activePlanet, playerTime);

        // Header row
        addBoardRow(boardTable, "#", "NAME", "TIME", new Color(0.45f, 0.55f, 0.70f, 1f), true);
        boardTable.add(new Image(game.skin.newDrawable("white",
            new Color(0.20f, 0.25f, 0.35f, 0.60f)))).height(1f).colspan(3).fillX().row();

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            Color rowColor = e.isPlayer ? PLAYER_ROW_COLOR : RIVAL_COLOR;
            addBoardRow(boardTable,
                String.valueOf(i + 1),
                e.isPlayer ? "> YOU <" : e.name,
                FakeLeaderboard.formatTime(e.timeSeconds),
                rowColor, false);
        }

        // If player has no time yet, append a placeholder at the bottom
        if (playerTime >= Float.MAX_VALUE) {
            boardTable.add(new Image(game.skin.newDrawable("white",
                new Color(0.20f, 0.25f, 0.35f, 0.40f)))).height(1f).colspan(3).fillX().row();
            Label noTime = new Label("Complete a flight to set your time", game.skin);
            noTime.setFontScale(0.72f);
            noTime.setColor(0.45f, 0.55f, 0.70f, 0.85f);
            noTime.setAlignment(Align.center);
            boardTable.add(noTime).colspan(3).center().padTop(10f).row();
        }
    }

    private void addBoardRow(Table t, String rank, String name, String time,
                              Color color, boolean isHeader) {
        float fontScale = isHeader ? 0.75f : 0.85f;

        Label rankLbl = new Label(rank, game.skin);
        Label nameLbl = new Label(name, game.skin);
        Label timeLbl = new Label(time, game.skin);

        for (Label l : new Label[]{rankLbl, nameLbl, timeLbl}) {
            l.setFontScale(fontScale);
            l.setColor(color);
        }
        rankLbl.setAlignment(Align.center);
        timeLbl.setAlignment(Align.right);

        t.add(rankLbl).width(40f).padLeft(8f).padRight(4f);
        t.add(nameLbl).expandX().left().padLeft(4f);
        t.add(timeLbl).width(80f).padRight(8f).row();
    }

    @Override public void show() {
        activePlanet = 0;
        // Default to the most recently visited planet
        ShipData sd = ShipData.get();
        for (int i = ShipData.PLANETS.length - 1; i >= 0; i--) {
            if (sd.bestArrivalTimes[i] < Float.MAX_VALUE) {
                activePlanet = i;
                break;
            }
        }
        Gdx.input.setInputProcessor(stage);
        // Rebuild UI fresh to reflect latest planet unlock state
        stage.clear();
        buildUi();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(OdysseyTheme.SPACE_BG.r, OdysseyTheme.SPACE_BG.g,
                            OdysseyTheme.SPACE_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() {
        stage.dispose();
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/com/odyssey/screen/LeaderboardScreen.java
git commit -m "feat: add LeaderboardScreen with per-planet tabs and sorted table"
```

---

## Task 5: Wire LeaderboardScreen into OdysseyGame

**Files:**
- Modify: `src/com/odyssey/OdysseyGame.java`

- [ ] **Step 1: Add the field after `arrivalScreen` declaration (line 30)**

```java
private LeaderboardScreen      leaderboardScreen;
```

- [ ] **Step 2: Add the LEADERBOARD case to `switchScreenImmediate()` after the NOVA_TERRA_ARRIVAL case**

```java
case LEADERBOARD:
    if (leaderboardScreen == null) leaderboardScreen = new LeaderboardScreen(this);
    setScreen(leaderboardScreen);
    break;
```

- [ ] **Step 3: Dispose it in `dispose()` — add after `arrivalScreen` disposal**

```java
if (leaderboardScreen != null) leaderboardScreen.dispose();
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/com/odyssey/OdysseyGame.java
git commit -m "feat: wire LeaderboardScreen into OdysseyGame routing"
```

---

## Task 6: Start the flight timer in BridgeFlightScreen

**Files:**
- Modify: `src/com/odyssey/screen/BridgeFlightScreen.java`

- [ ] **Step 1: Find `resetFlight()` (line ~146). At the very start of the method body, after `ShipData sd = ShipData.get();`, add:**

```java
// Start the leaderboard timer on first flight toward this destination
if (sd.flightStartTimeMs == 0L) {
    sd.flightStartTimeMs = System.currentTimeMillis();
}
```

This preserves the original start time across multiple flight attempts toward the same planet.

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/com/odyssey/screen/BridgeFlightScreen.java
git commit -m "feat: start leaderboard flight timer in BridgeFlightScreen.resetFlight"
```

---

## Task 7: Record best time and show rank popup in NovaTerraArrivalScreen

**Files:**
- Modify: `src/com/odyssey/screen/NovaTerraArrivalScreen.java`

- [ ] **Step 1: Add instance fields for the popup after existing label fields (around line 42)**

```java
// Leaderboard popup
private Table   lbPopup;
private Label   lbRankLabel;
private Label   lbContextLabel;
private int     recordedRank   = -1;
private int     recordedPlanet = -1;
```

- [ ] **Step 2: Add a helper method `recordArrivalTime()` before `buildUi()`**

```java
/** Records the player's arrival time and computes their rank. Call once per arrival. */
private void recordArrivalTime() {
    ShipData sd = ShipData.get();
    if (sd.flightStartTimeMs == 0L) return; // timer was never started

    float elapsed = (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f;
    int pIdx = sd.currentPlanetIndex;

    if (elapsed < sd.bestArrivalTimes[pIdx]) {
        sd.bestArrivalTimes[pIdx] = elapsed;
    }
    sd.flightStartTimeMs = 0L; // clear so the next planet gets a fresh timer
    sd.save();
    recordedRank   = FakeLeaderboard.getRank(pIdx, sd.bestArrivalTimes[pIdx]);
    recordedPlanet = pIdx;
}
```

- [ ] **Step 3: Add import for FakeLeaderboard at the top of the file**

After the existing imports, add:
```java
import com.odyssey.FakeLeaderboard;
import com.odyssey.FakeLeaderboard.Entry;
import java.util.List;
```

- [ ] **Step 4: Add `buildLbPopup()` method after `buildUi()`**

```java
private void buildLbPopup() {
    lbPopup = new Table();
    lbPopup.setFillParent(true);
    lbPopup.setVisible(false);
    lbPopup.setBackground(game.skin.newDrawable("white", new Color(0f, 0.02f, 0.08f, 0.92f)));
    lbPopup.center();

    Label title = new Label("LEADERBOARD", game.skin, "title");
    lbPopup.add(title).center().padBottom(8f).row();

    lbRankLabel = new Label("", game.skin, "heading");
    lbRankLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
    lbPopup.add(lbRankLabel).center().padBottom(14f).row();

    lbContextLabel = new Label("", game.skin);
    lbContextLabel.setFontScale(0.80f);
    lbContextLabel.setColor(0.75f, 0.85f, 1.00f, 0.90f);
    lbContextLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
    lbContextLabel.setWrap(true);
    lbPopup.add(lbContextLabel).width(380f).center().padBottom(20f).row();

    TextButton viewFull = new TextButton("VIEW FULL BOARD", game.skin);
    viewFull.addListener(new ChangeListener() {
        @Override public void changed(ChangeEvent event, Actor actor) {
            game.transitionTo(GameState.LEADERBOARD);
        }
    });

    TextButton cont = new TextButton("CONTINUE", game.skin);
    cont.addListener(new ChangeListener() {
        @Override public void changed(ChangeEvent event, Actor actor) {
            lbPopup.setVisible(false);
        }
    });

    lbPopup.add(viewFull).width(320f).height(64f).padBottom(10f).row();
    lbPopup.add(cont).width(320f).height(60f).row();

    stage.addActor(lbPopup);
}
```

- [ ] **Step 5: Update `buildUi()` — at the end of the method, just before the closing brace, add:**

```java
buildLbPopup();
```

- [ ] **Step 6: Update `show()` — replace the existing body with:**

```java
@Override public void show() {
    Gdx.input.setInputProcessor(stage);
    recordArrivalTime();
    refresh();
    showLbPopup();
}
```

- [ ] **Step 7: Add `showLbPopup()` method after `refresh()`**

```java
private void showLbPopup() {
    if (recordedRank < 0 || recordedPlanet < 0) return;

    ShipData sd = ShipData.get();
    String planetName = ShipData.PLANETS[recordedPlanet].name;
    float bestTime    = sd.bestArrivalTimes[recordedPlanet];

    lbRankLabel.setText("Rank #" + recordedRank + "  on  " + planetName);
    lbRankLabel.setColor(recordedRank <= 3
        ? new Color(1f, 0.82f, 0.20f, 1f)
        : new Color(0.22f, 1.00f, 0.52f, 1f));

    // Build context: up to 5 rows around the player's rank
    List<Entry> board = FakeLeaderboard.getBoard(recordedPlanet, bestTime);
    int playerIdx = recordedRank - 1; // 0-based
    int start = Math.max(0, playerIdx - 2);
    int end   = Math.min(board.size(), start + 5);
    start     = Math.max(0, end - 5);

    StringBuilder sb = new StringBuilder();
    for (int i = start; i < end; i++) {
        Entry e = board.get(i);
        if (e.isPlayer) sb.append("[#").append(i + 1).append("]  > YOU <  ")
                          .append(FakeLeaderboard.formatTime(e.timeSeconds)).append("\n");
        else            sb.append("  #").append(i + 1).append("   ").append(e.name)
                          .append("   ").append(FakeLeaderboard.formatTime(e.timeSeconds))
                          .append("\n");
    }
    lbContextLabel.setText(sb.toString().trim());

    lbPopup.setVisible(true);
}
```

- [ ] **Step 8: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/com/odyssey/screen/NovaTerraArrivalScreen.java
git commit -m "feat: record arrival time and show rank popup in NovaTerraArrivalScreen"
```

---

## Task 8: Add Leaderboard button to MainMenuScreen

**Files:**
- Modify: `src/com/odyssey/screen/MainMenuScreen.java`

- [ ] **Step 1: Add button bounds constants after `SH_X` declaration (around line 64)**

```java
// LEADERBOARD button — left of shop, same row
private static final float LB_W = 120f, LB_H = 36f;
private static final float LB_X = 16f;
private float lbY() { return statsY() - 62f - 8f - LB_H; }
```

- [ ] **Step 2: Add touch detection in the `show()` method's `InputAdapter.touchDown()`, after the SHOP button block (around line 139)**

```java
// LEADERBOARD button
if (tv.x >= LB_X && tv.x <= LB_X + LB_W && tv.y >= lbY() && tv.y <= lbY() + LB_H) {
    game.transitionTo(GameState.LEADERBOARD);
    return true;
}
```

- [ ] **Step 3: Add `drawLeaderboardButton()` method after `drawShopButton()`**

```java
private void drawLeaderboardButton() {
    float pulse = 0.5f + 0.5f * MathUtils.sin(animTime * 1.9f + 0.4f);
    float ly    = lbY();
    sr.setProjectionMatrix(viewport.getCamera().combined);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    sr.begin(ShapeRenderer.ShapeType.Filled);
    for (int g = 6; g > 0; g--) {
        float ex = g * 2.8f;
        sr.setColor(0.22f, 0.72f, 1.00f, 0.016f * g * pulse);
        sr.rect(LB_X - ex, ly - ex, LB_W + ex * 2f, LB_H + ex * 2f);
    }
    sr.setColor(0.02f, 0.07f, 0.18f, 0.92f);
    sr.rect(LB_X, ly, LB_W, LB_H);
    sr.setColor(0.22f, 0.72f, 1.00f, 0.10f + 0.06f * pulse);
    sr.rect(LB_X + 2f, ly + LB_H - 6f, LB_W - 4f, 4f);
    sr.end();

    sr.begin(ShapeRenderer.ShapeType.Line);
    sr.setColor(0.22f, 0.72f, 1.00f, 0.65f + 0.28f * pulse);
    sr.rect(LB_X, ly, LB_W, LB_H);
    sr.end();
}
```

- [ ] **Step 4: Add `drawLeaderboardLabel()` method after `drawLeaderboardButton()`**

```java
private void drawLeaderboardLabel() {
    float ly    = lbY();
    float pulse = 0.5f + 0.5f * MathUtils.sin(animTime * 1.9f + 0.4f);
    smallFont.getData().setScale(1.15f);
    smallFont.setColor(0.55f, 0.88f, 1.00f, 0.88f + 0.12f * pulse);
    smallFont.draw(batch, "RANKS", LB_X + 4f, ly + LB_H - 10f, LB_W - 8f, Align.center, false);
    smallFont.getData().setScale(1.00f);
}
```

- [ ] **Step 5: Call both methods in `render()` — add after `drawShopButton()` call (around line 297)**

```java
drawLeaderboardButton();
```

And after `drawShopLabel()` call (within the `batch.begin()` block):
```java
drawLeaderboardLabel();
```

- [ ] **Step 6: Verify compile**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/com/odyssey/screen/MainMenuScreen.java
git commit -m "feat: add RANKS leaderboard button to MainMenuScreen"
```

---

## Task 9: Smoke test — run the game

- [ ] **Step 1: Launch the desktop build**

```bash
./gradlew run
```

Expected: game opens, main menu visible with a "RANKS" button in the lower-left area.

- [ ] **Step 2: Verify main menu → leaderboard flow**

Tap the RANKS button. LeaderboardScreen should open showing Solara tab. All other planet tabs should be grayed out (no times yet). A "Complete a flight to set your time" message shows. BACK returns to main menu.

- [ ] **Step 3: Verify flight timer starts**

Start a flight (Engineering Lab → launch). Exit back. ShipData.flightStartTimeMs should be non-zero (visible in log or by adding a temporary Gdx.app.log line if needed).

- [ ] **Step 4: Complete an arrival and verify popup**

If you have a save near arrival, arrive at a planet. The leaderboard rank popup should appear in NovaTerraArrivalScreen showing your rank. "VIEW FULL BOARD" should navigate to the leaderboard. "CONTINUE" should dismiss the popup.

- [ ] **Step 5: Verify persisted best time**

After arriving, exit and relaunch the game. Go to RANKS. The planet you arrived at should now be an active tab. Your time should appear in the board with your row highlighted in green.

- [ ] **Step 6: Final commit if any fixes applied**

```bash
git add -A
git commit -m "fix: leaderboard smoke test corrections"
```

---

## Notes

- `flightStartTimeMs` is reset to `0L` in `claimArrivalReward()` so the next planet's timer starts fresh.
- Planet 0 (Solara) tab is always active in `LeaderboardScreen` (player can see the board they're working toward).
- `LeaderboardScreen.show()` rebuilds the UI on every visit to reflect newly unlocked planet tabs.
- Times persist across app restarts via `odyssey_save` preferences keys `bestArrivalTime_0` through `bestArrivalTime_4`.
