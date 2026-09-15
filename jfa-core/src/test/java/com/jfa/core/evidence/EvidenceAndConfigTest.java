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
    public void helpConfigIsCopyPasteableAndNotPrerequisite() {
        String t = RecommendedJvmConfig.fullHelpConfig();
        Assert.assertTrue(t.contains("不是使用本产品的前提") || t.contains(JfaConstants.EVIDENCE_ENHANCE_OPTIONAL));
        Assert.assertTrue(t.contains("HeapDumpOnOutOfMemoryError"));
        Assert.assertTrue(t.contains("HeapDumpPath"));
        Assert.assertTrue(t.contains("-Xloggc") || t.contains("PrintGC"));
        Assert.assertTrue(t.contains("UseGCLogFileRotation") || t.contains("NumberOfGCLogFiles"));
        Assert.assertTrue(t.contains("[Service]") || t.contains("Environment="));
        Assert.assertTrue(t.contains("#!/bin/bash") || t.contains("JAVA_OPTS"));
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
        cfg.setEvidenceRoot(tmp.getRoot());
        File svc = new File(tmp.getRoot(), "svc1/heap");
        svc.mkdirs();
        File oldF = new File(svc, "old.hprof");
        File newF = new File(svc, "new.hprof");
        Assert.assertTrue(oldF.createNewFile());
        Assert.assertTrue(newF.createNewFile());
        oldF.setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000);
        newF.setLastModified(System.currentTimeMillis());
        File meta = new File(tmp.getRoot(), "svc1/meta.json");
        String evidenceDir = new File(tmp.getRoot(), "svc1").getAbsolutePath().replace('\\', '/');
        java.nio.file.Files.write(meta.toPath(),
                ("{\"service_id\":\"svc1\",\"paths\":{\"evidence_dir\":\""
                        + evidenceDir + "\"},\"lifecycle_managed_by_jfa\":false}").getBytes("UTF-8"));
        EvidenceGc.GcReport r = new EvidenceGc().gc(cfg, "svc1", false);
        Assert.assertTrue(r.deleted.toString().contains("old.hprof"));
        Assert.assertFalse(newF.exists() && r.deleted.toString().contains("new.hprof") && !newF.exists());
        Assert.assertTrue(newF.exists());
        Assert.assertFalse(oldF.exists());
    }
}
