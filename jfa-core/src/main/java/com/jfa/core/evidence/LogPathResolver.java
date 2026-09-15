package com.jfa.core.evidence;

import com.jfa.common.model.JavaProcessInfo;
import com.jfa.common.model.ServiceMeta;
import com.jfa.core.io.FileSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves an application log path from CLI, the service registry, or JVM command line.
 */
public final class LogPathResolver {
    private LogPathResolver() {
    }

    public static File resolve(File cliAppLog, ServiceMeta meta, JavaProcessInfo process) {
        if (cliAppLog != null && cliAppLog.isFile()) {
            return cliAppLog.getAbsoluteFile();
        }
        if (meta != null && meta.getPaths() != null && meta.getPaths().getAppLog() != null) {
            File f = new File(meta.getPaths().getAppLog());
            if (f.isFile()) {
                return f.getAbsoluteFile();
            }
        }
        if (process == null) {
            return null;
        }
        String cmd = process.getCommandLine();
        String cwd = process.getCwd();
        File fromFlag = fromJvmFlags(cmd, cwd);
        if (fromFlag != null) {
            return fromFlag;
        }
        return null;
    }

    static File fromJvmFlags(String commandLine, String cwd) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return null;
        }
        String[] tokens = JvmEvidenceLocator.tokenize(commandLine);
        String loggingFile = null;
        String loggingPath = null;
        String catalinaBase = null;
        for (String t : tokens) {
            loggingFile = first(loggingFile, prop(t, "-Dlogging.file="));
            loggingFile = first(loggingFile, prop(t, "-Dlogging.file.name="));
            loggingFile = first(loggingFile, prop(t, "-Dlog.file="));
            loggingFile = first(loggingFile, prop(t, "-DLOG_FILE="));
            loggingPath = first(loggingPath, prop(t, "-Dlogging.path="));
            loggingPath = first(loggingPath, prop(t, "-DLOG_PATH="));
            catalinaBase = first(catalinaBase, prop(t, "-Dcatalina.base="));
            catalinaBase = first(catalinaBase, prop(t, "-Dcatalina.home="));
        }
        if (loggingFile != null) {
            File f = JvmEvidenceLocator.resolve(loggingFile, cwd);
            if (f.isFile()) {
                return f.getAbsoluteFile();
            }
        }
        if (loggingPath != null) {
            File dir = JvmEvidenceLocator.resolve(loggingPath, cwd);
            if (dir.isDirectory()) {
                File spring = new File(dir, "spring.log");
                if (spring.isFile()) {
                    return spring.getAbsoluteFile();
                }
                File newest = FileSupport.newest(FileSupport.findBySuffix(dir, ".log"));
                if (newest != null && newest.isFile()) {
                    return newest.getAbsoluteFile();
                }
            }
        }
        if (catalinaBase != null) {
            File out = new File(new File(JvmEvidenceLocator.resolve(catalinaBase, cwd), "logs"), "catalina.out");
            if (out.isFile()) {
                return out.getAbsoluteFile();
            }
        }
        return null;
    }

    private static String first(String cur, String next) {
        return cur != null ? cur : next;
    }

    private static String prop(String token, String prefix) {
        if (token != null && token.startsWith(prefix) && token.length() > prefix.length()) {
            return JvmEvidenceLocator.unquote(token.substring(prefix.length()));
        }
        return null;
    }

    public static List<File> extraCandidates(ServiceMeta meta, File evidenceDir) {
        List<File> out = new ArrayList<File>();
        if (evidenceDir != null) {
            File logs = new File(evidenceDir, "logs");
            File newest = FileSupport.newest(FileSupport.findBySuffix(logs, ".log"));
            if (newest != null) {
                out.add(newest);
            }
        }
        if (meta != null && meta.getPaths() != null && meta.getPaths().getAppLog() != null) {
            File f = new File(meta.getPaths().getAppLog());
            if (f.isFile()) {
                out.add(f);
            }
        }
        return out;
    }
}
