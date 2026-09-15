package com.jfa.common;

/**
 * CLI error codes from PRD §9. Numeric values are process exit codes.
 */
public enum ErrorCode {
    OK("OK", 0, "成功产出报告"),
    E_PID_NOT_FOUND("E_PID_NOT_FOUND", 10, "pid 不存在"),
    E_SERVICE_NOT_FOUND("E_SERVICE_NOT_FOUND", 11, "未登记服务名"),
    E_SERVICE_EXISTS("E_SERVICE_EXISTS", 12, "登记名冲突"),
    E_PERM_DISCOVERY("E_PERM_DISCOVERY", 20, "无权发现进程"),
    E_PERM_ATTACH("E_PERM_ATTACH", 21, "无权 attach JVM"),
    E_CONFIRM_REQUIRED("E_CONFIRM_REQUIRED", 30, "缺确认"),
    E_TRADING_HOURS_DENIED("E_TRADING_HOURS_DENIED", 31, "交易时段拒绝危险操作"),
    E_NO_THREAD_DUMP("E_NO_THREAD_DUMP", 40, "无法做死锁还原"),
    E_INSUFFICIENT_EVIDENCE("E_INSUFFICIENT_EVIDENCE", 41, "OOM 证据严重不足"),
    E_HPROF_INVALID("E_HPROF_INVALID", 42, "hprof 不可用"),
    E_DISK_FULL("E_DISK_FULL", 50, "磁盘不足"),
    E_IO_EVIDENCE("E_IO_EVIDENCE", 51, "证据目录 IO 失败"),
    E_IO_REPORT("E_IO_REPORT", 52, "报告写入失败"),
    E_USAGE("E_USAGE", 2, "命令用法错误"),
    E_INTERNAL("E_INTERNAL", 99, "未归类内部错误");

    private final String code;
    private final int exitCode;
    private final String defaultMessage;

    ErrorCode(String code, int exitCode, String defaultMessage) {
        this.code = code;
        this.exitCode = exitCode;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public int exitCode() {
        return exitCode;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
