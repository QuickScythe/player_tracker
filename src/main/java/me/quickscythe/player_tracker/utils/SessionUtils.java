package me.quickscythe.player_tracker.utils;

import json2.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionUtils {

    private static Map<UUID, JSONObject> SESSIONS = new HashMap<>();

    public static JSONObject getSessionInfo(UUID uid) {
        if (!SESSIONS.containsKey(uid)) SESSIONS.put(uid, new JSONObject());
        return SESSIONS.get(uid);
    }

    public static void clearSession(UUID uid){
        SESSIONS.remove(uid);
    }

    public static JSONObject getJson(String key, JSONObject json) {
        return json.has(key) ? json.getJSONObject(key) : json.put(key, new JSONObject()).getJSONObject(key);
    }

    public static void addInt(String key, int i, JSONObject json) {
        json.put(key, json.has(key) ? json.getInt(key) + 1 : 1);
    }
}
