package vn.edu.toeic.monitoring.usb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** At most one owned PowerShell process; bounded pipe readers; no device IDs or dumps in diagnostics. */
public final class CimSnapshotSource implements AutoCloseable {
    public enum Method { MSFT_DISK, WIN32_DISKDRIVE }
    private final Object lifecycle = new Object();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final ExecutorService readers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "usb-probe-pipe"); thread.setDaemon(true); return thread;
    });
    private final List<String> command;
    private final Duration timeout;
    private volatile boolean closed;
    private Process active;
    private final List<ProcessHandle> owned = new ArrayList<>();

    public CimSnapshotSource(Method method, Duration timeout) throws IOException {
        this(windowsCommand(method), timeout);
    }
    // Explicit process seam for deterministic child timeout/output/stop tests, never exposed in CLI.
    CimSnapshotSource(List<String> command, Duration timeout) {
        if (timeout.toMillis() < 100 || timeout.toMillis() > 10000) throw new IllegalArgumentException("Invalid timeout");
        this.command = List.copyOf(command); this.timeout = timeout;
    }
    private static List<String> windowsCommand(Method method) throws IOException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return List.of();
        String script;
        try (InputStream resource = CimSnapshotSource.class.getResourceAsStream("cim-snapshot.ps1")) {
            if (resource == null) throw new IOException("Missing probe resource");
            script = "$probeMode='" + method.name() + "';\n" + new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        String windows = System.getenv("SystemRoot");
        if (windows == null) return List.of();
        return List.of(Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
                "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }
    public UsbSnapshot scan() {
        long start = System.nanoTime();
        if (!scanning.compareAndSet(false, true)) return failure("QUERY_BUSY", start);
        Process process = null;
        Future<byte[]> output = null, errors = null;
        try {
            synchronized (lifecycle) {
                if (closed) return failure("STOPPED", start);
                if (command.isEmpty()) return UsbSnapshot.failed(UsbSnapshot.Status.UNSUPPORTED, "WINDOWS_REQUIRED", elapsed(start));
                active = process = new ProcessBuilder(command).start();
                owned.add(process.toHandle());
                process.getOutputStream().close();
                Process child = process;
                output = readers.submit(() -> readLimited(child.getInputStream()));
                errors = readers.submit(() -> readLimited(child.getErrorStream()));
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) return failure("QUERY_TIMEOUT", start);
            byte[] out = output.get(1, TimeUnit.SECONDS);
            byte[] err = errors.get(1, TimeUnit.SECONDS);
            if (closed) return failure("STOPPED", start);
            if (err.length != 0) return failure("CHILD_DIAGNOSTIC", start);
            return parse(new String(out, StandardCharsets.UTF_8), process.exitValue(), elapsed(start));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return failure("STOPPED", start);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException malformed) {
            return failure("OUTPUT_LIMIT_OR_READ_FAILURE", start);
        } catch (IOException | RuntimeException failure) {
            return failure("QUERY_LAUNCH_OR_PARSE_FAILURE", start);
        } finally {
            if (process != null) terminate(process);
            if (output != null) output.cancel(true);
            if (errors != null) errors.cancel(true);
            synchronized (lifecycle) { if (active == process) active = null; }
            scanning.set(false);
        }
    }
    private static byte[] readLimited(InputStream input) throws IOException {
        try (input; var bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 16384) throw new IOException("Output limit");
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        }
    }
    static UsbSnapshot parse(String output, int exit, long millis) {
        String[] lines = output.strip().split("\\R");
        if (lines.length > 131) throw new IllegalArgumentException("Output limit");
        String privilege = "UNKNOWN", version = "UNKNOWN";
        var disks = new ArrayList<UsbSnapshot.Disk>();
        boolean meta = false, end = false;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String[] fields = line.split("\\|", -1);
            if (fields.length == 2 && fields[0].equals("ERROR") && !end) {
                if (!Set.of("ACCESS_DENIED", "UNSUPPORTED_PROVIDER", "QUERY_FAILED", "ROW_LIMIT", "MISSING_METADATA").contains(fields[1]))
                    throw new IllegalArgumentException("Invalid diagnostic");
                if (exit == 0 || lineIndex != lines.length - 1) throw new IllegalArgumentException("Contradictory diagnostic");
                return new UsbSnapshot(fields[1].equals("UNSUPPORTED_PROVIDER") ? UsbSnapshot.Status.UNSUPPORTED : UsbSnapshot.Status.ERROR,
                        List.of(), fields[1], privilege, version, millis);
            }
            if (fields.length == 3 && fields[0].equals("META") && !meta && disks.isEmpty() && !end) {
                if (!Set.of("STANDARD", "ELEVATED").contains(fields[1]) || !fields[2].matches("[0-9.]{1,32}"))
                    throw new IllegalArgumentException("Invalid runtime metadata");
                privilege = fields[1]; version = fields[2]; meta = true;
            } else if (fields.length == 3 && fields[0].equals("DISK") && meta && !end) {
                if (!fields[1].matches("[0-9]{1,10}")) throw new IllegalArgumentException("Invalid disk metadata");
                disks.add(new UsbSnapshot.Disk(Long.parseLong(fields[1]), UsbSnapshot.Classification.valueOf(fields[2])));
            } else if (fields.length == 2 && fields[0].equals("END") && meta && !end) {
                if (!fields[1].matches("[0-9]{1,3}") || Integer.parseInt(fields[1]) != disks.size()) throw new IllegalArgumentException("Incomplete snapshot");
                end = true;
            } else throw new IllegalArgumentException("Invalid child output");
        }
        if (!end || exit != 0) throw new IllegalArgumentException("Incomplete snapshot");
        var status = disks.stream().anyMatch(d -> d.classification() == UsbSnapshot.Classification.UNKNOWN) ? UsbSnapshot.Status.UNKNOWN : UsbSnapshot.Status.READY;
        return new UsbSnapshot(status, disks, status == UsbSnapshot.Status.UNKNOWN ? "CLASSIFICATION_INCOMPLETE" : "NONE", privilege, version, millis);
    }
    private static UsbSnapshot failure(String problem, long start) {
        return UsbSnapshot.failed(UsbSnapshot.Status.ERROR, problem, elapsed(start));
    }
    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
    private static void terminate(Process process) {
        // No process-name/global kill. PowerShell CIM queries do not intentionally spawn grandchildren.
        List<ProcessHandle> descendants = process.descendants().toList();
        for (ProcessHandle child : descendants) if (child.isAlive()) child.destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
        try {
            process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (IOException ignored) {
            // Final alive/reader checks still report cleanup failure.
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    public int aliveChildren() {
        synchronized (lifecycle) { return (int) owned.stream().filter(ProcessHandle::isAlive).count(); }
    }
    public int childrenStarted() { synchronized (lifecycle) { return owned.size(); } }
    public boolean readersTerminated() { return readers.isTerminated(); }
    @Override public void close() {
        boolean interrupted = Thread.interrupted();
        Process process;
        synchronized (lifecycle) { closed = true; process = active; }
        if (process != null) terminate(process);
        readers.shutdownNow();
        try { readers.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException stopped) { interrupted = true; }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
