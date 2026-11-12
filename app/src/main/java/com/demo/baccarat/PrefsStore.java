package com.demo.baccarat;

import android.content.Context;
import android.content.SharedPreferences;

/** Lưu các bàn đã chơi (để học) vào bộ nhớ máy. */
final class PrefsStore implements Tracker.Store {
    private final SharedPreferences sp;

    PrefsStore(Context c) {
        sp = c.getSharedPreferences("bca", Context.MODE_PRIVATE);
    }

    @Override public String load() { return sp.getString("tables", ""); }

    @Override public void save(String data) { sp.edit().putString("tables", data).apply(); }
}
