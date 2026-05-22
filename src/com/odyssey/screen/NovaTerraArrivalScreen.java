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
import com.odyssey.OdysseyTheme;

public class NovaTerraArrivalScreen extends ScreenAdapter {

    private final OdysseyGame game;
    private final Stage stage;
    private final Texture backgroundTexture;

    private Label titleLabel;
    private Label summaryLabel;
    private Label energyLabel;
    private Label rewardLabel;
    private Label buildingsLabel;

    public NovaTerraArrivalScreen(OdysseyGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.backgroundTexture = new Texture("backgrounds/arrival_bg.png");
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
        root.bottom();

        Table card = new Table();
        card.setBackground(game.skin.getDrawable("card_medium"));
        card.pad(22f, 22f, 22f, 22f);

        titleLabel = new Label("", game.skin, "title");
        summaryLabel = new Label("", game.skin);
        energyLabel = new Label("", game.skin);
        rewardLabel = new Label("", game.skin);
        buildingsLabel = new Label("", game.skin);
        for (Label label : new Label[] {summaryLabel, energyLabel, rewardLabel, buildingsLabel}) {
            label.setWrap(true);
            label.setAlignment(Align.center);
        }

        TextButton claimButton = new TextButton("Claim Prestige Reward", game.skin);
        TextButton mapButton = new TextButton("Return to Galactic Map", game.skin);

        claimButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                ShipData.get().claimArrivalReward();
                game.resetLabScreen();
                game.transitionTo(GameState.ENGINEERING_LAB);
            }
        });
        mapButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.GALACTIC_MAP);
            }
        });

        card.add(titleLabel).center().padBottom(14f).row();
        card.add(summaryLabel).width(410f).padBottom(10f).row();
        card.add(energyLabel).width(410f).padBottom(10f).row();
        card.add(rewardLabel).width(410f).padBottom(14f).row();
        card.add(buildingsLabel).width(410f).padBottom(18f).row();
        card.add(claimButton).width(410f).height(68f).padBottom(8f).row();
        card.add(mapButton).width(410f).height(60f).row();

        root.add(card).width(460f).bottom();
        stage.addActor(root);
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(stage);
        refresh();
    }

    private void refresh() {
        ShipData sd = ShipData.get();
        ShipData.PlanetProfile planet = sd.getCurrentPlanet();
        titleLabel.setText("Arrival Confirmed: " + planet.name);
        summaryLabel.setText(String.format(
            "Colonization initiated on %s.\nGravity %.1fG, atmosphere %s.",
            planet.name, planet.gravity, planet.atmosphere));
        energyLabel.setText(String.format(
            "Journey time: %.0f days\nTotal energy used: %.0f joules",
            sd.lastArrivalJourneyDays, sd.lastArrivalEnergyUsed));
        rewardLabel.setText("Prestige unlock: " + planet.rewardLabel);
        buildingsLabel.setText(String.format(
            "New available buildings: %s and %s.\nClaiming this reward also improves the next Engineering Bay run.",
            planet.unlockedBuildingA, planet.unlockedBuildingB));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(OdysseyTheme.SPACE_BG.r, OdysseyTheme.SPACE_BG.g, OdysseyTheme.SPACE_BG.b, 1f);
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
