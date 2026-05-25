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
    private LeaderboardScreen      leaderboardScreen;

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
                     + "✓"   // ✓ check mark
                     + "♥"   // ♥ heart (lives HUD)
                     + "◆";  // ◆ diamond (gems HUD)

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

        // ── TextButton default — white rounded NinePatch, state driven by setColor() ──
        // The drawable is a white rounded rectangle (r=22); each button calls setColor()
        // each frame to apply BTN_LOCKED / BTN_BUYABLE / BTN_GO etc. from OdysseyTheme.
        Color wBorder = new Color(0.55f, 0.55f, 0.55f, 1f);
        NinePatchDrawable roundedWhite = makeRoundedBtn(Color.WHITE, wBorder, 22);
        TextButton.TextButtonStyle btn = new TextButton.TextButtonStyle();
        btn.font              = font;
        btn.up                = roundedWhite;
        btn.over              = roundedWhite;
        btn.down              = roundedWhite;
        btn.disabled          = makeRoundedBtn(Color.WHITE, new Color(0.30f, 0.30f, 0.30f, 1f), 22);
        btn.fontColor         = OdysseyTheme.TEXT_PRI;
        btn.downFontColor     = OdysseyTheme.TEXT_PRI;
        btn.disabledFontColor = OdysseyTheme.TEXT_DIM;
        s.add("default", btn);

        // Toggle (planet selection in GalacticMap) — unchanged behavior, new colors
        TextButton.TextButtonStyle tog = new TextButton.TextButtonStyle();
        tog.font             = font;
        tog.up               = roundedWhite;
        tog.over             = roundedWhite;
        tog.down             = roundedWhite;
        tog.checked          = makeRoundedBtn(Color.WHITE, new Color(0.35f, 0.55f, 1f, 1f), 22);
        tog.fontColor        = OdysseyTheme.TEXT_PRI;
        tog.checkedFontColor = OdysseyTheme.TEXT_PRI;
        s.add("toggle", tog);

        // ProgressBar horizontal
        ProgressBarStyle pb = new ProgressBarStyle();
        pb.background = s.newDrawable("white", new Color(0.15f, 0.15f, 0.15f, 1f));
        pb.knob       = s.newDrawable("white", Color.CLEAR);
        pb.knobBefore  = s.newDrawable("white", new Color(0.1f, 0.8f, 0.3f, 1f));
        s.add("default-horizontal", pb);

        // Sci-fi tile buttons — programmatic rounded NinePatches (r=22)
        // Each state has a fill colour from OdysseyTheme + a slightly lighter border.
        // _dn (pressed) variants darken the fill by 25%.
        Color bLocked   = new Color(0.14f, 0.14f, 0.24f, 1f);
        Color bAvail    = new Color(0.32f, 0.36f, 0.58f, 1f);
        Color bBuyable  = OdysseyTheme.ACCENT_E;
        Color bActive   = new Color(0.45f, 0.65f, 1.00f, 1f);
        Color bGo       = OdysseyTheme.ACCENT_GO;
        Color bGoLocked = new Color(0.12f, 0.30f, 0.14f, 1f);

        NinePatchDrawable npLocked   = makeRoundedBtn(OdysseyTheme.BTN_LOCKED,    bLocked,   22);
        NinePatchDrawable npAvail    = makeRoundedBtn(OdysseyTheme.BTN_AVAILABLE, bAvail,    22);
        NinePatchDrawable npBuyable  = makeRoundedBtn(OdysseyTheme.BTN_BUYABLE,   bBuyable,  22);
        NinePatchDrawable npActive   = makeRoundedBtn(OdysseyTheme.BTN_ACTIVE,    bActive,   22);
        NinePatchDrawable npGo       = makeRoundedBtn(OdysseyTheme.BTN_GO,        bGo,       22);
        NinePatchDrawable npGoLocked = makeRoundedBtn(OdysseyTheme.BTN_GO_LOCKED, bGoLocked, 22);

        // Pressed variants: darken fill by 25%
        Color lockedDn   = darken(OdysseyTheme.BTN_LOCKED,    0.75f);
        Color availDn    = darken(OdysseyTheme.BTN_AVAILABLE,  0.75f);
        Color buyableDn  = darken(OdysseyTheme.BTN_BUYABLE,    0.75f);
        Color activeDn   = darken(OdysseyTheme.BTN_ACTIVE,     0.75f);
        Color goDn       = darken(OdysseyTheme.BTN_GO,         0.75f);
        Color goLockedDn = darken(OdysseyTheme.BTN_GO_LOCKED,  0.75f);

        s.add("tile_locked",       npLocked);
        s.add("tile_available",    npAvail);
        s.add("tile_buyable",      npBuyable);
        s.add("tile_active",       npActive);
        s.add("tile_go",           npGo);
        s.add("tile_golocked",     npGoLocked);
        s.add("tile_locked_dn",    makeRoundedBtn(lockedDn,   bLocked,   22));
        s.add("tile_available_dn", makeRoundedBtn(availDn,    bAvail,    22));
        s.add("tile_buyable_dn",   makeRoundedBtn(buyableDn,  bBuyable,  22));
        s.add("tile_active_dn",    makeRoundedBtn(activeDn,   bActive,   22));
        s.add("tile_go_dn",        makeRoundedBtn(goDn,       bGo,       22));
        s.add("tile_golocked_dn",  makeRoundedBtn(goLockedDn, bGoLocked, 22));
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

    /** Generates a rounded-rectangle NinePatch drawable for TextButton styles.
     *  fill  – interior colour (use Color.WHITE for styles that rely on setColor() tinting)
     *  border – 2-pixel outer ring colour
     *  r     – corner radius in Pixmap pixels */
    private static NinePatchDrawable makeRoundedBtn(Color fill, Color border, int r) {
        int SIZE = 64;
        Pixmap pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        fillRoundedRect(pm, 0,     0,     SIZE,     SIZE,     r,     border);
        fillRoundedRect(pm, 2,     2,     SIZE - 4, SIZE - 4, r - 2, fill);
        Texture tex = new Texture(pm);
        pm.dispose();
        int m = r + 2; // NinePatch margin: preserves corners, stretches flat centre
        return new NinePatchDrawable(new NinePatch(tex, m, m, m, m));
    }

    /** Returns a new Color with r/g/b multiplied by factor (alpha unchanged). */
    private static Color darken(Color c, float factor) {
        return new Color(c.r * factor, c.g * factor, c.b * factor, c.a);
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
            case LEADERBOARD:
                if (leaderboardScreen == null) leaderboardScreen = new LeaderboardScreen(this);
                setScreen(leaderboardScreen);
                break;
        }
    }

    public GameState getCurrentState() { return currentState; }

    @Override
    public void pause() {
        if (labScreen != null) labScreen.snapshotState();
        ShipData.get().save();
    }

    @Override
    public void dispose() {
        if (labScreen != null) labScreen.snapshotState();
        ShipData.get().save();
        if (mainMenuScreen != null) mainMenuScreen.dispose();
        if (labScreen      != null) labScreen.dispose();
        if (flightScreen   != null) flightScreen.dispose();
        if (deployScreen   != null) deployScreen.dispose();
        if (galacticScreen != null) galacticScreen.dispose();
        if (arrivalScreen  != null) arrivalScreen.dispose();
        if (leaderboardScreen != null) leaderboardScreen.dispose();
        if (fadeBatch      != null) fadeBatch.dispose();
        if (fadePixel      != null) fadePixel.dispose();
        skin.dispose();
    }
}
