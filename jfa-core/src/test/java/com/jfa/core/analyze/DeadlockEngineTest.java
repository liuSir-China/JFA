package com.jfa.core.analyze;

import com.jfa.core.io.FileSupport;
import com.jfa.core.testsupport.TestDataPaths;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class DeadlockEngineTest {
    @Test
    public void detectsDeadlockRingAndBusinessFrames() {
        String dump = FileSupport.readUtf8(TestDataPaths.file("threads/deadlock-jstack.txt"));
        DeadlockEngine.ThreadAnalysis a = new DeadlockEngine().analyze(dump);
        Assert.assertTrue(a.isDeadlockFound());
        Assert.assertEquals(1, a.getDeadlockCount());
        Assert.assertFalse(a.getSuspects().isEmpty());
        Map<String, Object> s = a.getSuspects().get(0);
        Assert.assertTrue(s.get("threads").toString().contains("pool-1-thread-1"));
        Assert.assertTrue(s.get("threads").toString().contains("pool-1-thread-2"));
        Assert.assertTrue(s.get("frames").toString().contains("TransferService"));
        Assert.assertTrue(s.get("frames").toString().contains("AccountService"));
        Assert.assertTrue(s.get("lock_objects").toString().contains("java.lang.Object"));
    }

    @Test
    public void healthyDumpIsNotDeadlock() {
        String dump = FileSupport.readUtf8(TestDataPaths.file("threads/healthy-jstack.txt"));
        DeadlockEngine.ThreadAnalysis a = new DeadlockEngine().analyze(dump);
        Assert.assertFalse(a.isDeadlockFound());
        Assert.assertEquals(0, a.getDeadlockCount());
    }
}
