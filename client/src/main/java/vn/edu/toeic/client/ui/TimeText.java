package vn.edu.toeic.client.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** One place for how times are shown to people: the computer's own time zone, never a raw Instant. */
public final class TimeText {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeText() { }

    /** "14:31:52 26/10/2026" in the system zone; empty when the server gave no time. */
    public static String dateTime(Instant instant) { return format(instant, DATE_TIME, ZoneId.systemDefault()); }

    /** "14:31:52" in the system zone; empty when the server gave no time. */
    public static String time(Instant instant) { return format(instant, TIME, ZoneId.systemDefault()); }

    static String format(Instant instant, DateTimeFormatter pattern, ZoneId zone) {
        return instant == null ? "" : pattern.format(instant.atZone(zone));
    }
}
