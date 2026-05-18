package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;

public class BridgeFlightScreen extends ScreenAdapter {

    // ---- Route constants -------------------------------------------------------

    public static final float   TOTAL_ROUTE   = 25000f;
    // Gaps grow: 2000 → 4000 → 8000 → 11000 AU, requiring JPS ≈ 111 / 222 / 444 / 611
    public static final float[] SECTOR_DISTS  = {300f, 6000f, 14000f, 25000f};
    private static final String[] SECTOR_NAMES = {
        "CHECKPOINT I", "CHECKPOINT II", "CHECKPOINT III", "EMBER PRIME"
    };
    private static final String[] SECTOR_PERKS = {
        "Elastic Walls",
        "Resonance",
        "Wall x3 + Coll x2 + Free Intern",
        "OVERDRIVE!"
    };
    // Energy produced since last launch × scale = AU gained this run.
    public  static final float ENERGY_AU_SCALE = 0.5f;

    // ---- Layout ----------------------------------------------------------------

    private static final float W = 1280f, H = 720f;
    private static final float LINE_Y  = H * 0.50f;
    private static final float LINE_X0 = 100f;
    private static final float LINE_X1 = 1180f;
    private static final float LINE_LEN = LINE_X1 - LINE_X0;

    // Rocket animation speed (pixels/s)
    private static final float ROCKET_SPEED = 90f;
    private static final float HOLD_TIME    = 3.5f;

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
    private float   holdTimer     = 0f;
    private int     prevSector;       // sectorReached before this run
    private int     newHighSector;    // highest sector reached after this run
    private boolean newPerkReached;

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
        Pixmap pm = new Pixmap((int)W, (int)H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0.01f, 0.01f, 0.07f, 1f);
        pm.fill();
        pm.setColor(1f, 1f, 1f, 0.9f);
        java.util.Random rng = new java.util.Random(99991L);
        for (int i = 0; i < 280; i++)
            pm.drawPixel(rng.nextInt((int)W), rng.nextInt((int)H));
        Texture t = new Texture(pm); pm.dispose(); return t;
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
        holdTimer    = 0f;
        prevSector   = sd.sectorReached;

        float startDist = prevSector >= 0 ? SECTOR_DISTS[prevSector] : 0f;
        rocketX = distToX(startDist);

        float energyThisRun = sd.powerGenerated - sd.energyAtLastLaunch;
        sd.energyAtLastLaunch = sd.powerGenerated;
        float gained   = energyThisRun * ENERGY_AU_SCALE;
        float newAccum = Math.min(startDist + gained, TOTAL_ROUTE);
        rocketTargetX  = distToX(newAccum);

        newHighSector  = prevSector;
        for (int i = 0; i < SECTOR_DISTS.length; i++)
            if (newAccum >= SECTOR_DISTS[i]) newHighSector = i;

        newPerkReached     = newHighSector > prevSector;
        sd.accumulatedDist = newAccum;
        sd.sectorReached   = newHighSector;
    }

    private float distToX(float dist) {
        return LINE_X0 + (dist / TOTAL_ROUTE) * LINE_LEN;
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
                rocketX  = rocketTargetX;
                animDone = true;
            }
        } else {
            holdTimer += delta;
            if (holdTimer >= HOLD_TIME || Gdx.input.justTouched()
                    || Gdx.input.isKeyJustPressed(Input.Keys.ANY_KEY)) {
                finish();
                return;
            }
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
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
        String jpsStr = String.format("Power core: %.0f J  |  Distance covered: %.0f AU",
            ShipData.get().powerGenerated,
            ShipData.get().accumulatedDist);
        layout.setText(font, jpsStr);
        font.draw(batch, jpsStr, (W - layout.width) / 2f, H - 68f);

        // Route line (gray base)
        batch.setColor(0.35f, 0.35f, 0.35f, 1f);
        batch.draw(texPixel, LINE_X0, LINE_Y - 2f, LINE_LEN, 4f);

        // Green progress up to rocketX
        batch.setColor(0.27f, 1f, 0.55f, 0.5f);
        float coveredLen = Math.max(0, rocketX - LINE_X0);
        batch.draw(texPixel, LINE_X0, LINE_Y - 2f, coveredLen, 4f);

        // SOLARA label
        font.setColor(0.4f, 0.8f, 1f, 1f);
        font.draw(batch, "SOLARA", LINE_X0 - 58f, LINE_Y + 8f);

        // EMBER label
        font.setColor(1f, 0.5f, 0.2f, 1f);
        font.draw(batch, "EMBER", LINE_X1 + 4f, LINE_Y + 8f);

        // Sector markers
        float dotR = 12f;
        for (int i = 0; i < SECTOR_DISTS.length; i++) {
            float sx = distToX(SECTOR_DISTS[i]);
            boolean alreadyHad = (i <= prevSector);
            boolean justPassed = (!alreadyHad && rocketX >= sx);

            if (alreadyHad)
                batch.setColor(0.27f, 1f, 0.55f, 1f);   // green — carried over
            else if (justPassed)
                batch.setColor(1f, 0.85f, 0.2f, 1f);    // yellow — new this run
            else
                batch.setColor(0.28f, 0.28f, 0.28f, 1f); // gray — not yet

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
            if (newHighSector >= SECTOR_DISTS.length - 1) {
                font.setColor(1f, 0.5f, 0.2f, 1f);
                drawCentered("ARRIVED AT EMBER PRIME!", H * 0.25f);
            } else if (newHighSector >= 0) {
                font.setColor(0.27f, 1f, 0.55f, 1f);
                drawCentered("LANDED: " + SECTOR_NAMES[newHighSector], H * 0.25f);
            } else {
                font.setColor(1f, 0.4f, 0.4f, 1f);
                drawCentered("DID NOT REACH A CHECKPOINT", H * 0.25f);
            }
            font.getData().setScale(1f);

            if (newPerkReached) {
                font.setColor(1f, 1f, 0.4f, 1f);
                drawCentered("NEW PERK: " + SECTOR_PERKS[newHighSector], H * 0.17f);
            }

            font.setColor(0.55f, 0.55f, 0.55f, 1f);
            drawCentered(String.format("Returning to bay in %.0f s  (click to skip)",
                Math.max(0, HOLD_TIME - holdTimer)), H * 0.09f);
        }

        batch.end();
    }

    private void drawCentered(String text, float y) {
        layout.setText(font, text);
        font.draw(batch, text, (W - layout.width) / 2f, y);
    }

    private void finish() {
        if (newHighSector >= SECTOR_DISTS.length - 1)
            game.transitionTo(GameState.GALACTIC_MAP);
        else
            game.transitionTo(GameState.ENGINEERING_LAB);
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
