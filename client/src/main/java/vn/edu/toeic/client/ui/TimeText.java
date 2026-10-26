package vn.edu.toeic.client.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One place for how times are shown to people: Vietnam time (GMT+7), never a raw Instant. The launchers start
 * Java with user.timezone=UTC, so the computer's zone cannot be used; -Dtoeic.dashboard.timezone overrides it.
 */
public final class TimeText {
    public static final ZoneId ZONE = ZoneId.of(System.getProperty("toeic.dashboard.timezone", "Asia/Ho_Chi_Minh"));
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeText() { }

    /** "14:31:52 26/10/2026" in Vietnam time; empty when the server gave no time. */
    public static String dateTime(Instant instant) { return format(instant, DATE_TIME, ZONE); }

    /** "14:31:52" in Vietnam time; empty when the server gave no time. */
    public static String time(Instant instant) { return format(instant, TIME, ZONE); }

    static String format(Instant instant, DateTimeFormatter pattern, ZoneId zone) {
        return instant == null ? "" : pattern.format(instant.atZone(zone));
    }
}
