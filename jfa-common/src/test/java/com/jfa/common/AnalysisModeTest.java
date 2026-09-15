package com.jfa.common;

import org.junit.Assert;
import org.junit.Test;

public class AnalysisModeTest {
    @Test
    public void aliasesMapToPublicNames() {
        Assert.assertEquals(AnalysisMode.AUTO, AnalysisMode.fromCli(null));
        Assert.assertEquals(AnalysisMode.AUTO, AnalysisMode.fromCli("auto"));
        Assert.assertEquals(AnalysisMode.MEMORY, AnalysisMode.fromCli("memory"));
        Assert.assertEquals(AnalysisMode.THREAD, AnalysisMode.fromCli("thread"));
        Assert.assertEquals(AnalysisMode.MEMORY, AnalysisMode.fromCli("oom"));
        Assert.assertEquals(AnalysisMode.MEMORY, AnalysisMode.fromCli("heap"));
        Assert.assertEquals(AnalysisMode.THREAD, AnalysisMode.fromCli("deadlock"));
        Assert.assertEquals(AnalysisMode.AUTO, AnalysisMode.fromCli("both"));
        Assert.assertEquals("memory", AnalysisMode.MEMORY.wireName());
    }

    @Test
    public void errorCodesStable() {
        Assert.assertEquals(0, ErrorCode.OK.exitCode());
        Assert.assertEquals(10, ErrorCode.E_PID_NOT_FOUND.exitCode());
        Assert.assertEquals(12, ErrorCode.E_SERVICE_EXISTS.exitCode());
        Assert.assertEquals(30, ErrorCode.E_CONFIRM_REQUIRED.exitCode());
        Assert.assertEquals(40, ErrorCode.E_NO_THREAD_DUMP.exitCode());
        Assert.assertEquals(50, ErrorCode.E_DISK_FULL.exitCode());
        Assert.assertEquals(99, ErrorCode.E_INTERNAL.exitCode());
    }
}
