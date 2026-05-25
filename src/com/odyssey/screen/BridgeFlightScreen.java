package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.OdysseyTheme;
import com.odyssey.ShipData;
import com.odyssey.SoundManager;

public class BridgeFlightScreen extends ScreenAdapter {

    // ---- Route constants -------------------------------------------------------

    private static final String[] SECTOR_NAMES = {
        "CHECKPOINT I", "CHECKPOINT II", "CHECKPOINT III", "ARRIVAL"
    };
    private static final String[] SECTOR_PERKS = {
        "Elastic Walls",
        "Gravity Wells + Extra Slots",
        "Wall x3 + Coll x2 + Free Intern",
        "Colony Landing"
    };
    private static final String[] SECTOR_PERK_DESCS = {
        "Interns bounce off walls with more force",
        "Unlock gravity wells · Bumper cap 4 · Intern cap 8",
        "Wall sparks x3 · Collision sparks x2 · Free intern added to bay",
        "Mission complete — colony established!"
    };
    public static final float[] CHECKPOINT_ENERGIES = {2_000f, 10_000f, 60_000f, 200_000f};
    public static final float ENERGY_AU_SCALE = 1.0f;

    // ---- Layout ----------------------------------------------------------------

    private static final float W       = 480f, H = 854f;
    private static final float LINE_Y  = H * 0.50f;
    private static final float LINE_X0 = 54f;
    private static final float LINE_X1 = 426f;
    private static final float LINE_LEN = LINE_X1 - LINE_X0;
    private static final float ROCKET_SPEED = 90f;
    private static final float BTN_W  = 340f, BTN_H = 62f;
    private static final float BTN_X  = (W - BTN_W) * 0.5f;
    private static final float BTN_Y  = 30f;
    private static final float U      = 0.82f; // rocket size scalar

    // ---- Fields ----------------------------------------------------------------

    private final OdysseyGame  game;
    private SpriteBatch        batch;
    private ShapeRenderer      sr;
    private OrthographicCamera cam;
    private FitViewport        viewport;
    private BitmapFont         font;
    private GlyphLayout        layout;
    private Texture            texPixel;
    private Texture            texGlow;

    // Parallax star field — 3 speed groups
    private static final int STARS = 130;
    private final float[] starX  = new float[STARS];
    private final float[] starY  = new float[STARS];
    private final float[] starSz = new float[STARS];
    private final float[] starA  = new float[STARS];
    private final float[] starV  = new float[STARS];

    // Animation
    private float   animTime     = 0f;
    private float   rocketX      = LINE_X0;
    private float   rocketTargetX= LINE_X0;
    private boolean animDone     = false;
    private float   landFlash    = 0f;
    private float   cpFlashTimer = 0f;
    private int     cpFlashIdx   = -1;

    // Flight data
    private int     prevSector;
    private int     newHighSector;
    private boolean newPerkReached;
    private float   totalRoute;
    private float[] sectorDists;
    private boolean arrived;
    private final Vector3 touchVec = new Vector3();

    // ---- Construction ----------------------------------------------------------

    public BridgeFlightScreen(OdysseyGame game) {
        this.game = game;
        batch    = new SpriteBatch();
        sr       = new ShapeRenderer();
        cam      = new OrthographicCamera();
        viewport = new FitViewport(W, H, cam);
        cam.position.set(W * 0.5f, H * 0.5f, 0f);
        font   = game.skin.getFont("float");
        layout = new GlyphLayout();
        texPixel = genPixel();
        texGlow  = genGlow(64);
        initStars();
    }

