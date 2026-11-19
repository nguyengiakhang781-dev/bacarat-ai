package com.demo.baccarat;

import android.content.Context;
import android.content.SharedPreferences;

/** Ghi lại lỗi làm tool bị văng để lần sau mở app còn thấy lý do. */
final class CrashLog {
    private CrashLog() {}

    static void install(final Context c) {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                save(c, e);
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    static void save(Context c, Throwable e) {
        try {
            StackTraceElement[] st = e.getStackTrace();
            String where = st.length > 0 ? st[0].getClassName().replace("com.demo.baccarat.", "") + ":" + st[0].getLineNumber() : "";
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage() + " @ " + where;
            c.getSharedPreferences("bca", Context.MODE_PRIVATE).edit().putString("last_error", msg).commit();
        } catch (Throwable ignored) { }
    }

    static String last(Context c) { return c.getSharedPreferences("bca", Context.MODE_PRIVATE).getString("last_error", ""); }

    static void clear(Context c) { c.getSharedPreferences("bca", Context.MODE_PRIVATE).edit().remove("last_error").apply(); }
}
