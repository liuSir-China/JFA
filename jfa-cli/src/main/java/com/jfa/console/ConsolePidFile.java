package com.jfa.console;

import com.jfa.common.config.JfaConfig;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Install-dir PID file for {@code ./jfa start} / {@code ./jfa stop}:
 * {@code <install>/run/jfa-console.pid}.
 */
public final class ConsolePidFile {
    public static final String FILENAME = "jfa-console.pid";

    private ConsolePidFile() {
    }

    public static File file(JfaConfig config) {
        File run = config == null ? new File("run") : config.getRunRoot();
        return new File(run, FILENAME).getAbsoluteFile();
    }

    public static long currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        String pid = at > 0 ? name.substring(0, at) : name;
        try {
            return Long.parseLong(pid.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public static void writeCurrent(JfaConfig config) {
        write(config, currentPid());
    }

    public static void write(JfaConfig config, long pid) {
        File f = file(config);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + parent);
        }
        try {
            Files.write(f.toPath(), (String.valueOf(pid) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("无法写入 PID 文件: " + f, e);
        }
    }

    public static Long readPid(JfaConfig config) {
        File f = file(config);
        if (!f.isFile()) {
            return null;
        }
        try {
            String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return null;
            }
            return Long.valueOf(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isRunning(JfaConfig config) {
        Long pid = readPid(config);
        return pid != null && isProcessAlive(pid.longValue());
    }

    /**
     * True when a <em>different</em> process holds the console PID file.
     * Used by {@code jfa start} so the wrapper-written self pid is not treated
     * as an already-running instance.
     */
    public static boolean isForeignInstanceRunning(JfaConfig config) {
        Long pid = readPid(config);
        return pid != null && pid.longValue() != currentPid() && isProcessAlive(pid.longValue());
    }

    public static boolean isProcessAlive(long pid) {
        if (pid <= 0) {
            return false;
        }
        File proc = new File("/proc/" + pid);
        if (proc.exists()) {
            return true;
        }
        return windowsOs() && windowsProcessAlive(pid);
    }

    private static boolean windowsOs() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static boolean windowsProcessAlive(long pid) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c", "tasklist /FI \"PID eq " + pid + "\" /NH");
            pb.redirectErrorStream(true);
            p = pb.start();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int n;
            java.io.InputStream in = p.getInputStream();
            while ((n = in.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            p.waitFor();
            String out = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            return out.contains(String.valueOf(pid));
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    public static void delete(JfaConfig config) {
        File f = file(config);
        if (f.isFile() && !f.delete()) {
            f.deleteOnExit();
        }
    }

    public static void deleteIfCurrent(JfaConfig config) {
        Long pid = readPid(config);
        if (pid != null && pid.longValue() == currentPid()) {
            delete(config);
        }
    }

    /**
     * Send SIGTERM and wait briefly; SIGKILL if still alive.
     *
     * @return true if the process is gone (or was already gone)
     */
    public static boolean terminate(long pid) {
        if (pid <= 0) {
            return true;
        }
        if (!isProcessAlive(pid)) {
            return true;
        }
        kill(pid, false);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (isProcessAlive(pid) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (isProcessAlive(pid)) {
            kill(pid, true);
            long hard = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (isProcessAlive(pid) && System.nanoTime() < hard) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return !isProcessAlive(pid);
    }

    private static void kill(long pid, boolean force) {
        try {
            ProcessBuilder pb;
            if (windowsOs()) {
                pb = force
                        ? new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid))
                        : new ProcessBuilder("taskkill", "/PID", String.valueOf(pid));
            } else {
                pb = force
                        ? new ProcessBuilder("kill", "-9", String.valueOf(pid))
                        : new ProcessBuilder("kill", String.valueOf(pid));
                File devNull = new File("/dev/null");
                pb.redirectError(ProcessBuilder.Redirect.to(devNull));
                pb.redirectOutput(ProcessBuilder.Redirect.to(devNull));
            }
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
