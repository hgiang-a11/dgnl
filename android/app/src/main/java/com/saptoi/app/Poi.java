package com.saptoi.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Các loại địa điểm (ăn uống, mua sắm, y tế...): màu, biểu tượng, tên tiếng Việt.
 * Dùng để vẽ icon tròn trên bản đồ, trong danh sách tìm kiếm và trên ghim nơi đến.
 */
final class Poi {

    static final class Kind {
        final String id;
        final int color;
        final int glyph;
        /** Tên loại để hiện cho người dùng; null nếu không cần hiện. */
        final String label;

        Kind(String id, int color, int glyph, String label) {
            this.id = id;
            this.color = color;
            this.glyph = glyph;
            this.label = label;
        }
    }

    /** Các lớp chứa icon địa điểm trong kiểu bản đồ OpenFreeMap "liberty". */
    static final String[] MAP_LAYERS = {"poi_r1", "poi_r7", "poi_r20", "poi_transit"};

    private static final int FOOD = 0xFFE8710A;
    private static final int SHOP = 0xFF4285F4;
    private static final int TRANSIT = 0xFF174EA6;
    private static final int HEALTH = 0xFFEA4335;
    private static final int LODGING = 0xFFD01884;
    private static final int EDU = 0xFF5C6BC0;
    private static final int NATURE = 0xFF1E8E3E;
    private static final int CULTURE = 0xFF129EAF;
    private static final int WORSHIP = 0xFF8D6E63;
    private static final int FINANCE = 0xFF546E7A;
    private static final int SERVICE = 0xFF78909C;
    private static final int BEAUTY = 0xFFAB47BC;
    private static final int GRAY = 0xFF80868B;
    static final int SAVED = 0xFFF9AB00;
    static final int PIN = 0xFFEA4335;

    private static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    static {
        add("restaurant", FOOD, R.drawable.ic_g_restaurant, "Nhà hàng");
        add("fastfood", FOOD, R.drawable.ic_g_fastfood, "Đồ ăn nhanh");
        add("cafe", FOOD, R.drawable.ic_g_cafe, "Quán cà phê");
        add("bar", FOOD, R.drawable.ic_g_bar, "Quán bar, quán nhậu");
        add("bakery", FOOD, R.drawable.ic_g_bakery, "Tiệm bánh");
        add("icecream", FOOD, R.drawable.ic_g_icecream, "Tiệm kem");
        add("shop", SHOP, R.drawable.ic_g_shopping_bag, "Cửa hàng");
        add("mall", SHOP, R.drawable.ic_g_shopping_bag, "Trung tâm thương mại");
        add("grocery", SHOP, R.drawable.ic_g_cart, "Siêu thị, tạp hoá");
        add("market", SHOP, R.drawable.ic_g_storefront, "Chợ");
        add("hospital", HEALTH, R.drawable.ic_g_hospital, "Bệnh viện");
        add("clinic", HEALTH, R.drawable.ic_g_hospital, "Phòng khám");
        add("pharmacy", HEALTH, R.drawable.ic_g_pharmacy, "Nhà thuốc");
        add("bus", TRANSIT, R.drawable.ic_g_bus, "Trạm xe buýt");
        add("rail", TRANSIT, R.drawable.ic_g_train, "Nhà ga");
        add("airport", TRANSIT, R.drawable.ic_g_flight, "Sân bay");
        add("lodging", LODGING, R.drawable.ic_g_hotel, "Khách sạn, nhà nghỉ");
        add("school", EDU, R.drawable.ic_g_school, "Trường học");
        add("university", EDU, R.drawable.ic_g_school, "Trường đại học");
        add("library", EDU, R.drawable.ic_g_library, "Thư viện");
        add("park", NATURE, R.drawable.ic_g_park, "Công viên");
        add("sport", NATURE, R.drawable.ic_g_sports, "Thể thao");
        add("gym", NATURE, R.drawable.ic_g_fitness, "Phòng tập");
        add("attraction", CULTURE, R.drawable.ic_g_camera, "Điểm tham quan");
        add("museum", CULTURE, R.drawable.ic_g_museum, "Bảo tàng, di tích");
        add("cinema", CULTURE, R.drawable.ic_g_movie, "Rạp phim, nhà hát");
        add("worship", WORSHIP, R.drawable.ic_g_temple, "Chùa, nhà thờ");
        add("bank", FINANCE, R.drawable.ic_g_bank, "Ngân hàng");
        add("atm", FINANCE, R.drawable.ic_g_atm, "ATM");
        add("fuel", SERVICE, R.drawable.ic_g_gas, "Cây xăng");
        add("car", SERVICE, R.drawable.ic_g_car, "Dịch vụ xe");
        add("parking", SERVICE, R.drawable.ic_g_parking, "Bãi giữ xe");
        add("gov", SERVICE, R.drawable.ic_g_bank, "Cơ quan nhà nước");
        add("police", SERVICE, R.drawable.ic_g_police, "Công an");
        add("fire", SERVICE, R.drawable.ic_g_fire, "Phòng cháy chữa cháy");
        add("post", SERVICE, R.drawable.ic_g_post, "Bưu điện");
        add("laundry", SERVICE, R.drawable.ic_g_laundry, "Giặt ủi");
        add("toilets", SERVICE, R.drawable.ic_g_wc, "Nhà vệ sinh");
        add("pets", SERVICE, R.drawable.ic_g_pets, "Thú cưng, thú y");
        add("spa", BEAUTY, R.drawable.ic_g_spa, "Làm đẹp, spa");
        add("gate", SERVICE, R.drawable.ic_g_door, "Cổng, lối vào");
        add("road", GRAY, R.drawable.ic_g_road, "Đường");
        add("area", GRAY, R.drawable.ic_g_place, "Khu vực");
        add("address", GRAY, R.drawable.ic_g_place, "Địa chỉ");
        add("pin", PIN, R.drawable.ic_g_place, "Vị trí đã ghim");
        add("saved", SAVED, R.drawable.ic_g_star, "Đã lưu");
        add("place", GRAY, R.drawable.ic_g_place, null);
    }

