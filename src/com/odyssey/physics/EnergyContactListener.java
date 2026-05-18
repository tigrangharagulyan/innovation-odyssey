package com.odyssey.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.odyssey.ShipData;

public class EnergyContactListener implements ContactListener {

    // Reusable vectors — avoids GC pressure at 60fps
    private final Vector2 velA    = new Vector2();
    private final Vector2 velB    = new Vector2();
    private final Vector2 normVec = new Vector2();

    private static final float INTERN_INTERN_SCALE = 8f;   // intern-intern: primary joule source
    private static final float WALL_SCALE          = 0.5f;  // wall/bumper: small trickle

    @Override
    public void beginContact(Contact contact) {
        Fixture fA = contact.getFixtureA();
        Fixture fB = contact.getFixtureB();
        if (fA.isSensor() || fB.isSensor()) return;

        Body bodyA = fA.getBody();
        Body bodyB = fB.getBody();

        boolean aIsIntern = bodyA.getUserData() instanceof String && ((String)bodyA.getUserData()).startsWith("INTERN");
        boolean bIsIntern = bodyB.getUserData() instanceof String && ((String)bodyB.getUserData()).startsWith("INTERN");

        float massA = bodyA.getMass();
        float massB = bodyB.getMass();
        velA.set(bodyA.getLinearVelocity());
        velB.set(bodyB.getLinearVelocity());
        float bounciness = (fA.getRestitution() + fB.getRestitution()) * 0.5f;
        float base = ((massA * velA.len()) + (massB * velB.len())) * bounciness;

        ShipData sd = ShipData.get();
        float energy;
        if (aIsIntern && bIsIntern) {
            energy = base * INTERN_INTERN_SCALE * sd.collisionEnergyMult;
        } else {
            energy = base * WALL_SCALE * sd.wallEnergyMult;
            // bumper hit: medium bonus on top of wall trickle
            if ("BUMPER".equals(bodyA.getUserData()) || "BUMPER".equals(bodyB.getUserData())
                    || "BUMPER".equals(fA.getUserData()) || "BUMPER".equals(fB.getUserData()))
                energy *= sd.bumperEnergyMult;
        }

        if (energy > 0f) ShipData.get().addJoules(energy);

        // Speed boost on intern-intern hit — encourages chaos
        if (aIsIntern && bIsIntern) {
            normVec.set(bodyB.getPosition()).sub(bodyA.getPosition());
            if (normVec.len2() > 0.0001f) {
                normVec.nor();
                float boost = ShipData.get().internBoostStrength;
                bodyA.applyLinearImpulse(-normVec.x * boost, -normVec.y * boost,
                    bodyA.getPosition().x, bodyA.getPosition().y, true);
                bodyB.applyLinearImpulse( normVec.x * boost,  normVec.y * boost,
                    bodyB.getPosition().x, bodyB.getPosition().y, true);
            }
        }
    }

    // standard no-op overrides
    @Override public void endContact(Contact contact) {}
    @Override public void preSolve(Contact contact, Manifold oldManifold) {}
    @Override public void postSolve(Contact contact, ContactImpulse impulse) {}
}
