package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;
import com.odyssey.OdysseyTheme;
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
        "Wall sparks ×3 · Collision sparks ×2 · Free intern added to bay",
        "Mission complete — colony established!"
    };
    // Energy produced since last launch × scale = AU gained this run.
    // Energy-delta needed per segment (not cumulative): 2K → 10K → 100K → 10M
    public static final float[] CHECKPOINT_ENERGIES = {2_000f, 10_000f, 50_000f, 100_000f};
    // Scale = 1 so route position IS energy delta (no unit conversion)
    public  static final float ENERGY_AU_SCALE = 1.0f;

    // ---- Layout ----------------------------------------------------------------

    private static final float W = 480f, H = 854f;
    private static final float LINE_Y  = H * 0.50f;
    private static final float LINE_X0 = 40f;
    private static final float LINE_X1 = 440f;
    private static final float LINE_LEN = LINE_X1 - LINE_X0;

    // Rocket animation speed (pixels/s)
    private static final float ROCKET_SPEED = 90f;

    // Action button (drawn with SpriteBatch, hit-tested on touch)
    private static final float BTN_W = 320f;
    private static final float BTN_H = 60f;
    private static final float BTN_X = (W - BTN_W) / 2f;
    private static final float BTN_Y = 28f;

    // ---- Fields ----------------------------------------------------------------

    private final OdysseyGame game;
    private SpriteBatch       batch;
    private OrthographicCamera cam;
    private FitViewport        viewport;
    private BitmapFont         font;
    private GlyphLayout        layout;

    private Texture texBg;
    private Texture texPixel;
    private Texture texDot;
    private Texture texRocket;

    // Per-run state (reset each launch)
    private float   rocketX;
    private float   rocketTargetX;
    private boolean animDone      = false;
    private float   landFlash     = 0f;
    private int     prevSector;       // sectorReached before this run
    private final Vector3 touchVec = new Vector3();
    private int     newHighSector;    // highest sector reached after this run
    private boolean newPerkReached;
    private float   totalRoute;
    private float[] sectorDists;
    private boolean arrived;

    // ---- Construction ----------------------------------------------------------

    public BridgeFlightScreen(OdysseyGame game) {
        this.game = game;
        batch    = new SpriteBatch();
        cam      = new OrthographicCamera();
        viewport = new FitViewport(W, H, cam);
        cam.position.set(W / 2f, H / 2f, 0f);
        font   = new BitmapFont();
        layout = new GlyphLayout();
        texBg     = genBackground();
        texPixel  = genPixel();
        texDot    = genDot(24);
        texRocket = genRocket(44, 20);
    }

    // ---- Texture generators ----------------------------------------------------

    private Texture genBackground() {
        return new Texture("backgrounds/flight_bg.png");
    }

    private Texture genPixel() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fill();
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    private Texture genDot(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float r = size / 2f;
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++) {
                float d = (float)Math.sqrt((x - r) * (x - r) + (y - r) * (y - r));
                if (d < r - 0.5f) pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, 1f));
            }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    private Texture genRocket(int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        int mid = h / 2;
        // body
        pm.setColor(0.75f, 0.75f, 1f, 1f);
        pm.fillRectangle(h / 2, h / 4, w - h / 2 - 2, h / 2);
        // nose
        pm.setColor(1f, 0.55f, 0.15f, 1f);
        for (int x = w - h / 2; x < w; x++) {
            int span = (int)((w - x) / (float)(h / 2) * (h / 4f));
            if (span > 0) pm.fillRectangle(x, mid - span, 1, span * 2);
        }
        // fins
        pm.setColor(0.45f, 0.45f, 0.9f, 1f);
        pm.fillRectangle(h / 2, 0,         h / 3, h / 4);
        pm.fillRectangle(h / 2, 3 * h / 4, h / 3, h / 4);
        // engine glow
        pm.setColor(0f, 0.8f, 1f, 1f);
        pm.fillRectangle(0, mid - 2, h / 2, 4);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // ---- Reset -----------------------------------------------------------------

    /** Called by OdysseyGame each time we transition to this screen. */
    public void resetFlight() {
        ShipData sd  = ShipData.get();
        animDone     = false;
        prevSector   = sd.sectorReached;
        sectorDists  = buildSectorDistances(0f);
        totalRoute   = sectorDists[sectorDists.length - 1];
        arrived      = false;

        float startDist = prevSector >= 0 ? sectorDists[prevSector] : 0f;
        rocketX = distToX(startDist);

        // Only ever advance ONE checkpoint per launch — cap target at the very next marker
        int   nextSector = Math.min(prevSector + 1, sectorDists.length - 1);
        float targetDist = sectorDists[nextSector];

        float energyThisRun = sd.powerGenerated - sd.energyAtLastLaunch;
        sd.energyAtLastLaunch = sd.powerGenerated;
        float gained   = energyThisRun * ENERGY_AU_SCALE;
        float newAccum = Math.min(startDist + gained, targetDist);  // never skip a checkpoint
        rocketTargetX  = distToX(newAccum);

        newHighSector  = prevSector;
        for (int i = 0; i < sectorDists.length; i++)
            if (newAccum >= sectorDists[i]) newHighSector = i;

        newPerkReached     = newHighSector > prevSector;
        sd.accumulatedDist = newAccum;
        sd.sectorReached   = newHighSector;
        arrived = newAccum >= totalRoute;
    }

    private float distToX(float dist) {
        if (sectorDists == null || totalRoute <= 0f) return LINE_X0;
        // Piecewise linear: each segment (→CP I, →CP II, →CP III, →ARRIVAL) gets equal visual width
        // so checkpoints sit at 25 / 50 / 75 / 100 % regardless of their energy gap
        int    n       = sectorDists.length;
        float  segW    = LINE_LEN / n;
        float  prevDist = 0f, segStart = 0f;
        for (int i = 0; i < n; i++) {
            float segEnd  = segStart + segW;
            float distEnd = sectorDists[i];
            if (dist <= distEnd || i == n - 1) {
                float t = (distEnd > prevDist)
                    ? Math.min((dist - prevDist) / (distEnd - prevDist), 1f)
                    : 1f;
                return LINE_X0 + segStart + t * segW;
            }
            prevDist = distEnd;
            segStart = segEnd;
        }
        return LINE_X1;
    }

    // Frostheim (distance 5000f) uses larger energy thresholds — matches EngineeringLabScreen
    private static final float[] FROSTHEIM_CHECKPOINT_ENERGIES = {6_000f, 24_000f, 120_000f, 150_000f};

    public static float[] buildSectorDistances(float ignored) {
        float[] e = ShipData.get().targetPlanetDistance == 5000f
            ? FROSTHEIM_CHECKPOINT_ENERGIES
            : CHECKPOINT_ENERGIES;
        float c1 = e[0];
        float c2 = c1 + e[1];
        float c3 = c2 + e[2];
        float c4 = c3 + e[3];
        return new float[] { c1, c2, c3, c4 };
    }

    // ---- Lifecycle -------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        // Move rocket
        if (!animDone) {
            rocketX += ROCKET_SPEED * delta;
            if (rocketX >= rocketTargetX) {
                rocketX   = rocketTargetX;
                animDone  = true;
                landFlash = 0.35f;
            }
        } else {
            if (Gdx.input.justTouched()) {
                touchVec.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                viewport.unproject(touchVec);
                if (touchVec.x >= BTN_X && touchVec.x <= BTN_X + BTN_W &&
                        touchVec.y >= BTN_Y && touchVec.y <= BTN_Y + BTN_H) {
                    finish();
                    return;
                }
            }
        }

        Gdx.gl.glClearColor(OdysseyTheme.SPACE_BG.r, OdysseyTheme.SPACE_BG.g, OdysseyTheme.SPACE_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        // Background
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(texBg, 0, 0, W, H);

        // Title
        font.getData().setScale(2f);
        font.setColor(0.2f, 0.8f, 1f, 1f);
        String title = "INTERSTELLAR TRANSIT";
        layout.setText(font, title);
        font.draw(batch, title, (W - layout.width) / 2f, H - 24f);
        font.getData().setScale(1f);

        // JPS readout
        font.setColor(1f, 1f, 0.5f, 1f);
        String jpsStr = String.format("Energy: %.0f E  |  Distance covered: %.0f AU",
            ShipData.get().powerGenerated,
            ShipData.get().accumulatedDist);
        layout.setText(font, jpsStr);
        font.draw(batch, jpsStr, (W - layout.width) / 2f, H - 68f);

        // Route line (gray base)
        batch.setColor(OdysseyTheme.PANEL_BORDER);
        batch.draw(texPixel, LINE_X0, LINE_Y - 2f, LINE_LEN, 4f);

        // Green progress up to rocketX
        batch.setColor(OdysseyTheme.ACCENT_GO_DIM);
        float coveredLen = Math.max(0, rocketX - LINE_X0);
        batch.draw(texPixel, LINE_X0, LINE_Y - 2f, coveredLen, 4f);

        // SOLARA label
        font.setColor(0.4f, 0.8f, 1f, 1f);
        font.draw(batch, ShipData.get().getCurrentPlanet().name.toUpperCase(), LINE_X0 - 58f, LINE_Y + 8f);

        // EMBER label
        font.setColor(1f, 0.5f, 0.2f, 1f);
        font.draw(batch, ShipData.get().getSelectedPlanet().name.toUpperCase(), LINE_X1 + 4f, LINE_Y + 8f);

        // Sector markers
        float dotR = 12f;
        for (int i = 0; i < sectorDists.length; i++) {
            float sx = distToX(sectorDists[i]);
            boolean alreadyHad = (i <= prevSector);
            boolean justPassed = (!alreadyHad && rocketX >= sx);

            if (alreadyHad)
                batch.setColor(OdysseyTheme.ACCENT_GO);
            else if (justPassed)
                batch.setColor(OdysseyTheme.ACCENT_SP);
            else
                batch.setColor(OdysseyTheme.PANEL_BORDER);

            batch.draw(texDot, sx - dotR, LINE_Y - dotR, dotR * 2, dotR * 2);

            // Vertical tick
            batch.setColor(batch.getColor().r, batch.getColor().g, batch.getColor().b, 0.6f);
            batch.draw(texPixel, sx - 1f, LINE_Y - 20f, 2f, 40f);

            // Name above
            if (alreadyHad || justPassed) font.setColor(0.27f, 1f, 0.55f, 1f);
            else                          font.setColor(0.35f, 0.35f, 0.35f, 1f);
            layout.setText(font, SECTOR_NAMES[i]);
            font.draw(batch, SECTOR_NAMES[i], sx - layout.width / 2f, LINE_Y + 52f);

            // Perk below
            font.setColor(0.5f, 0.5f, 0.5f, 1f);
            layout.setText(font, SECTOR_PERKS[i]);
            font.draw(batch, SECTOR_PERKS[i], sx - layout.width / 2f, LINE_Y - 40f);
        }

        // Rocket sprite
        float rw = 44f, rh = 20f;
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(texRocket, rocketX - rw / 2f, LINE_Y - rh / 2f, rw, rh);

        // Landing result overlay
        if (animDone) {
            font.getData().setScale(1.5f);
            if (arrived) {
                font.setColor(1f, 0.5f, 0.2f, 1f);
                drawCentered("ARRIVED AT " + ShipData.get().getSelectedPlanet().name.toUpperCase() + "!", H * 0.25f);
            } else if (newHighSector >= 0) {
                font.setColor(0.27f, 1f, 0.55f, 1f);
                drawCentered("LANDED: " + SECTOR_NAMES[newHighSector], H * 0.25f);
            } else {
                font.setColor(1f, 0.4f, 0.4f, 1f);
                drawCentered("DID NOT REACH A CHECKPOINT", H * 0.25f);
            }
            font.getData().setScale(1f);

            if (newPerkReached) {
                font.getData().setScale(1f);
                font.setColor(1f, 1f, 0.4f, 1f);
                drawCentered("NEW PERK: " + SECTOR_PERKS[newHighSector], H * 0.17f);
                font.getData().setScale(0.80f);
                font.setColor(0.78f, 0.82f, 0.88f, 1f);
                drawCentered(SECTOR_PERK_DESCS[newHighSector], H * 0.135f);
                if (newHighSector == 0) {
                    font.getData().setScale(0.72f);
                    font.setColor(1f, 0.85f, 0.25f, 0.90f);
                    drawCentered("Next upgrade: Speed Keep — reach 5.0 r/s in the bay", H * 0.105f);
                }
                font.getData().setScale(1f);
            }

            // Action button
            String btnText = arrived
                ? "LAND ON " + ShipData.get().getSelectedPlanet().name.toUpperCase() + "  ▶"
                : newHighSector >= 0
                    ? "CLAIM REWARD  &  RETURN TO BAY  ▶"
                    : "RETURN TO BAY  ▶";
            boolean btnIsArrival = arrived;
            if (btnIsArrival) batch.setColor(OdysseyTheme.ACCENT_WARN.r, OdysseyTheme.ACCENT_WARN.g, OdysseyTheme.ACCENT_WARN.b, 0.95f);
            else              batch.setColor(OdysseyTheme.BTN_GO.r, OdysseyTheme.BTN_GO.g, OdysseyTheme.BTN_GO.b, 0.95f);
            batch.draw(texPixel, BTN_X, BTN_Y, BTN_W, BTN_H);
            // Button border highlight
            batch.setColor(1f, 1f, 1f, 0.18f);
            batch.draw(texPixel, BTN_X, BTN_Y + BTN_H - 2f, BTN_W, 2f);
            batch.draw(texPixel, BTN_X, BTN_Y, BTN_W, 2f);
            font.getData().setScale(0.88f);
            font.setColor(1f, 1f, 1f, 1f);
            layout.setText(font, btnText);
            font.draw(batch, btnText, (W - layout.width) / 2f, BTN_Y + BTN_H * 0.60f);
            font.getData().setScale(1f);
        }

        if (landFlash > 0f) {
            landFlash = Math.max(0f, landFlash - delta);
            float fa = (landFlash / 0.35f) * 0.45f;
            batch.setColor(1f, 1f, 1f, fa);
            batch.draw(texPixel, 0, 0, W, H);
            batch.setColor(1f, 1f, 1f, 1f);
        }

        batch.end();
    }

    private void drawCentered(String text, float y) {
        layout.setText(font, text);
        font.draw(batch, text, (W - layout.width) / 2f, y);
    }

    private void finish() {
        if (arrived) {
            ShipData sd = ShipData.get();
            sd.markArrival(totalRoute, totalRoute / ENERGY_AU_SCALE);
            game.transitionTo(GameState.NOVA_TERRA_ARRIVAL);
        } else {
            // Checkpoint reached: wipe energy so the player starts fresh for the next segment.
            // Crystals (space points) are deliberately preserved.
            ShipData sd = ShipData.get();
            SoundManager.get().playCheckpoint();
            sd.totalJoules        = 0f;
            sd.energyAtLastLaunch = sd.powerGenerated;
            game.transitionTo(GameState.ENGINEERING_LAB);
        }
    }

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        texBg.dispose();
        texPixel.dispose();
        texDot.dispose();
        texRocket.dispose();
        font.dispose();
    }
}
