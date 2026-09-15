package com.jfa.core.analyze.hprof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Product-side dual-hprof histogram compare (no external GUI).
 */
public class HprofComparer {

    public static final String LEAK_OR_RETENTION = "leak_or_retention";
    public static final String PEAK_OR_JITTER = "peak_or_jitter";
    public static final String STABLE_IN_WINDOW = "stable_in_window";
    public static final String SINGLE_SNAPSHOT_ONLY = "single_snapshot_only";

    public CompareResult compare(HprofParser.HprofSummary older, HprofParser.HprofSummary newer,
                                 int topN, List<String> priorSuspectClasses) {
        CompareResult r = new CompareResult();
        r.topN = topN <= 0 ? 20 : topN;
        if (newer == null) {
            r.judgment = SINGLE_SNAPSHOT_ONLY;
            r.summary = "仅有一份堆快照，无法做间隔增长对比（single-snapshot-only）。";
            if (older != null) {
                fillHeap(r, older, older);
            }
            return r;
        }
        if (older == null) {
            r.judgment = SINGLE_SNAPSHOT_ONLY;
            r.summary = "仅有较新一份堆快照，缺少对比基线（single-snapshot-only）。";
            fillHeap(r, newer, newer);
            return r;
        }
        r.dump1Used = older.approxUsedBytes;
        r.dump2Used = newer.approxUsedBytes;
        r.dump1Capacity = Math.max(older.approxUsedBytes, older.fileBytes);
        r.dump2Capacity = Math.max(newer.approxUsedBytes, newer.fileBytes);
        r.usedDelta = r.dump2Used - r.dump1Used;
        r.capacityDelta = r.dump2Capacity - r.dump1Capacity;
        r.usedDeltaPct = pct(r.usedDelta, r.dump1Used);

        Map<String, HprofParser.HprofSummary.ClassStat> a = index(older);
        Map<String, HprofParser.HprofSummary.ClassStat> b = index(newer);
        List<ClassDelta> all = new ArrayList<ClassDelta>();
        for (String name : unionKeys(a, b)) {
            HprofParser.HprofSummary.ClassStat s1 = a.get(name);
            HprofParser.HprofSummary.ClassStat s2 = b.get(name);
            ClassDelta d = new ClassDelta();
            d.className = name;
            d.dump1Bytes = s1 == null ? 0L : s1.retainedBytes;
            d.dump2Bytes = s2 == null ? 0L : s2.retainedBytes;
            d.dump1Instances = s1 == null ? 0L : s1.instances;
            d.dump2Instances = s2 == null ? 0L : s2.instances;
            d.deltaBytes = d.dump2Bytes - d.dump1Bytes;
            d.deltaInstances = d.dump2Instances - d.dump1Instances;
            d.deltaPct = pct(d.deltaBytes, d.dump1Bytes);
            all.add(d);
        }
        Collections.sort(all, new Comparator<ClassDelta>() {
            @Override
            public int compare(ClassDelta x, ClassDelta y) {
                int byAbs = Long.compare(Math.abs(y.deltaBytes), Math.abs(x.deltaBytes));
                if (byAbs != 0) {
                    return byAbs;
                }
                return Long.compare(y.dump2Bytes, x.dump2Bytes);
            }
        });
        int n = Math.min(r.topN, all.size());
        for (int i = 0; i < n; i++) {
            r.topDeltas.add(all.get(i));
        }
        if (priorSuspectClasses != null) {
            for (String sus : priorSuspectClasses) {
                if (sus == null) {
                    continue;
                }
                ClassDelta match = findSuspect(all, sus);
                if (match != null) {
                    r.priorSuspects.add(match);
                }
            }
        }
        judge(r);
        return r;
    }

    private static void fillHeap(CompareResult r, HprofParser.HprofSummary a, HprofParser.HprofSummary b) {
        r.dump1Used = a.approxUsedBytes;
        r.dump2Used = b.approxUsedBytes;
        r.dump1Capacity = Math.max(a.approxUsedBytes, a.fileBytes);
        r.dump2Capacity = Math.max(b.approxUsedBytes, b.fileBytes);
    }

