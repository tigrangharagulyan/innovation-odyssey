package com.odyssey.analytics;

import com.badlogic.gdx.Gdx;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ActiveTimeTracker {

    private static final int[] THRESHOLDS = {1, 3, 5, 10, 30};
    private static final String PREFS    = "odyssey_save";
    private static final String KEY_DATE = "analytics_active_date";
    private static final String KEY_SECS = "analytics_active_secs";

    private final AnalyticsService service;
    private boolean running = false;
    private float activeSecsToday = 0f;
    private final Set<Integer> fired = new HashSet<>();
    private String today;

    public ActiveTimeTracker(AnalyticsService service) {
        this.service = service;
        load();
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void load() {
        today = todayKey();
        com.badlogic.gdx.Preferences p = Gdx.app.getPreferences(PREFS);
        if (today.equals(p.getString(KEY_DATE, ""))) {
            activeSecsToday = p.getFloat(KEY_SECS, 0f);
            for (int t : THRESHOLDS) {
                if (activeSecsToday >= t * 60f) fired.add(t);
            }
        }
    }

    private void save() {
        com.badlogic.gdx.Preferences p = Gdx.app.getPreferences(PREFS);
        p.putString(KEY_DATE, today);
        p.putFloat(KEY_SECS, activeSecsToday);
        p.flush();
    }

    public void resume() { running = true; }

    public void pause() {
        running = false;
        save();
    }

    public void tick(float delta) {
        if (!running) return;

        String now = todayKey();
        if (!now.equals(today)) {
            today = now;
            activeSecsToday = 0f;
            fired.clear();
        }

        activeSecsToday += delta;

        for (int threshold : THRESHOLDS) {
            if (!fired.contains(threshold) && activeSecsToday >= threshold * 60f) {
                fired.add(threshold);
                Map<String, Object> params = new HashMap<>();
                params.put("minute", threshold);
                service.logEventRaw("active_time_reached", params);
            }
        }
    }
}
