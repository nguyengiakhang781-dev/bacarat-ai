package com.demo.baccarat;

import java.util.ArrayList;
import java.util.List;

/**
 * Theo dõi ván/bàn từ chuỗi đọc được trên bảng cầu: ván bắt đầu/kết thúc, đúng/sai,
 * đổi bàn, rời bàn, lưu và học từ các bàn cũ. Java thuần (không phụ thuộc Android).
 */
public final class Tracker {
    public interface Store { String load(); void save(String data); }

    /** Ảnh chụp trạng thái cho giao diện. phase: idle | scan | analyze | pred | result | notice. */
    public static final class Ui {
        public int ver;
        public String phase = "idle", title = "Sẵn sàng", sub = "Bấm bắt đầu để mình xem bảng cầu", learn = "";
        public String res = "", last = "";           // res: win | lose | neutral (khi phase = result)
        public Predictor.Pred pred;                  // khi phase = pred
        public int predN;                            // số thứ tự ván được dự đoán
        public long analyzeMs;                       // thời gian "đang phân tích" (để chạy vòng tiến trình)
        public int streak, accOk, accN;
        public boolean simVisible;
        public int evtId;
        public String evtType = "";                  // win | lose | new | switch
        public final List<Boolean> hist = new ArrayList<>();
    }

    private static final class State { String seq, win; char pred; }

    private static final int MINLEN = 20, CAP = 150;

    private final Store store;
    private final Predictor predictor = new Predictor();
    private List<String> tables = new ArrayList<>();
    private int curIdx = -1;
    private State state;
    private boolean gone;
    private String lastSeq = "", lastWin = "", lastRaw = "", pendingSeq = "";
    private int miss, cnt, mism;
    private long actAt = -1;
    private int act;
    private int okN, totN, streak;
    private final List<Boolean> hist = new ArrayList<>();
    private int ver, evtId;
    private String evtType = "";
    private String phase = "idle", title = "Sẵn sàng", sub = "Bấm bắt đầu để mình xem bảng cầu", res = "", last = "", learnTxt = "";
    private Predictor.Pred pred;
    private int predN;
    private long analyzeMs;
    private boolean simVisible;

    public Tracker(Store store) {
        this.store = store;
        String d = store == null ? "" : store.load();
        if (d != null) for (String t : d.split(",")) if (!t.isEmpty()) tables.add(t);
        relearn();
    }

    // ---------- giao diện ----------
    public synchronized Ui ui() {
        Ui u = new Ui();
        u.ver = ver; u.phase = phase; u.title = title; u.sub = sub; u.res = res; u.last = last; u.pred = pred; u.predN = predN;
        u.analyzeMs = analyzeMs; u.streak = streak; u.accOk = okN; u.accN = totN; u.learn = learnTxt; u.simVisible = simVisible;
        u.evtId = evtId; u.evtType = evtType;
        u.hist.addAll(hist.subList(Math.max(0, hist.size() - 14), hist.size()));
        return u;
    }

    private void say(String ph, String t, String s) {
        ver++; phase = ph; title = t; sub = s; res = ""; last = ""; pred = null; analyzeMs = 0;
    }

    private void fire(String type) { evtId++; evtType = type; }

    private String scoreTxt() { return totN > 0 ? "đúng " + okN + "/" + totN + " (" + Math.round(okN * 100.0 / totN) + "%)" : ""; }

    private static String name(char r) { return r == 'B' ? "CÁI" : r == 'P' ? "CON" : r == 'T' ? "HÒA" : "?"; }

    private void updL() {
        int fin = tables.size() - (curIdx >= 0 ? 1 : 0), hands = 0;
        for (int i = 0; i < tables.size(); i++) if (i != curIdx) hands += tables.get(i).length();
        learnTxt = "🧠 Đã học " + fin + " bàn · " + hands + " ván" + (curIdx >= 0 ? " · đang ở bàn #" + (fin + 1) : "");
    }

