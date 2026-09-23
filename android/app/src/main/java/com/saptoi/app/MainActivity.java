package com.saptoi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TrackingService.Listener {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final int RADIUS_STEP = 100;

    private MapView map;
    private Marker destMarker;
    private Polygon destCircle;
    private MyLocationNewOverlay myLocation;

    private EditText edtSearch;
    private TextView txtDest;
    private TextView txtRadius;
    private TextView txtDistance;
    private TextView txtEta;
    private SeekBar seekRadius;
    private Button btnStart;
    private Button btnSave;
    private LinearLayout savedList;

    private Place dest;
    private boolean running;
    /** Đang xin quyền để bấm "Bắt đầu" (chứ không phải để xem vị trí của tôi). */
    private boolean startAfterPermission;
    private boolean askedNotifications;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

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

        map = findViewById(R.id.map);
        edtSearch = findViewById(R.id.edtSearch);
        txtDest = findViewById(R.id.txtDest);
        txtRadius = findViewById(R.id.txtRadius);
        txtDistance = findViewById(R.id.txtDistance);
        txtEta = findViewById(R.id.txtEta);
        seekRadius = findViewById(R.id.seekRadius);
        btnStart = findViewById(R.id.btnStart);
        btnSave = findViewById(R.id.btnSave);
        savedList = findViewById(R.id.savedList);
        ImageButton btnMyLocation = findViewById(R.id.btnMyLocation);
        Button btnSearch = findViewById(R.id.btnSearch);

        setupMap();

        int radius = Prefs.getRadius(this);
        seekRadius.setProgress(Math.max(0, radius / RADIUS_STEP - 1));
        showRadius(radius);
        seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int r = (progress + 1) * RADIUS_STEP;
                showRadius(r);
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

        btnSearch.setOnClickListener(v -> search());
        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
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
        btnMyLocation.setOnClickListener(v -> goToMyLocation());

        Place saved = Prefs.getDest(this);
        if (saved != null) {
            setDest(saved);
            map.getController().setCenter(new GeoPoint(saved.lat, saved.lng));
        }
        renderSaved();

        if (hasLocationPermission()) {
            enableMyLocation(dest == null);
        } else {
            startAfterPermission = false;
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    // ---------- Bản đồ ----------

    private void setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
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

    private void goToMyLocation() {
        if (!hasLocationPermission()) {
            startAfterPermission = false;
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        enableMyLocation(false);
        GeoPoint p = myLocation.getMyLocation();
        if (p != null) {
            map.getController().setZoom(16.0);
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
            destCircle.getFillPaint().setColor(Color.argb(40, 16, 185, 129));
            destCircle.getOutlinePaint().setColor(Color.rgb(16, 185, 129));
            destCircle.getOutlinePaint().setStrokeWidth(4f);
            destCircle.setInfoWindow(null);
            // Chạm vào trong vòng tròn vẫn đổi được nơi đến.
            destCircle.setOnClickListener((poly, mv, pos) -> false);
            // Vòng tròn nằm dưới, ghim nằm trên.
            map.getOverlays().add(destCircle);
            map.getOverlays().add(destMarker);
        }
        destMarker.setPosition(gp);
        destCircle.setPoints(Polygon.pointsAsCircle(gp, Prefs.getRadius(this)));
        map.invalidate();

        showDestName();
        btnStart.setEnabled(true);
        btnSave.setEnabled(true);

        if (p.name == null) reverseGeocode(p);
    }

    private void showDestName() {
        txtDest.setText(dest == null ? getString(R.string.no_dest)
                : getString(R.string.dest_label, dest.label()));
    }

    private void showRadius(int meters) {
        txtRadius.setText(Fmt.distance(meters));
    }

    // ---------- Tìm địa chỉ ----------

    private void search() {
        String q = edtSearch.getText().toString().trim();
        if (q.isEmpty()) return;
        if (running) {
            toast(R.string.stop_first);
            return;
        }
        hideKeyboard();
        toast(R.string.searching);
        io.execute(() -> {
            try {
                List<Place> results = Geo.search(q);
                ui.post(() -> showResults(results));
            } catch (Exception e) {
                ui.post(() -> toast(R.string.network_error));
            }
        });
    }

    private void showResults(List<Place> results) {
        if (isFinishing()) return;
        if (results.isEmpty()) {
            toast(R.string.search_empty);
            return;
        }
        String[] names = new String[results.size()];
        for (int i = 0; i < names.length; i++) names[i] = results.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_result)
                .setItems(names, (d, which) -> {
                    Place r = results.get(which);
                    setDest(r.withName(Geo.shortName(r.name)));
                    map.getController().setZoom(16.0);
                    map.getController().animateTo(new GeoPoint(r.lat, r.lng));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void reverseGeocode(Place p) {
        io.execute(() -> {
            try {
                String name = Geo.reverse(p.lat, p.lng);
                if (name == null) return;
                ui.post(() -> {
                    // Chỉ cập nhật nếu người dùng chưa chọn nơi khác.
                    if (dest != null && dest.lat == p.lat && dest.lng == p.lng && dest.name == null) {
                        dest = p.withName(name);
                        Prefs.setDest(this, dest);
                        showDestName();
                    }
                });
            } catch (Exception ignored) {
                // Giữ tên dạng toạ độ.
            }
        });
    }

    // ---------- Địa điểm đã lưu ----------

    private void renderSaved() {
        savedList.removeAllViews();
        List<Place> places = Prefs.getSaved(this);
        findViewById(R.id.savedScroll).setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        int gap = dp(6);
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText("★ " + p.label());
            chip.setMaxWidth(dp(180));
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setTextColor(getColor(R.color.text));
            chip.setTextSize(13);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(gap);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                if (running) {
                    toast(R.string.stop_first);
                    return;
                }
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
            savedList.addView(chip);
        }
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
                    list.add(0, new Place(name, dest.lat, dest.lng));
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
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
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
            btnStart.setEnabled(true);
        } else {
            btnStart.setText(R.string.start);
            btnStart.setBackgroundResource(R.drawable.bg_btn_primary);
            btnStart.setEnabled(dest != null);
        }
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
