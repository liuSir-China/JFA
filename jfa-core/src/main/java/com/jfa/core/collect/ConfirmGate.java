package com.jfa.core.collect;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ConfirmGate {
    private ConfirmGate() {
    }

    public static final String RISK_HINT =
            "STW/磁盘/内存风险：heap dump 会触发目标 JVM 停顿（STW），并占用与堆大小相当的磁盘；"
                    + "jstat 采样有额外开销。交易时段请谨慎。";

    public static void assertDumpAllowed(JfaConfig config, boolean confirm) {
        if (config.isRequireConfirm() && !confirm) {
            throw new JfaException(ErrorCode.E_CONFIRM_REQUIRED,
                    "危险操作需要 --confirm。 " + RISK_HINT);
        }
        if (inTradingHours(config) && "deny".equalsIgnoreCase(config.getTradingHoursPolicy())) {
            throw new JfaException(ErrorCode.E_TRADING_HOURS_DENIED,
                    "当前处于交易时段，策略为 deny，拒绝 heap dump / 采样。请在非交易窗执行或调整 trading.hours.policy。");
        }
        if (inTradingHours(config) && "force_confirm".equalsIgnoreCase(config.getTradingHoursPolicy())
                && !confirm) {
            throw new JfaException(ErrorCode.E_CONFIRM_REQUIRED,
                    "交易时段强制二次确认，请追加 --confirm。" + RISK_HINT);
        }
    }

    public static boolean inTradingHours(JfaConfig config) {
        String spec = config.getTradingHours();
        if (spec == null || spec.trim().isEmpty()) {
            return false;
        }
        String[] parts = spec.trim().split("-");
        if (parts.length != 2) {
            return false;
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm");
        try {
            LocalTime start = LocalTime.parse(parts[0].trim(), f);
            LocalTime end = LocalTime.parse(parts[1].trim(), f);
            LocalTime now = LocalTime.now();
            if (start.isBefore(end) || start.equals(end)) {
                return !now.isBefore(start) && !now.isAfter(end);
            }
            return !now.isBefore(start) || !now.isAfter(end);
        } catch (Exception e) {
            return false;
        }
    }
}
