package com.jfa.core.analyze;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses JDK 8 {@code jstat -gcutil} output and interprets Eden/Survivor/Old
 * (and Metaspace when present) over a short sampling window.
 */
public class JstatGcutilAnalyzer {

    public SampleTrend analyze(String raw) {
        SampleTrend t = new SampleTrend();
        if (raw == null || raw.trim().isEmpty()) {
            t.available = false;
            t.judgment = "unavailable";
            t.summary = "无 jstat 采样输出。";
            return t;
        }
        String[] lines = raw.split("\n");
        int headerIdx = -1;
        String[] headers = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split("\\s+");
            if (looksLikeHeader(cols)) {
                headerIdx = i;
                headers = cols;
                break;
            }
        }
        if (headers == null) {
            t.available = false;
            t.judgment = "unavailable";
            t.summary = "jstat 输出无法解析（无表头）。";
            t.rawNote = trimRaw(raw);
            return t;
        }
        Map<String, Integer> idx = index(headers);
        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split("\\s+");
            if (cols.length < headers.length) {
                continue;
            }
            SampleRow row = new SampleRow();
            row.raw = line;
            row.s0 = pct(cols, idx, "S0");
            row.s1 = pct(cols, idx, "S1");
            row.eden = pct(cols, idx, "E");
            row.old = pct(cols, idx, "O");
            row.meta = firstPct(cols, idx, "M", "P");
            row.ccs = pct(cols, idx, "CCS");
            row.ygc = num(cols, idx, "YGC");
            row.fgc = num(cols, idx, "FGC");
            t.rows.add(row);
        }
        if (t.rows.isEmpty()) {
            t.available = false;
            t.judgment = "unavailable";
            t.summary = "jstat 表头可识别但无采样行。";
            return t;
        }
        t.available = true;
        interpret(t);
        return t;
    }

    private static void interpret(SampleTrend t) {
        SampleRow first = t.rows.get(0);
        SampleRow last = t.rows.get(t.rows.size() - 1);
        double minOld = first.old;
        double maxOld = first.old;
        for (SampleRow r : t.rows) {
            minOld = Math.min(minOld, r.old);
            maxOld = Math.max(maxOld, r.old);
        }
        double oldRange = maxOld - minOld;
        boolean fgcIncreased = last.fgc > first.fgc + 0.5d;
        boolean oldClimbed = last.old > first.old + 5.0d;
        boolean oldNearPeak = last.old >= maxOld - 2.0d;
        boolean oldDroppedAfterPeak = maxOld - last.old >= 5.0d;
        if (oldClimbed && oldNearPeak && (fgcIncreased || last.old > 80.0d)) {
            t.judgment = "climbing_old_no_reclaim";
            t.summary = "采样窗口内 Old 占用持续爬升且未在 Full GC 后回落（首="
                    + fmt(first.old) + "% 末=" + fmt(last.old) + "%，FGC "
                    + fmtNum(first.fgc) + "→" + fmtNum(last.fgc) + "）。倾向保留/泄漏，而非单次抖动。";
        } else if (oldDroppedAfterPeak && oldRange >= 8.0d) {
            t.judgment = "peak_jitter";
            t.summary = "采样窗口内 Old 有峰值后回落（峰值=" + fmt(maxOld) + "% 末="
                    + fmt(last.old) + "%），更像高峰/抖动而非窗口内持续保留。";
        } else if (oldRange <= 5.0d) {
            t.judgment = "stable";
            t.summary = "采样窗口内 Old 相对稳定（" + fmt(minOld) + "%–" + fmt(maxOld)
                    + "%），Eden/Survivor 见采样表。";
        } else {
            t.judgment = "mixed";
            t.summary = "采样窗口内 Old 有波动（" + fmt(minOld) + "%–" + fmt(maxOld)
                    + "%）但尚未形成明确的爬升不回落或峰值回落形态。";
        }
        t.firstOld = first.old;
        t.lastOld = last.old;
        t.minOld = minOld;
        t.maxOld = maxOld;
        t.firstFgc = first.fgc;
        t.lastFgc = last.fgc;
        t.hasMetaspace = !Double.isNaN(first.meta);
    }

    private static boolean looksLikeHeader(String[] cols) {
        boolean e = false;
        boolean o = false;
        for (String c : cols) {
            String u = c.toUpperCase(Locale.ROOT);
            if ("E".equals(u)) {
                e = true;
            }
            if ("O".equals(u)) {
                o = true;
            }
        }
        return e && o;
    }

    private static Map<String, Integer> index(String[] headers) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < headers.length; i++) {
            m.put(headers[i].toUpperCase(Locale.ROOT), i);
        }
        return m;
    }

    private static double pct(String[] cols, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name);
        if (i == null || i >= cols.length) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(cols[i]);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double firstPct(String[] cols, Map<String, Integer> idx, String... names) {
        for (String n : names) {
            double v = pct(cols, idx, n);
            if (!Double.isNaN(v)) {
                return v;
            }
        }
        return Double.NaN;
    }

    private static double num(String[] cols, Map<String, Integer> idx, String name) {
        return pct(cols, idx, name);
    }

    private static String fmt(double v) {
        if (Double.isNaN(v)) {
            return "-";
        }
        return String.format(Locale.US, "%.1f", v);
    }

    private static String fmtNum(double v) {
        if (Double.isNaN(v)) {
            return "-";
        }
        return String.format(Locale.US, "%.0f", v);
    }

    private static String trimRaw(String raw) {
        String t = raw.trim();
        return t.length() > 400 ? t.substring(0, 400) : t;
    }

    public static class SampleRow {
        public String raw;
        public double s0 = Double.NaN;
        public double s1 = Double.NaN;
        public double eden = Double.NaN;
        public double old = Double.NaN;
        public double meta = Double.NaN;
        public double ccs = Double.NaN;
        public double ygc = Double.NaN;
        public double fgc = Double.NaN;
    }

    public static class SampleTrend {
        public boolean available;
        public String judgment = "unavailable";
        public String summary;
        public String rawNote;
        public double firstOld;
        public double lastOld;
        public double minOld;
        public double maxOld;
        public double firstFgc;
        public double lastFgc;
        public boolean hasMetaspace;
        public final List<SampleRow> rows = new ArrayList<SampleRow>();
    }
}
