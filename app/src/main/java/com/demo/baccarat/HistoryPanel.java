package com.demo.baccarat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Bảng "Các bàn đã học": xem lại từng bàn, thống kê và đọc lại ván-theo-ván. */
final class HistoryPanel {
    interface Actions {
        void close();
        void delete(int idx);
    }

    private final Context ctx;
    private final Actions act;
    private final LinearLayout root, body;

    HistoryPanel(Context c, Actions a) {
        ctx = c;
        act = a;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2101322);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x33FFFFFF);
        root.setBackground(bg);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = tv("📚 Các bàn đã học", 16, true, Color.WHITE);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = tv("✕ Đóng", 14, true, 0xFFFFD34D);
        close.setOnClickListener(v -> act.close());
        head.addView(close);
        root.addView(head);

        ScrollView sv = new ScrollView(c);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    View view() { return root; }

    private int dp(int v) { return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    void showLoading() {
        body.removeAllViews();
        body.addView(tv("Đang đọc lại các bàn…", 13, false, 0xCCFFFFFF), lp(10));
    }

    void showReport(Analysis.Report r) {
        body.removeAllViews();
        body.addView(tv(Analysis.summary(r), 12, false, 0xDDFFFFFF), lp(8));
        if (r.current != null) addCard("Bàn hiện tại", r.current, true);
        for (int i = r.past.size() - 1; i >= 0; i--) addCard("Bàn #" + (i + 1), r.past.get(i), false);
        if (r.past.isEmpty() && r.current == null)
            body.addView(tv("Chưa có bàn nào được lưu. Hãy để trợ lý xem một bàn đủ 20 ván.", 13, false, 0xCCFFFFFF), lp(10));
    }

    private void addCard(String name, final Analysis.TableReport t, boolean open) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));

        final TextView head = tv(Analysis.headerText(name, t), 13, true, t.current ? 0xFF3ECF72 : Color.WHITE);
        final LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setVisibility(View.GONE);
        final boolean[] built = {false};
        final View.OnClickListener toggle = v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
            } else {
                if (!built[0]) { build(content, t); built[0] = true; }
                content.setVisibility(View.VISIBLE);
            }
        };
        head.setOnClickListener(toggle);
        card.addView(head);
        card.addView(content);
        body.addView(card, lp(8));
        if (open) toggle.onClick(head);
    }

    private void build(LinearLayout content, final Analysis.TableReport t) {
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.addView(new BeadView(ctx, t.seq, dp(22)));
        content.addView(hs, lp(8));
        content.addView(tv(Analysis.statsLine(t.st), 12, false, 0xDDFFFFFF), lp(6));
        content.addView(tv(Analysis.replayLine(t), 12, false, 0xFFFFD34D), lp(4));
        if (!t.marks.isEmpty()) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (int i = 0; i < t.marks.length(); i++) {
                int s = sb.length();
                sb.append("● ");
                sb.setSpan(new ForegroundColorSpan(t.marks.charAt(i) == '1' ? 0xFF3ECF72 : 0xFFFF4D5E), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView dots = tv("", 11, false, Color.WHITE);
            dots.setText(sb);
            content.addView(dots, lp(4));
            content.addView(tv("Chấm xanh = đoán đúng, đỏ = đoán sai (theo thứ tự ván)", 10, false, 0x99FFFFFF), lp(2));
        }
        if (!t.current) {
            TextView del = tv("🗑 Xóa bàn này khỏi bộ nhớ học", 11, false, 0x99FFFFFF);
            del.setPaintFlags(del.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            del.setOnClickListener(v -> act.delete(t.idx));
            content.addView(del, lp(8));
        }
    }
}
