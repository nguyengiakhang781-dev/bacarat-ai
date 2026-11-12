package com.demo.baccarat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Xem lại các bàn đã lưu: thống kê từng bàn + đọc lại ván-theo-ván bằng bộ dự đoán (Java thuần). */
public final class Analysis {
    private Analysis() {}

    public static final class Stats {
        public int nB, nP, nT, nX, longest, hands;
        public double z;
    }

    public static final class TableReport {
        public int idx;               // vị trí trong danh sách bàn đã lưu
        public String seq;
        public Stats st;
        public int evalN, okLearn, okPlain, baseB;
        public String marks = "";     // '1' đoán đúng, '0' đoán sai (theo từng ván, bản "có học")
        public boolean current;
    }

    public static final class Report {
        public final List<TableReport> past = new ArrayList<>();
        public TableReport current;
        public int tables, hands, n, okLearn, okPlain, baseB;
        public double ci() { return n > 0 ? 1.96 * Math.sqrt(0.25 / n) : 0; }
    }

    public static Stats stats(String seq) {
        Stats s = new Stats();
        for (int i = 0; i < seq.length(); i++) {
            char c = seq.charAt(i);
            if (c == 'B') s.nB++; else if (c == 'P') s.nP++; else if (c == 'T') s.nT++; else s.nX++;
        }
        String d = Predictor.strip(seq);
        s.hands = d.length();
        int runs = 0, run = 0;
        for (int i = 0; i < d.length(); i++) {
            if (i > 0 && d.charAt(i) == d.charAt(i - 1)) run++; else { runs++; run = 1; }
            if (run > s.longest) s.longest = run;
        }
        if (d.length() >= 10 && s.nB > 0 && s.nP > 0) {
            double n = d.length(), mu = 1 + 2.0 * s.nB * s.nP / n, vr = (mu - 1) * (mu - 2) / (n - 1);
            s.z = vr > 0 ? (runs - mu) / Math.sqrt(vr) : 0;
        }
        return s;
    }

    private static TableReport evalTable(Predictor pr, String seq, Predictor.Learner L, int idx) {
        TableReport t = new TableReport();
        t.idx = idx;
        t.seq = seq;
        t.st = stats(seq);
        StringBuilder marks = new StringBuilder();
        for (int j = 8; j < seq.length(); j++) {
            char r = seq.charAt(j);
            if (r == 'T' || r == 'X') continue;
            String pre = seq.substring(0, j);
            Predictor.Pred a = pr.predict(pre, L), b = pr.predict(pre, null);
            if (a == null || b == null) continue;
            t.evalN++;
            if (a.v == r) { t.okLearn++; marks.append('1'); } else marks.append('0');
            if (b.v == r) t.okPlain++;
            if (r == 'B') t.baseB++;
        }
        t.marks = marks.toString();
        return t;
    }

    /**
     * Đọc lại tất cả các bàn theo thứ tự thời gian: mỗi bàn chỉ được dùng kiến thức học từ các bàn TRƯỚC nó.
     * curIdx = vị trí bàn đang chơi (hoặc -1); bàn đó được đọc lại riêng, dùng kiến thức từ mọi bàn đã kết thúc.
     */
    public static Report run(List<String> tables, int curIdx) {
        Predictor pr = new Predictor();
        Predictor.Learner L = pr.newLearner();
        Report rep = new Report();
        for (int i = 0; i < tables.size(); i++) {
            if (i == curIdx) continue;
            TableReport t = evalTable(pr, tables.get(i), L, i);
            rep.past.add(t);
            rep.tables++;
            rep.hands += tables.get(i).length();
            rep.n += t.evalN; rep.okLearn += t.okLearn; rep.okPlain += t.okPlain; rep.baseB += t.baseB;
            pr.learnAdd(L, tables.get(i));
        }
        if (curIdx >= 0 && curIdx < tables.size()) {
            rep.current = evalTable(pr, tables.get(curIdx), L, curIdx);
            rep.current.current = true;
        }
        return rep;
    }

    private static String pct(int a, int n) { return n > 0 ? String.format(Locale.US, "%.1f%%", a * 100.0 / n) : "–"; }

    public static String statsLine(Stats s) {
        String v;
        if (s.hands < 10) v = "chưa đủ ván để kiểm định";
        else if (Math.abs(s.z) < 1.96) v = "bàn này trông ngẫu nhiên";
        else v = (s.z > 0 ? "nhảy nhiều hơn bình thường" : "bệt nhiều hơn bình thường") + " (có thể trùng hợp)";
        return "Cái " + s.nB + " · Con " + s.nP + " · Hòa " + s.nT + (s.nX > 0 ? " · không rõ " + s.nX : "")
                + " · bệt dài nhất " + s.longest + " · " + v;
    }

    public static String replayLine(TableReport t) {
        if (t.evalN == 0) return "Chưa đủ ván để đọc lại (cần ≥ 9 ván).";
        return "Đọc lại từng ván: có học " + pct(t.okLearn, t.evalN) + " · không học " + pct(t.okPlain, t.evalN)
                + " · luôn đoán Cái " + pct(t.baseB, t.evalN) + " (" + t.evalN + " lần đoán)";
    }

    public static String headerText(String name, TableReport t) {
        return name + " · " + t.seq.length() + " ván · " + (t.evalN > 0 ? "đoán đúng " + pct(t.okLearn, t.evalN) : "chưa đủ ván để đọc lại");
    }

    public static String summary(Report r) {
        String s = "Đã học " + r.tables + " bàn · " + r.hands + " ván.";
        if (r.n == 0) return s + "\nChưa đủ dữ liệu để đọc lại.";
        double a = (double) r.okLearn / r.n, b = (double) r.okPlain / r.n, ci = r.ci();
        boolean sig = Math.max(Math.abs(a - 0.5), Math.abs(b - 0.5)) > ci;
        return s + "\nĐọc lại toàn bộ (mỗi bàn chỉ dùng các bàn trước nó): có học " + pct(r.okLearn, r.n) + " · không học "
                + pct(r.okPlain, r.n) + " · luôn đoán Cái " + pct(r.baseB, r.n) + " trên " + r.n + " lần (±"
                + String.format(Locale.US, "%.1f", ci * 100) + "%) → " + (sig ? "lệch khỏi 50%" : "không khác ngẫu nhiên");
    }
}
