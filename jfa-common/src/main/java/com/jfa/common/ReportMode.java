package com.jfa.common;

public enum ReportMode {
    FAULT,
    HEALTH_CHECK;

    public String wireName() {
        return this == HEALTH_CHECK ? "health_check" : "fault";
    }
}
