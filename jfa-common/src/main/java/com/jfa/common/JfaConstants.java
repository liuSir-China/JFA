package com.jfa.common;

public final class JfaConstants {
    public static final String PRODUCT = "JFA";
    public static final String REPORT_SCHEMA_VERSION = "2.1";
    public static final String DISCLAIMER =
            "基于本地 JVM 证据的技术诊断；证据不足时不编造业务根因。数据未出域。有 hprof 时本产品独立给出可行动结论。";
    public static final String NOT_A_PREREQUISITE =
            "以下建议仅提高事后证据上限，不是使用本产品的前提";
    public static final String EVIDENCE_ENHANCE_OPTIONAL =
            "可选证据增强，不是使用本产品的前提";

    private JfaConstants() {
    }
}
