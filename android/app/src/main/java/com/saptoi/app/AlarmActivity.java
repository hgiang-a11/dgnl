package com.saptoi.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

/** Màn hình báo to, hiện cả khi điện thoại đang khoá. */
public class AlarmActivity extends Activity implements TrackingService.Listener {

    public static final String EXTRA_MESSAGE = "message";

    private TextView txtMessage;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_alarm);
        txtMessage = findViewById(R.id.txtAlarmMsg);
        findViewById(R.id.btnDismiss).setOnClickListener(v -> dismiss());
        showMessage(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showMessage(intent);
    }

    private void showMessage(Intent intent) {
        String msg = intent == null ? null : intent.getStringExtra(EXTRA_MESSAGE);
        txtMessage.setText(msg == null ? "" : msg);
    }

    private void dismiss() {
        TrackingService.stop(this);
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        dismiss();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TrackingService.addListener(this);
    }

    @Override
    protected void onStop() {
        TrackingService.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStatus(TrackingService.Status s) {
        // Đã tắt báo ở chỗ khác (ví dụ bấm trên thông báo) thì đóng màn hình này.
        if (!s.running) finish();
    }
}
