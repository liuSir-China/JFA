package com.jfa.core.collect;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.common.time.TimeSupport;
import com.jfa.core.disk.DiskGuard;
import com.jfa.core.io.FileSupport;
import com.jfa.core.proc.JdkToolLocator;
import com.jfa.core.proc.ProcessRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JdkCollectors {
    private final JdkToolLocator locator = new JdkToolLocator();
    private final ProcessRunner runner = new ProcessRunner();
    private final DiskGuard diskGuard;
    private final JfaConfig config;

    public JdkCollectors(JfaConfig config) {
        this.config = config;
        this.diskGuard = new DiskGuard(config);
    }

    public File collectThreadDump(JavaProcessInfo proc, File evidenceDir) {
        File dir = new File(evidenceDir, "threads");
        FileSupport.mkdirs(dir);
        File out = new File(dir, "td-" + TimeSupport.nowFileStamp() + ".txt");
        String text = threadDumpText(proc);
        FileSupport.writeUtf8(out, text);
        return out;
    }

    public String threadDumpText(JavaProcessInfo proc) {
        File jcmd = locator.findTool("jcmd", proc.getJavaHome());
        if (jcmd != null) {
            ProcessRunner.Result r = runner.run(
                    Arrays.asList(jcmd.getAbsolutePath(), String.valueOf(proc.getPid()), "Thread.print"),
                    30000L);
            if (r.exitCode == 0 && r.stdout != null && r.stdout.trim().length() > 0) {
                return r.stdout;
            }
            if (looksLikePermission(r)) {
                throw new JfaException(ErrorCode.E_PERM_ATTACH,
                        "无权 attach JVM pid=" + proc.getPid()
                                + "。请使用与目标进程相同的操作系统用户执行，或按手册提权。");
            }
        }
        File jstack = locator.findTool("jstack", proc.getJavaHome());
        if (jstack != null) {
            ProcessRunner.Result r = runner.run(
                    Arrays.asList(jstack.getAbsolutePath(), String.valueOf(proc.getPid())),
                    30000L);
            if (r.exitCode == 0 && r.stdout != null && r.stdout.trim().length() > 0) {
                return r.stdout;
            }
            if (looksLikePermission(r)) {
                throw new JfaException(ErrorCode.E_PERM_ATTACH,
                        "无权 attach JVM pid=" + proc.getPid()
                                + "。请使用与目标进程相同的操作系统用户执行。");
            }
            throw new JfaException(ErrorCode.E_PERM_ATTACH,
                    "jstack 失败 pid=" + proc.getPid() + ": " + trim(r.stderr + r.stdout));
        }
        throw new JfaException(ErrorCode.E_PERM_ATTACH,
                "未找到 jcmd/jstack。请设置 JAVA_HOME 指向与目标兼容的 JDK（含 jcmd）。");
    }

    public File collectHeapDump(JavaProcessInfo proc, File evidenceDir, boolean confirm) {
        ConfirmGate.assertDumpAllowed(confirm);
        File dir = new File(evidenceDir, "heap");
        FileSupport.mkdirs(dir);
        diskGuard.assertCanWriteLarge(dir);
        File out = new File(dir, "heap-" + TimeSupport.nowFileStamp() + ".hprof");
        File jcmd = locator.findTool("jcmd", proc.getJavaHome());
        if (jcmd != null) {
            ProcessRunner.Result r = runner.run(Arrays.asList(
                    jcmd.getAbsolutePath(), String.valueOf(proc.getPid()),
                    "GC.heap_dump", out.getAbsolutePath()), 180000L);
            if (r.exitCode == 0 && out.isFile() && out.length() > 0) {
                return out;
            }
            if (looksLikePermission(r)) {
                throw new JfaException(ErrorCode.E_PERM_ATTACH,
                        "无权 attach JVM pid=" + proc.getPid() + " 做 heap dump。请使用同用户执行。");
            }
        }
        File jmap = locator.findTool("jmap", proc.getJavaHome());
        if (jmap != null) {
            ProcessRunner.Result r = runner.run(Arrays.asList(
                    jmap.getAbsolutePath(),
                    "-dump:format=b,file=" + out.getAbsolutePath(),
                    String.valueOf(proc.getPid())), 180000L);
            if (out.isFile() && out.length() > 0) {
                return out;
            }
            if (looksLikePermission(r)) {
                throw new JfaException(ErrorCode.E_PERM_ATTACH,
                        "无权 jmap dump pid=" + proc.getPid() + "。请使用同用户执行。");
            }
            throw new JfaException(ErrorCode.E_INTERNAL, "heap dump 失败: " + trim(r.stderr + r.stdout));
        }
        throw new JfaException(ErrorCode.E_PERM_ATTACH, "未找到 jcmd/jmap，无法生成 hprof。");
    }

    public File collectJstatSample(JavaProcessInfo proc, File evidenceDir, boolean confirm,
                                   String interval, String duration) {
        ConfirmGate.assertDumpAllowed(confirm);
        File dir = new File(evidenceDir, "samples");
        FileSupport.mkdirs(dir);
        File jstat = locator.findTool("jstat", proc.getJavaHome());
        if (jstat == null) {
            throw new JfaException(ErrorCode.E_PERM_ATTACH, "未找到 jstat。");
        }
        long durationMs = parseDuration(duration);
        String intervalArg = interval == null ? "1000" : intervalToMs(interval);
        List<String> cmd = new ArrayList<String>();
        cmd.add(jstat.getAbsolutePath());
        cmd.add("-gc");
        cmd.add(String.valueOf(proc.getPid()));
        cmd.add(intervalArg);
        ProcessRunner.Result r = runner.run(cmd, durationMs + 5000L);
        File out = new File(dir, "jstat-" + TimeSupport.nowFileStamp() + ".txt");
        FileSupport.writeUtf8(out, r.stdout == null ? "" : r.stdout);
        return out;
    }

    /**
     * Product-executed short {@code jstat -gcutil} sampling. Not a dangerous collect gate.
     *
     * @return raw output file, or null if jstat is missing
     */
    public File collectGcutilSample(JavaProcessInfo proc, File runDir, int intervalSeconds, int count) {
        File dir = new File(runDir, "samples");
        FileSupport.mkdirs(dir);
        File jstat = locator.findTool("jstat", proc.getJavaHome());
        if (jstat == null) {
            return null;
        }
        int interval = Math.max(1, intervalSeconds);
        int n = Math.max(1, count);
        long timeout = interval * 1000L * n + 8000L;
        List<String> cmd = new ArrayList<String>();
        cmd.add(jstat.getAbsolutePath());
        cmd.add("-gcutil");
        cmd.add(String.valueOf(proc.getPid()));
        cmd.add(String.valueOf(interval * 1000));
        cmd.add(String.valueOf(n));
        ProcessRunner.Result r;
        try {
            r = runner.run(cmd, timeout);
        } catch (JfaException e) {
            File out = new File(dir, "jstat-gcutil-" + TimeSupport.nowFileStamp() + ".txt");
            FileSupport.writeUtf8(out, "jstat failed: " + e.getMessage());
            return out;
        }
        String body = r.stdout == null ? "" : r.stdout;
        if (r.stderr != null && !r.stderr.trim().isEmpty()) {
            body = body + (body.endsWith("\n") ? "" : "\n") + r.stderr;
        }
        File out = new File(dir, "jstat-gcutil-" + TimeSupport.nowFileStamp() + ".txt");
        FileSupport.writeUtf8(out, body);
        return out;
    }

    /**
     * Collect a heap dump after confirmation has already been obtained for this command.
     */
    public File collectHeapDumpConfirmed(JavaProcessInfo proc, File evidenceDir) {
        return collectHeapDump(proc, evidenceDir, true);
    }

    public String jstatOnce(JavaProcessInfo proc) {
        File jstat = locator.findTool("jstat", proc.getJavaHome());
        if (jstat == null) {
            return null;
        }
        ProcessRunner.Result r = runner.run(Arrays.asList(
                jstat.getAbsolutePath(), "-gcutil", String.valueOf(proc.getPid())), 10000L);
        if (r.exitCode == 0) {
            return r.stdout;
        }
        return null;
    }

    private static boolean looksLikePermission(ProcessRunner.Result r) {
        String t = ((r.stderr == null ? "" : r.stderr) + " " + (r.stdout == null ? "" : r.stdout)).toLowerCase();
        return t.contains("well-known file is not secure")
                || t.contains("permission denied")
                || t.contains("unable to attach")
                || t.contains("not permitted")
                || t.contains("operation not permitted");
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        return s.length() > 400 ? s.substring(0, 400) : s;
    }

    private static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 60000L;
        }
        return TimeSupport.parseDurationMs(duration);
    }

    private static String intervalToMs(String interval) {
        long ms = TimeSupport.parseDurationMs(interval);
        return String.valueOf(ms);
    }
}
