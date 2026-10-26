package vn.edu.toeic.client;

/**
 * Release default shows only the ACCOUNT_PIN flow: Admin accounts, Proctor PIN room, Candidate PIN entry.
 * Legacy entries (room code + personal PIN, previously assigned V1 sessions, old V2/V1 proctor tabs) keep
 * their code and backend and are shown again with {@code -Dtoeic.ui.legacy=true} or {@code TOEIC_UI_LEGACY=true}.
 * This only hides navigation; it is not an authorization boundary.
 */
public final class LegacyUi {
    private LegacyUi() {}

    public static boolean enabled() {
        String property = System.getProperty("toeic.ui.legacy");
        if (property != null && !property.isBlank()) return Boolean.parseBoolean(property.trim());
        return Boolean.parseBoolean(System.getenv("TOEIC_UI_LEGACY"));
    }
}
