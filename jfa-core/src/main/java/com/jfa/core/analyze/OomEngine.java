package com.jfa.core.analyze;

import com.jfa.common.Confidence;
import com.jfa.common.ErrorCode;
import com.jfa.common.EvidenceLevel;
import com.jfa.common.JfaException;
import com.jfa.common.model.report.Recommendation;
import com.jfa.common.model.report.Recommendations;
import com.jfa.common.model.report.ReportSection;
import com.jfa.core.analyze.hprof.HprofParser;
import com.jfa.core.collect.EvidencePack;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OOM / heap health engine covering evidence levels E3–E0. E3 always emits actionable advice.
 */
public class OomEngine {
    private final HprofParser hprofParser = new HprofParser();
    private final GcLogAnalyzer gcLogAnalyzer = new GcLogAnalyzer();
    private final AppLogAnalyzer appLogAnalyzer = new AppLogAnalyzer();

    public MemoryAnalysis analyze(EvidencePack pack, boolean dumpRefused, String commandLine) {
        MemoryAnalysis a = new MemoryAnalysis();
        File hprof = pack.getHprof();
        File gc = pack.getGcLog();
        File app = pack.getAppLog();
        String gcText = gc == null ? null : FileSupport.readUtf8(gc);
        String appText = app == null ? null : FileSupport.readUtf8(app);
        AppLogAnalyzer.OomStack stack = appLogAnalyzer.analyze(appText);
        GcLogAnalyzer.GcTrend trend = gcLogAnalyzer.analyze(gcText);

        if (hprof != null && hprof.isFile()) {
            try {
                HprofParser.HprofSummary sum = hprofParser.parse(hprof);
                fillE3(a, sum, trend, stack, hprof);
            } catch (JfaException e) {
                if (e.getErrorCode() == ErrorCode.E_HPROF_INVALID) {
                    a.hprofInvalid = true;
                    a.hprofInvalidNote = e.getMessage();
                    fillWithoutHprof(a, trend, stack, dumpRefused, commandLine, true);
                } else {
                    throw e;
                }
            }
        } else {
            fillWithoutHprof(a, trend, stack, dumpRefused, commandLine, false);
        }
        return a;
    }

    private void fillE3(MemoryAnalysis a, HprofParser.HprofSummary sum,
                        GcLogAnalyzer.GcTrend trend, AppLogAnalyzer.OomStack stack, File hprof) {
        a.level = EvidenceLevel.E3;
        a.confidence = Confidence.HIGH;
        a.oomConfirmed = stack.found;
        a.oomSubtype = stack.found ? stack.subtype : "unknown";
        a.heapUsed = sum.approxUsedBytes;
        a.heapCapacity = Math.max(sum.approxUsedBytes, sum.fileBytes);
        a.hprofSummary = sum;
        a.gcTrend = trend.available ? trend : null;
        a.sectionStatus = "ok";
        a.oneLineFault = stack.found
                ? "Java 堆 OOM（" + a.oomSubtype + "）；hprof 对象级归因可用。"
                : "具备 hprof（E3）；未在应用日志确认 OOM 字样，按堆快照给出可行动修改建议。";

        List<Map<String, Object>> top = new ArrayList<Map<String, Object>>();
        for (HprofParser.HprofSummary.ClassStat st : sum.topClasses) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("class", st.className);
            row.put("retained_bytes", st.retainedBytes);
            row.put("instances", st.instances);
            top.add(row);
        }
        a.topClasses = top;

        String holder = sum.primaryHolder;
        HprofParser.HprofSummary.ClassStat mapStat = firstMatch(sum, "ConcurrentHashMap", "HashMap", "Cache");
        HprofParser.HprofSummary.ClassStat bytes = sum.top("byte[]");
        if (holder == null && mapStat != null) {
            holder = mapStat.className;
        }
        if (holder == null && !sum.topClasses.isEmpty()) {
            holder = sum.topClasses.get(0).className;
        }

        Map<String, Object> sus = new LinkedHashMap<String, Object>();
        sus.put("id", "SUS-O-01");
        sus.put("kind", "dominator");
        sus.put("class_name", holder);
        double ratio = mapStat != null ? mapStat.ratio : (!sum.topClasses.isEmpty() ? sum.topClasses.get(0).ratio : 0d);
        if (bytes != null && bytes.ratio > ratio) {
            ratio = bytes.ratio;
        }
        sus.put("approx_retained_ratio", round4(ratio));
        String path = retentionPath(holder, sum, bytes);
        sus.put("detail", path);
        a.suspects.add(sus);

