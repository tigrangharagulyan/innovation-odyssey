package com.odyssey.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.odyssey.ShipData;

public class EnergyContactListener implements ContactListener {

    // Reusable vectors — avoids GC pressure at 60fps
    private final Vector2 velA = new Vector2();
    private final Vector2 velB = new Vector2();

    @Override
    public void beginContact(Contact contact) {
        Fixture fA = contact.getFixtureA();
        Fixture fB = contact.getFixtureB();

        Body bodyA = fA.getBody();
        Body bodyB = fB.getBody();

        float massA  = bodyA.getMass();
        float massB  = bodyB.getMass();
        velA.set(bodyA.getLinearVelocity());
        velB.set(bodyB.getLinearVelocity());

        float speedA = velA.len();
        float speedB = velB.len();

        // Surface bounciness = mean restitution of the two fixtures
        float bounciness = (fA.getRestitution() + fB.getRestitution()) * 0.5f;

        // Energy = (Mass * Velocity) * SurfaceBounciness  [per fixture pair]
        float energy = ((massA * speedA) + (massB * speedB)) * bounciness;

        if (energy > 0f) ShipData.get().addJoules(energy);
    }

    // standard no-op overrides
    @Override public void endContact(Contact contact) {}
    @Override public void preSolve(Contact contact, Manifold oldManifold) {}
    @Override public void postSolve(Contact contact, ContactImpulse impulse) {}
}