    private Texture genPixel() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(1f, 1f, 1f, 1f); p.fill();
        Texture t = new Texture(p); p.dispose(); return t;
    }

    private Texture genGlow(int sz) {
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0f, 0f, 0f, 0f); p.fill();
        float r = sz * 0.5f;
        for (int x = 0; x < sz; x++)
            for (int y = 0; y < sz; y++) {
                float d  = (float)Math.sqrt((x - r) * (x - r) + (y - r) * (y - r));
                float t2 = Math.max(0f, 1f - d / r);
                p.setColor(1f, 1f, 1f, t2 * t2 * 0.88f);
                p.drawPixel(x, y);
            }
        Texture t = new Texture(p); p.dispose(); return t;
    }

    private void initStars() {
        for (int i = 0; i < STARS; i++) {
            starX[i]  = MathUtils.random(0f, W);
            starY[i]  = MathUtils.random(0f, H);
            starSz[i] = MathUtils.random(1f, 3.2f);
            starA[i]  = MathUtils.random(0.25f, 0.95f);
            starV[i]  = MathUtils.random(6f, 40f);
        }
    }

    // ---- Reset -----------------------------------------------------------------

    public void resetFlight() {
        ShipData sd = ShipData.get();
        // Start the leaderboard timer on first flight toward this destination
        if (sd.flightStartTimeMs == 0L) {
            sd.flightStartTimeMs = System.currentTimeMillis();
        }
        animDone     = false;
        animTime     = 0f;
        cpFlashTimer = 0f;
        cpFlashIdx   = -1;
        prevSector   = sd.sectorReached;
        sectorDists  = buildSectorDistances(0f);
        totalRoute   = sectorDists[sectorDists.length - 1];
        arrived      = false;

        // accumulatedDist is the authoritative rocket position — derive nextSector from it
        float startDist = sd.accumulatedDist;
        rocketX = distToX(startDist);

        int nextSector = 0;
        for (int i = 0; i < sectorDists.length; i++)
            if (startDist >= sectorDists[i]) nextSector = i + 1;
        nextSector = Math.min(nextSector, sectorDists.length - 1);
        float targetDist = sectorDists[nextSector];
        float energyThisRun = sd.powerGenerated - sd.energyAtLastLaunch;
        sd.energyAtLastLaunch = sd.powerGenerated;
        float gained   = energyThisRun * ENERGY_AU_SCALE;
        float newAccum = Math.min(startDist + gained, targetDist);
        rocketTargetX  = distToX(newAccum);

        newHighSector = prevSector;
        for (int i = 0; i < sectorDists.length; i++)
            if (newAccum >= sectorDists[i]) newHighSector = i;

        newPerkReached     = newHighSector > prevSector;
        sd.accumulatedDist = newAccum;
        sd.sectorReached   = newHighSector;
        arrived = newAccum >= totalRoute;
    }

    private float distToX(float dist) {
        if (sectorDists == null || totalRoute <= 0f) return LINE_X0;
        int   n        = sectorDists.length;
        float segW     = LINE_LEN / n;
        float prevDist = 0f, segStart = 0f;
        for (int i = 0; i < n; i++) {
            float segEnd  = segStart + segW;
            float distEnd = sectorDists[i];
            if (dist <= distEnd || i == n - 1) {
                float t = (distEnd > prevDist)
                    ? Math.min((dist - prevDist) / (distEnd - prevDist), 1f) : 1f;
                return LINE_X0 + segStart + t * segW;
            }
            prevDist = distEnd;
            segStart = segEnd;
        }
        return LINE_X1;
    }

    private static final float[] FROSTHEIM_CHECKPOINT_ENERGIES = {4_000f, 24_000f, 120_000f, 150_000f};
    private static final float[] EMBER_CHECKPOINT_ENERGIES     = {5_000f, 60_000f, 300_000f, 250_000f};
    public static float[] buildSectorDistances(float ignored) {
        ShipData _sd = ShipData.get();
        int pidx = _sd.isReplayMode ? _sd.replayPlanetIndex : _sd.currentPlanetIndex;
        float[] e;
        if (pidx >= 2)      e = FROSTHEIM_CHECKPOINT_ENERGIES;  // Frostheim+
        else if (pidx >= 1) e = EMBER_CHECKPOINT_ENERGIES;      // Nova Terra
        else                e = CHECKPOINT_ENERGIES;             // Solara
        return new float[]{ e[0], e[0]+e[1], e[0]+e[1]+e[2], e[0]+e[1]+e[2]+e[3] };
    }

    // ---- Lifecycle -------------------------------------------------------------

    @Override public void show() { Gdx.input.setInputProcessor(null); }

    @Override
    public void render(float delta) {
        animTime += delta;
        if (cpFlashTimer > 0) cpFlashTimer -= delta;

        // Scroll stars left while flying
        if (!animDone) {
            for (int i = 0; i < STARS; i++) {
                starX[i] -= starV[i] * delta;
                if (starX[i] < -4f) starX[i] += W + 4f;
            }
        }

        // Advance rocket
        if (!animDone) {
            float prevX = rocketX;
            rocketX += ROCKET_SPEED * delta;
            if (rocketX >= rocketTargetX) {
                rocketX   = rocketTargetX;
                animDone  = true;
                landFlash = 0.45f;
                SoundManager.get().playCheckpoint();
            }
            // Trigger checkpoint flash when rocket crosses a node
            for (int i = 0; i < sectorDists.length; i++) {
                float nx = distToX(sectorDists[i]);
                if (prevX < nx && rocketX >= nx && i <= newHighSector) {
                    cpFlashTimer = 2.2f;
                    cpFlashIdx   = i;
                }
            }
        } else {
            if (Gdx.input.justTouched()) {
                touchVec.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                viewport.unproject(touchVec);
                if (touchVec.x >= BTN_X && touchVec.x <= BTN_X + BTN_W
                        && touchVec.y >= BTN_Y && touchVec.y <= BTN_Y + BTN_H) {
                    finish(); return;
                }
            }
        }

        // Clear to deep space
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        viewport.apply();
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        sr.setProjectionMatrix(cam.combined);

        // === Stars ===
        batch.begin();
        for (int i = 0; i < STARS; i++) {
            float sz = starSz[i];
            batch.setColor(0.80f, 0.88f, 1.00f, starA[i]);
            batch.draw(texPixel, starX[i], starY[i], sz, sz);
        }
        batch.end();

        // === Nebula-like large dim star clusters ===
        batch.begin();
        float nb1 = 0.25f + 0.12f * MathUtils.sin(animTime * 0.3f);
        float nb2 = 0.18f + 0.08f * MathUtils.sin(animTime * 0.5f + 1.1f);
        batch.setColor(0.15f, 0.25f, 0.55f, nb1);
        batch.draw(texGlow, W*0.6f - 90f, H*0.7f - 90f, 180f, 180f);
        batch.setColor(0.35f, 0.10f, 0.45f, nb2);
        batch.draw(texGlow, W*0.25f - 70f, H*0.3f - 70f, 140f, 140f);
        batch.end();

        // === Route line (ShapeRenderer) ===
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // Base dim line
        sr.setColor(0.12f, 0.15f, 0.25f, 1f);
        sr.rect(LINE_X0, LINE_Y - 2f, LINE_LEN, 4f);
        // Glow outer
        sr.setColor(0.10f, 0.45f, 0.25f, 0.30f);
        sr.rect(LINE_X0, LINE_Y - 4f, LINE_LEN, 8f);
        // Progress line
        if (rocketX > LINE_X0) {
            sr.setColor(0.18f, 0.90f, 0.48f, 1f);
            sr.rect(LINE_X0, LINE_Y - 2.5f, rocketX - LINE_X0, 5f);
            sr.setColor(0.35f, 1.00f, 0.65f, 0.30f);
            sr.rect(LINE_X0, LINE_Y - 5f, rocketX - LINE_X0, 10f);
        }
        sr.end();

        // === Text & icons ===
        batch.begin();

        // Title
        font.getData().setScale(1.90f);
        font.setColor(0.22f, 0.78f, 1.00f, 1f);
        drawCentered("INTERSTELLAR TRANSIT", H - 42f);

        // Destination
        font.getData().setScale(0.90f);
        font.setColor(0.58f, 0.64f, 0.82f, 0.82f);
        drawCentered(ShipData.get().getCurrentPlanet().name.toUpperCase()
            + "  ->  " + ShipData.get().getSelectedPlanet().name.toUpperCase(), H - 74f);

        // Energy stat
        font.getData().setScale(0.78f);
        font.setColor(1.00f, 0.85f, 0.32f, 0.88f);
        drawCentered(String.format("Energy generated: %.0f J", ShipData.get().powerGenerated), H - 100f);

        // Planet names at ends of route
        font.getData().setScale(0.78f);
        font.setColor(0.38f, 0.78f, 1.00f, 0.90f);
        font.draw(batch, ShipData.get().getCurrentPlanet().name.toUpperCase(), LINE_X0 - 4f, LINE_Y - 32f);
        font.setColor(1.00f, 0.55f, 0.20f, 0.90f);
        layout.setText(font, ShipData.get().getSelectedPlanet().name.toUpperCase());
        font.draw(batch, layout.toString(), LINE_X1 - layout.width + 4f, LINE_Y - 32f);

        font.getData().setScale(1f);

        // === Checkpoint nodes ===
        for (int i = 0; i < sectorDists.length; i++) {
            float nx    = distToX(sectorDists[i]);
            boolean past = rocketX >= nx;
            boolean flash = cpFlashIdx == i && cpFlashTimer > 0;
            float fp = flash ? Math.min(1f, cpFlashTimer / 0.5f) : 0f;

            // Outer glow halo
            if (past) {
                float gs = flash ? 68f + 14f * fp : 46f;
                float ga = flash ? 0.50f + 0.40f * fp : 0.28f;
                batch.setColor(0.18f, 1.00f, 0.52f, ga);
                batch.draw(texGlow, nx - gs*0.5f, LINE_Y - gs*0.5f, gs, gs);
            }

            // Inner node dot
            float nd = past ? 22f : 14f;
            if (past) batch.setColor(0.15f, 1.00f, 0.48f, 1f);
            else      batch.setColor(0.20f, 0.24f, 0.38f, 0.90f);
            batch.draw(texGlow, nx - nd*0.5f, LINE_Y - nd*0.5f, nd, nd);

            // Tick
            batch.setColor(past ? 0.18f : 0.15f, past ? 0.80f : 0.22f, past ? 0.50f : 0.30f, 0.50f);
            batch.draw(texPixel, nx - 1f, LINE_Y - 24f, 2f, 48f);

            // Name above node
            font.getData().setScale(past ? 0.80f : 0.68f);
            font.setColor(past ? 0.20f : 0.26f, past ? 1.00f : 0.28f, past ? 0.55f : 0.40f, past ? 1f : 0.60f);
            layout.setText(font, SECTOR_NAMES[i]);
            font.draw(batch, SECTOR_NAMES[i], nx - layout.width * 0.5f, LINE_Y + 54f);

            // Flash perk pop (rises up, fades in)
            if (flash && fp > 0) {
                float fy = LINE_Y + 72f + (1f - fp) * 24f;
                font.getData().setScale(0.90f);
                font.setColor(1f, 0.90f, 0.15f, fp);
                layout.setText(font, "+ " + SECTOR_PERKS[i]);
                font.draw(batch, "+ " + SECTOR_PERKS[i], nx - layout.width * 0.5f, fy);
            }
        }
        font.getData().setScale(1f);

        // === Speed trails behind rocket while moving ===
        if (!animDone) {
            for (int i = 0; i < 12; i++) {
                float tx = rocketX - U * 20f - i * 16f;
                float ta = (1f - i / 12f) * 0.55f;
                float th = Math.max(0.8f, 3.8f - i * 0.25f);
                batch.setColor(0.18f, 0.72f, 1.00f, ta);
                batch.draw(texPixel, tx, LINE_Y - th * 0.5f, 13f, th);
            }
        }

        batch.end();

        // === Rocket ===
        drawRocket(rocketX, LINE_Y);

        // === Result overlay (after animation) ===
        if (animDone) {
            batch.begin();

            font.getData().setScale(1.6f);
            if (arrived) {
                font.setColor(1f, 0.60f, 0.15f, 1f);
                drawCentered("ARRIVED AT " + ShipData.get().getSelectedPlanet().name.toUpperCase() + "!", H * 0.305f);
            } else if (newHighSector >= 0) {
                float pulse = 0.78f + 0.22f * MathUtils.sin(animTime * 2.8f);
                font.setColor(0.22f, 1f * pulse, 0.52f, 1f);
                drawCentered("CHECKPOINT " + (newHighSector + 1) + " REACHED!", H * 0.305f);
            } else {
                font.setColor(1f, 0.36f, 0.33f, 1f);
                drawCentered("NEED MORE ENERGY", H * 0.305f);
            }
            font.getData().setScale(1f);

            if (newPerkReached) {
                font.getData().setScale(1.08f);
                font.setColor(1.00f, 0.92f, 0.22f, 1f);
                drawCentered("UNLOCKED: " + SECTOR_PERKS[newHighSector], H * 0.230f);
                font.getData().setScale(0.82f);
                font.setColor(0.72f, 0.78f, 0.92f, 0.88f);
                drawCentered(SECTOR_PERK_DESCS[newHighSector], H * 0.180f);
            }

            // Button
            String btnText = arrived
                ? "LAND ON " + ShipData.get().getSelectedPlanet().name.toUpperCase() + "  >>"
                : (newHighSector >= 0 ? "CLAIM REWARD  &  RETURN  >>" : "RETURN TO BAY  >>");
            Color bc = arrived ? OdysseyTheme.ACCENT_WARN : OdysseyTheme.BTN_GO;
            batch.setColor(bc.r, bc.g, bc.b, 0.95f);
            batch.draw(texPixel, BTN_X, BTN_Y, BTN_W, BTN_H);
            batch.setColor(1f, 1f, 1f, 0.14f);
            batch.draw(texPixel, BTN_X, BTN_Y + BTN_H - 2f, BTN_W, 2f);
            font.getData().setScale(0.90f);
            font.setColor(1f, 1f, 1f, 1f);
            layout.setText(font, btnText);
            font.draw(batch, btnText, (W - layout.width) * 0.5f, BTN_Y + BTN_H * 0.62f);
            font.getData().setScale(1f);
            batch.end();
        }

        // Landing flash
        if (landFlash > 0f) {
            landFlash = Math.max(0f, landFlash - delta);
            float fa  = (landFlash / 0.45f) * 0.55f;
            batch.begin();
            batch.setColor(0.20f, 0.85f, 1.00f, fa);
            batch.draw(texPixel, 0, 0, W, H);
            batch.end();
        }
    }

    private void drawRocket(float cx, float cy) {
        // Rocket points RIGHT (a=0): wx = cx+fwd,  wy = cy−right
        float u  = U;
        float t1 = 0.70f + 0.30f * MathUtils.sin(animTime * 18f);
        float t2 = 0.75f + 0.25f * MathUtils.sin(animTime * 25f + 0.9f);
        float bf = -u * 18f; // flame base x offset

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        if (!animDone) {
            // Outer flame (yellow)
            float hw1 = u*8.5f*t1, tip1 = cx+bf - u*22f*t1;
            sr.setColor(1f, 0.85f, 0.15f, 0.45f*t1);
            sr.triangle(cx+bf, cy+hw1,  cx+bf, cy-hw1,  tip1, cy);
            // Middle flame (orange)
            float hw2 = u*5f, tip2 = cx+bf - u*16f*t2;
            sr.setColor(1f, 0.50f, 0.08f, 0.90f);
            sr.triangle(cx+bf, cy+hw2,  cx+bf, cy-hw2,  tip2, cy);
            // Core flame (white-hot)
            float hw3 = u*2.2f, tip3 = cx+bf - u*8f*t1;
            sr.setColor(1f, 0.97f, 0.88f, 1f);
            sr.triangle(cx+bf, cy+hw3,  cx+bf, cy-hw3,  tip3, cy);
        }

        // Upper swept fin (wy = cy + right since right < 0 means up)
        sr.setColor(0.18f, 0.52f, 0.78f, 1f);
        sr.triangle(cx+u*3f,  cy+u*9f,
                    cx-u*16f, cy+u*9f,
                    cx-u*19f, cy+u*22f);
        // Lower swept fin
        sr.triangle(cx+u*3f,  cy-u*9f,
                    cx-u*16f, cy-u*9f,
                    cx-u*19f, cy-u*22f);

        // Engine bell (trapezoid)
        sr.setColor(0.36f, 0.38f, 0.50f, 1f);
        sr.triangle(cx-u*13f, cy+u*8f,  cx-u*13f, cy-u*8f,  cx-u*18f, cy+u*11f);
        sr.triangle(cx-u*13f, cy-u*8f,  cx-u*18f, cy+u*11f, cx-u*18f, cy-u*11f);

        // Body (rectangle)
        sr.setColor(0.80f, 0.86f, 1.00f, 1f);
        sr.triangle(cx+u*13f, cy+u*9f,  cx+u*13f, cy-u*9f,  cx-u*13f, cy+u*9f);
        sr.triangle(cx+u*13f, cy-u*9f,  cx-u*13f, cy+u*9f,  cx-u*13f, cy-u*9f);

        // Nose cone
        sr.setColor(0.50f, 0.74f, 1.00f, 1f);
        sr.triangle(cx+u*28f, cy,  cx+u*13f, cy+u*9f,  cx+u*13f, cy-u*9f);

        // Accent stripe
        sr.setColor(0.22f, 0.72f, 1.00f, 0.52f);
        sr.triangle(cx+u*6f, cy+u*9f,  cx+u*6f, cy-u*9f,  cx+u*2f, cy+u*9f);
        sr.triangle(cx+u*6f, cy-u*9f,  cx+u*2f, cy+u*9f,  cx+u*2f, cy-u*9f);

        // Porthole
        sr.setColor(0.05f, 0.08f, 0.20f, 1f);
        sr.circle(cx+u*18f, cy, u*4.0f, 12);
        sr.setColor(0.28f, 0.82f, 1.00f, 0.80f);
        sr.circle(cx+u*18f, cy, u*2.4f, 10);

        sr.end();
    }

    private void drawCentered(String text, float y) {
        layout.setText(font, text);
        font.draw(batch, text, (W - layout.width) * 0.5f, y);
    }

    private void finish() {
        ShipData sd = ShipData.get();
        if (arrived) {
            if (sd.isReplayMode) {
                // Replay: record time, restore main state, return to main menu
                float elapsed = sd.flightStartTimeMs != 0L
                    ? (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f
                    : 0f;
                sd.endReplay(elapsed);
                sd.save();
                SoundManager.get().playMilestone();
                game.transitionTo(GameState.MAIN_MENU);
                return;
            }
            sd.markArrival(totalRoute, totalRoute / ENERGY_AU_SCALE);
            // Record leaderboard arrival time before claimArrivalReward resets the timer
            if (sd.flightStartTimeMs != 0L) {
                float elapsed = (System.currentTimeMillis() - sd.flightStartTimeMs) / 1000f;
                int pIdx = sd.currentPlanetIndex;
                if (elapsed < sd.bestArrivalTimes[pIdx]) {
                    sd.bestArrivalTimes[pIdx] = elapsed;
                }
                sd.pendingRankResult = com.odyssey.FakeLeaderboard.getRank(pIdx, sd.bestArrivalTimes[pIdx]);
                sd.pendingRankPlanet = pIdx;
                sd.save();
            }
            sd.claimArrivalReward();
            SoundManager.get().playMilestone();
            game.transitionTo(GameState.INTERN_DEPLOY);
        } else {
            SoundManager.get().playCheckpoint();
            sd.totalJoules        = 0f;
            sd.energyAtLastLaunch = sd.powerGenerated;
            game.transitionTo(GameState.ENGINEERING_LAB);
        }
    }

    @Override public void resize(int w, int h) { viewport.update(w, h, true); }

    @Override
    public void dispose() {
        batch.dispose();
        sr.dispose();
        texPixel.dispose();
        texGlow.dispose();
    }
}
