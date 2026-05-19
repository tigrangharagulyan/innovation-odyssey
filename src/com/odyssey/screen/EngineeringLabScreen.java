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
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
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
    private static final float CENTRIFUGE_CX       = 4.0f;
    private static final float CENTRIFUGE_CY       = 7.0f;   // drum sits just above bottom controls panel
    private static final float CENTRIFUGE_R        = 3.0f;   // bigger drum — fills viewport width
    private static final int   CENTRIFUGE_SEGS     = 36;
    private static final float CENTRIFUGE_RPM      = 5.0f;
    private static final int   MAX_BODIES           = 30;
    private static final int   MAX_INTERNS          = 12;
    private static final int   MAX_BUMPERS          = 5;
    private static final int   MAX_GRAVITY_WELLS    = 4;
    private static final float CENTRIFUGE_RPM_BASE   = 1.5f;   // starting ring speed
    private static final float CENTRIFUGE_RPM_MAX    = 10.0f;   // max ring speed
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
    private static final float[]  MILESTONE_RPMS  = {99f, 5.0f, 6.0f, 7.0f, 7.5f, 8.0f};
    private static final String[] MILESTONE_NAMES = {"Elastic Walls", "Speed Keep", "Wall ×3", "Hit ×2", "Free Intern", "Overdrive"};
    private static final String[] MILESTONE_DESCS = {
        "Interns bounce off walls harder — more chaos",
        "Interns keep 97% speed after every hit",
        "Each wall touch earns 3× more Space Points",
        "Intern-intern hits earn 2× more Space Points",
        "A bonus intern is added to your bay for free",
        "Bumpers give 15× points · intern bounces get a huge kick"
    };

    // Sector perk unlock thresholds (mirrors BridgeFlightScreen dynamic route sectors)
    private static final int NUM_SECTORS = 4;

    private static final int PLACE_NONE    = 0;
    private static final int PLACE_BUMPER  = 1;
    private static final int PLACE_GRAVITY = 2;

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
    private FitViewport        physViewport;

    // Rendering
    private SpriteBatch        batch;
    private OrthographicCamera renderCam;
    private FitViewport        renderViewport;
    private Texture            texBackground;
    private Texture            texParticle;       // glowing energy orb
    private Texture            texParticleCore;   // bright inner core
    private Texture            texBumper;
    private Texture            texRing;
    private Texture            texGravField;
    private Texture            texPixel;          // 1×1 white pixel

    // Scene2D
    private Stage      ui;
    private Label      joulesLabel;
    private Label      crystalsLabel;
    private Label      jpsLabel;
    private Label      outputLabel;
    private Label      uptimeLabel;

    // Space Points feed: last 5 gains shown at top of centrifuge  {age, value, colorType}
    private final Array<float[]> spacePointsQueue = new Array<>();
    private static final float SP_LIFETIME = 4.0f;
    private static final int   SP_MAX      = 5;
    private com.badlogic.gdx.graphics.g2d.BitmapFont floatFont;
    private com.badlogic.gdx.graphics.g2d.GlyphLayout floatLayout;
    private TextButton btnAdd;
    private TextButton btnBumper;
    private TextButton btnGravityWell;
    private TextButton[] perkButtons;
    private Label[]      perkDescLabels;
    private Table        rightPerksTable;

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

    // Intern-added overlay
    private static final float INTERN_ADDED_HOLD = 2.5f;
    private float internAddedTimer    = 0f;
    private float internAddedOldSpeed = 0f;
    private float internAddedNewSpeed = 0f;

    // Generic notification overlay (perk unlocks, free intern)
    private static final float NOTIF_HOLD = 3.2f;
    private float  notifTimer = 0f;
    private String notifTitle = "";
    private String notifBody  = "";

    // Free-intern-at-max state
    private boolean freeInternGiven = false;
    private float   centrifugeRpmMax = CENTRIFUGE_RPM_MAX; // bumped to 10.0 when free intern fires

    // Perks panel rebuild tracking
    private Label perksHeader;
    private int   lastUnlockedPerkCount = -1;

    // Space-points-per-second tracking
    private float lastCrystals = 0f;
    private float sparkRate    = 0f;

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

        // Deep-space gradient (bottom of Pixmap = top of screen after LibGDX flip)
        for (int y = 0; y < H; y++) {
            float t = (float) y / H;
            pm.setColor(0.015f + t * 0.02f, 0.018f + t * 0.025f, 0.055f + t * 0.07f, 1f);
            for (int x = 0; x < W; x++) pm.drawPixel(x, y);
        }

        // Stars
        for (int i = 0; i < 110; i++) {
            int sx = (int) (MathUtils.random() * W);
            int sy = (int) (MathUtils.random() * H);
            float b = 0.35f + MathUtils.random() * 0.65f;
            pm.setColor(b, b, b * 0.95f + 0.05f, 1f);
            pm.drawPixel(sx, sy);
            if (MathUtils.random() > 0.68f) { pm.drawPixel(sx + 1, sy); pm.drawPixel(sx, sy + 1); }
        }

        // Chamber — in Pixmap coords: chamberCY_pm = H - CCY_PX (LibGDX renders y-up, Pixmap is y-down)
        float cCX = CCX_PX, cCY = H - CCY_PX;
        float cR = CENTRIFUGE_R * PPM;

        // Dark inner area
        for (int y = (int)(cCY - cR - 2); y <= (int)(cCY + cR + 2); y++) {
            for (int x = (int)(cCX - cR - 2); x <= (int)(cCX + cR + 2); x++) {
                if (x < 0 || x >= W || y < 0 || y >= H) continue;
                float dx = x - cCX, dy = y - cCY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= cR - 4) {
                    float t = (float) y / H;
                    pm.setColor(0.01f + t * 0.01f, 0.013f + t * 0.01f, 0.04f + t * 0.03f, 1f);
                    pm.drawPixel(x, y);
                }
            }
        }

        // Cyan glow ring around chamber
        for (int y = (int)(cCY - cR - 20); y <= (int)(cCY + cR + 20); y++) {
            for (int x = (int)(cCX - cR - 20); x <= (int)(cCX + cR + 20); x++) {
                if (x < 0 || x >= W || y < 0 || y >= H) continue;
                float dx = x - cCX, dy = y - cCY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float ring = Math.abs(dist - cR);
                if (ring < 14) {
                    float glow = (1f - ring / 14f) * 0.55f;
                    float t = (float) y / H;
                    float br = 0.015f + t * 0.02f, bg = 0.018f + t * 0.025f, bb = 0.055f + t * 0.07f;
                    pm.setColor(br + glow * 0.12f, Math.min(bg + glow * 0.62f, 1f), Math.min(bb + glow, 1f), 1f);
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

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < innerR || dist > outerR) { pm.setColor(0f, 0f, 0f, 0f); pm.drawPixel(x, y); continue; }
                float t = (dist - innerR) / (outerR - innerR);   // 0..1 across ring
                float peak = 1f - Math.abs(t - 0.5f) * 2f;       // 1 at centerline, 0 at edges
                float r = 0.07f + peak * 0.23f;
                float g = 0.48f + peak * 0.42f;
                float b = 0.82f + peak * 0.18f;
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
                    pm.setColor(0.6f, 0.92f, 1f, 0.7f);
                    pm.drawPixel(sx, sy);
                    pm.drawPixel(sx + 1, sy);
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
        renderViewport = new FitViewport(RENDER_W, RENDER_H, renderCam);
        renderCam.position.set(RENDER_W / 2f, RENDER_H / 2f, 0f);
        floatFont   = new com.badlogic.gdx.graphics.g2d.BitmapFont();
        floatLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    }

    // ---- Physics setup ----------------------------------------------------------

    private void buildPhysics() {
        world = new World(new Vector2(0, GRAVITY * ShipData.get().planetGravityMultiplier), true);
        world.setContactListener(new EnergyContactListener());

        physCam = new OrthographicCamera();
        physViewport = new FitViewport(WORLD_W, WORLD_H, physCam);
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
        body.setUserData(new ShipData.BumperHitData());
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

    // ---- UI setup ---------------------------------------------------------------

    private void buildUI() {
        ui = new Stage(new FitViewport(RENDER_W, RENDER_H));

        Color panelBg     = new Color(0.04f, 0.06f, 0.16f, 0.92f);
        Color panelBgSolid = new Color(0.04f, 0.06f, 0.18f, 0.97f);

        // ---- Single full-screen layout table ----
        Table root = new Table();
        root.setFillParent(true);

        // -- TOP BAR: just title + live JPS --
        Table topInner = new Table();
        topInner.background(game.skin.newDrawable("white", panelBg));
        topInner.pad(3, 10, 3, 10);

        Label screenTitle = new Label("ENG BAY", game.skin);
        screenTitle.setFontScale(0.90f);
        configLabel  = new Label("SOLARA", game.skin);
        configLabel.setFontScale(0.72f);
        outputLabel  = new Label("0.0 J/s", game.skin, "accent");
        outputLabel.setFontScale(0.90f);
        uptimeLabel  = new Label("", game.skin);   // unused in top bar now
        milestoneStatusLabel = new Label("Next: Elastic 2.5 r/s", game.skin);
        milestoneStatusLabel.setFontScale(0.68f);

        TextButton.TextButtonStyle menuBtnStyle = new TextButton.TextButtonStyle();
        menuBtnStyle.font      = game.skin.getFont("font");
        menuBtnStyle.up        = game.skin.newDrawable("white", new Color(0.12f, 0.15f, 0.30f, 0.85f));
        menuBtnStyle.down      = game.skin.newDrawable("white", new Color(0.20f, 0.24f, 0.44f, 1.00f));
        menuBtnStyle.over      = menuBtnStyle.down;
        menuBtnStyle.fontColor = new Color(0.65f, 0.75f, 1f, 1f);
        TextButton btnMenu = new TextButton("≡", menuBtnStyle);
        btnMenu.getLabel().setFontScale(0.90f);
        btnMenu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                pauseTable.setVisible(true);
            }
        });

        topInner.add(screenTitle).left().padRight(8);
        topInner.add(configLabel).left().expandX();
        topInner.add(milestoneStatusLabel).right().padRight(6);
        topInner.add(outputLabel).right().padRight(6);
        topInner.add(btnMenu).right().width(32).height(28);

        root.add(topInner).growX().height(34).row();

        // -- CENTRIFUGE ZONE: transparent overlay — expands to fill space, holds JUMP READY button --
        Table centrifugeOverlay = new Table();

        TextButton.TextButtonStyle jumpStyle = new TextButton.TextButtonStyle();
        jumpStyle.font = game.skin.getFont("font");
        jumpStyle.up   = game.skin.newDrawable("button_primary", new Color(0.08f, 0.72f, 0.30f, 0.90f));
        jumpStyle.down = game.skin.newDrawable("button_primary", new Color(0.05f, 0.50f, 0.20f, 1.00f));
        jumpStyle.over = game.skin.newDrawable("button_primary", new Color(0.12f, 0.92f, 0.42f, 0.95f));
        jumpStyle.fontColor = Color.WHITE;

        btnJumpReady = new TextButton("▲  JUMP READY  ▲\nTAP TO LAUNCH", jumpStyle);
        btnJumpReady.getLabel().setFontScale(0.88f);
        btnJumpReady.setVisible(false);
        btnJumpReady.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                ShipData.get().savedFlightJPS = ShipData.get().currentJPS;
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

        // Row 1: compact stats strip
        joulesLabel    = new Label("0 J", game.skin, "accent");
        crystalsLabel  = new Label("◆ 0", game.skin);
        jpsLabel       = new Label("0.0/s", game.skin);
        ringSpeedLabel = new Label("1.5r/s", game.skin);
        joulesLabel.setFontScale(1.10f);
        crystalsLabel.setFontScale(0.82f);
        crystalsLabel.setColor(0.4f, 0.85f, 1f, 1f);
        jpsLabel.setFontScale(0.78f);
        ringSpeedLabel.setFontScale(0.68f);

        Table statsRow = new Table();
        statsRow.add(joulesLabel).left().padLeft(2);
        statsRow.add(crystalsLabel).padLeft(8);
        statsRow.add(jpsLabel).expandX().center();
        statsRow.add(ringSpeedLabel).right().padRight(2);
        panel.add(statsRow).growX().padBottom(3).row();

        // Row 2: 4 equal boxes side by side — INTERNS | BUMPERS | GRAVITY | ENGAGE JUMP
        // Use a custom button style with card_large as background so no extra wrapper table inflates preferred width

        TextButton.TextButtonStyle tileStyle = new TextButton.TextButtonStyle();
        tileStyle.font     = game.skin.getFont("font");
        tileStyle.up       = game.skin.getDrawable("card_large");
        tileStyle.down     = game.skin.newDrawable("white", new Color(0.10f, 0.14f, 0.30f, 0.97f));
        tileStyle.over     = tileStyle.down;
        tileStyle.fontColor = Color.WHITE;

        btnAdd         = new TextButton("INTERNS\n0 J\n0/4",      tileStyle);
        btnBumper      = new TextButton("BUMPERS\nLOCKED\nCP I",   tileStyle);
        btnGravityWell = new TextButton("GRAVITY\nLOCKED\nCP II",  tileStyle);
        btnFlight      = new TextButton("ENGAGE\nJUMP",            tileStyle);

        for (TextButton btn : new TextButton[]{btnAdd, btnBumper, btnGravityWell, btnFlight}) {
            btn.getLabel().setFontScale(0.60f);
        }

        // 4 equal squares in a single row
        float btnSz = (RENDER_W - 20f - 24f) / 4f;  // panel pad 10+10, gaps 3*8=24 → ~109px, capped
        Table tileRow = new Table();
        tileRow.add(btnAdd).size(btnSz, btnSz).pad(3);
        tileRow.add(btnBumper).size(btnSz, btnSz).pad(3);
        tileRow.add(btnGravityWell).size(btnSz, btnSz).pad(3);
        tileRow.add(btnFlight).size(btnSz, btnSz).pad(3);
        panel.add(tileRow).center().padBottom(4).row();

        root.add(panel).growX().row();

        // ---- Listeners ----
        btnAdd.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (balls.size < internCap() && ShipData.get().spendCrystals(internCost())) {
                    internAddedOldSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    float angle = MathUtils.random(MathUtils.PI2);
                    float r     = MathUtils.random(0.5f, CENTRIFUGE_R * 0.55f);
                    spawnBall(CENTRIFUGE_CX + MathUtils.cos(angle) * r,
                              CENTRIFUGE_CY + MathUtils.sin(angle) * r);
                    internAddedNewSpeed = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);
                    internAddedTimer    = INTERN_ADDED_HOLD;
                }
            }
        });
        btnBumper.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                int maxB = maxBumpersAllowed();
                if (maxB == 0 || bumpers.size >= maxB) return;
                if (ShipData.get().crystals >= bumperCost())
                    placementMode = (placementMode == PLACE_BUMPER) ? PLACE_NONE : PLACE_BUMPER;
            }
        });
        btnGravityWell.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!gravityUnlocked() || attractors.size >= maxGravityAllowed()) return;
                if (ShipData.get().crystals >= gravityCost())
                    placementMode = (placementMode == PLACE_GRAVITY) ? PLACE_NONE : PLACE_GRAVITY;
            }
        });
        btnFlight.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                ShipData.get().savedFlightJPS = ShipData.get().currentJPS;
                game.transitionTo(GameState.BRIDGE_FLIGHT);
            }
        });

        ui.addActor(root);
        ui.addActor(rightPerksTable); // rendered on top of root; positioned precisely in render()

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

        TextButton btnContinue = new TextButton("CONTINUE", tileStyle);
        btnContinue.getLabel().setFontScale(0.82f);
        btnContinue.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                pauseTable.setVisible(false);
            }
        });
        pauseTable.add(btnContinue).width(280f).height(62f).padBottom(18f).row();

        TextButton btnMainMenu = new TextButton("MAIN MENU", tileStyle);
        btnMainMenu.getLabel().setFontScale(0.82f);
        btnMainMenu.setColor(0.85f, 0.35f, 0.35f, 1f);
        btnMainMenu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                pauseTable.setVisible(false);
                game.transitionTo(GameState.MAIN_MENU);
            }
        });
        pauseTable.add(btnMainMenu).width(280f).height(62f).row();

        ui.addActor(pauseTable);

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
                if (placementMode == PLACE_BUMPER && bumpers.size < maxBumpersAllowed() && sd.spendCrystals(bumperCost())) {
                    spawnCentrifugeBumper(wx, wy);
                    placementMode = PLACE_NONE;
                } else if (placementMode == PLACE_GRAVITY && attractors.size < maxGravityAllowed() && sd.spendCrystals(gravityCost())) {
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
        if (sr >= 0 && !milestoneAchieved[0]) {
            milestoneAchieved[0] = true; applyElasticWalls();
            showNotif("PERK UNLOCKED", MILESTONE_NAMES[0] + "\n" + MILESTONE_DESCS[0]);
        }
        if (sr >= 2 && !milestoneAchieved[2]) {
            milestoneAchieved[2] = true; ShipData.get().wallEnergyMult = 3f;
            showNotif("PERK UNLOCKED", MILESTONE_NAMES[2] + "\n" + MILESTONE_DESCS[2]);
        }
        if (sr >= 2 && !milestoneAchieved[3]) {
            milestoneAchieved[3] = true; ShipData.get().collisionEnergyMult = 2f;
            showNotif("PERK UNLOCKED", MILESTONE_NAMES[3] + "\n" + MILESTONE_DESCS[3]);
        }
        if (sr >= 3 && !milestoneAchieved[5]) {
            milestoneAchieved[5] = true; applyOverdrive();
            showNotif("PERK UNLOCKED", MILESTONE_NAMES[5] + "\n" + MILESTONE_DESCS[5]);
        }
    }

    @Override
    public void render(float delta) {
        if (!tutorialDone && (Gdx.input.justTouched()
                || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY))) {
            tutorialDone = true;
        }

        uptime    += delta;
        animTime  += delta;
        if (tutorialDone && !pauseTable.isVisible()) stepPhysics(delta);
        updateJPS(delta);
        if (internAddedTimer > 0) internAddedTimer = Math.max(0, internAddedTimer - delta);
        if (notifTimer > 0)       notifTimer       = Math.max(0, notifTimer - delta);
        if (tutorialDone) checkFreeInternCondition();
        checkMilestones();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderViewport.apply();
        renderCam.update();
        batch.setProjectionMatrix(renderCam.combined);

        // Drain pending collision events (rate tracked in updateJPS via crystals delta)
        ShipData.get().pendingContactEvents.clear();

        batch.begin();
        drawBackground();
        drawCentrifuge();
        drawAttractors();
        drawBumpers();
        drawInterns();
        drawPlacementPreview();
        drawSpacePoints();
        batch.end();

        // UI overlay
        ui.getViewport().apply();
        ui.act(delta);
        ShipData sd = ShipData.get();
        ShipData.PlanetProfile currentPlanet = sd.getCurrentPlanet();
        joulesLabel.setText(formatNumber(energyDeltaSinceLaunch()) + " E");
        crystalsLabel.setText("◆ " + (int) sd.crystals);
        jpsLabel.setText(formatNumber(sd.currentJPS) + " E/s");
        outputLabel.setText(formatNumber(sd.currentJPS) + " E/s");
        int cap  = internCap();
        int maxB = maxBumpersAllowed();
        // ---- Button texts ----
        if (balls.size >= cap) {
            String nxt = cap == 4 ? "→ CP I" : cap == 6 ? "→ CP II" : cap == 10 ? "→ CP III" : "MAX";
            btnAdd.setText(cap < MAX_INTERNS
                ? String.format("ADD ORB\n%d/%d CAP\n%s", balls.size, cap, nxt)
                : "ADD ORB\n10/10\nFULL CAP");
        } else {
            btnAdd.setText(String.format("ADD ORB\n%.0f◆\n[%d/%d]", internCost(), balls.size, cap));
        }

        if (maxB == 0) {
            btnBumper.setText("BUMPER\nLOCKED\n→ CP I");
        } else if (bumpers.size >= maxB) {
            btnBumper.setText(String.format("BUMPER\n%d/%d\nFULL", bumpers.size, maxB));
        } else if (placementMode == PLACE_BUMPER) {
            btnBumper.setText("BUMPER\nTAP\nTO PLACE");
        } else {
            btnBumper.setText(String.format("BUMPER\n%.0f◆\n[%d/%d]", bumperCost(), bumpers.size, maxB));
        }

        if (!gravityUnlocked()) {
            btnGravityWell.setText("GRAVITY\nLOCKED\n→ CP II");
        } else if (attractors.size >= maxGravityAllowed()) {
            btnGravityWell.setText(String.format("GRAVITY\n%d/%d\nFULL", attractors.size, maxGravityAllowed()));
        } else if (placementMode == PLACE_GRAVITY) {
            btnGravityWell.setText("GRAVITY\nTAP\nTO PLACE");
        } else {
            btnGravityWell.setText(String.format("GRAVITY\n%.0f◆\n[%d/%d]", gravityCost(), attractors.size, maxGravityAllowed()));
        }

        float ringNow = centrifugeBody.getAngularVelocity();
        ringSpeedLabel.setText(String.format("%.1f r/s", ringNow));

        // ---- LAUNCH button: progress toward next checkpoint ----
        float eDelta = energyDeltaSinceLaunch();
        float eCost  = nextCheckpointEnergyCost();
        boolean jumpReady = eDelta >= eCost;
        String cpName = nextCPName();
        if (jumpReady) {
            btnFlight.setText("LAUNCH\n► " + cpName + " ◄\nGO!");
        } else {
            String prog;
            if (eCost >= 1_000_000f)
                prog = String.format("%.2fM/%.2fME", eDelta/1_000_000f, eCost/1_000_000f);
            else if (eCost >= 1_000f)
                prog = String.format("%.1f/%.1fKE", eDelta/1_000f, eCost/1_000f);
            else
                prog = String.format("%.0f/%.0fE", eDelta, eCost);
            btnFlight.setText("LAUNCH\n" + prog + "\n→ " + cpName);
        }

        // ---- JUMP READY centrifuge button ----
        btnJumpReady.setVisible(jumpReady);
        if (jumpReady) {
            float pulse = 0.60f + MathUtils.sin(animTime * 5f) * 0.40f;
            btnJumpReady.setColor(pulse, 1f, pulse * 0.7f + 0.3f, 1f);
        }

        // ---- Button tints ----
        Color BUY  = new Color(0.70f, 1.00f, 0.72f, 1f);
        Color LOCK = new Color(0.50f, 0.50f, 0.55f, 1f);
        Color ACT  = new Color(1.00f, 0.90f, 0.30f, 1f);
        Color GO   = new Color(0.27f, 1.00f, 0.55f, 1f);
        Color NORM = Color.WHITE;
        btnAdd.setColor(balls.size >= cap ? LOCK : sd.crystals >= internCost() ? BUY : NORM);
        btnBumper.setColor(placementMode == PLACE_BUMPER ? ACT : maxB == 0 || bumpers.size >= maxB ? LOCK : sd.crystals >= bumperCost() ? BUY : NORM);
        btnGravityWell.setColor(placementMode == PLACE_GRAVITY ? ACT : !gravityUnlocked() || attractors.size >= maxGravityAllowed() ? LOCK : sd.crystals >= gravityCost() ? BUY : NORM);
        btnFlight.setColor(jumpReady ? GO : eDelta / Math.max(eCost, 1f) > 0.6f ? new Color(1f, 0.85f, 0.3f, 1f) : NORM);

        // Re-apply font scale each frame (setText resets it)
        for (TextButton btn : new TextButton[]{btnAdd, btnBumper, btnGravityWell, btnFlight}) {
            btn.getLabel().setFontScale(0.68f);
        }

        configLabel.setText(currentPlanet.name);

        // Rebuild perks table only when a new perk unlocks (index 4 excluded — one-time event)
        int unlockedCount = 0;
        for (int i = 0; i < milestoneAchieved.length; i++) if (milestoneAchieved[i] && i != 4) unlockedCount++;
        if (unlockedCount != lastUnlockedPerkCount) {
            rebuildPerksTable();
            lastUnlockedPerkCount = unlockedCount;
        }

        // Align perks panel top exactly with headerY (same level as SOLARA in batch space)
        float headerY = CCY_PX + CENTRIFUGE_R * PPM + (SP_MAX + 1) * 17f + 6f;
        rightPerksTable.pack();
        rightPerksTable.setPosition(330f, headerY - rightPerksTable.getPrefHeight());

        ui.draw();

        if (!tutorialDone) {
            batch.begin();
            drawTutorial();
            batch.end();
        }

        if (internAddedTimer > 0) {
            batch.begin();
            drawInternAddedOverlay();
            batch.end();
        }

        if (notifTimer > 0) {
            batch.begin();
            drawNotifOverlay();
            batch.end();
        }
    }

    private void drawTutorial() {
        ShipData.PlanetProfile planet = ShipData.get().getCurrentPlanet();
        // Dark overlay
        batch.setColor(0f, 0f, 0f, 0.86f);
        batch.draw(texPixel, 0, 0, RENDER_W, RENDER_H);
        batch.setColor(1f, 1f, 1f, 1f);

        float cx = RENDER_W * 0.5f;
        float y  = RENDER_H * 0.84f;
        float lh = 27f;

        floatFont.getData().setScale(1.55f);
        floatFont.setColor(0.20f, 0.80f, 1.00f, 1f);
        drawFontCentered("ENGINEERING BAY", cx, y);
        y -= lh * 1.3f;

        floatFont.getData().setScale(0.95f);
        floatFont.setColor(1f, 0.82f, 0.30f, 1f);
        drawFontCentered(planet.name.toUpperCase() + " SYSTEM", cx, y);
        y -= lh * 1.6f;

        floatFont.getData().setScale(0.78f);
        float a = 0.90f;

        floatFont.setColor(0.85f, 0.88f, 1f, a);
        drawFontCentered("Interns (orbs) bounce inside the centrifuge ring.", cx, y); y -= lh;

        floatFont.setColor(0.25f, 0.95f, 1f, a);
        drawFontCentered("Each collision earns SPACE POINTS  ◆", cx, y); y -= lh;

        floatFont.setColor(0.85f, 0.88f, 1f, a);
        drawFontCentered("Spend  ◆  to buy more Interns, Bumpers", cx, y); y -= lh * 0.78f;
        drawFontCentered("and Gravity Wells.", cx, y); y -= lh * 1.15f;

        floatFont.setColor(0.27f, 1f, 0.55f, a);
        drawFontCentered("More Interns  →  faster ring  →  more Energy (E)", cx, y); y -= lh;

        floatFont.setColor(1f, 0.62f, 0.12f, a);
        drawFontCentered("Energy fuels your LAUNCH to the next checkpoint.", cx, y); y -= lh * 1.6f;

        float pulse = 0.55f + MathUtils.sin(animTime * 3.5f) * 0.45f;
        floatFont.getData().setScale(0.88f);
        floatFont.setColor(0.55f, 0.60f, 0.70f, pulse);
        drawFontCentered("TAP ANYWHERE TO BEGIN", cx, y);

        floatFont.getData().setScale(1f);
    }

    private void drawFontCentered(String text, float cx, float y) {
        floatLayout.setText(floatFont, text);
        floatFont.draw(batch, text, cx - floatLayout.width * 0.5f, y);
    }

    private void drawBackground() {
        batch.draw(texBackground, 0, 0, RENDER_W, RENDER_H);
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
        float fHalf     = fieldDiam * 0.5f;
        float hw        = BUMPER_W * 0.5f, hh = BUMPER_H * 0.5f;
        float spin      = animTime * 55f;                              // slow outer ring rotation
        float spinBack  = -animTime * 85f;                             // inner ring counter-rotation
        float pulse     = 1f + MathUtils.sin(animTime * 3.2f) * 0.10f; // scale pulse

        for (int i = 0; i < attractors.size; i++) {
            Vector2 pos = attractors.get(i).getPosition();
            float px = pos.x * PPM, py = pos.y * PPM;

            // Outer rotating field — slow purple ring
            float fd = fieldDiam * pulse;
            batch.setColor(0.55f, 0.25f, 1f, 0.45f);
            batch.draw(texGravField,
                px - fd * 0.5f, py - fd * 0.5f,
                fd * 0.5f, fd * 0.5f, fd, fd, 1f, 1f, spin,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Inner counter-rotating smaller ring — brighter
            float fi = fieldDiam * 0.62f * pulse;
            batch.setColor(0.75f, 0.35f, 1f, 0.60f);
            batch.draw(texGravField,
                px - fi * 0.5f, py - fi * 0.5f,
                fi * 0.5f, fi * 0.5f, fi, fi, 1f, 1f, spinBack,
                0, 0, texGravField.getWidth(), texGravField.getHeight(), false, false);

            // Core bumper
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(texBumper, px - hw, py - hh, BUMPER_W, BUMPER_H);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawBumpers() {
        for (int i = 0, n = bumpers.size; i < n; i++) {
            Body body = bumpers.get(i);
            Vector2 pos = body.getPosition();
            float px = pos.x * PPM, py = pos.y * PPM;

            ShipData.BumperHitData bud = (ShipData.BumperHitData) body.getUserData();
            long   hitElapsed = TimeUtils.millis() - bud.lastHitMs;
            float  hitFrac    = Math.max(0f, 1f - hitElapsed / 280f);
            float  hitScale   = 1f + hitFrac * 0.60f;
            float  hitAngle   = (i % 2 == 0 ? 1f : -1f) * hitFrac * 35f;
            float  dw = BUMPER_W * hitScale, dh = BUMPER_H * hitScale;

            batch.setColor(1f, 1f + hitFrac * 0.45f, 1f + hitFrac * 0.3f, 1f);
            batch.draw(texBumper,
                px - dw * 0.5f, py - dh * 0.5f,
                dw * 0.5f, dh * 0.5f,
                dw, dh, 1f, 1f, hitAngle,
                0, 0, texBumper.getWidth(), texBumper.getHeight(), false, false);
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


    private void drawSpacePoints() {
        float lineH    = 17f;
        float drumTopY = CCY_PX + CENTRIFUGE_R * PPM;
        float headerY  = drumTopY + (SP_MAX + 1) * lineH + 6f;

        // --- Left: planet name + checkpoint ---
        ShipData lsd = ShipData.get();
        floatFont.getData().setScale(0.72f);
        floatFont.setColor(0.35f, 0.82f, 1f, 0.88f);
        floatFont.draw(batch, lsd.getCurrentPlanet().name.toUpperCase(), 8f, headerY);

        floatFont.getData().setScale(0.60f);
        floatFont.setColor(0.68f, 0.68f, 0.80f, 0.80f);
        int lsr = lsd.sectorReached;
        String cpText = lsr < 0  ? "No checkpoint yet" :
                        lsr == 0 ? "Checkpoint I ✓" :
                        lsr == 1 ? "Checkpoint II ✓" :
                        lsr == 2 ? "Checkpoint III ✓" : "Arrived!";
        floatFont.draw(batch, cpText, 8f, headerY - lineH * 0.85f);

        // --- Left: live spark breakdown ---
        ShipData sd2 = ShipData.get();
        float collVal  = 20f * sd2.collisionEnergyMult;
        float bumpVal  = sd2.bumperSparkValue;
        floatFont.getData().setScale(0.52f);
        floatFont.setColor(0.55f, 0.85f, 0.55f, 0.80f);
        floatFont.draw(batch,
            String.format("col %.0f  bump %.0f  grav 50  wall 0.5 ◆", collVal, bumpVal),
            8f, headerY - lineH * 1.75f);

        // --- Center: SPACE POINTS header + ◆/s rate ---
        floatFont.getData().setScale(0.68f);
        floatFont.setColor(0.50f, 0.78f, 1f, 0.55f);
        drawFontCentered("SPACE POINTS", CCX_PX, headerY);

        floatFont.getData().setScale(1.15f);
        floatFont.setColor(0.25f, 0.95f, 1f, 0.92f);
        drawFontCentered(formatNumber(sparkRate) + " ◆/s", CCX_PX, headerY - lineH * 1.4f);

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
        freeInternGiven      = true;
        centrifugeRpmMax     = 10.0f;
        milestoneAchieved[4] = true;
        // Spawn extra intern uncapped
        float angle = MathUtils.random(MathUtils.PI2);
        float r     = MathUtils.random(0.5f, CENTRIFUGE_R * 0.45f);
        float wx    = CENTRIFUGE_CX + MathUtils.cos(angle) * r;
        float wy    = CENTRIFUGE_CY + MathUtils.sin(angle) * r;
        // Spawn directly bypassing internCap check
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(wx, wy);
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
        showNotif("BONUS INTERN DEPLOYED!",
            "Max interns reached · Ring now targets 10.0 r/s");
    }

    private void showNotif(String title, String body) {
        notifTitle = title;
        notifBody  = body;
        notifTimer = NOTIF_HOLD;
    }

    private void drawNotifOverlay() {
        float t = notifTimer / NOTIF_HOLD;
        float alpha = t > 0.88f ? (1f - t) / 0.12f
                    : t < 0.18f ? t / 0.18f
                    : 1f;
        alpha = Math.min(1f, alpha);

        float pw = 330f, ph = 90f;
        float px = (RENDER_W - pw) * 0.5f;
        float py = RENDER_H * 0.60f;

        batch.setColor(0f, 0.04f, 0.14f, 0.88f * alpha);
        batch.draw(texPixel, px, py, pw, ph);
        batch.setColor(1f, 1f, 1f, 0.15f * alpha);
        batch.draw(texPixel, px, py + ph - 2f, pw, 2f);
        batch.draw(texPixel, px, py, pw, 2f);

        float cx = RENDER_W * 0.5f;
        float y  = py + ph - 18f;

        floatFont.getData().setScale(0.95f);
        floatFont.setColor(1f, 0.88f, 0.28f, alpha);
        drawFontCentered(notifTitle, cx, y);
        y -= 28f;

        // Body may contain a '\n' — split and draw two lines
        String[] lines = notifBody.split("\n", 2);
        floatFont.getData().setScale(0.72f);
        floatFont.setColor(0.78f, 0.82f, 0.92f, alpha * 0.90f);
        for (String line : lines) {
            drawFontCentered(line, cx, y);
            y -= 20f;
        }

        floatFont.getData().setScale(1f);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawInternAddedOverlay() {
        float t = internAddedTimer / INTERN_ADDED_HOLD;
        float alpha = t > 0.85f ? (1f - t) / 0.15f   // fade in (first 15% of hold time reversed)
                    : t < 0.20f ? t / 0.20f            // fade out
                    : 1f;
        alpha = Math.min(1f, alpha);

        // Panel background
        float pw = 320f, ph = 110f;
        float px = (RENDER_W - pw) * 0.5f, py = RENDER_H * 0.38f;
        batch.setColor(0f, 0.04f, 0.14f, 0.88f * alpha);
        batch.draw(texPixel, px, py, pw, ph);

        float cx = RENDER_W * 0.5f;
        float y  = py + ph - 18f;

        floatFont.getData().setScale(1.1f);
        floatFont.setColor(0.27f, 1f, 0.55f, alpha);
        drawFontCentered("INTERN DEPLOYED", cx, y);
        y -= 30f;

        floatFont.getData().setScale(0.80f);
        floatFont.setColor(0.75f, 0.80f, 1f, alpha * 0.85f);
        drawFontCentered("TARGET RING SPEED", cx, y);
        y -= 26f;

        floatFont.getData().setScale(1.2f);
        floatFont.setColor(1f, 0.70f, 0.18f, alpha);
        drawFontCentered(String.format("%.1f r/s  →  %.1f r/s", internAddedOldSpeed, internAddedNewSpeed), cx, y);

        floatFont.getData().setScale(1f);
        batch.setColor(1f, 1f, 1f, 1f);
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
            if (i == 4) continue; // free intern: triggered by maxing interns, not ring speed
            // Index 0 (Elastic Walls) is CP I reward — only applied via applySectorPerks, never here
            if (i > 0 && !milestoneAchieved[i] && targetRPM >= MILESTONE_RPMS[i]) {
                milestoneAchieved[i] = true;
                switch (i) {
                    case 1: applyResonance();                           break;
                    case 2: ShipData.get().wallEnergyMult = 3f;         break;
                    case 3: ShipData.get().collisionEnergyMult = 2f;    break;
                    case 5: applyOverdrive();                           break;
                }
                showNotif("PERK UNLOCKED", MILESTONE_NAMES[i] + "\n" + MILESTONE_DESCS[i]);
            }
            if (!milestoneAchieved[i] && nextIdx < 0) nextIdx = i;
        }
        if (nextIdx == 0) {
            milestoneStatusLabel.setColor(0.62f, 0.62f, 0.70f, 1f);
            milestoneStatusLabel.setText("Reach Checkpoint I to unlock Elastic Walls");
        } else if (nextIdx > 0) {
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
        targetRPM = Math.min(CENTRIFUGE_RPM_BASE + balls.size * 0.75f, centrifugeRpmMax);

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

        float dt = Math.min(delta, 1f / 30f);
        world.step(dt, VEL_ITER, POS_ITER);

        // Passive joule generation: ring speed × intern count
        float ringSpeed = centrifugeBody.getAngularVelocity();
        if (ringSpeed > 0f && balls.size > 0) {
            ShipData.get().addJoules(ringSpeed * balls.size * 3f * dt);
        }

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
            ShipData sd = ShipData.get();
            sd.currentJPS = (sd.totalJoules - lastJoules) / jpsTimer;
            lastJoules    = sd.totalJoules;
            sparkRate     = Math.max(0f, (sd.crystals - lastCrystals) / jpsTimer);
            lastCrystals  = sd.crystals;
            jpsTimer      = 0f;
        }
    }

    // ---- Helpers ----------------------------------------------------------------


    private int internCap() {
        int sr = ShipData.get().sectorReached;
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
        if (sr >= 2) return 4;
        return 3;
    }

    private boolean gravityUnlocked()       { return ShipData.get().sectorReached >= 1; }
    private boolean tierUpgradesUnlocked()  { return ShipData.get().sectorReached >= 2; }

    private float internCost() {
        int idx = Math.max(0, balls.size - 2);
        return idx < INTERN_COSTS.length ? INTERN_COSTS[idx] : INTERN_COSTS[INTERN_COSTS.length - 1];
    }
    private float bumperCost() {
        int idx = bumpers.size;
        return idx < BUMPER_COSTS.length ? BUMPER_COSTS[idx] : BUMPER_COSTS[BUMPER_COSTS.length - 1];
    }
    private float gravityCost() {
        int idx = attractors.size;
        return idx < GRAVITY_COSTS.length ? GRAVITY_COSTS[idx] : GRAVITY_COSTS[GRAVITY_COSTS.length - 1];
    }

    private float energyDeltaSinceLaunch() {
        ShipData sd = ShipData.get();
        return sd.powerGenerated - sd.energyAtLastLaunch;
    }

    private float nextCheckpointEnergyCost() {
        ShipData sd = ShipData.get();
        float totalRoute = Math.max(1000f, sd.targetPlanetDistance);
        float[] sectors = BridgeFlightScreen.buildSectorDistances(totalRoute);
        int sr = sd.sectorReached;
        float currentDist = (sr >= 0 && sr < sectors.length) ? sectors[sr] : 0f;
        int nextIdx = sr + 1;
        float nextDist = nextIdx < sectors.length ? sectors[nextIdx] : totalRoute;
        return (nextDist - currentDist) / BridgeFlightScreen.ENERGY_AU_SCALE;
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
        texPixel.dispose();
        floatFont.dispose();
        ui.dispose();
    }
}
