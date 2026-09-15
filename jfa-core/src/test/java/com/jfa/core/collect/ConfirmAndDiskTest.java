package com.jfa.core.collect;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;
import com.jfa.core.disk.DiskGuard;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class ConfirmAndDiskTest {
    @Test
    public void confirmFlagSkipsPrompt() {
        ConfirmGate.assertDumpAllowed(true);
    }

    @Test
    public void interactiveYesProceeds() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ConfirmGate.assertDumpAllowed(false, new PrintStream(buf),
                new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)), null);
        String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertTrue(text.contains("STW") || text.contains("磁盘"));
    }

    @Test
    public void interactiveYesIsCaseInsensitive() {
        ConfirmGate.assertDumpAllowed(false, new PrintStream(new ByteArrayOutputStream()),
                new ByteArrayInputStream("Y\n".getBytes(StandardCharsets.UTF_8)), null);
    }

    @Test
    public void interactiveNoAborts() {
        try {
            ConfirmGate.assertDumpAllowed(false, new PrintStream(new ByteArrayOutputStream()),
                    new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)), null);
            Assert.fail("expected abort");
        } catch (JfaException e) {
            Assert.assertEquals(ErrorCode.E_CONFIRM_REQUIRED, e.getErrorCode());
            Assert.assertTrue(e.getMessage().contains("取消") || e.getMessage().contains("--confirm"));
        }
    }

    @Test
    public void nonInteractiveWithoutConfirmAbortsWithoutBlocking() {
        try {
            ConfirmGate.assertDumpAllowed(false);
            Assert.fail("expected confirm");
        } catch (JfaException e) {
            Assert.assertEquals(ErrorCode.E_CONFIRM_REQUIRED, e.getErrorCode());
            Assert.assertTrue(e.getMessage().contains("STW") || e.getMessage().contains("磁盘"));
        }
    }

    @Test
    public void diskGuardRejectsWhenThresholdHuge() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        setLong(cfg, "minFreeBytes", Long.MAX_VALUE / 4);
        try {
            new DiskGuard(cfg).assertCanWriteLarge(new File("."));
            Assert.fail("expected disk full");
        } catch (JfaException e) {
            Assert.assertEquals(ErrorCode.E_DISK_FULL, e.getErrorCode());
        }
    }

    private static void setLong(JfaConfig cfg, String field, long value) throws Exception {
        Field f = JfaConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setLong(cfg, value);
    }
}
