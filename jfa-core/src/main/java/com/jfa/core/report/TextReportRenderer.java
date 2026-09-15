package com.jfa.core.report;

import com.jfa.common.ReportMode;
import com.jfa.common.model.report.DiagnoseReport;
import com.jfa.common.model.report.EvidenceItem;
import com.jfa.common.model.report.Recommendation;
import com.jfa.common.model.report.Recommendations;
import com.jfa.common.model.report.ReportSection;
import com.jfa.common.model.report.TargetInfo;
import com.jfa.common.model.report.TimelineEvent;

import java.util.List;
import java.util.Map;

public class TextReportRenderer {

    public String render(DiagnoseReport report) {
        StringBuilder sb = new StringBuilder();
        boolean health = ReportMode.HEALTH_CHECK.wireName().equals(report.getReportMode());
        if (health) {
            sb.append("======== JFA 诊断报告（健康体检）========\n");
        } else {
            sb.append("======== JFA 诊断报告 ========\n");
        }
        sb.append("目标：").append(targetLine(report.getTarget())).append('\n');
        sb.append("生成时间：").append(nullToEmpty(report.getGeneratedAt())).append('\n');
        sb.append("分析模式：").append(nullToEmpty(report.getAnalysisMode())).append('\n');
        sb.append("报告模式：").append(health ? "健康体检" : "故障诊断").append('\n');
        sb.append("一句话结论：").append(report.getSummary() == null ? "" : nullToEmpty(report.getSummary().getOneLine())).append('\n');
        sb.append("总体置信度：").append(zhConf(report.getSummary() == null ? null : report.getSummary().getOverallConfidence())).append('\n');
        sb.append('\n');

        if (health) {
            sb.append("## 1. 结论（否定故障）\n");
        } else {
            sb.append("## 1. 故障定性\n");
        }
        if (report.getSummary() != null && report.getSummary().getHealth() != null) {
            if (report.getSummary().getHealth().getDeadlockFound() != null) {
                sb.append("- 线程侧：deadlock_found=").append(report.getSummary().getHealth().getDeadlockFound()).append('\n');
            }
            if (report.getSummary().getHealth().getHeapOomEvidenceFound() != null) {
                sb.append("- 内存侧：heap_oom_evidence_found=").append(report.getSummary().getHealth().getHeapOomEvidenceFound()).append('\n');
            }
        }
        if (report.getSummary() != null && report.getSummary().getFaultKinds() != null
                && !report.getSummary().getFaultKinds().isEmpty()) {
            sb.append("- 故障类型：").append(report.getSummary().getFaultKinds()).append('\n');
        }
        if (!health) {
            appendSectionQual(sb, report);
        }

        if (health) {
            sb.append("\n## 2. 采集时间线\n");
        } else {
            sb.append("\n## 2. 时间线摘要\n");
        }
        if (report.getTimeline() == null || report.getTimeline().isEmpty()) {
            sb.append("- （无）\n");
        } else {
            for (TimelineEvent e : report.getTimeline()) {
                sb.append("- ").append(e.getAt()).append(' ').append(e.getEvent());
                if (e.getSource() != null) {
                    sb.append("（").append(e.getSource()).append("）");
                }
                sb.append('\n');
            }
        }

        if (health) {
            return sb.toString();
        }

        sb.append("\n## 3. 证据清单与置信度\n");
        if (report.getEvidence() == null || report.getEvidence().isEmpty()) {
            sb.append("- （无有效证据文件）\n");
        } else {
            for (EvidenceItem ev : report.getEvidence()) {
                sb.append("- [").append(ev.isUsable() ? "可用" : "不可用").append("] ")
                        .append(ev.getType()).append(": ").append(ev.getPath());
                if (ev.getNotes() != null) {
                    sb.append("（").append(ev.getNotes()).append("）");
                }
                sb.append('\n');
            }
        }

        sb.append("\n## 4. 嫌疑点\n");
        boolean any = false;
        if (report.getSections() != null) {
            for (ReportSection sec : report.getSections()) {
                if (sec.getSuspects() == null) {
                    continue;
                }
                for (Map<String, Object> s : sec.getSuspects()) {
                    any = true;
                    sb.append("- ").append(s).append('\n');
                }
            }
        }
        if (!any) {
            sb.append("- 无\n");
        }

        sb.append("\n## 5. 修改建议\n");
        dumpRecs(sb, report);
        sb.append("\n## 6. 缺失证据与下一步最小操作（若有）\n");
        boolean miss = false;
        if (report.getSections() != null) {
            for (ReportSection sec : report.getSections()) {
                if (sec.getMissingEvidence() != null) {
                    for (String m : sec.getMissingEvidence()) {
                        miss = true;
                        sb.append("- 缺失：").append(m).append('\n');
                    }
                }
                if (sec.getNextMinimalActions() != null) {
                    for (String n : sec.getNextMinimalActions()) {
                        miss = true;
                        sb.append("- 下一步：").append(n).append('\n');
                    }
                }
            }
        }
        if (!miss) {
            sb.append("- 无\n");
        }
        return sb.toString();
    }

