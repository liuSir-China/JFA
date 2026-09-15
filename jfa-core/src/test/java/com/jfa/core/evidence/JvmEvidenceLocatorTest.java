package com.jfa.core.evidence;

import com.jfa.common.model.JavaProcessInfo;
import com.jfa.core.collect.EvidencePack;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class JvmEvidenceLocatorTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void parseJdk8FlagsFromProcStyleCommandLine() {
        String cmd = "java\n-XX:+HeapDumpOnOutOfMemoryError\n-XX:HeapDumpPath=/var/app/heap/\n"
                + "-Xloggc:/var/app/gc/gc.log\n-jar\napp.jar";
        JvmEvidenceLocator.ParsedFlags p = JvmEvidenceLocator.parse(cmd);
        Assert.assertTrue(p.heapDumpOnOom);
        Assert.assertEquals("/var/app/heap/", p.heapDumpPath);
        Assert.assertEquals("/var/app/gc/gc.log", p.gcLogPath);
    }

    @Test
    public void parseSpaceSeparatedAndQuotedPaths() {
        String cmd = "java -XX:HeapDumpPath=\"/tmp/my dumps/\" -Xloggc:/tmp/gc.log -jar app.jar";
        JvmEvidenceLocator.ParsedFlags p = JvmEvidenceLocator.parse(cmd);
        Assert.assertEquals("/tmp/my dumps/", p.heapDumpPath);
        Assert.assertEquals("/tmp/gc.log", p.gcLogPath);
    }

    @Test
    public void fillMissingPrefersExistingHprofAndGc() throws Exception {
        File heapDir = tmp.newFolder("heap");
        File hprof = new File(heapDir, "java_pid4242.hprof");
        byte[] header = new byte[64];
        byte[] magic = "JAVA PROFILE 1.0.2".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 0, magic.length);
        Files.write(hprof.toPath(), header);

        File gcDir = tmp.newFolder("gc");
        File gc = new File(gcDir, "gc.log");
        Files.write(gc.toPath(), "2010-01-01T00:00:00.000+0000: 0.1: [GC 1K->1K]\n".getBytes(StandardCharsets.UTF_8));

        JavaProcessInfo proc = new JavaProcessInfo();
        proc.setPid(4242);
        proc.setCommandLine("java\n-XX:HeapDumpPath=" + heapDir.getAbsolutePath()
                + "\n-Xloggc=" + gc.getAbsolutePath() + "\n-jar\napp.jar");

        EvidencePack pack = new EvidencePack();
        JvmEvidenceLocator.fillMissing(pack, proc);
        Assert.assertEquals(hprof.getAbsoluteFile(), pack.getHprof());
        Assert.assertEquals(gc.getAbsoluteFile(), pack.getGcLog());
        Assert.assertNull(pack.getThreadDump());
    }

    @Test
    public void gcAndHprofDoNotCountAsThreadDump() throws Exception {
        File hprof = new File(tmp.getRoot(), "x.hprof");
        byte[] header = new byte[64];
        byte[] magic = "JAVA PROFILE 1.0.2".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 0, magic.length);
        Files.write(hprof.toPath(), header);
        Assert.assertTrue(JvmEvidenceLocator.looksUsableHprof(hprof));
        Assert.assertFalse(JvmEvidenceLocator.looksUsableThreadDump(hprof));
    }
}
