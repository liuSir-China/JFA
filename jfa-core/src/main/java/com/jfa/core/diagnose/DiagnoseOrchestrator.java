package com.jfa.core.diagnose;

import com.jfa.common.AnalysisMode;
import com.jfa.common.Confidence;
import com.jfa.common.ErrorCode;
import com.jfa.common.EvidenceLevel;
import com.jfa.common.JfaException;
import com.jfa.common.OutputFormat;
import com.jfa.common.ReportMode;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.json.JsonSupport;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.common.model.ServiceMeta;
import com.jfa.common.model.report.DiagnoseReport;
import com.jfa.common.model.report.EvidenceItem;
import com.jfa.common.model.report.Recommendation;
import com.jfa.common.model.report.ReportSection;
import com.jfa.common.model.report.TimelineEvent;
import com.jfa.common.time.TimeSupport;
import com.jfa.core.analyze.DeadlockEngine;
import com.jfa.core.analyze.OomEngine;
import com.jfa.core.collect.ConfirmGate;
import com.jfa.core.collect.EvidencePack;
import com.jfa.core.collect.JdkCollectors;
import com.jfa.core.discovery.JavaProcessDiscovery;
import com.jfa.core.evidence.JvmEvidenceLocator;
import com.jfa.core.io.FileSupport;
import com.jfa.core.registry.ServiceRegistry;
import com.jfa.core.report.TextReportRenderer;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiagnoseOrchestrator {
    private final JavaProcessDiscovery discovery = new JavaProcessDiscovery();
    private final DeadlockEngine deadlockEngine = new DeadlockEngine();
    private final OomEngine oomEngine = new OomEngine();
    private final TextReportRenderer textRenderer = new TextReportRenderer();

    public DiagnoseResult run(DiagnoseRequest req) {
        JfaConfig cfg = req.getConfig();
        ServiceRegistry registry = new ServiceRegistry(cfg);
        ResolvedTarget target = resolve(req, registry);
        DiagnoseReport report = new DiagnoseReport();
        report.setGeneratedAt(TimeSupport.nowIso());
        report.setAnalysisMode(req.getMode().wireName());
        report.getTarget().setServiceId(target.serviceId);
        report.getTarget().setPid(target.pid);
        report.getTarget().setMainClassOrJar(target.mainClass);
        report.getTarget().setHost(localHost());

        EvidencePack pack = buildPack(req, target, report);
        pack.setEvidenceDir(target.runDir);

        boolean dumpRefused = false;
        if (req.getMode().includeThread()) {
            collectThreadIfNeeded(req, target, pack, report);
        }
        if (req.getMode().includeMemory()) {
            dumpRefused = collectMemoryIfNeeded(req, target, pack, report);
        }

        DeadlockEngine.ThreadAnalysis thread = null;
        if (req.getMode().includeThread()) {
            thread = analyzeThread(req, target, pack, report);
        }
        OomEngine.MemoryAnalysis memory = null;
        if (req.getMode().includeMemory()) {
            String cmd = req.getCommandLineHint();
            if (cmd == null && target.process != null) {
                cmd = target.process.getCommandLine();
            }
            memory = oomEngine.analyze(pack, dumpRefused, cmd);
            addMemorySection(report, memory, pack);
        }

        finalizeSummary(report, req.getMode(), thread, memory);
        DiagnoseResult result = write(report, req, target);
        if (req.getMode() == AnalysisMode.THREAD && thread == null && report.sectionOfType("thread") != null
                && "failed".equals(report.sectionOfType("thread").getStatus())) {
            result.setExitCode(ErrorCode.E_NO_THREAD_DUMP.exitCode());
        }
        return result;
    }

    private EvidencePack buildPack(DiagnoseRequest req, ResolvedTarget target, DiagnoseReport report) {
        EvidencePack pack = new EvidencePack();
        pack.setMeta(target.meta);
        pack.setHprof(existingFile(req.getHprof()));
        pack.setGcLog(existingFile(req.getGcLog()));
        pack.setAppLog(existingFile(req.getAppLog()));
        pack.setThreadDump(existingFile(req.getThreadDump()));

        if (target.process != null) {
            File hBefore = pack.getHprof();
            File gBefore = pack.getGcLog();
            JvmEvidenceLocator.fillMissing(pack, target.process);
            if (pack.getHprof() != null && pack.getHprof() != hBefore) {
                report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                        "复用目标 JVM HeapDumpPath 已有 hprof（不采集新 dump）",
                        pack.getHprof().getAbsolutePath()));
            }
            if (pack.getGcLog() != null && pack.getGcLog() != gBefore) {
                report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                        "复用目标 JVM GC 日志（-Xloggc / 相关标志）",
                        pack.getGcLog().getAbsolutePath()));
            }
        }

        EvidencePack fromDir = EvidencePack.index(target.evidenceDir, target.meta, null, null, null, null);
        if (pack.getHprof() == null) {
            pack.setHprof(fromDir.getHprof());
        }
        if (pack.getGcLog() == null) {
            pack.setGcLog(fromDir.getGcLog());
        }
        if (pack.getAppLog() == null) {
            pack.setAppLog(fromDir.getAppLog());
        }
        if (pack.getThreadDump() == null) {
            pack.setThreadDump(fromDir.getThreadDump());
        }
        if (pack.getJstatSample() == null) {
            pack.setJstatSample(fromDir.getJstatSample());
        }
        return pack;
    }

    private static File existingFile(File f) {
        return f != null && f.isFile() ? f.getAbsoluteFile() : null;
    }

    private void collectThreadIfNeeded(DiagnoseRequest req, ResolvedTarget target, EvidencePack pack,
                                       DiagnoseReport report) {
        if (JvmEvidenceLocator.looksUsableThreadDump(pack.getThreadDump())) {
            return;
        }
        pack.setThreadDump(null);
        if (!req.isLiveCollect() || target.process == null || !discovery.pidExists(target.process.getPid())) {
            return;
        }
        JdkCollectors col = new JdkCollectors(req.getConfig());
        File td = col.collectThreadDump(target.process, target.runDir);
        pack.setThreadDump(td);
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(), "采集 thread dump", td.getAbsolutePath()));
    }

    private boolean collectMemoryIfNeeded(DiagnoseRequest req, ResolvedTarget target, EvidencePack pack,
                                          DiagnoseReport report) {
        if (JvmEvidenceLocator.looksUsableHprof(pack.getHprof())) {
            return false;
        }
        pack.setHprof(null);
        if (!req.isLiveCollect() || target.process == null) {
            return false;
        }
        ConfirmGate.assertDumpAllowed(req.isConfirm());
        JdkCollectors col = new JdkCollectors(req.getConfig());
        File hprof = col.collectHeapDump(target.process, target.runDir, true);
        pack.setHprof(hprof);
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(), "经确认采集 heap dump", hprof.getAbsolutePath()));
        return false;
    }

    private DeadlockEngine.ThreadAnalysis analyzeThread(DiagnoseRequest req, ResolvedTarget target,
                                                        EvidencePack pack, DiagnoseReport report) {
        ReportSection sec = new ReportSection();
        sec.setType("thread");
        boolean live = target.process != null && discovery.pidExists(target.process.getPid());
        if (pack.getThreadDump() == null) {
            if (!live) {
                sec.setStatus("failed");
                sec.setConfidence(Confidence.NONE.wireName());
                sec.getQualification().put("deadlock_found", false);
                sec.getMissingEvidence().add("历史 thread dump（进程已死且无落盘 dump，无法还原死锁现场）");
                sec.getNextMinimalActions().add(
                        "下次在进程假死/OOM 脚本中调用 jstack 或 jcmd <pid> Thread.print 写入 threads/ 目录后复跑 --type thread");
                sec.getNextMinimalActions().add("可选：jfa help config 中 OnOutOfMemoryError 示例含 jstack（非诊断前提）");
                sec.getRecommendations().getOps().add(new Recommendation("REC-OPS-01",
                        "在进程假死告警脚本中增加 jstack/jcmd Thread.print 落盘。",
                        "进程一旦被杀将无法还原死锁现场。",
                        "演练杀进程前确认 threads/ 目录有新文件。"));
                report.getSections().add(sec);
                report.getEvidence().add(new EvidenceItem("EV-TD-MISS", "thread_dump", "", false, "缺失"));
                return null;
            }
            sec.setStatus("failed");
            sec.setNote("未能采集 thread dump");
            report.getSections().add(sec);
            return null;
        }
        String text = FileSupport.readUtf8(pack.getThreadDump());
        DeadlockEngine.ThreadAnalysis ta = deadlockEngine.analyze(text);
        report.getEvidence().add(new EvidenceItem("EV-TD", "thread_dump",
                pack.getThreadDump().getAbsolutePath(), true,
                ta.isDeadlockFound() ? "死锁检出" : "未发现死锁"));
        sec.setStatus("ok");
        sec.setConfidence(ta.isDeadlockFound() ? Confidence.HIGH.wireName() : Confidence.HIGH.wireName());
        sec.getQualification().put("deadlock_found", ta.isDeadlockFound());
        sec.getQualification().put("deadlock_count", ta.getDeadlockCount());
        if (ta.getJvmDeadlockDescription() != null) {
            sec.getQualification().put("jvm_deadlock_description", ta.getJvmDeadlockDescription());
        }
        sec.setSuspects(ta.getSuspects());
        if (ta.isDeadlockFound()) {
            addDeadlockRecs(sec, ta);
        } else {
            if (ta.getBlockedCount() > 0) {
                Map<String, Object> hint = new LinkedHashMap<String, Object>();
                hint.put("id", "SUS-T-RISK");
                hint.put("kind", "risk_hint");
                hint.put("detail", "高阻塞线程 Top：" + ta.getHotBlocked() + " ——风险提示，非故障定性");
                sec.getSuspects().add(hint);
            }
            sec.getRecommendations().getOps().add(new Recommendation("REC-OPS-T1",
                    "无死锁时无需硬性改锁顺序；可将本次 dump 作为基线保留。",
                    "未发现 JVM 报告的 Java 级死锁。",
                    "若再现卡顿，立即 jfa collect threaddump 后复跑 --type thread。"));
        }
        report.getSections().add(sec);
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                ta.isDeadlockFound() ? "JVM 报告 Java-level deadlock" : "thread dump 分析完成（未发现死锁）",
                "thread"));
        return ta;
    }

    private void addDeadlockRecs(ReportSection sec, DeadlockEngine.ThreadAnalysis ta) {
        String frames = ta.getBusinessFrames() == null || ta.getBusinessFrames().isEmpty()
                ? "死锁相关业务栈帧" : ta.getBusinessFrames().get(0);
        Recommendation c1 = new Recommendation("REC-CODE-01",
                "统一加锁顺序：对交叉加锁的业务方法（如 " + frames + "）按稳定键（如 accountId）排序后加锁。",
                "交叉加锁是死锁环的直接原因。",
                "用对向并发压测 10min 不再出现 jstack 死锁；单测覆盖对向加锁。");
        c1.getRelatedSuspects().add("SUS-D-01");
        sec.getRecommendations().getCode().add(c1);
        sec.getRecommendations().getCode().add(new Recommendation("REC-CODE-02",
                "对锁使用 tryLock(timeout) 并失败降级/重试；缩小 synchronized 临界区。",
                "缩短无限等待窗口，降低第三方库或嵌套锁放大死锁的概率。",
                "注入延迟后断言超时分支被触发且无线程永久 BLOCKED。"));
        sec.getRecommendations().getOps().add(new Recommendation("REC-OPS-01",
                "在进程假死告警脚本中增加 jstack/jcmd Thread.print 落盘。",
                "进程一旦被杀将无法还原死锁现场。",
                "演练杀进程前确认 threads/ 目录有新文件。"));
    }

    private void addMemorySection(DiagnoseReport report, OomEngine.MemoryAnalysis memory, EvidencePack pack) {
        ReportSection sec = new ReportSection();
        sec.setType("memory");
        sec.setStatus(memory.sectionStatus);
        sec.setConfidence(memory.confidence.wireName());
        sec.getQualification().put("oom_confirmed", memory.oomConfirmed);
        sec.getQualification().put("oom_confirmed_note", memory.oomConfirmed ? "应用日志或证据确认 OOM" : "健康体检无 OOM 证据时为 false");
        sec.getQualification().put("oom_subtype", memory.oomSubtype);
        sec.getQualification().put("evidence_level", memory.level.wireName());
        Map<String, Object> heap = new LinkedHashMap<String, Object>();
        heap.put("used_bytes", memory.heapUsed);
        heap.put("capacity_bytes", memory.heapCapacity);
        heap.put("top_classes", memory.topClasses);
        sec.getQualification().put("heap_summary", heap);
        Map<String, Object> gc = new LinkedHashMap<String, Object>();
        gc.put("available", memory.gcTrend != null && memory.gcTrend.available);
        gc.put("summary", memory.gcSummary);
        sec.getQualification().put("gc_trend", gc);
        if (memory.complexity != null) {
            sec.getQualification().put("complexity", memory.complexity);
            sec.getQualification().put("complexity_id", memory.complexityId);
        }
        if (memory.singleDumpLimitation != null && memory.level == EvidenceLevel.E3) {
            sec.getQualification().put("evidence_limit", memory.singleDumpLimitation);
        }
        sec.setSuspects(memory.suspects);
        sec.setRecommendations(memory.recommendations);
        sec.getMissingEvidence().addAll(memory.missing);
        sec.getNextMinimalActions().addAll(memory.next);
        if (memory.capabilityLimit != null) {
            sec.setNote(memory.capabilityLimit);
        }
        if (memory.hprofInvalid) {
            sec.setNote(memory.hprofInvalidNote);
        }
        report.getSections().add(sec);
        if (pack.getHprof() != null) {
            report.getEvidence().add(new EvidenceItem("EV-HPROF", "hprof",
                    pack.getHprof().getAbsolutePath(), !memory.hprofInvalid,
                    memory.hprofInvalid ? "无效已降级" : "E3"));
        }
        if (pack.getGcLog() != null) {
            report.getEvidence().add(new EvidenceItem("EV-GC", "gc_log",
                    pack.getGcLog().getAbsolutePath(), true, "趋势"));
        }
        if (pack.getAppLog() != null) {
            report.getEvidence().add(new EvidenceItem("EV-APP", "app_log",
                    pack.getAppLog().getAbsolutePath(), true, "OOM 栈扫描"));
        }
        for (String n : memory.timelineNotes) {
            report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(), n, "memory"));
        }
        if (memory.gcTrend != null && memory.gcTrend.available) {
            report.getTimeline().add(new TimelineEvent("unknown", memory.gcTrend.summary, "gc_log"));
        }
    }

    private void finalizeSummary(DiagnoseReport report, AnalysisMode mode,
                                 DeadlockEngine.ThreadAnalysis thread,
                                 OomEngine.MemoryAnalysis memory) {
        boolean deadlock = thread != null && thread.isDeadlockFound();
        boolean oom = memory != null && memory.oomConfirmed;
        boolean e3 = memory != null && memory.level == EvidenceLevel.E3;
        List<String> kinds = new ArrayList<String>();
        if (deadlock) {
            kinds.add("deadlock");
        }
        if (oom) {
            kinds.add("oom_heap");
        }
        report.getSummary().setFaultKinds(kinds);
        report.getSummary().setFabricatedRootCause(false);
        if (mode.includeThread() && thread != null) {
            report.getSummary().getHealth().setDeadlockFound(deadlock);
            report.getSummary().getHealth().getRiskHints().addAll(thread.getRiskHints());
        }
        if (mode.includeMemory() && memory != null) {
            report.getSummary().getHealth().setHeapOomEvidenceFound(oom || e3 && memory.oomConfirmed);
            report.getSummary().getHealth().getRiskHints().addAll(memory.riskHints);
        }
        boolean health = !deadlock && !oom;
        report.setReportMode(health ? ReportMode.HEALTH_CHECK.wireName() : ReportMode.FAULT.wireName());
        if (health) {
            report.getSummary().setFaultKindsNote("健康体检且无故障时可为 []，并由 one_line / health 字段表达否定结论");
            StringBuilder one = new StringBuilder();
            if (mode.includeThread()) {
                one.append("未发现死锁");
            }
            if (mode.includeMemory()) {
                if (one.length() > 0) {
                    one.append("；");
                }
                one.append("未发现堆 OOM 证据");
            }
            if (memory != null && memory.level == EvidenceLevel.E3 && !oom) {
                report.setReportMode(ReportMode.FAULT.wireName());
                report.getSummary().setOneLine(memory.oneLineFault);
                report.getSummary().setOverallConfidence(Confidence.HIGH.wireName());
            } else {
                report.getSummary().setOneLine(one.toString() + "。");
                report.getSummary().setOverallConfidence(
                        memory != null && memory.level == EvidenceLevel.E2
                                ? Confidence.MEDIUM.wireName()
                                : Confidence.MEDIUM.wireName());
                if (memory != null && memory.level == EvidenceLevel.E0) {
                    report.getSummary().setOverallConfidence(Confidence.LOW.wireName());
                }
                if (thread != null && thread.isDeadlockFound()) {
                    report.getSummary().setOverallConfidence(Confidence.HIGH.wireName());
                }
            }
        } else {
            StringBuilder one = new StringBuilder();
            if (deadlock) {
                one.append("确认线程死锁（").append(thread.getDeadlockCount()).append(" 组）");
            }
            if (oom || e3) {
                if (one.length() > 0) {
                    one.append("；");
                }
                one.append(memory.oneLineFault);
            }
            report.getSummary().setOneLine(one.toString());
            report.getSummary().setOverallConfidence(
                    deadlock || e3 ? Confidence.HIGH.wireName() : Confidence.MEDIUM.wireName());
        }
        if (memory != null && memory.level == EvidenceLevel.E1) {
            report.getSummary().setOverallConfidence(Confidence.LOW.wireName());
        }
        if (memory != null && memory.level == EvidenceLevel.E3) {
            report.getSummary().getHealth().setHeapOomEvidenceFound(true);
        }
    }

    private DiagnoseResult write(DiagnoseReport report, DiagnoseRequest req, ResolvedTarget target) {
        File outDir = target.runDir;
        if (outDir == null) {
            outDir = req.getOutDir() != null ? req.getOutDir() : new File(target.evidenceDir, "reports");
        }
        FileSupport.mkdirs(outDir);
        String stamp = TimeSupport.nowFileStamp();
        String text = textRenderer.render(report);
        String json;
        try {
            json = JsonSupport.mapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(jsonView(report));
        } catch (Exception e) {
            throw new JfaException(ErrorCode.E_IO_REPORT, "序列化 JSON 报告失败", e);
        }
        if (json.toLowerCase().contains(" mat") || json.contains("建议用 MAT") || json.contains("Eclipse MAT")) {
            throw new JfaException(ErrorCode.E_INTERNAL, "内部错误：报告禁止出现外部 hprof GUI 下一步");
        }
        DiagnoseResult result = new DiagnoseResult();
        result.setReport(report);
        result.setText(text);
        result.setJson(json);
        OutputFormat fmt = req.getFormat() == null ? OutputFormat.BOTH : req.getFormat();
        try {
            if (fmt.writeTextFile() || fmt == OutputFormat.BOTH) {
                File tf = new File(outDir, "diagnose-" + stamp + ".md");
                FileSupport.writeUtf8(tf, text);
                result.setTextFile(tf);
            }
            if (fmt.writeJsonFile() || fmt == OutputFormat.BOTH || fmt == OutputFormat.JSON) {
                File jf = new File(outDir, "diagnose-" + stamp + ".json");
                FileSupport.writeUtf8(jf, json);
                result.setJsonFile(jf);
            }
            if (fmt == OutputFormat.TEXT && result.getTextFile() == null) {
                File tf = new File(outDir, "diagnose-" + stamp + ".md");
                FileSupport.writeUtf8(tf, text);
                result.setTextFile(tf);
            }
        } catch (JfaException e) {
            if (e.getErrorCode() == ErrorCode.E_IO_EVIDENCE) {
                throw new JfaException(ErrorCode.E_IO_REPORT, e.getMessage(), e);
            }
            throw e;
        }
        return result;
    }

    /**
     * Healthy reports serialize only conclusion/summary + collection timeline.
     * Fault reports keep full chapters. Disclaimer is never included.
     */
    private static DiagnoseReport jsonView(DiagnoseReport report) {
        if (!ReportMode.HEALTH_CHECK.wireName().equals(report.getReportMode())) {
            return report;
        }
        DiagnoseReport slim = new DiagnoseReport();
        slim.setReportSchemaVersion(report.getReportSchemaVersion());
        slim.setProduct(report.getProduct());
        slim.setGeneratedAt(report.getGeneratedAt());
        slim.setAnalysisMode(report.getAnalysisMode());
        slim.setReportMode(report.getReportMode());
        slim.setTarget(report.getTarget());
        slim.setSummary(report.getSummary());
        slim.setTimeline(report.getTimeline());
        slim.setEvidence(null);
        slim.setSections(null);
        return slim;
    }

    private ResolvedTarget resolve(DiagnoseRequest req, ServiceRegistry registry) {
        int n = 0;
        if (req.getPid() != null) {
            n++;
        }
        if (req.getService() != null) {
            n++;
        }
        if (req.getEvidenceDir() != null) {
            n++;
        }
        if (n == 0 && req.getHprof() == null && req.getThreadDump() == null && req.getGcLog() == null) {
            throw new JfaException(ErrorCode.E_USAGE,
                    "需要 --pid 或 --service 或 --evidence-dir（或显式 --hprof/--thread-dump/--gc-log）");
        }
        ResolvedTarget t = new ResolvedTarget();
        if (req.getService() != null) {
            ServiceMeta meta = registry.require(req.getService());
            t.meta = meta;
            t.serviceId = meta.getServiceId();
            t.evidenceDir = registry.evidenceDirOf(meta);
            t.mainClass = meta.getMatch() == null ? null : meta.getMatch().getMainClassContains();
            Long pid = req.getPid();
            if (pid == null && meta.getMatch() != null) {
                pid = meta.getMatch().getLastPid();
            }
            if (pid != null && discovery.pidExists(pid)) {
                t.pid = pid;
                t.process = discovery.requirePid(pid);
                t.mainClass = t.process.getMainClassOrJar();
            } else if (req.getPid() != null) {
                t.process = discovery.requirePid(req.getPid());
                t.pid = req.getPid();
            }
        } else if (req.getPid() != null) {
            t.process = discovery.requirePid(req.getPid());
            t.pid = req.getPid();
            t.mainClass = t.process.getMainClassOrJar();
            t.serviceId = "pid-" + req.getPid();
            t.evidenceDir = req.getEvidenceDir() != null ? req.getEvidenceDir()
                    : req.getConfig().serviceDir(t.serviceId);
        } else if (req.getEvidenceDir() != null) {
            t.evidenceDir = req.getEvidenceDir();
            File meta = new File(t.evidenceDir, "meta.json");
            if (meta.isFile()) {
                t.meta = registry.readFile(meta);
                t.serviceId = t.meta.getServiceId();
                t.mainClass = t.meta.getMatch() == null ? null : t.meta.getMatch().getMainClassContains();
                if (t.pid == null && t.meta.getMatch() != null) {
                    t.pid = t.meta.getMatch().getLastPid();
                }
            } else {
                t.serviceId = t.evidenceDir.getName();
            }
        } else {
            t.serviceId = "adhoc";
            t.evidenceDir = req.getOutDir() != null ? req.getOutDir() : req.getConfig().serviceDir("adhoc");
        }
        if (req.getEvidenceDir() != null) {
            t.evidenceDir = req.getEvidenceDir();
        }
        if (t.pid == null) {
            t.pid = RunLayout.parsePid(t.evidenceDir, req.getHprof(), req.getThreadDump());
        }
        t.runDir = RunLayout.prepareRunDir(req.getConfig(),
                RunLayout.pidKey(t.pid, t.evidenceDir, req.getHprof(), req.getThreadDump()),
                req.getOutDir());
        return t;
    }

    private static String localHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    static class ResolvedTarget {
        Long pid;
        String serviceId;
        String mainClass;
        File evidenceDir;
        File runDir;
        ServiceMeta meta;
        JavaProcessInfo process;
    }
}
