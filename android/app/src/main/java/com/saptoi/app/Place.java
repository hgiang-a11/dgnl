package com.saptoi.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Một địa điểm: tên, toạ độ, địa chỉ và loại (bệnh viện, quán ăn...). */
public final class Place {
    public final String name;
    public final double lat;
    public final double lng;
    /** Địa chỉ ngắn hiển thị dưới tên; có thể null. */
    public final String address;
    /** Mã loại địa điểm trong {@link Poi}; có thể null. */
    public final String kind;

    public Place(String name, double lat, double lng) {
        this(name, lat, lng, null, null);
    }

    public Place(String name, double lat, double lng, String address) {
        this(name, lat, lng, address, null);
    }

    public Place(String name, double lat, double lng, String address, String kind) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.address = address;
        this.kind = kind;
    }

    /** Tên để hiển thị; nếu chưa có tên thì dùng toạ độ. */
    public String label() {
        if (name != null && !name.isEmpty()) return name;
        return coords();
    }

    public String coords() {
        return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng);
    }

    public Place withName(String newName) {
        return new Place(newName, lat, lng, address, kind);
    }

    public Place withAddress(String newAddress) {
        return new Place(name, lat, lng, newAddress, kind);
    }

    public boolean samePlace(Place o) {
        return o != null && Math.abs(o.lat - lat) < 1e-6 && Math.abs(o.lng - lng) < 1e-6;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name == null ? "" : name);
        o.put("lat", lat);
        o.put("lng", lng);
        if (address != null) o.put("address", address);
        if (kind != null) o.put("kind", kind);
        return o;
    }

    static Place fromJson(JSONObject o) {
        String n = o.optString("name", "");
        String a = o.optString("address", "");
        String k = o.optString("kind", "");
        return new Place(n.isEmpty() ? null : n, o.optDouble("lat"), o.optDouble("lng"),
                a.isEmpty() ? null : a, k.isEmpty() ? null : k);
    }
}
