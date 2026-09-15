package com.jfa.core.analyze;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HotSpot jstack / jcmd Thread.print output, including JVM deadlock reports.
 */
public class DeadlockEngine {
    private static final Pattern THREAD_HDR = Pattern.compile("^\"([^\"]+)\".*");
    private static final Pattern WAITING_HELD_BY = Pattern.compile(
            "waiting to lock monitor .* which is held by \"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCKED = Pattern.compile("- locked <([^>]+)> \\(a ([^)]+)\\)");
    private static final Pattern WAITING_LOCK = Pattern.compile(
            "- waiting to lock <([^>]+)> \\(a ([^)]+)\\)");
    private static final Pattern PARKING = Pattern.compile("- parking to wait for\\s+<([^>]+)> \\(a ([^)]+)\\)");

    public ThreadAnalysis analyze(String dumpText) {
        ThreadAnalysis result = new ThreadAnalysis();
        if (dumpText == null || dumpText.trim().isEmpty()) {
            result.setAvailable(false);
            result.setNote("thread dump 为空");
            return result;
        }
        result.setAvailable(true);
        result.setRaw(dumpText);
        parseDeadlockBlock(dumpText, result);
        parseThreads(dumpText, result);
        if (!result.isDeadlockFound()) {
            summarizeBlocked(result);
        }
        return result;
    }

    private void parseDeadlockBlock(String text, ThreadAnalysis result) {
        String lower = text.toLowerCase();
        int idx = lower.indexOf("found one java-level deadlock");
        int idxMany = lower.indexOf("found ");
        boolean found = idx >= 0 || lower.contains("java-level deadlock");
        if (lower.contains("no deadlock") || lower.contains("no java-level deadlock")) {
            found = false;
        }
        if (!found) {
            result.setDeadlockFound(false);
            result.setDeadlockCount(0);
            return;
        }
        result.setDeadlockFound(true);
        int count = 1;
        Matcher cm = Pattern.compile("Found (\\d+) Java-level deadlock", Pattern.CASE_INSENSITIVE).matcher(text);
        if (cm.find()) {
            count = Integer.parseInt(cm.group(1));
        } else if (lower.contains("found one java-level deadlock")) {
            count = 1;
        }
        result.setDeadlockCount(count);
        int start = Math.max(text.toLowerCase().indexOf("found"), 0);
        int stackInfo = text.toLowerCase().indexOf("java stack information for the threads listed above");
        String desc;
        if (stackInfo > start) {
            desc = text.substring(start, stackInfo).trim();
        } else {
            int end = Math.min(text.length(), start + 2500);
            desc = text.substring(start, end).trim();
        }
        result.setJvmDeadlockDescription(desc);
    }

    private void parseThreads(String text, ThreadAnalysis result) {
        String[] lines = text.split("\n");
        ThreadSnapshot cur = null;
        boolean inDeadlockStacks = false;
        for (int i = 0; i < lines.length; i++) {
            String line = rtrim(lines[i]);
            if (line.toLowerCase().contains("java stack information for the threads listed above")) {
                inDeadlockStacks = true;
            }
            Matcher hdr = THREAD_HDR.matcher(line);
            if (hdr.matches() && (line.contains("prio=") || line.contains("tid=")
                    || line.contains("nid=") || line.startsWith("\""))) {
                if (cur != null) {
                    result.getThreads().add(cur);
                }
                cur = new ThreadSnapshot();
                cur.name = hdr.group(1);
                cur.header = line;
                cur.inDeadlockSection = inDeadlockStacks;
                if (line.contains("BLOCKED")) {
                    cur.state = "BLOCKED";
                } else if (line.contains("WAITING") && !line.contains("TIMED_WAITING")) {
                    cur.state = "WAITING";
                } else if (line.contains("TIMED_WAITING")) {
                    cur.state = "TIMED_WAITING";
                } else if (line.contains("RUNNABLE")) {
                    cur.state = "RUNNABLE";
                }
                continue;
            }
            if (cur == null) {
                continue;
            }
            if (line.startsWith("   java.lang.Thread.State:") || line.startsWith("\tjava.lang.Thread.State:")) {
                cur.stateLine = line.trim();
                if (line.contains("BLOCKED")) {
                    cur.state = "BLOCKED";
                }
            }
            Matcher locked = LOCKED.matcher(line.trim());
            if (locked.find()) {
                cur.locked.add(locked.group(2) + " @" + locked.group(1));
            }
            Matcher waiting = WAITING_LOCK.matcher(line.trim());
            if (waiting.find()) {
                cur.waitingFor.add(waiting.group(2) + " @" + waiting.group(1));
            }
            Matcher park = PARKING.matcher(line.trim());
            if (park.find()) {
                cur.waitingFor.add(park.group(2) + " @" + park.group(1));
            }
            Matcher heldBy = WAITING_HELD_BY.matcher(line.trim());
            if (heldBy.find()) {
                cur.heldBy = heldBy.group(1);
            }
            String trim = line.trim();
            if (trim.startsWith("at ")) {
                cur.frames.add(trim.substring(3).trim());
            }
        }
        if (cur != null) {
            result.getThreads().add(cur);
        }
        buildSuspects(result);
    }

