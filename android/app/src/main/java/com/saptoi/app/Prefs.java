package com.saptoi.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lưu cài đặt của app trong máy: nơi đến, bán kính báo, danh sách địa điểm đã lưu. */
public final class Prefs {
    private static final String FILE = "saptoi";
    private static final String KEY_DEST = "dest";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_SAVED = "saved";
    private static final String KEY_ASKED_BATTERY = "asked_battery";
    private static final int MAX_SAVED = 12;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Place getDest(Context c) {
        String s = sp(c).getString(KEY_DEST, null);
        if (s == null) return null;
        try {
            return Place.fromJson(new JSONObject(s));
        } catch (JSONException e) {
            return null;
        }
    }

    public static void setDest(Context c, Place p) {
        try {
            sp(c).edit().putString(KEY_DEST, p == null ? null : p.toJson().toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Bán kính báo, tính bằng mét. */
    public static int getRadius(Context c) {
        return sp(c).getInt(KEY_RADIUS, 500);
    }

    public static void setRadius(Context c, int meters) {
        sp(c).edit().putInt(KEY_RADIUS, meters).apply();
    }

    public static List<Place> getSaved(Context c) {
        List<Place> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY_SAVED, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(Place.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void setSaved(Context c, List<Place> list) {
        JSONArray arr = new JSONArray();
        try {
            for (int i = 0; i < list.size() && i < MAX_SAVED; i++) arr.put(list.get(i).toJson());
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString(KEY_SAVED, arr.toString()).apply();
    }

    public static boolean askedBattery(Context c) {
        return sp(c).getBoolean(KEY_ASKED_BATTERY, false);
    }

    public static void setAskedBattery(Context c) {
        sp(c).edit().putBoolean(KEY_ASKED_BATTERY, true).apply();
    }
}
