package com.odyssey.analytics;

import java.util.Map;
import java.util.UUID;

public class AnalyticsEvent {
    public final String name;
    public final Map<String, Object> params;
    public final long time;
    public final String eventClientId;

    public AnalyticsEvent(String name, Map<String, Object> params) {
        this.name = name;
        this.params = params;
        this.time = System.currentTimeMillis() / 1000L;
        this.eventClientId = UUID.randomUUID().toString();
    }
}
