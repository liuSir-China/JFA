package com.jfa.core.proc;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {
    public static class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public Result run(List<String> cmd, long timeoutMs) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = null;
        try {
            p = pb.start();
            Gobbler out = new Gobbler(p.getInputStream());
            Gobbler err = new Gobbler(p.getErrorStream());
            out.start();
            err.start();
            boolean finished = waitFor(p, timeoutMs);
            if (!finished) {
                p.destroy();
                throw new JfaException(ErrorCode.E_INTERNAL, "命令超时: " + cmd);
            }
            out.join();
            err.join();
            return new Result(p.exitValue(), out.text(), err.text());
        } catch (JfaException e) {
            throw e;
        } catch (Exception e) {
            throw new JfaException(ErrorCode.E_INTERNAL, "执行命令失败: " + cmd + " — " + e.getMessage(), e);
        }
    }

    private static boolean waitFor(Process p, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                p.exitValue();
                return true;
            } catch (IllegalThreadStateException e) {
                Thread.sleep(50L);
            }
        }
        return false;
    }

    private static final class Gobbler extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        private Gobbler(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] b = new byte[4096];
            try {
                int n;
                while ((n = in.read(b)) >= 0) {
                    buf.write(b, 0, n);
                }
            } catch (IOException ignored) {
                // process ended
            }
        }

        String text() {
            return new String(buf.toByteArray(), Charset.defaultCharset());
        }
    }

    public static File resolveExe(File maybeLink) {
        if (maybeLink == null) {
            return null;
        }
        try {
            return maybeLink.getCanonicalFile();
        } catch (IOException e) {
            return maybeLink;
        }
    }
}
