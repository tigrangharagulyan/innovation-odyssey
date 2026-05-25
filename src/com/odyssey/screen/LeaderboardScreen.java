package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.FakeLeaderboard;
import com.odyssey.FakeLeaderboard.Entry;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.OdysseyTheme;
import com.odyssey.ShipData;

import java.util.List;

public class LeaderboardScreen extends ScreenAdapter {

    private static final Color PLAYER_ROW_COLOR = new Color(0.22f, 1.00f, 0.52f, 1f);
    private static final Color RIVAL_COLOR       = new Color(0.75f, 0.85f, 1.00f, 0.90f);
    private static final Color RANK_COLOR        = new Color(1.00f, 0.82f, 0.20f, 1f);
    private static final Color TAB_ACTIVE        = new Color(0.22f, 0.72f, 1.00f, 1f);
    private static final Color TAB_LOCKED        = new Color(0.30f, 0.30f, 0.38f, 0.60f);
    private static final Color TAB_UNLOCKED      = new Color(0.55f, 0.65f, 0.80f, 1f);

    private final OdysseyGame game;
    private final Stage       stage;
    private int               activePlanet = 0;

    // Kept so we can rebuild the board table when tab changes
    private Table             boardTable;
    private ScrollPane        scrollPane;
    private Table             root;

    public LeaderboardScreen(OdysseyGame game) {
        this.game  = game;
        this.stage = new Stage(new ScreenViewport());
        buildUi();
    }

    private void buildUi() {
        root = new Table();
        root.setFillParent(true);
        root.pad(16f);
        root.top();

        // ── Title ──────────────────────────────────────────────────────────────
        Label title = new Label("LEADERBOARD", game.skin, "title");
        root.add(title).center().padBottom(14f).row();

        // ── Planet tabs ────────────────────────────────────────────────────────
        Table tabs = new Table();
        for (int i = 0; i < ShipData.PLANETS.length; i++) {
            final int idx = i;
            boolean visited = ShipData.get().bestArrivalTimes[i] < Float.MAX_VALUE;
            boolean isActive = (i == activePlanet);

            TextButton tab = new TextButton(ShipData.PLANETS[i].name, game.skin);
            tab.getLabel().setFontScale(0.65f);
            if (isActive) {
                tab.setColor(TAB_ACTIVE);
            } else if (visited) {
                tab.setColor(TAB_UNLOCKED);
            } else {
                tab.setColor(TAB_LOCKED);
                tab.setDisabled(true);
            }

            tab.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    if (!((TextButton) actor).isDisabled()) {
                        activePlanet = idx;
                        refreshBoard();
                    }
                }
            });

            tabs.add(tab).width(84f).height(48f).pad(0f, 2f, 0f, 2f);
        }
        root.add(tabs).center().padBottom(10f).row();

        // ── Board table inside a scroll pane ──────────────────────────────────
        boardTable = new Table();
        scrollPane = new ScrollPane(boardTable);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        root.add(scrollPane).expand().fill().padBottom(12f).row();

        // ── Back button ───────────────────────────────────────────────────────
        TextButton backBtn = new TextButton("BACK", game.skin);
        backBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.transitionTo(GameState.MAIN_MENU);
            }
        });
        root.add(backBtn).width(260f).height(64f).row();

        stage.addActor(root);
        refreshBoard();
    }

    private void refreshBoard() {
        boardTable.clear();

        ShipData sd = ShipData.get();
        float playerTime = sd.bestArrivalTimes[activePlanet];
        List<Entry> entries = FakeLeaderboard.getBoard(activePlanet, playerTime);

        // Header row
        addBoardRow(boardTable, "#", "NAME", "TIME", new Color(0.45f, 0.55f, 0.70f, 1f), true);
        boardTable.add(new Image(game.skin.newDrawable("white",
            new Color(0.20f, 0.25f, 0.35f, 0.60f)))).height(1f).colspan(3).fillX().row();

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            Color rowColor = e.isPlayer ? PLAYER_ROW_COLOR : RIVAL_COLOR;
            addBoardRow(boardTable,
                String.valueOf(i + 1),
                e.isPlayer ? "> YOU <" : e.name,
                FakeLeaderboard.formatTime(e.timeSeconds),
                rowColor, false);
        }

        // If player has no time yet, append a placeholder at the bottom
        if (playerTime >= Float.MAX_VALUE) {
            boardTable.add(new Image(game.skin.newDrawable("white",
                new Color(0.20f, 0.25f, 0.35f, 0.40f)))).height(1f).colspan(3).fillX().row();
            Label noTime = new Label("Complete a flight to set your time", game.skin);
            noTime.setFontScale(0.72f);
            noTime.setColor(0.45f, 0.55f, 0.70f, 0.85f);
            noTime.setAlignment(Align.center);
            boardTable.add(noTime).colspan(3).center().padTop(10f).row();
        }
    }

    private void addBoardRow(Table t, String rank, String name, String time,
                              Color color, boolean isHeader) {
        float fontScale = isHeader ? 0.75f : 0.85f;

        Label rankLbl = new Label(rank, game.skin);
        Label nameLbl = new Label(name, game.skin);
        Label timeLbl = new Label(time, game.skin);

        for (Label l : new Label[]{rankLbl, nameLbl, timeLbl}) {
            l.setFontScale(fontScale);
            l.setColor(color);
        }
        rankLbl.setAlignment(Align.center);
        timeLbl.setAlignment(Align.right);

        t.add(rankLbl).width(40f).padLeft(8f).padRight(4f);
        t.add(nameLbl).expandX().left().padLeft(4f);
        t.add(timeLbl).width(80f).padRight(8f).row();
    }

    @Override public void show() {
        activePlanet = 0;
        // Default to the most recently visited planet
        ShipData sd = ShipData.get();
        for (int i = ShipData.PLANETS.length - 1; i >= 0; i--) {
            if (sd.bestArrivalTimes[i] < Float.MAX_VALUE) {
                activePlanet = i;
                break;
            }
        }
        Gdx.input.setInputProcessor(stage);
        // Rebuild UI fresh to reflect latest planet unlock state
        stage.clear();
        buildUi();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(OdysseyTheme.SPACE_BG.r, OdysseyTheme.SPACE_BG.g,
                            OdysseyTheme.SPACE_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() {
        stage.dispose();
    }
}
