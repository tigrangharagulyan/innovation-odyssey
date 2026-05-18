package com.odyssey;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Innovation Odyssey");
        cfg.setWindowedMode(1280, 720);
        cfg.setForegroundFPS(60);
        cfg.useVsync(true);
        new Lwjgl3Application(new OdysseyGame(), cfg);
    }
}
