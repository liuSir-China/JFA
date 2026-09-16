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
import com.jfa.core.analyze.AppLogLookback;
import com.jfa.core.analyze.DeadlockEngine;
import com.jfa.core.analyze.JstatGcutilAnalyzer;
import com.jfa.core.analyze.OomEngine;
import com.jfa.core.analyze.hprof.HprofComparer;
import com.jfa.core.analyze.hprof.HprofParser;
import com.jfa.core.collect.ConfirmGate;
import com.jfa.core.collect.EvidencePack;
import com.jfa.core.collect.JdkCollectors;
import com.jfa.core.discovery.JavaProcessDiscovery;
import com.jfa.core.evidence.JvmEvidenceLocator;
import com.jfa.core.evidence.LogPathResolver;
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
    private final JstatGcutilAnalyzer gcutilAnalyzer = new JstatGcutilAnalyzer();
    private final AppLogLookback logLookback = new AppLogLookback();
    private final HprofParser hprofParser = new HprofParser();
    private final HprofComparer hprofComparer = new HprofComparer();
    private ConsoleProgress progress = ConsoleProgress.DISABLED;

    public DiagnoseResult run(DiagnoseRequest req) {
        progress = ConsoleProgress.from(req);
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
        ingestIntoRunDir(pack, target.runDir, report, req);

        boolean dumpRefused = false;
        if (req.getMode().includeThread()) {
            collectThreadIfNeeded(req, target, pack, report);
        }
        if (req.getMode().includeMemory()) {
            dumpRefused = collectDump1IfNeeded(req, target, pack, report);
        }

        JstatGcutilAnalyzer.SampleTrend sample = null;
        if (req.getMode().includeMemory()) {
            sample = runSampling(req, target, pack, report);
        }
        AppLogLookback.Result lookback = runLogLookback(req, target, pack, report);

        if (req.getMode().includeMemory()) {
            collectDump2IfNeeded(req, target, pack, report);
        }

        HprofComparer.CompareResult compare = runCompare(req, pack, report);

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
            progress.step("分析内存证据 …");
            memory = oomEngine.analyze(pack, dumpRefused, cmd);
            addMemorySection(report, memory, pack);
        }
        if (sample != null && sample.available) {
            addSampleSection(report, sample, pack);
        }
        if (lookback != null) {
            addLogSection(report, lookback);
        }
        if (compare != null) {
            addCompareSection(report, compare, pack);
        }

        finalizeSummary(report, req.getMode(), thread, memory, sample, lookback, compare);
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
        pack.setHprofPrev(existingFile(req.getHprofPrev()));
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
                progress.detail("定位到目标 JVM 已有 hprof " + pack.getHprof().getAbsolutePath());
            }
            if (pack.getGcLog() != null && pack.getGcLog() != gBefore) {
                report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                        "复用目标 JVM GC 日志（-Xloggc / 相关标志）",
                        pack.getGcLog().getAbsolutePath()));
                progress.detail("定位到目标 JVM GC 日志 " + pack.getGcLog().getAbsolutePath());
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
        if (pack.getAppLog() == null) {
            File resolved = LogPathResolver.resolve(req.getAppLog(), target.meta, target.process);
            if (resolved != null) {
                pack.setAppLog(resolved);
            }
        }
        return pack;
    }

    private void ingestIntoRunDir(EvidencePack pack, File runDir, DiagnoseReport report, DiagnoseRequest req) {
        boolean compare = req.getCompareAfterMs() != null || req.getHprofPrev() != null;
        if (pack.getHprof() != null) {
            String name = compare ? "heap-2-" + pack.getHprof().getName() : pack.getHprof().getName();
            if (req.getHprofPrev() != null) {
                name = "heap-2-" + stampName(pack.getHprof(), ".hprof");
            } else if (req.getCompareAfterMs() != null) {
                name = "heap-1-" + stampName(pack.getHprof(), ".hprof");
            }
            File copied = ingest(pack.getHprof(), runDir, "heap", name);
            if (copied != null && copied != pack.getHprof()) {
                report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                        "已将复用 hprof 纳入本轮运行目录", copied.getAbsolutePath()));
                progress.detail("已纳入运行目录 " + copied.getAbsolutePath()
                        + fileSizeSuffix(copied));
            }
            pack.setHprof(copied);
        }
        if (pack.getHprofPrev() != null) {
            File copied = ingest(pack.getHprofPrev(), runDir, "heap",
                    "heap-1-" + stampName(pack.getHprofPrev(), ".hprof"));
            pack.setHprofPrev(copied);
        }
        if (pack.getGcLog() != null) {
            pack.setGcLog(ingest(pack.getGcLog(), runDir, "gc", pack.getGcLog().getName()));
        }
        if (pack.getAppLog() != null) {
            pack.setAppLog(ingest(pack.getAppLog(), runDir, "logs", pack.getAppLog().getName()));
        }
        if (pack.getThreadDump() != null) {
            pack.setThreadDump(ingest(pack.getThreadDump(), runDir, "threads", pack.getThreadDump().getName()));
        }
        if (pack.getJstatSample() != null) {
            pack.setJstatSample(ingest(pack.getJstatSample(), runDir, "samples", pack.getJstatSample().getName()));
        }
    }

    private static String stampName(File src, String suffix) {
        String n = src.getName();
        if (n.toLowerCase().endsWith(suffix)) {
            return n;
        }
        return TimeSupport.nowFileStamp() + suffix;
    }

    private static File ingest(File src, File runDir, String sub, String destName) {
        if (src == null || !src.isFile()) {
            return src;
        }
        if (FileSupport.isUnder(src, runDir)) {
            return src.getAbsoluteFile();
        }
        return FileSupport.ingestInto(src, new File(runDir, sub), destName);
    }

    private static File existingFile(File f) {
        return f != null && f.isFile() ? f.getAbsoluteFile() : null;
    }

    private void collectThreadIfNeeded(DiagnoseRequest req, ResolvedTarget target, EvidencePack pack,
                                       DiagnoseReport report) {
        if (JvmEvidenceLocator.looksUsableThreadDump(pack.getThreadDump())) {
            progress.step("复用已有 thread dump → " + pack.getThreadDump().getAbsolutePath());
            return;
        }
        pack.setThreadDump(null);
        if (!req.isLiveCollect() || target.process == null || !discovery.pidExists(target.process.getPid())) {
            progress.step("未找到可用 thread dump");
            return;
        }
        progress.step("开始采集 thread dump …");
        JdkCollectors col = new JdkCollectors(req.getConfig());
        File td = col.collectThreadDump(target.process, target.runDir);
        pack.setThreadDump(td);
        progress.step("thread dump 完成：" + td.getAbsolutePath());
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(), "采集 thread dump", td.getAbsolutePath()));
    }

    /**
     * Collect or reuse dump1. One {@code --confirm} covers dump1 and dump2 in this command.
     *
     * @return always false (refusal throws via ConfirmGate)
     */
    private boolean collectDump1IfNeeded(DiagnoseRequest req, ResolvedTarget target, EvidencePack pack,
                                         DiagnoseReport report) {
        boolean live = req.isLiveCollect() && target.process != null
                && discovery.pidExists(target.process.getPid());
        boolean haveDump1 = JvmEvidenceLocator.looksUsableHprof(pack.getHprof());
        boolean wantDump2 = live && req.getCompareAfterMs() != null;
        boolean needDump1 = live && !haveDump1;
        if (needDump1) {
            progress.step("开始采集 heap dump（需确认）…");
        }
        if (needDump1 || wantDump2) {
            ConfirmGate.assertDumpAllowed(req.isConfirm());
        }
        if (needDump1) {
            pack.setHprof(null);
            JdkCollectors col = new JdkCollectors(req.getConfig());
            File hprof = col.collectHeapDumpConfirmed(target.process, target.runDir);
            String name = wantDump2
                    ? "heap-1-" + TimeSupport.nowFileStamp() + ".hprof"
                    : hprof.getName();
            if (wantDump2 && !hprof.getName().equals(name)) {
                File renamed = new File(hprof.getParentFile(), name);
                if (hprof.renameTo(renamed)) {
                    hprof = renamed;
                }
            }
            pack.setHprof(hprof);
            progress.step("heap dump 完成：" + hprof.getAbsolutePath() + fileSizeSuffix(hprof));
            report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                    "经确认采集 heap dump" + (wantDump2 ? "（dump1）" : ""),
                    hprof.getAbsolutePath()));
        } else if (!haveDump1) {
            pack.setHprof(null);
        } else if (pack.getHprof() != null) {
            progress.step("复用已有 hprof → " + pack.getHprof().getAbsolutePath()
                    + fileSizeSuffix(pack.getHprof()));
            if (wantDump2 && !pack.getHprof().getName().startsWith("heap-1-")) {
                File renamed = ingest(pack.getHprof(), target.runDir, "heap",
                        "heap-1-" + stampName(pack.getHprof(), ".hprof"));
                pack.setHprof(renamed);
            }
        }
        return false;
    }

    private void collectDump2IfNeeded(DiagnoseRequest req, ResolvedTarget target, EvidencePack pack,
                                      DiagnoseReport report) {
        boolean live = req.isLiveCollect() && target.process != null;
        if (!live || req.getCompareAfterMs() == null) {
            return;
        }
        long wait = Math.max(0L, req.getCompareAfterMs().longValue());
        if (wait > 0L) {
            progress.step("等待二次 dump（compare-after " + ConsoleProgress.formatDuration(wait) + "）…");
            report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                    "等待 --compare-after " + wait + "ms 后采集 dump2", "compare"));
            sleepQuietly(wait);
        }
        if (!discovery.pidExists(target.process.getPid())) {
            progress.step("等待结束后进程已退出，未能采集 dump2");
            report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                    "等待结束后进程已退出，未能采集 dump2；保留 dump1 并按单快照分析", "compare"));
            return;
        }
        progress.step("开始采集 heap dump（dump2）…");
        JdkCollectors col = new JdkCollectors(req.getConfig());
        File dump1 = pack.getHprof();
        File dump2 = col.collectHeapDumpConfirmed(target.process, target.runDir);
        File named = new File(dump2.getParentFile(), "heap-2-" + TimeSupport.nowFileStamp() + ".hprof");
        if (dump2.renameTo(named)) {
            dump2 = named;
        }
        pack.setHprofPrev(dump1);
        pack.setHprof(dump2);
        progress.step("heap dump 完成：" + dump2.getAbsolutePath() + fileSizeSuffix(dump2));
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                "经确认采集 heap dump（dump2）", dump2.getAbsolutePath()));
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JstatGcutilAnalyzer.SampleTrend runSampling(DiagnoseRequest req, ResolvedTarget target,
                                                        EvidencePack pack, DiagnoseReport report) {
        boolean live = req.isLiveCollect() && target.process != null
                && discovery.pidExists(target.process.getPid());
        if (!live) {
            if (pack.getJstatSample() != null && pack.getJstatSample().isFile()) {
                progress.step("复用已有 jstat 采样 → " + pack.getJstatSample().getAbsolutePath());
                JstatGcutilAnalyzer.SampleTrend t = gcutilAnalyzer.analyze(
                        FileSupport.readUtf8(pack.getJstatSample()));
                if (t.available) {
                    progress.step("采样完成：" + t.summary);
                    report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                            "复用已有 jstat 采样", pack.getJstatSample().getAbsolutePath()));
                } else {
                    progress.step("采样完成：无法解析");
                }
                return t.available ? t : null;
            }
            return null;
        }
        JdkCollectors col = new JdkCollectors(req.getConfig());
        int interval = req.getConfig().getSampleIntervalSeconds();
        int count = req.getConfig().getSampleCount();
        progress.step("开始 jstat 采样（" + interval + "s × " + count + "）…");
        File raw = col.collectGcutilSample(target.process, target.runDir, interval, count);
        if (raw == null) {
            progress.step("未找到 jstat，跳过堆代采样");
            report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                    "未找到 jstat，跳过堆代采样", "samples"));
            return null;
        }
        pack.setJstatSample(raw);
        String text = FileSupport.readUtf8(raw);
        JstatGcutilAnalyzer.SampleTrend t = gcutilAnalyzer.analyze(text);
        progress.step("采样完成：" + (t.available ? t.summary : "无法解析"));
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                t.available ? "完成 jstat -gcutil 采样（" + interval + "s × " + count + "）"
                        : "jstat 采样已写入但无法解析",
                raw.getAbsolutePath()));
        report.getEvidence().add(new EvidenceItem("EV-SAMPLE", "jstat_gcutil",
                raw.getAbsolutePath(), t.available, t.judgment));
        return t;
    }

    private AppLogLookback.Result runLogLookback(DiagnoseRequest req, ResolvedTarget target,
                                                 EvidencePack pack, DiagnoseReport report) {
        int minutes = req.getConfig().getLogLookbackMinutes();
        File log = pack.getAppLog();
        if (log == null) {
            log = LogPathResolver.resolve(req.getAppLog(), target.meta, target.process);
            if (log != null) {
                log = ingest(log, target.runDir, "logs", log.getName());
                pack.setAppLog(log);
            }
        }
        AppLogLookback.Result r;
        if (log == null || !log.isFile()) {
            progress.step("定位应用日志：未解析到路径");
            r = new AppLogLookback.Result();
            r.lookbackMinutes = minutes;
            r.unresolvedNote = "未解析到应用日志路径。请使用 --app-log <file> 后复跑本产品。";
            r.summary = r.unresolvedNote;
            report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                    "应用日志路径未解析", "log_lookback"));
            return r;
        }
        progress.step("定位应用日志 " + log.getAbsolutePath());
        progress.step("开始倒查近 " + minutes + " 分钟日志：" + log.getAbsolutePath());
        String text = FileSupport.readUtf8(log);
        r = logLookback.scan(text, System.currentTimeMillis(), minutes);
        r.scannedFiles.add(log.getAbsolutePath());
        File excerpt = new File(new File(target.runDir, "logs"), "lookback-hits.txt");
        StringBuilder body = new StringBuilder();
        body.append("# lookback ").append(minutes).append(" minutes\n");
        body.append("# scanned ").append(log.getAbsolutePath()).append('\n');
        body.append("# ").append(r.summary).append('\n');
        if (r.hits.isEmpty()) {
            body.append("(no hits in window)\n");
        } else {
            for (AppLogLookback.Hit h : r.hits) {
                body.append("---\n").append(h.excerpt).append('\n');
            }
        }
        FileSupport.writeUtf8(excerpt, body.toString());
        progress.step("日志倒查完成：" + r.summary);
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                r.hits.isEmpty() ? "日志倒查：窗口内无命中" : "日志倒查：命中 " + r.hits.size() + " 处",
                excerpt.getAbsolutePath()));
        report.getEvidence().add(new EvidenceItem("EV-LOG", "app_log_lookback",
                excerpt.getAbsolutePath(), true, r.summary));
        return r;
    }

    private HprofComparer.CompareResult runCompare(DiagnoseRequest req, EvidencePack pack, DiagnoseReport report) {
        File newer = pack.getHprof();
        File older = pack.getHprofPrev();
        if (older == null && newer == null) {
            return null;
        }
        if (older == null) {
            if (req.getCompareAfterMs() == null && req.getHprofPrev() == null) {
                return null;
            }
        }
        progress.step("开始对比 dump1 vs dump2…");
        HprofParser.HprofSummary sOld = null;
        HprofParser.HprofSummary sNew = null;
        if (older != null && JvmEvidenceLocator.looksUsableHprof(older)) {
            try {
                sOld = hprofParser.parse(older);
            } catch (JfaException e) {
                report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                        "dump1 无法解析: " + e.getMessage(), "compare"));
            }
        }
        if (newer != null && JvmEvidenceLocator.looksUsableHprof(newer)) {
            try {
                sNew = hprofParser.parse(newer);
            } catch (JfaException e) {
                report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                        "dump2 无法解析: " + e.getMessage(), "compare"));
            }
        }
        if (sOld == null && sNew == null) {
            progress.step("堆对比跳过：两份 dump 均无法解析");
            return null;
        }
        List<String> prior = new ArrayList<String>();
        if (sOld != null && sOld.primaryHolder != null) {
            prior.add(sOld.primaryHolder);
        }
        HprofComparer.CompareResult cmp = hprofComparer.compare(sOld, sNew,
                req.getConfig().getCompareTopN(), prior);
        progress.step("堆对比完成：" + judgmentZh(cmp.judgment));
        try {
            File json = new File(new File(pack.getEvidenceDir(), "heap"), "compare-summary.json");
            FileSupport.writeUtf8(json, JsonSupport.mapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(cmp.toJsonMap()));
            report.getEvidence().add(new EvidenceItem("EV-CMP", "heap_compare",
                    json.getAbsolutePath(), true, cmp.judgment));
        } catch (Exception e) {
            throw new JfaException(ErrorCode.E_IO_REPORT, "写入 compare-summary.json 失败", e);
        }
        report.getTimeline().add(new TimelineEvent(TimeSupport.nowIso(),
                "完成堆对比：" + cmp.judgment, "compare"));
        return cmp;
    }

    private DeadlockEngine.ThreadAnalysis analyzeThread(DiagnoseRequest req, ResolvedTarget target,
                                                        EvidencePack pack, DiagnoseReport report) {
        ReportSection sec = new ReportSection();
        sec.setType("thread");
        boolean live = target.process != null && discovery.pidExists(target.process.getPid());
        if (pack.getThreadDump() == null) {
            progress.step("分析线程 dump：缺少 thread dump");
            if (!live) {
                sec.setStatus("failed");
                sec.setConfidence(Confidence.NONE.wireName());
                sec.getQualification().put("deadlock_found", false);
                sec.getMissingEvidence().add("历史 thread dump（进程已死且无落盘 dump，无法还原死锁现场）");
                sec.getNextMinimalActions().add(
                        "jfa collect threaddump --pid <pid> 或传入 --thread-dump 后复跑 --type thread");
                report.getSections().add(sec);
                report.getEvidence().add(new EvidenceItem("EV-TD-MISS", "thread_dump", "", false, "缺失"));
                return null;
            }
            sec.setStatus("failed");
            sec.setNote("未能采集 thread dump");
            report.getSections().add(sec);
            return null;
        }
        progress.step("分析线程 dump " + pack.getThreadDump().getAbsolutePath());
        String text = FileSupport.readUtf8(pack.getThreadDump());
        DeadlockEngine.ThreadAnalysis ta = deadlockEngine.analyze(text);
        if (ta.isDeadlockFound()) {
            progress.step("发现死锁 " + ta.getDeadlockCount() + " 组");
        } else {
            progress.step("未发现死锁");
        }
        report.getEvidence().add(new EvidenceItem("EV-TD", "thread_dump",
                pack.getThreadDump().getAbsolutePath(), true,
                ta.isDeadlockFound() ? "死锁检出" : "未发现死锁"));
        sec.setStatus("ok");
        sec.setConfidence(Confidence.HIGH.wireName());
        sec.getQualification().put("deadlock_found", ta.isDeadlockFound());
        sec.getQualification().put("deadlock_count", ta.getDeadlockCount());
        if (ta.getJvmDeadlockDescription() != null) {
            sec.getQualification().put("jvm_deadlock_description", ta.getJvmDeadlockDescription());
        }
        sec.setSuspects(ta.getSuspects());
        if (ta.isDeadlockFound()) {
            addDeadlockRecs(sec, ta);
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
        if (memory.singleDumpLimitation != null && memory.level == EvidenceLevel.E3
                && pack.getHprofPrev() == null) {
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
        if (pack.getHprofPrev() != null) {
            report.getEvidence().add(new EvidenceItem("EV-HPROF-1", "hprof",
                    pack.getHprofPrev().getAbsolutePath(), true, "dump1"));
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

    private void addSampleSection(DiagnoseReport report, JstatGcutilAnalyzer.SampleTrend sample, EvidencePack pack) {
        ReportSection sec = new ReportSection();
        sec.setType("sample");
        sec.setStatus("ok");
        sec.setNote(sample.summary);
        sec.getQualification().put("judgment", sample.judgment);
        sec.getQualification().put("summary", sample.summary);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (JstatGcutilAnalyzer.SampleRow row : sample.rows) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("S0", row.s0);
            m.put("S1", row.s1);
            m.put("E", row.eden);
            m.put("O", row.old);
            if (!Double.isNaN(row.meta)) {
                m.put("M", row.meta);
            }
            m.put("YGC", row.ygc);
            m.put("FGC", row.fgc);
            rows.add(m);
        }
        sec.getQualification().put("rows", rows);
        if (pack.getJstatSample() != null) {
            sec.getQualification().put("raw_file", pack.getJstatSample().getAbsolutePath());
        }
        report.getSections().add(sec);
    }

    private void addLogSection(DiagnoseReport report, AppLogLookback.Result lookback) {
        ReportSection sec = new ReportSection();
        sec.setType("log_lookback");
        sec.setStatus(lookback.unresolvedNote != null ? "degraded" : "ok");
        sec.setNote(lookback.judgmentLine());
        sec.getQualification().put("lookback_minutes", lookback.lookbackMinutes);
        sec.getQualification().put("summary", lookback.judgmentLine());
        sec.getQualification().put("had_timestamps", lookback.hadTimestamps);
        sec.getQualification().put("oom_in_window", lookback.oomInWindow);
        sec.getQualification().put("scanned_files", lookback.scannedFiles);
        List<Map<String, Object>> hits = new ArrayList<Map<String, Object>>();
        for (AppLogLookback.Hit h : lookback.hits) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("oom", h.oom);
            m.put("excerpt", h.excerpt);
            hits.add(m);
        }
        sec.getQualification().put("hits", hits);
        if (lookback.unresolvedNote != null) {
            sec.getNextMinimalActions().add("jfa diagnose ... --app-log <file>");
        }
        report.getSections().add(sec);
    }

    private void addCompareSection(DiagnoseReport report, HprofComparer.CompareResult compare, EvidencePack pack) {
        ReportSection sec = new ReportSection();
        sec.setType("heap_compare");
        sec.setStatus("ok");
        sec.setNote(compare.summary);
        sec.getQualification().putAll(compare.toJsonMap());
        if (pack.getHprofPrev() != null) {
            sec.getQualification().put("dump1", pack.getHprofPrev().getAbsolutePath());
        }
        if (pack.getHprof() != null) {
            sec.getQualification().put("dump2", pack.getHprof().getAbsolutePath());
        }
        report.getSections().add(sec);
    }

    private void finalizeSummary(DiagnoseReport report, AnalysisMode mode,
                                 DeadlockEngine.ThreadAnalysis thread,
                                 OomEngine.MemoryAnalysis memory,
                                 JstatGcutilAnalyzer.SampleTrend sample,
                                 AppLogLookback.Result lookback,
                                 HprofComparer.CompareResult compare) {
        boolean deadlock = thread != null && thread.isDeadlockFound();
        boolean oom = memory != null && memory.oomConfirmed;
        boolean e3 = memory != null && memory.level == EvidenceLevel.E3;
        boolean leakLean = compare != null && HprofComparer.LEAK_OR_RETENTION.equals(compare.judgment);
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
        if (sample != null && sample.available) {
            report.getSummary().getHealth().getRiskHints().add("采样: " + sample.summary);
        }
        if (lookback != null && lookback.oomInWindow) {
            report.getSummary().getHealth().getRiskHints().add("日志窗口内存在 OutOfMemoryError");
        }
        if (compare != null) {
            report.getSummary().getHealth().getRiskHints().add("堆对比: " + compare.summary);
        }
        boolean health = !deadlock && !oom && !leakLean;
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
            if (sample != null && sample.available) {
                one.append("；采样").append(judgmentZh(sample.judgment));
            }
            if (lookback != null) {
                one.append("；").append(lookback.hits.isEmpty() && lookback.unresolvedNote == null
                        ? "日志窗口无命中" : lookback.judgmentLine());
            }
            if (compare != null) {
                one.append("；堆对比 ").append(judgmentZh(compare.judgment));
            }
            if (memory != null && memory.level == EvidenceLevel.E3 && !oom) {
                report.setReportMode(ReportMode.FAULT.wireName());
                report.getSummary().setOneLine(memory.oneLineFault);
                report.getSummary().setOverallConfidence(Confidence.HIGH.wireName());
            } else {
                report.getSummary().setOneLine(one.toString() + "。");
                report.getSummary().setOverallConfidence(Confidence.MEDIUM.wireName());
                if (memory != null && memory.level == EvidenceLevel.E0) {
                    report.getSummary().setOverallConfidence(Confidence.LOW.wireName());
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
            if (lookback != null && lookback.oomInWindow) {
                one.append("；日志窗口确认 OOM");
            }
            if (sample != null && sample.available) {
                one.append("；采样").append(judgmentZh(sample.judgment));
            }
            if (compare != null) {
                one.append("；堆对比 ").append(judgmentZh(compare.judgment));
            }
            report.getSummary().setOneLine(one.toString());
            report.getSummary().setOverallConfidence(
                    deadlock || e3 || leakLean ? Confidence.HIGH.wireName() : Confidence.MEDIUM.wireName());
        }
        if (memory != null && memory.level == EvidenceLevel.E1) {
            report.getSummary().setOverallConfidence(Confidence.LOW.wireName());
        }
        if (memory != null && memory.level == EvidenceLevel.E3) {
            report.getSummary().getHealth().setHeapOomEvidenceFound(true);
        }
    }

    static String judgmentZh(String j) {
        if ("climbing_old_no_reclaim".equals(j) || HprofComparer.LEAK_OR_RETENTION.equals(j)) {
            return "倾向泄漏/保留";
        }
        if ("peak_jitter".equals(j) || HprofComparer.PEAK_OR_JITTER.equals(j)) {
            return "高峰/抖动";
        }
        if ("stable".equals(j) || HprofComparer.STABLE_IN_WINDOW.equals(j)) {
            return "窗口内稳定";
        }
        if (HprofComparer.SINGLE_SNAPSHOT_ONLY.equals(j)) {
            return "仅单快照";
        }
        return j == null ? "" : j;
    }

    private DiagnoseResult write(DiagnoseReport report, DiagnoseRequest req, ResolvedTarget target) {
        progress.step("生成报告 …");
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
     * Healthy reports serialize conclusion/summary + timeline + evidence (run dir)
     * plus product sample/log/compare sections. Disclaimer is never included.
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
        slim.setEvidence(report.getEvidence());
        List<ReportSection> keep = new ArrayList<ReportSection>();
        if (report.getSections() != null) {
            for (ReportSection s : report.getSections()) {
                if ("sample".equals(s.getType()) || "log_lookback".equals(s.getType())
                        || "heap_compare".equals(s.getType())) {
                    keep.add(s);
                }
            }
        }
        slim.setSections(keep.isEmpty() ? null : keep);
        return slim;
    }

    private ResolvedTarget resolve(DiagnoseRequest req, ServiceRegistry registry) {
        if (req.getPid() != null) {
            progress.step("解析目标进程 pid=" + req.getPid());
        } else if (req.getService() != null) {
            progress.step("解析服务 " + req.getService());
        } else if (req.getEvidenceDir() != null) {
            progress.step("解析证据目录 " + req.getEvidenceDir().getAbsolutePath());
        } else if (req.getHprof() != null) {
            progress.step("解析离线 hprof " + req.getHprof().getAbsolutePath());
        } else if (req.getThreadDump() != null) {
            progress.step("解析离线 thread dump " + req.getThreadDump().getAbsolutePath());
        } else {
            progress.step("解析诊断目标");
        }
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
            t.evidenceDir = req.getEvidenceDir();
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
            t.evidenceDir = req.getEvidenceDir();
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
        progress.step("准备运行目录 " + t.runDir.getAbsolutePath());
        return t;
    }

    private String fileSizeSuffix(File f) {
        if (!progress.isVerbose() || f == null || !f.isFile()) {
            return "";
        }
        return "（" + f.length() + " bytes）";
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
