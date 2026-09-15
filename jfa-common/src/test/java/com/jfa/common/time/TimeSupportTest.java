package com.jfa.common.time;

import org.junit.Assert;
import org.junit.Test;

public class TimeSupportTest {
    @Test
    public void parseCompareAfterDurations() {
        Assert.assertEquals(15L * 60L * 1000L, TimeSupport.parseDurationMs("15m"));
        Assert.assertEquals(90L * 1000L, TimeSupport.parseDurationMs("90s"));
        Assert.assertEquals(3600L * 1000L, TimeSupport.parseDurationMs("1h"));
        Assert.assertEquals(0L, TimeSupport.parseDurationMs("0s"));
        Assert.assertEquals(500L, TimeSupport.parseDurationMs("500ms"));
        Assert.assertEquals(15L * 60L * 1000L, TimeSupport.parseDurationMs("15min"));
    }
}
