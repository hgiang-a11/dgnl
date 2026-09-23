package com.saptoi.app;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tìm địa chỉ miễn phí trên dữ liệu OpenStreetMap:
 * - Photon: gợi ý ngay trong lúc gõ.
 * - Nominatim: dự phòng khi bấm tìm, và đổi toạ độ thành tên.
 * Gọi từ luồng phụ, không gọi từ luồng giao diện.
 */
public final class Geo {
    private static final String PHOTON = "https://photon.komoot.io/api/";
    private static final String NOMINATIM = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "SapToiNoi/1.0 (Android)";
    /** Khung bao quanh Việt Nam. */
    private static final String VN_BBOX = "102.1,8.1,109.6,23.5";

    private Geo() {}

    /** Gợi ý khi đang gõ. biasLat/biasLng là vị trí hiện tại (NaN nếu chưa có) để ưu tiên chỗ gần. */
    public static List<Place> suggest(String query, double biasLat, double biasLng)
            throws IOException, JSONException {
        StringBuilder url = new StringBuilder(PHOTON)
                .append("?limit=10&bbox=").append(VN_BBOX)
                .append("&q=").append(URLEncoder.encode(query, "UTF-8"));
        if (!Double.isNaN(biasLat) && !Double.isNaN(biasLng)) {
            url.append(String.format(Locale.US, "&lat=%.5f&lon=%.5f", biasLat, biasLng));
        }
        JSONArray features = new JSONObject(get(url.toString())).getJSONArray("features");
        List<Place> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject f = features.getJSONObject(i);
            JSONArray c = f.getJSONObject("geometry").getJSONArray("coordinates");
            JSONObject p = f.getJSONObject("properties");

            String street = join(" ", p.optString("housenumber"), p.optString("street"));
            String name = p.optString("name");
            if (name.isEmpty()) {
                name = street;
                street = "";
            }
            if (name.isEmpty()) continue;

            String address = joinUnique(name, street, p.optString("district"), p.optString("city"),
                    p.optString("county"), p.optString("state"));
            if (!seen.add(name + "|" + address)) continue;
            out.add(new Place(name, c.getDouble(1), c.getDouble(0), address));
        }
        return out;
    }

    /** Tìm đầy đủ bằng Nominatim, dùng khi gợi ý không ra kết quả. */
    public static List<Place> search(String query) throws IOException, JSONException {
        String url = NOMINATIM + "/search?format=json&limit=10&countrycodes=vn&accept-language=vi&q="
                + URLEncoder.encode(query, "UTF-8");
        JSONArray arr = new JSONArray(get(url));
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(fromDisplayName(o.optString("display_name"),
                    Double.parseDouble(o.getString("lat")),
                    Double.parseDouble(o.getString("lon"))));
        }
        return out;
    }

    /** Đổi toạ độ thành tên địa điểm, hoặc null nếu không tìm được. */
    public static Place reverse(double lat, double lng) throws IOException, JSONException {
        String url = String.format(Locale.US, "%s/reverse?format=json&accept-language=vi&lat=%f&lon=%f",
                NOMINATIM, lat, lng);
        String name = new JSONObject(get(url)).optString("display_name", "");
        return name.isEmpty() ? null : fromDisplayName(name, lat, lng);
    }

    /** "Chợ Bến Thành, Lê Lợi, Quận 1, TP HCM, 700000, Việt Nam" → tên "Chợ Bến Thành", địa chỉ "Lê Lợi, Quận 1, TP HCM" */
    private static Place fromDisplayName(String displayName, double lat, double lng) {
        String[] parts = displayName.split(",");
        String name = parts[0].trim();
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < parts.length && rest.size() < 3; i++) {
            String s = parts[i].trim();
            if (s.isEmpty() || s.matches("\\d+") || s.equalsIgnoreCase("Việt Nam")) continue;
            rest.add(s);
        }
        return new Place(name, lat, lng, rest.isEmpty() ? null : String.join(", ", rest));
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

    private static String joinUnique(String exclude, String... parts) {
        Set<String> set = new LinkedHashSet<>();
        for (String p : parts) {
            if (p != null && !p.isEmpty() && !p.equals(exclude)) set.add(p);
        }
        return set.isEmpty() ? null : String.join(", ", set);
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
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
