package com.odyssey.planet;

public interface PlanetDefinition {
    String id();

    // Physics
    float gravityY();
    float drumRadius();
    int   drumSegments();
    float internDensity();
    float internDamping();

    // Checkpoints
    float[] cpEnergies();

    // Ring-speed milestones
    float[]  milestoneRpms();
    String[] milestoneNames();
    String[] milestoneDescs();

    // Background rendering
    float[] bgGradient();       // [r0,rt, g0,gt, b0,bt]
    float[] chamberGlow();      // [r,g,b]
    float[] starTint();         // [r,g,b]
    float[] ringInnerColor();   // [r_base,r_peak, g_base,g_peak, b_base,b_peak]
    float[] ringMidColor();     // [r,g,b] segment marks

    // Upgrade costs — null = feature unavailable
    float[] bumperCosts();
    float[] gravityWellCosts();
    float[] internCosts();
    int     maxBumpers();
    int     maxGravityWells();

    // Hooks — default no-op
    default void onCpUnlocked(int cp, PlanetHooks hooks) {}
    default void tick(float delta, PlanetHooks hooks) {}
}
