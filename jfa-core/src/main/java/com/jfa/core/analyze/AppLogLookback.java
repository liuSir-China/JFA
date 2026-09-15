package com.jfa.core.analyze;

import com.jfa.common.time.TimeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Product-executed app-log lookback: OOM plus memory/thread ERROR/FATAL in a time window.
 */
public class AppLogLookback {
    private static final Pattern OOM = Pattern.compile("OutOfMemoryError", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL = Pattern.compile("\\b(ERROR|FATAL|SEVERE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEVANT = Pattern.compile(
            "memory|heap|oom|metaspace|permgen|gc overhead|deadlock|blocked|thread dump|unable to create.*thread",
            Pattern.CASE_INSENSITIVE);

    public Result scan(String text, long nowMs, int lookbackMinutes) {
        Result r = new Result();
        r.lookbackMinutes = lookbackMinutes;
        r.windowEndMs = nowMs;
        r.windowStartMs = nowMs - lookbackMinutes * 60L * 1000L;
        if (text == null || text.isEmpty()) {
            r.summary = "日志内容为空。";
            return r;
        }
        String[] lines = text.split("\n", -1);
        boolean anyTs = false;
        Long lastTs = null;
        StringBuilder event = new StringBuilder();
        Long eventTs = null;
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : null;
            Long ts = line == null ? null : TimeSupport.parseLogLineTime(line);
            boolean newEvent = line == null || ts != null;
            if (newEvent && event.length() > 0) {
                consume(r, event.toString(), eventTs, anyTs);
                event.setLength(0);
                eventTs = null;
            }
            if (line == null) {
                break;
            }
            if (ts != null) {
                anyTs = true;
                lastTs = ts;
                eventTs = ts;
                event.append(line).append('\n');
            } else {
                if (event.length() == 0) {
                    eventTs = lastTs;
                }
                event.append(line).append('\n');
            }
        }
        r.hadTimestamps = anyTs;
        if (r.hits.isEmpty()) {
            if (anyTs) {
                r.summary = "倒查窗口 " + lookbackMinutes + " 分钟内无 OutOfMemoryError，也无与内存/线程相关的 ERROR/FATAL。";
            } else {
                r.summary = "日志无可用时间戳，已扫描全文：无 OutOfMemoryError，也无与内存/线程相关的 ERROR/FATAL。";
            }
        } else {
            r.summary = "倒查命中 " + r.hits.size() + " 处（窗口 "
                    + lookbackMinutes + " 分钟" + (anyTs ? "" : "；无时间戳按全文") + "）。";
        }
        return r;
    }

    private static void consume(Result r, String event, Long ts, boolean anyTs) {
        if (anyTs && ts != null && (ts < r.windowStartMs || ts > r.windowEndMs + 60_000L)) {
            return;
        }
        if (!isHit(event)) {
            return;
        }
        Hit h = new Hit();
        h.timestampMs = ts;
        h.excerpt = clip(event, 40);
        h.oom = OOM.matcher(event).find();
        r.hits.add(h);
        if (h.oom) {
            r.oomInWindow = true;
        }
    }

    static boolean isHit(String event) {
        if (OOM.matcher(event).find()) {
            return true;
        }
        if (!LEVEL.matcher(event).find()) {
            return false;
        }
        return RELEVANT.matcher(event).find();
    }

    private static String clip(String event, int maxLines) {
        String[] lines = event.split("\n");
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxLines, lines.length);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = lines[i];
            if (line.length() > 400) {
                line = line.substring(0, 400) + "…";
            }
            sb.append(line);
        }
        if (lines.length > maxLines) {
            sb.append("\n…");
        }
        return sb.toString();
    }

    public static class Hit {
        public Long timestampMs;
        public boolean oom;
        public String excerpt;
    }

    public static class Result {
        public int lookbackMinutes;
        public long windowStartMs;
        public long windowEndMs;
        public boolean hadTimestamps;
        public boolean oomInWindow;
        public String summary;
        public String unresolvedNote;
        public final List<String> scannedFiles = new ArrayList<String>();
        public final List<Hit> hits = new ArrayList<Hit>();

        public String judgmentLine() {
            if (unresolvedNote != null) {
                return unresolvedNote;
            }
            return summary;
        }
    }
}
