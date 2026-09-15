package com.jfa.core.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDK 8 GC log trend analysis (PrintGCDetails / PrintGCDateStamps style).
 */
public class GcLogAnalyzer {
    private static final Pattern FULL_GC = Pattern.compile("\\[Full GC", Pattern.CASE_INSENSITIVE);
    private static final Pattern OLD = Pattern.compile(
            "\\[(?:ParOldGen|PSOldGen|CMS Old Gen|Tenured):\\s*(\\d+)K->(\\d+)K\\((\\d+)K\\)\\]");
    private static final Pattern HEAP = Pattern.compile(
            "(\\d+)K->(\\d+)K\\((\\d+)K\\)");

    public GcTrend analyze(String gcLog) {
        GcTrend t = new GcTrend();
        if (gcLog == null || gcLog.trim().isEmpty()) {
            t.available = false;
            return t;
        }
        t.available = true;
        String[] lines = gcLog.split("\n");
        int full = 0;
        int young = 0;
        List<Long> oldAfter = new ArrayList<Long>();
        List<Long> heapAfter = new ArrayList<Long>();
        long lastOldCap = 0;
        for (String line : lines) {
            if (FULL_GC.matcher(line).find()) {
                full++;
            } else if (line.contains("[GC") || line.contains("[Young") || line.contains("GC (")) {
                young++;
            }
            Matcher om = OLD.matcher(line);
            if (om.find()) {
                long after = Long.parseLong(om.group(2)) * 1024L;
                lastOldCap = Long.parseLong(om.group(3)) * 1024L;
                oldAfter.add(after);
            }
            Matcher hm = HEAP.matcher(line);
            if (hm.find()) {
                heapAfter.add(Long.parseLong(hm.group(2)) * 1024L);
            }
        }
        if (full == 0 && young == 0 && oldAfter.isEmpty()) {
            t.available = false;
            t.summary = "文件中无可用 GC 记录";
            return t;
        }
        t.fullGcCount = full;
        t.youngGcCount = young;
        t.oldCapacityBytes = lastOldCap;
        if (!oldAfter.isEmpty()) {
            t.oldUsedBytes = oldAfter.get(oldAfter.size() - 1);
            long first = oldAfter.get(0);
            long last = oldAfter.get(oldAfter.size() - 1);
            t.oldRising = last > first * 11 / 10;
            t.lowReclaim = full >= 3 && last > first * 9 / 10;
        }
        if (!heapAfter.isEmpty()) {
            t.heapUsedBytes = heapAfter.get(heapAfter.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        if (t.oldRising || t.lowReclaim) {
            sb.append("老年代在日志窗口内持续偏高或上升，Full GC 次数=").append(full);
            if (t.lowReclaim) {
                sb.append("，回收效率下降");
            }
            sb.append("。不能做精确对象归因（无 hprof）。");
        } else if (full > 0) {
            sb.append("观察到 Full GC ").append(full).append(" 次；未形成明确老年代螺旋。不能做精确对象归因。");
        } else {
            sb.append("GC 日志可解析，Full GC 较少。不能做精确对象归因。");
        }
        t.summary = sb.toString();
        if (full >= 2 && t.oldRising) {
            t.riskHints.add("老年代占用上升且 Full GC 频繁——风险提示，非 OOM 结论");
        }
        return t;
    }

    public static class GcTrend {
        public boolean available;
        public String summary;
        public int fullGcCount;
        public int youngGcCount;
        public long oldUsedBytes;
        public long oldCapacityBytes;
        public long heapUsedBytes;
        public boolean oldRising;
        public boolean lowReclaim;
        public final List<String> riskHints = new ArrayList<String>();
    }
}
