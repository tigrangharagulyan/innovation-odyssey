package com.odyssey;

import com.odyssey.analytics.AnalyticsService;
import com.odyssey.planet.FrostheimPlanet;
import com.odyssey.planet.NovaTerra;
import com.odyssey.planet.PlanetDefinition;
import com.odyssey.planet.PlanetState;
import com.odyssey.planet.SolaraPlanet;
import com.odyssey.planet.StubPlanet;
import java.util.HashMap;
import java.util.Map;

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

    public static final PlanetDefinition[] PLANET_DEFS = {
        new SolaraPlanet(),
        new NovaTerra(),
        new FrostheimPlanet(),
        new StubPlanet("cryon_reach"),
        new StubPlanet("helios_forge")
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

    // ── Replay mode ───────────────────────────────────────────────────────────
    public boolean isReplayMode      = false;
    public int     replayPlanetIndex = 0;

    // Pending result for MainMenuScreen to display after a replay run
    public boolean pendingReplayResult      = false;
    public boolean pendingReplayIsNewRecord = false;
    public float   pendingReplayTime        = 0f;
    public int     pendingReplayPlanetIdx   = 0;

    // Backup of main-progress state snapshotted before a replay starts.
    // Persisted so app-kill mid-replay can be recovered on next launch.
    public float   rb_totalJoules            = 0f;
    public float   rb_powerGenerated         = 0f;
    public float   rb_energyAtLastLaunch     = 0f;
    public float   rb_accumulatedDist        = 0f;
    public int     rb_sectorReached          = -1;
    public long    rb_flightStartTimeMs      = 0L;
    public float   rb_planetGravityMultiplier = 1.0f;
    // Saved lab arrays
    public float[] rb_savedBumpers           = new float[0];
    public float[] rb_savedAttractors        = new float[0];
    public float[] rb_savedIcicleNodes       = new float[0];
    public float[] rb_savedTeslaCoils        = new float[0];
    public float[] rb_savedSpringPads        = new float[0];
    public float[] rb_savedPortalPairs       = new float[0];
    public float[] rb_savedRelayNodes        = new float[0];
    public boolean[] rb_savedMilestoneAchieved = new boolean[6];
    // Saved lab scalars
    public int     rb_savedBallCount                      = 0;
    public int     rb_savedKineticBladeCount              = 0;
    public int     rb_savedHubUpgradeTier                 = 0;
    public int     rb_savedFrostheimDecision              = 0;
    public int     rb_savedGravShiftStep                  = 0;
    public float   rb_savedTeslaHarvestRate               = 15f;
    public boolean rb_savedFrostheimCpI                   = false;
    public boolean rb_savedFrostheimCpII                  = false;
    public boolean rb_savedFrostheimCpIII                 = false;
    public boolean rb_savedFrostheimIcicleUnlocked        = false;
    public boolean rb_savedEmberHeavyChassis              = false;
    public boolean rb_savedEmberMagneticRim               = false;
    public boolean rb_savedPortalBidirectional            = false;
    public boolean rb_savedEmberSpinReversed              = false;
    public boolean rb_savedEmberThirdInternUnlocked       = false;
    public boolean rb_savedFrostheimThirdInternUnlocked   = false;
    public boolean rb_savedFrostheimArmBumpersActive      = false;
    public int     rb_currentPlanetIndex                  = 0;
    public float   rb_crystals                            = 0f;

    // Lives & monetisation
    public int     lives           = 5;
    public int     maxLives        = 5;
    public long    nextLifeAtMs    = 0L;   // wall-clock ms when next life auto-refills; 0 = full
    public int     diamonds        = 0;
    public boolean unlimitedLives  = false;

    // Offline farming
    public int[]  internsLeftOnPlanet  = new int[PLANETS.length];
    private final Map<Integer, PlanetState> planetStates = new HashMap<>();
    public long   lastFarmingTimestamp = 0L;
    public int    pendingNewRecruits   = 0;

    // Gem farming — fixed gems/hr per planet index (Solara=1 … Helios Forge=5)
    public static final int[] GEM_FARM_RATES = {1, 2, 3, 4, 5};
    public long lastGemFarmTimestamp = 0L;   // ms epoch; 0 = uninitialised

    // Leaderboard — flight timer and per-planet personal bests
    public long    flightStartTimeMs  = 0L;          // epoch ms when current flight began; 0 = not started  // transient – not persisted
    public float[] bestArrivalTimes   = new float[]{  // seconds; Float.MAX_VALUE = no time yet
        Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE
    };

    // Per-planet time tracking
    public long   planetStartTimestampMs = 0L;         // epoch ms when player started working on current planet
    public long[] planetCompletionMs     = new long[]{ // ms taken to complete each planet; 0 = not completed
        0L, 0L, 0L, 0L, 0L
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
        diamonds                = 5000;
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
        planetStartTimestampMs = System.currentTimeMillis();
        for (int i = 0; i < planetCompletionMs.length; i++) planetCompletionMs[i] = 0L;
        pendingRankResult = -1;
        pendingRankPlanet = -1;
        planetStates.clear();
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

    /**
     * Enter replay mode for a previously-visited planet.
     * Snapshots all mutable lab/flight state into rb_* fields,
     * clears them for a fresh run, sets replay planet gravity,
     * and deducts diamond cost (50 × (planetIdx+1)).
     */
    public void startReplay(int planetIdx) {
        // --- snapshot scalars ---
        rb_totalJoules             = totalJoules;
        rb_powerGenerated          = powerGenerated;
        rb_energyAtLastLaunch      = energyAtLastLaunch;
        rb_accumulatedDist         = accumulatedDist;
        rb_sectorReached           = sectorReached;
        rb_flightStartTimeMs       = flightStartTimeMs;
        rb_planetGravityMultiplier = planetGravityMultiplier;
        // --- snapshot arrays ---
        rb_savedBumpers             = savedBumpers.clone();
        rb_savedAttractors          = savedAttractors.clone();
        rb_savedIcicleNodes         = savedIcicleNodes.clone();
        rb_savedTeslaCoils          = savedTeslaCoils.clone();
        rb_savedSpringPads          = savedSpringPads.clone();
        rb_savedPortalPairs         = savedPortalPairs.clone();
        rb_savedRelayNodes          = savedRelayNodes.clone();
        rb_savedMilestoneAchieved   = savedMilestoneAchieved.clone();
        // --- snapshot lab scalars ---
        rb_savedBallCount                   = savedBallCount;
        rb_savedKineticBladeCount           = savedKineticBladeCount;
        rb_savedHubUpgradeTier              = savedHubUpgradeTier;
        rb_savedFrostheimDecision           = savedFrostheimDecision;
        rb_savedGravShiftStep               = savedGravShiftStep;
        rb_savedTeslaHarvestRate            = savedTeslaHarvestRate;
        rb_savedFrostheimCpI                = savedFrostheimCpI;
        rb_savedFrostheimCpII               = savedFrostheimCpII;
        rb_savedFrostheimCpIII              = savedFrostheimCpIII;
        rb_savedFrostheimIcicleUnlocked     = savedFrostheimIcicleUnlocked;
        rb_savedEmberHeavyChassis           = savedEmberHeavyChassis;
        rb_savedEmberMagneticRim            = savedEmberMagneticRim;
        rb_savedPortalBidirectional         = savedPortalBidirectional;
        rb_savedEmberSpinReversed           = savedEmberSpinReversed;
        rb_savedEmberThirdInternUnlocked    = savedEmberThirdInternUnlocked;
        rb_savedFrostheimThirdInternUnlocked = savedFrostheimThirdInternUnlocked;
        rb_savedFrostheimArmBumpersActive   = savedFrostheimArmBumpersActive;

        // --- clear lab/flight state for fresh replay ---
        savedBumpers             = new float[0];
        savedAttractors          = new float[0];
        savedIcicleNodes         = new float[0];
        savedTeslaCoils          = new float[0];
        savedSpringPads          = new float[0];
        savedPortalPairs         = new float[0];
        savedRelayNodes          = new float[0];
        savedMilestoneAchieved   = new boolean[6];
        savedBallCount           = 0;
        savedKineticBladeCount   = 0;
        savedHubUpgradeTier      = 0;
        savedFrostheimDecision   = 0;
        savedGravShiftStep       = 0;
        savedTeslaHarvestRate    = 15f;
        savedFrostheimCpI        = false;
        savedFrostheimCpII       = false;
        savedFrostheimCpIII      = false;
        savedFrostheimIcicleUnlocked  = false;
        savedEmberHeavyChassis   = false;
        savedEmberMagneticRim    = false;
        savedPortalBidirectional = false;
        savedEmberSpinReversed   = false;
        savedEmberThirdInternUnlocked    = false;
        savedFrostheimThirdInternUnlocked = false;
        savedFrostheimArmBumpersActive   = false;
        rb_currentPlanetIndex = currentPlanetIndex;
        rb_crystals           = crystals;
        totalJoules          = 0f;
        crystals             = 0f;
        energyAtLastLaunch   = powerGenerated; // delta starts at 0 for replay launch
        accumulatedDist      = 0f;
        sectorReached        = -1;
        flightStartTimeMs    = 0L;
        planetGravityMultiplier = PLANETS[planetIdx].gravity;
        currentPlanetIndex   = planetIdx;

        // --- deduct diamond cost ---
        diamonds -= 50 * (planetIdx + 1);

        // --- enter replay mode ---
        isReplayMode      = true;
        replayPlanetIndex = planetIdx;
    }

    /**
     * Exit replay mode after a successful arrival.
     * Records the time if it beats the current best, sets pendingReplay* for display,
     * and fully restores main-progress state from rb_* snapshot.
     */
    public void endReplay(float elapsedSeconds) {
        // record time
        boolean isNew = elapsedSeconds < bestArrivalTimes[replayPlanetIndex];
        if (isNew) bestArrivalTimes[replayPlanetIndex] = elapsedSeconds;

        // set result for MainMenuScreen
        pendingReplayResult      = true;
        pendingReplayIsNewRecord = isNew;
        pendingReplayTime        = elapsedSeconds;
        pendingReplayPlanetIdx   = replayPlanetIndex;

        // restore all fields
        _restoreReplayBackup();
        isReplayMode = false;
    }

    /**
     * Abandon replay (e.g., app killed mid-replay and restarted).
     * Restores main-progress state without recording any result.
     */
    public void abandonReplay() {
        _restoreReplayBackup();
        isReplayMode = false;
    }

    private void _restoreReplayBackup() {
        totalJoules             = rb_totalJoules;
        powerGenerated          = rb_powerGenerated;
        energyAtLastLaunch      = rb_energyAtLastLaunch;
        accumulatedDist         = rb_accumulatedDist;
        sectorReached           = rb_sectorReached;
        flightStartTimeMs       = rb_flightStartTimeMs;
        planetGravityMultiplier = rb_planetGravityMultiplier;
        savedBumpers            = rb_savedBumpers;
        savedAttractors         = rb_savedAttractors;
        savedIcicleNodes        = rb_savedIcicleNodes;
        savedTeslaCoils         = rb_savedTeslaCoils;
        savedSpringPads         = rb_savedSpringPads;
        savedPortalPairs        = rb_savedPortalPairs;
        savedRelayNodes         = rb_savedRelayNodes;
        savedMilestoneAchieved  = rb_savedMilestoneAchieved;
        savedBallCount                    = rb_savedBallCount;
        savedKineticBladeCount            = rb_savedKineticBladeCount;
        savedHubUpgradeTier               = rb_savedHubUpgradeTier;
        savedFrostheimDecision            = rb_savedFrostheimDecision;
        savedGravShiftStep                = rb_savedGravShiftStep;
        savedTeslaHarvestRate             = rb_savedTeslaHarvestRate;
        savedFrostheimCpI                 = rb_savedFrostheimCpI;
        savedFrostheimCpII                = rb_savedFrostheimCpII;
        savedFrostheimCpIII               = rb_savedFrostheimCpIII;
        savedFrostheimIcicleUnlocked      = rb_savedFrostheimIcicleUnlocked;
        savedEmberHeavyChassis            = rb_savedEmberHeavyChassis;
        savedEmberMagneticRim             = rb_savedEmberMagneticRim;
        savedPortalBidirectional          = rb_savedPortalBidirectional;
        savedEmberSpinReversed            = rb_savedEmberSpinReversed;
        savedEmberThirdInternUnlocked     = rb_savedEmberThirdInternUnlocked;
        savedFrostheimThirdInternUnlocked = rb_savedFrostheimThirdInternUnlocked;
        savedFrostheimArmBumpersActive    = rb_savedFrostheimArmBumpersActive;
        currentPlanetIndex                = rb_currentPlanetIndex;
        crystals                          = rb_crystals;
    }

    /** Call every frame — refills lives from the real-time clock. */
    public void tickLives() {
        if (unlimitedLives || lives >= maxLives) { nextLifeAtMs = 0L; return; }
        long now = System.currentTimeMillis();
        // Auto-start timer if lives are short but the timer was never set
        // (e.g. old save had maxLives=3 full; now maxLives=5, timer was cleared)
        if (nextLifeAtMs == 0L) {
            nextLifeAtMs = now + 3_600_000L;
        }
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
        p.putLong("planetStartTimestampMs", planetStartTimestampMs);
        for (int i = 0; i < planetCompletionMs.length; i++)
            p.putLong("planetCompletionMs_" + i, planetCompletionMs[i]);
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
        p.putBoolean("isReplayMode",    isReplayMode);
        p.putInteger("replayPlanetIdx", replayPlanetIndex);
        if (isReplayMode) {
            p.putFloat("rb_totalJoules",            rb_totalJoules);
            p.putFloat("rb_powerGenerated",         rb_powerGenerated);
            p.putFloat("rb_energyAtLastLaunch",     rb_energyAtLastLaunch);
            p.putFloat("rb_accumulatedDist",        rb_accumulatedDist);
            p.putInteger("rb_sectorReached",        rb_sectorReached);
            p.putLong("rb_flightStartTimeMs",       rb_flightStartTimeMs);
            p.putFloat("rb_planetGravityMult",      rb_planetGravityMultiplier);
            p.putString("rb_bumpers",   floatsToString(rb_savedBumpers));
            p.putString("rb_attractors",floatsToString(rb_savedAttractors));
            p.putString("rb_icicles",   floatsToString(rb_savedIcicleNodes));
            p.putString("rb_tesla",     floatsToString(rb_savedTeslaCoils));
            p.putString("rb_springs",   floatsToString(rb_savedSpringPads));
            p.putString("rb_portals",   floatsToString(rb_savedPortalPairs));
            p.putString("rb_relays",    floatsToString(rb_savedRelayNodes));
            p.putInteger("rb_ballCount",            rb_savedBallCount);
            p.putInteger("rb_kineticBladeCount",    rb_savedKineticBladeCount);
            p.putInteger("rb_hubTier",              rb_savedHubUpgradeTier);
            p.putInteger("rb_fhDecision",           rb_savedFrostheimDecision);
            p.putInteger("rb_gravShiftStep",        rb_savedGravShiftStep);
            p.putFloat("rb_teslaRate",              rb_savedTeslaHarvestRate);
            p.putBoolean("rb_fhCpI",   rb_savedFrostheimCpI);
            p.putBoolean("rb_fhCpII",  rb_savedFrostheimCpII);
            p.putBoolean("rb_fhCpIII", rb_savedFrostheimCpIII);
            p.putBoolean("rb_fhIcicle",rb_savedFrostheimIcicleUnlocked);
            p.putBoolean("rb_emHeavy", rb_savedEmberHeavyChassis);
            p.putBoolean("rb_emMagnet",rb_savedEmberMagneticRim);
            p.putBoolean("rb_portalBidir",   rb_savedPortalBidirectional);
            p.putBoolean("rb_emSpinRev",     rb_savedEmberSpinReversed);
            p.putBoolean("rb_emThirdIntern", rb_savedEmberThirdInternUnlocked);
            p.putBoolean("rb_fhThirdIntern", rb_savedFrostheimThirdInternUnlocked);
            p.putBoolean("rb_fhArmBumpers",  rb_savedFrostheimArmBumpersActive);
            // milestone array: store as comma-separated ints
            StringBuilder msb = new StringBuilder();
            for (int i = 0; i < rb_savedMilestoneAchieved.length; i++) {
                if (i > 0) msb.append(',');
                msb.append(rb_savedMilestoneAchieved[i] ? 1 : 0);
            }
            p.putString("rb_milestones", msb.toString());
            p.putInteger("rb_currentPlanetIndex", rb_currentPlanetIndex);
            p.putFloat("rb_crystals",             rb_crystals);
        }
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
        planetStartTimestampMs = System.currentTimeMillis(); // always fresh — don't accumulate offline time
        for (int i = 0; i < planetCompletionMs.length; i++)
            planetCompletionMs[i] = p.getLong("planetCompletionMs_" + i, 0L);
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
        maxLives       = 5; // always 5; not persisted so upgrades don't carry over
        nextLifeAtMs   = p.getLong("nextLifeAtMs",      0L);
        diamonds       = p.getInteger("diamonds",       5000);
        if (diamonds < 5000) diamonds = 5000;
        unlimitedLives = p.getBoolean("unlimitedLives", false);
        for (int i = 0; i < bestArrivalTimes.length; i++)
            bestArrivalTimes[i] = p.getFloat("bestArrivalTime_" + i, Float.MAX_VALUE);
        isReplayMode      = p.getBoolean("isReplayMode",    false);
        replayPlanetIndex = p.getInteger("replayPlanetIdx", 0);
        if (isReplayMode) {
            rb_totalJoules            = p.getFloat("rb_totalJoules",        0f);
            rb_powerGenerated         = p.getFloat("rb_powerGenerated",     0f);
            rb_energyAtLastLaunch     = p.getFloat("rb_energyAtLastLaunch", 0f);
            rb_accumulatedDist        = p.getFloat("rb_accumulatedDist",    0f);
            rb_sectorReached          = p.getInteger("rb_sectorReached",   -1);
            rb_flightStartTimeMs      = p.getLong("rb_flightStartTimeMs",  0L);
            rb_planetGravityMultiplier = p.getFloat("rb_planetGravityMult", 1.0f);
            rb_savedBumpers           = stringToFloats(p.getString("rb_bumpers",    ""));
            rb_savedAttractors        = stringToFloats(p.getString("rb_attractors", ""));
            rb_savedIcicleNodes       = stringToFloats(p.getString("rb_icicles",    ""));
            rb_savedTeslaCoils        = stringToFloats(p.getString("rb_tesla",      ""));
            rb_savedSpringPads        = stringToFloats(p.getString("rb_springs",    ""));
            rb_savedPortalPairs       = stringToFloats(p.getString("rb_portals",    ""));
            rb_savedRelayNodes        = stringToFloats(p.getString("rb_relays",     ""));
            rb_savedBallCount                   = p.getInteger("rb_ballCount",         0);
            rb_savedKineticBladeCount           = p.getInteger("rb_kineticBladeCount", 0);
            rb_savedHubUpgradeTier              = p.getInteger("rb_hubTier",           0);
            rb_savedFrostheimDecision           = p.getInteger("rb_fhDecision",        0);
            rb_savedGravShiftStep               = p.getInteger("rb_gravShiftStep",     0);
            rb_savedTeslaHarvestRate            = p.getFloat("rb_teslaRate",          15f);
            rb_savedFrostheimCpI                = p.getBoolean("rb_fhCpI",   false);
            rb_savedFrostheimCpII               = p.getBoolean("rb_fhCpII",  false);
            rb_savedFrostheimCpIII              = p.getBoolean("rb_fhCpIII", false);
            rb_savedFrostheimIcicleUnlocked     = p.getBoolean("rb_fhIcicle",false);
            rb_savedEmberHeavyChassis           = p.getBoolean("rb_emHeavy", false);
            rb_savedEmberMagneticRim            = p.getBoolean("rb_emMagnet",false);
            rb_savedPortalBidirectional         = p.getBoolean("rb_portalBidir",   false);
            rb_savedEmberSpinReversed           = p.getBoolean("rb_emSpinRev",     false);
            rb_savedEmberThirdInternUnlocked    = p.getBoolean("rb_emThirdIntern", false);
            rb_savedFrostheimThirdInternUnlocked = p.getBoolean("rb_fhThirdIntern",false);
            rb_savedFrostheimArmBumpersActive   = p.getBoolean("rb_fhArmBumpers",  false);
            String msStr = p.getString("rb_milestones", "");
            if (!msStr.isEmpty()) {
                String[] parts = msStr.split(",");
                for (int i = 0; i < parts.length && i < rb_savedMilestoneAchieved.length; i++)
                    rb_savedMilestoneAchieved[i] = parts[i].equals("1");
            }
            rb_currentPlanetIndex = p.getInteger("rb_currentPlanetIndex", 0);
            rb_crystals           = p.getFloat("rb_crystals",             0f);
            // App was killed mid-replay — silently abandon and restore main progress
            abandonReplay();
        }
        return true;
    }

    /** Format milliseconds as "Xh Ym" (>= 1h), "Xm Ys" (>= 1m), or "Xs". */
    public static String formatDuration(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        if (h > 0)  return h + "h " + (m % 60) + "m";
        if (m > 0)  return m + "m " + (s % 60) + "s";
        return s + "s";
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

    public PlanetDefinition getCurrentDef() {
        return PLANET_DEFS[currentPlanetIndex];
    }

    public PlanetState getState(int idx) {
        return planetStates.computeIfAbsent(idx, k -> new PlanetState());
    }

    public void saveReplayBackup(int planetIndex, PlanetState backup) {
        planetStates.put(-(planetIndex + 1), backup);
    }

    public PlanetState getReplayBackup(int planetIndex) {
        return planetStates.get(-(planetIndex + 1));
    }

    public void selectPlanet(int index) {
        selectedPlanetIndex = Math.max(0, Math.min(index, PLANETS.length - 1));
    }

    public void commitSelectedPlanet() {
        PlanetProfile p = getSelectedPlanet();
        targetPlanetDistance    = p.distance;
        planetGravityMultiplier = p.gravity;
    }

    public void markArrival(float routeDistance, float energySpent) {
        // Record how long the player spent on the planet they just completed
        if (currentPlanetIndex < planetCompletionMs.length && planetStartTimestampMs > 0L)
            planetCompletionMs[currentPlanetIndex] = Math.max(0L, System.currentTimeMillis() - planetStartTimestampMs);
        planetStartTimestampMs = System.currentTimeMillis(); // start timer for next planet
        PlanetProfile p = getSelectedPlanet();
        currentPlanetIndex      = selectedPlanetIndex;
        targetPlanetDistance    = p.distance;
        planetGravityMultiplier = p.gravity;
        arrivalReady            = true;
        arrivalsCompleted++;
        AnalyticsService.getInstance().logMilestone("planet", p.name.toLowerCase().replace(" ", "_") + "_arrived", arrivalsCompleted);
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
