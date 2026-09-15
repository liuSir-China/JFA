package com.jfa.core.testsupport;

import com.jfa.testdata.UnboundedOrderCache;
import com.sun.management.HotSpotDiagnosticMXBean;

import java.io.File;
import java.lang.management.ManagementFactory;

public final class HeapDumpSupport {
    private HeapDumpSupport() {
    }

    public static File leakyDump(File dest) throws Exception {
        UnboundedOrderCache.fill();
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (dest.exists() && !dest.delete()) {
            throw new IllegalStateException("cannot replace " + dest);
        }
        HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        bean.dumpHeap(dest.getAbsolutePath(), true);
        return dest;
    }
}
