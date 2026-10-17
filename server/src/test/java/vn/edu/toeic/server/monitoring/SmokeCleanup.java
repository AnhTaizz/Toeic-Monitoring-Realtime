package vn.edu.toeic.server.monitoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleanup reporting for the C1/C2 smoke harnesses. Every step runs even if an earlier one failed,
 * each component prints its own PASS or FAIL line, and a failure is never swallowed: it is either
 * attached to the primary test failure (suppressed) or thrown on its own. Output carries only the
 * component label and exception class names, never paths, tokens or server messages.
 */
final class SmokeCleanup {
    interface Step { void run() throws Exception; }

    private final String harness;
    private final List<String> failed = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();

    SmokeCleanup(String harness) { this.harness = harness; }

    void step(String component, Step action) {
        try {
            action.run();
            System.out.println(harness + " CLEANUP PASS " + component);
        } catch (Exception | AssertionError failure) {
            failed.add(component);
            failures.add(failure);
            System.err.println(harness + " CLEANUP FAIL " + component + " " + classes(failure));
        }
    }

    boolean passed() { return failed.isEmpty(); }

    List<String> failedComponents() { return List.copyOf(failed); }

    /**
     * Ends a finally block. With a primary failure, cleanup failures are attached to it and the
     * primary keeps propagating; without one, a cleanup failure becomes the test failure.
     */
    void finish(Throwable primary, String messagePrefix) {
        if (failed.isEmpty()) return;
        if (primary != null) { failures.forEach(primary::addSuppressed); return; }
        IllegalStateException cleanup = new IllegalStateException(messagePrefix + "cleanup FAIL components=" + failed);
        failures.forEach(cleanup::addSuppressed);
        throw cleanup;
    }

    /** "class=IllegalStateException cause=FileSystemException": enough to triage, nothing sensitive. */
    static String classes(Throwable failure) {
        StringBuilder text = new StringBuilder("class=").append(failure.getClass().getSimpleName());
        Throwable cause = failure.getCause();
        for (int depth = 0; cause != null && cause != failure && depth < 4; depth++, cause = cause.getCause())
            text.append(" cause=").append(cause.getClass().getSimpleName());
        return text.toString();
    }
}
