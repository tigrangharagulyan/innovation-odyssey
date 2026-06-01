package com.odyssey.planet;

public final class SolaraPlanet implements PlanetDefinition {
    @Override public String id() { return "solara"; }
    @Override public float gravityY()      { return -4.5f; }
    @Override public float drumRadius()    { return 3.0f; }
    @Override public int   drumSegments()  { return 36; }
    @Override public float internDensity() { return 1.0f; }
    @Override public float internDamping() { return 0f; }

    @Override public float[] cpEnergies() {
        return new float[]{2_000f, 10_000f, 50_000f, 100_000f};
    }
    @Override public float[] milestoneRpms() {
        return new float[]{5.25f, 3.5f, 6.75f, 7.5f, 9.5f, 99f};
    }
    @Override public String[] milestoneNames() {
        return new String[]{"Elastic Walls","Speed Keep","Wall ×3","Hit ×2","Bumper ×3","-"};
    }
    @Override public String[] milestoneDescs() {
        return new String[]{
            "Interns bounce off walls harder — more chaos",
            "Interns keep 97% speed after every hit",
            "Each wall touch earns 3× more Space Points",
            "Intern-intern hits earn 2× more Space Points",
            "Bumpers deal 3× more Space Points per hit",
            "-"
        };
    }

    @Override public float[] bgGradient()    { return new float[]{0.015f,0.020f, 0.018f,0.025f, 0.055f,0.070f}; }
    @Override public float[] chamberGlow()   { return new float[]{0.12f, 0.62f, 1.00f}; }
    @Override public float[] starTint()      { return new float[]{1.00f, 1.00f, 1.00f}; }
    @Override public float[] ringInnerColor(){ return new float[]{0.07f,0.23f, 0.48f,0.42f, 0.82f,0.18f}; }
    @Override public float[] ringMidColor()  { return new float[]{0.60f, 0.92f, 1.00f}; }

    @Override public float[] bumperCosts()      { return new float[]{500, 1000, 5000, 10000, 100_000}; }
    @Override public float[] gravityWellCosts() { return new float[]{3000, 6000, 20000, 200_000}; }
    @Override public float[] internCosts()      { return new float[]{80, 500, 1_200, 3_000, 7_500, 30_000, 40_000, 50_000, 150_000, 200_000}; }
    @Override public int maxBumpers()      { return 5; }
    @Override public int maxGravityWells() { return 4; }
}