    /** "class" trong dữ liệu bản đồ OpenMapTiles → loại. */
    private static final Map<String, String> TILE_CLASS = new LinkedHashMap<>();

    static {
        tile("restaurant", "restaurant");
        tile("fast_food", "fastfood");
        tile("food_court", "fastfood");
        tile("cafe", "cafe");
        tile("bar", "bar");
        tile("beer", "bar");
        tile("alcohol_shop", "bar");
        tile("ice_cream", "icecream");
        tile("bakery", "bakery");
        tile("shop", "shop");
        tile("clothing_store", "shop");
        tile("florist", "shop");
        tile("furniture", "shop");
        tile("jewelry", "shop");
        tile("mobile_phone", "shop");
        tile("music", "shop");
        tile("bicycle", "shop");
        tile("grocery", "grocery");
        tile("marketplace", "market");
        tile("hospital", "hospital");
        tile("doctors", "clinic");
        tile("dentist", "clinic");
        tile("clinic", "clinic");
        tile("pharmacy", "pharmacy");
        tile("veterinary", "pets");
        tile("bus", "bus");
        tile("rail", "rail");
        tile("railway", "rail");
        tile("aerialway", "rail");
        tile("entrance", "rail");
        tile("airport", "airport");
        tile("lodging", "lodging");
        tile("campsite", "lodging");
        tile("school", "school");
        tile("college", "university");
        tile("library", "library");
        tile("park", "park");
        tile("garden", "park");
        tile("playground", "park");
        tile("golf", "sport");
        tile("stadium", "sport");
        tile("swimming", "sport");
        tile("pitch", "sport");
        tile("sports", "sport");
        tile("fitness", "gym");
        tile("fitness_centre", "gym");
        tile("zoo", "attraction");
        tile("attraction", "attraction");
        tile("museum", "museum");
        tile("art_gallery", "museum");
        tile("castle", "museum");
        tile("monument", "museum");
        tile("cinema", "cinema");
        tile("theatre", "cinema");
        tile("place_of_worship", "worship");
        tile("cemetery", "worship");
        tile("bank", "bank");
        tile("atm", "atm");
        tile("fuel", "fuel");
        tile("charging_station", "fuel");
        tile("car", "car");
        tile("parking", "parking");
        tile("bicycle_parking", "parking");
        tile("town_hall", "gov");
        tile("police", "police");
        tile("fire_station", "fire");
        tile("post", "post");
        tile("laundry", "laundry");
        tile("toilets", "toilets");
        tile("hairdresser", "spa");
        tile("beauty", "spa");
        tile("pet", "pets");
    }

    private Poi() {}

    private static void add(String id, int color, int glyph, String label) {
        KINDS.put(id, new Kind(id, color, glyph, label));
    }

    private static void tile(String cls, String kind) {
        TILE_CLASS.put(cls, kind);
    }

    static Kind kind(String id) {
        Kind k = id == null ? null : KINDS.get(id);
        return k != null ? k : KINDS.get("place");
    }