    private void buildSuspects(ThreadAnalysis result) {
        if (!result.isDeadlockFound()) {
            return;
        }
        List<ThreadSnapshot> involved = new ArrayList<ThreadSnapshot>();
        for (ThreadSnapshot t : result.getThreads()) {
            if (t.inDeadlockSection || (!t.waitingFor.isEmpty() && !t.locked.isEmpty()
                    && ("BLOCKED".equals(t.state) || t.heldBy != null))) {
                involved.add(t);
            }
        }
        if (involved.isEmpty()) {
            for (ThreadSnapshot t : result.getThreads()) {
                if ("BLOCKED".equals(t.state)) {
                    involved.add(t);
                }
            }
        }
        Map<String, Object> suspect = new LinkedHashMap<String, Object>();
        suspect.put("id", "SUS-D-01");
        List<String> names = new ArrayList<String>();
        List<String> locks = new ArrayList<String>();
        List<String> frames = new ArrayList<String>();
        StringBuilder chain = new StringBuilder();
        for (ThreadSnapshot t : involved) {
            names.add(t.name);
            locks.addAll(t.locked);
            locks.addAll(t.waitingFor);
            for (String f : t.frames) {
                if (isBusinessFrame(f) && frames.size() < 8 && !frames.contains(f)) {
                    frames.add(f);
                }
            }
            if (chain.length() > 0) {
                chain.append(" | ");
            }
            chain.append(t.name).append(" locked=").append(t.locked)
                    .append(" waiting=").append(t.waitingFor);
            if (t.heldBy != null) {
                chain.append(" heldBy=").append(t.heldBy);
            }
        }
        if (frames.isEmpty()) {
            for (ThreadSnapshot t : involved) {
                for (String f : t.frames) {
                    if (frames.size() < 6 && !f.startsWith("java.") && !f.startsWith("sun.")) {
                        frames.add(f);
                    }
                }
            }
        }
        suspect.put("threads", unique(names));
        suspect.put("lock_objects", unique(locks));
        suspect.put("frames", frames);
        suspect.put("blocking_chain_summary", chain.toString());
        result.getSuspects().add(suspect);
        result.setBusinessFrames(frames);
    }

    private void summarizeBlocked(ThreadAnalysis result) {
        int blocked = 0;
        List<String> top = new ArrayList<String>();
        for (ThreadSnapshot t : result.getThreads()) {
            if ("BLOCKED".equals(t.state)) {
                blocked++;
                if (top.size() < 5) {
                    String frame = t.frames.isEmpty() ? t.name : t.name + " @ " + t.frames.get(0);
                    top.add(frame);
                }
            }
        }
        result.setBlockedCount(blocked);
        result.setHotBlocked(top);
        if (blocked > 0) {
            result.getRiskHints().add("BLOCKED 线程数偏高（" + blocked + "）：" + join(top, "; ")
                    + " ——风险提示，非死锁结论");
        }
        int threads = result.getThreads().size();
        if (threads > 200) {
            result.getRiskHints().add("线程总数偏高（" + threads + "）——风险提示，非故障定性");
        }
    }

    private static boolean isBusinessFrame(String f) {
        if (f == null) {
            return false;
        }
        String x = f.toLowerCase();
        if (x.startsWith("java.") || x.startsWith("javax.") || x.startsWith("sun.")
                || x.startsWith("jdk.") || x.startsWith("com.sun.")
                || x.contains("org.springframework") || x.startsWith("io.netty")) {
            return !x.contains("example") && !x.contains("com.example") && f.contains(".biz.");
        }
        return true;
    }

    private static List<String> unique(List<String> in) {
        List<String> out = new ArrayList<String>();
        for (String s : in) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String join(List<String> xs, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(x);
        }
        return sb.toString();
    }

    private static String rtrim(String s) {
        int i = s.length();
        while (i > 0 && (s.charAt(i - 1) == '\r' || s.charAt(i - 1) == ' ')) {
            i--;
        }
        return s.substring(0, i);
    }

    public static class ThreadAnalysis {
        private boolean available;
        private boolean deadlockFound;
        private int deadlockCount;
        private String jvmDeadlockDescription;
        private String note;
        private String raw;
        private int blockedCount;
        private final List<ThreadSnapshot> threads = new ArrayList<ThreadSnapshot>();
        private final List<Map<String, Object>> suspects = new ArrayList<Map<String, Object>>();
        private final List<String> riskHints = new ArrayList<String>();
        private final List<String> hotBlocked = new ArrayList<String>();
        private List<String> businessFrames = new ArrayList<String>();

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public boolean isDeadlockFound() {
            return deadlockFound;
        }

        public void setDeadlockFound(boolean deadlockFound) {
            this.deadlockFound = deadlockFound;
        }

        public int getDeadlockCount() {
            return deadlockCount;
        }

        public void setDeadlockCount(int deadlockCount) {
            this.deadlockCount = deadlockCount;
        }

        public String getJvmDeadlockDescription() {
            return jvmDeadlockDescription;
        }

        public void setJvmDeadlockDescription(String jvmDeadlockDescription) {
            this.jvmDeadlockDescription = jvmDeadlockDescription;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public int getBlockedCount() {
            return blockedCount;
        }

        public void setBlockedCount(int blockedCount) {
            this.blockedCount = blockedCount;
        }

        public List<ThreadSnapshot> getThreads() {
            return threads;
        }

        public List<Map<String, Object>> getSuspects() {
            return suspects;
        }

        public List<String> getRiskHints() {
            return riskHints;
        }

        public List<String> getHotBlocked() {
            return hotBlocked;
        }

        public void setHotBlocked(List<String> hotBlocked) {
            this.hotBlocked.clear();
            this.hotBlocked.addAll(hotBlocked);
        }

        public List<String> getBusinessFrames() {
            return businessFrames;
        }

        public void setBusinessFrames(List<String> businessFrames) {
            this.businessFrames = businessFrames;
        }
    }

    public static class ThreadSnapshot {
        String name;
        String header;
        String state;
        String stateLine;
        String heldBy;
        boolean inDeadlockSection;
        List<String> frames = new ArrayList<String>();
        List<String> locked = new ArrayList<String>();
        List<String> waitingFor = new ArrayList<String>();
    }
}
