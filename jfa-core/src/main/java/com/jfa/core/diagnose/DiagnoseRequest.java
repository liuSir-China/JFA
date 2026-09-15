package com.jfa.core.diagnose;

import com.jfa.common.AnalysisMode;
import com.jfa.common.OutputFormat;
import com.jfa.common.config.JfaConfig;

import java.io.File;

public class DiagnoseRequest {
    private JfaConfig config;
    private Long pid;
    private String service;
    private File evidenceDir;
    private AnalysisMode mode = AnalysisMode.AUTO;
    private File hprof;
    private File gcLog;
    private File appLog;
    private File threadDump;
    private boolean confirm;
    private File outDir;
    private OutputFormat format = OutputFormat.BOTH;
    private boolean liveCollect = true;
    private String commandLineHint;

    public JfaConfig getConfig() {
        return config;
    }

    public void setConfig(JfaConfig config) {
        this.config = config;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public File getEvidenceDir() {
        return evidenceDir;
    }

    public void setEvidenceDir(File evidenceDir) {
        this.evidenceDir = evidenceDir;
    }

    public AnalysisMode getMode() {
        return mode;
    }

    public void setMode(AnalysisMode mode) {
        this.mode = mode;
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

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }

    public File getOutDir() {
        return outDir;
    }

    public void setOutDir(File outDir) {
        this.outDir = outDir;
    }

    public OutputFormat getFormat() {
        return format;
    }

    public void setFormat(OutputFormat format) {
        this.format = format;
    }

    public boolean isLiveCollect() {
        return liveCollect;
    }

    public void setLiveCollect(boolean liveCollect) {
        this.liveCollect = liveCollect;
    }

    public String getCommandLineHint() {
        return commandLineHint;
    }

    public void setCommandLineHint(String commandLineHint) {
        this.commandLineHint = commandLineHint;
    }
}
