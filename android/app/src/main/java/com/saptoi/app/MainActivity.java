package com.saptoi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
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
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
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
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.FillLayer;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TrackingService.Listener {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final int RADIUS_STEP = 100;
    private static final int REQ_RINGTONE = 3;
    private static final long SUGGEST_DELAY_MS = 250;
    private static final int GOOGLE_BLUE = Color.rgb(26, 115, 232);

    /**
     * Bản đồ OpenFreeMap: miễn phí, không cần mã đăng ký. Bản đồ dạng vẽ (vector) nên luôn nét
     * ở mọi mức phóng to. Dữ liệu là OpenStreetMap.
     */
    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private static final String SRC_CIRCLE = "dest-circle";
    private static final String SRC_PIN = "dest-pin";
    private static final String IMG_PIN = "dest-pin-img";

    private MapView mapView;
    /** null cho tới khi bản đồ tải xong. */
    private MapLibreMap map;
    private Style style;

    // Màn hình bản đồ
    private TextView txtSearchBar;
    private ImageButton btnClearDest;
    private LinearLayout mapChips;
    private TextView txtDestName;
    private TextView txtDestAddr;
    private TextView txtRadius;
    private TextView txtAlertMode;
    private TextView txtDistance;
    private TextView txtEta;
    private Button btnStart;
    private Button btnSave;

    // Trang tìm kiếm
    private View searchPage;
    private EditText edtSearch;
    private ImageButton btnClearText;
    private LinearLayout searchChips;
    private TextView txtListTitle;
    private final ResultsAdapter adapter = new ResultsAdapter();

    private Place dest;
    private boolean running;
    /** Đang xin quyền để bấm "Bắt đầu" (chứ không phải để xem vị trí của tôi). */
    private boolean startAfterPermission;
    private boolean askedNotifications;
    /** Tăng mỗi lần gõ, để bỏ qua kết quả của lần gõ cũ trả về muộn. */
    private int searchSeq;

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
        drawBehindStatusBar();

        mapView = findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        txtSearchBar = findViewById(R.id.txtSearchBar);
        btnClearDest = findViewById(R.id.btnClearDest);
        mapChips = findViewById(R.id.mapChips);
        txtDestName = findViewById(R.id.txtDestName);
        txtDestAddr = findViewById(R.id.txtDestAddr);
        txtRadius = findViewById(R.id.txtRadius);
        txtAlertMode = findViewById(R.id.txtAlertMode);
        txtDistance = findViewById(R.id.txtDistance);
        txtEta = findViewById(R.id.txtEta);
        btnStart = findViewById(R.id.btnStart);
        btnSave = findViewById(R.id.btnSave);
        searchPage = findViewById(R.id.searchPage);
        edtSearch = findViewById(R.id.edtSearch);
        btnClearText = findViewById(R.id.btnClearText);
        searchChips = findViewById(R.id.searchChips);
        txtListTitle = findViewById(R.id.txtListTitle);
        SeekBar seekRadius = findViewById(R.id.seekRadius);

        setupMap();
        setupSearch();

        int radius = Prefs.getRadius(this);
        seekRadius.setProgress(Math.max(0, radius / RADIUS_STEP - 1));
        txtRadius.setText(Fmt.distance(radius));
        seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int r = (progress + 1) * RADIUS_STEP;
                txtRadius.setText(Fmt.distance(r));
                Prefs.setRadius(MainActivity.this, r);
                updateDestOnMap();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });

        btnStart.setOnClickListener(v -> {
            if (running) {
                TrackingService.stop(this);
            } else {
                startAfterPermission = true;
                startTracking();
            }
        });
        btnSave.setOnClickListener(v -> saveCurrent());
        findViewById(R.id.alertRow).setOnClickListener(v -> chooseAlertMode());
        showAlertMode();
        findViewById(R.id.btnMyLocation).setOnClickListener(v -> goToMyLocation());
        btnClearDest.setOnClickListener(v -> {
            if (running) toast(R.string.stop_first);
            else clearDest();
        });

        Place saved = Prefs.getDest(this);
        if (saved != null) setDest(saved);
        else showDest();
        renderSaved();

        if (!hasLocationPermission()) {
            startAfterPermission = false;
            requestLocationPermission();
        }
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

        View topBar = findViewById(R.id.topBar);
        int topPad = topBar.getPaddingTop();
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            topBar.setPadding(0, top + topPad, 0, 0);
            // Chừa chỗ cho thanh trạng thái ở trên và bàn phím ở dưới.
            searchPage.setPadding(0, top, 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
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
                zoom = 15;
            } else if (last != null) {
                start = new LatLng(last.getLatitude(), last.getLongitude());
                zoom = 15;
            }
            m.setCameraPosition(new CameraPosition.Builder().target(start).zoom(zoom).build());

            m.addOnMapClickListener(p -> {
                if (running) toast(R.string.stop_first);
                else setDest(new Place(null, p.getLatitude(), p.getLongitude()));
                return true;
            });

            m.setStyle(new Style.Builder().fromUri(STYLE_URL)
                    .withImage(IMG_PIN, drawableToBitmap(getDrawable(R.drawable.ic_dest_pin))), st -> {
                style = st;
                // Vòng tròn vùng báo (nằm dưới) và ghim nơi đến (nằm trên).
                st.addSource(new GeoJsonSource(SRC_CIRCLE));
                st.addSource(new GeoJsonSource(SRC_PIN));
                st.addLayer(new FillLayer("dest-circle-fill", SRC_CIRCLE).withProperties(
                        PropertyFactory.fillColor(GOOGLE_BLUE),
                        PropertyFactory.fillOpacity(0.15f)));
                st.addLayer(new LineLayer("dest-circle-line", SRC_CIRCLE).withProperties(
                        PropertyFactory.lineColor(GOOGLE_BLUE),
                        PropertyFactory.lineWidth(2f)));
                st.addLayer(new SymbolLayer("dest-pin-layer", SRC_PIN).withProperties(
                        PropertyFactory.iconImage(IMG_PIN),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)));
                updateDestOnMap();
                if (hasLocationPermission()) enableMyLocation();
            });
        });
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

    private void moveCamera(double lat, double lng, double zoom) {
        if (map == null) return;
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom), 600);
    }

    private static Bitmap drawableToBitmap(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return bmp;
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

    private void setDest(Place p) {
        dest = p;
        Prefs.setDest(this, p);
        updateDestOnMap();
        showDest();
        if (p.name == null) reverseGeocode(p);
    }

    private void clearDest() {
        dest = null;
        Prefs.setDest(this, null);
        updateDestOnMap();
        showDest();
    }

    /** Vẽ lại ghim và vòng tròn vùng báo theo nơi đến và bán kính hiện tại. */
    private void updateDestOnMap() {
        if (style == null) return;
        GeoJsonSource circle = style.getSourceAs(SRC_CIRCLE);
        GeoJsonSource pin = style.getSourceAs(SRC_PIN);
        if (circle == null || pin == null) return;
        if (dest == null) {
            FeatureCollection empty = FeatureCollection.fromFeatures(new ArrayList<>());
            circle.setGeoJson(empty);
            pin.setGeoJson(empty);
            return;
        }
        pin.setGeoJson(Feature.fromGeometry(Point.fromLngLat(dest.lng, dest.lat)));
        circle.setGeoJson(Feature.fromGeometry(circlePolygon(dest.lat, dest.lng, Prefs.getRadius(this))));
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

    private void showDest() {
        if (dest == null) {
            txtSearchBar.setText(R.string.search_hint);
            txtSearchBar.setTextColor(getColor(R.color.text_light));
            btnClearDest.setVisibility(View.GONE);
            txtDestName.setText(R.string.pick_dest_title);
            txtDestAddr.setText(R.string.no_dest);
        } else {
            txtSearchBar.setText(dest.label());
            txtSearchBar.setTextColor(getColor(R.color.text));
            btnClearDest.setVisibility(View.VISIBLE);
            txtDestName.setText(dest.label());
            txtDestAddr.setText(dest.address != null ? dest.address
                    : String.format(java.util.Locale.US, "%.5f, %.5f", dest.lat, dest.lng));
        }
        btnStart.setEnabled(running || dest != null);
        btnSave.setEnabled(dest != null);
    }

    private void reverseGeocode(Place p) {
        io.execute(() -> {
            try {
                Place named = Geo.reverse(p.lat, p.lng);
                if (named == null) return;
                ui.post(() -> {
                    // Chỉ cập nhật nếu người dùng chưa chọn nơi khác.
                    if (dest != null && dest.samePlace(p) && dest.name == null) {
                        dest = named;
                        Prefs.setDest(this, dest);
                        showDest();
                    }
                });
            } catch (Exception ignored) {
                // Giữ tên dạng toạ độ.
            }
        });
    }

    // ---------- Tìm kiếm (giống Google Maps) ----------

    private void setupSearch() {
        ListView list = findViewById(R.id.listResults);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> pickPlace(adapter.getItem(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            if (adapter.getIcon(position) != R.drawable.ic_history) return false;
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
                boolean empty = s.toString().trim().isEmpty();
                btnClearText.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (empty) {
                    showHistory();
                } else {
                    // Hiện ngay chỗ đã lưu / đã tìm khớp với chữ đang gõ, rồi mới hỏi mạng.
                    showResults(s.toString().trim(), null, false, true);
                    if (s.toString().trim().length() >= 2) ui.postDelayed(suggestTask, SUGGEST_DELAY_MS);
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
        searchPage.setVisibility(View.VISIBLE);
        edtSearch.setText("");
        showHistory();
        edtSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) edtSearch.post(() -> imm.showSoftInput(edtSearch, InputMethodManager.SHOW_IMPLICIT));
    }

    private void closeSearch() {
        ui.removeCallbacks(suggestTask);
        searchSeq++;
        hideKeyboard();
        searchPage.setVisibility(View.GONE);
    }

    private void showHistory() {
        List<Place> history = Prefs.getHistory(this);
        txtListTitle.setText(history.isEmpty() ? R.string.no_recent : R.string.recent);
        adapter.clear();
        for (Place p : history) adapter.add(p, R.drawable.ic_history);
        adapter.notifyDataSetChanged();
    }

    /** submitted = người dùng bấm nút tìm trên bàn phím (khi đó được dùng thêm Nominatim). */
    private void runSearch(boolean submitted) {
        String q = edtSearch.getText().toString().trim();
        if (q.isEmpty()) return;
        final int seq = ++searchSeq;
        String key = normalize(q);
        List<Place> cached;
        synchronized (searchCache) {
            cached = searchCache.get(key);
        }
        if (cached != null && !(submitted && cached.isEmpty())) {
            showResults(q, cached, false, false);
            return;
        }
        showResults(q, null, false, true);

        Location me = lastKnownLocation();
        double lat = me == null ? Double.NaN : me.getLatitude();
        double lng = me == null ? Double.NaN : me.getLongitude();
        io.execute(() -> {
            List<Place> results;
            boolean failed = false;
            try {
                results = Geo.suggest(q, lat, lng);
            } catch (Exception e) {
                results = new ArrayList<>();
                failed = true;
            }
            if (submitted && results.isEmpty()) {
                try {
                    results = Geo.search(q);
                    failed = false;
                } catch (Exception e) {
                    failed = true;
                }
            }
            if (!failed) {
                synchronized (searchCache) {
                    searchCache.put(key, results);
                }
            }
            final List<Place> r = results;
            final boolean f = failed;
            ui.post(() -> {
                if (seq != searchSeq || searchPage.getVisibility() != View.VISIBLE) return;
                showResults(q, r, f, false);
            });
        });
    }

    /**
     * Hiện danh sách: chỗ đã lưu và đã tìm khớp với chữ đang gõ (hiện ngay), sau đó là kết quả từ mạng.
     * online = null nghĩa là chưa có kết quả mạng; loading = đang chờ mạng.
     */
    private void showResults(String query, List<Place> online, boolean failed, boolean loading) {
        String key = normalize(query);
        adapter.clear();
        for (Place p : Prefs.getSaved(this)) {
            if (matches(p, key)) adapter.add(p, R.drawable.ic_star);
        }
        for (Place p : Prefs.getHistory(this)) {
            if (matches(p, key) && !adapter.contains(p)) adapter.add(p, R.drawable.ic_history);
        }
        if (online != null) {
            for (Place p : online) {
                if (!adapter.contains(p)) adapter.add(p, R.drawable.ic_place);
            }
        }
        adapter.notifyDataSetChanged();

        if (loading) txtListTitle.setText(R.string.searching);
        else if (!adapter.isEmpty()) txtListTitle.setText(R.string.results);
        else if (failed) txtListTitle.setText(R.string.network_error);
        else txtListTitle.setText(R.string.search_empty);
    }

    private static boolean matches(Place p, String key) {
        return normalize(p.label()).contains(key)
                || (p.address != null && normalize(p.address).contains(key));
    }

    /** Bỏ dấu và viết thường, để gõ "benh vien" vẫn khớp "Bệnh viện". */
    private static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').trim();
    }

    private void pickPlace(Place p) {
        Prefs.addHistory(this, p);
        closeSearch();
        setDest(p);
        moveCamera(p.lat, p.lng, 16);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (searchPage.getVisibility() == View.VISIBLE) closeSearch();
        else super.onBackPressed();
    }

    /** Danh sách kết quả / gần đây: biểu tượng tròn, tên, địa chỉ. */
    private final class ResultsAdapter extends BaseAdapter {
        private final List<Place> items = new ArrayList<>();
        private final List<Integer> icons = new ArrayList<>();

        void clear() {
            items.clear();
            icons.clear();
        }

        void add(Place p, int icon) {
            items.add(p);
            icons.add(icon);
        }

        boolean contains(Place p) {
            for (Place i : items) if (i.samePlace(p)) return true;
            return false;
        }

        int getIcon(int position) {
            return icons.get(position);
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
            ((ImageView) view.findViewById(R.id.rowIcon)).setImageResource(icons.get(position));
            ((TextView) view.findViewById(R.id.rowTitle)).setText(p.label());
            TextView sub = view.findViewById(R.id.rowSubtitle);
            sub.setText(p.address == null ? "" : p.address);
            sub.setVisibility(p.address == null ? View.GONE : View.VISIBLE);
            return view;
        }
    }

    // ---------- Địa điểm đã lưu ----------

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
    }

    /** Nút tròn dài có ngôi sao vàng, giống các nút "Nhà riêng", "Nhà hàng" của Google Maps. */
    private View makeChip(Place p, int index, boolean onMap) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(onMap ? R.drawable.bg_map_chip : R.drawable.bg_chip);
        if (onMap) chip.setElevation(dp(3));
        chip.setPadding(dp(12), dp(8), dp(16), dp(8));

        ImageView star = new ImageView(this);
        star.setImageResource(R.drawable.ic_star);
        chip.addView(star, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView name = new TextView(this);
        name.setText(p.label());
        name.setMaxWidth(dp(160));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(15);
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
            if (searchPage.getVisibility() == View.VISIBLE) closeSearch();
            setDest(p);
            moveCamera(p.lat, p.lng, 16);
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

    private void saveCurrent() {
        if (dest == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(dest.label());
        input.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_title)
                .setView(wrap)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    List<Place> list = Prefs.getSaved(this);
                    list.add(0, new Place(name, dest.lat, dest.lng, dest.address));
                    Prefs.setSaved(this, list);
                    renderSaved();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- Kiểu báo: chuông + rung, hoặc chỉ rung ----------

    private void showAlertMode() {
        if (Prefs.vibrateOnly(this)) {
            txtAlertMode.setText(R.string.alert_vibrate_only);
        } else {
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
        running = s.running;
        if (running) {
            btnStart.setText(R.string.stop);
            btnStart.setBackgroundResource(R.drawable.bg_btn_stop);
        } else {
            btnStart.setText(R.string.start);
            btnStart.setBackgroundResource(R.drawable.bg_btn_primary);
        }
        btnStart.setEnabled(running || dest != null);
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
    }

    @Override
    protected void onStop() {
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
