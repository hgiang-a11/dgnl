package com.saptoi.app;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tìm địa chỉ miễn phí trên dữ liệu OpenStreetMap:
 * - Photon: gợi ý ngay trong lúc gõ, và đổi toạ độ thành địa chỉ.
 * - Nominatim: tìm thêm khi bấm nút tìm, và dự phòng khi Photon lỗi.
 * - Overpass: tìm cổng / lối vào quanh một địa điểm lớn.
 * Gọi từ luồng phụ, không gọi từ luồng giao diện.
 */
public final class Geo {
    private static final String PHOTON = "https://photon.komoot.io";
    private static final String NOMINATIM = "https://nominatim.openstreetmap.org";
    private static final String OVERPASS = "https://overpass-api.de/api/interpreter";
    private static final String USER_AGENT = "SapToiNoi/1.1 (Android)";
    /** Khung bao quanh Việt Nam. */
    private static final String VN_BBOX = "102.1,8.1,109.6,23.5";
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private Geo() {}

    // ---------- Tìm kiếm ----------

    /**
     * Gợi ý khi đang gõ. biasLat/biasLng (NaN nếu không có) là vị trí của bạn hoặc giữa bản đồ,
     * để các chỗ gần đó được xếp lên trước (trong bán kính khoảng 15 km).
     */
    public static List<Place> suggest(String query, double biasLat, double biasLng)
            throws IOException, JSONException {
        StringBuilder url = new StringBuilder(PHOTON)
                .append("/api/?limit=15&bbox=").append(VN_BBOX)
                .append("&q=").append(enc(query));
        if (hasBias(biasLat, biasLng)) {
            url.append(String.format(Locale.US, "&lat=%.5f&lon=%.5f&zoom=12&location_bias_scale=0.1",
                    biasLat, biasLng));
        }
        JSONArray features = new JSONObject(get(url.toString())).getJSONArray("features");
        List<Place> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject f = features.getJSONObject(i);
            JSONArray c = f.getJSONObject("geometry").getJSONArray("coordinates");
            Place p = fromPhoton(f.getJSONObject("properties"), c.getDouble(1), c.getDouble(0));
            if (p != null && seen.add(p.label() + "|" + p.address)) out.add(p);
        }
        return out;
    }

    /** Tìm đầy đủ bằng Nominatim (chỉ dùng khi bấm nút tìm, theo quy định của Nominatim). */
    public static List<Place> search(String query, double biasLat, double biasLng)
            throws IOException, JSONException {
        StringBuilder url = new StringBuilder(NOMINATIM)
                .append("/search?format=jsonv2&addressdetails=1&limit=10&countrycodes=vn&accept-language=vi&q=")
                .append(enc(query));
        if (hasBias(biasLat, biasLng)) {
            url.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=0",
                    biasLng - 0.4, biasLat + 0.4, biasLng + 0.4, biasLat - 0.4));
        }
        JSONArray arr = new JSONArray(get(url.toString()));
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Place p = fromNominatim(o, Double.parseDouble(o.getString("lat")),
                    Double.parseDouble(o.getString("lon")), false);
            if (p != null) out.add(p);
        }
        return out;
    }

    // ---------- Đổi toạ độ thành địa chỉ ----------

    /** Tên + địa chỉ bằng chữ của một điểm ghim trên bản đồ, hoặc null nếu không tìm được. */
    public static Place reverse(double lat, double lng) throws IOException, JSONException {
        try {
            Place p = photonReverse(lat, lng);
            if (p != null) return p;
        } catch (IOException | JSONException ignored) {
            // Thử Nominatim.
        }
        return nominatimReverse(lat, lng);
    }

    private static Place photonReverse(double lat, double lng) throws IOException, JSONException {
        String url = String.format(Locale.US, "%s/reverse?lat=%.6f&lon=%.6f&limit=6&radius=0.3", PHOTON, lat, lng);
        JSONArray features = new JSONObject(get(url)).getJSONArray("features");
        String title = null;
        String address = null;
        for (int i = 0; i < features.length() && (title == null || address == null); i++) {
            JSONObject f = features.getJSONObject(i);
            JSONObject p = f.getJSONObject("properties");
            JSONArray c = f.getJSONObject("geometry").getJSONArray("coordinates");
            float dist = distance(lat, lng, c.getDouble(1), c.getDouble(0));
            String name = p.optString("name").trim();
            String number = p.optString("housenumber").trim();
            String street = p.optString("street").trim();
            if (title == null) {
                if (!number.isEmpty() && !street.isEmpty()) title = number + " " + street;
                else if (!name.isEmpty() && (dist <= 40 || "highway".equals(p.optString("osm_key")))) title = name;
                else if (!street.isEmpty()) title = street;
                else if (!name.isEmpty()) title = "Gần " + name;
            }
            if (address == null) {
                address = joinUnique(title, p.optString("locality"), p.optString("district"),
                        p.optString("county"), p.optString("city"), p.optString("state"));
            }
        }
        return title == null ? null : new Place(title, lat, lng, address, "pin");
    }

    private static Place nominatimReverse(double lat, double lng) throws IOException, JSONException {
        String url = String.format(Locale.US,
                "%s/reverse?format=jsonv2&addressdetails=1&zoom=18&accept-language=vi&lat=%.6f&lon=%.6f",
                NOMINATIM, lat, lng);
        JSONObject o = new JSONObject(get(url));
        if (o.has("error")) return null;
        return fromNominatim(o, lat, lng, true);
    }

    // ---------- Cổng, lối vào ----------

    /** Các cổng / lối vào có tên hoặc số quanh một địa điểm (ví dụ "Cổng 1", "Cổng 2"). */
    public static List<Place> entrances(Place around, int radiusMeters) throws IOException, JSONException {
        String q = String.format(Locale.US,
                "[out:json][timeout:10];(node(around:%1$d,%2$.6f,%3$.6f)[\"entrance\"];"
                        + "node(around:%1$d,%2$.6f,%3$.6f)[\"barrier\"~\"^(gate|lift_gate|entrance)$\"];);out body 150;",
                radiusMeters, around.lat, around.lng);
        JSONArray els = new JSONObject(get(OVERPASS + "?data=" + enc(q))).optJSONArray("elements");
        Map<String, Place> byLabel = new LinkedHashMap<>();
        Map<String, Float> dist = new HashMap<>();
        if (els == null) return new ArrayList<>();
        for (int i = 0; i < els.length(); i++) {
            JSONObject e = els.getJSONObject(i);
            JSONObject t = e.optJSONObject("tags");
            if (t == null || !e.has("lat")) continue;
            String access = t.optString("access");
            if ("private".equals(access) || "no".equals(access)) continue;
            String label = gateLabel(t);
            if (label == null) continue;
            double la = e.getDouble("lat");
            double lo = e.getDouble("lon");
            float d = distance(around.lat, around.lng, la, lo);
            Float old = dist.get(label);
            if (old != null && old <= d) continue;
            dist.put(label, d);
            byLabel.put(label, new Place(label, la, lo, around.label(), "gate"));
        }
        List<Place> out = new ArrayList<>(byLabel.values());
        Collections.sort(out, (a, b) -> naturalCompare(a.label(), b.label()));
        return out.size() > 12 ? out.subList(0, 12) : out;
    }

    private static String gateLabel(JSONObject t) {
        String name = t.optString("name").trim();
        if (!name.isEmpty()) return name;
        String ref = t.optString("ref").trim();
        if (!ref.isEmpty()) return ref.toLowerCase(Locale.ROOT).startsWith("cổng") ? ref : "Cổng " + ref;
        if ("main".equals(t.optString("entrance"))) return "Cổng chính";
        return null;
    }

    /** "Cổng 2" đứng trước "Cổng 10". */
    private static int naturalCompare(String a, String b) {
        Matcher ma = NUMBER.matcher(a);
        Matcher mb = NUMBER.matcher(b);
        if (ma.find() && mb.find()) {
            String pa = a.substring(0, ma.start());
            String pb = b.substring(0, mb.start());
            if (pa.equalsIgnoreCase(pb)) {
                try {
                    int c = Long.compare(Long.parseLong(ma.group()), Long.parseLong(mb.group()));
                    if (c != 0) return c;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return a.compareToIgnoreCase(b);
    }

    // ---------- Đọc kết quả ----------

    private static Place fromPhoton(JSONObject p, double lat, double lng) {
        String name = p.optString("name").trim();
        String number = p.optString("housenumber").trim();
        String street = p.optString("street").trim();
        String house = join(" ", number, street);
        String kind = Poi.fromOsm(p.optString("osm_key"), p.optString("osm_value"));
        String title;
        String streetPart;
        if (!name.isEmpty()) {
            title = name;
            streetPart = house;
        } else if (!house.isEmpty()) {
            title = house;
            streetPart = "";
            if ("place".equals(kind)) kind = "address";
        } else {
            return null;
        }
        String address = joinUnique(title, streetPart, p.optString("locality"), p.optString("district"),
                p.optString("county"), p.optString("city"), p.optString("state"));
        return new Place(title, lat, lng, address, kind);
    }

    /** Kết quả Nominatim dạng jsonv2 có addressdetails. pin = true khi đổi toạ độ ghim thành địa chỉ. */
    private static Place fromNominatim(JSONObject o, double lat, double lng, boolean pin) {
        JSONObject a = o.optJSONObject("address");
        if (a == null) a = new JSONObject();
        String name = o.optString("name").trim();
        String number = a.optString("house_number").trim();
        String road = a.optString("road").trim();
        String house = join(" ", number, road);
        String title;
        String streetPart = "";
        if (pin && !number.isEmpty() && !road.isEmpty()) {
            title = house;
        } else if (!name.isEmpty()) {
            title = name;
            streetPart = house;
        } else if (!house.isEmpty()) {
            title = house;
        } else {
            String display = o.optString("display_name");
            title = display.contains(",") ? display.substring(0, display.indexOf(',')).trim() : display.trim();
        }
        if (title.isEmpty()) return null;
        String kind = pin ? "pin" : Poi.fromOsm(o.optString("category"), o.optString("type"));
        String address = joinUnique(title, streetPart, a.optString("quarter"), a.optString("suburb"),
                a.optString("neighbourhood"), a.optString("city_district"), a.optString("city"),
                a.optString("town"), a.optString("county"), a.optString("state"));
        return new Place(title, lat, lng, address, kind);
    }

    // ---------- Tiện ích ----------

    private static boolean hasBias(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    private static float distance(double lat1, double lng1, double lat2, double lng2) {
        float[] r = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, r);
        return r[0];
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(p);
        }
        return b.toString();
    }

    /** Nối các phần địa chỉ, bỏ phần trống, trùng nhau, trùng tên, và "Việt Nam". Tối đa 4 phần. */
    private static String joinUnique(String exclude, String... parts) {
        Set<String> set = new LinkedHashSet<>();
        for (String p : parts) {
            if (p == null) continue;
            p = p.trim();
            if (p.isEmpty() || p.equals(exclude) || p.equalsIgnoreCase("Việt Nam")) continue;
            if (set.size() < 4) set.add(p);
        }
        return set.isEmpty() ? null : String.join(", ", set);
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(12000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
