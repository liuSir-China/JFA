package com.jfa.console;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ReportCatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void missingPidDirIsNotAnalyzed() throws Exception {
        File root = tmp.newFolder("reportfile");
        Assert.assertFalse(ReportCatalog.hasReport(root, 9L));
        Assert.assertNull(ReportCatalog.latestTextFile(root, 9L));
    }

    @Test
    public void latestTimestampDirWins() throws Exception {
        File root = tmp.newFolder("reportfile");
        File pid = new File(root, "pid_42");
        File older = new File(pid, "20260101-010101");
        File newer = new File(pid, "20260916-120000");
        Assert.assertTrue(older.mkdirs());
        Assert.assertTrue(newer.mkdirs());
        Files.write(new File(older, "diagnose-old.md").toPath(), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(newer, "diagnose-new.md").toPath(), "new-report".getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(ReportCatalog.hasReport(root, 42L));
        File latest = ReportCatalog.latestTextFile(root, 42L);
        Assert.assertNotNull(latest);
        Assert.assertEquals("diagnose-new.md", latest.getName());
        Assert.assertEquals("new-report", new String(Files.readAllBytes(latest.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void emptyPidDirIsNotAnalyzed() throws Exception {
        File root = tmp.newFolder("reportfile");
        File pid = new File(root, "pid_7");
        Assert.assertTrue(pid.mkdirs());
        Assert.assertTrue(new File(pid, "20260101-000000").mkdirs());
        Assert.assertFalse(ReportCatalog.hasReport(root, 7L));
    }
}
