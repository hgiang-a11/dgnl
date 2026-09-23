package com.saptoi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chạy nền (kể cả khi tắt màn hình): liên tục lấy vị trí, tính khoảng cách tới nơi đến,
 * và bật chuông + rung + màn hình báo khi đã vào trong bán kính đã chọn.
 */
public class TrackingService extends Service implements LocationListener {

    public static final String ACTION_START = "com.saptoi.app.START";
    public static final String ACTION_STOP = "com.saptoi.app.STOP";

    private static final String CH_TRACKING = "tracking";
    private static final String CH_ALARM = "alarm_v1";
    private static final int NOTI_TRACKING = 1;
    private static final int NOTI_ALARM = 2;

    private static final long GPS_INTERVAL_MS = 2000;
    private static final long NETWORK_INTERVAL_MS = 5000;

    /** Trạng thái gửi cho màn hình chính và màn hình báo. */
    public static final class Status {
        public final boolean running;
        public final boolean alarming;
        /** Mét; âm nghĩa là chưa có vị trí. */
        public final float distance;
        /** Giây; âm nghĩa là chưa ước tính được. */
        public final long etaSeconds;

        Status(boolean running, boolean alarming, float distance, long etaSeconds) {
            this.running = running;
            this.alarming = alarming;
            this.distance = distance;
            this.etaSeconds = etaSeconds;
        }
    }

    public interface Listener {
        void onStatus(Status status);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static TrackingService instance;
    private static Status lastStatus = new Status(false, false, -1, -1);

    public static boolean isRunning() {
        return instance != null;
    }

    /** Gọi trên luồng giao diện. Gửi ngay trạng thái hiện tại cho người nghe mới. */
    public static void addListener(Listener l) {
        listeners.add(l);
        l.onStatus(lastStatus);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static void stop(Context c) {
        if (instance != null) instance.stopTracking();
    }

    private LocationManager locationManager;
    private Place dest;
    private boolean alarming;
    private Location lastLocation;
    private float smoothSpeed = -1;
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            return START_NOT_STICKY;
        }

        dest = Prefs.getDest(this);
        if (dest == null) {
            stopTracking();
            return START_NOT_STICKY;
        }

