package vn.edu.toeic.monitoring;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ProcessHandleProbe {
    private ProcessHandleProbe() {
    }

    public static void main(String[] args) {
        int limit = parseLimit(args);
        List<ProcessObservation> observations = ProcessHandle.allProcesses()
                .map(ProcessObservation::from)
                .sorted(Comparator.comparingLong(ProcessObservation::pid))
                .limit(limit)
                .toList();

        System.out.println("pid,command,startInstant,user,missingMetadata");
        observations.forEach(observation -> System.out.printf(
                "%d,%s,%s,%s,%s%n",
                observation.pid(),
                csv(observation.command().map(ProcessHandleProbe::fileName).orElse("<UNREADABLE>")),
                csv(observation.startInstant().map(Object::toString).orElse("<UNREADABLE>")),
                csv(observation.user().isPresent() ? "<AVAILABLE>" : "<UNREADABLE>"),
                observation.hasMissingMetadata()));

        long missingCommand = observations.stream().filter(item -> item.command().isEmpty()).count();
        long missingStart = observations.stream().filter(item -> item.startInstant().isEmpty()).count();
        long missingUser = observations.stream().filter(item -> item.user().isEmpty()).count();
        System.out.printf(
                "SUMMARY total=%d missingCommand=%d missingStartInstant=%d missingUser=%d%n",
                observations.size(), missingCommand, missingStart, missingUser);
    }

    private static int parseLimit(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--limit=")) {
                int value = Integer.parseInt(arg.substring("--limit=".length()));
                if (value < 1) {
                    throw new IllegalArgumentException("limit phải lớn hơn 0");
                }
                return value;
            }
        }
        return 200;
    }

    private static String fileName(String command) {
        try {
            Path path = Path.of(command);
            Path fileName = path.getFileName();
            return fileName == null ? command : fileName.toString();
        } catch (RuntimeException exception) {
            return command;
        }
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
