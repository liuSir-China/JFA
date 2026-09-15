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
        fillMore(400);
    }

    public static void fillMore(int targetSize) {
        int start = delegate.size();
        for (int i = start; i < targetSize; i++) {
            delegate.put(i, new byte[16 * 1024]);
        }
    }
}