        try {
            Notification n = buildTrackingNotification(getString(R.string.waiting_location));
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTI_TRACKING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTI_TRACKING, n);
            }
        } catch (RuntimeException e) {
            // Hệ thống không cho chạy nền (ví dụ bị khởi động lại khi app không mở).
            stopTracking();
            return START_NOT_STICKY;
        }

        stopAlarmSound();
        alarming = false;
        lastLocation = null;
        smoothSpeed = -1;
        requestUpdates();
        publish(new Status(true, false, -1, -1));
        return START_STICKY;
    }

    private void requestUpdates() {
        try {
            locationManager.removeUpdates(this);
            List<String> providers = locationManager.getAllProviders();
            boolean any = false;
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        GPS_INTERVAL_MS, 0, this, Looper.getMainLooper());
                any = true;
            }
            if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        NETWORK_INTERVAL_MS, 0, this, Looper.getMainLooper());
                any = true;
            }
            if (!any) stopTracking();
        } catch (SecurityException e) {
            stopTracking();
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (dest == null || alarming) return;

        // Có GPS mới thì bỏ qua vị trí kém chính xác từ mạng.
        if (lastLocation != null
                && LocationManager.GPS_PROVIDER.equals(lastLocation.getProvider())
                && !LocationManager.GPS_PROVIDER.equals(loc.getProvider())
                && loc.getTime() - lastLocation.getTime() < 10000) {
            return;
        }

        float[] result = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), dest.lat, dest.lng, result);
        float distance = result[0];

        // Tốc độ: ưu tiên số của GPS, nếu không có thì tự tính từ 2 lần đo, rồi làm mượt.
        float speed = -1;
        if (loc.hasSpeed()) {
            speed = loc.getSpeed();
        } else if (lastLocation != null) {
            long dt = loc.getTime() - lastLocation.getTime();
            if (dt > 0) speed = loc.distanceTo(lastLocation) / (dt / 1000f);
        }
        if (speed >= 0) smoothSpeed = smoothSpeed < 0 ? speed : smoothSpeed * 0.7f + speed * 0.3f;
        lastLocation = loc;

        long eta = smoothSpeed > 0.5f ? (long) (distance / smoothSpeed) : -1;
        int radius = Prefs.getRadius(this);

        if (distance <= radius) {
            alarming = true;
            publish(new Status(true, true, distance, eta));
            startAlarm(distance);
        } else {
            publish(new Status(true, false, distance, eta));
            updateTrackingNotification(distance, eta);
        }
    }

    // Các hàm dưới bắt buộc phải có trên Android 10 trở xuống, nếu thiếu app sẽ bị lỗi.
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    // ---------- Báo động ----------

    private void startAlarm(float distance) {
        String message = getString(R.string.alarm_msg, Fmt.distance(distance), dest.label());

        if (!Prefs.vibrateOnly(this)) playAlarmSound();
        vibrate();

        Intent alarmIntent = new Intent(this, AlarmActivity.class)
                .putExtra(AlarmActivity.EXTRA_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(this, 1, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CH_ALARM)
                .setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(getString(R.string.alarm_title))
                .setContentText(message)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.alarm_dismiss), stopPendingIntent()).build())
                .build();
        notificationManager().notify(NOTI_ALARM, n);

        // Nếu đang mở app thì hiện luôn màn hình báo.
        if (!listeners.isEmpty()) {
            try {
                startActivity(alarmIntent);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void playAlarmSound() {
        stopAlarmSound();
        Uri uri = Prefs.getRingtone(this);
        if (uri == null) uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        startPlayer(uri);
        // Nhạc đã chọn không phát được (ví dụ file đã bị xoá) thì dùng chuông mặc định.
        if (player == null) startPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
    }

    private void startPlayer(Uri uri) {
        if (uri == null) return;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(alarmAudioAttributes());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
    }

    private void stopAlarmSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (RuntimeException ignored) {
            }
            releasePlayer();
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate() {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            v = vm == null ? null : vm.getDefaultVibrator();
        } else {
            v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (v == null || !v.hasVibrator()) return;
        vibrator = v;
        long[] pattern = {0, 800, 400, 800, 400};
        v.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAudioAttributes());
    }

    private static AudioAttributes alarmAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    // ---------- Dừng ----------

    private void stopTracking() {
        stopAlarmSound();
        try {
            locationManager.removeUpdates(this);
        } catch (RuntimeException ignored) {
        }
        notificationManager().cancel(NOTI_ALARM);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        instance = null;
        publish(new Status(false, false, -1, -1));
    }

    @Override
    public void onDestroy() {
        if (instance == this) stopTracking();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static void publish(Status s) {
        lastStatus = s;
        for (Listener l : listeners) l.onStatus(s);
    }

    // ---------- Thông báo ----------

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private void createChannels() {
        NotificationManager nm = notificationManager();

        NotificationChannel tracking = new NotificationChannel(CH_TRACKING,
                getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW);
        tracking.setShowBadge(false);
        nm.createNotificationChannel(tracking);

        // Âm thanh và rung do app tự phát, nên tắt tiếng của thông báo để khỏi kêu hai lần.
        NotificationChannel alarm = new NotificationChannel(CH_ALARM,
                getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null);
        alarm.enableVibration(false);
        alarm.setBypassDnd(true);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alarm);
    }

    private PendingIntent stopPendingIntent() {
        Intent i = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        return PendingIntent.getService(this, 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildTrackingNotification(String text) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = getString(R.string.noti_tracking_title, dest == null ? "" : dest.label());
        return new Notification.Builder(this, CH_TRACKING)
                .setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.noti_stop), stopPendingIntent()).build())
                .build();
    }

    private void updateTrackingNotification(float distance, long eta) {
        String etaText = Fmt.duration(eta);
        String text = etaText == null
                ? getString(R.string.noti_tracking_text_no_eta, Fmt.distance(distance))
                : getString(R.string.noti_tracking_text, Fmt.distance(distance), etaText);
        notificationManager().notify(NOTI_TRACKING, buildTrackingNotification(text));
    }
}
