package com.jfa.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfa.common.AnalysisMode;
import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.OutputFormat;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.io.ConsoleLayout;
import com.jfa.common.json.JsonSupport;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.common.model.ServiceMeta;
import com.jfa.common.time.TimeSupport;
import com.jfa.core.collect.ConfirmGate;
import com.jfa.core.collect.JdkCollectors;
import com.jfa.core.diagnose.DiagnoseOrchestrator;
import com.jfa.core.diagnose.DiagnoseRequest;
import com.jfa.core.diagnose.DiagnoseResult;
import com.jfa.core.diagnose.RunLayout;
import com.jfa.core.discovery.JavaProcessDiscovery;
import com.jfa.core.evidence.EvidenceEnhancer;
import com.jfa.core.evidence.EvidenceGc;
import com.jfa.core.evidence.RecommendedJvmConfig;
import com.jfa.core.registry.ServiceRegistry;
import com.jfa.core.report.TextReportRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JfaMain {
    public static void main(String[] args) {
        System.exit(new JfaMain().run(args));
    }

    public int run(String[] args) {
        ConsoleLayout.resetSession();
        try {
            return runOrThrow(args == null ? new String[0] : args);
        } catch (JfaException e) {
            return fail(e.getErrorCode(), e.getMessage(), false);
        } catch (Exception e) {
            return fail(ErrorCode.E_INTERNAL, e.getMessage() == null ? e.getClass().getName() : e.getMessage(), false);
        }
    }

    int runOrThrow(String[] args) {
        args = mapInvocation(invocationName(), args == null ? new String[0] : args);
        if (args.length == 0) {
            printHelp(HelpText.mainHelp());
            return 0;
        }
        CliParser p = CliParser.parse(args);
        if (p.flag("help") && p.command() == null) {
            printHelp(HelpText.mainHelp());
            return 0;
        }
        if (p.flag("version")) {
            ConsoleLayout.printLine(System.out, "JFA 1.0.0 (report_schema_version 2.1, JDK8)");
            return 0;
        }
        String cmd = p.command();
        if (cmd == null) {
            printHelp(HelpText.mainHelp());
            return 0;
        }
        boolean jsonErr = "json".equals(p.opt("format"));
        JfaConfig config = JfaConfig.load(p.opt("config") == null ? null : new File(p.opt("config")));
        try {
            if ("help".equals(cmd)) {
                String topic = p.positional(1);
                if ("config".equals(topic) || topic == null && p.flag("help")) {
                    printHelp(RecommendedJvmConfig.fullHelpConfig());
                    return 0;
                }
                if ("config".equals(topic) || "recommend".equals(topic)) {
                    printHelp(RecommendedJvmConfig.fullHelpConfig());
                    return 0;
                }
                if (topic == null) {
                    printHelp(HelpText.mainHelp());
                    return 0;
                }
                printHelp(HelpText.mainHelp());
                return 0;
            }
            if ("config".equals(cmd)) {
                String sub = p.positional(1);
                if ("recommend".equals(sub) || sub == null) {
                    printHelp(RecommendedJvmConfig.fullHelpConfig());
                    return 0;
                }
                throw new JfaException(ErrorCode.E_USAGE, "未知 config 子命令，请用: jfa-config 或 jfa help");
            }
            if ("discover".equals(cmd)) {
                if (p.flag("help")) {
                    printHelp(RecommendedJvmConfig.fullHelpConfig());
                    return 0;
                }
                return cmdDiscover(p, jsonErr);
            }
            if ("register".equals(cmd)) {
                return cmdRegister(p, config);
            }
            if ("diagnose".equals(cmd) || "analyze".equals(cmd)) {
                if (p.flag("help") || isBare(p)) {
                    printHelp("analyze".equals(cmd) ? HelpText.analyzeHelp() : HelpText.diagnoseHelp());
                    return 0;
                }
                return cmdDiagnose(p, config, "analyze".equals(cmd));
            }
            if ("collect".equals(cmd)) {
                if (p.flag("help") || isBare(p)) {
                    printHelp(HelpText.collectHelp());
                    return 0;
                }
                return cmdCollect(p, config);
            }
            if ("evidence".equals(cmd)) {
                return cmdEvidence(p, config);
            }
            if ("status".equals(cmd) || "show".equals(cmd)) {
                return cmdStatus(p, config);
            }
            throw new JfaException(ErrorCode.E_USAGE, "未知命令: " + cmd + "\n" + HelpText.mainHelp());
        } catch (JfaException e) {
            return fail(e.getErrorCode(), e.getMessage(), jsonErr);
        }
    }

    /**
     * Map bin wrapper names ({@code JFA_CMD} or {@code argv[0]}-style) onto internal
     * subcommands so {@code jfa-analyze} becomes {@code diagnose}, etc.
     */
    static String[] mapInvocation(String invoked, String[] args) {
        if (args == null) {
            args = new String[0];
        }
        if (invoked == null || invoked.isEmpty()) {
            return args;
        }
        invoked = baseName(invoked);
        if ("jfa".equals(invoked) && isHelpToken(firstArg(args))) {
            return new String[]{"help", "config"};
        }
        if (args.length > 0 && isInternalCommand(args[0])) {
            return args;
        }
        if ("jfa".equals(invoked)) {
            return prepend(args, "discover");
        }
        if ("jfa-analyze".equals(invoked)) {
            return prepend(args, "diagnose");
        }
        if ("jfa-file-analyze".equals(invoked)) {
            return prepend(args, "analyze");
        }
        if ("jfa-collect".equals(invoked)) {
            return prepend(args, "collect");
        }
        if ("jfa-config".equals(invoked)) {
            return prepend(args, "help", "config");
        }
        return args;
    }

    static String invocationName() {
        String env = System.getenv("JFA_CMD");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return null;
    }

    static boolean isInternalCommand(String token) {
        return "discover".equals(token) || "diagnose".equals(token) || "analyze".equals(token)
                || "collect".equals(token) || "help".equals(token) || "config".equals(token)
                || "register".equals(token) || "evidence".equals(token)
                || "status".equals(token) || "show".equals(token);
    }

    private static boolean isHelpToken(String token) {
        return "--help".equals(token) || "help".equals(token) || "-h".equals(token);
    }

    private static String firstArg(String[] args) {
        return args.length == 0 ? null : args[0];
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String[] prepend(String[] args, String... prefix) {
        String[] out = new String[prefix.length + args.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(args, 0, out, prefix.length, args.length);
        return out;
    }

    /**
     * Bare command: only the subcommand (and cosmetic flags / --help).
     */
    static boolean isBare(CliParser p) {
        if (p.positionals.size() > 1) {
            return false;
        }
        for (String key : p.options.keySet()) {
            if (!isCosmeticFlag(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCosmeticFlag(String key) {
        return "help".equals(key) || "quiet".equals(key) || "verbose".equals(key) || "version".equals(key);
    }

    private static void printHelp(String text) {
        ConsoleLayout.printText(System.out, text);
    }

    private int cmdDiscover(CliParser p, boolean jsonErr) {
        List<JavaProcessInfo> list = new JavaProcessDiscovery().discover(p.opt("user"), p.opt("main"));
        OutputFormat fmt = OutputFormat.fromCli(p.opt("format"));
        if (fmt == OutputFormat.JSON) {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (JavaProcessInfo info : list) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("pid", info.getPid());
                row.put("main_class_or_jar", info.getMainClassOrJar());
                row.put("user", info.getUser());
                row.put("java_cmd_summary", info.getJavaCmdSummary());
                rows.add(row);
            }
            try {
                System.out.println(JsonSupport.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(rows));
            } catch (Exception e) {
                throw new JfaException(ErrorCode.E_INTERNAL, e.getMessage(), e);
            }
        } else {
            ConsoleLayout.printBanner(System.out, "======== Java 进程发现 ========");
            ConsoleLayout.printLine(System.out, String.format("%-8s %-16s %-40s %s", "PID", "USER", "MAIN", "CMD"));
            for (JavaProcessInfo info : list) {
                ConsoleLayout.printLine(System.out, String.format("%-8d %-16s %-40s %s", info.getPid(),
                        n(info.getUser()), n(info.getMainClassOrJar()), n(info.getJavaCmdSummary())));
            }
            ConsoleLayout.printLine(System.out, "共 " + list.size() + " 个 Java 进程。主路径: jfa-analyze --pid <pid>");
        }
        return 0;
    }

    private int cmdRegister(CliParser p, JfaConfig config) {
        String name = p.opt("name");
        if (name == null) {
            name = p.positional(1);
        }
        File evidenceDir = p.opt("evidence-dir") == null ? null : new File(p.opt("evidence-dir"));
        ServiceMeta meta = new ServiceRegistry(config).register(
                name, p.longOpt("pid"), evidenceDir, p.opt("app-log"), p.opt("java-home"),
                p.flag("force"), p.opt("display-name"), p.opt("main"));
        ConsoleLayout.printLine(System.out, "已登记服务 " + meta.getServiceId()
                + " evidence_dir=" + meta.getPaths().getEvidenceDir()
                + " lifecycle_managed_by_jfa=" + meta.isLifecycleManagedByJfa());
        return 0;
    }

    private int cmdDiagnose(CliParser p, JfaConfig config, boolean analyze) {
        DiagnoseRequest req = new DiagnoseRequest();
        req.setConfig(config);
        req.setPid(p.longOpt("pid"));
        req.setService(p.opt("service"));
        if (p.opt("evidence-dir") != null) {
            req.setEvidenceDir(new File(p.opt("evidence-dir")));
        }
        req.setMode(AnalysisMode.fromCli(p.opt("type")));
        if (p.opt("hprof") != null) {
            req.setHprof(new File(p.opt("hprof")));
        }
        if (p.opt("gc-log") != null) {
            req.setGcLog(new File(p.opt("gc-log")));
        }
        if (p.opt("app-log") != null) {
            req.setAppLog(new File(p.opt("app-log")));
        }
        if (p.opt("thread-dump") != null) {
            req.setThreadDump(new File(p.opt("thread-dump")));
        }
        req.setConfirm(p.flag("confirm"));
        if (p.opt("hprof-prev") != null) {
            req.setHprofPrev(new File(p.opt("hprof-prev")));
        }
        String compareAfter = p.opt("compare-after");
        if (compareAfter == null || compareAfter.trim().isEmpty()) {
            compareAfter = config.getCompareAfter();
        }
        if (compareAfter != null && !compareAfter.trim().isEmpty()) {
            req.setCompareAfterMs(TimeSupport.parseDurationMs(compareAfter));
        }
        if (p.opt("out") != null) {
            req.setOutDir(new File(p.opt("out")));
        }
        req.setFormat(p.opt("format") == null ? OutputFormat.BOTH : OutputFormat.fromCli(p.opt("format")));
        req.setQuiet(p.flag("quiet"));
        req.setVerbose(p.flag("verbose"));
        req.setLiveCollect(!analyze);
        if (analyze && req.getPid() == null) {
            req.setLiveCollect(false);
        }
        DiagnoseResult result = new DiagnoseOrchestrator().run(req);
        OutputFormat fmt = req.getFormat();
        if (fmt == OutputFormat.JSON) {
            System.out.println(result.getJson());
        }
        printReportPaths(result, fmt);
        return result.getExitCode();
    }

    /**
     * Print only the report-path footer (never the markdown/text body).
     * JSON format keeps stdout as pure JSON and prints paths on stderr.
     */
    static void printReportPaths(DiagnoseResult result, OutputFormat fmt) {
        java.io.PrintStream dest = fmt == OutputFormat.JSON ? System.err : System.out;
        ConsoleLayout.printBanner(dest, TextReportRenderer.REPORT_WRITTEN_BANNER);
        String[] lines = TextReportRenderer.renderWriteFooter(result.getTextFile(), result.getJsonFile())
                .split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            if (ConsoleLayout.isBanner(line)) {
                continue;
            }
            ConsoleLayout.printLine(dest, line);
        }
    }

    private int cmdCollect(CliParser p, JfaConfig config) {
        String sub = p.positional(1);
        Long pid = p.longOpt("pid");
        if (pid == null) {
            throw new JfaException(ErrorCode.E_USAGE, "collect 需要 --pid");
        }
        if (("heapdump".equals(sub) || "heap-dump".equals(sub) || "sample".equals(sub))) {
            ConfirmGate.assertDumpAllowed(p.flag("confirm"));
        }
        JavaProcessInfo proc = new JavaProcessDiscovery().requirePid(pid);
        File evidenceDir;
        if (p.opt("evidence-dir") != null) {
            evidenceDir = new File(p.opt("evidence-dir"));
        } else {
            evidenceDir = RunLayout.prepareRunDir(config, "pid_" + pid, null);
        }
        JdkCollectors col = new JdkCollectors(config);
        if ("threaddump".equals(sub) || "thread-dump".equals(sub)) {
            File f = col.collectThreadDump(proc, evidenceDir);
            ConsoleLayout.printLine(System.out, f.getAbsolutePath());
            return 0;
        }
        if ("heapdump".equals(sub) || "heap-dump".equals(sub)) {
            File f = col.collectHeapDump(proc, evidenceDir, true);
            ConsoleLayout.printLine(System.out, f.getAbsolutePath());
            return 0;
        }
        if ("sample".equals(sub)) {
            File f = col.collectJstatSample(proc, evidenceDir, true, p.opt("interval", "5s"),
                    p.opt("duration", "60s"));
            ConsoleLayout.printLine(System.out, f.getAbsolutePath());
            return 0;
        }
        throw new JfaException(ErrorCode.E_USAGE, "collect 子命令: heapdump | sample | threaddump");
    }

    private int cmdEvidence(CliParser p, JfaConfig config) {
        String sub = p.positional(1);
        if ("suggest".equals(sub)) {
            String text = new EvidenceEnhancer().suggest(config, p.opt("service"), p.longOpt("pid"), p.opt("cmd"));
            ConsoleLayout.printText(System.out, text);
            return 0;
        }
        if ("gc".equals(sub)) {
            EvidenceGc.GcReport r = new EvidenceGc().gc(config, p.opt("service"), p.flag("dry-run"));
            ConsoleLayout.printLine(System.out, "dry_run=" + r.dryRun + " deleted=" + r.deleted.size()
                    + " bytes_freed=" + r.bytesFreed);
            for (String d : r.deleted) {
                ConsoleLayout.printLine(System.out, d);
            }
            return 0;
        }
        if ("enhance-snippet".equals(sub)) {
            ConsoleLayout.printText(System.out, new EvidenceEnhancer().snippet(p.opt("target", "systemd"), p.opt("service")));
            return 0;
        }
        throw new JfaException(ErrorCode.E_USAGE, "evidence 子命令: suggest | gc | enhance-snippet");
    }

    private int cmdStatus(CliParser p, JfaConfig config) {
        ServiceRegistry reg = new ServiceRegistry(config);
        ObjectMapper mapper = JsonSupport.mapper();
        try {
            if (p.opt("service") != null) {
                System.out.println(mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(reg.require(p.opt("service"))));
            } else {
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reg.list()));
            }
        } catch (Exception e) {
            throw new JfaException(ErrorCode.E_INTERNAL, e.getMessage(), e);
        }
        return 0;
    }

    private int fail(ErrorCode code, String message, boolean json) {
        if (json) {
            Map<String, String> m = new LinkedHashMap<String, String>();
            m.put("error_code", code.code());
            m.put("message", message);
            try {
                System.err.println(JsonSupport.mapper().writeValueAsString(m));
            } catch (Exception e) {
                System.err.println("ERROR " + code.code() + ": " + message);
            }
        } else {
            ConsoleLayout.ensureLead(System.err);
            System.err.println("ERROR " + code.code() + ": " + message);
        }
        return code.exitCode();    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
