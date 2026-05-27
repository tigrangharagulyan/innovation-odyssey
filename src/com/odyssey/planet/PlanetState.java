package com.odyssey.planet;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlanetState {
    private final Map<String, Object> data;

    public PlanetState() { this.data = new LinkedHashMap<>(); }
    private PlanetState(Map<String, Object> src) { this.data = new LinkedHashMap<>(src); }

    public boolean getBool(String key)              { return getBool(key, false); }
    public boolean getBool(String key, boolean def) {
        Object v = data.get(key);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }
    public int getInt(String key)          { return getInt(key, 0); }
    public int getInt(String key, int def) {
        Object v = data.get(key);
        return (v instanceof Integer) ? (Integer) v : def;
    }
    public float getFloat(String key, float def) {
        Object v = data.get(key);
        if (v instanceof Float)   return (Float) v;
        if (v instanceof Integer) return ((Integer) v).floatValue();
        return def;
    }
    public void set(String key, boolean v) { data.put(key, v); }
    public void set(String key, int v)     { data.put(key, v); }
    public void set(String key, float v)   { data.put(key, v); }

    public PlanetState deepCopy() { return new PlanetState(data); }
}
