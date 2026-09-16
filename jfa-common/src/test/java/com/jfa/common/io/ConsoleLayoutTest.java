package com.jfa.common.io;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class ConsoleLayoutTest {

    @Before
    public void setUp() {
        ConsoleLayout.resetSession();
        ConsoleLayout.overrideWidth(Integer.valueOf(80));
    }

    @After
    public void tearDown() {
        ConsoleLayout.resetSession();
        ConsoleLayout.overrideWidth(null);
    }

    @Test
    public void ensureLeadPrintsTwoBlankLinesOnce() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf);
        Assert.assertTrue(ConsoleLayout.ensureLead(ps));
        Assert.assertFalse(ConsoleLayout.ensureLead(ps));
        Assert.assertEquals("\n\n", new String(buf.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n"));
    }

    @Test
    public void centerPadsBannerToWidth() {
        String banner = ConsoleLayout.REPORT_WRITTEN_BANNER;
        String centered = ConsoleLayout.center(banner);
        Assert.assertTrue(centered.endsWith(banner));
        Assert.assertTrue(centered.length() > banner.length());
        int leftPad = (80 - ConsoleLayout.displayWidth(banner)) / 2;
        Assert.assertTrue(centered.startsWith(" "));
        Assert.assertEquals(banner, centered.trim());
        Assert.assertEquals(leftPad, centered.length() - banner.length());
        Assert.assertTrue(ConsoleLayout.isBanner(banner));
        Assert.assertFalse(ConsoleLayout.isBanner("报告文件: /tmp/a.md"));
    }
    @Test
    public void printTextCentersBannersLeavesBody() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf);
        ConsoleLayout.printText(ps, "======== 标题 ========\n参数列表\n");
        String out = new String(buf.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        Assert.assertTrue(out.startsWith("\n\n"));
        Assert.assertTrue(out.contains("======== 标题 ========"));
        String bannerLine = out.substring(2, out.indexOf('\n', 2));
        Assert.assertTrue(bannerLine.startsWith(" "));
        Assert.assertEquals("======== 标题 ========", bannerLine.trim());
        Assert.assertTrue(out.contains("\n参数列表\n"));
    }

    @Test
    public void parseWidthRejectsJunk() {
        Assert.assertNull(ConsoleLayout.parseWidth(null));
        Assert.assertNull(ConsoleLayout.parseWidth("abc"));
        Assert.assertNull(ConsoleLayout.parseWidth("5"));
        Assert.assertEquals(Integer.valueOf(120), ConsoleLayout.parseWidth("120"));
    }
}