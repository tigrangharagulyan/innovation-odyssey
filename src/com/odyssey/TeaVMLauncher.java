package com.odyssey;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.teavm.TeaVMApplication;
import com.badlogic.gdx.backends.teavm.TeaVMApplicationConfig;
import org.teavm.jso.browser.Window;

/** TeaVM entry point — compiled to JS by the teavm-gradle plugin. */
public class TeaVMLauncher {

    public static void main(String[] args) {
        TeaVMApplicationConfig config = new TeaVMApplicationConfig();
        config.setWebappPath("webapp");
        config.setCanvasId("canvas");

        new TeaVMApplication((ApplicationListener) new OdysseyGame(), config);
    }
}
