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
    public float crystals               = 5f;
    public float bumperSparkValue       = 20f;
    public float bumperMult            = 1.0f;
    public float gravityMult           = 1.0f;

    // EmberIV perk persistence — survives screen rebuild across checkpoints
    public boolean[] emberPerksEarned  = new boolean[5];

    // Pause earnings during structure placement
    public boolean   placingStructure  = false;

    // EmberIV portal/relay positions — survive screen rebuilds across checkpoints
    // portals: flat [ax, ay, bx, by] per pair; relays: flat [x, y] per node
    public float[] savedPortalPairs = new float[0];
    public float[] savedRelayNodes  = new float[0];

    // Placed-structure positions — flat [x, y, x, y, ...] per type
    public float[] savedBumpers    = new float[0];
    public float[] savedAttractors = new float[0];
    public float[] savedCryoVents  = new float[0];
    public float[] savedTeslaCoils = new float[0];
    public float[] savedSpringPads = new float[0];

    // Checkpoint / milestone flags for restore after RAM-clear
    public boolean[] savedMilestoneAchieved      = new boolean[6];
    public boolean   savedFrostheimCpI            = false;
    public boolean   savedFrostheimCpII           = false;
    public boolean   savedFrostheimCpIII          = false;
    public boolean   savedFrostheimCryoUnlocked   = false;
    public boolean   savedEmberHeavyChassis       = false;
    public boolean   savedEmberMagneticRim        = false;
    public int       savedHubUpgradeTier          = 0;
    public int       savedBallCount               = 0;
    public int       savedKineticBladeCount            = 0;
    public int       savedFrostheimDecision            = 0;
    public float     savedTeslaHarvestRate             = 15f;
    public boolean   savedPortalBidirectional          = false;
    public boolean   savedEmberSpinReversed            = false;
    public int       savedGravShiftStep                = 0;
    public boolean   savedEmberThirdInternUnlocked     = false;
    public boolean   savedFrostheimThirdInternUnlocked = false;

    // Offline farming
    public int[]  internsLeftOnPlanet  = new int[PLANETS.length];
    public long   lastFarmingTimestamp = 0L;
    public int    pendingNewRecruits   = 0;

    public static final float FARM_RATE_PER_INTERN = 2f; // SP/s per deployed intern

    public final com.badlogic.gdx.utils.Array<float[]> pendingContactEvents = new com.badlogic.gdx.utils.Array<>();
    public int pendingBumperSounds    = 0;
    public int pendingCollisionSounds = 0;

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
        crystals                = 5f;
        bumperSparkValue        = 20f;
        bumperMult              = 1.0f;
        gravityMult             = 1.0f;
        lastFarmingTimestamp    = 0L;
        pendingNewRecruits      = 0;
        for (int i = 0; i < internsLeftOnPlanet.length; i++) internsLeftOnPlanet[i] = 0;
        for (int i = 0; i < emberPerksEarned.length; i++) emberPerksEarned[i] = false;
        savedPortalPairs = new float[0];
        savedRelayNodes  = new float[0];
        savedBumpers     = new float[0];
        savedAttractors  = new float[0];
        savedCryoVents   = new float[0];
        savedTeslaCoils  = new float[0];
        savedSpringPads  = new float[0];
        for (int i = 0; i < savedMilestoneAchieved.length; i++) savedMilestoneAchieved[i] = false;
        savedFrostheimCpI          = false;
        savedFrostheimCpII         = false;
        savedFrostheimCpIII        = false;
        savedFrostheimCryoUnlocked = false;
        savedEmberHeavyChassis     = false;
        savedEmberMagneticRim      = false;
        savedHubUpgradeTier        = 0;
        savedBallCount             = 0;
        savedKineticBladeCount           = 0;
        savedFrostheimDecision           = 0;
        savedTeslaHarvestRate            = 15f;
        savedPortalBidirectional         = false;
        savedEmberSpinReversed           = false;
        savedGravShiftStep               = 0;
        savedEmberThirdInternUnlocked    = false;
        savedFrostheimThirdInternUnlocked = false;
        pendingContactEvents.clear();
        pendingBumperSounds    = 0;
        pendingCollisionSounds = 0;
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

    /** Returns SP earned offline since last claim, then resets the timestamp. */
    public float claimOfflineFarming() {
        if (lastFarmingTimestamp == 0L) return 0f;
        long now = System.currentTimeMillis();
        float elapsed = (now - lastFarmingTimestamp) / 1000f;
        lastFarmingTimestamp = now;
        float total = 0f;
        for (int i = 0; i < PLANETS.length; i++) {
            if (internsLeftOnPlanet[i] <= 0) continue;
            float rate = internsLeftOnPlanet[i] * FARM_RATE_PER_INTERN;
            float cap  = PLANETS[i].maxFarmingStorage;
            total = Math.min(total + rate * elapsed, total + cap);
        }
        return total;
    }

    // ---- Persistence ----------------------------------------------------------------

    public void save() {
        com.badlogic.gdx.Preferences p = com.badlogic.gdx.Gdx.app.getPreferences("odyssey_save");
        p.putFloat("totalJoules",           totalJoules);
        p.putFloat("crystals",              crystals);
        p.putFloat("bumperEnergyMult",      bumperEnergyMult);
        p.putFloat("wallEnergyMult",        wallEnergyMult);
        p.putFloat("collisionEnergyMult",   collisionEnergyMult);
        p.putFloat("bumperMult",            bumperMult);
        p.putFloat("gravityMult",           gravityMult);
        p.putFloat("bumperSparkValue",      bumperSparkValue);
        p.putFloat("internBoostStrength",   internBoostStrength);
        p.putFloat("maxInternSpeed",        maxInternSpeed);
        p.putFloat("targetPlanetDistance",  targetPlanetDistance);
        p.putFloat("planetGravMult",        planetGravityMultiplier);
        p.putFloat("accumulatedDist",       accumulatedDist);
        p.putFloat("powerGenerated",        powerGenerated);
        p.putFloat("energyAtLastLaunch",    energyAtLastLaunch);
        p.putFloat("lastArrivalEnergyUsed", lastArrivalEnergyUsed);
        p.putFloat("lastArrivalJourneyDays",lastArrivalJourneyDays);
        p.putFloat("savedFlightJPS",        savedFlightJPS);
        p.putInteger("currentPlanetIndex",  currentPlanetIndex);
        p.putInteger("selectedPlanetIndex", selectedPlanetIndex);
        p.putInteger("sectorReached",       sectorReached);
        p.putInteger("arrivalsCompleted",   arrivalsCompleted);
        p.putBoolean("arrivalReady",        arrivalReady);
        p.putString("lastArrivalPlanetName",lastArrivalPlanetName);
        p.putLong("lastFarmingTimestamp",   lastFarmingTimestamp);
        p.putInteger("pendingNewRecruits",  pendingNewRecruits);
        for (int i = 0; i < PLANETS.length; i++)
            p.putInteger("internsLeft_" + i, internsLeftOnPlanet[i]);
        for (int i = 0; i < emberPerksEarned.length; i++)
            p.putBoolean("emberPerk_" + i, emberPerksEarned[i]);
        p.putString("savedPortalPairs", floatsToString(savedPortalPairs));
        p.putString("savedRelayNodes",  floatsToString(savedRelayNodes));
        p.putString("savedBumpers",     floatsToString(savedBumpers));
        p.putString("savedAttractors",  floatsToString(savedAttractors));
        p.putString("savedCryoVents",   floatsToString(savedCryoVents));
        p.putString("savedTeslaCoils",  floatsToString(savedTeslaCoils));
        p.putString("savedSpringPads",  floatsToString(savedSpringPads));
        for (int i = 0; i < savedMilestoneAchieved.length; i++)
            p.putBoolean("milestone_" + i, savedMilestoneAchieved[i]);
        p.putBoolean("fhCpI",   savedFrostheimCpI);
        p.putBoolean("fhCpII",  savedFrostheimCpII);
        p.putBoolean("fhCpIII", savedFrostheimCpIII);
        p.putBoolean("fhCryo",  savedFrostheimCryoUnlocked);
        p.putBoolean("emHeavy", savedEmberHeavyChassis);
        p.putBoolean("emMag",   savedEmberMagneticRim);
        p.putInteger("hubTier", savedHubUpgradeTier);
        p.putInteger("ballCount", savedBallCount);
        p.putInteger("kineticBladeCount",  savedKineticBladeCount);
        p.putInteger("fhDecision",         savedFrostheimDecision);
        p.putFloat("teslaHarvestRate",     savedTeslaHarvestRate);
        p.putBoolean("portalBidir",        savedPortalBidirectional);
        p.putBoolean("emSpinRev",          savedEmberSpinReversed);
        p.putInteger("gravShiftStep",      savedGravShiftStep);
        p.putBoolean("emThirdIntern",      savedEmberThirdInternUnlocked);
        p.putBoolean("fhThirdIntern",      savedFrostheimThirdInternUnlocked);
        p.putBoolean("hasSave", true);
        p.flush();
    }

    /** Returns true if a save file was found and loaded. */
    public boolean load() {
        com.badlogic.gdx.Preferences p = com.badlogic.gdx.Gdx.app.getPreferences("odyssey_save");
        if (!p.getBoolean("hasSave", false)) return false;
        totalJoules             = p.getFloat("totalJoules",           0f);
        crystals                = p.getFloat("crystals",              0f);
        bumperEnergyMult        = p.getFloat("bumperEnergyMult",      5.0f);
        wallEnergyMult          = p.getFloat("wallEnergyMult",        1.0f);
        collisionEnergyMult     = p.getFloat("collisionEnergyMult",   1.0f);
        bumperMult              = p.getFloat("bumperMult",            1.0f);
        gravityMult             = p.getFloat("gravityMult",           1.0f);
        bumperSparkValue        = p.getFloat("bumperSparkValue",      20f);
        internBoostStrength     = p.getFloat("internBoostStrength",   1.5f);
        maxInternSpeed          = p.getFloat("maxInternSpeed",        5.0f);
        targetPlanetDistance    = p.getFloat("targetPlanetDistance",  SOLARA_DISTANCE);
        planetGravityMultiplier = p.getFloat("planetGravMult",        SOLARA_GRAVITY);
        accumulatedDist         = p.getFloat("accumulatedDist",       0f);
        powerGenerated          = p.getFloat("powerGenerated",        0f);
        energyAtLastLaunch      = p.getFloat("energyAtLastLaunch",    0f);
        lastArrivalEnergyUsed   = p.getFloat("lastArrivalEnergyUsed", 0f);
        lastArrivalJourneyDays  = p.getFloat("lastArrivalJourneyDays",0f);
        savedFlightJPS          = p.getFloat("savedFlightJPS",        0f);
        currentPlanetIndex      = p.getInteger("currentPlanetIndex",  0);
        selectedPlanetIndex     = p.getInteger("selectedPlanetIndex", 1);
        sectorReached           = p.getInteger("sectorReached",       -1);
        arrivalsCompleted       = p.getInteger("arrivalsCompleted",   0);
        arrivalReady            = p.getBoolean("arrivalReady",        false);
        lastArrivalPlanetName   = p.getString("lastArrivalPlanetName","");
        lastFarmingTimestamp    = p.getLong("lastFarmingTimestamp",   0L);
        pendingNewRecruits      = p.getInteger("pendingNewRecruits",  0);
        for (int i = 0; i < PLANETS.length; i++)
            internsLeftOnPlanet[i] = p.getInteger("internsLeft_" + i, 0);
        for (int i = 0; i < emberPerksEarned.length; i++)
            emberPerksEarned[i] = p.getBoolean("emberPerk_" + i, false);
        savedPortalPairs = stringToFloats(p.getString("savedPortalPairs", ""));
        savedRelayNodes  = stringToFloats(p.getString("savedRelayNodes",  ""));
        savedBumpers     = stringToFloats(p.getString("savedBumpers",     ""));
        savedAttractors  = stringToFloats(p.getString("savedAttractors",  ""));
        savedCryoVents   = stringToFloats(p.getString("savedCryoVents",   ""));
        savedTeslaCoils  = stringToFloats(p.getString("savedTeslaCoils",  ""));
        savedSpringPads  = stringToFloats(p.getString("savedSpringPads",  ""));
        for (int i = 0; i < savedMilestoneAchieved.length; i++)
            savedMilestoneAchieved[i] = p.getBoolean("milestone_" + i, false);
        savedFrostheimCpI          = p.getBoolean("fhCpI",   false);
        savedFrostheimCpII         = p.getBoolean("fhCpII",  false);
        savedFrostheimCpIII        = p.getBoolean("fhCpIII", false);
        savedFrostheimCryoUnlocked = p.getBoolean("fhCryo",  false);
        savedEmberHeavyChassis     = p.getBoolean("emHeavy", false);
        savedEmberMagneticRim      = p.getBoolean("emMag",   false);
        savedHubUpgradeTier        = p.getInteger("hubTier", 0);
        savedBallCount             = p.getInteger("ballCount", 0);
        savedKineticBladeCount           = p.getInteger("kineticBladeCount",  0);
        savedFrostheimDecision           = p.getInteger("fhDecision",         0);
        savedTeslaHarvestRate            = p.getFloat("teslaHarvestRate",     15f);
        savedPortalBidirectional         = p.getBoolean("portalBidir",        false);
        savedEmberSpinReversed           = p.getBoolean("emSpinRev",          false);
        savedGravShiftStep               = p.getInteger("gravShiftStep",      0);
        savedEmberThirdInternUnlocked    = p.getBoolean("emThirdIntern",      false);
        savedFrostheimThirdInternUnlocked = p.getBoolean("fhThirdIntern",    false);
        return true;
    }

    private static String floatsToString(float[] arr) {
        if (arr == null || arr.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static float[] stringToFloats(String s) {
        if (s == null || s.isEmpty()) return new float[0];
        String[] parts = s.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { arr[i] = Float.parseFloat(parts[i].trim()); }
            catch (NumberFormatException e) { arr[i] = 0f; }
        }
        return arr;
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