    /** Loại của một icon trên bản đồ, theo "class" và "subclass" của nó. */
    static String fromTile(String cls, String subclass) {
        if (subclass != null) {
            switch (subclass) {
                case "mall":
                case "department_store":
                    return "mall";
                case "supermarket":
                case "convenience":
                    return "grocery";
                case "marketplace":
                    return "market";
                case "kindergarten":
                    return "school";
                case "university":
                    return "university";
                case "bus_station":
                case "bus_stop":
                    return "bus";
                default:
                    break;
            }
        }
        String k = cls == null ? null : TILE_CLASS.get(cls);
        return k != null ? k : "place";
    }

    /** Loại theo thẻ OpenStreetMap, ví dụ amenity=hospital (Photon: osm_key/osm_value). */
    static String fromOsm(String key, String value) {
        if (key == null) return "place";
        if (value == null) value = "";
        switch (key) {
            case "amenity":
                switch (value) {
                    case "restaurant": return "restaurant";
                    case "fast_food":
                    case "food_court": return "fastfood";
                    case "cafe": return "cafe";
                    case "bar":
                    case "pub":
                    case "biergarten":
                    case "nightclub": return "bar";
                    case "ice_cream": return "icecream";
                    case "hospital": return "hospital";
                    case "clinic":
                    case "doctors":
                    case "dentist": return "clinic";
                    case "pharmacy": return "pharmacy";
                    case "veterinary": return "pets";
                    case "bus_station": return "bus";
                    case "ferry_terminal": return "rail";
                    case "school":
                    case "kindergarten": return "school";
                    case "university":
                    case "college": return "university";
                    case "library": return "library";
                    case "place_of_worship":
                    case "monastery": return "worship";
                    case "bank": return "bank";
                    case "atm":
                    case "bureau_de_change": return "atm";
                    case "fuel":
                    case "charging_station": return "fuel";
                    case "parking":
                    case "motorcycle_parking":
                    case "bicycle_parking":
                    case "parking_entrance": return "parking";
                    case "car_wash":
                    case "car_rental":
                    case "motorcycle_rental": return "car";
                    case "townhall":
                    case "courthouse":
                    case "community_centre":
                    case "public_building":
                    case "embassy": return "gov";
                    case "police": return "police";
                    case "fire_station": return "fire";
                    case "post_office": return "post";
                    case "toilets": return "toilets";
                    case "cinema":
                    case "theatre":
                    case "arts_centre": return "cinema";
                    case "marketplace": return "market";
                    default: return "place";
                }
            case "shop":
                switch (value) {
                    case "mall":
                    case "department_store": return "mall";
                    case "supermarket":
                    case "convenience":
                    case "greengrocer": return "grocery";
                    case "bakery":
                    case "pastry":
                    case "confectionery": return "bakery";
                    case "car":
                    case "car_repair":
                    case "motorcycle":
                    case "motorcycle_repair":
                    case "tyres": return "car";
                    case "beauty":
                    case "hairdresser":
                    case "cosmetics":
                    case "massage": return "spa";
                    case "pet": return "pets";
                    case "laundry":
                    case "dry_cleaning": return "laundry";
                    default: return "shop";
                }
            case "tourism":
                switch (value) {
                    case "hotel":
                    case "motel":
                    case "guest_house":
                    case "hostel":
                    case "apartment":
                    case "chalet":
                    case "camp_site": return "lodging";
                    case "museum":
                    case "gallery": return "museum";
                    default: return "attraction";
                }
            case "leisure":
                switch (value) {
                    case "park":
                    case "garden":
                    case "playground":
                    case "nature_reserve":
                    case "dog_park": return "park";
                    case "fitness_centre":
                    case "fitness_station": return "gym";
                    default: return "sport";
                }
            case "highway":
                return "bus_stop".equals(value) ? "bus" : "road";
            case "railway":
                return "rail";
            case "public_transport":
                return "bus";
            case "aeroway":
                return "airport";
            case "historic":
                return "museum";
            case "healthcare":
                if ("pharmacy".equals(value)) return "pharmacy";
                return "hospital".equals(value) ? "hospital" : "clinic";
            case "office":
                return "government".equals(value) ? "gov" : "place";
            case "place":
            case "boundary":
            case "landuse":
                return "area";
            case "barrier":
            case "entrance":
                return "gate";
            default:
                return "place";
        }
    }

    // ---------- Vẽ icon ----------

    /** Icon tròn: nền màu theo loại, viền trắng, biểu tượng trắng ở giữa, có bóng mờ. */
    static Bitmap roundIcon(Context c, String kindId, float sizeDp) {
        Kind k = kind(kindId);
        float d = c.getResources().getDisplayMetrics().density;
        float margin = 2.5f * d;
        float r = sizeDp * d / 2f;
        int size = (int) Math.ceil(2 * (r + margin));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas cv = new Canvas(bmp);
        float cx = size / 2f;
        float cy = size / 2f - 0.5f * d;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setShadowLayer(1.6f * d, 0, 0.7f * d, 0x4D000000);
        cv.drawCircle(cx, cy, r, p);
        p.clearShadowLayer();
        p.setColor(k.color);
        cv.drawCircle(cx, cy, r - Math.max(1.5f * d, r * 0.13f), p);

        drawGlyph(c, cv, k.glyph, cx, cy, r * 1.15f);
        return bmp;
    }

