package com.demo.baccarat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Đọc bảng cầu từ ảnh chụp màn hình (Java thuần, không phụ thuộc Android). */
public final class Vision {
    public static final int ROWS = 6;

    public static final class Blob {
        public final int L;
        public final double x, y, s;
        Blob(int L, double x, double y, double s) { this.L = L; this.x = x; this.y = y; this.s = s; }
    }

    public static final class Read {
        public final String seq;
        public final double x0, y0, x1, y1, size;
        Read(String seq, double x0, double y0, double x1, double y1, double size) {
            this.seq = seq; this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.size = size;
        }
    }

    public static final class Align {
        public final String add;
        public final int off;
        Align(String add, int off) { this.add = add; this.off = off; }
    }

    private static final class Comp {
        int L, x0, x1, y0, y1, a, w, h;
        double cx, cy;
    }

    private static final class Col {
        final List<Blob> items = new ArrayList<>();
        double mx;
    }

    public double lastBeadSize = 0;
    private byte[] lab = new byte[0];
    private byte[] seen = new byte[0];
    private int[] st = new int[0];

    /** 1 = đỏ (Cái), 2 = xanh dương (Con), 3 = xanh lá (Hòa), 0 = khác. */
    static int cls(int r, int g, int b) {
        int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        if (mx < 100 || (mx - mn) < 0.5 * mx) return 0;
        int d = mx - mn;
        double h;
        if (mx == r) h = (((double) (g - b) / d) + 6) % 6;
        else if (mx == g) h = (double) (b - r) / d + 2;
        else h = (double) (r - g) / d + 4;
        h *= 60;
        if (h <= 18 || h >= 340) return 1;
        if (h >= 190 && h <= 250) return 2;
        if (h >= 85 && h <= 170) return 3;
        return 0;
    }

    private static boolean isSingle(Comp c) {
        double ar = (double) c.w / c.h, fill = (double) c.a / (c.w * c.h);
        return c.a >= 25 && c.w >= 6 && c.w <= 70 && ar >= 0.75 && ar <= 1.33 && fill >= 0.45 && fill <= 0.95;
    }

    private static int find(int[] par, int i) {
        while (par[i] != i) { par[i] = par[par[i]]; i = par[i]; }
        return i;
    }

    /** px: điểm ảnh dạng 0xRRGGBB. mask: {x0,y0,x1,y1} vùng bỏ qua (vd: chính trợ lý), hoặc null. */
    public List<Blob> findBlobs(int[] px, int W, int H, int[] mask) {
        final int N = W * H;
        if (lab.length < N) { lab = new byte[N]; seen = new byte[N]; st = new int[N]; }
        java.util.Arrays.fill(seen, 0, N, (byte) 0);
        for (int i = 0; i < N; i++) {
            int p = px[i];
            lab[i] = (byte) cls((p >> 16) & 255, (p >> 8) & 255, p & 255);
        }
        if (mask != null) {
            for (int y = Math.max(0, mask[1]); y < Math.min(H, mask[3]); y++)
                for (int x = Math.max(0, mask[0]); x < Math.min(W, mask[2]); x++) lab[y * W + x] = 0;
        }
        List<Comp> comps = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            if (lab[i] == 0 || seen[i] != 0) continue;
            final byte L = lab[i];
            int sp = 0;
            st[sp++] = i;
            seen[i] = 1;
            int x0 = W, x1 = 0, y0 = H, y1 = 0, a = 0;
            long sx = 0, sy = 0;
            while (sp > 0) {
                int p = st[--sp];
                int x = p % W, y = p / W;
                a++; sx += x; sy += y;
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
                if (x > 0 && seen[p - 1] == 0 && lab[p - 1] == L) { seen[p - 1] = 1; st[sp++] = p - 1; }
                if (x < W - 1 && seen[p + 1] == 0 && lab[p + 1] == L) { seen[p + 1] = 1; st[sp++] = p + 1; }
                if (y > 0 && seen[p - W] == 0 && lab[p - W] == L) { seen[p - W] = 1; st[sp++] = p - W; }
                if (y < H - 1 && seen[p + W] == 0 && lab[p + W] == L) { seen[p + W] = 1; st[sp++] = p + W; }
            }
            if (a < 12 || a > N / 20) continue;
            Comp c = new Comp();
            c.L = L; c.x0 = x0; c.x1 = x1; c.y0 = y0; c.y1 = y1; c.a = a;
            c.cx = (double) sx / a; c.cy = (double) sy / a;
            c.w = x1 - x0 + 1; c.h = y1 - y0 + 1;
            comps.add(c);
        }

