package com.odyssey;

public final class ShipData {

    private static ShipData instance;

    public float totalJoules           = 0f;
    public float currentJPS            = 0f;
    public float targetPlanetDistance  = 1000f;
    public float planetGravityMultiplier = 1.0f;

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
        targetPlanetDistance   = SOLARA_DISTANCE;
        planetGravityMultiplier = SOLARA_GRAVITY;
    }

    /** Called each Lab tick to accumulate energy. */
    public void addJoules(float joules) {
        totalJoules += joules;
    }
}
