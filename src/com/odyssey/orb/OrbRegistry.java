package com.odyssey.orb;

import com.badlogic.gdx.utils.Array;

/**
 * Single source of truth for all orb data (stats, skills, costs, visuals).
 *
 * <p>Behaviour (skill effects) still lives in the screen's switch statements keyed
 * by orb index + slot. This registry owns the DATA only — adding a new orb here makes
 * it appear in every data-driven array the screen derives at init time.</p>
 *
 * <p>IMPORTANT: order must stay stable. Persisted save data (skillRank, unlock flags)
 * indexes orbs by their position in {@link #ORBS}. Append new orbs at the end.</p>
 */
public final class OrbRegistry {

    /** One activatable skill on an orb. */
    public static final class SkillDef {
        public final String  name;        // short label e.g. "DASH"
        public final String  desc;        // 2-line button description
        public final float   manaCost;
        public final float   cooldown;    // seconds
        public final float   duration;    // seconds active (0 = instant / until-event)
        public final float[] rankSpCost;  // SP to reach rank 1, 2, 3 (length 3)

        public SkillDef(String name, String desc, float manaCost, float cooldown,
                        float duration, float[] rankSpCost) {
            this.name = name;
            this.desc = desc;
            this.manaCost = manaCost;
            this.cooldown = cooldown;
            this.duration = duration;
            this.rankSpCost = rankSpCost;
        }
    }

    /** Full definition of one orb type. */
    public static final class OrbDef {
        public final String    id;            // stable key e.g. "SPARK"
        public final String    displayName;
        public final float[]   color;         // {r,g,b} 0..1
        public final float     radius;        // meters
        public final float     restitution;
        public final float     density;
        public final String    textureKey;    // internal asset path
        public final float     unlockCost;    // crystals (0 = unlocked from start)
        public final SkillDef[] skills;       // length 4

        public OrbDef(String id, String displayName, float[] color, float radius,
                      float restitution, float density, String textureKey,
                      float unlockCost, SkillDef[] skills) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.radius = radius;
            this.restitution = restitution;
            this.density = density;
            this.textureKey = textureKey;
            this.unlockCost = unlockCost;
            this.skills = skills;
        }
    }

    public static final Array<OrbDef> ORBS = new Array<>();

    static {
        // ---- SPARK — purple, fast/small (starter) ----
        ORBS.add(new OrbDef("SPARK", "SPARK", new float[]{0.75f, 0.20f, 1.00f},
            0.14f, 1.25f, 1.0f, "ui/orb_spark.png", 0f, new SkillDef[]{
                new SkillDef("DASH",      "Burst fwd\ninstant",      10f,  6f,  0f, new float[]{ 500f, 1500f, 4000f}),
                new SkillDef("MARKER",    "Wall hit\nspawns bumper", 20f, 12f,  0f, new float[]{ 750f, 2000f, 5500f}),
                new SkillDef("OVERDRIVE", "6s min\nspd 8m/s",        30f, 14f,  6f, new float[]{1000f, 2500f, 6000f}),
                new SkillDef("SPLIT",     "6s split\n3 orbs",        50f, 25f,  6f, new float[]{1000f, 3000f, 8000f}),
            }));

        // ---- BLAZE — orange, normal ----
        ORBS.add(new OrbDef("BLAZE", "BLAZE", new float[]{1.00f, 0.42f, 0.10f},
            0.25f, 0.90f, 1.0f, "ui/orb_blaze.png", 350f, new SkillDef[]{
                new SkillDef("SHIELD",  "3x ring\nhit rate",      20f, 14f,  6f, new float[]{ 500f, 1500f, 4000f}),
                new SkillDef("MAGNET",  "Pull orbs\nto BLAZE",    20f, 12f,  6f, new float[]{ 750f, 2000f, 5500f}),
                new SkillDef("OVERLOAD","Horn push\nwall=speed",  25f, 16f,  8f, new float[]{1000f, 3000f, 8000f}),
                new SkillDef("REV POL", "Pull ALL\nto center",     0f, 18f,  0f, new float[]{ 500f, 1500f, 4000f}),
            }));

        // ---- FROST — cyan, big/slow ----
        ORBS.add(new OrbDef("FROST", "FROST", new float[]{0.25f, 0.92f, 1.00f},
            0.32f, 0.65f, 1.8f, "ui/orb_frost.png", 700f, new SkillDef[]{
                new SkillDef("ATTACH",   "Park+spin\nwall",     20f, 14f, 60f, new float[]{ 750f, 2000f, 5500f}),
                new SkillDef("ICE RUSH", "Detach\nrush 4x",     25f,  8f,  0f, new float[]{ 750f, 2000f, 5500f}),
                new SkillDef("BIG",      "3x size\n3x dmg",     30f, 14f,  6f, new float[]{ 500f, 1500f, 4000f}),
                new SkillDef("GRAVITY",  "Snow wave\nspin all", 40f, 22f, 10f, new float[]{1000f, 3000f, 8000f}),
            }));
    }

    public static int count() { return ORBS.size; }

    public static OrbDef get(int index) { return ORBS.get(index); }

    public static OrbDef byId(String id) {
        for (OrbDef d : ORBS) if (d.id.equals(id)) return d;
        return null;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ORBS.size; i++) if (ORBS.get(i).id.equals(id)) return i;
        return -1;
    }

    private OrbRegistry() {}
}
