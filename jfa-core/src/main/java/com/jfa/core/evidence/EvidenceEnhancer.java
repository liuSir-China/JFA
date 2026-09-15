package com.jfa.core.evidence;

import com.jfa.common.JfaConstants;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.core.discovery.JavaProcessDiscovery;
import com.jfa.core.registry.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;

public class EvidenceEnhancer {

    public String suggest(JfaConfig config, String service, Long pid, String cmd) {
        String command = cmd;
        if (command == null && pid != null) {
            try {
                JavaProcessInfo info = new JavaProcessDiscovery().requirePid(pid);
                command = info.getCommandLine() == null ? "" : info.getCommandLine().replace('\n', ' ');
            } catch (RuntimeException e) {
                command = "";
            }
        }
        if (command == null && service != null) {
            try {
                command = "";
                new ServiceRegistry(config).require(service);
            } catch (RuntimeException ignored) {
                command = "";
            }
        }
        if (command == null) {
            command = "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(JfaConstants.NOT_A_PREREQUISITE).append('\n');
        sb.append("技术事实：对已运行的 JDK 8 进程，不能动态追加与启动参数等价的完整 GC 文件日志，");
        sb.append("也不能动态打开与 HeapDumpOnOutOfMemoryError 等价的未来自动 dump。\n\n");
        List<String> gaps = new ArrayList<String>();
        if (!containsGcFile(command)) {
            gaps.add("缺少滚动 GC 文件日志（-Xloggc 与 PrintGC*）");
        }
        if (!command.contains("HeapDumpOnOutOfMemoryError")) {
            gaps.add("缺少 -XX:+HeapDumpOnOutOfMemoryError");
        }
        if (!command.contains("HeapDumpPath")) {
            gaps.add("缺少 -XX:HeapDumpPath=...");
        }
        sb.append("当前命令行缺口：\n");
        if (gaps.isEmpty()) {
            sb.append("- 未发现明显缺口（仍建议核对路径可写与轮转份数）。\n");
        } else {
            for (String g : gaps) {
                sb.append("- ").append(g).append('\n');
            }
        }
        sb.append('\n').append(RecommendedJvmConfig.jdk8Block());
        sb.append("\n验证方式：重启后确认 gc 日志文件增长、人为制造小堆 OOM 后 HeapDumpPath 出现 hprof，再用本产品 analyze --type memory。\n");
        return sb.toString();
    }

    public String snippet(String target, String serviceId) {
        String id = serviceId == null ? "my-svc" : serviceId;
        if ("wrapper".equalsIgnoreCase(target)) {
            return JfaConstants.NOT_A_PREREQUISITE + "\n# 可选 wrapper 片段（suggest 策略，默认不注入）\n"
                    + RecommendedJvmConfig.bashSnippet(id);
        }
        return JfaConstants.NOT_A_PREREQUISITE + "\n# 可选 systemd 片段（不会自动改写客户单元文件）\n"
                + RecommendedJvmConfig.systemdSnippet(id);
    }

    static boolean containsGcFile(String command) {
        String c = command == null ? "" : command;
        return c.contains("-Xloggc") || c.contains("-Xlog:gc") || c.contains("PrintGCDetails");
    }
}
