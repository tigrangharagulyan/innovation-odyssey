package com.odyssey.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.odyssey.GameState;
import com.odyssey.OdysseyGame;
import com.odyssey.ShipData;

public class InternDeployScreen extends ScreenAdapter {

    private static final float W = 480f, H = 854f;

    private static final int   MAX_INTERNS      = 12;
    private static final int   RECRUIT_COUNT    = 2;
    private static final float FALL_TIME        = 0.90f; // each intern's travel duration
    private static final float INTERN_STAGGER   = 0.18f; // delay between each intern launch
    private static final float FLY_DURATION     = 1.80f; // rocket travel between planets
    private static final float RECRUIT_TIME     = 1.30f; // each recruit's travel duration
    private static final float RECRUIT_STAGGER  = 0.55f;

    // Planet positions
    private static final float FROM_X  = 95f,      FROM_Y  = H * 0.46f;
    private static final float TO_X    = W - 95f,  TO_Y    = H * 0.46f;
    private static final float FROM_R  = 60f;
    private static final float TO_R    = 52f;
    private static final float ORBIT_R = FROM_R + 14f;

    // Rocket hover height above planet surface
    private static final float HOVER_H = 50f;

    // Planet colors [innerR,G,B, outerR,G,B]
    private static final float[][] PLANET_COL = {
        {1.0f, 0.78f, 0.12f,  0.95f, 0.35f, 0.05f}, // Solara
        {0.75f, 0.22f, 1.00f, 0.40f, 0.05f, 0.70f}, // Ember IV
        {0.55f, 0.90f, 1.00f, 0.05f, 0.40f, 0.75f}, // Frostheim
        {0.45f, 0.80f, 1.00f, 0.05f, 0.35f, 0.72f}, // Cryon Reach
        {1.00f, 0.45f, 0.12f, 0.70f, 0.10f, 0.02f}, // Helios Forge
    };

    private final OdysseyGame game;
    private SpriteBatch   batch;
    private ShapeRenderer sr;
    private BitmapFont    font;
    private FitViewport   viewport;
    private OrthographicCamera cam;
    private Texture       texGlow;

    private float animTime = 0f;
    private boolean applied = false;

    // Computed in show()
    private int   deployCount;
    private int   fromPlanetIdx, toPlanetIdx;
    private float tDeployDone;   // last intern lands
    private float tFlyStart;
    private float tFlyEnd;
    private float tRecruitStart;
    private float tRecruitDone;
    private float tReady;

    // Orbit positions around from-planet
    private final float[] orbitX = new float[MAX_INTERNS];
    private final float[] orbitY = new float[MAX_INTERNS];

    // Stars
    private static final int N_STARS = 110;
    private final float[] sX = new float[N_STARS];
    private final float[] sY = new float[N_STARS];
    private final float[] sR = new float[N_STARS];
    private final float[] sA = new float[N_STARS];

    private final GlyphLayout gl = new GlyphLayout();

    public InternDeployScreen(OdysseyGame game) { this.game = game; }

