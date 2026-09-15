package com.jfa.common;

/**
 * PD-I analysis modes. Public names are only memory | thread | auto.
 */
public enum AnalysisMode {
    MEMORY,
    THREAD,
    AUTO;

    public boolean includeMemory() {
        return this == MEMORY || this == AUTO;
    }

    public boolean includeThread() {
        return this == THREAD || this == AUTO;
    }

    public String wireName() {
        return name().toLowerCase();
    }

    public static AnalysisMode fromCli(String raw) {
        if (raw == null || raw.isEmpty()) {
            return AUTO;
        }
        String v = raw.trim().toLowerCase();
        if ("memory".equals(v)) {
            return MEMORY;
        }
        if ("thread".equals(v)) {
            return THREAD;
        }
        if ("auto".equals(v)) {
            return AUTO;
        }
        if ("oom".equals(v) || "heap".equals(v)) {
            return MEMORY;
        }
        if ("deadlock".equals(v)) {
            return THREAD;
        }
        if ("both".equals(v)) {
            return AUTO;
        }
        throw new JfaException(ErrorCode.E_USAGE,
                "未知 --type: " + raw + "（仅支持 memory | thread | auto；省略则全量）");
    }
}
