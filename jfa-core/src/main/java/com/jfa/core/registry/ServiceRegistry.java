package com.jfa.core.registry;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.json.JsonSupport;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.common.model.ServiceMeta;
import com.jfa.common.time.TimeSupport;
import com.jfa.core.discovery.JavaProcessDiscovery;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServiceRegistry {
    private final JfaConfig config;

    public ServiceRegistry(JfaConfig config) {
        this.config = config;
    }

    public File metaFile(String serviceId) {
        return new File(config.serviceDir(serviceId), "meta.json");
    }

    public ServiceMeta register(String name, Long pid, File evidenceDir, String appLog,
                                String javaHome, boolean force, String displayName, String mainContains) {
        if (name == null || name.trim().isEmpty()) {
            throw new JfaException(ErrorCode.E_USAGE, "register 需要 --name");
        }
        String id = name.trim();
        File dir = evidenceDir != null ? evidenceDir : config.serviceDir(id);
        File meta = new File(dir, "meta.json");
        if (meta.isFile() && !force) {
            throw new JfaException(ErrorCode.E_SERVICE_EXISTS,
                    "服务已存在: " + id + "，使用 --force 更新或更换名称。");
        }
        FileSupport.mkdirs(dir);
        FileSupport.mkdirs(new File(dir, "gc"));
        FileSupport.mkdirs(new File(dir, "heap"));
        FileSupport.mkdirs(new File(dir, "threads"));
        FileSupport.mkdirs(new File(dir, "reports"));
        FileSupport.mkdirs(new File(dir, "samples"));

        ServiceMeta existing = null;
        if (meta.isFile()) {
            existing = readFile(meta);
        }
        ServiceMeta m = existing == null ? new ServiceMeta() : existing;
        String now = TimeSupport.nowIso();
        if (m.getCreatedAt() == null) {
            m.setCreatedAt(now);
        }
        m.setUpdatedAt(now);
        m.setServiceId(id);
        m.setDisplayName(displayName == null ? id : displayName);
        m.setLifecycleManagedByJfa(false);
        m.setNotes("主路径登记；lifecycle_managed_by_jfa 必须可为 false");
        if (javaHome != null) {
            m.setJavaHome(javaHome);
        }
        ServiceMeta.Match match = m.getMatch();
        if (pid != null) {
            match.setLastPid(pid);
            try {
                JavaProcessInfo info = new JavaProcessDiscovery().requirePid(pid);
                if (match.getMainClassContains() == null) {
                    match.setMainClassContains(info.getMainClassOrJar());
                }
                if (m.getJavaHome() == null) {
                    m.setJavaHome(info.getJavaHome());
                }
            } catch (JfaException ignored) {
                // pid optional snapshot
            }
        }
        if (mainContains != null) {
            match.setMainClassContains(mainContains);
        }
        ServiceMeta.Paths paths = m.getPaths();
        paths.setEvidenceDir(dir.getAbsolutePath());
        if (appLog != null) {
            paths.setAppLog(appLog);
        }
        if (paths.getGcLogGlob() == null) {
            paths.setGcLogGlob(new File(dir, "gc").getAbsolutePath() + File.separator + "*.log");
        }
        if (paths.getHprofGlob() == null) {
            paths.setHprofGlob(new File(dir, "heap").getAbsolutePath() + File.separator + "*.hprof");
        }
        if (paths.getThreadDumpGlob() == null) {
            paths.setThreadDumpGlob(new File(dir, "threads").getAbsolutePath() + File.separator + "*.txt");
        }
        write(meta, m);
        File index = metaFile(id);
        try {
            if (!index.getCanonicalFile().equals(meta.getCanonicalFile())) {
                FileSupport.mkdirs(index.getParentFile());
                write(index, m);
            }
        } catch (IOException e) {
            write(index, m);
        }
        return m;
    }

    public ServiceMeta require(String serviceId) {
        File meta = findMeta(serviceId);
        if (meta == null) {
            throw new JfaException(ErrorCode.E_SERVICE_NOT_FOUND,
                    "未登记服务名: " + serviceId + "。先执行 jfa register 或使用 --pid / --evidence-dir。");
        }
        return readFile(meta);
    }

    public File evidenceDirOf(ServiceMeta meta) {
        if (meta.getPaths() != null && meta.getPaths().getEvidenceDir() != null) {
            return new File(meta.getPaths().getEvidenceDir());
        }
        return config.serviceDir(meta.getServiceId());
    }

    public List<ServiceMeta> list() {
        List<ServiceMeta> out = new ArrayList<ServiceMeta>();
        File root = config.getRegistryRoot();
        if (root == null || !root.isDirectory()) {
            return out;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return out;
        }
        for (File child : children) {
            File meta = new File(child, "meta.json");
            if (meta.isFile()) {
                try {
                    out.add(readFile(meta));
                } catch (RuntimeException ignored) {
                    // skip corrupt
                }
            }
        }
        return out;
    }

    private File findMeta(String serviceId) {
        File primary = metaFile(serviceId);
        if (primary.isFile()) {
            return primary;
        }
        File root = config.getRegistryRoot();
        if (root != null && root.isDirectory()) {
            File nested = new File(new File(root, serviceId), "meta.json");
            if (nested.isFile()) {
                return nested;
            }
        }
        return null;
    }

    public ServiceMeta readFile(File meta) {
        try {
            return JsonSupport.mapper().readValue(meta, ServiceMeta.class);
        } catch (IOException e) {
            throw new JfaException(ErrorCode.E_IO_EVIDENCE, "无法读取 meta.json: " + meta, e);
        }
    }

    private void write(File meta, ServiceMeta m) {
        try {
            JsonSupport.mapper().writerWithDefaultPrettyPrinter().writeValue(meta, m);
        } catch (IOException e) {
            throw new JfaException(ErrorCode.E_IO_EVIDENCE, "无法写入 meta.json: " + meta, e);
        }
    }
}
