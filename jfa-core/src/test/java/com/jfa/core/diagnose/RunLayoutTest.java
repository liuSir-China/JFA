package com.jfa.core.diagnose;

import com.jfa.common.config.JfaConfig;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class RunLayoutTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void pidKeyPrefersNumericPid() {
        Assert.assertEquals("pid_12", RunLayout.pidKey(12L, new File("x"), null, null));
    }

    @Test
    public void pidKeyParsesJavaPidHprof() {
        File hprof = new File("/var/app/java_pid99.hprof");
        Assert.assertEquals("pid_99", RunLayout.pidKey(null, null, hprof, null));
    }

    @Test
    public void pidKeyOfflineWhenUnknown() {
        Assert.assertEquals("pid_offline", RunLayout.pidKey(null, new File("evidence"), null, null));
    }

    @Test
    public void coverFileTrueDeletesPreviousPidFolder() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        File root = tmp.newFolder("reportfile");
        cfg.setReportfileRoot(root);
        cfg.setCoverFile(true);
        File first = RunLayout.prepareRunDir(cfg, "pid_1", null);
        File marker = new File(first, "old.md");
        Assert.assertTrue(marker.createNewFile());
        File second = RunLayout.prepareRunDir(cfg, "pid_1", null);
        Assert.assertFalse(marker.exists());
        Assert.assertTrue(second.isDirectory());
        File pidDir = new File(root, "pid_1");
        File[] stamps = pidDir.listFiles();
        Assert.assertNotNull(stamps);
        Assert.assertEquals(1, stamps.length);
    }

    @Test
    public void coverFileFalseKeepsPreviousTimestampDirs() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        File root = tmp.newFolder("reportfile");
        cfg.setReportfileRoot(root);
        cfg.setCoverFile(false);
        File first = RunLayout.prepareRunDir(cfg, "pid_7", null);
        Assert.assertTrue(new File(first, "old.md").createNewFile());
        Thread.sleep(1100L);
        File second = RunLayout.prepareRunDir(cfg, "pid_7", null);
        Assert.assertTrue(new File(first, "old.md").exists());
        Assert.assertFalse(first.getAbsolutePath().equals(second.getAbsolutePath()));
        File pidDir = new File(root, "pid_7");
        File[] stamps = pidDir.listFiles();
        Assert.assertNotNull(stamps);
        Assert.assertEquals(2, stamps.length);
    }

    @Test
    public void ingestCopiesOrLinksIntoDestDir() throws Exception {
        File src = tmp.newFile("src.txt");
        java.nio.file.Files.write(src.toPath(), "hello".getBytes("UTF-8"));
        File destDir = tmp.newFolder("run", "heap");
        File copied = com.jfa.core.io.FileSupport.ingestInto(src, destDir, "src.txt");
        Assert.assertTrue(copied.isFile());
        Assert.assertTrue(copied.getAbsolutePath().contains("heap"));
        Assert.assertEquals("hello", new String(java.nio.file.Files.readAllBytes(copied.toPath()), "UTF-8"));
        Assert.assertTrue(src.isFile());
    }

    @Test
    public void outOverrideDoesNotUseCoverFile() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        cfg.setCoverFile(true);
        File out = tmp.newFolder("explicit-out");
        File marker = new File(out, "keep.md");
        Assert.assertTrue(marker.createNewFile());
        File run = RunLayout.prepareRunDir(cfg, "pid_1", out);
        Assert.assertEquals(out.getAbsoluteFile(), run);
        Assert.assertTrue(marker.exists());
    }
}
