package com.odyssey.planet;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;

public final class NovaTerra implements PlanetDefinition {
    // Reusable temp vector — NovaTerra is a singleton in PLANET_DEFS, safe to reuse
    private final Vector2 tmp = new Vector2();

    @Override public String id() { return "nova_terra"; }
    @Override public float gravityY()      { return -9.81f * 1.6f; }
    @Override public float drumRadius()    { return 3.0f; }
    @Override public int   drumSegments()  { return 36; }
    @Override public float internDensity() { return 1.0f; }
    @Override public float internDamping() { return 0f; }

    @Override public float[] cpEnergies() {
        return new float[]{8_000f, 30_000f, 100_000f, 180_000f};
    }
    @Override public float[] milestoneRpms()  { return new float[]{5.5f, 6.5f, 7.5f}; }
    @Override public String[] milestoneNames() {
        return new String[]{"Heavy Chassis", "Magnetic Rim", "Hub Resonance"};
    }
    @Override public String[] milestoneDescs() {
        return new String[]{
            "Intern density 3.5\nTanks take hits harder",
            "Wall restitution 0.88\nInterns roll the ring",
            "Hub cycle halved\nBlast fires every 20s"
        };
    }

    @Override public float[] bgGradient()    { return new float[]{0.040f,0.030f, 0.010f,0.008f, 0.005f,0.003f}; }
    @Override public float[] chamberGlow()   { return new float[]{0.90f, 0.22f, 0.04f}; }
    @Override public float[] starTint()      { return new float[]{1.05f, 0.82f, 0.75f}; }
    @Override public float[] ringInnerColor(){ return new float[]{0.70f,0.28f, 0.18f,0.22f, 0.04f,0.04f}; }
    @Override public float[] ringMidColor()  { return new float[]{1.00f, 0.65f, 0.25f}; }

    @Override public float[] bumperCosts()      { return null; }
    @Override public float[] gravityWellCosts() { return new float[]{5_000f, 12_000f, 30_000f}; }
    @Override public float[] internCosts()      { return new float[]{80, 500, 1_200, 3_000, 7_500, 30_000, 40_000, 50_000, 150_000, 200_000}; }
    @Override public int maxBumpers()      { return 0; }
    @Override public int maxGravityWells() { return 3; }

    @Override
    public void onCpUnlocked(int cp, PlanetHooks hooks) {
        PlanetState s     = hooks.state();
        Array<Body> balls = hooks.balls();
        switch (cp) {
            case 0:
                s.set("heavy_chassis", true);
                for (int i = 0; i < balls.size; i++) {
                    Body b = balls.get(i);
                    Array<Fixture> fx = b.getFixtureList();
                    for (int f = 0; f < fx.size; f++) fx.get(f).setDensity(3.5f);
                    b.resetMassData();
                }
                break;
            case 1:
                s.set("magnetic_rim", true);
                // wall restitution change handled by screen — centrifugeBody lives there
                break;
            case 2:
                s.set("hub_cycle_len", 10f);
                break;
        }
    }

    @Override
    public void tick(float delta, PlanetHooks hooks) {
        PlanetState s      = hooks.state();
        Array<Body> balls  = hooks.balls();
        Array<Body> blades = hooks.specialBodiesA();
        float cx           = hooks.drumCenterX();
        float cy           = hooks.drumCenterY();
        float drumR        = hooks.drumRadius();

        float hubTimer    = s.getFloat("hub_timer",      0f);
        float cycleLen    = s.getFloat("hub_cycle_len",  20f);
        boolean blastDone = s.getBool("hub_blast_fired");
        int hubTier       = s.getInt("hub_tier", 0);

        hubTimer += delta;

        if (hubTimer < cycleLen) {
            // STATE A — suction + spiral
            float suctionMult = (hubTier >= 1) ? 1.35f : 1.0f;
            for (int j = 0; j < balls.size; j++) {
                Body ball = balls.get(j);
                tmp.set(cx, cy).sub(ball.getPosition());
                float dist = tmp.len();
                if (dist > 0.05f) {
                    float falloff  = 1f - dist / drumR;
                    float nx = tmp.x / dist;
                    float ny = tmp.y / dist;
                    float tx = -ny;
                    float ty =  nx;
                    float radialStr = 48f * suctionMult * falloff * ball.getMass();
                    float tangStr   = 16f * suctionMult * falloff * ball.getMass();
                    ball.applyForceToCenter(
                        nx * radialStr + tx * tangStr,
                        ny * radialStr + ty * tangStr, true);
                }
            }
        } else if (hubTimer < cycleLen * 2f) {
            // STATE B — single outward blast
            if (!blastDone) {
                s.set("hub_blast_fired", true);
                float blastMult = (hubTier >= 2) ? 1.50f : 1.0f;
                for (int j = 0; j < balls.size; j++) {
                    Body ball = balls.get(j);
                    tmp.set(ball.getPosition().x - cx, ball.getPosition().y - cy);
                    if (tmp.len2() > 0.0001f) {
                        tmp.nor();
                        float impMag = 30f * blastMult * ball.getMass();
                        ball.applyLinearImpulse(
                            tmp.x * impMag, tmp.y * impMag,
                            ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                    }
                }
            }
        } else {
            hubTimer = 0f;
            s.set("hub_blast_fired", false);
        }

        // Stack dissolution — kick stalled interns toward nearest blade (or hub center)
        for (int j = 0; j < balls.size; j++) {
            Body ball = balls.get(j);
            if (ball.getLinearVelocity().len() < 0.35f) {
                if (blades.size > 0) {
                    Body nearest = blades.get(0);
                    float nd = nearest.getPosition().dst(ball.getPosition());
                    for (int k = 1; k < blades.size; k++) {
                        float d = blades.get(k).getPosition().dst(ball.getPosition());
                        if (d < nd) { nd = d; nearest = blades.get(k); }
                    }
                    tmp.set(nearest.getPosition()).sub(ball.getPosition());
                } else {
                    tmp.set(cx - ball.getPosition().x, cy - ball.getPosition().y);
                }
                if (tmp.len2() > 0.0001f) {
                    tmp.nor();
                    ball.applyLinearImpulse(
                        tmp.x * 0.9f * ball.getMass(), tmp.y * 0.9f * ball.getMass(),
                        ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                }
                // Bottom-pinned horizontal shove breaks gravity stacks
                if (ball.getPosition().y < cy - drumR * 0.55f) {
                    ball.applyLinearImpulse(
                        (ball.getPosition().x < cx ? 0.5f : -0.5f) * ball.getMass(), 0f,
                        ball.getWorldCenter().x, ball.getWorldCenter().y, true);
                }
            }
        }

        s.set("hub_timer", hubTimer);
    }
}
