package com.demo.baccarat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bộ phân tích/dự đoán (ensemble 29 phương án) + học liên bàn. Java thuần. */
public final class Predictor {

    public static final class Pred {
        public final char v;
        public final String why;
        Pred(char v, String why) { this.v = v; this.why = why; }
    }

    /** Dữ liệu học từ các bàn đã kết thúc. */
    public static final class Learner {
        final List<Map<String, int[]>> G = new ArrayList<>(); // chỉ số 1..5: ngữ cảnh -> {B, P}
        final Map<String, int[]> prior = new HashMap<>();     // tên phương án -> {đúng, tổng}
        public int tables, hands;
        Learner() { for (int k = 0; k <= 5; k++) G.add(new HashMap<String, int[]>()); }
    }

    private interface Strat { char f(String s); } // 0 = không có ý kiến

    private final String[] names;
    private final Strat[] strats;
    private Learner cur;
    /** Mô hình đã học dùng mặc định cho predict(seq). */
    public Learner learn;

    public Predictor() {
        List<String> n = new ArrayList<>();
        List<Strat> s = new ArrayList<>();
        add(n, s, "Theo ván trước", x -> last(x));
        add(n, s, "Đảo ván trước", x -> opp(last(x)));
        add(n, s, "Cầu bệt", x -> { int[] R = runs(x); return R[R.length - 1] >= 3 ? last(x) : 0; });
        add(n, s, "Cầu 1-1", x -> { int[] R = runs(x); return R.length >= 3 && R[R.length - 1] == 1 && R[R.length - 2] == 1 && R[R.length - 3] == 1 ? opp(last(x)) : 0; });
        add(n, s, "Cầu 2-2", x -> {
            int[] R = runs(x); int m = R.length; if (m < 2) return 0;
            if (R[m - 1] == 2 && R[m - 2] == 2) return opp(last(x));
            if (R[m - 1] == 1 && R[m - 2] == 2) return last(x);
            return 0;
        });
        add(n, s, "Cầu 1-2", x -> { int[] R = runs(x); int m = R.length; return m >= 3 && R[m - 1] == 1 && R[m - 2] == 2 && R[m - 3] == 1 ? opp(last(x)) : 0; });
        add(n, s, "Xu hướng 8 ván", major(8));
        add(n, s, "Xu hướng 24 ván", major(24));
        add(n, s, "Bám cột trước", x -> { int[] R = runs(x); int m = R.length; if (m < 3) return 0; return R[m - 1] < R[m - 2] ? last(x) : opp(last(x)); });
        add(n, s, "Mẫu lặp 2", markov(2));
        add(n, s, "Mẫu lặp 3", markov(3));
        add(n, s, "Mẫu lặp 4", markov(4));
        add(n, s, "Mẫu lặp 5", markov(5));
        add(n, s, "Mẫu lặp 1", markov(1));
        add(n, s, "Mẫu lặp 6", markov(6));
        add(n, s, "Cầu 3-3", runPair(3));
        add(n, s, "Chu kỳ 2", periodic(2));
        add(n, s, "Chu kỳ 3", periodic(3));
        add(n, s, "Chu kỳ 4", periodic(4));
        add(n, s, "Chu kỳ 5", periodic(5));
        add(n, s, "Chu kỳ 6", periodic(6));
        add(n, s, "Khớp chuỗi dài", Predictor::longMatch);
        add(n, s, "Đảo sau bệt dài", x -> { int[] R = runs(x); return R[R.length - 1] >= 5 ? opp(last(x)) : 0; });
        add(n, s, "Hồi quy 6 ván", revert(6, 5));
        add(n, s, "Theo đà 6 ván", momentum(6, 5));
        add(n, s, "Hồi quy 10 ván", revert(10, 7));
        add(n, s, "Theo đà 10 ván", momentum(10, 7));
        add(n, s, "Xu hướng 40 ván", major(40));
        for (int k = 1; k <= 5; k++) add(n, s, "Đã học " + k, learned(k));
        names = n.toArray(new String[0]);
        strats = s.toArray(new Strat[0]);
    }

    private static void add(List<String> n, List<Strat> s, String name, Strat f) { n.add(name); s.add(f); }

    private static char last(String s) { return s.charAt(s.length() - 1); }
    private static char opp(char c) { return c == 'B' ? 'P' : 'B'; }

    private static int countB(String w) { int b = 0; for (int i = 0; i < w.length(); i++) if (w.charAt(i) == 'B') b++; return b; }

