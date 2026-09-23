package com.saptoi.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;

/**
 * Bảng dưới đáy màn hình, kéo lên / kéo xuống được giống Google Maps.
 * Khi thu gọn chỉ còn phần từ trên xuống hết view "peekAnchor".
 */
public class SheetLayout extends LinearLayout {

    public interface Callback {
        void onSheetMoved(SheetLayout sheet);
    }

    private final int touchSlop;
    private final float flingVelocity;
    private final int maxFlingVelocity;
    private final float density;

    private View peekAnchor;
    private int peekPadding;
    private boolean collapsed;
    private boolean dragging;
    private float downX;
    private float downY;
    private float lastY;
    private VelocityTracker velocity;
    private ValueAnimator settle;
    private Callback callback;

    public SheetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        density = getResources().getDisplayMetrics().density;
        flingVelocity = 500 * density;
    }

    public void setPeekAnchor(View anchor, int paddingPx) {
        peekAnchor = anchor;
        peekPadding = paddingPx;
        requestLayout();
    }

    public void setCallback(Callback cb) {
        callback = cb;
    }

    /** Báo cả khi bảng đổi vị trí do hiệu ứng co giãn (không qua onLayout). */
    private final ViewTreeObserver.OnPreDrawListener watcher = new ViewTreeObserver.OnPreDrawListener() {
        private float lastTop = Float.NaN;

        @Override
        public boolean onPreDraw() {
            float top = getTop() + getTranslationY();
            if (top != lastTop) {
                lastTop = top;
                if (callback != null) callback.onSheetMoved(SheetLayout.this);
            }
            return true;
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(watcher);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(watcher);
        super.onDetachedFromWindow();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /** Phần bảng đang nhìn thấy trên màn hình (px). */
    public int visibleHeight() {
        return Math.max(0, Math.round(getHeight() - getTranslationY()));
    }

    /** Độ cao phần nhìn thấy khi thu gọn (px). */
    public int peekHeight() {
        return getHeight() - maxOffset();
    }

    private int maxOffset() {
        if (peekAnchor == null || getHeight() == 0) return 0;
        int bottom = peekAnchor.getBottom();
        ViewParent p = peekAnchor.getParent();
        while (p instanceof View && p != this) {
            bottom += ((View) p).getTop();
            p = p.getParent();
        }
        return Math.max(0, getHeight() - bottom - peekPadding);
    }

    private boolean isSettling() {
        return settle != null && settle.isRunning();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (dragging || isSettling()) setOffset(getTranslationY());
        else setOffset(collapsed ? maxOffset() : 0);
    }

    private void setOffset(float offset) {
        setTranslationY(Math.max(0, Math.min(offset, maxOffset())));
        if (callback != null) callback.onSheetMoved(this);
    }

    public void expand() {
        animateTo(false);
    }

    public void collapse() {
        animateTo(true);
    }

    private void animateTo(boolean collapse) {
        collapsed = collapse;
        if (settle != null) settle.cancel();
        float from = getTranslationY();
        float to = collapse ? maxOffset() : 0;
        if (Math.abs(from - to) < 1) {
            setOffset(to);
            return;
        }
        long duration = (long) Math.max(220, Math.min(380, 180 + Math.abs(from - to) / density * 0.6f));
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(duration);
        a.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        a.addUpdateListener(v -> setOffset((float) v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Nội dung có thể đã đổi chiều cao trong lúc chạy, nên đặt lại cho đúng.
                if (!cancelled) setOffset(collapsed ? maxOffset() : 0);
            }
        });
        settle = a;
        a.start();
    }

    // ---------- Kéo bằng tay ----------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startGesture(e);
                if (isSettling()) {
                    // Chạm vào bảng đang trượt thì "bắt" nó lại để kéo tiếp.
                    settle.cancel();
                    beginDrag(e);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                track(e);
                if (!dragging && passedSlop(e)) {
                    beginDrag(e);
                    return true;
                }
                return dragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) endGesture();
                return false;
            default:
                return dragging;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startGesture(e);
                if (isSettling()) {
                    settle.cancel();
                    beginDrag(e);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                track(e);
                if (!dragging) {
                    if (!passedSlop(e)) return true;
                    beginDrag(e);
                }
                float y = e.getRawY();
                setOffset(getTranslationY() + (y - lastY));
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
                track(e);
                if (dragging) {
                    velocity.computeCurrentVelocity(1000, maxFlingVelocity);
                    release(velocity.getYVelocity());
                } else if (collapsed) {
                    // Chạm vào bảng đang thu gọn thì mở ra.
                    expand();
                }
                endGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) release(0);
                endGesture();
                return true;
            default:
                return true;
        }
    }

    private void startGesture(MotionEvent e) {
        dragging = false;
        downX = e.getRawX();
        downY = e.getRawY();
        lastY = downY;
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        track(e);
    }

    private boolean passedSlop(MotionEvent e) {
        float dy = Math.abs(e.getRawY() - downY);
        float dx = Math.abs(e.getRawX() - downX);
        return dy > touchSlop && dy > dx;
    }

    private void beginDrag(MotionEvent e) {
        dragging = true;
        lastY = e.getRawY();
        ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(true);
    }

    /** Đo tốc độ theo toạ độ màn hình, vì chính bảng cũng đang di chuyển. */
    private void track(MotionEvent e) {
        if (velocity == null) return;
        MotionEvent copy = MotionEvent.obtain(e);
        copy.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(copy);
        copy.recycle();
    }

    private void release(float vy) {
        boolean collapse;
        if (Math.abs(vy) > flingVelocity) collapse = vy > 0;
        else collapse = getTranslationY() > maxOffset() / 2f;
        animateTo(collapse);
    }

    private void endGesture() {
        dragging = false;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }
}
