package com.jfa.core.diagnose;

import com.jfa.common.io.ConsoleLayout;

import java.io.PrintStream;

/** * Default-on step progress for {@code diagnose}/{@code analyze}.
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
        ConsoleLayout.ensureLead(out);
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
        if (ms >= 60_000L) {
            long minutes = ms / 60_000L;
            long rem = ms % 60_000L;
            if (rem == 0L) {
                return minutes + "m";
            }
            if (rem % 1000L == 0L) {
                return minutes + "m" + (rem / 1000L) + "s";
            }
            return minutes + "m" + rem + "ms";
        }
        if (ms % 1000L == 0L) {
            return (ms / 1000L) + "s";
        }
        return ms + "ms";
    }
}