    private static int[] runs(String s) {
        List<Integer> r = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int j = i;
            while (j < s.length() && s.charAt(j) == s.charAt(i)) j++;
            r.add(j - i);
            i = j;
        }
        int[] a = new int[r.size()];
        for (int k = 0; k < a.length; k++) a[k] = r.get(k);
        return a;
    }

    private static Strat markov(final int k) {
        return s -> {
            if (s.length() < k + 4) return 0;
            String ctx = s.substring(s.length() - k);
            int cB = 0, cP = 0;
            for (int i = k; i < s.length(); i++)
                if (s.regionMatches(i - k, ctx, 0, k)) { if (s.charAt(i) == 'B') cB++; else cP++; }
            if (cB + cP < 3 || cB == cP) return 0;
            return cB > cP ? 'B' : 'P';
        };
    }

    private static Strat major(final int n) {
        return s -> {
            String w = s.substring(Math.max(0, s.length() - n));
            int b = countB(w);
            return b == w.length() - b ? 0 : (b > w.length() - b ? 'B' : 'P');
        };
    }

    private Strat learned(final int k) {
        return s -> {
            if (cur == null || s.length() < k) return 0;
            int[] e = cur.G.get(k).get(s.substring(s.length() - k));
            if (e == null) return 0;
            int n = e[0] + e[1];
            if (n < 40) return 0;
            double z = ((double) e[0] / n - 0.5) / Math.sqrt(0.25 / n);
            return Math.abs(z) < 1 ? 0 : (z > 0 ? 'B' : 'P');
        };
    }

    private static Strat periodic(final int p) {
        return s -> (s.length() >= 2 * p && s.substring(s.length() - p).equals(s.substring(s.length() - 2 * p, s.length() - p))) ? s.charAt(s.length() - p) : 0;
    }

    private static Strat runPair(final int k) {
        return s -> {
            int[] R = runs(s); int m = R.length; if (m < 2) return 0;
            if (R[m - 1] == k && R[m - 2] == k) return opp(last(s));
            if (R[m - 1] < k && R[m - 2] == k) return last(s);
            return 0;
        };
    }

    private static char longMatch(String s) {
        int n = s.length();
        for (int L = Math.min(12, n - 2); L >= 4; L--) {
            String suf = s.substring(n - L);
            int j = s.lastIndexOf(suf, n - L - 1);
            if (j >= 0 && j + L < n) return s.charAt(j + L);
        }
        return 0;
    }

    private static Strat revert(final int w, final int th) {
        return s -> {
            if (s.length() < w) return 0;
            int b = countB(s.substring(s.length() - w));
            return b >= th ? 'P' : (w - b >= th ? 'B' : 0);
        };
    }

    private static Strat momentum(final int w, final int th) {
        return s -> {
            if (s.length() < w) return 0;
            int b = countB(s.substring(s.length() - w));
            return b >= th ? 'B' : (w - b >= th ? 'P' : 0);
        };
    }

    /** Bỏ ván Hòa (T) và ô không rõ (X). */
    public static String strip(String seq) { return seq.replace("T", "").replace("X", ""); }

    // ---------- Học từ các bàn đã kết thúc ----------
    public Learner newLearner() { return new Learner(); }

    public void learnAdd(Learner L, String raw) {
        cur = null;
        String s = strip(raw);
        if (s.length() < 12) return;
        L.tables++;
        L.hands += s.length();
        for (int k = 1; k <= 5; k++)
            for (int i = k; i < s.length(); i++) {
                String c = s.substring(i - k, i);
                int[] e = L.G.get(k).get(c);
                if (e == null) { e = new int[2]; L.G.get(k).put(c, e); }
                e[s.charAt(i) == 'B' ? 0 : 1]++;
            }
        for (int i = 8; i < s.length(); i++) {
            String pre = s.substring(0, i);
            for (int q = 0; q < strats.length; q++) {
                if (names[q].startsWith("Đã học")) continue;
                char p = strats[q].f(pre);
                if (p == 0) continue;
                int[] e = L.prior.get(names[q]);
                if (e == null) { e = new int[2]; L.prior.put(names[q], e); }
                e[1]++;
                if (p == s.charAt(i)) e[0]++;
            }
        }
    }

    private double priorInit(String name) {
        int[] e = cur == null ? null : cur.prior.get(name);
        if (e == null || e[1] < 60) return 0;
        return Math.max(-1.5, Math.min(1.5, ((double) e[0] / e[1] - 0.5) * 30));
    }

    public Pred predict(String seq) { return predict(seq, learn); }

    /** L = null: không dùng dữ liệu đã học. */
    public Pred predict(String seq, Learner L) {
        cur = L;
        String s = strip(seq);
        if (s.length() < 8) return null;
        final double[] DECS = {0.86, 0.93, 0.98};
        final double ETA = 1.2;
        int K = strats.length;
        double[][] score = new double[DECS.length][K];
        if (cur != null) for (int k = 0; k < K; k++) { double v = priorInit(names[k]); for (int m = 0; m < DECS.length; m++) score[m][k] = v; }
        for (int i = 6; i < s.length(); i++) {
            String pre = s.substring(0, i);
            for (int k = 0; k < K; k++) {
                char p = strats[k].f(pre);
                double d = p != 0 ? (p == s.charAt(i) ? 0.5 : -0.5) : 0;
                for (int m = 0; m < DECS.length; m++) score[m][k] = score[m][k] * DECS[m] + d;
            }
        }
        char[] now = new char[K];
        for (int k = 0; k < K; k++) now[k] = strats[k].f(s);
        double pbSum = 0;
        int cnt = 0;
        for (int m = 0; m < DECS.length; m++) {
            double wB = 0, wP = 0;
            for (int k = 0; k < K; k++) {
                if (now[k] == 0) continue;
                double w = Math.exp(Math.max(-3, Math.min(3, ETA * score[m][k])));
                if (now[k] == 'B') wB += w; else wP += w;
            }
            if (wB + wP > 0) { pbSum += wB / (wB + wP); cnt++; }
        }
        if (cnt == 0) return null;
        int actN = 0;
        for (int k = 0; k < K; k++) if (now[k] != 0) actN++;
        double pb = pbSum / cnt, lean = Math.abs(pb - 0.5);
        if (lean < 0.04) {
            char v = major(10).f(s);
            if (v == 0) v = last(s);
            return new Pred(v, "Chưa có tín hiệu rõ · theo xu hướng");
        }
        char v = pb > 0.5 ? 'B' : 'P';
        int bestK = -1, same = 0;
        for (int k = 0; k < K; k++) {
            if (now[k] != v) continue;
            same++;
            if (bestK < 0 || score[1][k] > score[1][bestK]) bestK = k;
        }
        long lean100 = Math.round((v == 'B' ? pb : 1 - pb) * 100);
        return new Pred(v, names[bestK] + " · " + same + "/" + actN + " đồng thuận · nghiêng " + lean100 + "%");
    }
}
