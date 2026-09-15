package com.jfa.cli;

public final class HelpText {
    private HelpText() {
    }

    public static String mainHelp() {
        return ""
                + "JFA — 内网 JVM 故障诊断助手（JDK 8 Linux CLI）\n"
                + "主路径：诊断已有服务，无需 jfa start。\n"
                + "本产品执行短时堆代采样、应用日志倒查、以及双 hprof 对比；不要把“请自行查日志/对比 dump”当主路径。\n\n"
                + "用法:\n"
                + "  jfa [--config <path>] [--format text|json|both] [--verbose] <command> [options]\n\n"
                + "主入口:\n"
                + "  jfa diagnose --pid <pid>                 # 默认全量 = memory + thread；无异常则为健康体检\n"
                + "  jfa diagnose --service <name>\n"
                + "  jfa diagnose --pid <pid> --type auto     # 与默认等价\n"
                + "  jfa diagnose --pid <pid> --type memory   # 只分析内存（含 jstat 采样 + 日志倒查）\n"
                + "  jfa diagnose --pid <pid> --type thread   # 只分析线程（含死锁）\n"
                + "  jfa diagnose --pid <pid> --type memory --compare-after 15m [--confirm]\n"
                + "      # 活体 A/B：dump1（复用已有 hprof 或采集）→ 采样+日志倒查 → 等待 → dump2 → 本产品对比\n"
                + "      # 一次 --confirm 覆盖本命令两次 dump\n\n"
                + "常用命令:\n"
                + "  jfa discover [--user u] [--main keyword] [--format json]\n"
                + "  jfa register --name <id> [--pid n] [--evidence-dir d] [--app-log f] [--java-home d] [--force]\n"
                + "  jfa analyze --evidence-dir d --type memory|thread|auto\n"
                + "  jfa analyze --hprof file --gc-log file --type memory\n"
                + "  jfa analyze --hprof newer.hprof --hprof-prev older.hprof --type memory\n"
                + "      # 离线双快照对比，不采集\n"
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
                + "诊断行为:\n"
                + "  每轮只写 <install>/reportfile/pid_<pid>/<yyyyMMdd-HHmmss>/（自包含：复用的 hprof/GC/日志会 copy 或硬链接进来）。\n"
                + "  cover.file=true 时先清空该 pid 目录；false 保留旧时间戳目录。\n"
                + "  活体 memory/auto：jstat -gcutil 短采样（sample.interval.seconds × sample.count，默认 5s×8）。\n"
                + "  日志倒查：默认分析时刻往前 10 分钟（log.lookback.minutes）；来源 --app-log / 已登记服务 / JVM 命令行。\n"
                + "  解析不到日志时只提示补 --app-log，不会列出一长串研发自查项。\n"
                + "--type 仅支持 memory | thread | auto（兼容别名 oom/heap→memory，deadlock→thread，both→auto）。\n"
                + "活体 heap dump 会先说明 STW/磁盘/服务影响并询问 y/n；脚本/CI 可用 --confirm 等同于回答 y。\n"
                + "thread dump 采集不经过该危险确认。诊断会优先复用目标 JVM 已有 hprof/GC，证据不足才活体采集。\n"
                + "GC/hprof 从不替代 thread dump。推荐 JVM 配置见: jfa help config （可选证据增强，不是使用前提）。\n";
    }
}
