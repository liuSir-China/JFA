package com.jfa.cli;

public final class HelpText {
    private HelpText() {
    }

    public static String mainHelp() {
        return ""
                + "JFA — 内网 JVM 故障诊断助手（JDK 8 Linux CLI）\n"
                + "主路径：诊断已有服务，无需 jfa start。\n\n"
                + "用法:\n"
                + "  jfa [--config <path>] [--format text|json|both] [--verbose] <command> [options]\n\n"
                + "主入口:\n"
                + "  jfa diagnose --pid <pid>                 # 默认全量 = memory + thread；无异常则为健康体检\n"
                + "  jfa diagnose --service <name>\n"
                + "  jfa diagnose --pid <pid> --type auto     # 与默认等价\n"
                + "  jfa diagnose --pid <pid> --type memory   # 只分析内存\n"
                + "  jfa diagnose --pid <pid> --type thread   # 只分析线程（含死锁）\n\n"
                + "常用命令:\n"
                + "  jfa discover [--user u] [--main keyword] [--format json]\n"
                + "  jfa register --name <id> [--pid n] [--evidence-dir d] [--app-log f] [--java-home d] [--force]\n"
                + "  jfa analyze --evidence-dir d --type memory|thread|auto\n"
                + "  jfa analyze --hprof file --gc-log file --type memory\n"
                + "  jfa collect threaddump --pid n\n"
                + "  jfa collect heapdump --pid n [--confirm]\n"
                + "  jfa collect sample --pid n --interval 5s --duration 60s [--confirm]\n"
                + "  jfa evidence suggest [--service n|--pid n|--cmd '...']\n"
                + "  jfa evidence gc [--service n] [--dry-run]\n"
                + "  jfa evidence enhance-snippet [--service n] [--target systemd|wrapper]\n"
                + "  jfa status / jfa show [--service n]\n"
                + "  jfa help\n"
                + "  jfa help config          # 打印可复制的推荐 JVM 配置（抄作业）\n"
                + "  jfa config recommend     # 与 help config 等价\n\n"
                + "--type 仅支持 memory | thread | auto（兼容别名 oom/heap→memory，deadlock→thread，both→auto）。\n"
                + "活体 heap dump / jstat 采样会先说明 STW/磁盘/服务影响并询问 y/n；脚本/CI 可用 --confirm 等同于回答 y。\n"
                + "thread dump 采集不经过该危险确认。诊断会优先复用目标 JVM 已有 hprof/GC，证据不足才活体采集。\n"
                + "推荐 JVM 配置见: jfa help config （可选证据增强，不是使用前提）。\n";
    }
}
