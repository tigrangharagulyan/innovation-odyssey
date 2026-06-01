package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.OdysseyTheme;
import com.odyssey.SoundManager;
import com.odyssey.ShipData;
import com.odyssey.physics.EnergyContactListener;
import com.odyssey.planet.PlanetDefinition;
import com.odyssey.planet.PlanetHooks;
import com.odyssey.planet.PlanetState;

public class EngineeringLabScreen extends ScreenAdapter {

    // ---- Constants --------------------------------------------------------------

    private static final float PPM      = 60f;
    private static final float RENDER_W = 480f;
    private static final float RENDER_H = 854f;
    private static final float WORLD_W  = 8.0f;          // RENDER_W / PPM
    private static final float WORLD_H  = 14.23f;        // RENDER_H / PPM — centrifuge at Y=10.5 fits well

    private static final float GRAVITY  = -4.5f;
    private static final int   VEL_ITER = 6;
    private static final int   POS_ITER = 2;

    private static final float BALL_RADIUS        = 0.25f;
    private static final float EMBER_BALL_RADIUS  = 0.38f;
    private static final float EMBER_INTERN_DRAW  = 52f;
    private static final float BALL_DENSITY        = 1.0f;
    private static final float BALL_RESTITUTION    = 0.90f;
    private static final float WALL_RESTITUTION    = 0.65f;
    private static final float BUMPER_RADIUS       = 0.20f;
    private static final float BUMPER_RESTITUTION  = 1.40f;

    // Level 2 — Frostheim exclusive objects (separate from Level 1 bumpers/gravity wells)
    private static final float ICICLE_RADIUS       = 0.25f;   // icicle node radius — same as orb
    private static final float PELLET_RADIUS       = 0.25f;   // snow pellet radius — same as orb
    // Collision filter bits — pellets and arm bumpers ignore each other
    private static final short CAT_DEFAULT     = 0x0001;
    private static final short CAT_PELLET      = 0x0002;
    private static final short CAT_ARM_BUMPER  = 0x0004;
    private static final short MASK_DEFAULT    = ~0;           // collides with everything
    private static final short MASK_PELLET     = (short)(~CAT_ARM_BUMPER);  // skip arm bumpers
    private static final short MASK_ARM_BUMPER = (short)(~CAT_PELLET);      // skip pellets
    private static final float TESLA_COIL_FIELD_R  = 1.5f;    // wider harvest zone than gravity pull radius
    private static final float SPIRAL_CAPTURE_R  = 0.9f;
    private static final float SPIRAL_ORBIT_R    = 0.5f;
    private static final float SPIRAL_ORBIT_RATE = 3.0f;  // rad/s
    private static final float SPIRAL_DURATION   = 2.0f;  // seconds before launch
    private static final float SPIRAL_LAUNCH_V   = 14.0f; // m/s launch speed
    private static final float[] ICICLE_COSTS      = {5_000f, 15_000f, 24_000f, 60_000f, 450_000f};
    private static final float[] TESLA_COIL_COSTS  = {15_000f, 36_000f, 105_000f, 900_000f};
    private static final float CENTRIFUGE_CX       = 4.0f;
    private static final float CENTRIFUGE_CY       = 8.5f;   // drum sits just above bottom controls panel
    private static final float CENTRIFUGE_R        = 3.2f;   // bigger drum — fills viewport width
    private static final int   CENTRIFUGE_SEGS     = 36;
    private static final float CENTRIFUGE_RPM      = 5.0f;
    private static final int   MAX_BODIES           = 30;
    private static final int   MAX_INTERNS          = 12;
    private static final int   MAX_BUMPERS          = 5;
    private static final int   MAX_GRAVITY_WELLS    = 4;
    private static final float CENTRIFUGE_RPM_BASE   = 1.5f;   // starting ring speed
    private static final float CENTRIFUGE_RPM_MAX    = 10.5f;   // max ring speed
    private static final float CENTRIFUGE_RPM_ACCEL  = 0.18f;
    private static final float MAX_INTERN_SPEED      = 5.0f;

    // Frostheim snowflake centrifuge shape
    private static final int   SNOWFLAKE_SEGS     = 216;
    private static final float SNOWFLAKE_ARM_R    = CENTRIFUGE_R * 1.08f;  // arm tips
    private static final float SNOWFLAKE_VALLEY_R = CENTRIFUGE_R * 0.72f;  // valley — high floor keeps drum prominent
    private static final float VALLEY_BLADE_HL    = 0.13f;   // half-length of spike triangle (meters)
    private static final float VALLEY_BLADE_HW    = 0.07f;   // half-width at base of spike (meters)
    // body center placed so base sits exactly on the valley wall
    private static final float VALLEY_BLADE_R     = SNOWFLAKE_VALLEY_R - VALLEY_BLADE_HL;
    private static final float FROSTHEIM_HUB_HALF = 10f;                   // seconds per pull/push phase

    // Spark (◆) costs — indexed by current count (2 interns spawn free so index = balls.size - 2)
    // index = balls.size - 2 (interns 3–12; first 2 are free)
    private static final float[] INTERN_COSTS  = {
        80, 500, 1_200, 3_000, 7_500,   // no CP – CP I  (interns 3–7)
        30_000, 40_000, 50_000,         // CP II         (interns 8–10)
        150_000, 200_000                // CP III        (interns 11–12)
    };
    // Final prices (not multiplied by price() — EmberIV uses these directly)
    private static final float[] EMBER_INTERN_COSTS = {
        1_500, 2_400,                   // pre-CP        (interns 3–4)
        5_000, 8_000, 15_000,           // CP I          (interns 5–7)
        75_000, 150_000, 180_000,       // CP II         (interns 8–10)
        200_000, 300_000                // CP III        (interns 11–12)
    };
    // Frostheim intern costs (3× base rates — no price() multiplier)
    private static final float[] FROSTHEIM_INTERN_COSTS = {
        150, 6_000, 10_000, 22_500, 22_500,   // entry – CP I   (interns 3–7)
        90_000, 120_000,                     // CP II          (interns 8–9)
        150_000, 450_000, 600_000            // CP III         (interns 10–12)
    };
    // Crystal costs — linear, separate currency from joules
    private static final float[] BUMPER_COSTS  = {500, 2_500, 25_000, 30_000, 100_000};
    private static final float[] GRAVITY_COSTS = {3000, 15_000, 150_000};
    // Ring-speed milestones: auto-unlock in order as ring climbs
    // Index 0 (Elastic Walls) is checkpoint-gated — only unlocked at CP I, never by ring speed
    private static final float[]  MILESTONE_RPMS  = {5.25f, 3.5f, 6.75f, 7.5f, 9.5f, 99f};
    private static final String[] MILESTONE_NAMES = {"Elastic Walls", "Speed Keep", "Wall ×3", "Hit ×2", "Bumper ×3", "-"};
    private static final String[] MILESTONE_DESCS = {
        "Interns bounce off walls harder — more chaos",
        "Interns keep 97% speed after every hit",
        "Each wall touch earns 3× more Space Points",
        "Intern-intern hits earn 2× more Space Points",
        "Bumpers deal 3× more Space Points per hit",
        "-"
    };
    private static final String[] EMBER_PERK_NAMES = {
        "Speed Keep", "Wall Energy", "Gravity Shift", "Portal Sync", "Reverse Field"
    };
    private static final String[] EMBER_PERK_DESCS = {
        "Interns keep 97% speed after every hit",
        "Wall bounces generate Space Points",
        "Manual gravity redirect · 5000 SP/shift",
        "Portals teleport in both directions",
        "Centrifuge spin direction reversed"
    };
    private static final String[] FROST_PERK_NAMES = {
        "Arm Bumpers", "Notch Guards", "Merge Burst", "Cryo Extension", "Double Vortex"
    };
    private static final String[] FROST_PERK_DESCS = {
        "Bumpers on each arm tip · +25 FS per hit",
        "Deflectors in small arm notches · +8 FS per hit",
        "Pellets merging back award +500 Energy",
        "Pellet merge window extended: 5s \u2192 7s",
        "Capture up to 2 orbs per coil"
    };

    // Solara (Level 1) checkpoint energy thresholds
    private static final float[] SOLARA_CP_ENERGIES = {2_000f, 10_000f, 60_000f, 200_000f};

    // Frostheim (Level 3) checkpoint energy thresholds
    private static final float[] FROSTHEIM_CP_ENERGIES = {4_000f, 24_000f, 120_000f, 150_000f};
    // Frostheim CP ring-speed thresholds — tuned to new intern caps (entry=4, cpI=7, cpII=9)
    private static final float FROSTHEIM_CP1_RPM = 5.5f;   // entry max 4.5 → need ≥1 CP I intern
    private static final float FROSTHEIM_CP2_RPM = 7.5f;   // cpI max 6.75 → need ≥1 CP II intern
    private static final float FROSTHEIM_CP3_RPM = 9.0f;   // cpII max 8.25 → need ≥2 CP III interns

    // Ember IV (Level 2) checkpoint energy thresholds — 1.6G high-yield
    private static final float[] EMBER_CP_ENERGIES = {5_000f, 60_000f, 300_000f, 1_000_000f};

    // Ember IV: Kinetic Blade costs (◆) — one blade unlocks per CP
    private static final float[] BLADE_COSTS = {1_500f, 12_000f, 30_000f};

    // Ember IV: Gravity well costs (◆) — scaled 1.5× vs Solara
    private static final float[] EMBER_GRAVITY_COSTS = {5_000f, 12_000f, 30_000f};

    // Ember IV: Cybernetic Hub upgrade costs per tier (paid in SP/crystals)
    // Tier 1 = Suction Boosted (+35%), Tier 2 = Blast Boosted (+50%), Tier 3 = Resonance Overdrive (cycle/2)
    private static final float[] HUB_UPGRADE_COSTS = {5_000f, 15_000f, 50_000f};

    // Ember IV: Volcanic Spring-Pad placement mode + per-pad purchase costs
    private static final int     PLACE_SPRING_PAD   = 4;
    private static final int     MAX_SPRING_PADS    = 8;
    private static final float[] SPRING_PAD_COSTS   = {300f, 600f, 1_200f, 2_400f, 4_800f, 9_600f, 19_200f, 38_400f};
    private static final float   SPRING_PAD_RESTITUTION = 2.25f;  // high elasticity = violent launch

    // Ember IV: Kinetic Blade physics
    private static final float BLADE_LENGTH = 1.5f;
    private static final float BLADE_WIDTH  = 0.15f;

    // Ember IV: ring-speed CPs (Heavy Chassis / Magnetic Rim / Hub Resonance)
    private static final float[] EMBER_MILESTONE_RPMS = {5.5f, 6.5f, 7.5f};

    // Sector perk unlock thresholds
    private static final int NUM_SECTORS = 4;

    private static final int PLACE_NONE    = 0;
    private static final int PLACE_BUMPER  = 1;
    private static final int PLACE_GRAVITY = 2;
    private static final int PLACE_BLADE   = 3;

    // Ember IV: Resonance Relay — CP I unlock
    private static final int   PLACE_RELAY          = 6;
    private static final int   MAX_RELAY_NODES      = 4;
    private static final float RELAY_NODE_RADIUS    = 0.10f;
    private static final float RELAY_COST           = 15_000f; // base; relayCost() returns dynamic price
    private static final float RELAY_CROSS_REWARD   = 50f;
    private static final float RELAY_CROSS_COOLDOWN = 0.6f;

    // Ember IV: Phase Portal — CP I unlock
    private static final int   PLACE_PORTAL         = 7;
    private static final int   MAX_PORTAL_PAIRS      = 3;
    private static final float PORTAL_RADIUS         = 0.22f;  // trigger radius (world units)
    private static final float PORTAL_COST           = 3_000f; // base; portalCost() returns dynamic price
    private static final float PORTAL_EXIT_MULT      = 1.2f;   // speed multiplier on exit
    private static final float PORTAL_COOLDOWN       = 0.2f;   // per-orb re-trigger cooldown (s)
    private static final float PORTAL_ENTER_REWARD   = 100f;   // SP reward per teleport

    // Sprite draw sizes in pixels
    private static final float INTERN_W       = 80f;   // particle glow draw size on screen
    private static final float INTERN_H       = 80f;
    private static final float BUMPER_W       = 48f;
    private static final float BUMPER_H       = 48f;
    private static final float RING_TEX_SIZE  = CENTRIFUGE_R * PPM * 2f;  // matches drum diameter

    // Centrifuge center in pixel space
    private static final float CCX_PX = CENTRIFUGE_CX * PPM;   // 240
    private static final float CCY_PX = CENTRIFUGE_CY * PPM;   // 630

    // ---- Fields -----------------------------------------------------------------

    private final OdysseyGame game;

    // Box2D
    private World              world;
    private Body               centrifugeBody;
    private OrthographicCamera physCam;
    private ExtendViewport     physViewport;

    // Rendering
    private SpriteBatch        batch;
    private ShapeRenderer      shapeR;
    private OrthographicCamera renderCam;
    private ExtendViewport     renderViewport;
    private Texture            texBackground;
    private Texture            texParticle;       // glowing energy orb
    private Texture            texParticleCore;   // bright inner core
    private Texture            texBumper;
    private Texture            texRing;
    private Texture            texGravField;
    private Texture            texGravCenter;     // singularity core icon for gravity wells
    private Texture            texPortalIcon;     // EmberIV portal button icon
    private Texture            texRailIcon;       // EmberIV rail button icon
    private Texture            texEmberIntern;    // EmberIV hire button icon
    private Texture            texPixel;          // 1×1 white pixel
    private Texture            texCryoVent;       // Level 2: snowflake crystal icon
    private Texture            texTeslaCoil;      // Level 2: electromagnetic coil-rings icon

    // Scene2D
    private Stage      ui;
    private Label      joulesLabel;
    private Label      crystalsLabel;
    private Label      jpsLabel;
    private Label      outputLabel;
    private Label      uptimeLabel;

    // 3-column top dashboard labels
    private Label topPlanetLabel;
    private Label topCheckpointLabel;
    private Label topGravLabel;
    private Label topYieldsLabel;
    private Label topSpRateHeaderLabel;
    private Label topSpValueLabel;
    private Label planetTimerLabel;
    private Label topPerksHeaderLabel;
    private Label topPerksListLabel;

    // ── HUD strip ──
    private Label hudPlanetLabel;
    private Label hudEnergyLabel;
    private Label hudRateLabel;
    private Label hudSpLabel;
    private float hudBarFill = 0f;   // 0..1, current fill fraction

    // ── Perk readout strip — icon Images (one per milestone slot) ──
    private com.badlogic.gdx.scenes.scene2d.ui.Image perkIconSpeed, perkIconElas, perkIconWall, perkIconColl, perkIconBump;
    private Texture texPerkSpeed, texPerkElas, texPerkWall, texPerkColl, texPerkBump;
    private Texture texEmberPerk1, texEmberPerk2, texEmberPerk3, texEmberPerk4, texEmberPerk5;
    private Texture texFrostPerk1, texFrostPerk2, texFrostPerk3, texFrostPerk4, texFrostPerk5;
    private Texture texIconSP, texIconEnergy;
    private Texture texHandDrag;
    private com.badlogic.gdx.scenes.scene2d.ui.Image uiIconSP, uiIconEnergy;

    // Perk tap popup state
    private int   activePerkPopup      = -1;   // milestone index 0-4, or -1 for none
    private int   activeFrostPerkPopup = -1;   // Frostheim perk slot 0-4, or -1 for none
    private float perkPopupTimer  = 0f;
    private static final float PERK_POPUP_DURATION = 2.8f;

    // Space Points feed: last 5 gains shown at top of centrifuge  {age, value, colorType}
    private final Array<float[]> spacePointsQueue = new Array<>();
    private static final float SP_LIFETIME = 4.0f;
    private static final int   SP_MAX      = 5;
    private com.badlogic.gdx.graphics.g2d.BitmapFont floatFont;
    private com.badlogic.gdx.graphics.g2d.GlyphLayout floatLayout;

    // ── Floating number entries (colored by source) ──
    private static final class FloatEntry {
        float wx, wy;   // world-space spawn position
        float value;
        int   colorType; // 0=energy, 1=SP, 2=attractor, 3=bumper
        float age;       // seconds since spawn
        float driftX;    // horizontal arc drift (world units/sec)
        static final float LIFETIME = 1.2f;
    }
    private final com.badlogic.gdx.utils.Array<FloatEntry> activeFloats = new com.badlogic.gdx.utils.Array<>();
    private boolean floatSideTog = false;

    private TextButton btnAdd;
    private TextButton btnBumper;
    private TextButton btnGravityWell;
    private TextButton.TextButtonStyle tileStyleLock, tileStyleNorm, tileStyleBuy, tileStyleBuyGreen, tileStyleAct, tileStyleGo, tileStyleGoLock;
    private TextButton[] perkButtons;
    private Label[]      perkDescLabels;
    private Table        rightPerksTable;

    // Bookkeeping
    private final Array<Body>            balls           = new Array<>();
    private final ObjectMap<Body, Long>  ballLastHitMs   = new ObjectMap<>();
    private final Array<Body> bumpers      = new Array<>();   // Level 1: Solara standard bumpers
    private final Array<Body> attractors   = new Array<>();   // Level 1: Solara / Ember IV gravity wells
    private final Array<Body> icicleNodes     = new Array<>();   // Level 3: Frostheim Icicle Nodes (orb-splitters)
    private final Array<Float> icicleAngOffsets = new Array<>(); // angle offsets for icicle co-rotation
    private final Array<Float> icicleRadii      = new Array<>(); // radii for icicle co-rotation
    private final Array<Body> snowPellets    = new Array<>();   // snow pellets from intern splits
    private final Array<PelletGroup> pelletGroups = new Array<>(); // pending pellet merge groups
    private final Array<Body> teslaCoils   = new Array<>();   // Level 3: Frostheim Spiral Slingshots
    private final Array<SpiralCapture> spiralCaptures = new Array<>();
    private final Array<Body> armBumpers   = new Array<>();   // Frostheim 3rd-intern perk: 6 arm-tip repulsors
    private final Array<Body>  kineticBlades     = new Array<>();  // Level 2: Ember IV Kinetic Radius Blades
    private final Array<Float> bladeInitAngles   = new Array<>();  // initial placement angle for each blade (orbit tracking)
    private final Vector2     pullVec    = new Vector2();
    private final Vector3     touchWorld = new Vector3();
    private int   placementMode    = PLACE_NONE;
    private static final int PLACE_INTERN = 5;
    private int   dragMode         = PLACE_NONE;
    private float dragStageX, dragStageY;
    private float dragOriginStageX = 0f;
    private float dragOriginStageY = 0f;
    private final com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body>  curlingBodies  = new com.badlogic.gdx.utils.Array<>();
    private final com.badlogic.gdx.utils.Array<Float> curlingTimers  = new com.badlogic.gdx.utils.Array<>();
    private static final float CURLING_SETTLE_TIME  = 2.0f;
    private static final float CURLING_SETTLE_SPEED = 0.3f;
    // Visual flight phase — bumper travels from button to drum before physics takes over
    private boolean flyingBumperActive = false;
    private float   flyingBumperWX, flyingBumperWY;
    private float   flyingBumperVX, flyingBumperVY;
    // Visual flight phase — intern orb slingshot
    private boolean flyingInternActive = false;
    private float   flyingInternWX, flyingInternWY;
    private float   flyingInternVX, flyingInternVY;
    // Visual flight phase — gravity well slingshot
    private boolean flyingAttractorActive = false;
    private float   flyingAttractorWX, flyingAttractorWY;
    private float   flyingAttractorVX, flyingAttractorVY;
    private InputMultiplexer inputMux;
    private float jpsTimer         = 0f;
    private float lastJoules       = 0f;
    private float uptime           = 0f;

    // Upgradeable physics parameters (mutated by upgrade purchases)
    private float gravityPull   = 20f;
    private float gravityFieldR = 1.2f;  // wide influence radius
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
    private TextButton btnGravShift;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> gravShiftCell;
    private TextButton btnGravCenter;
    private TextButton.TextButtonStyle gravOffStyle, gravPullStyle, gravPushStyle;
    private Label      ringSpeedLabel;
    private Label      configLabel;
    private Label      milestoneStatusLabel;
    private TextButton btnJumpReady;
    private Table      pauseTable;

    // Monetisation UI
    private Table      livesBlockTable;
    private Table      shopTable;
    private Label      livesLabel;
    private Label      diamondsLabel;
    private Label      lifeTimerLabel;
    private Label      greyHeartsLabel;   // invisible spacer; used as position anchor for ShapeRenderer hearts
    private Label      livesBlockGemsLabel;

    private final boolean[] milestoneAchieved = new boolean[6];
    private float currentBallRestitution = BALL_RESTITUTION;
    private float animTime = 0f;  // drives intern wobble animation
    private boolean tutorialDone = false;
    private boolean tutorialInternDragging = false;
    private float   tutorialPostDropTimer  = -1f; // counts up after first intern placed; -1 = not started
    private boolean launchHintShown      = false;
    private boolean bumperHintShown      = false;
    private boolean gravityHintShown     = false;
    private boolean perkIconHintShown    = false;
    private boolean launchWasReady    = false;
    private Cell<TextButton> launchBtnCell;
    private Table rootTable;
    // 0=intro overlay, 1=hire-intern callout, 2=launch callout, 3=done
    private int   tutorialStep    = 0;
    private float tutorialStepAge = 0f;   // time spent on current callout step
    private float tutorialEnergyBaseline = 0f; // powerGenerated snapshot when step 1 begins
    private int   lastPlanetIndex = -1;           // tracks planet changes for texture regen

    // Speed tooltip (small popup above r/s label, auto-hides after 5s)
    private static final float INTERN_ADDED_HOLD = 5.0f;
    private float internAddedTimer    = 0f;
    private float internAddedOldSpeed = 0f;
    private float internAddedNewSpeed = 0f;
    private float lastDisplayedSpeed  = -1f; // throttle ringSpeedLabel setText
    private float hireIdleTimer       = 0f;  // counts up when ≤2 interns & no recent hire

    // Generic notification overlay — tap to dismiss
    private static final float NOTIF_HOLD  = 3.2f; // kept for legacy; now controls fade-in only
    private static final float NOTIF_FADEIN = 0.18f;
    private float  notifTimer  = 0f;   // counts up from 0, stays active until tap
    private boolean notifActive = false;
    private String notifTitle  = "";
    private String notifBody   = "";

    // Milestone celebration overlay — slides up, tap to dismiss
    private static final float CELEB_HOLD  = 3.5f;
    private static final float CELEB_SLIDE = 0.30f;
    private float  celebTimer  = 0f;   // counts up for slide-in animation
    private boolean celebActive = false;
    private float  celebSlideY = 0f;
    private String celebTitle  = "";
    private String celebBody   = "";

    // Free-intern-at-max state
    private boolean freeInternGiven = false;
    private float   centrifugeRpmMax = CENTRIFUGE_RPM_MAX; // bumped to 10.0 when free intern fires

    // Frostheim checkpoint perk flags
    private boolean frostheimCpI   = false;
    private boolean frostheimCpII  = false;
    private boolean frostheimCpIII = false;

    // Frostheim purchasable start unlocks (before any checkpoint)
    private boolean frostheimIcicleUnlocked      = false;  // 400FS — unlocks Icicle Node slot
    private boolean frostheimThirdInternUnlocked = false;  // 800FS — spawns 3rd intern
    private boolean frostheimArmBumpersActive        = false;  // perk A: 6 powerful repulsors on arm tips
    private boolean frostheimMergeBurstUnlocked       = false;  // perk B: +500 J on pellet merge-back
    private boolean frostheimValleyBladesUnlocked     = false;  // perk C: deflectors in valley dips
    private boolean frostheimExtendedPelletUnlocked   = false;  // perk D: pellet merge timer 5s->7s
    private boolean frostheimDoubleCapture            = false;  // perk E: tesla captures 2 orbs
    private float   pelletMergeTime                   = 5f;     // seconds before pellets merge back
    private final Array<Body> valleyBlades            = new Array<>();

    // Frostheim live physics parameters — mutated by Frost CP perks
    private float frostheimBallDamping  = 0.01f;   // CP I drops this to 0.005f
    private float teslaHarvestRate      = 15f;      // CP III raises this to 35f

    // CP III decision: 0=not chosen, 1=all cryo→tesla, 2=all tesla→cryo, 3=+2 interns
    private int   frostheimDecision     = 0;
    private Table decisionTable;

    // ---- Ember IV (Level 2) state ------------------------------------------------
    private boolean emberCpI   = false;
    private boolean emberCpII  = false;
    private boolean emberCpIII = false;
    private boolean emberPerk1 = false;
    private boolean emberPerk2 = false;
    private boolean emberPerk3 = false;
    private boolean emberPerk4 = false;
    private boolean emberPerk5 = false;
    private boolean portalBidirectional = false;
    private boolean emberSpinReversed   = false;
    private int     gravShiftStep       = 0;
    private boolean emberThirdInternUnlocked = false;  // 1200◆ — unlock + spawn 3rd intern


    // Gravity toggle: pull interns toward center or push them away
    private boolean emberGravityEnabled = false;
    private boolean emberGravityPush    = false; // false=pull, true=push
    private static final float EMBER_GRAVITY_UNLOCK_COST = 2_000f;
    private static final float EMBER_GRAVITY_TOGGLE_COST = 800f;
    private static final float EMBER_GRAVITY_FORCE       = 18f;

    // Cybernetic Axle Hub dual-state clock
    private float   hubStateTimer  = 0f;
    private float   hubCycleLength = 20f;   // CP III shortens to 10f
    private boolean hubBlastFired  = false; // fires once on each STATE B entry
    // Hub upgrade tier: 0=base, 1=suction×1.35, 2=blast×1.50, 3=cycle halved to 10s

    // Frostheim hub gravity cycle (separate from EmberIV hub)
    private float   frostheimHubTimer      = 0f;
    private boolean frostheimHubBlastFired = false;
    private int     hubUpgradeTier = 0;

    // Ember IV: Volcanic Spring-Pads — static rim fixtures that catapult orbs on contact
    private final Array<Body> springPads = new Array<>();
    private boolean emberHeavyChassis  = false; // CP I: density 3.5

    // Resonance Relay
    private final com.badlogic.gdx.utils.Array<com.badlogic.gdx.math.Vector2> relayNodes = new com.badlogic.gdx.utils.Array<>();
    private float[] relayCooldowns  = new float[0];
    private float[] relaySegGlow    = new float[MAX_RELAY_NODES];

    // Phase Portal — list of pairs; each pair = Vector2[2] {localA, localB} in rect local frame
    private final com.badlogic.gdx.utils.Array<com.badlogic.gdx.math.Vector2[]> portalPairs =
        new com.badlogic.gdx.utils.Array<>();
    // portalOrbCooldowns flat: [ballIdx * MAX_PORTAL_PAIRS + pairIdx]
    private float[] portalOrbCooldowns = new float[0];
    // portalGlow: [pairIdx*2+0]=A glow, [pairIdx*2+1]=B glow
    private float[] portalGlow         = new float[MAX_PORTAL_PAIRS * 2];

    private boolean emberMagneticRim   = false; // CP II: rolling wall contact
    // ---- Ember IV textures -------------------------------------------------------
    private Texture texBlade1;   // Variation A — Heavy Carbon-Steel
    private Texture texBlade2;   // Variation B — Sleek Titanium
    private Texture texBlade3;   // Variation C — Reinforced Diamond-Cleaver
    private Texture texRocket;
    private Texture texLock;

    // Perk lock images (one per slot)
    private com.badlogic.gdx.scenes.scene2d.ui.Image lockSpeedImg;
    private com.badlogic.gdx.scenes.scene2d.ui.Image lockCollImg;
    private com.badlogic.gdx.scenes.scene2d.ui.Image lockWallImg;
    private com.badlogic.gdx.scenes.scene2d.ui.Image lockBoostImg;
    private com.badlogic.gdx.scenes.scene2d.ui.Image lockBumpImg;

    // Perks panel rebuild tracking
    private Label perksHeader;
    private int   lastUnlockedPerkCount = -1;

    // Space-points-per-second tracking
    private float lastCrystals = 0f;
    private float sparkRate    = 0f;

    // 1-Second Heartbeat Pulse system — rolling 5-entry feed above the centrifuge
    private float  pulseTimer                  = 0f;
    private float  accumulatedSparksThisSecond = 0f;
    private float  prevCrystalsPulse           = 0f;
    // Frostheim stall detection — auto-pelletize if no SP for 5 s
    private float  spStallTimer                = 0f;
    private float  lastCrystalsStall           = 0f;
    private float  totalCrystalsEarned         = 0f;  // monotonic earn counter, never decremented
    // pulseHistory entries: float[]{value, age}  (newest = index 0)
    private final Array<float[]> pulseHistory  = new Array<>();
    private static final int   PULSE_HISTORY_MAX = 5;
    private static final float PULSE_DURATION    = 5.0f;  // each entry fades over 5 s

    // Screen shake (parameterized)
    private float shakeTimer    = 0f;
    private float shakeDuration = 0.05f;
    private float shakeMag      = 4f;

    // Planet architecture — current planet definition and state
    private PlanetDefinition currentDef   = ShipData.PLANET_DEFS[0];
    private PlanetState      currentState = ShipData.get().getState(0);

    // ---- Construction -----------------------------------------------------------

    public EngineeringLabScreen(OdysseyGame game) {
        this.game = game;
        buildTextures();
        buildRendering();
        buildPhysics();
        buildUI();
    }

    // ---- Texture loading --------------------------------------------------------

    private void buildTextures() {
        texBackground   = genBackground();
        texParticle     = genGlowTexture(64);
        texParticleCore = genGlowTexture(24);
        texBumper       = new Texture(Gdx.files.internal("ui/bumper.png"));
        texRing         = genRingTexture((int) RING_TEX_SIZE);
        texGravField    = genGravFieldTexture(128);
        texGravCenter   = genGravCenterTexture(48);
        texPortalIcon   = genPortalIconTexture(48);
        texRailIcon     = genRailIconTexture(48);
        texEmberIntern  = genEmberInternTexture(64);
        texCryoVent     = genCryoVentTexture(64);
        texTeslaCoil    = genTeslaCoilTexture(64);
        texBlade1       = genBladeTextureA(96);
        texBlade2       = genBladeTextureB(96);
        texBlade3       = genBladeTextureC(96);
        texRocket       = genRocketTexture(56);
        texHandDrag     = new Texture(Gdx.files.internal("ui/hand_drag.png"));
        texLock         = genLockTexture(28);
        texPerkSpeed    = genPerkIconSpeed(40);
        texPerkElas     = genPerkIconElas(40);
        texPerkWall     = genPerkIconWall(40);
        texPerkColl     = genPerkIconColl(40);
        texPerkBump     = genPerkIconBump(40);
        if (isEmberIV()) {
            texEmberPerk1 = genEmberPerkIconSpeedKeep(40);
            texEmberPerk2 = genEmberPerkIconWallEnergy(40);
            texEmberPerk3 = genEmberPerkIconGravShift(40);
            texEmberPerk4 = genEmberPerkIconPortalSync(40);
            texEmberPerk5 = genEmberPerkIconReverse(40);
        }
        if (isFrostheim()) {
            texFrostPerk1 = genFrostPerkIconArmBumpers(40);
            texFrostPerk2 = genFrostPerkIconValleyBlades(40);
            texFrostPerk3 = genFrostPerkIconMergeBurst(40);
            texFrostPerk4 = genFrostPerkIconCryoExtension(40);
            texFrostPerk5 = genFrostPerkIconDoubleVortex(40);
        }
        texIconSP       = genIconSpaceCoin(24);
        texIconEnergy   = genIconBolt(24);

        // 1×1 white pixel — used for all colored stick-limb rect drawing
        Pixmap pm1 = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm1.setColor(Color.WHITE);
        pm1.fill();
        texPixel = new Texture(pm1);
        pm1.dispose();
    }

    private Texture[] loadInternSet(String prefix) {
        Texture[] textures = new Texture[3];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = new Texture(prefix + i + ".png");
        }
        return textures;
    }

    private Texture[] genAstronautSet(float hr, float hg, float hb) {
        return new Texture[]{
            genAstronautFrame(80, hr, hg, hb, 1.00f, 1.00f),
            genAstronautFrame(80, hr, hg, hb, 1.22f, 0.82f),
            genAstronautFrame(80, hr, hg, hb, 0.82f, 1.22f),
        };
    }

    // ---- Procedural texture generators ------------------------------------------

    private Texture genBackground() {
        int W = (int) RENDER_W, H = (int) RENDER_H;
        Pixmap pm = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        int pidx = ShipData.get().currentPlanetIndex;
        float[] gp = currentDef.bgGradient();
        float[] gc = currentDef.chamberGlow();
        float[] st = currentDef.starTint();

        // Background gradient (bottom of Pixmap = top of screen after LibGDX flip)
        for (int y = 0; y < H; y++) {
            float t = (float) y / H;
            pm.setColor(gp[0] + t * gp[1], gp[2] + t * gp[3], gp[4] + t * gp[5], 1f);
            for (int x = 0; x < W; x++) pm.drawPixel(x, y);
        }

        // Stars
        for (int i = 0; i < 110; i++) {
            int sx = (int) (MathUtils.random() * W);
            int sy = (int) (MathUtils.random() * H);
            float b = 0.35f + MathUtils.random() * 0.65f;
            pm.setColor(Math.min(b * st[0], 1f), Math.min(b * st[1], 1f), Math.min(b * st[2], 1f), 1f);
            pm.drawPixel(sx, sy);
            if (MathUtils.random() > 0.68f) { pm.drawPixel(sx + 1, sy); pm.drawPixel(sx, sy + 1); }
        }

        // Chamber — in Pixmap coords: chamberCY_pm = H - CCY_PX (LibGDX renders y-up, Pixmap is y-down)
        float cCX = CCX_PX, cCY = H - CCY_PX;
        float cR   = CENTRIFUGE_R * PPM;
        float sqHS = RECT_HW * PPM; // square half-size in pixels (same for both axes)
        boolean isSquare = isEmberIV();

        // Dark inner area
        float scanR = isSquare ? sqHS + 2 : cR + 2;
        for (int y = (int)(cCY - scanR); y <= (int)(cCY + scanR); y++) {
            for (int x = (int)(cCX - scanR); x <= (int)(cCX + scanR); x++) {
                if (x < 0 || x >= W || y < 0 || y >= H) continue;
                float dx = x - cCX, dy = y - cCY;
                float dist = isSquare ? Math.max(Math.abs(dx), Math.abs(dy))
                                      : (float) Math.sqrt(dx * dx + dy * dy);
                float inner = isSquare ? sqHS - 4 : cR - 4;
                if (dist <= inner) {
                    float t = (float) y / H;
                    pm.setColor((gp[0] + t * gp[1]) * 0.55f,
                                (gp[2] + t * gp[3]) * 0.55f,
                                (gp[4] + t * gp[5]) * 0.55f, 1f);
                    pm.drawPixel(x, y);
                }
            }
        }

        // Planet-themed glow ring — skip for square (rotating drawRectRing handles it)
        if (!isSquare) {
            for (int y = (int)(cCY - cR - 20); y <= (int)(cCY + cR + 20); y++) {
                for (int x = (int)(cCX - cR - 20); x <= (int)(cCX + cR + 20); x++) {
                    if (x < 0 || x >= W || y < 0 || y >= H) continue;
                    float dx = x - cCX, dy = y - cCY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float ring = Math.abs(dist - cR);
                    if (ring < 14) {
                        float glow = (1f - ring / 14f) * 0.55f;
                        float t = (float) y / H;
                        float br = gp[0] + t * gp[1], bg2 = gp[2] + t * gp[3], bb = gp[4] + t * gp[5];
                        pm.setColor(Math.min(br + glow * gc[0], 1f),
                                    Math.min(bg2 + glow * gc[1], 1f),
                                    Math.min(bb + glow * gc[2], 1f), 1f);
                        pm.drawPixel(x, y);
                    }
                }
            }
        }

        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private Texture genRingTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f;
        float outerR = size * 0.46f;
        float innerR = size * 0.31f;

        float[] rc = currentDef.ringInnerColor();
        float[] mc = currentDef.ringMidColor();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < innerR || dist > outerR) { pm.setColor(0f, 0f, 0f, 0f); pm.drawPixel(x, y); continue; }
                float t = (dist - innerR) / (outerR - innerR);   // 0..1 across ring
                float peak = 1f - Math.abs(t - 0.5f) * 2f;       // 1 at centerline, 0 at edges
                float r = rc[0] + peak * rc[1];
                float g = rc[2] + peak * rc[3];
                float b = rc[4] + peak * rc[5];
                float a = 0.55f + peak * 0.45f;
                pm.setColor(r, g, b, a);
                pm.drawPixel(x, y);
            }
        }

        // 12 evenly-spaced segment marks
        for (int seg = 0; seg < 12; seg++) {
            float angle = (float) (seg * Math.PI * 2 / 12);
            float cosA = MathUtils.cos(angle), sinA = MathUtils.sin(angle);
            for (float r = innerR + 3; r <= outerR - 3; r += 1f) {
                int sx = (int) (cx + cosA * r), sy = (int) (cy + sinA * r);
                if (sx >= 0 && sx < size && sy >= 0 && sy < size) {
                    pm.setColor(mc[0], mc[1], mc[2], 0.7f);
                    pm.drawPixel(sx, sy);
                    pm.drawPixel(sx + 1, sy);
                }
            }
        }

        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    // ---- Level 2 texture generators ------------------------------------------------

    // Safe bounds-checked pixel write used by both Level 2 generators
    private static void sp(Pixmap pm, float fx, float fy, float r, float g, float b, float a) {
        int x = Math.round(fx), y = Math.round(fy);
        if (x >= 0 && x < pm.getWidth() && y >= 0 && y < pm.getHeight()) {
            pm.setColor(r, g, b, a);
            pm.drawPixel(x, y);
        }
    }

    // Cryo-Vent: 6-arm snowflake crystal with perpendicular branches
    private Texture genCryoVentTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f;
        float armLen = size * 0.44f;

        for (int a = 0; a < 6; a++) {
            float ang = a * MathUtils.PI / 3f;
            float ac  = MathUtils.cos(ang), as = MathUtils.sin(ang);

            // Main arm — 2-pixel-wide line radiating from centre
            for (float t = 4f; t <= armLen; t += 0.7f) {
                float fade  = 1f - (t - 4f) / (armLen - 4f);
                float alpha = 0.55f + fade * 0.45f;
                sp(pm, cx + ac * t,        cy + as * t,        0.72f, 0.94f, 1f, alpha);
                sp(pm, cx + ac * t - as,   cy + as * t + ac,   0.50f, 0.78f, 1f, alpha * 0.50f);
                sp(pm, cx + ac * t + as,   cy + as * t - ac,   0.50f, 0.78f, 1f, alpha * 0.50f);
            }

            // First branch pair at 40% of arm length
            float b1x = cx + ac * armLen * 0.40f, b1y = cy + as * armLen * 0.40f;
            float bl1 = armLen * 0.28f;
            for (float t = 1f; t <= bl1; t += 0.7f) {
                float alpha = (1f - t / bl1) * 0.85f;
                sp(pm, b1x + (-as) * t,  b1y + ac * t,  0.65f, 0.88f, 1f, alpha);
                sp(pm, b1x - (-as) * t,  b1y - ac * t,  0.65f, 0.88f, 1f, alpha);
            }

            // Second branch pair at 70% of arm length (shorter)
            float b2x = cx + ac * armLen * 0.70f, b2y = cy + as * armLen * 0.70f;
            float bl2 = armLen * 0.18f;
            for (float t = 1f; t <= bl2; t += 0.7f) {
                float alpha = (1f - t / bl2) * 0.70f;
                sp(pm, b2x + (-as) * t,  b2y + ac * t,  0.70f, 0.90f, 1f, alpha);
                sp(pm, b2x - (-as) * t,  b2y - ac * t,  0.70f, 0.90f, 1f, alpha);
            }
        }

        // Bright icy-white centre core
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                float r2 = dx * dx + dy * dy;
                if (r2 <= 16f) {
                    float br = 1f - r2 / 16f;
                    pm.setColor(0.75f + br * 0.25f, 0.93f + br * 0.07f, 1f, 0.65f + br * 0.35f);
                    pm.drawPixel((int) cx + dx, (int) cy + dy);
                }
            }
        }

        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    // Tesla Coil: 3 concentric partial rings (coil cross-section) + 4 radial spokes + yellow core
    private Texture genTeslaCoilTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f;

        float[] radii    = { size * 0.42f, size * 0.28f, size * 0.15f };
        float[] rCol     = { 0.12f, 0.22f, 0.55f };
        float[] gCol     = { 0.58f, 0.80f, 0.95f };
        float   gapRad   = 0.32f;  // angular gap in each ring (radians)

        // Each ring has its gap rotated 120° from the previous to imply a helix winding
        for (int ring = 0; ring < 3; ring++) {
            float r          = radii[ring];
            float alpha      = 0.62f + ring * 0.14f;
            float gapCentre  = -MathUtils.PI * 0.5f + ring * (MathUtils.PI2 / 3f);

            for (float ang = 0f; ang < MathUtils.PI2; ang += 0.018f) {
                // Skip the winding gap
                float diff = Math.abs(ang - gapCentre);
                if (diff > MathUtils.PI) diff = MathUtils.PI2 - diff;
                if (diff < gapRad) continue;

                float pulse = 0.8f + 0.2f * MathUtils.sin(ang * 4f);
                float a     = alpha * pulse;
                for (float dr = -1.4f; dr <= 1.4f; dr += 0.7f) {
                    sp(pm,
                        cx + MathUtils.cos(ang) * (r + dr),
                        cy + MathUtils.sin(ang) * (r + dr),
                        rCol[ring], gCol[ring], 1f, a);
                }
            }
        }

        // 4 radial spokes connecting inner ring to middle ring at 90° intervals
        for (int s = 0; s < 4; s++) {
            float ang = s * MathUtils.PI * 0.5f;
            float cosA = MathUtils.cos(ang), sinA = MathUtils.sin(ang);
            float r0 = radii[2] + 1f, r1 = radii[1] - 1f;
            for (float r = r0; r <= r1; r += 0.5f) {
                float bright = (r - r0) / (r1 - r0);
                sp(pm, cx + cosA * r, cy + sinA * r, 0.45f, 0.88f + bright * 0.12f, 1f, 0.80f);
            }
        }

        // Bright electric-yellow centre core
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                float r2 = dx * dx + dy * dy;
                if (r2 <= 16f) {
                    float br = 1f - r2 / 16f;
                    pm.setColor(1f, 0.92f + br * 0.08f, 0.25f + br * 0.75f, 0.70f + br * 0.30f);
                    pm.drawPixel((int) cx + dx, (int) cy + dy);
                }
            }
        }

        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    // Jelly astronaut — big helmeted head + small body + visible arms & legs
    // Pixmap y=0 is top of pm → bottom of rendered sprite (LibGDX flips y).
    // Large pm-y → top of rendered sprite.
    private Texture genAstronautFrame(int size, float hr, float hg, float hb, float sx, float sy) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f;

        // Head: big — 26% of sprite, centered at 72% height in pm (top of rendered sprite)
        float headCY = size * 0.72f;
        float headRx = size * 0.26f * sx;
        float headRy = size * 0.26f * sy;

        // Body: small round blob below head on screen (= smaller pm-y)
        float bodyCY = headCY - headRy * 1.55f;
        float bodyRx = size * 0.09f * sx;
        float bodyRy = size * 0.11f * sy;

        float limbR = hr * 0.72f, limbG = hg * 0.72f, limbB = hb * 0.72f;

        // --- Legs (behind body so body hides roots) ---
        float legTopY = bodyCY - bodyRy + 1;
        float legBotY = legTopY - size * 0.24f;
        drawThickLine(pm, (int)(cx - bodyRx * 0.55f), (int)legTopY,
                          (int)(cx - bodyRx * 1.5f),  (int)legBotY,
                          limbR, limbG, limbB, 2);
        drawThickLine(pm, (int)(cx + bodyRx * 0.55f), (int)legTopY,
                          (int)(cx + bodyRx * 1.5f),  (int)legBotY,
                          limbR, limbG, limbB, 2);

        // --- Arms (behind body so body hides roots) ---
        float armY    = bodyCY + bodyRy * 0.6f;
        float armTipY = armY - size * 0.16f;
        float armLen  = size * 0.22f;
        drawThickLine(pm, (int)(cx - bodyRx),       (int)armY,
                          (int)(cx - bodyRx - armLen), (int)armTipY,
                          limbR, limbG, limbB, 2);
        drawThickLine(pm, (int)(cx + bodyRx),       (int)armY,
                          (int)(cx + bodyRx + armLen), (int)armTipY,
                          limbR, limbG, limbB, 2);

        // --- Body blob ---
        for (int py = (int)(bodyCY - bodyRy); py <= (int)(bodyCY + bodyRy); py++) {
            for (int px = (int)(cx - bodyRx); px <= (int)(cx + bodyRx); px++) {
                float dx = (px - cx) / bodyRx, dy = (py - bodyCY) / bodyRy;
                float d = (float) Math.sqrt(dx*dx + dy*dy);
                if (d > 1f) continue;
                float bright = 0.55f + (1f - d) * 0.40f;
                pm.setColor(hr * bright, hg * bright, hb * bright, 1f);
                pm.drawPixel(px, py);
            }
        }

        // --- Head: jelly ball with specular highlight ---
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float dx = (px - cx) / headRx, dy = (py - headCY) / headRy;
                float d = (float) Math.sqrt(dx*dx + dy*dy);
                if (d > 1f) continue;
                float bright = 1f - d * 0.50f;
                float hlDx = (px - (cx - headRx * 0.28f)) / headRx;
                float hlDy = (py - (headCY - headRy * 0.28f)) / headRy;
                float hl = Math.max(0f, 1f - (float) Math.sqrt(hlDx*hlDx + hlDy*hlDy) / 0.42f);
                float r = Math.min(hr * bright + hl * 0.35f, 1f);
                float g = Math.min(hg * bright + hl * 0.35f, 1f);
                float b = Math.min(hb * bright + hl * 0.35f, 1f);
                float a = d < 0.88f ? 1f : 1f - (d - 0.88f) / 0.12f;
                pm.setColor(r, g, b, a);
                pm.drawPixel(px, py);
            }
        }

        // --- Visor: dark oval inside helmet ---
        float visorCY = headCY + headRy * 0.05f;
        float visorRx = headRx * 0.58f, visorRy = headRy * 0.40f;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float dx = (px - cx) / visorRx, dy = (py - visorCY) / visorRy;
                float d = (float) Math.sqrt(dx*dx + dy*dy);
                if (d > 1f) continue;
                pm.setColor(0.03f + d*0.06f, 0.06f + d*0.10f, 0.18f + d*0.14f, 0.95f);
                pm.drawPixel(px, py);
            }
        }
        // Visor glint
        pm.setColor(0.60f, 0.92f, 1f, 0.75f);
        int gx = (int)(cx - headRx * 0.22f), gy = (int)(visorCY - visorRy * 0.38f);
        pm.drawPixel(gx, gy); pm.drawPixel(gx+1, gy); pm.drawPixel(gx, gy+1);

        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private void fillCirclePm(Pixmap pm, int cx, int cy, int r, float red, float g, float b) {
        for (int py = cy - r; py <= cy + r; py++)
            for (int px = cx - r; px <= cx + r; px++) {
                float dx = px - cx, dy = py - cy;
                if (dx*dx + dy*dy <= r*r) { pm.setColor(red, g, b, 1f); pm.drawPixel(px, py); }
            }
    }

    private Texture genLimbTexture() {
        int w = 6, h = 22;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            float t = (float) y / h;
            float bright = 0.68f + t * 0.22f;
            float alpha  = 0.92f - t * 0.25f;
            pm.setColor(bright, bright, bright * 1.08f, alpha);
            for (int x = 0; x < w; x++) pm.drawPixel(x, y);
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private Texture genBumperTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f, r = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                if (d >= 1f) continue;
                // Bright center core + outer ring outline at ~65% radius
                float core = Math.max(0f, 1f - d * 2.2f);
                float ring = Math.max(0f, 1f - Math.abs(d - 0.65f) * 9f);
                float intensity = Math.min(core * 0.95f + ring * 0.80f, 1f);
                float alpha     = Math.min(core + ring * 0.90f + (1f - d) * 0.18f, 0.95f);
                // Hot magenta: strong R, almost no G, medium B
                pm.setColor(Math.min(intensity + 0.22f, 1f), intensity * 0.10f,
                            Math.min(intensity * 0.75f + 0.12f, 1f), alpha);
                pm.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private Texture genGravFieldTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f, r = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                if (d >= 1f) continue;
                float body   = (1f - d) * (1f - d) * 0.22f;
                float border = Math.max(0f, 1f - Math.abs(d - 0.88f) * 14f) * 0.55f;
                float alpha  = Math.min(body + border, 0.65f);
                pm.setColor(0.35f, 0.18f, 0.95f, alpha);
                pm.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private Texture genGravCenterTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f, r = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                if (d >= 1f) continue;
                float ring  = Math.max(0f, 1f - Math.abs(d - 0.28f) * 18f);   // event horizon band
                float core  = Math.max(0f, 1f - d * 3.5f) * 0.45f;
                float outer = (1f - d) * 0.28f;
                float alpha = Math.min(ring * 0.92f + core + outer, 0.92f);
                float bright = ring * 0.68f + core * 0.22f;
                pm.setColor(Math.min(bright + 0.22f, 1f), Math.min(bright * 0.18f, 1f),
                            Math.min(bright + 0.52f, 1f), alpha);
                pm.drawPixel(x, y);
            }
        }
        // 4 inward radial streaks suggesting gravity pull
        for (int arm = 0; arm < 4; arm++) {
            float ang = arm * MathUtils.PI * 0.5f;
            float ca = MathUtils.cos(ang), sa = MathUtils.sin(ang);
            for (float t = 0.42f; t <= 0.92f; t += 0.04f) {
                float fade = 1f - (t - 0.42f) / 0.50f;
                sp(pm, cx + ca * r * t, cy + sa * r * t, 0.82f, 0.35f, 1f, fade * 0.62f);
                sp(pm, cx + ca * r * t + sa, cy + sa * r * t - ca, 0.65f, 0.20f, 0.85f, fade * 0.38f);
                sp(pm, cx + ca * r * t - sa, cy + sa * r * t + ca, 0.65f, 0.20f, 0.85f, fade * 0.38f);
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    // EmberIV hire button icon — amber/orange glowing orb with hot core and radial heat haze
    // Intern button icon — amber orb with green "+" hire indicator
    private Texture genEmberInternTexture(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        float cx = s * 0.5f, cy = s * 0.5f, r = s * 0.5f - 2f;

        // amber filled orb
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float)Math.sqrt(dx*dx + dy*dy) / r;
                if (d > 1f) continue;
                float rim   = Math.max(0f, 1f - Math.abs(d - 0.80f) * 10f);
                float fill2 = Math.max(0f, 1f - d * 1.1f) * 0.75f;
                float a     = Math.min(rim * 0.95f + fill2, 0.95f);
                float bright = rim * 0.85f + fill2 * 0.60f;
                pm.setColor(Math.min(bright + 0.30f, 1f), Math.min(bright * 0.55f + 0.05f, 1f), bright * 0.02f, a);
                pm.drawPixel(x, y);
            }
        }

        // green "+" at center
        int pcx = (int)cx, pcy = (int)cy;
        pm.setColor(0.10f, 0.95f, 0.55f, 1f);
        pm.fillRectangle(pcx - 6, pcy - 2, 13, 4);
        pm.fillRectangle(pcx - 2, pcy - 6, 4, 13);

        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Portal button icon — cyan ring portal with dark interior and spinning vortex arcs
    private Texture genPortalIconTexture(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        float cx = s * 0.5f, cy = s * 0.5f, r = s * 0.5f - 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float)Math.sqrt(dx*dx + dy*dy) / r;
                if (d > 1f) continue;
                float ring  = Math.max(0f, 1f - Math.abs(d - 0.82f) * 14f);
                float inner = Math.max(0f, 1f - Math.abs(d - 0.55f) * 12f) * 0.5f;
                float core  = Math.max(0f, 1f - d * 4.5f) * 0.35f;
                float fill  = (1f - d) * 0.12f;
                float a     = Math.min(ring * 0.95f + inner + core + fill, 0.95f);
                float bright = ring * 0.9f + inner * 0.4f + core;
                pm.setColor(bright * 0.05f, Math.min(bright * 0.85f + 0.05f, 1f),
                            Math.min(bright + 0.1f, 1f), a);
                pm.drawPixel(x, y);
            }
        }
        // 5 vortex spokes from center outward
        for (int k = 0; k < 5; k++) {
            float ang = (float)(k * Math.PI * 2.0 / 5);
            float ca = MathUtils.cos(ang), sa = MathUtils.sin(ang);
            for (float t2 = 0.15f; t2 <= 0.70f; t2 += 0.06f) {
                float fade = 1f - t2 / 0.70f;
                int px = (int)(cx + ca * r * t2), py = (int)(cy + sa * r * t2);
                pm.setColor(0.05f, 0.92f, 1f, fade * 0.75f);
                pm.fillRectangle(px - 1, py - 1, 3, 3);
            }
        }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Rail button icon — wall bar with two glowing nodes and a ball arc
    private Texture genRailIconTexture(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        // wall bar
        pm.setColor(0.55f, 0.70f, 1.00f, 1f);
        pm.fillRectangle(4, s * 3/4 - 2, s - 8, 5);
        // two node circles on wall
        pm.setColor(0.25f, 1.00f, 0.75f, 1f);
        pm.fillCircle(s / 4,     s * 3/4, 5);
        pm.fillCircle(s * 3 / 4, s * 3/4, 5);
        // node highlight rings
        pm.setColor(1f, 1f, 1f, 0.6f);
        for (int deg = 0; deg < 360; deg += 45) {
            double a = Math.toRadians(deg);
            pm.fillRectangle((int)(s/4   + Math.cos(a)*5)-1, (int)(s*3/4 + Math.sin(a)*5)-1, 2, 2);
            pm.fillRectangle((int)(s*3/4 + Math.cos(a)*5)-1, (int)(s*3/4 + Math.sin(a)*5)-1, 2, 2);
        }
        // ball
        pm.setColor(1.00f, 0.88f, 0.20f, 1f);
        pm.fillCircle(8, s / 4, 5);
        // dashed arc path toward right node
        for (int i = 0; i <= 12; i++) {
            if (i % 3 == 2) continue;
            float t2 = i / 12f;
            int tx = (int)(8 + (s * 3/4 - 8) * t2);
            int ty = (int)(s/4 + (s * 3/4 - s/4) * t2 - (float)Math.sin(t2 * Math.PI) * 10f);
            pm.setColor(1.00f, 0.88f, 0.20f, 0.7f);
            pm.fillRectangle(tx - 1, ty - 1, 3, 3);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // ---- Perk icon generators -------------------------------------------------

    // Speed Keep — cyan circular arrow
    private Texture genPerkIconSpeed(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2, r = s / 2 - 4;
        pm.setColor(0.20f, 0.95f, 1.00f, 1f);
        for (int deg = 40; deg <= 360; deg++) {
            double a = Math.toRadians(deg);
            int px = cx + (int)(Math.cos(a) * r);
            int py = cy + (int)(Math.sin(a) * r);
            pm.fillRectangle(px - 2, py - 2, 4, 4);
        }
        // arrowhead at ~40° end
        double ae = Math.toRadians(40);
        int ax = cx + (int)(Math.cos(ae) * r), ay = cy + (int)(Math.sin(ae) * r);
        pm.fillTriangle(ax, ay - 6, ax + 6, ay + 4, ax - 6, ay + 4);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Elastic Walls — orange ball bouncing off wall
    private Texture genPerkIconElas(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        // Wall on left
        pm.setColor(1.00f, 0.55f, 0.10f, 1f);
        pm.fillRectangle(2, 4, 5, s - 8);
        // Ball at upper-right
        pm.fillCircle(s - 10, 10, 7);
        // Bounce line (diagonal from wall to ball)
        for (int i = 0; i <= 20; i++) {
            int bx = 7 + i, by = (s - 8) - i;
            pm.fillRectangle(bx - 1, by - 1, 3, 3);
        }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Wall ×3 — three vertical blue bars
    private Texture genPerkIconWall(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        pm.setColor(0.35f, 0.65f, 1.00f, 1f);
        int bw = 6, bh = s - 8, y0 = 4;
        pm.fillRectangle(5,       y0, bw, bh);
        pm.fillRectangle(s/2 - 3, y0, bw, bh);
        pm.fillRectangle(s - 11,  y0, bw, bh);
        // small "×3" via two diagonal strokes at bottom-right
        pm.setColor(1f, 1f, 1f, 0.85f);
        for (int i = 0; i < 5; i++) { pm.drawPixel(s - 9 + i, s - 6 + i); pm.drawPixel(s - 9 + i, s - 2 - i); }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Coll ×2 — two magenta circles colliding
    private Texture genPerkIconColl(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        pm.setColor(1.00f, 0.35f, 0.90f, 1f);
        pm.fillCircle(s / 2 - 8, s / 2, 9);
        pm.setColor(0.90f, 0.30f, 1.00f, 1f);
        pm.fillCircle(s / 2 + 8, s / 2, 9);
        // collision spark (white dot at center)
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fillCircle(s / 2, s / 2, 3);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // SP currency icon — gold diamond (◆ shape)
    private Texture genIconCrystal(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        int r  = s / 2 - 1;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = Math.abs(x - cx), dy = Math.abs(y - cy);
                if (dx / r + dy / r <= 1.0f) {
                    float depth = 1f - (dx / r + dy / r) * 0.55f;
                    pm.setColor(depth * 1.00f, depth * 0.82f, depth * 0.08f, 1f); // gold
                    pm.drawPixel(x, y);
                }
            }
        }
        // Facet highlight — upper-left face brighter
        for (int y = 0; y < cy; y++) {
            for (int x = 0; x < cx; x++) {
                float dx = Math.abs(x - cx), dy = Math.abs(y - cy);
                if (dx / r + dy / r <= 1.0f) {
                    float t2 = 1f - (dx / r + dy / r);
                    pm.setColor(Math.min(1f, 1.0f * t2 + 0.3f), Math.min(1f, 0.95f * t2 + 0.3f), 0.50f * t2, 0.55f);
                    pm.drawPixel(x, y);
                }
            }
        }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Space Points coin icon — round gold coin with 5-pointed star impression
    private Texture genIconSpaceCoin(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        float cx = s * 0.5f, cy = s * 0.5f, r = s * 0.5f - 1f;
        // Coin body — gold gradient (brighter toward center)
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= r) {
                    float t = 1f - dist / r;
                    pm.setColor(Math.min(1f, 0.82f + t * 0.18f),
                                Math.min(1f, 0.60f + t * 0.22f),
                                0.06f + t * 0.06f, 1f);
                    pm.drawPixel(x, y);
                }
            }
        }
        // Upper-left specular highlight
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= r * 0.55f && dx <= 0 && dy <= 0) {
                    float t = 1f - dist / (r * 0.55f);
                    pm.setColor(1f, 0.97f, 0.78f, t * 0.52f);
                    pm.drawPixel(x, y);
                }
            }
        }
        // 5-pointed star inset (dark gold lines)
        pm.setColor(0.50f, 0.24f, 0.01f, 0.80f);
        float outerStar = r * 0.50f, innerStar = r * 0.21f;
        int[] starPx = new int[10 * 2];
        for (int i = 0; i < 5; i++) {
            double ao = Math.PI / 2 + i * 2 * Math.PI / 5;
            double ai = Math.PI / 2 + (i + 0.5) * 2 * Math.PI / 5;
            starPx[i * 4]     = (int)(cx + outerStar * Math.cos(ao));
            starPx[i * 4 + 1] = (int)(cy - outerStar * Math.sin(ao));
            starPx[i * 4 + 2] = (int)(cx + innerStar * Math.cos(ai));
            starPx[i * 4 + 3] = (int)(cy - innerStar * Math.sin(ai));
        }
        for (int i = 0; i < 5; i++) {
            int nx = (i + 1) % 5;
            int ox = starPx[i * 4], oy = starPx[i * 4 + 1];
            int ix = starPx[i * 4 + 2], iy = starPx[i * 4 + 3];
            int ox2 = starPx[nx * 4], oy2 = starPx[nx * 4 + 1];
            pm.drawLine(ox, oy, ix, iy);
            pm.drawLine(ix, iy, ox2, oy2);
        }
        // Rim highlight
        pm.setColor(1f, 0.95f, 0.55f, 0.65f);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist >= r - 1.5f && dist <= r + 0.5f)
                    pm.drawPixel(x, y);
            }
        }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Energy currency icon — filled lightning bolt
    private Texture genIconBolt(int s) {
        // Pixmap y=0 = top; GL renders y-flipped so y=0 appears at bottom of sprite
        int W = s, H = s + s / 2;
        Pixmap pm = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        // Upper arm:  leans left (top-right → middle-left)
        // Notch shelf: right edge kicks far right
        // Lower arm:  both edges converge to a bottom-center tip
        float shelf = 0.48f, shelfEnd = 0.59f;
        for (int y = 0; y < H; y++) {
            float fy = (float) y / H;
            float x0, x1;
            if (fy < shelf) {
                float t = fy / shelf;
                x0 = W * (0.38f - t * 0.28f);  // 0.38 → 0.10
                x1 = W * (0.84f - t * 0.36f);  // 0.84 → 0.48
            } else if (fy < shelfEnd) {
                float t = (fy - shelf) / (shelfEnd - shelf);
                x0 = W * 0.10f;
                x1 = W * (0.48f + t * 0.44f);  // shelf: 0.48 → 0.92
            } else {
                float t = (fy - shelfEnd) / (1f - shelfEnd);
                x0 = W * (0.10f + t * 0.40f);  // converge to tip: 0.10 → 0.50
                x1 = W * (0.92f - t * 0.42f);  //                  0.92 → 0.50
            }
            int ix0 = Math.max(0, (int) x0);
            int ix1 = Math.min(W - 1, (int) x1);
            if (ix1 < ix0) continue;
            for (int x = ix0; x <= ix1; x++) {
                float cx = (ix1 > ix0) ? (float)(x - ix0) / (ix1 - ix0) : 0.5f;
                float b  = 0.80f + 0.20f * (1f - Math.abs(cx * 2f - 1f));
                pm.setColor(b, b, b * 0.65f, 1f); // warm white (tinted green by Image.setColor)
                pm.drawPixel(x, y);
            }
        }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Bump x3 — yellow circle with 6 radial spikes
    private Texture genPerkIconBump(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        pm.setColor(1.00f, 0.90f, 0.15f, 1f);
        pm.fillCircle(cx, cy, 7);
        // 6 spikes
        int innerR = 8, outerR = s / 2 - 3;
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60);
            int x1 = cx + (int)(Math.cos(a) * innerR), y1 = cy + (int)(Math.sin(a) * innerR);
            int x2 = cx + (int)(Math.cos(a) * outerR), y2 = cy + (int)(Math.sin(a) * outerR);
            for (float t2 = 0; t2 <= 1f; t2 += 0.07f) {
                int px = (int)(x1 + (x2 - x1) * t2), py = (int)(y1 + (y2 - y1) * t2);
                pm.fillRectangle(px - 2, py - 2, 4, 4);
            }
        }
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // EmberIV perk 1 — Speed Keep: cyan circle arc with speed arrow
    private Texture genEmberPerkIconSpeedKeep(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2, r = s / 2 - 4;
        pm.setColor(0.20f, 0.95f, 1.00f, 1f);
        for (int deg = 30; deg <= 360; deg++) {
            double a = Math.toRadians(deg);
            int px = cx + (int)(Math.cos(a) * r), py = cy + (int)(Math.sin(a) * r);
            pm.fillRectangle(px - 2, py - 2, 4, 4);
        }
        double ae = Math.toRadians(30);
        int ax = cx + (int)(Math.cos(ae) * r), ay = cy + (int)(Math.sin(ae) * r);
        pm.fillTriangle(ax, ay - 5, ax + 5, ay + 4, ax - 5, ay + 4);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // EmberIV perk 2 — Wall Energy: green wall bar + yellow lightning bolt
    private Texture genEmberPerkIconWallEnergy(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        pm.setColor(0.20f, 0.90f, 0.45f, 1f);
        pm.fillRectangle(3, 4, 6, s - 8);
        // lightning bolt
        pm.setColor(1.00f, 0.92f, 0.15f, 1f);
        int bx = 14, by = 6;
        pm.fillTriangle(bx, by, bx + 12, by, bx + 4, by + 14);
        pm.fillTriangle(bx + 4, by + 12, bx + 16, by + 12, bx + 4, by + s - 8);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // EmberIV perk 3 — Gravity Shift: two rotation arrows forming a circle
    private Texture genEmberPerkIconGravShift(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2, r = s / 2 - 5;
        pm.setColor(1.00f, 0.55f, 0.10f, 1f);
        // top arc (0–150 deg)
        for (int deg = 0; deg <= 150; deg++) {
            double a = Math.toRadians(deg);
            int px = cx + (int)(Math.cos(a) * r), py = cy + (int)(Math.sin(a) * r);
            pm.fillRectangle(px - 2, py - 2, 4, 4);
        }
        // arrowhead at 150 deg
        double a0 = Math.toRadians(150);
        int ax0 = cx + (int)(Math.cos(a0) * r), ay0 = cy + (int)(Math.sin(a0) * r);
        pm.fillTriangle(ax0 - 5, ay0, ax0 + 3, ay0 - 6, ax0 + 3, ay0 + 6);
        // bottom arc (180–330 deg)
        pm.setColor(0.50f, 0.80f, 1.00f, 1f);
        for (int deg = 180; deg <= 330; deg++) {
            double a = Math.toRadians(deg);
            int px = cx + (int)(Math.cos(a) * r), py = cy + (int)(Math.sin(a) * r);
            pm.fillRectangle(px - 2, py - 2, 4, 4);
        }
        double a1 = Math.toRadians(330);
        int ax1 = cx + (int)(Math.cos(a1) * r), ay1 = cy + (int)(Math.sin(a1) * r);
        pm.fillTriangle(ax1 + 5, ay1, ax1 - 3, ay1 - 6, ax1 - 3, ay1 + 6);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // EmberIV perk 4 — Portal Sync: cyan dot ←→ orange dot
    private Texture genEmberPerkIconPortalSync(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cy = s / 2;
        // cyan portal
        pm.setColor(0.10f, 0.85f, 1.00f, 1f);
        pm.fillCircle(8, cy, 6);
        // orange portal
        pm.setColor(1.00f, 0.50f, 0.05f, 1f);
        pm.fillCircle(s - 8, cy, 6);
        // double-headed arrow between them
        pm.setColor(1f, 1f, 1f, 0.90f);
        for (int x = 16; x <= s - 16; x++) pm.fillRectangle(x, cy - 1, 1, 3);
        // left arrowhead
        pm.fillTriangle(14, cy, 20, cy - 5, 20, cy + 5);
        // right arrowhead
        pm.fillTriangle(s - 14, cy, s - 20, cy - 5, s - 20, cy + 5);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // EmberIV perk 5 — Reverse Field: square outline with curved reverse arrow
    private Texture genEmberPerkIconReverse(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        // square outline
        pm.setColor(0.75f, 0.50f, 1.00f, 1f);
        int m = 6;
        pm.fillRectangle(m,     m,     s-2*m, 3);
        pm.fillRectangle(m,     s-m-3, s-2*m, 3);
        pm.fillRectangle(m,     m,     3,     s-2*m);
        pm.fillRectangle(s-m-3, m,     3,     s-2*m);
        // reverse arrow inside (two short horizontal arrows pointing opposite)
        pm.setColor(1.00f, 0.85f, 0.20f, 1f);
        int my = s / 2;
        // left arrow pointing left
        for (int x = m+5; x <= s/2 - 3; x++) pm.fillRectangle(x, my-1, 1, 3);
        pm.fillTriangle(m+4, my, m+10, my-5, m+10, my+5);
        // right arrow pointing right
        for (int x = s/2+3; x <= s-m-6; x++) pm.fillRectangle(x, my-1, 1, 3);
        pm.fillTriangle(s-m-4, my, s-m-10, my-5, s-m-10, my+5);
        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    // Frostheim Perk 1 — Arm Bumpers: hexagon outline with 6 small filled circles at tips
    private Texture genFrostPerkIconArmBumpers(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        int hexR = s / 2 - 7, dotR = 4;
        pm.setColor(0.35f, 0.75f, 1.00f, 1f);
        // hexagon outline (6 segments between vertices)
        for (int seg = 0; seg < 6; seg++) {
            double a0 = Math.toRadians(seg * 60);
            double a1 = Math.toRadians((seg + 1) * 60);
            int x0 = cx + (int)(Math.cos(a0) * hexR), y0 = cy + (int)(Math.sin(a0) * hexR);
            int x1 = cx + (int)(Math.cos(a1) * hexR), y1 = cy + (int)(Math.sin(a1) * hexR);
            for (float tt = 0; tt <= 1f; tt += 0.05f) {
                int px = (int)(x0 + (x1 - x0) * tt), py = (int)(y0 + (y1 - y0) * tt);
                pm.fillRectangle(px - 1, py - 1, 3, 3);
            }
        }
        // 6 circles at tip vertices — brighter
        pm.setColor(0.80f, 0.95f, 1.00f, 1f);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60);
            int tx = cx + (int)(Math.cos(a) * hexR), ty = cy + (int)(Math.sin(a) * hexR);
            pm.fillCircle(tx, ty, dotR);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Frostheim Perk 2 — Merge Burst: 3 small dots converging into large circle + lightning bolt
    private Texture genFrostPerkIconMergeBurst(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        // central large circle (the merged intern)
        pm.setColor(0.25f, 0.90f, 1.00f, 1f);
        pm.fillCircle(cx, cy, 8);
        // 3 small dots at 120° intervals pointing inward (lines toward center)
        pm.setColor(1.00f, 0.90f, 0.20f, 1f);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(i * 120 - 90);
            int sx = cx + (int)(Math.cos(a) * (s/2 - 5)), sy = cy + (int)(Math.sin(a) * (s/2 - 5));
            pm.fillCircle(sx, sy, 3);
            // dashed line toward center
            for (float tt = 0.35f; tt <= 0.80f; tt += 0.12f) {
                int lx = (int)(sx + (cx - sx) * tt), ly = (int)(sy + (cy - sy) * tt);
                pm.fillRectangle(lx - 1, ly - 1, 3, 3);
            }
        }
        // small lightning bolt overlay on center circle
        pm.setColor(1f, 1f, 0.50f, 1f);
        pm.fillTriangle(cx - 2, cy - 7, cx + 3, cy, cx - 1, cy);
        pm.fillTriangle(cx - 1, cy,     cx + 4, cy, cx + 1, cy + 7);
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Frostheim Perk 3 — Valley Blades: small diamond in each valley notch around a ring
    private Texture genFrostPerkIconValleyBlades(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        int ringR = s / 2 - 5;
        // faint ring outline
        pm.setColor(0.35f, 0.75f, 1.00f, 0.60f);
        for (int deg = 0; deg < 360; deg++) {
            double a = Math.toRadians(deg);
            int px = cx + (int)(Math.cos(a) * ringR), py = cy + (int)(Math.sin(a) * ringR);
            pm.fillRectangle(px - 1, py - 1, 2, 2);
        }
        // 6 teal diamonds at valley angles (offset 30° from arms = 30,90,150,210,270,330)
        pm.setColor(0.20f, 0.90f, 0.85f, 1f);
        int dR = s / 2 - 9;
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60 + 30);
            int dx = cx + (int)(Math.cos(a) * dR), dy = cy + (int)(Math.sin(a) * dR);
            // diamond: 4 triangles around center
            pm.fillTriangle(dx, dy - 4, dx - 3, dy, dx + 3, dy);
            pm.fillTriangle(dx, dy + 4, dx - 3, dy, dx + 3, dy);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Frostheim Perk 4 — Cryo Extension: clock face with ice-crystal hand
    private Texture genFrostPerkIconCryoExtension(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        int r = s / 2 - 4;
        // clock circle
        pm.setColor(0.75f, 0.90f, 1.00f, 1f);
        for (int deg = 0; deg < 360; deg++) {
            double a = Math.toRadians(deg);
            int px = cx + (int)(Math.cos(a) * r), py = cy + (int)(Math.sin(a) * r);
            pm.fillRectangle(px - 1, py - 1, 3, 3);
        }
        // 12 tick marks
        pm.setColor(0.75f, 0.90f, 1.00f, 0.70f);
        for (int tick = 0; tick < 12; tick++) {
            double a = Math.toRadians(tick * 30);
            int x1 = cx + (int)(Math.cos(a) * (r - 3)), y1 = cy + (int)(Math.sin(a) * (r - 3));
            int x2 = cx + (int)(Math.cos(a) * (r - 6)), y2 = cy + (int)(Math.sin(a) * (r - 6));
            pm.fillRectangle(Math.min(x1,x2), Math.min(y1,y2), Math.abs(x1-x2)+2, Math.abs(y1-y2)+2);
        }
        // ice-blue hour hand pointing to ~7 o'clock (extended time)
        pm.setColor(0.40f, 0.85f, 1.00f, 1f);
        double handA = Math.toRadians(210); // 7 o'clock
        int hx = cx + (int)(Math.cos(handA) * (r - 7)), hy = cy + (int)(Math.sin(handA) * (r - 7));
        for (float tt = 0.1f; tt <= 1f; tt += 0.08f) {
            int lx = (int)(cx + (hx - cx) * tt), ly = (int)(cy + (hy - cy) * tt);
            pm.fillRectangle(lx - 1, ly - 1, 3, 3);
        }
        // minute hand (12 o'clock = top = -90°)
        double minA = Math.toRadians(-90);
        int mx = cx + (int)(Math.cos(minA) * (r - 5)), my = cy + (int)(Math.sin(minA) * (r - 5));
        for (float tt = 0.1f; tt <= 1f; tt += 0.07f) {
            int lx = (int)(cx + (mx - cx) * tt), ly = (int)(cy + (my - cy) * tt);
            pm.fillRectangle(lx - 1, ly - 1, 3, 3);
        }
        // tiny center dot
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fillCircle(cx, cy, 2);
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Frostheim Perk 5 — Double Vortex: spiral arc with 2 orb dots caught in it
    private Texture genFrostPerkIconDoubleVortex(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0f, 0f, 0f, 0f); pm.fill();
        int cx = s / 2, cy = s / 2;
        // spiral: 1.5 turns, radius shrinks from outer to inner
        pm.setColor(1.00f, 0.72f, 0.10f, 1f);
        for (float tt = 0; tt <= 1.5f; tt += 0.01f) {
            double a = Math.toRadians(tt * 360 - 90);
            float r2 = (s / 2f - 4f) * (1f - tt * 0.55f);
            int px = cx + (int)(Math.cos(a) * r2), py = cy + (int)(Math.sin(a) * r2);
            pm.fillRectangle(px - 1, py - 1, 3, 3);
        }
        // arrowhead at the spiral tip (inner end, ~1.5 turns)
        double tipA = Math.toRadians(1.5 * 360 - 90);
        float tipR = (s / 2f - 4f) * (1f - 1.5f * 0.55f);
        int tx2 = cx + (int)(Math.cos(tipA) * tipR), ty2 = cy + (int)(Math.sin(tipA) * tipR);
        pm.fillTriangle(tx2, ty2 - 4, tx2 + 4, ty2 + 3, tx2 - 4, ty2 + 3);
        // 2 captured orb dots at ~0.35 and ~0.80 turns along spiral
        pm.setColor(0.25f, 0.90f, 1.00f, 1f);
        for (float pos : new float[]{0.30f, 0.75f}) {
            double oa = Math.toRadians(pos * 360 - 90);
            float or2 = (s / 2f - 4f) * (1f - pos * 0.55f);
            int ox = cx + (int)(Math.cos(oa) * or2), oy = cy + (int)(Math.sin(oa) * or2);
            pm.fillCircle(ox, oy, 4);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Rocket pointing UP. In Pixmap: large py = top of rendered sprite (LibGDX flips y).
    private Texture genLockTexture(int s) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        // Pixmap y-down: small y = top of rendered sprite in LibGDX

        // Body: bottom 55%
        int bx = s * 2 / 8, bw = s * 4 / 8;
        int bodyPmY = s * 42 / 100, bodyH = s * 52 / 100;
        pm.setColor(0.72f, 0.76f, 0.92f, 1f);
        pm.fillRectangle(bx, bodyPmY, bw, bodyH);

        // Keyhole: small dark rect in body center
        pm.setColor(0.08f, 0.10f, 0.22f, 1f);
        int kw = s / 6, kh = s / 5;
        pm.fillRectangle(s / 2 - kw / 2, bodyPmY + bodyH / 4, kw, kh);

        // Shackle: two vertical bars + top connector (y-down = top of image)
        int sw  = s / 6;
        int sl  = bx + sw / 2, sr = bx + bw - sw - sw / 2;
        int stY = s * 8 / 100, shH = bodyPmY - stY + sw;
        pm.setColor(0.72f, 0.76f, 0.92f, 1f);
        pm.fillRectangle(sl, stY, sw, shH);
        pm.fillRectangle(sr, stY, sw, shH);
        pm.fillRectangle(sl, stY, sr - sl + sw, sw);
        // Hollow inside arch
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fillRectangle(sl + sw, stY + sw, sr - sl - sw, shH - sw * 2);

        Texture t = new Texture(pm); pm.dispose(); return t;
    }

    private Texture genRocketTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f;
        float bodyW   = size * 0.16f;
        float bodyBot = size * 0.22f;
        float bodyTop = size * 0.72f;
        float noseTop = size * 0.92f;
        float finMaxW = size * 0.36f;
        float finTop  = size * 0.40f;
        float finBot  = size * 0.04f;

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float dx = px - cx;
                float fpy = py;
                // Nose cone (large py = top of sprite)
                if (fpy >= bodyTop && fpy <= noseTop) {
                    float t = (fpy - bodyTop) / (noseTop - bodyTop);
                    float hw = bodyW * (1f - t);
                    if (Math.abs(dx) <= hw) {
                        float bright = 0.72f + t * 0.28f;
                        pm.setColor(bright * 0.82f, bright * 0.90f, bright, 0.97f);
                        pm.drawPixel(px, py);
                    }
                }
                // Window circle on body
                float winCY = (bodyBot + bodyTop) * 0.57f;
                float winR  = bodyW * 0.55f;
                float wdx = px - cx, wdy = fpy - winCY;
                float wdist = (float) Math.sqrt(wdx * wdx + wdy * wdy);
                // Body rectangle
                if (fpy >= bodyBot && fpy <= bodyTop) {
                    if (Math.abs(dx) <= bodyW) {
                        float edgeFade = 1f - Math.abs(dx) / bodyW * 0.55f;
                        float t = (fpy - bodyBot) / (bodyTop - bodyBot);
                        float bright = 0.62f + t * 0.22f;
                        if (wdist < winR) {
                            // Window
                            float wt = 1f - wdist / winR;
                            pm.setColor(0.25f * wt + 0.10f, 0.65f * wt + 0.20f, 1f, 0.97f);
                        } else {
                            pm.setColor(bright * edgeFade * 0.82f, bright * edgeFade * 0.90f,
                                        bright * edgeFade, 0.97f);
                        }
                        pm.drawPixel(px, py);
                    }
                }
                // Fins (small py = bottom of sprite)
                if (fpy >= finBot && fpy <= finTop) {
                    float t = (fpy - finBot) / (finTop - finBot);
                    float finHW = bodyW + (finMaxW - bodyW) * (1f - t);
                    if (Math.abs(dx) >= bodyW * 0.92f && Math.abs(dx) <= finHW) {
                        float fade = t * 0.7f + 0.3f;
                        pm.setColor(0.42f * fade, 0.68f * fade, 1f, 0.88f);
                        pm.drawPixel(px, py);
                    }
                }
                // Engine flame at very bottom
                float flameTop = bodyBot * 0.75f;
                if (fpy >= finBot && fpy <= flameTop && Math.abs(dx) <= bodyW * 0.5f) {
                    float t = (fpy - finBot) / (flameTop - finBot);
                    float glow = 0.55f + t * 0.45f;
                    pm.setColor(0.15f + glow * 0.85f, 0.45f + glow * 0.50f, 1f, glow * 0.92f);
                    pm.drawPixel(px, py);
                }
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private Texture genGlowTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = size * 0.5f, cy = size * 0.5f, r = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                if (d < 1f) {
                    float a = (1f - d) * (1f - d);
                    pm.setColor(1f, 1f, 1f, a);
                    pm.drawPixel(x, y);
                }
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    // ---- Ember IV blade texture generators ----------------------------------------

    // Variation A — Heavy Carbon-Steel: dark grey with scratched sheen
    private Texture genBladeTextureA(int size) {
        int h = Math.max(4, size / 6);
        Pixmap pm = new Pixmap(size, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cy = h * 0.5f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < size; x++) {
                float tx = (float) x / size;
                float ty = 1f - Math.abs((y - cy) / cy);
                float sheen   = 0.42f + tx * 0.22f + ty * 0.18f;
                float scratch = MathUtils.sin(tx * 90f) * 0.025f;
                float v = Math.min(sheen + scratch, 1f);
                pm.setColor(v * 0.55f, v * 0.58f, v * 0.62f, ty * 0.95f);
                pm.drawPixel(x, y);
            }
        }
        for (int x = 0; x < size; x++) {
            pm.setColor(0.88f, 0.92f, 0.96f, 0.85f);
            pm.drawPixel(x, 0); pm.drawPixel(x, h - 1);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Variation B — Sleek Titanium: bright silver-blue gradient
    private Texture genBladeTextureB(int size) {
        int h = Math.max(4, size / 6);
        Pixmap pm = new Pixmap(size, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cy = h * 0.5f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < size; x++) {
                float tx = (float) x / size;
                float ty = 1f - Math.abs((y - cy) / cy);
                float sheen = 0.58f + tx * 0.28f + ty * 0.14f;
                float v = Math.min(sheen, 1f);
                pm.setColor(v * 0.72f, v * 0.82f, v, ty * 0.95f);
                pm.drawPixel(x, y);
            }
        }
        for (int x = 0; x < size; x++) {
            pm.setColor(0.92f, 0.96f, 1f, 0.90f);
            pm.drawPixel(x, 0); pm.drawPixel(x, h - 1);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    // Variation C — Reinforced Diamond-Cleaver: cyan crystalline facets
    private Texture genBladeTextureC(int size) {
        int h = Math.max(4, size / 6);
        Pixmap pm = new Pixmap(size, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cy = h * 0.5f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < size; x++) {
                float tx = (float) x / size;
                float ty = 1f - Math.abs((y - cy) / cy);
                float crystal = 0.5f + MathUtils.sin(tx * 12f) * 0.28f + ty * 0.22f;
                float v = Math.min(crystal, 1f);
                pm.setColor(v * 0.60f, v * 0.92f, v, ty * 0.95f);
                pm.drawPixel(x, y);
            }
        }
        for (int x = 0; x < size; x++) {
            pm.setColor(0.80f, 1f, 1f, 0.90f);
            pm.drawPixel(x, 0); pm.drawPixel(x, h - 1);
        }
        Texture tex = new Texture(pm); pm.dispose(); return tex;
    }

    private void drawThickLine(Pixmap pm, int x0, int y0, int x1, int y1, float r, float g, float b, int half) {
        pm.setColor(r, g, b, 1f);
        int dx = Math.abs(x1-x0), dy = Math.abs(y1-y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        while (true) {
            for (int tx = -half; tx <= half; tx++)
                for (int ty = -half; ty <= half; ty++)
                    pm.drawPixel(x0+tx, y0+ty);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 <  dx) { err += dx; y0 += sy; }
        }
    }

    // ---- Rendering setup --------------------------------------------------------

    private void buildRendering() {
        batch  = new SpriteBatch();
        shapeR = new ShapeRenderer();
        shapeR.setAutoShapeType(true);
        renderCam = new OrthographicCamera();
        renderViewport = new ExtendViewport(RENDER_W, RENDER_H, renderCam);
        renderCam.position.set(RENDER_W / 2f, RENDER_H / 2f, 0f);
        floatFont   = game.skin.getFont("float");
        floatLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    }

    // ---- Physics setup ----------------------------------------------------------

    private void buildPhysics() {
        float gMult = ShipData.get().gravityEnabled ? ShipData.get().planetGravityMultiplier : 0f;
        float initG = isFrostheim() ? 0f : GRAVITY * gMult;
        world = new World(new Vector2(0, initG), true);
        world.setContactListener(new EnergyContactListener(ballLastHitMs));

        physCam = new OrthographicCamera();
        physViewport = new ExtendViewport(WORLD_W, WORLD_H, physCam);
        physCam.position.set(WORLD_W * 0.5f, WORLD_H * 0.5f, 0f);

        spawnWalls();
        spawnBall(CENTRIFUGE_CX - CENTRIFUGE_R * 0.4f, CENTRIFUGE_CY + CENTRIFUGE_R * 0.4f);
        spawnBall(CENTRIFUGE_CX + CENTRIFUGE_R * 0.4f, CENTRIFUGE_CY - CENTRIFUGE_R * 0.4f);
    }

    /** 0=circle, -1=rectangle, 3=triangle, 4=diamond, 5=pentagon */
    private int centrifugeSides() {
        return switch (ShipData.get().currentPlanetIndex) {
            case 1  -> -1; // Ember IV     — rectangle
            case 2  -> -3; // Frostheim    — snowflake (6-arm)
            case 3  ->  4; // Cryon Reach  — diamond
            case 4  ->  5; // Helios Forge — pentagon
            default ->  0; // Solara       — circle
        };
    }

    // Ember IV square half-size (world units)
    private static final float RECT_HW = CENTRIFUGE_R * 0.80f;
    private static final float RECT_HH = CENTRIFUGE_R * 0.80f;

    private float centrifugeShapeAngle() {
        return switch (ShipData.get().currentPlanetIndex) {
            case 2  -> MathUtils.PI / 2f;  // Frostheim: flat base
            case 3  -> MathUtils.PI / 4f;  // Cryon: rotated 45° = diamond
            default -> 0f;
        };
    }

    private float snowflakeR(float theta) {
        // Primary 6-fold arms — sharp tips, deep valleys
        float p6 = 0.5f + 0.5f * MathUtils.cos(6f * theta);
        p6 = (float) Math.pow(p6, 3.2f);
        // Secondary 12-fold side notches on each arm
        float p12 = 0.5f + 0.5f * MathUtils.cos(12f * theta);
        p12 = (float) Math.pow(p12, 5f) * 0.28f;
        // Tertiary 24-fold micro barbs
        float p24 = 0.5f + 0.5f * MathUtils.cos(24f * theta);
        p24 = (float) Math.pow(p24, 8f) * 0.09f;
        float combined = Math.min(1f, p6 + p12 + p24);
        return SNOWFLAKE_VALLEY_R + (SNOWFLAKE_ARM_R - SNOWFLAKE_VALLEY_R) * combined;
    }

    private void spawnWalls() {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(CENTRIFUGE_CX, CENTRIFUGE_CY);
        centrifugeBody = world.createBody(bd);

        EdgeShape edge = new EdgeShape();
        FixtureDef fd  = new FixtureDef();
        fd.shape       = edge;
        fd.restitution = WALL_RESTITUTION;
        fd.friction    = 0.05f;

        int sides = centrifugeSides();
        if (sides == 0) {
            float step = (float)(2 * Math.PI / CENTRIFUGE_SEGS);
            for (int i = 0; i < CENTRIFUGE_SEGS; i++) {
                float a0 = step * i, a1 = step * (i + 1);
                edge.set(
                    MathUtils.cos(a0) * CENTRIFUGE_R, MathUtils.sin(a0) * CENTRIFUGE_R,
                    MathUtils.cos(a1) * CENTRIFUGE_R, MathUtils.sin(a1) * CENTRIFUGE_R
                );
                centrifugeBody.createFixture(fd);
            }
        } else if (sides == -3) {
            // Frostheim snowflake: 120-segment approximation of r(θ) = valley + (arm-valley)*cos^1.4(3θ)
            float step2 = (float)(2 * Math.PI / SNOWFLAKE_SEGS);
            for (int i = 0; i < SNOWFLAKE_SEGS; i++) {
                float a0 = step2 * i, a1 = step2 * (i + 1);
                float r0 = snowflakeR(a0), r1 = snowflakeR(a1);
                edge.set(MathUtils.cos(a0)*r0, MathUtils.sin(a0)*r0,
                         MathUtils.cos(a1)*r1, MathUtils.sin(a1)*r1);
                centrifugeBody.createFixture(fd);
            }
        } else if (sides < 0) {
            // Rectangle: 4 walls (local coords, body rotates)
            float hw = RECT_HW, hh = RECT_HH;
            float[][] walls = {
                {-hw, -hh,  hw, -hh}, // bottom
                { hw, -hh,  hw,  hh}, // right
                { hw,  hh, -hw,  hh}, // top
                {-hw,  hh, -hw, -hh}, // left
            };
            for (float[] w : walls) { edge.set(w[0],w[1],w[2],w[3]); centrifugeBody.createFixture(fd); }
        } else {
            float sa = centrifugeShapeAngle();
            for (int i = 0; i < sides; i++) {
                float a0 = sa + (float)(2 * Math.PI * i / sides);
                float a1 = sa + (float)(2 * Math.PI * (i + 1) / sides);
                edge.set(
                    MathUtils.cos(a0) * CENTRIFUGE_R, MathUtils.sin(a0) * CENTRIFUGE_R,
                    MathUtils.cos(a1) * CENTRIFUGE_R, MathUtils.sin(a1) * CENTRIFUGE_R
                );
                centrifugeBody.createFixture(fd);
            }
        }
        edge.dispose();
        centrifugeBody.setAngularVelocity(0f);
    }

    private void spawnBall(float x, float y) {
        if (balls.size >= internCap()) return;
        boolean fh = isFrostheim();

        BodyDef bd = new BodyDef();
        bd.type           = BodyDef.BodyType.DynamicBody;
        bd.position.set(x, y);
        bd.linearDamping  = fh ? frostheimBallDamping : 0f;
        bd.angularDamping = fh ? frostheimBallDamping : 0f;

        CircleShape circle = new CircleShape();
        circle.setRadius(isEmberIV() ? EMBER_BALL_RADIUS : BALL_RADIUS);

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
        float kickSpd = fh ? 4.5f : 3.0f;
        body.setLinearVelocity(MathUtils.cos(kickAngle) * kickSpd, MathUtils.sin(kickAngle) * kickSpd);
        circle.dispose();
        balls.add(body);
        ballLastHitMs.put(body, System.currentTimeMillis());
    }

    private void spawnCentrifugeBumper(float wx, float wy) {
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
        body.setUserData(new ShipData.BumperHitData());
        circle.dispose();
        bumpers.add(body);
    }

    private void launchCurlingBumper(float wx, float wy, float vx, float vy) {
        com.badlogic.gdx.physics.box2d.BodyDef bd = new com.badlogic.gdx.physics.box2d.BodyDef();
        bd.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody;
        bd.position.set(wx, wy);
        com.badlogic.gdx.physics.box2d.CircleShape circle = new com.badlogic.gdx.physics.box2d.CircleShape();
        circle.setRadius(bumperCoreR);
        com.badlogic.gdx.physics.box2d.FixtureDef fd = new com.badlogic.gdx.physics.box2d.FixtureDef();
        fd.shape       = circle;
        fd.restitution = 0.88f;   // high bounce — loses energy off walls not gravity
        fd.friction    = 0.02f;
        fd.density     = 1.0f;
        com.badlogic.gdx.physics.box2d.Body body = world.createBody(bd);
        body.createFixture(fd);
        body.setUserData(new ShipData.BumperHitData());
        body.setLinearVelocity(vx, vy);
        body.setGravityScale(0f);  // no gravity — settles anywhere in drum, not always bottom
        circle.dispose();
        curlingBodies.add(body);
        curlingTimers.add(0f);
    }

    private void spawnIcicleNode(float wx, float wy) {
        // Snap to nearest of 6 arm directions at clamped radius from center
        float centAng = (centrifugeBody != null) ? centrifugeBody.getAngle() : 0f;
        float dx0 = wx - CENTRIFUGE_CX, dy0 = wy - CENTRIFUGE_CY;
        float distFromCenter = (float) Math.sqrt(dx0 * dx0 + dy0 * dy0);

        // Find the nearest arm direction (k * PI/3)
        float inputAngle = (float) Math.atan2(dy0, dx0);
        float bestArmAng = centAng;
        float bestAngDiff = Float.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            float armAng = centAng + k * MathUtils.PI / 3f;
            float diff = Math.abs(MathUtils.atan2(MathUtils.sin(inputAngle - armAng), MathUtils.cos(inputAngle - armAng)));
            if (diff < bestAngDiff) { bestAngDiff = diff; bestArmAng = armAng; }
        }
        // Clamp distance between 0.3 and 0.9 of arm radius
        float minR = SNOWFLAKE_ARM_R * 0.3f;
        float maxR = SNOWFLAKE_ARM_R * 0.9f;
        float clampedDist = Math.max(minR, Math.min(maxR, distFromCenter));
        wx = CENTRIFUGE_CX + MathUtils.cos(bestArmAng) * clampedDist;
        wy = CENTRIFUGE_CY + MathUtils.sin(bestArmAng) * clampedDist;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(wx, wy);
        CircleShape circle = new CircleShape();
        circle.setRadius(ICICLE_RADIUS);
        FixtureDef fd  = new FixtureDef();
        fd.shape       = circle;
        fd.restitution = 0f;
        fd.friction    = 0f;
        Body body = world.createBody(bd);
        body.createFixture(fd);
        body.setUserData("ICICLE");
        circle.dispose();
        icicleNodes.add(body);

        // Store angle offset and radius for co-rotation with centrifuge
        float offset = bestArmAng - centAng;
        icicleAngOffsets.add(offset);
        icicleRadii.add(clampedDist);
    }

    private void splitIntern(Body ball, int ballIdx, int icicleNodeIdx) {
        Vector2 bPos = ball.getPosition();
        Vector2 bVel = ball.getLinearVelocity();
        float origSpeed = bVel.len();
        float useSpeed = (origSpeed < 0.6f) ? 0.6f : origSpeed * 0.30f;
        float baseAngle = (origSpeed < 0.001f) ? 0f : (float) Math.atan2(bVel.y, bVel.x);

        // Remove original ball
        balls.removeIndex(ballIdx);
        ballLastHitMs.remove(ball);
        world.destroyBody(ball);

        // Spawn 3 snow pellets
        PelletGroup group = new PelletGroup(bPos.x, bPos.y);
        group.icicleNodeIdx = icicleNodeIdx;  // lock the triggering icicle until merge
        float[] angleOffsets = { -25f * MathUtils.degreesToRadians, 0f, 25f * MathUtils.degreesToRadians };
        for (int p = 0; p < 3; p++) {
            BodyDef pbd = new BodyDef();
            pbd.type = BodyDef.BodyType.DynamicBody;
            pbd.position.set(bPos.x, bPos.y);
            CircleShape ps = new CircleShape();
            ps.setRadius(PELLET_RADIUS);
            FixtureDef pfd = new FixtureDef();
            pfd.shape              = ps;
            pfd.density            = 0.5f;
            pfd.restitution        = 0.92f;
            pfd.friction           = 0f;
            pfd.filter.categoryBits = CAT_PELLET;
            pfd.filter.maskBits     = MASK_PELLET;
            Body pellet = world.createBody(pbd);
            pellet.setBullet(true);
            pellet.createFixture(pfd);
            pellet.setUserData("PELLET");
            float ang = baseAngle + angleOffsets[p];
            pellet.setLinearVelocity(MathUtils.cos(ang) * useSpeed, MathUtils.sin(ang) * useSpeed);
            ps.dispose();
            snowPellets.add(pellet);
            group.pellets.add(pellet);
        }
        pelletGroups.add(group);
    }

    private void spawnArmBumpers() {
        float bodyAng = centrifugeBody != null ? centrifugeBody.getAngle() : 0f;
        for (int k = 0; k < 6; k++) {
            float ang = bodyAng + k * MathUtils.PI / 3f;
            float wx  = CENTRIFUGE_CX + MathUtils.cos(ang) * SNOWFLAKE_ARM_R * 0.90f;
            float wy  = CENTRIFUGE_CY + MathUtils.sin(ang) * SNOWFLAKE_ARM_R * 0.90f;
            BodyDef bd = new BodyDef();
            bd.type = BodyDef.BodyType.KinematicBody;
            bd.position.set(wx, wy);
            Body body = world.createBody(bd);
            CircleShape c = new CircleShape();
            c.setRadius(0.32f);
            FixtureDef fd  = new FixtureDef();
            fd.shape       = c;
            fd.restitution = 2.2f;
            fd.friction    = 0f;
            fd.filter.categoryBits = CAT_ARM_BUMPER;
            fd.filter.maskBits     = MASK_ARM_BUMPER;
            body.createFixture(fd);
            ShipData.BumperHitData ahd = new ShipData.BumperHitData();
            ahd.isArmBumper = true;
            body.setUserData(ahd);
            c.dispose();
            armBumpers.add(body);
        }
        frostheimArmBumpersActive = true;
    }

    private void spawnValleyBlades() {
        // 6 triangle spike guards placed at the 6 small-arm valley positions.
        // Each triangle tip points toward the drum center, base faces the valley wall.
        // Positions updated every physics step to track drum rotation (see stepPhysics).
        float bodyAng = centrifugeBody != null ? centrifugeBody.getAngle() : 0f;
        float hl = VALLEY_BLADE_HL;
        float hw = VALLEY_BLADE_HW;
        for (int k = 0; k < 6; k++) {
            float ang = bodyAng + (k * MathUtils.PI / 3f) + (MathUtils.PI / 6f);
            float r   = VALLEY_BLADE_R;
            float wx  = CENTRIFUGE_CX + MathUtils.cos(ang) * r;
            float wy  = CENTRIFUGE_CY + MathUtils.sin(ang) * r;
            BodyDef bd = new BodyDef();
            bd.type  = BodyDef.BodyType.StaticBody;
            bd.position.set(wx, wy);
            bd.angle = ang + MathUtils.PI;   // local +x axis points toward drum center
            Body body = world.createBody(bd);
            // Triangle vertices in local space: tip at (+hl, 0), base at (-hl, ±hw)
            PolygonShape tri = new PolygonShape();
            tri.set(new float[]{ hl, 0f,  -hl, hw,  -hl, -hw });
            FixtureDef fd  = new FixtureDef();
            fd.shape       = tri;
            fd.restitution = 1.4f;
            fd.friction    = 0f;
            body.createFixture(fd);
            ShipData.BumperHitData vhd = new ShipData.BumperHitData();
            vhd.isValleyBlade = true;
            body.setUserData(vhd);
            tri.dispose();
            valleyBlades.add(body);
        }
    }

    public void spawnAttractorBumper(float wx, float wy) {
        BodyDef bd = new BodyDef();
        bd.type    = BodyDef.BodyType.StaticBody;
        bd.position.set(wx, wy);
        Body body = world.createBody(bd);
        body.setUserData(new ShipData.AttractorHitData());

        CircleShape core = new CircleShape();
        core.setRadius(0.07f);
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

    private void spawnTeslaCoil(float wx, float wy) {
        BodyDef bd = new BodyDef();
        bd.type    = BodyDef.BodyType.StaticBody;
        bd.position.set(wx, wy);
        Body body = world.createBody(bd);
        body.setUserData("TESLA_COIL_FIELD");

        CircleShape core = new CircleShape();
        core.setRadius(0.07f);
        FixtureDef cfd  = new FixtureDef();
        cfd.shape       = core;
        cfd.restitution = 0f;   // no elastic kick — it's an energy harvester, not a bumper
        cfd.friction    = 0f;
        body.createFixture(cfd).setUserData("TESLA_COIL_CORE");
        core.dispose();

        CircleShape field = new CircleShape();
        field.setRadius(TESLA_COIL_FIELD_R);   // independent radius from gravity pull zone
        FixtureDef ffd = new FixtureDef();
        ffd.shape      = field;
        ffd.isSensor   = true;
        body.createFixture(ffd);
        field.dispose();

        teslaCoils.add(body);
    }

    public void spawnKineticBlade() {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(CENTRIFUGE_CX, CENTRIFUGE_CY);
        Body body = world.createBody(bd);

        // Triangle: base at origin (wide), tip extending along +X axis
        float halfW = BLADE_WIDTH * 2.5f;
        float len   = BLADE_LENGTH;
        PolygonShape tri = new PolygonShape();
        tri.set(new float[]{ 0f, -halfW,  len, 0f,  0f, halfW });

        FixtureDef fd = new FixtureDef();
        fd.shape       = tri;
        fd.restitution = 1.55f;
        fd.density     = 0f;
        fd.friction    = 0f;
        body.createFixture(fd);
        body.setUserData("KINETIC_BLADE");
        tri.dispose();

        bladeInitAngles.add(0f); // redistributed below
        kineticBlades.add(body);
        redistributeBlades();
    }

    private void redistributeBlades() {
        int n = kineticBlades.size;
        float baseOffset = -centrifugeBody.getAngle(); // counter-rotate so visual angle stays intuitive
        for (int i = 0; i < n; i++) {
            bladeInitAngles.set(i, (float)(Math.PI * 2.0 * i / n));
        }
    }

    // ---- UI setup ---------------------------------------------------------------

    // Transparent table layer for icon overlay inside a Stack; touches pass through.
    private com.badlogic.gdx.scenes.scene2d.Actor makeDotSep() {
        return new com.badlogic.gdx.scenes.scene2d.Actor() {
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
                float cx  = getX() + getWidth() * 0.5f;
                float cy  = getY() + getHeight() * 0.5f;
                float sz  = 3f, gap = 6f;
                batch.setColor(0.40f, 0.45f, 0.62f, 0.65f * parentAlpha);
                for (int i = -2; i <= 2; i++) {
                    batch.draw(texPixel, cx + i * gap - sz * 0.5f, cy - sz * 0.5f, sz, sz);
                }
                batch.setColor(1f, 1f, 1f, 1f);
            }
        };
    }

    private Stack makePerkSlot(com.badlogic.gdx.scenes.scene2d.ui.Image iconImg,
                                com.badlogic.gdx.scenes.scene2d.ui.Image lockImg,
                                int milestoneIdx) {
        Stack slot = new Stack();
        slot.setTouchable(Touchable.enabled);  // must be enabled so hit() returns self when no child hits
        Table iconWrap = new Table();
        iconWrap.add(iconImg).size(26f, 26f).center();
        iconWrap.setTouchable(Touchable.disabled);
        Table lockWrap = new Table();
        lockImg.setTouchable(Touchable.disabled);
        lockWrap.setTouchable(Touchable.disabled);
        lockWrap.add(lockImg).size(22f, 22f).center();
        slot.add(iconWrap);
        slot.add(lockWrap);
        slot.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (milestoneIdx >= 0 && milestoneAchieved[milestoneIdx]) {
                    activePerkPopup = milestoneIdx;
                    perkPopupTimer  = 0f;
                } else if (milestoneIdx <= -1 && isFrostheim()) {
                    int frostSlot = -milestoneIdx - 1; // -1→0, -2→1, -3→2, -4→3, -5→4
                    if (isFrostheimPerkUnlocked(frostSlot)) {
                        activeFrostPerkPopup = frostSlot;
                        perkPopupTimer       = 0f;
                    }
                }
            }
        });
        return slot;
    }

    private Table makeIconLayer(Texture tex, float iconSize) {
        return makeIconLayer(new TextureRegion(tex), iconSize);
    }

    private Table makeIconLayer(TextureRegion region, float iconSize) {
        return makeIconLayer(region, iconSize, Color.WHITE);
    }

    private Table makeIconLayer(TextureRegion region, float iconSize, Color tint) {
        Image img = new Image(new TextureRegionDrawable(region));
        img.setColor(tint);
        img.setTouchable(Touchable.disabled);
        Table t = new Table();
        t.top().padTop(22f);
        t.add(img).size(iconSize).row();
        t.setTouchable(Touchable.disabled);
        return t;
    }

    private void buildUI() {
        ui = new Stage(new ExtendViewport(RENDER_W, RENDER_H));

        Color panelBg     = new Color(0.04f, 0.06f, 0.16f, 0.97f);
        Color panelBgSolid = new Color(0.04f, 0.06f, 0.18f, 1.00f);

        // ---- Single full-screen layout table ----
        Table root = new Table();
        rootTable = root;
        root.setFillParent(true);

        // -- TOP PANEL: 3-column tactical grid (planet info | SP rate | active perks) --
        Table topPanel = new Table();
        topPanel.background(game.skin.newDrawable("white", panelBg));
        topPanel.pad(5, 8, 5, 8);

        // ---- Left column: planet / checkpoint / environment / milestone ----
        Table topLeft = new Table();
        topLeft.top().left();

        topPlanetLabel = new Label("SOLARA", game.skin);
        topPlanetLabel.setFontScale(0.92f);
        topPlanetLabel.setColor(0.20f, 0.92f, 1f, 1f);

        topCheckpointLabel = new Label("Status: Pre-Checkpoint", game.skin);
        topCheckpointLabel.setFontScale(0.72f);
        topCheckpointLabel.setColor(0.88f, 0.90f, 1f, 1f);

        topGravLabel = new Label("Gravity: 1.0G Standard", game.skin);
        topGravLabel.setFontScale(0.54f);
        topGravLabel.setColor(0.45f, 0.90f, 0.48f, 0.85f);

        topYieldsLabel = new Label("Base Yields: Collision 20 SP | Wall 0.5 SP", game.skin);
        topYieldsLabel.setFontScale(0.50f);
        topYieldsLabel.setColor(0.48f, 0.85f, 0.48f, 0.75f);

        milestoneStatusLabel = new Label("Status: Pre-Checkpoint", game.skin);
        milestoneStatusLabel.setFontScale(0.50f);
        milestoneStatusLabel.setColor(1f, 0.78f, 0.25f, 0.88f);

        // Lives + gems HUD labels — shown under Solara status in left column
        livesLabel    = new Label("5/5", game.skin);
        livesLabel.setFontScale(1.00f);
        livesLabel.setColor(1f, 0.28f, 0.40f, 0.95f);

        diamondsLabel = new Label("0", game.skin);
        diamondsLabel.setFontScale(1.00f);
        diamondsLabel.setColor(0.35f, 0.90f, 1.00f, 1f);

        Table livesGemsRow = new Table();
        // padLeft: icon center ≈ 7px, icon right edge ≈ 13px; extra space gives gap before text
        livesGemsRow.add(livesLabel).left().padLeft(24f).padRight(14f);
        livesGemsRow.add(diamondsLabel).left().padLeft(24f);

        topLeft.add(topPlanetLabel).left().row();
        topLeft.add(topCheckpointLabel).left().padTop(2f).row();
        topLeft.add(livesGemsRow).left().padTop(4f).row();

        // ---- Center column: Energy current/needed ----
        Table topCenter = new Table();
        topCenter.top();

        topSpRateHeaderLabel = new Label("ENERGY", game.skin);
        topSpRateHeaderLabel.setFontScale(0.72f);
        topSpRateHeaderLabel.setColor(0.20f, 1.00f, 0.50f, 1f);

        topSpValueLabel = new Label("0 / 0", game.skin);
        topSpValueLabel.setFontScale(1.00f);
        topSpValueLabel.setColor(0.30f, 1.00f, 0.55f, 1f);

        outputLabel = new Label("", game.skin); // hidden — kept to avoid null refs

        uiIconEnergy = new com.badlogic.gdx.scenes.scene2d.ui.Image(
            new TextureRegionDrawable(new TextureRegion(texIconEnergy)));
        uiIconEnergy.setColor(0.25f, 1.00f, 0.45f, 0.90f);

        // Planet timer in top center
        Label timerCaption = new Label("TIME ON PLANET", game.skin);
        timerCaption.setFontScale(0.58f);
        timerCaption.setColor(0.45f, 0.70f, 1.00f, 0.80f);

        planetTimerLabel = new Label("0s", game.skin);
        planetTimerLabel.setFontScale(1.60f);
        planetTimerLabel.setColor(0.28f, 0.92f, 1.00f, 1f);

        topCenter.add(timerCaption).center().row();
        topCenter.add(planetTimerLabel).center().padTop(2f).row();

        // ---- Right column: active perks list + menu button ----
        Table topRight = new Table();
        topRight.top().right();

        topPerksHeaderLabel = new Label("ACTIVE PERKS", game.skin);
        topPerksHeaderLabel.setFontScale(0.55f);
        topPerksHeaderLabel.setColor(0.45f, 0.60f, 0.88f, 0.70f);

        topPerksListLabel = new Label("> No Perks Active", game.skin);
        topPerksListLabel.setFontScale(0.50f);
        topPerksListLabel.setColor(1f, 0.88f, 0.35f, 0.88f);

        // Hidden compat labels — kept as fields to avoid null refs elsewhere
        configLabel = new Label("", game.skin);
        uptimeLabel = new Label("", game.skin);

        TextButton.TextButtonStyle menuBtnStyle = new TextButton.TextButtonStyle();
        menuBtnStyle.font      = game.skin.getFont("font");
        menuBtnStyle.up        = game.skin.newDrawable("white", new Color(0.12f, 0.15f, 0.30f, 0.85f));
        menuBtnStyle.down      = game.skin.newDrawable("white", new Color(0.20f, 0.24f, 0.44f, 1.00f));
        menuBtnStyle.over      = menuBtnStyle.down;
        menuBtnStyle.fontColor = new Color(0.65f, 0.75f, 1f, 1f);
        TextButton btnMenu = new TextButton("=", menuBtnStyle);
        btnMenu.getLabel().setFontScale(0.90f);
        btnMenu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                pauseTable.setVisible(true);
            }
        });

        TextButton.TextButtonStyle shopBtnStyle = new TextButton.TextButtonStyle();
        shopBtnStyle.font      = game.skin.getFont("font");
        shopBtnStyle.up        = game.skin.newDrawable("white", new Color(0.30f, 0.18f, 0.05f, 0.85f));
        shopBtnStyle.down      = game.skin.newDrawable("white", new Color(0.50f, 0.30f, 0.08f, 1.00f));
        shopBtnStyle.over      = shopBtnStyle.down;
        shopBtnStyle.fontColor = new Color(1f, 0.82f, 0.20f, 1f);
        TextButton btnShop = new TextButton("SHOP", shopBtnStyle);
        btnShop.getLabel().setFontScale(0.80f);
        btnShop.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (shopTable != null) shopTable.setVisible(true);
            }
        });

        // Button row: menu + shop side by side
        Table topRightBtns = new Table();
        topRightBtns.add(btnMenu).width(44f).height(34f).padRight(6f);
        topRightBtns.add(btnShop).width(68f).height(34f);

        topRight.add(topRightBtns).right().padTop(6f).row();

        // Assemble: left col fixed, center expands to fill, right col fixed
        topPanel.add(topLeft).width(168f).top().left().padRight(4f);
        topPanel.add(topCenter).expandX().top().center();
        topPanel.add(topRight).width(148f).top().right().padLeft(4f);

        // Stub labels — not shown but referenced in render
        hudPlanetLabel = new Label("", game.skin);
        hudEnergyLabel = new Label("", game.skin);
        hudRateLabel   = new Label("", game.skin);
        hudSpLabel     = new Label("", game.skin);

        root.add(topPanel).growX().row();

        // Tile styles needed before energy bar section (btnFlight inserted there)
        tileStyleLock    = makeTileStyle(game.skin, "tile_locked");
        tileStyleNorm    = makeTileStyle(game.skin, "tile_available");
        tileStyleBuy     = makeTileStyle(game.skin, "tile_buyable");
        tileStyleBuyGreen= makeTileStyle(game.skin, "tile_buygreen");
        tileStyleAct     = makeTileStyle(game.skin, "tile_active");
        tileStyleGo      = makeTileStyle(game.skin, "tile_go");
        tileStyleGoLock  = makeTileStyle(game.skin, "tile_golocked");

        // ── Perk multiplier strip ──
        // perkStrip built later — inserted into bottom panel above stats row

        // Energy label row above bar: [icon] ENERGY  value
        Table energyAboveBar = new Table();
        energyAboveBar.add(uiIconEnergy).size(11f, 16f).padRight(4f);
        energyAboveBar.add(topSpRateHeaderLabel).padRight(10f);
        energyAboveBar.add(topSpValueLabel);
        root.add(energyAboveBar).center().padTop(2f).padBottom(2f).row();

        // -- ENERGY BAR: rainbow fill + status label --
        com.badlogic.gdx.scenes.scene2d.Actor energyBarActor = new com.badlogic.gdx.scenes.scene2d.Actor() {
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch b, float parentAlpha) {
                float fill  = Math.min(1f, hudBarFill);
                float pulse = 0.55f + 0.45f * com.badlogic.gdx.math.MathUtils.sin(animTime * 3.5f);
                boolean fullyUpgraded = isFullyUpgraded();
                boolean launchReady   = fullyUpgraded && fill >= 1f;

                float bx = getX() + 6f;
                float bh = getHeight() - 10f;
                float by = getY() + getHeight() - bh - 4f; // top-aligned, leave space below for text
                float bw = getWidth() - 12f;

                // Track
                b.setColor(0.06f, 0.04f, 0.04f, 0.95f * parentAlpha);
                b.draw(texPixel, bx, by, bw, bh);

                if (fill > 0f) {
                    float fw = bw * fill;
                    // Smooth gradient: 12 slices, each interpolates between key colours
                    // Keys: 0=red, 0.25=orange, 0.5=yellow, 0.75=lime, 1.0=green
                    float[][] keys = {
                        {1.00f, 0.05f, 0.00f},  // 0%   red
                        {1.00f, 0.35f, 0.00f},  // 25%  orange
                        {1.00f, 0.82f, 0.05f},  // 50%  yellow
                        {0.55f, 0.98f, 0.10f},  // 75%  lime
                        {0.10f, 0.95f, 0.28f},  // 100% green
                    };
                    int slices = 16;
                    float sw = fw / slices;
                    for (int si = 0; si < slices; si++) {
                        // t = absolute position in the full bar, not relative to fill
                        float t = ((si + 0.5f) * fw) / (slices * bw);
                        // find which key segment we're in
                        float seg  = t * (keys.length - 1);
                        int   ki   = (int) seg;
                        float frac = seg - ki;
                        if (ki >= keys.length - 1) { ki = keys.length - 2; frac = 1f; }
                        float r = keys[ki][0] + (keys[ki+1][0] - keys[ki][0]) * frac;
                        float g = keys[ki][1] + (keys[ki+1][1] - keys[ki][1]) * frac;
                        float bl = keys[ki][2] + (keys[ki+1][2] - keys[ki][2]) * frac;
                        b.setColor(r, g, bl, parentAlpha);
                        b.draw(texPixel, bx + si * sw, by, sw + 1f, bh);
                        // bright core stripe
                        b.setColor(r * 0.6f + 0.4f, g * 0.6f + 0.4f, bl * 0.6f + 0.4f, 0.55f * parentAlpha);
                        b.draw(texPixel, bx + si * sw, by + bh * 0.28f, sw + 1f, bh * 0.44f);
                    }

                    // Outer glow when launch ready
                    if (launchReady) {
                        b.setColor(0.10f, 1f, 0.35f, 0.25f * pulse * parentAlpha);
                        b.draw(texPixel, bx, by - 5f, fw, bh + 10f);
                    }

                    // Leading edge spark
                    float sparkW = 3f + 3f * pulse;
                    b.setColor(1f, 1f, 1f, (0.80f + 0.20f * pulse) * parentAlpha);
                    b.draw(texPixel, bx + fw - sparkW * 0.5f, by - 2f, sparkW, bh + 4f);
                }

                // Border
                float bd = 1f;
                b.setColor(0.30f, 0.30f, 0.30f, 0.45f * parentAlpha);
                b.draw(texPixel, bx,           by,           bw, bd);
                b.draw(texPixel, bx,           by + bh - bd, bw, bd);
                b.draw(texPixel, bx,           by,           bd, bh);
                b.draw(texPixel, bx + bw - bd, by,           bd, bh);

                // Status text under bar — only shown at 100%
                float ty = by - 6f;
                if (launchReady) {
                    floatFont.getData().setScale(0.90f);
                    float tp = 0.60f + 0.40f * pulse;
                    floatFont.setColor(0.30f, 1f, 0.50f, tp * parentAlpha);
                    com.badlogic.gdx.graphics.g2d.GlyphLayout gl = new com.badlogic.gdx.graphics.g2d.GlyphLayout(floatFont, "TAP LAUNCH TO TRAVEL!");
                    floatFont.draw(b, "TAP LAUNCH TO TRAVEL!", bx + (bw - gl.width) * 0.5f, ty);
                } else if (fill >= 1f && !fullyUpgraded) {
                    floatFont.getData().setScale(0.90f);
                    floatFont.setColor(1f, 0.55f, 0.15f, 0.92f * parentAlpha);
                    com.badlogic.gdx.graphics.g2d.GlyphLayout gl = new com.badlogic.gdx.graphics.g2d.GlyphLayout(floatFont, "MAX OUT ALL UPGRADES TO TRAVEL");
                    floatFont.draw(b, "MAX OUT ALL UPGRADES TO TRAVEL", bx + (bw - gl.width) * 0.5f, ty);
                }
                floatFont.getData().setScale(1f);
                b.setColor(1f, 1f, 1f, 1f);
            }
        };
        root.add(energyBarActor).growX().height(36f).padBottom(6f).row();

        // Launch button created early so it can be inserted here (before styles are finalized below)
        btnFlight = new TextButton("LAUNCH", tileStyleGoLock); // style re-applied after tileStyles built
        btnFlight.setVisible(false);
        btnFlight.setTouchable(Touchable.disabled);
        launchBtnCell = root.add(btnFlight).growX().padLeft(10f).padRight(10f).height(0f);
        root.row();

        // -- CENTRIFUGE ZONE: transparent overlay — expands to fill space, holds JUMP READY button --
        Table centrifugeOverlay = new Table();

        TextButton.TextButtonStyle jumpStyle = new TextButton.TextButtonStyle();
        jumpStyle.font = game.skin.getFont("font");
        jumpStyle.up   = game.skin.newDrawable("button_primary", new Color(0.08f, 0.72f, 0.30f, 0.90f));
        jumpStyle.down = game.skin.newDrawable("button_primary", new Color(0.05f, 0.50f, 0.20f, 1.00f));
        jumpStyle.over = game.skin.newDrawable("button_primary", new Color(0.12f, 0.92f, 0.42f, 0.95f));
        jumpStyle.fontColor = Color.WHITE;

        btnJumpReady = new TextButton("^ JUMP READY ^\nTAP TO LAUNCH", jumpStyle);
        btnJumpReady.getLabel().setFontScale(0.88f);
        btnJumpReady.setVisible(false);
        btnJumpReady.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!isJumpReady()) return;
                ShipData sd2 = ShipData.get();
                sd2.saveReplayBackup(sd2.currentPlanetIndex, currentState.deepCopy());
                commitNextPlanetDestination(sd2);
                sd2.savedFlightJPS = sd2.currentJPS;
                SoundManager.get().playLaunch();
                game.transitionTo(GameState.BRIDGE_FLIGHT);
            }
        });

        // ---- Right: unlocked perks panel ----
        TextButton.TextButtonStyle perkBtnStyle = new TextButton.TextButtonStyle();
        perkBtnStyle.font      = game.skin.getFont("font");
        perkBtnStyle.up        = game.skin.newDrawable("white", new Color(0.06f, 0.09f, 0.20f, 0.82f));
        perkBtnStyle.down      = game.skin.newDrawable("white", new Color(0.12f, 0.16f, 0.30f, 0.92f));
        perkBtnStyle.over      = perkBtnStyle.down;
        perkBtnStyle.fontColor = new Color(1f, 0.88f, 0.35f, 1f);

        Table rightPerks = new Table();
        rightPerks.top();
        perksHeader = new Label("PERKS", game.skin);
        perksHeader.setFontScale(0.60f);
        perksHeader.setColor(0.50f, 0.78f, 1f, 0.72f);
        rightPerks.add(perksHeader).left().padBottom(2f).row();

        perkButtons    = new TextButton[MILESTONE_NAMES.length];
        perkDescLabels = new Label[MILESTONE_NAMES.length];
        for (int i = 0; i < MILESTONE_NAMES.length; i++) {
            final int idx = i;
            TextButton btn = new TextButton(MILESTONE_NAMES[i], perkBtnStyle);
            btn.getLabel().setFontScale(0.58f);

            Label desc = new Label(MILESTONE_DESCS[i], game.skin);
            desc.setFontScale(0.52f);
            desc.setColor(0.70f, 0.70f, 0.80f, 1f);
            desc.setVisible(false);

            btn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor a) {
                    perkDescLabels[idx].setVisible(!perkDescLabels[idx].isVisible());
                }
            });

            perkButtons[i]    = btn;
            perkDescLabels[i] = desc;
            // Rows are added dynamically via rebuildPerksTable() — only unlocked perks are included
        }

        // Main overlay: transparent (batch draws centrifuge + space points + perks in batch space)
        Table mainArea = new Table();
        mainArea.add().expandX().expandY();

        centrifugeOverlay.add(mainArea).growX().expandY().row();
        centrifugeOverlay.add(btnJumpReady).center().padBottom(24f).width(300f).height(62f);
        root.add(centrifugeOverlay).growX().expandY().row();

        // Perks panel added directly to Stage so we can set its position precisely in render()
        rightPerksTable = rightPerks;

        // -- BOTTOM CONTROLS PANEL (everything below centrifuge) --
        Table panel = new Table();
        panel.background(game.skin.newDrawable("white", panelBgSolid));
        panel.pad(10, 10, 10, 10);

        // Row 1: compact stats strip — SP left | speed center | E right
        crystalsLabel  = new Label("0 SP", game.skin);
        joulesLabel    = new Label("", game.skin, "accent"); // hidden — energy shown in top panel
        jpsLabel       = new Label("", game.skin); // blank — E/s removed
        ringSpeedLabel = new Label("1.5 r/s", game.skin);

        crystalsLabel.setFontScale(1.10f);
        crystalsLabel.setColor(0.25f, 0.80f, 1.00f, 1f); // cyan-blue = SP

        ringSpeedLabel.setFontScale(1.40f);
        ringSpeedLabel.setColor(0.70f, 0.72f, 0.82f, 1f);

        // Each label lives in its own fixed-size Container — completely isolated from siblings
        crystalsLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        ringSpeedLabel.setAlignment(com.badlogic.gdx.utils.Align.right);

        uiIconSP = new com.badlogic.gdx.scenes.scene2d.ui.Image(
            new TextureRegionDrawable(new TextureRegion(texIconSP)));
        uiIconSP.setColor(1f, 0.90f, 0.20f, 1f); // gold tint

        Table spCell = new Table();
        spCell.left();
        spCell.add(uiIconSP).size(14f, 14f).padLeft(2f).padRight(4f);
        spCell.add(crystalsLabel).left();

        com.badlogic.gdx.scenes.scene2d.ui.Container<Label> speedBox =
            new com.badlogic.gdx.scenes.scene2d.ui.Container<>(ringSpeedLabel);
        speedBox.fill().right();

        BitmapFont smallFont = game.skin.getFont("small");
        gravOffStyle = new TextButton.TextButtonStyle();
        gravOffStyle.font      = smallFont;
        gravOffStyle.up        = game.skin.newDrawable("white", OdysseyTheme.BTN_AVAILABLE);
        gravOffStyle.down      = game.skin.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        gravOffStyle.over      = game.skin.newDrawable("white", OdysseyTheme.BTN_AVAILABLE);
        gravOffStyle.fontColor = OdysseyTheme.TEXT_PRI;

        gravPullStyle = new TextButton.TextButtonStyle();
        gravPullStyle.font      = smallFont;
        gravPullStyle.up        = game.skin.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        gravPullStyle.down      = game.skin.newDrawable("white", OdysseyTheme.BTN_BUYABLE);
        gravPullStyle.over      = game.skin.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        gravPullStyle.fontColor = OdysseyTheme.ACCENT_GO;

        gravPushStyle = new TextButton.TextButtonStyle();
        gravPushStyle.font      = smallFont;
        gravPushStyle.up        = game.skin.newDrawable("white", OdysseyTheme.BTN_GO);
        gravPushStyle.down      = game.skin.newDrawable("white", OdysseyTheme.BTN_GO_LOCKED);
        gravPushStyle.over      = game.skin.newDrawable("white", OdysseyTheme.BTN_GO);
        gravPushStyle.fontColor = OdysseyTheme.ACCENT_WARN;

        btnGravCenter = new TextButton("GRAVITY\nOFF", gravOffStyle);
        btnGravCenter.setVisible(false);

        Table statsRow = new Table();
        statsRow.add(spCell).width(150f).height(28f).left();
        statsRow.add(btnGravCenter).width(80f).height(20f);
        statsRow.add(speedBox).width(150f).height(28f).right();
        // Perk strip — just above stats row, inside bottom panel
        Table perkStrip = new Table();
        perkStrip.setBackground(new NinePatchDrawable(game.skin.get("rounded_dark", NinePatch.class)));
        perkStrip.defaults().padTop(1f).padBottom(1f);
        TextureRegionDrawable lockDrw = new TextureRegionDrawable(new TextureRegion(texLock));
        lockSpeedImg = new com.badlogic.gdx.scenes.scene2d.ui.Image(lockDrw);
        lockCollImg  = new com.badlogic.gdx.scenes.scene2d.ui.Image(lockDrw);
        lockWallImg  = new com.badlogic.gdx.scenes.scene2d.ui.Image(lockDrw);
        lockBoostImg = new com.badlogic.gdx.scenes.scene2d.ui.Image(lockDrw);
        lockBumpImg  = new com.badlogic.gdx.scenes.scene2d.ui.Image(lockDrw);
        if (isEmberIV()) {
            perkIconSpeed = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texEmberPerk1)));
            perkIconElas  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texEmberPerk2)));
            perkIconWall  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texEmberPerk3)));
            perkIconColl  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texEmberPerk4)));
            perkIconBump  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texEmberPerk5)));
            perkStrip.add(makePerkSlot(perkIconSpeed, lockSpeedImg, 0)).expandX().padLeft(4f);
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconElas,  lockCollImg,  1)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconWall,  lockWallImg,  2)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconColl,  lockBoostImg, 3)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconBump,  lockBumpImg,  4)).expandX().padRight(4f);
        } else if (isFrostheim()) {
            perkIconSpeed = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texFrostPerk1)));
            perkIconElas  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texFrostPerk2)));
            perkIconWall  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texFrostPerk3)));
            perkIconColl  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texFrostPerk4)));
            perkIconBump  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texFrostPerk5)));
            perkStrip.add(makePerkSlot(perkIconSpeed, lockSpeedImg, -1)).expandX().padLeft(4f);
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconElas,  lockCollImg,  -2)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconWall,  lockWallImg,  -3)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconColl,  lockBoostImg, -4)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconBump,  lockBumpImg,  -5)).expandX().padRight(4f);
        } else {
            perkIconSpeed = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texPerkSpeed)));
            perkIconElas  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texPerkElas)));
            perkIconWall  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texPerkWall)));
            perkIconColl  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texPerkColl)));
            perkIconBump  = new com.badlogic.gdx.scenes.scene2d.ui.Image(new TextureRegionDrawable(new TextureRegion(texPerkBump)));
            perkStrip.add(makePerkSlot(perkIconSpeed, lockSpeedImg, 1)).expandX().padLeft(4f);
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconElas,  lockCollImg,  0)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconWall,  lockWallImg,  2)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconColl,  lockBoostImg, 3)).expandX();
            perkStrip.add(makeDotSep()).width(24f);
            perkStrip.add(makePerkSlot(perkIconBump,  lockBumpImg,  4)).expandX().padRight(4f);
        }
        panel.add(perkStrip).growX().padBottom(3f).row();

        panel.add(statsRow).width(420f).padBottom(3).row();

        btnGravShift = new TextButton("SHIFT GRAVITY\n5000 SP", tileStyleLock);
        btnGravShift.getLabel().setFontScale(0.75f);
        btnGravShift.setVisible(false);
        gravShiftCell = panel.add(btnGravShift).height(0f).pad(0f);
        panel.row();

        // Row 2: 4 equal boxes side by side — INTERNS | BUMPERS | GRAVITY | ENGAGE JUMP
        // Use a custom button style with card_large as background so no extra wrapper table inflates preferred width

        // (tile styles already created above, before energy bar)
        // Apply proper style + label setup to btnFlight (created early for layout insertion)
        btnFlight.setStyle(tileStyleGo);

        btnAdd         = new TextButton("HIRE ORB\n0 SP",  tileStyleLock);
        btnBumper      = new TextButton("BUMPER\nCP I",    tileStyleLock);
        btnGravityWell = new TextButton("GRAVITY\nCP II",  tileStyleLock);

        for (TextButton btn : new TextButton[]{btnAdd, btnBumper, btnGravityWell}) {
            btn.getLabel().setFontScale(0.80f);
            btn.getLabel().setAlignment(Align.bottom | Align.center);
            btn.pad(0f);
            btn.getLabelCell().expand().bottom().padBottom(18f);
            addPressEffect(btn);
        }
        // Launch button: single line, centered, bigger font
        btnFlight.getLabel().setFontScale(0.90f);
        btnFlight.getLabel().setAlignment(Align.center);
        btnFlight.pad(0f);
        btnFlight.getLabelCell().expand().center();
        addPressEffect(btnFlight);

        // 3 bigger squares at bottom
        float btnSz = (RENDER_W - 20f - 18f) / 3f;  // ~147px
        Table tileRow = new Table();
        Texture btnAddTex = isEmberIV() ? texEmberIntern : texParticle;
        Stack stackAdd = new Stack(); stackAdd.add(btnAdd); stackAdd.add(makeIconLayer(btnAddTex, 62f));
        Texture btnBmpTex = isEmberIV() ? texPortalIcon : isFrostheim() ? texCryoVent   : texBumper;
        Texture btnGrvTex = isEmberIV() ? texRailIcon   : isFrostheim() ? texTeslaCoil  : texGravCenter;
        Color   btnBmpClr = isFrostheim() ? new Color(0.85f, 0.45f, 1f, 1f) : Color.WHITE;  // violet for icicle
        Color   btnGrvClr = isFrostheim() ? new Color(1f, 0.75f, 0.20f, 1f) : Color.WHITE;  // orange for spiral
        Stack stackBmp = new Stack(); stackBmp.add(btnBumper); stackBmp.add(makeIconLayer(new TextureRegion(btnBmpTex), 58f, btnBmpClr));
        Stack stackGrv = new Stack(); stackGrv.add(btnGravityWell); stackGrv.add(makeIconLayer(new TextureRegion(btnGrvTex), 58f, btnGrvClr));
        tileRow.add(stackAdd).size(btnSz, btnSz).pad(3);
        tileRow.add(stackBmp).size(btnSz, btnSz).pad(3);
        tileRow.add(stackGrv).size(btnSz, btnSz).pad(3);
        panel.add(tileRow).center().padBottom(4).row();

        panel.add(new com.badlogic.gdx.scenes.scene2d.ui.Container<>()).height(0).row();

        root.add(panel).growX().row();

        // ---- Listeners — drag-to-place for HIRE/BUMPER/GRAVITY, tap for LAUNCH ----
        btnAdd.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                     float x, float y, int pointer, int button) {
                if (button != 0) return false;
                ShipData sd2 = ShipData.get();
                // During step 1: always capture touch so card hides immediately
                if (tutorialStep == 1) tutorialInternDragging = true;
                // Check capacity and affordability before starting actual drag
                boolean normalHire  = (balls.size + pelletGroups.size) < internCap() && sd2.crystals >= internCost();
                if (!normalHire) return tutorialStep == 1; // capture for step 1 even if not affordable
                dragMode         = PLACE_INTERN;
                dragStageX       = event.getStageX();
                dragStageY       = event.getStageY();
                dragOriginStageX = event.getStageX();
                dragOriginStageY = event.getStageY();
                return true;
            }
            @Override
            public void touchDragged(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                     float x, float y, int pointer) {
                dragStageX = event.getStageX();
                dragStageY = event.getStageY();
            }
            @Override
            public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                float x, float y, int pointer, int button) {
                tutorialInternDragging = false;
                if (dragMode != PLACE_INTERN) { dragMode = PLACE_NONE; return; }
                dragMode = PLACE_NONE;
                // Spawn at ghost position (80px above finger) to match visual
                float wx = dragStageX / PPM, wy = (dragStageY + 80f) / PPM;
                float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
                float internSafeR = CENTRIFUGE_R * 0.92f;
                float dist2i = ddx * ddx + ddy * ddy;
                if (dist2i > internSafeR * internSafeR) {
                    float dist = (float) Math.sqrt(dist2i);
                    wx = CENTRIFUGE_CX + ddx / dist * internSafeR;
                    wy = CENTRIFUGE_CY + ddy / dist * internSafeR;
                }
                ShipData sd2 = ShipData.get();
                if (balls.size < internCap() && sd2.spendCrystals(internCost())) {
                    // Slingshot: use RAW (pre-clamp) stage coords for direction
                    float originWX = dragOriginStageX / PPM;
                    float originWY = (dragOriginStageY + 80f) / PPM;
                    float rawWxI = dragStageX / PPM;
                    float rawWyI = (dragStageY + 80f) / PPM;
                    float dvx = originWX - rawWxI;   // pull down → fires up
                    float dvy = originWY - rawWyI;
                    float dragDist = (float) Math.sqrt(dvx * dvx + dvy * dvy);
                    if (dragDist < 0.01f) { dvx = 0f; dvy = 1f; dragDist = 1f; }
                    float launchSpeed = Math.min(dragDist * 4f, 9f) + 3f;
                    dvx /= dragDist; dvy /= dragDist;
                    flyingInternWX = originWX;
                    flyingInternWY = originWY;
                    flyingInternVX = dvx * launchSpeed;
                    flyingInternVY = dvy * launchSpeed;
                    flyingInternActive = true;
                    internAddedOldSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    if (tutorialStep == 1 && tutorialPostDropTimer < 0f) tutorialPostDropTimer = 0f;
                }
            }
        });
        btnBumper.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                     float x, float y, int pointer, int button) {
                if (button != 0) return false;
                ShipData sd2 = ShipData.get();
                boolean eligible;
                if (isFrostheim()) {
                    eligible = icicleNodes.size < maxIcicleNodesAllowed() && sd2.crystals >= icicileCost();
                } else if (isEmberIV()) {
                    eligible = emberCpI && portalPairs.size < maxPortalPairsNow() && sd2.crystals >= portalCost();
                } else {
                    eligible = bumpers.size < maxBumpersAllowed() && sd2.crystals >= bumperCost();
                }
                if (!eligible) return false;
                if (isEmberIV()) {
                    dragMode   = PLACE_PORTAL;
                    dragStageX = event.getStageX();
                    dragStageY = event.getStageY();
                    ShipData.get().placingStructure = true;
                    return true;
                }
                dragMode         = PLACE_BUMPER;
                dragStageX       = event.getStageX();
                dragStageY       = event.getStageY();
                dragOriginStageX = event.getStageX();
                dragOriginStageY = event.getStageY();
                ShipData.get().placingStructure = true;
                return true;
            }
            @Override
            public void touchDragged(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                     float x, float y, int pointer) {
                dragStageX = event.getStageX();
                dragStageY = event.getStageY();
            }
            @Override
            public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                float x, float y, int pointer, int button) {
                int dm = dragMode;
                dragMode = PLACE_NONE;
                ShipData.get().placingStructure = false;
                if (dm == PLACE_NONE) return;
                float wx = dragStageX / PPM, wy = (dragStageY + 80f) / PPM;
                ShipData sd2 = ShipData.get();
                if (isEmberIV() && dm == PLACE_PORTAL) {
                    if (portalPairs.size < maxPortalPairsNow() && sd2.spendCrystals(portalCost())) {
                        com.badlogic.gdx.math.Vector2 localA = snapPortalToNearestWall(wx, wy);
                        com.badlogic.gdx.math.Vector2 localB = oppositePortalLocal(localA);
                        portalPairs.add(new com.badlogic.gdx.math.Vector2[]{localA, localB});
                        int needed = balls.size * MAX_PORTAL_PAIRS;
                        if (portalOrbCooldowns.length < needed)
                            portalOrbCooldowns = new float[needed];
                        portalGlow = new float[MAX_PORTAL_PAIRS * 2];
                        savePortalState(sd2);
                    }
                    return;
                }
                float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
                float maxPlaceR = (isFrostheim() ? SNOWFLAKE_ARM_R : CENTRIFUGE_R) * 0.92f;
                float dist2b = ddx * ddx + ddy * ddy;
                if (dist2b > maxPlaceR * maxPlaceR) {
                    float dist = (float) Math.sqrt(dist2b);
                    wx = CENTRIFUGE_CX + ddx / dist * maxPlaceR;
                    wy = CENTRIFUGE_CY + ddy / dist * maxPlaceR;
                }
                if (isFrostheim()) {
                    if (icicleNodes.size < maxIcicleNodesAllowed() && sd2.spendCrystals(icicileCost())) {
                        spawnIcicleNode(wx, wy);
                    }
                } else if (!isEmberIV()) {
                    if (bumpers.size < maxBumpersAllowed() && sd2.spendCrystals(bumperCost())) {
                        // Slingshot: use RAW (pre-clamp) stage coords for direction
                        float originWX = dragOriginStageX / PPM;
                        float originWY = (dragOriginStageY + 80f) / PPM;
                        float rawWxS = dragStageX / PPM;
                        float rawWyS = (dragStageY + 80f) / PPM;
                        float dvx = originWX - rawWxS;   // pull down → fires up
                        float dvy = originWY - rawWyS;
                        float dragDist = (float) Math.sqrt(dvx * dvx + dvy * dvy);
                        if (dragDist < 0.01f) { dvx = 0f; dvy = 1f; dragDist = 1f; }
                        float launchSpeed = Math.min(dragDist * 4f, 9f) + 3f;
                        dvx /= dragDist; dvy /= dragDist;
                        // Start visual flight from button position upward
                        flyingBumperWX = originWX;
                        flyingBumperWY = originWY;
                        flyingBumperVX = dvx * launchSpeed;
                        flyingBumperVY = dvy * launchSpeed;
                        flyingBumperActive = true;
                    }
                }
            }
        });
        btnGravityWell.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                     float x, float y, int pointer, int button) {
                if (button != 0) return false;
                ShipData sd2 = ShipData.get();
                if (isFrostheim()) {
                    if (teslaCoils.size >= maxTeslaCoilsAllowed()) return false;
                    if (sd2.crystals < teslaCost()) return false;
                } else if (isEmberIV()) {
                    if (!emberCpII || relayNodes.size >= maxRelayNodesNow()) return false;
                    if (sd2.crystals < relayCost()) return false;
                    dragMode   = PLACE_RELAY;
                    dragStageX = event.getStageX();
                    dragStageY = event.getStageY();
                    ShipData.get().placingStructure = true;
                    return true;
                } else {
                    if (!gravityUnlocked() || attractors.size >= maxGravityAllowed()) return false;
                    if (sd2.crystals < gravityCost()) return false;
                }
                dragMode         = PLACE_GRAVITY;
                dragStageX       = event.getStageX();
                dragStageY       = event.getStageY();
                dragOriginStageX = event.getStageX();
                dragOriginStageY = event.getStageY();
                return true;
            }
            @Override
            public void touchDragged(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                     float x, float y, int pointer) {
                dragStageX = event.getStageX();
                dragStageY = event.getStageY();
            }
            @Override
            public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent event,
                                float x, float y, int pointer, int button) {
                int dm = dragMode;
                dragMode = PLACE_NONE;
                ShipData.get().placingStructure = false;
                if (dm == PLACE_NONE) return;
                float wx = dragStageX / PPM, wy = (dragStageY + 80f) / PPM;
                ShipData sd2 = ShipData.get();
                // Relay snaps to wall — skip radius check, direction from center determines snap
                if (isEmberIV() && dm == PLACE_RELAY) {
                    if (relayNodes.size < maxRelayNodesNow() && sd2.spendCrystals(relayCost())) {
                        com.badlogic.gdx.math.Vector2 wallPt = snapRelayToWall(wx, wy);
                        relayNodes.add(wallPt);
                        int needed = (balls.size + 1) * MAX_RELAY_NODES;
                        if (relayCooldowns.length < needed) relayCooldowns = new float[needed];
                        saveRelayState(sd2);
                    }
                    return;
                }
                float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
                float gravSafeR = CENTRIFUGE_R * 0.92f;
                float dist2gv = ddx * ddx + ddy * ddy;
                if (dist2gv > gravSafeR * gravSafeR) {
                    float dist = (float) Math.sqrt(dist2gv);
                    wx = CENTRIFUGE_CX + ddx / dist * gravSafeR;
                    wy = CENTRIFUGE_CY + ddy / dist * gravSafeR;
                }
                if (isFrostheim()) {
                    if (teslaCoils.size < maxTeslaCoilsAllowed() && sd2.spendCrystals(teslaCost())) {
                        spawnTeslaCoil(wx, wy);
                    }
                } else if (!isEmberIV()) {
                    if (attractors.size < maxGravityAllowed() && sd2.spendCrystals(gravityCost())) {
                        float originWX = dragOriginStageX / PPM;
                        float originWY = (dragOriginStageY + 80f) / PPM;
                        float rawWxA   = dragStageX / PPM;
                        float rawWyA   = (dragStageY + 80f) / PPM;
                        float dvx = originWX - rawWxA;
                        float dvy = originWY - rawWyA;
                        float dragDist = (float) Math.sqrt(dvx * dvx + dvy * dvy);
                        if (dragDist < 0.01f) { dvx = 0f; dvy = 1f; dragDist = 1f; }
                        float launchSpeed = Math.min(dragDist * 4f, 9f) + 3f;
                        dvx /= dragDist; dvy /= dragDist;
                        flyingAttractorWX = originWX;
                        flyingAttractorWY = originWY;
                        flyingAttractorVX = dvx * launchSpeed;
                        flyingAttractorVY = dvy * launchSpeed;
                        flyingAttractorActive = true;
                    }
                }
            }
        });
        btnGravShift.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { /* replaced by btnGravCenter */ }
        });

        btnGravCenter.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!emberPerk3) return;
                if (!ShipData.get().spendCrystals(10_000f)) {
                    showNotif("NOT ENOUGH SP", "Gravity switch costs 10,000 SP");
                    return;
                }
                if (!emberGravityEnabled) {
                    emberGravityEnabled = true;
                    emberGravityPush    = false;
                } else if (!emberGravityPush) {
                    emberGravityPush = true;
                } else {
                    emberGravityEnabled = false;
                    emberGravityPush    = false;
                }
                triggerShake(2f, 0.06f);
            }
        });

        btnFlight.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!isFullyUpgraded()) {
                    showNotif("LAUNCH BLOCKED", upgradeGateHint());
                    return;
                }
                if (!isJumpReady()) return;
                ShipData sd2 = ShipData.get();
                if (!sd2.isReplayMode) {
                    // Lives gate — skipped during replay (diamonds already paid as entry fee)
                    if (!sd2.canPlay()) {
                        if (livesBlockTable != null) livesBlockTable.setVisible(true);
                        return;
                    }
                    sd2.consumeLife();
                }
                sd2.saveReplayBackup(sd2.currentPlanetIndex, currentState.deepCopy());
                commitNextPlanetDestination(sd2);
                sd2.savedFlightJPS = sd2.currentJPS;
                SoundManager.get().playLaunch();
                game.transitionTo(GameState.BRIDGE_FLIGHT);
            }
        });

        ui.addActor(root);

        // ---- Pause overlay (added last = top Z-order, blocks all touches when visible) ----
        pauseTable = new Table();
        pauseTable.setFillParent(true);
        pauseTable.setVisible(false);
        pauseTable.setTouchable(Touchable.enabled);
        pauseTable.background(game.skin.newDrawable("white", new Color(0f, 0.02f, 0.08f, 0.88f)));
        pauseTable.center();

        Label pauseTitle = new Label("PAUSED", game.skin);
        pauseTitle.setFontScale(1.6f);
        pauseTitle.setColor(0.20f, 0.80f, 1f, 1f);
        pauseTable.add(pauseTitle).padBottom(36f).row();

        TextButton btnContinue = new TextButton("CONTINUE", tileStyleNorm);
        btnContinue.getLabel().setFontScale(0.82f);
        btnContinue.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                pauseTable.setVisible(false);
            }
        });
        pauseTable.add(btnContinue).width(280f).height(62f).padBottom(18f).row();

        TextButton btnMainMenu = new TextButton("MAIN MENU", tileStyleNorm);
        btnMainMenu.getLabel().setFontScale(0.82f);
        btnMainMenu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                pauseTable.setVisible(false);
                game.transitionTo(GameState.MAIN_MENU);
            }
        });
        pauseTable.add(btnMainMenu).width(280f).height(62f).padBottom(18f).row();

        TextButton btnCheckpoint = new TextButton("BACK TO CHECKPOINT", tileStyleBuy);
        btnCheckpoint.getLabel().setFontScale(0.82f);
        btnCheckpoint.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                backToCheckpoint();
            }
        });
        pauseTable.add(btnCheckpoint).width(280f).height(62f).padBottom(28f).row();

        // DEV cheat: inject 100 000 energy instantly
        TextButton.TextButtonStyle cheatStyle = new TextButton.TextButtonStyle();
        cheatStyle.font      = game.skin.getFont("font");
        cheatStyle.up        = game.skin.newDrawable("white", new Color(0.08f, 0.22f, 0.10f, 0.80f));
        cheatStyle.down      = game.skin.newDrawable("white", new Color(0.12f, 0.38f, 0.16f, 0.90f));
        cheatStyle.over      = cheatStyle.down;
        cheatStyle.fontColor = new Color(0.35f, 1f, 0.45f, 1f);
        TextButton btnCheat = new TextButton("DEV: +100 000 ENERGY", cheatStyle);
        btnCheat.getLabel().setFontScale(0.62f);
        btnCheat.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                cheatAddEnergy();
            }
        });
        pauseTable.add(btnCheat).width(280f).height(48f).row();

        // DEV cheat: inject 100 000 space points (crystals)
        TextButton.TextButtonStyle cheatSpStyle = new TextButton.TextButtonStyle();
        cheatSpStyle.font      = game.skin.getFont("font");
        cheatSpStyle.up        = game.skin.newDrawable("white", new Color(0.06f, 0.14f, 0.28f, 0.80f));
        cheatSpStyle.down      = game.skin.newDrawable("white", new Color(0.10f, 0.22f, 0.44f, 0.90f));
        cheatSpStyle.over      = cheatSpStyle.down;
        cheatSpStyle.fontColor = new Color(0.30f, 0.85f, 1f, 1f);
        TextButton btnCheatSP = new TextButton("DEV: +100 000 SP", cheatSpStyle);
        btnCheatSP.getLabel().setFontScale(0.62f);
        btnCheatSP.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                cheatAddSP();
            }
        });
        pauseTable.add(btnCheatSP).width(280f).height(48f).row();

        // DEV cheat: pass full level — max resources, spawn all upgrades, trigger launch
        TextButton.TextButtonStyle cheatPassStyle = new TextButton.TextButtonStyle();
        cheatPassStyle.font      = game.skin.getFont("font");
        cheatPassStyle.up        = game.skin.newDrawable("white", new Color(0.22f, 0.08f, 0.08f, 0.85f));
        cheatPassStyle.down      = game.skin.newDrawable("white", new Color(0.40f, 0.12f, 0.12f, 0.90f));
        cheatPassStyle.over      = cheatPassStyle.down;
        cheatPassStyle.fontColor = new Color(1f, 0.40f, 0.35f, 1f);
        TextButton btnCheatPass = new TextButton("DEV: PASS LEVEL", cheatPassStyle);
        btnCheatPass.getLabel().setFontScale(0.62f);
        btnCheatPass.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                cheatPassLevel();
            }
        });
        pauseTable.add(btnCheatPass).width(280f).height(48f).row();

        // DEV cheat: jump to any planet directly
        Label planetCheatLabel = new Label("DEV: JUMP TO PLANET", game.skin);
        planetCheatLabel.setFontScale(0.52f);
        planetCheatLabel.setColor(0.60f, 1f, 0.55f, 0.80f);
        pauseTable.add(planetCheatLabel).padTop(14f).padBottom(4f).row();

        TextButton.TextButtonStyle planetBtnStyle = new TextButton.TextButtonStyle();
        planetBtnStyle.font      = game.skin.getFont("font");
        planetBtnStyle.up        = game.skin.newDrawable("white", new Color(0.05f, 0.20f, 0.08f, 0.85f));
        planetBtnStyle.down      = game.skin.newDrawable("white", new Color(0.10f, 0.38f, 0.14f, 0.95f));
        planetBtnStyle.over      = planetBtnStyle.down;
        planetBtnStyle.fontColor = new Color(0.45f, 1f, 0.50f, 1f);

        Table devPlanetRow = new Table();
        for (int pi = 0; pi < ShipData.PLANETS.length; pi++) {
            final int planetIdx = pi;
            String shortName = ShipData.PLANETS[pi].name.split(" ")[0]; // first word
            TextButton btn = new TextButton(shortName, planetBtnStyle);
            btn.getLabel().setFontScale(0.50f);
            btn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor a) {
                    cheatJumpToPlanet(planetIdx);
                }
            });
            devPlanetRow.add(btn).width(52f).height(40f).pad(2f);
        }
        pauseTable.add(devPlanetRow).padBottom(8f).row();

        ui.addActor(pauseTable);

        // ---- Lives block overlay (shown when player taps LAUNCH with 0 lives) ----
        livesBlockTable = new Table();
        livesBlockTable.setFillParent(true);
        livesBlockTable.setVisible(false);
        livesBlockTable.setTouchable(Touchable.enabled);
        livesBlockTable.background(game.skin.newDrawable("white", new Color(0f, 0f, 0f, 0.90f)));
        livesBlockTable.center();

        Label noLivesTitle = new Label("OUT OF LIVES", game.skin);
        noLivesTitle.setFontScale(1.50f);
        noLivesTitle.setColor(1f, 0.28f, 0.28f, 1f);

        // Invisible spacer label — real hearts are drawn by drawTopBarIcons() via ShapeRenderer
        greyHeartsLabel = new Label("· · · · ·", game.skin);
        greyHeartsLabel.setFontScale(1.30f);
        greyHeartsLabel.setColor(0f, 0f, 0f, 0f); // fully transparent spacer
        livesBlockTable.add(greyHeartsLabel).padBottom(10f).row();

        livesBlockTable.add(noLivesTitle).padBottom(10f).row();

        Label noLivesSub = new Label("Lives refill 1 per hour.", game.skin);
        noLivesSub.setFontScale(0.65f);
        noLivesSub.setColor(0.65f, 0.65f, 0.70f, 1f);
        livesBlockTable.add(noLivesSub).padBottom(6f).row();

        lifeTimerLabel = new Label("Next life in 0:00", game.skin);
        lifeTimerLabel.setFontScale(0.90f);
        lifeTimerLabel.setColor(0.90f, 0.90f, 0.90f, 1f);
        livesBlockTable.add(lifeTimerLabel).padBottom(28f).row();

        TextButton btnAdHalve = new TextButton("WATCH AD — HALVE WAIT", tileStyleBuy);
        btnAdHalve.getLabel().setFontScale(0.72f);
        btnAdHalve.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                // TODO: show rewarded ad; on completion halve wait
                showNotif("COMING SOON", "Rewarded ads not yet implemented.");
            }
        });
        livesBlockTable.add(btnAdHalve).width(300f).height(56f).padBottom(12f).row();

        TextButton btnBuyLife = new TextButton("BUY 1 LIFE  ·  30 GEM", tileStyleGo);
        btnBuyLife.getLabel().setFontScale(0.72f);
        btnBuyLife.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                ShipData sdL = ShipData.get();
                if (sdL.diamonds >= 30) {
                    sdL.diamonds -= 30;
                    sdL.lives = Math.min(sdL.lives + 1, sdL.maxLives);
                    if (sdL.lives >= sdL.maxLives) sdL.nextLifeAtMs = 0L;
                    livesBlockTable.setVisible(false);
                } else {
                    showNotif("NOT ENOUGH GEMS", "Open the SHOP to get more Gems.");
                }
            }
        });
        livesBlockTable.add(btnBuyLife).width(300f).height(56f).padBottom(18f).row();

        livesBlockGemsLabel = new Label("Gems: 0", game.skin);
        livesBlockGemsLabel.setFontScale(0.70f);
        livesBlockGemsLabel.setColor(0.30f, 0.82f, 1.00f, 0.90f);
        livesBlockTable.add(livesBlockGemsLabel).padBottom(22f).row();

        TextButton btnDismissLives = new TextButton("OK — I'LL WAIT", tileStyleNorm);
        btnDismissLives.getLabel().setFontScale(0.72f);
        btnDismissLives.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                livesBlockTable.setVisible(false);
            }
        });
        livesBlockTable.add(btnDismissLives).width(300f).height(52f).padBottom(10f).row();

        TextButton btnLivesMainMenu = new TextButton("MAIN MENU", tileStyleNorm);
        btnLivesMainMenu.getLabel().setFontScale(0.72f);
        btnLivesMainMenu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                livesBlockTable.setVisible(false);
                game.transitionTo(GameState.MAIN_MENU);
            }
        });
        livesBlockTable.add(btnLivesMainMenu).width(300f).height(52f).row();

        ui.addActor(livesBlockTable);

        // ---- Shop overlay (placeholder — 3 tabs coming soon) ----
        shopTable = new Table();
        shopTable.setFillParent(true);
        shopTable.setVisible(false);
        shopTable.setTouchable(Touchable.enabled);
        shopTable.background(game.skin.newDrawable("white", new Color(0f, 0.02f, 0.08f, 0.93f)));
        shopTable.center();

        Label shopTitle = new Label("SHOP", game.skin);
        shopTitle.setFontScale(1.60f);
        shopTitle.setColor(1f, 0.82f, 0.20f, 1f);
        shopTable.add(shopTitle).padBottom(12f).row();

        Label shopSoonLabel = new Label("Full shop coming soon!\n\nTabs:\n  Ads — watch for rewards\n  Gems — buy hard currency\n  Permanents — lifetime boosts", game.skin);
        shopSoonLabel.setFontScale(0.65f);
        shopSoonLabel.setColor(0.70f, 0.78f, 0.88f, 1f);
        shopSoonLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        shopTable.add(shopSoonLabel).padBottom(36f).row();

        TextButton btnCloseShop = new TextButton("CLOSE", tileStyleNorm);
        btnCloseShop.getLabel().setFontScale(0.80f);
        btnCloseShop.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                shopTable.setVisible(false);
            }
        });
        shopTable.add(btnCloseShop).width(240f).height(58f).row();

        ui.addActor(shopTable);
        decisionTable = new Table();
        decisionTable.setFillParent(true);
        decisionTable.setVisible(false);
        decisionTable.setTouchable(Touchable.enabled);
        decisionTable.background(game.skin.newDrawable("white", new Color(0f, 0.05f, 0.12f, 0.93f)));
        decisionTable.center();

        Label decisionTitle = new Label("* BLIZZARD DECISION", game.skin);
        decisionTitle.setFontScale(1.3f);
        decisionTitle.setColor(0.45f, 0.90f, 1f, 1f);
        decisionTable.add(decisionTitle).padBottom(8f).row();

        Label decisionSub = new Label("Choose your evolution path — this is permanent", game.skin);
        decisionSub.setFontScale(0.60f);
        decisionSub.setColor(0.65f, 0.75f, 0.85f, 1f);
        decisionTable.add(decisionSub).padBottom(28f).row();

        TextButton btnAllTesla = new TextButton("ALL ICICLE  →  SPIRAL\nConvert every Icicle Node into a Tesla Coil", tileStyleBuy);
        btnAllTesla.getLabel().setFontScale(0.70f);
        btnAllTesla.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { applyFrostheimDecision(1); }
        });
        decisionTable.add(btnAllTesla).width(310f).height(68f).padBottom(14f).row();

        TextButton btnAllCryo = new TextButton("ALL TESLA  →  ICICLE\nConvert every Tesla Coil into an Icicle Node", tileStyleAct);
        btnAllCryo.getLabel().setFontScale(0.70f);
        btnAllCryo.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { applyFrostheimDecision(2); }
        });
        decisionTable.add(btnAllCryo).width(310f).height(68f).padBottom(14f).row();

        TextButton btnMoreInterns = new TextButton("+2 INTERNS  (cap → 12)\nSacrifice conversion for raw intern power", tileStyleGo);
        btnMoreInterns.getLabel().setFontScale(0.70f);
        btnMoreInterns.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { applyFrostheimDecision(3); }
        });
        decisionTable.add(btnMoreInterns).width(310f).height(68f).row();

        ui.addActor(decisionTable);

        inputMux = new InputMultiplexer(ui, new InputAdapter() {
            @Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
                if (btn == 1) { placementMode = PLACE_NONE; return true; }
                // ---- Harvest tap: check charged structures before placement ----
                touchWorld.set(sx, sy, 0);
                physViewport.unproject(touchWorld);
                float wx = touchWorld.x, wy = touchWorld.y;
                ShipData _hsd = ShipData.get();
                float _tapR2 = (BUMPER_RADIUS * 2.5f) * (BUMPER_RADIUS * 2.5f);
                for (int _i = 0; _i < bumpers.size; _i++) {
                    Body _b = bumpers.get(_i);
                    if (!(_b.getUserData() instanceof ShipData.BumperHitData)) continue;
                    ShipData.BumperHitData _bhd = (ShipData.BumperHitData) _b.getUserData();
                    if (!_bhd.harvestPending) continue;
                    float _dx = _b.getPosition().x - wx, _dy = _b.getPosition().y - wy;
                    if (_dx * _dx + _dy * _dy < _tapR2) {
                        float _joules = Math.max(10f, _hsd.currentJPS * 30f);
                        int   _gems   = 1 + _i / 5;
                        _hsd.addJoules(_joules);
                        _hsd.diamonds += _gems;
                        _bhd.harvestPending = false;
                        _bhd.hitCount = 0;
                        queueHarvestPop(_b.getPosition().x * PPM, _b.getPosition().y * PPM, _joules, _gems);
                        return true;
                    }
                }
                for (int _i = 0; _i < attractors.size; _i++) {
                    Body _b = attractors.get(_i);
                    if (!(_b.getUserData() instanceof ShipData.AttractorHitData)) continue;
                    ShipData.AttractorHitData _ahd = (ShipData.AttractorHitData) _b.getUserData();
                    if (!_ahd.harvestPending) continue;
                    float _dx = _b.getPosition().x - wx, _dy = _b.getPosition().y - wy;
                    if (_dx * _dx + _dy * _dy < _tapR2) {
                        float _joules = Math.max(10f, _hsd.currentJPS * 30f);
                        int   _gems   = 1 + _i / 5;
                        _hsd.addJoules(_joules);
                        _hsd.diamonds += _gems;
                        _ahd.harvestPending = false;
                        _ahd.hitCount = 0;
                        queueHarvestPop(_b.getPosition().x * PPM, _b.getPosition().y * PPM, _joules, _gems);
                        return true;
                    }
                }
                if (btn != 0 || placementMode == PLACE_NONE) return false;
                float dx = wx - CENTRIFUGE_CX, dy = wy - CENTRIFUGE_CY;
                if (dx * dx + dy * dy >= CENTRIFUGE_R * CENTRIFUGE_R) return false;
                ShipData sd = ShipData.get();
                boolean fh = isFrostheim();
                if (placementMode == PLACE_BUMPER) {
                    if (fh && icicleNodes.size < maxIcicleNodesAllowed() && sd.spendCrystals(icicileCost())) {
                        spawnIcicleNode(wx, wy);
                        placementMode = PLACE_NONE;
                    } else if (!fh && !isEmberIV() && bumpers.size < maxBumpersAllowed() && sd.spendCrystals(bumperCost())) {
                        spawnCentrifugeBumper(wx, wy);
                        placementMode = PLACE_NONE;
                    }
                } else if (placementMode == PLACE_GRAVITY) {
                    if (fh && teslaCoils.size < maxTeslaCoilsAllowed() && sd.spendCrystals(teslaCost())) {
                        spawnTeslaCoil(wx, wy);
                        placementMode = PLACE_NONE;
                    } else if (!fh && !isEmberIV() && attractors.size < maxGravityAllowed() && sd.spendCrystals(gravityCost())) {
                        spawnAttractorBumper(wx, wy);
                        placementMode = PLACE_NONE;
                    }
                } else if (placementMode == PLACE_SPRING_PAD) {
                    // Snap spring pad to ring inner wall at the tapped angle
                    if (isEmberIV() && springPads.size < MAX_SPRING_PADS
                            && sd.spendCrystals(springPadCost())) {
                        spawnSpringPad(wx, wy);
                        placementMode = PLACE_NONE;
                    }
                }
                return true;
            }
        });
    }

    // ---- Lifecycle --------------------------------------------------------------

    @Override
    public void hide() {
        snapshotState();
        ShipData.get().save();
    }

    public void show() {
        Gdx.input.setInputProcessor(inputMux);

        // If arrivalReady is still set when entering the lab the player skipped the arrival
        // screen (rocket/planet overlap at t=0). Auto-claim so state stays consistent.
        if (ShipData.get().arrivalReady) {
            ShipData.get().claimArrivalReward();
        }

        int pidx = ShipData.get().currentPlanetIndex;
        currentDef   = ShipData.PLANET_DEFS[pidx];
        currentState = ShipData.get().getState(pidx);
        boolean didFullReset = false;
        if (pidx != lastPlanetIndex) {
            lastPlanetIndex = pidx;
            fullReset();
            texBackground.dispose();
            texRing.dispose();
            texBackground = genBackground();
            texRing       = genRingTexture((int) RING_TEX_SIZE);
            didFullReset = true;
        }

        ShipData sd = ShipData.get();
        float gMult = sd.gravityEnabled ? sd.planetGravityMultiplier : 0f;
        if (!sd.gravityEnabled) {
            world.setGravity(new Vector2(0f, 0f));
        } else if (isEmberIV()) {
            world.setGravity(new Vector2(0f, -9.81f * 1.6f));
        } else if (isFrostheim()) {
            world.setGravity(new Vector2(0f, -2.5f * gMult));
        } else {
            world.setGravity(new Vector2(0f, GRAVITY * gMult));
        }

        if (balls.size == 0) {
            ShipData sdInit = ShipData.get();
            int needed = sdInit.savedBallCount > 0
                ? sdInit.savedBallCount
                : (sdInit.arrivalsCompleted > 0 ? 2 : 1);
            needed = Math.max(0, needed - sdInit.pendingNewRecruits);
            float[][] initSpots = {
                {CENTRIFUGE_CX - 0.6f, CENTRIFUGE_CY + 0.4f},
                {CENTRIFUGE_CX + 0.6f, CENTRIFUGE_CY - 0.4f},
                {CENTRIFUGE_CX - 0.8f, CENTRIFUGE_CY - 0.3f},
                {CENTRIFUGE_CX + 0.8f, CENTRIFUGE_CY + 0.3f},
                {CENTRIFUGE_CX,        CENTRIFUGE_CY + 0.7f},
                {CENTRIFUGE_CX - 0.4f, CENTRIFUGE_CY - 0.7f},
                {CENTRIFUGE_CX + 0.4f, CENTRIFUGE_CY + 0.7f},
                {CENTRIFUGE_CX - 0.7f, CENTRIFUGE_CY + 0.1f},
            };
            for (int i = 0; i < needed && i < initSpots.length; i++) {
                spawnBall(initSpots[i][0], initSpots[i][1]);
            }
        }

        if (didFullReset) {
            restorePortalsAndRelays();
            restoreStructures();
        }
        applySectorPerks();
        claimGemFarming();
        claimPendingRecruits();
    }

    private void savePortalState(ShipData sd) {
        sd.savedPortalPairs = new float[portalPairs.size * 4];
        for (int i = 0; i < portalPairs.size; i++) {
            com.badlogic.gdx.math.Vector2[] pair = portalPairs.get(i);
            sd.savedPortalPairs[i * 4]     = pair[0].x;
            sd.savedPortalPairs[i * 4 + 1] = pair[0].y;
            sd.savedPortalPairs[i * 4 + 2] = pair[1].x;
            sd.savedPortalPairs[i * 4 + 3] = pair[1].y;
        }
    }

    private void saveRelayState(ShipData sd) {
        sd.savedRelayNodes = new float[relayNodes.size * 2];
        for (int i = 0; i < relayNodes.size; i++) {
            sd.savedRelayNodes[i * 2]     = relayNodes.get(i).x;
            sd.savedRelayNodes[i * 2 + 1] = relayNodes.get(i).y;
        }
    }

    private void restorePortalsAndRelays() {
        portalPairs.clear();
        relayNodes.clear();
        ShipData sd = ShipData.get();
        if (sd.savedPortalPairs.length >= 4) {
            for (int i = 0; i + 3 < sd.savedPortalPairs.length; i += 4) {
                com.badlogic.gdx.math.Vector2 a = new com.badlogic.gdx.math.Vector2(
                    sd.savedPortalPairs[i], sd.savedPortalPairs[i + 1]);
                com.badlogic.gdx.math.Vector2 b = new com.badlogic.gdx.math.Vector2(
                    sd.savedPortalPairs[i + 2], sd.savedPortalPairs[i + 3]);
                portalPairs.add(new com.badlogic.gdx.math.Vector2[]{a, b});
            }
            int needed = balls.size * MAX_PORTAL_PAIRS;
            if (portalOrbCooldowns.length < needed) portalOrbCooldowns = new float[needed];
            portalGlow = new float[MAX_PORTAL_PAIRS * 2];
        }
        if (sd.savedRelayNodes.length >= 2) {
            for (int i = 0; i + 1 < sd.savedRelayNodes.length; i += 2) {
                relayNodes.add(new com.badlogic.gdx.math.Vector2(
                    sd.savedRelayNodes[i], sd.savedRelayNodes[i + 1]));
            }
            int needed = (balls.size + 1) * MAX_RELAY_NODES;
            if (relayCooldowns.length < needed) relayCooldowns = new float[needed];
        }
    }

    /** Called by OdysseyGame.pause() / dispose() — snapshots all structural state into ShipData. */
    public void snapshotState() {
        ShipData sd = ShipData.get();
        sd.savedBumpers      = bodiesToFloatArray(bumpers);
        sd.savedAttractors   = bodiesToFloatArray(attractors);
        sd.savedIcicleNodes  = bodiesToFloatArray(icicleNodes);
        sd.savedTeslaCoils   = bodiesToFloatArray(teslaCoils);
        sd.savedSpringPads = bodiesToFloatArray(springPads);
        System.arraycopy(milestoneAchieved, 0, sd.savedMilestoneAchieved, 0, milestoneAchieved.length);
        sd.savedFrostheimCpI          = frostheimCpI;
        sd.savedFrostheimCpII         = frostheimCpII;
        sd.savedFrostheimCpIII        = frostheimCpIII;
        sd.savedFrostheimIcicleUnlocked = frostheimIcicleUnlocked;
        sd.savedEmberHeavyChassis     = emberHeavyChassis;
        sd.savedEmberMagneticRim      = emberMagneticRim;
        sd.savedHubUpgradeTier        = hubUpgradeTier;
        sd.savedBallCount             = balls.size;
        sd.savedKineticBladeCount           = kineticBlades.size;
        sd.savedFrostheimDecision           = frostheimDecision;
        sd.savedTeslaHarvestRate            = teslaHarvestRate;
        sd.savedPortalBidirectional         = portalBidirectional;
        sd.savedEmberSpinReversed           = emberSpinReversed;
        sd.savedGravShiftStep = !emberGravityEnabled ? 0 : (emberGravityPush ? 2 : 1);
        sd.savedEmberThirdInternUnlocked    = emberThirdInternUnlocked;
        sd.savedFrostheimThirdInternUnlocked = frostheimThirdInternUnlocked;
        sd.savedFrostheimArmBumpersActive    = frostheimArmBumpersActive;
        savePortalState(sd);
        saveRelayState(sd);
    }

    private static float[] bodiesToFloatArray(Array<Body> bodies) {
        float[] arr = new float[bodies.size * 2];
        for (int i = 0; i < bodies.size; i++) {
            Vector2 pos = bodies.get(i).getPosition();
            arr[i * 2]     = pos.x;
            arr[i * 2 + 1] = pos.y;
        }
        return arr;
    }

    private void restoreStructures() {
        ShipData sd = ShipData.get();
        // Nothing to restore on fresh game
        if (sd.savedBallCount == 0 && sd.savedBumpers.length == 0) return;

        // Restore checkpoint / milestone flags before spawning (guards inside spawn methods need them)
        System.arraycopy(sd.savedMilestoneAchieved, 0, milestoneAchieved, 0, milestoneAchieved.length);
        frostheimCpI          = sd.savedFrostheimCpI;
        frostheimCpII         = sd.savedFrostheimCpII;
        frostheimCpIII        = sd.savedFrostheimCpIII;
        frostheimIcicleUnlocked = sd.savedFrostheimIcicleUnlocked;
        emberHeavyChassis     = sd.savedEmberHeavyChassis;
        emberMagneticRim      = sd.savedEmberMagneticRim;
        hubUpgradeTier        = sd.savedHubUpgradeTier;
        frostheimDecision            = sd.savedFrostheimDecision;
        frostheimThirdInternUnlocked = sd.savedFrostheimThirdInternUnlocked;
        frostheimArmBumpersActive    = sd.savedFrostheimArmBumpersActive;
        teslaHarvestRate             = sd.savedTeslaHarvestRate;
        portalBidirectional          = sd.savedPortalBidirectional;
        emberSpinReversed            = sd.savedEmberSpinReversed;
        emberGravityEnabled          = (sd.savedGravShiftStep > 0);
        emberGravityPush             = (sd.savedGravShiftStep == 2);
        emberThirdInternUnlocked     = sd.savedEmberThirdInternUnlocked;

        // No persistent capture state — clear any stale captures
        spiralCaptures.clear();

        // Spawn placed structures
        int maxBR = maxBumpersAllowed();
        for (int i = 0; i + 1 < sd.savedBumpers.length && bumpers.size < maxBR; i += 2)
            spawnCentrifugeBumper(sd.savedBumpers[i], sd.savedBumpers[i + 1]);
        int maxGR = maxGravityAllowed();
        for (int i = 0; i + 1 < sd.savedAttractors.length && attractors.size < maxGR; i += 2)
            spawnAttractorBumper(sd.savedAttractors[i], sd.savedAttractors[i + 1]);
        for (int i = 0; i + 1 < sd.savedIcicleNodes.length && icicleNodes.size < maxIcicleNodesAllowed(); i += 2) {
            float ix = sd.savedIcicleNodes[i], iy = sd.savedIcicleNodes[i + 1];
            if (ix == 0f && iy == 0f) continue;  // skip phantom/corrupt entries
            spawnIcicleNode(ix, iy);
        }
        for (int i = 0; i + 1 < sd.savedTeslaCoils.length && teslaCoils.size < maxTeslaCoilsAllowed(); i += 2)
            spawnTeslaCoil(sd.savedTeslaCoils[i], sd.savedTeslaCoils[i + 1]);
        for (int i = 0; i + 1 < sd.savedSpringPads.length; i += 2)
            spawnSpringPad(sd.savedSpringPads[i], sd.savedSpringPads[i + 1]);
        if (isFrostheim() && frostheimArmBumpersActive && armBumpers.size == 0) spawnArmBumpers();
        if (isFrostheim() && frostheimValleyBladesUnlocked && valleyBlades.size == 0) spawnValleyBlades();

        // Spawn extra interns if saved count exceeds what show() already placed
        float[][] extraSpots = {
            {CENTRIFUGE_CX - 1.2f, CENTRIFUGE_CY + 0.0f}, {CENTRIFUGE_CX + 1.2f, CENTRIFUGE_CY + 0.0f},
            {CENTRIFUGE_CX - 0.4f, CENTRIFUGE_CY - 0.8f}, {CENTRIFUGE_CX + 0.4f, CENTRIFUGE_CY + 0.8f},
            {CENTRIFUGE_CX - 1.5f, CENTRIFUGE_CY + 0.5f}, {CENTRIFUGE_CX + 1.5f, CENTRIFUGE_CY - 0.5f},
            {CENTRIFUGE_CX - 0.8f, CENTRIFUGE_CY - 1.0f}, {CENTRIFUGE_CX + 0.8f, CENTRIFUGE_CY + 1.0f},
        };
        int cap = Math.min(sd.savedBallCount, internCap());
        for (int i = balls.size; i < cap; i++) {
            int si = (i - 2) % extraSpots.length;
            if (si >= 0) spawnBall(extraSpots[si][0], extraSpots[si][1]);
        }

        // Restore kinetic blades (EmberIV) — spawn by count, positions self-redistribute
        for (int i = kineticBlades.size; i < sd.savedKineticBladeCount; i++) spawnKineticBlade();

        // Silently apply Frostheim checkpoint physics effects (avoids re-triggering celebrations)
        if (isFrostheim()) {
            if (frostheimCpI) {
                frostheimBallDamping = 0.005f;
                for (int i = 0; i < balls.size; i++) {
                    balls.get(i).setLinearDamping(0.005f);
                    balls.get(i).setAngularDamping(0.005f);
                }
            }
            if (frostheimCpII) {
                Array<Fixture> wallFx = centrifugeBody.getFixtureList();
                for (int i = 0; i < wallFx.size; i++) wallFx.get(i).setRestitution(0.94f);
            }
            if (frostheimCpIII) {
                teslaHarvestRate = 30f;
                ShipData.get().maxInternSpeed = 8.5f;
            }
        }
    }

    private void claimGemFarming() {
        int earned = ShipData.get().claimGemFarming();
        if (earned > 0) {
            showNotif("GEM FARMS", "+" + earned + " gems from planetary farms");
        }
    }

    private void claimPendingRecruits() {
        ShipData sd = ShipData.get();
        int n = sd.pendingNewRecruits;
        if (n <= 0) return;
        sd.pendingNewRecruits = 0;
        float[][] spots = {
            {CENTRIFUGE_CX - 0.5f, CENTRIFUGE_CY + 0.5f},
            {CENTRIFUGE_CX + 0.5f, CENTRIFUGE_CY - 0.5f},
            {CENTRIFUGE_CX - 0.8f, CENTRIFUGE_CY - 0.3f},
            {CENTRIFUGE_CX + 0.8f, CENTRIFUGE_CY + 0.3f},
        };
        for (int i = 0; i < n && i < spots.length; i++) {
            if (balls.size < internCap()) {
                spawnBall(spots[i][0], spots[i][1]);
            }
        }
        if (n > 0) showNotif("NEW CREW", n + " recruits from Nova Terra joined!");
    }

    private void applySectorPerks() {
        int sr = ShipData.get().sectorReached;

        if (isFrostheim() || isEmberIV()) {
            // Perks triggered by ring speed inside checkMilestones()
            return;
        }

        // ---- Solara / default perk chain (checkpoint-gated ones only) ----
        if (sr >= 2 && !milestoneAchieved[2]) {
            milestoneAchieved[2] = true;
            ShipData.get().wallEnergyMult = 3f;
            showCeleb("PERK UNLOCKED", MILESTONE_NAMES[2] + "\n" + MILESTONE_DESCS[2]);
            SoundManager.get().playMilestone();
            triggerShake(4f, 0.12f);
        }
        if (sr >= 2 && !milestoneAchieved[3]) {
            milestoneAchieved[3] = true;
            ShipData.get().collisionEnergyMult = 2f;
            showCeleb("PERK UNLOCKED", MILESTONE_NAMES[3] + "\n" + MILESTONE_DESCS[3]);
            SoundManager.get().playMilestone();
            triggerShake(4f, 0.12f);
        }
        if (sr >= 3 && !milestoneAchieved[5]) {
            milestoneAchieved[5] = true;
            applyOverdrive();
            showCeleb("PERK UNLOCKED", MILESTONE_NAMES[5] + "\n" + MILESTONE_DESCS[5]);
            SoundManager.get().playMilestone();
            triggerShake(4f, 0.12f);
        }
    }

    @Override
    public void render(float delta) {
        // ---- Tutorial step-machine ----
        if (isFrostheim() && !tutorialDone) { tutorialDone = true; tutorialStep = 99; }
        boolean inputHit = Gdx.input.justTouched()
                || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY);

        if (tutorialStep == 0 && inputHit) {
            // Step 0 → 1: intro dismissed, physics starts, show "hire intern" callout
            tutorialStep             = 1;
            tutorialStepAge          = 0f;
            tutorialEnergyBaseline   = ShipData.get().powerGenerated;
            tutorialDone             = true;
        } else if (tutorialStep == 1) {
            // Advance only after intern is placed and 3s have passed
            if (tutorialPostDropTimer >= 0f) {
                tutorialPostDropTimer += delta;
                if (tutorialPostDropTimer >= 3f) {
                    tutorialPostDropTimer = -1f;
                    tutorialStep          = 2;
                    tutorialStepAge       = 0f;
                }
            }
        } else if (tutorialStep == 2) {
            tutorialStepAge += delta;
            // advance when 200J earned THIS session, or on tap
            float earnedThisSession = ShipData.get().powerGenerated - tutorialEnergyBaseline;
            if (earnedThisSession >= 200f || inputHit) {
                tutorialStep    = 3;
                tutorialStepAge = 0f;
            }
        } else if (tutorialStep == 3) {
            tutorialStepAge += delta;
            if (inputHit) {
                tutorialStep    = 4;
                tutorialStepAge = 0f;
            }
        } else if (tutorialStep == 4) {
            tutorialStepAge += delta;
            if (inputHit) {
                tutorialStep    = 6;
                tutorialStepAge = 0f;
            }
        } else if (tutorialStep == 5) {
            tutorialStep = 6; // skip — duplicate of step 4
        }

        uptime   += delta;
        animTime += delta;
        // Flying bumper: travel from button to drum, then hand off to physics
        if (flyingBumperActive) {
            flyingBumperWX += flyingBumperVX * delta;
            flyingBumperWY += flyingBumperVY * delta;
            float _fdx = flyingBumperWX - CENTRIFUGE_CX;
            float _fdy = flyingBumperWY - CENTRIFUGE_CY;
            // Entered drum interior — spawn physics body
            if (_fdx * _fdx + _fdy * _fdy < (CENTRIFUGE_R * 0.80f) * (CENTRIFUGE_R * 0.80f)) {
                launchCurlingBumper(flyingBumperWX, flyingBumperWY, flyingBumperVX, flyingBumperVY);
                flyingBumperActive = false;
            }
            // Flew past drum or off world — cancel (crystal cost already paid)
            else if (flyingBumperWY > WORLD_H + 2f || flyingBumperWY < -1f
                     || flyingBumperWX < -1f || flyingBumperWX > WORLD_W + 1f) {
                flyingBumperActive = false;
            }
        }
        // Flying intern: travel from button to drum, then spawn physics ball
        if (flyingInternActive) {
            flyingInternWX += flyingInternVX * delta;
            flyingInternWY += flyingInternVY * delta;
            float _idx = flyingInternWX - CENTRIFUGE_CX;
            float _idy = flyingInternWY - CENTRIFUGE_CY;
            if (_idx * _idx + _idy * _idy < (CENTRIFUGE_R * 0.80f) * (CENTRIFUGE_R * 0.80f)) {
                spawnBall(flyingInternWX, flyingInternWY);
                // Override random kick with entry velocity
                if (!balls.isEmpty()) {
                    balls.get(balls.size - 1).setLinearVelocity(flyingInternVX * 0.6f, flyingInternVY * 0.6f);
                }
                internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                internAddedTimer    = INTERN_ADDED_HOLD;
                hireIdleTimer       = 0f;
                SoundManager.get().playHire();
                triggerShake(2f, 0.06f);
                flyingInternActive = false;
            } else if (flyingInternWY > WORLD_H + 2f || flyingInternWY < -1f
                       || flyingInternWX < -1f || flyingInternWX > WORLD_W + 1f) {
                flyingInternActive = false;
            }
        }
        // Flying gravity well
        if (flyingAttractorActive) {
            flyingAttractorWX += flyingAttractorVX * delta;
            flyingAttractorWY += flyingAttractorVY * delta;
            float _adx = flyingAttractorWX - CENTRIFUGE_CX;
            float _ady = flyingAttractorWY - CENTRIFUGE_CY;
            if (_adx * _adx + _ady * _ady < (CENTRIFUGE_R * 0.80f) * (CENTRIFUGE_R * 0.80f)) {
                spawnAttractorBumper(flyingAttractorWX, flyingAttractorWY);
                flyingAttractorActive = false;
            } else if (flyingAttractorWY > WORLD_H + 2f || flyingAttractorWY < -1f
                       || flyingAttractorWX < -1f || flyingAttractorWX > WORLD_W + 1f) {
                flyingAttractorActive = false;
            }
        }
        // Age all pulse-history entries; drop any that have fully faded
        for (int _pi = pulseHistory.size - 1; _pi >= 0; _pi--) {
            pulseHistory.get(_pi)[1] += delta;
            if (pulseHistory.get(_pi)[1] >= PULSE_DURATION) pulseHistory.removeIndex(_pi);
        }
        if (tutorialDone && !pauseTable.isVisible() && !decisionTable.isVisible()) stepPhysics(delta);
        updateJPS(delta);
        if (internAddedTimer > 0) internAddedTimer = Math.max(0, internAddedTimer - delta);
        if (notifActive)  notifTimer  = Math.min(notifTimer + delta, NOTIF_FADEIN * 2f);
        if (celebActive)  celebTimer  = Math.min(celebTimer + delta, CELEB_SLIDE + 0.1f);
        // Idle nudge: count up while player has ≤2 interns and can still hire
        if (balls.size <= 2 && balls.size < internCap()) hireIdleTimer += delta;
        else hireIdleTimer = 0f;
        if (tutorialDone) checkFreeInternCondition();
        checkMilestones();

        Gdx.gl.glClearColor(
            OdysseyTheme.SPACE_BG.r,
            OdysseyTheme.SPACE_BG.g,
            OdysseyTheme.SPACE_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderViewport.apply();
        // Screen shake: offset camera for SHAKE_DURATION seconds, then snap back
        if (shakeTimer > 0f) {
            shakeTimer = Math.max(0f, shakeTimer - delta);
            float mag = shakeMag * (shakeTimer / shakeDuration);
            float cy = renderViewport.getWorldHeight() * 0.5f;
            renderCam.position.x = RENDER_W * 0.5f + (MathUtils.random() - 0.5f) * 2f * mag;
            renderCam.position.y = cy + (MathUtils.random() - 0.5f) * 2f * mag;
        } else {
            renderCam.position.set(RENDER_W * 0.5f, renderViewport.getWorldHeight() * 0.5f, 0f);
        }
        renderCam.update();
        batch.setProjectionMatrix(renderCam.combined);

        // Drain contact events → spawn floats
        final float SIDE_L_X = (CENTRIFUGE_CX - CENTRIFUGE_R) + 0.3f;
        final float SIDE_R_X = (CENTRIFUGE_CX + CENTRIFUGE_R) - 0.3f;
        com.badlogic.gdx.utils.Array<float[]> events = ShipData.get().pendingContactEvents;
        for (int ei = 0; ei < events.size; ei++) {
            float[] ev = events.get(ei);
            FloatEntry fe = new FloatEntry();
            int colorType = (int) ev[3];
            if (colorType == 0) {
                // Wall hit — show at actual contact point in world coords
                fe.wx = ev[0];
                fe.wy = ev[1];
            } else {
                // Intern-intern / bumper / attractor — alternate left/right sides
                floatSideTog = !floatSideTog;
                fe.wx = floatSideTog ? SIDE_L_X : SIDE_R_X;
                fe.wy = CENTRIFUGE_CY + (MathUtils.random() - 0.5f) * CENTRIFUGE_R * 0.8f;
            }
            fe.value     = ev[2];
            fe.colorType = colorType;
            fe.age       = 0f;
            fe.driftX    = 0f;
            activeFloats.add(fe);
        }
        events.clear();

        // Advance and cull floats
        for (int ei = activeFloats.size - 1; ei >= 0; ei--) {
            FloatEntry fe = activeFloats.get(ei);
            fe.age += delta;
            if (fe.age >= FloatEntry.LIFETIME) activeFloats.removeIndex(ei);
        }


        batch.begin();
        drawHudBar();
        drawBackground();
        drawCentrifuge();
        if (isEmberIV()) drawEmberCenter();
        drawEmberHub();
        drawKineticBlades();
        // Objective 2: bypass Solara/Frostheim structures on Ember IV — visual layer isolation
        if (!isEmberIV()) {
            drawAttractors();
            drawBumpers();
        }
        drawSpringPads();      // Ember IV spring-pads along ring wall
        drawTeslaCoils();
        drawIcicleNodes();
        drawSnowPellets();
        drawArmBumpers();
        drawValleyBlades();
        drawInterns();
        drawRelayNodes();
        drawPortals();
        drawHarvestGlows();
        drawInternCountBadge();
        drawHireIdleNudge();
        drawFloatNumbers();
        drawPlacementPreview();
        // drawHeartbeatPulse() removed — coin bump effect disabled
        batch.end();

        // ---- UI overlay ----
        ui.getViewport().apply();
        ui.act(delta);
        ShipData sd = ShipData.get();
        ShipData.PlanetProfile currentPlanet = sd.getCurrentPlanet();
        boolean fh    = isFrostheim();
        boolean ember = isEmberIV();
        // ASCII-safe currency symbols — default BitmapFont has no box-drawing glyphs
        String sparkSym = fh ? "FS" : "SP";   // FS = Frost Shards, SP = Space Points

        // ---- Stats strip ----
        joulesLabel.setText(""); // energy now shown in top panel
        crystalsLabel.setText((int) sd.crystals + " " + sparkSym);
        jpsLabel.setText("");

        // ---- Lives / Gems HUD tick ----
        sd.tickLives();
        livesLabel.setText(sd.lives + "/" + sd.maxLives);
        livesLabel.setColor(sd.lives > 0 ? new Color(1f, 0.35f, 0.35f, 1f)
                                         : new Color(1f, 0.20f, 0.20f, 1.00f));
        diamondsLabel.setText(String.valueOf(sd.diamonds));
        if (livesBlockTable.isVisible()) {
            long secs = sd.secondsToNextLife();
            lifeTimerLabel.setText(secs <= 0 ? "Life ready soon..."
                : String.format("Next life in %d:%02d", secs / 60, secs % 60));
            livesBlockGemsLabel.setText("Gems: " + sd.diamonds);
        }

        SoundManager.get().update(delta);

        // Drain physics sound events (throttled in SoundManager)
        ShipData snd = ShipData.get();
        while (snd.pendingBumperSounds > 0) {
            SoundManager.get().playBumper();
            snd.pendingBumperSounds--;
        }
        while (snd.pendingCollisionSounds > 0) {
            SoundManager.get().playCollision();
            snd.pendingCollisionSounds--;
        }

        // Update HUD strip
        float eDelta = energyDeltaSinceLaunch();
        float eCost  = nextCheckpointEnergyCost();
        hudBarFill = eCost > 0 ? eDelta / eCost : 0f;
        hudPlanetLabel.setText(sd.getCurrentPlanet().name.toUpperCase());
        if (eCost >= 1_000f)
            hudEnergyLabel.setText(String.format("%.1f/%.0fKE", eDelta/1000f, eCost/1000f));
        else
            hudEnergyLabel.setText(String.format("%.0f/%.0fE", eDelta, eCost));
        hudRateLabel.setText("");
        String sparkSym2 = isFrostheim() ? "FS" : "SP";
        hudSpLabel.setText((int) sd.crystals + " " + sparkSym2);

        // Perk strip — highlight multipliers above base value
        // Left→right: Elas(5.3) → Wall×3(6.0) → Coll×2(7.5) → Bump×3(9.5)
        if (isEmberIV()) {
            updatePerkIcon(perkIconSpeed, lockSpeedImg, milestoneAchieved[0]);
            updatePerkIcon(perkIconElas,  lockCollImg,  milestoneAchieved[1]);
            updatePerkIcon(perkIconWall,  lockWallImg,  milestoneAchieved[2]);
            updatePerkIcon(perkIconColl,  lockBoostImg, milestoneAchieved[3]);
            updatePerkIcon(perkIconBump,  lockBumpImg,  milestoneAchieved[4]);
        } else if (isFrostheim()) {
            updatePerkIcon(perkIconSpeed, lockSpeedImg, frostheimArmBumpersActive);
            updatePerkIcon(perkIconElas,  lockCollImg,  frostheimValleyBladesUnlocked);
            updatePerkIcon(perkIconWall,  lockWallImg,  frostheimMergeBurstUnlocked);
            updatePerkIcon(perkIconColl,  lockBoostImg, frostheimExtendedPelletUnlocked);
            updatePerkIcon(perkIconBump,  lockBumpImg,  frostheimDoubleCapture);
        } else {
            updatePerkIcon(perkIconSpeed, lockSpeedImg, milestoneAchieved[1]);
            updatePerkIcon(perkIconElas,  lockCollImg,  milestoneAchieved[0]);
            updatePerkIcon(perkIconWall,  lockWallImg,  milestoneAchieved[2]);
            updatePerkIcon(perkIconColl,  lockBoostImg, milestoneAchieved[3]);
            updatePerkIcon(perkIconBump,  lockBumpImg,  milestoneAchieved[4]);
        }
        int cap = internCap();

        float ringNow = centrifugeBody.getAngularVelocity();
        // Only update text when value changes by ≥0.1 — prevents per-frame label resize jitter
        float roundedSpeed = Math.round(ringNow * 10f) / 10f;
        if (Math.abs(roundedSpeed - lastDisplayedSpeed) >= 0.1f) {
            ringSpeedLabel.setText(String.format("%.1f r/s", roundedSpeed));
            lastDisplayedSpeed = roundedSpeed;
        }

        // ---- Economy-loop button styles ----
        TextButton.TextButtonStyle CORAL = tileStyleBuyGreen;
        TextButton.TextButtonStyle BUY   = tileStyleBuyGreen;
        TextButton.TextButtonStyle LOCK  = tileStyleLock;
        TextButton.TextButtonStyle ACT   = tileStyleAct;
        TextButton.TextButtonStyle GO    = tileStyleGo;
        TextButton.TextButtonStyle NORM  = tileStyleNorm;

        // effectiveCount counts pellet-groups as 1 orb each so the display never drops when split
        int effectiveCount = balls.size + pelletGroups.size;
        boolean orbCanBuy = (effectiveCount < cap) && sd.crystals >= internCost();
        // Ring speed label turns coral whenever more orbs can still be purchased — visual causal link
        ringSpeedLabel.setColor(orbCanBuy ? OdysseyTheme.ACCENT_WARN : new Color(0.70f, 0.72f, 0.82f, 1f));

        // ---- ADD ORB / Intern button text ----
        if (effectiveCount >= cap) {
            String nxt;
            if (fh) {
                nxt = cap == 4  ? "Req: CP I"   : cap == 7  ? "Req: CP II"
                    : cap == 9  ? "Req: CP III" : "MAX";
            } else if (ember) {
                nxt = cap == 4  ? "Req: CP I"   : cap == 7  ? "Req: CP II"
                    : cap == 9  ? "Req: CP III" : cap == 10 ? "Req: LAND!" : "MAX";
            } else {
                nxt = cap == 4  ? "Req: CP I"   : cap == 6  ? "Req: CP II"
                    : cap == 10 ? "Req: CP III" : "MAX";
            }
            btnAdd.setText(cap < MAX_INTERNS
                ? String.format("%d/%d CAP\n%s", effectiveCount, cap, nxt)
                : String.format("%d/%d\nFULL CAP", effectiveCount, cap));
        } else {
            btnAdd.setText(String.format(
                "%d/%d HIRE\n%.0f %s", effectiveCount, cap, internCost(), sparkSym));
        }

        // ---- Bumper / Icicle Node / Blade button ----
        if (fh) {
            int maxCV = maxIcicleNodesAllowed();
            if (maxCV == 0) {
                btnBumper.setText("ICICLE\nReq: CP I");
            } else if (icicleNodes.size >= maxCV) {
                btnBumper.setText(String.format("ICICLE\n%d/%d FULL", icicleNodes.size, maxCV));
            } else if (placementMode == PLACE_BUMPER) {
                btnBumper.setText("ICICLE\nTap Ring");
            } else {
                btnBumper.setText(String.format("ICICLE %d/%d\n%.0f FS", icicleNodes.size, maxCV, icicileCost()));
            }
        } else if (ember) {
            if (!emberCpI) {
                btnBumper.setText("PORTAL\nReq: CP I");
            } else if (portalPairs.size >= maxPortalPairsNow()) {
                btnBumper.setText(String.format("PORTAL\n%d/%d FULL", portalPairs.size, maxPortalPairsNow()));
            } else if (dragMode == PLACE_PORTAL) {
                btnBumper.setText("PORTAL\nDrag to Wall");
            } else {
                btnBumper.setText(String.format("PORTAL %d/%d\n%.0f SP", portalPairs.size, maxPortalPairsNow(), portalCost()));
            }
        } else {
            int maxB = maxBumpersAllowed();
            if (maxB == 0) {
                btnBumper.setText("BUMPER\nReq: CP I");
            } else if (bumpers.size >= maxB) {
                btnBumper.setText(String.format("BUMPER\n%d/%d FULL", bumpers.size, maxB));
            } else if (placementMode == PLACE_BUMPER) {
                btnBumper.setText("BUMPER\nTap Ring");
            } else {
                btnBumper.setText(String.format("BUMPER %d/%d\n%.0f SP", bumpers.size, maxB, bumperCost()));
            }
        }

        // ---- Gravity Well / Tesla Coil button ----
        if (fh) {
            int maxTC = maxTeslaCoilsAllowed();
            if (maxTC == 0) {
                btnGravityWell.setText("SPIRAL\nReq: CP II");
            } else if (teslaCoils.size >= maxTC) {
                btnGravityWell.setText(String.format("SPIRAL\n%d/%d FULL", teslaCoils.size, maxTC));
            } else if (placementMode == PLACE_GRAVITY) {
                btnGravityWell.setText("SPIRAL\nTap Ring");
            } else {
                btnGravityWell.setText(String.format("SPIRAL %d/%d\n%.0f FS",
                    teslaCoils.size, maxTC, teslaCost()));
            }
        } else if (ember) {
            if (!emberCpII) {
                btnGravityWell.setText("RELAY\nReq: CP II");
            } else if (relayNodes.size >= maxRelayNodesNow()) {
                btnGravityWell.setText(String.format("RELAY\n%d/%d FULL", relayNodes.size, maxRelayNodesNow()));
            } else if (dragMode == PLACE_RELAY) {
                btnGravityWell.setText("RELAY\nDrag to Ring");
            } else {
                btnGravityWell.setText(String.format("RELAY %d/%d\n%.0f SP", relayNodes.size, maxRelayNodesNow(), relayCost()));
            }
        } else {
            if (!gravityUnlocked()) {
                btnGravityWell.setText("GRAVITY\nReq: CP II");
            } else if (attractors.size >= maxGravityAllowed()) {
                btnGravityWell.setText(String.format("GRAVITY\n%d/%d FULL", attractors.size, maxGravityAllowed()));
            } else if (placementMode == PLACE_GRAVITY) {
                btnGravityWell.setText("GRAVITY\nTap Ring");
            } else {
                btnGravityWell.setText(String.format("GRAVITY %d/%d\n%.0f SP",
                    attractors.size, maxGravityAllowed(), gravityCost()));
            }
        }

        if (isEmberIV() && btnGravCenter != null) {
            btnGravCenter.setVisible(emberPerk3);
            if (!emberGravityEnabled) {
                btnGravCenter.setStyle(gravOffStyle);
                btnGravCenter.setText("GRAVITY\nOFF");
            } else if (!emberGravityPush) {
                btnGravCenter.setStyle(gravPullStyle);
                btnGravCenter.setText("GRAVITY\nPULL");
            } else {
                btnGravCenter.setStyle(gravPushStyle);
                btnGravCenter.setText("GRAVITY\nPUSH");
            }
        }

        // ---- LAUNCH / progress button ----
        eDelta  = energyDeltaSinceLaunch();
        eCost   = nextCheckpointEnergyCost();
        boolean jumpReady      = eDelta >= eCost;
        boolean workforceGated = !isFullyUpgraded();
        String cpName = nextCPName();

        btnFlight.setText("LAUNCH");

        btnJumpReady.setVisible(false); // replaced by launch button under energy bar

        // ---- Button tints ----
        // ADD ORB: buyable when purchasable, locked when capped
        btnAdd.setStyle(
            balls.size >= cap ? LOCK :
            orbCanBuy         ? CORAL : NORM);

        if (fh) {
            int maxCV = maxIcicleNodesAllowed();
            btnBumper.setStyle(placementMode == PLACE_BUMPER        ? ACT
                : maxCV == 0 || icicleNodes.size >= maxCV           ? LOCK
                : sd.crystals >= icicileCost()                      ? BUY : NORM);
            int maxTC = maxTeslaCoilsAllowed();
            btnGravityWell.setStyle(placementMode == PLACE_GRAVITY  ? ACT
                : teslaCoils.size >= maxTC        ? LOCK
                : sd.crystals >= teslaCost()      ? BUY : NORM);
        } else if (ember) {
            btnBumper.setStyle(!emberCpI || portalPairs.size >= maxPortalPairsNow() ? LOCK
                : dragMode == PLACE_PORTAL ? ACT
                : sd.crystals >= portalCost() ? BUY : NORM);
            btnGravityWell.setStyle(!emberCpII || relayNodes.size >= maxRelayNodesNow() ? LOCK
                : dragMode == PLACE_RELAY ? ACT
                : sd.crystals >= relayCost() ? BUY : NORM);
        } else {
            int maxB = maxBumpersAllowed();
            btnBumper.setStyle(placementMode == PLACE_BUMPER             ? ACT
                : maxB == 0 || bumpers.size >= maxB ? LOCK
                : sd.crystals >= bumperCost()       ? BUY : NORM);
            btnGravityWell.setStyle(placementMode == PLACE_GRAVITY ? ACT
                : !gravityUnlocked() || attractors.size >= maxGravityAllowed() ? LOCK
                : sd.crystals >= gravityCost()      ? BUY : NORM);
        }
        // Launch button appears below energy bar only when fully ready
        boolean launchReady = jumpReady && !workforceGated;
        if (launchReady != launchWasReady) {
            launchWasReady = launchReady;
            btnFlight.setTouchable(launchReady ? Touchable.enabled : Touchable.disabled);
            launchBtnCell.height(launchReady ? 130f : 0f)
                         .padTop(launchReady ? 4f : 0f)
                         .padBottom(launchReady ? 4f : 0f);
            rootTable.invalidateHierarchy();
        }
        // Keep button fully transparent — rocket drawn manually in render loop
        btnFlight.setVisible(true);
        btnFlight.setColor(0f, 0f, 0f, 0f);
        btnFlight.setDisabled(false);

        // Re-apply font scale each frame (setText resets it)
        btnFlight.getLabel().setFontScale(0.90f);
        for (TextButton btn : new TextButton[]{btnAdd, btnBumper, btnGravityWell}) {
            btn.getLabel().setFontScale(0.80f);
        }

        // ---- Top panel: live label updates each frame ----
        // Column 1 — Region Log
        topPlanetLabel.setText(ember ? "NOVA TERRA" : fh ? "FROSTHEIM" : "SOLARA");

        int lsr = sd.sectorReached;
        String statusText = lsr < 0  ? "Status: Pre-Checkpoint"      :
                            lsr == 0 ? "Status: Checkpoint I Reached" :
                            lsr == 1 ? "Status: Checkpoint II Reached":
                            lsr == 2 ? "Status: Checkpoint III Reached": "Status: Arrived!";
        topCheckpointLabel.setText(statusText);

        String gravEnvStr;
        if (fh)          gravEnvStr = "Gravity: 0.4G Cryo-Field";
        else if (ember)  gravEnvStr = "Gravity: 1.6G Hyper-Gravity";
        else             gravEnvStr = "Gravity: 1.0G Standard";
        topGravLabel.setText(gravEnvStr);

        float topCollVal = 20f * sd.collisionEnergyMult;
        if (fh) {
            topYieldsLabel.setText(String.format("Base Yields: Icicle Split | Tesla %.0f J/orb", teslaHarvestRate));
        } else {
            topYieldsLabel.setText(String.format("Base Yields: Collision %.0f %s | Wall 0.5 %s",
                topCollVal, sparkSym, sparkSym));
        }

        // Column 2 — Money Engine: SPACE POINTS RATE header + live SP/s value
        topSpRateHeaderLabel.setText("ENERGY");
        // Planet timer
        if (planetTimerLabel != null) {
            long elapsed = sd.planetStartTimestampMs > 0L
                ? System.currentTimeMillis() - sd.planetStartTimestampMs : 0L;
            planetTimerLabel.setText(ShipData.formatDuration(elapsed));
        }
        float _eDelta = energyDeltaSinceLaunch();
        float _eCost  = nextCheckpointEnergyCost();
        String _eDeltaStr = _eDelta >= 1_000f ? String.format("%.1fK", _eDelta / 1000f) : String.format("%.0f", _eDelta);
        String _eCostStr  = _eCost  >= 1_000f ? String.format("%.0fK", _eCost  / 1000f) : String.format("%.0f", _eCost);
        topSpValueLabel.setText(_eDeltaStr + " / " + _eCostStr);
        // outputLabel hidden — E/s removed

        // Column 3 — Active Perks: rebuild only when unlock count changes
        int unlockedCount = 0;
        StringBuilder perkList = new StringBuilder();
        for (int i = 0; i < milestoneAchieved.length; i++) {
            if (i == 4 || !milestoneAchieved[i]) continue;
            unlockedCount++;
            if (perkList.length() > 0) perkList.append("\n");
            perkList.append(MILESTONE_NAMES[i]);
        }
        if (unlockedCount != lastUnlockedPerkCount) {
            topPerksListLabel.setText(perkList.length() > 0 ? perkList.toString() : "> No Perks Active");
            lastUnlockedPerkCount = unlockedCount;
        }

        ui.draw();
        drawTopBarIcons();

        // Draw launch rocket when ready (replaces transparent button)
        boolean launchReadyNow = launchWasReady; // already computed above in updateLabels
        if (launchReadyNow) {
            batch.begin();
            drawLaunchRocket();
            batch.end();
        }

        // Only one overlay at a time — priority: notif > celeb > launchHint > bumperHint > gravityHint > tutorial
        boolean bigOverlayActive = notifActive || celebActive;

        if (!bigOverlayActive && tutorialStep < 6) {
            batch.begin();
            drawTutorial();
            batch.end();
        }

        // Launch hint: fires once when all conditions met, dismisses on tap
        boolean launchAllReady = isJumpReady() && isFullyUpgraded();
        if (!bigOverlayActive && launchAllReady && !launchHintShown && !isEmberIV() && !isFrostheim()) {
            batch.begin();
            drawLaunchReadyHint();
            batch.end();
            if (inputHit) launchHintShown = true;
        }

        // Perk icon hint: fires once after first perk unlocks, teaches tap-for-info
        boolean anyPerkUnlocked = milestoneAchieved[0] || milestoneAchieved[1] || milestoneAchieved[2]
                                || milestoneAchieved[3] || milestoneAchieved[4];
        if (!bigOverlayActive && anyPerkUnlocked && !perkIconHintShown && tutorialStep >= 6 && !isEmberIV() && !isFrostheim()) {
            batch.begin();
            drawPerkIconHint();
            batch.end();
            if (inputHit) perkIconHintShown = true;
        }

        // Bumper tutorial: first time bumper slot unlocks (CP I reached)
        boolean bumperAvail = !isFrostheim() && !isEmberIV() && maxBumpersAllowed() > 0;
        if (!bigOverlayActive && bumperAvail && !bumperHintShown && tutorialStep >= 6) {
            batch.begin();
            drawBumperHint();
            batch.end();
            if (inputHit) bumperHintShown = true;
        }

        // Gravity tutorial: first time gravity unlocks (CP II reached)
        boolean gravityAvail = !isFrostheim() && !isEmberIV() && gravityUnlocked();
        if (!bigOverlayActive && gravityAvail && !gravityHintShown && bumperHintShown && tutorialStep >= 6) {
            batch.begin();
            drawGravityHint();
            batch.end();
            if (inputHit) gravityHintShown = true;
        }

        if (internAddedTimer > 0) {
            batch.begin();
            drawInternAddedOverlay();
            batch.end();
        }

        if (notifActive) {
            if (inputHit) {
                notifActive = false;
                inputHit = false;
            } else {
                batch.begin();
                drawNotifOverlay();
                batch.end();
            }
        }

        if (celebActive) {
            if (inputHit && celebTimer >= CELEB_SLIDE) {
                celebActive = false;
                inputHit = false;
            } else {
                batch.begin();
                drawCelebOverlay();
                batch.end();
            }
        }

        // Perk info popup — triggered by tapping an unlocked perk icon, dismissed by tap
        if (activePerkPopup >= 0) {
            perkPopupTimer += delta;
            if (inputHit && perkPopupTimer > 0.25f) {
                activePerkPopup = -1;
            } else {
                batch.begin();
                drawPerkPopup(activePerkPopup);
                batch.end();
            }
        }
        if (activeFrostPerkPopup >= 0) {
            perkPopupTimer += delta;
            if (inputHit && perkPopupTimer > 0.25f) {
                activeFrostPerkPopup = -1;
            } else {
                batch.begin();
                drawFrostPerkPopup(activeFrostPerkPopup);
                batch.end();
            }
        }
    }

    private void drawPerkPopup(int idx) {
        float alpha = Math.min(perkPopupTimer / 0.15f, 1f);

        float cardW = 300f, cardH = 56f;
        float cardX = RENDER_W * 0.5f - cardW * 0.5f;
        // Draw just above the perk strip (~200px from bottom on 854-height layout)
        float cardY = 195f;

        // Shadow
        batch.setColor(0f, 0f, 0f, 0.50f * alpha);
        batch.draw(texPixel, cardX + 3f, cardY - 3f, cardW, cardH);
        // Background
        batch.setColor(0.05f, 0.06f, 0.18f, 0.95f * alpha);
        batch.draw(texPixel, cardX, cardY, cardW, cardH);
        // Top accent bar
        batch.setColor(0.25f, 0.85f, 1.00f, 0.80f * alpha);
        batch.draw(texPixel, cardX, cardY + cardH - 3f, cardW, 3f);
        batch.setColor(1f, 1f, 1f, 1f);

        // Title
        floatFont.getData().setScale(1.05f);
        floatFont.setColor(0.25f, 0.95f, 1.00f, alpha);
        String perkName = isEmberIV() ? EMBER_PERK_NAMES[idx] : MILESTONE_NAMES[idx];
        String perkDesc = isEmberIV() ? EMBER_PERK_DESCS[idx] : MILESTONE_DESCS[idx];
        floatLayout.setText(floatFont, perkName);
        floatFont.draw(batch, perkName,
            cardX + 14f, cardY + cardH - 10f);

        // Description
        floatFont.getData().setScale(0.82f);
        floatFont.setColor(0.85f, 0.88f, 0.95f, alpha);
        floatFont.draw(batch, perkDesc,
            cardX + 14f, cardY + cardH - 28f);

        floatFont.getData().setScale(1f);
        floatFont.setColor(Color.WHITE);
    }

    private void drawFrostPerkPopup(int slot) {
        float alpha = Math.min(perkPopupTimer / 0.15f, 1f);

        float cardW = 300f, cardH = 56f;
        float cardX = RENDER_W * 0.5f - cardW * 0.5f;
        float cardY = 195f;

        // Shadow
        batch.setColor(0f, 0f, 0f, 0.50f * alpha);
        batch.draw(texPixel, cardX + 3f, cardY - 3f, cardW, cardH);
        // Background
        batch.setColor(0.04f, 0.08f, 0.18f, 0.95f * alpha);
        batch.draw(texPixel, cardX, cardY, cardW, cardH);
        // Top accent bar — ice blue
        batch.setColor(0.35f, 0.75f, 1.00f, 0.85f * alpha);
        batch.draw(texPixel, cardX, cardY + cardH - 3f, cardW, 3f);
        batch.setColor(1f, 1f, 1f, 1f);

        // Title
        floatFont.getData().setScale(1.05f);
        floatFont.setColor(0.35f, 0.85f, 1.00f, alpha);
        floatFont.draw(batch, FROST_PERK_NAMES[slot], cardX + 14f, cardY + cardH - 10f);

        // Description
        floatFont.getData().setScale(0.82f);
        floatFont.setColor(0.80f, 0.90f, 1.00f, alpha);
        floatFont.draw(batch, FROST_PERK_DESCS[slot], cardX + 14f, cardY + cardH - 28f);

        floatFont.getData().setScale(1f);
        floatFont.setColor(Color.WHITE);
    }

    private void drawTutorial() {
        if (tutorialStep == 0) {
            drawTutorialIntroOverlay();
        } else if (tutorialStep == 1) {
            if (tutorialInternDragging || tutorialPostDropTimer >= 0f) return; // hide while dragging or waiting after drop
            drawFingerDragHint(87f, 90f, 200f, 380f);
            drawTutorialCalloutCard(
                "HIRE YOUR FIRST INTERN",
                "Hold the ORB button, drag into the ring!",
                "More orbs = faster ring = more Energy!",
                87f, 90f,   // ADD ORB center: X=10+3+147/2, Y=10+4+3+147/2
                true,
                false
            );
        } else if (tutorialStep == 2) {
            // Draw card — "SP" replaced with spaces; icon drawn inline below
            drawTutorialCalloutCard(
                "SPACE POINTS EARNED!",
                "Each bounce earns    — your main currency.",
                "Spend it on bumpers, gravity wells and more!",
                60f, 184f,
                false, false
            );
            // Inline SP coin icon: position it where the gap sits in the centered text
            final String PREFIX = "Each bounce earns  ";
            final String FULL   = "Each bounce earns    — your main currency.";
            float iconSz = 16f;
            floatFont.getData().setScale(1.18f);
            floatLayout.setText(floatFont, FULL);
            float startX = RENDER_W * 0.5f - floatLayout.width * 0.5f;
            floatLayout.setText(floatFont, PREFIX);
            float iconX = startX + floatLayout.width;
            floatFont.getData().setScale(1f);
            float cardBotY2 = CCY_PX + CENTRIFUGE_R * PPM + 20f;
            float line1Y = cardBotY2 + 160f - 60f;  // matches ty in drawTutorialCalloutCard
            batch.setColor(1f, 0.90f, 0.20f, 1f);
            batch.draw(texIconSP, iconX, line1Y - iconSz + 1f, iconSz, iconSz);
            batch.setColor(1f, 1f, 1f, 1f);
        } else if (tutorialStep == 3) {
            // Card sits inside the ring; arrow points up to the energy bar in the top panel
            float energyBarY = renderViewport.getWorldHeight() - 95f;
            float cardBot3   = CCY_PX - CENTRIFUGE_R * PPM * 0.5f - 80f; // center of ring, offset down
            drawTutorialCalloutCard(
                "ENERGY (E)",
                "Ring spin generates Energy.",
                "Energy fuels your LAUNCH to next sector!",
                240f, energyBarY,
                false,
                true,   // arrowFromTop: points up to energy bar
                false,
                cardBot3
            );
        } else if (tutorialStep == 4) {
            // Observation: ring speed — right side of stats strip
            drawTutorialCalloutCard(
                "RING SPEED",
                "More interns = faster ring = more Energy.",
                "Watch the r/s counter climb!",
                390f, 184f,
                false, false
            );
        }
    }

    private void drawTutorialIntroOverlay() {
        ShipData.PlanetProfile planet = ShipData.get().getCurrentPlanet();
        float extH  = renderViewport.getWorldHeight();
        float cx    = RENDER_W * 0.5f;
        float pulse = 0.55f + MathUtils.sin(animTime * 1.8f) * 0.35f;
        float rp    = 0.30f + MathUtils.sin(animTime * 3.6f) * 0.28f;

        // ── Full-screen dim ──────────────────────────────────────────────────
        batch.setColor(0f, 0f, 0f, 0.82f);
        batch.draw(texPixel, 0, 0, RENDER_W, extH);
        batch.setColor(1f, 1f, 1f, 1f);

        // ── Card geometry ────────────────────────────────────────────────────
        float cW = 444f, cH = 490f;
        float cX = cx - cW * 0.5f;
        float cY = extH * 0.5f - cH * 0.5f - 10f;

        // Outer glow layers
        for (int g = 6; g > 0; g--) {
            float ex = g * 4f;
            batch.setColor(0.08f, 0.45f, 1.00f, 0.018f * g * pulse);
            batch.draw(texPixel, cX - ex, cY - ex, cW + ex * 2f, cH + ex * 2f);
        }

        // Card background
        batch.setColor(0.025f, 0.042f, 0.140f, 0.97f);
        batch.draw(texPixel, cX, cY, cW, cH);

        // Inner highlight panel
        batch.setColor(0.055f, 0.095f, 0.240f, 0.50f);
        batch.draw(texPixel, cX + 6f, cY + 6f, cW - 12f, cH - 12f);

        // Top accent stripe
        batch.setColor(0.20f, 0.82f, 1.00f, 0.90f * pulse);
        batch.draw(texPixel, cX, cY + cH - 4f, cW, 4f);

        // Animated side edges (left + right thin glows)
        batch.setColor(0.18f, 0.65f, 1.00f, 0.45f * pulse);
        batch.draw(texPixel, cX,            cY, 2f, cH);
        batch.draw(texPixel, cX + cW - 2f,  cY, 2f, cH);
        batch.setColor(0.18f, 0.65f, 1.00f, 0.20f * pulse);
        batch.draw(texPixel, cX,            cY, cW, 2f);

        // ── Corner brackets ──────────────────────────────────────────────────
        float bl = 26f, bt = 3.5f;
        batch.setColor(0.28f, 0.95f, 1.00f, 0.95f);
        // top-left
        batch.draw(texPixel, cX,           cY + cH - bt, bl, bt);
        batch.draw(texPixel, cX,           cY + cH - bl, bt, bl);
        // top-right
        batch.draw(texPixel, cX + cW - bl, cY + cH - bt, bl, bt);
        batch.draw(texPixel, cX + cW - bt, cY + cH - bl, bt, bl);
        // bottom-left
        batch.draw(texPixel, cX,           cY, bl, bt);
        batch.draw(texPixel, cX,           cY, bt, bl);
        // bottom-right
        batch.draw(texPixel, cX + cW - bl, cY, bl, bt);
        batch.draw(texPixel, cX + cW - bt, cY, bt, bl);

        // ── Planet badge ─────────────────────────────────────────────────────
        float badgeW = 190f, badgeH = 26f;
        float badgeX = cx - badgeW * 0.5f;
        float badgeY = cY + cH - 54f;
        batch.setColor(0.14f, 0.28f, 0.52f, 0.90f);
        batch.draw(texPixel, badgeX, badgeY, badgeW, badgeH);
        batch.setColor(0.25f, 0.70f, 1.00f, 0.70f);
        batch.draw(texPixel, badgeX, badgeY, badgeW, 1.5f);
        batch.draw(texPixel, badgeX, badgeY + badgeH - 1.5f, badgeW, 1.5f);

        // ── Divider 1 (below title area) ─────────────────────────────────────
        float div1Y = cY + cH - 112f;
        batch.setColor(0.18f, 0.65f, 1.00f, 0.40f);
        batch.draw(texPixel, cX + 28f, div1Y, cW - 56f, 1.5f);
        // diamond
        float dia = 5f;
        batch.setColor(0.28f, 0.95f, 1.00f, 0.95f);
        batch.draw(texPixel, cx - dia, div1Y - dia * 0.5f + 0.75f, dia * 2f, dia);

        // ── Divider 2 (above CTA) ────────────────────────────────────────────
        float div2Y = cY + 58f;
        batch.setColor(0.18f, 0.65f, 1.00f, 0.35f);
        batch.draw(texPixel, cX + 28f, div2Y, cW - 56f, 1.5f);
        batch.setColor(0.28f, 0.95f, 1.00f, 0.95f);
        batch.draw(texPixel, cx - dia, div2Y - dia * 0.5f + 0.75f, dia * 2f, dia);

        // ── Floating particles ───────────────────────────────────────────────
        for (int i = 0; i < 6; i++) {
            float px = cX + 20f + (i * 73f + MathUtils.sin(animTime * 0.4f + i) * 12f) % (cW - 40f);
            float py = cY + 70f + (MathUtils.sin(animTime * 0.55f + i * 1.1f) * 0.5f + 0.5f) * (cH - 140f);
            float pa = 0.10f + 0.08f * MathUtils.sin(animTime * 1.3f + i * 0.9f);
            batch.setColor(0.30f, 0.80f, 1.00f, pa);
            batch.draw(texPixel, px, py, 2.5f, 2.5f);
        }
        batch.setColor(1f, 1f, 1f, 1f);

        // ── TEXT ─────────────────────────────────────────────────────────────
        // Title
        floatFont.getData().setScale(2.30f);
        floatFont.setColor(0.22f, 0.90f, 1.00f, 1f);
        drawFontCentered("ENGINEERING BAY", cx, cY + cH - 16f);

        // Planet badge text
        floatFont.getData().setScale(1.18f);
        floatFont.setColor(0.75f, 0.90f, 1.00f, 0.95f);
        drawFontCentered(planet.name.toUpperCase() + " SYSTEM", cx, badgeY + badgeH - 6f);

        // Bullet rows
        float bx  = cX + 52f;
        float by  = cY + cH - 128f;
        float bLh = 58f;

        float[][] dotCols = {
            {0.28f, 0.88f, 1.00f},
            {0.25f, 1.00f, 0.55f},
            {1.00f, 0.82f, 0.20f},
            {1.00f, 0.50f, 0.12f},
        };
        String[][] bullets = {
            {"ORBS IN THE RING",       "Drag orbs in — they bounce and collide."},
            {"EARN SPACE POINTS",      "Every collision earns SP currency."},
            {"SPEND SP, GROW FASTER",  "Buy more orbs, bumpers, gravity wells."},
            {"ENERGY -> LAUNCH",       "Ring spin builds Energy for your next jump."},
        };

        floatFont.getData().setScale(1.22f);
        for (int i = 0; i < bullets.length; i++) {
            float rowY = by - i * bLh;
            float[] dc = dotCols[i];

            // Colored dot
            batch.setColor(dc[0], dc[1], dc[2], 0.92f);
            batch.draw(texPixel, cX + 18f, rowY - 10f, 12f, 12f);

            // Bullet title
            floatFont.setColor(dc[0], dc[1], dc[2], 1f);
            floatFont.draw(batch, bullets[i][0], bx, rowY);

            // Bullet sub
            floatFont.getData().setScale(1.00f);
            floatFont.setColor(0.74f, 0.78f, 0.90f, 0.88f);
            floatFont.draw(batch, bullets[i][1], bx, rowY - 24f);
            floatFont.getData().setScale(1.22f);
        }

        // CTA
        floatFont.getData().setScale(1.35f);
        floatFont.setColor(0.22f, 1.00f, 0.52f, 0.55f + MathUtils.sin(animTime * 3.2f) * 0.45f);
        drawFontCentered(">> TAP ANYWHERE TO BEGIN <<", cx, cY + 32f);

        floatFont.getData().setScale(1f);
        floatFont.setColor(Color.WHITE);
    }

    /**
     * Floating callout card with an animated arrow pointing at a target element.
     *
     * @param isAction    true  = waiting for an explicit action (button-sized highlight, no skip hint)
     *                    false = observation step (label-sized highlight, "tap to continue")
     * @param arrowFromTop true = arrow leaves from the card's TOP edge (target is above the card)
     *                     false = arrow leaves from the card's BOTTOM edge (target is below the card)
     */

    /** Draws the glowing rocket widget that replaces the transparent launch button. */
    private void drawLaunchRocket() {
        float extH  = renderViewport.getWorldHeight();
        float cx    = RENDER_W * 0.5f;
        float pulse = 0.5f + 0.5f * MathUtils.sin(animTime * 4f);
        float pulse2 = 0.5f + 0.5f * MathUtils.sin(animTime * 2.5f + 1.2f);

        // Rocket cell center: top of screen minus top sections; cell height 90f
        float rocketCY = extH - 193f;
        float rocketSz = 72f;

        // Expanding energy rings behind rocket
        for (int ri = 0; ri < 3; ri++) {
            float phase  = (animTime * 0.9f + ri * 0.33f) % 1f;
            float ringR  = 36f + phase * 60f;
            float alpha  = (1f - phase) * 0.35f;
            float sz     = ringR * 2f;
            batch.setColor(0.10f, 0.95f, 0.30f, alpha);
            batch.draw(texPixel, cx - ringR, rocketCY - ringR, sz, sz);
        }

        // Soft glow halo
        float halo = 52f + 10f * pulse2;
        batch.setColor(0.10f, 0.90f, 0.35f, 0.18f * pulse);
        batch.draw(texPixel, cx - halo, rocketCY - halo, halo * 2f, halo * 2f);

        // Engine flame trail below rocket (3 layered rects)
        float flameW = rocketSz * 0.28f;
        float flameH = 20f + 12f * pulse;
        batch.setColor(1.0f, 0.55f + 0.35f * pulse, 0.10f, 0.80f);
        batch.draw(texPixel, cx - flameW * 0.5f, rocketCY - rocketSz * 0.5f - flameH * 0.5f, flameW, flameH);
        batch.setColor(1.0f, 0.85f, 0.30f, 0.55f);
        batch.draw(texPixel, cx - flameW * 0.3f, rocketCY - rocketSz * 0.5f - flameH * 0.3f, flameW * 0.6f, flameH * 0.6f);
        batch.setColor(1f, 1f, 0.9f, 0.70f);
        batch.draw(texPixel, cx - flameW * 0.15f, rocketCY - rocketSz * 0.5f - flameH * 0.15f, flameW * 0.3f, flameH * 0.3f);

        // Rocket sprite (flipped so nose points up)
        TextureRegion rocketReg = new TextureRegion(texRocket);
        rocketReg.flip(false, true);
        float tint = 0.75f + 0.25f * pulse;
        batch.setColor(tint, 1f, tint, 1f);
        batch.draw(rocketReg, cx - rocketSz * 0.5f, rocketCY - rocketSz * 0.5f, rocketSz, rocketSz);
        batch.setColor(1f, 1f, 1f, 1f);

        // "TAP TO LAUNCH" label below rocket
        float labelY = rocketCY - rocketSz * 0.5f - 18f;
        floatFont.getData().setScale(0.90f);
        floatFont.setColor(0.20f, 1f, 0.45f, 0.60f + 0.35f * pulse);
        drawFontCentered("TAP TO LAUNCH", cx, labelY);
        floatFont.getData().setScale(1f);
        floatFont.setColor(1f, 1f, 1f, 1f);
    }

    // Launch hint overlay — fires once, dismisses on tap; card positioned in lower screen
    private void drawLaunchReadyHint() {
        float extH  = renderViewport.getWorldHeight();
        float cx    = RENDER_W * 0.5f;
        float pulse = 0.5f + 0.5f * MathUtils.sin(animTime * 3.5f);

        // Full screen dim
        batch.setColor(0f, 0f, 0f, 0.72f);
        batch.draw(texPixel, 0, 0, RENDER_W, extH);
        batch.setColor(1f, 1f, 1f, 1f);

        // Rocket glow highlight (same position as drawLaunchRocket)
        float rocketCY = extH - 193f;
        float halo     = 58f;
        batch.setColor(0.10f, 0.95f, 0.30f, 0.35f * pulse);
        batch.draw(texPixel, cx - halo, rocketCY - halo, halo * 2f, halo * 2f);
        TextureRegion rocketReg2 = new TextureRegion(texRocket);
        rocketReg2.flip(false, true);
        batch.setColor(0.70f + 0.30f * pulse, 1f, 0.70f + 0.30f * pulse, 1f);
        batch.draw(rocketReg2, cx - 36f, rocketCY - 36f, 72f, 72f);
        batch.setColor(1f, 1f, 1f, 1f);

        // Card in lower third of screen
        float cardW    = 420f;
        float cardH    = 150f;
        float cardX    = cx - cardW * 0.5f;
        float cardBotY = 240f; // fixed lower position

        // Arrow from card top up to rocket
        drawTutorialArrowGreen(cx, cardBotY + cardH, cx, rocketCY - 36f);

        // Card shadow + background
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(texPixel, cardX + 3f, cardBotY - 3f, cardW, cardH);
        batch.setColor(0.04f, 0.10f, 0.06f, 0.96f);
        batch.draw(texPixel, cardX, cardBotY, cardW, cardH);
        // Green border
        float bdr = 1.8f;
        batch.setColor(0.20f, 0.90f, 0.35f, 0.85f);
        batch.draw(texPixel, cardX,               cardBotY + cardH - bdr, cardW, bdr);
        batch.draw(texPixel, cardX,               cardBotY,               cardW, bdr);
        batch.draw(texPixel, cardX,               cardBotY,               bdr,   cardH);
        batch.draw(texPixel, cardX + cardW - bdr, cardBotY,               bdr,   cardH);
        batch.setColor(1f, 1f, 1f, 1f);

        float ty = cardBotY + cardH - 26f;
        floatFont.getData().setScale(1.55f);
        floatFont.setColor(0.20f, 1f, 0.45f, 0.80f + 0.20f * pulse);
        drawFontCentered("READY TO LAUNCH!", cx, ty);

        batch.setColor(0.20f, 0.90f, 0.35f, 0.28f);
        batch.draw(texPixel, cardX + 14f, ty - 10f, cardW - 28f, 1.5f);
        batch.setColor(1f, 1f, 1f, 1f);

        ty -= 34f;
        floatFont.getData().setScale(1.12f);
        floatFont.setColor(1f, 0.92f, 0.55f, 0.95f);
        drawFontCentered("All systems go — tap the rocket to launch!", cx, ty);
        ty -= 26f;
        floatFont.getData().setScale(0.85f);
        floatFont.setColor(0.50f, 0.55f, 0.72f, 0.55f + 0.28f * pulse);
        drawFontCentered("tap anywhere to dismiss", cx, ty);
        floatFont.getData().setScale(1f);
        floatFont.setColor(1f, 1f, 1f, 1f);
    }

    private void drawPerkIconHint() {
        // Speed Keep icon: leftmost slot, X≈42px, icon center Y≈234px
        drawTutorialCalloutCard(
            "PERK UNLOCKED!",
            "Tap any glowing icon to see what it does.",
            "Perks unlock as your ring speed climbs.",
            61f, 234f,
            false, false, true
        );
    }

    private void drawBumperHint() {
        drawFingerDragHint(240f, 90f, 255f, 370f);
        drawTutorialCalloutCard(
            "DEPLOY A BUMPER",
            "Bumpers bounce interns for big SP bursts!",
            "Hold BUMPER, drag it into the ring!",
            240f, 90f,
            true, false
        );
    }

    private void drawGravityHint() {
        drawFingerDragHint(387f, 90f, 295f, 370f);
        drawTutorialCalloutCard(
            "GRAVITY WELL UNLOCKED",
            "Gravity wells pull interns — more collisions!",
            "Hold GRAVITY, drag it into the ring!",
            393f, 90f,
            true, false
        );
    }

    /**
     * Animated finger-drag hint — draws a looping finger moving from (startX,startY)
     * to (endX,endY), with a dotted path trail and a green press-ripple at the start.
     * Call inside a batch.begin()…end() block.
     */
    private void drawFingerDragHint(float startX, float startY, float endX, float endY) {
        final float PERIOD = 2.4f;
        float t = (animTime % PERIOD) / PERIOD;

        // --- Compute finger position / alpha ---
        float fingerAlpha, fingerX, fingerY;
        if (t < 0.16f) {
            fingerAlpha = t / 0.16f;
            fingerX = startX;
            fingerY = startY;
        } else if (t < 0.80f) {
            float p = (t - 0.16f) / 0.64f;
            p = p * p * (3f - 2f * p);   // smoothstep ease
            fingerAlpha = 1f;
            fingerX = startX + (endX - startX) * p;
            fingerY = startY + (endY - startY) * p;
        } else {
            float p = (t - 0.80f) / 0.20f;
            fingerAlpha = 1f - p;
            fingerX = endX;
            fingerY = endY;
        }

        float progressT = t < 0.16f ? 0f : t < 0.80f ? (t - 0.16f) / 0.64f : 1f;
        float dx = endX - startX, dy = endY - startY;

        // --- Dotted path (show unvisited portion only) ---
        float pathLen = (float) Math.sqrt(dx * dx + dy * dy);
        int dotCount = Math.max(5, (int)(pathLen / 20f));
        for (int i = 0; i <= dotCount; i++) {
            float pt = (float)i / dotCount;
            if (pt < progressT - 0.05f) continue;   // hide already-passed dots
            float dotA = 0.32f + 0.16f * MathUtils.sin(animTime * 5f - pt * 11f);
            dotA = Math.max(0f, dotA) * fingerAlpha * (0.5f + 0.5f * pt);
            if (dotA < 0.02f) continue;
            float dotSz = 3.5f + 1.5f * (float)Math.sin(Math.PI * pt);
            batch.setColor(0.55f, 0.82f, 1f, dotA);
            batch.draw(texPixel,
                startX + dx * pt - dotSz * 0.5f,
                startY + dy * pt - dotSz * 0.5f,
                dotSz, dotSz);
        }

        // --- Press-ripple ring at start (first 22% of cycle) ---
        if (t < 0.22f) {
            float rt = t / 0.22f;
            float ripR = 15f + rt * 30f;
            float ripA = (1f - rt) * 0.88f;
            final int SEGS = 30;
            for (int i = 0; i < SEGS; i++) {
                float angle = (float)(i * Math.PI * 2.0 / SEGS);
                float rx = startX + (float)Math.cos(angle) * ripR;
                float ry = startY + (float)Math.sin(angle) * ripR;
                batch.setColor(0.25f, 1f, 0.50f, ripA);
                batch.draw(texPixel, rx - 2.8f, ry - 2.8f, 5.6f, 5.6f);
            }
        }

        // --- Hand sprite (asset: ui/hand_drag.png) ---
        // fingerX/fingerY = fingertip; sprite is 128×192, fingertip at top-center
        final float SPR_W = 80f;
        final float SPR_H = 120f;  // maintain 128:192 = 2:3 ratio
        // fingertip sits at top-center of sprite → anchor at (SPR_W*0.5, SPR_H)
        batch.setColor(1f, 1f, 1f, fingerAlpha);
        batch.draw(texHandDrag,
            fingerX - SPR_W * 0.5f, fingerY - SPR_H,
            SPR_W, SPR_H);

        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Green-tinted arrow variant for the launch ready hint */
    private void drawTutorialArrowGreen(float x1, float y1, float x2, float y2) {
        float dx  = x2 - x1;
        float dy  = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 2f) return;
        float nx = dx / len;
        float ny = dy / len;

        int steps = Math.max(3, (int) (len / 11f));
        for (int i = 0; i <= steps; i++) {
            float t    = (float) i / steps;
            float wave = 0.5f + 0.5f * MathUtils.sin(animTime * 7f - t * 9f);
            float fade = (0.30f + 0.70f * t) * wave;
            float sz   = 3.5f + t * 5.5f;
            float px   = x1 + t * dx;
            float py   = y1 + t * dy;
            batch.setColor(0.15f, 1f, 0.40f, fade);
            batch.draw(texPixel, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
        }
        float headBack = 14f, headSpread = 9f;
        float hbx = x2 - nx * headBack, hby = y2 - ny * headBack;
        float px2 = -ny, py2 = nx;
        batch.setColor(0.15f, 1f, 0.40f, 0.88f);
        float ts = 5.5f;
        batch.draw(texPixel, x2 - ts * 0.5f,                     y2 - ts * 0.5f,                     ts, ts);
        batch.draw(texPixel, hbx + px2 * headSpread - ts * 0.5f, hby + py2 * headSpread - ts * 0.5f, ts, ts);
        batch.draw(texPixel, hbx - px2 * headSpread - ts * 0.5f, hby - py2 * headSpread - ts * 0.5f, ts, ts);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawTutorialCalloutCard(String title, String line1, String line2,
                                          float tipX, float tipY,
                                          boolean isAction, boolean arrowFromTop) {
        drawTutorialCalloutCard(title, line1, line2, tipX, tipY, isAction, arrowFromTop, false, -1f);
    }
    private void drawTutorialCalloutCard(String title, String line1, String line2,
                                          float tipX, float tipY,
                                          boolean isAction, boolean arrowFromTop, boolean isIcon) {
        drawTutorialCalloutCard(title, line1, line2, tipX, tipY, isAction, arrowFromTop, isIcon, -1f);
    }
    private void drawTutorialCalloutCard(String title, String line1, String line2,
                                          float tipX, float tipY,
                                          boolean isAction, boolean arrowFromTop, boolean isIcon,
                                          float cardBotYOverride) {
        float cx       = RENDER_W * 0.5f;
        float cardW    = 420f;
        float cardH    = 160f;
        float cardX    = cx - cardW * 0.5f;
        float cardBotY = cardBotYOverride > 0f
                ? cardBotYOverride
                : CCY_PX + CENTRIFUGE_R * PPM + 20f;

        // Dark vignette over the lower portion of the screen (centrifuge + stats/buttons)
        batch.setColor(0f, 0f, 0f, 0.50f);
        batch.draw(texPixel, 0, 0, RENDER_W, cardBotY);
        batch.setColor(1f, 1f, 1f, 1f);

        // Arrow from card edge to target
        float arrowOriginX = cx;
        float arrowOriginY = arrowFromTop ? (cardBotY + cardH) : cardBotY;
        drawTutorialArrow(arrowOriginX, arrowOriginY, tipX, tipY);

        // Target highlight
        float pulse = 0.45f + 0.45f * MathUtils.sin(animTime * 5.2f);
        if (isAction) {
            // Full button-sized pulsing fill + border
            float btnHalf = (RENDER_W - 20f - 18f) / 6f; // 3-button layout: btnSz/2 ≈ 73.7px
            batch.setColor(0.18f, 0.92f, 1f, pulse * 0.50f);
            batch.draw(texPixel, tipX - btnHalf, tipY - btnHalf, btnHalf * 2f, btnHalf * 2f);
            float bw = 2.5f;
            batch.setColor(0.18f, 0.92f, 1f, pulse * 0.88f);
            batch.draw(texPixel, tipX - btnHalf,        tipY + btnHalf - bw, btnHalf * 2f, bw);
            batch.draw(texPixel, tipX - btnHalf,        tipY - btnHalf,      btnHalf * 2f, bw);
            batch.draw(texPixel, tipX - btnHalf,        tipY - btnHalf,      bw, btnHalf * 2f);
            batch.draw(texPixel, tipX + btnHalf - bw,   tipY - btnHalf,      bw, btnHalf * 2f);
        } else if (isIcon) {
            // Small square glow for icon-sized targets (26×26)
            float hw = 14f, hh = 14f;
            batch.setColor(0.20f, 1.00f, 0.55f, pulse * 0.45f);
            batch.draw(texPixel, tipX - hw, tipY - hh, hw * 2f, hh * 2f);
            float bw = 2f;
            batch.setColor(0.20f, 1.00f, 0.55f, pulse * 0.90f);
            batch.draw(texPixel, tipX - hw,        tipY + hh - bw, hw * 2f, bw);
            batch.draw(texPixel, tipX - hw,        tipY - hh,      hw * 2f, bw);
            batch.draw(texPixel, tipX - hw,        tipY - hh,      bw, hh * 2f);
            batch.draw(texPixel, tipX + hw - bw,   tipY - hh,      bw, hh * 2f);
        } else {
            // Compact label-area underline glow
            float hw = 70f, hh = 14f;
            batch.setColor(1f, 0.82f, 0.20f, pulse * 0.50f);
            batch.draw(texPixel, tipX - hw, tipY - hh, hw * 2f, hh * 2f);
            float bw = 2f;
            batch.setColor(1f, 0.82f, 0.20f, pulse * 0.85f);
            batch.draw(texPixel, tipX - hw, tipY - hh,        hw * 2f, bw);
            batch.draw(texPixel, tipX - hw, tipY + hh - bw,   hw * 2f, bw);
        }
        batch.setColor(1f, 1f, 1f, 1f);

        // Card shadow
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(texPixel, cardX + 3f, cardBotY - 3f, cardW, cardH);
        // Card background
        batch.setColor(0.05f, 0.07f, 0.16f, 0.96f);
        batch.draw(texPixel, cardX, cardBotY, cardW, cardH);
        // Card border — cyan for action, amber for observation
        float bdr = 1.5f;
        if (isAction) {
            batch.setColor(0.22f, 0.68f, 1f, 0.82f);
        } else {
            batch.setColor(1f, 0.75f, 0.18f, 0.80f);
        }
        batch.draw(texPixel, cardX,               cardBotY + cardH - bdr, cardW, bdr);
        batch.draw(texPixel, cardX,               cardBotY,               cardW, bdr);
        batch.draw(texPixel, cardX,               cardBotY,               bdr,   cardH);
        batch.draw(texPixel, cardX + cardW - bdr, cardBotY,               bdr,   cardH);
        batch.setColor(1f, 1f, 1f, 1f);

        // Title
        float ty = cardBotY + cardH - 26f;
        floatFont.getData().setScale(1.55f);
        if (isAction) {
            floatFont.setColor(0.20f, 0.90f, 1f, 1f);
        } else {
            floatFont.setColor(1f, 0.80f, 0.22f, 1f);
        }
        drawFontCentered(title, cx, ty);

        // Divider
        batch.setColor(isAction ? 0.22f : 1f,
                       isAction ? 0.68f : 0.75f,
                       isAction ? 1f    : 0.18f,
                       0.28f);
        batch.draw(texPixel, cardX + 14f, ty - 10f, cardW - 28f, 1.5f);
        batch.setColor(1f, 1f, 1f, 1f);

        // Body lines
        ty -= 34f;
        floatFont.getData().setScale(1.18f);
        floatFont.setColor(1f, 0.88f, 0.45f, 0.95f);
        drawFontCentered(line1, cx, ty);
        ty -= 28f;
        floatFont.setColor(0.78f, 0.85f, 0.98f, 0.88f);
        drawFontCentered(line2, cx, ty);

        // Footer: waiting message (action) or tap-to-continue (observation)
        ty -= 30f;
        float fp = 0.38f + 0.28f * MathUtils.sin(animTime * 2.6f);
        floatFont.getData().setScale(0.88f);
        if (isAction) {
            floatFont.setColor(0.48f, 0.90f, 0.48f, fp);
            drawFontCentered("waiting for you to tap ADD ORB ...", cx, ty);
        } else {
            floatFont.setColor(0.50f, 0.55f, 0.65f, fp);
            drawFontCentered("tap anywhere to continue  /  SKIP", cx, ty);
        }

        floatFont.getData().setScale(1f);
        floatFont.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Draws an animated dashed-line arrow from (x1,y1) to (x2,y2) in render-space.
     * Dots travel toward the target; arrowhead drawn at the tip.
     */
    private void drawTutorialArrow(float x1, float y1, float x2, float y2) {
        float dx  = x2 - x1;
        float dy  = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 2f) return;
        float nx = dx / len;
        float ny = dy / len;

        int steps = Math.max(3, (int) (len / 11f));
        for (int i = 0; i <= steps; i++) {
            float t    = (float) i / steps;
            float wave = 0.5f + 0.5f * MathUtils.sin(animTime * 7f - t * 9f);
            float fade = (0.30f + 0.70f * t) * wave;
            float sz   = 3.5f + t * 5.5f;
            float px   = x1 + t * dx;
            float py   = y1 + t * dy;
            batch.setColor(0.18f, 0.92f, 1f, fade);
            batch.draw(texPixel, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
        }

        // Arrowhead: tip point + two flanking squares
        float headBack   = 14f;
        float headSpread = 9f;
        float hbx = x2 - nx * headBack;
        float hby = y2 - ny * headBack;
        float px2 = -ny;
        float py2 =  nx;
        batch.setColor(0.18f, 0.92f, 1f, 0.88f);
        float ts = 5.5f;
        batch.draw(texPixel, x2 - ts * 0.5f,                        y2 - ts * 0.5f,                        ts, ts);
        batch.draw(texPixel, hbx + px2 * headSpread - ts * 0.5f,    hby + py2 * headSpread - ts * 0.5f,    ts, ts);
        batch.draw(texPixel, hbx - px2 * headSpread - ts * 0.5f,    hby - py2 * headSpread - ts * 0.5f,    ts, ts);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawFontCentered(String text, float cx, float y) {
        floatLayout.setText(floatFont, text);
        floatFont.draw(batch, text, cx - floatLayout.width * 0.5f, y);
    }

    /** Draws ShapeRenderer heart and diamond icons next to the lives/gems labels in the top bar.
     *  Also draws 5 greyed hearts in the livesBlockTable overlay when it is visible.
     *  Called after ui.draw() so that Table layout has been fully resolved. */
    private void drawTopBarIcons() {
        if (livesLabel == null || diamondsLabel == null) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(ui.getViewport().getCamera().combined);

        // ── Heart icon (lives label) ──────────────────────────────────────────
        Vector2 lp = livesLabel.localToStageCoordinates(new Vector2(0f, livesLabel.getHeight() * 0.5f));
        float hx = lp.x - 14f, hy = lp.y, hhr = 5.5f;
        ShipData sdI = ShipData.get();
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        shapeR.setColor(sdI.lives > 0 ? new Color(1f, 0.28f, 0.40f, 0.95f)
                                      : new Color(1f, 0.20f, 0.20f, 0.75f));
        shapeR.circle(hx - hhr * 0.65f, hy + hhr * 0.25f, hhr * 0.72f, 10);
        shapeR.circle(hx + hhr * 0.65f, hy + hhr * 0.25f, hhr * 0.72f, 10);
        shapeR.triangle(hx - hhr * 1.30f, hy + hhr * 0.25f,
                        hx + hhr * 1.30f, hy + hhr * 0.25f,
                        hx,               hy - hhr * 1.20f);
        shapeR.end();

        // ── Diamond icon (gems label) ─────────────────────────────────────────
        Vector2 dp = diamondsLabel.localToStageCoordinates(new Vector2(0f, diamondsLabel.getHeight() * 0.5f));
        float gx = dp.x - 14f, gy = dp.y, gs = 5.5f;
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        shapeR.setColor(0.38f, 0.92f, 1.00f, 0.92f);
        shapeR.triangle(gx, gy + gs, gx + gs, gy, gx, gy - gs);
        shapeR.triangle(gx, gy + gs, gx - gs, gy, gx, gy - gs);
        shapeR.end();
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(0.72f, 1.00f, 1.00f, 0.78f);
        shapeR.triangle(gx, gy + gs, gx + gs, gy, gx, gy - gs);
        shapeR.triangle(gx, gy + gs, gx - gs, gy, gx, gy - gs);
        shapeR.end();

        // ── 5 grey hearts in lives-out overlay ───────────────────────────────
        if (livesBlockTable != null && livesBlockTable.isVisible() && greyHeartsLabel != null) {
            Vector2 ghp = greyHeartsLabel.localToStageCoordinates(
                    new Vector2(greyHeartsLabel.getWidth() * 0.5f, greyHeartsLabel.getHeight() * 0.5f));
            float ghhr = 8f, spacing = 26f;
            float startX = ghp.x - spacing * 2f;
            shapeR.begin(ShapeRenderer.ShapeType.Filled);
            shapeR.setColor(0.35f, 0.35f, 0.38f, 0.80f);
            for (int i = 0; i < 5; i++) {
                float cx = startX + i * spacing, cy = ghp.y;
                shapeR.circle(cx - ghhr * 0.65f, cy + ghhr * 0.25f, ghhr * 0.72f, 10);
                shapeR.circle(cx + ghhr * 0.65f, cy + ghhr * 0.25f, ghhr * 0.72f, 10);
                shapeR.triangle(cx - ghhr * 1.30f, cy + ghhr * 0.25f,
                                cx + ghhr * 1.30f, cy + ghhr * 0.25f,
                                cx,                cy - ghhr * 1.20f);
            }
            shapeR.end();
        }
    }

    /** Draws a gold Space Points coin (circle + 5-point star) via shapeR. Call outside batch begin/end. */
    private void drawSpCoinIcon(float cx, float cy, float r) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(renderViewport.getCamera().combined);
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        // Outer glow
        shapeR.setColor(1f, 0.78f, 0.05f, 0.18f);
        shapeR.circle(cx, cy, r * 1.50f, 24);
        // Coin body
        shapeR.setColor(1f, 0.80f, 0.10f, 1f);
        shapeR.circle(cx, cy, r, 28);
        // Inner highlight (upper-left)
        shapeR.setColor(1f, 0.98f, 0.72f, 0.48f);
        shapeR.circle(cx - r * 0.20f, cy + r * 0.22f, r * 0.50f, 16);
        // 5-pointed star
        shapeR.setColor(0.55f, 0.26f, 0.01f, 0.85f);
        float outerS = r * 0.52f, innerS = r * 0.22f;
        for (int i = 0; i < 5; i++) {
            float a1 = (float)(Math.PI / 2 + i       * 2 * Math.PI / 5);
            float a2 = (float)(Math.PI / 2 + (i+0.5) * 2 * Math.PI / 5);
            float a3 = (float)(Math.PI / 2 + (i+1)   * 2 * Math.PI / 5);
            float ox1 = cx + outerS * MathUtils.cos(a1), oy1 = cy + outerS * MathUtils.sin(a1);
            float ix  = cx + innerS * MathUtils.cos(a2), iy  = cy + innerS * MathUtils.sin(a2);
            float ox2 = cx + outerS * MathUtils.cos(a3), oy2 = cy + outerS * MathUtils.sin(a3);
            shapeR.triangle(cx, cy, ox1, oy1, ix, iy);
            shapeR.triangle(cx, cy, ix, iy, ox2, oy2);
        }
        shapeR.end();
        // Coin rim
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(1f, 0.95f, 0.55f, 0.80f);
        shapeR.circle(cx, cy, r, 28);
        shapeR.end();
    }

    private void drawHudBar() {
        float barY  = renderViewport.getWorldHeight() - 38f;
        float barX  = 80f;
        float barW  = RENDER_W - 160f;
        float barH  = 3f;

        batch.setColor(OdysseyTheme.PANEL_BORDER);
        batch.draw(texPixel, barX, barY, barW, barH);

        float fill = Math.min(1f, hudBarFill);
        if (fill > 0f) {
            batch.setColor(OdysseyTheme.ACCENT_E);
            batch.draw(texPixel, barX, barY, barW * fill, barH);
            batch.setColor(OdysseyTheme.TEXT_PRI);
            batch.draw(texPixel, barX + barW * fill - 1f, barY - 2f, 2f, barH + 4f);
        }
    }

    private void drawFloatNumbers() {
        for (int i = 0; i < activeFloats.size; i++) {
            FloatEntry fe = activeFloats.get(i);
            float t      = fe.age / FloatEntry.LIFETIME;
            float alpha  = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
            alpha = Math.max(0f, alpha);

            // World → screen
            float sx = fe.wx * PPM + fe.driftX * PPM * fe.age * 60f;
            float sy = fe.wy * PPM + fe.age * 55f;  // float upward ~55px/s

            // Color by source
            Color c = switch (fe.colorType) {
                case 0  -> OdysseyTheme.FLOAT_E;
                case 1  -> OdysseyTheme.FLOAT_SP;
                case 2  -> OdysseyTheme.FLOAT_SPECIAL;
                case 4  -> OdysseyTheme.FLOAT_HARVEST;
                case 5  -> OdysseyTheme.FLOAT_GEMS;
                default -> OdysseyTheme.FLOAT_BUMPER;
            };

            // Scale by magnitude: base at ≤20, max at ≥200
            float scale = 0.72f + Math.min(1f, fe.value / 200f) * 0.38f;

            floatFont.getData().setScale(scale);
            floatFont.setColor(c.r, c.g, c.b, alpha);
            String text = fe.colorType == 5
                ? "+" + (int) fe.value + "◆"
                : "+" + (int) fe.value;
            floatLayout.setText(floatFont, text);
            floatFont.draw(batch, text, sx - floatLayout.width * 0.5f, sy);
        }
        floatFont.getData().setScale(1f);
    }

    private void drawBackground() {
        // Draw background at its natural size — ring glow is baked in at RENDER_H
        batch.draw(texBackground, 0, 0, RENDER_W, RENDER_H);
        // Fill extended area above with space background color
        float extH = renderViewport.getWorldHeight();
        if (extH > RENDER_H) {
            batch.setColor(OdysseyTheme.SPACE_BG.r, OdysseyTheme.SPACE_BG.g, OdysseyTheme.SPACE_BG.b, 1f);
            batch.draw(texPixel, 0, RENDER_H, RENDER_W, extH - RENDER_H);
            batch.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawRectRing() {
        float bodyAng = centrifugeBody.getAngle(); // visual matches physics (body runs at 1/3 speed)
        float ca = MathUtils.cos(bodyAng), sa = MathUtils.sin(bodyAng);
        float speedT  = Math.min(Math.abs(centrifugeBody.getAngularVelocity()) / centrifugeRpmMax, 1f);
        float pulse   = 0.55f + 0.45f * MathUtils.sin(animTime * (2.5f + speedT * 8f));

        // Rectangle corners (world → pixel, rotated by body angle)
        float hw = RECT_HW * PPM, hh = RECT_HH * PPM;
        float thick = 18f; // ring thickness in pixels
        // Local corners of outer rect
        float[][] outerL = {{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
        float[][] innerL = {{-(hw-thick),-(hh-thick)},{(hw-thick),-(hh-thick)},{(hw-thick),(hh-thick)},{-(hw-thick),(hh-thick)}};

        // Rotate and translate to screen
        float[] ox = new float[4], oy = new float[4];
        float[] ix = new float[4], iy = new float[4];
        for (int i = 0; i < 4; i++) {
            ox[i] = CCX_PX + outerL[i][0]*ca - outerL[i][1]*sa;
            oy[i] = CCY_PX + outerL[i][0]*sa + outerL[i][1]*ca;
            ix[i] = CCX_PX + innerL[i][0]*ca - innerL[i][1]*sa;
            iy[i] = CCY_PX + innerL[i][0]*sa + innerL[i][1]*ca;
        }

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        // Outer glow (bloomed border)
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        for (int g = 6; g > 0; g--) {
            float ga = 0.030f * g * pulse * (0.4f + speedT * 0.6f);
            shapeR.setColor(0.65f, 0.15f, 1.00f, ga);
            float eg = g * 5f;
            float[][] gl2 = {{-(hw+eg),-(hh+eg)},{(hw+eg),-(hh+eg)},{(hw+eg),(hh+eg)},{-(hw+eg),(hh+eg)}};
            float[] gox = new float[4], goy = new float[4];
            for (int i = 0; i < 4; i++) {
                gox[i] = CCX_PX + gl2[i][0]*ca - gl2[i][1]*sa;
                goy[i] = CCY_PX + gl2[i][0]*sa + gl2[i][1]*ca;
            }
            // Fill as 2 triangles (fan from first corner)
            for (int i = 1; i < 3; i++) shapeR.triangle(gox[0],goy[0],gox[i],goy[i],gox[i+1],goy[i+1]);
        }
        shapeR.end();

        // Ring fill: 4 wall quads (each quad = outer edge i→i+1, inner edge i→i+1)
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        float bright = 0.15f + speedT * 0.30f;
        for (int i = 0; i < 4; i++) {
            int n = (i + 1) % 4;
            shapeR.setColor(0.40f, 0.08f, 0.75f+bright, 0.88f);
            shapeR.triangle(ox[i],oy[i], ox[n],oy[n], ix[i],iy[i]);
            shapeR.triangle(ox[n],oy[n], ix[n],iy[n], ix[i],iy[i]);
        }
        // Corner accent circles
        shapeR.setColor(0.85f, 0.55f, 1.00f, 0.95f);
        for (int i = 0; i < 4; i++) shapeR.circle(ox[i], oy[i], 6f, 10);
        shapeR.end();

        // Edge lines — outer and inner ring borders
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(0.90f, 0.65f, 1.00f, 0.90f + 0.10f * pulse);
        for (int i = 0; i < 4; i++) { int n=(i+1)%4; shapeR.line(ox[i],oy[i],ox[n],oy[n]); }
        shapeR.setColor(0.35f, 0.08f, 0.60f, 0.50f);
        for (int i = 0; i < 4; i++) { int n=(i+1)%4; shapeR.line(ix[i],iy[i],ix[n],iy[n]); }
        shapeR.end();

        // Second inner square — rotates with same body angle, shows playing field boundary
        float hw2 = hw - thick - 8f, hh2 = hh - thick - 8f;
        float[][] inner2L = {{-hw2,-hh2},{hw2,-hh2},{hw2,hh2},{-hw2,hh2}};
        float[] i2x = new float[4], i2y = new float[4];
        for (int i = 0; i < 4; i++) {
            i2x[i] = CCX_PX + inner2L[i][0]*ca - inner2L[i][1]*sa;
            i2y[i] = CCY_PX + inner2L[i][0]*sa + inner2L[i][1]*ca;
        }
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(0.75f, 0.35f, 1.00f, 0.70f + 0.20f * pulse);
        for (int i = 0; i < 4; i++) { int n=(i+1)%4; shapeR.line(i2x[i],i2y[i],i2x[n],i2y[n]); }
        shapeR.end();

        // Rotation indicators: tick marks along each edge at 25% and 75%
        // plus one BRIGHT corner (corner 0 = "leading corner") so spin is visible
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        // Bright leading corner — corner 0 gets a bigger white dot
        shapeR.setColor(1f, 1f, 0.6f, 0.98f);
        shapeR.circle(ox[0], oy[0], 9f, 12);
        shapeR.setColor(1f, 0.9f, 0.3f, 0.70f);
        shapeR.circle(ox[0], oy[0], 5f, 10);
        // Tick marks at ¼ and ¾ of each edge (perpendicular inward)
        shapeR.setColor(0.80f, 0.50f, 1.00f, 0.80f);
        for (int i = 0; i < 4; i++) {
            int n = (i + 1) % 4;
            for (float frac : new float[]{0.28f, 0.72f}) {
                float tx = ox[i] + (ox[n] - ox[i]) * frac;
                float ty = oy[i] + (oy[n] - oy[i]) * frac;
                // inward normal direction
                float edgeDx = ox[n]-ox[i], edgeDy = oy[n]-oy[i];
                float len = (float)Math.sqrt(edgeDx*edgeDx + edgeDy*edgeDy);
                float nx2 = -edgeDy/len, ny2 = edgeDx/len; // inward toward center
                float tickLen = thick * 0.9f;
                shapeR.rectLine(tx, ty, tx + nx2*tickLen, ty + ny2*tickLen, 3f);
            }
        }
        shapeR.end();

        batch.begin();
    }

    private void drawPolygonRing() {
        int sides = centrifugeSides();
        if (sides == 0) return;
        if (sides == -3) { drawSnowflakeRing(); return; }
        if (sides < 0) { drawRectRing(); return; }

        float drumR    = CENTRIFUGE_R * PPM;
        float innerR   = drumR * 0.72f;
        float bodyAng  = centrifugeBody.getAngle();
        float sa       = centrifugeShapeAngle();
        float speedT   = Math.min(Math.abs(centrifugeBody.getAngularVelocity()) / centrifugeRpmMax, 1f);
        float pulse    = 0.55f + 0.45f * MathUtils.sin(animTime * (2.5f + speedT * 8f));

        // Planet fill / edge colors
        float[] fill, edge;
        switch (ShipData.get().currentPlanetIndex) {
            case 1: fill=new float[]{0.85f,0.28f,0.04f,0.82f}; edge=new float[]{1f,0.55f,0.15f}; break; // hex orange
            case 2: fill=new float[]{0.35f,0.75f,1.00f,0.82f}; edge=new float[]{0.80f,0.95f,1.0f}; break; // tri ice
            case 3: fill=new float[]{0.05f,0.55f,0.80f,0.82f}; edge=new float[]{0.28f,1.00f,0.90f}; break; // diamond teal
            case 4: fill=new float[]{0.80f,0.55f,0.05f,0.82f}; edge=new float[]{1.00f,0.85f,0.25f}; break; // penta forge
            default: fill=new float[]{0.10f,0.50f,0.90f,0.82f}; edge=new float[]{0.60f,0.90f,1.0f};
        }

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        // Outer glow layers
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        for (int g = 5; g > 0; g--) {
            float gr = drumR + g * 7f;
            float ga = 0.025f * g * speedT * pulse;
            shapeR.setColor(edge[0], edge[1], edge[2], ga);
            for (int i = 0; i < sides; i++) {
                float a0 = sa + bodyAng + (float)(2 * Math.PI * i / sides);
                float a1 = sa + bodyAng + (float)(2 * Math.PI * (i + 1) / sides);
                float ox0=CCX_PX+MathUtils.cos(a0)*gr, oy0=CCY_PX+MathUtils.sin(a0)*gr;
                float ox1=CCX_PX+MathUtils.cos(a1)*gr, oy1=CCY_PX+MathUtils.sin(a1)*gr;
                shapeR.triangle(CCX_PX, CCY_PX, ox0, oy0, ox1, oy1);
            }
        }
        shapeR.end();

        // Ring fill (outer polygon - inner polygon)
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < sides; i++) {
            float a0 = sa + bodyAng + (float)(2 * Math.PI * i / sides);
            float a1 = sa + bodyAng + (float)(2 * Math.PI * (i + 1) / sides);
            float ox0=CCX_PX+MathUtils.cos(a0)*drumR, oy0=CCY_PX+MathUtils.sin(a0)*drumR;
            float ox1=CCX_PX+MathUtils.cos(a1)*drumR, oy1=CCY_PX+MathUtils.sin(a1)*drumR;
            float ix0=CCX_PX+MathUtils.cos(a0)*innerR, iy0=CCY_PX+MathUtils.sin(a0)*innerR;
            float ix1=CCX_PX+MathUtils.cos(a1)*innerR, iy1=CCY_PX+MathUtils.sin(a1)*innerR;
            // mid-edge brightness boost
            float mid = sa + bodyAng + (float)(2 * Math.PI * (i + 0.5) / sides);
            float mx0=CCX_PX+MathUtils.cos(mid)*drumR, my0=CCY_PX+MathUtils.sin(mid)*drumR;
            float mx1=CCX_PX+MathUtils.cos(mid)*innerR, my1=CCY_PX+MathUtils.sin(mid)*innerR;
            float bright = 0.15f + speedT * 0.25f;
            shapeR.setColor(fill[0]+bright, fill[1]+bright, fill[2]+bright, fill[3]);
            shapeR.triangle(ox0, oy0, mx0, my0, ix0, iy0);
            shapeR.triangle(mx0, my0, mx1, my1, ix0, iy0);
            shapeR.setColor(fill[0], fill[1], fill[2], fill[3]);
            shapeR.triangle(mx0, my0, ox1, oy1, mx1, my1);
            shapeR.triangle(ox1, oy1, ix1, iy1, mx1, my1);
        }
        // Corner accent circles at vertices
        shapeR.setColor(edge[0], edge[1], edge[2], 0.92f);
        for (int i = 0; i < sides; i++) {
            float a = sa + bodyAng + (float)(2 * Math.PI * i / sides);
            shapeR.circle(CCX_PX+MathUtils.cos(a)*drumR, CCY_PX+MathUtils.sin(a)*drumR, 5.5f, 10);
        }
        shapeR.end();

        // Outer + inner edge lines
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < sides; i++) {
            float a0 = sa + bodyAng + (float)(2 * Math.PI * i / sides);
            float a1 = sa + bodyAng + (float)(2 * Math.PI * (i + 1) / sides);
            shapeR.setColor(edge[0], edge[1], edge[2], 0.88f + 0.12f * pulse);
            shapeR.line(CCX_PX+MathUtils.cos(a0)*drumR, CCY_PX+MathUtils.sin(a0)*drumR,
                        CCX_PX+MathUtils.cos(a1)*drumR, CCY_PX+MathUtils.sin(a1)*drumR);
            shapeR.setColor(edge[0]*0.6f, edge[1]*0.6f, edge[2]*0.6f, 0.55f);
            shapeR.line(CCX_PX+MathUtils.cos(a0)*innerR, CCY_PX+MathUtils.sin(a0)*innerR,
                        CCX_PX+MathUtils.cos(a1)*innerR, CCY_PX+MathUtils.sin(a1)*innerR);
        }
        shapeR.end();

        batch.begin();
    }

    private void drawSnowflakeRing() {
        float bodyAng = centrifugeBody.getAngle();
        float speedT  = Math.min(Math.abs(centrifugeBody.getAngularVelocity()) / centrifugeRpmMax, 1f);
        float pulse   = 0.55f + 0.45f * MathUtils.sin(animTime * (2.5f + speedT * 8f));
        float step    = (float)(2 * Math.PI / SNOWFLAKE_SEGS);
        float innerR  = SNOWFLAKE_VALLEY_R * PPM * 0.75f;  // ~67px fixed inner ring

        // ice-blue palette
        float fr = 0.35f, fg = 0.75f, fb = 1.00f, fa = 0.82f;  // fill
        float er = 0.80f, eg = 0.95f, eb = 1.0f;                 // edge/glow

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        // Outer glow layers (scaled-out snowflake outline)
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        for (int g = 5; g > 0; g--) {
            float gs = 1f + g * 0.06f;
            float ga = 0.025f * g * speedT * pulse;
            shapeR.setColor(er, eg, eb, ga);
            for (int i = 0; i < SNOWFLAKE_SEGS; i++) {
                float la0 = step * i, la1 = step * (i + 1);
                float ox0 = CCX_PX + MathUtils.cos(bodyAng + la0) * snowflakeR(la0) * PPM * gs;
                float oy0 = CCY_PX + MathUtils.sin(bodyAng + la0) * snowflakeR(la0) * PPM * gs;
                float ox1 = CCX_PX + MathUtils.cos(bodyAng + la1) * snowflakeR(la1) * PPM * gs;
                float oy1 = CCY_PX + MathUtils.sin(bodyAng + la1) * snowflakeR(la1) * PPM * gs;
                shapeR.triangle(CCX_PX, CCY_PX, ox0, oy0, ox1, oy1);
            }
        }
        shapeR.end();

        // Ring fill (outer snowflake contour strips minus inner circle)
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        float bright = 0.15f + speedT * 0.25f;
        for (int i = 0; i < SNOWFLAKE_SEGS; i++) {
            float la0 = step * i, la1 = step * (i + 1);
            float wa0 = bodyAng + la0, wa1 = bodyAng + la1;
            float or0 = snowflakeR(la0) * PPM, or1 = snowflakeR(la1) * PPM;
            float ox0 = CCX_PX + MathUtils.cos(wa0) * or0, oy0 = CCY_PX + MathUtils.sin(wa0) * or0;
            float ox1 = CCX_PX + MathUtils.cos(wa1) * or1, oy1 = CCY_PX + MathUtils.sin(wa1) * or1;
            float ix0 = CCX_PX + MathUtils.cos(wa0) * innerR, iy0 = CCY_PX + MathUtils.sin(wa0) * innerR;
            float ix1 = CCX_PX + MathUtils.cos(wa1) * innerR, iy1 = CCY_PX + MathUtils.sin(wa1) * innerR;
            shapeR.setColor(fr + bright, fg + bright, fb + bright, fa);
            shapeR.triangle(ox0, oy0, ox1, oy1, ix0, iy0);
            shapeR.setColor(fr, fg, fb, fa);
            shapeR.triangle(ox1, oy1, ix1, iy1, ix0, iy0);
        }
        // Hub state indicator: cool ice-blue during PULL, bright white-cyan during PUSH
        boolean pullPhase = frostheimHubTimer < FROSTHEIM_HUB_HALF;
        float hubT = pullPhase
            ? frostheimHubTimer / FROSTHEIM_HUB_HALF
            : Math.min(1f, (frostheimHubTimer - FROSTHEIM_HUB_HALF) / FROSTHEIM_HUB_HALF);
        if (pullPhase) {
            shapeR.setColor(0.10f, 0.55f + hubT * 0.45f, 1f, 0.40f + hubT * 0.55f);
        } else {
            float fade = 1f - hubT;
            shapeR.setColor(0.75f + hubT * 0.25f, 0.90f + hubT * 0.10f, 1f, 0.55f + fade * 0.40f);
        }
        shapeR.circle(CCX_PX, CCY_PX, innerR, 28);
        shapeR.end();

        // Arm-tip accent circles (6 tips at k*PI/3 in local frame)
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        shapeR.setColor(er, eg, eb, 0.92f);
        for (int k = 0; k < 6; k++) {
            float tipLoc = k * MathUtils.PI / 3f;
            float tipAng = bodyAng + tipLoc;
            float tipR   = SNOWFLAKE_ARM_R * PPM;
            shapeR.circle(CCX_PX + MathUtils.cos(tipAng) * tipR,
                          CCY_PX + MathUtils.sin(tipAng) * tipR, 5.5f, 10);
        }
        shapeR.end();

        // Outer edge lines
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(er, eg, eb, 0.88f + 0.12f * pulse);
        for (int i = 0; i < SNOWFLAKE_SEGS; i++) {
            float la0 = step * i, la1 = step * (i + 1);
            float wa0 = bodyAng + la0, wa1 = bodyAng + la1;
            float or0 = snowflakeR(la0) * PPM, or1 = snowflakeR(la1) * PPM;
            shapeR.line(CCX_PX + MathUtils.cos(wa0) * or0, CCY_PX + MathUtils.sin(wa0) * or0,
                        CCX_PX + MathUtils.cos(wa1) * or1, CCY_PX + MathUtils.sin(wa1) * or1);
        }
        shapeR.end();

        batch.begin();
    }

    private void drawCentrifuge() {
        float speedT = Math.min(centrifugeBody.getAngularVelocity() / centrifugeRpmMax, 1f);
        float angle  = centrifugeBody.getAngle() * MathUtils.radiansToDegrees;
        float drumR  = CENTRIFUGE_R * PPM;   // 180 px

        // === Speed outer aura — only for circle ===
        if (centrifugeSides() == 0 && speedT > 0.05f) {
            float pulseRate  = 2.5f + speedT * 10f;
            float glowPulse  = 0.55f + 0.45f * MathUtils.sin(animTime * pulseRate);
            float glowAlpha  = speedT * 0.55f * glowPulse;
            float glowExtra  = speedT * 22f;  // expands outward at high speed
            float gs         = RING_TEX_SIZE + glowExtra * 2f;
            float go         = glowExtra;
            // Colour shifts from cool blue → electric cyan at max speed
            batch.setColor(0.30f + speedT * 0.20f, 0.70f + speedT * 0.30f, 1.00f, glowAlpha);
            batch.draw(texRing,
                CCX_PX - drumR - go, CCY_PX - drumR - go,
                gs * 0.5f, gs * 0.5f, gs, gs,
                1f, 1f, angle,
                0, 0, texRing.getWidth(), texRing.getHeight(), false, false);
        }

        // === Motion blur — only for circle ===
        if (centrifugeSides() == 0 && speedT > 0.20f) {
            float trailDeg = speedT * 30f;
            batch.setColor(0.75f, 0.90f, 1f, speedT * 0.22f);
            batch.draw(texRing,
                CCX_PX - drumR, CCY_PX - drumR,
                RING_TEX_SIZE * 0.5f, RING_TEX_SIZE * 0.5f, RING_TEX_SIZE, RING_TEX_SIZE,
                1f, 1f, angle - trailDeg * 0.5f,
                0, 0, texRing.getWidth(), texRing.getHeight(), false, false);
            batch.setColor(0.75f, 0.90f, 1f, speedT * 0.12f);
            batch.draw(texRing,
                CCX_PX - drumR, CCY_PX - drumR,
                RING_TEX_SIZE * 0.5f, RING_TEX_SIZE * 0.5f, RING_TEX_SIZE, RING_TEX_SIZE,
                1f, 1f, angle - trailDeg,
                0, 0, texRing.getWidth(), texRing.getHeight(), false, false);
        }

        // === Main ring — circle or polygon ===
        if (centrifugeSides() == 0) {
            batch.setColor(0.82f + 0.18f * speedT, 0.88f + 0.12f * speedT, 1f, 1f);
            batch.draw(texRing,
                CCX_PX - drumR, CCY_PX - drumR,
                RING_TEX_SIZE * 0.5f, RING_TEX_SIZE * 0.5f,
                RING_TEX_SIZE, RING_TEX_SIZE,
                1f, 1f, angle,
                0, 0, texRing.getWidth(), texRing.getHeight(),
                false, false);
        } else {
            drawPolygonRing(); // ends batch, draws with shapeR, re-opens batch
        }

        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawAttractors() {
        if (attractors.size == 0) return;
        float fieldDiam = gravityFieldR * 2f * PPM;
        float spin      =  animTime * 55f;
        float spinBack  = -animTime * 85f;
        float pulse     = 1f + MathUtils.sin(animTime * 3.2f) * 0.10f;
        long  now       = System.currentTimeMillis();

        for (int i = 0; i < attractors.size; i++) {
            Body    body = attractors.get(i);
            Vector2 pos  = body.getPosition();
            float   px   = pos.x * PPM;
            float   py   = pos.y * PPM;

            // Hit-flash age: 1.0 at impact, decays to 0 over 380 ms
            float hitT = 0f;
            if (body.getUserData() instanceof ShipData.AttractorHitData) {
                long elapsed = now - ((ShipData.AttractorHitData) body.getUserData()).lastHitMs;
                hitT = Math.max(0f, 1f - elapsed / 380f);
            }

            float fd = fieldDiam * pulse;
            float fi = fieldDiam * 0.62f * pulse;

            // On-hit burst: bright cyan-white shockwave ring that expands outward
            if (hitT > 0.01f) {
                float burstD = fieldDiam * (1.15f + (1f - hitT) * 1.6f);
                batch.setColor(0.60f + hitT * 0.40f, 0.25f + hitT * 0.55f, 1f, hitT * 0.75f);
                batch.draw(texGravField,
                    px - burstD * 0.5f, py - burstD * 0.5f,
                    burstD * 0.5f, burstD * 0.5f, burstD, burstD, 1f, 1f, -animTime * 120f,
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            }

            // Outer field ring — flares more opaque and slightly larger on hit
            batch.setColor(0.55f + hitT * 0.35f, 0.25f + hitT * 0.45f, 1.00f, 0.45f + hitT * 0.40f);
            batch.draw(texGravField,
                px - fd * 0.5f, py - fd * 0.5f,
                fd * 0.5f, fd * 0.5f, fd, fd, 1f, 1f, spin,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Inner counter-ring
            batch.setColor(0.75f + hitT * 0.25f, 0.35f + hitT * 0.45f, 1.00f, 0.60f + hitT * 0.30f);
            batch.draw(texGravField,
                px - fi * 0.5f, py - fi * 0.5f,
                fi * 0.5f, fi * 0.5f, fi, fi, 1f, 1f, spinBack,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Singularity core — flashes toward white-cyan on hit then fades back to purple
            float gcd = 54f, ghw = gcd * 0.5f;
            float gravPulse = 0.72f + 0.28f * MathUtils.sin(animTime * 4.8f + i * 1.3f);
            float cR = Math.min(0.78f + 0.22f * gravPulse + hitT * 0.40f, 1f);
            float cG = Math.min(0.22f              + hitT * 0.65f, 1f);
            float cB = 1f;
            batch.setColor(cR, cG, cB, 0.95f);
            batch.draw(texGravCenter, px - ghw, py - ghw, ghw, ghw, gcd, gcd, 1f, 1f, animTime * 44f,
                0, 0, texGravCenter.getWidth(), texGravCenter.getHeight(), false, false);
        }
        // Flying attractor (visual slingshot phase)
        if (flyingAttractorActive) {
            float _apx = flyingAttractorWX * PPM, _apy = flyingAttractorWY * PPM;
            float _afd = 80f;
            batch.setColor(0.60f, 0.30f, 1f, 0.65f);
            batch.draw(texGravField, _apx - _afd * 0.5f, _apy - _afd * 0.5f, _afd, _afd);
            batch.setColor(0.78f, 0.22f, 1f, 0.95f);
            float _agcd = 44f;
            batch.draw(texGravCenter, _apx - _agcd * 0.5f, _apy - _agcd * 0.5f,
                _agcd * 0.5f, _agcd * 0.5f, _agcd, _agcd, 1f, 1f, animTime * 60f,
                0, 0, texGravCenter.getWidth(), texGravCenter.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Add floating "+NJ" and "+N◆" harvest reward labels directly to activeFloats. */
    private void queueHarvestPop(float px, float py, float joules, int gems) {
        FloatEntry fe = new FloatEntry();
        fe.wx        = px / PPM;
        fe.wy        = py / PPM;
        fe.value     = joules;
        fe.colorType = 4;
        fe.age       = 0f;
        fe.driftX    = 0f;
        activeFloats.add(fe);
        FloatEntry fe2 = new FloatEntry();
        fe2.wx        = px / PPM + 0.3f;
        fe2.wy        = py / PPM - 0.2f;
        fe2.value     = gems;
        fe2.colorType = 5;
        fe2.age       = 0f;
        fe2.driftX    = 0.05f;
        activeFloats.add(fe2);
    }

    private void drawHarvestGlows() {
        boolean hasCharged = false;
        for (int _i = 0; _i < bumpers.size; _i++) {
            Body _b = bumpers.get(_i);
            if (_b.getUserData() instanceof ShipData.BumperHitData
                    && ((ShipData.BumperHitData) _b.getUserData()).harvestPending) {
                hasCharged = true; break;
            }
        }
        if (!hasCharged) {
            for (int _i = 0; _i < attractors.size; _i++) {
                Body _b = attractors.get(_i);
                if (_b.getUserData() instanceof ShipData.AttractorHitData
                        && ((ShipData.AttractorHitData) _b.getUserData()).harvestPending) {
                    hasCharged = true; break;
                }
            }
        }
        if (!hasCharged) return;

        batch.end();

        Gdx.gl.glLineWidth(2.5f);
        shapeR.setProjectionMatrix(renderCam.combined);
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        float _pulse = 0.55f + 0.45f * MathUtils.sin(animTime * 4f);

        for (int _i = 0; _i < bumpers.size; _i++) {
            Body _b = bumpers.get(_i);
            if (!(_b.getUserData() instanceof ShipData.BumperHitData)) continue;
            if (!((ShipData.BumperHitData) _b.getUserData()).harvestPending) continue;
            float _bx = _b.getPosition().x * PPM;
            float _by = _b.getPosition().y * PPM;
            shapeR.setColor(1.0f, 0.82f, 0.1f, _pulse);
            shapeR.circle(_bx, _by, BUMPER_RADIUS * PPM * 1.6f, 20);
        }
        for (int _i = 0; _i < attractors.size; _i++) {
            Body _b = attractors.get(_i);
            if (!(_b.getUserData() instanceof ShipData.AttractorHitData)) continue;
            if (!((ShipData.AttractorHitData) _b.getUserData()).harvestPending) continue;
            float _bx = _b.getPosition().x * PPM;
            float _by = _b.getPosition().y * PPM;
            shapeR.setColor(1.0f, 0.82f, 0.1f, _pulse);
            shapeR.circle(_bx, _by, BUMPER_RADIUS * PPM * 2.0f, 24);
        }
        shapeR.end();
        Gdx.gl.glLineWidth(1f);

        batch.begin();
    }

    private void drawBumpers() {
        long now = System.currentTimeMillis();
        for (int i = 0, n = bumpers.size; i < n; i++) {
            Body    body  = bumpers.get(i);
            Vector2 pos   = body.getPosition();
            float   px    = pos.x * PPM, py = pos.y * PPM;

            // Hit-flash age: 1.0 right at impact, decays to 0 over 320 ms
            float hitT = 0f;
            if (body.getUserData() instanceof ShipData.BumperHitData) {
                long elapsed = now - ((ShipData.BumperHitData) body.getUserData()).lastHitMs;
                hitT = Math.max(0f, 1f - elapsed / 320f);
            }

            float pulse = 0.70f + MathUtils.sin(animTime * 3.5f + i * 1.3f) * 0.30f;
            float scale = 0.92f + MathUtils.sin(animTime * 2.6f + i * 0.8f) * 0.08f + hitT * 0.25f;

            // On-hit burst: expanding ring + spark streaks + white flash
            if (hitT > 0.01f) {
                // Expanding cyan ring
                float burstD = BUMPER_W * (2.2f + (1f - hitT) * 3.4f);
                batch.setColor(0.3f, 0.9f + hitT * 0.1f, 1f, hitT * 0.85f);
                batch.draw(texGravField,
                    px - burstD * 0.5f, py - burstD * 0.5f,
                    burstD * 0.5f, burstD * 0.5f, burstD, burstD, 1f, 1f, animTime * 90f,
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
                // 8 spark streaks shooting outward
                float sparkDist = (1f - hitT) * 40f;
                float sparkLen  = hitT * 18f + 4f;
                for (int s = 0; s < 8; s++) {
                    float angle = s * 45f;
                    float radA  = angle * MathUtils.degreesToRadians;
                    float sx    = px + MathUtils.cos(radA) * (BUMPER_W * 0.5f + sparkDist);
                    float sy    = py + MathUtils.sin(radA) * (BUMPER_W * 0.5f + sparkDist);
                    batch.setColor(0.5f + hitT * 0.5f, 0.88f, 1f, hitT * 0.90f);
                    batch.draw(texPixel,
                        sx - 1.5f, sy - sparkLen * 0.5f,
                        1.5f, sparkLen * 0.5f,
                        3f, sparkLen,
                        1f, 1f, angle + 90f,
                        0, 0, 1, 1, false, false);
                }
                // White flash overlay on bumper at peak impact
                if (hitT > 0.55f) {
                    float flashA = (hitT - 0.55f) / 0.45f;
                    float fd = BUMPER_W * (scale + 0.15f);
                    batch.setColor(1f, 1f, 1f, flashA * 0.75f);
                    batch.draw(texParticle,
                        px - fd * 0.5f, py - fd * 0.5f, fd, fd);
                }
            }

            // Pulsing magenta outer glow — flares brighter on hit
            float glowD = BUMPER_W * 2.2f * scale;
            batch.setColor(1f, 0.25f * pulse + hitT * 0.35f, 0.75f * pulse, 0.42f * pulse + hitT * 0.45f);
            batch.draw(texGravField,
                px - glowD * 0.5f, py - glowD * 0.5f,
                glowD * 0.5f, glowD * 0.5f, glowD, glowD, 1f, 1f, animTime * 28f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Core — counter-spins; flashes toward white on impact then returns to magenta
            float dw = BUMPER_W * scale, dh = BUMPER_H * scale;
            float cR = Math.min(pulse + 0.15f + hitT * 0.50f, 1f);
            float cG = Math.min(pulse * 0.08f + hitT * 0.75f, 1f);
            float cB = Math.min(pulse * 0.85f + 0.10f + hitT * 0.25f, 1f);
            batch.setColor(cR, cG, cB, 0.95f);
            batch.draw(texBumper,
                px - dw * 0.5f, py - dh * 0.5f,
                dw * 0.5f, dh * 0.5f, dw, dh, 1f, 1f, -animTime * 18f,
                0, 0, texBumper.getWidth(), texBumper.getHeight(), false, false);
        }
        // Draw pre-entry flying bumper (visual flight phase, no physics yet)
        if (flyingBumperActive) {
            float _fpx = flyingBumperWX * PPM, _fpy = flyingBumperWY * PPM;
            float _fsz = BUMPER_W * 1.4f;
            batch.setColor(0.35f, 1.0f, 0.90f, 0.92f);
            batch.draw(texBumper, _fpx - _fsz * 0.5f, _fpy - _fsz * 0.5f,
                _fsz * 0.5f, _fsz * 0.5f, _fsz, _fsz, 1f, 1f, animTime * 50f,
                0, 0, texBumper.getWidth(), texBumper.getHeight(), false, false);
        }
        // Draw in-flight curling bodies (physics phase, inside drum)
        for (int _i = 0; _i < curlingBodies.size; _i++) {
            com.badlogic.gdx.physics.box2d.Body _cb = curlingBodies.get(_i);
            float _px = _cb.getPosition().x * PPM;
            float _py = _cb.getPosition().y * PPM;
            float _t  = curlingTimers.get(_i) / CURLING_SETTLE_TIME;
            float _dw = BUMPER_W * (1.1f + 0.2f * (1f - _t));
            batch.setColor(0.4f, 1.0f, 0.9f, 0.85f);
            batch.draw(texBumper,
                _px - _dw * 0.5f, _py - _dw * 0.5f,
                _dw * 0.5f, _dw * 0.5f, _dw, _dw, 1f, 1f, animTime * 30f,
                0, 0, texBumper.getWidth(), texBumper.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawArmBumpers() {
        if (armBumpers.size == 0) return;
        long now = System.currentTimeMillis();
        float D = BUMPER_W * 1.5f;
        // ice-blue palette matching snowflake ring: fill(0.35,0.75,1.0) edge(0.80,0.95,1.0)
        for (int i = 0; i < armBumpers.size; i++) {
            Body    body = armBumpers.get(i);
            Vector2 pos  = body.getPosition();
            float   px   = pos.x * PPM, py = pos.y * PPM;
            float hitT = 0f;
            if (body.getUserData() instanceof ShipData.BumperHitData) {
                long elapsed = now - ((ShipData.BumperHitData) body.getUserData()).lastHitMs;
                hitT = Math.max(0f, 1f - elapsed / 280f);
            }
            float pulse = 0.70f + MathUtils.sin(animTime * 4f + i * 1.05f) * 0.30f;
            float scale = 0.92f + MathUtils.sin(animTime * 3f + i * 0.7f) * 0.08f + hitT * 0.30f;
            // Outer glow — ice-blue edge tint
            float glowD = D * 2.6f * scale;
            batch.setColor(0.80f, 0.95f * pulse, 1f, 0.30f * pulse + hitT * 0.45f);
            batch.draw(texGravField,
                px - glowD * 0.5f, py - glowD * 0.5f,
                glowD * 0.5f, glowD * 0.5f, glowD, glowD, 1f, 1f, animTime * 35f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            // Core — snowflake fill colour, flash to white on hit
            float dw = D * scale, dh = D * scale;
            batch.setColor(
                Math.min(0.35f + hitT * 0.65f, 1f),
                Math.min(0.75f + hitT * 0.25f, 1f),
                1f, 0.95f);
            batch.draw(texBumper,
                px - dw * 0.5f, py - dh * 0.5f,
                dw * 0.5f, dh * 0.5f, dw, dh, 1f, 1f, -animTime * 22f,
                0, 0, texBumper.getWidth(), texBumper.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawValleyBlades() {
        if (valleyBlades.size == 0) return;
        long  now         = System.currentTimeMillis();
        float halfLenPx   = VALLEY_BLADE_HL * PPM;   // matches physics size exactly
        float halfWidthPx = VALLEY_BLADE_HW * PPM;

        if (batch.isDrawing()) batch.end();
        shapeR.setProjectionMatrix(renderCam.combined);
        shapeR.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < valleyBlades.size; i++) {
            Body    body = valleyBlades.get(i);
            Vector2 pos  = body.getPosition();
            float   px   = pos.x * PPM, py = pos.y * PPM;

            float hitT = 0f;
            if (body.getUserData() instanceof ShipData.BumperHitData) {
                long elapsed = now - ((ShipData.BumperHitData) body.getUserData()).lastHitMs;
                hitT = Math.max(0f, 1f - elapsed / 280f);
            }
            float pulse = 0.60f + MathUtils.sin(animTime * 3.5f + i * 1.1f) * 0.40f;

            // Direction from blade position toward drum center
            float toCx = CCX_PX - px, toCy = CCY_PX - py;
            float d = (float) Math.sqrt(toCx * toCx + toCy * toCy);
            float ndx = toCx / d, ndy = toCy / d;   // unit toward center
            float pdx = -ndy, pdy = ndx;             // perpendicular

            // Triangle vertices: tip toward center, base facing valley wall
            float tipX  = px + ndx * halfLenPx,  tipY  = py + ndy * halfLenPx;
            float baseMx = px - ndx * halfLenPx, baseMy = py - ndy * halfLenPx;
            float b1x = baseMx + pdx * halfWidthPx, b1y = baseMy + pdy * halfWidthPx;
            float b2x = baseMx - pdx * halfWidthPx, b2y = baseMy - pdy * halfWidthPx;

            // Outer glow — slightly enlarged triangle
            float g = 1.35f;
            float tGx = px + ndx * halfLenPx * g, tGy = py + ndy * halfLenPx * g;
            float bGMx = px - ndx * halfLenPx * g, bGMy = py - ndy * halfLenPx * g;
            float g1x = bGMx + pdx * halfWidthPx * g, g1y = bGMy + pdy * halfWidthPx * g;
            float g2x = bGMx - pdx * halfWidthPx * g, g2y = bGMy - pdy * halfWidthPx * g;
            shapeR.setColor(0.30f, 0.92f, 0.90f, 0.22f * pulse + hitT * 0.38f);
            shapeR.triangle(tGx, tGy, g1x, g1y, g2x, g2y);

            // Core triangle — teal spike, flash cyan-white on hit
            shapeR.setColor(
                Math.min(0.20f + hitT * 0.80f, 1f),
                Math.min(0.85f + hitT * 0.15f, 1f) * pulse,
                Math.min(0.88f + hitT * 0.12f, 1f),
                0.93f);
            shapeR.triangle(tipX, tipY, b1x, b1y, b2x, b2y);

            // Bright edge highlight along the two leading sides
            shapeR.setColor(0.85f, 1f, 1f, 0.65f * pulse + hitT * 0.35f);
            shapeR.rectLine(tipX, tipY, b1x, b1y, 1.5f);
            shapeR.rectLine(tipX, tipY, b2x, b2y, 1.5f);
        }

        shapeR.end();
        batch.begin();
    }

    private void drawIcicleNodes() {
        // Frostheim Icicle Nodes — drawn slightly larger than physics radius for visibility
        float drawD = ICICLE_RADIUS * 2f * PPM;  // matches physics diameter exactly
        for (int i = 0, n = icicleNodes.size; i < n; i++) {
            Vector2 pos   = icicleNodes.get(i).getPosition();
            float   px    = pos.x * PPM;
            float   py    = pos.y * PPM;

            // Check if this icicle is busy (has a live pellet group)
            boolean busy = false;
            for (int pg = 0; pg < pelletGroups.size; pg++) {
                if (pelletGroups.get(pg).icicleNodeIdx == i) { busy = true; break; }
            }

            float   pulse = 0.85f + MathUtils.sin(animTime * 3.0f + i * 1.4f) * 0.15f;

            // Bright cyan-white crystal icon — sharp, fully opaque, slowly spins
            // Busy icicles are dimmed and desaturated (recharging state)
            float rot   = animTime * 15f + i * 60f;
            if (busy) {
                batch.setColor(0.35f, 0.55f, 0.65f, 0.45f);  // dim blue-grey — locked/recharging
            } else {
                batch.setColor(0.55f + pulse * 0.45f, 1f, 1f, 1f);  // bright cyan — ready
            }
            batch.draw(texCryoVent,
                px - drawD * 0.5f, py - drawD * 0.5f, drawD * 0.5f, drawD * 0.5f,
                drawD, drawD, 1f, 1f, rot,
                0, 0, texCryoVent.getWidth(), texCryoVent.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawSnowPellets() {
        // Draw size exactly matches physics diameter
        float drawD = PELLET_RADIUS * 2f * PPM;
        for (int i = 0, n = snowPellets.size; i < n; i++) {
            Vector2 pos = snowPellets.get(i).getPosition();
            float   px  = pos.x * PPM;
            float   py  = pos.y * PPM;
            float   rot = animTime * 90f + i * 120f;

            // Bright saturated cyan, fully opaque so tiny pellets remain visible
            batch.setColor(0f, 1f, 1f, 1f);
            batch.draw(texCryoVent,
                px - drawD * 0.5f, py - drawD * 0.5f, drawD * 0.5f, drawD * 0.5f,
                drawD, drawD, 1f, 1f, rot,
                0, 0, texCryoVent.getWidth(), texCryoVent.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawTeslaCoils() {
        if (teslaCoils.size == 0) return;
        for (int i = 0; i < teslaCoils.size; i++) {
            Vector2 pos = teslaCoils.get(i).getPosition();
            float px = pos.x * PPM, py = pos.y * PPM;

            // Check if this spiral is active (has a captured orb)
            float chargeT = 0f;
            for (int c = 0; c < spiralCaptures.size; c++) {
                SpiralCapture sc = spiralCaptures.get(c);
                float ddx = sc.cx - pos.x, ddy = sc.cy - pos.y;
                if (ddx * ddx + ddy * ddy < 0.01f) {
                    chargeT = Math.min(sc.timer / SPIRAL_DURATION, 1f);
                    break;
                }
            }

            float pulse = 0.75f + MathUtils.sin(animTime * 4f + i * 1.2f) * 0.25f;
            // Idle outer glow — warm orange
            float fd = SPIRAL_CAPTURE_R * 2f * PPM * pulse;
            batch.setColor(1f, 0.65f, 0.10f, 0.18f + chargeT * 0.35f);
            batch.draw(texGravField,
                px - fd * 0.5f, py - fd * 0.5f, fd * 0.5f, fd * 0.5f, fd, fd, 1f, 1f,
                animTime * 55f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Charge ring — grows as timer fills
            if (chargeT > 0f) {
                float cr = SPIRAL_ORBIT_R * 2f * PPM * (0.8f + chargeT * 0.4f);
                batch.setColor(1f, 0.90f, 0.20f, chargeT * 0.85f);
                batch.draw(texGravField,
                    px - cr * 0.5f, py - cr * 0.5f, cr * 0.5f, cr * 0.5f, cr, cr, 1f, 1f,
                    -animTime * 180f,
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            }

            // Core icon — spinning spiral shape
            float dw = BUMPER_W * 1.4f * pulse, dh = BUMPER_H * 1.4f * pulse;
            batch.setColor(1f, 0.80f + chargeT * 0.20f, 0.25f, 0.95f);
            batch.draw(texTeslaCoil,
                px - dw * 0.5f, py - dh * 0.5f, dw * 0.5f, dh * 0.5f, dw, dh, 1f, 1f,
                animTime * 40f,
                0, 0, texTeslaCoil.getWidth(), texTeslaCoil.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawKineticBlades() {
        if (kineticBlades.size == 0) return;

        float pulse = 0.80f + MathUtils.sin(animTime * 5f) * 0.20f;
        float len   = BLADE_LENGTH * PPM;
        float hw    = BLADE_WIDTH  * PPM * 2.5f; // visual half-width at base
        float cx    = CCX_PX, cy = CCY_PX;

        // ---- Draw hub glow (batch is already open from caller) ----
        float hubR = hw * 2.2f;
        if (batch.isDrawing()) {
            batch.setColor(0.50f, 0.80f, 1f, 0.30f * pulse);
            batch.draw(texGravField, cx - hubR, cy - hubR, hubR, hubR,
                       hubR * 2f, hubR * 2f, 1f, 1f, animTime * -60f,
                       0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            batch.end();
        }

        // ---- Draw triangle blade arms via ShapeRenderer ----
        shapeR.setProjectionMatrix(renderCam.combined);
        shapeR.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < kineticBlades.size; i++) {
            float angle = kineticBlades.get(i).getAngle();
            float ca = MathUtils.cos(angle), sa = MathUtils.sin(angle);
            // Perpendicular direction for base width
            float pca = -sa, psa = ca;

            // Blade tip
            float tx = cx + ca * len, ty = cy + sa * len;
            // Base corners (at center, half-width apart)
            float bx1 = cx + pca * hw, by1 = cy + psa * hw;
            float bx2 = cx - pca * hw, by2 = cy - psa * hw;

            // Outer glow triangle (wider, transparent)
            float og = 1.22f;
            float[] c = i == 0 ? new float[]{0.55f, 0.65f, 0.80f}
                       : i == 1 ? new float[]{0.60f, 0.85f, 1.00f}
                       :          new float[]{0.45f, 1.00f, 0.90f};
            shapeR.setColor(c[0] * 0.5f, c[1] * 0.5f, c[2] * 0.5f, 0.28f * pulse);
            shapeR.triangle(bx1 * og - cx * (og-1), by1 * og - cy * (og-1),
                            bx2 * og - cx * (og-1), by2 * og - cy * (og-1),
                            tx  * 1.08f - cx * 0.08f, ty * 1.08f - cy * 0.08f);

            // Core triangle
            shapeR.setColor(c[0] * pulse, c[1] * pulse, c[2] * pulse, 0.90f);
            shapeR.triangle(bx1, by1, bx2, by2, tx, ty);

            // Bright leading edge line
            shapeR.setColor(0.90f, 0.96f, 1f, 0.70f * pulse);
            shapeR.rectLine(cx, cy, tx, ty, 1.5f);
        }

        // Hub center dot
        shapeR.setColor(0.70f, 0.88f, 1f, 0.85f);
        shapeR.circle(cx, cy, hw * 0.55f);
        shapeR.setColor(0.95f, 0.98f, 1f, 1f);
        shapeR.circle(cx, cy, hw * 0.28f);

        shapeR.end();
        if (!batch.isDrawing()) batch.begin();
    }

    private void drawEmberHub() {
        if (!isEmberIV()) return;
        float cx      = CCX_PX, cy = CCY_PX;
        boolean stateA = hubStateTimer < hubCycleLength;
        float   chargeT = stateA ? (hubStateTimer / hubCycleLength) : 1f;  // 0→1 during suction

        if (stateA) {
            // ============================================================
            // STATE A — SUCTION / VACUUM
            // 5 rings contracting toward center; speed + brightness build
            // as charge climbs from 0→1 over the full cycle.
            // ============================================================
            float speed = 0.55f + chargeT * 1.80f;   // rings accelerate as charge builds
            for (int ring = 0; ring < 5; ring++) {
                float phase = ((animTime * speed + ring * 0.20f) % 1f);
                float r     = CENTRIFUGE_R * PPM * (0.92f - phase * 0.85f);  // deep contraction to core
                float alpha = (1f - phase) * (0.30f + chargeT * 0.52f);      // brightens over time
                float white = chargeT * 0.45f;
                batch.setColor(0.08f + white, 0.72f + white * 0.28f, 1f, alpha);
                batch.draw(texGravField,
                    cx - r, cy - r, r, r, r * 2f, r * 2f, 1f, 1f,
                    animTime * -(50f + chargeT * 80f),   // rotation accelerates
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            }

            // Central core glow — grows and whitens as energy accumulates
            float coreR = 18f + chargeT * 62f;
            float coreA = 0.28f + chargeT * 0.70f;
            batch.setColor(0.15f + chargeT * 0.65f, 0.68f + chargeT * 0.32f, 1f, coreA);
            batch.draw(texGravCenter,
                cx - coreR, cy - coreR, coreR, coreR, coreR * 2f, coreR * 2f,
                1f, 1f, animTime * 100f,
                0, 0, texGravCenter.getWidth(), texGravCenter.getHeight(), false, false);

            // Warning flicker in the final 25 %: amber rings signal imminent blast
            if (chargeT > 0.75f) {
                float warnT  = (chargeT - 0.75f) / 0.25f;
                float flick  = warnT * (0.55f + 0.45f * MathUtils.sin(animTime * 22f));
                float wr     = CENTRIFUGE_R * PPM * 0.42f;
                batch.setColor(1f, 0.62f - warnT * 0.28f, 0.08f, flick);
                batch.draw(texGravField,
                    cx - wr, cy - wr, wr, wr, wr * 2f, wr * 2f, 1f, 1f, -animTime * 200f,
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            }

        } else {
            // ============================================================
            // STATE B — BLAST / EXPLOSION
            // Initial white flash → 5 staggered orange-red shock rings
            // expanding outward → dim red cooling glow.
            // ============================================================
            float blastAge = hubStateTimer - hubCycleLength;

            // White-yellow flash at ground zero (first 0.18 s)
            float flashFade = Math.max(0f, 1f - blastAge / 0.18f);
            if (flashFade > 0.01f) {
                float fr = CENTRIFUGE_R * PPM * 0.30f;
                batch.setColor(1f, 0.90f, 0.55f, flashFade * 0.95f);
                batch.draw(texGravField,
                    cx - fr, cy - fr, fr, fr, fr * 2f, fr * 2f, 1f, 1f, 0f,
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            }

            // 5 shock rings expanding outward — each slightly delayed and darker
            for (int ring = 0; ring < 5; ring++) {
                float delay    = ring * 0.09f;
                float localAge = blastAge - delay;
                if (localAge < 0f) continue;
                float phase = Math.min(1f, localAge / 0.85f);
                float r     = CENTRIFUGE_R * PPM * (0.03f + phase * 1.05f);
                float alpha = (1f - phase) * (0.88f - ring * 0.14f);
                float gC    = Math.max(0f, 0.72f - ring * 0.16f - phase * 0.30f);
                float bC    = Math.max(0f, 0.25f - ring * 0.05f - phase * 0.20f);
                batch.setColor(1f, gC, bC, alpha);
                batch.draw(texGravField,
                    cx - r, cy - r, r, r, r * 2f, r * 2f, 1f, 1f,
                    animTime * (25f + ring * 18f),
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
            }

            // Cooling core — dim ember-red glow persisting after blast
            float coolDelay = 0.55f;
            if (blastAge > coolDelay) {
                float coolingT = Math.min(1f, (blastAge - coolDelay) / (hubCycleLength - coolDelay));
                float cr       = 24f + coolingT * 6f;
                batch.setColor(0.70f - coolingT * 0.35f, 0.06f, 0.04f, (1f - coolingT) * 0.50f);
                batch.draw(texGravCenter,
                    cx - cr, cy - cr, cr, cr, cr * 2f, cr * 2f, 1f, 1f, animTime * -28f,
                    0, 0, texGravCenter.getWidth(), texGravCenter.getHeight(), false, false);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    // Intern count badge — bold text just below the ring
    private void drawInternCountBadge() {
        int count = balls.size + pelletGroups.size;  // pellets count as the 1 orb they came from
        int cap   = internCap();
        float bx  = CCX_PX;
        float by  = CCY_PX - CENTRIFUGE_R * PPM - 18f;

        // Main count — large, bright white
        floatFont.getData().setScale(1.15f);
        floatFont.setColor(0.90f, 0.93f, 1.00f, 0.95f);
        drawFontCentered(count + "/" + cap + " interns", bx, by);

        // Sub-hint — actual next-intern gain (capped)
        float nextGain = Math.min(targetRPM + 0.75f, centrifugeRpmMax) - targetRPM;
        String gainText = nextGain > 0.01f
            ? String.format("+%.2f r/s per intern", nextGain)
            : "SPEED CAPPED";
        floatFont.getData().setScale(0.85f);
        floatFont.setColor(0.45f, 0.85f, 1.00f, 0.82f);
        drawFontCentered(gainText, bx, by - 22f);

        floatFont.getData().setScale(1f);
        floatFont.setColor(1f, 1f, 1f, 1f);
    }

    // Idle nudge — large pulsing hint above HIRE button when ≤2 interns for 8+ seconds
    private void drawHireIdleNudge() {
        if (isEmberIV() || isFrostheim()) return;
        if (hireIdleTimer < 8f || notifActive || celebActive) return;
        float pulse = 0.60f + 0.40f * MathUtils.sin(animTime * 3.5f);
        float nx = 87f; // HIRE button center x in render coords
        // Background dim rect to improve legibility
        floatFont.getData().setScale(0.98f);
        floatFont.setColor(1.00f, 0.78f, 0.15f, 0.95f * pulse);
        drawFontCentered("hire more → ring spins faster", nx, 200f);
        floatFont.getData().setScale(1.10f);
        floatFont.setColor(1.00f, 0.65f, 0.10f, 0.80f * pulse);
        drawFontCentered("v", nx, 178f);
        floatFont.getData().setScale(1f);
        floatFont.setColor(1f, 1f, 1f, 1f);
    }

    private void drawEmberCenter() {
        float speedT  = Math.min(Math.abs(centrifugeBody.getAngularVelocity()) / centrifugeRpmMax, 1f);
        float pulse   = 0.55f + 0.45f * MathUtils.sin(animTime * 4.0f);
        float spin1   = animTime * 1.2f;   // outer ring rotation
        float spin2   = -animTime * 1.8f;  // inner diamond counter-rotation
        float cx      = CCX_PX, cy = CCY_PX;

        if (batch.isDrawing()) batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        // ── Outer glow corona ──
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        for (int g = 8; g >= 1; g--) {
            float r = 28f + g * 4f;
            float a = 0.022f * g * pulse * (0.5f + speedT * 0.5f);
            shapeR.setColor(0.55f, 0.15f, 1.00f, a);
            shapeR.circle(cx, cy, r, 24);
        }
        shapeR.end();

        // ── Outer orbit ring — 6 satellite dots ──
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < 6; i++) {
            float a = spin1 + (float)(Math.PI * 2.0 * i / 6.0);
            float sx = cx + MathUtils.cos(a) * 22f;
            float sy = cy + MathUtils.sin(a) * 22f;
            float dotR = i % 2 == 0 ? 3.5f : 2.0f;
            float bright = i == 0 ? 1.0f : 0.65f; // lead satellite brighter
            shapeR.setColor(bright, 0.55f * bright, 1.00f, 0.85f * pulse);
            shapeR.circle(sx, sy, dotR, 8);
        }
        shapeR.end();

        // ── Outer dashed orbit circle ──
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(0.65f, 0.30f, 1.00f, 0.30f * pulse);
        shapeR.circle(cx, cy, 22f, 36);
        shapeR.end();

        // ── Rotating diamond (4-point star) ──
        float dR = 13f + speedT * 3f;
        float[] dax = new float[4], day = new float[4];
        for (int i = 0; i < 4; i++) {
            float a = spin2 + (float)(Math.PI * 0.5 * i);
            float r = (i % 2 == 0) ? dR : dR * 0.55f;
            dax[i] = cx + MathUtils.cos(a) * r;
            day[i] = cy + MathUtils.sin(a) * r;
        }
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        shapeR.setColor(0.45f, 0.10f, 0.90f, 0.70f);
        shapeR.triangle(dax[0], day[0], dax[1], day[1], dax[2], day[2]);
        shapeR.triangle(dax[2], day[2], dax[3], day[3], dax[0], day[0]);
        shapeR.end();
        shapeR.begin(ShapeRenderer.ShapeType.Line);
        shapeR.setColor(0.85f, 0.55f, 1.00f, 0.90f);
        for (int i = 0; i < 4; i++) shapeR.line(dax[i], day[i], dax[(i+1)%4], day[(i+1)%4]);
        shapeR.end();

        // ── Inner bright core ──
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        shapeR.setColor(0.70f, 0.30f, 1.00f, 0.50f + speedT * 0.20f);
        shapeR.circle(cx, cy, 8f, 16);
        shapeR.setColor(0.95f, 0.80f, 1.00f, 0.90f + 0.10f * pulse);
        shapeR.circle(cx, cy, 4.5f, 12);
        shapeR.setColor(1.00f, 1.00f, 1.00f, 1.00f);
        shapeR.circle(cx, cy, 2.0f, 8);
        shapeR.end();

        // ── Speed ring — outer arc fills at higher speed ──
        if (speedT > 0.05f) {
            shapeR.begin(ShapeRenderer.ShapeType.Line);
            shapeR.setColor(0.80f, 0.50f, 1.00f, speedT * 0.60f * pulse);
            shapeR.arc(cx, cy, 17f, MathUtils.radiansToDegrees * spin1, speedT * 360f, 36);
            shapeR.end();
        }

        if (!batch.isDrawing()) batch.begin();
    }

    private void drawRelayNodes() {
        if (!isEmberIV() || (relayNodes.size == 0 && dragMode != PLACE_RELAY)) return;
        float pulse = 0.55f + 0.45f * MathUtils.sin(animTime * 3.5f);
        float nr    = RELAY_NODE_RADIUS * PPM;

        // Tick down hit-glow timers
        float rdt = Gdx.graphics.getDeltaTime();
        for (int i = 0; i < relaySegGlow.length; i++) {
            if (relaySegGlow[i] > 0f) relaySegGlow[i] = Math.max(0f, relaySegGlow[i] - rdt);
        }

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        // ---- Draw placed rails: center → node (node in local frame → world each frame) ----
        for (int i = 0; i < relayNodes.size; i++) {
            com.badlogic.gdx.math.Vector2 wp = relayNodeWorldPos(i);
            float bx  = wp.x * PPM, by = wp.y * PPM;
            float hitT = (i < relaySegGlow.length) ? relaySegGlow[i] / 0.35f : 0f;

            // Thin stacked Line passes = soft glow without width bar
            int glowPasses = hitT > 0.01f ? 6 : 4;
            for (int g = glowPasses; g >= 1; g--) {
                shapeR.begin(ShapeRenderer.ShapeType.Line);
                shapeR.setColor(0.55f + hitT * 0.45f, 0.20f + hitT * 0.55f, 1.00f,
                                0.09f * g * pulse + hitT * 0.12f * g);
                shapeR.line(CCX_PX, CCY_PX, bx, by);
                shapeR.end();
            }
            // Core — 2px rectLine
            shapeR.begin(ShapeRenderer.ShapeType.Filled);
            shapeR.setColor(0.88f + hitT * 0.12f, 0.65f + hitT * 0.35f, 1.00f,
                            0.90f * pulse + hitT * 0.10f);
            shapeR.rectLine(CCX_PX, CCY_PX, bx, by, 2.0f);
            shapeR.end();

            // Far-end node
            float nt = hitT;
            shapeR.begin(ShapeRenderer.ShapeType.Filled);
            shapeR.setColor(0.50f + nt * 0.50f, 0.20f + nt * 0.60f, 1.00f, (0.40f + nt * 0.50f) * pulse);
            shapeR.circle(bx, by, nr * (2.5f + nt * 2.0f), 12);
            shapeR.setColor(0.90f + nt * 0.10f, 0.75f + nt * 0.25f, 1.00f, 0.95f);
            shapeR.circle(bx, by, nr * (1f + nt * 0.5f), 10);
            shapeR.end();
        }

        // ---- Drag preview: dashed line from center to wall-snapped point ----
        if (dragMode == PLACE_RELAY) {
            float fw = dragStageX / PPM, fh = (dragStageY + 80f) / PPM;
            com.badlogic.gdx.math.Vector2 local = snapRelayToWall(fw, fh);
            // local → world
            float angle = centrifugeBody.getAngle();
            float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
            float wx2 = (CENTRIFUGE_CX + local.x * ca - local.y * sa) * PPM;
            float wy2 = (CENTRIFUGE_CY + local.x * sa + local.y * ca) * PPM;
            float ldx = wx2 - CCX_PX, ldy = wy2 - CCY_PX;
            float len = (float) Math.sqrt(ldx * ldx + ldy * ldy);
            if (len > 4f) {
                float dashLen = 18f, stepLen = 28f;
                int segs = (int)(len / stepLen) + 1;
                for (int pass = 4; pass >= 1; pass--) {
                    shapeR.begin(ShapeRenderer.ShapeType.Line);
                    shapeR.setColor(0.70f, 0.35f, 1.00f, 0.12f * pass * pulse);
                    for (int d = 0; d < segs; d++) {
                        float t0 = d * stepLen / len;
                        float t1 = Math.min((d * stepLen + dashLen) / len, 1f);
                        shapeR.line(CCX_PX + ldx * t0, CCY_PX + ldy * t0,
                                    CCX_PX + ldx * t1, CCY_PX + ldy * t1);
                    }
                    shapeR.end();
                }
                shapeR.begin(ShapeRenderer.ShapeType.Filled);
                shapeR.setColor(0.92f, 0.72f, 1.00f, 0.90f * pulse);
                for (int d = 0; d < segs; d++) {
                    float t0 = d * stepLen / len;
                    float t1 = Math.min((d * stepLen + dashLen) / len, 1f);
                    shapeR.rectLine(CCX_PX + ldx * t0, CCY_PX + ldy * t0,
                                    CCX_PX + ldx * t1, CCY_PX + ldy * t1, 2.0f);
                }
                shapeR.end();
                // Ghost node at wall point
                shapeR.begin(ShapeRenderer.ShapeType.Filled);
                for (int r2 = 4; r2 >= 1; r2--) {
                    shapeR.setColor(0.65f, 0.25f, 1.00f, 0.10f * r2 * pulse);
                    shapeR.circle(wx2, wy2, nr * (1.5f + r2 * 1.5f), 14);
                }
                shapeR.setColor(1.00f, 0.85f, 1.00f, 0.95f);
                shapeR.circle(wx2, wy2, nr * 1.8f, 12);
                shapeR.setColor(1.00f, 1.00f, 1.00f, 1.00f);
                shapeR.circle(wx2, wy2, nr * 0.8f, 8);
                shapeR.end();
            }
        }

        batch.begin();
    }

    private void checkRelayCrosses(float delta) {
        if (!isEmberIV() || relayNodes.size == 0) return;
        // Tick down cooldowns
        for (int i = 0; i < relayCooldowns.length; i++) {
            if (relayCooldowns[i] > 0f) relayCooldowns[i] -= delta;
        }
        int needed = balls.size * MAX_RELAY_NODES;
        if (relayCooldowns.length < needed) relayCooldowns = java.util.Arrays.copyOf(relayCooldowns, needed);

        float ballR = BALL_RADIUS;
        for (int bi = 0; bi < balls.size; bi++) {
            com.badlogic.gdx.math.Vector2 bPos = balls.get(bi).getPosition();
            float cx = bPos.x, cy = bPos.y;
            // Each rail = centrifuge center → relayNodes[si] (world pos, rotates with body)
            for (int si = 0; si < relayNodes.size; si++) {
                int cdIdx = bi * MAX_RELAY_NODES + si;
                if (cdIdx >= relayCooldowns.length || relayCooldowns[cdIdx] > 0f) continue;
                com.badlogic.gdx.math.Vector2 wp = relayNodeWorldPos(si);
                float bx = wp.x, by = wp.y;
                if (segmentCircleIntersects(CENTRIFUGE_CX, CENTRIFUGE_CY, bx, by, cx, cy, ballR)) {
                    relayCooldowns[cdIdx] = RELAY_CROSS_COOLDOWN;
                    if (si < relaySegGlow.length) relaySegGlow[si] = 0.35f;
                    onRelayCross(cx, cy);
                }
            }
        }
    }

    /**
     * Snaps world pos (wx,wy) to the EmberIV rectangle wall IN LOCAL FRAME.
     * Returned Vector2 is in local (un-rotated) coords relative to centrifuge center.
     * Store this in relayNodes; use relayNodeWorldPos() to get world coords each frame.
     */
    private com.badlogic.gdx.math.Vector2 snapRelayToWall(float wx, float wy) {
        float angle = centrifugeBody.getAngle();
        float cos = (float) Math.cos(-angle), sin = (float) Math.sin(-angle);
        float dx = wx - CENTRIFUGE_CX, dy = wy - CENTRIFUGE_CY;
        // Transform to local frame
        float lx = dx * cos - dy * sin;
        float ly = dx * sin + dy * cos;
        float len = (float) Math.sqrt(lx * lx + ly * ly);
        if (len < 0.001f) return new com.badlogic.gdx.math.Vector2(RECT_HW, 0f);
        float nx = lx / len, ny = ly / len;
        float tx = Math.abs(nx) > 0.0001f ? RECT_HW / Math.abs(nx) : Float.MAX_VALUE;
        float ty = Math.abs(ny) > 0.0001f ? RECT_HH / Math.abs(ny) : Float.MAX_VALUE;
        float t  = Math.min(tx, ty);
        return new com.badlogic.gdx.math.Vector2(nx * t, ny * t);
    }

    // ---- Phase Portal helpers -------------------------------------------------------

    /** Snaps world pos to nearest wall face, returns LOCAL frame coords. */
    private com.badlogic.gdx.math.Vector2 snapPortalToNearestWall(float wx, float wy) {
        float angle = centrifugeBody.getAngle();
        float cos = (float) Math.cos(-angle), sin = (float) Math.sin(-angle);
        float dx = wx - CENTRIFUGE_CX, dy = wy - CENTRIFUGE_CY;
        float lx = dx * cos - dy * sin;
        float ly = dx * sin + dy * cos;
        // Clamp inside rect first
        lx = Math.max(-RECT_HW, Math.min(RECT_HW, lx));
        ly = Math.max(-RECT_HH, Math.min(RECT_HH, ly));
        // Distance to each wall face
        float dL = Math.abs(lx + RECT_HW), dR = Math.abs(lx - RECT_HW);
        float dB = Math.abs(ly + RECT_HH), dT = Math.abs(ly - RECT_HH);
        float min = Math.min(Math.min(dL, dR), Math.min(dB, dT));
        if (min == dL) lx = -RECT_HW;
        else if (min == dR) lx = RECT_HW;
        else if (min == dB) ly = -RECT_HH;
        else ly = RECT_HH;
        return new com.badlogic.gdx.math.Vector2(lx, ly);
    }

    /** Given portal A in local frame, returns opposite parallel wall position for portal B. */
    private com.badlogic.gdx.math.Vector2 oppositePortalLocal(com.badlogic.gdx.math.Vector2 localA) {
        if (Math.abs(Math.abs(localA.x) - RECT_HW) < 0.001f) {
            // Left/right wall — mirror x, keep y
            return new com.badlogic.gdx.math.Vector2(-localA.x, localA.y);
        } else {
            // Top/bottom wall — keep x, mirror y
            return new com.badlogic.gdx.math.Vector2(localA.x, -localA.y);
        }
    }

    /** Returns world position of portal pair[pairIdx][side] (side 0=A,1=B) by rotating by body angle. */
    private com.badlogic.gdx.math.Vector2 portalWorldPos(int pairIdx, int side) {
        float angle = centrifugeBody.getAngle();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        com.badlogic.gdx.math.Vector2 local = portalPairs.get(pairIdx)[side];
        return new com.badlogic.gdx.math.Vector2(
            CENTRIFUGE_CX + local.x * cos - local.y * sin,
            CENTRIFUGE_CY + local.x * sin + local.y * cos
        );
    }

    /** Inward wall normal for a LOCAL frame portal position (points toward interior). */
    private com.badlogic.gdx.math.Vector2 portalInwardNormalLocal(com.badlogic.gdx.math.Vector2 local) {
        if (Math.abs(Math.abs(local.x) - RECT_HW) < Math.abs(Math.abs(local.y) - RECT_HH)) {
            return new com.badlogic.gdx.math.Vector2(local.x > 0 ? -1f : 1f, 0f);
        } else {
            return new com.badlogic.gdx.math.Vector2(0f, local.y > 0 ? -1f : 1f);
        }
    }

    /** Transforms local-frame direction to world direction using current body angle. */
    private com.badlogic.gdx.math.Vector2 localDirToWorld(float lx, float ly) {
        float angle = centrifugeBody.getAngle();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        return new com.badlogic.gdx.math.Vector2(lx * cos - ly * sin, lx * sin + ly * cos);
    }

    // ---- Portal draw ---------------------------------------------------------------

    private void drawPortals() {
        if (!isEmberIV()) return;
        float pulse = 0.55f + 0.45f * MathUtils.sin(animTime * 5f);
        float rdt   = Gdx.graphics.getDeltaTime();
        for (int i = 0; i < 2; i++) {
            if (portalGlow[i] > 0f) portalGlow[i] = Math.max(0f, portalGlow[i] - rdt);
        }

        if (dragMode == PLACE_PORTAL) {
            float fw = dragStageX / PPM, fh = (dragStageY + 80f) / PPM;
            com.badlogic.gdx.math.Vector2 preA = snapPortalToNearestWall(fw, fh);
            com.badlogic.gdx.math.Vector2 preB = oppositePortalLocal(preA);
            // Connecting beam between previewed portals
            drawPortalBeam(preA, preB, pulse * 0.7f);
            drawPortalGlyph(preA, 0, pulse, 0f, true);
            drawPortalGlyph(preB, 1, pulse, 0f, true);
        }

        for (int pi = 0; pi < portalPairs.size; pi++) {
            com.badlogic.gdx.math.Vector2[] pair = portalPairs.get(pi);
            float hitA = (portalGlow.length > pi*2)   ? (portalGlow[pi*2]   > 0f ? portalGlow[pi*2]   / 0.4f : 0f) : 0f;
            float hitB = (portalGlow.length > pi*2+1) ? (portalGlow[pi*2+1] > 0f ? portalGlow[pi*2+1] / 0.4f : 0f) : 0f;
            drawPortalBeam(pair[0], pair[1], pulse * (0.4f + hitA * 0.6f));
            drawPortalGlyph(pair[0], 0, pulse, hitA, false);
            drawPortalGlyph(pair[1], 1, pulse, hitB, false);
        }
    }

    /** Dashed energy beam connecting both portals. */
    private void drawPortalBeam(com.badlogic.gdx.math.Vector2 localA, com.badlogic.gdx.math.Vector2 localB, float alpha) {
        com.badlogic.gdx.math.Vector2 wA = localToWorldPx(localA);
        com.badlogic.gdx.math.Vector2 wB = localToWorldPx(localB);
        float dx = wB.x - wA.x, dy = wB.y - wA.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 2f) return;

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        float dashLen = 14f, gap = 18f, step = dashLen + gap;
        float offset  = (animTime * 60f) % step; // animated flow A→B
        int segs = (int)(len / step) + 2;
        // Beam color: cyan when unidirectional, gold when bidirectional
        float br = portalBidirectional ? 1.00f : 0.50f;
        float bg = portalBidirectional ? 0.90f : 0.85f;
        float bb = portalBidirectional ? 0.10f : 1.00f;
        float cr2 = portalBidirectional ? 1.00f : 0.70f;
        float cg2 = portalBidirectional ? 0.95f : 0.95f;
        float cb2 = portalBidirectional ? 0.15f : 1.00f;
        // Glow pass
        for (int pass = 3; pass >= 1; pass--) {
            shapeR.begin(ShapeRenderer.ShapeType.Line);
            shapeR.setColor(br, bg, bb, 0.06f * pass * alpha);
            for (int d = 0; d < segs; d++) {
                float t0 = (d * step - offset) / len;
                float t1 = (d * step - offset + dashLen) / len;
                if (t1 < 0f || t0 > 1f) continue;
                t0 = Math.max(0f, t0); t1 = Math.min(1f, t1);
                shapeR.line(wA.x + dx*t0, wA.y + dy*t0, wA.x + dx*t1, wA.y + dy*t1);
            }
            shapeR.end();
        }
        // Core
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        shapeR.setColor(cr2, cg2, cb2, 0.65f * alpha);
        for (int d = 0; d < segs; d++) {
            float t0 = (d * step - offset) / len;
            float t1 = (d * step - offset + dashLen) / len;
            if (t1 < 0f || t0 > 1f) continue;
            t0 = Math.max(0f, t0); t1 = Math.min(1f, t1);
            shapeR.rectLine(wA.x + dx*t0, wA.y + dy*t0, wA.x + dx*t1, wA.y + dy*t1, 2.5f);
        }
        shapeR.end();
        batch.begin();
    }

    /** World pixel position of a local-frame point. */
    private com.badlogic.gdx.math.Vector2 localToWorldPx(com.badlogic.gdx.math.Vector2 local) {
        float angle = centrifugeBody.getAngle();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        return new com.badlogic.gdx.math.Vector2(
            (CENTRIFUGE_CX + local.x * cos - local.y * sin) * PPM,
            (CENTRIFUGE_CY + local.x * sin + local.y * cos) * PPM
        );
    }

    /** Full portal ring: outer halo, spinning vortex arcs, filled disc, white core, label. */
    private void drawPortalGlyph(com.badlogic.gdx.math.Vector2 local, int side,
                                  float pulse, float hit, boolean ghost) {
        com.badlogic.gdx.math.Vector2 wp = localToWorldPx(local);
        float wx = wp.x, wy = wp.y;

        // Portal visual radius — much larger than physics trigger for clarity
        float r = PORTAL_RADIUS * PPM * 2.8f;

        // default: A=cyan, B=orange; bidirectional perk: A=green, B=purple
        float cr, cg, cb;
        if (portalBidirectional) {
            cr = side == 0 ? 0.10f : 0.75f;
            cg = side == 0 ? 1.00f : 0.15f;
            cb = side == 0 ? 0.45f : 1.00f;
        } else {
            cr = side == 0 ? 0.00f : 1.00f;
            cg = side == 0 ? 0.90f : 0.50f;
            cb = side == 0 ? 1.00f : 0.05f;
        }
        float baseA = ghost ? 0.55f : 0.85f;
        float gA = baseA + hit * 0.15f;

        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeR.setProjectionMatrix(batch.getProjectionMatrix());

        // --- Outer halo: 8 fading rings ---
        for (int g = 8; g >= 1; g--) {
            shapeR.begin(ShapeRenderer.ShapeType.Line);
            shapeR.setColor(cr, cg, cb, 0.055f * g * gA * pulse);
            shapeR.circle(wx, wy, r * (1f + g * 0.28f), 28);
            shapeR.end();
        }

        // --- Spinning vortex arcs ---
        int SPOKES = 6;
        float spinA = animTime * (side == 0 ? 2.8f : -2.2f); // opposite spin per portal
        for (int k = 0; k < SPOKES; k++) {
            float baseAngle = spinA + k * (float)(Math.PI * 2.0 / SPOKES);
            // Draw arc from r*0.3 to r outward as a series of tiny lines
            int arcSegs = 8;
            float arcSpan = (float)(Math.PI * 0.35);
            shapeR.begin(ShapeRenderer.ShapeType.Filled);
            shapeR.setColor(cr, cg, cb, 0.55f * gA * pulse);
            for (int s = 0; s < arcSegs; s++) {
                float t0 = baseAngle + s * arcSpan / arcSegs;
                float t1 = baseAngle + (s + 1) * arcSpan / arcSegs;
                float rad0 = r * (0.28f + 0.72f * s / arcSegs);
                float rad1 = r * (0.28f + 0.72f * (s + 1) / arcSegs);
                shapeR.rectLine(
                    wx + (float)Math.cos(t0) * rad0, wy + (float)Math.sin(t0) * rad0,
                    wx + (float)Math.cos(t1) * rad1, wy + (float)Math.sin(t1) * rad1,
                    2.5f
                );
            }
            shapeR.end();
        }

        // --- Thick ring border ---
        for (int pass = 4; pass >= 1; pass--) {
            shapeR.begin(ShapeRenderer.ShapeType.Line);
            shapeR.setColor(cr, cg, cb, 0.18f * pass * gA);
            shapeR.circle(wx, wy, r * (1f + pass * 0.04f), 32);
            shapeR.end();
        }

        // --- Filled disc ---
        shapeR.begin(ShapeRenderer.ShapeType.Filled);
        // Dark interior
        shapeR.setColor(cr * 0.12f, cg * 0.12f, cb * 0.12f, 0.78f * gA);
        shapeR.circle(wx, wy, r * 0.92f, 28);
        // Bright ring band
        shapeR.setColor(cr, cg, cb, (0.70f + hit * 0.30f) * gA);
        shapeR.circle(wx, wy, r * 0.92f, 28);
        // Re-darken center
        shapeR.setColor(0f, 0f, 0.08f, 0.82f);
        shapeR.circle(wx, wy, r * 0.72f, 24);
        // Inner bright ring
        shapeR.setColor(cr, cg, cb, (0.55f + hit * 0.45f) * pulse);
        shapeR.circle(wx, wy, r * 0.52f, 20);
        shapeR.setColor(0f, 0f, 0.06f, 0.90f);
        shapeR.circle(wx, wy, r * 0.36f, 16);
        // White hot core
        shapeR.setColor(0.85f + cr * 0.15f, 0.85f + cg * 0.15f, 1.00f, (ghost ? 0.60f : 0.92f) * pulse);
        shapeR.circle(wx, wy, r * 0.18f, 12);
        shapeR.setColor(1f, 1f, 1f, ghost ? 0.5f : 1.0f);
        shapeR.circle(wx, wy, r * 0.08f, 8);
        shapeR.end();
        batch.begin();
    }

    // ---- Portal teleportation ------------------------------------------------------

    private void checkPortalTeleport(float delta) {
        if (!isEmberIV() || portalPairs.size == 0 || balls.size == 0) return;

        // Resize flat cooldown array: [ballIdx * MAX_PORTAL_PAIRS + pairIdx]
        int needed = balls.size * MAX_PORTAL_PAIRS;
        if (portalOrbCooldowns.length < needed)
            portalOrbCooldowns = java.util.Arrays.copyOf(portalOrbCooldowns, needed);

        // Tick down all cooldowns
        for (int i = 0; i < portalOrbCooldowns.length; i++)
            if (portalOrbCooldowns[i] > 0f) portalOrbCooldowns[i] -= delta;

        float trigR      = BALL_RADIUS + PORTAL_RADIUS;
        float exitOffset = BALL_RADIUS + 0.08f;

        for (int pi = 0; pi < portalPairs.size; pi++) {
            com.badlogic.gdx.math.Vector2[] pair = portalPairs.get(pi);
            com.badlogic.gdx.math.Vector2 wA = portalWorldPos(pi, 0);
            com.badlogic.gdx.math.Vector2 wB = portalWorldPos(pi, 1);

            com.badlogic.gdx.math.Vector2 entryDir = localDirToWorld(
                portalInwardNormalLocal(pair[0]).x, portalInwardNormalLocal(pair[0]).y);
            com.badlogic.gdx.math.Vector2 exitDir = localDirToWorld(
                portalInwardNormalLocal(pair[1]).x, portalInwardNormalLocal(pair[1]).y);

            for (int bi = 0; bi < balls.size; bi++) {
                int cdIdx = bi * MAX_PORTAL_PAIRS + pi;
                if (cdIdx < portalOrbCooldowns.length && portalOrbCooldowns[cdIdx] > 0f) continue;

                com.badlogic.gdx.physics.box2d.Body orb = balls.get(bi);
                com.badlogic.gdx.math.Vector2 pos = orb.getPosition();
                float dx = pos.x - wA.x, dy = pos.y - wA.y;
                if (dx * dx + dy * dy <= trigR * trigR) {
                    com.badlogic.gdx.math.Vector2 vel = orb.getLinearVelocity();
                    float speed    = vel.len();
                    float exitSpeed = Math.max(speed * PORTAL_EXIT_MULT, 3.0f);

                    orb.setTransform(
                        wB.x + exitDir.x * exitOffset,
                        wB.y + exitDir.y * exitOffset,
                        orb.getAngle());
                    orb.setLinearVelocity(exitDir.x * exitSpeed, exitDir.y * exitSpeed);

                    if (cdIdx < portalOrbCooldowns.length) portalOrbCooldowns[cdIdx] = PORTAL_COOLDOWN;
                    if (portalGlow.length > pi*2)   portalGlow[pi*2]   = 0.4f;
                    if (portalGlow.length > pi*2+1) portalGlow[pi*2+1] = 0.4f;

                    ShipData.get().addJoules(PORTAL_ENTER_REWARD);
                    pulseHistory.insert(0, new float[]{PORTAL_ENTER_REWARD, 0f, pos.x * PPM, pos.y * PPM});
                    continue;
                }
                // B→A if bidirectional
                if (portalBidirectional) {
                    float bx = pos.x - wB.x, by = pos.y - wB.y;
                    if (bx * bx + by * by <= trigR * trigR) {
                        com.badlogic.gdx.math.Vector2 vel = orb.getLinearVelocity();
                        float speed    = vel.len();
                        float exitSpeed = Math.max(speed * PORTAL_EXIT_MULT, 3.0f);
                        com.badlogic.gdx.math.Vector2 exitDirRev = localDirToWorld(
                            portalInwardNormalLocal(pair[0]).x, portalInwardNormalLocal(pair[0]).y);
                        orb.setTransform(
                            wA.x + exitDirRev.x * exitOffset,
                            wA.y + exitDirRev.y * exitOffset,
                            orb.getAngle());
                        orb.setLinearVelocity(exitDirRev.x * exitSpeed, exitDirRev.y * exitSpeed);
                        if (cdIdx < portalOrbCooldowns.length) portalOrbCooldowns[cdIdx] = PORTAL_COOLDOWN;
                        if (portalGlow.length > pi*2)   portalGlow[pi*2]   = 0.4f;
                        if (portalGlow.length > pi*2+1) portalGlow[pi*2+1] = 0.4f;
                        ShipData.get().addJoules(PORTAL_ENTER_REWARD);
                        pulseHistory.insert(0, new float[]{PORTAL_ENTER_REWARD, 0f, pos.x * PPM, pos.y * PPM});
                    }
                }
            }
        }
    }

    /** Returns world-space position of relayNodes[i] by rotating its local coords by body angle. */
    private com.badlogic.gdx.math.Vector2 relayNodeWorldPos(int i) {
        float angle = centrifugeBody.getAngle();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        com.badlogic.gdx.math.Vector2 local = relayNodes.get(i);
        return new com.badlogic.gdx.math.Vector2(
            CENTRIFUGE_CX + local.x * cos - local.y * sin,
            CENTRIFUGE_CY + local.x * sin + local.y * cos
        );
    }

    private boolean segmentCircleIntersects(float ax, float ay, float bx, float by,
                                             float cx, float cy, float r) {
        float dx = bx - ax, dy = by - ay;
        float fx = ax - cx, fy = ay - cy;
        float a = dx*dx + dy*dy;
        if (a < 1e-9f) return false; // zero-length segment
        float b = 2f*(fx*dx + fy*dy);
        float c = fx*fx + fy*fy - r*r;
        float disc = b*b - 4f*a*c;
        if (disc < 0f) return false;
        float sq = (float)Math.sqrt(disc);
        float t1 = (-b - sq) / (2f*a);
        float t2 = (-b + sq) / (2f*a);
        return (t1 >= 0f && t1 <= 1f) || (t2 >= 0f && t2 <= 1f);
    }

    private void onRelayCross(float wx, float wy) {
        ShipData.get().addJoules(RELAY_CROSS_REWARD);
        float px = wx * PPM, py = wy * PPM;
        pulseHistory.insert(0, new float[]{RELAY_CROSS_REWARD, 0f, px, py});
        if (pulseHistory.size > 8) pulseHistory.removeIndex(pulseHistory.size - 1);
    }

    private void drawInterns() {
        float hw = INTERN_W * 0.5f, hh = INTERN_H * 0.5f;
        float coreW = 32f, coreH = 32f, chw = coreW * 0.5f, chh = coreH * 0.5f;

        for (int i = 0, n = balls.size; i < n; i++) {
            Body    body  = balls.get(i);
            Vector2 pos   = body.getPosition();
            Vector2 vel   = body.getLinearVelocity();
            boolean cyber = "INTERN_CYBER".equals(body.getUserData());

            float speed = vel.len();
            float px    = pos.x * PPM;
            float py    = pos.y * PPM;

            // Perfect glowing dot — uniform scale, gentle pulse, no oval/squish
            float breathe = 1f + MathUtils.sin(animTime * 4.5f + i * 1.7f) * 0.12f;
            float scaleX  = breathe;
            float scaleY  = breathe;
            float drawAngle = 0f;

            // Speed-based glow intensity
            float glow = Math.min(0.55f + speed * 0.09f, 1f);

            boolean ember = isEmberIV();
            if (ember) {
                float ehw = EMBER_INTERN_DRAW * 0.5f;
                batch.setColor(1f, 1f, 1f, 0.92f + glow * 0.08f);
                batch.draw(texEmberIntern, px - ehw, py - ehw, ehw, ehw,
                    EMBER_INTERN_DRAW, EMBER_INTERN_DRAW, scaleX, scaleY, drawAngle,
                    0, 0, texEmberIntern.getWidth(), texEmberIntern.getHeight(), false, false);
            } else {
                // Outer glow layer
                if (cyber) batch.setColor(0.20f * glow, 0.75f * glow, 1.00f * glow, 0.65f);
                else       batch.setColor(1.00f * glow, 0.55f * glow, 0.10f * glow, 0.65f);
                batch.draw(texParticle, px - hw, py - hh, hw, hh,
                    INTERN_W, INTERN_H, scaleX, scaleY, drawAngle,
                    0, 0, texParticle.getWidth(), texParticle.getHeight(), false, false);

                // Bright inner core
                if (cyber) batch.setColor(0.70f, 0.95f, 1.00f, 0.90f);
                else       batch.setColor(1.00f, 0.88f, 0.50f, 0.90f);
                batch.draw(texParticleCore, px - chw, py - chh, chw, chh,
                    coreW, coreH, scaleX, scaleY, drawAngle,
                    0, 0, texParticleCore.getWidth(), texParticleCore.getHeight(), false, false);
            }
        }
        // Flying intern sprite (visual slingshot phase before entering drum)
        if (flyingInternActive) {
            float _ipx = flyingInternWX * PPM, _ipy = flyingInternWY * PPM;
            float _id  = BALL_RADIUS * PPM * 5.5f;
            batch.setColor(0.35f, 0.85f, 1.0f, 0.92f);
            batch.draw(texParticle, _ipx - _id * 0.5f, _ipy - _id * 0.5f, _id, _id);
            float _ic = _id * 0.42f;
            batch.setColor(1f, 1f, 1f, 0.88f);
            batch.draw(texParticleCore, _ipx - _ic * 0.5f, _ipy - _ic * 0.5f, _ic, _ic);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }


    private void drawHeartbeatPulse() {
        if (pulseHistory.size == 0) return;

        // Feed stacks upward from just above the centrifuge ring.
        // Index 0 = newest (bottom), index N-1 = oldest (top).
        float drumTopY  = CCY_PX + CENTRIFUGE_R * PPM;
        float lineH     = 26f;   // vertical gap between entries
        float baseY     = drumTopY + 12f;
        float cx        = CCX_PX;
        boolean isFrost = isFrostheim();
        String suffix   = isFrost ? " FS" : " SP";

        for (int i = 0; i < pulseHistory.size; i++) {
            float[] entry = pulseHistory.get(i);
            float value   = entry[0];
            float age     = entry[1];
            float t       = age / PULSE_DURATION;      // 0 = just fired, 1 = fully expired
            float alpha   = 1f - t;
            float y       = baseY + i * lineH;

            if (value < 1_000f) {
                // ---- Tier 1: small neon cyan — gentle appearance ----
                floatFont.getData().setScale(0.82f);
                floatFont.setColor(0.18f, 0.97f, 1f, alpha * 0.92f);

            } else if (value < 10_000f) {
                // ---- Tier 2: bold orange — elastic pop on entry ----
                float entryFrac = Math.min(age / 0.15f, 1f);
                float pop = 1.10f + (1f - entryFrac) * 0.60f * (float) Math.cos(entryFrac * Math.PI);
                floatFont.getData().setScale(Math.max(pop, 1.10f));
                floatFont.setColor(1f, 0.50f + alpha * 0.20f, 0.05f, alpha);

            } else {
                // ---- Tier 3: massive hot-magenta — pop + electric burst ring ----
                float entryFrac = Math.min(age / 0.10f, 1f);
                float pop = 1.35f + (1f - entryFrac) * 0.75f * (float) Math.cos(entryFrac * Math.PI);
                floatFont.getData().setScale(Math.max(pop, 1.35f));
                floatFont.setColor(1f, 0.12f + alpha * 0.22f, 0.88f + alpha * 0.12f, alpha);

                // Electric radial burst: only on the newest entry, during first 40% of lifetime
                if (i == 0 && t < 0.40f) {
                    float burstT     = t / 0.40f;
                    float burstR     = 38f + burstT * 55f;
                    float burstAlpha = (1f - burstT) * alpha;
                    for (int k = 0; k < 6; k++) {
                        float angle = k * MathUtils.PI2 / 6f + animTime * 16f;
                        float bx    = cx + MathUtils.cos(angle) * burstR;
                        float by    = y  + MathUtils.sin(angle) * burstR;
                        float bd    = 14f + burstT * 20f;
                        batch.setColor(1f, 0.15f, 0.92f, burstAlpha * 0.85f);
                        batch.draw(texGravField,
                            bx - bd * 0.5f, by - bd * 0.5f,
                            bd * 0.5f, bd * 0.5f, bd, bd, 1f, 1f, animTime * 210f,
                            0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
                    }
                }
            }

            drawFontCentered("+" + formatNumber(value) + suffix, cx, y);
        }

        batch.setColor(1f, 1f, 1f, 1f);
        floatFont.getData().setScale(1f);
    }

    private void rebuildPerksTable() {
        rightPerksTable.clearChildren();
        rightPerksTable.add(perksHeader).left().padBottom(2f).row();
        for (int i = 0; i < milestoneAchieved.length; i++) {
            if (i == 4 || !milestoneAchieved[i]) continue;
            rightPerksTable.add(perkButtons[i]).left().padTop(1f).minWidth(130f).row();
            rightPerksTable.add(perkDescLabels[i]).left().padLeft(4f).padBottom(1f).row();
        }
    }

    private void checkFreeInternCondition() {
        if (freeInternGiven) return;
        if (ShipData.get().sectorReached < 2) return;
        if (balls.size < internCap()) return;
        if (balls.size >= MAX_INTERNS) return;

        freeInternGiven      = true;
        centrifugeRpmMax     = 10.5f;
        milestoneAchieved[4] = true;

        float angle = MathUtils.random(MathUtils.PI2);
        float r     = MathUtils.random(0.5f, CENTRIFUGE_R * 0.45f);
        float wx    = CENTRIFUGE_CX + MathUtils.cos(angle) * r;
        float wy    = CENTRIFUGE_CY + MathUtils.sin(angle) * r;

        boolean fh = isFrostheim();

        BodyDef bd = new BodyDef();
        bd.type           = BodyDef.BodyType.DynamicBody;
        bd.position.set(wx, wy);
        bd.linearDamping  = fh ? frostheimBallDamping : 0f;
        bd.angularDamping = fh ? frostheimBallDamping : 0f;

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
        float kickSpd = fh ? 4.5f : 3.0f;
        body.setLinearVelocity(MathUtils.cos(kickAngle) * kickSpd, MathUtils.sin(kickAngle) * kickSpd);
        circle.dispose();
        balls.add(body);
        ballLastHitMs.put(body, System.currentTimeMillis());

        showNotif("BONUS INTERN DEPLOYED!", "Max interns reached · Ring now targets 10.0 r/s");
    }

    private void applyFrostheimDecision(int choice) {
        frostheimDecision = choice;
        decisionTable.setVisible(false);
        if (choice == 1) {
            // Convert all Icicle Nodes → Tesla Coils at the same positions
            float[] xs = new float[icicleNodes.size];
            float[] ys = new float[icicleNodes.size];
            for (int i = 0; i < icicleNodes.size; i++) {
                xs[i] = icicleNodes.get(i).getPosition().x;
                ys[i] = icicleNodes.get(i).getPosition().y;
                world.destroyBody(icicleNodes.get(i));
            }
            icicleNodes.clear();
            icicleAngOffsets.clear();
            icicleRadii.clear();
            for (int i = 0; i < xs.length; i++) spawnTeslaCoil(xs[i], ys[i]);
            showNotif("ICICLE → TESLA", "All Icicle Nodes converted to Tesla Coils");
        } else if (choice == 2) {
            // Convert all Tesla Coils → Icicle Nodes at the same positions, capped to limit
            float[] xs = new float[teslaCoils.size];
            float[] ys = new float[teslaCoils.size];
            for (int i = 0; i < teslaCoils.size; i++) {
                xs[i] = teslaCoils.get(i).getPosition().x;
                ys[i] = teslaCoils.get(i).getPosition().y;
                world.destroyBody(teslaCoils.get(i));
            }
            teslaCoils.clear();
            int icicleMax = maxIcicleNodesAllowed();
            for (int i = 0; i < xs.length && icicleNodes.size < icicleMax; i++) spawnIcicleNode(xs[i], ys[i]);
            showNotif("TESLA → ICICLE", "All Tesla Coils converted to Icicle Nodes");
        } else {
            showNotif("+2 INTERNS UNLOCKED", "Intern cap raised to 12");
        }
    }

    private void backToCheckpoint() {
        // Restore any captured orbs to Dynamic before destroying bodies
        for (int c = 0; c < spiralCaptures.size; c++)
            spiralCaptures.get(c).orb.setType(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody);
        spiralCaptures.clear();
        // Destroy every placed construction body — pause menu is visible so world is not stepping
        for (int _ci = 0; _ci < curlingBodies.size; _ci++) world.destroyBody(curlingBodies.get(_ci));
        curlingBodies.clear();
        curlingTimers.clear();
        for (int i = 0; i < bumpers.size;       i++) world.destroyBody(bumpers.get(i));
        for (int i = 0; i < attractors.size;    i++) world.destroyBody(attractors.get(i));
        for (int i = 0; i < icicleNodes.size;   i++) world.destroyBody(icicleNodes.get(i));
        for (int i = 0; i < snowPellets.size;   i++) world.destroyBody(snowPellets.get(i));
        for (int i = 0; i < teslaCoils.size;    i++) world.destroyBody(teslaCoils.get(i));
        for (int i = 0; i < kineticBlades.size; i++) world.destroyBody(kineticBlades.get(i));
        for (int i = 0; i < springPads.size;    i++) world.destroyBody(springPads.get(i));
        for (int i = 0; i < armBumpers.size;    i++) world.destroyBody(armBumpers.get(i));
        for (int i = 0; i < valleyBlades.size;  i++) world.destroyBody(valleyBlades.get(i));
        bumpers.clear();
        attractors.clear();
        icicleNodes.clear();
        icicleAngOffsets.clear();
        icicleRadii.clear();
        snowPellets.clear();
        pelletGroups.clear();
        teslaCoils.clear();
        kineticBlades.clear();
        springPads.clear();
        armBumpers.clear();
        valleyBlades.clear();
        bladeInitAngles.clear();

        // Reset placement-related upgrade state
        bumperTier  = 0;
        gravityTier = 0;
        bumperCoreR = BUMPER_RADIUS;
        placementMode = PLACE_NONE;

        // Wipe resources earned since the last checkpoint
        ShipData sd = ShipData.get();
        sd.crystals           = 0f;
        sd.energyAtLastLaunch = sd.powerGenerated;   // energy delta resets to 0

        frostheimDecision            = 0;
        frostheimIcicleUnlocked      = false;
        frostheimThirdInternUnlocked = false;
        frostheimArmBumpersActive    = false;
        decisionTable.setVisible(false);
        pauseTable.setVisible(false);

        // Ember IV reset
        emberCpI = emberCpII = emberCpIII = false;
        emberPerk1 = emberPerk2 = emberPerk3 = emberPerk4 = emberPerk5 = false;
        portalBidirectional = false;
        emberSpinReversed   = false;
        gravShiftStep       = 0;
        setGravShiftVisible(false);
        emberThirdInternUnlocked = false;
        hubStateTimer  = 0f;
        hubCycleLength = 20f;
        hubBlastFired  = false;
        hubUpgradeTier = 0;
        emberHeavyChassis = false;
        emberMagneticRim  = false;

        // Frostheim hub reset
        frostheimHubTimer      = 0f;
        frostheimHubBlastFired = false;

        // Clear saved snapshots so restore doesn't re-place destroyed structures
        ShipData sdc = ShipData.get();
        sdc.savedBumpers      = new float[0];
        sdc.savedAttractors   = new float[0];
        sdc.savedIcicleNodes  = new float[0];
        sdc.savedTeslaCoils   = new float[0];
        sdc.savedSpringPads  = new float[0];
        sdc.savedPortalPairs = new float[0];
        sdc.savedRelayNodes  = new float[0];
        for (int i = 0; i < sdc.savedMilestoneAchieved.length; i++) sdc.savedMilestoneAchieved[i] = false;
        sdc.savedFrostheimCpI = sdc.savedFrostheimCpII = sdc.savedFrostheimCpIII = false;
        sdc.savedFrostheimIcicleUnlocked = false;
        sdc.savedEmberHeavyChassis = false;
        sdc.savedEmberMagneticRim  = false;
        sdc.savedHubUpgradeTier    = 0;
        sdc.savedBallCount         = 0;
        sdc.savedKineticBladeCount           = 0;
        sdc.savedFrostheimDecision           = 0;
        sdc.savedTeslaHarvestRate            = 15f;
        sdc.savedPortalBidirectional         = false;
        sdc.savedEmberSpinReversed           = false;
        sdc.savedGravShiftStep               = 0;
        sdc.savedEmberThirdInternUnlocked    = false;
        sdc.savedFrostheimThirdInternUnlocked = false;
    }

    private void fullReset() {
        int pidx = ShipData.get().currentPlanetIndex;
        currentDef   = ShipData.PLANET_DEFS[pidx];
        currentState = ShipData.get().getState(pidx);

        // Restore any captured orbs to Dynamic before destroying bodies
        for (int c = 0; c < spiralCaptures.size; c++)
            spiralCaptures.get(c).orb.setType(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody);
        spiralCaptures.clear();
        // Destroy every placed body and all interns
        for (int _ci = 0; _ci < curlingBodies.size; _ci++) world.destroyBody(curlingBodies.get(_ci));
        curlingBodies.clear();
        curlingTimers.clear();
        for (int i = 0; i < bumpers.size;       i++) world.destroyBody(bumpers.get(i));
        for (int i = 0; i < attractors.size;    i++) world.destroyBody(attractors.get(i));
        for (int i = 0; i < icicleNodes.size;   i++) world.destroyBody(icicleNodes.get(i));
        for (int i = 0; i < snowPellets.size;   i++) world.destroyBody(snowPellets.get(i));
        for (int i = 0; i < teslaCoils.size;    i++) world.destroyBody(teslaCoils.get(i));
        for (int i = 0; i < kineticBlades.size; i++) world.destroyBody(kineticBlades.get(i));
        for (int i = 0; i < springPads.size;    i++) world.destroyBody(springPads.get(i));
        for (int i = 0; i < armBumpers.size;    i++) world.destroyBody(armBumpers.get(i));
        for (int i = 0; i < valleyBlades.size;  i++) world.destroyBody(valleyBlades.get(i));
        for (int i = 0; i < balls.size;         i++) world.destroyBody(balls.get(i));
        bumpers.clear(); attractors.clear(); icicleNodes.clear();
        icicleAngOffsets.clear(); icicleRadii.clear();
        snowPellets.clear(); pelletGroups.clear();
        teslaCoils.clear(); kineticBlades.clear(); springPads.clear(); balls.clear();
        ballLastHitMs.clear();
        armBumpers.clear(); bladeInitAngles.clear(); valleyBlades.clear();

        // Rebuild centrifuge ring with correct shape for current planet
        world.destroyBody(centrifugeBody);
        spawnWalls();

        // Physics params
        bumperTier    = 0;
        gravityTier   = 0;
        bumperCoreR   = BUMPER_RADIUS;
        gravityPull   = 20f;
        gravityFieldR = 1.2f;
        placementMode = PLACE_NONE;
        targetRPM     = CENTRIFUGE_RPM_BASE;
        currentBallRestitution = BALL_RESTITUTION;

        // Perk / milestone state
        java.util.Arrays.fill(milestoneAchieved, false);
        freeInternGiven       = false;
        centrifugeRpmMax      = CENTRIFUGE_RPM_MAX;
        lastUnlockedPerkCount = -1;

        // Frostheim state
        frostheimCpI = frostheimCpII = frostheimCpIII = false;
        frostheimDecision            = 0;
        frostheimIcicleUnlocked      = false;
        frostheimThirdInternUnlocked = false;
        frostheimArmBumpersActive    = false;
        frostheimMergeBurstUnlocked  = false;
        frostheimValleyBladesUnlocked = false;
        frostheimExtendedPelletUnlocked = false;
        frostheimDoubleCapture       = false;
        pelletMergeTime              = 5f;
        frostheimBallDamping = 0.01f;
        teslaHarvestRate     = 15f;
        frostheimHubTimer      = 0f;
        frostheimHubBlastFired = false;

        // Ember IV state
        emberCpI = emberCpII = emberCpIII = false;
        emberPerk1 = emberPerk2 = emberPerk3 = emberPerk4 = emberPerk5 = false;
        portalBidirectional = false;
        emberSpinReversed   = false;
        gravShiftStep       = 0;
        setGravShiftVisible(false);
        emberThirdInternUnlocked = false;
        emberHeavyChassis  = false;
        emberMagneticRim   = false;
        hubStateTimer  = 0f;
        hubCycleLength = 20f;
        hubBlastFired  = false;
        hubUpgradeTier = 0;
        emberGravityEnabled = false;
        emberGravityPush    = false;

        relayNodes.clear();
        relayCooldowns = new float[0];
        relaySegGlow   = new float[MAX_RELAY_NODES];

        portalPairs.clear();
        portalOrbCooldowns = new float[0];
        portalGlow         = new float[MAX_PORTAL_PAIRS * 2];

        // ShipData per-level stats
        ShipData sd = ShipData.get();
        sd.maxInternSpeed      = MAX_INTERN_SPEED;
        sd.wallEnergyMult      = 1.0f;
        sd.collisionEnergyMult = 1.0f;
        sd.bumperEnergyMult    = 5.0f;
        sd.bumperSparkValue    = 20f;
        sd.internBoostStrength = 1.5f;

        // UI
        pauseTable.setVisible(false);
        decisionTable.setVisible(false);
        notifTimer          = 0f;
        notifActive         = false;
        celebTimer          = 0f;
        celebActive         = false;
        internAddedTimer    = 0f;
        uptime              = 0f;
        lastJoules          = 0f;
        lastCrystals        = 0f;
        sparkRate           = 0f;
        activePerkPopup      = -1;
        activeFrostPerkPopup = -1;

        // Tutorial: only show on absolute first ever launch (no saved progress of any kind).
        ShipData rsd = ShipData.get();
        boolean hasPriorProgress = rsd.savedBallCount > 1
                || rsd.sectorReached >= 0
                || rsd.totalJoules > 0f
                || rsd.arrivalsCompleted > 0
                || rsd.currentPlanetIndex > 0;
        if (!hasPriorProgress && rsd.arrivalsCompleted == 0 && rsd.currentPlanetIndex == 0) {
            tutorialStep    = 0;
            tutorialStepAge = 0f;
            tutorialDone    = false;
            // Starter gift: covers first intern cost (80 SP) so tutorial step 1 is not blocked
            sd.crystals     = 100f;
        } else {
            tutorialStep = 6;
            tutorialDone = true;
            showNotif("SYSTEMS ONLINE",
                "Welcome to " + rsd.getCurrentPlanet().name
                + "\nGenerate Energy to reach your next checkpoint.");
        }

        // Reset pulse feed
        pulseHistory.clear();
        pulseTimer                  = 0f;
        accumulatedSparksThisSecond = 0f;
        prevCrystalsPulse           = 0f;
        spStallTimer                = 0f;
        lastCrystalsStall           = 0f;
        totalCrystalsEarned         = 0f;
    }

    private void commitNextPlanetDestination(ShipData sd) {
        int next = sd.currentPlanetIndex + 1;
        if (next < ShipData.PLANETS.length) {
            sd.selectPlanet(next);
            sd.commitSelectedPlanet();
        }
    }

    private void cheatAddEnergy() {
        ShipData.get().addJoules(100_000f);
        pauseTable.setVisible(false);
        showNotif("DEV CHEAT", "+100 000 Energy injected");
    }

    private void cheatAddSP() {
        ShipData.get().addCrystals(100_000f);
        pauseTable.setVisible(false);
        showNotif("DEV CHEAT", "+100 000 Space Points injected");
    }

    private void cheatPassLevel() {
        ShipData sd = ShipData.get();

        // Max out sector and resources
        sd.sectorReached = 3;
        sd.addJoules(500_000f);
        sd.addCrystals(500_000f);

        // Apply sector perks now that sr=3
        applySectorPerks();

        // Hire interns up to cap (12 at sr>=2)
        int cap = internCap();
        float[] internSpawnX = {-0.8f, 0.8f, -1.2f, 1.2f, -0.4f, 0.4f, -1.5f, 1.5f, 0f, -1.0f, 1.0f, 0f};
        float[] internSpawnY = { 0.6f, 0.6f,  0.0f, 0.0f, -0.8f,-0.8f,  0.6f, 0.6f, 0.5f,-0.4f,-0.4f,-0.9f};
        for (int i = balls.size; i < cap && i < internSpawnX.length; i++) {
            spawnBall(CENTRIFUGE_CX + internSpawnX[i], CENTRIFUGE_CY + internSpawnY[i]);
        }

        // Spawn bumpers up to max (5 at sr>=2) — evenly around inner ring
        int maxB = maxBumpersAllowed();
        float bumperR = CENTRIFUGE_R * 0.55f;
        for (int i = bumpers.size; i < maxB; i++) {
            double angle = Math.PI * 2.0 * i / maxB + Math.PI * 0.25;
            spawnCentrifugeBumper(
                CENTRIFUGE_CX + (float)(Math.cos(angle) * bumperR),
                CENTRIFUGE_CY + (float)(Math.sin(angle) * bumperR));
        }

        // Spawn gravity attractors or blades depending on planet
        if (isEmberIV()) {
            int maxBl = maxBladesAllowed();
            for (int i = kineticBlades.size; i < maxBl; i++) spawnKineticBlade();
        } else {
            int maxG = maxGravityAllowed();
            float gravR = CENTRIFUGE_R * 0.30f;
            for (int i = attractors.size; i < maxG; i++) {
                double angle = Math.PI * 2.0 * i / maxG + Math.PI * 0.5;
                spawnAttractorBumper(
                    CENTRIFUGE_CX + (float)(Math.cos(angle) * gravR),
                    CENTRIFUGE_CY + (float)(Math.sin(angle) * gravR));
            }
        }

        pauseTable.setVisible(false);
        showNotif("DEV: PASS LEVEL", "All upgrades placed — tap LAUNCH");
    }

    private void cheatSkipToNextPlanet() {
        cheatJumpToPlanet(ShipData.get().currentPlanetIndex + 1);
    }

    private void cheatJumpToPlanet(int idx) {
        ShipData sd = ShipData.get();
        idx = Math.max(0, Math.min(idx, ShipData.PLANETS.length - 1));
        sd.selectPlanet(idx);
        sd.commitSelectedPlanet();
        sd.markArrival(sd.targetPlanetDistance, 0f);
        lastPlanetIndex = idx;
        fullReset();
        texBackground.dispose();
        texRing.dispose();
        texBackground = genBackground();
        texRing       = genRingTexture((int) RING_TEX_SIZE);
        if (!sd.gravityEnabled) {
            world.setGravity(new Vector2(0f, 0f));
        } else {
            world.setGravity(new Vector2(0f, isFrostheim() ? 0f
                                           : isEmberIV()  ? -9.81f * 1.6f
                                           : GRAVITY * sd.planetGravityMultiplier));
        }
        spawnBall(CENTRIFUGE_CX - 0.6f, CENTRIFUGE_CY + 0.4f);
        spawnBall(CENTRIFUGE_CX + 0.6f, CENTRIFUGE_CY - 0.4f);
        applySectorPerks();
        pauseTable.setVisible(false);
        Gdx.app.postRunnable(() -> game.forceRebuildLab());
    }

    private void showNotif(String title, String body) {
        notifTitle  = title;
        notifBody   = body;
        notifTimer  = 0f;
        notifActive = true;
    }

    private void drawNotifOverlay() {
        float alpha = Math.min(1f, notifTimer / NOTIF_FADEIN);

        float pw = 330f, ph = 110f;
        float px = (RENDER_W - pw) * 0.5f;
        float py = renderViewport.getWorldHeight() * 0.60f;

        NinePatch popup = game.skin.get("rounded_popup", NinePatch.class);
        batch.setColor(1f, 1f, 1f, alpha);
        popup.draw(batch, px, py, pw, ph);
        batch.setColor(1f, 1f, 1f, 0.25f * alpha);
        batch.draw(texPixel, px + 18f, py + ph - 2f, pw - 36f, 2f);

        float cx = RENDER_W * 0.5f;
        float y  = py + ph - 18f;

        floatFont.getData().setScale(0.95f);
        floatFont.setColor(1f, 0.88f, 0.28f, alpha);
        drawFontCentered(notifTitle, cx, y);
        y -= 28f;

        String[] lines = notifBody.split("\n", 2);
        floatFont.getData().setScale(0.72f);
        floatFont.setColor(0.78f, 0.82f, 0.92f, alpha * 0.90f);
        for (String line : lines) {
            drawFontCentered(line, cx, y);
            y -= 20f;
        }

        // "TAP TO CONTINUE" — pulsing glow
        float pulse = 0.55f + 0.45f * MathUtils.sin(animTime * 4f);
        floatFont.getData().setScale(0.58f);
        floatFont.setColor(0.30f, 0.88f, 1f, alpha * pulse);
        drawFontCentered("TAP TO CONTINUE", cx, py + 10f);

        floatFont.getData().setScale(1f);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void showCeleb(String title, String body) {
        celebTitle  = title;
        celebBody   = body;
        celebTimer  = 0f;
        celebActive = true;
        celebSlideY = -200f;
    }

    private void drawCelebOverlay() {
        if (!celebActive) return;

        float slideProgress = Math.min(1f, celebTimer / CELEB_SLIDE);
        float ease = 1f - (float) Math.pow(1f - slideProgress, 3.0);
        float targetY = renderViewport.getWorldHeight() * (isEmberIV() ? 0.45f : 0.28f);
        celebSlideY = -200f + (targetY + 200f) * ease;

        float alpha = Math.min(1f, celebTimer / 0.12f);

        // Dark overlay
        batch.setColor(0f, 0f, 0f, 0.65f * alpha);
        batch.draw(texPixel, 0, 0, RENDER_W, renderViewport.getWorldHeight());

        // Card
        float cw = 360f, ch = 140f;
        float cx = (RENDER_W - cw) * 0.5f;
        float cy = celebSlideY;
        NinePatch card = game.skin.get("rounded_dark", NinePatch.class);
        batch.setColor(1f, 1f, 1f, alpha);
        card.draw(batch, cx, cy, cw, ch);
        batch.setColor(OdysseyTheme.ACCENT_E.r, OdysseyTheme.ACCENT_E.g, OdysseyTheme.ACCENT_E.b, alpha);
        batch.draw(texPixel, cx + 18f, cy + ch - 2f, cw - 36f, 2f);

        // Title
        floatFont.getData().setScale(1.10f);
        floatFont.setColor(OdysseyTheme.ACCENT_SP.r, OdysseyTheme.ACCENT_SP.g, OdysseyTheme.ACCENT_SP.b, alpha);
        drawFontCentered(celebTitle, RENDER_W * 0.5f, cy + ch - 22f);

        // Body lines
        floatFont.getData().setScale(0.75f);
        floatFont.setColor(OdysseyTheme.TEXT_PRI.r, OdysseyTheme.TEXT_PRI.g, OdysseyTheme.TEXT_PRI.b, alpha);
        String[] lines = celebBody.split("\n");
        float lineY = cy + ch - 52f;
        for (String line : lines) {
            drawFontCentered(line, RENDER_W * 0.5f, lineY);
            lineY -= 22f;
        }

        // "TAP TO CONTINUE" — pulsing, only after slide completes
        if (celebTimer >= CELEB_SLIDE) {
            float pulse = 0.55f + 0.45f * MathUtils.sin(animTime * 4f);
            floatFont.getData().setScale(0.58f);
            floatFont.setColor(0.30f, 0.88f, 1f, alpha * pulse);
            drawFontCentered("TAP TO CONTINUE", RENDER_W * 0.5f, cy + 10f);
        }

        floatFont.getData().setScale(1f);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawInternAddedOverlay() {
        float t = internAddedTimer / INTERN_ADDED_HOLD;
        // Fade in over first 0.3s, hold, fade out over last 0.4s
        float alpha = t > 0.94f ? (1f - t) / 0.06f
                    : t < 0.06f ? t / 0.06f
                    : 1f;
        alpha = Math.min(1f, alpha);

        // Small tooltip: higher in EmberIV because panel is taller
        float pw = 190f, ph = 44f;
        float cx = RENDER_W * 0.5f;
        float px = cx - pw * 0.5f;
        float py = isEmberIV() ? 310f : 156f;

        batch.setColor(0f, 0.05f, 0.18f, 0.90f * alpha);
        batch.draw(texPixel, px, py, pw, ph);
        // top accent line
        batch.setColor(0.27f, 1f, 0.55f, 0.70f * alpha);
        batch.draw(texPixel, px, py + ph - 2f, pw, 2f);
        batch.setColor(1f, 1f, 1f, 1f);

        float y = py + ph - 10f;
        floatFont.getData().setScale(0.62f);
        floatFont.setColor(0.27f, 1f, 0.55f, alpha);
        drawFontCentered("INTERN DEPLOYED", cx, y);
        y -= 18f;

        floatFont.getData().setScale(0.72f);
        floatFont.setColor(1f, 0.75f, 0.20f, alpha);
        drawFontCentered(String.format("%.1f  ->  %.1f r/s", internAddedOldSpeed, internAddedNewSpeed), cx, y);

        floatFont.getData().setScale(1f);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawPlacementPreview() {
        // Drag ghost: show preview above finger so thumb doesn't hide it
        if (dragMode != PLACE_NONE) {
            float rawWx = dragStageX / PPM, rawWy = (dragStageY + 80f) / PPM;
            float ddx = rawWx - CENTRIFUGE_CX, ddy = rawWy - CENTRIFUGE_CY;
            float ghostMaxR = (dragMode == PLACE_BUMPER && isFrostheim()) ? SNOWFLAKE_ARM_R * 0.95f : CENTRIFUGE_R * 0.92f;
            float dist2g = ddx * ddx + ddy * ddy;
            float ghostWx = rawWx, ghostWy = rawWy;
            if (dist2g > ghostMaxR * ghostMaxR) {
                float dist = (float) Math.sqrt(dist2g);
                ghostWx = CENTRIFUGE_CX + ddx / dist * ghostMaxR;
                ghostWy = CENTRIFUGE_CY + ddy / dist * ghostMaxR;
            }
            float alpha = 0.90f;
            float px = ghostWx * PPM, py = ghostWy * PPM;

            if (dragMode == PLACE_INTERN) {
                float ox  = dragOriginStageX, oy  = dragOriginStageY + 80f;
                float fx2 = dragStageX,       fy2 = dragStageY + 80f;
                // Slingshot direction = opposite of drag
                float dvx2 = ox - fx2, dvy2 = oy - fy2;
                float dlen2 = (float) Math.sqrt(dvx2 * dvx2 + dvy2 * dvy2);
                batch.end();
                Gdx.gl.glLineWidth(3f);
                shapeR.setProjectionMatrix(renderCam.combined);
                shapeR.begin(ShapeRenderer.ShapeType.Filled);
                // Rubber band: button → finger
                shapeR.setColor(0.35f, 0.80f, 1f, 0.80f);
                shapeR.rectLine(ox, oy, fx2, fy2, 4f);
                // Trajectory dots from button in launch direction
                if (dlen2 > 4f) {
                    float nx = dvx2 / dlen2, ny = dvy2 / dlen2;
                    for (int _d2 = 1; _d2 <= 8; _d2++) {
                        float dotX = ox + nx * _d2 * 34f;
                        float dotY = oy + ny * _d2 * 34f;
                        float dotR = 8f * (1f - _d2 * 0.08f);
                        shapeR.setColor(0.35f, 0.80f, 1f, 0.80f - _d2 * 0.08f);
                        shapeR.circle(dotX, dotY, dotR, 10);
                    }
                }
                shapeR.end();
                Gdx.gl.glLineWidth(1f);
                batch.begin();
                // Orb glow at button
                float orbD = BALL_RADIUS * PPM * 5.5f;
                batch.setColor(0.35f, 0.80f, 1f, 0.90f);
                batch.draw(texParticle, ox - orbD * 0.5f, oy - orbD * 0.5f, orbD, orbD);
                batch.setColor(1f, 1f, 1f, 0.85f);
                float coreD = orbD * 0.42f;
                batch.draw(texParticleCore, ox - coreD * 0.5f, oy - coreD * 0.5f, coreD, coreD);
                batch.setColor(1f, 1f, 1f, 1f);
            } else if (dragMode == PLACE_BUMPER && !isFrostheim()) {
                float ox = dragOriginStageX, oy = dragOriginStageY + 80f;
                float fx = dragStageX,       fy = dragStageY + 80f;
                float dvx = ox - fx, dvy = oy - fy;
                float dlen = (float) Math.sqrt(dvx * dvx + dvy * dvy);
                batch.end();
                Gdx.gl.glLineWidth(3f);
                shapeR.setProjectionMatrix(renderCam.combined);
                shapeR.begin(ShapeRenderer.ShapeType.Filled);
                // Rubber band: button → finger
                shapeR.setColor(1f, 0.55f, 0.15f, 0.80f);
                shapeR.rectLine(ox, oy, fx, fy, 4f);
                // Trajectory dots
                if (dlen > 4f) {
                    float nx = dvx / dlen, ny = dvy / dlen;
                    for (int _d = 1; _d <= 8; _d++) {
                        float dotX = ox + nx * _d * 34f;
                        float dotY = oy + ny * _d * 34f;
                        float dotR = 8f * (1f - _d * 0.08f);
                        shapeR.setColor(0.35f, 1.0f, 0.90f, 0.80f - _d * 0.08f);
                        shapeR.circle(dotX, dotY, dotR, 10);
                    }
                }
                shapeR.end();
                Gdx.gl.glLineWidth(1f);
                batch.begin();
                // Ghost bumper at button
                float sz = BUMPER_W * 1.3f;
                batch.setColor(0.35f, 1.0f, 0.90f, 0.90f);
                batch.draw(texBumper, ox - sz * 0.5f, oy - sz * 0.5f, sz, sz);
                batch.setColor(1f, 1f, 1f, 1f);
            } else if (dragMode == PLACE_BUMPER && isFrostheim()) {
                // Frostheim icicle: keep original placement ghost
                Texture icon = texCryoVent;
                float sz = BUMPER_W * 1.2f;
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(icon, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
            } else if (dragMode == PLACE_BLADE) {
                batch.setColor(1f, 0.75f, 0.20f, alpha);
                float sz = BUMPER_W * 1.2f;
                batch.draw(texBlade1, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
            } else if (dragMode == PLACE_GRAVITY && !isFrostheim()) {
                float ox3 = dragOriginStageX, oy3 = dragOriginStageY + 80f;
                float fx3 = dragStageX,       fy3 = dragStageY + 80f;
                float dvx3 = ox3 - fx3, dvy3 = oy3 - fy3;
                float dlen3 = (float) Math.sqrt(dvx3 * dvx3 + dvy3 * dvy3);
                batch.end();
                Gdx.gl.glLineWidth(3f);
                shapeR.setProjectionMatrix(renderCam.combined);
                shapeR.begin(ShapeRenderer.ShapeType.Filled);
                shapeR.setColor(0.60f, 0.30f, 1f, 0.80f);
                shapeR.rectLine(ox3, oy3, fx3, fy3, 4f);
                if (dlen3 > 4f) {
                    float nx3 = dvx3 / dlen3, ny3 = dvy3 / dlen3;
                    for (int _d3 = 1; _d3 <= 8; _d3++) {
                        float dotR3 = 8f * (1f - _d3 * 0.08f);
                        shapeR.setColor(0.70f, 0.35f, 1f, 0.80f - _d3 * 0.08f);
                        shapeR.circle(ox3 + nx3 * _d3 * 34f, oy3 + ny3 * _d3 * 34f, dotR3, 10);
                    }
                }
                shapeR.end();
                Gdx.gl.glLineWidth(1f);
                batch.begin();
                float _gfd = gravityFieldR * 2f * PPM, _gfh = _gfd * 0.5f;
                batch.setColor(0.60f, 0.30f, 1f, 0.60f);
                batch.draw(texGravField, ox3 - _gfh, oy3 - _gfh, _gfd, _gfd);
                batch.setColor(1f, 1f, 1f, 0.90f);
                float _gsz = BUMPER_W * 1.2f;
                batch.draw(texGravCenter, ox3 - _gsz * 0.5f, oy3 - _gsz * 0.5f, _gsz, _gsz);
            } else if (dragMode == PLACE_GRAVITY && isFrostheim()) {
                // Frostheim tesla: original placement ghost
                float fd = gravityFieldR * 2f * PPM, fh = fd * 0.5f;
                batch.setColor(0.60f, 0.30f, 1f, alpha * 0.55f);
                batch.draw(texGravField, px - fh, py - fh, fd, fd);
                float sz = BUMPER_W * 1.2f;
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(texTeslaCoil, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
            } else if (dragMode == PLACE_SPRING_PAD) {
                batch.setColor(1f, 0.42f, 0.05f, alpha);
                float sz = BUMPER_W * 1.2f;
                batch.draw(texBumper, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
            }
            batch.setColor(1f, 1f, 1f, 1f);
            return;
        }
        // Legacy placement mode fallback (keyboard/mouse)
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

    // ---- Spring Pad (Ember IV Objective 3) ----------------------------------------

    private void spawnSpringPad(float wx, float wy) {
        // Snap to inner ring wall at the tapped angle
        float dx    = wx - CENTRIFUGE_CX;
        float dy    = wy - CENTRIFUGE_CY;
        float angle = MathUtils.atan2(dy, dx);
        float snapR = CENTRIFUGE_R - 0.30f;   // slightly inside the wall
        float sx    = CENTRIFUGE_CX + MathUtils.cos(angle) * snapR;
        float sy    = CENTRIFUGE_CY + MathUtils.sin(angle) * snapR;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;
        bd.position.set(sx, sy);
        Body body = world.createBody(bd);

        CircleShape circle = new CircleShape();
        circle.setRadius(0.28f);   // slightly larger than standard bumper
        FixtureDef fd = new FixtureDef();
        fd.shape       = circle;
        fd.restitution = SPRING_PAD_RESTITUTION;   // 2.25 — violent catapult launch
        fd.friction    = 0.01f;
        body.createFixture(fd).setUserData("SPRING_PAD");
        circle.dispose();
        springPads.add(body);
    }

    private void drawSpringPads() {
        if (springPads.size == 0) return;
        for (int i = 0; i < springPads.size; i++) {
            Vector2 pos   = springPads.get(i).getPosition();
            float   px    = pos.x * PPM;
            float   py    = pos.y * PPM;
            float   pulse = 0.65f + MathUtils.sin(animTime * 3.8f + i * 1.1f) * 0.35f;
            float   scale = 0.90f + pulse * 0.10f;

            // Lava-orange pulsing glow aura
            float glowD = BUMPER_W * 2.0f * scale;
            batch.setColor(1f, 0.35f + pulse * 0.25f, 0.05f, 0.40f * pulse);
            batch.draw(texGravField,
                px - glowD * 0.5f, py - glowD * 0.5f,
                glowD * 0.5f, glowD * 0.5f, glowD, glowD, 1f, 1f, animTime * 40f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Core — bumper texture tinted lava-orange
            float dw = BUMPER_W * scale, dh = BUMPER_H * scale;
            batch.setColor(1f, 0.42f + pulse * 0.18f, 0.05f, 0.92f);
            batch.draw(texBumper,
                px - dw * 0.5f, py - dh * 0.5f,
                dw * 0.5f, dh * 0.5f, dw, dh, 1f, 1f, animTime * 12f,
                0, 0, texBumper.getWidth(), texBumper.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private float springPadCost() {
        int idx = springPads.size;
        return idx < SPRING_PAD_COSTS.length
            ? SPRING_PAD_COSTS[idx]
            : SPRING_PAD_COSTS[SPRING_PAD_COSTS.length - 1];
    }

    // ---- Offline Harvest (Objective 4) --------------------------------------------

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
        if (isEmberIV()) {
            // Keep flight-checkpoint unlocks for portal/rail gating
            if (!emberCpI && ShipData.get().sectorReached >= 0) emberCpI = true;
            if (!emberCpII && ShipData.get().sectorReached >= 1) emberCpII = true;
            if (!emberCpIII && ShipData.get().sectorReached >= 2) emberCpIII = true;

            // Speed-based perks — skip celebration if already earned in a previous session
            ShipData sdp = ShipData.get();
            if (!emberPerk1 && targetRPM >= 3.75f) {
                emberPerk1 = true; milestoneAchieved[0] = true;
                applyResonance();
                if (!sdp.emberPerksEarned[0]) {
                    sdp.emberPerksEarned[0] = true;
                    showCeleb("SPEED KEEP", "Interns keep 97% speed after every hit");
                    SoundManager.get().playMilestone(); triggerShake(4f, 0.12f);
                    snapshotState(); sdp.save();
                }
            }
            if (!emberPerk2 && targetRPM >= 5.25f) {
                emberPerk2 = true; milestoneAchieved[1] = true;
                sdp.wallEnergyMult = 2.0f;
                if (!sdp.emberPerksEarned[1]) {
                    sdp.emberPerksEarned[1] = true;
                    showCeleb("WALL ENERGY", "Wall bounces now generate Space Points");
                    SoundManager.get().playMilestone(); triggerShake(4f, 0.12f);
                    snapshotState(); sdp.save();
                }
            }
            if (!emberPerk3 && targetRPM >= 6.75f) {
                emberPerk3 = true; milestoneAchieved[2] = true;
                if (!sdp.emberPerksEarned[2]) {
                    sdp.emberPerksEarned[2] = true;
                    showCeleb("GRAVITY SHIFT", "Tap the center area to redirect gravity");
                    SoundManager.get().playMilestone(); triggerShake(4f, 0.12f);
                    snapshotState(); sdp.save();
                }
            }
            if (!emberPerk4 && targetRPM >= 7.5f) {
                emberPerk4 = true; milestoneAchieved[3] = true;
                portalBidirectional = true;
                if (!sdp.emberPerksEarned[3]) {
                    sdp.emberPerksEarned[3] = true;
                    showCeleb("PORTAL SYNC", "Portals now pull and push in both directions");
                    SoundManager.get().playMilestone(); triggerShake(4f, 0.12f);
                    snapshotState(); sdp.save();
                }
            }
            if (!emberPerk5 && targetRPM >= 9.75f) {
                emberPerk5 = true; milestoneAchieved[4] = true;
                emberSpinReversed = true;
                if (!sdp.emberPerksEarned[4]) {
                    sdp.emberPerksEarned[4] = true;
                    showCeleb("REVERSE FIELD", "Centrifuge spin direction reversed");
                    SoundManager.get().playMilestone(); triggerShake(4f, 0.12f);
                    snapshotState(); sdp.save();
                }
            }

            // Status label — also show flight CP gate info when relevant
            if (!emberCpI) {
                milestoneStatusLabel.setColor(0.8f, 0.8f, 1f, 1f);
                milestoneStatusLabel.setText("Reach Flight CP I to unlock Portal");
            } else if (!emberCpII) {
                milestoneStatusLabel.setColor(0.8f, 0.8f, 1f, 1f);
                milestoneStatusLabel.setText("Reach Flight CP II to unlock Rail");
            } else if (!emberPerk1) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText("Speed Keep at 3.75 r/s");
            } else if (!emberPerk2) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText("Wall Energy at 5.25 r/s");
            } else if (!emberPerk3) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText("Gravity Shift at 6.75 r/s");
            } else if (!emberPerk4) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText("Portal Sync at 7.5 r/s");
            } else if (!emberPerk5) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText("Reverse Field at 9.75 r/s");
            } else {
                milestoneStatusLabel.setColor(1f, 0.45f, 0.10f, 1f);
                milestoneStatusLabel.setText("All Nova Terra perks active.");
            }
            return;
        }

        if (isFrostheim()) {
            // ---- Frostheim: three ring-speed checkpoint perks ----
            // milestoneAchieved[1/2/3] map to Frostheim CP I/II/III respectively.

            // PERK A — Arm Bumpers (3.75 r/s = intern 3)
            if (!frostheimArmBumpersActive && targetRPM >= 3.75f) {
                frostheimArmBumpersActive = true;
                spawnArmBumpers();
                showCeleb("ARM BUMPERS ONLINE", "6 repulsors on arm tips · +25 FS per hit");
                triggerShake(5f, 0.14f);
                snapshotState(); ShipData.get().save();
            }

            // PERK B — Valley Blades (4.5 r/s = intern 4)
            if (!frostheimValleyBladesUnlocked && targetRPM >= 4.5f) {
                frostheimValleyBladesUnlocked = true;
                spawnValleyBlades();
                showCeleb("NOTCH GUARDS", "Rotary deflectors in every small arm · +8 FS per hit");
                triggerShake(5f, 0.14f);
                snapshotState(); ShipData.get().save();
            }

            // CP I — Superconductor Friction-Zero (5.0 r/s)
            if (!frostheimCpI && targetRPM >= FROSTHEIM_CP1_RPM) {
                frostheimCpI = true;
                milestoneAchieved[1] = true;
                ShipData.get().sectorReached = 0;
                frostheimBallDamping = 0.005f;
                for (int i = 0; i < balls.size; i++) {
                    balls.get(i).setLinearDamping(0.005f);
                    balls.get(i).setAngularDamping(0.005f);
                }
                showCeleb("CP I — SUPERCONDUCTOR", "Friction-Zero\nInterns arc freely in 0.4G");
                triggerShake(8f, 0.20f);
                snapshotState(); ShipData.get().save();
            }

            // CP II — Absolute Zero Resonance (6.0 r/s)
            if (!frostheimCpII && targetRPM >= FROSTHEIM_CP2_RPM) {
                frostheimCpII = true;
                milestoneAchieved[2] = true;
                ShipData.get().sectorReached = 1;
                Array<Fixture> wallFx = centrifugeBody.getFixtureList();
                for (int i = 0; i < wallFx.size; i++) wallFx.get(i).setRestitution(0.94f);
                showCeleb("CP II — ABSOLUTE ZERO", "Wall restitution 0.94\nPerfect elastic bounce");
                triggerShake(8f, 0.20f);
                snapshotState(); ShipData.get().save();
            }

            // CP III — Blizzard Overdrive (7.0 r/s)
            if (!frostheimCpIII && targetRPM >= FROSTHEIM_CP3_RPM) {
                frostheimCpIII = true;
                milestoneAchieved[3] = true;
                ShipData.get().sectorReached = 2;
                teslaHarvestRate = 30f;
                ShipData.get().maxInternSpeed = 8.5f;
                decisionTable.setVisible(true);
                showCeleb("CP III — BLIZZARD OVERDRIVE", "Choose your evolution path");
                triggerShake(8f, 0.20f);
                snapshotState(); ShipData.get().save();
            }

            // PERK C — Merge Burst (6.75 r/s = intern 7)
            if (!frostheimMergeBurstUnlocked && targetRPM >= 6.75f) {
                frostheimMergeBurstUnlocked = true;
                showCeleb("MERGE BURST", "Pellets reforming into orb · +500 energy per merge");
                triggerShake(5f, 0.14f);
                snapshotState(); ShipData.get().save();
            }

            // PERK D — Extended Pellet Time (8.25 r/s = intern 9)
            if (!frostheimExtendedPelletUnlocked && targetRPM >= 8.25f) {
                frostheimExtendedPelletUnlocked = true;
                pelletMergeTime = 7f;
                showCeleb("CRYO EXTENSION", "Pellets stay split 7 s · more time to earn");
                triggerShake(5f, 0.14f);
                snapshotState(); ShipData.get().save();
            }

            // PERK E — Double Spiral Capacity (9.75 r/s = intern 11)
            if (!frostheimDoubleCapture && targetRPM >= 9.75f) {
                frostheimDoubleCapture = true;
                showCeleb("DOUBLE VORTEX", "Each spiral now captures 2 orbs simultaneously");
                triggerShake(5f, 0.14f);
                snapshotState(); ShipData.get().save();
            }

            // Status label
            if (!frostheimCpI) {
                milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP I at %.1f r/s", FROSTHEIM_CP1_RPM));
            } else if (!frostheimCpII) {
                milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP II at %.1f r/s", FROSTHEIM_CP2_RPM));
            } else if (!frostheimCpIII) {
                milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP III at %.1f r/s", FROSTHEIM_CP3_RPM));
            } else {
                milestoneStatusLabel.setColor(0.27f, 1f, 0.55f, 1f);
                milestoneStatusLabel.setText("All Frostheim perks active. Blizzard in effect.");
            }
            return;
        }

        // ---- Solara: ring-speed milestone chain — one at a time, wait for notif dismiss ----
        int nextIdx = -1;
        for (int i = 0; i < MILESTONE_RPMS.length; i++) {
            if (i == 5) continue; // slot 5 unused on Solara
            if (!milestoneAchieved[i] && targetRPM >= MILESTONE_RPMS[i]) {
                if (notifActive) break; // wait — player must dismiss current notif first
                milestoneAchieved[i] = true;
                switch (i) {
                    case 0: applyElasticWalls();                            break;
                    case 1: applyResonance();                               break;
                    case 2: ShipData.get().wallEnergyMult = 3f;             break;
                    case 3: ShipData.get().collisionEnergyMult = 2f;        break;
                    case 4: ShipData.get().bumperMult = 3f;                 break;
                }
                showNotif("PERK UNLOCKED", MILESTONE_NAMES[i] + "\n" + MILESTONE_DESCS[i]);
                break; // one per check
            }
            if (!milestoneAchieved[i] && nextIdx < 0) nextIdx = i;
        }
        if (nextIdx >= 0) {
            milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
            milestoneStatusLabel.setText(String.format(
                "Next: %s at %.1f r/s", MILESTONE_NAMES[nextIdx], MILESTONE_RPMS[nextIdx]));
        } else {
            milestoneStatusLabel.setColor(0.27f, 1f, 0.55f, 1f);
            milestoneStatusLabel.setText("All milestones unlocked. Bay is in overdrive.");
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
        sd.bumperSparkValue      = 50f;
        sd.internBoostStrength   = 3f;
    }

    private void stepPhysics(float delta) {
        // ---- 1. Centrifuge ring speed: driven by intern count ----
        // Each orb adds +0.75 r/s to target — this is the core economy loop the player must learn.
        // pelletGroups.size counts split orbs still "in flight" — keep RPM stable during split
        int effectiveBalls = balls.size + pelletGroups.size;
        targetRPM = Math.min(CENTRIFUGE_RPM_BASE + effectiveBalls * 0.75f, centrifugeRpmMax);
        // Ember IV / Frostheim: body spins at 1/3 visual speed
        float bodyTargetRPM = (isEmberIV() || isFrostheim()) ? targetRPM * (1f / 3f) : targetRPM;
        if (isEmberIV() && emberSpinReversed) bodyTargetRPM = -bodyTargetRPM;
        boolean placing = ShipData.get().placingStructure;
        if (placing) bodyTargetRPM = 0f;
        float accel = CENTRIFUGE_RPM_ACCEL * (placing ? 8f : 1f);
        float cur = centrifugeBody.getAngularVelocity();
        if (cur < bodyTargetRPM)
            centrifugeBody.setAngularVelocity(Math.min(cur + accel * delta, bodyTargetRPM));
        else if (cur > bodyTargetRPM)
            centrifugeBody.setAngularVelocity(Math.max(cur - accel * delta, bodyTargetRPM));

        // ---- 2. Blade spin: triangle arms pivot from centrifuge center ----
        if (kineticBlades.size > 0) {
            float centAngle = centrifugeBody.getAngle();
            float omega     = centrifugeBody.getAngularVelocity();
            float tipR      = BLADE_LENGTH * 0.70f; // effective impact radius for velocity
            for (int i = 0; i < kineticBlades.size; i++) {
                float angle = bladeInitAngles.get(i) + centAngle;
                kineticBlades.get(i).setTransform(CENTRIFUGE_CX, CENTRIFUGE_CY, angle);
                float tipX = CENTRIFUGE_CX + MathUtils.cos(angle) * tipR;
                float tipY = CENTRIFUGE_CY + MathUtils.sin(angle) * tipR;
                kineticBlades.get(i).setLinearVelocity(
                    -omega * (tipY - CENTRIFUGE_CY),
                     omega * (tipX - CENTRIFUGE_CX));
                kineticBlades.get(i).setAngularVelocity(omega);
            }
        }

        // ---- 2b. Frostheim arm bumpers — rotate with centrifuge ----
        if (armBumpers.size > 0) {
            float centAng = centrifugeBody.getAngle();
            for (int i = 0; i < armBumpers.size; i++) {
                float ang = centAng + i * MathUtils.PI / 3f;
                float wx  = CENTRIFUGE_CX + MathUtils.cos(ang) * SNOWFLAKE_ARM_R * 0.90f;
                float wy  = CENTRIFUGE_CY + MathUtils.sin(ang) * SNOWFLAKE_ARM_R * 0.90f;
                armBumpers.get(i).setTransform(wx, wy, 0f);
            }
        }

        // ---- 2c. Frostheim valley notch guards — rotate with centrifuge ----
        if (valleyBlades.size > 0) {
            float centAng = centrifugeBody.getAngle();
            for (int i = 0; i < valleyBlades.size; i++) {
                float ang = centAng + i * MathUtils.PI / 3f + MathUtils.PI / 6f;
                float wx  = CENTRIFUGE_CX + MathUtils.cos(ang) * VALLEY_BLADE_R;
                float wy  = CENTRIFUGE_CY + MathUtils.sin(ang) * VALLEY_BLADE_R;
                valleyBlades.get(i).setTransform(wx, wy, ang + MathUtils.PI);
            }
        }

        // ---- 3. Standard gravity wells — centripetal pull toward attractor core ----
        for (int i = 0; i < attractors.size; i++) {
            Body    attr = attractors.get(i);
            Vector2 aPos = attr.getPosition();
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                pullVec.set(aPos).sub(ball.getPosition());
                float dist = pullVec.len();
                if (dist < gravityFieldR && dist > 0.01f) {
                    pullVec.nor();
                    float dot      = ball.getLinearVelocity().dot(pullVec);
                    float falloff  = 1f - dist / gravityFieldR;
                    float strength = (dot > 0f)
                        ? gravityPull * falloff
                        : -gravityPull * 0.8f * falloff;
                    ball.applyForceToCenter(pullVec.scl(strength * ball.getMass()), true);
                }
            }
        }

        // ---- 4. Ember IV central gravity toggle (push or pull toward square center) ----
        if (isEmberIV() && emberGravityEnabled) {
            for (int j = 0; j < balls.size; j++) {
                Body    ball  = balls.get(j);
                Vector2 bPos  = ball.getPosition();
                float   dx    = CENTRIFUGE_CX - bPos.x;
                float   dy    = CENTRIFUGE_CY - bPos.y;
                float   dist  = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0.05f) {
                    float nx = dx / dist, ny = dy / dist;
                    float sign = emberGravityPush ? -1f : 1f; // pull=toward center, push=away
                    ball.applyForceToCenter(nx * sign * EMBER_GRAVITY_FORCE * ball.getMass(),
                                            ny * sign * EMBER_GRAVITY_FORCE * ball.getMass(), true);
                }
            }
        }

        checkRelayCrosses(delta);
        checkPortalTeleport(delta);

        // ---- 5. Spiral slingshot — capture, orbit, launch ----
        if (teslaCoils.size > 0 && !ShipData.get().placingStructure) {
            // Step captured orbs
            for (int c = spiralCaptures.size - 1; c >= 0; c--) {
                SpiralCapture sc = spiralCaptures.get(c);
                sc.timer += delta;
                sc.angle += SPIRAL_ORBIT_RATE * delta;

                // Keep orb on circular orbit path
                float tx = sc.cx + MathUtils.cos(sc.angle) * SPIRAL_ORBIT_R;
                float ty = sc.cy + MathUtils.sin(sc.angle) * SPIRAL_ORBIT_R;
                sc.orb.setTransform(tx, ty, 0f);
                sc.orb.setLinearVelocity(0f, 0f);

                if (sc.timer >= SPIRAL_DURATION) {
                    // Launch: restore dynamic, apply tangent impulse
                    sc.orb.setType(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody);
                    float launchAngle = sc.angle + MathUtils.PI * 0.5f; // tangent = perpendicular to radius
                    sc.orb.setLinearVelocity(
                        MathUtils.cos(launchAngle) * SPIRAL_LAUNCH_V,
                        MathUtils.sin(launchAngle) * SPIRAL_LAUNCH_V);
                    ShipData.get().addCrystals(200f);
                    spiralCaptures.removeIndex(c);
                }
            }

            // Try to capture new orbs into idle spirals
            outer:
            for (int i = 0; i < teslaCoils.size; i++) {
                Body coil = teslaCoils.get(i);
                Vector2 cPos = coil.getPosition();
                // Check how many orbs this coil is already capturing
                int captureCount = 0;
                for (int c = 0; c < spiralCaptures.size; c++) {
                    float dx = spiralCaptures.get(c).cx - cPos.x;
                    float dy = spiralCaptures.get(c).cy - cPos.y;
                    if (dx * dx + dy * dy < 0.01f) captureCount++;
                }
                int maxPerCoil = frostheimDoubleCapture ? 2 : 1;
                if (captureCount >= maxPerCoil) continue;
                // Find nearest free orb within capture radius
                for (int j = 0; j < balls.size; j++) {
                    Body b = balls.get(j);
                    if ("PELLET".equals(b.getUserData())) continue;
                    Vector2 bPos = b.getPosition();
                    float dx = bPos.x - cPos.x, dy = bPos.y - cPos.y;
                    if (dx * dx + dy * dy < SPIRAL_CAPTURE_R * SPIRAL_CAPTURE_R) {
                        float startAngle = (float) Math.atan2(bPos.y - cPos.y, bPos.x - cPos.x);
                        b.setType(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.KinematicBody);
                        spiralCaptures.add(new SpiralCapture(b, cPos.x, cPos.y, startAngle));
                        continue outer;
                    }
                }
            }
        }

        // ---- 5. Ember IV — Cybernetic Axle Hub dual-state 20-second cycle ----
        // STATE A (Suction, 0..hubCycleLength):
        //   Neon-cyan visual (drawEmberHub), centripetal force draws all orbs to hub core.
        // STATE B (Blast, hubCycleLength..2×):
        //   Hot-magenta visual, single explosive outward impulse flings clustered orbs to blades.
        if (isEmberIV()) {
            hubStateTimer += delta;

            if (hubStateTimer < hubCycleLength) {
                // STATE A — suction with vortex spiral: inward centripetal + tangential component
                float suctionMult = (hubUpgradeTier >= 1) ? 1.35f : 1.0f;
                for (int j = 0; j < balls.size; j++) {
                    Body ball = balls.get(j);
                    pullVec.set(CENTRIFUGE_CX, CENTRIFUGE_CY).sub(ball.getPosition());
                    float dist = pullVec.len();
                    if (dist > 0.05f) {
                        float falloff = 1f - dist / CENTRIFUGE_R;
                        float nx = pullVec.x / dist;   // normalized radial (inward)
                        float ny = pullVec.y / dist;
                        float tx = -ny;                // tangential (perpendicular, CW spiral)
                        float ty =  nx;
                        float radialStr = 48f * suctionMult * falloff * ball.getMass();
                        float tangStr   = 16f * suctionMult * falloff * ball.getMass();
                        ball.applyForceToCenter(
                            nx * radialStr + tx * tangStr,
                            ny * radialStr + ty * tangStr, true);
                    }
                }
            } else if (hubStateTimer < hubCycleLength * 2f) {
                // STATE B — blast: fire outward impulse exactly once per cycle
                if (!hubBlastFired) {
                    hubBlastFired = true;
                    float blastMult = (hubUpgradeTier >= 2) ? 1.50f : 1.0f;
                    for (int j = 0; j < balls.size; j++) {
                        Body    ball = balls.get(j);
                        Vector2 bPos = ball.getPosition();
                        pullVec.set(bPos.x - CENTRIFUGE_CX, bPos.y - CENTRIFUGE_CY);
                        if (pullVec.len2() > 0.0001f) {
                            pullVec.nor();
                            float impMag = 30f * blastMult * ball.getMass();
                            ball.applyLinearImpulse(
                                pullVec.x * impMag, pullVec.y * impMag,
                                ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                        }
                    }
                }
            } else {
                hubStateTimer = 0f;
                hubBlastFired = false;
            }

            // ---- Stack dissolution: kick stalled interns into blade sweep paths ----
            // When an orb is gravity-pinned (speed < 0.35), push it toward the nearest blade.
            // A horizontal nudge is added for bottom-pinned interns to break the gravity stack
            // so the rotating blades can shatter it (awarding +35 SP on contact).
            for (int j = 0; j < balls.size; j++) {
                Body    ball = balls.get(j);
                Vector2 vel  = ball.getLinearVelocity();
                if (vel.len() < 0.35f) {
                    if (kineticBlades.size > 0) {
                        Body nearest = kineticBlades.get(0);
                        float nd = nearest.getPosition().dst(ball.getPosition());
                        for (int k = 1; k < kineticBlades.size; k++) {
                            float d = kineticBlades.get(k).getPosition().dst(ball.getPosition());
                            if (d < nd) { nd = d; nearest = kineticBlades.get(k); }
                        }
                        pullVec.set(nearest.getPosition()).sub(ball.getPosition());
                    } else {
                        pullVec.set(CENTRIFUGE_CX, CENTRIFUGE_CY).sub(ball.getPosition());
                    }
                    if (pullVec.len2() > 0.0001f) {
                        pullVec.nor();
                        ball.applyLinearImpulse(
                            pullVec.x * 0.9f * ball.getMass(),
                            pullVec.y * 0.9f * ball.getMass(),
                            ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                    }
                    // Bottom-pinned: horizontal shove breaks gravity-stack so blades can sweep
                    Vector2 bPos = ball.getPosition();
                    if (bPos.y < CENTRIFUGE_CY - CENTRIFUGE_R * 0.55f) {
                        float nudge = (bPos.x < CENTRIFUGE_CX ? 1f : -1f) * 0.6f * ball.getMass();
                        ball.applyLinearImpulse(nudge, 0f,
                            ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                    }
                }
            }
        }


        // ---- 6. Physics step ----
        float dt = Math.min(delta, 1f / 30f);
        world.step(dt, VEL_ITER, POS_ITER);

        // ---- 7. Icicle Node proximity split (Frostheim) ----
        // When a standard intern gets close to a FREE icicle node, split it into 3 snow pellets.
        // Each icicle can only handle one orb at a time — it locks until its pellets merge back.
        if (isFrostheim() && icicleNodes.size > 0) {
            final float icicleThreshold = BALL_RADIUS + ICICLE_RADIUS + 0.05f;
            int splitIdx = -1;
            int splitIcicleIdx = -1;
            Body splitBall = null;
            outer:
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                Object ud = ball.getUserData();
                if (!(ud instanceof String) || !((String) ud).startsWith("INTERN")) continue;
                // skip orbs currently held by a spiral
                boolean captured = false;
                for (int c = 0; c < spiralCaptures.size; c++) {
                    if (spiralCaptures.get(c).orb == ball) { captured = true; break; }
                }
                if (captured) continue;
                Vector2 bPos = ball.getPosition();
                for (int k = 0; k < icicleNodes.size; k++) {
                    // skip icicle if it already has a live pellet group
                    boolean busy = false;
                    for (int pg = 0; pg < pelletGroups.size; pg++) {
                        if (pelletGroups.get(pg).icicleNodeIdx == k) { busy = true; break; }
                    }
                    if (busy) continue;
                    pullVec.set(icicleNodes.get(k).getPosition()).sub(bPos);
                    if (pullVec.len() <= icicleThreshold) {
                        splitIdx = j;
                        splitIcicleIdx = k;
                        splitBall = ball;
                        break outer;
                    }
                }
            }
            if (splitIdx >= 0) {
                splitIntern(splitBall, splitIdx, splitIcicleIdx);
            }
        }

        // ---- 7b. Pellet group merge timer ----
        if (isFrostheim()) {
            for (int g = pelletGroups.size - 1; g >= 0; g--) {
                PelletGroup pg = pelletGroups.get(g);
                pg.timer += delta;
                if (pg.timer >= pelletMergeTime) {
                    // Compute average position of surviving pellets
                    float ax = 0f, ay = 0f;
                    int alive = 0;
                    for (int p = 0; p < pg.pellets.size; p++) {
                        Body pel = pg.pellets.get(p);
                        if (snowPellets.contains(pel, true)) {
                            ax += pel.getPosition().x;
                            ay += pel.getPosition().y;
                            alive++;
                        }
                    }
                    // Destroy surviving pellets
                    for (int p = 0; p < pg.pellets.size; p++) {
                        Body pel = pg.pellets.get(p);
                        if (snowPellets.contains(pel, true)) {
                            snowPellets.removeValue(pel, true);
                            world.destroyBody(pel);
                        }
                    }
                    pg.pellets.clear();
                    pelletGroups.removeIndex(g);
                    // Spawn replacement intern at average position
                    if (alive > 0) {
                        spawnBall(ax / alive, ay / alive);
                    } else {
                        spawnBall(pg.spawnX, pg.spawnY);
                    }
                    // Perk B: merge burst — award energy on re-formation
                    if (frostheimMergeBurstUnlocked) {
                        ShipData.get().addJoules(500f);
                    }
                }
            }
        }

        // ---- 7c-extra. Frostheim orb containment — teleport any escaped ball back to center ----
        if (isFrostheim()) {
            float escapeR2 = SNOWFLAKE_ARM_R * SNOWFLAKE_ARM_R;
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                Vector2 pos = ball.getPosition();
                float dx = pos.x - CENTRIFUGE_CX;
                float dy = pos.y - CENTRIFUGE_CY;
                if (dx * dx + dy * dy > escapeR2) {
                    ball.setTransform(CENTRIFUGE_CX, CENTRIFUGE_CY, 0f);
                    ball.setLinearVelocity(0f, 0f);
                }
            }
        }

        // ---- 7c-stall. Frostheim stall detection — split all orbs if 0 SP earned for 5 s ----
        // Uses a monotonic earn counter so spending crystals does NOT reset the timer.
        if (isFrostheim() && (balls.size + pelletGroups.size) > 0) {
            float curCrystals = ShipData.get().crystals;
            // Accumulate any positive gain into monotonic counter
            if (curCrystals > lastCrystalsStall) {
                totalCrystalsEarned += (curCrystals - lastCrystalsStall);
            }
            lastCrystalsStall = curCrystals;

            if (totalCrystalsEarned > 0f) {
                // Some SP earned this tick — reset stall clock and consume the earn token
                spStallTimer = 0f;
                totalCrystalsEarned = 0f;
            } else {
                spStallTimer += delta;
                if (spStallTimer >= 5f) {
                    spStallTimer = 0f;
                    for (int j = balls.size - 1; j >= 0; j--) {
                        splitIntern(balls.get(j), j, -1);
                    }
                }
            }
        }

        // ---- 7c. Icicle node co-rotation with centrifuge ----
        if (isFrostheim() && icicleNodes.size > 0 && centrifugeBody != null) {            float centAng = centrifugeBody.getAngle();
            for (int k = 0; k < icicleNodes.size; k++) {
                float offset = icicleAngOffsets.get(k);
                float radius = icicleRadii.get(k);
                float ang = centAng + offset;
                float nx = CENTRIFUGE_CX + MathUtils.cos(ang) * radius;
                float ny = CENTRIFUGE_CY + MathUtils.sin(ang) * radius;
                icicleNodes.get(k).setTransform(nx, ny, 0f);
            }
        }

        // ---- 8. Passive energy: ring speed x intern count ----
        // EmberIV body spins at 1/3 speed → multiply coefficient by 3 to keep energy equal
        float ringSpeed = centrifugeBody.getAngularVelocity();
        if (ringSpeed > 0f && balls.size > 0) {
            float energyMult = isEmberIV() ? 9f : 3f;
            ShipData.get().addJoules(ringSpeed * balls.size * energyMult * dt);
        }

        // ---- 9. Intern velocity cap ----
        float speedCap = ShipData.get().maxInternSpeed;
        for (int j = 0; j < balls.size; j++) {
            Body    ball = balls.get(j);
            Vector2 v    = ball.getLinearVelocity();
            float   spd  = v.len();
            if (spd > speedCap)
                ball.setLinearVelocity(v.x * speedCap / spd, v.y * speedCap / spd);
        }

        // ---- 10. Ambient turbulence (Frostheim) — prevents dead-zone orbits in low gravity ----
        if (isFrostheim()) {
            for (int j = 0; j < balls.size; j++) {
                if (MathUtils.random() < 0.015f) {
                    Body ball = balls.get(j);
                    pullVec.set(CENTRIFUGE_CX, CENTRIFUGE_CY).sub(ball.getPosition());
                    if (pullVec.len2() > 0.0001f) {
                        pullVec.nor();
                        ball.applyLinearImpulse(
                            pullVec.x * 0.06f, pullVec.y * 0.06f,
                            ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                    }
                }
            }
        }

        // ---- 11. Idle-pull: orbs with no collision for 5 s get one impulse toward center ----
        {
            long nowMs = System.currentTimeMillis();
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                Long last = ballLastHitMs.get(ball);
                if (last == null) { ballLastHitMs.put(ball, nowMs); continue; }
                if (nowMs - last >= 5000L) {
                    Vector2 pos = ball.getPosition();
                    pullVec.set(CENTRIFUGE_CX - pos.x, CENTRIFUGE_CY - pos.y);
                    if (pullVec.len2() > 0.0001f) {
                        pullVec.nor().scl(ball.getMass() * 5.0f);
                        ball.applyLinearImpulse(pullVec.x, pullVec.y,
                            ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                    }
                    ballLastHitMs.put(ball, nowMs);  // reset — won't fire again for another 5 s
                }
            }
        }

        // ---- 12. 1-Second Heartbeat Pulse — accumulate crystals earned this step ----
        // Crystals are added by EnergyContactListener inside world.step() (section 6 above).
        // We capture the delta after the step so every collision this frame is counted.
        ShipData psd = ShipData.get();
        float crystalDelta = psd.crystals - prevCrystalsPulse;
        if (crystalDelta > 0f) accumulatedSparksThisSecond += crystalDelta;
        prevCrystalsPulse = psd.crystals;

        pulseTimer += delta;
        if (pulseTimer >= 1.0f) {
            if (accumulatedSparksThisSecond > 0f) {
                // Push newest entry at front; evict oldest when history is full
                pulseHistory.insert(0, new float[]{accumulatedSparksThisSecond, 0f});
                if (pulseHistory.size > PULSE_HISTORY_MAX)
                    pulseHistory.removeIndex(PULSE_HISTORY_MAX);
                // Tier-3 Supernova: reactor-breathing screen shake
                if (accumulatedSparksThisSecond >= 10_000f) triggerShake(4f, 0.05f);
            }
            pulseTimer                  = 0f;
            accumulatedSparksThisSecond = 0f;
        }
        // ---- Settle curling bodies ----
        for (int _ci = curlingBodies.size - 1; _ci >= 0; _ci--) {
            com.badlogic.gdx.physics.box2d.Body _cb = curlingBodies.get(_ci);
            float _ct = curlingTimers.get(_ci) + delta;
            curlingTimers.set(_ci, _ct);
            float _speed = _cb.getLinearVelocity().len();
            boolean _settled = _ct >= CURLING_SETTLE_TIME || _speed < CURLING_SETTLE_SPEED;
            if (_settled) {
                float _fx = _cb.getPosition().x;
                float _fy = _cb.getPosition().y;
                world.destroyBody(_cb);
                curlingBodies.removeIndex(_ci);
                curlingTimers.removeIndex(_ci);
                float _dx = _fx - CENTRIFUGE_CX, _dy = _fy - CENTRIFUGE_CY;
                float _d2 = _dx * _dx + _dy * _dy;
                float _maxR = CENTRIFUGE_R * 0.90f;
                if (_d2 > _maxR * _maxR) {
                    float _d = (float) Math.sqrt(_d2);
                    _fx = CENTRIFUGE_CX + _dx / _d * _maxR;
                    _fy = CENTRIFUGE_CY + _dy / _d * _maxR;
                }
                spawnCentrifugeBumper(_fx, _fy);
            }
        }
    }

    private void updateJPS(float delta) {
        jpsTimer += delta;
        if (jpsTimer >= 1f) {
            ShipData sd = ShipData.get();
            sd.currentJPS = (sd.totalJoules - lastJoules) / jpsTimer;
            lastJoules    = sd.totalJoules;
            sparkRate     = Math.max(0f, (sd.crystals - lastCrystals) / jpsTimer);
            lastCrystals  = sd.crystals;
            jpsTimer      = 0f;
        }
    }

    // ---- Helpers ----------------------------------------------------------------

    private boolean isFrostheim() {
        return "frostheim".equals(currentDef.id());
    }

    private boolean isFrostheimPerkUnlocked(int slot) {
        switch (slot) {
            case 0: return frostheimArmBumpersActive;
            case 1: return frostheimValleyBladesUnlocked;
            case 2: return frostheimMergeBurstUnlocked;
            case 3: return frostheimExtendedPelletUnlocked;
            case 4: return frostheimDoubleCapture;
            default: return false;
        }
    }

    private boolean isEmberIV() {
        return "nova_terra".equals(currentDef.id());
    }

    private void setGravShiftVisible(boolean visible) {
        if (btnGravShift == null || gravShiftCell == null) return;
        btnGravShift.setVisible(visible);
        if (visible) {
            gravShiftCell.height(40f).width(220f).padBottom(4f).padTop(2f);
        } else {
            gravShiftCell.height(0f).width(0f).pad(0f);
        }
        gravShiftCell.getTable().invalidate();
    }

    private float price(float base) {
        if (isFrostheim()) return base * 2.5f;
        if (isEmberIV())   return base * 1.5f;
        return base;
    }

    private int internCap() {
        int sr = ShipData.get().sectorReached;
        if (isEmberIV()) {
            if (sr >= 2) return 12;
            if (sr >= 1) return 9;
            if (sr >= 0) return 7;
            return 4;
        }
        if (isFrostheim()) {
            if (sr >= 2) return 12;
            if (sr >= 1) return 9;
            if (sr >= 0) return 7;
            return 4;
        }
        if (sr >= 2) return 12;
        if (sr >= 1) return 9;
        if (sr >= 0) return 6;
        return 4;
    }

    private int maxBumpersAllowed() {
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 5;
        if (sr >= 1) return 3;
        if (sr >= 0) return 2;
        return 0;
    }

    private int maxGravityAllowed() {
        int sr = ShipData.get().sectorReached;
        if (isEmberIV()) {
            if (sr >= 2) return 3;
            if (sr >= 1) return 2;
            if (sr >= 0) return 1;
            return 0;
        }
        if (sr >= 2) return 3;
        if (sr >= 1) return 2;
        return 0;
    }

    private int maxIcicleNodesAllowed() {
        if (!isFrostheim()) return 0;
        if (frostheimDecision == 1) return 0;
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 3;
        if (sr >= 0) return 2;
        return 0;
    }

    private int maxTeslaCoilsAllowed() {
        if (!isFrostheim()) return 0;
        if (frostheimDecision == 2) return 0;
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 3;
        if (sr >= 1) return 2;
        return 0;
    }

    private boolean gravityUnlocked() {
        if (isFrostheim()) return true;
        if (isEmberIV())   return ShipData.get().sectorReached >= 0;   // unlocks at CP I
        return ShipData.get().sectorReached >= 1;
    }
    private boolean tierUpgradesUnlocked()  { return ShipData.get().sectorReached >= 2; }

    private int maxBladesAllowed() {
        if (!isEmberIV()) return 0;
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 3;   // CP III: 3rd blade available but not gated
        if (sr >= 0) return 2;   // CP I+: 2 blades
        return 0;
    }

    private float bladeCost() {
        int idx = kineticBlades.size;
        return idx < BLADE_COSTS.length ? BLADE_COSTS[idx] : BLADE_COSTS[BLADE_COSTS.length - 1];
    }

    private float internCost() {
        int idx = Math.max(0, balls.size + pelletGroups.size - 2);
        if (isEmberIV()) {
            return idx < EMBER_INTERN_COSTS.length ? EMBER_INTERN_COSTS[idx] : EMBER_INTERN_COSTS[EMBER_INTERN_COSTS.length - 1];
        }
        if (isFrostheim()) {
            return idx < FROSTHEIM_INTERN_COSTS.length ? FROSTHEIM_INTERN_COSTS[idx] : FROSTHEIM_INTERN_COSTS[FROSTHEIM_INTERN_COSTS.length - 1];
        }
        float base = idx < INTERN_COSTS.length ? INTERN_COSTS[idx] : INTERN_COSTS[INTERN_COSTS.length - 1];
        return price(base);
    }
    private int maxPortalPairsNow() {
        if (!isEmberIV()) return MAX_PORTAL_PAIRS;
        return ShipData.get().sectorReached >= 2 ? 3 : 2;
    }
    private int maxRelayNodesNow() {
        if (!isEmberIV()) return MAX_RELAY_NODES;
        return ShipData.get().sectorReached >= 2 ? 3 : 2;
    }
    private float portalCost() {
        if (portalPairs.size == 0) return 10_000f;
        if (portalPairs.size == 1) return 30_000f;
        return 200_000f;
    }
    private float relayCost() {
        if (relayNodes.size == 0) return 40_000f;
        if (relayNodes.size == 1) return 100_000f;
        if (relayNodes.size == 2) return 150_000f;
        return 200_000f;
    }
    private float bumperCost() {
        int idx = bumpers.size;
        return idx < BUMPER_COSTS.length ? BUMPER_COSTS[idx] : BUMPER_COSTS[BUMPER_COSTS.length - 1];
    }
    private float gravityCost() {
        if (isEmberIV()) {
            int idx = attractors.size;
            return idx < EMBER_GRAVITY_COSTS.length ? EMBER_GRAVITY_COSTS[idx] : EMBER_GRAVITY_COSTS[EMBER_GRAVITY_COSTS.length - 1];
        }
        int idx = attractors.size;
        return idx < GRAVITY_COSTS.length ? GRAVITY_COSTS[idx] : GRAVITY_COSTS[GRAVITY_COSTS.length - 1];
    }
    private float icicileCost() {
        int idx = icicleNodes.size;
        return idx < ICICLE_COSTS.length ? ICICLE_COSTS[idx] : ICICLE_COSTS[ICICLE_COSTS.length - 1];
    }
    private float teslaCost() {
        int idx = teslaCoils.size;
        return idx < TESLA_COIL_COSTS.length ? TESLA_COIL_COSTS[idx] : TESLA_COIL_COSTS[TESLA_COIL_COSTS.length - 1];
    }

    private float energyDeltaSinceLaunch() {
        ShipData sd = ShipData.get();
        return sd.powerGenerated - sd.energyAtLastLaunch;
    }

    private float nextCheckpointEnergyCost() {
        float[] energies = currentDef.cpEnergies();
        int nextIdx = ShipData.get().sectorReached + 1;
        if (nextIdx < 0) nextIdx = 0;
        return nextIdx < energies.length ? energies[nextIdx] : energies[energies.length - 1];
    }

    private boolean isJumpReady() {
        return energyDeltaSinceLaunch() >= nextCheckpointEnergyCost();
    }

    private String nextCPName() {
        int nextIdx = ShipData.get().sectorReached + 1;
        switch (nextIdx) {
            case 0:  return "CP I";
            case 1:  return "CP II";
            case 2:  return "CP III";
            default: return "LAND!";
        }
    }

    private void triggerShake(float mag, float duration) {
        shakeMag      = mag;
        shakeDuration = duration;
        shakeTimer    = duration;
    }

    private void updatePerkLabel(Label label, String name, float value, float base) {
        label.setText(name + " x" + String.format("%.1f", value));
        if (value > base) {
            label.setColor(0.20f, 1.00f, 0.55f, 1f);   // bright green — upgraded
            label.setFontScale(1.15f);
        } else {
            label.setColor(0.75f, 0.78f, 0.90f, 1f);   // light grey — base value
            label.setFontScale(1.05f);
        }
    }

    private void updatePerkIcon(com.badlogic.gdx.scenes.scene2d.ui.Image iconImg,
                                 com.badlogic.gdx.scenes.scene2d.ui.Image lockImg,
                                 boolean unlocked) {
        lockImg.setVisible(!unlocked);
        iconImg.setVisible(unlocked);
    }

    /**
     * Returns true when all purchasable upgrades for the current checkpoint tier are maxed out.
     * Used to gate the launch button — player must fully invest before advancing.
     */
    private boolean isFullyUpgraded() {
        int sr = ShipData.get().sectorReached;

        // Must fill intern cap — pellet groups count as the 1 orb they came from
        if (balls.size + pelletGroups.size < internCap()) return false;

        if (isFrostheim()) {
            // CP-I+: icicle slots filled and tesla maxed
            if (sr >= 0) {
                if (icicleNodes.size < maxIcicleNodesAllowed()) return false;
                if (teslaCoils.size < maxTeslaCoilsAllowed()) return false;
            }
        } else if (isEmberIV()) {
            // perks TBD — no upgrade gate for now
        } else {
            // Solara: bumpers and gravity maxed
            if (bumpers.size < maxBumpersAllowed()) return false;
            if (gravityUnlocked() && attractors.size < maxGravityAllowed()) return false;
        }

        return true;
    }

    /** Short description of what's blocking the upgrade gate, for the launch button label. */
    private String upgradeGateHint() {
        int sr = ShipData.get().sectorReached;
        int cap = internCap();
        int effective = balls.size + pelletGroups.size;
        if (effective < cap) return "HIRE " + effective + "/" + cap + " ORBS";
        if (isFrostheim()) {
            if (sr >= 0) {
                if (icicleNodes.size < maxIcicleNodesAllowed()) return "MAX ICICLE " + icicleNodes.size + "/" + maxIcicleNodesAllowed();
                if (teslaCoils.size < maxTeslaCoilsAllowed()) return "MAX TESLA " + teslaCoils.size + "/" + maxTeslaCoilsAllowed();
            }
        } else if (isEmberIV()) {
            if (sr >= 0 && kineticBlades.size < 2) return "BUY 2 BLADES " + kineticBlades.size + "/2";
            if (gravityUnlocked()) {
                int mg = maxGravityAllowed();
                if (attractors.size < mg) return "MAX GRAVITY " + attractors.size + "/" + mg;
            }
        } else {
            int mb = maxBumpersAllowed();
            if (mb > 0 && bumpers.size < mb) return "MAX BUMPERS " + bumpers.size + "/" + mb;
            if (gravityUnlocked()) {
                int mg = maxGravityAllowed();
                if (attractors.size < mg) return "MAX GRAVITY " + attractors.size + "/" + mg;
            }
        }
        return "UPGRADE";
    }

    private static void addPressEffect(TextButton btn) {
        btn.setTransform(true);
        btn.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e,
                                     float x, float y, int ptr, int b) {
                btn.setOrigin(btn.getWidth() * 0.5f, btn.getHeight() * 0.5f);
                btn.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(0.91f, 0.91f, 0.06f));
                return false;
            }
            @Override
            public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent e,
                                float x, float y, int ptr, int b) {
                btn.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1f, 1f, 0.12f));
            }
        });
    }

    private TextButton.TextButtonStyle makeTileStyle(com.badlogic.gdx.scenes.scene2d.ui.Skin skin, String key) {
        NinePatchDrawable up   = skin.get(key, NinePatchDrawable.class);
        // Pressed: same texture tinted brighter white
        NinePatchDrawable down = new NinePatchDrawable(up);
        down.getPatch().setColor(new Color(1.6f, 1.6f, 1.6f, 1f));
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font      = skin.getFont("font");
        s.up        = up;
        s.down      = down;
        s.over      = up;
        s.fontColor = OdysseyTheme.TEXT_PRI;
        return s;
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
        shapeR.dispose();
        texBackground.dispose();
        texParticle.dispose();
        texParticleCore.dispose();
        texBumper.dispose();
        texRing.dispose();
        texGravField.dispose();
        texGravCenter.dispose();
        texPixel.dispose();
        texCryoVent.dispose();
        texTeslaCoil.dispose();
        texBlade1.dispose();
        texBlade2.dispose();
        texBlade3.dispose();
        texRocket.dispose();
        texHandDrag.dispose();
        texLock.dispose();
        texPerkSpeed.dispose();
        texPerkElas.dispose();
        texPerkWall.dispose();
        texPerkColl.dispose();
        texPerkBump.dispose();
        if (texEmberPerk1 != null) { texEmberPerk1.dispose(); texEmberPerk2.dispose(); texEmberPerk3.dispose(); texEmberPerk4.dispose(); texEmberPerk5.dispose(); }
        if (texFrostPerk1 != null) { texFrostPerk1.dispose(); texFrostPerk2.dispose(); texFrostPerk3.dispose(); texFrostPerk4.dispose(); texFrostPerk5.dispose(); }
        texIconSP.dispose();
        texIconEnergy.dispose();
        // floatFont owned by skin — do not dispose here
        ui.dispose();
    }

    /** Tracks a group of 3 snow pellets that merge back into an intern after 5 seconds. */
    private static final class PelletGroup {
        final Array<Body> pellets = new Array<>();
        float timer = 0f;
        float spawnX, spawnY;
        int icicleNodeIdx = -1;  // which icicle node triggered this split; -1 = none
        PelletGroup(float x, float y) { spawnX = x; spawnY = y; }
    }

    // ---- Planet hooks bridge --------------------------------------------------------

    private final PlanetHooks planetHooks = new PlanetHooks() {
        @Override public PlanetState  state()            { return currentState; }
        @Override public ShipData     shipData()         { return ShipData.get(); }
        @Override public com.badlogic.gdx.physics.box2d.World world() { return world; }
        @Override public com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body> balls()          { return balls; }
        @Override public com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body> bumpers()        { return bumpers; }
        @Override public com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body> attractors()     { return attractors; }
        @Override public com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body> specialBodiesA() { return kineticBlades; }
        @Override public com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body> specialBodiesB() { return springPads; }
        @Override public com.badlogic.gdx.utils.Array<Float> specialData()                                  { return null; }
        @Override public float drumCenterX() { return CENTRIFUGE_CX; }
        @Override public float drumCenterY() { return CENTRIFUGE_CY; }
        @Override public float drumRadius()  { return CENTRIFUGE_R; }
        @Override public void showCelebration(String title, String body) { showNotif(title, body); }
        @Override public void triggerShake(float mag, float dur) {
            shakeMag      = mag;
            shakeDuration = dur;
            shakeTimer    = dur;
        }
    };

    /** Tracks an orb captured by a Spiral Slingshot — orbits for SPIRAL_DURATION then launches. */
    private static final class SpiralCapture {
        Body orb;
        float timer;
        float cx, cy;
        float angle;  // current orbit angle in radians
        SpiralCapture(Body b, float cx, float cy, float startAngle) {
            this.orb = b; this.timer = 0f; this.cx = cx; this.cy = cy; this.angle = startAngle;
        }
    }
}
