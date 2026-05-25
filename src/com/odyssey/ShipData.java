package com.odyssey;

public final class ShipData {

    /** Mutable state stored in each standard bumper body's userData for per-bumper hit animation. */
    public static final class BumperHitData {

        public boolean isArmBumper   = false;
        public boolean isValleyBlade = false;
        public long    lastHitMs     = 0L;
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
    public boolean gravityEnabled       = true;

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
    public float[] savedIcicleNodes = new float[0];
    public float[] savedTeslaCoils = new float[0];
    public float[] savedSpringPads = new float[0];

    // Checkpoint / milestone flags for restore after RAM-clear
    public boolean[] savedMilestoneAchieved      = new boolean[6];
    public boolean   savedFrostheimCpI            = false;
    public boolean   savedFrostheimCpII           = false;
    public boolean   savedFrostheimCpIII          = false;
    public boolean   savedFrostheimIcicleUnlocked  = false;
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
    public boolean   savedFrostheimArmBumpersActive    = false;

    // Lives & monetisation
    public int     lives           = 5;
    public int     maxLives        = 5;
    public long    nextLifeAtMs    = 0L;   // wall-clock ms when next life auto-refills; 0 = full
    public int     diamonds        = 0;
    public boolean unlimitedLives  = false;

    // Offline farming
    public int    pendingNewRecruits   = 0;

    // Gem farming — fixed gems/hr per planet index (Solara=1 … Helios Forge=5)
    public static final int[] GEM_FARM_RATES = {1, 2, 3, 4, 5};
    public long lastGemFarmTimestamp = 0L;   // ms epoch; 0 = uninitialised

    // Leaderboard — flight timer and per-planet personal bests
    public long    flightStartTimeMs  = 0L;          // epoch ms when current flight began; 0 = not started  // transient – not persisted
    public float[] bestArrivalTimes   = new float[]{  // seconds; Float.MAX_VALUE = no time yet
        Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE
    };

    // Transient — pending post-arrival rank notification (not persisted, cleared after shown)
    public int  pendingRankResult = -1;   // 1-based rank, or -1 if none
    public int  pendingRankPlanet = -1;   // planet index of the pending rank

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
        gravityEnabled          = true;
        lastGemFarmTimestamp = 0L;
        pendingNewRecruits      = 0;
        for (int i = 0; i < emberPerksEarned.length; i++) emberPerksEarned[i] = false;
        savedPortalPairs = new float[0];
        savedRelayNodes  = new float[0];
        savedBumpers     = new float[0];
        savedAttractors  = new float[0];
        savedIcicleNodes = new float[0];
        savedTeslaCoils  = new float[0];
        savedSpringPads  = new float[0];
        for (int i = 0; i < savedMilestoneAchieved.length; i++) savedMilestoneAchieved[i] = false;
        savedFrostheimCpI          = false;
        savedFrostheimCpII         = false;
        savedFrostheimCpIII        = false;
        savedFrostheimIcicleUnlocked = false;
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
        flightStartTimeMs = 0L;
        for (int i = 0; i < bestArrivalTimes.length; i++) bestArrivalTimes[i] = Float.MAX_VALUE;
        pendingRankResult = -1;
        pendingRankPlanet = -1;
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

    // ---- Lives helpers -------------------------------------------------------

    /** True when the player is allowed to launch. */
    public boolean canPlay() { return unlimitedLives || lives > 0; }

    /** Deduct one life and start the refill timer if not already running. */
    public void consumeLife() {
        if (unlimitedLives) return;
        if (lives > 0) {
            lives--;
            if (lives < maxLives && nextLifeAtMs == 0L) {
                nextLifeAtMs = System.currentTimeMillis() + 3_600_000L; // 1 hour
            }
        }
    }

    /** Call every frame — refills lives from the real-time clock. */
    public void tickLives() {
        if (unlimitedLives || lives >= maxLives) { nextLifeAtMs = 0L; return; }
        long now = System.currentTimeMillis();
        while (lives < maxLives && nextLifeAtMs > 0L && now >= nextLifeAtMs) {
            lives++;
            nextLifeAtMs = (lives < maxLives) ? nextLifeAtMs + 3_600_000L : 0L;
        }
    }

    /** Seconds until the next life, or 0 if full / already ready. */
    public long secondsToNextLife() {
        if (lives >= maxLives || nextLifeAtMs == 0L) return 0L;
        return Math.max(0L, (nextLifeAtMs - System.currentTimeMillis()) / 1000L);
    }

    // ---- Gem farming helpers ---------------------------------------------------------

    /** Total gem/hr from all planets unlocked so far. 0 if farming not yet active. */
    public int gemFarmRatePerHour() {
        if (arrivalsCompleted < 1) return 0;
        int total = 0;
        int unlocked = Math.min(arrivalsCompleted + 1, GEM_FARM_RATES.length);
        for (int i = 0; i < unlocked; i++) total += GEM_FARM_RATES[i];
        return total;
    }

    /** Storage cap: 24 hours of the current total rate. */
    public int gemFarmCap() {
        return gemFarmRatePerHour() * 24;
    }

