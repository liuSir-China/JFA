package com.jfa.core.io;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public final class FileSupport {
    private FileSupport() {
    }

    public static String readUtf8(File file) {
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JfaException(ErrorCode.E_IO_EVIDENCE, "无法读取文件: " + file, e);
        }
    }

    public static void writeUtf8(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new JfaException(ErrorCode.E_IO_EVIDENCE, "无法创建目录: " + parent);
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (JfaException e) {
            throw e;
        } catch (IOException e) {
            throw new JfaException(ErrorCode.E_IO_EVIDENCE, "无法写入文件: " + file, e);
        }
    }

    public static String readMaybe(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            return new String(raw, Charset.defaultCharset());
        } catch (IOException e) {
            return null;
        }
    }

    public static List<File> globFiles(File root, String glob) {
        List<File> out = new ArrayList<File>();
        if (glob == null || glob.trim().isEmpty()) {
            return out;
        }
        File asFile = new File(glob);
        if (asFile.isFile()) {
            out.add(asFile.getAbsoluteFile());
            return out;
        }
        if (glob.indexOf('*') < 0 && glob.indexOf('?') < 0) {
            if (asFile.isDirectory()) {
                collectDir(asFile, out);
            }
            return out;
        }
        File base = asFile.getParentFile();
        String pattern = asFile.getName();
        if (base == null || !base.isDirectory()) {
            if (root != null && root.isDirectory()) {
                base = root;
                pattern = glob;
            } else {
                return out;
            }
        }
        File[] children = base.listFiles();
        if (children == null) {
            return out;
        }
        for (File child : children) {
            if (child.isFile() && wildcard(child.getName(), pattern)) {
                out.add(child.getAbsoluteFile());
            }
        }
        return out;
    }

    public static List<File> findBySuffix(File dir, String suffix) {
        List<File> out = new ArrayList<File>();
        if (dir == null || !dir.isDirectory()) {
            return out;
        }
        collectBySuffix(dir, suffix.toLowerCase(), out);
        return out;
    }

    private static void collectDir(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile()) {
                out.add(child.getAbsoluteFile());
            }
        }
    }

    private static void collectBySuffix(File dir, String suffix, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectBySuffix(child, suffix, out);
            } else if (child.getName().toLowerCase().endsWith(suffix)) {
                out.add(child.getAbsoluteFile());
            }
        }
    }

    public static List<File> listFilesRecursive(File dir) {
        final List<File> out = new ArrayList<File>();
        if (dir == null || !dir.exists()) {
            return out;
        }
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    out.add(file.toFile());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new JfaException(ErrorCode.E_IO_EVIDENCE, "遍历目录失败: " + dir, e);
        }
        return out;
    }

    public static void mkdirs(File dir) {
        if (dir != null && !dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new JfaException(ErrorCode.E_IO_EVIDENCE, "无法创建目录: " + dir);
        }
    }

    public static boolean wildcard(String name, String pattern) {
        return match(name, pattern, 0, 0);
    }

    private static boolean match(String s, String p, int si, int pi) {
        if (pi == p.length()) {
            return si == s.length();
        }
        char c = p.charAt(pi);
        if (c == '*') {
            return match(s, p, si, pi + 1) || (si < s.length() && match(s, p, si + 1, pi));
        }
        if (si < s.length() && (c == '?' || c == s.charAt(si))) {
            return match(s, p, si + 1, pi + 1);
        }
        return false;
    }

    public static File newest(List<File> files) {
        File best = null;
        long t = Long.MIN_VALUE;
        for (File f : files) {
            long mt = f.lastModified();
            if (best == null || mt >= t) {
                best = f;
                t = mt;
            }
        }
        return best;
    }
}
