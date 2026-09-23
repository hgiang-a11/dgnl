package com.saptoi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

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
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 15;
    private static final String KEY_VIBRATE_ONLY = "vibrate_only";
    private static final String KEY_RINGTONE = "ringtone";
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
        return readList(c, KEY_SAVED);
    }

    public static void setSaved(Context c, List<Place> list) {
        writeList(c, KEY_SAVED, list, MAX_SAVED);
    }

    /** Các nơi đã chọn gần đây, mới nhất ở đầu. */
    public static List<Place> getHistory(Context c) {
        return readList(c, KEY_HISTORY);
    }

    public static void addHistory(Context c, Place p) {
        List<Place> list = getHistory(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).samePlace(p)) list.remove(i);
        }
        list.add(0, p);
        writeList(c, KEY_HISTORY, list, MAX_HISTORY);
    }

    public static void removeHistory(Context c, Place p) {
        List<Place> list = getHistory(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).samePlace(p)) list.remove(i);
        }
        writeList(c, KEY_HISTORY, list, MAX_HISTORY);
    }

    private static List<Place> readList(Context c, String key) {
        List<Place> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(Place.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return list;
    }

    private static void writeList(Context c, String key, List<Place> list, int max) {
        JSONArray arr = new JSONArray();
        try {
            for (int i = 0; i < list.size() && i < max; i++) arr.put(list.get(i).toJson());
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString(key, arr.toString()).apply();
    }

    /** true: chỉ rung, không phát chuông. */
    public static boolean vibrateOnly(Context c) {
        return sp(c).getBoolean(KEY_VIBRATE_ONLY, false);
    }

    public static void setVibrateOnly(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_VIBRATE_ONLY, v).apply();
    }

    /** Nhạc chuông đã chọn, hoặc null để dùng chuông báo thức mặc định của máy. */
    public static Uri getRingtone(Context c) {
        String s = sp(c).getString(KEY_RINGTONE, null);
        return s == null ? null : Uri.parse(s);
    }

    public static void setRingtone(Context c, Uri uri) {
        sp(c).edit().putString(KEY_RINGTONE, uri == null ? null : uri.toString()).apply();
    }

    public static boolean askedBattery(Context c) {
        return sp(c).getBoolean(KEY_ASKED_BATTERY, false);
    }

    public static void setAskedBattery(Context c) {
        sp(c).edit().putBoolean(KEY_ASKED_BATTERY, true).apply();
    }
}