    private static void appendSectionQual(StringBuilder sb, DiagnoseReport report) {
        if (report.getSections() == null) {
            return;
        }
        for (ReportSection sec : report.getSections()) {
            sb.append("- section ").append(sec.getType()).append(" status=").append(sec.getStatus());
            if (sec.getConfidence() != null) {
                sb.append(" confidence=").append(sec.getConfidence());
            }
            sb.append('\n');
            if (sec.getQualification() != null && !sec.getQualification().isEmpty()) {
                Object level = sec.getQualification().get("evidence_level");
                if (level != null) {
                    sb.append("  - 证据级别：").append(level).append('\n');
                }
                Object dead = sec.getQualification().get("deadlock_found");
                if (Boolean.TRUE.equals(dead)) {
                    sb.append("  - 死锁组数：").append(sec.getQualification().get("deadlock_count")).append('\n');
                }
                Object oom = sec.getQualification().get("oom_subtype");
                if (oom != null) {
                    sb.append("  - OOM 子类型：").append(oom).append('\n');
                }
            }
        }
    }

    private static void dumpRecs(StringBuilder sb, DiagnoseReport report) {
        Recommendations merged = new Recommendations();
        if (report.getSections() != null) {
            for (ReportSection sec : report.getSections()) {
                if (sec.getRecommendations() == null) {
                    continue;
                }
                merged.getCode().addAll(sec.getRecommendations().getCode());
                merged.getConfig().addAll(sec.getRecommendations().getConfig());
                merged.getCapacity().addAll(sec.getRecommendations().getCapacity());
                merged.getOps().addAll(sec.getRecommendations().getOps());
            }
        }
        sb.append("### 5.1 代码\n");
        writeList(sb, merged.getCode());
        sb.append("### 5.2 配置\n");
        writeList(sb, merged.getConfig());
        sb.append("### 5.3 容量\n");
        writeList(sb, merged.getCapacity());
        sb.append("### 5.4 运维\n");
        writeList(sb, merged.getOps());
    }

    private static void writeList(StringBuilder sb, List<Recommendation> list) {
        if (list == null || list.isEmpty()) {
            sb.append("- （无强制项）\n");
            return;
        }
        for (Recommendation r : list) {
            sb.append("- [").append(r.getId()).append("] 改什么：").append(r.getWhat()).append('\n');
            sb.append("  为什么：").append(r.getWhy()).append('\n');
            sb.append("  验证方式：").append(r.getHowToVerify()).append('\n');
        }
    }

    private static String targetLine(TargetInfo t) {
        if (t == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        if (t.getPid() != null) {
            sb.append("pid=").append(t.getPid());
        }
        if (t.getMainClassOrJar() != null) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append("main=").append(t.getMainClassOrJar());
        }
        if (t.getServiceId() != null) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append("service=").append(t.getServiceId());
        }
        if (t.getHost() != null) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append("host=").append(t.getHost());
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }

    private static String zhConf(String wire) {
        if ("high".equals(wire)) {
            return "高";
        }
        if ("medium".equals(wire)) {
            return "中";
        }
        if ("low".equals(wire)) {
            return "低";
        }
        if ("none".equals(wire)) {
            return "无";
        }
        return wire == null ? "无" : wire;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
