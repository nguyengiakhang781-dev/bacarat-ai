package com.demo.baccarat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001, REQ_NOTIF = 1002;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int p = (int) (24 * d);
        box.setPadding(p, p, p, p);
        box.setBackgroundColor(Color.parseColor("#14162A"));

        TextView title = new TextView(this);
        title.setText("Trợ lý Baccarat (demo)");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("1. Cho phép \"Hiển thị trên các ứng dụng khác\"\n2. Cho phép quay màn hình\n3. Mở game, bấm \"Bắt đầu xem màn hình\" ở trợ lý nổi.\n\nMọi kết quả dự đoán chỉ mang tính demo.");
        info.setTextSize(14);
        info.setTextColor(0xCCFFFFFF);
        info.setPadding(0, (int) (16 * d), 0, (int) (24 * d));
        box.addView(info);

        Button go = new Button(this);
        go.setText("Bật trợ lý");
        go.setAllCaps(false);
        go.setOnClickListener(v -> begin());
        box.addView(go);
        setContentView(box);
    }

    private void begin() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy bật \"Hiển thị trên các ứng dụng khác\" cho ứng dụng, rồi quay lại bấm lần nữa", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent s = new Intent(this, OverlayService.class);
            s.putExtra("code", resultCode);
            s.putExtra("data", data);
            startForegroundService(s);
            moveTaskToBack(true);
        } else {
            Toast.makeText(this, "Chưa được cấp quyền quay màn hình", Toast.LENGTH_SHORT).show();
        }
    }
}
