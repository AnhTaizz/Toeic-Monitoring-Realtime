package vn.edu.toeic.monitoring.usb;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone CLI only. No production session, network, DB, JavaFX or native subscription. */
public final class UsbStorageProbe {
    record Options(CimSnapshotSource.Method method, int durationSeconds, int intervalMillis,
                   int queryTimeoutMillis, int maxLines, String machineAlias, String sourceSha, Path outputFile) {
        static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 == args.length || !args[i].startsWith("--") || values.putIfAbsent(args[i], args[i + 1]) != null)
                    throw new IllegalArgumentException();
            }
            if (!java.util.Set.of("--method", "--duration-seconds", "--interval-millis", "--query-timeout-millis",
                    "--max-lines", "--machine-alias", "--source-sha", "--output-file").containsAll(values.keySet())) throw new IllegalArgumentException();
            String alias = values.getOrDefault("--machine-alias", "WIN-PROBE");
            String sha = values.getOrDefault("--source-sha", "UNVERSIONED");
            if (!alias.matches("[A-Za-z0-9_.-]{1,32}") || !sha.matches("(?:[a-f0-9]{40}|UNVERSIONED)")) throw new IllegalArgumentException();
            CimSnapshotSource.Method method = switch (values.getOrDefault("--method", "msft-disk")) {
                case "msft-disk" -> CimSnapshotSource.Method.MSFT_DISK;
                case "win32-diskdrive" -> CimSnapshotSource.Method.WIN32_DISKDRIVE;
                default -> throw new IllegalArgumentException();
            };
            return new Options(method, number(values, "--duration-seconds", 0, 0, 600),
                    number(values, "--interval-millis", 1000, 250, 60000),
                    number(values, "--query-timeout-millis", 5000, 100, 10000),
                    number(values, "--max-lines", 1000, 4, 2000), alias, sha,
                    values.containsKey("--output-file") ? Path.of(values.get("--output-file")) : null);
        }
        private static int number(Map<String,String> values, String key, int fallback, int min, int max) {
            String raw = values.getOrDefault(key, Integer.toString(fallback));
            if (!raw.matches("[0-9]{1,6}")) throw new IllegalArgumentException();
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new IllegalArgumentException();
            return value;
        }
    }
    /** Allows exactly one final record and never more than maxLines; all strings are escaped. */
    static final class Output {
        private final PrintStream stream;
        private final int maximum;
        private final String runId;
        private int lines;
        private boolean limited, finished;
        Output(PrintStream stream, int maximum, String runId) {
            this.stream = stream; this.maximum = maximum; this.runId = runId;
        }
        synchronized void write(String kind, Object... pairs) {
            if (finished) return;
            if (lines >= maximum - 1) { limited = true; return; }
            emit(kind, pairs);
        }
        synchronized void finish(Object... pairs) {
            if (finished) return;
            emit("FINISH", pairs); finished = true;
        }
        synchronized boolean limited() { return limited; }
        private void emit(String kind, Object... pairs) {
            StringBuilder json = new StringBuilder("{\"schema\":\"usb-storage-probe-local-v1\",\"runId\":").append(quoted(runId))
                    .append(",\"kind\":").append(quoted(kind)).append(",\"localAt\":").append(quoted(Instant.now().toString()));
            for (int i = 0; i < pairs.length; i += 2) {
                Object value = pairs[i + 1];
                json.append(',').append(quoted(pairs[i].toString())).append(':')
                        .append(value == null ? "null" : value instanceof Number || value instanceof Boolean ? value.toString() : quoted(value.toString()));
            }
            stream.println(json.append('}')); stream.flush(); lines++;
        }
        private static String quoted(String text) {
            StringBuilder result = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                if (c == '"' || c == '\\') result.append('\\').append(c);
                else if (c < 32) result.append(String.format("\\u%04x", (int)c));
                else result.append(c);
            }
            return result.append('"').toString();
        }
    }
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Standalone MON-02 probe, NOT MON-07 wire. Java21 + Windows PowerShell/CIM.\n"
                    + "--method msft-disk|win32-diskdrive --duration-seconds 0..600 (0=snapshot)\n"
                    + "--interval-millis 250..60000 --query-timeout-millis 100..10000 --max-lines 4..2000\n"
                    + "--machine-alias WIN-A --source-sha <40 lowercase hex or UNVERSIONED> --output-file <NEW JSONL path>\n"
                    + "Exit: 0 complete; 2 unsupported; 3 error/cleanup; 4 unknown; 5 output limit; 64 arguments; Ctrl+C stop.");
            return;
        }
        Options options;
        try { options = Options.parse(args); }
        catch (IllegalArgumentException badArguments) { System.err.println("PROBE INVALID_ARGUMENTS; use --help"); System.exit(64); return; }
        int result;
        try { result = run(options); }
        catch (java.io.IOException | RuntimeException unavailable) { System.err.println("PROBE RESOURCE_OR_RUNTIME_FAILURE"); result = 3; }
        System.exit(result);
    }
    static int run(Options options) throws java.io.IOException {
        // Direct output avoids losing the final record when PowerShell cancels a Tee pipeline.
        PrintStream stream = options.outputFile == null ? System.out : new PrintStream(
                Files.newOutputStream(options.outputFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), true, StandardCharsets.UTF_8);
        var output = new Output(stream, options.maxLines, UUID.randomUUID().toString());
        CimSnapshotSource source;
        try { source = new CimSnapshotSource(options.method, Duration.ofMillis(options.queryTimeoutMillis)); }
        catch (java.io.IOException | RuntimeException failure) { if (stream != System.out) stream.close(); throw failure; }
        var tracker = new UsbObservationTracker();
        long run = tracker.start();
        var cleaned = new AtomicBoolean();
        var exit = new AtomicInteger(130);
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            tracker.stop(); source.close();
            boolean clean = source.aliveChildren() == 0 && source.readersTerminated();
            if (!clean) exit.set(3);
            output.finish("exitCode", exit.get(), "queryChildrenStarted", source.childrenStarted(),
                    "queryChildrenAlive", source.aliveChildren(), "pipeReadersTerminated", source.readersTerminated(),
                    "nativeRegistration", "NOT_USED", "outputLimited", output.limited());
            if (stream.checkError()) exit.set(3);
            if (stream != System.out) stream.close();
        };
        Thread hook = new Thread(cleanup, "usb-probe-stop");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            output.write("START", "sourceSha", options.sourceSha, "machineAlias", options.machineAlias,
                    "method", options.method, "os", System.getProperty("os.name"), "osVersion", System.getProperty("os.version"),
                    "architecture", System.getProperty("os.arch"), "javaVersion", System.getProperty("java.version"),
                    "durationSeconds", options.durationSeconds, "intervalMillis", options.intervalMillis,
                    "queryTimeoutMillis", options.queryTimeoutMillis, "maxLines", options.maxLines);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(options.durationSeconds);
            int worst = 0;
            do {
                UsbSnapshot snapshot = source.scan();
                if (cleaned.get()) break;
                output.write("SNAPSHOT", "status", snapshot.status(), "storageCount", snapshot.status() == UsbSnapshot.Status.READY ? snapshot.observedStorageCount() : null,
                        "observedStorageCount", snapshot.observedStorageCount(), "problem", snapshot.problem(),
                        "privilege", snapshot.privilege(), "powershellVersion", snapshot.powershellVersion(), "queryMillis", snapshot.queryMillis());
                for (var observation : tracker.accept(run, snapshot))
                    output.write(observation.kind(), "deviceRef", observation.deviceRef(), "storageCount", observation.storageCount());
                // Deterministic severity: ERROR > UNSUPPORTED > UNKNOWN > READY, independent of enum order.
                int severity = switch (snapshot.status()) { case READY -> 0; case UNKNOWN -> 1; case UNSUPPORTED -> 2; case ERROR -> 3; };
                worst = Math.max(worst, severity);
                if (output.limited() || options.durationSeconds == 0 || snapshot.status() == UsbSnapshot.Status.UNSUPPORTED) break;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(options.intervalMillis)));
            } while (!cleaned.get() && System.nanoTime() < deadline);
            exit.set(output.limited() ? 5 : switch (worst) { case 1 -> 4; case 2 -> 2; case 3 -> 3; default -> 0; });
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt(); exit.set(130);
        } finally {
            cleanup.run();
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException shuttingDown) { /* Shutdown hook owns cleanup. */ }
        }
        return exit.get();
    }
}
