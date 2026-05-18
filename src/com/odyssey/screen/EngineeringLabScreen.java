package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
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

    // ---- Constants --------------------------------------------------------------

    private static final float WORLD_W = 16f;
    private static final float WORLD_H = 9f;
    private static final float GRAVITY = 0f;   // zero-g centrifuge — gravity wells are the only attractor
    private static final int   VEL_ITER = 6;
    private static final int   POS_ITER = 2;
    private static final float PPM      = 64f;

    private static final float RENDER_W = 1280f;
    private static final float RENDER_H = 720f;

    private static final float BALL_RADIUS        = 0.25f;
    private static final float BALL_DENSITY        = 1.0f;
    private static final float BALL_RESTITUTION    = 0.90f;
    private static final float WALL_RESTITUTION    = 0.65f;
    private static final float BUMPER_RADIUS       = 0.20f;
    private static final float BUMPER_RESTITUTION  = 1.40f;
    private static final float CENTRIFUGE_CX       = 8.0f;
    private static final float CENTRIFUGE_CY       = 4.5f;
    private static final float CENTRIFUGE_R        = 4.0f;
    private static final int   CENTRIFUGE_SEGS     = 36;
    private static final float CENTRIFUGE_RPM      = 5.0f;
    private static final int   MAX_BODIES           = 30;
    private static final int   MAX_INTERNS          = 10;
    private static final int   MAX_BUMPERS          = 4;
    private static final int   MAX_GRAVITY_WELLS    = 3;
    private static final float CENTRIFUGE_RPM_BASE   = 1.5f;   // starting ring speed
    private static final float CENTRIFUGE_RPM_MAX    = 9.0f;   // max ring speed
    private static final float CENTRIFUGE_RPM_ACCEL  = 0.18f;
    private static final float MAX_INTERN_SPEED      = 5.0f;

    // JPS multipliers for dynamic pricing  (cost = JPS × multiplier ≈ seconds of grind)
    private static final float INTERN_MULT   =   30f;
    private static final float BUMPER_MULT   =  100f;
    private static final float GRAVITY_MULT  =  300f;
    private static final float UPG_B1_MULT   =  500f;
    private static final float UPG_B2_MULT   = 1500f;
    private static final float UPG_G1_MULT   =  800f;
    private static final float UPG_G2_MULT   = 2000f;
    // Ring-speed milestones: auto-unlock in order as ring climbs
    private static final float[]  MILESTONE_RPMS  = {2.5f, 5.0f, 6.0f, 7.0f, 7.5f, 8.0f};
    private static final String[] MILESTONE_NAMES = {"Elastic", "Resonance", "Wall ×3", "Coll ×2", "Free +1", "Overdrive"};

    // Sector perk unlock thresholds (mirrors BridgeFlightScreen.SECTOR_DISTS / TOTAL_ROUTE)
    private static final int NUM_SECTORS = 4;

    private static final int PLACE_NONE    = 0;
    private static final int PLACE_BUMPER  = 1;
    private static final int PLACE_GRAVITY = 2;

    // Sprite draw sizes in pixels
    private static final float INTERN_W       = 64f;
    private static final float INTERN_H       = 64f;
    private static final float BUMPER_W       = 32f;
    private static final float BUMPER_H       = 32f;
    private static final float RING_TEX_SIZE  = 512f;

    // Centrifuge center in pixel space (constant — body never translates)
    private static final float CCX_PX = CENTRIFUGE_CX * PPM;   // 512
    private static final float CCY_PX = CENTRIFUGE_CY * PPM;   // 288

    // ---- Fields -----------------------------------------------------------------

    private final OdysseyGame game;

    // Box2D
    private World              world;
    private Body               centrifugeBody;
    private OrthographicCamera physCam;
    private FitViewport        physViewport;

    // Rendering
    private SpriteBatch        batch;
    private OrthographicCamera renderCam;
    private FitViewport        renderViewport;
    private Texture            texBackground;
    private Texture            texInternNormal;
    private Texture            texInternCyber;
    private Texture            texBumper;
    private Texture            texRing;
    private Texture            texGravField;

    // Scene2D
    private Stage      ui;
    private Label      joulesLabel;
    private Label      jpsLabel;
    private Label      outputLabel;
    private Label      uptimeLabel;
    private Label      destLabel;
    private TextButton btnAdd;
    private TextButton btnBumper;
    private TextButton btnGravityWell;

    // Bookkeeping
    private final Array<Body> balls      = new Array<>();
    private final Array<Body> bumpers    = new Array<>();
    private final Array<Body> attractors = new Array<>();
    private final Vector2     pullVec    = new Vector2();
    private final Vector3     touchWorld = new Vector3();
    private int   placementMode    = PLACE_NONE;
    private InputMultiplexer inputMux;
    private float jpsTimer         = 0f;
    private float lastJoules       = 0f;
    private float uptime           = 0f;

    // Upgradeable physics parameters (mutated by upgrade purchases)
    private float gravityPull   = 18f;    // safe base — no stable orbits at MAX_INTERN_SPEED
    private float gravityFieldR = 0.9f;
    private float bumperCoreR   = BUMPER_RADIUS;   // 0.20f → 0.35f at tier 2

    // Dynamic centrifuge target speed driven by JPS
    private float targetRPM = CENTRIFUGE_RPM_BASE;

    // Upgrade tiers (0 = base, 1 = first upgrade, 2 = maxed)
    private int bumperTier  = 0;
    private int gravityTier = 0;

    // Upgrade buttons + ring-speed display
    private TextButton btnUpgBumper;
    private TextButton btnUpgGravity;
    private TextButton btnFlight;
    private Label      ringSpeedLabel;
    private Label      configLabel;

    // Milestone auto-unlocks
    private final boolean[] milestoneAchieved = new boolean[6];
    private final Label[]   milestoneLabels   = new Label[6];
    private float currentBallRestitution = BALL_RESTITUTION;

    // ---- Construction -----------------------------------------------------------

    public EngineeringLabScreen(OdysseyGame game) {
        this.game = game;
        buildTextures();
        buildRendering();
        buildPhysics();
        buildUI();
    }

    // ---- Texture generation (Pixmap — no PNG files needed) ----------------------

    private void buildTextures() {
        texBackground   = genBackground();
        texInternNormal = genGlowCircle(64, 1.0f, 0.58f, 0.0f);
        texInternCyber  = genGlowCircle(64, 0.0f, 1.0f,  0.88f);
        texBumper       = genGlowCircle(32, 1.0f, 1.0f,  0.80f);
        texRing         = genCentrifugeRing(512);
        texGravField    = genGravField(128);
    }

    private Texture genGravField(int size) {
        Pixmap pm    = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx     = size * 0.5f;
        float outerR = size * 0.47f;
        float ringW  = size * 0.08f;
        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                float dx = px - cx, dy = py - cx;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist > outerR) continue;
                float ringDist = Math.abs(dist - (outerR - ringW * 0.5f));
                if (ringDist < ringW) {
                    float a = (1f - ringDist / ringW) * 0.70f;
                    pm.drawPixel(px, py, Color.rgba8888(0f, 0.85f, 1f, a));
                } else if (dist < outerR - ringW) {
                    float a = (dist / (outerR - ringW)) * 0.12f;
                    pm.drawPixel(px, py, Color.rgba8888(0f, 0.65f, 1f, a));
                }
            }
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    private Texture genBackground() {
        Pixmap pm = new Pixmap(1280, 720, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0.02f, 0.02f, 0.08f, 1f);
        pm.fill();

        // Dark interior circle behind centrifuge for depth
        int innerR = 210;
        int innerR2 = innerR * innerR;
        int bx = (int)(CCX_PX - innerR) - 1, ex = (int)(CCX_PX + innerR) + 1;
        int by = (int)(CCY_PX - innerR) - 1, ey = (int)(CCY_PX + innerR) + 1;
        for (int x = bx; x <= ex; x++) {
            for (int y = by; y <= ey; y++) {
                if (x < 0 || x >= 1280 || y < 0 || y >= 720) continue;
                float dx = x - CCX_PX, dy = y - CCY_PX;
                if (dx * dx + dy * dy < innerR2)
                    pm.drawPixel(x, y, Color.rgba8888(0.01f, 0.01f, 0.05f, 1f));
            }
        }

        // Stars — seeded so layout is consistent
        long seed = 7391L;
        for (int i = 0; i < 280; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = (int)(Math.abs(seed) % 1280);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sy = (int)(Math.abs(seed) % 720);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float b = 0.35f + (Math.abs(seed) % 100) / 153f;
            pm.drawPixel(sx, sy, Color.rgba8888(b, b, b * 0.88f, 1f));
        }

        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    private Texture genGlowCircle(int size, float cr, float cg, float cb) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx    = size * 0.5f;
        float coreR = size * 0.18f;
        float glowR = size * 0.46f;
        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                float dx = px - cx, dy = py - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > glowR) continue;
                float alpha;
                float r, g, b;
                if (dist <= coreR) {
                    alpha = 1f;
                    r = Math.min(cr + 0.35f, 1f);
                    g = Math.min(cg + 0.35f, 1f);
                    b = Math.min(cb + 0.35f, 1f);
                } else {
                    alpha = 1f - (dist - coreR) / (glowR - coreR);
                    r = cr; g = cg; b = cb;
                }
                pm.drawPixel(px, py, Color.rgba8888(r, g, b, alpha));
            }
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    private Texture genCentrifugeRing(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx      = size * 0.5f;
        float outerR  = size * 0.484f;   // 248
        float innerR  = size * 0.422f;   // 216  — ring is 32px thick
        float glowOut = 16f;
        float glowIn  = 16f;
        float accentR = size * 0.352f;   // 180  — inner accent ring
        float accentW = 3f;

        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                float dx = px - cx, dy = py - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                // ---- Main orange ring ----
                if (dist >= innerR && dist <= outerR) {
                    float t = (dist - innerR) / (outerR - innerR);
                    float bright = 0.65f + 0.35f * t;
                    pm.drawPixel(px, py, Color.rgba8888(bright, bright * 0.52f, 0f, 1f));
                }
                // Outer glow
                else if (dist > outerR && dist < outerR + glowOut) {
                    float a = (1f - (dist - outerR) / glowOut) * 0.55f;
                    pm.drawPixel(px, py, Color.rgba8888(1f, 0.55f, 0f, a));
                }
                // Inner glow (bleeds inside ring toward center)
                else if (dist < innerR && dist > innerR - glowIn) {
                    float a = (1f - (innerR - dist) / glowIn) * 0.65f;
                    pm.drawPixel(px, py, Color.rgba8888(1f, 0.65f, 0.1f, a));
                }
                // ---- Cyan inner accent ring ----
                else if (dist >= accentR - accentW && dist <= accentR + accentW) {
                    float a = 1f - Math.abs(dist - accentR) / accentW;
                    pm.drawPixel(px, py, Color.rgba8888(0f, 0.83f, 1f, a * 0.75f));
                }
            }
        }

        // 8 segment dividers (dark notches cut through the ring)
        for (int seg = 0; seg < 8; seg++) {
            double angle = seg * Math.PI * 2.0 / 8;
            for (float r = innerR - 4; r <= outerR + 4; r += 0.5f) {
                int nx = (int)(cx + Math.cos(angle) * r);
                int ny = (int)(cx + Math.sin(angle) * r);
                if (nx >= 0 && nx < size && ny >= 0 && ny < size)
                    pm.drawPixel(nx, ny, Color.rgba8888(0.02f, 0.01f, 0.04f, 0.9f));
            }
        }

        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    // ---- Rendering setup --------------------------------------------------------

    private void buildRendering() {
        batch = new SpriteBatch();
        renderCam = new OrthographicCamera();
        renderViewport = new FitViewport(RENDER_W, RENDER_H, renderCam);
        renderCam.position.set(RENDER_W / 2f, RENDER_H / 2f, 0f);
    }

    // ---- Physics setup ----------------------------------------------------------

    private void buildPhysics() {
        world = new World(new Vector2(0, GRAVITY * ShipData.get().planetGravityMultiplier), true);
        world.setContactListener(new EnergyContactListener());

        physCam = new OrthographicCamera();
        physViewport = new FitViewport(WORLD_W, WORLD_H, physCam);
        physCam.position.set(WORLD_W / 2f, WORLD_H / 2f, 0f);

        spawnWalls();
        spawnBall(CENTRIFUGE_CX - CENTRIFUGE_R * 0.4f, CENTRIFUGE_CY + CENTRIFUGE_R * 0.4f);
        spawnBall(CENTRIFUGE_CX + CENTRIFUGE_R * 0.4f, CENTRIFUGE_CY - CENTRIFUGE_R * 0.4f);
    }

    private void spawnWalls() {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(CENTRIFUGE_CX, CENTRIFUGE_CY);
        centrifugeBody = world.createBody(bd);

        float step = (float)(2 * Math.PI / CENTRIFUGE_SEGS);
        EdgeShape edge = new EdgeShape();
        FixtureDef fd  = new FixtureDef();
        fd.shape       = edge;
        fd.restitution = WALL_RESTITUTION;
        fd.friction    = 0.05f;

        for (int i = 0; i < CENTRIFUGE_SEGS; i++) {
            float a0 = step * i, a1 = step * (i + 1);
            edge.set(
                (float)Math.cos(a0) * CENTRIFUGE_R, (float)Math.sin(a0) * CENTRIFUGE_R,
                (float)Math.cos(a1) * CENTRIFUGE_R, (float)Math.sin(a1) * CENTRIFUGE_R
            );
            centrifugeBody.createFixture(fd);
        }
        edge.dispose();
        centrifugeBody.setAngularVelocity(0f);
    }

    private void spawnBall(float x, float y) {
        if (balls.size >= internCap()) return;
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(x, y);
        bd.linearDamping  = 0f;
        bd.angularDamping = 0f;
        CircleShape circle = new CircleShape();
        circle.setRadius(BALL_RADIUS);
        FixtureDef fd  = new FixtureDef();
        fd.shape       = circle;
        fd.density     = BALL_DENSITY;
        fd.restitution = currentBallRestitution;
        fd.friction    = 0.2f;
        Body body = world.createBody(bd);
        body.setBullet(true);
        body.createFixture(fd);
        body.setUserData("INTERN_NORMAL");
        float kickAngle = MathUtils.random(MathUtils.PI2);
        body.setLinearVelocity(MathUtils.cos(kickAngle) * 3.0f, MathUtils.sin(kickAngle) * 3.0f);
        circle.dispose();
        balls.add(body);
    }

    private void spawnCentrifugeBumper(float wx, float wy) {
        if (balls.size + bumpers.size >= MAX_BODIES) return;
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;
        bd.position.set(wx, wy);
        CircleShape circle = new CircleShape();
        circle.setRadius(bumperCoreR);
        FixtureDef fd  = new FixtureDef();
        fd.shape       = circle;
        fd.restitution = BUMPER_RESTITUTION;
        fd.friction    = 0f;
        Body body = world.createBody(bd);
        body.createFixture(fd);
        body.setUserData("BUMPER");
        circle.dispose();
        bumpers.add(body);
    }

    public void spawnAttractorBumper(float wx, float wy) {
        BodyDef bd = new BodyDef();
        bd.type    = BodyDef.BodyType.StaticBody;
        bd.position.set(wx, wy);
        Body body = world.createBody(bd);
        body.setUserData("ATTRACTOR_FIELD");

        CircleShape core = new CircleShape();
        core.setRadius(0.15f);
        FixtureDef cfd  = new FixtureDef();
        cfd.shape       = core;
        cfd.restitution = 1.2f;
        cfd.friction    = 0f;
        body.createFixture(cfd).setUserData("BUMPER");
        core.dispose();

        CircleShape field = new CircleShape();
        field.setRadius(gravityFieldR);
        FixtureDef ffd = new FixtureDef();
        ffd.shape      = field;
        ffd.isSensor   = true;
        body.createFixture(ffd);
        field.dispose();

        attractors.add(body);
    }

    // ---- UI setup ---------------------------------------------------------------

    private void buildUI() {
        ui = new Stage(new ScreenViewport());

        Color panelBg     = new Color(0.04f, 0.06f, 0.16f, 0.90f);
        Color panelBorder = new Color(0.00f, 0.60f, 0.80f, 0.95f);

        // ---- Top bar ----
        Table topBar = new Table();
        topBar.setFillParent(true);
        topBar.top();

        Table topInner = new Table();
        topInner.background(game.skin.newDrawable("white", panelBg));

        Label screenTitle = new Label("ENGINEERING BAY", game.skin, "title");
        configLabel = new Label("STATION 1/4  SOLARA SYSTEM", game.skin);

        outputLabel = new Label("OUTPUT: 0.00 J/s", game.skin);
        uptimeLabel = new Label("UPTIME: 0:00:00",  game.skin);

        topInner.add(screenTitle).left().padLeft(12).padRight(32);
        topInner.add(configLabel).left().expandX();
        topInner.add(outputLabel).right().padRight(12);
        topInner.add(uptimeLabel).right().padRight(12);

        topBar.add(topInner).growX().height(44).row();

        // Milestone progress strip
        Table milestoneStrip = new Table();
        milestoneStrip.background(game.skin.newDrawable("white", new Color(0.03f, 0.05f, 0.12f, 0.95f)));
        milestoneStrip.left().pad(4, 10, 4, 10);
        for (int i = 0; i < MILESTONE_RPMS.length; i++) {
            milestoneLabels[i] = new Label(
                String.format("%.1f  %s", MILESTONE_RPMS[i], MILESTONE_NAMES[i]), game.skin);
            milestoneLabels[i].setColor(0.4f, 0.4f, 0.4f, 1f);
            milestoneStrip.add(milestoneLabels[i]).padRight(18);
        }
        topBar.add(milestoneStrip).growX().height(28).row();

        // ---- Left panel ----
        Table leftPanel = new Table();
        leftPanel.setFillParent(true);
        leftPanel.left().top().padTop(80).padLeft(6);

        Table leftInner = new Table();
        leftInner.background(game.skin.newDrawable("white", panelBg));
        leftInner.pad(10);

        joulesLabel    = new Label("0.00 J", game.skin);
        jpsLabel       = new Label("0.00 J/s", game.skin);
        ringSpeedLabel = new Label("RING: 1.5 / 9.0 r/s", game.skin);
        btnAdd        = new TextButton("+ Hire Intern (10 J)",    game.skin);
        btnBumper     = new TextButton("+ Place Bumper (25 J)",   game.skin);
        btnGravityWell = new TextButton("+ Gravity Well (600 J)", game.skin);
        btnUpgBumper       = new TextButton("Upg Bumper: Fast Bounce (2000 J)", game.skin);
        btnUpgGravity      = new TextButton("Upg Gravity: Deep Pull (5000 J)",  game.skin);

        leftInner.add(new Label("ENERGY", game.skin)).left().padBottom(2).row();
        leftInner.add(joulesLabel).left().padBottom(2).row();
        leftInner.add(jpsLabel).left().padBottom(2).row();
        leftInner.add(ringSpeedLabel).left().padBottom(10).row();
        leftInner.add(btnAdd).growX().padBottom(4).row();
        leftInner.add(new Label("--- BUMPERS ---", game.skin)).left().padBottom(2).row();
        leftInner.add(btnBumper).growX().padBottom(2).row();
        leftInner.add(btnUpgBumper).growX().padBottom(6).row();
        leftInner.add(new Label("--- GRAVITY ---", game.skin)).left().padBottom(2).row();
        leftInner.add(btnGravityWell).growX().padBottom(2).row();
        leftInner.add(btnUpgGravity).growX().padBottom(6).row();
        leftPanel.add(leftInner).width(220).row();

        // ---- Bottom bar ----
        Table bottomBar = new Table();
        bottomBar.setFillParent(true);
        bottomBar.bottom();

        Table bottomInner = new Table();
        bottomInner.background(game.skin.newDrawable("white", panelBg));
        bottomInner.pad(6, 12, 6, 12);

        destLabel = new Label("DESTINATION: SOLARA PRIME", game.skin);
        btnFlight = new TextButton("LAUNCH FLIGHT", game.skin);
        TextButton btnBack = new TextButton("< MENU", game.skin);

        TextButton btnCheat = new TextButton(">> CHEAT <<", game.skin);
        btnCheat.setColor(1f, 0.3f, 0.3f, 1f);

        bottomInner.add(destLabel).left().expandX();
        bottomInner.add(btnCheat).right().padRight(16);
        bottomInner.add(btnBack).right().padRight(8);
        bottomInner.add(btnFlight).right();

        bottomBar.add(bottomInner).growX().height(44).row();

        // ---- Listeners ----
        btnAdd.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (balls.size < internCap() && ShipData.get().spend(price(INTERN_MULT))) {
                    float angle = MathUtils.random(MathUtils.PI2);
                    float r     = MathUtils.random(0.5f, CENTRIFUGE_R * 0.55f);
                    spawnBall(CENTRIFUGE_CX + MathUtils.cos(angle) * r,
                              CENTRIFUGE_CY + MathUtils.sin(angle) * r);
                }
            }
        });
        btnBumper.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                int maxB = maxBumpersAllowed();
                if (maxB == 0 || bumpers.size >= maxB) return;
                if (ShipData.get().totalJoules >= price(BUMPER_MULT))
                    placementMode = (placementMode == PLACE_BUMPER) ? PLACE_NONE : PLACE_BUMPER;
            }
        });
        btnGravityWell.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!gravityUnlocked() || attractors.size >= MAX_GRAVITY_WELLS) return;
                if (ShipData.get().totalJoules >= price(GRAVITY_MULT))
                    placementMode = (placementMode == PLACE_GRAVITY) ? PLACE_NONE : PLACE_GRAVITY;
            }
        });
        btnUpgBumper.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!tierUpgradesUnlocked()) return;
                ShipData sd = ShipData.get();
                if (bumperTier == 0 && sd.spend(price(UPG_B1_MULT))) {
                    sd.bumperEnergyMult = 8f;
                    bumperTier = 1;
                } else if (bumperTier == 1 && sd.spend(price(UPG_B2_MULT))) {
                    bumperCoreR = 0.35f;
                    applyBumperWideUpgrade();
                    sd.maxInternSpeed = 7f;
                    bumperTier = 2;
                }
            }
        });
        btnUpgGravity.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!tierUpgradesUnlocked()) return;
                ShipData sd = ShipData.get();
                if (gravityTier == 0 && sd.spend(price(UPG_G1_MULT))) {
                    gravityPull = 110f;
                    gravityFieldR = 1.7f;
                    rebuildAttractorFields();
                    gravityTier = 1;
                } else if (gravityTier == 1 && sd.spend(price(UPG_G2_MULT))) {
                    gravityPull = 180f;
                    gravityFieldR = 2.4f;
                    rebuildAttractorFields();
                    sd.maxInternSpeed = Math.max(sd.maxInternSpeed, 10f);
                    gravityTier = 2;
                }
            }
        });
        btnCheat.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                ShipData.get().addJoules(1_000_000f);
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

        ui.addActor(topBar);
        ui.addActor(leftPanel);
        ui.addActor(bottomBar);

        inputMux = new InputMultiplexer(ui, new InputAdapter() {
            @Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
                if (btn == 1) { placementMode = PLACE_NONE; return true; }
                if (btn != 0 || placementMode == PLACE_NONE) return false;
                touchWorld.set(sx, sy, 0);
                physViewport.unproject(touchWorld);
                float wx = touchWorld.x, wy = touchWorld.y;
                float dx = wx - CENTRIFUGE_CX, dy = wy - CENTRIFUGE_CY;
                if (dx * dx + dy * dy >= CENTRIFUGE_R * CENTRIFUGE_R) return false;
                ShipData sd = ShipData.get();
                if (placementMode == PLACE_BUMPER && bumpers.size < maxBumpersAllowed() && sd.spend(price(BUMPER_MULT))) {
                    spawnCentrifugeBumper(wx, wy);
                    placementMode = PLACE_NONE;
                } else if (placementMode == PLACE_GRAVITY && attractors.size < MAX_GRAVITY_WELLS && sd.spend(price(GRAVITY_MULT))) {
                    spawnAttractorBumper(wx, wy);
                    placementMode = PLACE_NONE;
                }
                return true;
            }
        });
    }

    // ---- Lifecycle --------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputMux);
        world.setGravity(new Vector2(0, GRAVITY * ShipData.get().planetGravityMultiplier));
        applySectorPerks();
    }

    private void applySectorPerks() {
        int sr = ShipData.get().sectorReached;
        if (sr >= 0 && !milestoneAchieved[0]) { milestoneAchieved[0] = true; applyElasticWalls(); }
        if (sr >= 1 && !milestoneAchieved[1]) { milestoneAchieved[1] = true; applyResonance(); }
        if (sr >= 2 && !milestoneAchieved[2]) { milestoneAchieved[2] = true; ShipData.get().wallEnergyMult = 3f; }
        if (sr >= 2 && !milestoneAchieved[3]) { milestoneAchieved[3] = true; ShipData.get().collisionEnergyMult = 2f; }
        if (sr >= 2 && !milestoneAchieved[4]) { milestoneAchieved[4] = true; applyFreeIntern(); }
        if (sr >= 3 && !milestoneAchieved[5]) { milestoneAchieved[5] = true; applyOverdrive(); }
    }

    @Override
    public void render(float delta) {
        uptime += delta;
        stepPhysics(delta);
        updateJPS(delta);
        checkMilestones();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderViewport.apply();
        renderCam.update();
        batch.setProjectionMatrix(renderCam.combined);

        batch.begin();
        drawBackground();
        drawCentrifuge();
        drawAttractors();
        drawBumpers();
        drawInterns();
        drawPlacementPreview();
        batch.end();

        // UI overlay
        ui.getViewport().apply();
        ui.act(delta);
        ShipData sd = ShipData.get();
        joulesLabel.setText(String.format("%-14s", formatNumber(sd.totalJoules) + " J"));
        jpsLabel.setText(String.format("%-14s", formatNumber(sd.currentJPS) + " J/s"));
        outputLabel.setText("OUTPUT: " + formatNumber(sd.currentJPS) + " J/s");
        uptimeLabel.setText("UPTIME: " + formatUptime(uptime));
        int cap  = internCap();
        int maxB = maxBumpersAllowed();
        if (balls.size >= cap) {
            String cpNeeded = cap == 4 ? "reach CP I" : cap == 6 ? "reach CP II" : "reach CP III";
            btnAdd.setText(cap < MAX_INTERNS
                ? String.format("LOCKED %d/%d  (%s)", balls.size, cap, cpNeeded)
                : "Interns FULL (10/10)");
        } else {
            btnAdd.setText(String.format("+ Intern (%.0f J)  %d/%d", price(INTERN_MULT), balls.size, cap));
        }
        if (maxB == 0) {
            btnBumper.setText("BUMPERS locked — reach CP I");
        } else if (bumpers.size >= maxB) {
            btnBumper.setText(String.format("Bumpers FULL (%d/%d)", bumpers.size, maxB));
        } else if (placementMode == PLACE_BUMPER) {
            btnBumper.setText(">> Click in ring");
        } else {
            btnBumper.setText(String.format("+ Bumper (%.0f J)  %d/%d", price(BUMPER_MULT), bumpers.size, maxB));
        }
        if (!gravityUnlocked()) {
            btnGravityWell.setText("GRAVITY locked — reach CP II");
        } else if (attractors.size >= MAX_GRAVITY_WELLS) {
            btnGravityWell.setText(String.format("Gravity FULL (%d/%d)", attractors.size, MAX_GRAVITY_WELLS));
        } else if (placementMode == PLACE_GRAVITY) {
            btnGravityWell.setText(">> Click in ring");
        } else {
            btnGravityWell.setText(String.format("+ Gravity (%.0f J)  %d/%d", price(GRAVITY_MULT), attractors.size, MAX_GRAVITY_WELLS));
        }
        float ringNow = centrifugeBody.getAngularVelocity();
        ringSpeedLabel.setText(String.format("RING: %.1f / %.1f r/s", ringNow, CENTRIFUGE_RPM_MAX));

        if (!tierUpgradesUnlocked()) {
            btnUpgBumper.setText("UPGRADES locked — reach CP III");
            btnUpgGravity.setText("UPGRADES locked — reach CP III");
        } else {
            if (bumperTier >= 2) {
                btnUpgBumper.setText("Bumper: MAXED");
            } else {
                String bName = bumperTier == 0 ? "Fast Bounce" : "Wide Core";
                btnUpgBumper.setText(String.format("Upg %s (%.0f J)", bName,
                    bumperTier == 0 ? price(UPG_B1_MULT) : price(UPG_B2_MULT)));
            }
            if (gravityTier >= 2) {
                btnUpgGravity.setText("Gravity: MAXED");
            } else {
                String gName = gravityTier == 0 ? "Deep Pull" : "Wide Field";
                btnUpgGravity.setText(String.format("Upg %s (%.0f J)", gName,
                    gravityTier == 0 ? price(UPG_G1_MULT) : price(UPG_G2_MULT)));
            }
        }
        int sr = sd.sectorReached;
        String sectorStr = sr < 0 ? "0/" + NUM_SECTORS : (sr + 1) + "/" + NUM_SECTORS;
        configLabel.setText("POWER: " + formatNumber(sd.powerGenerated) + " J  |  " + sectorStr + " sectors");
        float startDist = sr >= 0 ? BridgeFlightScreen.SECTOR_DISTS[sr] : 0f;
        float energySinceLaunch = sd.powerGenerated - sd.energyAtLastLaunch;
        float estNewAccum = Math.min(startDist + energySinceLaunch * BridgeFlightScreen.ENERGY_AU_SCALE, BridgeFlightScreen.TOTAL_ROUTE);
        int estSector = -1;
        for (int i = 0; i < BridgeFlightScreen.SECTOR_DISTS.length; i++)
            if (estNewAccum >= BridgeFlightScreen.SECTOR_DISTS[i]) estSector = i;
        String reachStr = estSector >= 0
            ? (estSector == 3 ? "EMBER!" : "Checkpoint " + (estSector + 1))
            : "no checkpoint";
        btnFlight.setText(String.format("LAUNCH  (est: %s  +%.0f AU)", reachStr, energySinceLaunch * BridgeFlightScreen.ENERGY_AU_SCALE));

        // ETA strip — time until each unreached sector is ready to claim on next launch
        StringBuilder etaSb = new StringBuilder();
        for (int i = 0; i < BridgeFlightScreen.SECTOR_DISTS.length; i++) {
            if (i <= sr) continue;
            float energyNeeded = (BridgeFlightScreen.SECTOR_DISTS[i] - startDist) / BridgeFlightScreen.ENERGY_AU_SCALE;
            float remaining    = Math.max(0f, energyNeeded - energySinceLaunch);
            String lbl = (i == BridgeFlightScreen.SECTOR_DISTS.length - 1) ? "EMBER" : "CP " + (i + 1);
            if (etaSb.length() > 0) etaSb.append("    ");
            if (remaining <= 0f) {
                etaSb.append(lbl).append(": READY");
            } else if (sd.currentJPS < 0.1f) {
                etaSb.append(lbl).append(": ---");
            } else {
                int secs = (int)(remaining / sd.currentJPS);
                if (secs < 3600) etaSb.append(String.format("%s: %d:%02d", lbl, secs / 60, secs % 60));
                else             etaSb.append(String.format("%s: %dh%02dm", lbl, secs / 3600, (secs % 3600) / 60));
            }
        }
        destLabel.setText(etaSb.toString());
        ui.draw();
    }

    private void drawBackground() {
        batch.draw(texBackground, 0, 0, RENDER_W, RENDER_H);
    }

    private void drawCentrifuge() {
        float angle = centrifugeBody.getAngle() * MathUtils.radiansToDegrees;
        float half  = RING_TEX_SIZE * 0.5f;
        batch.draw(texRing,
            CCX_PX - half, CCY_PX - half,
            half, half,
            RING_TEX_SIZE, RING_TEX_SIZE,
            1f, 1f, angle,
            0, 0, (int)RING_TEX_SIZE, (int)RING_TEX_SIZE,
            false, false
        );
    }

    private void drawAttractors() {
        if (attractors.size == 0) return;
        float fieldDiam = gravityFieldR * 2f * PPM;
        float fHalf     = fieldDiam * 0.5f;
        float hw        = BUMPER_W * 0.5f, hh = BUMPER_H * 0.5f;
        for (int i = 0; i < attractors.size; i++) {
            Vector2 pos = attractors.get(i).getPosition();
            float px = pos.x * PPM, py = pos.y * PPM;
            batch.draw(texGravField, px - fHalf, py - fHalf, fieldDiam, fieldDiam);
            batch.draw(texBumper,    px - hw,    py - hh,    BUMPER_W,  BUMPER_H);
        }
    }

    private void drawBumpers() {
        float hw = BUMPER_W * 0.5f, hh = BUMPER_H * 0.5f;
        for (int i = 0, n = bumpers.size; i < n; i++) {
            Vector2 pos = bumpers.get(i).getPosition();
            batch.draw(texBumper, pos.x * PPM - hw, pos.y * PPM - hh, BUMPER_W, BUMPER_H);
        }
    }

    private void drawInterns() {
        float hw = INTERN_W * 0.5f, hh = INTERN_H * 0.5f;
        for (int i = 0, n = balls.size; i < n; i++) {
            Body  body = balls.get(i);
            Texture tex = "INTERN_CYBER".equals(body.getUserData()) ? texInternCyber : texInternNormal;
            batch.draw(tex, body.getPosition().x * PPM - hw, body.getPosition().y * PPM - hh,
                INTERN_W, INTERN_H);
        }
    }

    private void drawPlacementPreview() {
        if (placementMode == PLACE_NONE) return;
        touchWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        physViewport.unproject(touchWorld);
        float wx = touchWorld.x, wy = touchWorld.y;
        float dx = wx - CENTRIFUGE_CX, dy = wy - CENTRIFUGE_CY;
        if (dx * dx + dy * dy >= CENTRIFUGE_R * CENTRIFUGE_R) return;
        float px = wx * PPM, py = wy * PPM;
        batch.setColor(1f, 1f, 1f, 0.5f);
        if (placementMode == PLACE_GRAVITY) {
            float fd = gravityFieldR * 2f * PPM, fh = fd * 0.5f;
            batch.draw(texGravField, px - fh, py - fh, fd, fd);
        }
        batch.draw(texBumper, px - BUMPER_W * 0.5f, py - BUMPER_H * 0.5f, BUMPER_W, BUMPER_H);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void applyBumperWideUpgrade() {
        // Recreate all bumper fixtures with the new bumperCoreR radius
        for (int i = 0; i < bumpers.size; i++) {
            Body body = bumpers.get(i);
            Array<Fixture> fixtures = body.getFixtureList();
            for (int f = fixtures.size - 1; f >= 0; f--)
                body.destroyFixture(fixtures.get(f));
            CircleShape circle = new CircleShape();
            circle.setRadius(bumperCoreR);
            FixtureDef fd = new FixtureDef();
            fd.shape = circle;
            fd.restitution = BUMPER_RESTITUTION;
            fd.friction = 0f;
            body.createFixture(fd);
            circle.dispose();
        }
    }

    private void rebuildAttractorFields() {
        // Recreate sensor fixture on each attractor with updated gravityFieldR
        for (int i = 0; i < attractors.size; i++) {
            Body body = attractors.get(i);
            Array<Fixture> fixtures = body.getFixtureList();
            // Last fixture is the sensor field; keep core (fixture 0)
            if (fixtures.size > 1) body.destroyFixture(fixtures.get(fixtures.size - 1));
            CircleShape field = new CircleShape();
            field.setRadius(gravityFieldR);
            FixtureDef ffd = new FixtureDef();
            ffd.shape = field;
            ffd.isSensor = true;
            body.createFixture(ffd);
            field.dispose();
        }
    }

    private void checkMilestones() {
        int nextIdx = -1;
        for (int i = 0; i < MILESTONE_RPMS.length; i++) {
            if (!milestoneAchieved[i] && targetRPM >= MILESTONE_RPMS[i]) {
                milestoneAchieved[i] = true;
                switch (i) {
                    case 0: applyElasticWalls();                        break;
                    case 1: applyResonance();                           break;
                    case 2: ShipData.get().wallEnergyMult = 3f;         break;
                    case 3: ShipData.get().collisionEnergyMult = 2f;    break;
                    case 4: applyFreeIntern();                          break;
                    case 5: applyOverdrive();                           break;
                }
            }
            if (!milestoneAchieved[i] && nextIdx < 0) nextIdx = i;
        }
        for (int i = 0; i < MILESTONE_RPMS.length; i++) {
            if (milestoneAchieved[i])
                milestoneLabels[i].setColor(0.27f, 1f, 0.55f, 1f);   // bright green
            else if (i == nextIdx)
                milestoneLabels[i].setColor(1f, 0.85f, 0.2f, 1f);    // yellow — next target
            else
                milestoneLabels[i].setColor(0.4f, 0.4f, 0.4f, 1f);   // dim gray
        }
    }

    private void applyElasticWalls() {
        Array<Fixture> fx = centrifugeBody.getFixtureList();
        for (int i = 0; i < fx.size; i++) fx.get(i).setRestitution(0.82f);
    }

    private void applyFreeIntern() {
        if (balls.size < internCap())
            spawnBall(CENTRIFUGE_CX, CENTRIFUGE_CY + CENTRIFUGE_R * 0.3f);
    }

    private void applyResonance() {
        currentBallRestitution = 0.97f;
        for (int i = 0; i < balls.size; i++) {
            Array<Fixture> fx = balls.get(i).getFixtureList();
            for (int f = 0; f < fx.size; f++) fx.get(f).setRestitution(currentBallRestitution);
        }
    }

    private void applyOverdrive() {
        ShipData sd = ShipData.get();
        sd.bumperEnergyMult      = 15f;
        sd.internBoostStrength   = 3f;
    }

    private void stepPhysics(float delta) {
        float jps = Math.max(ShipData.get().currentJPS, 0f);
        targetRPM = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.8f + (float)Math.sqrt(jps) * 0.2f, CENTRIFUGE_RPM_MAX);

        float cur = centrifugeBody.getAngularVelocity();
        if (cur < targetRPM)
            centrifugeBody.setAngularVelocity(Math.min(cur + CENTRIFUGE_RPM_ACCEL * delta, targetRPM));
        else if (cur > targetRPM)
            centrifugeBody.setAngularVelocity(Math.max(cur - CENTRIFUGE_RPM_ACCEL * delta, targetRPM));

        for (int i = 0; i < attractors.size; i++) {
            Vector2 aPos = attractors.get(i).getPosition();
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                pullVec.set(aPos).sub(ball.getPosition());
                float dist = pullVec.len();
                if (dist < gravityFieldR && dist > 0.01f) {
                    pullVec.nor(); // unit vector toward attractor
                    float dot = ball.getLinearVelocity().dot(pullVec);
                    float falloff = 1f - dist / gravityFieldR;
                    float strength;
                    if (dot > 0f) {
                        // approaching: pull inward
                        strength = gravityPull * falloff;
                    } else {
                        // exiting: push outward — slingshot effect
                        strength = -gravityPull * 0.8f * falloff;
                    }
                    ball.applyForceToCenter(pullVec.scl(strength * ball.getMass()), true);
                }
            }
        }

        world.step(Math.min(delta, 1f / 30f), VEL_ITER, POS_ITER);

        float speedCap = ShipData.get().maxInternSpeed;
        for (int j = 0; j < balls.size; j++) {
            Body ball = balls.get(j);
            Vector2 v  = ball.getLinearVelocity();
            float   spd = v.len();
            if (spd > speedCap)
                ball.setLinearVelocity(v.x * speedCap / spd, v.y * speedCap / spd);
        }
    }

    private void updateJPS(float delta) {
        jpsTimer += delta;
        if (jpsTimer >= 1f) {
            float cur = ShipData.get().totalJoules;
            ShipData.get().currentJPS = (cur - lastJoules) / jpsTimer;
            lastJoules = cur;
            jpsTimer   = 0f;
        }
    }

    // ---- Helpers ----------------------------------------------------------------


    private int internCap() {
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 10;
        if (sr >= 1) return 8;
        if (sr >= 0) return 6;
        return 4;
    }

    private int maxBumpersAllowed() {
        int sr = ShipData.get().sectorReached;
        if (sr >= 1) return 4;
        if (sr >= 0) return 2;
        return 0;
    }

    private boolean gravityUnlocked()       { return ShipData.get().sectorReached >= 1; }
    private boolean tierUpgradesUnlocked()  { return ShipData.get().sectorReached >= 2; }

    private float price(float mult) {
        return Math.max(20f, ShipData.get().currentJPS * mult);
    }

    private static String formatNumber(float v) {
        if (v >= 1_000_000_000f) return String.format("%.2fB", v / 1_000_000_000f);
        if (v >= 1_000_000f)     return String.format("%.2fM", v / 1_000_000f);
        if (v >= 1_000f)         return String.format("%.2fK", v / 1_000f);
        return String.format("%.2f", v);
    }

    private static String formatUptime(float sec) {
        int s = (int) sec;
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static String getPlanetName() {
        ShipData sd = ShipData.get();
        if (sd.targetPlanetDistance <=  1000f) return "SOLARA PRIME";
        if (sd.targetPlanetDistance <=  2500f) return "EMBER IV";
        if (sd.targetPlanetDistance <=  5000f) return "FROSTHEIM";
        return "NOVA RIFT";
    }

    // ---- Resize / Dispose -------------------------------------------------------

    @Override
    public void resize(int w, int h) {
        renderViewport.update(w, h, true);
        physViewport.update(w, h, true);
        ui.getViewport().update(w, h, true);
    }

    @Override
    public void dispose() {
        world.dispose();
        batch.dispose();
        texBackground.dispose();
        texInternNormal.dispose();
        texInternCyber.dispose();
        texBumper.dispose();
        texRing.dispose();
        texGravField.dispose();
        ui.dispose();
    }
}
