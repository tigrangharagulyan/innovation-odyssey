package com.odyssey;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.odyssey.screen.*;

public class OdysseyGame extends Game {

    public Skin skin;

    private MainMenuScreen         mainMenuScreen;
    private EngineeringLabScreen   labScreen;
    private BridgeFlightScreen     flightScreen;
    private InternDeployScreen     deployScreen;
    private GalacticMapScreen      galacticScreen;
    private NovaTerraArrivalScreen arrivalScreen;

    private GameState currentState;

    // ---- Fade-to-black transition -----------------------------------------------
    private SpriteBatch fadeBatch;
    private Texture     fadePixel;
    private float       fadeAlpha   = 0f;
    private float       fadeTimer   = 0f;
    private boolean     fadingOut   = false;
    private boolean     fadingIn    = false;
    private GameState   pendingState = null;
    private static final float FADE_DUR = 0.28f;

    @Override
    public void create() {
        skin = buildSkin();

        // Fade overlay resources
        fadeBatch = new SpriteBatch();
        Pixmap fp = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        fp.setColor(Color.BLACK);
        fp.fill();
        fadePixel = new Texture(fp);
        fp.dispose();

        ShipData.get().reset();
        switchScreenImmediate(GameState.MAIN_MENU);
    }

    @Override
    public void render() {
        super.render();   // delegates to the active Screen's render()

        float delta = Gdx.graphics.getDeltaTime();

        if (fadingOut) {
            fadeTimer += delta;
            fadeAlpha  = Math.min(fadeTimer / FADE_DUR, 1f);
            if (fadeTimer >= FADE_DUR) {
                fadeAlpha    = 1f;
                fadingOut    = false;
                fadingIn     = true;
                fadeTimer    = 0f;
                switchScreenImmediate(pendingState);
                pendingState = null;
            }
        } else if (fadingIn) {
            fadeTimer += delta;
            fadeAlpha  = Math.max(1f - fadeTimer / FADE_DUR, 0f);
            if (fadeTimer >= FADE_DUR) {
                fadingIn  = false;
                fadeAlpha = 0f;
            }
        }

        if (fadeAlpha > 0.01f) {
            fadeBatch.getProjectionMatrix().setToOrtho2D(
                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            fadeBatch.begin();
            fadeBatch.setColor(0f, 0f, 0f, fadeAlpha);
            fadeBatch.draw(fadePixel, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            fadeBatch.end();
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        fadeBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    private Skin buildSkin() {
        Skin s = new Skin();

        // 1×1 white pixel — base for all coloured drawables
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        s.add("white", new Texture(px));
        px.dispose();

        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Exo2.ttf"));
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.color     = Color.WHITE;
        p.shadowColor  = new Color(0f, 0f, 0f, 0.55f);
        p.shadowOffsetX = 1; p.shadowOffsetY = -1;
        // Extend character set: add multiplication sign and other Latin-1/punctuation used in UI
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                     + "×"   // × multiplication sign
                     + "·"   // · middle dot
                     + "→"   // → right arrow
                     + "▶"   // ▶ right-pointing triangle
                     + "▲"   // ▲ up-pointing triangle
                     + "✓";  // ✓ check mark

        p.size = 17; BitmapFont font   = gen.generateFont(p);
        p.size = 24; BitmapFont medium = gen.generateFont(p);
        p.size = 38; BitmapFont large  = gen.generateFont(p);
        p.size = 13; BitmapFont small  = gen.generateFont(p);
        p.size = 14; BitmapFont floatF = gen.generateFont(p); // used for batch-drawn overlay text
        gen.dispose();

        s.add("font",   font);
        s.add("medium", medium);
        s.add("large",  large);
        s.add("small",  small);
        s.add("float",  floatF);

        Texture panelLargeTex      = new Texture("ui/card_large.png");
        Texture panelMediumTex     = new Texture("ui/card_medium.png");
        Texture buttonPrimaryTex   = new Texture("ui/button_primary.png");
        Texture buttonSecondaryTex = new Texture("ui/button_secondary.png");
        Texture buttonDisabledTex  = new Texture("ui/button_disabled.png");
        Texture badgePanelTex      = new Texture("ui/badge_panel.png");
        s.add("card_large",       new NinePatch(panelLargeTex,      20, 20, 20, 20));
        s.add("card_medium",      new NinePatch(panelMediumTex,     20, 20, 20, 20));
        s.add("button_primary",   new NinePatch(buttonPrimaryTex,   28, 28, 28, 28));
        s.add("button_secondary", new NinePatch(buttonSecondaryTex, 28, 28, 28, 28));
        s.add("button_disabled",  new NinePatch(buttonDisabledTex,  28, 28, 28, 28));
        s.add("badge_panel",      new NinePatch(badgePanelTex,      20, 20, 20, 20));

        // Label styles
        LabelStyle def     = new LabelStyle(font,  OdysseyTheme.TEXT_PRI);
        LabelStyle title   = new LabelStyle(large, OdysseyTheme.ACCENT_E);
        LabelStyle heading = new LabelStyle(medium, OdysseyTheme.TEXT_PRI);
        LabelStyle accent  = new LabelStyle(font, OdysseyTheme.ACCENT_GO);
        s.add("default", def);
        s.add("title",   title);
        s.add("heading", heading);
        s.add("accent",  accent);

        // ── TextButton default — dark panel, white text, state driven by setColor() ──
        // The drawable is a solid white pixel; each button calls setColor() each frame
        // to apply BTN_LOCKED / BTN_BUYABLE / BTN_GO etc. from OdysseyTheme.
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

        // Toggle (planet selection in GalacticMap) — unchanged behavior, new colors
        TextButton.TextButtonStyle tog = new TextButton.TextButtonStyle();
        tog.font             = font;
        tog.up               = s.newDrawable("white", OdysseyTheme.PANEL_BG);
        tog.over             = s.newDrawable("white", OdysseyTheme.BTN_AVAILABLE);
        tog.down             = s.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        tog.checked          = s.newDrawable("white", OdysseyTheme.BTN_BUYABLE);
        tog.fontColor        = OdysseyTheme.TEXT_PRI;
        tog.checkedFontColor = OdysseyTheme.TEXT_PRI;
        s.add("toggle", tog);

        // ProgressBar horizontal
        ProgressBarStyle pb = new ProgressBarStyle();
        pb.background = s.newDrawable("white", new Color(0.15f, 0.15f, 0.15f, 1f));
        pb.knob       = s.newDrawable("white", Color.CLEAR);
        pb.knobBefore  = s.newDrawable("white", new Color(0.1f, 0.8f, 0.3f, 1f));
        s.add("default-horizontal", pb);

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
        // Green "can afford" variant — used for action tile buttons when purchase is possible
        Color gcBorder = new Color(0.07f, 0.52f, 0.20f, 1f);
        Color gcGlow   = new Color(0.18f, 0.92f, 0.40f, 1f);
        s.add("tile_buygreen",    makeSciBtn(gcGlow, 0.78f, gcBorder, false));
        s.add("tile_buygreen_dn", makeSciBtn(gcGlow, 0.78f, gcBorder, true));

        // Rounded panels for overlays and strips
        s.add("rounded_dark",  makeRoundedPanel(OdysseyTheme.PANEL_BG, 16));
        s.add("rounded_popup", makeRoundedPanel(new Color(0.02f, 0.04f, 0.12f, 1f), 18));

        return s;
    }

    private static NinePatchDrawable makeSciBtn(Color glowCol, float glowPow, Color borderCol, boolean pressed) {
        int SZ = 128, R = 22;
        Pixmap pm = new Pixmap(SZ, SZ, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float boost = pressed ? 0.18f : 0f;
        Color dark  = new Color(0.04f + boost, 0.05f + boost, 0.10f + boost, 1f);
        Color dark2 = new Color(0.06f + boost, 0.08f + boost, 0.16f + boost, 1f);

        pm.setColor(0, 0, 0, 0); pm.fill();
        fillRoundedRect(pm, 0, 0, SZ, SZ, R, borderCol);
        fillRoundedRect(pm, 3, 3, SZ - 6, SZ - 6, R - 2, dark);
        Color borderDim = new Color(borderCol.r * 0.35f, borderCol.g * 0.35f, borderCol.b * 0.35f, 1f);
        fillRoundedRect(pm, 4, 4, SZ - 8, SZ - 8, R - 3, borderDim);
        fillRoundedRect(pm, 6, 6, SZ - 12, SZ - 12, R - 4, dark2);

        if (glowPow > 0.05f) {
            int cx = SZ / 2, cy = SZ / 2;
            float inner = SZ / 2f - 6f;
            for (int py = 6; py < SZ - 6; py++) {
                for (int px = 6; px < SZ - 6; px++) {
                    float dx = (px - cx) / inner;
                    float dy = (py - cy) / inner;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float t = Math.max(0f, 1f - dist) * glowPow;
                    t = t * t;
                    if (t > 0.008f) {
                        float br = Math.min(1f, dark2.r + glowCol.r * t * 0.85f);
                        float bg = Math.min(1f, dark2.g + glowCol.g * t * 0.85f);
                        float bb = Math.min(1f, dark2.b + glowCol.b * t * 0.85f);
                        pm.setColor(br, bg, bb, 1f);
                        pm.drawPixel(px, py);
                    }
                }
            }
        }

        // Corner L-bracket accents
        pm.setColor(borderCol);
        int ca = 10;
        pm.fillRectangle(1, 1, ca, 2);              pm.fillRectangle(1, 1, 2, ca);
        pm.fillRectangle(SZ - ca - 1, 1, ca, 2);   pm.fillRectangle(SZ - 3, 1, 2, ca);
        pm.fillRectangle(1, SZ - 3, ca, 2);         pm.fillRectangle(1, SZ - ca - 1, 2, ca);
        pm.fillRectangle(SZ - ca - 1, SZ - 3, ca, 2); pm.fillRectangle(SZ - 3, SZ - ca - 1, 2, ca);

        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, R + 10, R + 10, R + 10, R + 10));
    }

    private static NinePatch makeRoundedPanel(Color col, int r) {
        int SZ = 64;
        Pixmap pm = new Pixmap(SZ, SZ, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0); pm.fill();
        fillRoundedRect(pm, 0, 0, SZ, SZ, r, col);
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatch(tex, r + 2, r + 2, r + 2, r + 2);
    }

    private static void fillRoundedRect(Pixmap pm, int x, int y, int w, int h, int r, Color col) {
        pm.setColor(col);
        if (r <= 0) { pm.fillRectangle(x, y, w, h); return; }
        pm.fillRectangle(x + r, y, w - 2 * r, h);
        pm.fillRectangle(x, y + r, r, h - 2 * r);
        pm.fillRectangle(x + w - r, y + r, r, h - 2 * r);
        pm.fillCircle(x + r,             y + r,             r);
        pm.fillCircle(x + w - 1 - r,     y + r,             r);
        pm.fillCircle(x + r,             y + h - 1 - r,     r);
        pm.fillCircle(x + w - 1 - r,     y + h - 1 - r,     r);
    }

    /**
     * Public API — starts a 0.28 s fade-out, switches screen at the midpoint,
     * then fades back in. Safe to call from any screen at any time.
     */
    /** Wipes the cached EngineeringLabScreen so the next visit creates a fresh instance. */
    public void resetLabScreen() {
        if (labScreen != null) { labScreen.dispose(); labScreen = null; }
    }

    /** Dispose and rebuild EngineeringLabScreen immediately, bypassing fade/state guards. */
    public void forceRebuildLab() {
        if (labScreen != null) { labScreen.dispose(); labScreen = null; }
        labScreen = new EngineeringLabScreen(this);
        currentState = GameState.ENGINEERING_LAB;
        setScreen(labScreen);
    }

    public void transitionTo(GameState next) {
        if (fadingOut || fadingIn) return;   // already mid-transition
        if (next == currentState) return;
        pendingState = next;
        fadingOut    = true;
        fadeTimer    = 0f;
        fadeAlpha    = 0f;
    }

    /** Switches screen immediately with no fade — used internally and for the initial launch. */
    private void switchScreenImmediate(GameState next) {
        currentState = next;
        switch (next) {
            case MAIN_MENU:
                if (mainMenuScreen == null) mainMenuScreen = new MainMenuScreen(this);
                setScreen(mainMenuScreen);
                break;
            case ENGINEERING_LAB:
                if (labScreen == null) labScreen = new EngineeringLabScreen(this);
                setScreen(labScreen);
                break;
            case BRIDGE_FLIGHT:
                if (flightScreen == null) flightScreen = new BridgeFlightScreen(this);
                flightScreen.resetFlight();
                setScreen(flightScreen);
                break;
            case INTERN_DEPLOY:
                if (deployScreen == null) deployScreen = new InternDeployScreen(this);
                deployScreen.show();
                setScreen(deployScreen);
                break;
            case GALACTIC_MAP:
                if (galacticScreen == null) galacticScreen = new GalacticMapScreen(this);
                setScreen(galacticScreen);
                break;
            case NOVA_TERRA_ARRIVAL:
                if (arrivalScreen == null) arrivalScreen = new NovaTerraArrivalScreen(this);
                setScreen(arrivalScreen);
                break;
        }
    }

    public GameState getCurrentState() { return currentState; }

    @Override
    public void dispose() {
        if (mainMenuScreen != null) mainMenuScreen.dispose();
        if (labScreen      != null) labScreen.dispose();
        if (flightScreen   != null) flightScreen.dispose();
        if (deployScreen   != null) deployScreen.dispose();
        if (galacticScreen != null) galacticScreen.dispose();
        if (arrivalScreen  != null) arrivalScreen.dispose();
        if (fadeBatch      != null) fadeBatch.dispose();
        if (fadePixel      != null) fadePixel.dispose();
        skin.dispose();
    }
}
