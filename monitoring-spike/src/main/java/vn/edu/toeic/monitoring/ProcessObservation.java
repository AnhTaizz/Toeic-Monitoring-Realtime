package vn.edu.toeic.monitoring;

import java.time.Instant;
import java.util.Optional;

public record ProcessObservation(
        long pid,
        Optional<String> command,
        Optional<Instant> startInstant,
        Optional<String> user) {

    public static ProcessObservation from(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        return new ProcessObservation(process.pid(), info.command(), info.startInstant(), info.user());
    }

    public boolean hasMissingMetadata() {
        return command.isEmpty() || startInstant.isEmpty() || user.isEmpty();
    }
}
