package com.jfa.core.proc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates jcmd / jstack / jmap / jstat for a target JVM on Linux.
 */
public class JdkToolLocator {

    public File findTool(String toolName, String preferredJavaHome) {
        List<File> candidates = new ArrayList<File>();
        addBin(candidates, preferredJavaHome, toolName);
        addBin(candidates, System.getenv("JAVA_HOME"), toolName);
        addBin(candidates, System.getProperty("java.home"), toolName);
        File javaHomeProp = new File(System.getProperty("java.home"));
        if (javaHomeProp.getName().equals("jre") && javaHomeProp.getParentFile() != null) {
            addBin(candidates, javaHomeProp.getParent(), toolName);
        }
        String path = System.getenv("PATH");
        if (path != null) {
            String[] parts = path.split(File.pathSeparator);
            for (String part : parts) {
                File f = new File(part, toolName);
                if (f.isFile() && f.canExecute()) {
                    candidates.add(f);
                }
            }
        }
        for (File c : candidates) {
            if (c != null && c.isFile() && c.canExecute()) {
                return c;
            }
        }
        return null;
    }

    public File inferJavaHomeFromJavaBinary(File javaBinary) {
        if (javaBinary == null) {
            return null;
        }
        File bin = javaBinary.getParentFile();
        if (bin == null) {
            return null;
        }
        File home = bin.getParentFile();
        return home;
    }

    private static void addBin(List<File> candidates, String javaHome, String tool) {
        if (javaHome == null || javaHome.trim().isEmpty()) {
            return;
        }
        File home = new File(javaHome);
        candidates.add(new File(new File(home, "bin"), tool));
        File parent = home.getParentFile();
        if ("jre".equals(home.getName()) && parent != null) {
            candidates.add(new File(new File(parent, "bin"), tool));
        }
    }
}