    private static void judge(CompareResult r) {
        boolean usedUp = r.usedDeltaPct >= 10.0d && r.usedDelta > 1024L * 1024L;
        boolean usedDown = r.usedDeltaPct <= -5.0d;
        boolean usedFlat = Math.abs(r.usedDeltaPct) < 5.0d;
        boolean suspectGrowing = false;
        for (ClassDelta d : r.priorSuspects) {
            if (d.deltaBytes > 0 && d.deltaPct >= 10.0d) {
                suspectGrowing = true;
            }
        }
        boolean cacheGrowing = false;
        for (ClassDelta d : r.topDeltas) {
            String n = d.className == null ? "" : d.className.toLowerCase(Locale.ROOT);
            if (d.deltaBytes > 0 && d.deltaPct >= 15.0d
                    && (n.contains("cache") || n.contains("hashmap") || n.contains("concurrenthashmap")
                    || n.contains("arraylist") || n.contains("linkedhashmap"))) {
                cacheGrowing = true;
            }
        }
        if (usedDown || (usedFlat && !suspectGrowing && !cacheGrowing)) {
            if (usedDown) {
                r.judgment = PEAK_OR_JITTER;
                r.summary = "第二份快照堆占用低于第一份（Δ=" + r.usedDelta + " bytes，"
                        + fmtPct(r.usedDeltaPct) + "），更像高峰/抖动而非窗口内持续保留。";
            } else {
                r.judgment = STABLE_IN_WINDOW;
                r.summary = "两份快照堆占用接近（Δ=" + r.usedDelta + " bytes，"
                        + fmtPct(r.usedDeltaPct) + "），窗口内相对稳定。";
            }
            return;
        }
        if (usedUp && (suspectGrowing || cacheGrowing || r.usedDeltaPct >= 20.0d)) {
            r.judgment = LEAK_OR_RETENTION;
            r.summary = "第二份快照堆占用上升（Δ=" + r.usedDelta + " bytes，"
                    + fmtPct(r.usedDeltaPct) + "）"
                    + (suspectGrowing ? "，此前嫌疑类型仍在增长" : "")
                    + (cacheGrowing ? "，集合/缓存类占用上升" : "")
                    + "。倾向泄漏/保留，而非单纯瞬时高峰。";
            return;
        }
        r.judgment = PEAK_OR_JITTER;
        r.summary = "两份快照堆占用有变化（Δ=" + r.usedDelta + " bytes，"
                + fmtPct(r.usedDeltaPct) + "），但增长形态不足以判定窗口内持续保留。";
    }

    private static ClassDelta findSuspect(List<ClassDelta> all, String sus) {
        for (ClassDelta d : all) {
            if (d.className != null && d.className.contains(sus)) {
                return d;
            }
        }
        return null;
    }

    private static Map<String, HprofParser.HprofSummary.ClassStat> index(HprofParser.HprofSummary sum) {
        Map<String, HprofParser.HprofSummary.ClassStat> m =
                new LinkedHashMap<String, HprofParser.HprofSummary.ClassStat>();
        List<HprofParser.HprofSummary.ClassStat> src = sum.allClasses.isEmpty()
                ? sum.topClasses : sum.allClasses;
        for (HprofParser.HprofSummary.ClassStat st : src) {
            if (st.className != null) {
                m.put(st.className, st);
            }
        }
        return m;
    }

    private static List<String> unionKeys(Map<String, ?> a, Map<String, ?> b) {
        LinkedHashMap<String, Boolean> keys = new LinkedHashMap<String, Boolean>();
        for (String k : a.keySet()) {
            keys.put(k, Boolean.TRUE);
        }
        for (String k : b.keySet()) {
            keys.put(k, Boolean.TRUE);
        }
        return new ArrayList<String>(keys.keySet());
    }

    private static double pct(long delta, long base) {
        if (base == 0L) {
            return delta == 0L ? 0d : 100d;
        }
        return (delta * 10000L / base) / 100.0d;
    }

    private static String fmtPct(double v) {
        return String.format(Locale.US, "%+.1f%%", v);
    }

    public static class ClassDelta {
        public String className;
        public long dump1Bytes;
        public long dump2Bytes;
        public long deltaBytes;
        public double deltaPct;
        public long dump1Instances;
        public long dump2Instances;
        public long deltaInstances;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("class", className);
            m.put("dump1_bytes", dump1Bytes);
            m.put("dump2_bytes", dump2Bytes);
            m.put("delta_bytes", deltaBytes);
            m.put("delta_pct", deltaPct);
            m.put("dump1_instances", dump1Instances);
            m.put("dump2_instances", dump2Instances);
            m.put("delta_instances", deltaInstances);
            return m;
        }
    }

    public static class CompareResult {
        public int topN;
        public long dump1Used;
        public long dump2Used;
        public long usedDelta;
        public double usedDeltaPct;
        public long dump1Capacity;
        public long dump2Capacity;
        public long capacityDelta;
        public String judgment = SINGLE_SNAPSHOT_ONLY;
        public String summary;
        public final List<ClassDelta> topDeltas = new ArrayList<ClassDelta>();
        public final List<ClassDelta> priorSuspects = new ArrayList<ClassDelta>();

        public Map<String, Object> toJsonMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            Map<String, Object> used = new LinkedHashMap<String, Object>();
            used.put("dump1", dump1Used);
            used.put("dump2", dump2Used);
            used.put("delta", usedDelta);
            used.put("delta_pct", usedDeltaPct);
            m.put("heap_used_bytes", used);
            Map<String, Object> cap = new LinkedHashMap<String, Object>();
            cap.put("dump1", dump1Capacity);
            cap.put("dump2", dump2Capacity);
            cap.put("delta", capacityDelta);
            m.put("heap_capacity_bytes", cap);
            List<Map<String, Object>> top = new ArrayList<Map<String, Object>>();
            for (ClassDelta d : topDeltas) {
                top.add(d.toMap());
            }
            m.put("top_classes", top);
            List<Map<String, Object>> sus = new ArrayList<Map<String, Object>>();
            for (ClassDelta d : priorSuspects) {
                sus.add(d.toMap());
            }
            m.put("prior_suspects", sus);
            m.put("judgment", judgment);
            m.put("summary", summary);
            return m;
        }
    }
}
