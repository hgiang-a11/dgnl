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
import android.location.LocationManager;
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

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TrackingService.Listener {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final int RADIUS_STEP = 100;
    private static final long SUGGEST_DELAY_MS = 350;
    private static final int GOOGLE_BLUE = Color.rgb(26, 115, 232);

    /**
     * Bản đồ CARTO Voyager bản độ nét cao (ảnh 512px, "@2x"): chữ rõ, màu nhạt giống Google Maps.
     * Dữ liệu vẫn là OpenStreetMap.
     */
    private static final XYTileSource CARTO_VOYAGER = new XYTileSource("CartoVoyager2x",
            0, 20, 512, "@2x.png", new String[]{
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"},
            "© OpenStreetMap contributors © CARTO");

    private MapView map;
    private Marker destMarker;
    private Polygon destCircle;
    private MyLocationNewOverlay myLocation;

    // Màn hình bản đồ
    private TextView txtSearchBar;
    private ImageButton btnClearDest;
    private LinearLayout mapChips;
    private TextView txtDestName;
    private TextView txtDestAddr;
    private TextView txtRadius;
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

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable suggestTask = () -> runSearch(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cài đặt bản đồ phải làm trước khi tạo giao diện.
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        cfg.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osmdroid");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));

        setContentView(R.layout.activity_main);
        drawBehindStatusBar();

        map = findViewById(R.id.map);
        txtSearchBar = findViewById(R.id.txtSearchBar);
        btnClearDest = findViewById(R.id.btnClearDest);
        mapChips = findViewById(R.id.mapChips);
        txtDestName = findViewById(R.id.txtDestName);
        txtDestAddr = findViewById(R.id.txtDestAddr);
        txtRadius = findViewById(R.id.txtRadius);
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
                if (destCircle != null && dest != null) {
                    destCircle.setPoints(Polygon.pointsAsCircle(new GeoPoint(dest.lat, dest.lng), r));
                    map.invalidate();
                }
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
        findViewById(R.id.btnMyLocation).setOnClickListener(v -> goToMyLocation());
        btnClearDest.setOnClickListener(v -> {
            if (running) toast(R.string.stop_first);
            else clearDest();
        });

        Place saved = Prefs.getDest(this);
        if (saved != null) {
            setDest(saved);
            map.getController().setCenter(new GeoPoint(saved.lat, saved.lng));
        } else {
            showDest();
        }
        renderSaved();

        if (hasLocationPermission()) {
            enableMyLocation(dest == null);
        } else {
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
        map.setTileSource(CARTO_VOYAGER);
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(4.0);
        map.getController().setZoom(13.0);
        map.getController().setCenter(new GeoPoint(10.7769, 106.7009)); // TP.HCM

        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (running) {
                    toast(R.string.stop_first);
                } else {
                    setDest(new Place(null, p.getLatitude(), p.getLongitude()));
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));
    }

    private void enableMyLocation(boolean centerOnFirstFix) {
        if (myLocation != null) return;
        myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        // Chấm xanh viền trắng như Google Maps; khi đang di chuyển có thêm hình quạt chỉ hướng.
        myLocation.setDirectionArrow(makeBlueDot(false), makeBlueDot(true));
        myLocation.setPersonAnchor(0.5f, 0.5f);
        myLocation.setDirectionAnchor(0.5f, 0.5f);
        myLocation.enableMyLocation();
        map.getOverlays().add(myLocation);
        if (centerOnFirstFix) {
            myLocation.runOnFirstFix(() -> ui.post(() -> {
                GeoPoint p = myLocation.getMyLocation();
                if (p != null && dest == null) {
                    map.getController().setZoom(16.0);
                    map.getController().animateTo(p);
                }
            }));
        }
    }

    /** Vẽ chấm vị trí màu xanh. withHeading: thêm hình quạt phía trên (hệ thống sẽ xoay theo hướng đi). */
    private Bitmap makeBlueDot(boolean withHeading) {
        int size = dp(withHeading ? 64 : 30);
        float c = size / 2f;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (withHeading) {
            Path cone = new Path();
            cone.moveTo(c, c);
            cone.lineTo(c - dp(16), dp(2));
            cone.quadTo(c, -dp(4), c + dp(16), dp(2));
            cone.close();
            p.setColor(Color.argb(90, 26, 115, 232));
            canvas.drawPath(cone, p);
        }

        p.setColor(Color.argb(60, 0, 0, 0));
        canvas.drawCircle(c, c + dp(1), dp(11), p);
        p.setColor(Color.WHITE);
        canvas.drawCircle(c, c, dp(11), p);
        p.setColor(GOOGLE_BLUE);
        canvas.drawCircle(c, c, dp(8), p);
        return bmp;
    }

    private void goToMyLocation() {
        if (!hasLocationPermission()) {
            startAfterPermission = false;
            requestLocationPermission();
            return;
        }
        enableMyLocation(false);
        GeoPoint p = myLocation.getMyLocation();
        if (p != null) {
            map.getController().setZoom(17.0);
            map.getController().animateTo(p);
        } else {
            toast(R.string.waiting_location);
        }
    }

    private void setDest(Place p) {
        dest = p;
        Prefs.setDest(this, p);
        GeoPoint gp = new GeoPoint(p.lat, p.lng);

        if (destMarker == null) {
            destMarker = new Marker(map);
            destMarker.setIcon(getDrawable(R.drawable.ic_dest_pin));
            destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            destMarker.setInfoWindow(null);
            destMarker.setOnMarkerClickListener((m, mv) -> true);
        }
        if (destCircle == null) {
            destCircle = new Polygon(map);
            destCircle.getFillPaint().setColor(Color.argb(40, 26, 115, 232));
            destCircle.getOutlinePaint().setColor(GOOGLE_BLUE);
            destCircle.getOutlinePaint().setStrokeWidth(dp(2));
            destCircle.setInfoWindow(null);
            // Chạm vào trong vòng tròn vẫn đổi được nơi đến.
            destCircle.setOnClickListener((poly, mv, pos) -> false);
        }
        if (!map.getOverlays().contains(destCircle)) {
            // Vòng tròn nằm dưới, ghim nằm trên.
            map.getOverlays().add(destCircle);
            map.getOverlays().add(destMarker);
        }
        destMarker.setPosition(gp);
        destCircle.setPoints(Polygon.pointsAsCircle(gp, Prefs.getRadius(this)));
        map.invalidate();

        showDest();
        if (p.name == null) reverseGeocode(p);
    }

    private void clearDest() {
        dest = null;
        Prefs.setDest(this, null);
        map.getOverlays().remove(destCircle);
        map.getOverlays().remove(destMarker);
        map.invalidate();
        showDest();
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
            if (!adapter.showingHistory) return false;
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
                if (empty) showHistory();
                else if (s.toString().trim().length() >= 2) ui.postDelayed(suggestTask, SUGGEST_DELAY_MS);
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
        adapter.set(history, true);
    }

    /** submitted = người dùng bấm nút tìm trên bàn phím (khi đó được dùng thêm Nominatim). */
    private void runSearch(boolean submitted) {
        String q = edtSearch.getText().toString().trim();
        if (q.isEmpty()) return;
        final int seq = ++searchSeq;
        txtListTitle.setText(R.string.searching);
        GeoPoint me = myLocation == null ? null : myLocation.getMyLocation();
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
            final List<Place> r = results;
            final boolean f = failed;
            ui.post(() -> {
                if (seq != searchSeq || searchPage.getVisibility() != View.VISIBLE) return;
                if (f && r.isEmpty()) txtListTitle.setText(R.string.network_error);
                else if (r.isEmpty()) txtListTitle.setText(R.string.search_empty);
                else txtListTitle.setText(R.string.results);
                adapter.set(r, false);
            });
        });
    }

    private void pickPlace(Place p) {
        Prefs.addHistory(this, p);
        closeSearch();
        setDest(p);
        map.getController().setZoom(16.0);
        map.getController().animateTo(new GeoPoint(p.lat, p.lng));
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
        boolean showingHistory;

        void set(List<Place> list, boolean history) {
            items.clear();
            items.addAll(list);
            showingHistory = history;
            notifyDataSetChanged();
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
            ((ImageView) view.findViewById(R.id.rowIcon))
                    .setImageResource(showingHistory ? R.drawable.ic_history : R.drawable.ic_place);
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
            map.getController().setZoom(16.0);
            map.getController().animateTo(new GeoPoint(p.lat, p.lng));
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
                enableMyLocation(dest == null);
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
        map.onResume();
        if (myLocation != null) myLocation.enableMyLocation();
        TrackingService.addListener(this);
    }

    @Override
    protected void onPause() {
        TrackingService.removeListener(this);
        if (myLocation != null) myLocation.disableMyLocation();
        map.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
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
