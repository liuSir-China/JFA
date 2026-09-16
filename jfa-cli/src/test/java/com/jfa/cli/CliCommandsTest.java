package com.jfa.cli;

import com.jfa.common.json.JsonSupport;
import com.jfa.common.model.report.DiagnoseReport;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class CliCommandsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void helpAndHelpConfigAndRecommend() {
        Capture c = run(0, "help");
        Assert.assertTrue(c.out.contains("jfa-analyze") || c.out.contains("diagnose"));
        Assert.assertTrue(c.out.contains("--type"));
        Assert.assertTrue(c.out.contains("--compare-after"));
        Assert.assertTrue(c.out.contains("--hprof-prev"));
        Assert.assertTrue(c.out.contains("--quiet"));
        Assert.assertTrue(c.out.contains("[JFA]"));
        Assert.assertTrue(c.out.contains("jfa-config") || c.out.contains("help config"));
        Assert.assertTrue(c.out.startsWith("\n\n") || c.out.startsWith("\r\n\r\n"));
        Assert.assertTrue(c.out.contains("jfa start"));
        Assert.assertTrue(c.out.contains("jfa stop"));
        Assert.assertTrue(c.out.contains("/jfa"));
        Assert.assertTrue(c.out.contains("不必先改") || c.out.contains("不依赖 Web")
                || c.out.contains("CLI 不依赖"));
        Assert.assertFalse(c.out.contains("第一步") && c.out.contains("不要执行 start"));

        Capture startHelp = run(0, "start", "--help");
        Assert.assertTrue(startHelp.out.contains("console.port") || startHelp.out.contains("Web"));
        Capture stopHelp = run(0, "stop", "--help");
        Assert.assertTrue(stopHelp.out.contains("jfa-console.pid") || stopHelp.out.contains("stop"));

        Capture cfg = run(0, "help", "config");
        Capture rec = run(0, "config", "recommend");
        Assert.assertEquals(cfg.out, rec.out);
        Assert.assertTrue(cfg.out.contains("不是使用本产品的前提"));
        Assert.assertTrue(cfg.out.contains("HeapDumpOnOutOfMemoryError"));
        Assert.assertTrue(cfg.out.contains("-Xloggc") || cfg.out.contains("PrintGC"));
        Assert.assertTrue(cfg.out.contains("| 参数 |") || cfg.out.contains("|------|"));
        Assert.assertTrue(cfg.out.contains("#!/bin/bash") || cfg.out.contains("JAVA_OPTS"));
        Assert.assertFalse(cfg.out.contains("Environment="));
        Assert.assertFalse(cfg.out.contains("[Service]"));
        Assert.assertFalse(cfg.out.contains("ExecStart="));
        Assert.assertFalse(cfg.out.contains("JDK 11"));
        Assert.assertFalse(cfg.out.contains("-Xlog:gc"));
        Assert.assertFalse(cfg.out.toLowerCase().contains("mat"));
    }

    @Test
    public void registerForceAndExists() throws Exception {
        File home = tmp.newFolder("jfa-home");
        File cfg = writeConfig(home);
        Capture first = run(0, "--config", cfg.getAbsolutePath(), "register", "--name", "order-svc",
                "--evidence-dir", new File(home, "order-svc").getAbsolutePath());
        Assert.assertTrue(first.out.contains("order-svc"));
        Assert.assertTrue(first.out.contains("lifecycle_managed_by_jfa=false"));
        Capture dup = run(12, "--config", cfg.getAbsolutePath(), "register", "--name", "order-svc");
        Assert.assertTrue(dup.err.contains("E_SERVICE_EXISTS"));
        run(0, "--config", cfg.getAbsolutePath(), "register", "--name", "order-svc", "--force");
    }

    @Test
    public void analyzeDeadlockAndHealthAndModes() throws Exception {
        File testdata = testdata();
        Capture deadlock = run(0, "analyze",
                "--evidence-dir", new File(testdata, "threads").getAbsolutePath(),
                "--thread-dump", new File(testdata, "threads/deadlock-jstack.txt").getAbsolutePath(),
                "--type", "thread",
                "--format", "both",
                "--out", tmp.newFolder("out-d").getAbsolutePath());
        Assert.assertTrue(deadlock.out.contains("报告已写入"));
        Assert.assertTrue(deadlock.out.contains("报告文件") || deadlock.out.contains("JSON 报告"));
        Assert.assertFalse("console must not dump report body", deadlock.out.contains("## 1. 结论"));
        Assert.assertFalse(deadlock.out.toLowerCase().contains("mat"));
        File deadReport = reportFileFromFooter(deadlock.out);
        Assert.assertTrue(deadReport.isFile());
        String deadBody = new String(java.nio.file.Files.readAllBytes(deadReport.toPath()), StandardCharsets.UTF_8);
        Assert.assertTrue(deadBody.contains("死锁") || deadBody.contains("deadlock"));

        Capture health = run(0, "analyze",
                "--evidence-dir", new File(testdata, "evidence/health-check").getAbsolutePath(),
                "--type", "auto",
                "--format", "json",
                "--out", tmp.newFolder("out-h").getAbsolutePath());
        DiagnoseReport report = JsonSupport.mapper().readValue(health.out, DiagnoseReport.class);
        Assert.assertEquals("2.1", report.getReportSchemaVersion());
        Assert.assertEquals("health_check", report.getReportMode());
        Assert.assertEquals("auto", report.getAnalysisMode());
        Assert.assertFalse(report.getSummary().isFabricatedRootCause());
        Assert.assertFalse(health.out.contains("disclaimer"));
        Assert.assertFalse("JSON stdout must stay parseable", health.out.contains("[JFA]"));
        Assert.assertTrue(health.err.contains("[JFA]"));
        Assert.assertTrue(health.err.contains("JSON 报告") || health.err.contains("报告文件"));
        Assert.assertTrue(health.err.contains("/") || health.err.contains("\\"));

        Capture mem = run(0, "analyze",
                "--gc-log", new File(testdata, "gc/old-gen-spiral.log").getAbsolutePath(),
                "--app-log", new File(testdata, "app/oom-heap-space.log").getAbsolutePath(),
                "--type", "memory",
                "--format", "json",
                "--out", tmp.newFolder("out-m").getAbsolutePath());
        DiagnoseReport e2 = JsonSupport.mapper().readValue(mem.out, DiagnoseReport.class);
        Assert.assertNull(e2.sectionOfType("thread"));
        Assert.assertNotNull(e2.sectionOfType("memory"));
        Assert.assertTrue(mem.out.contains("不能做精确对象归因") || mem.out.contains("E2"));
    }

    @Test
    public void collectWithoutConfirmIsError() throws Exception {
        // Use this JVM's PID so the process exists on Windows and Linux alike.
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Capture c = run(30, "collect", "heapdump", "--pid", pid);
        Assert.assertTrue(c.err.contains("E_CONFIRM_REQUIRED"));
        Assert.assertTrue(c.err.contains("STW") || c.err.contains("磁盘"));
        Capture s = run(30, "collect", "sample", "--pid", pid);
        Assert.assertTrue(s.err.contains("E_CONFIRM_REQUIRED"));
    }

    @Test
    public void pidNotFound() {
        Capture c = run(10, "diagnose", "--pid", "99999999", "--type", "thread");
        Assert.assertTrue(c.err.contains("E_PID_NOT_FOUND"));
    }

    @Test
    public void evidenceSuggest() {
        Capture c = run(0, "evidence", "suggest", "--cmd", "java -jar app.jar");
        Assert.assertTrue(c.out.contains("不是使用本产品的前提"));
        Assert.assertTrue(c.out.contains("HeapDumpOnOutOfMemoryError"));
    }

    @Test
    public void unknownType() {
        Capture c = run(2, "analyze", "--type", "magic", "--evidence-dir", tmp.getRoot().getAbsolutePath());
        Assert.assertTrue(c.err.contains("E_USAGE") || c.err.contains("未知 --type"));
    }

    @Test
    public void analyzeDefaultLayoutPrintsAbsoluteReportPath() throws Exception {
        File install = tmp.newFolder("jfa-install");
        File confDir = new File(install, "conf");
        Assert.assertTrue(confDir.mkdirs());
        File cfg = new File(confDir, "jfa.properties");
        java.nio.file.Files.write(cfg.toPath(),
                ("cover.file=true\nretention.days=7\n"
                        + "min.free.bytes=1\nmin.free.ratio=0\n"
                        + "sample.interval.seconds=5\nsample.count=8\n"
                        + "log.lookback.minutes=10\ncompare.top.n=20\n")
                        .getBytes(StandardCharsets.UTF_8));
        File testdata = testdata();
        Capture c = run(0, "--config", cfg.getAbsolutePath(), "analyze",
                "--evidence-dir", new File(testdata, "evidence/health-check").getAbsolutePath(),
                "--type", "auto",
                "--format", "text");
        Assert.assertTrue(c.out.contains("报告已写入"));
        Assert.assertTrue(c.out.contains("报告文件:"));
        Assert.assertFalse("console must not dump report body", c.out.contains("健康体检") && c.out.contains("## 1"));
        String marker = "报告文件:";
        int i = c.out.indexOf(marker);
        Assert.assertTrue(i >= 0);
        String pathLine = c.out.substring(i + marker.length()).trim().split("\\r?\\n")[0].trim();
        File report = new File(pathLine);
        Assert.assertTrue("expected report file: " + pathLine, report.isFile());
        String norm = report.getAbsolutePath().replace('\\', '/');
        Assert.assertTrue(norm.contains("/reportfile/"));
        Assert.assertTrue(norm.contains("/pid_"));
        Assert.assertTrue(report.getName().endsWith(".md"));
    }

    @Test
    public void defaultProgressGoesToStderrQuietKeepsReportPaths() throws Exception {
        File testdata = testdata();
        Capture def = run(0, "analyze",
                "--evidence-dir", new File(testdata, "evidence/health-check").getAbsolutePath(),
                "--thread-dump", new File(testdata, "evidence/health-check/threads/td.txt").getAbsolutePath(),
                "--type", "auto",
                "--format", "text",
                "--out", tmp.newFolder("out-prog").getAbsolutePath());
        Assert.assertTrue(def.err.contains("[JFA] 准备运行目录") || def.err.contains("[JFA] 生成报告"));
        Assert.assertTrue(def.err.contains("[JFA]"));
        Assert.assertFalse(def.out.contains("[JFA]"));
        Assert.assertTrue(def.out.contains("报告文件:"));

        Capture quiet = run(0, "--quiet", "analyze",
                "--evidence-dir", new File(testdata, "evidence/health-check").getAbsolutePath(),
                "--thread-dump", new File(testdata, "evidence/health-check/threads/td.txt").getAbsolutePath(),
                "--type", "auto",
                "--format", "text",
                "--out", tmp.newFolder("out-quiet").getAbsolutePath());
        Assert.assertFalse(quiet.err.contains("[JFA]"));
        Assert.assertFalse(quiet.out.contains("[JFA]"));
        Assert.assertTrue(quiet.out.contains("报告文件:"));

        Capture verbose = run(0, "--verbose", "analyze",
                "--gc-log", new File(testdata, "gc/old-gen-spiral.log").getAbsolutePath(),
                "--app-log", new File(testdata, "app/oom-heap-space.log").getAbsolutePath(),
                "--type", "memory",
                "--format", "json",
                "--out", tmp.newFolder("out-verbose").getAbsolutePath());
        JsonSupport.mapper().readValue(verbose.out, DiagnoseReport.class);
        Assert.assertFalse(verbose.out.contains("[JFA]"));
        Assert.assertTrue(verbose.err.contains("[JFA] 开始倒查近") || verbose.err.contains("[JFA] 定位应用日志"));
        Assert.assertTrue(verbose.err.contains("JSON 报告") || verbose.err.contains("报告文件"));
    }


    @Test
    public void bareDiagnoseAnalyzeCollectPrintChineseHelp() {
        Capture d = run(0, "diagnose");
        Assert.assertTrue(d.out.contains("jfa-analyze") || d.out.contains("活体诊断"));
        Assert.assertTrue(d.out.contains("--pid"));
        Assert.assertTrue(d.out.contains("目标 Java 进程"));

        Capture a = run(0, "analyze");
        Assert.assertTrue(a.out.contains("jfa-file-analyze") || a.out.contains("离线分析"));
        Assert.assertTrue(a.out.contains("--hprof") || a.out.contains("--evidence-dir"));

        Capture c = run(0, "collect");
        Assert.assertTrue(c.out.contains("jfa-collect") || c.out.contains("采集"));
        Assert.assertTrue(c.out.contains("heapdump") || c.out.contains("threaddump"));
    }

    @Test
    public void mapInvocationAliases() {
        Assert.assertArrayEquals(new String[]{"discover"}, JfaMain.mapInvocation("jfa", new String[0]));
        Assert.assertArrayEquals(new String[]{"help", "config"}, JfaMain.mapInvocation("jfa", new String[]{"--help"}));
        Assert.assertArrayEquals(new String[]{"help", "config"}, JfaMain.mapInvocation("jfa", new String[]{"help"}));
        Assert.assertArrayEquals(new String[]{"diagnose"}, JfaMain.mapInvocation("jfa-analyze", new String[0]));
        Assert.assertArrayEquals(new String[]{"diagnose", "--pid", "1"},
                JfaMain.mapInvocation("jfa-analyze", new String[]{"--pid", "1"}));
        Assert.assertArrayEquals(new String[]{"diagnose", "--pid", "1"},
                JfaMain.mapInvocation("jfa-analyze", new String[]{"diagnose", "--pid", "1"}));
        Assert.assertArrayEquals(new String[]{"analyze"}, JfaMain.mapInvocation("jfa-file-analyze", new String[0]));
        Assert.assertArrayEquals(new String[]{"collect"}, JfaMain.mapInvocation("jfa-collect", new String[0]));
        Assert.assertArrayEquals(new String[]{"help", "config"}, JfaMain.mapInvocation("jfa-config", new String[0]));
        Assert.assertArrayEquals(new String[]{"start"}, JfaMain.mapInvocation("jfa", new String[]{"start"}));
        Assert.assertArrayEquals(new String[]{"stop"}, JfaMain.mapInvocation("jfa", new String[]{"stop"}));
        Assert.assertArrayEquals(new String[]{"--config", "x", "start"},
                JfaMain.mapInvocation("jfa", new String[]{"--config", "x", "start"}));
        Assert.assertArrayEquals(new String[]{"--config", "x", "stop"},
                JfaMain.mapInvocation("jfa", new String[]{"--config", "x", "stop"}));
        Assert.assertArrayEquals(new String[]{"start", "--help"},
                JfaMain.mapInvocation("jfa", new String[]{"start", "--help"}));
        Assert.assertArrayEquals(new String[]{"discover", "--user", "app"},
                JfaMain.mapInvocation("jfa", new String[]{"--user", "app"}));
    }

    @Test
    public void stopWhenNotRunningIsOk() throws Exception {
        File home = tmp.newFolder("jfa-home-stop");
        File cfg = writeConfig(home);
        Capture c = run(0, "--config", cfg.getAbsolutePath(), "stop");
        Assert.assertTrue(c.out.contains("not running"));
    }

    @Test
    public void stopTerminatesPidFileProcess() throws Exception {
        File home = tmp.newFolder("jfa-home-kill");
        File cfgFile = writeConfig(home);
        Process proc = new ProcessBuilder("sh", "-c", "echo $$; exec sleep 30").start();
        java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        long pid = Long.parseLong(br.readLine().trim());
        com.jfa.common.config.JfaConfig cfg = com.jfa.common.config.JfaConfig.load(cfgFile);
        com.jfa.console.ConsolePidFile.write(cfg, pid);
        Capture c = run(0, "--config", cfgFile.getAbsolutePath(), "stop");
        Assert.assertTrue(c.out.contains("stopped"));
        Assert.assertFalse(com.jfa.console.ConsolePidFile.isProcessAlive(pid));
    }

    private static File reportFileFromFooter(String out) {
        String marker = "报告文件:";
        int i = out.indexOf(marker);
        Assert.assertTrue("missing report path footer: " + out, i >= 0);
        String pathLine = out.substring(i + marker.length()).trim().split("\\r?\\n")[0].trim();
        return new File(pathLine);
    }

    private File writeConfig(File home) throws Exception {
        File conf = new File(home, "conf");
        Assert.assertTrue(conf.mkdirs() || conf.isDirectory());
        File cfg = new File(conf, "jfa.properties");
        java.nio.file.Files.write(cfg.toPath(),
                ("cover.file=true\nretention.days=7\nmin.free.bytes=1\nmin.free.ratio=0\n")
                        .getBytes(StandardCharsets.UTF_8));
        return cfg;
    }

    private static File testdata() {
        String p = System.getProperty("jfa.testdata");
        if (p != null) {
            return new File(p);
        }
        File f = new File("../testdata");
        if (f.isDirectory()) {
            return f;
        }
        return new File("testdata");
    }

    private Capture run(int expected, String... args) {
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
        int code;
        try {
            code = new JfaMain().run(args);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        Capture c = new Capture();
        c.code = code;
        c.out = new String(out.toByteArray(), StandardCharsets.UTF_8);
        c.err = new String(err.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals("out=" + c.out + " err=" + c.err, expected, c.code);
        return c;
    }

    private static class Capture {
        int code;
        String out;
        String err;
    }
}
