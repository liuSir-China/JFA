package com.jfa.core.discovery;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.core.io.FileSupport;
import com.jfa.core.proc.JdkToolLocator;
import com.jfa.core.proc.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaProcessDiscovery {
    private final JdkToolLocator tools = new JdkToolLocator();
    private final ProcessRunner runner = new ProcessRunner();

    public List<JavaProcessInfo> discover(String userFilter, String mainKeyword) {
        File proc = new File("/proc");
        if (!proc.isDirectory()) {
            throw new JfaException(ErrorCode.E_PERM_DISCOVERY,
                    "无法读取 /proc，请在 Linux 上以对目标进程有权限的用户执行。");
        }
        File[] pids = proc.listFiles();
        if (pids == null) {
            throw new JfaException(ErrorCode.E_PERM_DISCOVERY,
                    "无权枚举 /proc。请切换到有权限的用户后重试。");
        }
        List<JavaProcessInfo> list = new ArrayList<JavaProcessInfo>();
        for (File dir : pids) {
            if (!dir.isDirectory()) {
                continue;
            }
            long pid;
            try {
                pid = Long.parseLong(dir.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            JavaProcessInfo info = readIfJava(pid, dir);
            if (info == null) {
                continue;
            }
            if (userFilter != null && !userFilter.isEmpty()
                    && (info.getUser() == null || !info.getUser().equals(userFilter))) {
                continue;
            }
            if (mainKeyword != null && !mainKeyword.isEmpty()) {
                String hay = (info.getMainClassOrJar() == null ? "" : info.getMainClassOrJar())
                        + " " + (info.getJavaCmdSummary() == null ? "" : info.getJavaCmdSummary());
                if (hay.indexOf(mainKeyword) < 0) {
                    continue;
                }
            }
            list.add(info);
        }
        Collections.sort(list, new Comparator<JavaProcessInfo>() {
            @Override
            public int compare(JavaProcessInfo a, JavaProcessInfo b) {
                return Long.compare(a.getPid(), b.getPid());
            }
        });
        return list;
    }

    public JavaProcessInfo requirePid(long pid) {
        File dir = new File("/proc/" + pid);
        if (!dir.isDirectory()) {
            throw new JfaException(ErrorCode.E_PID_NOT_FOUND, "pid 不存在: " + pid + "。检查进程或改用 --evidence-dir。");
        }
        JavaProcessInfo info = readIfJava(pid, dir);
        if (info == null) {
            JavaProcessInfo any = readAny(pid, dir);
            if (any == null) {
                throw new JfaException(ErrorCode.E_PID_NOT_FOUND, "pid 不存在或无法读取: " + pid);
            }
            return any;
        }
        return info;
    }

    public boolean pidExists(long pid) {
        return new File("/proc/" + pid).isDirectory();
    }

    private JavaProcessInfo readIfJava(long pid, File dir) {
        String cmdline = readCmdline(new File(dir, "cmdline"));
        if (cmdline == null) {
            return null;
        }
        File exe = ProcessRunner.resolveExe(new File(dir, "exe"));
        String exeName = exe == null ? "" : exe.getName();
        boolean java = "java".equals(exeName)
                || cmdline.startsWith("java")
                || cmdline.contains("/java ")
                || cmdline.contains("/java\n")
                || looksLikeJavaCmd(cmdline);
        if (!java) {
            return null;
        }
        return fill(pid, dir, cmdline, exe);
    }

    private JavaProcessInfo readAny(long pid, File dir) {
        String cmdline = readCmdline(new File(dir, "cmdline"));
        if (cmdline == null && !dir.isDirectory()) {
            return null;
        }
        File exe = ProcessRunner.resolveExe(new File(dir, "exe"));
        return fill(pid, dir, cmdline == null ? "" : cmdline, exe);
    }

    private JavaProcessInfo fill(long pid, File dir, String cmdline, File exe) {
        JavaProcessInfo info = new JavaProcessInfo();
        info.setPid(pid);
        info.setCommandLine(cmdline);
        info.setUser(readUser(dir));
        info.setMainClassOrJar(parseMain(cmdline));
        info.setJavaCmdSummary(summarize(cmdline));
        if (exe != null && exe.getParentFile() != null && exe.getParentFile().getParentFile() != null) {
            info.setJavaHome(exe.getParentFile().getParentFile().getAbsolutePath());
        }
        File cwd = ProcessRunner.resolveExe(new File(dir, "cwd"));
        if (cwd != null && cwd.exists()) {
            info.setCwd(cwd.getAbsolutePath());
        }
        return info;
    }

    private static boolean looksLikeJavaCmd(String cmdline) {
        String[] parts = cmdline.split("\n");
        if (parts.length == 0) {
            return false;
        }
        String bin = parts[0];
        return bin.endsWith("/java") || bin.equals("java") || bin.endsWith("\\java");
    }

    public static String parseMain(String cmdline) {
        if (cmdline == null || cmdline.isEmpty()) {
            return "unknown";
        }
        String[] args = cmdline.split("\n");
        boolean nextJar = false;
        boolean skipNext = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (nextJar) {
                return a;
            }
            if ("-jar".equals(a)) {
                nextJar = true;
                continue;
            }
            if ("-cp".equals(a) || "-classpath".equals(a) || "--class-path".equals(a)
                    || "-modulepath".equals(a) || "--module-path".equals(a)
                    || "-p".equals(a) || "--module".equals(a) || "-m".equals(a)) {
                skipNext = true;
                continue;
            }
            if (a.startsWith("-")) {
                continue;
            }
            return a;
        }
        return args[0];
    }

    private static String summarize(String cmdline) {
        if (cmdline == null) {
            return "";
        }
        String one = cmdline.replace('\n', ' ');
        if (one.length() > 180) {
            return one.substring(0, 177) + "...";
        }
        return one;
    }

    private static String readCmdline(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            if (raw.length == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == 0) {
                    sb.append('\n');
                } else {
                    sb.append((char) (raw[i] & 0xff));
                }
            }
            String s = sb.toString();
            if (s.endsWith("\n")) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        } catch (IOException e) {
            return null;
        }
    }

    private String readUser(File dir) {
        String status = FileSupport.readMaybe(new File(dir, "status"));
        if (status == null) {
            return "unknown";
        }
        String uid = null;
        String[] lines = status.split("\n");
        for (String line : lines) {
            if (line.startsWith("Uid:")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    uid = parts[1];
                }
                break;
            }
        }
        if (uid == null) {
            return "unknown";
        }
        return lookupUser(uid);
    }

    private String lookupUser(String uid) {
        String passwd = FileSupport.readMaybe(new File("/etc/passwd"));
        if (passwd == null) {
            return uid;
        }
        String[] lines = passwd.split("\n");
        for (String line : lines) {
            String[] p = line.split(":");
            if (p.length >= 3 && uid.equals(p[2])) {
                return p[0];
            }
        }
        return uid;
    }

    public Map<String, String> readEnviron(long pid) {
        Map<String, String> map = new HashMap<String, String>();
        File f = new File("/proc/" + pid + "/environ");
        if (!f.isFile()) {
            return map;
        }
        try {
            byte[] raw = Files.readAllBytes(f.toPath());
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == 0) {
                    addEnv(map, cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append((char) (raw[i] & 0xff));
                }
            }
            if (cur.length() > 0) {
                addEnv(map, cur.toString());
            }
        } catch (IOException ignored) {
            // permission
        }
        return map;
    }

    private static void addEnv(Map<String, String> map, String entry) {
        int eq = entry.indexOf('=');
        if (eq > 0) {
            map.put(entry.substring(0, eq), entry.substring(eq + 1));
        }
    }

    public String commandLineOneLine(long pid) {
        JavaProcessInfo info = requirePid(pid);
        return info.getCommandLine() == null ? "" : info.getCommandLine().replace('\n', ' ');
    }
}
