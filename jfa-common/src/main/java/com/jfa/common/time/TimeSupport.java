package com.jfa.common.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeSupport {
    private static final DateTimeFormatter FILE =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private TimeSupport() {
    }

    public static String nowIso() {
        return Instant.now().toString();
    }

    public static String nowFileStamp() {
        return FILE.format(Instant.now());
    }
}
