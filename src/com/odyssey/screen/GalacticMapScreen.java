package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
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

public class GalacticMapScreen extends ScreenAdapter {

    private final OdysseyGame game;
    private final Stage ui;
    private final Texture backgroundTexture;

    private Label routeLabel;
    private Label specsLabel;
    private Label rewardLabel;

    public GalacticMapScreen(OdysseyGame game) {
        this.game = game;
        this.ui = new Stage(new ScreenViewport());
        this.backgroundTexture = new Texture("backgrounds/map_bg.png");
        buildUi();
    }

    private void buildUi() {
        Image bg = new Image(backgroundTexture);
        bg.setFillParent(true);
        bg.setScaling(Scaling.fill);
        ui.addActor(bg);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(18f);
        root.bottom();

        Table card = new Table();
        card.setBackground(game.skin.getDrawable("card_medium"));
        card.pad(20f, 22f, 20f, 22f);

        Label title = new Label("Galactic Map", game.skin, "title");
        routeLabel = new Label("", game.skin);
        specsLabel = new Label("", game.skin);
        rewardLabel = new Label("", game.skin);
        for (Label label : new Label[] {routeLabel, specsLabel, rewardLabel}) {
            label.setWrap(true);
            label.setAlignment(Align.center);
        }

        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMaxCheckCount(1);
        group.setMinCheckCount(1);

        Table planetList = new Table();
        for (int i = 0; i < ShipData.PLANETS.length; i++) {
            final int index = i;
            ShipData.PlanetProfile planet = ShipData.PLANETS[i];
            TextButton button = new TextButton(
                String.format("%s  |  %.1f ly  |  %.1fG", planet.name, planet.distance / 1000f, planet.gravity),
                game.skin, "toggle");
            button.setChecked(i == ShipData.get().selectedPlanetIndex);
            button.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    if (((TextButton) actor).isChecked()) {
                        ShipData.get().selectPlanet(index);
                        refreshDetails();
                    }
                }
            });
            group.add(button);
            planetList.add(button).width(420f).height(62f).padBottom(8f).row();
        }

        TextButton setJump = new TextButton("Set Jump", game.skin);
        TextButton departure = new TextButton("Departure Lab", game.skin);
        TextButton menu = new TextButton("Main Menu", game.skin);

        setJump.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                ShipData.get().commitSelectedPlanet();
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
        departure.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
        menu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.MAIN_MENU);
            }
        });

        Table actionRow = new Table();
        actionRow.add(departure).width(200f).height(64f).padRight(8f);
        actionRow.add(setJump).width(200f).height(64f);

        card.add(title).center().padBottom(12f).row();
        card.add(routeLabel).width(420f).padBottom(10f).row();
        card.add(planetList).padBottom(12f).row();
        card.add(specsLabel).width(420f).padBottom(8f).row();
        card.add(rewardLabel).width(420f).padBottom(16f).row();
        card.add(actionRow).padBottom(8f).row();
        card.add(menu).width(408f).height(60f).row();

        root.add(card).width(470f).bottom();
        ui.addActor(root);
    }

    private void refreshDetails() {
        ShipData sd = ShipData.get();
        ShipData.PlanetProfile current = sd.getCurrentPlanet();
        ShipData.PlanetProfile selected = sd.getSelectedPlanet();
        routeLabel.setText(String.format(
            "Current location: %s\nDestination: %s",
            current.name, selected.name));
        specsLabel.setText(String.format(
            "Distance: %.1f light years   |   Gravity: %.1fG\nAtmosphere: %s",
            selected.distance / 1000f, selected.gravity, selected.atmosphere));
        rewardLabel.setText(String.format(
            "Arrival reward: %s\nUnlocks: %s, %s",
            selected.rewardLabel, selected.unlockedBuildingA, selected.unlockedBuildingB));
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(ui);
        refreshDetails();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ui.act(delta);
        ui.draw();
    }

    @Override public void resize(int width, int height) {
        ui.getViewport().update(width, height, true);
    }

    @Override public void dispose() {
        ui.dispose();
        backgroundTexture.dispose();
    }
}
