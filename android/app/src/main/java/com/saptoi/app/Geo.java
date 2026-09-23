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
import java.util.List;
import java.util.Locale;

/** Tìm địa chỉ bằng Nominatim của OpenStreetMap (miễn phí). Gọi từ luồng phụ, không gọi từ luồng giao diện. */
public final class Geo {
    private static final String BASE = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "SapToiNoi/1.0 (Android)";

    private Geo() {}

    public static List<Place> search(String query) throws IOException, JSONException {
        String url = BASE + "/search?format=json&limit=8&countrycodes=vn&accept-language=vi&q="
                + URLEncoder.encode(query, "UTF-8");
        JSONArray arr = new JSONArray(get(url));
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new Place(o.optString("display_name"),
                    Double.parseDouble(o.getString("lat")),
                    Double.parseDouble(o.getString("lon"))));
        }
        return out;
    }

    /** Trả về tên ngắn của một toạ độ, hoặc null nếu không tìm được. */
    public static String reverse(double lat, double lng) throws IOException, JSONException {
        String url = String.format(Locale.US, "%s/reverse?format=json&accept-language=vi&lat=%f&lon=%f",
                BASE, lat, lng);
        JSONObject o = new JSONObject(get(url));
        String name = o.optString("display_name", "");
        return name.isEmpty() ? null : shortName(name);
    }

    /** "Chợ Bến Thành, Lê Lợi, Quận 1, ..." → "Chợ Bến Thành, Lê Lợi" */
    public static String shortName(String displayName) {
        String[] parts = displayName.split(",");
        if (parts.length <= 2) return displayName.trim();
        return parts[0].trim() + ", " + parts[1].trim();
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
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
