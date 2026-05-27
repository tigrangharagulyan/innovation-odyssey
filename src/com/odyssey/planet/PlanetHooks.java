package com.odyssey.planet;

import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.physics.box2d.Body;
import com.odyssey.ShipData;

public interface PlanetHooks {
    PlanetState  state();
    ShipData     shipData();
    World        world();
    Array<Body>  balls();
    Array<Body>  bumpers();
    Array<Body>  attractors();
    /** Cryo vents (Frostheim) or Kinetic Blades (NovaTerra) */
    Array<Body>  specialBodiesA();
    /** Tesla coils (Frostheim) or Spring pads (NovaTerra) */
    Array<Body>  specialBodiesB();
    Array<Float> specialData();
    float drumCenterX();
    float drumCenterY();
    float drumRadius();
    void showCelebration(String title, String body);
    void triggerShake(float mag, float dur);
}
