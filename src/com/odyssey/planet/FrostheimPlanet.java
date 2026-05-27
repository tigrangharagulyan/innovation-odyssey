package com.odyssey.planet;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

public final class FrostheimPlanet implements PlanetDefinition {
    private static final float TESLA_FIELD_R = 1.5f;

    @Override public String id() { return "frostheim"; }
    @Override public float gravityY()      { return -2.5f; }
    @Override public float drumRadius()    { return 3.0f; }
    @Override public int   drumSegments()  { return 36; }
    @Override public float internDensity() { return 1.0f; }
    @Override public float internDamping() { return 0.01f; }

    @Override public float[] cpEnergies() {
        return new float[]{4_000f, 24_000f, 120_000f, 150_000f};
    }
    @Override public float[] milestoneRpms()  { return new float[]{3.5f, 6.75f, 7.5f}; }
    @Override public String[] milestoneNames() {
        return new String[]{"Superconductor", "Absolute Zero", "Blizzard Overdrive"};
    }
    @Override public String[] milestoneDescs() {
        return new String[]{
            "Friction-Zero\nInterns arc freely in 0.4G",
            "Wall restitution 0.94\nPerfect elastic bounce",
            "Choose your evolution path"
        };
    }

    @Override public float[] bgGradient()    { return new float[]{0.010f,0.008f, 0.018f,0.028f, 0.048f,0.065f}; }
    @Override public float[] chamberGlow()   { return new float[]{0.48f, 0.72f, 1.00f}; }
    @Override public float[] starTint()      { return new float[]{0.88f, 0.94f, 1.05f}; }
    @Override public float[] ringInnerColor(){ return new float[]{0.50f,0.40f, 0.72f,0.22f, 0.90f,0.10f}; }
    @Override public float[] ringMidColor()  { return new float[]{0.85f, 0.95f, 1.00f}; }

    @Override public float[] bumperCosts()      { return null; }
    @Override public float[] gravityWellCosts() { return null; }
    @Override public float[] internCosts()      { return new float[]{80, 500, 1_200, 3_000, 7_500, 30_000, 40_000, 50_000, 150_000, 200_000}; }
    @Override public int maxBumpers()      { return 0; }
    @Override public int maxGravityWells() { return 0; }

    @Override
    public void onCpUnlocked(int cp, PlanetHooks hooks) {
        PlanetState s     = hooks.state();
        Array<Body> balls = hooks.balls();
        switch (cp) {
            case 0:
                s.set("ball_damping", 0.005f);
                for (int i = 0; i < balls.size; i++) {
                    balls.get(i).setLinearDamping(0.005f);
                    balls.get(i).setAngularDamping(0.005f);
                }
                break;
            case 1:
                // wall restitution 0.94 — handled by screen (centrifugeBody lives there)
                break;
            case 2:
                s.set("tesla_rate", 30f);
                hooks.shipData().maxInternSpeed = 8.5f;
                s.set("show_decision", true);
                break;
        }
    }

    @Override
    public void tick(float delta, PlanetHooks hooks) {
        Array<Body> teslaCoils = hooks.specialBodiesB();
        if (teslaCoils.size == 0) return;
        Array<Body> balls = hooks.balls();
        float rate = hooks.state().getFloat("tesla_rate", 15f);
        float r2   = TESLA_FIELD_R * TESLA_FIELD_R;
        for (int i = 0; i < teslaCoils.size; i++) {
            float tx = teslaCoils.get(i).getPosition().x;
            float ty = teslaCoils.get(i).getPosition().y;
            for (int j = 0; j < balls.size; j++) {
                float dx = tx - balls.get(j).getPosition().x;
                float dy = ty - balls.get(j).getPosition().y;
                if (dx * dx + dy * dy < r2) {
                    hooks.shipData().addJoules(rate * delta);
                }
            }
        }
    }
}
