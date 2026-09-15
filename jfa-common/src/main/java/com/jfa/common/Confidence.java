package com.jfa.common;

public enum Confidence {
    HIGH("high", "高"),
    MEDIUM("medium", "中"),
    LOW("low", "低"),
    NONE("none", "无");

    private final String wire;
    private final String zh;

    Confidence(String wire, String zh) {
        this.wire = wire;
        this.zh = zh;
    }

    public String wireName() {
        return wire;
    }

    public String zh() {
        return zh;
    }
}
