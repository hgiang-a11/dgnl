package com.saptoi.app;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Màn hình báo to, hiện cả khi điện thoại đang khoá. */
public class AlarmActivity extends Activity implements TrackingService.Listener {

    public static final String EXTRA_MESSAGE = "message";

    private TextView txtMessage;
    private final List<Animator> animators = new ArrayList<>();

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

        pulse(findViewById(R.id.pulse1), 0);
        pulse(findViewById(R.id.pulse2), 900);
        ObjectAnimator beat = ObjectAnimator.ofPropertyValuesHolder(findViewById(R.id.alarmIcon),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        beat.setDuration(450);
        beat.setRepeatCount(ValueAnimator.INFINITE);
        beat.setRepeatMode(ValueAnimator.REVERSE);
        beat.setInterpolator(new AccelerateDecelerateInterpolator());
        animators.add(beat);
        beat.start();
    }

    /** Vòng tròn to dần và mờ dần, lặp lại mãi. */
    private void pulse(View v, long delay) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 2f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 2f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f));
        a.setDuration(1800);
        a.setStartDelay(delay);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new DecelerateInterpolator());
        v.setAlpha(0f);
        animators.add(a);
        a.start();
    }

    @Override
    protected void onDestroy() {
        for (Animator a : animators) a.cancel();
        super.onDestroy();
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