        // 1) chọn cỡ chấm theo nhóm chấm cùng cỡ xếp sát nhau thành lưới; ưu tiên nhóm có tổng diện tích lớn nhất
        final TreeMap<Integer, List<Comp>> bySize = new TreeMap<>();
        for (Comp c : comps) {
            if (!isSingle(c)) continue;
            int k = (int) Math.round((c.w + c.h) / 4.0);
            List<Comp> l = bySize.get(k);
            if (l == null) { l = new ArrayList<>(); bySize.put(k, l); }
            l.add(c);
        }
        double dEst = 0, bestScore = 0;
        List<Integer> cand = new ArrayList<>(bySize.keySet());
        Collections.sort(cand, (p, q) -> bySize.get(q).size() - bySize.get(p).size());
        if (cand.size() > 5) cand = new ArrayList<>(cand.subList(0, 5));
        for (int k : cand) {
            List<Comp> set = new ArrayList<>();
            List<Comp> l0 = bySize.get(k - 1), l1 = bySize.get(k), l2 = bySize.get(k + 1);
            if (l0 != null) set.addAll(l0);
            set.addAll(l1);
            if (l2 != null) set.addAll(l2);
            if (set.size() < 3) continue;
            int[] par = new int[set.size()];
            for (int i = 0; i < par.length; i++) par[i] = i;
            double sz = 0;
            for (Comp c : set) sz += (c.w + c.h) / 2.0;
            sz /= set.size();
            for (int i = 0; i < set.size(); i++)
                for (int j = i + 1; j < set.size(); j++)
                    if (Math.hypot(set.get(i).cx - set.get(j).cx, set.get(i).cy - set.get(j).cy) < 2.2 * sz)
                        par[find(par, i)] = find(par, j);
            int[] cnt = new int[par.length];
            int mx = 0;
            for (int i = 0; i < par.length; i++) { int r = find(par, i); cnt[r]++; if (cnt[r] > mx) mx = cnt[r]; }
            if (mx >= 3 && mx * sz * sz > bestScore) { bestScore = mx * sz * sz; dEst = sz; }
        }
        lastBeadSize = dEst;

