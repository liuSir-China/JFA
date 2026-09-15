package com.jfa.core.evidence;

import com.jfa.common.JfaConstants;
import com.jfa.common.config.JfaConfig;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class EvidenceAndConfigTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void helpConfigIsTablesPlusBashOnlyJdk8() {
        String t = RecommendedJvmConfig.fullHelpConfig();
        Assert.assertTrue(t.contains("不是使用本产品的前提") || t.contains(JfaConstants.EVIDENCE_ENHANCE_OPTIONAL));
        Assert.assertTrue(t.contains("HeapDumpOnOutOfMemoryError"));
        Assert.assertTrue(t.contains("HeapDumpPath"));
        Assert.assertTrue(t.contains("-Xloggc") || t.contains("PrintGC"));
        Assert.assertTrue(t.contains("UseGCLogFileRotation") || t.contains("NumberOfGCLogFiles"));
        Assert.assertTrue(t.contains("| 参数 |") || t.contains("|------|"));
        Assert.assertTrue(t.contains("#!/bin/bash") || t.contains("JAVA_OPTS"));
        Assert.assertFalse(t.contains("Environment="));
        Assert.assertFalse(t.contains("[Service]"));
        Assert.assertFalse(t.contains("ExecStart="));
        Assert.assertFalse(t.contains("JDK 11"));
        Assert.assertFalse(t.contains("-Xlog:gc"));
        Assert.assertFalse(t.toLowerCase().contains("mat"));
        Assert.assertFalse(t.contains("jfa start") && t.indexOf("不要先 start") < 0);
    }

    @Test
    public void suggestDeclaresNotPrerequisite() {
        String t = new EvidenceEnhancer().suggest(JfaConfig.defaults(), null, null, "java -jar app.jar");
        Assert.assertTrue(t.contains("不是使用本产品的前提"));
        Assert.assertTrue(t.contains("HeapDumpOnOutOfMemoryError"));
        Assert.assertTrue(t.contains("缺少"));
    }

    @Test
    public void evidenceGcDeletesExpiredOnly() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        File reportfile = tmp.newFolder("reportfile");
        cfg.setReportfileRoot(reportfile);
        File pidDir = new File(new File(reportfile, "pid_1"), "20200101-000000");
        File heap = new File(new File(pidDir, "heap"), "old.hprof");
        heap.getParentFile().mkdirs();
        File newRun = new File(new File(reportfile, "pid_1"), "20990101-000000");
        File newF = new File(new File(newRun, "heap"), "new.hprof");
        newF.getParentFile().mkdirs();
        Assert.assertTrue(heap.createNewFile());
        Assert.assertTrue(newF.createNewFile());
        heap.setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000);
        newF.setLastModified(System.currentTimeMillis());
        EvidenceGc.GcReport r = new EvidenceGc().gc(cfg, null, false);
        Assert.assertTrue(r.deleted.toString().contains("old.hprof"));
        Assert.assertTrue(newF.exists());
        Assert.assertFalse(heap.exists());
    }

    @Test
    public void evidenceGcDoesNotDeleteOutsideManagedDirs() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        File reportfile = tmp.newFolder("reportfile");
        cfg.setReportfileRoot(reportfile);
        File outside = new File(tmp.getRoot(), "app-dumps/java_pid1.hprof");
        outside.getParentFile().mkdirs();
        Assert.assertTrue(outside.createNewFile());
        outside.setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000);
        EvidenceGc.GcReport r = new EvidenceGc().gc(cfg, null, true);
        Assert.assertFalse(r.deleted.toString().contains("java_pid1.hprof"));
    }
}
