package com.odyssey.planet;

public final class NovaTerra implements PlanetDefinition {
    @Override public String id() { return "nova_terra"; }
    @Override public float gravityY()      { return -9.81f * 1.6f; }
    @Override public float drumRadius()    { return 3.0f; }
    @Override public int   drumSegments()  { return 36; }
    @Override public float internDensity() { return 1.0f; }
    @Override public float internDamping() { return 0f; }

    @Override public float[] cpEnergies() {
        return new float[]{5_000f, 60_000f, 300_000f, 250_000f};
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
    @Override public float[] chamberGlow()   { return new float[]{0.65f, 0.10f, 0.95f}; }
    @Override public float[] starTint()      { return new float[]{1.05f, 0.82f, 0.75f}; }
    @Override public float[] ringInnerColor(){ return new float[]{0.35f,0.20f, 0.05f,0.10f, 0.70f,0.90f}; }
    @Override public float[] ringMidColor()  { return new float[]{0.85f, 0.50f, 1.00f}; }

    @Override public float[] bumperCosts()      { return null; }
    @Override public float[] gravityWellCosts() { return new float[]{5_000f, 12_000f, 30_000f}; }
    @Override public float[] internCosts()      { return new float[]{80, 500, 1_200, 3_000, 7_500, 30_000, 40_000, 50_000, 150_000, 200_000}; }
    @Override public int maxBumpers()      { return 0; }
    @Override public int maxGravityWells() { return 3; }
}
