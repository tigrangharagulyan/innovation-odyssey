package com.odyssey.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.odyssey.ShipData;

public class EnergyContactListener implements ContactListener {

    private final Vector2 normVec = new Vector2();

    // Sparks (◆/❅) earned per collision type
    private static final float SPARK_INTERN_INTERN = 20f;  // multiplied by collisionEnergyMult
    private static final float SPARK_WALL          = 0.5f; // every ring/wall hit
    private static final float SPARK_GRAVITY       = 50f;  // gravity-well core contact

    @Override
    public void beginContact(Contact contact) {
        Fixture fA = contact.getFixtureA();
        Fixture fB = contact.getFixtureB();
        if (fA.isSensor() || fB.isSensor()) return;

        Body bodyA = fA.getBody();
        Body bodyB = fB.getBody();

        // ---- Classify each body by its userData token ----
        boolean aIsIntern = bodyA.getUserData() instanceof String
                            && ((String) bodyA.getUserData()).startsWith("INTERN");
        boolean bIsIntern = bodyB.getUserData() instanceof String
                            && ((String) bodyB.getUserData()).startsWith("INTERN");

        // Cryo-Vent impulse is handled entirely in stepPhysics — skip here
        boolean aIsCryo = "CRYO_VENT".equals(bodyA.getUserData());
        boolean bIsCryo = "CRYO_VENT".equals(bodyB.getUserData());
        if (aIsCryo || bIsCryo) return;

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
            float bonus     = attractorHit ? SPARK_GRAVITY : sd.bumperSparkValue;
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

        } else {
            // ---- Wall / ring contact: tiny passive trickle ----
            sd.addCrystals(SPARK_WALL);
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
