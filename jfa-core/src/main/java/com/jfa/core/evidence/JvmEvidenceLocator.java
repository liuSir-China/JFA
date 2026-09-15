package com.jfa.core.evidence;

import com.jfa.common.model.JavaProcessInfo;
import com.jfa.core.collect.EvidencePack;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses JDK 8 JVM command-line flags and locates existing on-disk GC / hprof evidence.
 * GC/hprof never substitute for a thread dump.
 */
public final class JvmEvidenceLocator {
    private JvmEvidenceLocator() {
    }

    public static class ParsedFlags {
        public String heapDumpPath;
        public boolean heapDumpOnOom;
        public String gcLogPath;
        public String onOutOfMemoryError;
    }

    public static ParsedFlags parse(String commandLine) {
        ParsedFlags p = new ParsedFlags();
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return p;
        }
        String[] tokens = tokenize(commandLine);
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.startsWith("-XX:HeapDumpPath=")) {
                p.heapDumpPath = unquote(t.substring("-XX:HeapDumpPath=".length()));
            } else if (t.startsWith("-Xloggc:")) {
                p.gcLogPath = unquote(t.substring("-Xloggc:".length()));
            } else if (t.startsWith("-Xloggc=")) {
                p.gcLogPath = unquote(t.substring("-Xloggc=".length()));
            } else if ("-XX:+HeapDumpOnOutOfMemoryError".equals(t)) {
                p.heapDumpOnOom = true;
            } else if ("-XX:-HeapDumpOnOutOfMemoryError".equals(t)) {
                p.heapDumpOnOom = false;
            } else if (t.startsWith("-XX:OnOutOfMemoryError=")) {
                p.onOutOfMemoryError = unquote(t.substring("-XX:OnOutOfMemoryError=".length()));
            } else if (t.startsWith("-Xlog:")) {
                String file = extractXlogFile(t);
                if (file != null && p.gcLogPath == null) {
                    p.gcLogPath = file;
                }
            }
        }
        return p;
    }

    public static void fillMissing(EvidencePack pack, JavaProcessInfo process) {
        if (pack == null || process == null) {
            return;
        }
        ParsedFlags flags = parse(process.getCommandLine());
        String cwd = process.getCwd();
        if (pack.getHprof() == null) {
            File hprof = findExistingHprof(flags, cwd, process.getPid());
            if (hprof != null) {
                pack.setHprof(hprof);
            }
        }
        if (pack.getGcLog() == null) {
            File gc = findExistingGcLog(flags, cwd);
            if (gc != null) {
                pack.setGcLog(gc);
            }
        }
    }

    public static File findExistingHprof(ParsedFlags flags, String cwd, long pid) {
        if (flags == null || flags.heapDumpPath == null || flags.heapDumpPath.trim().isEmpty()) {
            return null;
        }
        File path = resolve(flags.heapDumpPath.trim(), cwd);
        if (path.isFile()) {
            return looksUsableHprof(path) ? path.getAbsoluteFile() : null;
        }
        File dir = path;
        if (path.getName().toLowerCase().endsWith(".hprof") && path.getParentFile() != null) {
            dir = path.getParentFile();
        }
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File preferred = new File(dir, "java_pid" + pid + ".hprof");
        if (looksUsableHprof(preferred)) {
            return preferred.getAbsoluteFile();
        }
        File newest = FileSupport.newest(FileSupport.findBySuffix(dir, ".hprof"));
        if (looksUsableHprof(newest)) {
            return newest.getAbsoluteFile();
        }
        return null;
    }

    public static File findExistingGcLog(ParsedFlags flags, String cwd) {
        if (flags == null || flags.gcLogPath == null || flags.gcLogPath.trim().isEmpty()) {
            return null;
        }
        File path = resolve(flags.gcLogPath.trim(), cwd);
        List<File> candidates = new ArrayList<File>();
        if (path.isFile()) {
            candidates.add(path);
        }
        File dir = path.isDirectory() ? path : path.getParentFile();
        String prefix = path.isDirectory() ? "gc" : path.getName();
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!c.isFile()) {
                        continue;
                    }
                    String name = c.getName();
                    if (name.equals(prefix) || name.startsWith(prefix + ".") || name.startsWith(prefix + "-")) {
                        candidates.add(c);
                    }
                }
            }
        }
        File newest = FileSupport.newest(candidates);
        if (newest != null && newest.isFile() && newest.length() > 0) {
            return newest.getAbsoluteFile();
        }
        return null;
    }

    public static boolean looksUsableHprof(File file) {
        if (file == null || !file.isFile() || file.length() < 32) {
            return false;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buf = new byte[12];
            int n = in.read(buf);
            if (n < 12) {
                return false;
            }
            return new String(buf, 0, n, StandardCharsets.US_ASCII).startsWith("JAVA PROFILE");
        } catch (IOException e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    public static boolean looksUsableThreadDump(File file) {
        if (file == null || !file.isFile() || file.length() < 16) {
            return false;
        }
        String text;
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(file.toPath());
            text = new String(raw, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("full thread dump")
                || lower.contains("tid=")
                || text.contains("\"main\"")
                || text.contains("Thread.print");
    }

    static File resolve(String path, String cwd) {
        File f = new File(path);
        if (f.isAbsolute()) {
            return f;
        }
        if (cwd != null && !cwd.trim().isEmpty()) {
            return new File(cwd, path);
        }
        return f.getAbsoluteFile();
    }

    static String[] tokenize(String commandLine) {
        if (commandLine.indexOf('\n') >= 0) {
            return commandLine.split("\n");
        }
        List<String> tokens = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (inQuote) {
                if (c == quote) {
                    inQuote = false;
                } else {
                    cur.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens.toArray(new String[tokens.size()]);
    }

    static String unquote(String v) {
        if (v == null) {
            return null;
        }
        v = v.trim();
        if (v.length() >= 2) {
            char a = v.charAt(0);
            char b = v.charAt(v.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    static String extractXlogFile(String token) {
        int file = token.indexOf("file=");
        if (file < 0) {
            return null;
        }
        int start = file + "file=".length();
        int end = start;
        while (end < token.length()) {
            char c = token.charAt(end);
            if (c == ':' || c == ',' || c == ' ') {
                break;
            }
            end++;
        }
        if (end == start) {
            return null;
        }
        return unquote(token.substring(start, end));
    }
}
