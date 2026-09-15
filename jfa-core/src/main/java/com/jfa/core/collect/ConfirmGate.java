package com.jfa.core.collect;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * Interactive confirmation for dangerous live collects (heap dump / jstat sample).
 * {@code --confirm} is the non-interactive equivalent of answering {@code y}.
 * No trading-hours policy is consulted.
 */
public final class ConfirmGate {
    private ConfirmGate() {
    }

    public static final String RISK_HINT =
            "STW/磁盘/内存风险：heap dump 会触发目标 JVM 停顿（STW），并占用与堆大小相当的磁盘；"
                    + "jstat 采样有额外开销。";

    public static final String EXPLANATION =
            "即将对目标 JVM 执行危险活体采集（heap dump / jstat 采样）。\n"
                    + "  - Heap dump 会触发 Stop-The-World（STW）停顿，服务可能短时不可用。\n"
                    + "  - 生成的 hprof 体积约等于当前堆大小，将占用相应磁盘空间。\n"
                    + "  - jstat 采样会带来额外 CPU / 开销。\n"
                    + "请确认当前允许该影响。\n";

    private static final String ABORT_MESSAGE =
            "已取消危险采集。请在终端输入 y 继续，或使用 --confirm（脚本/CI 非交互等价于回答 y）。";

    public static void assertDumpAllowed(boolean confirm) {
        assertDumpAllowed(confirm, System.out, System.in, System.console());
    }

    /**
     * @param confirm {@code true} if CLI {@code --confirm} was passed
     * @param out     explanation / prompt destination
     * @param in      stdin used when {@code console} is null but a test injects a stream
     * @param console real terminal console; {@code null} means non-interactive (do not block)
     */
    public static void assertDumpAllowed(boolean confirm, PrintStream out, InputStream in, Console console) {
        if (confirm) {
            return;
        }
        if (console != null) {
            out.print(EXPLANATION);
            out.flush();
            String line = console.readLine("继续采集？请输入 y 继续，或 n 取消 [y/n]: ");
            if (isYes(line)) {
                return;
            }
            throw new JfaException(ErrorCode.E_CONFIRM_REQUIRED, ABORT_MESSAGE + " " + RISK_HINT);
        }
        if (in != null && in != System.in) {
            out.print(EXPLANATION);
            out.print("继续采集？请输入 y 继续，或 n 取消 [y/n]: ");
            out.flush();
            String line = readLine(in);
            if (isYes(line)) {
                return;
            }
            throw new JfaException(ErrorCode.E_CONFIRM_REQUIRED, ABORT_MESSAGE + " " + RISK_HINT);
        }
        throw new JfaException(ErrorCode.E_CONFIRM_REQUIRED,
                "危险操作需要确认。请在终端输入 y 继续，或使用 --confirm。" + RISK_HINT);
    }

    static boolean isYes(String line) {
        return line != null && "y".equalsIgnoreCase(line.trim());
    }

    static String readLine(InputStream in) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()));
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}
