package com.odyssey;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

public final class SoundManager {
    private static SoundManager instance;

    private Sound sndHire, sndCollision, sndBumper, sndMilestone, sndCheckpoint, sndLaunch;
    private float collisionCooldown = 0f;
    private static final float COLLISION_THROTTLE = 0.20f;  // max 1 collision sound / 200ms

    private SoundManager() {
        sndHire       = tryLoad("sounds/hire.ogg");
        sndCollision  = tryLoad("sounds/collision.ogg");
        sndBumper     = tryLoad("sounds/bumper.ogg");
        sndMilestone  = tryLoad("sounds/milestone.ogg");
        sndCheckpoint = tryLoad("sounds/checkpoint.ogg");
        sndLaunch     = tryLoad("sounds/launch.ogg");
    }

    public static SoundManager get() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    private Sound tryLoad(String path) {
        try {
            if (Gdx.files.internal(path).exists())
                return Gdx.audio.newSound(Gdx.files.internal(path));
        } catch (Exception ignored) {}
        return null;
    }

    public void update(float delta) {
        if (collisionCooldown > 0f) collisionCooldown -= delta;
    }

    public void playHire()       { play(sndHire,       1.0f); }
    public void playBumper()     { play(sndBumper,     0.8f); }
    public void playMilestone()  { play(sndMilestone,  1.0f); }
    public void playCheckpoint() { play(sndCheckpoint, 1.0f); }
    public void playLaunch()     { play(sndLaunch,     1.0f); }

    public void playCollision() {
        if (collisionCooldown <= 0f) {
            play(sndCollision, 0.5f);
            collisionCooldown = COLLISION_THROTTLE;
        }
    }

    private void play(Sound s, float volume) {
        if (s != null) s.play(volume);
    }

    public void dispose() {
        Sound[] all = {sndHire, sndCollision, sndBumper, sndMilestone, sndCheckpoint, sndLaunch};
        for (Sound s : all) if (s != null) s.dispose();
        instance = null;
    }
}