package com.saptoi.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.Polygon;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements TrackingService.Listener {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final int REQ_RINGTONE = 3;
    private static final long SUGGEST_DELAY_MS = 250;
    private static final int GOOGLE_BLUE = Color.rgb(26, 115, 232);
    private static final float FILL_OPACITY = 0.14f;

    /**
     * Bản đồ OpenFreeMap: miễn phí, không cần mã đăng ký. Bản đồ dạng vẽ (vector) nên luôn nét
     * ở mọi mức phóng to. Dữ liệu là OpenStreetMap.
     */
    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private static final String SRC_CIRCLE = "dest-circle";
    private static final String SRC_PIN = "dest-pin";
    private static final String SRC_SAVED = "saved-places";
    private static final String LAYER_FILL = "dest-circle-fill";
    private static final String LAYER_LINE = "dest-circle-line";
    private static final String LAYER_PIN = "dest-pin-layer";
    private static final String LAYER_SAVED = "saved-layer";

    /** Các loại địa điểm rộng, thường có nhiều cổng (trung tâm thương mại, bệnh viện, đại học...). */
    private static final Set<String> BIG_KINDS = new HashSet<>(Arrays.asList(
            "mall", "market", "hospital", "university", "park", "attraction", "museum", "rail",
            "airport", "sport"));
    /** Các loại nhỏ hơn nhưng vẫn hay có cổng; chỉ tìm cổng ở sát bên. */
    private static final Set<String> SMALL_GATE_KINDS = new HashSet<>(Arrays.asList(
            "grocery", "school", "gov", "worship", "lodging", "place"));

    private static final int T_SAVED = 0;
    private static final int T_HISTORY = 1;
    private static final int T_RESULT = 2;

    private MapView mapView;
    /** null cho tới khi bản đồ tải xong. */
    private MapLibreMap map;
    private Style style;

    private View root;
    private View topBar;
    private SheetLayout sheet;
    private ImageButton fab;
    private TextView txtAttribution;

    // Ô tìm kiếm trên bản đồ
    private TextView txtSearchBar;
    private ImageButton btnClearDest;
    private LinearLayout mapChips;

    // Bảng dưới
    private View destIconBg;
    private ImageView destIcon;
    private TextView txtDestName;
    private TextView txtDestMeta;
    private TextView txtDestAddr;
    private View gatesBox;
    private LinearLayout gatesRow;
    private View statsRow;
    private TextView txtDistance;
    private TextView txtEta;
    private View btnStart;
    private ImageView icStart;
    private TextView txtStart;
    private View btnSave;
    private ImageView icSave;
    private TextView txtSave;
    private View btnShare;
    private TextView txtRadius;
    private SeekBar seekRadius;
    private ImageView icAlert;
    private TextView txtAlertMode;

    // Trang tìm kiếm
    private View searchPage;
    private EditText edtSearch;
    private ImageButton btnClearText;
    private LinearLayout searchChips;
    private TextView txtListTitle;
    private ListView listResults;
    private final ResultsAdapter adapter = new ResultsAdapter();
    private boolean searchOpen;

    private Place dest;
    private boolean running;
    private boolean started;
    /** Đang xin quyền để bấm "Bắt đầu" (chứ không phải để xem vị trí của tôi). */
    private boolean startAfterPermission;
    private boolean askedNotifications;
    /** Tăng mỗi lần gõ, để bỏ qua kết quả của lần gõ cũ trả về muộn. */
    private int searchSeq;
    private int gateSeq;

    /** Địa điểm đang hiện danh sách cổng, và các cổng của nó. */
    private Place gatesParent;
    private final List<Place> gates = new ArrayList<>();

    private ValueAnimator pinAnim;
    private ValueAnimator circleAnim;
    private ValueAnimator pulseAnim;

    /** Nhiều luồng để lần gõ mới không phải chờ lần gõ cũ tìm xong. */
    private final ExecutorService io = Executors.newCachedThreadPool();
    /** Nhớ kết quả đã tìm, để xoá bớt chữ hay gõ lại thì hiện ngay. */
    private final Map<String, List<Place>> searchCache = new LinkedHashMap<String, List<Place>>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Place>> e) {
            return size() > 50;
        }
    };
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable suggestTask = () -> runSearch(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Phải gọi trước khi tạo giao diện có bản đồ.
        MapLibre.getInstance(this);

        setContentView(R.layout.activity_main);
        bindViews();
        drawBehindStatusBar();
        mapView.onCreate(savedInstanceState);

        setupMap();
        setupSearch();
        setupSheet();
        setupRadius();

        btnStart.setOnClickListener(v -> {
            if (running) {
                TrackingService.stop(this);
            } else {
                startAfterPermission = true;
                startTracking();
            }
        });
        btnSave.setOnClickListener(v -> onSaveClicked());
        btnShare.setOnClickListener(v -> shareDest());
        findViewById(R.id.alertRow).setOnClickListener(v -> chooseAlertMode());
        showAlertMode();
        fab.setOnClickListener(v -> goToMyLocation());
        btnClearDest.setOnClickListener(v -> {
            if (running) toast(R.string.stop_first);
            else clearDest();
        });

        Place saved = Prefs.getDest(this);
        if (saved != null) setDest(saved, false);
        else showDest();
        renderSaved();

        if (!hasLocationPermission()) {
            startAfterPermission = false;
            requestLocationPermission();
        }
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        mapView = findViewById(R.id.map);
        topBar = findViewById(R.id.topBar);
        sheet = findViewById(R.id.sheet);
        fab = findViewById(R.id.btnMyLocation);
        txtAttribution = findViewById(R.id.txtAttribution);
        txtSearchBar = findViewById(R.id.txtSearchBar);
        btnClearDest = findViewById(R.id.btnClearDest);
        mapChips = findViewById(R.id.mapChips);
        destIconBg = findViewById(R.id.destIconBg);
        destIcon = findViewById(R.id.destIcon);
        txtDestName = findViewById(R.id.txtDestName);
        txtDestMeta = findViewById(R.id.txtDestMeta);
        txtDestAddr = findViewById(R.id.txtDestAddr);
        gatesBox = findViewById(R.id.gatesBox);
        gatesRow = findViewById(R.id.gatesRow);
        statsRow = findViewById(R.id.statsRow);
        txtDistance = findViewById(R.id.txtDistance);
        txtEta = findViewById(R.id.txtEta);
        btnStart = findViewById(R.id.btnStart);
        icStart = findViewById(R.id.icStart);
        txtStart = findViewById(R.id.txtStart);
        btnSave = findViewById(R.id.btnSave);
        icSave = findViewById(R.id.icSave);
        txtSave = findViewById(R.id.txtSave);
        btnShare = findViewById(R.id.btnShare);
        txtRadius = findViewById(R.id.txtRadius);
        seekRadius = findViewById(R.id.seekRadius);
        icAlert = findViewById(R.id.icAlert);
        txtAlertMode = findViewById(R.id.txtAlertMode);
        searchPage = findViewById(R.id.searchPage);
        edtSearch = findViewById(R.id.edtSearch);
        btnClearText = findViewById(R.id.btnClearText);
        searchChips = findViewById(R.id.searchChips);
        txtListTitle = findViewById(R.id.txtListTitle);
        listResults = findViewById(R.id.listResults);
    }

    /** Cho bản đồ tràn lên dưới thanh trạng thái (giờ, pin) giống Google Maps. */
    @SuppressWarnings("deprecation")
    private void drawBehindStatusBar() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else w.setNavigationBarColor(Color.BLACK); // Android 8.0 chưa có nút điều hướng màu tối
        w.getDecorView().setSystemUiVisibility(flags);

        int topPad = topBar.getPaddingTop();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            topBar.setPadding(0, top + topPad, 0, 0);
            // Chừa chỗ cho thanh trạng thái ở trên và bàn phím ở dưới.
            searchPage.setPadding(0, top, 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    // ---------- Bảng dưới kéo được ----------

    private void setupSheet() {
        sheet.setPeekAnchor(findViewById(R.id.actionsRow), dp(16));
        sheet.setCallback(this::onSheetMoved);
    }

    /** Nút "vị trí của tôi" và dòng ghi nguồn bản đồ luôn nằm ngay trên bảng, giống Google Maps. */
    private void onSheetMoved(SheetLayout s) {
        float lift = s.visibleHeight();
        fab.setTranslationY(-lift);
        txtAttribution.setTranslationY(-lift);
        // Bảng kéo lên quá cao thì ẩn dần nút đi cho gọn.
        float free = root.getHeight() - lift - topBar.getBottom();
        float a = Math.max(0f, Math.min(1f, (free - dp(80)) / (float) dp(60)));
        fab.setAlpha(a);
        txtAttribution.setAlpha(a);
        int vis = a < 0.05f ? View.INVISIBLE : View.VISIBLE;
        if (fab.getVisibility() != vis) fab.setVisibility(vis);
    }

    /** Làm mượt khi bảng đổi chiều cao (hiện / ẩn một phần). */
    private void animateSheetChange() {
        if (!(root instanceof ViewGroup)) return;
        AutoTransition t = new AutoTransition();
        t.setDuration(220);
        t.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        t.excludeTarget(mapView, true);
        t.excludeChildren(mapView, true);
        t.excludeTarget(searchPage, true);
        t.excludeChildren(searchPage, true);
        t.excludeTarget(topBar, true);
        t.excludeChildren(topBar, true);
        TransitionManager.beginDelayedTransition((ViewGroup) root, t);
    }

    // ---------- Bản đồ ----------

    private void setupMap() {
        mapView.getMapAsync(m -> {
            map = m;
            m.getUiSettings().setLogoEnabled(false);
            m.getUiSettings().setAttributionEnabled(false);
            m.getUiSettings().setTiltGesturesEnabled(false);
            m.getUiSettings().setCompassMargins(0, dp(150), dp(16), 0);

            // Mở app lần đầu: xem TP.HCM; nếu đã có nơi đến hoặc vị trí thì xem ở đó.
            LatLng start = new LatLng(10.7769, 106.7009);
            double zoom = 12;
            Location last = lastKnownLocation();
            if (dest != null) {
                start = new LatLng(dest.lat, dest.lng);
                zoom = 16;
            } else if (last != null) {
                start = new LatLng(last.getLatitude(), last.getLongitude());
                zoom = 15;
            }
            m.setCameraPosition(new CameraPosition.Builder().target(start).zoom(zoom).build());

            m.addOnMapClickListener(this::onMapTap);
            m.addOnMapLongClickListener(this::onMapLongPress);

            m.setStyle(new Style.Builder().fromUri(STYLE_URL), st -> {
                style = st;
                try {
                    Poi.addImages(this, st);
                } catch (RuntimeException ignored) {
                    // Không vẽ được icon tròn thì vẫn dùng icon gốc của bản đồ.
                }
                try {
                    Poi.restyleLayers(st);
                } catch (RuntimeException ignored) {
                }
                addOverlays(st);
                updateDestOnMap(false);
                updateSavedOnMap();
                if (hasLocationPermission()) enableMyLocation();
                updatePulse();
            });
        });
    }

    /** Vòng tròn vùng báo, các điểm đã lưu (ngôi sao), và ghim nơi đến. */
    private void addOverlays(Style st) {
        st.addSource(new GeoJsonSource(SRC_CIRCLE));
        st.addSource(new GeoJsonSource(SRC_SAVED));
        st.addSource(new GeoJsonSource(SRC_PIN));

        // Vòng tròn nằm dưới chữ và icon của bản đồ để không che tên đường.
        FillLayer fill = new FillLayer(LAYER_FILL, SRC_CIRCLE).withProperties(
                PropertyFactory.fillColor(GOOGLE_BLUE),
                PropertyFactory.fillOpacity(FILL_OPACITY));
        LineLayer line = new LineLayer(LAYER_LINE, SRC_CIRCLE).withProperties(
                PropertyFactory.lineColor(GOOGLE_BLUE),
                PropertyFactory.lineWidth(2f));
        String firstLabels = firstSymbolLayer(st);
        if (firstLabels != null) {
            st.addLayerBelow(fill, firstLabels);
            st.addLayerBelow(line, firstLabels);
        } else {
            st.addLayer(fill);
            st.addLayer(line);
        }

        st.addLayer(new SymbolLayer(LAYER_SAVED, SRC_SAVED).withProperties(
                PropertyFactory.iconImage(Poi.imageName("saved")),
                PropertyFactory.iconSize(1.15f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(new String[]{"Noto Sans Regular"}),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(getColor(R.color.amber_dark)),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.textVariableAnchor(new String[]{"left", "right", "top"}),
                PropertyFactory.textRadialOffset(1.35f),
                PropertyFactory.textJustify("auto"),
                PropertyFactory.textMaxWidth(8f),
                PropertyFactory.textOptional(true)));

        st.addLayer(new SymbolLayer(LAYER_PIN, SRC_PIN).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)));
    }

    private static String firstSymbolLayer(Style st) {
        for (Layer l : st.getLayers()) {
            if (l instanceof SymbolLayer) return l.getId();
        }
        return null;
    }

    /** Chạm bản đồ: vào điểm đã lưu hoặc icon địa điểm thì chọn đúng chỗ đó, còn không thì ghim tại chỗ chạm. */
    private boolean onMapTap(LatLng p) {
        if (map == null) return false;
        PointF pt = map.getProjection().toScreenLocation(p);
        Place hit = savedAt(pt);
        if (hit == null) hit = poiAt(pt);
        if (hit != null) {
            selectPlace(hit, Double.NaN);
        } else if (running) {
            toast(R.string.stop_first);
        } else {
            selectPlace(new Place(null, p.getLatitude(), p.getLongitude(), null, "pin"), Double.NaN);
        }
        return true;
    }

    /** Nhấn giữ bản đồ: thêm điểm đánh dấu tại đó. */
    private boolean onMapLongPress(LatLng p) {
        if (map == null) return false;
        mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Place poi = poiAt(map.getProjection().toScreenLocation(p));
        promptSaveMarker(poi != null ? poi : new Place(null, p.getLatitude(), p.getLongitude(), null, "pin"),
                R.string.marker_title);
        return true;
    }

    /** Icon địa điểm (bệnh viện, quán ăn...) gần chỗ chạm nhất, hoặc null. */
    private Place poiAt(PointF pt) {
        if (style == null) return null;
        float r = dp(18);
        List<Feature> features;
        try {
            features = map.queryRenderedFeatures(new RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r), Poi.MAP_LAYERS);
        } catch (RuntimeException e) {
            return null;
        }
        Place best = null;
        double bestDist = Double.MAX_VALUE;
        for (Feature f : features) {
            if (!(f.geometry() instanceof Point)) continue;
            String name = firstNonEmpty(prop(f, "name:vi"), prop(f, "name"), prop(f, "name_en"));
            if (name == null) continue;
            Point g = (Point) f.geometry();
            PointF s = map.getProjection().toScreenLocation(new LatLng(g.latitude(), g.longitude()));
            double d = Math.hypot(s.x - pt.x, s.y - pt.y);
            if (d < bestDist) {
                bestDist = d;
                best = new Place(name, g.latitude(), g.longitude(), null,
                        Poi.fromTile(prop(f, "class"), prop(f, "subclass")));
            }
        }
        return best;
    }

    /** Điểm đã lưu (ngôi sao) ở chỗ chạm, hoặc null. */
    private Place savedAt(PointF pt) {
        if (style == null) return null;
        float r = dp(16);
        try {
            List<Feature> features = map.queryRenderedFeatures(
                    new RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r), LAYER_SAVED);
            List<Place> saved = Prefs.getSaved(this);
            for (Feature f : features) {
                Number i = f.hasNonNullValueForProperty("i") ? f.getNumberProperty("i") : null;
                if (i != null && i.intValue() >= 0 && i.intValue() < saved.size()) return saved.get(i.intValue());
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static String prop(Feature f, String key) {
        try {
            return f.hasNonNullValueForProperty(key) ? f.getStringProperty(key) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return null;
    }

    /** Hiện chấm xanh vị trí của tôi (có hình quạt chỉ hướng điện thoại), giống Google Maps. */
    @SuppressWarnings("MissingPermission")
    private void enableMyLocation() {
        if (style == null || map == null) return;
        LocationComponent lc = map.getLocationComponent();
        try {
            if (!lc.isLocationComponentActivated()) {
                lc.activateLocationComponent(LocationComponentActivationOptions.builder(this, style)
                        .useDefaultLocationEngine(true).build());
            }
            lc.setLocationComponentEnabled(true);
            lc.setCameraMode(CameraMode.NONE);
            lc.setRenderMode(RenderMode.COMPASS);
        } catch (RuntimeException ignored) {
            // Chưa có quyền vị trí.
        }
    }

    /** Vị trí gần nhất: của bản đồ nếu có, không thì hỏi hệ thống. */
    @SuppressWarnings("MissingPermission")
    private Location lastKnownLocation() {
        try {
            if (map != null && map.getLocationComponent().isLocationComponentActivated()) {
                Location l = map.getLocationComponent().getLastKnownLocation();
                if (l != null) return l;
            }
            if (!hasLocationPermission()) return null;
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Di chuyển bản đồ tới một điểm, đặt điểm đó giữa phần bản đồ còn thấy (trừ ô tìm kiếm và bảng dưới). */
    private void moveCamera(double lat, double lng, double zoom) {
        if (map == null) return;
        CameraPosition.Builder b = new CameraPosition.Builder()
                .target(new LatLng(lat, lng))
                .padding(0, topBar.getHeight(), 0, sheet.visibleHeight());
        if (!Double.isNaN(zoom)) b.zoom(zoom);
        map.animateCamera(CameraUpdateFactory.newCameraPosition(b.build()), 700);
    }

    /** Chỉ dịch bản đồ nếu điểm đang bị ô tìm kiếm hoặc bảng dưới che. */
    private void ensureVisible(double lat, double lng) {
        if (map == null) return;
        PointF s = map.getProjection().toScreenLocation(new LatLng(lat, lng));
        float top = topBar.getBottom() + dp(40);
        float bottom = root.getHeight() - sheet.visibleHeight() - dp(24);
        if (s.y < top || s.y > bottom || s.x < dp(24) || s.x > root.getWidth() - dp(24)) {
            moveCamera(lat, lng, Double.NaN);
        }
    }

    private void goToMyLocation() {
        if (!hasLocationPermission()) {
            startAfterPermission = false;
            requestLocationPermission();
            return;
        }
        enableMyLocation();
        Location l = lastKnownLocation();
        if (l != null) moveCamera(l.getLatitude(), l.getLongitude(), 17);
        else toast(R.string.waiting_location);
    }

    // ---------- Nơi đến ----------

    /** zoom = NaN: giữ mức phóng hiện tại, chỉ dịch bản đồ nếu điểm bị che. */
    private void selectPlace(Place p, double zoom) {
        if (running) {
            toast(R.string.stop_first);
            return;
        }
        setDest(p, true);
        if (Double.isNaN(zoom)) ensureVisible(p.lat, p.lng);
        else moveCamera(p.lat, p.lng, zoom);
    }

    private void setDest(Place p, boolean animate) {
        dest = p;
        Prefs.setDest(this, p);
        updateDestOnMap(animate);
        showDest();
        if (p.name == null || (p.address == null && !"gate".equals(p.kind))) findAddress(p);
        loadGates(p);
    }

    private void clearDest() {
        dest = null;
        Prefs.setDest(this, null);
        gateSeq++;
        gatesParent = null;
        gates.clear();
        renderGates();
        updateDestOnMap(false);
        showDest();
    }

    /** Vẽ lại ghim và vòng tròn vùng báo theo nơi đến và bán kính hiện tại. */
    private void updateDestOnMap(boolean animate) {
        if (style == null) return;
        GeoJsonSource pin = style.getSourceAs(SRC_PIN);
        if (pin == null) return;
        if (pinAnim != null) pinAnim.cancel();
        if (circleAnim != null) circleAnim.cancel();
        if (dest == null) {
            pin.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<>()));
            setCircle(0);
            updatePulse();
            return;
        }
        Feature f = Feature.fromGeometry(Point.fromLngLat(dest.lng, dest.lat));
        f.addStringProperty("icon", pinImage(dest.kind));
        pin.setGeoJson(f);
        if (animate) {
            animatePin();
            animateCircle();
        } else {
            setPinScale(1f);
            setCircle(Prefs.getRadius(this));
        }
        updatePulse();
    }

    /** Ghim màu đỏ có biểu tượng loại địa điểm; mỗi loại vẽ một lần. */
    private String pinImage(String kind) {
        String id = "pin-" + (kind == null ? "pin" : Poi.kind(kind).id);
        if (style != null && style.getImage(id) == null) style.addImage(id, Poi.pin(this, kind));
        return id;
    }

    private void setCircle(double meters) {
        if (style == null) return;
        GeoJsonSource circle = style.getSourceAs(SRC_CIRCLE);
        if (circle == null) return;
        if (dest == null || meters <= 0) circle.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<>()));
        else circle.setGeoJson(Feature.fromGeometry(circlePolygon(dest.lat, dest.lng, meters)));
    }

    private void setPinScale(float scale) {
        Layer l = style == null ? null : style.getLayer(LAYER_PIN);
        if (l != null) {
            l.setProperties(PropertyFactory.iconSize(Math.max(0.01f, scale)),
                    PropertyFactory.iconOpacity(Math.max(0f, Math.min(1f, scale * 1.5f))));
        }
    }

    /** Ghim "bật" lên từ mũi ghim, giống khi chọn địa điểm trên Google Maps. */
    private void animatePin() {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(420);
        a.setInterpolator(new OvershootInterpolator(2.4f));
        a.addUpdateListener(v -> setPinScale((float) v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setPinScale(1f);
            }
        });
        pinAnim = a;
        a.start();
    }

    /** Vòng tròn vùng báo lan ra từ tâm. */
    private void animateCircle() {
        int r = Prefs.getRadius(this);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(520);
        a.setStartDelay(80);
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(v -> setCircle(r * (float) v.getAnimatedValue()));
        circleAnim = a;
        setCircle(0);
        a.start();
    }

    /** Khi đang theo dõi, vòng tròn nhấp nháy nhẹ để biết app đang chạy. */
    private void updatePulse() {
        boolean want = running && started && style != null && dest != null;
        if (want && pulseAnim == null) {
            Layer fill = style.getLayer(LAYER_FILL);
            Layer line = style.getLayer(LAYER_LINE);
            if (fill == null || line == null) return;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(1100);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.addUpdateListener(v -> {
                float t = (float) v.getAnimatedValue();
                fill.setProperties(PropertyFactory.fillOpacity(0.08f + 0.18f * t));
                line.setProperties(PropertyFactory.lineWidth(2f + 1.5f * t));
            });
            pulseAnim = a;
            a.start();
        } else if (!want && pulseAnim != null) {
            pulseAnim.cancel();
            pulseAnim = null;
            if (style != null) {
                Layer fill = style.getLayer(LAYER_FILL);
                Layer line = style.getLayer(LAYER_LINE);
                if (fill != null) fill.setProperties(PropertyFactory.fillOpacity(FILL_OPACITY));
                if (line != null) line.setProperties(PropertyFactory.lineWidth(2f));
            }
        }
    }

    /** Hình tròn bán kính meters (mét) quanh một điểm, vẽ bằng 64 đoạn thẳng. */
    private static Polygon circlePolygon(double lat, double lng, double meters) {
        List<Point> ring = new ArrayList<>();
        double dLat = meters / 111320.0;
        double dLng = meters / (111320.0 * Math.cos(Math.toRadians(lat)));
        for (int i = 0; i <= 64; i++) {
            double a = 2 * Math.PI * i / 64;
            ring.add(Point.fromLngLat(lng + dLng * Math.cos(a), lat + dLat * Math.sin(a)));
        }
        List<List<Point>> rings = new ArrayList<>();
        rings.add(ring);
        return Polygon.fromLngLats(rings);
    }

    /** Thu nhỏ bản đồ nếu vòng tròn vùng báo lớn hơn phần bản đồ đang thấy. */
    private void fitCircle() {
        if (map == null || dest == null) return;
        double r = Prefs.getRadius(this);
        double dLat = r / 111320.0;
        double dLng = r / (111320.0 * Math.cos(Math.toRadians(dest.lat)));
        LatLng ne = new LatLng(dest.lat + dLat, dest.lng + dLng);
        LatLng sw = new LatLng(dest.lat - dLat, dest.lng - dLng);
        PointF a = map.getProjection().toScreenLocation(ne);
        PointF b = map.getProjection().toScreenLocation(sw);
        int top = topBar.getBottom();
        int bottom = root.getHeight() - sheet.visibleHeight();
        boolean fits = a.y >= top && b.y <= bottom && b.x >= 0 && a.x <= root.getWidth();
        if (fits) return;
        LatLngBounds bounds = new LatLngBounds.Builder().include(ne).include(sw).build();
        int pad = dp(24);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, top + pad, pad,
                sheet.visibleHeight() + pad), 600);
    }

    private void showDest() {
        if (dest == null) {
            txtSearchBar.setText(R.string.search_hint);
            txtSearchBar.setTextColor(getColor(R.color.text_light));
            btnClearDest.setVisibility(View.GONE);
            destIconBg.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.bg_alt)));
            destIcon.setImageResource(R.drawable.ic_place);
            txtDestName.setText(R.string.pick_dest_title);
            txtDestMeta.setVisibility(View.GONE);
            txtDestAddr.setText(R.string.no_dest);
        } else {
            Poi.Kind k = Poi.kind(dest.kind);
            String title = dest.name != null ? dest.name : getString(R.string.finding_address);
            txtSearchBar.setText(title);
            txtSearchBar.setTextColor(getColor(R.color.text));
            btnClearDest.setVisibility(View.VISIBLE);
            destIconBg.setBackgroundTintList(ColorStateList.valueOf(k.color));
            destIcon.setImageResource(k.glyph);
            txtDestName.setText(title);
            txtDestAddr.setText(dest.address != null ? dest.address : dest.coords());

            SpannableStringBuilder meta = new SpannableStringBuilder();
            if (k.label != null) {
                meta.append(k.label);
                meta.setSpan(new ForegroundColorSpan(Poi.textColor(k.color)), 0, meta.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            Location me = lastKnownLocation();
            if (me != null) {
                float[] d = new float[1];
                Location.distanceBetween(me.getLatitude(), me.getLongitude(), dest.lat, dest.lng, d);
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(getString(R.string.away, Fmt.distance(d[0])));
            }
            txtDestMeta.setText(meta);
            txtDestMeta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);
        }
        updateButtons();
    }

    private void updateButtons() {
        setButtonEnabled(btnStart, running || dest != null);
        setButtonEnabled(btnSave, dest != null);
        setButtonEnabled(btnShare, dest != null);
        updateSaveButton();
    }

    private static void setButtonEnabled(View v, boolean on) {
        v.setEnabled(on);
        float alpha = on ? 1f : 0.4f;
        if (v.getAlpha() != alpha) v.animate().alpha(alpha).setDuration(150).start();
    }

    /** Tìm địa chỉ bằng chữ cho điểm vừa ghim (hoặc địa chỉ cho icon vừa chạm). */
    private void findAddress(Place p) {
        io.execute(() -> {
            Place named = null;
            try {
                named = Geo.reverse(p.lat, p.lng);
            } catch (Exception ignored) {
                // Mất mạng: giữ toạ độ.
            }
            Place result = named;
            ui.post(() -> {
                if (dest == null || !dest.samePlace(p)) return;
                if (dest.name == null) {
                    dest = result != null ? result : dest.withName(getString(R.string.dropped_pin));
                } else if (dest.address == null && result != null && result.address != null) {
                    dest = dest.withAddress(result.address);
                } else {
                    return;
                }
                Prefs.setDest(this, dest);
                showDest();
            });
        });
    }

    // ---------- Cổng / lối vào ----------

    private void loadGates(Place p) {
        if (gatesParent != null && ("gate".equals(p.kind) || gatesParent.samePlace(p))) {
            renderGates();
            return;
        }
        final int seq = ++gateSeq;
        gatesParent = null;
        gates.clear();
        renderGates();
        if (p.name == null || p.kind == null) return;
        int radius;
        if (BIG_KINDS.contains(p.kind)) radius = 300;
        else if (SMALL_GATE_KINDS.contains(p.kind)) radius = 120;
        else return;
        io.execute(() -> {
            List<Place> list;
            try {
                list = Geo.entrances(p, radius);
            } catch (Exception e) {
                return;
            }
            ui.post(() -> {
                if (seq != gateSeq || dest == null || !dest.samePlace(p)) return;
                gatesParent = p;
                gates.clear();
                gates.addAll(list);
                renderGates();
            });
        });
    }

    private void renderGates() {
        boolean show = dest != null && gatesParent != null && !gates.isEmpty();
        if (show) {
            gatesRow.removeAllViews();
            gatesRow.addView(makeGateChip(gatesParent, true));
            for (Place g : gates) gatesRow.addView(makeGateChip(g, false));
        }
        boolean visible = gatesBox.getVisibility() == View.VISIBLE;
        if (show != visible) {
            animateSheetChange();
            gatesBox.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private View makeGateChip(Place g, boolean parent) {
        boolean selected = dest != null && dest.samePlace(g);
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setPadding(dp(10), dp(7), dp(14), dp(7));
        chip.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.press));

        ImageView icon = new ImageView(this);
        icon.setImageResource(parent ? Poi.kind(g.kind).glyph : R.drawable.ic_g_door);
        icon.setImageTintList(ColorStateList.valueOf(getColor(selected ? R.color.primary : R.color.text_light)));
        chip.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView name = new TextView(this);
        name.setText(g.label());
        name.setMaxWidth(dp(170));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(getColor(selected ? R.color.on_primary_container : R.color.text));
        name.setTextSize(14);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.setMarginStart(dp(6));
        chip.addView(name, nlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            if (gatesParent == null) return;
            Place target = parent ? gatesParent
                    : new Place(g.label() + " – " + gatesParent.label(), g.lat, g.lng, gatesParent.address, "gate");
            selectPlace(target, Double.NaN);
        });
        return chip;
    }

    // ---------- Tìm kiếm (giống Google Maps) ----------

    private void setupSearch() {
        listResults.setAdapter(adapter);
        listResults.setOnItemClickListener((parent, view, position, id) -> pickPlace(adapter.getItem(position)));
        listResults.setOnItemLongClickListener((parent, view, position, id) -> {
            if (adapter.getType(position) != T_HISTORY) return false;
            Place p = adapter.getItem(position);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_saved, p.label()))
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        Prefs.removeHistory(this, p);
                        showHistory();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });

        findViewById(R.id.searchBar).setOnClickListener(v -> {
            if (running) toast(R.string.stop_first);
            else openSearch();
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> closeSearch());
        btnClearText.setOnClickListener(v -> edtSearch.setText(""));

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                ui.removeCallbacks(suggestTask);
                searchSeq++;
                String q = s.toString().trim();
                btnClearText.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (q.isEmpty()) {
                    showHistory();
                } else {
                    // Hiện ngay chỗ đã lưu / đã tìm khớp với chữ đang gõ, rồi mới hỏi mạng.
                    List<Place> cached = cached(fold(q));
                    showResults(q, cached, false, cached == null, false);
                    if (q.length() >= 2 && cached == null) ui.postDelayed(suggestTask, SUGGEST_DELAY_MS);
                }
            }
        });
        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                ui.removeCallbacks(suggestTask);
                runSearch(true);
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void openSearch() {
        searchOpen = true;
        searchPage.animate().cancel();
        searchPage.setVisibility(View.VISIBLE);
        searchPage.setAlpha(0f);
        searchPage.setTranslationY(dp(24));
        searchPage.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
        edtSearch.setText("");
        showHistory();
        listResults.scheduleLayoutAnimation();
        edtSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) edtSearch.post(() -> imm.showSoftInput(edtSearch, InputMethodManager.SHOW_IMPLICIT));
    }

    private void closeSearch() {
        searchOpen = false;
        ui.removeCallbacks(suggestTask);
        searchSeq++;
        hideKeyboard();
        searchPage.animate().cancel();
        searchPage.animate().alpha(0f).translationY(dp(16)).setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!searchOpen) searchPage.setVisibility(View.GONE);
                }).start();
    }

    private void showHistory() {
        List<Place> history = Prefs.getHistory(this);
        txtListTitle.setText(history.isEmpty() ? R.string.no_recent : R.string.recent);
        adapter.setQuery(new String[0], lastKnownLocation());
        adapter.clear();
        for (Place p : history) adapter.add(p, T_HISTORY);
        adapter.notifyDataSetChanged();
    }

    private List<Place> cached(String key) {
        synchronized (searchCache) {
            List<Place> full = searchCache.get(key + "|full");
            return full != null ? full : searchCache.get(key);
        }
    }

    /** Vị trí của bạn, hoặc giữa bản đồ nếu chưa có vị trí; để ưu tiên kết quả ở gần. */
    private double[] searchBias() {
        Location me = lastKnownLocation();
        if (me != null) return new double[]{me.getLatitude(), me.getLongitude()};
        if (map != null && map.getCameraPosition().target != null) {
            LatLng t = map.getCameraPosition().target;
            return new double[]{t.getLatitude(), t.getLongitude()};
        }
        return new double[]{Double.NaN, Double.NaN};
    }

    /** submitted = người dùng bấm nút tìm trên bàn phím (khi đó tìm thêm bằng Nominatim). */
    private void runSearch(boolean submitted) {
        String q = edtSearch.getText().toString().trim();
        if (q.isEmpty()) return;
        final int seq = ++searchSeq;
        String key = fold(q) + (submitted ? "|full" : "");
        List<Place> hit;
        synchronized (searchCache) {
            hit = searchCache.get(key);
        }
        if (hit != null) {
            showResults(q, hit, false, false, true);
            return;
        }
        showResults(q, cached(fold(q)), false, true, false);

        double[] bias = searchBias();
        Future<List<Place>> full = submitted ? io.submit(() -> Geo.search(q, bias[0], bias[1])) : null;
        io.execute(() -> {
            List<Place> results = new ArrayList<>();
            boolean failed = false;
            try {
                results.addAll(Geo.suggest(q, bias[0], bias[1]));
            } catch (Exception e) {
                failed = true;
            }
            if (full != null) {
                try {
                    merge(results, full.get(15, TimeUnit.SECONDS));
                    failed = false;
                } catch (Exception ignored) {
                    // Nominatim lỗi thì vẫn dùng kết quả của Photon.
                }
            }
            if (!failed) {
                synchronized (searchCache) {
                    searchCache.put(key, results);
                }
            }
            boolean f = failed;
            ui.post(() -> {
                if (seq != searchSeq || !searchOpen) return;
                showResults(q, results, f, false, true);
            });
        });
    }

    /** Thêm kết quả mới, bỏ những chỗ trùng (cùng tên và gần nhau, hoặc gần như cùng một điểm). */
    private static void merge(List<Place> into, List<Place> more) {
        for (Place m : more) {
            boolean dup = false;
            for (Place p : into) {
                float[] d = new float[1];
                Location.distanceBetween(p.lat, p.lng, m.lat, m.lng, d);
                if (d[0] < 30 || (d[0] < 400 && fold(p.label()).equals(fold(m.label())))) {
                    dup = true;
                    break;
                }
            }
            if (!dup) into.add(m);
        }
    }

    /**
     * Hiện danh sách: chỗ đã lưu và đã tìm khớp với chữ đang gõ (hiện ngay), sau đó là kết quả từ mạng.
     * online = null nghĩa là chưa có kết quả mạng; loading = đang chờ mạng.
     */
    private void showResults(String query, List<Place> online, boolean failed, boolean loading, boolean animate) {
        String[] words = words(query);
        adapter.setQuery(words, lastKnownLocation());
        adapter.clear();
        for (Place p : Prefs.getSaved(this)) {
            if (matches(p, words)) adapter.add(p, T_SAVED);
        }
        for (Place p : Prefs.getHistory(this)) {
            if (matches(p, words) && !adapter.contains(p)) adapter.add(p, T_HISTORY);
        }
        if (online != null) {
            for (Place p : online) {
                if (!adapter.contains(p)) adapter.add(p, T_RESULT);
            }
        }
        adapter.notifyDataSetChanged();
        if (animate && online != null && !online.isEmpty()) listResults.scheduleLayoutAnimation();

        if (loading) txtListTitle.setText(R.string.searching);
        else if (!adapter.isEmpty()) txtListTitle.setText(R.string.results);
        else if (failed) txtListTitle.setText(R.string.network_error);
        else txtListTitle.setText(R.string.search_empty);
    }

    private static String[] words(String query) {
        List<String> out = new ArrayList<>();
        for (String w : fold(query).split("\\s+")) if (!w.isEmpty()) out.add(w);
        return out.toArray(new String[0]);
    }

    private static boolean matches(Place p, String[] words) {
        String hay = fold(p.label() + " " + (p.address == null ? "" : p.address));
        for (String w : words) if (!hay.contains(w)) return false;
        return true;
    }

    /**
     * Bỏ dấu và viết thường, để gõ "benh vien" vẫn khớp "Bệnh viện".
     * Giữ nguyên độ dài chuỗi để tô đậm được đúng chỗ khớp trong tên gốc.
     */
    static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c == 'đ') {
                b.append('d');
                continue;
            }
            String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            b.append(d.isEmpty() ? c : d.charAt(0));
        }
        return b.toString();
    }

    /** Tô đậm phần chữ khớp với từ đang gõ. */
    private static CharSequence highlight(String text, String[] words) {
        if (words.length == 0) return text;
        SpannableString s = new SpannableString(text);
        String f = fold(text);
        for (String w : words) {
            int i = f.indexOf(w);
            if (i >= 0 && i + w.length() <= text.length()) {
                s.setSpan(new StyleSpan(Typeface.BOLD), i, i + w.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }

    private void pickPlace(Place p) {
        Prefs.addHistory(this, p);
        closeSearch();
        selectPlace(p, 17);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (searchOpen) closeSearch();
        else super.onBackPressed();
    }

    /** Danh sách kết quả / gần đây: icon tròn có màu theo loại, khoảng cách, tên, loại + địa chỉ. */
    private final class ResultsAdapter extends BaseAdapter {
        private final List<Place> items = new ArrayList<>();
        private final List<Integer> types = new ArrayList<>();
        private String[] words = new String[0];
        private Location me;

        void setQuery(String[] w, Location location) {
            words = w;
            me = location;
        }

        void clear() {
            items.clear();
            types.clear();
        }

        void add(Place p, int type) {
            items.add(p);
            types.add(type);
        }

        boolean contains(Place p) {
            for (Place i : items) if (i.samePlace(p)) return true;
            return false;
        }

        int getType(int position) {
            return types.get(position);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Place getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_place, parent, false);
            }
            Place p = items.get(position);
            int type = types.get(position);
            View iconBg = view.findViewById(R.id.rowIconBg);
            ImageView icon = view.findViewById(R.id.rowIcon);
            Poi.Kind k = Poi.kind(p.kind);
            if (type == T_SAVED) {
                iconBg.setBackgroundTintList(ColorStateList.valueOf(Poi.SAVED));
                icon.setImageResource(R.drawable.ic_g_star);
            } else if (type == T_HISTORY) {
                iconBg.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.bg_alt)));
                icon.setImageResource(R.drawable.ic_history);
            } else {
                iconBg.setBackgroundTintList(ColorStateList.valueOf(k.color));
                icon.setImageResource(k.glyph);
            }

            ((TextView) view.findViewById(R.id.rowTitle)).setText(highlight(p.label(), words));

            SpannableStringBuilder sub = new SpannableStringBuilder();
            if (k.label != null && !"pin".equals(k.id)) {
                sub.append(k.label);
                sub.setSpan(new ForegroundColorSpan(Poi.textColor(k.color)), 0, sub.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (p.address != null) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(p.address);
            }
            TextView subtitle = view.findViewById(R.id.rowSubtitle);
            subtitle.setText(sub);
            subtitle.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

            TextView dist = view.findViewById(R.id.rowDistance);
            if (me != null) {
                float[] d = new float[1];
                Location.distanceBetween(me.getLatitude(), me.getLongitude(), p.lat, p.lng, d);
                dist.setText(Fmt.distance(d[0]));
                dist.setVisibility(View.VISIBLE);
            } else {
                dist.setVisibility(View.GONE);
            }
            return view;
        }
    }

    // ---------- Địa điểm đã lưu / điểm đánh dấu ----------

    private void renderSaved() {
        List<Place> places = Prefs.getSaved(this);
        mapChips.removeAllViews();
        searchChips.removeAllViews();
        findViewById(R.id.mapChipsScroll).setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.searchChipsScroll).setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < places.size(); i++) {
            mapChips.addView(makeChip(places.get(i), i, true));
            searchChips.addView(makeChip(places.get(i), i, false));
        }
        updateSavedOnMap();
        updateSaveButton();
    }

    /** Các điểm đã lưu hiện trên bản đồ thành ngôi sao vàng kèm tên. */
    private void updateSavedOnMap() {
        if (style == null) return;
        GeoJsonSource src = style.getSourceAs(SRC_SAVED);
        if (src == null) return;
        List<Place> places = Prefs.getSaved(this);
        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            Feature f = Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat));
            f.addStringProperty("name", p.label());
            f.addNumberProperty("i", i);
            features.add(f);
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features));
    }

    /** Nút tròn dài có ngôi sao vàng, giống các nút "Nhà riêng", "Nhà hàng" của Google Maps. */
    private View makeChip(Place p, int index, boolean onMap) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(onMap ? R.drawable.bg_map_chip : R.drawable.bg_chip);
        if (onMap) chip.setElevation(dp(3));
        chip.setPadding(dp(12), dp(8), dp(16), dp(8));
        chip.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.press));

        ImageView star = new ImageView(this);
        star.setImageResource(R.drawable.ic_star);
        chip.addView(star, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView name = new TextView(this);
        name.setText(p.label());
        name.setMaxWidth(dp(160));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(14);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.setMarginStart(dp(8));
        chip.addView(name, nlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            if (running) {
                toast(R.string.stop_first);
                return;
            }
            if (searchOpen) closeSearch();
            selectPlace(p, 17);
        });
        chip.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_saved, p.label()))
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        List<Place> list = Prefs.getSaved(this);
                        if (index < list.size()) list.remove(index);
                        Prefs.setSaved(this, list);
                        renderSaved();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        return chip;
    }

    private int savedIndex(Place p) {
        if (p == null) return -1;
        List<Place> list = Prefs.getSaved(this);
        for (int i = 0; i < list.size(); i++) if (list.get(i).samePlace(p)) return i;
        return -1;
    }

    private void updateSaveButton() {
        boolean saved = savedIndex(dest) >= 0;
        if (saved) {
            icSave.setImageResource(R.drawable.ic_star);
            icSave.setImageTintList(null);
            txtSave.setText(R.string.saved);
        } else {
            icSave.setImageResource(R.drawable.ic_star_border);
            icSave.setImageTintList(ColorStateList.valueOf(getColor(R.color.primary)));
            txtSave.setText(R.string.save);
        }
    }

    private void onSaveClicked() {
        if (dest == null) return;
        int idx = savedIndex(dest);
        if (idx < 0) {
            promptSaveMarker(dest, R.string.save_title);
            return;
        }
        Place p = dest;
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.unsave, Prefs.getSaved(this).get(idx).label()))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    List<Place> list = Prefs.getSaved(this);
                    int i = savedIndex(p);
                    if (i >= 0) list.remove(i);
                    Prefs.setSaved(this, list);
                    renderSaved();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Hỏi tên rồi lưu một điểm (hiện thành ngôi sao trên bản đồ và nút nhanh dưới ô tìm kiếm). */
    private void promptSaveMarker(Place p, int titleRes) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.marker_hint);
        if (p.name != null) {
            input.setText(p.name);
            input.setSelectAllOnFocus(true);
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input);

        // Điểm chưa có tên / địa chỉ: tìm trong lúc người dùng gõ tên.
        final Place[] resolved = {p};
        if (p.name == null || p.address == null) {
            io.execute(() -> {
                try {
                    Place named = Geo.reverse(p.lat, p.lng);
                    if (named == null) return;
                    ui.post(() -> {
                        if (p.name == null) {
                            resolved[0] = named;
                            if (input.getText().length() == 0) {
                                input.setText(named.label());
                                input.selectAll();
                            }
                        } else {
                            resolved[0] = p.withAddress(named.address);
                        }
                    });
                } catch (Exception ignored) {
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(wrap)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    Place r = resolved[0];
                    if (name.isEmpty()) name = r.name != null ? r.name : getString(R.string.marker_default);
                    String kind = r.kind == null || "pin".equals(r.kind) ? "saved" : r.kind;
                    List<Place> list = Prefs.getSaved(this);
                    list.add(0, new Place(name, p.lat, p.lng, r.address, kind));
                    Prefs.setSaved(this, list);
                    renderSaved();
                    popView(icSave);
                    Toast.makeText(this, getString(R.string.marker_added, name), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    private void shareDest() {
        if (dest == null) return;
        String url = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f",
                dest.lat, dest.lng);
        String text = dest.label() + (dest.address != null ? "\n" + dest.address : "") + "\n" + url;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        } catch (RuntimeException ignored) {
        }
    }

    /** Nảy nhẹ một view để báo vừa có thay đổi. */
    private static void popView(View v) {
        v.animate().cancel();
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(new OvershootInterpolator(3f)).start();
    }

    // ---------- Bán kính báo: 10 m – 3 km, mỗi nấc 10 m ----------

    private void setupRadius() {
        int r = Prefs.getRadius(this);
        seekRadius.setMax((Prefs.RADIUS_MAX - Prefs.RADIUS_MIN) / Prefs.RADIUS_STEP);
        seekRadius.setProgress((r - Prefs.RADIUS_MIN) / Prefs.RADIUS_STEP);
        txtRadius.setText(Fmt.radius(r));
        seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int meters = Prefs.RADIUS_MIN + progress * Prefs.RADIUS_STEP;
                txtRadius.setText(Fmt.radius(meters));
                Prefs.setRadius(MainActivity.this, meters);
                if (circleAnim != null) circleAnim.cancel();
                setCircle(dest == null ? 0 : meters);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                fitCircle();
            }
        });
        setupStepButton(findViewById(R.id.btnMinus), -1);
        setupStepButton(findViewById(R.id.btnPlus), 1);
    }

    /** Bấm: đổi 10 m. Giữ: đổi liên tục. */
    @SuppressWarnings("ClickableViewAccessibility")
    private void setupStepButton(View b, int delta) {
        Runnable[] repeat = new Runnable[1];
        boolean[] repeating = {false};
        repeat[0] = () -> {
            seekRadius.setProgress(seekRadius.getProgress() + delta);
            ui.postDelayed(repeat[0], 60);
        };
        b.setOnClickListener(v -> {
            seekRadius.setProgress(seekRadius.getProgress() + delta);
            fitCircle();
        });
        b.setOnLongClickListener(v -> {
            repeating[0] = true;
            ui.post(repeat[0]);
            return true;
        });
        b.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && repeating[0]) {
                repeating[0] = false;
                ui.removeCallbacks(repeat[0]);
                fitCircle();
            }
            return false;
        });
    }

    // ---------- Kiểu báo: chuông + rung, hoặc chỉ rung ----------

    private void showAlertMode() {
        if (Prefs.vibrateOnly(this)) {
            icAlert.setImageResource(R.drawable.ic_vibration);
            txtAlertMode.setText(R.string.alert_vibrate_only);
        } else {
            icAlert.setImageResource(R.drawable.ic_bell);
            txtAlertMode.setText(getString(R.string.alert_sound_vibrate) + " · " + ringtoneTitle());
        }
    }

    private String ringtoneTitle() {
        Uri uri = Prefs.getRingtone(this);
        if (uri == null) return getString(R.string.alert_default_ringtone);
        try {
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            String t = r == null ? null : r.getTitle(this);
            return t == null ? getString(R.string.alert_default_ringtone) : t;
        } catch (RuntimeException e) {
            return getString(R.string.alert_default_ringtone);
        }
    }

    private void chooseAlertMode() {
        String[] items = {
                getString(R.string.alert_sound_vibrate),
                getString(R.string.alert_vibrate_only),
                getString(R.string.alert_pick_ringtone)};
        int checked = Prefs.vibrateOnly(this) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_dialog_title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    if (which == 0) Prefs.setVibrateOnly(this, false);
                    else if (which == 1) Prefs.setVibrateOnly(this, true);
                    else pickRingtone();
                    showAlertMode();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Mở danh sách nhạc chuông có sẵn của điện thoại (bấm vào để nghe thử). */
    private void pickRingtone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.alert_pick_ringtone))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        Uri current = Prefs.getRingtone(this);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                current != null ? current : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        try {
            startActivityForResult(i, REQ_RINGTONE);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_RINGTONE || resultCode != RESULT_OK || data == null) return;
        Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        // Chọn "Mặc định" thì lưu null để luôn theo chuông báo thức của máy.
        if (picked != null && picked.equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))) {
            picked = null;
        }
        Prefs.setRingtone(this, picked);
        Prefs.setVibrateOnly(this, false);
        showAlertMode();
    }

    // ---------- Bắt đầu / dừng theo dõi ----------

    private void startTracking() {
        if (dest == null) return;

        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            askedNotifications = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.location_off_title)
                    .setMessage(R.string.location_off_msg)
                    .setPositiveButton(R.string.open_settings, (d, w) ->
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        startAfterPermission = false;
        Intent i = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START);
        startForegroundService(i);
        askBatteryOptimizationOnce();
    }

    /** Xin phép để hệ thống không tự tắt app khi chạy nền (hay gặp trên Xiaomi, Oppo, Samsung...). */
    private void askBatteryOptimizationOnce() {
        if (Prefs.askedBattery(this)) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        Prefs.setAskedBattery(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.battery_title)
                .setMessage(R.string.battery_msg)
                .setPositiveButton(R.string.battery_ok, (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (RuntimeException ignored) {
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                enableMyLocation();
                if (startAfterPermission) startTracking();
            } else if (startAfterPermission) {
                toast(R.string.need_location);
            }
        } else if (requestCode == REQ_NOTIFICATIONS) {
            // Không cho hiện thông báo thì chuông và màn hình báo vẫn hoạt động.
            if (startAfterPermission) startTracking();
        }
    }

    @Override
    public void onStatus(TrackingService.Status s) {
        boolean changed = running != s.running;
        running = s.running;
        if (changed) {
            animateSheetChange();
            statsRow.setVisibility(running ? View.VISIBLE : View.GONE);
            if (running) {
                btnStart.setBackgroundResource(R.drawable.bg_btn_stop);
                icStart.setImageResource(R.drawable.ic_stop_circle);
                txtStart.setText(R.string.stop);
                // Thu gọn bảng để thấy bản đồ; vẫn thấy khoảng cách và nút dừng.
                sheet.collapse();
            } else {
                btnStart.setBackgroundResource(R.drawable.bg_btn_primary);
                icStart.setImageResource(R.drawable.ic_navigation);
                txtStart.setText(R.string.start);
            }
            popView(icStart);
            updatePulse();
        }
        setButtonEnabled(btnStart, running || dest != null);
        if (running && s.distance >= 0) {
            txtDistance.setText(Fmt.distance(s.distance));
            String eta = Fmt.duration(s.etaSeconds);
            txtEta.setText(eta == null ? getString(R.string.dash) : eta);
        } else {
            txtDistance.setText(R.string.dash);
            txtEta.setText(R.string.dash);
        }
    }

    // ---------- Vòng đời màn hình ----------

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        TrackingService.addListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        started = true;
        updatePulse();
    }

    @Override
    protected void onStop() {
        started = false;
        updatePulse();
        mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onPause() {
        TrackingService.removeListener(this);
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (pinAnim != null) pinAnim.cancel();
        if (circleAnim != null) circleAnim.cancel();
        if (pulseAnim != null) pulseAnim.cancel();
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
        mapView.onDestroy();
        super.onDestroy();
    }

    // ---------- Tiện ích ----------

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
        edtSearch.clearFocus();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
