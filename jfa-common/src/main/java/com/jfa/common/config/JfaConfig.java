package com.jfa.common.config;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.io.InstallHome;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Local JFA configuration. Defaults keep data on-box. Dangerous collects use
 * interactive y/n or {@code --confirm}; trading-hours knobs are not used.
 */
public class JfaConfig {
    private File reportfileRoot;
    private File registryRoot;
    private int retentionDays = 7;
    private long minFreeBytes = 1L * 1024 * 1024 * 1024;
    private double minFreeRatio = 0.05d;
    private boolean coverFile = true;
    private int sampleIntervalSeconds = 5;
    private int sampleCount = 8;
    private int logLookbackMinutes = 10;
    private int compareTopN = 20;
    private String compareAfter;
    private int consolePort = 8080;
    private String consoleBind = "0.0.0.0";
    private File configFile;

    public static JfaConfig load(File configFile) {
        JfaConfig cfg = defaults();
        if (configFile == null) {
            File home = new File(System.getProperty("user.home"), ".jfa");
            File fallback = new File(home, "jfa.properties");
            File env = firstExisting(
                    System.getenv("JFA_CONFIG"),
                    new File("conf/jfa.properties").getAbsolutePath(),
                    fallback.getAbsolutePath());
            if (env != null && env.isFile()) {
                cfg.applyFile(env);
            }
            return cfg;
        }
        if (!configFile.isFile()) {
            throw new JfaException(ErrorCode.E_USAGE, "配置文件不存在: " + configFile);
        }
        cfg.applyFile(configFile);
        return cfg;
    }

    public static JfaConfig defaults() {
        return new JfaConfig();
    }

    private void applyFile(File file) {
        this.configFile = file;
        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JfaException(ErrorCode.E_USAGE, "无法读取配置: " + file, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
        String reportRoot = p.getProperty("reportfile.root");
        if (reportRoot != null && !reportRoot.trim().isEmpty()) {
            this.reportfileRoot = new File(expand(reportRoot.trim()));
        }
        String regRoot = p.getProperty("registry.root");
        if (regRoot != null && !regRoot.trim().isEmpty()) {
            this.registryRoot = new File(expand(regRoot.trim()));
        }
        this.retentionDays = intProp(p, "retention.days", retentionDays);
        this.minFreeBytes = longProp(p, "min.free.bytes", minFreeBytes);
        this.minFreeRatio = doubleProp(p, "min.free.ratio", minFreeRatio);
        this.coverFile = boolProp(p, "cover.file", coverFile);
        this.sampleIntervalSeconds = intProp(p, "sample.interval.seconds", sampleIntervalSeconds);
        this.sampleCount = intProp(p, "sample.count", sampleCount);
        this.logLookbackMinutes = intProp(p, "log.lookback.minutes", logLookbackMinutes);
        this.compareTopN = intProp(p, "compare.top.n", compareTopN);
        String after = p.getProperty("compare.after");
        if (after != null && !after.trim().isEmpty()) {
            this.compareAfter = after.trim();
        }
        this.consolePort = intProp(p, "console.port", consolePort);
        String bind = p.getProperty("console.bind");
        if (bind != null && !bind.trim().isEmpty()) {
            this.consoleBind = bind.trim();
        }
    }

    private static File firstExisting(String... paths) {
        if (paths == null) {
            return null;
        }
        for (String path : paths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            File f = new File(path);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private String expand(String value) {
        String home = System.getProperty("user.home");
        if (home != null) {
            value = value.replace("${user.home}", home);
        }
        File install = InstallHome.detect(configFile);
        if (install != null) {
            value = value.replace("${jfa.install.home}", install.getAbsolutePath());
        }
        return value;
    }

    private static int intProp(Properties p, String key, int dflt) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            return dflt;
        }
        return Integer.parseInt(v.trim());
    }

    private static long longProp(Properties p, String key, long dflt) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            return dflt;
        }
        return Long.parseLong(v.trim());
    }

    private static double doubleProp(Properties p, String key, double dflt) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            return dflt;
        }
        return Double.parseDouble(v.trim());
    }

    private static boolean boolProp(Properties p, String key, boolean dflt) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            return dflt;
        }
        return Boolean.parseBoolean(v.trim());
    }

    /**
     * Output root for diagnose/analyze runs. Defaults to {@code <install>/reportfile}.
     */
    public File getReportfileRoot() {
        if (reportfileRoot != null) {
            return reportfileRoot.getAbsoluteFile();
        }
        return new File(InstallHome.detect(configFile), "reportfile").getAbsoluteFile();
    }

    public void setReportfileRoot(File reportfileRoot) {
        this.reportfileRoot = reportfileRoot;
    }

    /**
     * Service registry root (meta.json only). Defaults to {@code <install>/registry}.
     * Diagnose/analyze evidence is never written here.
     */
    public File getRegistryRoot() {
        if (registryRoot != null) {
            return registryRoot.getAbsoluteFile();
        }
        return new File(InstallHome.detect(configFile), "registry").getAbsoluteFile();
    }

    public void setRegistryRoot(File registryRoot) {
        this.registryRoot = registryRoot;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getMinFreeBytes() {
        return minFreeBytes;
    }

    public double getMinFreeRatio() {
        return minFreeRatio;
    }

    public boolean isCoverFile() {
        return coverFile;
    }

    public void setCoverFile(boolean coverFile) {
        this.coverFile = coverFile;
    }

    public int getSampleIntervalSeconds() {
        return sampleIntervalSeconds;
    }

    public void setSampleIntervalSeconds(int sampleIntervalSeconds) {
        this.sampleIntervalSeconds = sampleIntervalSeconds;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = sampleCount;
    }

    public int getLogLookbackMinutes() {
        return logLookbackMinutes;
    }

    public void setLogLookbackMinutes(int logLookbackMinutes) {
        this.logLookbackMinutes = logLookbackMinutes;
    }

    public int getCompareTopN() {
        return compareTopN;
    }

    public void setCompareTopN(int compareTopN) {
        this.compareTopN = compareTopN;
    }

    public String getCompareAfter() {
        return compareAfter;
    }

    public void setCompareAfter(String compareAfter) {
        this.compareAfter = compareAfter;
    }

    public File getConfigFile() {
        return configFile;
    }

    /**
     * Web console HTTP port ({@code console.port}). {@code 0} means an ephemeral port.
     */
    public int getConsolePort() {
        return consolePort;
    }

    public void setConsolePort(int consolePort) {
        this.consolePort = consolePort;
    }

    /**
     * Web console bind address ({@code console.bind}). Default {@code 0.0.0.0}.
     */
    public String getConsoleBind() {
        return consoleBind == null || consoleBind.trim().isEmpty() ? "0.0.0.0" : consoleBind;
    }

    public void setConsoleBind(String consoleBind) {
        this.consoleBind = consoleBind;
    }

    /**
     * Runtime dir for the console PID file: {@code <install>/run}.
     */
    public File getRunRoot() {
        return new File(InstallHome.detect(configFile), "run").getAbsoluteFile();
    }

    public File serviceDir(String serviceId) {
        return new File(getRegistryRoot(), serviceId);
    }
}
