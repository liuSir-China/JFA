package com.jfa.console;

import com.jfa.common.model.JavaProcessInfo;
import com.jfa.core.discovery.JavaProcessDiscovery;

import java.util.ArrayList;
import java.util.List;

/**
 * Live Java process list for the web console, excluding this JFA JVM.
 */
public final class ConsoleProcessSource {
    public interface Source {
        List<JavaProcessInfo> discover();

        JavaProcessInfo requirePid(long pid);
    }

    private ConsoleProcessSource() {
    }

    public static Source live() {
        return new Live();
    }

    static boolean looksLikeThisConsole(JavaProcessInfo info, long selfPid) {
        if (info == null) {
            return true;
        }
        if (info.getPid() == selfPid) {
            return true;
        }
        String hay = safe(info.getMainClassOrJar()) + " " + safe(info.getJavaCmdSummary())
                + " " + safe(info.getCommandLine());
        return hay.indexOf("com.jfa.cli.JfaMain") >= 0
                || hay.indexOf("/jfa.jar") >= 0
                || hay.indexOf("\\jfa.jar") >= 0
                || hay.indexOf("jfa-cli-") >= 0 && hay.indexOf("com.jfa") >= 0;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static final class Live implements Source {
        private final JavaProcessDiscovery discovery = new JavaProcessDiscovery();

        @Override
        public List<JavaProcessInfo> discover() {
            List<JavaProcessInfo> all = discovery.discover(null, null);
            long self = ConsolePidFile.currentPid();
            List<JavaProcessInfo> out = new ArrayList<JavaProcessInfo>();
            for (int i = 0; i < all.size(); i++) {
                JavaProcessInfo p = all.get(i);
                if (looksLikeThisConsole(p, self)) {
                    continue;
                }
                out.add(p);
            }
            return out;
        }

        @Override
        public JavaProcessInfo requirePid(long pid) {
            return discovery.requirePid(pid);
        }
    }
}
