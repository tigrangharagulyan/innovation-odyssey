package com.odyssey;

public final class ShipData {

    /** Mutable state stored in each standard bumper body's userData for per-bumper hit animation. */
    public static final class BumperHitData {
        public long lastHitMs = 0L;
    }

    /** Mutable state stored in each gravity-well (attractor) body's userData for per-hit animation. */
    public static final class AttractorHitData {
        public long lastHitMs = 0L;
    }

    public static final class PlanetProfile {
        public final String name;
        public final float  distance;
        public final float  gravity;
        public final String atmosphere;
        public final String rewardLabel;
        public final String unlockedBuildingA;
        public final String unlockedBuildingB;
        public final float  maxFarmingStorage;   // SP cap for offline background production

        public PlanetProfile(String name, float distance, float gravity, String atmosphere,
                             String rewardLabel, String unlockedBuildingA, String unlockedBuildingB,
                             float maxFarmingStorage) {
            this.name               = name;
            this.distance           = distance;
            this.gravity            = gravity;
            this.atmosphere         = atmosphere;
            this.rewardLabel        = rewardLabel;
            this.unlockedBuildingA  = unlockedBuildingA;
            this.unlockedBuildingB  = unlockedBuildingB;
            this.maxFarmingStorage  = maxFarmingStorage;
        }
    }

    public static final PlanetProfile[] PLANETS = {
        new PlanetProfile("Solara", 1000f, 1.0f, "Stable", "Baseline Diagnostics",
            "Habitat Hub", "Solar Relay",     5_000f),
        new PlanetProfile("Nova Terra", 2500f, 1.6f, "Volcanic", "Industrial Mastery",
            "Forge Hub", "Plasma Relay",      25_000f),
        new PlanetProfile("Frostheim", 5000f, 0.4f, "Frozen", "Cryo Tech",
            "Ice Lab", "Frost Relay",         100_000f),
        new PlanetProfile("Cryon Reach", 7600f, 0.7f, "Frozen", "Cryo Suspension",
            "Quantum Farm", "Fusion Dock",    250_000f),
        new PlanetProfile("Helios Forge", 12000f, 2.2f, "Volatile", "Overdrive Core",
            "Plasma Refinery", "Orbital Shipyard", 1_000_000f)
    };

    private static ShipData instance;

    public float totalJoules           = 0f;
    public float currentJPS            = 0f;
    public float savedFlightJPS        = 0f;
    public float powerGenerated        = 0f;
    public float energyAtLastLaunch    = 0f;
    public float bumperEnergyMult      = 5.0f;
    public float maxInternSpeed        = 5.0f;
    public float wallEnergyMult        = 1.0f;
    public float collisionEnergyMult   = 1.0f;
    public float internBoostStrength   = 1.5f;
    public float targetPlanetDistance  = 1000f;
    public float planetGravityMultiplier = 1.0f;
    public float accumulatedDist       = 0f;
    public int   sectorReached         = -1;
    public int   currentPlanetIndex    = 0;
    public int   selectedPlanetIndex   = 1;
    public int   arrivalsCompleted     = 0;
    public boolean arrivalReady        = false;
    public String lastArrivalPlanetName = "";
    public float lastArrivalEnergyUsed  = 0f;
    public float lastArrivalJourneyDays = 0f;
    public float crystals               = 0f;
    public float bumperSparkValue       = 20f;
    public float bumperMult            = 1.0f;
    public float gravityMult           = 1.0f;

    // Offline farming
    public int[]  internsLeftOnPlanet  = new int[PLANETS.length];
    public long   lastFarmingTimestamp = 0L;

    public final com.badlogic.gdx.utils.Array<float[]> pendingContactEvents = new com.badlogic.gdx.utils.Array<>();
    public int pendingBumperSounds = 0;   // drained each frame by SoundManager call

    public static final float SOLARA_GRAVITY  = 1.0f;
    public static final float SOLARA_DISTANCE = 1000f;

    private ShipData() {}

    public static ShipData get() {
        if (instance == null) instance = new ShipData();
        return instance;
    }

    public void reset() {
        totalJoules             = 0f;
        currentJPS              = 0f;
        bumperEnergyMult        = 5.0f;
        maxInternSpeed          = 5.0f;
        wallEnergyMult          = 1.0f;
        collisionEnergyMult     = 1.0f;
        internBoostStrength     = 1.5f;
        targetPlanetDistance    = SOLARA_DISTANCE;
        planetGravityMultiplier = SOLARA_GRAVITY;
        accumulatedDist         = 0f;
        sectorReached           = -1;
        powerGenerated          = 0f;
        energyAtLastLaunch      = 0f;
        currentPlanetIndex      = 0;
        selectedPlanetIndex     = 1;
        arrivalsCompleted       = 0;
        arrivalReady            = false;
        lastArrivalPlanetName   = "";
        lastArrivalEnergyUsed   = 0f;
        lastArrivalJourneyDays  = 0f;
        crystals                = 0f;
        bumperSparkValue        = 20f;
        bumperMult              = 1.0f;
        gravityMult             = 1.0f;
        lastFarmingTimestamp    = 0L;
        for (int i = 0; i < internsLeftOnPlanet.length; i++) internsLeftOnPlanet[i] = 0;
        pendingContactEvents.clear();
        pendingBumperSounds = 0;
    }

    public void addJoules(float joules) {
        totalJoules    += joules;
        powerGenerated += joules;
    }

    public void addCrystals(float c)       { crystals += c; }
    public boolean spendCrystals(float cost) {
        if (crystals >= cost) { crystals -= cost; return true; }
        return false;
    }

    public boolean spend(float cost) {
        if (totalJoules >= cost) { totalJoules -= cost; return true; }
        return false;
    }

    public PlanetProfile getCurrentPlanet()  { return PLANETS[currentPlanetIndex]; }
    public PlanetProfile getSelectedPlanet() { return PLANETS[selectedPlanetIndex]; }

    public void selectPlanet(int index) {
        selectedPlanetIndex = Math.max(0, Math.min(index, PLANETS.length - 1));
    }

    public void commitSelectedPlanet() {
        PlanetProfile p = getSelectedPlanet();
        targetPlanetDistance    = p.distance;
        planetGravityMultiplier = p.gravity;
    }

    public void markArrival(float routeDistance, float energySpent) {
        PlanetProfile p = getSelectedPlanet();
        currentPlanetIndex      = selectedPlanetIndex;
        targetPlanetDistance    = p.distance;
        planetGravityMultiplier = p.gravity;
        arrivalReady            = true;
        arrivalsCompleted++;
        lastArrivalPlanetName   = p.name;
        lastArrivalEnergyUsed   = energySpent;
        lastArrivalJourneyDays  = Math.max(3f, routeDistance / 300f);
        accumulatedDist         = 0f;
        sectorReached           = -1;
        energyAtLastLaunch      = powerGenerated;
    }

    public void claimArrivalReward() {
        arrivalReady          = false;
        bumperEnergyMult     += 0.5f;
        collisionEnergyMult  += 0.25f;
        wallEnergyMult       += 0.15f;
        internBoostStrength  += 0.15f;
        totalJoules          += 150f + arrivalsCompleted * 50f;
    }
}
