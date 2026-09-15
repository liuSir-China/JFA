package com.jfa.core.analyze.hprof;

import com.jfa.core.testsupport.HeapDumpSupport;
import com.jfa.testdata.UnboundedOrderCache;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;

public class HprofComparerTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void growingCacheIsRetentionLeaning() throws Exception {
        File d1 = new File(tmp.getRoot(), "h1.hprof");
        File d2 = new File(tmp.getRoot(), "h2.hprof");
        HeapDumpSupport.leakyDump(d1);
        UnboundedOrderCache.fillMore(800);
        HeapDumpSupport.leakyDump(d2);
        HprofParser parser = new HprofParser();
        HprofParser.HprofSummary s1 = parser.parse(d1);
        HprofParser.HprofSummary s2 = parser.parse(d2);
        HprofComparer.CompareResult r = new HprofComparer().compare(s1, s2, 20,
                Collections.singletonList("UnboundedOrderCache"));
        Assert.assertTrue(r.usedDelta != 0L || !r.topDeltas.isEmpty());
        Assert.assertNotNull(r.judgment);
        Assert.assertFalse(HprofComparer.SINGLE_SNAPSHOT_ONLY.equals(r.judgment));
        Assert.assertNotNull(r.toJsonMap().get("top_classes"));
    }

    @Test
    public void singleSnapshotWhenOlderMissing() throws Exception {
        File d2 = new File(tmp.getRoot(), "h2.hprof");
        HeapDumpSupport.leakyDump(d2);
        HprofParser.HprofSummary s2 = new HprofParser().parse(d2);
        HprofComparer.CompareResult r = new HprofComparer().compare(null, s2, 20, null);
        Assert.assertEquals(HprofComparer.SINGLE_SNAPSHOT_ONLY, r.judgment);
    }
}
