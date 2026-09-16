package com.jfa.common.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared console layout for the JFA CLI: two leading blank lines, then
 * content. Banner/title lines are centered to the terminal width; body
 * stays left-aligned.
 */
public final class ConsoleLayout {
    public static final int FALLBACK_WIDTH = 80;
    public static final String REPORT_WRITTEN_BANNER = "======== 报告已写入 ========";

    private static final AtomicBoolean LEAD_DONE = new AtomicBoolean(false);
    private static volatile Integer cachedWidth;
    private static volatile Integer widthOverride;

    private ConsoleLayout() {
    }

    public static void resetSession() {
        LEAD_DONE.set(false);
    }

    /**
     * Test hook: {@code null} restores detection / {@link #FALLBACK_WIDTH}.
     */
    public static void overrideWidth(Integer width) {
        widthOverride = width;
        cachedWidth = null;
    }

    public static boolean leadDone() {
        return LEAD_DONE.get();
    }

    /**
     * Print two blank lines once per CLI session on {@code dest}.
     *
     * @return true if this call printed the blanks
     */
    public static boolean ensureLead(PrintStream dest) {
        if (dest == null) {
            return false;
        }
        if (LEAD_DONE.compareAndSet(false, true)) {
            dest.println();
            dest.println();
            dest.flush();
            return true;
        }
        return false;
    }

    public static int width() {
        Integer over = widthOverride;
        if (over != null && over.intValue() >= 20) {
            return over.intValue();
        }
        Integer cached = cachedWidth;
        if (cached != null) {
            return cached.intValue();
        }
        int detected = detectWidth();
        cachedWidth = Integer.valueOf(detected);
        return detected;
    }

    public static String center(String line) {
        if (line == null) {
            return "";
        }
        int w = width();
        int dw = displayWidth(line);
        if (dw >= w || dw <= 0) {
            return line;
        }
        int pad = (w - dw) / 2;
        StringBuilder sb = new StringBuilder(pad + line.length());
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append(line);
        return sb.toString();
    }

    public static boolean isBanner(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        return t.startsWith("========") && t.endsWith("========") && t.length() >= 16;
    }

    public static int displayWidth(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                w += 2;
                i++;
                continue;
            }
            w += isWide(c) ? 2 : 1;
        }
        return w;
    }

    public static void printText(PrintStream dest, String text) {
        if (dest == null) {
            return;
        }
        ensureLead(dest);
        if (text == null || text.isEmpty()) {
            return;
        }
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (isBanner(line)) {
                dest.println(center(line.trim()));
            } else {
                dest.println(line);
            }
        }
        dest.flush();
    }

    public static void printBanner(PrintStream dest, String banner) {
        if (dest == null) {
            return;
        }
        ensureLead(dest);
        dest.println(center(banner == null ? "" : banner.trim()));
        dest.flush();
    }

    public static void printLine(PrintStream dest, String line) {
        if (dest == null) {
            return;
        }
        ensureLead(dest);
        dest.println(line == null ? "" : line);
        dest.flush();
    }

    static int detectWidth() {
        String env = System.getenv("COLUMNS");
        Integer fromEnv = parseWidth(env);
        if (fromEnv != null) {
            return fromEnv.intValue();
        }
        Integer fromStty = sttyColumns();
        if (fromStty != null) {
            return fromStty.intValue();
        }
        return FALLBACK_WIDTH;
    }

    static Integer parseWidth(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            int w = Integer.parseInt(t);
            if (w >= 20 && w <= 500) {
                return Integer.valueOf(w);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    private static Integer sttyColumns() {
        File tty = new File("/dev/tty");
        if (!tty.exists()) {
            return null;
        }
        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("stty", "size");
            pb.redirectInput(ProcessBuilder.Redirect.from(tty));
            pb.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
            proc = pb.start();
            final Process running = proc;
            final String[] holder = new String[1];
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(running.getInputStream(), "UTF-8"));
                        holder[0] = br.readLine();
                    } catch (Exception ignored) {
                        // leave holder null
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();
            reader.join(400);
            if (reader.isAlive()) {
                reader.interrupt();
            }
            String line = holder[0];
            if (line == null) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                return parseWidth(parts[1]);
            }
            return parseWidth(line);
        } catch (Exception e) {
            return null;
        } finally {
            if (proc != null) {
                try {
                    proc.destroy();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private static boolean isWide(char c) {
        return c >= 0x1100 && (
                c <= 0x115F
                        || c == 0x2329
                        || c == 0x232A
                        || (c >= 0x2E80 && c <= 0xA4CF && c != 0x303F)
                        || (c >= 0xAC00 && c <= 0xD7A3)
                        || (c >= 0xF900 && c <= 0xFAFF)
                        || (c >= 0xFE10 && c <= 0xFE19)
                        || (c >= 0xFE30 && c <= 0xFE6F)
                        || (c >= 0xFF00 && c <= 0xFF60)
                        || (c >= 0xFFE0 && c <= 0xFFE6));
    }
}