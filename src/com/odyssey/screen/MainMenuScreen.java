package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.OdysseyTheme;
import com.odyssey.ShipData;

public class MainMenuScreen extends ScreenAdapter {

    private static final float W = 480f, H = 854f;

    // Planet node positions and sizes (0-4 = game planets, 5-6 = locked)
    private static final float[] NX = {130, 350, 118, 352, 118, 338, 228};
    private static final float[] NY = {120, 230, 345, 460, 570, 660, 750};
    private static final float[] NR = { 40,  36,  34,  32,  30,  27,  27};

    // Zigzag path through 5 game planets + locked branch
    private static final int[][] EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {4, 6}, {5, 6}
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

    private final OdysseyGame   game;
    private final FitViewport   viewport;
    private final ShapeRenderer sr;
    private final SpriteBatch   batch;
    private final BitmapFont    titleFont, bodyFont, smallFont;

    // Pre-baked starfield
    private final float[] starX, starY, starA;

    private float rocketX, rocketY, rocketAngle;
    private int   currentIdx;
    private float animTime = 0f;
    private final Vector3 tv = new Vector3();

    public MainMenuScreen(OdysseyGame game) {
        this.game     = game;
        this.viewport = new FitViewport(W, H);
        this.sr       = new ShapeRenderer();
        this.batch    = new SpriteBatch();

        titleFont = new BitmapFont(); titleFont.getData().setScale(2.2f);
        bodyFont  = new BitmapFont(); bodyFont.getData().setScale(0.90f);
        smallFont = new BitmapFont(); smallFont.getData().setScale(0.70f);

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
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
                tv.set(sx, sy, 0);
                viewport.unproject(tv);
                float dx = tv.x - rocketX, dy = tv.y - rocketY;
                if (dx*dx + dy*dy < ROCKET_HIT * ROCKET_HIT) {
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
                    game.transitionTo(GameState.ENGINEERING_LAB);
                    return true;
                }
                return false;
            }
        });
    }

    private void refresh() {
        ShipData sd = ShipData.get();
        currentIdx = Math.min(sd.currentPlanetIndex, NX.length - 1);
        int next   = Math.min(currentIdx + 1, NX.length - 1);

        // Map sectorReached (-1..2) to a position along the current edge.
        // Checkpoint dots are at t = 0.25, 0.50, 0.75.
        float t;
        if (sd.arrivalReady) {
            t = 1.0f; // show at destination planet
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

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawTitle();
        drawLabels();
        drawBottomBar();
        drawNewGameLabel();
        batch.end();
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
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0f, 0.65f, 0.75f, 0.10f);
        float cw = 28f;
        for (float y = 0; y < H; y += cw) {
            sr.line(0, y, 64f, y);
            sr.line(W - 64f, y, W, y);
        }
        for (float x = 0; x <= 64f; x += cw)    sr.line(x, 0, x, H);
        for (float x = W - 64f; x <= W; x += cw) sr.line(x, 0, x, H);
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
                sr.setColor(c[3] * dim, c[4] * dim, c[5] * dim, 1f);
                sr.circle(cx, cy, r, 32);
                sr.setColor(c[0] * dim, c[1] * dim, c[2] * dim, 1f);
                sr.circle(cx, cy, r * 0.66f, 32);
                sr.setColor(1f, 1f, 1f, 0.14f * dim);
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
    }

    private void drawRocket() {
        float rx = rocketX, ry = rocketY, a = rocketAngle;
        float sz = 22f; // bigger rocket

        sr.begin(ShapeRenderer.ShapeType.Filled);
        // Tap-hint glow
        sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.07f);
        sr.circle(rx, ry, ROCKET_HIT + 10f, 24);
        sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.12f);
        sr.circle(rx, ry, ROCKET_HIT, 24);

        // Rocket body
        float tipX = rx + (float)Math.cos(a) * sz * 1.5f,
              tipY = ry + (float)Math.sin(a) * sz * 1.5f;
        float lx   = rx + (float)Math.cos(a + 2.2f) * sz,
              ly   = ry + (float)Math.sin(a + 2.2f) * sz;
        float ex   = rx + (float)Math.cos(a - 2.2f) * sz,
              ey   = ry + (float)Math.sin(a - 2.2f) * sz;
        sr.setColor(0.88f, 0.93f, 1f, 1f);
        sr.triangle(tipX, tipY, lx, ly, ex, ey);

        // Exhaust core
        float bx = rx + (float)Math.cos(a + Math.PI) * sz * 0.9f,
              by = ry + (float)Math.sin(a + Math.PI) * sz * 0.9f;
        sr.setColor(1f, 0.58f, 0.10f, 0.95f);
        sr.triangle(lx, ly, ex, ey, bx, by);

        // Flame tongue
        float fx = rx + (float)Math.cos(a + Math.PI) * sz * 2.1f,
              fy = ry + (float)Math.sin(a + Math.PI) * sz * 2.1f;
        sr.setColor(1f, 0.90f, 0.22f, 0.70f);
        sr.triangle(
            rx + (float)Math.cos(a + Math.PI + 0.38f) * sz * 0.5f,
            ry + (float)Math.sin(a + Math.PI + 0.38f) * sz * 0.5f,
            rx + (float)Math.cos(a + Math.PI - 0.38f) * sz * 0.5f,
            ry + (float)Math.sin(a + Math.PI - 0.38f) * sz * 0.5f,
            fx, fy
        );
        sr.end();
    }

    private void drawTitle() {
        titleFont.setColor(CYAN);
        titleFont.draw(batch, "GALACTIC MAP", 0f, H - 22f, W, Align.center, false);
    }

    private void drawLabels() {
        for (int i = 0; i < NX.length; i++) {
            float cx = NX[i], cy = NY[i], r = NR[i];
            if (i < ShipData.PLANETS.length) {
                String name   = ShipData.PLANETS[i].name;
                boolean dimmed = (i > currentIdx);
                bodyFont.setColor(dimmed ? DIM : Color.WHITE);
                float tw = name.length() * 7.2f;
                bodyFont.draw(batch, name, cx - tw * 0.5f, cy - r - 7f);
            } else {
                smallFont.setColor(0.40f, 0.40f, 0.46f, 0.70f);
                smallFont.draw(batch, "COMING", cx - 20f, cy - r -  6f);
                smallFont.draw(batch, " SOON",  cx - 14f, cy - r - 18f);
            }
        }
        // Tap hint near rocket (offset so it doesn't overlap planet)
        smallFont.setColor(CYAN.r, CYAN.g, CYAN.b, 0.65f);
        float hintX = (currentIdx % 2 == 0) ? rocketX + NR[currentIdx] + 6f : rocketX - 108f;
        smallFont.draw(batch, "TAP ▶ ENTER BAY", hintX, rocketY + 6f);
    }

    private void drawBottomBar() {
        ShipData sd = ShipData.get();
        float y = 46f;
        smallFont.setColor(DIM);  smallFont.draw(batch, "Power:",    28f, y);
        smallFont.setColor(CYAN); smallFont.draw(batch, String.format("%.0f J", sd.totalJoules), 80f, y);
        smallFont.setColor(DIM);  smallFont.draw(batch, "Colonies:", 186f, y);
        smallFont.setColor(CYAN); smallFont.draw(batch, String.valueOf(sd.arrivalsCompleted), 256f, y);
        if (sd.arrivalReady) {
            smallFont.setColor(0.22f, 1f, 0.44f, 0.95f);
            smallFont.draw(batch, "✓ ARRIVAL READY", 302f, y);
        }
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

    @Override public void resize(int w, int h) { viewport.update(w, h, true); }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
        titleFont.dispose();
        bodyFont.dispose();
        smallFont.dispose();
    }
}
