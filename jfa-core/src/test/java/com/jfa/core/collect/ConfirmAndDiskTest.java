package com.jfa.core.collect;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;
import com.jfa.core.disk.DiskGuard;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;

public class ConfirmAndDiskTest {
    @Test
    public void confirmRequired() {
        JfaConfig cfg = JfaConfig.defaults();
        try {
            ConfirmGate.assertDumpAllowed(cfg, false);
            Assert.fail("expected confirm");
        } catch (JfaException e) {
            Assert.assertEquals(ErrorCode.E_CONFIRM_REQUIRED, e.getErrorCode());
            Assert.assertTrue(e.getMessage().contains("STW") || e.getMessage().contains("磁盘"));
        }
        ConfirmGate.assertDumpAllowed(cfg, true);
    }

    @Test
    public void tradingHoursDeny() throws Exception {
        JfaConfig cfg = JfaConfig.defaults();
        set(cfg, "tradingHoursPolicy", "deny");
        set(cfg, "tradingHours", "00:00-23:59");
        try {
            ConfirmGate.assertDumpAllowed(cfg, true);
            Assert.fail("expected deny");
        } catch (JfaException e) {
            Assert.assertEquals(ErrorCode.E_TRADING_HOURS_DENIED, e.getErrorCode());
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

    private static void set(JfaConfig cfg, String field, Object value) throws Exception {
        Field f = JfaConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(cfg, value);
    }

    private static void setLong(JfaConfig cfg, String field, long value) throws Exception {
        Field f = JfaConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setLong(cfg, value);
    }
}
