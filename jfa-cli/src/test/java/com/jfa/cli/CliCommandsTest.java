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
        Assert.assertTrue(c.out.contains("diagnose"));
        Assert.assertTrue(c.out.contains("--type"));
        Assert.assertTrue(c.out.contains("help config"));
        Assert.assertFalse(c.out.contains("第一步") && c.out.contains("jfa start"));
        Assert.assertTrue(c.out.contains("无需 jfa start") || c.out.contains("不要先 start")
                || c.out.contains("无需 jfa start"));

        Capture cfg = run(0, "help", "config");
        Capture rec = run(0, "config", "recommend");
        Assert.assertEquals(cfg.out, rec.out);
        Assert.assertTrue(cfg.out.contains("不是使用本产品的前提"));
        Assert.assertTrue(cfg.out.contains("HeapDumpOnOutOfMemoryError"));
        Assert.assertTrue(cfg.out.contains("-Xloggc") || cfg.out.contains("PrintGC"));
        Assert.assertTrue(cfg.out.contains("Environment=") || cfg.out.contains("[Service]"));
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
        Assert.assertTrue(deadlock.out.contains("死锁") || deadlock.out.contains("deadlock"));
        Assert.assertFalse(deadlock.out.toLowerCase().contains("mat"));

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

    private File writeConfig(File home) throws Exception {
        File cfg = new File(home, "jfa.properties");
        java.nio.file.Files.write(cfg.toPath(),
                ("evidence.root=" + home.getAbsolutePath().replace("\\", "/") + "\nretention.days=7\n")
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
