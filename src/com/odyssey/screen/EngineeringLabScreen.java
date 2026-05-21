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
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
    private static final float BALL_DENSITY        = 1.0f;
    private static final float BALL_RESTITUTION    = 0.90f;
    private static final float WALL_RESTITUTION    = 0.65f;
    private static final float BUMPER_RADIUS       = 0.20f;
    private static final float BUMPER_RESTITUTION  = 1.40f;

    // Level 2 — Frostheim exclusive objects (separate from Level 1 bumpers/gravity wells)
    private static final float CRYO_VENT_RADIUS    = 0.22f;   // slightly larger than bumper
    private static final float TESLA_COIL_FIELD_R  = 1.5f;    // wider harvest zone than gravity pull radius
    private static final float[] CRYO_VENT_COSTS   = {800f, 2_000f, 8_000f, 20_000f, 150_000f};
    private static final float[] TESLA_COIL_COSTS  = {5_000f, 12_000f, 35_000f, 300_000f};
    private static final float CENTRIFUGE_CX       = 4.0f;
    private static final float CENTRIFUGE_CY       = 8.5f;   // drum sits just above bottom controls panel
    private static final float CENTRIFUGE_R        = 3.0f;   // bigger drum — fills viewport width
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

    // Spark (◆) costs — indexed by current count (2 interns spawn free so index = balls.size - 2)
    // index = balls.size - 2 (interns 3–12; first 2 are free)
    private static final float[] INTERN_COSTS  = {
        80, 500, 1_200, 3_000, 7_500,   // no CP – CP I  (interns 3–7)
        30_000, 40_000, 50_000,         // CP II         (interns 8–10)
        150_000, 200_000                // CP III        (interns 11–12)
    };
    // Crystal costs — linear, separate currency from joules
    private static final float[] BUMPER_COSTS  = {500, 1000, 5000, 10000, 100_000};
    private static final float[] GRAVITY_COSTS = {3000, 6000, 20000, 200_000};
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

    // Solara (Level 1) checkpoint energy thresholds
    private static final float[] SOLARA_CP_ENERGIES = {2_000f, 10_000f, 50_000f, 100_000f};

    // Frostheim (Level 3) checkpoint energy thresholds
    private static final float[] FROSTHEIM_CP_ENERGIES = {4_000f, 24_000f, 120_000f, 150_000f};

    // Ember IV (Level 2) checkpoint energy thresholds — 1.6G high-yield
    private static final float[] EMBER_CP_ENERGIES = {8_000f, 30_000f, 100_000f, 180_000f};

    // Ember IV: Kinetic Blade costs (◆) — one blade unlocks per CP
    private static final float[] BLADE_COSTS = {1_500f, 4_000f, 10_000f};

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

    // Sprite draw sizes in pixels
    private static final float INTERN_W       = 80f;   // particle glow draw size on screen
    private static final float INTERN_H       = 80f;
    private static final float BUMPER_W       = 48f;
    private static final float BUMPER_H       = 48f;
    private static final float RING_TEX_SIZE  = 360f;  // matches drum diameter (3.0*60*2)

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
    private OrthographicCamera renderCam;
    private ExtendViewport     renderViewport;
    private Texture            texBackground;
    private Texture            texParticle;       // glowing energy orb
    private Texture            texParticleCore;   // bright inner core
    private Texture            texBumper;
    private Texture            texRing;
    private Texture            texGravField;
    private Texture            texGravCenter;     // singularity core icon for gravity wells
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
    private Texture texIconSP, texIconEnergy;
    private com.badlogic.gdx.scenes.scene2d.ui.Image uiIconSP, uiIconEnergy;

    // Perk tap popup state
    private int   activePerkPopup = -1;   // milestone index 0-4, or -1 for none
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
    private final Array<Body> balls        = new Array<>();
    private final Array<Body> bumpers      = new Array<>();   // Level 1: Solara standard bumpers
    private final Array<Body> attractors   = new Array<>();   // Level 1: Solara / Ember IV gravity wells
    private final Array<Body> cryoVents    = new Array<>();   // Level 3: Frostheim Cryo-Vent launchers
    private final Array<Body> teslaCoils   = new Array<>();   // Level 3: Frostheim Tesla Coil harvesters
    private final Array<Body>  kineticBlades     = new Array<>();  // Level 2: Ember IV Kinetic Radius Blades
    private final Array<Float> bladeInitAngles   = new Array<>();  // initial placement angle for each blade (orbit tracking)
    private final Vector2     pullVec    = new Vector2();
    private final Vector3     touchWorld = new Vector3();
    private int   placementMode    = PLACE_NONE;
    private static final int PLACE_INTERN = 5;
    private int   dragMode         = PLACE_NONE;
    private float dragStageX, dragStageY;
    private InputMultiplexer inputMux;
    private float jpsTimer         = 0f;
    private float lastJoules       = 0f;
    private float uptime           = 0f;

    // Upgradeable physics parameters (mutated by upgrade purchases)
    private float gravityPull   = 85f;    // strong pull
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
    private Label      ringSpeedLabel;
    private Label      configLabel;
    private Label      milestoneStatusLabel;
    private TextButton btnJumpReady;
    private Table      pauseTable;

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
    private int   lastPlanetIndex = -1;           // tracks planet changes for texture regen

    // Speed tooltip (small popup above r/s label, auto-hides after 5s)
    private static final float INTERN_ADDED_HOLD = 5.0f;
    private float internAddedTimer    = 0f;
    private float internAddedOldSpeed = 0f;
    private float internAddedNewSpeed = 0f;
    private float lastDisplayedSpeed  = -1f; // throttle ringSpeedLabel setText

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
    private boolean frostheimCryoUnlocked        = false;  // 400❅ — unlocks Cryo-Vent slot
    private boolean frostheimThirdInternUnlocked = false;  // 800❅ — spawns 3rd intern

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
    private boolean emberThirdInternUnlocked = false;  // 1200◆ — unlock + spawn 3rd intern

    // Cybernetic Axle Hub dual-state clock
    private float   hubStateTimer  = 0f;
    private float   hubCycleLength = 20f;   // CP III shortens to 10f
    private boolean hubBlastFired  = false; // fires once on each STATE B entry
    // Hub upgrade tier: 0=base, 1=suction×1.35, 2=blast×1.50, 3=cycle halved to 10s
    private int     hubUpgradeTier = 0;

    // Ember IV: Volcanic Spring-Pads — static rim fixtures that catapult orbs on contact
    private final Array<Body> springPads = new Array<>();
    private boolean emberHeavyChassis  = false; // CP I: density 3.5
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
    // pulseHistory entries: float[]{value, age}  (newest = index 0)
    private final Array<float[]> pulseHistory  = new Array<>();
    private static final int   PULSE_HISTORY_MAX = 5;
    private static final float PULSE_DURATION    = 5.0f;  // each entry fades over 5 s

    // Screen shake (parameterized)
    private float shakeTimer    = 0f;
    private float shakeDuration = 0.05f;
    private float shakeMag      = 4f;

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
        texBumper       = genBumperTexture(48);
        texRing         = genRingTexture((int) RING_TEX_SIZE);
        texGravField    = genGravFieldTexture(128);
        texGravCenter   = genGravCenterTexture(48);
        texCryoVent     = genCryoVentTexture(64);
        texTeslaCoil    = genTeslaCoilTexture(64);
        texBlade1       = genBladeTextureA(96);
        texBlade2       = genBladeTextureB(96);
        texBlade3       = genBladeTextureC(96);
        texRocket       = genRocketTexture(56);
        texLock         = genLockTexture(28);
        texPerkSpeed    = genPerkIconSpeed(40);
        texPerkElas     = genPerkIconElas(40);
        texPerkWall     = genPerkIconWall(40);
        texPerkColl     = genPerkIconColl(40);
        texPerkBump     = genPerkIconBump(40);
        texIconSP       = genIconCrystal(20);
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
        // Gradient palette [r0, r_t, g0, g_t, b0, b_t] (t=0 = bottom of screen, t=1 = top)
        float[] gp = switch (pidx) {
            case 1  -> new float[]{0.040f,0.030f, 0.010f,0.008f, 0.005f,0.003f}; // Ember IV  volcanic
            case 2  -> new float[]{0.010f,0.008f, 0.018f,0.028f, 0.048f,0.065f}; // Frostheim ice
            case 3  -> new float[]{0.005f,0.006f, 0.022f,0.025f, 0.028f,0.040f}; // Cryon Reach teal
            case 4  -> new float[]{0.022f,0.018f, 0.016f,0.014f, 0.007f,0.004f}; // Helios Forge heat
            default -> new float[]{0.015f,0.020f, 0.018f,0.025f, 0.055f,0.070f}; // Solara blue
        };
        // Chamber glow color [glowR, glowG, glowB]
        float[] gc = switch (pidx) {
            case 1  -> new float[]{0.90f, 0.22f, 0.04f}; // Ember IV  lava-orange
            case 2  -> new float[]{0.48f, 0.72f, 1.00f}; // Frostheim ice-blue
            case 3  -> new float[]{0.05f, 0.85f, 0.78f}; // Cryon Reach teal
            case 4  -> new float[]{0.95f, 0.82f, 0.35f}; // Helios Forge solar
            default -> new float[]{0.12f, 0.62f, 1.00f}; // Solara cyan
        };
        // Star tint multipliers [sr, sg, sb]
        float[] st = switch (pidx) {
            case 1  -> new float[]{1.05f, 0.82f, 0.75f}; // Ember IV  reddish
            case 2  -> new float[]{0.88f, 0.94f, 1.05f}; // Frostheim blueish
            case 3  -> new float[]{0.82f, 1.05f, 0.98f}; // Cryon Reach teal
            case 4  -> new float[]{1.05f, 0.98f, 0.75f}; // Helios Forge warm
            default -> new float[]{1.00f, 1.00f, 1.00f}; // Solara neutral
        };

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
        float cR = CENTRIFUGE_R * PPM;

        // Dark inner area (darker shade of the planet gradient)
        for (int y = (int)(cCY - cR - 2); y <= (int)(cCY + cR + 2); y++) {
            for (int x = (int)(cCX - cR - 2); x <= (int)(cCX + cR + 2); x++) {
                if (x < 0 || x >= W || y < 0 || y >= H) continue;
                float dx = x - cCX, dy = y - cCY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= cR - 4) {
                    float t = (float) y / H;
                    pm.setColor((gp[0] + t * gp[1]) * 0.55f,
                                (gp[2] + t * gp[3]) * 0.55f,
                                (gp[4] + t * gp[5]) * 0.55f, 1f);
                    pm.drawPixel(x, y);
                }
            }
        }

        // Planet-themed glow ring around chamber
        for (int y = (int)(cCY - cR - 20); y <= (int)(cCY + cR + 20); y++) {
            for (int x = (int)(cCX - cR - 20); x <= (int)(cCX + cR + 20); x++) {
                if (x < 0 || x >= W || y < 0 || y >= H) continue;
                float dx = x - cCX, dy = y - cCY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float ring = Math.abs(dist - cR);
                if (ring < 14) {
                    float glow = (1f - ring / 14f) * 0.55f;
                    float t = (float) y / H;
                    float br = gp[0] + t * gp[1], bg = gp[2] + t * gp[3], bb = gp[4] + t * gp[5];
                    pm.setColor(Math.min(br + glow * gc[0], 1f),
                                Math.min(bg + glow * gc[1], 1f),
                                Math.min(bb + glow * gc[2], 1f), 1f);
                    pm.drawPixel(x, y);
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

        int pidx = ShipData.get().currentPlanetIndex;
        // Ring fill color: [r_base, r_peak, g_base, g_peak, b_base, b_peak]
        float[] rc = switch (pidx) {
            case 1  -> new float[]{0.70f,0.28f, 0.18f,0.22f, 0.04f,0.04f}; // Ember IV  orange-red
            case 2  -> new float[]{0.50f,0.40f, 0.72f,0.22f, 0.90f,0.10f}; // Frostheim ice-white
            case 3  -> new float[]{0.04f,0.08f, 0.50f,0.38f, 0.46f,0.40f}; // Cryon Reach teal
            case 4  -> new float[]{0.80f,0.18f, 0.68f,0.24f, 0.20f,0.16f}; // Helios Forge yellow
            default -> new float[]{0.07f,0.23f, 0.48f,0.42f, 0.82f,0.18f}; // Solara cyan-blue
        };
        // Segment mark color [r, g, b]
        float[] mc = switch (pidx) {
            case 1  -> new float[]{1.00f, 0.65f, 0.25f}; // Ember IV  orange
            case 2  -> new float[]{0.85f, 0.95f, 1.00f}; // Frostheim ice-white
            case 3  -> new float[]{0.25f, 1.00f, 0.88f}; // Cryon Reach teal
            case 4  -> new float[]{1.00f, 0.90f, 0.40f}; // Helios Forge yellow
            default -> new float[]{0.60f, 0.92f, 1.00f}; // Solara cyan
        };

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
        batch = new SpriteBatch();
        renderCam = new OrthographicCamera();
        renderViewport = new ExtendViewport(RENDER_W, RENDER_H, renderCam);
        renderCam.position.set(RENDER_W / 2f, RENDER_H / 2f, 0f);
        floatFont   = game.skin.getFont("float");
        floatLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    }

    // ---- Physics setup ----------------------------------------------------------

    private void buildPhysics() {
        world = new World(new Vector2(0, GRAVITY * ShipData.get().planetGravityMultiplier), true);
        world.setContactListener(new EnergyContactListener());

        physCam = new OrthographicCamera();
        physViewport = new ExtendViewport(WORLD_W, WORLD_H, physCam);
        physCam.position.set(WORLD_W * 0.5f, WORLD_H * 0.5f, 0f);

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
        boolean fh = isFrostheim();

        BodyDef bd = new BodyDef();
        bd.type           = BodyDef.BodyType.DynamicBody;
        bd.position.set(x, y);
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

    private void spawnCryoVent(float wx, float wy) {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;
        bd.position.set(wx, wy);
        CircleShape circle = new CircleShape();
        circle.setRadius(CRYO_VENT_RADIUS);
        FixtureDef fd  = new FixtureDef();
        fd.shape       = circle;
        fd.restitution = 0f;    // launcher, not a bouncer — zero elastic return
        fd.friction    = 0.8f;  // sticky so interns slow against the nozzle before launch
        Body body = world.createBody(bd);
        body.createFixture(fd);
        body.setUserData("CRYO_VENT");
        circle.dispose();
        cryoVents.add(body);
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

    public void spawnKineticBlade(float angleDegrees) {
        float angleRad = angleDegrees * MathUtils.degreesToRadians;
        // Initial position — orbit tracking in stepPhysics will keep this correct every frame
        float bladeR = CENTRIFUGE_R - BLADE_LENGTH * 0.5f;
        float bx = CENTRIFUGE_CX + MathUtils.cos(angleRad) * bladeR;
        float by = CENTRIFUGE_CY + MathUtils.sin(angleRad) * bladeR;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(bx, by);
        bd.angle = angleRad;

        Body body = world.createBody(bd);

        PolygonShape box = new PolygonShape();
        box.setAsBox(BLADE_LENGTH * 0.5f, BLADE_WIDTH * 0.5f);

        FixtureDef fd = new FixtureDef();
        fd.shape       = box;
        fd.restitution = 1.55f;
        fd.density     = 0f;
        fd.friction    = 0f;
        body.createFixture(fd);
        body.setUserData("KINETIC_BLADE");
        box.dispose();
        // Store placement angle relative to current centrifuge angle so the blade orbits with the ring
        bladeInitAngles.add(angleRad - centrifugeBody.getAngle());
        kineticBlades.add(body);
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
                if (milestoneAchieved[milestoneIdx]) {
                    activePerkPopup = milestoneIdx;
                    perkPopupTimer  = 0f;
                }
            }
        });
        return slot;
    }

    private Table makeIconLayer(Texture tex, float iconSize) {
        return makeIconLayer(new TextureRegion(tex), iconSize);
    }

    private Table makeIconLayer(TextureRegion region, float iconSize) {
        Image img = new Image(new TextureRegionDrawable(region));
        img.setTouchable(Touchable.disabled);
        Table t = new Table();
        t.top().padTop(22f);
        t.add(img).size(iconSize).row();
        t.setTouchable(Touchable.disabled);
        return t;
    }

    private void buildUI() {
        ui = new Stage(new ExtendViewport(RENDER_W, RENDER_H));

        Color panelBg     = new Color(0.04f, 0.06f, 0.16f, 0.92f);
        Color panelBgSolid = new Color(0.04f, 0.06f, 0.18f, 0.97f);

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
        topPlanetLabel.setFontScale(0.85f);
        topPlanetLabel.setColor(0.20f, 0.85f, 1f, 1f);

        topCheckpointLabel = new Label("Status: Pre-Checkpoint", game.skin);
        topCheckpointLabel.setFontScale(0.65f);
        topCheckpointLabel.setColor(0.80f, 0.82f, 0.96f, 1f);

        topGravLabel = new Label("Gravity: 1.0G Standard", game.skin);
        topGravLabel.setFontScale(0.54f);
        topGravLabel.setColor(0.45f, 0.90f, 0.48f, 0.85f);

        topYieldsLabel = new Label("Base Yields: Collision 20 SP | Wall 0.5 SP", game.skin);
        topYieldsLabel.setFontScale(0.50f);
        topYieldsLabel.setColor(0.48f, 0.85f, 0.48f, 0.75f);

        milestoneStatusLabel = new Label("Status: Pre-Checkpoint", game.skin);
        milestoneStatusLabel.setFontScale(0.50f);
        milestoneStatusLabel.setColor(1f, 0.78f, 0.25f, 0.88f);

        // Info toggle button — shows/hides the dense secondary stats
        TextButton.TextButtonStyle infoStyle = new TextButton.TextButtonStyle();
        infoStyle.font      = game.skin.getFont("font");
        infoStyle.up        = game.skin.newDrawable("white", new Color(0.08f, 0.12f, 0.28f, 0.80f));
        infoStyle.down      = game.skin.newDrawable("white", new Color(0.15f, 0.22f, 0.45f, 1.00f));
        infoStyle.over      = infoStyle.down;
        infoStyle.fontColor = new Color(0.50f, 0.78f, 1f, 1f);
        TextButton btnInfo = new TextButton("i", infoStyle);
        btnInfo.getLabel().setFontScale(0.75f);

        Table infoDetails = new Table();
        infoDetails.left();
        infoDetails.add(topGravLabel).left().row();
        infoDetails.add(topYieldsLabel).left().padTop(1f).row();
        infoDetails.add(milestoneStatusLabel).left().padTop(1f).row();
        infoDetails.setVisible(false);

        btnInfo.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                infoDetails.setVisible(!infoDetails.isVisible());
            }
        });

        Table planetRow = new Table();
        planetRow.add(topPlanetLabel).left().expandX();
        planetRow.add(btnInfo).right().size(22f, 20f);

        topLeft.add(planetRow).growX().row();
        topLeft.add(topCheckpointLabel).left().padTop(2f).row();
        topLeft.add(infoDetails).left().padTop(1f).row();

        // ---- Center column: Energy current/needed ----
        Table topCenter = new Table();
        topCenter.top();

        topSpRateHeaderLabel = new Label("ENERGY", game.skin);
        topSpRateHeaderLabel.setFontScale(0.68f);
        topSpRateHeaderLabel.setColor(0.25f, 1.00f, 0.45f, 0.90f);

        topSpValueLabel = new Label("0 / 0", game.skin);
        topSpValueLabel.setFontScale(1.40f);
        topSpValueLabel.setColor(0.20f, 1.00f, 0.50f, 1f);

        outputLabel = new Label("", game.skin); // hidden — kept to avoid null refs

        uiIconEnergy = new com.badlogic.gdx.scenes.scene2d.ui.Image(
            new TextureRegionDrawable(new TextureRegion(texIconEnergy)));
        uiIconEnergy.setColor(0.25f, 1.00f, 0.45f, 0.90f);

        Table energyHeader = new Table();
        energyHeader.add(uiIconEnergy).size(12f, 18f).padRight(3f);
        energyHeader.add(topSpRateHeaderLabel);

        topCenter.add(energyHeader).center().row();
        topCenter.add(topSpValueLabel).center().padTop(3f).row();

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

        topRight.add(btnMenu).right().padTop(6f).width(32f).height(26f).row();

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
                sd2.internsLeftOnPlanet[sd2.currentPlanetIndex] = balls.size;
                sd2.lastFarmingTimestamp = System.currentTimeMillis();
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

        ringSpeedLabel.setFontScale(1.10f);
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

        Table statsRow = new Table();
        statsRow.add(spCell).width(210f).height(28f).left();
        statsRow.add(speedBox).width(210f).height(28f);
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
        panel.add(perkStrip).growX().padBottom(3f).row();

        panel.add(statsRow).width(420f).padBottom(3).row();

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
        Stack stackAdd = new Stack(); stackAdd.add(btnAdd); stackAdd.add(makeIconLayer(texParticle,   62f));
        Stack stackBmp = new Stack(); stackBmp.add(btnBumper); stackBmp.add(makeIconLayer(texBumper,     58f));
        Stack stackGrv = new Stack(); stackGrv.add(btnGravityWell); stackGrv.add(makeIconLayer(texGravCenter, 58f));
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
                boolean frostheimUnlock = isFrostheim() && !frostheimThirdInternUnlocked
                        && sd2.sectorReached < 0 && balls.size >= internCap() && sd2.crystals >= 800f;
                boolean emberUnlock = isEmberIV() && !emberThirdInternUnlocked
                        && sd2.sectorReached < 0 && balls.size >= internCap() && sd2.crystals >= 1200f;
                boolean normalHire  = balls.size < internCap() && sd2.crystals >= internCost();
                if (!frostheimUnlock && !emberUnlock && !normalHire) return tutorialStep == 1; // capture for step 1 even if not affordable
                dragMode   = PLACE_INTERN;
                dragStageX = event.getStageX();
                dragStageY = event.getStageY();
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
                float spawnSX = dragStageX, spawnSY = dragStageY + 80f;
                float wx = spawnSX / PPM, wy = spawnSY / PPM;
                float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
                if (ddx * ddx + ddy * ddy >= CENTRIFUGE_R * CENTRIFUGE_R) return;
                ShipData sd2 = ShipData.get();
                if (isFrostheim() && !frostheimThirdInternUnlocked && sd2.sectorReached < 0
                        && balls.size >= internCap() && sd2.spendCrystals(800f)) {
                    frostheimThirdInternUnlocked = true;
                    internAddedOldSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    spawnBall(wx, wy);
                    internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    internAddedTimer    = INTERN_ADDED_HOLD;
                    showNotif("3RD INTERN UNLOCKED", "Cap now 3 · Keep generating for CP I");
                    SoundManager.get().playHire();
                    triggerShake(2f, 0.06f);
                    return;
                }
                if (isEmberIV() && !emberThirdInternUnlocked && sd2.sectorReached < 0
                        && balls.size >= internCap() && sd2.spendCrystals(1200f)) {
                    emberThirdInternUnlocked = true;
                    internAddedOldSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    spawnBall(wx, wy);
                    internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    internAddedTimer    = INTERN_ADDED_HOLD;
                    showNotif("3RD INTERN UNLOCKED", "Cap now 3 · Reach CP I for 5 interns");
                    SoundManager.get().playHire();
                    triggerShake(2f, 0.06f);
                    return;
                }
                if (balls.size < internCap() && sd2.spendCrystals(internCost())) {
                    internAddedOldSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    spawnBall(wx, wy);
                    internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    internAddedTimer    = INTERN_ADDED_HOLD;
                    SoundManager.get().playHire();
                    triggerShake(2f, 0.06f);
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
                    eligible = (!frostheimCryoUnlocked && sd2.sectorReached < 0 && sd2.crystals >= 400f)
                               || (frostheimCryoUnlocked && cryoVents.size < maxCryoVentsAllowed() && sd2.crystals >= cryoVentCost());
                } else if (isEmberIV()) {
                    eligible = kineticBlades.size < maxBladesAllowed() && sd2.crystals >= bladeCost();
                } else {
                    eligible = bumpers.size < maxBumpersAllowed() && sd2.crystals >= bumperCost();
                }
                if (!eligible) return false;
                dragMode   = isFrostheim() ? PLACE_BUMPER
                           : isEmberIV()   ? PLACE_BLADE
                           :                 PLACE_BUMPER;
                dragStageX = event.getStageX();
                dragStageY = event.getStageY();
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
                if (dm == PLACE_NONE) return;
                float wx = dragStageX / PPM, wy = (dragStageY + 80f) / PPM;
                float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
                if (ddx * ddx + ddy * ddy >= CENTRIFUGE_R * CENTRIFUGE_R) return;
                ShipData sd2 = ShipData.get();
                if (isFrostheim()) {
                    if (!frostheimCryoUnlocked && sd2.sectorReached < 0) {
                        if (sd2.spendCrystals(400f)) {
                            frostheimCryoUnlocked = true;
                            spawnCryoVent(wx, wy);
                            showNotif("CRYO-VENT UNLOCKED", "First Cryo-Vent placed!");
                        }
                    } else if (cryoVents.size < maxCryoVentsAllowed() && sd2.spendCrystals(cryoVentCost())) {
                        spawnCryoVent(wx, wy);
                    }
                } else if (isEmberIV()) {
                    float angleDeg = MathUtils.atan2(ddy, ddx) * MathUtils.radiansToDegrees;
                    if (kineticBlades.size < maxBladesAllowed() && sd2.spendCrystals(bladeCost())) {
                        spawnKineticBlade(angleDeg);
                    }
                } else {
                    if (bumpers.size < maxBumpersAllowed() && sd2.spendCrystals(bumperCost())) {
                        spawnCentrifugeBumper(wx, wy);
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
                    // hub tier purchase: no drag needed, execute immediately on touchDown
                    if (hubUpgradeTier < HUB_UPGRADE_COSTS.length) {
                        float cost = HUB_UPGRADE_COSTS[hubUpgradeTier];
                        if (!sd2.spendCrystals(cost)) return false;
                        hubUpgradeTier++;
                        if (hubUpgradeTier == 3) hubCycleLength = 10f;
                        String[] tierNames = {"Suction Boost x1.35", "Blast Boost x1.50", "Resonance Overdrive"};
                        showNotif("HUB UPGRADED T" + hubUpgradeTier,
                            tierNames[hubUpgradeTier - 1] + "\nHub Tier " + hubUpgradeTier + " / 3");
                        return true;
                    }
                    if (springPads.size >= MAX_SPRING_PADS) return false;
                    if (sd2.crystals < springPadCost()) return false;
                } else {
                    if (!gravityUnlocked() || attractors.size >= maxGravityAllowed()) return false;
                    if (sd2.crystals < gravityCost()) return false;
                }
                dragMode   = isEmberIV() ? PLACE_SPRING_PAD : PLACE_GRAVITY;
                dragStageX = event.getStageX();
                dragStageY = event.getStageY();
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
                if (dm == PLACE_NONE) return;
                float wx = dragStageX / PPM, wy = (dragStageY + 80f) / PPM;
                float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
                if (ddx * ddx + ddy * ddy >= CENTRIFUGE_R * CENTRIFUGE_R) return;
                ShipData sd2 = ShipData.get();
                if (isFrostheim()) {
                    if (teslaCoils.size < maxTeslaCoilsAllowed() && sd2.spendCrystals(teslaCost())) {
                        spawnTeslaCoil(wx, wy);
                    }
                } else if (isEmberIV()) {
                    if (springPads.size < MAX_SPRING_PADS && sd2.spendCrystals(springPadCost())) {
                        spawnSpringPad(wx, wy);
                    }
                } else {
                    if (attractors.size < maxGravityAllowed() && sd2.spendCrystals(gravityCost())) {
                        spawnAttractorBumper(wx, wy);
                    }
                }
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
                sd2.internsLeftOnPlanet[sd2.currentPlanetIndex] = balls.size;
                sd2.lastFarmingTimestamp = System.currentTimeMillis();
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

        ui.addActor(pauseTable);

        // ---- CP III Decision tree dialog (Frostheim only) ----
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

        TextButton btnAllTesla = new TextButton("ALL CRYO  →  TESLA COIL\nConvert every Cryo-Vent into a Tesla Coil", tileStyleBuy);
        btnAllTesla.getLabel().setFontScale(0.70f);
        btnAllTesla.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { applyFrostheimDecision(1); }
        });
        decisionTable.add(btnAllTesla).width(310f).height(68f).padBottom(14f).row();

        TextButton btnAllCryo = new TextButton("ALL TESLA  →  CRYO VENT\nConvert every Tesla Coil into a Cryo-Vent", tileStyleAct);
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
                if (btn != 0 || placementMode == PLACE_NONE) return false;
                touchWorld.set(sx, sy, 0);
                physViewport.unproject(touchWorld);
                float wx = touchWorld.x, wy = touchWorld.y;
                float dx = wx - CENTRIFUGE_CX, dy = wy - CENTRIFUGE_CY;
                if (dx * dx + dy * dy >= CENTRIFUGE_R * CENTRIFUGE_R) return false;
                ShipData sd = ShipData.get();
                boolean fh = isFrostheim();
                if (placementMode == PLACE_BUMPER) {
                    // First cryo vent is free — 400❅ unlock already paid for it
                    float cryoCost = (fh && cryoVents.size == 0) ? 0f : cryoVentCost();
                    if (fh && cryoVents.size < maxCryoVentsAllowed() && sd.spendCrystals(cryoCost)) {
                        spawnCryoVent(wx, wy);
                        placementMode = PLACE_NONE;
                    } else if (!fh && bumpers.size < maxBumpersAllowed() && sd.spendCrystals(bumperCost())) {
                        spawnCentrifugeBumper(wx, wy);
                        placementMode = PLACE_NONE;
                    }
                } else if (placementMode == PLACE_GRAVITY) {
                    if (fh && teslaCoils.size < maxTeslaCoilsAllowed() && sd.spendCrystals(teslaCost())) {
                        spawnTeslaCoil(wx, wy);
                        placementMode = PLACE_NONE;
                    } else if (!fh && attractors.size < maxGravityAllowed() && sd.spendCrystals(gravityCost())) {
                        spawnAttractorBumper(wx, wy);
                        placementMode = PLACE_NONE;
                    }
                } else if (placementMode == PLACE_BLADE) {
                    // Snap blade to ring wall at the tapped angle
                    float angleDeg = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                    if (isEmberIV() && kineticBlades.size < maxBladesAllowed()
                            && sd.spendCrystals(bladeCost())) {
                        spawnKineticBlade(angleDeg);
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
    public void show() {
        Gdx.input.setInputProcessor(inputMux);

        int pidx = ShipData.get().currentPlanetIndex;
        if (pidx != lastPlanetIndex) {
            lastPlanetIndex = pidx;
            fullReset();
            texBackground.dispose();
            texRing.dispose();
            texBackground = genBackground();
            texRing       = genRingTexture((int) RING_TEX_SIZE);
        }

        ShipData sd = ShipData.get();
        if (isEmberIV()) {
            world.setGravity(new Vector2(0f, -9.81f * 1.6f));
        } else if (isFrostheim()) {
            world.setGravity(new Vector2(0f, -2.5f * sd.planetGravityMultiplier));
        } else {
            world.setGravity(new Vector2(0f, GRAVITY * sd.planetGravityMultiplier));
        }

        // Solara tutorial starts with 1 intern so the player learns to hire their first one.
        // All other planets start with 2.
        if (balls.size == 0) {
            spawnBall(CENTRIFUGE_CX - 0.6f, CENTRIFUGE_CY + 0.4f);
            if (ShipData.get().arrivalsCompleted > 0) {
                spawnBall(CENTRIFUGE_CX + 0.6f, CENTRIFUGE_CY - 0.4f);
            }
        }

        applySectorPerks();
        checkOfflineHarvestProgress();
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
        boolean inputHit = Gdx.input.justTouched()
                || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY);

        if (tutorialStep == 0 && inputHit) {
            // Step 0 → 1: intro dismissed, physics starts, show "hire intern" callout
            tutorialStep    = 1;
            tutorialStepAge = 0f;
            tutorialDone    = true;
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
            if (inputHit) {
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
        drawEmberHub();
        drawKineticBlades();
        // Objective 2: bypass Solara/Frostheim structures on Ember IV — visual layer isolation
        if (!isEmberIV()) {
            drawAttractors();
            drawBumpers();
        }
        drawSpringPads();      // Ember IV spring-pads along ring wall
        drawTeslaCoils();
        drawCryoVents();
        drawInterns();
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

        SoundManager.get().update(delta);

        // Drain bumper sound events
        ShipData snd = ShipData.get();
        while (snd.pendingBumperSounds > 0) {
            SoundManager.get().playBumper();
            snd.pendingBumperSounds--;
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
        updatePerkIcon(perkIconSpeed, lockSpeedImg, milestoneAchieved[1]);
        updatePerkIcon(perkIconElas,  lockCollImg,  milestoneAchieved[0]);
        updatePerkIcon(perkIconWall,  lockWallImg,  milestoneAchieved[2]);
        updatePerkIcon(perkIconColl,  lockBoostImg, milestoneAchieved[3]);
        updatePerkIcon(perkIconBump,  lockBumpImg,  milestoneAchieved[4]);
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

        boolean orbCanBuy = (balls.size < cap) && sd.crystals >= internCost();
        // Ring speed label turns coral whenever more orbs can still be purchased — visual causal link
        ringSpeedLabel.setColor(orbCanBuy ? OdysseyTheme.ACCENT_WARN : new Color(0.70f, 0.72f, 0.82f, 1f));

        // ---- ADD ORB / Intern button text ----
        if (fh && !frostheimThirdInternUnlocked && sd.sectorReached < 0 && balls.size >= cap) {
            btnAdd.setText(sd.crystals >= 800f
                ? "UNLOCK 3RD\n800 FS"
                : "3RD INTERN\n800 FS");
        } else if (ember && !emberThirdInternUnlocked && sd.sectorReached < 0 && balls.size >= cap) {
            btnAdd.setText(sd.crystals >= 1200f
                ? "UNLOCK 3RD\n1200 SP"
                : "3RD INTERN\n1200 SP");
        } else if (balls.size >= cap) {
            String nxt;
            if (fh || ember) {
                nxt = cap == 3  ? "Req: CP I"   : cap == 5  ? "Req: CP II"
                    : cap == 8  ? "Req: CP III" : cap == 10 ? "Req: LAND!" : "MAX";
            } else {
                nxt = cap == 4  ? "Req: CP I"   : cap == 6  ? "Req: CP II"
                    : cap == 10 ? "Req: CP III" : "MAX";
            }
            btnAdd.setText(cap < MAX_INTERNS
                ? String.format("%d/%d CAP\n%s", balls.size, cap, nxt)
                : String.format("%d/%d\nFULL CAP", balls.size, cap));
        } else {
            btnAdd.setText(String.format(
                "%d/%d HIRE\n%.0f %s", balls.size, cap, internCost(), sparkSym));
        }

        // ---- Bumper / Cryo-Vent / Blade button ----
        if (fh) {
            int maxCV = maxCryoVentsAllowed();
            if (!frostheimCryoUnlocked && sd.sectorReached < 0) {
                btnBumper.setText(sd.crystals >= 400f
                    ? "UNLOCK CRYO\n400 FS"
                    : "CRYO-VENT\n400 FS");
            } else if (maxCV == 0) {
                btnBumper.setText("CRYO-VENT\nReq: CP I");
            } else if (cryoVents.size >= maxCV) {
                btnBumper.setText(String.format("CRYO-VENT\n%d/%d FULL", cryoVents.size, maxCV));
            } else if (placementMode == PLACE_BUMPER) {
                btnBumper.setText("CRYO-VENT\nTap Ring");
            } else {
                btnBumper.setText(String.format("CRYO %d/%d\n%.0f FS", cryoVents.size, maxCV, cryoVentCost()));
            }
        } else if (ember) {
            int maxBl = maxBladesAllowed();
            if (maxBl == 0) {
                btnBumper.setText("BLADE\nReq: CP I");
            } else if (kineticBlades.size >= maxBl) {
                btnBumper.setText(String.format("BLADES\n%d/%d FULL", kineticBlades.size, maxBl));
            } else if (placementMode == PLACE_BLADE) {
                btnBumper.setText("BLADE\nTap Ring");
            } else {
                btnBumper.setText(String.format("BLADE %d/%d\n%.0f SP",
                    kineticBlades.size, maxBl, bladeCost()));
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
                btnGravityWell.setText("TESLA COIL\nReq: CP II");
            } else if (teslaCoils.size >= maxTC) {
                btnGravityWell.setText(String.format("TESLA COIL\n%d/%d FULL", teslaCoils.size, maxTC));
            } else if (placementMode == PLACE_GRAVITY) {
                btnGravityWell.setText("TESLA COIL\nTap Ring");
            } else {
                btnGravityWell.setText(String.format("TESLA %d/%d\n%.0f FS",
                    teslaCoils.size, maxTC, teslaCost()));
            }
        } else if (ember) {
            if (hubUpgradeTier >= HUB_UPGRADE_COSTS.length) {
                if (springPads.size >= MAX_SPRING_PADS) {
                    btnGravityWell.setText(String.format("SPRING PAD\n%d/%d FULL", springPads.size, MAX_SPRING_PADS));
                } else if (placementMode == PLACE_SPRING_PAD) {
                    btnGravityWell.setText("SPRING PAD\nTap Ring");
                } else {
                    btnGravityWell.setText(String.format("SPRING %d/%d\n%.0f SP",
                        springPads.size, MAX_SPRING_PADS, springPadCost()));
                }
            } else {
                String[] tierLabels = {"HUB +SUCTION", "HUB +BLAST", "HUB OVERDRIVE"};
                if (sd.crystals >= HUB_UPGRADE_COSTS[hubUpgradeTier]) {
                    btnGravityWell.setText(String.format("%s\n%.0f SP",
                        tierLabels[hubUpgradeTier], HUB_UPGRADE_COSTS[hubUpgradeTier]));
                } else {
                    btnGravityWell.setText(String.format("%s\nNEED %.0f SP",
                        tierLabels[hubUpgradeTier], HUB_UPGRADE_COSTS[hubUpgradeTier]));
                }
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

        // ---- LAUNCH / progress button ----
        eDelta  = energyDeltaSinceLaunch();
        eCost   = nextCheckpointEnergyCost();
        boolean jumpReady      = eDelta >= eCost;
        boolean workforceGated = !isFullyUpgraded();
        String cpName = nextCPName();

        btnFlight.setText("LAUNCH");

        btnJumpReady.setVisible(false); // replaced by launch button under energy bar

        // ---- Button tints ----
        boolean showFhInternUnlock    = fh    && !frostheimThirdInternUnlocked && sd.sectorReached < 0 && balls.size >= cap;
        boolean showEmberInternUnlock = ember && !emberThirdInternUnlocked     && sd.sectorReached < 0 && balls.size >= cap;
        // ADD ORB: buyable when purchasable, locked when capped
        btnAdd.setStyle(
            showFhInternUnlock    ? (sd.crystals >= 800f   ? BUY : NORM) :
            showEmberInternUnlock ? (sd.crystals >= 1200f  ? BUY : NORM) :
            balls.size >= cap     ? LOCK :
            orbCanBuy             ? CORAL : NORM);

        if (fh) {
            boolean showCryoUnlock = !frostheimCryoUnlocked && sd.sectorReached < 0;
            int maxCV = maxCryoVentsAllowed();
            btnBumper.setStyle(showCryoUnlock
                ? (sd.crystals >= 400f ? BUY : NORM)
                : placementMode == PLACE_BUMPER  ? ACT
                : cryoVents.size >= maxCV        ? LOCK
                : sd.crystals >= cryoVentCost()  ? BUY : NORM);
            int maxTC = maxTeslaCoilsAllowed();
            btnGravityWell.setStyle(placementMode == PLACE_GRAVITY  ? ACT
                : teslaCoils.size >= maxTC        ? LOCK
                : sd.crystals >= teslaCost()      ? BUY : NORM);
        } else if (ember) {
            int maxBl = maxBladesAllowed();
            btnBumper.setStyle(placementMode == PLACE_BLADE              ? ACT
                : maxBl == 0 || kineticBlades.size >= maxBl ? LOCK
                : sd.crystals >= bladeCost()                ? BUY : NORM);
            // Hub upgrade / spring pad style
            if (hubUpgradeTier >= HUB_UPGRADE_COSTS.length) {
                btnGravityWell.setStyle(
                    placementMode == PLACE_SPRING_PAD        ? ACT
                    : springPads.size >= MAX_SPRING_PADS     ? LOCK
                    : sd.crystals >= springPadCost()         ? BUY : NORM);
            } else {
                btnGravityWell.setStyle(
                    sd.crystals >= HUB_UPGRADE_COSTS[hubUpgradeTier] ? BUY : LOCK);
            }
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
            topYieldsLabel.setText(String.format("Base Yields: Cryo 25 FS | Tesla %.0f J/orb", teslaHarvestRate));
        } else {
            topYieldsLabel.setText(String.format("Base Yields: Collision %.0f %s | Wall 0.5 %s",
                topCollVal, sparkSym, sparkSym));
        }

        // Column 2 — Money Engine: SPACE POINTS RATE header + live SP/s value
        topSpRateHeaderLabel.setText("ENERGY");
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
        if (!bigOverlayActive && launchAllReady && !launchHintShown) {
            batch.begin();
            drawLaunchReadyHint();
            batch.end();
            if (inputHit) launchHintShown = true;
        }

        // Perk icon hint: fires once after first perk unlocks, teaches tap-for-info
        boolean anyPerkUnlocked = milestoneAchieved[0] || milestoneAchieved[1] || milestoneAchieved[2]
                                || milestoneAchieved[3] || milestoneAchieved[4];
        if (!bigOverlayActive && anyPerkUnlocked && !perkIconHintShown && tutorialStep >= 6) {
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
        floatLayout.setText(floatFont, MILESTONE_NAMES[idx]);
        floatFont.draw(batch, MILESTONE_NAMES[idx],
            cardX + 14f, cardY + cardH - 10f);

        // Description
        floatFont.getData().setScale(0.82f);
        floatFont.setColor(0.85f, 0.88f, 0.95f, alpha);
        floatFont.draw(batch, MILESTONE_DESCS[idx],
            cardX + 14f, cardY + cardH - 28f);

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
            // Observation: intern hired, now watch SP accumulate
            // crystalsLabel: left side of stats strip, approx X=60, Y=139
            drawTutorialCalloutCard(
                "IT'S WORKING!",
                "Your intern earns Space Points (SP)",
                "watch the SP counter — bottom left!",
                60f, 184f,
                false, false
            );
        } else if (tutorialStep == 3) {
            // Observation: Energy current/needed — top center panel
            drawTutorialCalloutCard(
                "ENERGY (E)",
                "Ring spin generates Energy.",
                "Energy fuels your LAUNCH to next sector!",
                240f, renderViewport.getWorldHeight() - 50f,
                false,
                true    // arrowFromTop: points up at top panel
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

        // --- Finger shape: glow-circle tip + rect shaft ---
        final float TIP_D  = 28f;   // diameter of fingertip
        final float TIP_R  = TIP_D * 0.5f;
        final float SHAFT_W = 9f;
        final float SHAFT_H = 16f;

        // Shadow
        batch.setColor(0f, 0f, 0f, fingerAlpha * 0.38f);
        batch.draw(texParticle,
            fingerX - TIP_R + 3f, fingerY - TIP_R - 3f,
            TIP_D, TIP_D);
        batch.draw(texPixel,
            fingerX - SHAFT_W * 0.5f + 3f, fingerY - TIP_R - SHAFT_H - 3f,
            SHAFT_W, SHAFT_H);

        // Shaft
        batch.setColor(0.88f, 0.80f, 0.73f, fingerAlpha * 0.90f);
        batch.draw(texPixel,
            fingerX - SHAFT_W * 0.5f, fingerY - TIP_R - SHAFT_H,
            SHAFT_W, SHAFT_H);

        // Fingertip (glow orb tinted skin-tone)
        batch.setColor(0.97f, 0.89f, 0.81f, fingerAlpha * 0.95f);
        batch.draw(texParticle, fingerX - TIP_R, fingerY - TIP_R, TIP_D, TIP_D);

        // Bright highlight
        batch.setColor(1f, 1f, 1f, fingerAlpha * 0.50f);
        batch.draw(texParticleCore,
            fingerX - TIP_R * 0.35f, fingerY + TIP_R * 0.10f,
            10f, 10f);

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
        drawTutorialCalloutCard(title, line1, line2, tipX, tipY, isAction, arrowFromTop, false);
    }
    private void drawTutorialCalloutCard(String title, String line1, String line2,
                                          float tipX, float tipY,
                                          boolean isAction, boolean arrowFromTop, boolean isIcon) {
        float cx       = RENDER_W * 0.5f;
        float cardW    = 420f;
        float cardH    = 160f;
        float cardX    = cx - cardW * 0.5f;
        // Place card above the centrifuge (centrifuge top ≈ CCY_PX + CENTRIFUGE_R*PPM = 600)
        float cardBotY = CCY_PX + CENTRIFUGE_R * PPM + 20f;

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
                default -> OdysseyTheme.FLOAT_BUMPER;
            };

            // Scale by magnitude: base at ≤20, max at ≥200
            float scale = 0.72f + Math.min(1f, fe.value / 200f) * 0.38f;

            floatFont.getData().setScale(scale);
            floatFont.setColor(c.r, c.g, c.b, alpha);
            String text = "+" + (int) fe.value;
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

    private void drawCentrifuge() {
        float speedT = Math.min(centrifugeBody.getAngularVelocity() / centrifugeRpmMax, 1f);
        float angle  = centrifugeBody.getAngle() * MathUtils.radiansToDegrees;
        float drumR  = CENTRIFUGE_R * PPM;   // 180 px

        // --- Ring texture rotated with the centrifuge body ---
        batch.setColor(0.82f + 0.18f * speedT, 0.88f + 0.12f * speedT, 1f, 1f);
        batch.draw(texRing,
            CCX_PX - drumR, CCY_PX - drumR,
            RING_TEX_SIZE * 0.5f, RING_TEX_SIZE * 0.5f,
            RING_TEX_SIZE, RING_TEX_SIZE,
            1f, 1f, angle,
            0, 0, texRing.getWidth(), texRing.getHeight(),
            false, false);

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
        batch.setColor(1f, 1f, 1f, 1f);
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

            // On-hit burst: white-yellow ring that expands outward and fades
            if (hitT > 0.01f) {
                float burstD = BUMPER_W * (2.6f + (1f - hitT) * 2.8f);  // starts tight, expands
                batch.setColor(1f, 0.85f + hitT * 0.15f, hitT * 0.5f, hitT * 0.80f);
                batch.draw(texGravField,
                    px - burstD * 0.5f, py - burstD * 0.5f,
                    burstD * 0.5f, burstD * 0.5f, burstD, burstD, 1f, 1f, animTime * 90f,
                    0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);
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
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawCryoVents() {
        // Level 2 Frostheim Cryo-Vent launchers — snowflake crystal icon with cold-mist aura
        for (int i = 0, n = cryoVents.size; i < n; i++) {
            Vector2 pos   = cryoVents.get(i).getPosition();
            float   px    = pos.x * PPM;
            float   py    = pos.y * PPM;
            float   pulse = 0.75f + MathUtils.sin(animTime * 2.0f + i * 1.4f) * 0.25f;

            // Cold-mist aura: slow counter-rotating diffuse ring
            float aura = BUMPER_W * 2.0f;
            batch.setColor(0.25f, 0.60f, 1.00f, 0.30f * pulse);
            batch.draw(texGravField,
                px - aura * 0.5f, py - aura * 0.5f, aura * 0.5f, aura * 0.5f,
                aura, aura, 1f, 1f, -animTime * 18f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Snowflake icon — slowly rotates and breathes
            float rot   = animTime * 8f;
            float scale = 0.88f + pulse * 0.12f;
            float dw    = BUMPER_W * 1.35f * scale, dh = BUMPER_H * 1.35f * scale;
            batch.setColor(0.60f + pulse * 0.40f, 0.88f + pulse * 0.12f, 1f, 0.92f);
            batch.draw(texCryoVent,
                px - dw * 0.5f, py - dh * 0.5f, dw * 0.5f, dh * 0.5f,
                dw, dh, 1f, 1f, rot,
                0, 0, texCryoVent.getWidth(), texCryoVent.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawTeslaCoils() {
        // Level 2 Frostheim Tesla Coil harvesters — coil-rings icon with electric-field aura
        if (teslaCoils.size == 0) return;
        float fieldDiam = TESLA_COIL_FIELD_R * 2f * PPM;
        float pulse     = 1f + MathUtils.sin(animTime * 3.5f) * 0.08f;

        for (int i = 0; i < teslaCoils.size; i++) {
            Vector2 pos = teslaCoils.get(i).getPosition();
            float   px  = pos.x * PPM;
            float   py  = pos.y * PPM;

            // Outer harvest field: fast-spinning electric arc ring
            float fd = fieldDiam * pulse;
            batch.setColor(0.15f, 0.65f, 1.00f, 0.35f);
            batch.draw(texGravField,
                px - fd * 0.5f, py - fd * 0.5f,
                fd * 0.5f, fd * 0.5f, fd, fd, 1f, 1f, animTime * 90f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Inner counter-arc — bright cyan, tighter
            float fi = fieldDiam * 0.55f * pulse;
            batch.setColor(0.40f, 0.90f, 1.00f, 0.55f);
            batch.draw(texGravField,
                px - fi * 0.5f, py - fi * 0.5f,
                fi * 0.5f, fi * 0.5f, fi, fi, 1f, 1f, -animTime * 130f,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Coil-rings icon — spins slowly, electric yellow tint at centre
            float dw = BUMPER_W * 1.50f * pulse, dh = BUMPER_H * 1.50f * pulse;
            batch.setColor(0.70f, 0.95f, 1.00f, 0.95f);
            batch.draw(texTeslaCoil,
                px - dw * 0.5f, py - dh * 0.5f, dw * 0.5f, dh * 0.5f,
                dw, dh, 1f, 1f, animTime * 22f,
                0, 0, texTeslaCoil.getWidth(), texTeslaCoil.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawKineticBlades() {
        if (kineticBlades.size == 0) return;
        float bw    = BLADE_LENGTH * PPM;   // 90 px
        float bh    = BLADE_WIDTH  * PPM;   // 9 px
        float pulse = 0.85f + MathUtils.sin(animTime * 4f) * 0.15f;

        for (int i = 0; i < kineticBlades.size; i++) {
            Body    blade = kineticBlades.get(i);
            Vector2 pos   = blade.getPosition();
            float   px    = pos.x * PPM;
            float   py    = pos.y * PPM;
            float   ang   = blade.getAngle() * MathUtils.radiansToDegrees;

            Texture tex;
            float   cr, cg, cb;
            if (i == 0)      { tex = texBlade1; cr = 0.55f; cg = 0.60f; cb = 0.65f; }
            else if (i == 1) { tex = texBlade2; cr = 0.72f; cg = 0.82f; cb = 1.00f; }
            else             { tex = texBlade3; cr = 0.60f; cg = 1.00f; cb = 1.00f; }

            batch.setColor(cr * pulse, cg * pulse, cb * pulse, 0.92f);
            batch.draw(tex,
                px - bw * 0.5f, py - bh * 0.5f,
                bw * 0.5f, bh * 0.5f,
                bw, bh, 1f, 1f, ang,
                0, 0, tex.getWidth(), tex.getHeight(), false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
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

            // Outer glow layer
            if (cyber) batch.setColor(0.20f * glow, 0.75f * glow, 1.00f * glow, 0.65f);
            else        batch.setColor(1.00f * glow, 0.55f * glow, 0.10f * glow, 0.65f);
            batch.draw(texParticle, px - hw, py - hh, hw, hh,
                INTERN_W, INTERN_H, scaleX, scaleY, drawAngle,
                0, 0, texParticle.getWidth(), texParticle.getHeight(), false, false);

            // Bright inner core
            if (cyber) batch.setColor(0.70f, 0.95f, 1.00f, 0.90f);
            else        batch.setColor(1.00f, 0.88f, 0.50f, 0.90f);
            batch.draw(texParticleCore, px - chw, py - chh, chw, chh,
                coreW, coreH, scaleX, scaleY, drawAngle,
                0, 0, texParticleCore.getWidth(), texParticleCore.getHeight(), false, false);
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

        showNotif("BONUS INTERN DEPLOYED!", "Max interns reached · Ring now targets 10.0 r/s");
    }

    private void applyFrostheimDecision(int choice) {
        frostheimDecision = choice;
        decisionTable.setVisible(false);
        if (choice == 1) {
            // Convert all Cryo-Vents → Tesla Coils at the same positions
            float[] xs = new float[cryoVents.size];
            float[] ys = new float[cryoVents.size];
            for (int i = 0; i < cryoVents.size; i++) {
                xs[i] = cryoVents.get(i).getPosition().x;
                ys[i] = cryoVents.get(i).getPosition().y;
                world.destroyBody(cryoVents.get(i));
            }
            cryoVents.clear();
            for (int i = 0; i < xs.length; i++) spawnTeslaCoil(xs[i], ys[i]);
            showNotif("CRYO → TESLA", "All Cryo-Vents converted to Tesla Coils");
        } else if (choice == 2) {
            // Convert all Tesla Coils → Cryo-Vents at the same positions
            float[] xs = new float[teslaCoils.size];
            float[] ys = new float[teslaCoils.size];
            for (int i = 0; i < teslaCoils.size; i++) {
                xs[i] = teslaCoils.get(i).getPosition().x;
                ys[i] = teslaCoils.get(i).getPosition().y;
                world.destroyBody(teslaCoils.get(i));
            }
            teslaCoils.clear();
            for (int i = 0; i < xs.length; i++) spawnCryoVent(xs[i], ys[i]);
            showNotif("TESLA → CRYO", "All Tesla Coils converted to Cryo-Vents");
        } else {
            showNotif("+2 INTERNS UNLOCKED", "Intern cap raised to 12");
        }
    }

    private void backToCheckpoint() {
        // Destroy every placed construction body — pause menu is visible so world is not stepping
        for (int i = 0; i < bumpers.size;       i++) world.destroyBody(bumpers.get(i));
        for (int i = 0; i < attractors.size;    i++) world.destroyBody(attractors.get(i));
        for (int i = 0; i < cryoVents.size;     i++) world.destroyBody(cryoVents.get(i));
        for (int i = 0; i < teslaCoils.size;    i++) world.destroyBody(teslaCoils.get(i));
        for (int i = 0; i < kineticBlades.size; i++) world.destroyBody(kineticBlades.get(i));
        for (int i = 0; i < springPads.size;    i++) world.destroyBody(springPads.get(i));
        bumpers.clear();
        attractors.clear();
        cryoVents.clear();
        teslaCoils.clear();
        kineticBlades.clear();
        springPads.clear();
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
        frostheimCryoUnlocked        = false;
        frostheimThirdInternUnlocked = false;
        decisionTable.setVisible(false);
        pauseTable.setVisible(false);

        // Ember IV reset
        emberCpI = emberCpII = emberCpIII = false;
        emberThirdInternUnlocked = false;
        hubStateTimer  = 0f;
        hubCycleLength = 20f;
        hubBlastFired  = false;
        hubUpgradeTier = 0;
        emberHeavyChassis = false;
        emberMagneticRim  = false;
    }

    private void fullReset() {
        // Destroy every placed body and all interns
        for (int i = 0; i < bumpers.size;       i++) world.destroyBody(bumpers.get(i));
        for (int i = 0; i < attractors.size;    i++) world.destroyBody(attractors.get(i));
        for (int i = 0; i < cryoVents.size;     i++) world.destroyBody(cryoVents.get(i));
        for (int i = 0; i < teslaCoils.size;    i++) world.destroyBody(teslaCoils.get(i));
        for (int i = 0; i < kineticBlades.size; i++) world.destroyBody(kineticBlades.get(i));
        for (int i = 0; i < springPads.size;    i++) world.destroyBody(springPads.get(i));
        for (int i = 0; i < balls.size;         i++) world.destroyBody(balls.get(i));
        bumpers.clear(); attractors.clear(); cryoVents.clear();
        teslaCoils.clear(); kineticBlades.clear(); springPads.clear(); balls.clear();
        bladeInitAngles.clear();

        // Reset centrifuge ring
        centrifugeBody.setAngularVelocity(0f);
        com.badlogic.gdx.utils.Array<Fixture> wallFx = centrifugeBody.getFixtureList();
        for (int i = 0; i < wallFx.size; i++) wallFx.get(i).setRestitution(WALL_RESTITUTION);

        // Physics params
        bumperTier    = 0;
        gravityTier   = 0;
        bumperCoreR   = BUMPER_RADIUS;
        gravityPull   = 85f;
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
        frostheimCryoUnlocked        = false;
        frostheimThirdInternUnlocked = false;
        frostheimBallDamping = 0.01f;
        teslaHarvestRate     = 15f;

        // Ember IV state
        emberCpI = emberCpII = emberCpIII = false;
        emberThirdInternUnlocked = false;
        emberHeavyChassis  = false;
        emberMagneticRim   = false;
        hubStateTimer  = 0f;
        hubCycleLength = 20f;
        hubBlastFired  = false;
        hubUpgradeTier = 0;

        // ShipData per-level stats
        ShipData sd = ShipData.get();
        sd.totalJoules         = 0f;
        sd.crystals            = 0f;
        sd.energyAtLastLaunch  = sd.powerGenerated;
        sd.maxInternSpeed      = MAX_INTERN_SPEED;
        sd.wallEnergyMult      = 1.0f;
        sd.collisionEnergyMult = 1.0f;
        sd.bumperEnergyMult    = 5.0f;
        sd.bumperSparkValue    = 20f;
        sd.internBoostStrength = 1.5f;

        // UI
        pauseTable.setVisible(false);
        decisionTable.setVisible(false);
        notifTimer       = 0f;
        notifActive      = false;
        celebTimer       = 0f;
        celebActive      = false;
        internAddedTimer = 0f;
        uptime           = 0f;
        lastJoules       = 0f;
        lastCrystals     = 0f;
        sparkRate        = 0f;

        // Tutorial: only show on Solara (first visit). Later planets skip straight to step 3.
        ShipData rsd = ShipData.get();
        if (rsd.arrivalsCompleted == 0) {
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

    private void cheatSkipToNextPlanet() {
        ShipData sd = ShipData.get();
        int nextIdx = sd.currentPlanetIndex + 1;
        if (nextIdx >= ShipData.PLANETS.length) nextIdx = ShipData.PLANETS.length - 1;
        sd.selectPlanet(nextIdx);
        sd.commitSelectedPlanet();
        sd.markArrival(sd.targetPlanetDistance, 0f);
        // transitionTo() is a no-op when already on this screen, so drive the reset directly
        lastPlanetIndex = nextIdx;
        fullReset();
        texBackground.dispose();
        texRing.dispose();
        texBackground = genBackground();
        texRing       = genRingTexture((int) RING_TEX_SIZE);
        world.setGravity(new Vector2(0f, isEmberIV()    ? -9.81f * 1.6f
                                       : isFrostheim() ? -2.5f * sd.planetGravityMultiplier
                                       : GRAVITY * sd.planetGravityMultiplier));
        spawnBall(CENTRIFUGE_CX - 0.6f, CENTRIFUGE_CY + 0.4f);
        spawnBall(CENTRIFUGE_CX + 0.6f, CENTRIFUGE_CY - 0.4f);
        applySectorPerks();
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
        float targetY = renderViewport.getWorldHeight() * 0.28f;
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

        // Small tooltip centred above the r/s label (stats row ~Y=139, tooltip sits just above)
        float pw = 190f, ph = 44f;
        float cx = RENDER_W * 0.5f;
        float px = cx - pw * 0.5f;
        float py = 156f; // just above stats row

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
            float wx = dragStageX / PPM, wy = dragStageY / PPM;
            float ddx = wx - CENTRIFUGE_CX, ddy = wy - CENTRIFUGE_CY;
            boolean inside = (ddx * ddx + ddy * ddy < CENTRIFUGE_R * CENTRIFUGE_R);
            float alpha = inside ? 0.90f : 0.40f;
            // Lift ghost 80px above finger so thumb doesn't obscure it
            float px = dragStageX, py = dragStageY + 80f;

            if (dragMode == PLACE_INTERN) {
                // Orb glow
                float orbD = BALL_RADIUS * PPM * 5f; // ~75px display size
                batch.setColor(0.35f, 0.80f, 1f, alpha);
                batch.draw(texParticle, px - orbD * 0.5f, py - orbD * 0.5f, orbD, orbD);
                batch.setColor(1f, 1f, 1f, alpha * 0.8f);
                float coreD = orbD * 0.45f;
                batch.draw(texParticleCore, px - coreD * 0.5f, py - coreD * 0.5f, coreD, coreD);
            } else if (dragMode == PLACE_BUMPER) {
                Texture icon = isFrostheim() ? texCryoVent : texBumper;
                float sz = BUMPER_W * 1.2f;
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(icon, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
            } else if (dragMode == PLACE_BLADE) {
                batch.setColor(1f, 0.75f, 0.20f, alpha);
                float sz = BUMPER_W * 1.2f;
                batch.draw(texBlade1, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
            } else if (dragMode == PLACE_GRAVITY) {
                float fd = gravityFieldR * 2f * PPM, fh = fd * 0.5f;
                batch.setColor(0.60f, 0.30f, 1f, alpha * 0.55f);
                batch.draw(texGravField, px - fh, py - fh, fd, fd);
                Texture icon = isFrostheim() ? texTeslaCoil : texGravCenter;
                float sz = BUMPER_W * 1.2f;
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(icon, px - sz * 0.5f, py - sz * 0.5f, sz, sz);
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

    private void checkOfflineHarvestProgress() {
        ShipData sd = ShipData.get();
        if (sd.lastFarmingTimestamp == 0L) return;

        long  now        = System.currentTimeMillis();
        float elapsedSec = (now - sd.lastFarmingTimestamp) / 1000f;
        if (elapsedSec < 60f) return;   // ignore brief re-entries (< 1 minute)

        // Sum SP yield across all planets with stationed interns
        float rawYield = 0f;
        for (int p = 0; p < ShipData.PLANETS.length; p++) {
            int stationed = sd.internsLeftOnPlanet[p];
            if (stationed <= 0) continue;
            float ratePerIntern = 5f;   // SP/s per intern while ship is in flight
            float cap           = ShipData.PLANETS[p].maxFarmingStorage;
            rawYield += Math.min(stationed * ratePerIntern * elapsedSec, cap);
        }

        sd.lastFarmingTimestamp = 0L;   // clear so we don't double-count
        if (rawYield > 0f) showHarvestModal(rawYield, elapsedSec);
    }

    private void showHarvestModal(final float rawYield, float elapsedSec) {
        int hrs = (int)(elapsedSec / 3600f);
        int min = (int)((elapsedSec % 3600f) / 60f);
        String elapsedStr = hrs > 0
            ? String.format("%dh %dm away", hrs, min)
            : String.format("%dm away",     min);

        final float energyGain  = rawYield * 1.5f;
        final float crystalGain = rawYield * 2.0f;

        TextButton.TextButtonStyle tileStyle = new TextButton.TextButtonStyle();
        tileStyle.font      = game.skin.getFont("font");
        tileStyle.up        = game.skin.newDrawable("white", OdysseyTheme.PANEL_BG);
        tileStyle.down      = game.skin.newDrawable("white", OdysseyTheme.BTN_ACTIVE);
        tileStyle.over      = tileStyle.down;
        tileStyle.fontColor = OdysseyTheme.TEXT_PRI;

        final Table modal = new Table();
        modal.setFillParent(true);
        modal.setTouchable(Touchable.enabled);
        modal.background(game.skin.newDrawable("white", new Color(0f, 0.03f, 0.10f, 0.92f)));
        modal.center();

        Label title = new Label("SECTOR HARVEST REPORT", game.skin);
        title.setFontScale(1.2f);
        title.setColor(1f, 0.82f, 0.20f, 1f);
        modal.add(title).padBottom(8f).row();

        Label sub = new Label("Deployed interns worked while you flew\n" + elapsedStr, game.skin);
        sub.setFontScale(0.62f);
        sub.setColor(0.62f, 0.72f, 0.85f, 1f);
        modal.add(sub).padBottom(20f).row();

        Label rawLabel = new Label("Raw Harvest: " + formatNumber(rawYield) + " SP", game.skin);
        rawLabel.setFontScale(0.82f);
        rawLabel.setColor(0.78f, 0.88f, 1f, 1f);
        modal.add(rawLabel).padBottom(24f).row();

        TextButton btnRefine = new TextButton(
            "REFINE TO ENERGY\n+" + formatNumber(energyGain) + " E\n(x1.5 multiplier)", tileStyle);
        btnRefine.getLabel().setFontScale(0.68f);
        btnRefine.setColor(0.25f, 0.95f, 1f, 1f);
        btnRefine.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                ShipData.get().addJoules(energyGain);
                modal.remove();
            }
        });
        modal.add(btnRefine).width(280f).height(72f).padBottom(14f).row();

        TextButton btnMelt = new TextButton(
            "MELT TO SHARDS\n+" + formatNumber(crystalGain) + " SP\n(x2.0 multiplier)", tileStyle);
        btnMelt.getLabel().setFontScale(0.68f);
        btnMelt.setColor(1f, 0.72f, 0.15f, 1f);
        btnMelt.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                ShipData.get().addCrystals(crystalGain);
                modal.remove();
            }
        });
        modal.add(btnMelt).width(280f).height(72f).row();

        ui.addActor(modal);
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
        if (isEmberIV()) {
            // ---- Ember IV: ring-speed checkpoint perks ----

            // CP I — Heavy Chassis (5.5 r/s): intern density 3.5f
            if (!emberCpI && targetRPM >= EMBER_MILESTONE_RPMS[0]) {
                emberCpI = true;
                milestoneAchieved[1] = true;
                ShipData.get().sectorReached = 0;
                emberHeavyChassis = true;
                for (int i = 0; i < balls.size; i++) {
                    Body b = balls.get(i);
                    Array<Fixture> fx = b.getFixtureList();
                    for (int f = 0; f < fx.size; f++) fx.get(f).setDensity(3.5f);
                    b.resetMassData();
                }
                showCeleb("CP I — HEAVY CHASSIS", "Intern density 3.5\nTanks take hits harder");
                triggerShake(8f, 0.20f);
            }

            // CP II — Magnetic Rim (6.5 r/s): wall restitution boost to 0.88
            if (!emberCpII && targetRPM >= EMBER_MILESTONE_RPMS[1]) {
                emberCpII = true;
                milestoneAchieved[2] = true;
                ShipData.get().sectorReached = 1;
                emberMagneticRim = true;
                Array<Fixture> wallFx = centrifugeBody.getFixtureList();
                for (int i = 0; i < wallFx.size; i++) wallFx.get(i).setRestitution(0.88f);
                showCeleb("CP II — MAGNETIC RIM", "Wall restitution 0.88\nInterns roll the ring");
                triggerShake(8f, 0.20f);
            }

            // CP III — Hub Resonance (7.5 r/s): hub cycle 20s → 10s
            if (!emberCpIII && targetRPM >= EMBER_MILESTONE_RPMS[2]) {
                emberCpIII = true;
                milestoneAchieved[3] = true;
                ShipData.get().sectorReached = 2;
                hubCycleLength = 10f;
                showCeleb("CP III — HUB RESONANCE", "Hub cycle halved\nBlast fires every 20s");
                triggerShake(8f, 0.20f);
            }

            // Status label
            if (!emberCpI) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP I at %.1f r/s", EMBER_MILESTONE_RPMS[0]));
            } else if (!emberCpII) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP II at %.1f r/s", EMBER_MILESTONE_RPMS[1]));
            } else if (!emberCpIII) {
                milestoneStatusLabel.setColor(1f, 0.65f, 0.15f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP III at %.1f r/s", EMBER_MILESTONE_RPMS[2]));
            } else {
                milestoneStatusLabel.setColor(1f, 0.45f, 0.10f, 1f);
                milestoneStatusLabel.setText("All Nova Terra perks active. Hub in overdrive.");
            }
            return;
        }

        if (isFrostheim()) {
            // ---- Frostheim: three ring-speed checkpoint perks ----
            // milestoneAchieved[1/2/3] map to Frostheim CP I/II/III respectively.

            // CP I — Superconductor Friction-Zero (5.0 r/s)
            if (!frostheimCpI && targetRPM >= MILESTONE_RPMS[1]) {
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
            }

            // CP II — Absolute Zero Resonance (6.0 r/s)
            if (!frostheimCpII && targetRPM >= MILESTONE_RPMS[2]) {
                frostheimCpII = true;
                milestoneAchieved[2] = true;
                ShipData.get().sectorReached = 1;
                Array<Fixture> wallFx = centrifugeBody.getFixtureList();
                for (int i = 0; i < wallFx.size; i++) wallFx.get(i).setRestitution(0.94f);
                showCeleb("CP II — ABSOLUTE ZERO", "Wall restitution 0.94\nPerfect elastic bounce");
                triggerShake(8f, 0.20f);
            }

            // CP III — Blizzard Overdrive (7.0 r/s)
            if (!frostheimCpIII && targetRPM >= MILESTONE_RPMS[3]) {
                frostheimCpIII = true;
                milestoneAchieved[3] = true;
                ShipData.get().sectorReached = 2;
                teslaHarvestRate = 30f;
                ShipData.get().maxInternSpeed = 8.5f;
                decisionTable.setVisible(true);
                showCeleb("CP III — BLIZZARD OVERDRIVE", "Choose your evolution path");
                triggerShake(8f, 0.20f);
            }

            // Status label
            if (!frostheimCpI) {
                milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP I at %.1f r/s", MILESTONE_RPMS[1]));
            } else if (!frostheimCpII) {
                milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP II at %.1f r/s", MILESTONE_RPMS[2]));
            } else if (!frostheimCpIII) {
                milestoneStatusLabel.setColor(1f, 0.85f, 0.2f, 1f);
                milestoneStatusLabel.setText(String.format("Next: CP III at %.1f r/s", MILESTONE_RPMS[3]));
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
        targetRPM = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
        float cur = centrifugeBody.getAngularVelocity();
        if (cur < targetRPM)
            centrifugeBody.setAngularVelocity(Math.min(cur + CENTRIFUGE_RPM_ACCEL * delta, targetRPM));
        else if (cur > targetRPM)
            centrifugeBody.setAngularVelocity(Math.max(cur - CENTRIFUGE_RPM_ACCEL * delta, targetRPM));

        // ---- 2. Blade orbit: co-rotate rigidly with centrifuge ring ----
        // Contact with a sweeping blade shatters gravity-lock stacks and awards +35 SP
        // (handled by EnergyContactListener for KINETIC_BLADE userData).
        if (kineticBlades.size > 0) {
            float centAngle = centrifugeBody.getAngle();
            float omega     = centrifugeBody.getAngularVelocity();
            float bladeR    = CENTRIFUGE_R - BLADE_LENGTH * 0.5f;
            for (int i = 0; i < kineticBlades.size; i++) {
                float angle = bladeInitAngles.get(i) + centAngle;
                float bx    = CENTRIFUGE_CX + MathUtils.cos(angle) * bladeR;
                float by    = CENTRIFUGE_CY + MathUtils.sin(angle) * bladeR;
                kineticBlades.get(i).setTransform(bx, by, angle);
                // Tangential velocity gives physically-correct collision impulses
                kineticBlades.get(i).setLinearVelocity(
                    -omega * (by - CENTRIFUGE_CY),
                     omega * (bx - CENTRIFUGE_CX));
                kineticBlades.get(i).setAngularVelocity(omega);
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

        // ---- 4. Tesla Coil passive harvest (Frostheim) — no gravitational pull ----
        for (int i = 0; i < teslaCoils.size; i++) {
            Body    coil = teslaCoils.get(i);
            Vector2 cPos = coil.getPosition();
            for (int j = 0; j < balls.size; j++) {
                pullVec.set(cPos).sub(balls.get(j).getPosition());
                if (pullVec.len() < TESLA_COIL_FIELD_R) {
                    ShipData.get().addJoules(teslaHarvestRate * delta);
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

        // ---- 7. Cryo-Vent proximity launch (Frostheim) ----
        // When an intern overlaps a Cryo-Vent nozzle and isn't already flying up,
        // fire a violent upward impulse and award 25 FS. y-guard prevents repeated stacking.
        if (isFrostheim() && cryoVents.size > 0) {
            final float cryoThreshold = BALL_RADIUS + CRYO_VENT_RADIUS + 0.04f;
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                if (ball.getLinearVelocity().y > 3f) continue;
                Vector2 bPos = ball.getPosition();
                for (int k = 0; k < cryoVents.size; k++) {
                    pullVec.set(cryoVents.get(k).getPosition()).sub(bPos);
                    if (pullVec.len() <= cryoThreshold) {
                        ball.applyLinearImpulse(
                            0f, 16.5f * ball.getMass(),
                            ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                        ShipData.get().addCrystals(25f);
                        break;
                    }
                }
            }
        }

        // ---- 8. Passive energy: ring speed x intern count ----
        float ringSpeed = centrifugeBody.getAngularVelocity();
        if (ringSpeed > 0f && balls.size > 0) {
            ShipData.get().addJoules(ringSpeed * balls.size * 3f * dt);
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

        // ---- 11. 1-Second Heartbeat Pulse — accumulate crystals earned this step ----
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
        return ShipData.get().targetPlanetDistance == 5000f;
    }

    private boolean isEmberIV() {
        return ShipData.get().targetPlanetDistance == 2500f;
    }

    private float price(float base) {
        if (isFrostheim()) return base * 2.5f;
        if (isEmberIV())   return base * 1.5f;
        return base;
    }

    private int internCap() {
        int sr = ShipData.get().sectorReached;
        if (isEmberIV()) {
            if (sr >= 3) return 12;
            if (sr >= 2) return 10;
            if (sr >= 1) return 8;
            if (sr >= 0) return 5;
            return emberThirdInternUnlocked ? 3 : 2;
        }
        if (isFrostheim()) {
            if (sr >= 2) return frostheimDecision == 3 ? 12 : 10;
            if (sr >= 1) return 8;
            if (sr >= 0) return 5;
            return frostheimThirdInternUnlocked ? 3 : 2;
        }
        if (sr >= 2) return 12;
        if (sr >= 1) return 10;
        if (sr >= 0) return 6;
        return 4;
    }

    private int maxBumpersAllowed() {
        int sr = ShipData.get().sectorReached;
        if (sr >= 2) return 5;
        if (sr >= 1) return 4;
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
        if (sr >= 2) return 4;
        if (sr >= 1) return 2;
        return 0;
    }

    private int maxCryoVentsAllowed() {
        if (!isFrostheim()) return 0;
        if (frostheimDecision == 1) return 0;   // all converted to Tesla at CP III
        int sr = ShipData.get().sectorReached;
        if (sr >= 1) return 2;
        if (sr >= 0) return 1;
        return frostheimCryoUnlocked ? 1 : 0;   // locked until 400❅ purchase
    }

    private int maxTeslaCoilsAllowed() {
        if (!isFrostheim()) return 0;
        if (frostheimDecision == 2) return 0;   // all converted to Cryo at CP III
        int sr = ShipData.get().sectorReached;
        if (sr >= 1) return 2;
        if (sr >= 0) return 1;
        return 0;   // locked until CP I
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
        if (sr >= 2) return 3;   // CP III
        if (sr >= 1) return 2;   // CP II
        if (sr >= 0) return 1;   // CP I
        return 0;
    }

    private float bladeCost() {
        int idx = kineticBlades.size;
        return idx < BLADE_COSTS.length ? BLADE_COSTS[idx] : BLADE_COSTS[BLADE_COSTS.length - 1];
    }

    private float internCost() {
        int idx = Math.max(0, balls.size - 2);
        float base = idx < INTERN_COSTS.length ? INTERN_COSTS[idx] : INTERN_COSTS[INTERN_COSTS.length - 1];
        return price(base);
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
    private float cryoVentCost() {
        int idx = cryoVents.size;
        return idx < CRYO_VENT_COSTS.length ? CRYO_VENT_COSTS[idx] : CRYO_VENT_COSTS[CRYO_VENT_COSTS.length - 1];
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
        float[] energies;
        if (isEmberIV())        energies = EMBER_CP_ENERGIES;
        else if (isFrostheim()) energies = FROSTHEIM_CP_ENERGIES;
        else                    energies = SOLARA_CP_ENERGIES;
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

        // Must fill intern cap
        if (balls.size < internCap()) return false;

        if (isFrostheim()) {
            // Pre-CP-I: must buy the 3rd intern unlock first
            if (sr < 0 && !frostheimThirdInternUnlocked) return false;
            // CP-I+: cryo must be unlocked (400❅ purchase) and all slots filled; tesla maxed
            if (sr >= 0) {
                if (!frostheimCryoUnlocked) return false;
                if (cryoVents.size < maxCryoVentsAllowed()) return false;
                if (teslaCoils.size < maxTeslaCoilsAllowed()) return false;
            }
        } else if (isEmberIV()) {
            // Pre-CP-I: must buy the 3rd intern unlock first
            if (sr < 0 && !emberThirdInternUnlocked) return false;
            // CP-I+: blades and gravity maxed
            if (kineticBlades.size < maxBladesAllowed()) return false;
            if (gravityUnlocked() && attractors.size < maxGravityAllowed()) return false;
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
        if (balls.size < cap) return "HIRE " + balls.size + "/" + cap + " ORBS";
        if (isFrostheim()) {
            if (sr < 0 && !frostheimThirdInternUnlocked) return "BUY 3RD INTERN";
            if (sr >= 0) {
                if (!frostheimCryoUnlocked) return "UNLOCK CRYO";
                if (cryoVents.size < maxCryoVentsAllowed()) return "MAX CRYO " + cryoVents.size + "/" + maxCryoVentsAllowed();
                if (teslaCoils.size < maxTeslaCoilsAllowed()) return "MAX TESLA " + teslaCoils.size + "/" + maxTeslaCoilsAllowed();
            }
        } else if (isEmberIV()) {
            if (sr < 0 && !emberThirdInternUnlocked) return "BUY 3RD INTERN";
            int mb = maxBladesAllowed();
            if (kineticBlades.size < mb) return "MAX BLADES " + kineticBlades.size + "/" + mb;
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
        texLock.dispose();
        texPerkSpeed.dispose();
        texPerkElas.dispose();
        texPerkWall.dispose();
        texPerkColl.dispose();
        texPerkBump.dispose();
        texIconSP.dispose();
        texIconEnergy.dispose();
        // floatFont owned by skin — do not dispose here
        ui.dispose();
    }
}
