package com.jfa.core.analyze;

import com.jfa.common.EvidenceLevel;
import com.jfa.common.model.report.Recommendation;
import com.jfa.core.collect.EvidencePack;
import com.jfa.core.testsupport.HeapDumpSupport;
import com.jfa.core.testsupport.TestDataPaths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class OomEngineTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void e3HprofIsActionableWithoutExternalToolWording() throws Exception {
        File hprof = new File(tmp.getRoot(), "unbounded-cache.hprof");
        HeapDumpSupport.leakyDump(hprof);
        EvidencePack pack = EvidencePack.index(tmp.getRoot(), null, hprof,
                TestDataPaths.file("gc/old-gen-spiral.log"),
                TestDataPaths.file("app/oom-heap-space.log"), null);
        OomEngine.MemoryAnalysis a = new OomEngine().analyze(pack, false, null);
        Assert.assertEquals(EvidenceLevel.E3, a.level);
        Assert.assertFalse(a.recommendations.getCode().isEmpty());
        Recommendation rec = a.recommendations.getCode().get(0);
        Assert.assertNotNull(rec.getWhat());
        Assert.assertNotNull(rec.getWhy());
        Assert.assertNotNull(rec.getHowToVerify());
        Assert.assertFalse(a.suspects.isEmpty());
        String blob = a.recommendations.getCode().toString() + a.recommendations.getOps().toString()
                + a.next.toString() + a.suspects.toString();
        Assert.assertFalse(blob.toLowerCase().contains("mat"));
        Assert.assertTrue(a.hprofSummary.topClasses.size() > 0);
    }

    @Test
    public void e2GcLogCannotDoObjectAttribution() {
        EvidencePack pack = EvidencePack.index(TestDataPaths.file("evidence/order-svc-e2"), null,
                null, TestDataPaths.file("evidence/order-svc-e2/gc/gc.log"),
                TestDataPaths.file("app/oom-heap-space.log"), null);
        OomEngine.MemoryAnalysis a = new OomEngine().analyze(pack, false, null);
        Assert.assertEquals(EvidenceLevel.E2, a.level);
        Assert.assertTrue(a.gcSummary.contains("不能做精确对象归因") || a.oneLineFault.contains("不能做精确对象归因"));
        Assert.assertTrue(a.suspects.isEmpty() || !a.oneLineFault.contains("OrderCache 持有约"));
    }

    @Test
    public void e1StackOnlyIsWeak() {
        EvidencePack pack = EvidencePack.index(TestDataPaths.file("evidence/order-svc-e1"), null,
                null, null, TestDataPaths.file("evidence/order-svc-e1/app.log"), null);
        OomEngine.MemoryAnalysis a = new OomEngine().analyze(pack, false, null);
        Assert.assertEquals(EvidenceLevel.E1, a.level);
        Assert.assertTrue(a.oomConfirmed);
        Assert.assertFalse(a.missing.isEmpty());
        Assert.assertTrue(a.oneLineFault.contains("不能做对象归因") || a.oneLineFault.contains("弱结论"));
    }

    @Test
    public void e0NoFabricatedRootCause() {
        EvidencePack pack = EvidencePack.index(TestDataPaths.file("evidence/order-svc-e0"), null,
                null, null, null, null);
        OomEngine.MemoryAnalysis a = new OomEngine().analyze(pack, true, "-Xmx512m -jar app.jar");
        Assert.assertEquals(EvidenceLevel.E0, a.level);
        Assert.assertFalse(a.oomConfirmed);
        Assert.assertTrue(a.suspects.isEmpty());
        Assert.assertNotNull(a.capabilityLimit);
        Assert.assertFalse(a.oneLineFault.contains("肯定是"));
        Assert.assertFalse(a.oneLineFault.contains("缓存泄漏"));
    }
}
