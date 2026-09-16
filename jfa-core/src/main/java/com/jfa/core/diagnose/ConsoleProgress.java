package com.jfa.core.diagnose;

import java.io.PrintStream;

/**
 * Default-on step progress for {@code diagnose}/{@code analyze}.
 * Mid-run lines go to stderr so JSON stdout stays parseable; {@code --quiet}
 * suppresses them. {@code --verbose} may add extra detail but is not required
 * to see the main steps.
 */
public final class ConsoleProgress {
    public static final ConsoleProgress DISABLED = new ConsoleProgress(null, false, false);

    private static final String PREFIX = "[JFA] ";

    private final PrintStream out;
    private final boolean enabled;
    private final boolean verbose;

    public ConsoleProgress(PrintStream out, boolean enabled, boolean verbose) {
        this.out = out;
        this.enabled = enabled && out != null;
        this.verbose = verbose && this.enabled;
    }

    public static ConsoleProgress from(DiagnoseRequest req) {
        if (req == null) {
            return new ConsoleProgress(System.err, true, false);
        }
        PrintStream dest = req.getProgressStream() != null ? req.getProgressStream() : System.err;
        return new ConsoleProgress(dest, !req.isQuiet(), req.isVerbose());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void step(String message) {
        emit(message, false);
    }

    public void detail(String message) {
        emit(message, true);
    }

    private void emit(String message, boolean detailOnly) {
        if (message == null || !enabled) {
            return;
        }
        if (detailOnly && !verbose) {
            return;
        }
        out.println(PREFIX + message);
        out.flush();
    }

    /**
     * Human-readable duration for wait / sample messages (JDK 8, no {@code Duration}).
     */
    public static String formatDuration(long ms) {
        if (ms <= 0L) {
            return "0s";
        }
        if (ms % 60_000L == 0L) {
            return (ms / 60_000L) + "m";
        }
        if (ms % 1000L == 0L) {
            return (ms / 1000L) + "s";
        }
        if (ms >= 60_000L) {
            long minutes = ms / 60_000L;
            long seconds = (ms % 60_000L) / 1000L;
            if (seconds == 0L) {
                return minutes + "m";
            }
            return minutes + "m" + seconds + "s";
        }
        return ms + "ms";
    }
}
