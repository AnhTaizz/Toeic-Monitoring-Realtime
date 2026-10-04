package vn.edu.toeic.client.monitoring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import vn.edu.toeic.protocol.Role;

/** Explicit Windows smoke với source production. Context active là MOCK/dev fixture,
 * không auto-start từ login hoặc giả có assignment production. Không đọc/in full path/user/arguments.
 */
public final class ProcessCollectorSmoke {
    private static String phase = "INITIAL_SCAN";
    private ProcessCollectorSmoke() { }
    public static void main(String[] args) {
        try { run(args.length == 0 ? null : Path.of(args[0])); }
        catch (Exception failure) {
            System.err.println("C2 ProcessHandle smoke FAIL phase=" + phase + " (" + failure.getClass().getSimpleName() + ")");
            System.exit(1); // Không in exception message có metadata private.
        }
    }
    private static void run(Path demoExecutable) throws Exception {
        System.out.println("ENV OS=" + System.getProperty("os.name") + " JDK=" + System.getProperty("java.version"));
        ProcessCollector collector = new ProcessCollector(new ProcessHandleSnapshotSource(), Duration.ofMillis(250));
        BlockingQueue<ProcessSnapshot> snapshots = new LinkedBlockingQueue<>();
        BlockingQueue<ProcessCollector.Problem> problems = new LinkedBlockingQueue<>();
        Process owned = null;
        Path profile = null;
        Set<Long> ownedIds = Set.of();
        try {
            String firstSession = collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE, true, null),
                    snapshots::add, problems::add); // MOCK active monitoring context for explicit smoke only.
            ProcessSnapshot baseline = await(snapshots, snapshot -> true);
            var diagnostics = baseline.diagnostics();
            String filenames = baseline.restrictedProcesses().stream().map(ObservedProcess::executableName)
                    .map(name -> name.toLowerCase(java.util.Locale.ROOT)).distinct().sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            System.out.printf("SUMMARY scanned=%d unreadable=%d missingCommand=%d missingStartInstant=%d missingUser=%d%n",
                    diagnostics.processesScanned(), diagnostics.unreadableProcesses(), diagnostics.missingCommand(),
                    diagnostics.missingStartInstant(), diagnostics.missingUser());
            System.out.println("OBSERVED restrictedExecutableFilenames=" + (filenames.isEmpty() ? "<NONE OBSERVED>" : filenames));
            check(!firstSession.isBlank() && baseline.policyVersion().equals("process-policy-v1"), "Production snapshot contract");
            System.out.println("PASS real ProcessHandle.allProcesses source + local polling snapshot; no network send");

            if (demoExecutable != null && Files.isRegularFile(demoExecutable)
                    && demoExecutable.getFileName().toString().equalsIgnoreCase("msedge.exe")) {
                phase = "OWNED_DEMO_OPEN";
                profile = Files.createTempDirectory("toeic-c2-owned-edge-");
                owned = new ProcessBuilder(demoExecutable.toString(), "--headless=new", "--disable-gpu",
                        "--no-first-run", "--no-default-browser-check", "--disable-background-networking",
                        "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=0",
                        "--user-data-dir=" + profile, "about:blank")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                long pid = owned.pid();
                check(baseline.restrictedProcesses().stream().noneMatch(item -> item.identity().pid() == pid), "Owned PID absent before launch");
                ProcessSnapshot appeared = await(snapshots, snapshot -> snapshot.restrictedProcesses().stream()
                        .anyMatch(item -> item.identity().pid() == pid && "msedge.exe".equalsIgnoreCase(item.executableName())));
                var item = appeared.restrictedProcesses().stream().filter(process -> process.identity().pid() == pid).findFirst().orElseThrow();
                System.out.println("PASS owned isolated headless msedge.exe appears; metadataQuality=" + item.metadataQuality());
                phase = "OWNED_DEMO_STOP";
                ownedIds = stopOwnedTree(owned);
                owned = null;
                Set<Long> ended = ownedIds;
                await(snapshots, snapshot -> snapshot.restrictedProcesses().stream().noneMatch(process -> ended.contains(process.identity().pid())));
                System.out.println("PASS only owned Edge process tree stopped; owned processes absent from later snapshot");
            } else System.out.println("OPEN_CLOSE_DEMO NOT RUN: isolated owned msedge executable unavailable");

            phase = "STOP_RESTART";
            collector.stop().get(5, TimeUnit.SECONDS);
            check(collector.latestSnapshot().isEmpty() && !collector.isRunning(), "Stop clears snapshot/lifecycle");
            snapshots.clear();
            String secondSession = collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE, true, null), snapshots::add, problems::add);
            check(!secondSession.equals(firstSession) && await(snapshots, snapshot -> true).collectorSessionId().equals(secondSession), "Fresh session on restart");
            collector.stop().get(5, TimeUnit.SECONDS);
            check(problems.isEmpty(), "No production poll/listener error");
            awaitWorkersGone();
            System.out.println("PASS stop/restart generates fresh collectorSessionId and terminates collector workers");
            try {
                collector.start(new MonitoringSessionGate.Context(Role.PROCTOR, true, null), snapshots::add, problems::add);
                throw new IllegalStateException("Proctor unexpectedly started");
            } catch (IllegalStateException expected) { check(!collector.isRunning(), "Proctor gate side effect"); }
            System.out.println("PASS active proctor denied; no collector worker started");
            System.out.println("C2 ProcessHandle smoke PASS; active context MOCK; GUI manual NOT RUN; no event/transport integration");
        } finally {
            phase = "CLEANUP";
            collector.close(); collector.stop().get(5, TimeUnit.SECONDS);
            if (owned != null) stopOwnedTree(owned);
            if (profile != null) {
                // Chỉ xóa đúng directory tạm do harness tạo; không in path.
                try (var files = Files.walk(profile)) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Set<Long> stopOwnedTree(Process owned) throws Exception {
        List<ProcessHandle> handles = new ArrayList<>(owned.descendants().toList());
        handles.add(owned.toHandle());
        // Handle lấy từ process con agent vừa mở, không tìm/kill theo tên executable.
        for (ProcessHandle handle : handles) if (handle.isAlive()) handle.destroyForcibly();
        for (ProcessHandle handle : handles) handle.onExit().get(5, TimeUnit.SECONDS);
        check(owned.waitFor(5, TimeUnit.SECONDS), "Owned process stopped");
        return handles.stream().map(ProcessHandle::pid).collect(java.util.stream.Collectors.toSet());
    }
    private static ProcessSnapshot await(BlockingQueue<ProcessSnapshot> snapshots, Predicate<ProcessSnapshot> wanted) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            ProcessSnapshot snapshot = snapshots.poll(250, TimeUnit.MILLISECONDS);
            if (snapshot != null && wanted.test(snapshot)) return snapshot;
        }
        throw new IllegalStateException("Expected production snapshot transition");
    }
    private static void awaitWorkersGone() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Thread.getAllStackTraces().keySet().stream().noneMatch(thread -> thread.isAlive()
                    && thread.getName().equals("toeic-process-collector"))) return;
            CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS)).get();
        }
        throw new IllegalStateException("Collector worker did not terminate");
    }
    private static void check(boolean pass, String fixedLabel) { if (!pass) throw new IllegalStateException(fixedLabel); }
}
