package com.jfa.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfa.common.AnalysisMode;
import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.OutputFormat;
import com.jfa.common.config.JfaConfig;
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
        try {
            return runOrThrow(args == null ? new String[0] : args);
        } catch (JfaException e) {
            return fail(e.getErrorCode(), e.getMessage(), false);
        } catch (Exception e) {
            return fail(ErrorCode.E_INTERNAL, e.getMessage() == null ? e.getClass().getName() : e.getMessage(), false);
        }
    }

    int runOrThrow(String[] args) {
        if (args.length == 0) {
            System.out.print(HelpText.mainHelp());
            return 0;
        }
        CliParser p = CliParser.parse(args);
        if (p.flag("help") && p.command() == null) {
            System.out.print(HelpText.mainHelp());
            return 0;
        }
        if (p.flag("version")) {
            System.out.println("JFA 1.0.0 (report_schema_version 2.1, JDK8)");
            return 0;
        }
        String cmd = p.command();
        if (cmd == null) {
            System.out.print(HelpText.mainHelp());
            return 0;
        }
        boolean jsonErr = "json".equals(p.opt("format"));
        JfaConfig config = JfaConfig.load(p.opt("config") == null ? null : new File(p.opt("config")));
        try {
            if ("help".equals(cmd)) {
                String topic = p.positional(1);
                if ("config".equals(topic)) {
                    System.out.print(RecommendedJvmConfig.fullHelpConfig());
                    return 0;
                }
                System.out.print(HelpText.mainHelp());
                return 0;
            }
            if ("config".equals(cmd)) {
                String sub = p.positional(1);
                if ("recommend".equals(sub) || sub == null) {
                    System.out.print(RecommendedJvmConfig.fullHelpConfig());
                    return 0;
                }
                throw new JfaException(ErrorCode.E_USAGE, "未知 config 子命令，请用: jfa config recommend");
            }
            if ("discover".equals(cmd)) {
                return cmdDiscover(p, jsonErr);
            }
            if ("register".equals(cmd)) {
                return cmdRegister(p, config);
            }
            if ("diagnose".equals(cmd) || "analyze".equals(cmd)) {
                return cmdDiagnose(p, config, "analyze".equals(cmd));
            }
            if ("collect".equals(cmd)) {
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
            System.out.printf("%-8s %-16s %-40s %s%n", "PID", "USER", "MAIN", "CMD");
            for (JavaProcessInfo info : list) {
                System.out.printf("%-8d %-16s %-40s %s%n", info.getPid(),
                        n(info.getUser()), n(info.getMainClassOrJar()), n(info.getJavaCmdSummary()));
            }
            System.out.println("共 " + list.size() + " 个 Java 进程。主路径: jfa diagnose --pid <pid>");
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
        System.out.println("已登记服务 " + meta.getServiceId()
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
        req.setLiveCollect(!analyze);
        if (analyze && req.getPid() == null) {
            req.setLiveCollect(false);
        }
        DiagnoseResult result = new DiagnoseOrchestrator().run(req);
        OutputFormat fmt = req.getFormat();
        if (fmt == OutputFormat.JSON) {
            System.out.println(result.getJson());
        } else {
            System.out.print(result.getText());
        }
        printReportPaths(result, fmt);
        return result.getExitCode();
    }

    /**
     * Always print absolute report path(s) so the user can open the file.
     * JSON format keeps stdout as pure JSON and prints paths on stderr.
     */
    static void printReportPaths(DiagnoseResult result, OutputFormat fmt) {
        java.io.PrintStream dest = fmt == OutputFormat.JSON ? System.err : System.out;
        dest.println();
        dest.println("======== 报告已写入 ========");
        if (result.getTextFile() != null) {
            dest.println("报告文件: " + result.getTextFile().getAbsolutePath());
        }
        if (result.getJsonFile() != null) {
            dest.println("JSON 报告: " + result.getJsonFile().getAbsolutePath());
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
            System.out.println(f.getAbsolutePath());
            return 0;
        }
        if ("heapdump".equals(sub) || "heap-dump".equals(sub)) {
            File f = col.collectHeapDump(proc, evidenceDir, true);
            System.out.println(f.getAbsolutePath());
            return 0;
        }
        if ("sample".equals(sub)) {
            File f = col.collectJstatSample(proc, evidenceDir, true, p.opt("interval", "5s"),
                    p.opt("duration", "60s"));
            System.out.println(f.getAbsolutePath());
            return 0;
        }
        throw new JfaException(ErrorCode.E_USAGE, "collect 子命令: heapdump | sample | threaddump");
    }

    private int cmdEvidence(CliParser p, JfaConfig config) {
        String sub = p.positional(1);
        if ("suggest".equals(sub)) {
            String text = new EvidenceEnhancer().suggest(config, p.opt("service"), p.longOpt("pid"), p.opt("cmd"));
            System.out.print(text);
            return 0;
        }
        if ("gc".equals(sub)) {
            EvidenceGc.GcReport r = new EvidenceGc().gc(config, p.opt("service"), p.flag("dry-run"));
            System.out.println("dry_run=" + r.dryRun + " deleted=" + r.deleted.size()
                    + " bytes_freed=" + r.bytesFreed);
            for (String d : r.deleted) {
                System.out.println(d);
            }
            return 0;
        }
        if ("enhance-snippet".equals(sub)) {
            System.out.print(new EvidenceEnhancer().snippet(p.opt("target", "systemd"), p.opt("service")));
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
            System.err.println("ERROR " + code.code() + ": " + message);
        }
        return code.exitCode();
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
