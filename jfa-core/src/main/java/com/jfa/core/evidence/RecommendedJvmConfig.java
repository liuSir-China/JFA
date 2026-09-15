package com.jfa.core.evidence;

import com.jfa.common.JfaConstants;

public final class RecommendedJvmConfig {
    private RecommendedJvmConfig() {
    }

    public static String fullHelpConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append(JfaConstants.EVIDENCE_ENHANCE_OPTIONAL).append('\n');
        sb.append(JfaConstants.NOT_A_PREREQUISITE).append('\n');
        sb.append("诊断已有服务请直接：jfa diagnose --pid <pid>  （不要先 start）\n\n");
        sb.append("======== JDK 8 推荐启动参数（可复制）========\n");
        sb.append(jdk8Block());
        sb.append('\n');
        sb.append("======== JDK 11+ 对照（可复制）========\n");
        sb.append(jdk11Block());
        sb.append('\n');
        sb.append("======== bash 启动脚本片段 ========\n");
        sb.append(bashSnippet("my-svc"));
        sb.append('\n');
        sb.append("======== systemd Environment= / ExecStart 粘贴示例 ========\n");
        sb.append(systemdSnippet("my-svc"));
        sb.append('\n');
        sb.append("说明：以上全部为可选证据增强，用于提高下次故障的事后归因上限；");
        sb.append("对已运行 JDK8 进程动态追加不能获得等价的完整 GC 文件日志或未来自动 dump。\n");
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

    public static String jdk11Block() {
        return ""
                + "-Xlog:gc*,gc+heap=info:file=/var/jfa/my-svc/gc/gc.log:time,uptime,level,tags:filecount=5,filesize=20M \\\n"
                + "-XX:+HeapDumpOnOutOfMemoryError \\\n"
                + "-XX:HeapDumpPath=/var/jfa/my-svc/heap/\n";
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
