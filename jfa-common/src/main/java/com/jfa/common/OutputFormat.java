package com.jfa.common;

public enum OutputFormat {
    TEXT,
    JSON,
    BOTH;

    public static OutputFormat fromCli(String raw) {
        if (raw == null || raw.isEmpty()) {
            return TEXT;
        }
        String v = raw.trim().toLowerCase();
        if ("text".equals(v)) {
            return TEXT;
        }
        if ("json".equals(v)) {
            return JSON;
        }
        if ("both".equals(v)) {
            return BOTH;
        }
        throw new JfaException(ErrorCode.E_USAGE, "未知 --format: " + raw + "（text|json|both）");
    }

    public boolean writeTextFile() {
        return this == TEXT || this == BOTH;
    }

    public boolean writeJsonFile() {
        return this == JSON || this == BOTH;
    }
}
