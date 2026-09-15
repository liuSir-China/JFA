package com.jfa.core.collect;

import com.jfa.common.model.ServiceMeta;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EvidencePack {
    private File evidenceDir;
    private File hprof;
    private File gcLog;
    private File appLog;
    private File threadDump;
    private File jstatSample;
    private ServiceMeta meta;

    public File getEvidenceDir() {
        return evidenceDir;
    }

    public void setEvidenceDir(File evidenceDir) {
        this.evidenceDir = evidenceDir;
    }

    public File getHprof() {
        return hprof;
    }

    public void setHprof(File hprof) {
        this.hprof = hprof;
    }

    public File getGcLog() {
        return gcLog;
    }

    public void setGcLog(File gcLog) {
        this.gcLog = gcLog;
    }

    public File getAppLog() {
        return appLog;
    }

    public void setAppLog(File appLog) {
        this.appLog = appLog;
    }

    public File getThreadDump() {
        return threadDump;
    }

    public void setThreadDump(File threadDump) {
        this.threadDump = threadDump;
    }

    public File getJstatSample() {
        return jstatSample;
    }

    public void setJstatSample(File jstatSample) {
        this.jstatSample = jstatSample;
    }

    public ServiceMeta getMeta() {
        return meta;
    }

    public void setMeta(ServiceMeta meta) {
        this.meta = meta;
    }

    public static EvidencePack index(File evidenceDir, ServiceMeta meta,
                                     File explicitHprof, File explicitGc, File explicitApp, File explicitTd) {
        EvidencePack pack = new EvidencePack();
        pack.evidenceDir = evidenceDir;
        pack.meta = meta;
        pack.hprof = firstExisting(explicitHprof, newestSuffix(evidenceDir, ".hprof"),
                glob(meta, evidenceDir, meta == null ? null : meta.getPaths().getHprofGlob(), ".hprof"));
        pack.gcLog = firstExisting(explicitGc, newestIn(new File(evidenceDir, "gc")),
                glob(meta, evidenceDir, meta == null ? null : meta.getPaths().getGcLogGlob(), ".log"));
        pack.appLog = firstExisting(explicitApp,
                meta != null && meta.getPaths() != null && meta.getPaths().getAppLog() != null
                        ? new File(meta.getPaths().getAppLog()) : null,
                newestIn(new File(evidenceDir, "logs")),
                newestSuffix(evidenceDir, ".log"));
        pack.threadDump = firstExisting(explicitTd, newestIn(new File(evidenceDir, "threads")),
                glob(meta, evidenceDir, meta == null ? null : meta.getPaths().getThreadDumpGlob(), ".txt"));
        pack.jstatSample = newestIn(new File(evidenceDir, "samples"));
        return pack;
    }

    private static File glob(ServiceMeta meta, File evidenceDir, String pattern, String suffix) {
        if (pattern != null) {
            File newest = FileSupport.newest(FileSupport.globFiles(evidenceDir, pattern));
            if (newest != null) {
                return newest;
            }
        }
        if (".hprof".equals(suffix) || ".txt".equals(suffix)) {
            return newestSuffix(evidenceDir, suffix);
        }
        File typed = newestIn(new File(evidenceDir, "gc"));
        return typed;
    }

    private static File newestSuffix(File dir, String suffix) {
        if (dir == null) {
            return null;
        }
        return FileSupport.newest(FileSupport.findBySuffix(dir, suffix));
    }

    private static File newestIn(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        List<File> files = new ArrayList<File>();
        for (File c : children) {
            if (c.isFile()) {
                files.add(c);
            }
        }
        return FileSupport.newest(files);
    }

    private static File firstExisting(File... files) {
        if (files == null) {
            return null;
        }
        for (File f : files) {
            if (f != null && f.isFile()) {
                return f.getAbsoluteFile();
            }
        }
        return null;
    }
}
