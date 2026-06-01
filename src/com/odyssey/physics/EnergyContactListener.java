package com.odyssey.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.ObjectMap;
import com.odyssey.ShipData;

public class EnergyContactListener implements ContactListener {

    private final Vector2 normVec = new Vector2();
    /** Tracks last-collision timestamp per orb body; written here, read in stepPhysics idle-pull. */
    private final ObjectMap<Body, Long> ballLastHitMs;

    // Sparks (◆/❅) earned per collision type
    private static final float SPARK_INTERN_INTERN = 20f;  // multiplied by collisionEnergyMult
    private static final float SPARK_WALL          = 1f;   // every ring/wall hit
    private static final float SPARK_GRAVITY       = 50f;  // gravity-well core contact

    public EnergyContactListener(ObjectMap<Body, Long> ballLastHitMs) {
        this.ballLastHitMs = ballLastHitMs;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture fA = contact.getFixtureA();
        Fixture fB = contact.getFixtureB();
        if (fA.isSensor() || fB.isSensor()) return;

        Body bodyA = fA.getBody();
        Body bodyB = fB.getBody();

        // ---- Ring and center hit detection ----
        boolean aIsRing   = bodyA.getUserData() instanceof ShipData.RingHitData;
        boolean bIsRing   = bodyB.getUserData() instanceof ShipData.RingHitData;
        boolean aIsCenter = bodyA.getUserData() instanceof ShipData.CenterHitData;
        boolean bIsCenter = bodyB.getUserData() instanceof ShipData.CenterHitData;

        if (aIsRing || bIsRing || aIsCenter || bIsCenter) {
            boolean _aInt = (bodyA.getUserData() instanceof String
                         && (((String) bodyA.getUserData()).startsWith("INTERN")
                             || "PELLET".equals(bodyA.getUserData())));
            boolean _bInt = (bodyB.getUserData() instanceof String
                         && (((String) bodyB.getUserData()).startsWith("INTERN")
                             || "PELLET".equals(bodyB.getUserData())));
            if (_aInt || _bInt) {
                long _now = System.currentTimeMillis();
                boolean _isBlaze = (_aInt && "INTERN_BLAZE".equals(bodyA.getUserData()))
                                || (_bInt && "INTERN_BLAZE".equals(bodyB.getUserData()));
                ShipData _sd0 = ShipData.get();
                long _cooldown = (_isBlaze && _sd0.blazeShieldActive) ? 30L : 120L;
                int  _dmg = 1;
                if (_isBlaze && _sd0.blazeOverloadHits > 0) { _dmg = 3; _sd0.blazeOverloadHits--; }
                if (aIsRing) {
                    ShipData.RingHitData _rhd = (ShipData.RingHitData) bodyA.getUserData();
                    if (!_rhd.readyToDestroy && _now - _rhd.lastHitMs > _cooldown) {
                        _rhd.lastHitMs = _now;
                        _rhd.hitsRemaining -= _dmg;
                        if (_rhd.hitsRemaining <= 0) _rhd.readyToDestroy = true;
                    }
                }
                if (bIsRing) {
                    ShipData.RingHitData _rhd = (ShipData.RingHitData) bodyB.getUserData();
                    if (!_rhd.readyToDestroy && _now - _rhd.lastHitMs > _cooldown) {
                        _rhd.lastHitMs = _now;
                        _rhd.hitsRemaining -= _dmg;
                        if (_rhd.hitsRemaining <= 0) _rhd.readyToDestroy = true;
                    }
                }
                if (aIsCenter) {
                    ShipData.CenterHitData _chd = (ShipData.CenterHitData) bodyA.getUserData();
                    if (!_chd.readyToDestroy && _now - _chd.lastHitMs > 80L) {
                        _chd.lastHitMs = _now;
                        if (--_chd.hitsRemaining <= 0) _chd.readyToDestroy = true;
                    }
                }
                if (bIsCenter) {
                    ShipData.CenterHitData _chd = (ShipData.CenterHitData) bodyB.getUserData();
                    if (!_chd.readyToDestroy && _now - _chd.lastHitMs > 80L) {
                        _chd.lastHitMs = _now;
                        if (--_chd.hitsRemaining <= 0) _chd.readyToDestroy = true;
                    }
                }
            }
            return;  // ring/center contacts don't generate SP/energy
        }

        // ---- Classify each body by its userData token ----
        boolean aIsIntern = bodyA.getUserData() instanceof String
                            && (((String) bodyA.getUserData()).startsWith("INTERN")
                                || "PELLET".equals(bodyA.getUserData()));
        boolean bIsIntern = bodyB.getUserData() instanceof String
                            && (((String) bodyB.getUserData()).startsWith("INTERN")
                                || "PELLET".equals(bodyB.getUserData()));

        // Icicle Node contacts are handled entirely in stepPhysics — skip here
        boolean aIsIcicle = "ICICLE".equals(bodyA.getUserData());
        boolean bIsIcicle = "ICICLE".equals(bodyB.getUserData());
        if (aIsIcicle || bIsIcicle) return;

        // Stamp last-hit time for any intern involved — used by idle-pull in stepPhysics
        if (ballLastHitMs != null) {
            long now = System.currentTimeMillis();
            if (aIsIntern) ballLastHitMs.put(bodyA, now);
            if (bIsIntern) ballLastHitMs.put(bodyB, now);
        }

        // Pause all earnings while player is placing a structure
        if (ShipData.get().placingStructure) return;

        // Ember IV: Kinetic Blade slam — award +35 J per impact
        boolean aIsBlade = "KINETIC_BLADE".equals(bodyA.getUserData());
        boolean bIsBlade = "KINETIC_BLADE".equals(bodyB.getUserData());
        if (aIsBlade || bIsBlade) {
            if (aIsIntern || bIsIntern) {
                ShipData sd2 = ShipData.get();
                sd2.addJoules(35f);
                queueFloatNum(contact, bodyA, bodyB, 35f, 0, sd2);
            }
            return;
        }

        // Ember IV: Volcanic Spring-Pad contact — award +25 SP burst per hit
        boolean aIsSpringPad = "SPRING_PAD".equals(fA.getUserData());
        boolean bIsSpringPad = "SPRING_PAD".equals(fB.getUserData());
        if (aIsSpringPad || bIsSpringPad) {
            if (aIsIntern || bIsIntern) {
                ShipData sdSp = ShipData.get();
                sdSp.addCrystals(25f);
                queueFloatNum(contact, bodyA, bodyB, 25f, 1, sdSp);
            }
            return;
        }

        // Standard bumpers carry a BumperHitData instance for per-hit flash animation
        boolean aIsStdBumper = bodyA.getUserData() instanceof ShipData.BumperHitData;
        boolean bIsStdBumper = bodyB.getUserData() instanceof ShipData.BumperHitData;

        // Frostheim arm bumpers — fixed 25 SP, bypass normal bumper multiplier
        boolean aIsArmBumper = aIsStdBumper && ((ShipData.BumperHitData) bodyA.getUserData()).isArmBumper;
        boolean bIsArmBumper = bIsStdBumper && ((ShipData.BumperHitData) bodyB.getUserData()).isArmBumper;
        if (aIsArmBumper || bIsArmBumper) {
            if (aIsIntern || bIsIntern) {
                ShipData sdArm = ShipData.get();
                sdArm.addCrystals(25f);
                queueFloatNum(contact, bodyA, bodyB, 25f, 3, sdArm);
                if (aIsArmBumper) ((ShipData.BumperHitData) bodyA.getUserData()).lastHitMs = System.currentTimeMillis();
                if (bIsArmBumper) ((ShipData.BumperHitData) bodyB.getUserData()).lastHitMs = System.currentTimeMillis();
            }
            return;
        }

        // Frostheim valley notch guards — 8 SP per hit, 280ms cooldown (Perk C)
        boolean aIsValleyBlade = aIsStdBumper && ((ShipData.BumperHitData) bodyA.getUserData()).isValleyBlade;
        boolean bIsValleyBlade = bIsStdBumper && ((ShipData.BumperHitData) bodyB.getUserData()).isValleyBlade;
        if (aIsValleyBlade || bIsValleyBlade) {
            if (aIsIntern || bIsIntern) {
                Body bladeBody = aIsValleyBlade ? bodyA : bodyB;
                ShipData.BumperHitData vhd = (ShipData.BumperHitData) bladeBody.getUserData();
                long now = System.currentTimeMillis();
                if (now < vhd.lastHitMs) vhd.lastHitMs = now; // NTP clock rollback guard
                if (now - vhd.lastHitMs >= 280) {
                    ShipData sdVb = ShipData.get();
                    sdVb.addCrystals(8f);
                    queueFloatNum(contact, bodyA, bodyB, 8f, 3, sdVb);
                    vhd.lastHitMs = now;
                }
            }
            return;
        }

        // fixtureIsBump catches attractor core (Level 1: "BUMPER") and Tesla-Coil core (Level 2: "TESLA_COIL_CORE")
        boolean fixtureIsBump = "BUMPER".equals(fA.getUserData())
                             || "BUMPER".equals(fB.getUserData())
                             || "TESLA_COIL_CORE".equals(fA.getUserData())
                             || "TESLA_COIL_CORE".equals(fB.getUserData());

        boolean attractorHit = fixtureIsBump && !aIsStdBumper && !bIsStdBumper;
        boolean bumperHit    = aIsStdBumper || bIsStdBumper || fixtureIsBump;

        ShipData sd = ShipData.get();

        if (aIsIntern && bIsIntern) {
            // ---- Intern-intern collision: primary Spark / Frost-Shard source ----
            float sparks = SPARK_INTERN_INTERN * sd.collisionEnergyMult;
            sd.addCrystals(sparks);
            sd.pendingCollisionSounds++;
            queueFloatNum(contact, bodyA, bodyB, sparks, 1, sd);

            // Mutual separation impulse keeps the chaos alive
            normVec.set(bodyB.getPosition()).sub(bodyA.getPosition());
            if (normVec.len2() > 0.0001f) {
                normVec.nor();
                float boost = sd.internBoostStrength;
                bodyA.applyLinearImpulse(
                    -normVec.x * boost, -normVec.y * boost,
                    bodyA.getPosition().x, bodyA.getPosition().y, true);
                bodyB.applyLinearImpulse(
                     normVec.x * boost,  normVec.y * boost,
                    bodyB.getPosition().x, bodyB.getPosition().y, true);
            }

        } else if (bumperHit) {
            // ---- Standard bumper or gravity-well / Tesla-Coil core contact ----
            float bonus     = attractorHit ? SPARK_GRAVITY * sd.gravityMult : sd.bumperSparkValue * sd.bumperMult;
            int   colorType = attractorHit ? 2 : 3;
            sd.addCrystals(bonus);
            sd.pendingBumperSounds++;
            queueFloatNum(contact, bodyA, bodyB, bonus, colorType, sd);
            // Stamp hit time so each body's renderer can drive its own flash animation
            if (aIsStdBumper) ((ShipData.BumperHitData)   bodyA.getUserData()).lastHitMs = System.currentTimeMillis();
            if (bIsStdBumper) ((ShipData.BumperHitData)   bodyB.getUserData()).lastHitMs = System.currentTimeMillis();
            if (attractorHit) {
                long ts = System.currentTimeMillis();
                if (bodyA.getUserData() instanceof ShipData.AttractorHitData)
                    ((ShipData.AttractorHitData) bodyA.getUserData()).lastHitMs = ts;
                if (bodyB.getUserData() instanceof ShipData.AttractorHitData)
                    ((ShipData.AttractorHitData) bodyB.getUserData()).lastHitMs = ts;
            }
            // Harvest node: increment hitCount up to 10 for standard bumpers and attractors
            if (aIsIntern || bIsIntern) {
                if (aIsStdBumper) {
                    ShipData.BumperHitData bhd = (ShipData.BumperHitData) bodyA.getUserData();
                    if (!bhd.harvestPending && ++bhd.hitCount >= 10) bhd.harvestPending = true;
                }
                if (bIsStdBumper) {
                    ShipData.BumperHitData bhd = (ShipData.BumperHitData) bodyB.getUserData();
                    if (!bhd.harvestPending && ++bhd.hitCount >= 10) bhd.harvestPending = true;
                }
                if (attractorHit) {
                    if (bodyA.getUserData() instanceof ShipData.AttractorHitData) {
                        ShipData.AttractorHitData ahd = (ShipData.AttractorHitData) bodyA.getUserData();
                        if (!ahd.harvestPending && ++ahd.hitCount >= 10) ahd.harvestPending = true;
                    }
                    if (bodyB.getUserData() instanceof ShipData.AttractorHitData) {
                        ShipData.AttractorHitData ahd = (ShipData.AttractorHitData) bodyB.getUserData();
                        if (!ahd.harvestPending && ++ahd.hitCount >= 10) ahd.harvestPending = true;
                    }
                }
            }

        } else if (aIsIntern || bIsIntern) {
            // ---- Wall / ring contact ----
            float wallGain = SPARK_WALL * sd.wallEnergyMult;
            if (wallGain > 0f) {
                sd.addCrystals(wallGain);
                queueFloatNum(contact, bodyA, bodyB, wallGain, 0, sd);
            }
            // Perk 2 (Wall Energy, wallEnergyMult >= 2): also generate energy
            if (sd.wallEnergyMult >= 2f) {
                sd.addJoules(8f);
            }
            // BLAZE skill 3 BURN: +8 SP per wall/ring bounce
            boolean _burnBlaze = (aIsIntern && "INTERN_BLAZE".equals(bodyA.getUserData()))
                              || (bIsIntern && "INTERN_BLAZE".equals(bodyB.getUserData()));
            if (_burnBlaze && sd.blazeBurnActive) {
                sd.addCrystals(8f);
                queueFloatNum(contact, bodyA, bodyB, 8f, 1, sd);
            }
        }
    }

    private void queueFloatNum(Contact contact, Body bA, Body bB, float value, int colorType, ShipData sd) {
        WorldManifold wm = contact.getWorldManifold();
        float cx, cy;
        if (wm.getNumberOfContactPoints() > 0) {
            cx = wm.getPoints()[0].x;
            cy = wm.getPoints()[0].y;
        } else {
            cx = (bA.getPosition().x + bB.getPosition().x) * 0.5f;
            cy = (bA.getPosition().y + bB.getPosition().y) * 0.5f;
        }
        sd.pendingContactEvents.add(new float[]{cx, cy, value, colorType});
    }

    @Override public void endContact(Contact contact) {}
    @Override public void preSolve(Contact contact, Manifold oldManifold) {}
    @Override public void postSolve(Contact contact, ContactImpulse impulse) {}
}
