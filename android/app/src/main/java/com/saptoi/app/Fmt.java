package com.saptoi.app;

import java.util.Locale;

/** Định dạng khoảng cách và thời gian để hiển thị. */
public final class Fmt {
    private static final Locale VI = new Locale("vi", "VN");

    private Fmt() {}

    public static String distance(float meters) {
        if (meters >= 1000) return String.format(VI, "%.1f km", meters / 1000f);
        return String.format(VI, "%d m", Math.round(meters));
    }

    /** Trả về null nếu chưa ước tính được. */
    public static String duration(long seconds) {
        if (seconds <= 0) return null;
        long min = Math.round(seconds / 60.0);
        if (min < 1) return "< 1 phút";
        if (min < 60) return min + " phút";
        return (min / 60) + " giờ " + (min % 60) + " phút";
    }
}
