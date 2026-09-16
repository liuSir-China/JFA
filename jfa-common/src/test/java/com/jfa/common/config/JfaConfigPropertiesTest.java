package com.jfa.common.config;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class JfaConfigPropertiesTest {
    @Test
    public void defaultFileHasEnglishCommentsCoverFileAndNoTradingKnobs() throws Exception {
        File f = propertiesFile();
        Assert.assertTrue("missing " + f.getAbsolutePath(), f.isFile());
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        Assert.assertFalse(lines.isEmpty());
        String prev = "#";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#") || line.startsWith("!")) {
                prev = line;
                continue;
            }
            Assert.assertTrue("property must have an English comment above it: " + line, prev.startsWith("#"));
            Assert.assertTrue(line.contains("="));
            Assert.assertFalse("jfa.properties comments must be English: " + prev,
                    prev.matches(".*[\\u4e00-\\u9fff].*"));
        }
        String all = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        Assert.assertTrue(all.contains("cover.file=true"));
        Assert.assertFalse(all.contains("evidence.root"));
        Assert.assertTrue(all.contains("retention.days="));
        Assert.assertTrue(all.contains("min.free.bytes="));
        Assert.assertTrue(all.contains("min.free.ratio="));
        Assert.assertTrue(all.contains("sample.interval.seconds="));
        Assert.assertTrue(all.contains("sample.count="));
        Assert.assertTrue(all.contains("log.lookback.minutes="));
        Assert.assertTrue(all.contains("compare.top.n="));
        Assert.assertTrue(all.contains("console.port=8080"));
        Assert.assertTrue(all.contains("console.bind=0.0.0.0"));
        Assert.assertTrue(all.contains("./jfa start"));
        Assert.assertFalse(all.contains("require.confirm"));
        Assert.assertFalse(all.contains("trading.hours"));
        Assert.assertFalse(all.contains("outbound.enabled"));
        JfaConfig cfg = JfaConfig.load(f);
        Assert.assertTrue(cfg.isCoverFile());
        Assert.assertEquals(7, cfg.getRetentionDays());
        Assert.assertEquals(5, cfg.getSampleIntervalSeconds());
        Assert.assertEquals(8, cfg.getSampleCount());
        Assert.assertEquals(10, cfg.getLogLookbackMinutes());
        Assert.assertEquals(20, cfg.getCompareTopN());
        Assert.assertNull(cfg.getCompareAfter());
        Assert.assertEquals(8080, cfg.getConsolePort());
        Assert.assertEquals("0.0.0.0", cfg.getConsoleBind());
        boolean firstProp = true;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            if (firstProp) {
                Assert.assertTrue("console.port must be the first property: " + line,
                        line.startsWith("console.port="));
                firstProp = false;
            }
        }
        Assert.assertFalse(firstProp);
    }

    @Test
    public void defaultsDoNotWriteUnderUserHomeEvidence() {
        JfaConfig cfg = JfaConfig.defaults();
        File homeEvidence = new File(System.getProperty("user.home"), ".jfa/evidence");
        Assert.assertFalse(homeEvidence.getAbsolutePath().equals(cfg.getReportfileRoot().getAbsolutePath()));
        Assert.assertFalse(cfg.getRegistryRoot().getAbsolutePath().contains(homeEvidence.getAbsolutePath()));
    }

    private static File propertiesFile() {
        File f = new File("conf/jfa.properties");
        if (f.isFile()) {
            return f;
        }
        return new File("../conf/jfa.properties");
    }
}
