package com.jfa.core.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppLogAnalyzer {
    private static final Pattern OOM = Pattern.compile(
            "java\\.lang\\.OutOfMemoryError(?::\\s*([^\\r\\n]+))?");
    private static final Pattern AT = Pattern.compile("^\\s*at\\s+(\\S+)");

    public OomStack analyze(String log) {
        OomStack r = new OomStack();
        if (log == null || log.trim().isEmpty()) {
            return r;
        }
        String[] lines = log.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = OOM.matcher(lines[i]);
            if (m.find()) {
                r.found = true;
                String detail = m.group(1);
                r.detail = detail == null ? "OutOfMemoryError" : detail.trim();
                r.subtype = classify(r.detail);
                for (int j = i + 1; j < lines.length && j <= i + 12; j++) {
                    Matcher at = AT.matcher(lines[j]);
                    if (at.find()) {
                        r.frames.add(at.group(1));
                    }
                }
                break;
            }
        }
        return r;
    }

    public static String classify(String detail) {
        if (detail == null) {
            return "unknown";
        }
        String d = detail.toLowerCase();
        if (d.contains("java heap space")) {
            return "java_heap_space";
        }
        if (d.contains("gc overhead")) {
            return "gc_overhead_limit";
        }
        if (d.contains("metaspace") || d.contains("permgen")) {
            return "other";
        }
        if (d.contains("direct buffer") || d.contains("directbuffer")) {
            return "other";
        }
        return "other";
    }

    public static class OomStack {
        public boolean found;
        public String detail;
        public String subtype = "none";
        public final List<String> frames = new ArrayList<String>();
    }
}
