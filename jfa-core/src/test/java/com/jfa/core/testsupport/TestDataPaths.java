package com.jfa.core.testsupport;

import java.io.File;

public final class TestDataPaths {
    private TestDataPaths() {
    }

    public static File root() {
        String p = System.getProperty("jfa.testdata");
        if (p != null && new File(p).isDirectory()) {
            return new File(p).getAbsoluteFile();
        }
        File[] candidates = new File[] {
                new File("testdata"),
                new File("../testdata"),
                new File("../../testdata"),
                new File("/workspace/testdata")
        };
        for (File c : candidates) {
            if (c.isDirectory()) {
                try {
                    return c.getCanonicalFile();
                } catch (Exception e) {
                    return c.getAbsoluteFile();
                }
            }
        }
        throw new IllegalStateException("testdata/ not found");
    }

    public static File file(String rel) {
        return new File(root(), rel);
    }
}
