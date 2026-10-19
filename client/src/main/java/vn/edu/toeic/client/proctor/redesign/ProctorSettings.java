package vn.edu.toeic.client.proctor.redesign;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Display-only preferences of the proctor view. Deliberately holds no server URL, token, password,
 * attempt or candidate data: the file is safe to leave on a shared machine.
 *
 * <p>Alert sound is not offered: there is no reviewed, safe playback path for the proctor machine yet,
 * so {@code sound} is always false and the switch is disabled in the UI.
 */
record ProctorSettings(boolean compact, boolean largeFont, boolean notifications, boolean navBadge) {
    static final ProctorSettings DEFAULTS = new ProctorSettings(false, false, true, true);

    /** Persistence boundary; tests use an in-memory store. Both methods run off the FX thread. */
    interface Store {
        /** {@code null} when nothing has been saved yet (callers then use {@link #DEFAULTS}). */
        ProctorSettings load() throws IOException;
        void save(ProctorSettings settings) throws IOException;
        /** Human-readable place where settings live, for the confirmation text. */
        String location();
    }

    /** Small properties file; only the four whitelisted keys are read or written. */
    static final class FileStore implements Store {
        private static final int MAX_BYTES = 4096;
        private final Path file;
        FileStore(Path file) { this.file = file; }

        /** {@code toeic.proctor.settingsFile} overrides; default is next to the other per-user app data. */
        static FileStore standard() {
            String override = System.getProperty("toeic.proctor.settingsFile");
            if (override != null && !override.isBlank()) return new FileStore(Path.of(override));
            String local = System.getenv("LOCALAPPDATA");
            Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), ".toeic-monitor")
                    : Path.of(local, "TOEIC Monitor");
            return new FileStore(base.resolve("proctor-ui.properties"));
        }

        @Override public ProctorSettings load() throws IOException {
            if (!Files.exists(file)) return null;
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("File cài đặt quá lớn");
                properties.load(new java.io.ByteArrayInputStream(bytes));
            }
            return new ProctorSettings(flag(properties, "table.compact", DEFAULTS.compact()),
                    flag(properties, "font.large", DEFAULTS.largeFont()),
                    flag(properties, "notifications.ui", DEFAULTS.notifications()),
                    flag(properties, "nav.badge", DEFAULTS.navBadge()));
        }

        @Override public void save(ProctorSettings settings) throws IOException {
            Properties properties = new Properties();
            properties.setProperty("table.compact", Boolean.toString(settings.compact()));
            properties.setProperty("font.large", Boolean.toString(settings.largeFont()));
            properties.setProperty("notifications.ui", Boolean.toString(settings.notifications()));
            properties.setProperty("nav.badge", Boolean.toString(settings.navBadge()));
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "proctor-ui", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    properties.store(output, "TOEIC Proctor display settings (no credentials)");
                }
                try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        @Override public String location() { return file.toString(); }

        private static boolean flag(Properties properties, String key, boolean fallback) {
            String value = properties.getProperty(key);
            if ("true".equals(value)) return true;
            if ("false".equals(value)) return false;
            return fallback;
        }
    }
}
