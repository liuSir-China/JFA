package com.jfa.core.evidence;

import com.jfa.common.JfaConstants;

public final class RecommendedJvmConfig {
    private RecommendedJvmConfig() {
    }

    public static String fullHelpConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append(JfaConstants.EVIDENCE_ENHANCE_OPTIONAL).append('\n');
        sb.append(JfaConstants.NOT_A_PREREQUISITE).append('\n');
        sb.append("诊断已有服务请直接：jfa diagnose --pid <pid>\n");
        sb.append("以下均为可选证据增强，不是使用本产品的前提；补参数只提高下次故障的事后归因上限。\n\n");

        sb.append("======== 启用 GC 日志（JDK 8）========\n");
        sb.append("| 参数 | 作用 |\n");
        sb.append("|------|------|\n");
        sb.append("| -XX:+PrintGCDetails | 打印详细 GC 事件 |\n");
        sb.append("| -XX:+PrintGCDateStamps | GC 行带日历时间戳 |\n");
        sb.append("| -XX:+PrintGCTimeStamps | GC 行带 JVM 启动后秒数 |\n");
        sb.append("| -Xloggc:<path> | 将 GC 日志写入文件（JFA 活体诊断会解析该路径并优先复用） |\n");
        sb.append("| -XX:+UseGCLogFileRotation | 启用 GC 日志文件轮转 |\n");
        sb.append("| -XX:NumberOfGCLogFiles=5 | 轮转保留的 GC 日志文件个数 |\n");
        sb.append("| -XX:GCLogFileSize=20M | 单个 GC 日志文件大小 |\n");
        sb.append('\n');

        sb.append("======== 启用 hprof / HeapDumpOnOutOfMemoryError（JDK 8）========\n");
        sb.append("| 参数 | 作用 |\n");
        sb.append("|------|------|\n");
        sb.append("| -XX:+HeapDumpOnOutOfMemoryError | OOM 时自动写出 hprof |\n");
        sb.append("| -XX:HeapDumpPath=<dir-or-file> | hprof 目录或文件路径（JFA 活体诊断会解析并优先复用已有 dump） |\n");
        sb.append('\n');

        sb.append("======== 其他可选证据增强（JDK 8）========\n");
        sb.append("| 参数 | 作用 |\n");
        sb.append("|------|------|\n");
        sb.append("| -XX:OnOutOfMemoryError=\"jstack -l %p > <threads-dir>/oom-%p.td\" | OOM 时额外落盘 thread dump（线程/死锁分析仍需要 thread dump，GC/hprof 不能替代） |\n");
        sb.append('\n');

        sb.append("======== bash 启动脚本配置示例 ========\n");
        sb.append(bashSnippet("my-svc"));
        sb.append('\n');
        sb.append("说明：以上全部为可选证据增强，用于提高下次故障的事后归因上限；");
        sb.append("对已运行 JDK 8 进程动态追加不能获得等价的完整 GC 文件日志或未来自动 dump。\n");
        return sb.toString();
    }

    public static String jdk8Block() {
        return ""
                + "-XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps \\\n"
                + "-Xloggc:/var/jfa/my-svc/gc/gc.log \\\n"
                + "-XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=20M \\\n"
                + "-XX:+HeapDumpOnOutOfMemoryError \\\n"
                + "-XX:HeapDumpPath=/var/jfa/my-svc/heap/ \\\n"
                + "-XX:OnOutOfMemoryError=\"jstack -l %p > /var/jfa/my-svc/threads/oom-%p.td\"\n";
    }

    public static String bashSnippet(String serviceId) {
        return "#!/bin/bash\n"
                + "set -euo pipefail\n"
                + "EV=/var/jfa/" + serviceId + "\n"
                + "mkdir -p \"$EV\"/{gc,heap,threads}\n"
                + "JAVA_OPTS=\"\\\n"
                + "  -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps \\\n"
                + "  -Xloggc:$EV/gc/gc.log \\\n"
                + "  -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=20M \\\n"
                + "  -XX:+HeapDumpOnOutOfMemoryError \\\n"
                + "  -XX:HeapDumpPath=$EV/heap/\"\n"
                + "exec java $JAVA_OPTS -jar /opt/apps/" + serviceId + ".jar\n";
    }

    public static String systemdSnippet(String serviceId) {
        return "[Service]\n"
                + "Environment=JAVA_OPTS=-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc=/var/jfa/"
                + serviceId + "/gc/gc.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 "
                + "-XX:GCLogFileSize=20M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/jfa/"
                + serviceId + "/heap/\n"
                + "ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/apps/" + serviceId + ".jar\n";
    }
}
