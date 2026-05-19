package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;

public class MainMenuScreen extends ScreenAdapter {

    private final OdysseyGame game;
    private final Stage stage;
    private final Texture backgroundTexture;

    private Label saveLabel;
    private Label destinationLabel;
    private Label playerLabel;
    private TextButton arrivalButton;

    public MainMenuScreen(OdysseyGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.backgroundTexture = new Texture("backgrounds/menu_bg.png");
        buildUi();
    }

    private void buildUi() {
        Image bg = new Image(backgroundTexture);
        bg.setFillParent(true);
        bg.setScaling(Scaling.fill);
        stage.addActor(bg);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(18f);

        Table card = new Table();
        card.setBackground(game.skin.getDrawable("card_large"));
        card.pad(22f, 24f, 22f, 24f);

        Label title = new Label("JAVA COLONIZATION SHIP", game.skin, "title");
        Label subtitle = new Label("Innovation Odyssey", game.skin, "accent");
        Label section = new Label("Mission Control", game.skin, "heading");

        TextButton menuButton = new TextButton("New Colonization", game.skin);
        TextButton labButton = new TextButton("Engineering Bay", game.skin);
        TextButton mapButton = new TextButton("Galactic Map", game.skin);
        arrivalButton = new TextButton("Nova Terra Arrival", game.skin);

        saveLabel = new Label("", game.skin);
        destinationLabel = new Label("", game.skin);
        playerLabel = new Label("", game.skin);
        for (Label label : new Label[] {saveLabel, destinationLabel, playerLabel}) {
            label.setWrap(true);
            label.setAlignment(Align.center);
        }

        menuButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
        labButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
        mapButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.GALACTIC_MAP);
            }
        });
        arrivalButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.NOVA_TERRA_ARRIVAL);
            }
        });

        card.defaults().growX();
        card.add(title).center().padBottom(6f).row();
        card.add(subtitle).center().padBottom(6f).row();
        card.add(section).center().padBottom(18f).row();
        card.add(menuButton).height(72f).padBottom(10f).row();
        card.add(labButton).height(72f).padBottom(10f).row();
        card.add(mapButton).height(72f).padBottom(10f).row();
        card.add(arrivalButton).height(72f).padBottom(18f).row();
        card.add(saveLabel).width(380f).padBottom(8f).row();
        card.add(destinationLabel).width(380f).padBottom(8f).row();
        card.add(playerLabel).width(380f).row();

        root.add(card).width(430f).center();
        stage.addActor(root);
    }

    private void refresh() {
        ShipData sd = ShipData.get();
        ShipData.PlanetProfile current = sd.getCurrentPlanet();
        ShipData.PlanetProfile destination = sd.getSelectedPlanet();
        saveLabel.setText(String.format(
            "Current world: %s\nStored power: %.1f J   |   Colonies: %d",
            current.name, sd.totalJoules, sd.arrivalsCompleted));
        destinationLabel.setText(String.format(
            "Jump target: %s\nDistance: %.1f light years   |   Gravity: %.1fG",
            destination.name, destination.distance / 1000f, destination.gravity));
        playerLabel.setText(String.format(
            "Player: CAPTAIN_UNIX\nTotal generated: %.1f J   |   Peak output: %.1f J/s",
            sd.powerGenerated, sd.currentJPS));
        arrivalButton.setDisabled(!sd.arrivalReady);
        arrivalButton.setText(sd.arrivalReady ? "Nova Terra Arrival" : "Arrival Locked");
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(stage);
        refresh();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.05f, 0.11f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() {
        stage.dispose();
        backgroundTexture.dispose();
    }
}
