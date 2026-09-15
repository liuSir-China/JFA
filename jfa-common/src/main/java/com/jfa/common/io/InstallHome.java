package com.jfa.common.io;

import java.io.File;
import java.net.URI;
import java.net.URL;

/**
 * Resolves the JFA install root (the directory that contains {@code bin/} and {@code conf/}).
 */
public final class InstallHome {
    private InstallHome() {
    }

    public static File detect(File configFile) {
        String prop = System.getProperty("jfa.install.home");
        if (prop != null && !prop.trim().isEmpty()) {
            return new File(prop.trim()).getAbsoluteFile();
        }
        File fromConfig = fromConfigFile(configFile);
        if (fromConfig != null) {
            return fromConfig;
        }
        File fromJar = fromCodeSource();
        if (fromJar != null) {
            return fromJar;
        }
        String userDir = System.getProperty("user.dir", ".");
        return new File(userDir).getAbsoluteFile();
    }

    static File fromConfigFile(File configFile) {
        if (configFile == null) {
            return null;
        }
        File confDir = configFile.getAbsoluteFile().getParentFile();
        if (confDir != null && "conf".equalsIgnoreCase(confDir.getName())) {
            File root = confDir.getParentFile();
            if (root != null) {
                return root;
            }
        }
        return null;
    }

    static File fromCodeSource() {
        try {
            URL loc = InstallHome.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            File f;
            try {
                f = new File(loc.toURI());
            } catch (Exception e) {
                try {
                    f = new File(new URI(loc.toString()));
                } catch (Exception e2) {
                    f = new File(loc.getPath());
                }
            }
            if (f.isFile()) {
                File lib = f.getParentFile();
                if (lib != null && "lib".equalsIgnoreCase(lib.getName()) && lib.getParentFile() != null) {
                    return lib.getParentFile().getAbsoluteFile();
                }
                return lib == null ? null : lib.getAbsoluteFile();
            }
            if (f.isDirectory()) {
                return f.getAbsoluteFile();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }
}
