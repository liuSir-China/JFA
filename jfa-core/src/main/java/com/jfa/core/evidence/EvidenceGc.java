package com.jfa.core.evidence;

import com.jfa.common.config.JfaConfig;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EvidenceGc {
    public static class GcReport {
        public final List<String> deleted = new ArrayList<String>();
        public long bytesFreed;
        public boolean dryRun;
    }

    public GcReport gc(JfaConfig config, String serviceId, boolean dryRun) {
        long cutoff = System.currentTimeMillis() - config.getRetentionDays() * 24L * 3600L * 1000L;
        GcReport r = new GcReport();
        r.dryRun = dryRun;
        File reportfile = config.getReportfileRoot();
        if (reportfile != null && reportfile.exists()) {
            sweep(reportfile, cutoff, true, r, dryRun);
        }
        return r;
    }

    private static void sweep(File dir, long cutoff, boolean entireTree, GcReport r, boolean dryRun) {
        if (dir == null || !dir.exists()) {
            return;
        }
        for (File f : FileSupport.listFilesRecursive(dir)) {
            String path = f.getAbsolutePath().replace('\\', '/');
            if (path.endsWith("meta.json")) {
                continue;
            }
            if (!entireTree && !isManagedCategory(path)) {
                continue;
            }
            if (f.lastModified() >= cutoff) {
                continue;
            }
            long sz = f.length();
            r.deleted.add(f.getAbsolutePath());
            r.bytesFreed += sz;
            if (!dryRun) {
                f.delete();
            }
        }
    }

    /**
     * Category folders under a JFA-managed tree. Application dump/GC paths outside
     * reportfile run directories are never deleted.
     */
    static boolean isManagedCategory(String path) {
        String n = path.replace('\\', '/');
        return n.contains("/heap/") || n.contains("/threads/")
                || n.contains("/reports/") || n.contains("/samples/")
                || n.contains("/gc/") || n.contains("/logs/");
    }
}
