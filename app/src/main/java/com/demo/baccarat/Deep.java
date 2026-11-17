package com.demo.baccarat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Phân tích sâu: thống kê trung thực (Java thuần). Kiểm định ngẫu nhiên, chấm điểm phương án, hiệu chỉnh, đối chứng, rủi ro vốn. */
public final class Deep {
    private Deep() {}

    public static final double TB = 0.458597, TP = 0.446247, TT = 0.095156;
    public static final double PB_NT = TB / (TB + TP);
    public static final double EV_B = TB * 0.95 - TP, EV_P = TP - TB, EV_T = 8 * TT - (1 - TT);

    // ---------- hàm thống kê ----------
    static double erfc(double x) {
        double z = Math.abs(x), t = 1 / (1 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? r : 2 - r;
    }
    static double normTwo(double z) { return erfc(Math.abs(z) / Math.sqrt(2)); }
    static double normUp(double z) { return 0.5 * erfc(z / Math.sqrt(2)); }
    static double lgamma(double x) {
        double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x, t = x + 5.5;
        t -= (x + 0.5) * Math.log(t);
        double s = 1.000000000190015;
        for (int j = 0; j < 6; j++) s += c[j] / ++y;
        return -t + Math.log(2.5066282746310005 * s / x);
    }
    static double gammq(double a, double x) {
        if (x < 0 || a <= 0) return 1;
        if (x < a + 1) {
            double ap = a, sum = 1 / a, del = sum;
            for (int n = 0; n < 300; n++) { ap++; del *= x / ap; sum += del; if (Math.abs(del) < Math.abs(sum) * 1e-12) break; }
            return 1 - sum * Math.exp(-x + a * Math.log(x) - lgamma(a));
        }
        double b = x + 1 - a, c = 1e300, d = 1 / b, h = d;
        for (int i = 1; i < 300; i++) {
            double an = -i * (i - a);
            b += 2; d = an * d + b; if (Math.abs(d) < 1e-300) d = 1e-300;
            c = b + an / c; if (Math.abs(c) < 1e-300) c = 1e-300;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-12) break;
        }
        return Math.exp(-x + a * Math.log(x) - lgamma(a)) * h;
    }
    static double chi2sf(double x, int df) { return x <= 0 ? 1 : gammq(df / 2.0, x / 2); }
    static double[] wilson(int k, int n) {
        if (n == 0) return new double[]{0, 1};
        double z = 1.96, p = (double) k / n, d = 1 + z * z / n, c = p + z * z / (2 * n), h = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n));
        return new double[]{(c - h) / d, (c + h) / d};
    }

    // ---------- kết quả ----------
    public static final class Strat { public String name; public int h, n; public double acc, z, p, padj; public double[] ci; }
    public static final class Test { public String name; public int n; public double stat, p; }
    public static final class Bin { public int a, b, n, ok; public double acc; public double[] ci; }
    public static final class Risk { public String name; public double mean, win, ruin, p5, p50, p95; }
    public static final class Result {
        public int tables, hands, predN, rawSig, adjSig, serialBonf;
        public int nB, nP, nT; public double freqChi, freqP;
        public double modelAcc, predBShare, calR, calP;
        public final List<Bin> bins = new ArrayList<>();
        public final List<Test> serial = new ArrayList<>();
        public final List<Strat> strat = new ArrayList<>();
        public final List<Risk> risk = new ArrayList<>();
        public int ctlTables, ctlHands, ctlRaw, ctlAdj, serialSig;
    }

    static List<Strat> score(Predictor pr, List<String> tables) {
        List<Predictor.Score> raw = pr.scoreStrategies(tables);
        List<Strat> out = new ArrayList<>();
        int m = raw.size();
        for (Predictor.Score s : raw) {
            Strat r = new Strat();
            r.name = s.name; r.h = s.h; r.n = s.n;
            r.acc = s.n > 0 ? (double) s.h / s.n : 0;
            r.z = s.n > 0 ? (s.h - 0.5 * s.n) / Math.sqrt(0.25 * s.n) : 0;
            r.p = s.n > 0 ? normUp(r.z) : 1;
            r.padj = Math.min(1, r.p * m);
            r.ci = wilson(s.h, s.n);
            out.add(r);
        }
        return out;
    }

    private static int[] runsOf(String s) {
        List<Integer> r = new ArrayList<>();
        int i = 0;
        while (i < s.length()) { int j = i; while (j < s.length() && s.charAt(j) == s.charAt(i)) j++; r.add(j - i); i = j; }
        int[] a = new int[r.size()];
        for (int k = 0; k < a.length; k++) a[k] = r.get(k);
        return a;
    }

    static List<Test> serialTests(List<String> seqs) {
        List<Test> out = new ArrayList<>();
        for (int lag = 1; lag <= 5; lag++) {
            int[][] t = new int[2][2];
            for (String s : seqs) for (int i = 0; i + lag < s.length(); i++) t[s.charAt(i) == 'B' ? 0 : 1][s.charAt(i + lag) == 'B' ? 0 : 1]++;
            int n = t[0][0] + t[0][1] + t[1][0] + t[1][1];
            double r0 = t[0][0] + t[0][1], r1 = t[1][0] + t[1][1], c0 = t[0][0] + t[1][0], c1 = t[0][1] + t[1][1];
            double chi = 0;
            if (n > 0 && r0 > 0 && r1 > 0 && c0 > 0 && c1 > 0) {
                double[][] e = {{r0 * c0 / n, r0 * c1 / n}, {r1 * c0 / n, r1 * c1 / n}};
                for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) chi += Math.pow(t[a][b] - e[a][b], 2) / e[a][b];
            }
            Test x = new Test();
            x.name = "Liên hệ giữa ván i và ván i+" + lag; x.n = n; x.stat = chi; x.p = n > 0 ? chi2sf(chi, 1) : 1;
            out.add(x);
        }
        return out;
    }

    static Test runsTest(List<String> seqs) {
        double zs = 0; int k = 0;
        for (String s : seqs) {
            int n = s.length(), nB = 0;
            for (int i = 0; i < n; i++) if (s.charAt(i) == 'B') nB++;
            int nP = n - nB;
            if (n < 10 || nB == 0 || nP == 0) continue;
            int R = runsOf(s).length;
            double mu = 1 + 2.0 * nB * nP / n, vr = (mu - 1) * (mu - 2) / (n - 1);
            if (vr <= 0) continue;
            zs += (R - mu) / Math.sqrt(vr); k++;
        }
        double z = k > 0 ? zs / Math.sqrt(k) : 0;
        Test t = new Test();
        t.name = "Số chuỗi bệt/nhảy (gộp " + k + " bàn)"; t.n = k; t.stat = z; t.p = k > 0 ? normTwo(z) : 1;
        return t;
    }

    static Test runLengthTest(List<String> seqs) {
        int[][] cnt = new int[2][5];
        for (String s : seqs) {
            int i = 0;
            while (i < s.length()) {
                int j = i; while (j < s.length() && s.charAt(j) == s.charAt(i)) j++;
                cnt[s.charAt(i) == 'B' ? 0 : 1][Math.min(j - i, 5) - 1]++;
                i = j;
            }
        }
        double chi = 0; int tot = 0;
        for (int c = 0; c < 2; c++) {
            double q = c == 0 ? PB_NT : 1 - PB_NT;
            int n = 0; for (int v : cnt[c]) n += v;
            if (n < 20) continue;
            double[] pr = {1 - q, q * (1 - q), q * q * (1 - q), q * q * q * (1 - q), q * q * q * q};
            for (int i = 0; i < 5; i++) { double e = n * pr[i]; chi += Math.pow(cnt[c][i] - e, 2) / e; }
            tot += n;
        }
        Test t = new Test();
        t.name = "Phân bố độ dài chuỗi bệt"; t.n = tot; t.stat = chi; t.p = tot > 0 ? chi2sf(chi, 8) : 1;
        return t;
    }

    static void calibrate(Result R, List<int[]> pts) {
        int[][] edges = {{50, 55}, {55, 65}, {65, 75}, {75, 85}, {85, 101}};
        for (int[] e : edges) { Bin b = new Bin(); b.a = e[0]; b.b = Math.min(e[1], 100); R.bins.add(b); }
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (int[] q : pts) {
            double lean = q[0], ok = q[1];
            for (int i = 0; i < edges.length; i++) if (lean >= edges[i][0] && lean < edges[i][1]) { R.bins.get(i).n++; R.bins.get(i).ok += q[1]; break; }
            sx += lean; sy += ok; sxx += lean * lean; syy += ok * ok; sxy += lean * ok;
        }
        int n = pts.size();
        double den = Math.sqrt((n * sxx - sx * sx) * (n * syy - sy * sy));
        double r = n > 2 && den > 0 ? (n * sxy - sx * sy) / den : 0;
        double z = Math.abs(r) * Math.sqrt(Math.max(0, n - 2));
        for (Bin b : R.bins) { b.acc = b.n > 0 ? (double) b.ok / b.n : 0; b.ci = wilson(b.ok, b.n); }
        R.calR = r; R.calP = n > 10 ? normTwo(z) : 1;
    }

    static List<Risk> bankrollSim(double bank, int hands, int sessions, double qB, Random rnd) {
        String[] names = {"Luôn đặt Cái (hoa hồng 5%)", "Luôn đặt Con", "Theo dự đoán của tool", "Luôn đặt Hòa (ăn 8:1)"};
        List<Risk> out = new ArrayList<>();
        for (int si = 0; si < 4; si++) {
            double[] finals = new double[sessions];
            int ruin = 0;
            for (int s = 0; s < sessions; s++) {
                double b = bank; boolean ruined = false;
                for (int h = 0; h < hands && b > 0; h++) {
                    double u = rnd.nextDouble();
                    char o = u < TB ? 'B' : u < TB + TP ? 'P' : 'T';
                    char bet = si == 0 ? 'B' : si == 1 ? 'P' : si == 2 ? (rnd.nextDouble() < qB ? 'B' : 'P') : 'T';
                    if (o == 'T') { if (bet == 'T') b += 8; else continue; }
                    else if (bet == 'T') b -= 1;
                    else if (bet == o) b += bet == 'B' ? 0.95 : 1;
                    else b -= 1;
                    if (b <= 0) { ruined = true; break; }
                }
                if (ruined) ruin++;
                finals[s] = Math.max(0, b);
            }
            java.util.Arrays.sort(finals);
            double sum = 0; int win = 0;
            for (double f : finals) { sum += f; if (f > bank) win++; }
            Risk r = new Risk();
            r.name = names[si]; r.mean = sum / sessions - bank; r.win = (double) win / sessions; r.ruin = (double) ruin / sessions;
            r.p5 = finals[Math.min(sessions - 1, (int) (0.05 * sessions))] - bank;
            r.p50 = finals[Math.min(sessions - 1, (int) (0.5 * sessions))] - bank;
            r.p95 = finals[Math.min(sessions - 1, (int) (0.95 * sessions))] - bank;
            out.add(r);
        }
        return out;
    }

    /** Chạy toàn bộ bộ phân tích (nặng: nên chạy ở luồng nền). tables: các bàn đã lưu (kể cả bàn đang chơi). */
    public static Result run(List<String> tables, int curIdx) {
        Result R = new Result();
        R.tables = tables.size();
        for (String s : tables) for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == 'B') R.nB++; else if (c == 'P') R.nP++; else if (c == 'T') R.nT++; }
        int n = R.nB + R.nP + R.nT; R.hands = n;
        if (n > 0) {
            double[][] eo = {{R.nB, TB}, {R.nP, TP}, {R.nT, TT}};
            for (double[] x : eo) R.freqChi += Math.pow(x[0] - n * x[1], 2) / (n * x[1]);
            R.freqP = chi2sf(R.freqChi, 2);
        } else R.freqP = 1;
        List<String> seqs = new ArrayList<>();
        for (String t : tables) { String s = Predictor.strip(t); if (s.length() >= 10) seqs.add(s); }
        Analysis.Report rep = Analysis.run(tables, curIdx);
        List<Analysis.TableReport> all = new ArrayList<>(rep.past);
        if (rep.current != null) all.add(rep.current);
        List<int[]> pts = new ArrayList<>();
        long predB = 0; int ok = 0;
        for (Analysis.TableReport t : all) for (int[] p : t.pts) { pts.add(p); predB += p[2]; ok += p[1]; }
        calibrate(R, pts);
        R.predN = pts.size(); R.predBShare = pts.isEmpty() ? 0.5 : (double) predB / pts.size(); R.modelAcc = pts.isEmpty() ? 0 : (double) ok / pts.size();
        R.serial.addAll(serialTests(seqs));
        R.serial.add(runsTest(seqs));
        R.serial.add(runLengthTest(seqs));
        R.serialBonf = R.serial.size();
        Predictor pr = new Predictor();
        R.strat.addAll(score(pr, tables));
        Collections.sort(R.strat, (a, b) -> Double.compare(a.p, b.p));
        Random rnd = new Random();
        int K = Math.max(15, Math.min(40, tables.size()));
        List<String> ctrl = new ArrayList<>();
        for (int i = 0; i < K; i++) ctrl.add(Sim.dealShoe(rnd));
        List<Strat> cs = score(pr, ctrl);
        R.ctlTables = K;
        for (String s : ctrl) R.ctlHands += s.length();
        for (Strat s : cs) { if (s.p < 0.05) R.ctlRaw++; if (s.padj < 0.05) R.ctlAdj++; }
        for (Strat s : R.strat) { if (s.p < 0.05) R.rawSig++; if (s.padj < 0.05) R.adjSig++; }
        R.risk.addAll(bankrollSim(100, 100, 4000, R.predBShare, rnd));
        for (Test t : R.serial) if (Math.min(1, t.p * R.serialBonf) < 0.05) R.serialSig++;
        return R;
    }

    // ---------- diễn giải ----------
    public static String pct(double x, int d) { return String.format(Locale.US, "%." + d + "f%%", x * 100).replace('.', ','); }
    static String fp(double p) { return p < 0.001 ? "<0,001" : String.format(Locale.US, "%.3f", p).replace('.', ','); }
    static String num(double x, int d) { return String.format(Locale.US, "%." + d + "f", x).replace('.', ','); }

    public static boolean flagged(Result D) {
        return D.predN >= 300 && (D.adjSig + D.serialSig + (D.freqP < 0.01 ? 1 : 0) + (D.calP < 0.01 ? 1 : 0)) > 0;
    }

    public static String verdict(Result D) {
        if (D.predN < 300) return "Dữ liệu còn ít (" + D.predN + " lần đoán đã đọc lại, " + D.hands + " ván): chưa đủ để kết luận gì chắc chắn. Hãy để tool xem thêm các bàn.";
        if (!flagged(D)) return "Không tìm thấy bằng chứng nào cho thấy lịch sử bảng cầu giúp đoán được ván sau. Sau khi hiệu chỉnh vì thử nhiều phương án cùng lúc, có " + D.adjSig + "/" + D.strat.size() + " phương án vượt ngẫu nhiên, và mức \"nghiêng\" của mô hình không liên quan tới việc đoán đúng.";
        List<String> parts = new ArrayList<>();
        if (D.adjSig > 0) parts.add(D.adjSig + " phương án vượt ngẫu nhiên sau hiệu chỉnh");
        if (D.serialSig > 0) parts.add(D.serialSig + " kiểm định ngẫu nhiên bị lệch");
        if (D.freqP < 0.01) parts.add("tần suất Cái/Con/Hòa lệch so với lý thuyết");
        if (D.calP < 0.01) parts.add("mức nghiêng của mô hình có liên hệ với đúng/sai");
        return "Có dấu hiệu đáng chú ý: " + String.join("; ", parts) + ". Hãy kiểm chứng lại trên dữ liệu mới trước khi tin, vì kết quả thống kê trên dữ liệu cũ rất dễ chỉ là trùng hợp.";
    }

    public static final class Sec {
        public final String title, tone;
        public final List<String[]> lines = new ArrayList<>(); // {nội dung, kiểu: "" | dim | warn | ok}
        Sec(String t, String tone) { title = t; this.tone = tone; }
        Sec add(String text, String cls) { lines.add(new String[]{text, cls}); return this; }
    }

    public static List<Sec> sections(Result D) {
        List<Sec> out = new ArrayList<>();
        out.add(new Sec("Kết luận", D.predN < 300 || flagged(D) ? "amber" : "green").add(verdict(D), ""));
        Sec th = new Sec("Xác suất thật của mỗi ván (lý thuyết, 8 bộ bài)", "");
        th.add("Cái " + pct(TB, 2) + " · Con " + pct(TP, 2) + " · Hòa " + pct(TT, 2), "");
        th.add("Kỳ vọng mỗi 1 đơn vị cược: Cái " + pct(EV_B, 2) + " · Con " + pct(EV_P, 2) + " · Hòa " + pct(EV_T, 2) + " (luôn âm: đó là lợi thế của nhà cái).", "dim");
        double n = Math.max(1, D.hands);
        th.add("Dữ liệu của bạn (" + D.hands + " ván): Cái " + pct(D.nB / n, 1) + " · Con " + pct(D.nP / n, 1) + " · Hòa " + pct(D.nT / n, 1) + " → " + (D.freqP < 0.01 ? "lệch khỏi lý thuyết" : "khớp lý thuyết") + " (p = " + fp(D.freqP) + ")", "");
        out.add(th);
        Sec se = new Sec("Kiểm định ngẫu nhiên (hiệu chỉnh Bonferroni cho " + D.serialBonf + " kiểm định)", "");
        for (Test t : D.serial) { double pa = Math.min(1, t.p * D.serialBonf); se.add((pa < 0.05 ? "⚠ " : "✓ ") + t.name + " · p = " + fp(t.p) + " · p hiệu chỉnh = " + fp(pa), pa < 0.05 ? "warn" : ""); }
        se.add("Dấu ✓ nghĩa là không thấy bằng chứng bảng cầu có quy luật; ⚠ nghĩa là có độ lệch đáng chú ý.", "dim");
        out.add(se);
        Sec st = new Sec("Chấm điểm từng phương án cầu (" + D.strat.size() + " phương án)", "");
        st.add("Luôn đoán Cái cho kết quả ≈ " + pct(PB_NT, 1) + " trong các ván không Hòa. p thô = \"có hơn ngẫu nhiên không\"; p hiệu chỉnh trừ đi may mắn do thử nhiều phương án.", "dim");
        for (int i = 0; i < Math.min(8, D.strat.size()); i++) { Strat r = D.strat.get(i); st.add(r.name + " · " + pct(r.acc, 1) + " (n=" + r.n + ") · p thô " + fp(r.p) + " · hiệu chỉnh " + fp(r.padj), r.padj < 0.05 ? "ok" : ""); }
        st.add("Trước hiệu chỉnh có " + D.rawSig + " phương án \"có vẻ\" vượt ngẫu nhiên; sau hiệu chỉnh còn " + D.adjSig + ".", D.adjSig > 0 ? "ok" : "dim");
        out.add(st);
        Sec ca = new Sec("Độ nghiêng của mô hình có đáng tin không?", "");
        for (Bin b : D.bins) ca.add("Nghiêng " + b.a + "–" + b.b + "% → thực tế đúng " + (b.n > 0 ? pct(b.acc, 1) + " (n=" + b.n + ", khoảng " + pct(b.ci[0], 0) + "–" + pct(b.ci[1], 0) + ")" : "chưa có dữ liệu"), "");
        ca.add("Tương quan giữa mức nghiêng và việc đoán đúng: r = " + num(D.calR, 3) + " (p = " + fp(D.calP) + ") → " + (D.calP < 0.01 ? "có liên hệ" : "không liên hệ: \"nghiêng 90%\" không có nghĩa là 90% đúng") + ".", "");
        out.add(ca);
        Sec co = new Sec("Nhóm đối chứng (dữ liệu ngẫu nhiên thật)", "");
        co.add(D.ctlTables + " bàn mô phỏng (" + D.ctlHands + " ván) cho " + D.ctlRaw + " phương án \"có vẻ hiệu quả\" trước hiệu chỉnh và " + D.ctlAdj + " sau hiệu chỉnh. Dữ liệu của bạn: " + D.rawSig + " và " + D.adjSig + ".", "");
        co.add("Nếu hai con số gần nhau thì chuyện \"có vẻ hiệu quả\" chỉ là trùng hợp.", "dim");
        out.add(co);
        Sec ri = new Sec("Rủi ro vốn (mô phỏng 4.000 phiên × 100 ván, cược 1 đơn vị, vốn 100)", "");
        for (Risk r : D.risk) ri.add(r.name + ": trung bình " + (r.mean >= 0 ? "+" : "") + num(r.mean, 1) + " · lãi " + pct(r.win, 0) + " · 5–95%: " + String.format(Locale.US, "%.0f", r.p5) + " đến " + (r.p95 >= 0 ? "+" : "") + String.format(Locale.US, "%.0f", r.p95) + " · cháy vốn " + pct(r.ruin, 1), "");
        ri.add("Dùng xác suất lý thuyết, mỗi ván độc lập. Mọi cách cược đều có kỳ vọng âm.", "dim");
        out.add(ri);
        return out;
    }
}
