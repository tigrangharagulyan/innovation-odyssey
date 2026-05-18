package com.odyssey;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.odyssey.screen.*;

public class OdysseyGame extends Game {

    public Skin skin;

    private MainMenuScreen       mainMenuScreen;
    private EngineeringLabScreen labScreen;
    private BridgeFlightScreen   flightScreen;
    private GalacticMapScreen    galacticScreen;

    private GameState currentState;

    @Override
    public void create() {
        skin = buildSkin();
        ShipData.get().reset();
        transitionTo(GameState.MAIN_MENU);
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
        large.getData().setScale(2f);
        s.add("font",  font);
        s.add("large", large);

        // Label styles
        LabelStyle def   = new LabelStyle(font,  Color.WHITE);
        LabelStyle title = new LabelStyle(large, Color.CYAN);
        s.add("default", def);
        s.add("title",   title);

        // TextButton default
        TextButtonStyle btn = new TextButtonStyle();
        btn.font    = font;
        btn.up      = s.newDrawable("white", new Color(0.2f, 0.2f, 0.35f, 1f));
        btn.over    = s.newDrawable("white", new Color(0.3f, 0.3f, 0.5f,  1f));
        btn.down    = s.newDrawable("white", new Color(0.1f, 0.1f, 0.2f,  1f));
        btn.fontColor = Color.WHITE;
        s.add("default", btn);

        // TextButton toggle (for planet selection in GalacticMap)
        TextButtonStyle tog = new TextButtonStyle();
        tog.font      = font;
        tog.up        = s.newDrawable("white", new Color(0.15f, 0.15f, 0.3f, 1f));
        tog.over      = s.newDrawable("white", new Color(0.25f, 0.25f, 0.45f, 1f));
        tog.down      = s.newDrawable("white", new Color(0.05f, 0.05f, 0.15f, 1f));
        tog.checked   = s.newDrawable("white", new Color(0.1f, 0.5f, 0.8f,  1f));
        tog.fontColor = Color.WHITE;
        tog.checkedFontColor = Color.WHITE;
        s.add("toggle", tog);

        // ProgressBar horizontal
        ProgressBarStyle pb = new ProgressBarStyle();
        pb.background  = s.newDrawable("white", new Color(0.15f, 0.15f, 0.15f, 1f));
        pb.knob        = s.newDrawable("white", Color.CLEAR);
        pb.knobBefore  = s.newDrawable("white", new Color(0.1f, 0.8f, 0.3f, 1f));
        s.add("default-horizontal", pb);

        return s;
    }

    public void transitionTo(GameState next) {
        if (next == currentState) return;
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
                else flightScreen.resetFlight();
                setScreen(flightScreen);
                break;
            case GALACTIC_MAP:
                if (galacticScreen == null) galacticScreen = new GalacticMapScreen(this);
                setScreen(galacticScreen);
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
        skin.dispose();
    }
}
