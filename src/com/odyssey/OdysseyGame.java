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
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.odyssey.screen.*;

public class OdysseyGame extends Game {

    public Skin skin;

    private MainMenuScreen        mainMenuScreen;
    private EngineeringLabScreen  labScreen;
    private BridgeFlightScreen    flightScreen;
    private GalacticMapScreen     galacticScreen;
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

        BitmapFont font  = new BitmapFont();
        BitmapFont large = new BitmapFont();
        BitmapFont medium = new BitmapFont();
        large.getData().setScale(2f);
        medium.getData().setScale(1.25f);
        s.add("font",  font);
        s.add("large", large);
        s.add("medium", medium);

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

        return s;
    }

    /**
     * Public API — starts a 0.28 s fade-out, switches screen at the midpoint,
     * then fades back in. Safe to call from any screen at any time.
     */
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
        if (galacticScreen != null) galacticScreen.dispose();
        if (arrivalScreen  != null) arrivalScreen.dispose();
        if (fadeBatch      != null) fadeBatch.dispose();
        if (fadePixel      != null) fadePixel.dispose();
        skin.dispose();
    }
}
