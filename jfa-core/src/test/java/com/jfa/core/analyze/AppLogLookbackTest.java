package com.jfa.core.analyze;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AppLogLookbackTest {
    @Test
    public void hitsOomInsideWindow() {
        long now = System.currentTimeMillis();
        String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(now - 60_000L));
        String log = ts + " ERROR com.example.biz - boom\n"
                + "java.lang.OutOfMemoryError: Java heap space\n"
                + "\tat com.example.cache.OrderCache.put(OrderCache.java:44)\n";
        AppLogLookback.Result r = new AppLogLookback().scan(log, now, 10);
        Assert.assertTrue(r.oomInWindow);
        Assert.assertFalse(r.hits.isEmpty());
        Assert.assertTrue(r.summary.contains("命中"));
    }

    @Test
    public void noHitsInWindow() {
        long now = System.currentTimeMillis();
        String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(now - 60_000L));
        String log = ts + " INFO com.example.biz - ok\n";
        AppLogLookback.Result r = new AppLogLookback().scan(log, now, 10);
        Assert.assertFalse(r.oomInWindow);
        Assert.assertTrue(r.hits.isEmpty());
        Assert.assertTrue(r.summary.contains("无"));
    }

    @Test
    public void oldOomOutsideWindowIgnored() {
        long now = System.currentTimeMillis();
        String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(now - 40L * 60_000L));
        String log = ts + " ERROR boom\njava.lang.OutOfMemoryError: Java heap space\n";
        AppLogLookback.Result r = new AppLogLookback().scan(log, now, 10);
        Assert.assertFalse(r.oomInWindow);
        Assert.assertTrue(r.hits.isEmpty());
    }

    @Test
    public void noTimestampScansFullFile() {
        String log = "ERROR deadlock detected in worker\n";
        AppLogLookback.Result r = new AppLogLookback().scan(log, System.currentTimeMillis(), 10);
        Assert.assertFalse(r.hadTimestamps);
        Assert.assertEquals(1, r.hits.size());
    }
}
