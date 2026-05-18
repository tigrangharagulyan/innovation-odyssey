package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;

/**
 * Passive screen: no physics, purely time-based travel math derived from ShipData.
 * Consumes Joules to close distance. Arrives when distanceCovered >= targetPlanetDistance.
 */
public class BridgeFlightScreen extends ScreenAdapter {

    // Travel equation constants
    private static final float JOULE_TO_DISTANCE_RATIO = 0.001f; // 1000 J moves 1 unit

    private final OdysseyGame game;
    private final Stage ui;

    private Label distanceLabel;
    private Label etaLabel;
    private Label joulesLabel;
    private ProgressBar progressBar;

    private float distanceCovered = 0f;
    private boolean arrived       = false;

    public BridgeFlightScreen(OdysseyGame game) {
        this.game = game;
        this.ui   = new Stage(new ScreenViewport());
        buildUI();
    }

    public void resetFlight() {
        distanceCovered = 0f;
        arrived         = false;
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.center();

        Label title  = new Label("Bridge Flight", game.skin, "title");
        distanceLabel = new Label("", game.skin);
        etaLabel      = new Label("", game.skin);
        joulesLabel   = new Label("", game.skin);

        progressBar = new ProgressBar(0f, 1f, 0.001f, false, game.skin);

        TextButton btnBack = new TextButton("Abort (< Menu)", game.skin);
        btnBack.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.MAIN_MENU);
            }
        });

        root.add(title).padBottom(24).row();
        root.add(progressBar).width(400).padBottom(8).row();
        root.add(distanceLabel).padBottom(4).row();
        root.add(etaLabel).padBottom(4).row();
        root.add(joulesLabel).padBottom(24).row();
        root.add(btnBack).row();
        ui.addActor(root);
    }

    @Override public void show() { Gdx.input.setInputProcessor(ui); }

    @Override
    public void render(float delta) {
        if (!arrived) tickFlight(delta);

        Gdx.gl.glClearColor(0f, 0f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        ShipData sd   = ShipData.get();
        float target  = sd.targetPlanetDistance;
        float progress = Math.min(distanceCovered / target, 1f);

        progressBar.setValue(progress);
        distanceLabel.setText(String.format("Distance: %.1f / %.1f AU", distanceCovered, target));
        joulesLabel.setText(String.format("Joules remaining: %.2f J", sd.totalJoules));

        // ETA: at current JPS, joules needed to cover remaining distance
        float remaining = target - distanceCovered;
        float jpsNeeded = remaining / JOULE_TO_DISTANCE_RATIO;
        float etaSec    = (sd.currentJPS > 0) ? (jpsNeeded / sd.currentJPS) : Float.POSITIVE_INFINITY;
        etaLabel.setText(etaSec < Float.POSITIVE_INFINITY
            ? String.format("ETA: %.0f s", etaSec)
            : "ETA: --  (no energy production)");

        ui.act(delta);
        ui.draw();
    }

    private void tickFlight(float delta) {
        ShipData sd    = ShipData.get();
        float joulesToSpend = sd.currentJPS * delta;

        if (sd.totalJoules < joulesToSpend) joulesToSpend = sd.totalJoules;
        sd.totalJoules  -= joulesToSpend;
        distanceCovered += joulesToSpend * JOULE_TO_DISTANCE_RATIO;

        if (distanceCovered >= sd.targetPlanetDistance) {
            arrived = true;
            distanceCovered = sd.targetPlanetDistance;
            game.transitionTo(GameState.GALACTIC_MAP);
        }
    }

    @Override public void resize(int w, int h) { ui.getViewport().update(w, h, true); }
    @Override public void dispose() { ui.dispose(); }
}
