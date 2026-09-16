package com.jfa.console;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Finds diagnose reports under {@code reportfile/pid_<pid>/}.
 */
public final class ReportCatalog {
    private ReportCatalog() {
    }

    public static boolean hasReport(File reportfileRoot, long pid) {
        return latestTextFile(reportfileRoot, pid) != null;
    }

    /**
     * Latest text report ({@code diagnose-*.md} or {@code .txt}) for the pid, or null.
     */
    public static File latestTextFile(File reportfileRoot, long pid) {
        if (reportfileRoot == null) {
            return null;
        }
        File pidDir = new File(reportfileRoot, "pid_" + pid);
        if (!pidDir.isDirectory()) {
            return null;
        }
        File[] entries = pidDir.listFiles();
        if (entries == null || entries.length == 0) {
            return null;
        }
        Arrays.sort(entries, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());
            }
        });
        File best = null;
        long bestMtime = Long.MIN_VALUE;
        for (int i = 0; i < entries.length; i++) {
            File entry = entries[i];
            if (entry.isFile() && isTextReport(entry)) {
                long mt = entry.lastModified();
                if (best == null || mt >= bestMtime) {
                    best = entry;
                    bestMtime = mt;
                }
                continue;
            }
            if (!entry.isDirectory()) {
                continue;
            }
            File nested = latestInDir(entry);
            if (nested == null) {
                continue;
            }
            // Timestamp directory names sort lexicographically newest-first.
            return nested;
        }
        return best;
    }

    private static File latestInDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                int byName = b.getName().compareTo(a.getName());
                if (byName != 0) {
                    return byName;
                }
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = 0; i < files.length; i++) {
            if (isTextReport(files[i])) {
                return files[i];
            }
        }
        return null;
    }

    static boolean isTextReport(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String n = file.getName();
        return n.startsWith("diagnose-") && (n.endsWith(".md") || n.endsWith(".txt"));
    }
}
