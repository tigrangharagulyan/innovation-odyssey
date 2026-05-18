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
 * Destination selection + prestige parameter calculation.
 * Prestige multiplier = sqrt(distance) * gravityMultiplier — displayed before commit.
 */
public class GalacticMapScreen extends ScreenAdapter {

    // Demo destinations for Solara System
    private static final Planet[] PLANETS = {
        new Planet("Solara Prime",  1000f, 1.0f),
        new Planet("Ember IV",      2500f, 1.8f),
        new Planet("Frostheim",     5000f, 0.6f),
        new Planet("Nova Rift",    12000f, 3.2f),
    };

    private final OdysseyGame game;
    private final Stage ui;

    private Label prestigeLabel;
    private int   selectedIndex = 0;

    public GalacticMapScreen(OdysseyGame game) {
        this.game = game;
        this.ui   = new Stage(new ScreenViewport());
        buildUI();
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.center();

        Label title = new Label("Galactic Map — Solara System", game.skin, "title");
        prestigeLabel = new Label("", game.skin);

        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMaxCheckCount(1);
        group.setMinCheckCount(1);

        Table planetList = new Table();
        for (int i = 0; i < PLANETS.length; i++) {
            final int idx   = i;
            Planet p        = PLANETS[i];
            TextButton btn  = new TextButton(
                String.format("%s  |  %.0f AU  |  G×%.1f", p.name, p.distance, p.gravity),
                game.skin, "toggle");
            if (i == 0) btn.setChecked(true);
            btn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor a) {
                    if (((TextButton) a).isChecked()) {
                        selectedIndex = idx;
                        refreshPrestige();
                    }
                }
            });
            group.add(btn);
            planetList.add(btn).width(480).padBottom(8).row();
        }

        TextButton btnCommit = new TextButton("Set Destination & Return", game.skin);
        btnCommit.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                commitDestination();
                game.transitionTo(GameState.MAIN_MENU);
            }
        });

        TextButton btnBack = new TextButton("< Back", game.skin);
        btnBack.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.MAIN_MENU);
            }
        });

        root.add(title).padBottom(24).row();
        root.add(planetList).padBottom(16).row();
        root.add(prestigeLabel).padBottom(16).row();
        root.add(btnCommit).padBottom(8).row();
        root.add(btnBack).row();
        ui.addActor(root);

        refreshPrestige();
    }

    private void refreshPrestige() {
        Planet p = PLANETS[selectedIndex];
        // Prestige multiplier: reward scaling for harder destinations
        float prestige = (float) Math.sqrt(p.distance) * p.gravity;
        prestigeLabel.setText(String.format(
            "Prestige Multiplier: %.2f×  (distance=%.0f AU, G=%.1f)",
            prestige, p.distance, p.gravity));
    }

    private void commitDestination() {
        Planet p = PLANETS[selectedIndex];
        ShipData sd = ShipData.get();
        sd.targetPlanetDistance    = p.distance;
        sd.planetGravityMultiplier = p.gravity;
    }

    @Override public void show() { Gdx.input.setInputProcessor(ui); }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ui.act(delta);
        ui.draw();
    }

    @Override public void resize(int w, int h) { ui.getViewport().update(w, h, true); }
    @Override public void dispose() { ui.dispose(); }

    // ---- Inner data record ---------------------------------------------------

    private static final class Planet {
        final String name;
        final float  distance;
        final float  gravity;
        Planet(String name, float distance, float gravity) {
            this.name     = name;
            this.distance = distance;
            this.gravity  = gravity;
        }
    }
}
