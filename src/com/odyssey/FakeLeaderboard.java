package com.odyssey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static fake rivals for the per-planet leaderboard.
 * Times are in real-world seconds. Lower = better (faster arrival).
 */
public final class FakeLeaderboard {

    public static final class Entry {
        public final String  name;
        public final float   timeSeconds;
        public final boolean isPlayer;

        public Entry(String name, float timeSeconds, boolean isPlayer) {
            this.name        = name;
            this.timeSeconds = timeSeconds;
            this.isPlayer    = isPlayer;
        }
    }

    // 20 rivals per planet index (0=Solara … 4=Helios Forge).
    // Times in seconds. Top 5 fast, mid 10 moderate, bottom 5 slow.
    private static final String[][] NAMES = {
        // Planet 0 — Solara
        { "StarForge-7","CaptainNova","IonDrift-X","NebulaPilot","VoidWatcher",
          "OrbitalSam","CosmicAce","GalaxyRun","PulseDrive","SolarFlare",
          "CoreBreaker","CrystalEdge","WarpRookie","StarSeeker","NovaTech",
          "SlowBurn-9","DustCloud","RetroThrust","GraviTrek","IceCrystal" },
        // Planet 1 — Nova Terra
        { "HeliosBlazer","CometRider","MagmaCore","FusionPilot","IronForge-1",
          "LavaDrift","PlasmaAce","ThermalX","FlareChaser","VulcanStar",
          "MoltBurst","ForgeRunner","EmberCross","HeatSeeker","SlagPilot",
          "ColdStart-3","SlowMagma","VolcanSlow","GraviWait","IonCrawl" },
        // Planet 2 — Frostheim
        { "CryoStar","IceForge-4","FrostPilot","NordDrift","PolarAce",
          "BlizzardX","GlacierRun","FrostCore","SnowChaser","ArcticFlare",
          "IceBurst","CryoEdge","FrostRook","ShiveringX","NordStar",
          "SlowFreeze","ArcticCrawl","IceDrift-9","ColdRunner","FrostWait" },
        // Planet 3 — Cryon Reach
        { "QuantumAce","FusionReach","CryonEdge","DeepPilot","VoidForge-2",
          "DarkMatter","QuantumX","ReachRunner","NullDrift","StarVault",
          "CryonFlare","DeepCore","VoidBurst","NullChaser","QuantumStar",
          "SlowReach","DriftWait","NullCrawl","DeepSlow","VoidWander" },
        // Planet 4 — Helios Forge
        { "HeliosAce","ForgeGod-1","StellarEdge","OverdriveX","NovaForge",
          "PlasmaStar","CoreForge","StellarCore","HeliosPilot","StarAnvil",
          "ForgeMaster","HeliosRun","StellarFlare","AnvilDrift","PlasmaRook",
          "SlowForge","AnvilCrawl","StellarSlow","HeliosWait","CoreDrift" }
    };

    // Times in seconds per planet. Row: [top5 fast, mid10 moderate, bottom5 slow]
    private static final float[][] TIMES = {
        // Solara (first planet — shorter absolute times)
        { 390f, 435f, 480f, 545f, 610f,
          720f, 840f, 960f, 1080f, 1260f, 1440f, 1680f, 1920f, 2100f, 2280f,
          2700f, 3000f, 3600f, 4200f, 5400f },
        // Nova Terra
        { 900f, 1020f, 1140f, 1320f, 1500f,
          1800f, 2100f, 2400f, 2700f, 3000f, 3600f, 4200f, 4800f, 5400f, 6000f,
          7200f, 8400f, 9600f, 10800f, 14400f },
        // Frostheim
        { 1800f, 2100f, 2400f, 2700f, 3000f,
          3600f, 4200f, 4800f, 5400f, 6600f, 7800f, 9000f, 10200f, 12000f, 14400f,
          18000f, 21600f, 25200f, 28800f, 36000f },
        // Cryon Reach
        { 3600f, 4200f, 4800f, 5400f, 6000f,
          7200f, 8400f, 9600f, 10800f, 12600f, 14400f, 16200f, 18000f, 21600f, 25200f,
          32400f, 39600f, 46800f, 54000f, 72000f },
        // Helios Forge
        { 7200f, 8400f, 9600f, 10800f, 12000f,
          14400f, 16800f, 19200f, 21600f, 25200f, 28800f, 32400f, 36000f, 43200f, 50400f,
          64800f, 79200f, 93600f, 108000f, 144000f }
    };

    /**
     * Returns all 20 rivals for a planet, sorted ascending by time (fastest first).
     * The player's row is injected if playerTimeSeconds < Float.MAX_VALUE.
     *
     * @param planetIndex     0–4
     * @param playerTimeSeconds player's best time in seconds, or Float.MAX_VALUE if none
     * @return sorted list including the player row (if valid time exists)
     */
    public static List<Entry> getBoard(int planetIndex, float playerTimeSeconds) {
        List<Entry> list = new ArrayList<>();
        String[] names = NAMES[planetIndex];
        float[]  times = TIMES[planetIndex];
        for (int i = 0; i < names.length; i++) {
            list.add(new Entry(names[i], times[i], false));
        }
        if (playerTimeSeconds < Float.MAX_VALUE) {
            list.add(new Entry("YOU", playerTimeSeconds, true));
        }
        Collections.sort(list, (a, b) -> Float.compare(a.timeSeconds, b.timeSeconds));
        return list;
    }

    /**
     * Returns the player's 1-based rank on the given planet board.
     * Returns -1 if the player has no time yet.
     */
    public static int getRank(int planetIndex, float playerTimeSeconds) {
        if (playerTimeSeconds >= Float.MAX_VALUE) return -1;
        List<Entry> board = getBoard(planetIndex, playerTimeSeconds);
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).isPlayer) return i + 1;
        }
        return -1;
    }

    /** Formats seconds as "m:ss" (e.g. 125.4 → "2:05"). */
    public static String formatTime(float seconds) {
        if (seconds >= Float.MAX_VALUE) return "--:--";
        int totalSecs = (int) seconds;
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private FakeLeaderboard() {}
}
