package com.saptoi.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Một địa điểm: tên và toạ độ. */
public final class Place {
    public final String name;
    public final double lat;
    public final double lng;

    public Place(String name, double lat, double lng) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    /** Tên để hiển thị; nếu chưa có tên thì dùng toạ độ. */
    public String label() {
        if (name != null && !name.isEmpty()) return name;
        return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng);
    }

    public Place withName(String newName) {
        return new Place(newName, lat, lng);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name == null ? "" : name);
        o.put("lat", lat);
        o.put("lng", lng);
        return o;
    }

    static Place fromJson(JSONObject o) {
        String n = o.optString("name", "");
        return new Place(n.isEmpty() ? null : n, o.optDouble("lat"), o.optDouble("lng"));
    }
}