    /**
     * Claim gems accumulated since last call. Call from EngineeringLabScreen.show().
     * Initialises the timestamp on first call once farming is active.
     * Returns the number of whole gems awarded (0 if farming not yet active).
     */
    public int claimGemFarming() {
        if (arrivalsCompleted < 1) return 0;
        long now = System.currentTimeMillis();
        if (lastGemFarmTimestamp == 0L) {
            lastGemFarmTimestamp = now;
            return 0;
        }
        float elapsedHrs = (now - lastGemFarmTimestamp) / 3_600_000f;
        int cap    = gemFarmCap();
        int earned = (int) Math.min(gemFarmRatePerHour() * elapsedHrs, cap);
        if (earned > 0) {
            diamonds += earned;
            lastGemFarmTimestamp = now;
        }
        return earned;
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
        p.putLong("lastGemFarmTimestamp", lastGemFarmTimestamp);
        p.putInteger("pendingNewRecruits",  pendingNewRecruits);
        for (int i = 0; i < emberPerksEarned.length; i++)
            p.putBoolean("emberPerk_" + i, emberPerksEarned[i]);
        p.putString("savedPortalPairs", floatsToString(savedPortalPairs));
        p.putString("savedRelayNodes",  floatsToString(savedRelayNodes));
        p.putString("savedBumpers",     floatsToString(savedBumpers));
        p.putString("savedAttractors",  floatsToString(savedAttractors));
        p.putString("savedIcicleNodes", floatsToString(savedIcicleNodes));
        p.putString("savedTeslaCoils",  floatsToString(savedTeslaCoils));
        p.putString("savedSpringPads",  floatsToString(savedSpringPads));
        for (int i = 0; i < savedMilestoneAchieved.length; i++)
            p.putBoolean("milestone_" + i, savedMilestoneAchieved[i]);
        p.putBoolean("fhCpI",   savedFrostheimCpI);
        p.putBoolean("fhCpII",  savedFrostheimCpII);
        p.putBoolean("fhCpIII", savedFrostheimCpIII);
        p.putBoolean("fhIcicle", savedFrostheimIcicleUnlocked);
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
        p.putBoolean("fhArmBumpers",       savedFrostheimArmBumpersActive);
        p.putBoolean("gravityEnabled",  gravityEnabled);
        p.putInteger("lives",          lives);
        p.putInteger("maxLives",       maxLives);
        p.putLong("nextLifeAtMs",      nextLifeAtMs);
        p.putInteger("diamonds",       diamonds);
        p.putBoolean("unlimitedLives", unlimitedLives);
        for (int i = 0; i < bestArrivalTimes.length; i++)
            p.putFloat("bestArrivalTime_" + i, bestArrivalTimes[i]);
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
        lastGemFarmTimestamp = p.getLong("lastGemFarmTimestamp", 0L);
        pendingNewRecruits      = p.getInteger("pendingNewRecruits",  0);
        gravityEnabled          = p.getBoolean("gravityEnabled",  true);
        for (int i = 0; i < emberPerksEarned.length; i++)
            emberPerksEarned[i] = p.getBoolean("emberPerk_" + i, false);
        savedPortalPairs = stringToFloats(p.getString("savedPortalPairs", ""));
        savedRelayNodes  = stringToFloats(p.getString("savedRelayNodes",  ""));
        savedBumpers     = stringToFloats(p.getString("savedBumpers",     ""));
        savedAttractors  = stringToFloats(p.getString("savedAttractors",  ""));
        savedIcicleNodes = stringToFloats(p.getString("savedIcicleNodes",  ""));
        savedTeslaCoils  = stringToFloats(p.getString("savedTeslaCoils",  ""));
        savedSpringPads  = stringToFloats(p.getString("savedSpringPads",  ""));
        for (int i = 0; i < savedMilestoneAchieved.length; i++)
            savedMilestoneAchieved[i] = p.getBoolean("milestone_" + i, false);
        savedFrostheimCpI          = p.getBoolean("fhCpI",   false);
        savedFrostheimCpII         = p.getBoolean("fhCpII",  false);
        savedFrostheimCpIII        = p.getBoolean("fhCpIII", false);
        savedFrostheimIcicleUnlocked = p.getBoolean("fhIcicle", false);
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
        savedFrostheimArmBumpersActive    = p.getBoolean("fhArmBumpers",     false);
        lives          = p.getInteger("lives",          5);
        maxLives       = p.getInteger("maxLives",       5);
        nextLifeAtMs   = p.getLong("nextLifeAtMs",      0L);
        diamonds       = p.getInteger("diamonds",       0);
        unlimitedLives = p.getBoolean("unlimitedLives", false);
        for (int i = 0; i < bestArrivalTimes.length; i++)
            bestArrivalTimes[i] = p.getFloat("bestArrivalTime_" + i, Float.MAX_VALUE);
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
        totalJoules           = 0f;
        crystals              = 0f;
        bumperEnergyMult     += 0.5f;
        collisionEnergyMult  += 0.25f;
        wallEnergyMult       += 0.15f;
        internBoostStrength  += 0.15f;

        // Clear all saved lab state so the new planet starts fresh with 2 interns
        savedBallCount             = 0;
        savedBumpers               = new float[0];
        savedAttractors            = new float[0];
        savedIcicleNodes           = new float[0];
        savedTeslaCoils            = new float[0];
        savedSpringPads            = new float[0];
        savedPortalPairs           = new float[0];
        savedRelayNodes            = new float[0];
        savedKineticBladeCount     = 0;
        savedFrostheimDecision     = 0;
        savedTeslaHarvestRate      = 15f;
        savedHubUpgradeTier        = 0;
        savedEmberHeavyChassis     = false;
        savedEmberMagneticRim      = false;
        savedPortalBidirectional   = false;
        savedEmberSpinReversed     = false;
        savedGravShiftStep         = 0;
        savedEmberThirdInternUnlocked    = false;
        savedFrostheimThirdInternUnlocked = false;
        savedFrostheimCpI          = false;
        savedFrostheimCpII         = false;
        savedFrostheimCpIII        = false;
        savedFrostheimIcicleUnlocked = false;
        for (int i = 0; i < savedMilestoneAchieved.length; i++) savedMilestoneAchieved[i] = false;
        sectorReached              = -1;
        flightStartTimeMs = 0L;  // next planet gets a fresh timer
    }
}
