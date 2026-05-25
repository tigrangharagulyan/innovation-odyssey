package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.OdysseyTheme;
import com.odyssey.ShipData;

public class MainMenuScreen extends ScreenAdapter {

    private static final float W = 480f, H = 854f;

    // Planet node positions and sizes (0-4 = game planets, 5 = locked/coming soon)
    private static final float[] NX = {130, 350, 118, 352, 118, 338};
    private static final float[] NY = {120, 230, 345, 460, 570, 660};
    private static final float[] NR = { 40,  36,  34,  32,  30,  27};

    // Zigzag path through 5 game planets + 1 locked stub
    private static final int[][] EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}
    };

    // Planet glow colors: {innerR,G,B, outerR,G,B}
    private static final float[][] COL = {
        {1.0f, 0.78f, 0.12f,  0.95f, 0.35f, 0.05f}, // Solara       — fiery
        {0.75f, 0.22f, 1.00f, 0.40f, 0.05f, 0.70f}, // Ember IV     — volcanic purple
        {0.55f, 0.90f, 1.00f, 0.05f, 0.40f, 0.75f}, // Frostheim    — icy blue
        {0.45f, 0.80f, 1.00f, 0.05f, 0.35f, 0.72f}, // Cryon Reach  — icy
        {1.00f, 0.45f, 0.12f, 0.70f, 0.10f, 0.02f}, // Helios Forge — forge
    };

    private static final Color GOLD     = new Color(0.95f, 0.82f, 0.18f, 1f); // traveled
    private static final Color PATH_ON  = new Color(0.92f, 0.25f, 0.82f, 1f); // active / ahead
    private static final Color PATH_OFF = new Color(0.28f, 0.28f, 0.38f, 0.6f); // future / locked
    private static final Color CYAN     = new Color(0f,   0.90f, 1f,   1f);
    private static final Color DIM      = new Color(0.55f, 0.62f, 0.72f, 1f);
    private static final float ROCKET_HIT = 38f;

    // NEW GAME button bounds (bottom-right corner)
    private static final float NG_X = 312f, NG_Y = 16f, NG_W = 148f, NG_H = 36f;
    // SHOP button — width/height only; Y is dynamic (just below the green stats panel)
    private static final float SH_W = 130f, SH_H = 36f;
    private static final float SH_X = (480f - 130f) / 2f;

    private float shopY() { return statsY() - 62f - 8f - SH_H; }

    // LEADERBOARD button — left of shop, same visual row
    private static final float LB_W = 120f, LB_H = 36f;
    private static final float LB_X = 16f;
    private float lbY() { return statsY() - 62f - 8f - LB_H; }

    private final OdysseyGame   game;
    private final ExtendViewport viewport;
    private final ShapeRenderer sr;
    private final SpriteBatch   batch;
    private final BitmapFont    titleFont, bodyFont, smallFont;

    private Stage stage;
    private Table shopOverlay;

    // Leaderboard rank popup (Scene2D, shown after arrival)
    private Table   rankPopup;
    private boolean rankPopupPending = false;

    // Pre-baked starfield
    private final float[] starX, starY, starA;

    private float rocketX, rocketY, rocketAngle;
    private int   currentIdx;
    private float animTime = 0f;
    private final Vector3 tv = new Vector3();
    private boolean showRocketTutorial = false;

    public MainMenuScreen(OdysseyGame game) {
        this.game     = game;
        this.viewport = new ExtendViewport(W, H);
        this.sr       = new ShapeRenderer();
        this.batch    = new SpriteBatch();

        titleFont = game.skin.getFont("float"); titleFont.getData().setScale(3.2f);
        bodyFont  = game.skin.getFont("float"); bodyFont.getData().setScale(1.60f);
        smallFont = game.skin.getFont("float"); smallFont.getData().setScale(1.25f);

        int N = 120;
        starX = new float[N]; starY = new float[N]; starA = new float[N];
        java.util.Random rnd = new java.util.Random(0xABCDE);
        for (int i = 0; i < N; i++) {
            starX[i] = rnd.nextFloat() * W;
            starY[i] = rnd.nextFloat() * H;
            starA[i] = 0.18f + rnd.nextFloat() * 0.55f;
        }
    }

    @Override public void show() {
        refresh();
        showRocketTutorial = (ShipData.get().arrivalsCompleted == 0
                              && ShipData.get().totalJoules < 1f
                              && ShipData.get().crystals < 101f);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
                tv.set(sx, sy, 0);
                viewport.unproject(tv);
                float dx = tv.x - rocketX, dy = tv.y - rocketY;
                if (dx*dx + dy*dy < ROCKET_HIT * ROCKET_HIT) {
                    game.resetLabScreen();
                    game.transitionTo(GameState.ENGINEERING_LAB);
                    return true;
                }
                if (ShipData.get().arrivalReady && currentIdx < NX.length) {
                    float pcx = NX[currentIdx], pcy = NY[currentIdx], pr = NR[currentIdx];
                    float pdx = tv.x - pcx, pdy = tv.y - pcy;
                    if (pdx*pdx + pdy*pdy < pr*pr) {
                        game.transitionTo(GameState.NOVA_TERRA_ARRIVAL);
                        return true;
                    }
                }
                // NEW GAME button
                if (tv.x >= NG_X && tv.x <= NG_X + NG_W && tv.y >= NG_Y && tv.y <= NG_Y + NG_H) {
                    ShipData.get().reset();
                    game.resetLabScreen();
                    game.transitionTo(GameState.ENGINEERING_LAB);
                    return true;
                }
                // SHOP button — show shop overlay
                if (tv.x >= SH_X && tv.x <= SH_X + SH_W && tv.y >= shopY() && tv.y <= shopY() + SH_H) {
                    if (shopOverlay != null) shopOverlay.setVisible(true);
                    return true;
                }
                // LEADERBOARD button
                if (tv.x >= LB_X && tv.x <= LB_X + LB_W && tv.y >= lbY() && tv.y <= lbY() + LB_H) {
                    game.transitionTo(GameState.LEADERBOARD);
                    return true;
                }
                return false;
            }
        });

        // ---- Shop overlay (Scene2D) ----
        if (stage != null) stage.dispose();
        stage = new Stage(new ExtendViewport(W, H));
        buildShopOverlay();
        buildRankPopup();
        Gdx.input.setInputProcessor(new InputMultiplexer(stage,
            Gdx.input.getInputProcessor()));
    }

    private void buildShopOverlay() {
        TextButton.TextButtonStyle closeStyle = game.skin.get("default", TextButton.TextButtonStyle.class);

        shopOverlay = new Table();
        shopOverlay.setFillParent(true);
        shopOverlay.setVisible(false);
        shopOverlay.setTouchable(Touchable.enabled);
        shopOverlay.background(game.skin.newDrawable("white", new Color(0f, 0.03f, 0.10f, 0.96f)));
        shopOverlay.center();

        // ---- Title ----
        Label title = new Label("SHOP", game.skin);
        title.setFontScale(2.80f);
        title.setColor(1f, 0.84f, 0.22f, 1f);
        shopOverlay.add(title).padBottom(6f).row();

        Label sub = new Label("coming soon", game.skin);
        sub.setFontScale(0.80f);
        sub.setColor(0.55f, 0.62f, 0.72f, 1f);
        shopOverlay.add(sub).padBottom(28f).row();

        // ---- Cards ----
        addShopCard(shopOverlay,
            "\u25B6",  new Color(0.22f, 0.88f, 1.00f, 1f),
            "ADS",     new Color(0.85f, 0.96f, 1.00f, 1f),
            "Watch short rewarded ads\nto earn bonus Gems & energy.",
            new Color(0.60f, 0.78f, 0.90f, 1f));

        addShopCard(shopOverlay,
            "\u2666",  new Color(0.30f, 0.85f, 0.40f, 1f),
            "GEMS",    new Color(0.82f, 1.00f, 0.85f, 1f),
            "Buy hard currency packs\nto unlock lives & boosts.",
            new Color(0.60f, 0.85f, 0.65f, 1f));

        addShopCard(shopOverlay,
            "\u221E",  new Color(1.00f, 0.65f, 0.18f, 1f),
            "PERMANENTS", new Color(1.00f, 0.90f, 0.72f, 1f),
            "One-time lifetime upgrades:\nmultipliers, speed & capacity.",
            new Color(0.88f, 0.72f, 0.45f, 1f));

        // ---- Close button ----
        TextButton btnClose = new TextButton("CLOSE", closeStyle);
        btnClose.getLabel().setFontScale(0.95f);
        btnClose.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                shopOverlay.setVisible(false);
            }
        });
        shopOverlay.add(btnClose).width(260f).height(64f).padTop(28f).row();

        stage.addActor(shopOverlay);
    }

    private void buildRankPopup() {
        final ShipData sd = ShipData.get();
        if (sd.pendingRankResult < 0) return; // nothing to show

        rankPopup = new Table();
        rankPopup.setFillParent(true);
        rankPopup.setBackground(game.skin.newDrawable("white", new Color(0f, 0.02f, 0.08f, 0.92f)));
        rankPopup.center();

        Label title = new Label("LEADERBOARD", game.skin);
        rankPopup.add(title).center().padBottom(8f).row();

        String planetName = ShipData.PLANETS[sd.pendingRankPlanet].name;
        Label rankLabel = new Label("Rank #" + sd.pendingRankResult + "  on  " + planetName, game.skin);
        rankLabel.setAlignment(Align.center);
        rankLabel.setColor(sd.pendingRankResult <= 3
            ? new Color(1f, 0.82f, 0.20f, 1f)
            : new Color(0.22f, 1.00f, 0.52f, 1f));
        rankPopup.add(rankLabel).center().padBottom(14f).row();

        // Context snippet: up to 5 rows around player rank
        float bestTime = sd.bestArrivalTimes[sd.pendingRankPlanet];
        java.util.List<com.odyssey.FakeLeaderboard.Entry> board =
            com.odyssey.FakeLeaderboard.getBoard(sd.pendingRankPlanet, bestTime);
        int playerIdx = sd.pendingRankResult - 1;
        int start = Math.max(0, playerIdx - 2);
        int end   = Math.min(board.size(), start + 5);
        start     = Math.max(0, end - 5);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            com.odyssey.FakeLeaderboard.Entry e = board.get(i);
            if (e.isPlayer) sb.append("[#").append(i+1).append("]  > YOU <  ")
                              .append(com.odyssey.FakeLeaderboard.formatTime(e.timeSeconds)).append("\n");
            else            sb.append("  #").append(i+1).append("   ").append(e.name)
                              .append("   ").append(com.odyssey.FakeLeaderboard.formatTime(e.timeSeconds)).append("\n");
        }
        Label ctx = new Label(sb.toString().trim(), game.skin);
        ctx.setFontScale(0.72f);
        ctx.setColor(0.75f, 0.85f, 1.00f, 0.90f);
        ctx.setAlignment(Align.center);
        ctx.setWrap(true);
        rankPopup.add(ctx).width(380f).center().padBottom(20f).row();

        TextButton viewFull = new TextButton("VIEW FULL BOARD", game.skin);
        viewFull.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                sd.pendingRankResult = -1;
                sd.pendingRankPlanet = -1;
                game.transitionTo(GameState.LEADERBOARD);
            }
        });

        TextButton cont = new TextButton("CONTINUE", game.skin);
        cont.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                sd.pendingRankResult = -1;
                sd.pendingRankPlanet = -1;
                rankPopup.setVisible(false);
            }
        });

        rankPopup.add(viewFull).width(320f).height(64f).padBottom(10f).row();
        rankPopup.add(cont).width(320f).height(60f).row();

        stage.addActor(rankPopup);
    }

    /** Adds one shop category card row to the parent table. */
    private void addShopCard(Table parent,
                             String icon,  Color iconColor,
                             String name,  Color nameColor,
                             String desc,  Color descColor) {
        // Card background
        Table card = new Table();
        card.background(game.skin.newDrawable("white", new Color(0.04f, 0.08f, 0.18f, 0.85f)));
        card.pad(14f, 18f, 14f, 18f);

        // Icon column
        Label lblIcon = new Label(icon, game.skin);
        lblIcon.setFontScale(2.60f);
        lblIcon.setColor(iconColor);
        card.add(lblIcon).width(54f).top().padRight(16f);

        // Text column
        Table text = new Table();
        Label lblName = new Label(name, game.skin);
        lblName.setFontScale(1.30f);
        lblName.setColor(nameColor);
        text.add(lblName).left().row();

        Label lblDesc = new Label(desc, game.skin);
        lblDesc.setFontScale(0.72f);
        lblDesc.setColor(descColor);
        lblDesc.setWrap(true);
        text.add(lblDesc).left().width(260f).row();

        card.add(text).left();

        parent.add(card).width(380f).padBottom(12f).row();
    }

    private void refresh() {
        ShipData sd = ShipData.get();
        currentIdx = Math.min(sd.currentPlanetIndex, NX.length - 1);
        int next   = Math.min(currentIdx + 1, NX.length - 1);

        // Map sectorReached (-1..2) to a position along the current edge.
        // Checkpoint dots are at t = 0.25, 0.50, 0.75.
        float t;
        if (sd.arrivalReady && sd.sectorReached < 0) {
            t = 0.0f; // arrived, no flights yet — sit at destination planet
        } else {
            switch (sd.sectorReached) {
                case 0:  t = 0.25f; break; // CP I  — first dot
                case 1:  t = 0.50f; break; // CP II — second dot
                case 2:  t = 0.75f; break; // CP III— third dot
                default: t = 0.00f; break; // no checkpoint yet — at start planet
            }
        }

        if (t <= 0f) {
            rocketX = NX[currentIdx]; rocketY = NY[currentIdx];
        } else if (t >= 1f) {
            rocketX = NX[next]; rocketY = NY[next];
        } else {
            rocketX = NX[currentIdx] + (NX[next] - NX[currentIdx]) * t;
            rocketY = NY[currentIdx] + (NY[next] - NY[currentIdx]) * t;
        }

        rocketAngle = (currentIdx == next) ? (float)(Math.PI / 2)
            : (float)Math.atan2(NY[next] - NY[currentIdx], NX[next] - NX[currentIdx]);
    }

    // Edge state helpers
    private boolean isTraveled(int a, int b)   { return Math.max(a, b) <= currentIdx; }
    private boolean isActiveEdge(int a, int b) {
        return (a == currentIdx && b > currentIdx) || (b == currentIdx && a > currentIdx);
    }

    @Override
    public void render(float delta) {
        animTime += delta;
        Gdx.gl.glClearColor(OdysseyTheme.SPACE_BG.r, OdysseyTheme.SPACE_BG.g, OdysseyTheme.SPACE_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(viewport.getCamera().combined);

        drawStars();
        drawGrid();
        drawPaths();
        drawPlanets();

        drawRocket();

        drawNewGameButton();
        drawShopButton();
        drawLeaderboardButton();
        drawTopStatsBg();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawTitle();
        drawTopStatsText();
        drawLabels();
        drawBottomBar();
        drawNewGameLabel();
        drawShopLabel();
        drawLeaderboardLabel();
        batch.end();

        // Tutorial overlay drawn last — on top of all planet labels
        if (showRocketTutorial) {
            drawRocketTutorialBg();
            batch.begin();
            drawRocketTutorialText();
            batch.end();
        }

        // Stage (shop overlay)
        if (stage != null) {
            stage.act(delta);
            stage.draw();
        }
    }

    private void drawStars() {
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < starX.length; i++) {
            sr.setColor(1f, 1f, 1f, starA[i]);
            sr.circle(starX[i], starY[i], 1.2f, 4);
        }
        sr.end();
    }

    private void drawGrid() {
        float vh = viewport.getWorldHeight();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0f, 0.65f, 0.75f, 0.10f);
        float cw = 28f;
        for (float y = 0; y < vh; y += cw) {
            sr.line(0, y, 64f, y);
            sr.line(W - 64f, y, W, y);
        }
        for (float x = 0; x <= 64f; x += cw)    sr.line(x, 0, x, vh);
        for (float x = W - 64f; x <= W; x += cw) sr.line(x, 0, x, vh);
        sr.end();
    }

    private void drawPaths() {
        Vector2 dir = new Vector2();

        // Pass 1: dotted lines
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int[] e : EDGES) {
            float x1 = NX[e[0]], y1 = NY[e[0]], x2 = NX[e[1]], y2 = NY[e[1]];
            Color c  = edgeColor(e[0], e[1]);
            float a  = isTraveled(e[0], e[1]) ? 0.90f : (isActiveEdge(e[0], e[1]) ? 0.88f : 0.38f);
            float dr = isTraveled(e[0], e[1]) ? 3.0f  : (isActiveEdge(e[0], e[1]) ? 3.6f  : 2.0f);

            dir.set(x2 - x1, y2 - y1);
            float len = dir.len(); dir.nor();
            for (float d = 28f; d < len - 28f; d += 11f) {
                sr.setColor(c.r, c.g, c.b, a);
                sr.circle(x1 + dir.x * d, y1 + dir.y * d, dr, 6);
            }
        }
        sr.end();

        // Pass 2: checkpoint markers (filled dot) at 33% and 67% of each edge
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int[] e : EDGES) {
            float x1 = NX[e[0]], y1 = NY[e[0]], x2 = NX[e[1]], y2 = NY[e[1]];
            Color c = edgeColor(e[0], e[1]);
            for (float t : new float[]{0.25f, 0.50f, 0.75f}) {
                float cx = x1 + (x2 - x1) * t;
                float cy = y1 + (y2 - y1) * t;
                sr.setColor(c.r, c.g, c.b, 0.95f);
                sr.circle(cx, cy, 5.5f, 14);
            }
        }
        sr.end();

        // Pass 3: checkpoint rings
        sr.begin(ShapeRenderer.ShapeType.Line);
        for (int[] e : EDGES) {
            float x1 = NX[e[0]], y1 = NY[e[0]], x2 = NX[e[1]], y2 = NY[e[1]];
            Color c = edgeColor(e[0], e[1]);
            for (float t : new float[]{0.25f, 0.50f, 0.75f}) {
                float cx = x1 + (x2 - x1) * t;
                float cy = y1 + (y2 - y1) * t;
                sr.setColor(c.r, c.g, c.b, 0.55f);
                sr.circle(cx, cy, 9.5f, 14);
            }
        }
        sr.end();
    }


    private Color edgeColor(int a, int b) {
        if (isTraveled(a, b))   return GOLD;
        if (isActiveEdge(a, b)) return PATH_ON;
        return PATH_OFF;
    }

    private void drawPlanets() {
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < NX.length; i++) {
            float cx = NX[i], cy = NY[i], r = NR[i];
            if (i < COL.length) {
                float[] c   = COL[i];
                float   dim = (i > currentIdx) ? 0.42f : 1f;
                for (int g = 5; g > 0; g--) {
                    sr.setColor(c[0], c[1], c[2], 0.028f * g * dim);
                    sr.circle(cx, cy, r + g * 8f, 24);
                }
                sr.setColor(c[3] * dim * 0.45f, c[4] * dim * 0.45f, c[5] * dim * 0.45f, 1f);
                sr.circle(cx, cy, r, 32);
                sr.setColor(c[0] * dim * 0.38f, c[1] * dim * 0.38f, c[2] * dim * 0.38f, 1f);
                sr.circle(cx, cy, r * 0.66f, 32);
                sr.setColor(1f, 1f, 1f, 0.06f * dim);
                sr.circle(cx - r * 0.22f, cy + r * 0.22f, r * 0.35f, 18);
            } else {
                sr.setColor(0.07f, 0.08f, 0.12f, 1f);
                sr.circle(cx, cy, r, 32);
                // Padlock body
                sr.setColor(0.42f, 0.42f, 0.48f, 0.78f);
                sr.rect(cx - 8f, cy - 9f, 16f, 11f);
            }
        }
        sr.end();

        sr.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < NX.length; i++) {
            float cx = NX[i], cy = NY[i], r = NR[i];
            if (i >= COL.length) {
                sr.setColor(0.70f, 0.13f, 0.13f, 1f);
                sr.circle(cx, cy, r, 32);
                sr.circle(cx, cy, r + 2.5f, 32);
                sr.setColor(0.42f, 0.42f, 0.48f, 0.78f);
                sr.arc(cx, cy + 2f, 8f, 0f, 180f);
            }
        }
        sr.end();

        // Arrival-ready green ring on current planet
        if (ShipData.get().arrivalReady && currentIdx < COL.length) {
            float cx = NX[currentIdx], cy = NY[currentIdx], r = NR[currentIdx];
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(0.20f, 1f, 0.40f, 0.80f);
            sr.circle(cx, cy, r + 5f, 32);
            sr.circle(cx, cy, r + 9f, 32);
            sr.end();
        }

        // Pulse ring on current active planet
        if (currentIdx < COL.length) {
            float pulse = 1.0f + 0.08f * com.badlogic.gdx.math.MathUtils.sin(animTime * com.badlogic.gdx.math.MathUtils.PI);
            float pr = NR[currentIdx] * pulse + 6f;
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(OdysseyTheme.ACCENT_E.r, OdysseyTheme.ACCENT_E.g, OdysseyTheme.ACCENT_E.b, 0.40f);
            sr.circle(NX[currentIdx], NY[currentIdx], pr, 32);
            sr.end();
        }

        // Checkpoint progress arc on current planet
        int sectorReached = ShipData.get().sectorReached;
        if (currentIdx < COL.length && sectorReached >= 0) {
            float arcFraction = (sectorReached + 1) / 4f;
            float arcR = NR[currentIdx] + 4f;
            float startAngle = 90f;
            float sweepAngle = 360f * arcFraction;
            int segments = Math.max(4, (int)(sweepAngle / 6f));
            float prevX = NX[currentIdx] + arcR * com.badlogic.gdx.math.MathUtils.cosDeg(startAngle);
            float prevY = NY[currentIdx] + arcR * com.badlogic.gdx.math.MathUtils.sinDeg(startAngle);
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(OdysseyTheme.ACCENT_GO.r, OdysseyTheme.ACCENT_GO.g, OdysseyTheme.ACCENT_GO.b, 0.80f);
            for (int s = 1; s <= segments; s++) {
                float ang = startAngle - sweepAngle * s / segments;
                float nx2 = NX[currentIdx] + arcR * com.badlogic.gdx.math.MathUtils.cosDeg(ang);
                float ny2 = NY[currentIdx] + arcR * com.badlogic.gdx.math.MathUtils.sinDeg(ang);
                sr.line(prevX, prevY, nx2, ny2);
                prevX = nx2; prevY = ny2;
            }
            sr.end();
        }

        drawPlanetSymbols();
    }

    /** Per-planet icon drawn inside the planet circle. */
    private void drawPlanetSymbols() {
        // Per-planet pulse (each offset in phase for organic feel)
        float[] pulse = new float[5];
        for (int i = 0; i < 5; i++)
            pulse[i] = 0.65f + 0.35f * MathUtils.sin(animTime * 2.0f + i * 1.1f);

        // ── Ambient glow halos behind each active planet symbol ───────────────
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < Math.min(NX.length, COL.length); i++) {
            float cx = NX[i], cy = NY[i], r = NR[i];
            if (i > currentIdx) continue;
            float p = pulse[i];
            float[] c = COL[i];
            sr.setColor(c[0] * 0.5f, c[1] * 0.5f, c[2] * 0.5f, 0.09f * p);
            sr.circle(cx, cy, r * 0.62f, 22);
            sr.setColor(c[0] * 0.7f, c[1] * 0.7f, c[2] * 0.7f, 0.14f * p);
            sr.circle(cx, cy, r * 0.38f, 16);
        }

        // ── Filled symbols: heart (Cryon Reach) + flame (Helios Forge) ────────
        for (int i = 0; i < Math.min(NX.length, COL.length); i++) {
            float cx = NX[i], cy = NY[i], r = NR[i];
            float dim = (i > currentIdx) ? 0.30f : 1.00f;
            float p = pulse[i];
            switch (i) {
                case 3: { // Cryon Reach — heart
                    float hr  = r * 0.27f;
                    float bcy = cy + hr * 0.45f;
                    // soft glow behind heart
                    sr.setColor(1f, 0.28f, 0.48f, 0.18f * dim * p);
                    sr.circle(cx, bcy - hr * 0.3f, hr * 2.3f, 18);
                    // main heart
                    sr.setColor(1f, 0.32f, 0.52f, 0.96f * dim);
                    sr.circle(cx - hr, bcy, hr, 16);
                    sr.circle(cx + hr, bcy, hr, 16);
                    sr.triangle(cx - hr * 1.90f, bcy,
                                cx + hr * 1.90f, bcy,
                                cx,              cy - hr * 1.65f);
                    break;
                }
                case 4: { // Helios Forge — forge flame
                    float fh = r * 0.54f, fw = r * 0.38f;
                    // outer glow orb
                    sr.setColor(1f, 0.45f, 0.05f, 0.16f * dim * p);
                    sr.circle(cx, cy, r * 0.50f, 18);
                    // outer flame
                    sr.setColor(1f, 0.52f, 0.10f, 0.90f * dim);
                    sr.triangle(cx, cy + fh, cx - fw, cy - fh * 0.22f, cx + fw, cy - fh * 0.22f);
                    // inner bright flame
                    sr.setColor(1f, 0.96f, 0.48f, 0.97f * dim);
                    sr.triangle(cx, cy + fh * 0.62f,
                                cx - fw * 0.46f, cy - fh * 0.02f,
                                cx + fw * 0.46f, cy - fh * 0.02f);
                    break;
                }
            }
        }
        sr.end();

        // ── Line symbols: Solara, Ember IV, Frostheim — drawn twice ───────────
        // Pass 0 = glow (scaled up, low alpha); Pass 1 = sharp bright lines
        for (int pass = 0; pass < 2; pass++) {
            sr.begin(ShapeRenderer.ShapeType.Line);
            for (int i = 0; i < Math.min(NX.length, COL.length); i++) {
                float cx = NX[i], cy = NY[i], r = NR[i];
                float dim = (i > currentIdx) ? 0.30f : 1.00f;
                float p = pulse[i];
                float sc = (pass == 0) ? 1.10f : 1.00f;
                switch (i) {
                    case 0: { // Solara — centrifuge (two rings + 3 spinning spokes)
                        float or1 = r * 0.56f * sc, or2 = r * 0.27f * sc;
                        sr.setColor(1f, 0.95f, 0.55f, (pass == 0) ? 0.28f * dim * p : 0.96f * dim);
                        sr.circle(cx, cy, or1, 26);
                        sr.circle(cx, cy, or2, 16);
                        for (int s = 0; s < 3; s++) {
                            float ang = s * (float)(Math.PI * 2.0 / 3.0) + animTime * 0.55f;
                            float cos = (float)Math.cos(ang), sin = (float)Math.sin(ang);
                            sr.line(cx + cos * or2, cy + sin * or2,
                                    cx + cos * or1, cy + sin * or1);
                        }
                        break;
                    }
                    case 1: { // Ember IV — diamond (rotated square + inner cross)
                        float hs = r * 0.52f * sc;
                        sr.setColor(1f, 0.62f, 1f, (pass == 0) ? 0.26f * dim * p : 0.96f * dim);
                        sr.line(cx,      cy + hs, cx + hs, cy);
                        sr.line(cx + hs, cy,      cx,      cy - hs);
                        sr.line(cx,      cy - hs, cx - hs, cy);
                        sr.line(cx - hs, cy,      cx,      cy + hs);
                        sr.setColor(1f, 0.62f, 1f, (pass == 0) ? 0.14f * dim * p : 0.58f * dim);
                        sr.line(cx - hs, cy,      cx + hs, cy);
                        sr.line(cx,      cy - hs, cx,      cy + hs);
                        break;
                    }
                    case 2: { // Frostheim — 6-arm snowflake with branches
                        float armLen = r * 0.56f * sc;
                        sr.setColor(0.70f, 0.97f, 1f, (pass == 0) ? 0.26f * dim * p : 0.98f * dim);
                        float baseAngle = animTime * 0.12f;
                        for (int s = 0; s < 6; s++) {
                            float ang = s * (float)(Math.PI / 3.0) + baseAngle;
                            float cos = (float)Math.cos(ang), sin = (float)Math.sin(ang);
                            float ex = cx + cos * armLen, ey = cy + sin * armLen;
                            sr.line(cx, cy, ex, ey);
                            for (float t : new float[]{0.44f, 0.68f}) {
                                float bx = cx + cos * armLen * t;
                                float by = cy + sin * armLen * t;
                                float blen = armLen * 0.28f;
                                float perp = ang + (float)(Math.PI / 2.0);
                                float pc = (float)Math.cos(perp) * blen;
                                float ps = (float)Math.sin(perp) * blen;
                                sr.line(bx, by, bx + pc, by + ps);
                                sr.line(bx, by, bx - pc, by - ps);
                            }
                        }
                        break;
                    }
                }
            }
            sr.end();
        }
    }

    private void drawRocket() {
        float rx = rocketX, ry = rocketY, a = rocketAngle;
        float ca = (float)Math.cos(a), sa = (float)Math.sin(a);
        float u = 1.30f;
        // Local-to-world: point at (fwd, right) → (rx+fwd*ca+right*sa, ry+fwd*sa-right*ca)

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Glow halos
        sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.07f);
        sr.circle(rx, ry, ROCKET_HIT + 10f, 24);
        sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.13f);
        sr.circle(rx, ry, ROCKET_HIT, 24);

        float t1 = 0.70f + 0.30f * (float)Math.sin(animTime * 18f);
        float t2 = 0.75f + 0.25f * (float)Math.sin(animTime * 25f + 0.9f);
        float baseF = -u * 18f; // engine bell exit

        // Outer flame (yellow, wide)
        { float hw = u*8.5f*t1, tip = baseF - u*22f*t1;
          sr.setColor(1f, 0.85f, 0.15f, 0.45f*t1);
          sr.triangle(rx+baseF*ca-hw*sa, ry+baseF*sa+hw*ca,
                      rx+baseF*ca+hw*sa, ry+baseF*sa-hw*ca,
                      rx+tip*ca,         ry+tip*sa); }

        // Mid flame (orange)
        { float hw = u*5f, tip = baseF - u*16f*t2;
          sr.setColor(1f, 0.50f, 0.08f, 0.88f);
          sr.triangle(rx+baseF*ca-hw*sa, ry+baseF*sa+hw*ca,
                      rx+baseF*ca+hw*sa, ry+baseF*sa-hw*ca,
                      rx+tip*ca,         ry+tip*sa); }

        // Core flame (white-hot)
        { float hw = u*2.2f, tip = baseF - u*8f*t1;
          sr.setColor(1f, 0.97f, 0.88f, 1f);
          sr.triangle(rx+baseF*ca-hw*sa, ry+baseF*sa+hw*ca,
                      rx+baseF*ca+hw*sa, ry+baseF*sa-hw*ca,
                      rx+tip*ca,         ry+tip*sa); }

        // Left swept fin
        sr.setColor(0.18f, 0.52f, 0.78f, 1f);
        sr.triangle(rx + u*3f*ca  - u*9f*sa,  ry + u*3f*sa  + u*9f*ca,
                    rx - u*16f*ca - u*9f*sa,  ry - u*16f*sa + u*9f*ca,
                    rx - u*19f*ca - u*22f*sa, ry - u*19f*sa + u*22f*ca);

        // Right swept fin
        sr.setColor(0.18f, 0.52f, 0.78f, 1f);
        sr.triangle(rx + u*3f*ca  + u*9f*sa,  ry + u*3f*sa  - u*9f*ca,
                    rx - u*16f*ca + u*9f*sa,  ry - u*16f*sa - u*9f*ca,
                    rx - u*19f*ca + u*22f*sa, ry - u*19f*sa - u*22f*ca);

        // Engine bell (trapezoid = 2 triangles)
        { float bx1=rx-u*13f*ca-u*8f*sa,  by1=ry-u*13f*sa+u*8f*ca,
                bx2=rx-u*13f*ca+u*8f*sa,  by2=ry-u*13f*sa-u*8f*ca,
                bx3=rx-u*18f*ca-u*11f*sa, by3=ry-u*18f*sa+u*11f*ca,
                bx4=rx-u*18f*ca+u*11f*sa, by4=ry-u*18f*sa-u*11f*ca;
          sr.setColor(0.36f, 0.38f, 0.50f, 1f);
          sr.triangle(bx1,by1,bx2,by2,bx3,by3);
          sr.triangle(bx2,by2,bx3,by3,bx4,by4); }

        // Body (rectangle = 2 triangles)
        { float bw=u*9f;
          float px1=rx+u*13f*ca-bw*sa, py1=ry+u*13f*sa+bw*ca,
                px2=rx+u*13f*ca+bw*sa, py2=ry+u*13f*sa-bw*ca,
                px3=rx-u*13f*ca-bw*sa, py3=ry-u*13f*sa+bw*ca,
                px4=rx-u*13f*ca+bw*sa, py4=ry-u*13f*sa-bw*ca;
          sr.setColor(0.80f, 0.86f, 1.00f, 1f);
          sr.triangle(px1,py1,px2,py2,px3,py3);
          sr.triangle(px2,py2,px3,py3,px4,py4); }

        // Nose cone
        sr.setColor(0.50f, 0.74f, 1.00f, 1f);
        sr.triangle(rx+u*28f*ca,              ry+u*28f*sa,
                    rx+u*13f*ca-u*9f*sa,      ry+u*13f*sa+u*9f*ca,
                    rx+u*13f*ca+u*9f*sa,      ry+u*13f*sa-u*9f*ca);

        // Accent stripe (cyan band across body mid-section)
        { float sw=u*9f;
          float sx1=rx+u*6f*ca-sw*sa, sy1=ry+u*6f*sa+sw*ca,
                sx2=rx+u*6f*ca+sw*sa, sy2=ry+u*6f*sa-sw*ca,
                sx3=rx+u*2f*ca-sw*sa, sy3=ry+u*2f*sa+sw*ca,
                sx4=rx+u*2f*ca+sw*sa, sy4=ry+u*2f*sa-sw*ca;
          sr.setColor(0.22f, 0.72f, 1.00f, 0.55f);
          sr.triangle(sx1,sy1,sx2,sy2,sx3,sy3);
          sr.triangle(sx2,sy2,sx3,sy3,sx4,sy4); }

        // Porthole window
        float pcx=rx+u*18f*ca, pcy=ry+u*18f*sa;
        sr.setColor(0.05f, 0.08f, 0.20f, 1f);
        sr.circle(pcx, pcy, u*4.2f, 12);
        sr.setColor(0.28f, 0.82f, 1.00f, 0.82f);
        sr.circle(pcx, pcy, u*2.5f, 10);

        sr.end();
    }

    private void drawTitle() {
        titleFont.getData().setScale(4.2f);
        titleFont.setColor(CYAN);
        titleFont.draw(batch, "GALACTIC MAP", 0f, viewport.getWorldHeight() - 18f, W, Align.center, false);
    }

    private float statsY() { return viewport.getWorldHeight() - 96f; }

    private void drawTopStatsBg() {
        float sy = statsY();
        float pulse  = 0.5f + 0.5f * MathUtils.sin(animTime * 1.8f);
        float pulse2 = 0.5f + 0.5f * MathUtils.sin(animTime * 2.4f + 1.0f);

        // Panel: sy+8 (top) → sy-62 (bottom) = 70px; row1 at sy, row2 at sy-32
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int g = 8; g > 0; g--) {
            float ex = g * 4f;
            sr.setColor(0.10f, 0.90f, 0.28f, 0.016f * g * pulse);
            sr.rect(20f - ex, sy - 62f - ex, W - 40f + ex*2f, 70f + ex*2f);
        }
        sr.setColor(0.02f, 0.07f, 0.14f, 0.94f);
        sr.rect(20f, sy - 62f, W - 40f, 70f);
        // Left accent (lives)
        sr.setColor(0.85f, 0.15f, 0.25f, 0.14f * pulse2);
        sr.rect(20f, sy - 62f, 120f, 70f);
        // Right accent (gems)
        sr.setColor(0.08f, 0.78f, 1.00f, 0.14f * pulse);
        sr.rect(W - 140f, sy - 62f, 120f, 70f);
        // Row divider
        sr.setColor(0.14f, 0.55f, 0.22f, 0.42f);
        sr.rect(28f, sy - 20f, W - 56f, 1.4f);
        sr.end();

        // Green border
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.20f, 1.00f, 0.38f, 0.65f + 0.30f * pulse);
        sr.rect(20f, sy - 62f, W - 40f, 70f);
        sr.setColor(0.10f, 0.70f, 0.22f, 0.28f + 0.18f * pulse2);
        sr.rect(22f, sy - 60f, W - 44f, 66f);
        sr.end();

        // ── Heart icon (lives, row 1 left) ────────────────────────────────────
        float hx = 34f, hy = sy - 8f, hhr = 5.5f;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(1f, 0.28f, 0.40f, 0.92f);
        sr.circle(hx - hhr * 0.65f, hy + hhr * 0.25f, hhr * 0.72f, 10);
        sr.circle(hx + hhr * 0.65f, hy + hhr * 0.25f, hhr * 0.72f, 10);
        sr.triangle(hx - hhr * 1.30f, hy + hhr * 0.25f,
                    hx + hhr * 1.30f, hy + hhr * 0.25f,
                    hx,               hy - hhr * 1.20f);
        sr.end();

        // ── Gem icon (gems, row 1 right) ──────────────────────────────────────
        float gx = W - 34f, gy = sy - 8f, gs = 6.5f;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.38f, 0.92f, 1.00f, 0.92f);
        sr.triangle(gx, gy + gs, gx + gs, gy, gx, gy - gs);
        sr.triangle(gx, gy + gs, gx - gs, gy, gx, gy - gs);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.72f, 1.00f, 1.00f, 0.78f);
        sr.triangle(gx, gy + gs, gx + gs, gy, gx, gy - gs);
        sr.triangle(gx, gy + gs, gx - gs, gy, gx, gy - gs);
        sr.end();

        // ── Planet orbit icon (planets visited, row 2 center-left) ───────────
        float ox = W * 0.5f - 68f, oy = sy - 44f, or_ = 5.5f;
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.28f, 1.00f, 0.72f, 0.85f);
        sr.circle(ox, oy, or_, 14);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.28f, 1.00f, 0.72f, 0.80f);
        sr.circle(ox, oy, or_ * 0.38f, 8);
        sr.end();
    }

    private void drawTopStatsText() {
        ShipData sd = ShipData.get();
        float sy = statsY();
        smallFont.getData().setScale(1.45f);

        float charW = 10.5f;
        float gap   = 16f;   // space between label and value

        // ── Row 1: LIVES (left)  ·  GEMS (right) ─────────────────────────────
        // LIVES label then gap then value
        float livesLabelX = 46f;
        String livesVal = sd.lives + "/" + sd.maxLives;
        smallFont.setColor(0.40f, 0.55f, 0.68f, 0.80f);
        smallFont.draw(batch, "LIVES", livesLabelX, sy);
        smallFont.setColor(1f, 0.40f, 0.45f, 1f);
        smallFont.draw(batch, livesVal, livesLabelX + 5f * charW + gap, sy);

        // GEMS value then gap then label (right-anchored)
        String gemsVal = String.valueOf(sd.diamonds);
        float gemsValX = W - 46f - gemsVal.length() * charW;
        smallFont.setColor(0.38f, 0.92f, 1.00f, 1f);
        smallFont.draw(batch, gemsVal, gemsValX, sy);
        smallFont.setColor(0.40f, 0.55f, 0.68f, 0.80f);
        smallFont.draw(batch, "GEMS", gemsValX - gap - 4f * charW, sy);

        // ── Row 2: PLANETS VISITED (centered) ────────────────────────────────
        float ry = sy - 34f;
        String planetsVal = String.valueOf(sd.arrivalsCompleted);
        // center the whole "PLANETS  <val>" block around W/2
        float blockW = 7f * charW + gap + planetsVal.length() * charW;
        float blockX = W * 0.5f - blockW * 0.5f;
        smallFont.setColor(0.40f, 0.55f, 0.68f, 0.78f);
        smallFont.draw(batch, "PLANETS", blockX, ry);
        smallFont.setColor(0.28f, 0.96f, 0.72f, 0.92f);
        smallFont.draw(batch, planetsVal, blockX + 7f * charW + gap, ry);
    }

    private void drawLabels() {
        ShipData sd = ShipData.get();
        for (int i = 0; i < NX.length; i++) {
            float cx = NX[i], cy = NY[i], r = NR[i];
            if (i < ShipData.PLANETS.length) {
                String name   = ShipData.PLANETS[i].name;
                boolean dimmed = (i > currentIdx);
                bodyFont.setColor(dimmed ? DIM : Color.WHITE);
                float tw = name.length() * 7.2f;
                bodyFont.draw(batch, name, cx - tw * 0.5f, cy - r - 7f);

                // Show gem farm rate for each planet once farming is active (arrivalsCompleted >= 1)
                if (ShipData.get().arrivalsCompleted >= 1 && i < ShipData.GEM_FARM_RATES.length && i <= currentIdx) {
                    int rate = ShipData.GEM_FARM_RATES[i];
                    String rateStr = "+" + rate + " gem/hr";
                    smallFont.getData().setScale(0.85f);
                    smallFont.setColor(1.00f, 0.82f, 0.20f, 0.88f);
                    smallFont.draw(batch, rateStr, cx - 64f, cy + r + 28f, 128f, Align.center, false);
                    smallFont.getData().setScale(1.00f);
                }
            } else {
                smallFont.setColor(0.40f, 0.40f, 0.46f, 0.70f);
                smallFont.draw(batch, "COMING", cx - 20f, cy - r -  6f);
                smallFont.draw(batch, " SOON",  cx - 14f, cy - r - 18f);
            }
        }
        // ── ENTER BAY glowing badge ───────────────────────────────────────────
        float hintX    = (currentIdx % 2 == 0) ? rocketX + NR[currentIdx] + 6f : rocketX - 122f;
        float badgeX   = hintX;
        float badgeY   = rocketY - 8f;
        float badgeW   = 122f;
        float badgeH   = 22f;
        float bpulse   = 0.5f + 0.5f * MathUtils.sin(animTime * 2.6f);
        float bounce   = MathUtils.sin(animTime * 4.5f) * 2.8f;

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(viewport.getCamera().combined);

        // Outer glow layers
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int g = 7; g > 0; g--) {
            float ex = g * 3.2f;
            sr.setColor(0.10f, 0.95f, 0.88f, 0.017f * g * bpulse);
            sr.rect(badgeX - ex, badgeY - ex, badgeW + ex*2f, badgeH + ex*2f);
        }
        // Dark badge fill
        sr.setColor(0.02f, 0.09f, 0.20f, 0.90f);
        sr.rect(badgeX, badgeY, badgeW, badgeH);
        // Subtle cyan inner highlight strip at top
        sr.setColor(0.20f, 1.00f, 0.95f, 0.08f);
        sr.rect(badgeX + 1f, badgeY + badgeH - 4f, badgeW - 2f, 3f);
        // Animated >> arrow triangles (bounce right)
        float arrowMidY = badgeY + badgeH * 0.5f;
        float ah = 5.0f, aw = 5.5f;
        for (int a = 0; a < 2; a++) {
            float ax = badgeX + 7f + a * 9f + bounce;
            sr.setColor(0.30f, 1.00f, 0.92f, (0.70f + 0.30f * bpulse) - a * 0.18f);
            sr.triangle(ax,      arrowMidY + ah,
                        ax,      arrowMidY - ah,
                        ax + aw, arrowMidY);
        }
        sr.end();

        // Pulsing border
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.25f, 1.00f, 0.95f, 0.60f + 0.35f * bpulse);
        sr.rect(badgeX, badgeY, badgeW, badgeH);
        sr.setColor(0.15f, 0.80f, 0.75f, 0.20f);
        sr.rect(badgeX + 1f, badgeY + 1f, badgeW - 2f, badgeH - 2f);
        sr.end();

        batch.begin();
        smallFont.getData().setScale(1.10f);
        float textAlpha = 0.82f + 0.18f * bpulse;
        smallFont.setColor(0.85f, 1.00f, 0.98f, textAlpha);
        smallFont.draw(batch, "ENTER BAY", badgeX + 28f, badgeY + badgeH - 4f, badgeW - 30f, Align.center, false);
    }

    private void drawBottomBar() {
        ShipData sd = ShipData.get();
        float y = 46f;
        if (sd.arrivalReady) {
            smallFont.setColor(0.22f, 1f, 0.44f, 0.95f);
            smallFont.draw(batch, ">> ARRIVAL READY", 28f, y);
        } else if (!sd.unlimitedLives && sd.lives < sd.maxLives) {
            long secs = sd.secondsToNextLife();
            String timer = secs > 0
                ? String.format("Life in %d:%02d", secs / 60, secs % 60)
                : "Life ready!";
            smallFont.setColor(1f, 0.38f, 0.42f, 0.88f);
            smallFont.draw(batch, timer, 28f, y);
        }
    }

    private void drawShopButton() {
        float pulse  = 0.5f + 0.5f * MathUtils.sin(animTime * 2.2f);
        float pulse2 = 0.5f + 0.5f * MathUtils.sin(animTime * 3.4f + 0.8f);
        sr.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float sy = shopY();

        sr.begin(ShapeRenderer.ShapeType.Filled);
        // Amber/gold outer glow layers
        for (int g = 8; g > 0; g--) {
            float ex = g * 3.8f;
            sr.setColor(1.00f, 0.70f, 0.08f, 0.018f * g * pulse);
            sr.rect(SH_X - ex, sy - ex, SH_W + ex*2f, SH_H + ex*2f);
        }
        // Dark fill with warm amber tint
        sr.setColor(0.14f, 0.08f, 0.02f, 0.92f);
        sr.rect(SH_X, sy, SH_W, SH_H);
        // Inner amber gradient band (top highlight)
        sr.setColor(1.00f, 0.75f, 0.15f, 0.10f + 0.08f * pulse);
        sr.rect(SH_X + 2f, sy + SH_H - 7f, SH_W - 4f, 5f);
        // Coin icon — filled circle left side
        float cx = SH_X + 18f, cy = sy + SH_H * 0.5f, cr = 7.5f;
        // Coin glow
        sr.setColor(1.00f, 0.82f, 0.10f, 0.22f * pulse);
        sr.circle(cx, cy, cr * 1.8f, 16);
        // Coin body
        sr.setColor(1.00f, 0.80f, 0.08f, 0.92f);
        sr.circle(cx, cy, cr, 20);
        // Coin inner highlight
        sr.setColor(1.00f, 0.98f, 0.72f, 0.45f);
        sr.circle(cx - cr * 0.18f, cy + cr * 0.25f, cr * 0.45f, 12);
        sr.end();

        // Bright animated border
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(1.00f, 0.82f, 0.20f, 0.70f + 0.28f * pulse);
        sr.rect(SH_X, sy, SH_W, SH_H);
        sr.setColor(1.00f, 0.62f, 0.08f, 0.22f + 0.15f * pulse2);
        sr.rect(SH_X + 1f, sy + 1f, SH_W - 2f, SH_H - 2f);
        // Coin rim
        sr.setColor(1.00f, 0.95f, 0.55f, 0.60f);
        sr.circle(cx, cy, cr, 20);
        sr.end();
    }

    private void drawShopLabel() {
        float sy    = shopY();
        float pulse = 0.5f + 0.5f * MathUtils.sin(animTime * 2.2f);
        smallFont.getData().setScale(1.35f);
        smallFont.setColor(1.00f, 0.88f, 0.30f, 0.88f + 0.12f * pulse);
        smallFont.draw(batch, "SHOP", SH_X + 30f, sy + SH_H - 9f, SH_W - 32f, Align.center, false);
        smallFont.getData().setScale(1.00f);
    }

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

    private void drawLeaderboardLabel() {
        float ly    = lbY();
        float pulse = 0.5f + 0.5f * MathUtils.sin(animTime * 1.9f + 0.4f);
        smallFont.getData().setScale(1.15f);
        smallFont.setColor(0.55f, 0.88f, 1.00f, 0.88f + 0.12f * pulse);
        smallFont.draw(batch, "RANKS", LB_X + 4f, ly + LB_H - 10f, LB_W - 8f, Align.center, false);
        smallFont.getData().setScale(1.00f);
    }

    private void drawNewGameButton() {
        sr.setProjectionMatrix(viewport.getCamera().combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.70f, 0.10f, 0.10f, 0.88f);
        sr.rect(NG_X, NG_Y, NG_W, NG_H);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(1f, 0.35f, 0.35f, 0.90f);
        sr.rect(NG_X, NG_Y, NG_W, NG_H);
        sr.end();
    }

    private void drawNewGameLabel() {
        smallFont.setColor(1f, 0.72f, 0.72f, 1f);
        smallFont.draw(batch, "NEW GAME", NG_X, NG_Y + NG_H - 10f, NG_W, Align.center, false);
        smallFont.setColor(0.72f, 0.72f, 0.72f, 0.75f);
        smallFont.draw(batch, "reset all progress", NG_X, NG_Y + NG_H - 23f, NG_W, Align.center, false);
    }

    // Tutorial card bounds — shared between bg and text passes
    private float tutCardX, tutCardY, tutCardW = 430f, tutCardH = 190f;

    private void calcTutCardPos() {
        tutCardX = (W - tutCardW) * 0.5f;
        tutCardY = viewport.getWorldHeight() * 0.5f - tutCardH * 0.5f;
    }

    private void drawRocketTutorialBg() {
        calcTutCardPos();
        sr.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float bp   = 0.60f + 0.30f * com.badlogic.gdx.math.MathUtils.sin(animTime * 1.6f);
        float rp   = 0.28f + 0.28f * com.badlogic.gdx.math.MathUtils.sin(animTime * 3.8f);
        float cx   = tutCardX + tutCardW * 0.5f;

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Outer glow layers
        for (int g = 5; g > 0; g--) {
            float ex = g * 4.5f;
            sr.setColor(0.10f, 0.50f, 1.00f, 0.022f * g * bp);
            sr.rect(tutCardX - ex, tutCardY - ex, tutCardW + ex * 2f, tutCardH + ex * 2f);
        }

        // Dark background
        sr.setColor(0.02f, 0.04f, 0.15f, 0.97f);
        sr.rect(tutCardX, tutCardY, tutCardW, tutCardH);

        // Inner highlight panel (slightly lighter)
        sr.setColor(0.06f, 0.11f, 0.26f, 0.55f);
        sr.rect(tutCardX + 6f, tutCardY + 6f, tutCardW - 12f, tutCardH - 12f);

        // Horizontal divider between title and body
        float divY = tutCardY + tutCardH - 52f;
        sr.setColor(0.18f, 0.70f, 1.00f, 0.40f);
        sr.rect(tutCardX + 30f, divY, tutCardW - 60f, 1.5f);
        // Diamond on divider
        float dia = 5f;
        sr.setColor(0.25f, 0.92f, 1.00f, 0.95f);
        // draw rotated square as 4 triangles
        sr.triangle(cx, divY + dia, cx + dia, divY, cx, divY - dia + 1.5f);
        sr.triangle(cx, divY + dia, cx - dia, divY, cx, divY - dia + 1.5f);

        // Pulsing rocket glow rings
        sr.setColor(0.15f, 0.85f, 1.00f, rp * 0.25f);
        sr.circle(rocketX, rocketY, ROCKET_HIT + 24f, 28);
        sr.setColor(0.15f, 0.85f, 1.00f, rp * 0.55f);
        sr.circle(rocketX, rocketY, ROCKET_HIT + 14f, 28);

        sr.end();

        sr.begin(ShapeRenderer.ShapeType.Line);

        // Outer border (animated)
        sr.setColor(0.20f, 0.78f, 1.00f, bp * 0.85f);
        sr.rect(tutCardX, tutCardY, tutCardW, tutCardH);
        // Inner border (dimmer)
        sr.setColor(0.15f, 0.55f, 0.90f, bp * 0.30f);
        sr.rect(tutCardX + 5f, tutCardY + 5f, tutCardW - 10f, tutCardH - 10f);

        // Pulsing rocket ring outline
        sr.setColor(0.20f, 0.92f, 1.00f, rp * 0.85f);
        sr.circle(rocketX, rocketY, ROCKET_HIT + 14f, 28);

        sr.end();

        // HUD corner brackets
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.25f, 0.92f, 1.00f, 0.95f);
        float bl = 22f, bt = 3f;
        // top-left
        sr.rect(tutCardX,              tutCardY + tutCardH - bt, bl, bt);
        sr.rect(tutCardX,              tutCardY + tutCardH - bl, bt, bl);
        // top-right
        sr.rect(tutCardX + tutCardW - bl, tutCardY + tutCardH - bt, bl, bt);
        sr.rect(tutCardX + tutCardW - bt, tutCardY + tutCardH - bl, bt, bl);
        // bottom-left
        sr.rect(tutCardX,              tutCardY, bl, bt);
        sr.rect(tutCardX,              tutCardY, bt, bl);
        // bottom-right
        sr.rect(tutCardX + tutCardW - bl, tutCardY, bl, bt);
        sr.rect(tutCardX + tutCardW - bt, tutCardY, bt, bl);
        sr.end();
    }

    private void drawRocketTutorialText() {
        float pulse = 0.50f + 0.50f * com.badlogic.gdx.math.MathUtils.sin(animTime * 2.6f);

        // Title — large, cyan
        bodyFont.setColor(0.25f, 0.95f, 1.00f, 1f);
        bodyFont.draw(batch, "WELCOME, CAPTAIN",
            tutCardX + 8f, tutCardY + tutCardH - 12f, tutCardW - 16f, Align.center, false);

        // Body lines — centered, just below divider
        smallFont.setColor(0.80f, 0.88f, 1.00f, 0.92f);
        smallFont.draw(batch, "Tap the rocket ship to enter the",
            tutCardX + 8f, tutCardY + tutCardH - 70f, tutCardW - 16f, Align.center, false);
        smallFont.draw(batch, "Engineering Bay and start your mission.",
            tutCardX + 8f, tutCardY + tutCardH - 87f, tutCardW - 16f, Align.center, false);

        // Sub-hint
        smallFont.setColor(0.55f, 0.65f, 0.80f, 0.75f);
        smallFont.draw(batch, "Hire orbs · collect energy · travel the galaxy",
            tutCardX + 8f, tutCardY + tutCardH - 108f, tutCardW - 16f, Align.center, false);

        // Pulsing CTA
        smallFont.setColor(0.22f, 1.00f, 0.52f, pulse);
        smallFont.draw(batch, ">> TAP THE ROCKET TO BEGIN <<",
            tutCardX, tutCardY + 18f, tutCardW, Align.center, false);
    }

    @Override public void resize(int w, int h) {
        viewport.update(w, h, true);
        if (stage != null) stage.getViewport().update(w, h, true);
    }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
        if (stage != null) stage.dispose();
        // fonts owned by skin — do not dispose here
    }
}
