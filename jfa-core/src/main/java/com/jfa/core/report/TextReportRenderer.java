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

        sb.append("## 1. 结论\n");
        appendConclusion(sb, report, health);

        ReportSection sample = report.sectionOfType("sample");
        ReportSection log = report.sectionOfType("log_lookback");
        ReportSection cmp = report.sectionOfType("heap_compare");
        ReportSection mem = report.sectionOfType("memory");

        if (sample != null) {
            sb.append("\n## 2. 内存采样表 + 判读\n");
            appendSample(sb, sample);
        }
        if (log != null) {
            sb.append("\n## 3. 日志倒查\n");
            appendLog(sb, log);
        }
        if (!health && (mem != null || cmp != null)) {
            sb.append("\n## 4. 堆结构要点 / 堆对比\n");
            appendHeap(sb, mem, cmp);
        } else if (health && cmp != null) {
            sb.append("\n## 4. 堆对比\n");
            appendCompareOnly(sb, cmp);
        }

        sb.append("\n## 5. 证据清单\n");
        appendEvidence(sb, report);

        if (health) {
            sb.append("\n## 采集时间线\n");
            appendTimeline(sb, report);
            return sb.toString();
        }

        sb.append("\n## 采集时间线\n");
        appendTimeline(sb, report);
        return sb.toString();
    }

    private static void appendConclusion(StringBuilder sb, DiagnoseReport report, boolean health) {
        if (report.getSummary() != null && report.getSummary().getHealth() != null) {
            if (report.getSummary().getHealth().getDeadlockFound() != null) {
                sb.append("- 线程侧：deadlock_found=").append(report.getSummary().getHealth().getDeadlockFound()).append('\n');
            }
            if (report.getSummary().getHealth().getHeapOomEvidenceFound() != null) {
                sb.append("- 内存侧：heap_oom_evidence_found=").append(report.getSummary().getHealth().getHeapOomEvidenceFound()).append('\n');
            }
        }
        ReportSection sample = report.sectionOfType("sample");
        if (sample != null && sample.getQualification().get("summary") != null) {
            sb.append("- 采样：").append(sample.getQualification().get("summary")).append('\n');
        }
        ReportSection log = report.sectionOfType("log_lookback");
        if (log != null && log.getNote() != null) {
            sb.append("- 日志窗口：").append(log.getNote()).append('\n');
        }
        ReportSection cmp = report.sectionOfType("heap_compare");
        if (cmp != null && cmp.getNote() != null) {
            sb.append("- 堆对比：").append(cmp.getNote()).append('\n');
        }
        if (report.getSummary() != null && report.getSummary().getFaultKinds() != null
                && !report.getSummary().getFaultKinds().isEmpty()) {
            sb.append("- 故障类型：").append(report.getSummary().getFaultKinds()).append('\n');
        }
        if (!health) {
            appendProductRecs(sb, report);
        }
    }

    private static void appendProductRecs(StringBuilder sb, DiagnoseReport report) {
        Recommendations merged = new Recommendations();
        if (report.getSections() == null) {
            return;
        }
        for (ReportSection sec : report.getSections()) {
            if (sec.getRecommendations() == null) {
                continue;
            }
            merged.getCode().addAll(sec.getRecommendations().getCode());
            merged.getConfig().addAll(sec.getRecommendations().getConfig());
            merged.getCapacity().addAll(sec.getRecommendations().getCapacity());
            merged.getOps().addAll(sec.getRecommendations().getOps());
        }
        int n = merged.getCode().size() + merged.getConfig().size()
                + merged.getCapacity().size() + merged.getOps().size();
        if (n == 0) {
            return;
        }
        sb.append("- 产品建议：\n");
        writeList(sb, merged.getCode());
        writeList(sb, merged.getConfig());
        writeList(sb, merged.getCapacity());
        writeList(sb, merged.getOps());
    }

    @SuppressWarnings("unchecked")
    private static void appendSample(StringBuilder sb, ReportSection sample) {
        if (sample.getQualification().get("judgment") != null) {
            sb.append("- 判读：").append(sample.getQualification().get("judgment")).append('\n');
        }
        if (sample.getNote() != null) {
            sb.append("- ").append(sample.getNote()).append('\n');
        }
        Object rows = sample.getQualification().get("rows");
        sb.append("| # | S0 | S1 | Eden | Old | Meta | YGC | FGC |\n");
        sb.append("|---|----|----|------|-----|------|-----|-----|\n");
        if (rows instanceof List) {
            List<?> list = (List<?>) rows;
            int i = 1;
            for (Object o : list) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> m = (Map<String, Object>) o;
                sb.append("| ").append(i++)
                        .append(" | ").append(fmt(m.get("S0")))
                        .append(" | ").append(fmt(m.get("S1")))
                        .append(" | ").append(fmt(m.get("E")))
                        .append(" | ").append(fmt(m.get("O")))
                        .append(" | ").append(fmt(m.get("M")))
                        .append(" | ").append(fmt(m.get("YGC")))
                        .append(" | ").append(fmt(m.get("FGC")))
                        .append(" |\n");
            }
        }
        Object raw = sample.getQualification().get("raw_file");
        if (raw != null) {
            sb.append("- 原始采样：").append(raw).append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendLog(StringBuilder sb, ReportSection log) {
        if (log.getNote() != null) {
            sb.append("- ").append(log.getNote()).append('\n');
        }
        Object files = log.getQualification().get("scanned_files");
        if (files instanceof List) {
            List<?> list = (List<?>) files;
            if (list.isEmpty()) {
                sb.append("- 扫描文件：（无）\n");
            } else {
                for (Object f : list) {
                    sb.append("- 扫描文件：").append(f).append('\n');
                }
            }
        }
        Object hits = log.getQualification().get("hits");
        if (hits instanceof List && !((List<?>) hits).isEmpty()) {
            for (Object o : (List<?>) hits) {
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    sb.append("- 命中：\n```\n").append(m.get("excerpt")).append("\n```\n");
                }
            }
        }
        if (log.getNextMinimalActions() != null) {
            for (String a : log.getNextMinimalActions()) {
                sb.append("- 产品补救：").append(a).append('\n');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendHeap(StringBuilder sb, ReportSection mem, ReportSection cmp) {
        if (mem != null && mem.getQualification() != null) {
            Object heap = mem.getQualification().get("heap_summary");
            if (heap instanceof Map) {
                Map<String, Object> h = (Map<String, Object>) heap;
                sb.append("- 堆占用：used=").append(h.get("used_bytes"))
                        .append(" capacity=").append(h.get("capacity_bytes")).append('\n');
                Object top = h.get("top_classes");
                if (top instanceof List) {
                    sb.append("- Top 类：\n");
                    int n = 0;
                    for (Object o : (List<?>) top) {
                        if (n++ >= 8) {
                            break;
                        }
                        sb.append("  - ").append(o).append('\n');
                    }
                }
            }
            Object limit = mem.getQualification().get("evidence_limit");
            if (limit != null) {
                sb.append("- ").append(limit).append('\n');
            }
        }
        if (cmp != null) {
            appendCompareOnly(sb, cmp);
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendCompareOnly(StringBuilder sb, ReportSection cmp) {
        if (cmp.getNote() != null) {
            sb.append("- 判读：").append(cmp.getNote()).append('\n');
        }
        Object used = cmp.getQualification().get("heap_used_bytes");
        if (used instanceof Map) {
            sb.append("- 堆 used Δ：").append(used).append('\n');
        }
        Object cap = cmp.getQualification().get("heap_capacity_bytes");
        if (cap instanceof Map) {
            sb.append("- 堆 capacity Δ：").append(cap).append('\n');
        }
        Object top = cmp.getQualification().get("top_classes");
        if (top instanceof List) {
            sb.append("- Top 类 Δ：\n");
            for (Object o : (List<?>) top) {
                sb.append("  - ").append(o).append('\n');
            }
        }
        Object sus = cmp.getQualification().get("prior_suspects");
        if (sus instanceof List && !((List<?>) sus).isEmpty()) {
            sb.append("- 此前嫌疑类型：\n");
            for (Object o : (List<?>) sus) {
                sb.append("  - ").append(o).append('\n');
            }
        }
    }

    private static void appendEvidence(StringBuilder sb, DiagnoseReport report) {
        if (report.getEvidence() == null || report.getEvidence().isEmpty()) {
            sb.append("- （无）\n");
            return;
        }
        for (EvidenceItem ev : report.getEvidence()) {
            sb.append("- [").append(ev.isUsable() ? "可用" : "不可用").append("] ")
                    .append(ev.getType()).append(": ").append(ev.getPath());
            if (ev.getNotes() != null) {
                sb.append("（").append(ev.getNotes()).append("）");
            }
            sb.append('\n');
        }
    }

    private static void appendTimeline(StringBuilder sb, DiagnoseReport report) {
        if (report.getTimeline() == null || report.getTimeline().isEmpty()) {
            sb.append("- （无）\n");
            return;
        }
        for (TimelineEvent e : report.getTimeline()) {
            sb.append("- ").append(e.getAt()).append(' ').append(e.getEvent());
            if (e.getSource() != null) {
                sb.append("（").append(e.getSource()).append("）");
            }
            sb.append('\n');
        }
    }

    private static void writeList(StringBuilder sb, List<Recommendation> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Recommendation r : list) {
            sb.append("  - [").append(r.getId()).append("] ").append(r.getWhat()).append('\n');
            if (r.getWhy() != null) {
                sb.append("    原因：").append(r.getWhy()).append('\n');
            }
            if (r.getHowToVerify() != null) {
                sb.append("    验证：").append(r.getHowToVerify()).append('\n');
            }
        }
    }

    private static String fmt(Object v) {
        if (v == null) {
            return "-";
        }
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d)) {
                return "-";
            }
            return String.format("%.1f", d);
        }
        return String.valueOf(v);
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