    @Override
    public void show() {
        cam      = new OrthographicCamera();
        viewport = new FitViewport(W, H, cam);
        batch    = new SpriteBatch();
        sr       = new ShapeRenderer();
        sr.setAutoShapeType(true);
        font     = game.skin.getFont("font");

        int gs = 64;
        Pixmap gpm = new Pixmap(gs, gs, Pixmap.Format.RGBA8888);
        for (int yy = 0; yy < gs; yy++) for (int xx = 0; xx < gs; xx++) {
            float dx = xx - gs*.5f, dy = yy - gs*.5f;
            float a  = Math.max(0f, 1f - (dx*dx+dy*dy)/((gs*.5f)*(gs*.5f)));
            gpm.setColor(1f,1f,1f,a); gpm.drawPixel(xx,yy);
        }
        texGlow = new Texture(gpm);
        gpm.dispose();

        for (int i = 0; i < N_STARS; i++) {
            sX[i] = MathUtils.random(0f,W); sY[i] = MathUtils.random(0f,H);
            sR[i] = MathUtils.random(0.8f,2.5f); sA[i] = MathUtils.random(0.25f,0.90f);
        }

        ShipData sd = ShipData.get();
        toPlanetIdx   = sd.currentPlanetIndex;
        fromPlanetIdx = Math.max(0, toPlanetIdx - 1);
        deployCount   = 12;  // 12 interns auto-stationed per planet (gem farming system)

        // Evenly-spaced orbit positions starting at top of from-planet
        for (int i = 0; i < deployCount; i++) {
            float angle = (float)(Math.PI * 2.0 * i / deployCount) - MathUtils.PI / 2f;
            orbitX[i] = FROM_X + MathUtils.cos(angle) * ORBIT_R;
            orbitY[i] = FROM_Y + MathUtils.sin(angle) * ORBIT_R;
        }

        tDeployDone    = INTERN_STAGGER * (deployCount - 1) + FALL_TIME + 0.2f;
        tFlyStart      = tDeployDone + 0.4f;
        tFlyEnd        = tFlyStart + FLY_DURATION;
        tRecruitStart  = tFlyEnd + 0.35f;
        tRecruitDone   = tRecruitStart + RECRUIT_STAGGER * (RECRUIT_COUNT - 1) + RECRUIT_TIME;
        tReady         = tRecruitDone + 0.4f;

        animTime = 0f;
        applied  = false;
    }