    // ---------- lưu & học ----------
    private void save() {
        if (store == null) return;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tables.size(); i++) { if (i > 0) b.append(','); b.append(tables.get(i)); }
        store.save(b.toString());
    }

    private void relearn() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) if (i != curIdx) list.add(tables.get(i));
        if (list.size() > 60) list = list.subList(list.size() - 60, list.size());
        Predictor.Learner L = predictor.newLearner();
        for (String s : list) predictor.learnAdd(L, s);
        predictor.learn = L;
        updL();
    }

    public synchronized void clearLearned() {
        List<String> keep = new ArrayList<>();
        if (curIdx >= 0) keep.add(tables.get(curIdx));
        tables = keep;
        curIdx = keep.isEmpty() ? -1 : 0;
        save();
        predictor.learn = null;
        updL();
    }

    private void archive() {
        if (curIdx < 0) return;
        if (tables.get(curIdx).length() < MINLEN) tables.remove(curIdx);
        curIdx = -1;
        save();
        relearn();
    }

    private void resetStats() { okN = totN = streak = 0; hist.clear(); }

    // ---------- máy trạng thái ----------
    private void schedule(long at, int a) { actAt = at; act = a; }

    private void advance(long now) {
        if (actAt >= 0 && now >= actAt) {
            int a = act;
            actAt = -1; act = 0;
            if (a == 1) analyze(now, 1200);
            else if (a == 2) { if (state != null) showPred(); }
            else if (a == 3) beginTable(lastRaw.isEmpty() ? pendingSeq : lastRaw, now);
        }
    }

    /** Gọi định kỳ (vd: 4 lần/giây) để các pha theo thời gian chạy đúng giờ. */
    public synchronized void tickTime(long now) { advance(now); }

    private void analyze(long now, int ms) {
        say("analyze", "Đang phân tích…", state.seq.length() + " ván trên bảng cầu");
        analyzeMs = ms;
        schedule(now + ms, 2);
    }

    private void showPred() {
        Predictor.Pred p = predictor.predict(state.seq);
        state.pred = p == null ? 0 : p.v;
        if (p == null) { say("analyze", "Đang thu thập…", state.seq.length() + " ván đã đọc · cần ≥ 8 ván"); return; }
        predN = state.seq.length() + 1;
        say("pred", "Ván tới: " + name(p.v), "Dự đoán cho ván #" + predN);
        pred = p;
    }

    private void beginTable(String n, long now) {
        state = new State();
        state.seq = n; state.win = n;
        gone = false; miss = 0; mism = 0;
        tables.add(n);
        curIdx = tables.size() - 1;
        if (tables.size() > CAP) { tables.remove(0); curIdx--; }
        save(); updL();
        simVisible = true;
        fire("new");
        analyze(now, 2000);
    }

    private void switchTable(String n, String why, long now) {
        archive(); resetStats();
        state = null;
        pendingSeq = n;
        say("notice", "Đã đổi bàn", why + " · đã lưu bàn cũ để học");
        fire("switch");
        schedule(now + 1200, 3);
    }

    private void onGone() {
        lastSeq = state.seq; lastWin = state.win;
        archive();
        state = null; gone = true; actAt = -1;
        say("notice", "Đã rời bàn", "Đã lưu bàn cũ để học · chờ bàn mới");
        fire("switch");
    }

    private void onSeq(String n, long now) {
        if (state == null) {
            Vision.Align al = null;
            if (gone && !lastWin.isEmpty() && !lastSeq.isEmpty() && !tables.isEmpty() && tables.get(tables.size() - 1).equals(lastSeq))
                al = n.equals(lastWin) ? new Vision.Align("", 0) : Vision.align(lastWin, n);
            if (al != null) {
                curIdx = tables.size() - 1;
                state = new State();
                state.seq = lastSeq + al.add; state.win = n;
                tables.set(curIdx, state.seq);
                gone = false; miss = 0;
                save(); relearn(); simVisible = true;
                analyze(now, 1500);
                return;
            }
            if (n.length() < 8) {
                say("scan", "Đang tìm bảng cầu…", "Mới thấy " + n.length() + " ván trên bảng cầu · chờ đủ 8 ván để bắt đầu");
                return;
            }
            beginTable(n, now);
            return;
        }
        String w = state.win;
        if (n.equals(w)) return;
        Vision.Align al;
        if (Math.min(w.length(), n.length()) < 6) {
            if (n.startsWith(w)) al = new Vision.Align(n.substring(w.length()), 0);
            else if (w.startsWith(n) && n.length() >= w.length() - 2) return;
            else al = null;
        } else al = Vision.align(w, n);
        if (al == null) {
            if (++mism >= 3) { mism = 0; switchTable(n, "Bảng cầu khác hẳn", now); }
            return;
        }
        mism = 0;
        state.win = n;
        String add = al.add;
        if (add.isEmpty()) return;
        int verdict = -1; // -1 chưa chấm, 0 sai, 1 đúng
        boolean judge = add.length() <= 2;
        for (int i = 0; judge && i < add.length(); i++) {
            char r = add.charAt(i);
            if (r == 'T' || r == 'X' || state.pred == 0) continue;
            boolean ok = r == state.pred;
            totN++; hist.add(ok);
            if (ok) { okN++; streak++; } else streak = 0;
            verdict = ok ? 1 : 0;
            state.pred = 0;
        }
        char r = add.charAt(add.length() - 1);
        state.seq += add;
        if (curIdx >= 0) { tables.set(curIdx, state.seq); save(); }
        updL();
        int N = state.seq.length();
        String head = verdict < 0
                ? "Ván #" + N + ": " + (r == 'X' ? "không rõ (hổ/rồng)" : name(r)) + (r == 'T' ? " (không tính)" : "")
                : (verdict == 1 ? "✓ ĐOÁN ĐÚNG" : "✗ ĐOÁN SAI") + " · " + name(r);
        String tail = judge ? "Ván #" + N + " xong · ván mới bắt đầu" + (streak > 1 ? " · chuỗi đúng " + streak : "")
                : "Bỏ lỡ " + add.length() + " ván (không chấm đúng/sai) · ván mới bắt đầu";
        say("result", head, tail);
        res = verdict < 0 ? "neutral" : verdict == 1 ? "win" : "lose";
        last = String.valueOf(r);
        if (verdict >= 0) fire(verdict == 1 ? "win" : "lose");
        schedule(now + 1400, 1);
    }

    /**
     * Gọi sau mỗi lần quét. seq = null nếu không thấy bảng cầu.
     * beads/beadSize/w/h chỉ để hiển thị chẩn đoán khi chưa thấy bảng cầu.
     */
    public synchronized void onRead(String seq, int beads, double beadSize, int w, int h, long now) {
        advance(now);
        if (act == 3 && actAt >= 0) return; // đang chuyển bàn
        if (seq == null) {
            miss++;
            if (state != null && miss >= 10 && actAt < 0) onGone();
            else if (state == null && actAt < 0 && !gone)
                say("scan", "Đang tìm bảng cầu…", "Thấy " + beads + " chấm (cỡ " + Math.round(beadSize) + "px) · ảnh " + w + "×" + h + " · hãy để bảng cầu hiện rõ");
            return;
        }
        miss = 0;
        if (seq.equals(lastRaw)) cnt++; else { lastRaw = seq; cnt = 1; }
        if (cnt >= 2 && (state == null || !seq.equals(state.win))) onSeq(seq, now);
    }

    /** Khi người dùng bấm "Dừng". */
    public synchronized void stop() {
        archive();
        state = null; gone = false; actAt = -1; act = 0; simVisible = false; miss = 0; cnt = 0; lastRaw = "";
        say("idle", "Đã dừng", "Bấm bắt đầu để xem lại");
    }

    /** Khi người dùng bấm "Bắt đầu". */
    public synchronized void start() {
        state = null; gone = false; actAt = -1; act = 0; miss = 0; cnt = 0; mism = 0; lastRaw = ""; lastSeq = ""; lastWin = "";
        curIdx = -1; resetStats(); simVisible = false;
        say("scan", "Đang tìm bảng cầu…", "Mình đang xem màn hình");
        updL();
    }

    public synchronized boolean hasTable() { return state != null; }

    /** Bản sao danh sách các bàn đã lưu (bàn đang chơi nằm ở vị trí currentIndex()). */
    public synchronized List<String> tablesSnapshot() { return new ArrayList<>(tables); }

    /** Vị trí bàn đang chơi trong danh sách, hoặc -1. */
    public synchronized int currentIndex() { return curIdx; }

    /** Xóa một bàn đã kết thúc khỏi bộ nhớ học. Không xóa bàn đang chơi. */
    public synchronized void deleteTable(int idx) {
        if (idx < 0 || idx >= tables.size() || idx == curIdx) return;
        tables.remove(idx);
        if (curIdx > idx) curIdx--;
        save();
        relearn();
    }
}
