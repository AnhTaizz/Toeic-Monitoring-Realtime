package vn.edu.toeic.client.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** ProcessHandle production source; không đọc commandLine/arguments. */
public final class ProcessHandleSnapshotSource implements ProcessSnapshotSource {
    private final Supplier<Stream<ProcessHandle>> processes;
    public ProcessHandleSnapshotSource() { this(ProcessHandle::allProcesses); }
    ProcessHandleSnapshotSource(Supplier<Stream<ProcessHandle>> processes) { this.processes = processes; }

    @Override public List<ProcessReading> scan() {
        try (Stream<ProcessHandle> stream = processes.get()) {
            return stream.map(ProcessHandleSnapshotSource::read).toList();
        }
    }

    private static ProcessReading read(ProcessHandle handle) {
        long pid = handle.pid();
        try {
            ProcessHandle.Info info = handle.info();
            String name = filename(safe(info::command).orElse(null));
            Instant start = safe(info::startInstant).orElse(null);
            boolean userAvailable = safe(info::user).filter(value -> !value.isBlank()).isPresent();
            return new ProcessReading(pid, name, start, userAvailable);
        } catch (RuntimeException ignored) {
            return new ProcessReading(pid, null, null, false);
        }
    }

    private static <T> Optional<T> safe(Supplier<Optional<T>> read) {
        try { return read.get(); } catch (RuntimeException ignored) { return Optional.empty(); }
    }

    static String filename(String command) {
        if (command == null || command.isBlank()) return null;
        int separator = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        String name = command.substring(separator + 1);
        return name.isBlank() ? null : name;
    }
}
