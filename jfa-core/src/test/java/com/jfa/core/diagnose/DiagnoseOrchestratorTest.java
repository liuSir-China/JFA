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

import java.io.File;

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
        Assert.assertTrue(r.getText().contains("采集时间线") || r.getText().contains("## 2"));
        Assert.assertFalse(r.getText().contains("## 3. 证据"));
        Assert.assertFalse(r.getText().contains("## 7"));
        Assert.assertFalse(r.getText().contains("声明"));
        Assert.assertFalse(r.getJson().contains("disclaimer"));
        Assert.assertFalse(r.getJson().contains("\"sections\""));
        Assert.assertFalse(r.getJson().contains("\"evidence\""));
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
        DiagnoseResult r = new DiagnoseOrchestrator().run(req);
        Assert.assertNotNull(r.getTextFile());
        String path = r.getTextFile().getAbsolutePath().replace('\\', '/');
        Assert.assertTrue(path.contains("/pid_"));
        Assert.assertTrue(r.getTextFile().getParentFile().getParentFile().getName().startsWith("pid_"));
        Assert.assertEquals("reportfile", r.getTextFile().getParentFile().getParentFile().getParentFile().getName());
        Assert.assertTrue(r.getTextFile().getName().endsWith(".md"));
        Assert.assertTrue(r.getText().contains("健康体检"));
    }

    private DiagnoseResult run(File evidenceDir, AnalysisMode mode, File hprof, File gc, File td) {
        DiagnoseRequest req = base(evidenceDir);
        req.setMode(mode);
        req.setHprof(hprof);
        req.setGcLog(gc);
        req.setThreadDump(td);
        return new DiagnoseOrchestrator().run(req);
    }

    private DiagnoseRequest base(File evidenceDir) {
        JfaConfig cfg = JfaConfig.defaults();
        cfg.setEvidenceRoot(tmp.getRoot());
        DiagnoseRequest req = new DiagnoseRequest();
        req.setConfig(cfg);
        req.setEvidenceDir(evidenceDir);
        req.setLiveCollect(false);
        req.setFormat(OutputFormat.BOTH);
        req.setOutDir(new File(tmp.getRoot(), "reports"));
        return req;
    }

    private static String joinNext(ReportSection sec) {
        return String.valueOf(sec.getNextMinimalActions()) + sec.getRecommendations().getOps();
    }
}
