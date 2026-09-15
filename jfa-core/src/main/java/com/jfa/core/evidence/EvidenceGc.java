package com.jfa.core.evidence;

import com.jfa.common.config.JfaConfig;
import com.jfa.core.io.FileSupport;
import com.jfa.core.registry.ServiceRegistry;

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
        List<Root> roots = new ArrayList<Root>();
        if (serviceId != null) {
            roots.add(new Root(new ServiceRegistry(config).evidenceDirOf(
                    new ServiceRegistry(config).require(serviceId)), false));
        } else {
            if (config.getEvidenceRoot() != null) {
                roots.add(new Root(config.getEvidenceRoot(), false));
            }
            File reportfile = config.getReportfileRoot();
            if (reportfile != null) {
                roots.add(new Root(reportfile, true));
            }
        }
        GcReport r = new GcReport();
        r.dryRun = dryRun;
        for (Root root : roots) {
            if (root.dir == null || !root.dir.exists()) {
                continue;
            }
            for (File f : FileSupport.listFilesRecursive(root.dir)) {
                String path = f.getAbsolutePath().replace('\\', '/');
                if (path.endsWith("meta.json")) {
                    continue;
                }
                if (!root.entireTree && !isManagedCategory(path)) {
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
        return r;
    }

    /**
     * Category folders under JFA workspace. Application dump/GC paths outside these
     * directories are never deleted.
     */
    static boolean isManagedCategory(String path) {
        String n = path.replace('\\', '/');
        return n.contains("/heap/") || n.contains("/threads/")
                || n.contains("/reports/") || n.contains("/samples/")
                || n.contains("/gc/");
    }

    private static final class Root {
        final File dir;
        final boolean entireTree;

        Root(File dir, boolean entireTree) {
            this.dir = dir;
            this.entireTree = entireTree;
        }
    }
}
