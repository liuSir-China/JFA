package com.jfa.core.diagnose;

import com.jfa.common.config.JfaConfig;
import com.jfa.common.time.TimeSupport;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnose/analyze output layout: {@code reportfile/pid_<pid>/<yyyyMMdd-HHmmss>/}.
 */
public final class RunLayout {
    private static final Pattern PID_DIR = Pattern.compile("(?i)(?:^|[/\\\\])pid[_-](\\d+)(?:[/\\\\]|$)");
    private static final Pattern JAVA_PID_HPROF = Pattern.compile("(?i)java_pid(\\d+)\\.hprof$");
    private static final Pattern PID_TOKEN = Pattern.compile("(?i)(?:^|[^0-9])pid[_-]?(\\d+)(?:[^0-9]|$)");

    private RunLayout() {
    }

    public static File prepareRunDir(JfaConfig cfg, String pidKey, File outOverride) {
        if (outOverride != null) {
            FileSupport.mkdirs(outOverride);
            return outOverride.getAbsoluteFile();
        }
        File root = cfg.getReportfileRoot();
        FileSupport.mkdirs(root);
        File pidDir = new File(root, pidKey);
        if (cfg.isCoverFile() && pidDir.exists()) {
            FileSupport.deleteTree(pidDir);
        }
        File runDir = new File(pidDir, TimeSupport.nowFileStamp());
        FileSupport.mkdirs(runDir);
        FileSupport.mkdirs(new File(runDir, "heap"));
        FileSupport.mkdirs(new File(runDir, "threads"));
        FileSupport.mkdirs(new File(runDir, "gc"));
        FileSupport.mkdirs(new File(runDir, "samples"));
        return runDir.getAbsoluteFile();
    }

    public static String pidKey(Long pid, File evidenceDir, File hprof, File threadDump) {
        if (pid != null) {
            return "pid_" + pid;
        }
        Long parsed = parsePid(evidenceDir, hprof, threadDump);
        if (parsed != null) {
            return "pid_" + parsed;
        }
        return "pid_offline";
    }

    public static Long parsePid(File evidenceDir, File hprof, File threadDump) {
        Long fromDir = parsePidFromPath(evidenceDir);
        if (fromDir != null) {
            return fromDir;
        }
        Long fromHprof = parsePidFromPath(hprof);
        if (fromHprof != null) {
            return fromHprof;
        }
        return parsePidFromPath(threadDump);
    }

    static Long parsePidFromPath(File file) {
        if (file == null) {
            return null;
        }
        String path = file.getAbsolutePath().replace('\\', '/');
        Matcher javaPid = JAVA_PID_HPROF.matcher(file.getName());
        if (javaPid.find()) {
            return Long.valueOf(javaPid.group(1));
        }
        Matcher dir = PID_DIR.matcher(path);
        if (dir.find()) {
            return Long.valueOf(dir.group(1));
        }
        Matcher token = PID_TOKEN.matcher(file.getName());
        if (token.find()) {
            return Long.valueOf(token.group(1));
        }
        return null;
    }
}
