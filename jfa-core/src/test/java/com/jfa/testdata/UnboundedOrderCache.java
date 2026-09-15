package com.jfa.testdata;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixture holder used by E3 hprof tests (unbounded cache pattern).
 */
public final class UnboundedOrderCache {
    public static final ConcurrentHashMap<Integer, byte[]> delegate = new ConcurrentHashMap<Integer, byte[]>();

    private UnboundedOrderCache() {
    }

    public static void fill() {
        if (delegate.size() >= 400) {
            return;
        }
        for (int i = 0; i < 400; i++) {
            delegate.put(i, new byte[16 * 1024]);
        }
    }
}
