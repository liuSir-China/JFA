package com.jfa.core.analyze;

import org.junit.Assert;
import org.junit.Test;

public class JstatGcutilAnalyzerTest {
    @Test
    public void climbingOldAfterFgc() {
        String raw = ""
                + "  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT\n"
                + "  0.00  99.00  80.00  40.00  90.00  80.00     10    0.20     1    0.50    0.70\n"
                + "  0.00  10.00  20.00  55.00  90.00  80.00     12    0.25     2    1.10    1.35\n"
                + "  0.00   0.00  30.00  70.00  91.00  81.00     14    0.30     3    2.00    2.30\n";
        JstatGcutilAnalyzer.SampleTrend t = new JstatGcutilAnalyzer().analyze(raw);
        Assert.assertTrue(t.available);
        Assert.assertEquals("climbing_old_no_reclaim", t.judgment);
        Assert.assertTrue(t.summary.contains("爬升") || t.summary.contains("Old"));
        Assert.assertEquals(3, t.rows.size());
        Assert.assertTrue(t.hasMetaspace);
    }

    @Test
    public void peakThenDropIsJitter() {
        String raw = ""
                + "  S0     S1     E      O      P     YGC     YGCT    FGC    FGCT     GCT\n"
                + "  0.00  50.00  40.00  30.00  80.00     4    0.10     0    0.00    0.10\n"
                + "  0.00  10.00  90.00  62.00  80.00     5    0.12     1    0.40    0.52\n"
                + "  0.00  20.00  10.00  33.00  80.00     6    0.14     1    0.40    0.54\n";
        JstatGcutilAnalyzer.SampleTrend t = new JstatGcutilAnalyzer().analyze(raw);
        Assert.assertEquals("peak_jitter", t.judgment);
    }

    @Test
    public void stableOld() {
        String raw = ""
                + "  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT\n"
                + "  0.00  12.00  40.00  41.00  90.00  80.00     8    0.20     1    0.50    0.70\n"
                + "  0.00   8.00  55.00  42.00  90.00  80.00     9    0.22     1    0.50    0.72\n"
                + "  0.00  15.00  20.00  41.50  90.00  80.00    10    0.24     1    0.50    0.74\n";
        JstatGcutilAnalyzer.SampleTrend t = new JstatGcutilAnalyzer().analyze(raw);
        Assert.assertEquals("stable", t.judgment);
    }

    @Test
    public void emptyIsUnavailable() {
        JstatGcutilAnalyzer.SampleTrend t = new JstatGcutilAnalyzer().analyze("");
        Assert.assertFalse(t.available);
        Assert.assertEquals("unavailable", t.judgment);
    }
}