    @Override
    public void render(float delta) {
        animTime += delta;
        if (!applied && animTime >= tReady) { applyFarming(); applied = true; }

        Gdx.gl.glClearColor(0.01f, 0.02f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();

        drawBackground();
        drawPlanets();
        drawOrbitInterns();
        drawDeployParticles();
        drawRecruitParticles();
        drawRocket();
        drawUI();

        if (animTime >= tReady && Gdx.input.justTouched()) {
            game.resetLabScreen();
            game.transitionTo(GameState.ENGINEERING_LAB);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** Current rocket screen position based on animation phase. */
    private float rocketX() {
        float fromHoverY = FROM_Y + FROM_R + HOVER_H;
        float toHoverY   = TO_Y   + TO_R   + HOVER_H;
        if (animTime <= tFlyStart) return FROM_X;
        if (animTime >= tFlyEnd)   return TO_X;
        float t  = (animTime - tFlyStart) / FLY_DURATION;
        float ct = smoothstep(t);
        // Bezier: p0=(FROM_X,fromHoverY), p2=(TO_X,toHoverY), ctrl at mid-top
        float cx = W * 0.5f, cy = Math.max(fromHoverY, toHoverY) + 80f;
        return (1-ct)*(1-ct)*FROM_X + 2*(1-ct)*ct*cx + ct*ct*TO_X;
    }

    private float rocketY() {
        float fromHoverY = FROM_Y + FROM_R + HOVER_H;
        float toHoverY   = TO_Y   + TO_R   + HOVER_H;
        if (animTime <= tFlyStart) return fromHoverY + MathUtils.sin(animTime * 1.8f) * 4f;
        if (animTime >= tFlyEnd)   return toHoverY   + MathUtils.sin(animTime * 1.8f) * 4f;
        float t  = (animTime - tFlyStart) / FLY_DURATION;
        float ct = smoothstep(t);
        float cx = W * 0.5f, cy = Math.max(fromHoverY, toHoverY) + 80f;
        return (1-ct)*(1-ct)*fromHoverY + 2*(1-ct)*ct*cy + ct*ct*toHoverY;
    }

    /** Rocket points up (π/2) during hover, tilts toward travel direction during flight. */
    private float rocketAngle() {
        // atan2(to-from) for travel direction
        float flyAngle = MathUtils.atan2(TO_Y - FROM_Y, TO_X - FROM_X);
        if (animTime <= tFlyStart) return MathUtils.PI / 2f;
        if (animTime >= tFlyEnd)   return MathUtils.PI / 2f;
        float t = (animTime - tFlyStart) / FLY_DURATION;
        // blend: ramp in over first 25%, ramp out over last 25%
        float blend;
        if (t < 0.25f)      blend = t / 0.25f;
        else if (t > 0.75f) blend = (1f - t) / 0.25f;
        else                blend = 1f;
        return MathUtils.PI / 2f + (flyAngle - MathUtils.PI / 2f) * blend;
    }

    private static float smoothstep(float t) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ── drawing ──────────────────────────────────────────────────────────────────

    private void drawBackground() {
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < N_STARS; i++) {
            float tw = 0.5f + 0.5f * MathUtils.sin(animTime * 1.3f + i * 0.83f);
            sr.setColor(1f, 1f, 1f, sA[i] * (0.35f + 0.65f * tw));
            sr.circle(sX[i], sY[i], sR[i]);
        }
        sr.end();
    }

    private void drawPlanets() {
        float[] fc = col(fromPlanetIdx), tc = col(toPlanetIdx);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        drawGlow(FROM_X, FROM_Y, FROM_R, fc);
        drawGlow(TO_X,   TO_Y,   TO_R,   tc);
        batch.end();

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        drawPlanetBody(FROM_X, FROM_Y, FROM_R, fc);
        drawPlanetBody(TO_X,   TO_Y,   TO_R,   tc);
        sr.end();
    }

    private void drawGlow(float cx, float cy, float r, float[] c) {
        float g = r * 3.5f;
        batch.setColor(c[0], c[1], c[2], 0.20f);
        batch.draw(texGlow, cx - g, cy - g, g * 2f, g * 2f);
        float gi = r * 2f;
        batch.setColor(c[0], c[1], c[2], 0.35f);
        batch.draw(texGlow, cx - gi, cy - gi, gi * 2f, gi * 2f);
    }

    private void drawPlanetBody(float cx, float cy, float r, float[] c) {
        sr.setColor(c[3], c[4], c[5], 1f);  sr.circle(cx, cy, r);
        sr.setColor(c[0], c[1], c[2], 0.55f); sr.circle(cx - r*.22f, cy + r*.18f, r*.60f);
        sr.setColor(1f,1f,1f,0.18f);          sr.circle(cx - r*.30f, cy + r*.35f, r*.28f);
        for (float rr = r+2f; rr <= r+10f; rr += 1.5f) {
            sr.setColor(c[0], c[1], c[2], 0.14f*(1f-(rr-r)/10f));
            sr.circle(cx, cy, rr);
        }
    }

    /** Interns that have already landed stay orbiting the from-planet. */
    private void drawOrbitInterns() {
        if (animTime < INTERN_STAGGER * 0 + FALL_TIME * 0.8f) return;
        float[] fc = col(fromPlanetIdx);
        float orbitSpeed = animTime * 0.40f;

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < deployCount; i++) {
            float launchTime = i * INTERN_STAGGER;
            float landTime   = launchTime + FALL_TIME;
            if (animTime < landTime) continue; // not yet landed

            float showA = Math.min(1f, (animTime - landTime) / 0.25f);
            float baseAngle = (float)(Math.PI * 2.0 * i / deployCount) - MathUtils.PI / 2f;
            float angle = baseAngle + orbitSpeed;
            float dx = FROM_X + MathUtils.cos(angle) * ORBIT_R;
            float dy = FROM_Y + MathUtils.sin(angle) * ORBIT_R;

            sr.setColor(fc[0], fc[1], fc[2], 0.28f * showA);
            sr.circle(dx, dy, 7f, 10);
            sr.setColor(fc[0], fc[1], fc[2], 0.92f * showA);
            sr.circle(dx, dy, 4.5f, 8);
            sr.setColor(fc[3], fc[4], fc[5], 0.75f * showA);
            sr.circle(dx, dy, 2.2f, 6);
        }
        sr.end();
    }

    /** Interns flying from rocket toward their orbit positions. */
    private void drawDeployParticles() {
        float[] fc = col(fromPlanetIdx);
        float rx = FROM_X, ry = FROM_Y + FROM_R + HOVER_H;

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < deployCount; i++) {
            float launchTime = i * INTERN_STAGGER;
            float landTime   = launchTime + FALL_TIME;
            if (animTime < launchTime || animTime >= landTime) continue;

            float t  = (animTime - launchTime) / FALL_TIME;
            float ex = orbitX[i], ey = orbitY[i];
            // control point: midpoint shifted sideways for a gentle arc
            float side = ((i % 2 == 0) ? 1f : -1f) * 22f;
            float ctlX = (rx + ex) * 0.5f + side;
            float ctlY = (ry + ey) * 0.5f - 20f;
            float bx = (1-t)*(1-t)*rx + 2*(1-t)*t*ctlX + t*t*ex;
            float by = (1-t)*(1-t)*ry + 2*(1-t)*t*ctlY + t*t*ey;

            float alpha = t < 0.12f ? t/0.12f : (t > 0.85f ? (1f-t)/0.15f : 1f);
            sr.setColor(fc[0], fc[1], fc[2], 0.28f * alpha);
            sr.circle(bx, by, 9f, 10);
            sr.setColor(fc[0], fc[1], fc[2], alpha);
            sr.circle(bx, by, 5f,  8);
            sr.setColor(1f, 1f, 1f, 0.80f * alpha);
            sr.circle(bx, by, 2.2f, 6);
        }
        sr.end();
    }

