package com.odyssey.analytics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnalyticsService {

    private static AnalyticsService instance;

    public static AnalyticsService getInstance() {
        if (instance == null) instance = new AnalyticsService();
        return instance;
    }

    private static final float FLUSH_INTERVAL  = 10f;
    private static final int   FLUSH_THRESHOLD = 20;
    private static final String PREFS          = "odyssey_save";

    private final Gson gson = new Gson();
    private final List<AnalyticsEvent> queue = new ArrayList<>();
    private float flushTimer = 0f;

    private String userId;
    private final String sessionId = UUID.randomUUID().toString();
    private int sessionNumber;
    private long firstOpen;
    private long sessionStartMs;
    private long totalGameplayMinutes;

    private volatile String jwt = null;

    private ActiveTimeTracker activeTimeTracker;

    private AnalyticsService() {}

    public void init() {
        com.badlogic.gdx.Preferences p = Gdx.app.getPreferences(PREFS);

        userId = p.getString("analytics_user_id", "");
        if (userId.isEmpty()) {
            userId = UUID.randomUUID().toString();
            p.putString("analytics_user_id", userId);
            p.flush();
        }

        firstOpen = p.getLong("analytics_first_open", 0L);
        if (firstOpen == 0L) {
            firstOpen = System.currentTimeMillis() / 1000L;
            p.putLong("analytics_first_open", firstOpen);
            p.flush();
        }

        sessionNumber = p.getInteger("analytics_session_number", 0) + 1;
        p.putInteger("analytics_session_number", sessionNumber);
        p.flush();

        totalGameplayMinutes = p.getLong("analytics_gameplay_mins", 0L);
        sessionStartMs = System.currentTimeMillis();

        activeTimeTracker = new ActiveTimeTracker(this);
        fetchJwt();
    }

    private void fetchJwt() {
        if (AnalyticsConfig.BASE_URL.isEmpty() || AnalyticsConfig.CLIENT_SECRET.isEmpty()) return;
        try {
            String body = "{\"userId\":\"" + userId + "\"}";
            Net.HttpRequest req = new Net.HttpRequest(Net.HttpMethods.POST);
            req.setUrl(AnalyticsConfig.BASE_URL + "/token");
            req.setHeader("Content-Type", "application/json");
            req.setHeader("x-client-secret", AnalyticsConfig.CLIENT_SECRET);
            req.setContent(body);
            req.setTimeOut(8000);
            Gdx.net.sendHttpRequest(req, new Net.HttpResponseListener() {
                @Override
                public void handleHttpResponse(Net.HttpResponse r) {
                    try {
                        String raw = r.getResultAsString();
                        Gdx.app.log("Analytics", "token response: " + raw);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resp = gson.fromJson(raw, Map.class);
                        if (resp != null && resp.containsKey("token")) {
                            jwt = (String) resp.get("token");
                            Gdx.app.log("Analytics", "JWT acquired");
                        }
                    } catch (Exception e) { Gdx.app.log("Analytics", "token parse error: " + e); }
                }
                @Override public void failed(Throwable t) { Gdx.app.log("Analytics", "token fetch failed: " + t); }
                @Override public void cancelled() {}
            });
        } catch (Exception ignored) {}
    }

    public void tick(float delta) {
        if (activeTimeTracker != null) activeTimeTracker.tick(delta);
        flushTimer += delta;
        if (flushTimer >= FLUSH_INTERVAL || queue.size() >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    public void appForegrounded() {
        if (activeTimeTracker != null) activeTimeTracker.resume();
    }

    public void appBackgrounded() {
        if (activeTimeTracker != null) activeTimeTracker.pause();
        flush();
    }

    // ---- Public event API ----

    public void sessionStart() {
        logEventRaw("session_start", new HashMap<>());
        if (activeTimeTracker != null) activeTimeTracker.resume();
    }

    public void sessionEnd() {
        if (activeTimeTracker != null) activeTimeTracker.pause();
        long sessionMins = (System.currentTimeMillis() - sessionStartMs) / 60_000L;
        totalGameplayMinutes += sessionMins;
        Gdx.app.getPreferences(PREFS).putLong("analytics_gameplay_mins", totalGameplayMinutes).flush();
        logEventRaw("session_end", new HashMap<>());
        flush();
    }

    public void logTutorial(int stepNumber, String stepName) {
        Map<String, Object> p = new HashMap<>();
        p.put("step_number", stepNumber);
        p.put("step_name", stepName);
        logEventRaw("tutorial", p);
    }

    public void logAdWatch(String placement) {
        Map<String, Object> p = new HashMap<>();
        p.put("placement", placement);
        logEventRaw("ad_watch", p);
    }

    public void logPurchase(String sku, String orderId, double valueUsd, boolean test) {
        Map<String, Object> p = new HashMap<>();
        p.put("sku", sku);
        p.put("order_id", orderId);
        p.put("value_usd", valueUsd);
        p.put("test", test);
        logEventRaw("purchase", p);
    }

    public void logCurrencySource(String originType, String origin, long value, long balanceAfter) {
        logCurrencyEvent("source", originType, origin, value, balanceAfter);
    }

    public void logCurrencySink(String originType, String origin, long value, long balanceAfter) {
        logCurrencyEvent("sink", originType, origin, value, balanceAfter);
    }

    private void logCurrencyEvent(String transType, String originType, String origin, long value, long balanceAfter) {
        Map<String, Object> p = new HashMap<>();
        p.put("trans_type", transType);
        p.put("origin_type", originType);
        p.put("origin", origin);
        p.put("value", value);
        p.put("balance", balanceAfter);
        logEventRaw("main_currency", p);
    }

    public void logMilestone(String milestoneType, String milestoneName) {
        Map<String, Object> p = new HashMap<>();
        p.put("milestone_type", milestoneType);
        p.put("milestone_name", milestoneName);
        logEventRaw("milestone_achieved", p);
    }

    public void logMilestone(String milestoneType, String milestoneName, int milestoneNumber) {
        Map<String, Object> p = new HashMap<>();
        p.put("milestone_type", milestoneType);
        p.put("milestone_name", milestoneName);
        p.put("milestone_number", milestoneNumber);
        logEventRaw("milestone_achieved", p);
    }

    // ---- Internal ----

    public void logEventRaw(String name, Map<String, Object> params) {
        params.put("user_id", userId);
        params.put("session_id", sessionId);
        params.put("session_number", sessionNumber);
        params.put("first_open", firstOpen);
        params.put("platform", Gdx.app.getType().name().toLowerCase());
        params.put("client_version", AnalyticsConfig.CLIENT_VERSION);
        params.put("gameplay_time", totalGameplayMinutes);
        params.put("device_model", System.getProperty("os.name", "unknown"));
        params.put("os_version", System.getProperty("os.version", "unknown"));
        queue.add(new AnalyticsEvent(name, params));
        if (queue.size() >= FLUSH_THRESHOLD) flush();
    }

    private synchronized void flush() {
        if (queue.isEmpty()) return;
        if (jwt == null) return; // wait for token fetch to complete

        flushTimer = 0f;
        List<AnalyticsEvent> batch = new ArrayList<>(queue);
        queue.clear();

        List<Map<String, Object>> events = new ArrayList<>();
        for (AnalyticsEvent e : batch) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("name", e.name);
            ev.put("params", e.params);
            ev.put("time", e.time);
            ev.put("eventClientId", e.eventClientId);
            events.add(ev);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", AnalyticsConfig.APP_ID);
        body.put("env", AnalyticsConfig.ENV);
        body.put("events", events);

        String json = gson.toJson(body);
        final String currentJwt = jwt;
        final List<AnalyticsEvent> sentBatch = batch;

        try {
            Net.HttpRequest req = new Net.HttpRequest(Net.HttpMethods.POST);
            req.setUrl(AnalyticsConfig.BASE_URL + "/logEventsBatch");
            req.setHeader("Content-Type", "application/json");
            req.setHeader("Authorization", "Bearer " + currentJwt);
            req.setContent(json);
            req.setTimeOut(8000);
            Gdx.net.sendHttpRequest(req, new Net.HttpResponseListener() {
                @Override
                public void handleHttpResponse(Net.HttpResponse r) {
                    int code = r.getStatus().getStatusCode();
                    Gdx.app.log("Analytics", "flush response: " + code + " " + r.getResultAsString());
                    if (code == 401) {
                        jwt = null;
                        queue.addAll(0, sentBatch);
                        fetchJwt();
                    }
                }
                @Override public void failed(Throwable t) {
                    Gdx.app.log("Analytics", "flush failed: " + t);
                    queue.addAll(0, sentBatch);
                }
                @Override public void cancelled() {}
            });
        } catch (Exception ignored) {}
    }
}
