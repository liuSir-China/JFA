package com.jfa.core.diagnose;

import com.jfa.common.AnalysisMode;
import com.jfa.common.JfaConstants;
import com.jfa.common.OutputFormat;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.model.report.DiagnoseReport;
import com.jfa.common.model.report.ReportSection;
import com.jfa.core.testsupport.HeapDumpSupport;
import com.jfa.core.testsupport.TestDataPaths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

public class DiagnoseOrchestratorTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void deadlockAnalyzeProducesActionableReport() throws Exception {
        File dir = tmp.newFolder("deadlock");
        File td = new File(new File(dir, "threads"), "td.txt");
        td.getParentFile().mkdirs();
        java.nio.file.Files.copy(TestDataPaths.file("threads/deadlock-jstack.txt").toPath(), td.toPath());
        DiagnoseResult r = run(dir, AnalysisMode.THREAD, null, null, td);
        DiagnoseReport report = r.getReport();
        Assert.assertEquals(JfaConstants.REPORT_SCHEMA_VERSION, report.getReportSchemaVersion());
        Assert.assertEquals("fault", report.getReportMode());
        Assert.assertEquals("thread", report.getAnalysisMode());
        Assert.assertTrue(report.getSummary().getFaultKinds().contains("deadlock"));
        ReportSection sec = report.sectionOfType("thread");
        Assert.assertEquals(Boolean.TRUE, sec.getQualification().get("deadlock_found"));
        Assert.assertFalse(sec.getRecommendations().getCode().isEmpty());
        Assert.assertTrue(r.getText().contains("统一加锁") || r.getText().contains("tryLock")
                || r.getText().contains("加锁顺序"));
        Assert.assertFalse(r.getJson().toLowerCase().contains("mat"));
        Assert.assertFalse(r.getText().toLowerCase().contains("mat"));
        Assert.assertFalse(r.getText().contains("## 7"));
        Assert.assertFalse(r.getText().contains("声明"));
        Assert.assertFalse(r.getJson().contains("disclaimer"));
        Assert.assertTrue(r.getTextFile().getName().endsWith(".md"));
    }

    @Test
    public void healthCheckOffline() throws Exception {
        DiagnoseResult r = run(TestDataPaths.file("evidence/health-check"), AnalysisMode.AUTO, null, null,
                TestDataPaths.file("evidence/health-check/threads/td.txt"));
        Assert.assertEquals("health_check", r.getReport().getReportMode());
        Assert.assertTrue(r.getReport().getSummary().getOneLine().contains("未发现死锁"));
        Assert.assertTrue(r.getReport().getSummary().getOneLine().contains("未发现堆 OOM"));
        Assert.assertEquals(Boolean.FALSE, r.getReport().getSummary().getHealth().getDeadlockFound());
        Assert.assertFalse(r.getReport().getSummary().isFabricatedRootCause());
        Assert.assertEquals(0, r.getExitCode());
        Assert.assertTrue(r.getText().contains("健康体检"));
        Assert.assertTrue(r.getText().contains("采集时间线") || r.getText().contains("## 1"));
        Assert.assertFalse(r.getText().contains("## 7"));
        Assert.assertFalse(r.getText().contains("声明"));
        Assert.assertFalse(r.getJson().contains("disclaimer"));
        Assert.assertTrue(r.getJson().contains("\"evidence\"") || r.getReport().getEvidence() != null);
        Assert.assertTrue(r.getTextFile().getName().endsWith(".md"));
    }

    @Test
    public void e3OfflineHprofMustBeActionable() throws Exception {
        File dir = tmp.newFolder("e3");
        File heap = new File(new File(dir, "heap"), "leak.hprof");
        heap.getParentFile().mkdirs();
        HeapDumpSupport.leakyDump(heap);
        DiagnoseRequest req = base(dir);
        req.setMode(AnalysisMode.MEMORY);
        req.setHprof(heap);
        req.setGcLog(TestDataPaths.file("gc/old-gen-spiral.log"));
        req.setAppLog(TestDataPaths.file("app/oom-heap-space.log"));
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        Assert.assertEquals("2.1", r.getReport().getReportSchemaVersion());
        ReportSection mem = r.getReport().sectionOfType("memory");
        Assert.assertEquals("E3", mem.getQualification().get("evidence_level"));
        Assert.assertFalse(mem.getRecommendations().getCode().isEmpty());
        Assert.assertNotNull(mem.getRecommendations().getCode().get(0).getHowToVerify());
        Assert.assertFalse(r.getJson().toLowerCase().contains("mat"));
        Assert.assertFalse(joinNext(mem).toLowerCase().contains("mat"));
        Assert.assertFalse(r.getText().contains("## 7"));
        Assert.assertFalse(r.getJson().contains("disclaimer"));
    }

    @Test
    public void typeMemorySkipsThreadSection() throws Exception {
        DiagnoseResult r = run(TestDataPaths.file("evidence/order-svc-e2"), AnalysisMode.MEMORY,
                null, TestDataPaths.file("evidence/order-svc-e2/gc/gc.log"), null);
        Assert.assertNull(r.getReport().sectionOfType("thread"));
        Assert.assertNotNull(r.getReport().sectionOfType("memory"));
        Assert.assertEquals("memory", r.getReport().getAnalysisMode());
    }

    @Test
    public void deadProcessNoThreadDump() throws Exception {
        File dir = tmp.newFolder("dead");
        DiagnoseResult r = run(dir, AnalysisMode.THREAD, null, null, null);
        Assert.assertEquals(40, r.getExitCode());
        ReportSection sec = r.getReport().sectionOfType("thread");
        Assert.assertFalse(sec.getNextMinimalActions().isEmpty());
        Assert.assertTrue(sec.getMissingEvidence().toString().contains("thread dump"));
        Assert.assertFalse(r.getText().contains("Found one Java-level deadlock") && r.getText().contains("伪造"));
    }

    @Test
    public void defaultLayoutWritesUnderReportfilePidTimestamp() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        File reportfile = tmp.newFolder("reportfile");
        cfg.setReportfileRoot(reportfile);
        cfg.setCoverFile(true);
        DiagnoseRequest req = new DiagnoseRequest();
        req.setConfig(cfg);
        req.setEvidenceDir(TestDataPaths.file("evidence/health-check"));
        req.setThreadDump(TestDataPaths.file("evidence/health-check/threads/td.txt"));
        req.setMode(AnalysisMode.AUTO);
        req.setLiveCollect(false);
        req.setFormat(OutputFormat.BOTH);
        req.setProgressStream(utf8Stream(new ByteArrayOutputStream()));
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        Assert.assertNotNull(r.getTextFile());
        String path = r.getTextFile().getAbsolutePath().replace('\\', '/');
        Assert.assertTrue(path.contains("/pid_"));
        Assert.assertTrue(r.getTextFile().getParentFile().getParentFile().getName().startsWith("pid_"));
        Assert.assertEquals("reportfile", r.getTextFile().getParentFile().getParentFile().getParentFile().getName());
        Assert.assertTrue(r.getTextFile().getName().endsWith(".md"));
        Assert.assertTrue(r.getText().contains("健康体检"));
    }

    @Test
    public void reusedHprofIsCopiedIntoRunDir() throws Exception {
        File dir = tmp.newFolder("copy-hprof");
        File heap = new File(dir, "outside.hprof");
        HeapDumpSupport.leakyDump(heap);
        DiagnoseRequest req = base(dir);
        req.setMode(AnalysisMode.MEMORY);
        req.setHprof(heap);
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        File run = r.getTextFile().getParentFile();
        Assert.assertTrue(new File(new File(run, "heap"), "outside.hprof").isFile()
                || new File(run, "heap").list() != null && new File(run, "heap").list().length > 0);
        String path = r.getReport().sectionOfType("memory") == null ? "" : r.getJson();
        Assert.assertTrue(r.getText().contains(run.getAbsolutePath())
                || r.getJson().contains("/heap/"));
        Assert.assertFalse("original outside path should not be the only evidence path",
                r.getJson().contains(heap.getAbsolutePath()) && !r.getJson().contains("/heap/"));
        Assert.assertTrue(FileSupportIsUnderRun(run, heap.getName()));
    }

    private static boolean FileSupportIsUnderRun(File run, String name) {
        File heapDir = new File(run, "heap");
        File[] kids = heapDir.listFiles();
        if (kids == null) {
            return false;
        }
        for (File k : kids) {
            if (k.getName().contains("outside") || k.getName().endsWith(".hprof")) {
                return k.length() > 0;
            }
        }
        return false;
    }

    @Test
    public void offlineHprofPrevProducesCompareSection() throws Exception {
        File dir = tmp.newFolder("cmp");
        File older = new File(dir, "older.hprof");
        File newer = new File(dir, "newer.hprof");
        HeapDumpSupport.leakyDump(older);
        com.jfa.testdata.UnboundedOrderCache.fillMore(700);
        HeapDumpSupport.leakyDump(newer);
        DiagnoseRequest req = base(dir);
        req.setMode(AnalysisMode.MEMORY);
        req.setHprof(newer);
        req.setHprofPrev(older);
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        Assert.assertNotNull(r.getReport().sectionOfType("heap_compare"));
        Assert.assertTrue(r.getText().contains("堆对比") || r.getJson().contains("judgment"));
        File run = r.getTextFile().getParentFile();
        Assert.assertTrue(new File(new File(run, "heap"), "compare-summary.json").isFile());
        String[] heapFiles = new File(run, "heap").list();
        Assert.assertNotNull(heapFiles);
        boolean one = false;
        boolean two = false;
        for (String n : heapFiles) {
            if (n.startsWith("heap-1-")) {
                one = true;
            }
            if (n.startsWith("heap-2-")) {
                two = true;
            }
        }
        Assert.assertTrue(one && two);
        Assert.assertFalse(r.getText().contains("## 7"));
        Assert.assertFalse(r.getText().contains("请研发自行"));
    }

    @Test
    public void appLogLookbackCopiesExcerpt() throws Exception {
        File dir = tmp.newFolder("logs-run");
        File log = new File(dir, "app.log");
        long now = System.currentTimeMillis();
        String ts = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(now - 30_000L));
        java.nio.file.Files.write(log.toPath(),
                (ts + " ERROR com.example - java.lang.OutOfMemoryError: Java heap space\n").getBytes("UTF-8"));
        DiagnoseRequest req = base(dir);
        req.setMode(AnalysisMode.MEMORY);
        req.setAppLog(log);
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        Assert.assertNotNull(r.getReport().sectionOfType("log_lookback"));
        Assert.assertTrue(r.getText().contains("日志倒查") || r.getText().contains("OutOfMemoryError"));
        File hits = new File(new File(r.getTextFile().getParentFile(), "logs"), "lookback-hits.txt");
        Assert.assertTrue(hits.isFile());
    }

    @Test
    public void defaultProgressReflectsOfflineAnalyzeSteps() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DiagnoseRequest req = base(TestDataPaths.file("evidence/health-check"));
        req.setThreadDump(TestDataPaths.file("evidence/health-check/threads/td.txt"));
        req.setMode(AnalysisMode.AUTO);
        req.setProgressStream(utf8Stream(buf));
        new DiagnoseOrchestrator().run(req);
        String steps = buf.toString("UTF-8").replace("\r\n", "\n");
        Assert.assertTrue(steps.contains("[JFA] 解析证据目录"));
        Assert.assertTrue(steps.contains("[JFA] 准备运行目录"));
        Assert.assertTrue(steps.contains("[JFA] 复用已有 thread dump") || steps.contains("[JFA] 分析线程 dump"));
        Assert.assertTrue(steps.contains("[JFA] 未发现死锁"));
        Assert.assertTrue(steps.contains("[JFA] 定位应用日志"));
        Assert.assertTrue(steps.contains("[JFA] 分析内存证据"));
        Assert.assertTrue(steps.contains("[JFA] 生成报告"));
    }

    @Test
    public void quietSuppressesMidRunProgress() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DiagnoseRequest req = base(TestDataPaths.file("evidence/health-check"));
        req.setThreadDump(TestDataPaths.file("evidence/health-check/threads/td.txt"));
        req.setMode(AnalysisMode.AUTO);
        req.setQuiet(true);
        req.setProgressStream(utf8Stream(buf));
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        Assert.assertEquals("", buf.toString("UTF-8"));
        Assert.assertNotNull(r.getTextFile());
        Assert.assertTrue(r.getTextFile().isFile());
    }

    @Test
    public void compareAndLookbackProgressAreRealWork() throws Exception {
        File dir = tmp.newFolder("prog-cmp");
        File older = new File(dir, "older.hprof");
        File newer = new File(dir, "newer.hprof");
        HeapDumpSupport.leakyDump(older);
        com.jfa.testdata.UnboundedOrderCache.fillMore(700);
        HeapDumpSupport.leakyDump(newer);
        File log = new File(dir, "app.log");
        long now = System.currentTimeMillis();
        String ts = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(now - 30_000L));
        java.nio.file.Files.write(log.toPath(),
                (ts + " ERROR com.example - java.lang.OutOfMemoryError: Java heap space\n").getBytes("UTF-8"));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DiagnoseRequest req = base(dir);
        req.setMode(AnalysisMode.MEMORY);
        req.setHprof(newer);
        req.setHprofPrev(older);
        req.setAppLog(log);
        req.setProgressStream(utf8Stream(buf));
        new DiagnoseOrchestrator().run(req);
        String steps = buf.toString("UTF-8");
        Assert.assertTrue(steps.contains("[JFA] 复用已有 hprof"));
        Assert.assertTrue(steps.contains("[JFA] 开始倒查近"));
        Assert.assertTrue(steps.contains("[JFA] 日志倒查完成"));
        Assert.assertTrue(steps.contains("[JFA] 开始对比 dump1 vs dump2"));
        Assert.assertTrue(steps.contains("[JFA] 堆对比完成"));
        Assert.assertTrue(steps.contains("[JFA] 生成报告"));
        Assert.assertFalse(steps.contains("[JFA] 开始 jstat 采样"));
    }

    private DiagnoseResult run(File evidenceDir, AnalysisMode mode, File hprof, File gc, File td) {
        DiagnoseRequest req = base(evidenceDir);
        req.setMode(mode);
        req.setHprof(hprof);
        req.setGcLog(gc);
        req.setThreadDump(td);
        req.setLiveCollect(false);
        req.setConfirm(true);
        return new DiagnoseOrchestrator().run(req);
    }

    private DiagnoseRequest base(File evidenceDir) {
        JfaConfig cfg = JfaConfig.defaults();
        cfg.setRegistryRoot(tmp.getRoot());
        cfg.setReportfileRoot(new File(tmp.getRoot(), "reportfile"));
        DiagnoseRequest req = new DiagnoseRequest();
        req.setConfig(cfg);
        req.setEvidenceDir(evidenceDir);
        req.setLiveCollect(false);
        req.setConfirm(true);
        req.setFormat(OutputFormat.BOTH);
        req.setOutDir(new File(tmp.getRoot(), "reports"));
        req.setProgressStream(utf8Stream(new ByteArrayOutputStream()));
        return req;
    }

    private static PrintStream utf8Stream(ByteArrayOutputStream buf) {
        try {
            return new PrintStream(buf, true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String joinNext(ReportSection sec) {
        return String.valueOf(sec.getNextMinimalActions()) + sec.getRecommendations().getOps();
    }
}
