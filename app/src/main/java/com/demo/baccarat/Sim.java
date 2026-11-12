package com.demo.baccarat;

import java.util.Random;

/** Mô phỏng baccarat thật (8 bộ bài, luật rút lá thứ ba) để kiểm chứng tỉ lệ đoán đúng. */
public final class Sim {
    public interface Progress { void on(String phase, int k, int n); }

    public static final class Result {
        public final int n;
        public final double accA, accB, base, ci;
        Result(int n, double accA, double accB, double base, double ci) { this.n = n; this.accA = accA; this.accB = accB; this.base = base; this.ci = ci; }
    }

    private Sim() {}

    public static String dealShoe(Random rnd) {
        int[] sh = new int[416];
        int top = 0;
        for (int d = 0; d < 8; d++) for (int s = 0; s < 4; s++) for (int r = 1; r <= 13; r++) sh[top++] = r > 9 ? 0 : r;
        for (int i = sh.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = sh[i]; sh[i] = sh[j]; sh[j] = t; }
        StringBuilder seq = new StringBuilder();
        while (top > 14) {
            int p = (sh[--top] + sh[--top]) % 10;
            int b = (sh[--top] + sh[--top]) % 10;
            if (p < 8 && b < 8) {
                int p3 = -1;
                if (p <= 5) { p3 = sh[--top]; p = (p + p3) % 10; }
                boolean draw;
                if (p3 < 0) draw = b <= 5;
                else draw = b <= 2 || (b == 3 && p3 != 8) || (b == 4 && p3 >= 2 && p3 <= 7) || (b == 5 && p3 >= 4 && p3 <= 7) || (b == 6 && (p3 == 6 || p3 == 7));
                if (draw) b = (b + sh[--top]) % 10;
            }
            seq.append(p > b ? 'P' : b > p ? 'B' : 'T');
        }
        return seq.toString();
    }

    public static Result run(int trainN, int testN, Progress prog) {
        Random rnd = new Random();
        Predictor pr = new Predictor();
        Predictor.Learner L = pr.newLearner();
        for (int k = 1; k <= trainN; k++) { pr.learnAdd(L, dealShoe(rnd)); if (prog != null) prog.on("học", k, trainN); }
        int n = 0, okA = 0, okB = 0, bok = 0;
        for (int k = 1; k <= testN; k++) {
            String seq = dealShoe(rnd);
            for (int i = 8; i < seq.length(); i++) {
                char r = seq.charAt(i);
                if (r == 'T') continue;
                String pre = seq.substring(0, i);
                Predictor.Pred pa = pr.predict(pre, L), pb = pr.predict(pre, null);
                if (pa == null || pb == null) continue;
                n++;
                if (pa.v == r) okA++;
                if (pb.v == r) okB++;
                if (r == 'B') bok++;
            }
            if (prog != null) prog.on("thử", k, testN);
        }
        if (n == 0) return new Result(0, 0, 0, 0, 0);
        return new Result(n, (double) okA / n, (double) okB / n, (double) bok / n, 1.96 * Math.sqrt(0.25 / n));
    }
}
