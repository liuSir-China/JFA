package com.jfa.core.diagnose;

import com.jfa.common.io.ConsoleLayout;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class ConsoleProgressTest {

    @Before
    public void setUp() {
        ConsoleLayout.resetSession();
    }

    @After
    public void tearDown() {
        ConsoleLayout.resetSession();
    }

    @Test
    public void defaultPrintsMainStepsNotDetails() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ConsoleProgress p = new ConsoleProgress(new PrintStream(buf, true, "UTF-8"), true, false);
        p.step("生成报告 …");
        p.detail("不应出现");
        String s = new String(buf.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        Assert.assertEquals("\n\n[JFA] 生成报告 …\n", s);
    }

    @Test
    public void quietSuppressesStepsEvenWithVerbose() throws Exception {
        DiagnoseRequest req = new DiagnoseRequest();
        req.setQuiet(true);
        req.setVerbose(true);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        req.setProgressStream(new PrintStream(buf, true, "UTF-8"));
        ConsoleProgress p = ConsoleProgress.from(req);
        p.step("生成报告 …");
        p.detail("细节");
        Assert.assertEquals("", new String(buf.toByteArray(), StandardCharsets.UTF_8));
        Assert.assertFalse(p.isEnabled());
    }

    @Test
    public void verboseAddsDetailLines() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ConsoleProgress p = new ConsoleProgress(new PrintStream(buf, true, "UTF-8"), true, true);
        p.step("复用已有 hprof → /tmp/a.hprof");
        p.detail("已纳入运行目录 /tmp/run/heap/a.hprof");
        String s = new String(buf.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        Assert.assertTrue(s.startsWith("\n\n"));
        Assert.assertTrue(s.contains("[JFA] 复用已有 hprof → /tmp/a.hprof"));
        Assert.assertTrue(s.contains("[JFA] 已纳入运行目录 /tmp/run/heap/a.hprof"));
    }

    @Test
    public void formatDurationUsesCompactUnits() {
        Assert.assertEquals("0s", ConsoleProgress.formatDuration(0L));
        Assert.assertEquals("15m", ConsoleProgress.formatDuration(15L * 60_000L));
        Assert.assertEquals("5s", ConsoleProgress.formatDuration(5000L));
        Assert.assertEquals("1500ms", ConsoleProgress.formatDuration(1500L));
        Assert.assertEquals("1m2s", ConsoleProgress.formatDuration(62_000L));
    }
}
