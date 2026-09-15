package com.jfa.core.disk;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;

import java.io.File;

public class DiskGuard {
    private final JfaConfig config;

    public DiskGuard(JfaConfig config) {
        this.config = config;
    }

    public void assertCanWriteLarge(File targetDir) {
        File dir = targetDir;
        if (dir == null) {
            dir = new File(".");
        }
        while (dir != null && !dir.exists()) {
            dir = dir.getParentFile();
        }
        if (dir == null) {
            dir = new File(".");
        }
        long usable = dir.getUsableSpace();
        long total = dir.getTotalSpace();
        if (usable < config.getMinFreeBytes()) {
            throw new JfaException(ErrorCode.E_DISK_FULL,
                    "磁盘可用空间不足（" + usable + " < min_free_bytes=" + config.getMinFreeBytes()
                            + "），拒绝可能写入大文件的操作。请清理证据或扩容。");
        }
        if (total > 0 && config.getMinFreeRatio() > 0
                && (double) usable / (double) total < config.getMinFreeRatio()) {
            throw new JfaException(ErrorCode.E_DISK_FULL,
                    "磁盘可用比例低于阈值 " + config.getMinFreeRatio() + "，拒绝 heap dump / 大缓存写入。");
        }
    }
}
