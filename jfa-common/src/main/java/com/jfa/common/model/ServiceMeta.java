package com.jfa.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceMeta {
    @JsonProperty("service_id")
    private String serviceId;
    @JsonProperty("display_name")
    private String displayName;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    private Match match = new Match();
    private Paths paths = new Paths();
    @JsonProperty("java_home")
    private String javaHome;
    @JsonProperty("lifecycle_managed_by_jfa")
    private boolean lifecycleManagedByJfa;
    private String notes;

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match == null ? new Match() : match;
    }

    public Paths getPaths() {
        return paths;
    }

    public void setPaths(Paths paths) {
        this.paths = paths == null ? new Paths() : paths;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public boolean isLifecycleManagedByJfa() {
        return lifecycleManagedByJfa;
    }

    public void setLifecycleManagedByJfa(boolean lifecycleManagedByJfa) {
        this.lifecycleManagedByJfa = lifecycleManagedByJfa;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Match {
        @JsonProperty("main_class_contains")
        private String mainClassContains;
        @JsonProperty("last_pid")
        private Long lastPid;
        @JsonProperty("systemd_unit")
        private String systemdUnit;

        public String getMainClassContains() {
            return mainClassContains;
        }

        public void setMainClassContains(String mainClassContains) {
            this.mainClassContains = mainClassContains;
        }

        public Long getLastPid() {
            return lastPid;
        }

        public void setLastPid(Long lastPid) {
            this.lastPid = lastPid;
        }

        public String getSystemdUnit() {
            return systemdUnit;
        }

        public void setSystemdUnit(String systemdUnit) {
            this.systemdUnit = systemdUnit;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Paths {
        @JsonProperty("evidence_dir")
        private String evidenceDir;
        @JsonProperty("app_log")
        private String appLog;
        @JsonProperty("gc_log_glob")
        private String gcLogGlob;
        @JsonProperty("hprof_glob")
        private String hprofGlob;
        @JsonProperty("thread_dump_glob")
        private String threadDumpGlob;

        public String getEvidenceDir() {
            return evidenceDir;
        }

        public void setEvidenceDir(String evidenceDir) {
            this.evidenceDir = evidenceDir;
        }

        public String getAppLog() {
            return appLog;
        }

        public void setAppLog(String appLog) {
            this.appLog = appLog;
        }

        public String getGcLogGlob() {
            return gcLogGlob;
        }

        public void setGcLogGlob(String gcLogGlob) {
            this.gcLogGlob = gcLogGlob;
        }

        public String getHprofGlob() {
            return hprofGlob;
        }

        public void setHprofGlob(String hprofGlob) {
            this.hprofGlob = hprofGlob;
        }

        public String getThreadDumpGlob() {
            return threadDumpGlob;
        }

        public void setThreadDumpGlob(String threadDumpGlob) {
            this.threadDumpGlob = threadDumpGlob;
        }
    }

    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("service_id", serviceId);
        return m;
    }
}
