package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.FakeLeaderboard;
import com.odyssey.FakeLeaderboard.Entry;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;
import com.odyssey.OdysseyTheme;
import java.util.List;

public class NovaTerraArrivalScreen extends ScreenAdapter {

    private final OdysseyGame game;
    private final Stage stage;
    private final Texture backgroundTexture;

    private Label titleLabel;
    private Label summaryLabel;
    private Label gravityValueLabel;
    private Label gravityTagLabel;
    private Label gravityDescLabel;
    private ProgressBar gravityBar;
    private TextButton gravityToggle;
    private TextButton.TextButtonStyle styleGravOn;
    private TextButton.TextButtonStyle styleGravOff;
    private Label energyLabel;
    private Label rewardLabel;
    private Label buildingsLabel;

    // Leaderboard popup
    private Table   lbPopup;
    private Label   lbRankLabel;
    private Label   lbContextLabel;
    private int     recordedRank   = -1;
    private int     recordedPlanet = -1;

    private static String gravityDescriptor(float g) {
        if (g <= 0.3f) return "Microgravity";
        if (g <= 0.6f) return "Low Gravity";
        if (g <= 1.2f) return "Earth-like";
        if (g <= 1.8f) return "High Gravity";
        return "Extreme Gravity";
    }

    private static Color gravityColor(float g) {
        if (g <= 0.6f) return OdysseyTheme.FLOAT_E;
        if (g <= 1.2f) return OdysseyTheme.FLOAT_SPECIAL;
        if (g <= 1.8f) return OdysseyTheme.ACCENT_SP;
        return OdysseyTheme.ACCENT_WARN;
    }

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

        Table gravityPanel = new Table();
        gravityPanel.setBackground(game.skin.getDrawable("card_large"));
        gravityPanel.pad(14f, 18f, 14f, 18f);

        gravityTagLabel = new Label("", game.skin, "default");
        gravityTagLabel.setFontScale(0.7f);

        gravityDescLabel = new Label("", game.skin, "default");
        gravityDescLabel.setFontScale(0.75f);
        gravityDescLabel.setAlignment(Align.right);

        gravityValueLabel = new Label("", game.skin, "large");
        gravityValueLabel.setAlignment(Align.center);

        gravityBar = new ProgressBar(0f, 3f, 0.05f, false, game.skin);
        ProgressBarStyle gravBarStyle = new ProgressBarStyle(game.skin.get("default-horizontal", ProgressBarStyle.class));
        gravBarStyle.knobBefore = game.skin.newDrawable("white", OdysseyTheme.FLOAT_SPECIAL);
        gravBarStyle.background = game.skin.newDrawable("white", new Color(0.06f, 0.08f, 0.14f, 1f));
        gravityBar.setStyle(gravBarStyle);
        gravityBar.setAnimateDuration(0.6f);

        // Gravity toggle — sci-fi tile style using existing skin drawables
        styleGravOn = new TextButton.TextButtonStyle();
        styleGravOn.font            = game.skin.getFont("medium");
        styleGravOn.up              = game.skin.getDrawable("tile_go");
        styleGravOn.down            = game.skin.getDrawable("tile_go_dn");
        styleGravOn.over            = game.skin.getDrawable("tile_go_dn");
        styleGravOn.fontColor       = OdysseyTheme.ACCENT_GO;
        styleGravOn.downFontColor   = OdysseyTheme.ACCENT_GO;

        styleGravOff = new TextButton.TextButtonStyle();
        styleGravOff.font           = game.skin.getFont("medium");
        styleGravOff.up             = game.skin.getDrawable("tile_golocked");
        styleGravOff.down           = game.skin.getDrawable("tile_golocked_dn");
        styleGravOff.over           = game.skin.getDrawable("tile_golocked");
        styleGravOff.fontColor      = OdysseyTheme.TEXT_DIM;
        styleGravOff.downFontColor  = OdysseyTheme.TEXT_DIM;

