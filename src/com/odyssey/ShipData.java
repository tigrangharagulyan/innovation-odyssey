package com.odyssey;

public final class ShipData {

    private static ShipData instance;

    public float totalJoules           = 0f;
    public float currentJPS            = 0f;
    public float savedFlightJPS        = 0f;
    public float powerGenerated        = 0f;   // cumulative joules ever produced — never spent, always grows
    public float energyAtLastLaunch    = 0f;
    public float bumperEnergyMult      = 5.0f;
    public float maxInternSpeed        = 5.0f;
    public float wallEnergyMult        = 1.0f;
    public float collisionEnergyMult   = 1.0f;
    public float internBoostStrength   = 1.5f;
    public float targetPlanetDistance  = 1000f;
    public float planetGravityMultiplier = 1.0f;
    public float accumulatedDist       = 0f;   // total AU covered across all runs
    public int   sectorReached         = -1;   // -1=none, 0-3 = sector index

    // Demo level: Solara System defaults
    public static final float SOLARA_GRAVITY   = 1.0f;
    public static final float SOLARA_DISTANCE  = 1000f;

    private ShipData() {}

    public static ShipData get() {
        if (instance == null) instance = new ShipData();
        return instance;
    }

    public void reset() {
        totalJoules            = 0f;
        currentJPS             = 0f;
        bumperEnergyMult       = 5.0f;
        maxInternSpeed         = 5.0f;
        wallEnergyMult         = 1.0f;
        collisionEnergyMult    = 1.0f;
        internBoostStrength    = 1.5f;
        targetPlanetDistance   = SOLARA_DISTANCE;
        planetGravityMultiplier = SOLARA_GRAVITY;
        accumulatedDist        = 0f;
        sectorReached          = -1;
        powerGenerated         = 0f;
        energyAtLastLaunch     = 0f;
    }

    public void addJoules(float joules) {
        totalJoules     += joules;
        powerGenerated  += joules;
    }

    public boolean spend(float cost) {
        if (totalJoules >= cost) {
            totalJoules -= cost;
            return true;
        }
        return false;
    }

}
