package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;
import com.odyssey.physics.EnergyContactListener;

public class EngineeringLabScreen extends ScreenAdapter {

    // Physics constants
    private static final float   WORLD_W    = 16f;   // metres
    private static final float   WORLD_H    = 9f;
    private static final float   GRAVITY    = -9.8f;
    private static final int     VEL_ITER   = 6;
    private static final int     POS_ITER   = 2;
    private static final float   PPM        = 64f;   // pixels per metre

    private static final float   BALL_RADIUS     = 0.25f;
    private static final float   BALL_DENSITY    = 1.0f;
    private static final float   BALL_RESTITUTION = 0.75f;
    private static final float   WALL_RESTITUTION = 0.4f;
    private static final int     MAX_BALLS       = 30;

    private final OdysseyGame game;

    // Box2D
    private World              world;
    private Box2DDebugRenderer debugRenderer;
    private OrthographicCamera physCam;
    private FitViewport        physViewport;

    // Scene2D UI overlay
    private Stage  ui;
    private Label  joulesLabel;
    private Label  jpsLabel;

    // Ball bookkeeping
    private final Array<Body> balls = new Array<>();
    private float jpsAccum   = 0f;
    private float jpsTimer   = 0f;
    private float lastJoules = 0f;

    public EngineeringLabScreen(OdysseyGame game) {
        this.game = game;
        buildPhysics();
        buildUI();
    }

    // ---- Physics setup -------------------------------------------------------

    private void buildPhysics() {
        world = new World(new Vector2(0, GRAVITY * ShipData.get().planetGravityMultiplier), true);
        world.setContactListener(new EnergyContactListener());
        debugRenderer = new Box2DDebugRenderer();

        physCam = new OrthographicCamera();
        physViewport = new FitViewport(WORLD_W, WORLD_H, physCam);
        physCam.position.set(WORLD_W / 2f, WORLD_H / 2f, 0f);

        spawnWalls();
        spawnBall(WORLD_W / 2f, WORLD_H - 1f); // starter ball
    }

    private void spawnWalls() {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;

        ChainShape chain = new ChainShape();
        chain.createLoop(new float[]{
            0, 0,  WORLD_W, 0,  WORLD_W, WORLD_H,  0, WORLD_H
        });

        FixtureDef fd = new FixtureDef();
        fd.shape       = chain;
        fd.restitution = WALL_RESTITUTION;
        fd.friction    = 0.1f;

        world.createBody(bd).createFixture(fd);
        chain.dispose();
    }

    private void spawnBall(float x, float y) {
        if (balls.size >= MAX_BALLS) return;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(x, y);
        bd.linearDamping  = 0.02f;
        bd.angularDamping = 0.01f;

        CircleShape circle = new CircleShape();
        circle.setRadius(BALL_RADIUS);

        FixtureDef fd = new FixtureDef();
        fd.shape       = circle;
        fd.density     = BALL_DENSITY;
        fd.restitution = BALL_RESTITUTION;
        fd.friction    = 0.2f;

        Body body = world.createBody(bd);
        body.createFixture(fd);
        circle.dispose();

        balls.add(body);
    }

    // ---- UI setup ------------------------------------------------------------

    private void buildUI() {
        ui = new Stage(new ScreenViewport());

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(12);

        joulesLabel = new Label("Joules: 0.00 J", game.skin);
        jpsLabel    = new Label("JPS: 0.00 J/s",  game.skin);

        TextButton btnAdd    = new TextButton("+ Ball",       game.skin);
        TextButton btnFlight = new TextButton("Launch Flight", game.skin);
        TextButton btnBack   = new TextButton("< Menu",       game.skin);

        btnAdd.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                spawnBall(WORLD_W / 2f + (float)(Math.random() * 2 - 1), WORLD_H - 1f);
            }
        });
        btnFlight.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.BRIDGE_FLIGHT);
            }
        });
        btnBack.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                game.transitionTo(GameState.MAIN_MENU);
            }
        });

        root.add(joulesLabel).left().row();
        root.add(jpsLabel).left().padBottom(8).row();
        root.add(btnAdd).left().padBottom(4).row();
        root.add(btnFlight).left().padBottom(4).row();
        root.add(btnBack).left().row();

        ui.addActor(root);
    }

    // ---- Lifecycle -----------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(ui);
        // Reapply gravity in case gravity multiplier changed via Galactic Map
        world.setGravity(new Vector2(0, GRAVITY * ShipData.get().planetGravityMultiplier));
    }

    @Override
    public void render(float delta) {
        stepPhysics(delta);
        updateJPS(delta);

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        physViewport.apply();
        debugRenderer.render(world, physCam.combined);

        ui.getViewport().apply();
        ui.act(delta);
        joulesLabel.setText(String.format("Joules: %.2f J",  ShipData.get().totalJoules));
        jpsLabel.setText(String.format(   "JPS: %.2f J/s",   ShipData.get().currentJPS));
        ui.draw();
    }

    private void stepPhysics(float delta) {
        // Fixed-step accumulator — keeps Box2D deterministic at 60fps
        world.step(Math.min(delta, 1f / 30f), VEL_ITER, POS_ITER);
    }

    private void updateJPS(float delta) {
        jpsTimer += delta;
        if (jpsTimer >= 1f) {
            float currentJoules = ShipData.get().totalJoules;
            ShipData.get().currentJPS = (currentJoules - lastJoules) / jpsTimer;
            lastJoules = currentJoules;
            jpsTimer   = 0f;
        }
    }

    @Override
    public void resize(int w, int h) {
        physViewport.update(w, h, true);
        ui.getViewport().update(w, h, true);
    }

    @Override
    public void dispose() {
        world.dispose();
        debugRenderer.dispose();
        ui.dispose();
    }
}