    /** 2 recruits fly from to-planet up to the rocket. */
    private void drawRecruitParticles() {
        if (animTime < tRecruitStart) return;
        float[] tc = col(toPlanetIdx);
        float toHoverY = TO_Y + TO_R + HOVER_H;

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < RECRUIT_COUNT; i++) {
            float launchTime = tRecruitStart + i * RECRUIT_STAGGER;
            float arriveTime = launchTime + RECRUIT_TIME;
            if (animTime < launchTime || animTime >= arriveTime) continue;

            float t  = (animTime - launchTime) / RECRUIT_TIME;
            float sx = TO_X + MathUtils.cos(i * 1.8f + 0.5f) * TO_R * 0.6f;
            float sy = TO_Y + MathUtils.sin(i * 1.8f + 0.5f) * TO_R * 0.6f;
            float ex = TO_X, ey = toHoverY;
            float ctlX = (sx + ex) * 0.5f + (i == 0 ? -20f : 20f);
            float ctlY = (sy + ey) * 0.5f + 30f;
            float bx = (1-t)*(1-t)*sx + 2*(1-t)*t*ctlX + t*t*ex;
            float by = (1-t)*(1-t)*sy + 2*(1-t)*t*ctlY + t*t*ey;

            float alpha = t < 0.12f ? t/0.12f : (t > 0.85f ? (1f-t)/0.15f : 1f);
            sr.setColor(tc[0], tc[1], tc[2], 0.28f * alpha);
            sr.circle(bx, by, 9f, 10);
            sr.setColor(tc[0], tc[1], tc[2], alpha);
            sr.circle(bx, by, 5f,  8);
            sr.setColor(1f, 1f, 1f, 0.80f * alpha);
            sr.circle(bx, by, 2.2f, 6);
        }
        sr.end();
    }

    private void drawRocket() {
        float rx = rocketX(), ry = rocketY();
        float a  = rocketAngle();
        float ca = MathUtils.cos(a), sa = MathUtils.sin(a);
        float u  = 0.82f;

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Engine glow
        float gp = 0.55f + 0.45f * MathUtils.sin(animTime * 4.8f);
        sr.setColor(0.55f, 0.82f, 1f, 0.14f * gp);
        sr.circle(rx - u*18f*ca, ry - u*18f*sa, u * 24f, 18);

        // Flame outer
        { float hw=u*5f, bl=-u*10f, tip=-u*24f;
          float bx1=rx+bl*ca-hw*sa, by1=ry+bl*sa+hw*ca,
                bx2=rx+bl*ca+hw*sa, by2=ry+bl*sa-hw*ca,
                tx =rx+tip*ca,       ty =ry+tip*sa;
          sr.setColor(0.35f, 0.82f, 1f, 0.45f*gp);
          sr.triangle(bx1,by1, bx2,by2, tx,ty); }

        // Flame inner
        { float hw=u*3.5f, bl=-u*10f, tip=-u*20f;
          float bx1=rx+bl*ca-hw*sa, by1=ry+bl*sa+hw*ca,
                bx2=rx+bl*ca+hw*sa, by2=ry+bl*sa-hw*ca,
                tx =rx+tip*ca,       ty =ry+tip*sa;
          sr.setColor(0.82f, 0.96f, 1f, 0.92f);
          sr.triangle(bx1,by1, bx2,by2, tx,ty); }

        // Left fin
        { float f1x=rx+u*3f*ca-u*9f*sa,  f1y=ry+u*3f*sa+u*9f*ca,
                f2x=rx-u*14f*ca-u*9f*sa, f2y=ry-u*14f*sa+u*9f*ca,
                f3x=rx-u*18f*ca-u*22f*sa,f3y=ry-u*18f*sa+u*22f*ca;
          sr.setColor(0.26f, 0.40f, 0.60f, 1f);
          sr.triangle(f1x,f1y, f2x,f2y, f3x,f3y); }

        // Right fin
        { float f1x=rx+u*3f*ca+u*9f*sa,  f1y=ry+u*3f*sa-u*9f*ca,
                f2x=rx-u*14f*ca+u*9f*sa, f2y=ry-u*14f*sa-u*9f*ca,
                f3x=rx-u*18f*ca+u*22f*sa,f3y=ry-u*18f*sa-u*22f*ca;
          sr.setColor(0.26f, 0.40f, 0.60f, 1f);
          sr.triangle(f1x,f1y, f2x,f2y, f3x,f3y); }

        // Engine bell (two tris)
        { float x1=rx-u*10f*ca-u*8f*sa, y1=ry-u*10f*sa+u*8f*ca,
                x2=rx-u*10f*ca+u*8f*sa, y2=ry-u*10f*sa-u*8f*ca,
                x3=rx-u*18f*ca-u*11f*sa,y3=ry-u*18f*sa+u*11f*ca,
                x4=rx-u*18f*ca+u*11f*sa,y4=ry-u*18f*sa-u*11f*ca;
          sr.setColor(0.22f, 0.33f, 0.48f, 1f);
          sr.triangle(x1,y1, x2,y2, x3,y3);
          sr.triangle(x2,y2, x3,y3, x4,y4); }

        // Body
        { float bw=u*9f;
          float x1=rx+u*13f*ca-bw*sa, y1=ry+u*13f*sa+bw*ca,
                x2=rx+u*13f*ca+bw*sa, y2=ry+u*13f*sa-bw*ca,
                x3=rx-u*10f*ca-bw*sa, y3=ry-u*10f*sa+bw*ca,
                x4=rx-u*10f*ca+bw*sa, y4=ry-u*10f*sa-bw*ca;
          sr.setColor(0.33f, 0.50f, 0.72f, 1f);
          sr.triangle(x1,y1, x2,y2, x3,y3);
          sr.triangle(x2,y2, x3,y3, x4,y4); }

        // Nose
        { float bw=u*9f;
          float x1=rx+u*13f*ca-bw*sa, y1=ry+u*13f*sa+bw*ca,
                x2=rx+u*13f*ca+bw*sa, y2=ry+u*13f*sa-bw*ca,
                tx=rx+u*28f*ca, ty=ry+u*28f*sa;
          sr.setColor(0.52f, 0.70f, 0.90f, 1f);
          sr.triangle(x1,y1, x2,y2, tx,ty); }

        // Porthole
        float pcx=rx+u*5f*ca, pcy=ry+u*5f*sa;
        sr.setColor(0.18f, 0.26f, 0.42f, 1f); sr.circle(pcx,pcy, u*4f, 12);
        sr.setColor(0.58f, 0.84f, 1f, 0.85f); sr.circle(pcx,pcy, u*2.4f, 10);

        sr.end();
    }

    private void drawUI() {
        ShipData sd = ShipData.get();
        String fromName = ShipData.PLANETS[fromPlanetIdx].name;
        String toName   = ShipData.PLANETS[toPlanetIdx].name;
        float[] fc = col(fromPlanetIdx), tc = col(toPlanetIdx);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        // Title + route
        font.getData().setScale(1.10f);
        font.setColor(0.78f, 0.92f, 1f, 1f);
        drawCentered("CREW HANDOFF", W * 0.5f, H - 48f);
        font.getData().setScale(0.65f);
        font.setColor(0.45f, 0.65f, 0.85f, 0.80f);
        drawCentered(fromName + "  →  " + toName, W * 0.5f, H - 76f);

        // Planet name labels
        font.getData().setScale(0.70f);
        font.setColor(fc[0], fc[1], fc[2], 0.92f);
        drawCentered(fromName, FROM_X, FROM_Y - FROM_R - 20f);
        font.setColor(tc[0], tc[1], tc[2], 0.92f);
        drawCentered(toName,   TO_X,   TO_Y   - TO_R   - 20f);

        // Big farm rate — shown after all interns land
        if (animTime >= tDeployDone) {
            float fa = Math.min(1f, (animTime - tDeployDone) / 0.55f);
            int ratePerHr = (fromPlanetIdx < ShipData.GEM_FARM_RATES.length)
                ? ShipData.GEM_FARM_RATES[fromPlanetIdx] : 0;
            String rateStr = ratePerHr >= 1000
                ? String.format("+%d,%03d SP/HR", ratePerHr / 1000, ratePerHr % 1000)
                : String.format("+%d SP/HR", ratePerHr);

            font.getData().setScale(1.55f);
            font.setColor(fc[0], fc[1], fc[2], fa);
            drawCentered(rateStr, FROM_X, FROM_Y + FROM_R + 72f);

            font.getData().setScale(0.68f);
            font.setColor(fc[0]*.75f, fc[1]*.75f, fc[2]*.75f, fa * 0.85f);
            drawCentered("FARMING RATE", FROM_X, FROM_Y + FROM_R + 50f);

            font.getData().setScale(0.65f);
            font.setColor(0.50f, 0.88f, 0.58f, fa);
            drawCentered(deployCount + " INTERNS DEPLOYED", FROM_X, FROM_Y - FROM_R - 38f);
        }

        // Recruit label
        float tRecruitShow = tRecruitDone - RECRUIT_TIME * 0.25f;
        if (animTime >= tRecruitShow) {
            float fa = Math.min(1f, (animTime - tRecruitShow) * 2.0f);
            font.getData().setScale(0.75f);
            font.setColor(tc[0], tc[1], tc[2], fa);
            drawCentered(RECRUIT_COUNT + " NEW RECRUITS", TO_X, TO_Y + TO_R + 32f);
            font.getData().setScale(0.62f);
            font.setColor(tc[0]*.75f, tc[1]*.75f, tc[2]*.75f, fa);
            drawCentered("JOINED YOUR CREW", TO_X, TO_Y + TO_R + 52f);
        }

        // Tap to continue
        if (animTime >= tReady) {
            float pulse = 0.52f + 0.48f * MathUtils.sin(animTime * 3.2f);
            font.getData().setScale(0.88f);
            font.setColor(1f, 0.88f, 0.28f, pulse);
            drawCentered("TAP TO CONTINUE", W * 0.5f, 62f);
        }

        batch.end();
    }

    private void applyFarming() {
        ShipData sd = ShipData.get();
        sd.pendingNewRecruits += RECRUIT_COUNT;
    }

    private float[] col(int idx) {
        return (idx >= 0 && idx < PLANET_COL.length) ? PLANET_COL[idx] : PLANET_COL[0];
    }

    private void drawCentered(String text, float cx, float y) {
        gl.setText(font, text);
        font.draw(batch, text, cx - gl.width * 0.5f, y);
    }

    @Override public void resize(int w, int h) { viewport.update(w, h, true); }

    @Override
    public void dispose() {
        batch.dispose();
        sr.dispose();
        if (texGlow != null) texGlow.dispose();
    }
}
