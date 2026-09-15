package com.jfa.common.time;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeSupport {
    private static final DateTimeFormatter FILE =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final Pattern LEADING_TS = Pattern.compile(
            "^\\[?(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?)");
    private static final DateTimeFormatter LOCAL_SPACE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SSSSSS]");
    private static final DateTimeFormatter LOCAL_T =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][.SSSSSS]");

    private TimeSupport() {
    }

    public static String nowIso() {
        return Instant.now().toString();
    }

    public static String nowFileStamp() {
        return FILE.format(Instant.now());
    }

    /**
     * Parse a duration used by CLI/config ({@code 15m}, {@code 90s}, {@code 1h}, {@code 500ms}).
     * A bare number is seconds.
     */
    public static long parseDurationMs(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new JfaException(ErrorCode.E_USAGE, "时长不能为空");
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        long mul = 1000L;
        if (v.endsWith("ms")) {
            mul = 1L;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("min")) {
            mul = 60L * 1000L;
            v = v.substring(0, v.length() - 3);
        } else if (v.endsWith("h")) {
            mul = 60L * 60L * 1000L;
            v = v.substring(0, v.length() - 1);
        } else if (v.endsWith("m")) {
            mul = 60L * 1000L;
            v = v.substring(0, v.length() - 1);
        } else if (v.endsWith("s")) {
            mul = 1000L;
            v = v.substring(0, v.length() - 1);
        }
        v = v.trim();
        try {
            return Long.parseLong(v) * mul;
        } catch (NumberFormatException e) {
            throw new JfaException(ErrorCode.E_USAGE, "无法解析时长: " + raw);
        }
    }

    /**
     * Best-effort parse of a timestamp at the start of an application log line.
     *
     * @return epoch millis, or null if the line has no recognized timestamp
     */
    public static Long parseLogLineTime(String line) {
        if (line == null) {
            return null;
        }
        Matcher m = LEADING_TS.matcher(line.trim());
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1).replace(',', '.');
        try {
            if (raw.endsWith("Z") || raw.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                String iso = raw;
                if (iso.matches(".*[+-]\\d{4}$")) {
                    iso = iso.substring(0, iso.length() - 2) + ":" + iso.substring(iso.length() - 2);
                }
                iso = iso.replace(' ', 'T');
                return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            }
            LocalDateTime ldt;
            try {
                ldt = LocalDateTime.parse(raw.replace(' ', 'T'));
            } catch (DateTimeParseException e) {
                try {
                    ldt = LocalDateTime.parse(raw, LOCAL_SPACE);
                } catch (DateTimeParseException e2) {
                    ldt = LocalDateTime.parse(raw, LOCAL_T);
                }
            }
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return OffsetDateTime.parse(raw.replace(' ', 'T')).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public static String formatIso(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC).toString();
    }
}
