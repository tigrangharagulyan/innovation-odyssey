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

public class MainMenuScreen extends ScreenAdapter {

    private final OdysseyGame game;
    private final Stage stage;

    public MainMenuScreen(OdysseyGame game) {
        this.game  = game;
        this.stage = new Stage(new ScreenViewport());

        Table root = new Table();
        root.setFillParent(true);
        root.center();

        Label title = new Label("Innovation Odyssey", game.skin, "title");

        TextButton btnLab    = new TextButton("Engineering Lab",  game.skin);
        TextButton btnMap    = new TextButton("Galactic Map",      game.skin);
        TextButton btnFlight = new TextButton("Bridge Flight",     game.skin);

        btnLab.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
        btnMap.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.GALACTIC_MAP);
            }
        });
        btnFlight.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.BRIDGE_FLIGHT);
            }
        });

        root.add(title).padBottom(40).row();
        root.add(btnLab).width(300).padBottom(16).row();
        root.add(btnMap).width(300).padBottom(16).row();
        root.add(btnFlight).width(300).row();

        stage.addActor(root);
    }

    @Override public void show() { Gdx.input.setInputProcessor(stage); }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int w, int h) { stage.getViewport().update(w, h, true); }
    @Override public void dispose() { stage.dispose(); }
}