        if (bytes != null) {
            Map<String, Object> sus2 = new LinkedHashMap<String, Object>();
            sus2.put("id", "SUS-O-02");
            sus2.put("kind", "top_class");
            sus2.put("class_name", "byte[]");
            sus2.put("approx_retained_ratio", round4(bytes.ratio));
            sus2.put("detail", "byte[] 浅堆约 " + bytes.retainedBytes + " bytes / " + bytes.instances + " 实例");
            a.suspects.add(sus2);
        }

        classifyComplexity(a, sum);
        a.recommendations = e3Recommendations(holder, sum, path);
        if (trend.available) {
            a.gcSummary = trend.summary;
        }
        a.timelineNotes.add("hprof: " + hprof.getAbsolutePath());
        if (stack.found) {
            a.timelineNotes.add("应用日志 OutOfMemoryError: " + stack.detail);
        }
    }

    private void fillWithoutHprof(MemoryAnalysis a, GcLogAnalyzer.GcTrend trend,
                                  AppLogAnalyzer.OomStack stack, boolean dumpRefused,
                                  String commandLine, boolean hprofBad) {
        a.gcTrend = trend.available ? trend : null;
        if (trend.available) {
            a.gcSummary = trend.summary;
            a.riskHints.addAll(trend.riskHints);
        }
        if (stack.found) {
            a.oomConfirmed = true;
            a.oomSubtype = stack.subtype;
            Map<String, Object> sus = new LinkedHashMap<String, Object>();
            sus.put("id", "SUS-O-E1");
            sus.put("kind", "allocation_site");
            sus.put("class_name", stack.frames.isEmpty() ? "unknown" : stack.frames.get(0));
            sus.put("detail", "应用日志确认 " + stack.detail + "；无 hprof，不能做精确对象归因。");
            a.suspects.add(sus);
        }
        if (trend.available && stack.found) {
            a.level = EvidenceLevel.E2;
            a.confidence = Confidence.MEDIUM;
            a.sectionStatus = "degraded";
            a.oneLineFault = "日志级 OOM 初诊（E2）：" + (stack.detail == null ? "heap OOM" : stack.detail)
                    + "。不能做精确对象归因。";
            a.missing.add("hprof（`jfa diagnose --pid <pid> --type memory --confirm` 或 `--hprof`）");
            a.next.add("由本产品采集或传入 hprof 后复跑 --type memory");
            a.recommendations = e2Recommendations(stack, trend);
        } else if (trend.available && !stack.found) {
            a.level = EvidenceLevel.E2;
            a.confidence = Confidence.MEDIUM;
            a.oomConfirmed = false;
            a.oomSubtype = "none";
            a.sectionStatus = "ok";
            a.oneLineFault = "未发现堆 OOM 证据；仅有 GC 趋势（不能做精确对象归因）。";
            a.recommendations = healthMemoryRecommendations(dumpRefused);
            if (trend.oldRising) {
                a.riskHints.add(trend.summary);
            }
        } else if (stack.found) {
            a.level = EvidenceLevel.E1;
            a.confidence = Confidence.LOW;
            a.sectionStatus = "degraded";
            a.oneLineFault = "栈级弱结论：确认 " + stack.detail + "，缺少 GC 日志与 hprof，不能做对象归因。";
            a.missing.add("hprof");
            a.next.add("由本产品 `--hprof` 或活体 `--confirm` 补齐堆快照");
            a.recommendations = e1Recommendations(stack);
        } else {
            a.level = EvidenceLevel.E0;
            a.confidence = dumpRefused ? Confidence.LOW : Confidence.LOW;
            a.oomConfirmed = false;
            a.oomSubtype = "none";
            a.sectionStatus = dumpRefused ? "degraded" : "ok";
            a.oneLineFault = "未发现堆 OOM 证据。";
            if (dumpRefused) {
                a.capabilityLimit = "用户未确认活体 heap dump：不能做对象级堆归因；采样与日志倒查仍由本产品执行。";
                a.missing.add("hprof（用户拒绝或未触发活体 dump）");
                a.next.add("确认后执行 jfa diagnose --pid <pid> --type memory --confirm");
            } else {
                a.missing.add("hprof");
                a.next.add("进程仍存活时 `jfa diagnose --pid <pid> --type memory --confirm`，或传入 `--hprof`");
            }
            if (commandLine != null && commandLine.contains("-Xmx")) {
                a.riskHints.add("命令行含堆参数（" + extractXmx(commandLine) + "），无 hprof 时仅作基线——非根因");
            }
            a.recommendations = healthMemoryRecommendations(dumpRefused);
        }
        if (hprofBad) {
            a.missing.add(0, "可用 hprof（当前文件损坏或不支持，已降级）");
        }
    }

    private static Recommendations e3Recommendations(String holder, HprofParser.HprofSummary sum, String path) {
        Recommendations r = new Recommendations();
        String cls = holder == null ? "嫌疑持有者类" : holder;
        Recommendation code = new Recommendation("REC-CODE-01",
                "为 " + cls + " 增加最大条目/权重上限与淘汰（或替换为有界缓存），并审查写入路径。",
                "hprof 直方图与保留路径嫌疑显示该结构/类型主导堆占用：" + path,
                "单测写入超过上限后 size 受限；预发同流量下堆占用不再单调上升至 OOM。");
        code.getRelatedSuspects().add("SUS-O-01");
        r.getCode().add(code);
        Recommendation cfg = new Recommendation("REC-CFG-01",
                "若短期无法改代码，先降低相关缓存/批大小配置或关闭无界缓存开关（按 " + shortName(cls) + " 配置项排查）。",
                "降低保留集大小，为代码修复争取空间。",
                "配置变更后观察 GC 老年代占用与 Full GC 频率，并复跑本产品 --type memory 对比直方图。");
        r.getConfig().add(cfg);
        Recommendation cap = new Recommendation("REC-CAP-01",
                "仅作为临时措施评估 -Xmx 上调；不能替代有界结构修复。",
                "无界增长下加大堆只会推迟 OOM。",
                "若上调后到达 OOM 的时间近似线性推迟，则印证泄漏仍在。");
        r.getCapacity().add(cap);
        Recommendation ops = new Recommendation("REC-OPS-01",
                "保留本次运行目录中的 hprof；若需验证是否持续增长，用本产品 `--compare-after` 或 `--hprof-prev` 做双快照对比。",
                "单次快照给出修改方向；间隔对比由本产品完成，不必把 dump 交给外部 GUI。",
                "对比报告中嫌疑类型 Δ 下降或稳定，即可验证修复。");
        r.getOps().add(ops);
        return r;
    }

    private static Recommendations e2Recommendations(AppLogAnalyzer.OomStack stack, GcLogAnalyzer.GcTrend trend) {
        Recommendations r = new Recommendations();
        r.getOps().add(new Recommendation("REC-OPS-01",
                "由本产品补第二份 hprof：活体 `jfa diagnose --pid <pid> --type memory --confirm`，或离线 `--hprof` / `--hprof-prev`。",
                "当前无对象级直方图，双快照对比与采样/日志倒查由本产品执行。",
                "取得 hprof 后报告应出现 Top 类与堆对比判读。"));
        if (trend.oldRising) {
            r.getCapacity().add(new Recommendation("REC-CAP-01",
                    "临时评估 Full GC 频率与 -Xmx，但不能当作根因修复。",
                    trend.summary,
                    "观察 Full GC 后老年代是否回落；回落差则倾向泄漏而非单纯容量。"));
        }
        return r;
    }

    private static Recommendations e1Recommendations(AppLogAnalyzer.OomStack stack) {
        Recommendations r = new Recommendations();
        r.getOps().add(new Recommendation("REC-OPS-01",
                "指定 `--hprof` 或对仍存活进程执行 `jfa diagnose --pid <pid> --type memory --confirm`。",
                "仅有 OOM 栈时本产品不能做对象级归因；日志倒查已由本产品完成。",
                "补 hprof 后报告出现 Top 类。"));
        return r;
    }

    private static Recommendations healthMemoryRecommendations(boolean dumpRefused) {
        Recommendations r = new Recommendations();
        r.getOps().add(new Recommendation("REC-OPS-01",
                dumpRefused
                        ? "需要对象级归因时：`jfa diagnose --pid <pid> --type memory --confirm`（本产品采集并分析）。"
                        : "无硬性代码修改建议。需要对象级归因时由本产品 `--confirm` 采集 hprof，或传入 `--hprof`。",
                dumpRefused ? "未取得 hprof，不能做对象级堆归因。" : "健康体检弱结论，禁止硬编根因。",
                "复跑后对照 heap_oom_evidence_found 与 Top 类是否出现。"));
        return r;
    }

    private static String retentionPath(String holder, HprofParser.HprofSummary sum,
                                        HprofParser.HprofSummary.ClassStat bytes) {
        String fields = (holder != null && sum.primaryHolderFields != null && !sum.primaryHolderFields.isEmpty())
                ? sum.primaryHolderFields.toString() : "delegate/map";
        String byteNote = bytes == null ? "" : " → byte[]";
        return "保留路径嫌疑：GC Root → " + (holder == null ? "业务持有者" : holder)
                + " " + fields + " → ConcurrentHashMap/集合" + byteNote
                + "。单次 dump 无法证明增长，但当前快照已足够确定修改方向（限界/淘汰/停止无界写入）。";
    }

    private static HprofParser.HprofSummary.ClassStat firstMatch(HprofParser.HprofSummary sum, String... keys) {
        for (String k : keys) {
            HprofParser.HprofSummary.ClassStat st = sum.top(k);
            if (st != null) {
                return st;
            }
        }
        return null;
    }

    private static void classifyComplexity(MemoryAnalysis a, HprofParser.HprofSummary sum) {
        boolean springTop = false;
        long tiny = 0;
        boolean direct = false;
        for (HprofParser.HprofSummary.ClassStat st : sum.topClasses) {
            String n = st.className == null ? "" : st.className;
            if (n.contains("springframework") || n.contains("netty") || n.contains("ClassLoader")) {
                springTop = true;
            }
            if (st.instances > 1_000_000L && st.retainedBytes / Math.max(1, st.instances) < 64) {
                tiny = st.instances;
            }
            if (n.contains("DirectByteBuffer")) {
                direct = true;
            }
        }
        if (direct && (sum.topClasses.isEmpty() || sum.topClasses.get(0).ratio < 0.15d)) {
            a.complexity = "complex";
            a.complexityId = "C4";
        } else if (tiny > 0) {
            a.complexity = "complex";
            a.complexityId = "C3";
        } else if (springTop && (sum.primaryHolder == null || sum.primaryHolder.contains("springframework"))) {
            a.complexity = "complex";
            a.complexityId = "C1";
        } else {
            a.complexity = "normal";
        }
        a.singleDumpLimitation = "单次 hprof 无法做间隔增长对比；使用本产品 `--compare-after` 或 `--hprof-prev` 即可自动对比。";
    }

    private static String extractXmx(String cmd) {
        int i = cmd.indexOf("-Xmx");
        if (i < 0) {
            return "-Xmx";
        }
        int end = i + 4;
        while (end < cmd.length() && !Character.isWhitespace(cmd.charAt(end))) {
            end++;
        }
        return cmd.substring(i, end);
    }

    private static String shortName(String cls) {
        int d = cls.lastIndexOf('.');
        return d < 0 ? cls : cls.substring(d + 1);
    }

    private static double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }

    public static class MemoryAnalysis {
        public EvidenceLevel level = EvidenceLevel.E0;
        public Confidence confidence = Confidence.NONE;
        public boolean oomConfirmed;
        public String oomSubtype = "none";
        public String sectionStatus = "ok";
        public String oneLineFault;
        public String capabilityLimit;
        public String complexity;
        public String complexityId;
        public String singleDumpLimitation;
        public boolean hprofInvalid;
        public String hprofInvalidNote;
        public long heapUsed;
        public long heapCapacity;
        public HprofParser.HprofSummary hprofSummary;
        public GcLogAnalyzer.GcTrend gcTrend;
        public String gcSummary;
        public List<Map<String, Object>> topClasses = new ArrayList<Map<String, Object>>();
        public List<Map<String, Object>> suspects = new ArrayList<Map<String, Object>>();
        public List<String> missing = new ArrayList<String>();
        public List<String> next = new ArrayList<String>();
        public List<String> riskHints = new ArrayList<String>();
        public List<String> timelineNotes = new ArrayList<String>();
        public Recommendations recommendations = new Recommendations();
    }
}