        gravityToggle = new TextButton("", styleGravOn);
        gravityToggle.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                ShipData sd = ShipData.get();
                sd.gravityEnabled = !sd.gravityEnabled;
                sd.save();
                refresh();
            }
        });

        Table gravHeader = new Table();
        gravHeader.add(gravityTagLabel).left().expandX();
        gravHeader.add(gravityDescLabel).right();

        gravityPanel.add(gravHeader).width(390f).row();
        gravityPanel.add(gravityValueLabel).center().padTop(-4f).padBottom(2f).row();
        gravityPanel.add(gravityBar).width(390f).height(8f).padBottom(6f).row();
        gravityPanel.add(gravityToggle).width(390f).height(68f);

        card.add(titleLabel).center().padBottom(14f).row();
        card.add(summaryLabel).width(410f).padBottom(12f).row();
        card.add(gravityPanel).width(410f).padBottom(12f).row();
        card.add(energyLabel).width(410f).padBottom(10f).row();
        card.add(rewardLabel).width(410f).padBottom(14f).row();
        card.add(buildingsLabel).width(410f).padBottom(18f).row();
        card.add(claimButton).width(410f).height(68f).padBottom(8f).row();
        card.add(mapButton).width(410f).height(60f).row();

        root.add(card).width(460f).bottom();
        stage.addActor(root);
        buildLbPopup();
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(stage);
        recordArrivalTime();
        refresh();
        showLbPopup();
    }

    /** Reads pending rank data from ShipData (recorded earlier in BridgeFlightScreen). */
    private void recordArrivalTime() {
        ShipData sd = ShipData.get();
        // Primary recording happens in BridgeFlightScreen.finish() before claimArrivalReward.
        // This method just reads back those results if available.
        if (sd.pendingRankResult >= 0) {
            recordedRank   = sd.pendingRankResult;
            recordedPlanet = sd.pendingRankPlanet;
            // Don't clear pending here — MainMenuScreen will clear it on return
        }
    }

    private void buildLbPopup() {
        lbPopup = new Table();
        lbPopup.setFillParent(true);
        lbPopup.setVisible(false);
        lbPopup.setBackground(game.skin.newDrawable("white", new Color(0f, 0.02f, 0.08f, 0.92f)));
        lbPopup.center();

        Label title = new Label("LEADERBOARD", game.skin);
        lbPopup.add(title).center().padBottom(8f).row();

        lbRankLabel = new Label("", game.skin);
        lbRankLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        lbPopup.add(lbRankLabel).center().padBottom(14f).row();

        lbContextLabel = new Label("", game.skin);
        lbContextLabel.setFontScale(0.80f);
        lbContextLabel.setColor(0.75f, 0.85f, 1.00f, 0.90f);
        lbContextLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        lbContextLabel.setWrap(true);
        lbPopup.add(lbContextLabel).width(380f).center().padBottom(20f).row();

        TextButton viewFull = new TextButton("VIEW FULL BOARD", game.skin);
        viewFull.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.LEADERBOARD);
            }
        });

        TextButton cont = new TextButton("CONTINUE", game.skin);
        cont.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                lbPopup.setVisible(false);
            }
        });

        lbPopup.add(viewFull).width(320f).height(64f).padBottom(10f).row();
        lbPopup.add(cont).width(320f).height(60f).row();

        stage.addActor(lbPopup);
    }

    private void showLbPopup() {
        if (recordedRank < 0 || recordedPlanet < 0) return;

        ShipData sd = ShipData.get();
        String planetName = ShipData.PLANETS[recordedPlanet].name;
        float bestTime    = sd.bestArrivalTimes[recordedPlanet];

        lbRankLabel.setText("Rank #" + recordedRank + "  on  " + planetName);
        lbRankLabel.setColor(recordedRank <= 3
            ? new Color(1f, 0.82f, 0.20f, 1f)
            : new Color(0.22f, 1.00f, 0.52f, 1f));

        // Build context: up to 5 rows around the player's rank
        List<Entry> board = FakeLeaderboard.getBoard(recordedPlanet, bestTime);
        int playerIdx = recordedRank - 1; // 0-based
        int start = Math.max(0, playerIdx - 2);
        int end   = Math.min(board.size(), start + 5);
        start     = Math.max(0, end - 5);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            Entry e = board.get(i);
            if (e.isPlayer) sb.append("[#").append(i + 1).append("]  > YOU <  ")
                              .append(FakeLeaderboard.formatTime(e.timeSeconds)).append("\n");
            else            sb.append("  #").append(i + 1).append("   ").append(e.name)
                              .append("   ").append(FakeLeaderboard.formatTime(e.timeSeconds))
                              .append("\n");
        }
        lbContextLabel.setText(sb.toString().trim());

        lbPopup.setVisible(true);
    }

    private void refresh() {
        ShipData sd = ShipData.get();
        ShipData.PlanetProfile planet = sd.getCurrentPlanet();
        Color gColor = gravityColor(planet.gravity);
        String gDesc = gravityDescriptor(planet.gravity);

        titleLabel.setText("Arrival Confirmed: " + planet.name);
        summaryLabel.setText(String.format(
            "Colonization initiated on %s.\nAtmosphere: %s.",
            planet.name, planet.atmosphere));

        if (sd.gravityEnabled) {
            gravityTagLabel.setText("\u25C9 GRAVITY FIELD");
            gravityValueLabel.setText(String.format("%.1fG", planet.gravity));
            gravityDescLabel.setText(gDesc);
            gravityBar.setValue(planet.gravity);
            gravityToggle.setStyle(styleGravOn);
            gravityToggle.setText("\u25B6  GRAVITY  ENABLED");
            gravityToggle.setColor(Color.WHITE);
        } else {
            gravityTagLabel.setText("\u25CB GRAVITY OFFLINE");
            gravityValueLabel.setText("0.0G");
            gravityDescLabel.setText("Zero-G Mode");
            gColor = OdysseyTheme.TEXT_DIM;
            gravityBar.setValue(0f);
            gravityToggle.setStyle(styleGravOff);
            gravityToggle.setText("\u00D7  GRAVITY  DISABLED");
            gravityToggle.setColor(Color.WHITE);
        }

        gravityTagLabel.setColor(gColor);
        gravityValueLabel.setColor(gColor);
        gravityDescLabel.setColor(gColor);
        ((ProgressBarStyle)gravityBar.getStyle()).knobBefore = game.skin.newDrawable("white", gColor);

        int completedIdx = Math.max(0, sd.currentPlanetIndex - 1);
        long completedMs = sd.planetCompletionMs[completedIdx];
        String planetTimeStr = completedMs > 0L ? ShipData.formatDuration(completedMs) : "N/A";
        energyLabel.setText(String.format(
            "Journey time: %.0f days\nTotal energy used: %.0f joules\nTime on planet: %s",
            sd.lastArrivalJourneyDays, sd.lastArrivalEnergyUsed, planetTimeStr));
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