    /** Ghim đỏ nơi đến; có biểu tượng loại địa điểm ở đầu ghim. */
    static Bitmap pin(Context c, String kindId) {
        float d = c.getResources().getDisplayMetrics().density;
        Drawable shape = c.getDrawable(R.drawable.ic_dest_pin);
        int w = shape.getIntrinsicWidth();
        int h = shape.getIntrinsicHeight();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas cv = new Canvas(bmp);
        shape.setBounds(0, 0, w, h);
        shape.draw(cv);

        // Đầu ghim là hình tròn tâm (w/2, w/2).
        float cx = w / 2f;
        float cy = w / 2f;
        Kind k = kind(kindId);
        if (kindId == null || "pin".equals(kindId) || "place".equals(k.id)) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFA50E0E);
            cv.drawCircle(cx, cy, 4.5f * d, p);
        } else {
            drawGlyph(c, cv, k.glyph, cx, cy, w * 0.5f);
        }
        return bmp;
    }

    private static void drawGlyph(Context c, Canvas cv, int glyph, float cx, float cy, float size) {
        Drawable g = c.getDrawable(glyph);
        if (g == null) return;
        g = g.mutate();
        g.setTint(Color.WHITE);
        int half = Math.round(size / 2f);
        g.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
        g.draw(cv);
    }

    // ---------- Kiểu bản đồ ----------

    /** Thêm icon tròn của mọi loại địa điểm vào bản đồ (tên "poi-<loại>"). */
    static void addImages(Context c, Style style) {
        HashMap<String, Bitmap> images = new HashMap<>();
        for (Kind k : KINDS.values()) images.put(imageName(k.id), roundIcon(c, k.id, 20));
        style.addImages(images);
    }

    /** Đổi icon địa điểm trên bản đồ thành hình tròn có màu theo loại, tên hiện bên cạnh giống Google Maps. */
    static void restyleLayers(Style style) {
        Expression icon = Expression.raw(iconExpression());
        Expression color = Expression.raw(textColorExpression());
        Expression name = Expression.raw("[\"coalesce\",[\"get\",\"name:vi\"],[\"get\",\"name\"]]");
        for (String id : MAP_LAYERS) {
            Layer layer = style.getLayer(id);
            if (!(layer instanceof SymbolLayer)) continue;
            layer.setProperties(
                    PropertyFactory.iconImage(icon),
                    PropertyFactory.iconSize(1f),
                    PropertyFactory.textField(name),
                    PropertyFactory.textFont(new String[]{"Noto Sans Regular"}),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor(color),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.4f),
                    PropertyFactory.textHaloBlur(0.3f),
                    PropertyFactory.textVariableAnchor(new String[]{"left", "right", "top"}),
                    PropertyFactory.textRadialOffset(1.25f),
                    PropertyFactory.textJustify("auto"),
                    PropertyFactory.textMaxWidth(8f));
        }
    }

    static String imageName(String kindId) {
        return "poi-" + kind(kindId).id;
    }

    /** ["match", ["get","class"], "restaurant", "poi-restaurant", ..., "poi-place"] */
    static String iconExpression() {
        StringBuilder b = new StringBuilder("[\"match\",[\"get\",\"class\"]");
        for (Map.Entry<String, String> e : TILE_CLASS.entrySet()) {
            b.append(",\"").append(e.getKey()).append("\",\"").append(imageName(e.getValue())).append('"');
        }
        return b.append(",\"").append(imageName("place")).append("\"]").toString();
    }

    static String textColorExpression() {
        StringBuilder b = new StringBuilder("[\"match\",[\"get\",\"class\"]");
        for (Map.Entry<String, String> e : TILE_CLASS.entrySet()) {
            b.append(",\"").append(e.getKey()).append("\",\"").append(hex(textColor(kind(e.getValue()).color))).append('"');
        }
        return b.append(",\"").append(hex(textColor(GRAY))).append("\"]").toString();
    }

    /** Màu chữ đậm hơn màu icon một chút để dễ đọc trên nền bản đồ. */
    static int textColor(int color) {
        return Color.rgb(Math.round(Color.red(color) * 0.8f), Math.round(Color.green(color) * 0.8f),
                Math.round(Color.blue(color) * 0.8f));
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }
}