        // 2) chấm đứng riêng → nhận; cụm chấm dính nhau (khi hình bị thu nhỏ) → tách theo lưới
        List<Blob> out = new ArrayList<>();
        for (Comp c : comps) {
            double fill = (double) c.a / (c.w * c.h);
            if (isSingle(c) && (dEst == 0 || (c.w <= 1.35 * dEst && c.h <= 1.35 * dEst && c.w >= 0.6 * dEst))) {
                out.add(new Blob(c.L, c.cx, c.cy, (c.w + c.h) / 2.0));
                continue;
            }
            if (dEst == 0 || (c.w <= 1.35 * dEst && c.h <= 1.35 * dEst) || fill > 0.9 || fill < 0.25) continue;
            double p = dEst * 1.05;
            int nx = Math.max(1, (int) Math.round((c.w - dEst) / p) + 1);
            int ny = Math.max(1, (int) Math.round((c.h - dEst) / p) + 1);
            if (nx > 60 || ny > 8) continue;
            double stx = nx > 1 ? (c.w - dEst) / (nx - 1) : 0, sty = ny > 1 ? (c.h - dEst) / (ny - 1) : 0;
            double ring = 0.36 * dEst;
            int co = (int) Math.round(0.42 * dEst);
            for (int j = 0; j < ny; j++) {
                for (int i = 0; i < nx; i++) {
                    double cxp = nx > 1 ? c.x0 + dEst / 2 + i * stx : (c.x0 + c.x1) / 2.0;
                    double cyp = ny > 1 ? c.y0 + dEst / 2 + j * sty : (c.y0 + c.y1) / 2.0;
                    int hit = 0;
                    for (int q = 0; q < 8; q++) {
                        int xx = (int) Math.round(cxp + ring * Math.cos(q * Math.PI / 4));
                        int yy = (int) Math.round(cyp + ring * Math.sin(q * Math.PI / 4));
                        if (xx >= 0 && yy >= 0 && xx < W && yy < H && lab[yy * W + xx] == c.L) hit++;
                    }
                    if (hit < 4) continue;
                    int free = 0;
                    for (int sx = -1; sx <= 1; sx += 2)
                        for (int sy = -1; sy <= 1; sy += 2) {
                            int xx = (int) Math.round(cxp + sx * co), yy = (int) Math.round(cyp + sy * co);
                            if (xx < 0 || yy < 0 || xx >= W || yy >= H || lab[yy * W + xx] != c.L) free++;
                        }
                    if (free >= 3) out.add(new Blob(c.L, cxp, cyp, dEst));
                }
            }
        }
        return out;
    }

    private static List<Col> colsOf(List<Blob> items, double size) {
        List<Blob> a = new ArrayList<>(items);
        Collections.sort(a, (p, q) -> Double.compare(p.x, q.x));
        List<Col> cols = new ArrayList<>();
        for (Blob b : a) {
            Col c = cols.isEmpty() ? null : cols.get(cols.size() - 1);
            if (c != null && b.x - c.mx < 0.6 * size) {
                c.items.add(b);
                double s = 0;
                for (Blob q : c.items) s += q.x;
                c.mx = s / c.items.size();
            } else {
                Col n = new Col();
                n.items.add(b);
                n.mx = b.x;
                cols.add(n);
            }
        }
        return cols;
    }

    /** hint: {x, y, r} quanh vị trí bảng cầu lần trước (hoặc null). */
    public Read boardSequence(List<Blob> bl, double[] hint) {
        if (bl.size() < 3) return null;
        int[] par = new int[bl.size()];
        for (int i = 0; i < par.length; i++) par[i] = i;
        for (int i = 0; i < bl.size(); i++)
            for (int j = i + 1; j < bl.size(); j++) {
                Blob a = bl.get(i), b = bl.get(j);
                double r = a.s / b.s;
                if (r < 0.75 || r > 1.33) continue;
                if (Math.hypot(a.x - b.x, a.y - b.y) < 2 * Math.max(a.s, b.s)) par[find(par, i)] = find(par, j);
            }
        TreeMap<Integer, List<Blob>> g = new TreeMap<>();
        for (int i = 0; i < bl.size(); i++) {
            int r = find(par, i);
            List<Blob> l = g.get(r);
            if (l == null) { l = new ArrayList<>(); g.put(r, l); }
            l.add(bl.get(i));
        }
        List<List<Blob>> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<Blob>> e : g.entrySet()) groups.add(e.getValue());
        Collections.sort(groups, (p, q) -> q.size() - p.size());
        List<Blob> best = groups.get(0);
        if (hint != null) {
            for (List<Blob> gr : groups) {
                if (gr.size() < 3) continue;
                boolean near = false;
                for (Blob b : gr) if (Math.hypot(b.x - hint[0], b.y - hint[1]) < 3 * b.s + hint[2]) { near = true; break; }
                if (near) { best = gr; break; }
            }
        }
        if (best.size() < 3) return null;
        double size = 0;
        for (Blob b : best) size += b.s;
        size /= best.size();

        // bảng cầu chỉ có 6 hàng: bỏ các chấm nằm dưới (vd: chú thích B / P / T)
        double y0g = 1e9;
        for (Blob b : best) y0g = Math.min(y0g, b.y);
        List<Double> ds = new ArrayList<>();
        for (Col c : colsOf(best, size)) {
            List<Double> ys = new ArrayList<>();
            for (Blob b : c.items) ys.add(b.y);
            Collections.sort(ys);
            for (int i = 1; i < ys.size(); i++) {
                double d = ys.get(i) - ys.get(i - 1);
                if (d > 0.7 * size && d < 1.8 * size) ds.add(d);
            }
        }
        Collections.sort(ds);
        double pitch = ds.isEmpty() ? size * 1.1 : ds.get(ds.size() >> 1);
        List<Blob> kept = new ArrayList<>();
        for (Blob b : best) if (Math.round((b.y - y0g) / pitch) < ROWS) kept.add(b);
        best = kept;
        if (best.size() < 3) return null;

        // chấm nhiều màu (hổ/rồng đặc biệt) không phân loại được -> ghi 'X' để giữ đúng vị trí ô trong lưới
        List<Col> cols = colsOf(best, size);
        StringBuilder seq = new StringBuilder();
        for (int ci = 0; ci < cols.size(); ci++) {
            char[] slot = new char[ROWS];
            int mr = 0;
            for (Blob b : cols.get(ci).items) {
                int r = (int) Math.min(ROWS - 1, Math.round((b.y - y0g) / pitch));
                if (r < 0) r = 0;
                slot[r] = "BPT".charAt(b.L - 1);
                if (r > mr) mr = r;
            }
            int top = ci < cols.size() - 1 ? ROWS - 1 : mr;
            for (int r = 0; r <= top; r++) seq.append(slot[r] == 0 ? 'X' : slot[r]);
        }
        double x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
        for (Blob b : best) {
            x0 = Math.min(x0, b.x - b.s / 2); y0 = Math.min(y0, b.y - b.s / 2);
            x1 = Math.max(x1, b.x + b.s / 2); y1 = Math.max(y1, b.y + b.s / 2);
        }
        return new Read(seq.toString(), x0, y0, x1, y1, size);
    }

    /**
     * Bảng cầu nhiều game chỉ hiện N cột cuối và cuộn sang trái khi đầy:
     * so khớp cửa sổ mới với cửa sổ cũ (cho phép lệch theo cột) để biết ván mới nào đã ra.
     */
    public static Align align(String w, String n) {
        double bestR = -1;
        int bestOff = -1;
        for (int j = 0; j <= 5; j++) {
            int off = ROWS * j;
            if (off >= w.length()) break;
            String ww = w.substring(off);
            int L = Math.min(ww.length(), n.length());
            if (L < 6) continue;
            int eq = 0;
            for (int i = 0; i < L; i++) if (ww.charAt(i) == n.charAt(i)) eq++;
            double r = (double) eq / L;
            if (bestOff < 0 || r > bestR + 1e-9) { bestR = r; bestOff = off; }
        }
        if (bestOff < 0 || bestR < 0.85) return null;
        int keep = w.length() - bestOff;
        return new Align(n.length() > keep ? n.substring(keep) : "", bestOff);
    }
}
