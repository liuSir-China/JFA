package com.jfa.console;

import com.jfa.common.config.JfaConfig;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConsolePidFileTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writeReadAndAlive() throws Exception {
        JfaConfig cfg = config();
        long self = ConsolePidFile.currentPid();
        Assert.assertTrue(self > 0);
        Assert.assertTrue(ConsolePidFile.isProcessAlive(self));
        Assert.assertFalse(ConsolePidFile.isProcessAlive(99999999L));
        ConsolePidFile.write(cfg, self);
        Assert.assertEquals(Long.valueOf(self), ConsolePidFile.readPid(cfg));
        Assert.assertTrue(ConsolePidFile.isRunning(cfg));
        Assert.assertFalse(ConsolePidFile.isForeignInstanceRunning(cfg));
        ConsolePidFile.delete(cfg);
        Assert.assertFalse(ConsolePidFile.file(cfg).isFile());
    }

    @Test
    public void terminateSleepProcess() throws Exception {
        Process proc = new ProcessBuilder("sh", "-c", "echo $$; exec sleep 30").start();
        BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        String line = br.readLine();
        Assert.assertNotNull(line);
        long pid = Long.parseLong(line.trim());
        Assert.assertTrue(ConsolePidFile.isProcessAlive(pid));
        Assert.assertTrue(ConsolePidFile.terminate(pid));
        Assert.assertFalse(ConsolePidFile.isProcessAlive(pid));
    }

    private JfaConfig config() throws Exception {
        File install = tmp.newFolder("inst");
        File conf = new File(install, "conf");
        Assert.assertTrue(conf.mkdirs());
        File props = new File(conf, "jfa.properties");
        java.nio.file.Files.write(props.toPath(),
                ("console.port=8080\n# bind\nconsole.bind=127.0.0.1\n"
                        + "retention.days=7\nmin.free.bytes=1\nmin.free.ratio=0\ncover.file=true\n")
                        .getBytes(StandardCharsets.UTF_8));
        return JfaConfig.load(props);
    }
}
