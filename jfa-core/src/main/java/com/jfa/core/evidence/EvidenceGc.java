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
        List<File> roots = new ArrayList<File>();
        if (serviceId != null) {
            roots.add(new ServiceRegistry(config).evidenceDirOf(
                    new ServiceRegistry(config).require(serviceId)));
        } else if (config.getEvidenceRoot() != null) {
            roots.add(config.getEvidenceRoot());
        }
        GcReport r = new GcReport();
        r.dryRun = dryRun;
        for (File root : roots) {
            for (File f : FileSupport.listFilesRecursive(root)) {
                // Normalize separators so Windows paths match category folders too.
                String path = f.getAbsolutePath().replace('\\', '/');
                if (path.endsWith("meta.json")) {
                    continue;
                }
                if (!(path.contains("/heap/") || path.contains("/threads/")
                        || path.contains("/reports/") || path.contains("/samples/")
                        || path.contains("/gc/"))) {
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
}
