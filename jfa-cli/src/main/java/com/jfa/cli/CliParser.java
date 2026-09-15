package com.jfa.cli;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CliParser {
    public final Map<String, String> options = new LinkedHashMap<String, String>();
    public final List<String> flags = new ArrayList<String>();
    public final List<String> positionals = new ArrayList<String>();

    public static CliParser parse(String[] args) {
        CliParser p = new CliParser();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--".equals(a)) {
                for (int j = i + 1; j < args.length; j++) {
                    p.positionals.add(args[j]);
                }
                break;
            }
            if (a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq > 0) {
                    p.options.put(a.substring(2, eq), a.substring(eq + 1));
                    continue;
                }
                String key = a.substring(2);
                if (isFlag(key)) {
                    p.flags.add(key);
                    p.options.put(key, "true");
                    continue;
                }
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    p.flags.add(key);
                    p.options.put(key, "true");
                    continue;
                }
                p.options.put(key, args[++i]);
            } else if (a.startsWith("-") && a.length() == 2) {
                throw new JfaException(ErrorCode.E_USAGE, "请使用长选项，例如 --pid。收到: " + a);
            } else {
                p.positionals.add(a);
            }
        }
        return p;
    }

    private static boolean isFlag(String key) {
        return "confirm".equals(key) || "force".equals(key) || "verbose".equals(key)
                || "dry-run".equals(key) || "help".equals(key) || "version".equals(key);
    }

    public String opt(String key) {
        return options.get(key);
    }

    public String opt(String key, String dflt) {
        String v = options.get(key);
        return v == null ? dflt : v;
    }

    public boolean flag(String key) {
        return flags.contains(key) || "true".equalsIgnoreCase(options.get(key));
    }

    public Long longOpt(String key) {
        String v = options.get(key);
        if (v == null || v.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            throw new JfaException(ErrorCode.E_USAGE, "--" + key + " 必须是数字: " + v);
        }
    }

    public String command() {
        return positionals.isEmpty() ? null : positionals.get(0);
    }

    public String positional(int i) {
        return i < positionals.size() ? positionals.get(i) : null;
    }
}
