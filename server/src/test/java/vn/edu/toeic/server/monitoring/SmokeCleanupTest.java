package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure test of the smoke cleanup reporting; no resources are created. */
class SmokeCleanupTest {
    private final PrintStream out = System.out, err = System.err;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    @BeforeEach void capture() {
        PrintStream stream = new PrintStream(captured, true, StandardCharsets.UTF_8);
        System.setOut(stream); System.setErr(stream);
    }
    @AfterEach void restore() { System.setOut(out); System.setErr(err); }

    @Test void everyStepRunsAndAFailedStepIsReportedWithoutPathsOrPass() {
        List<String> ran = new ArrayList<>();
        SmokeCleanup cleanup = new SmokeCleanup("TEST");
        cleanup.step("first", () -> ran.add("first"));
        cleanup.step("profile", () -> { throw new FileSystemException("C:\\Users\\secret\\profile", null, "locked"); });
        cleanup.step("schema", () -> ran.add("schema"));

        assertThat(ran).containsExactly("first", "schema");
        assertThat(cleanup.passed()).isFalse();
        assertThat(cleanup.failedComponents()).containsExactly("profile");
        String text = captured.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("TEST CLEANUP PASS first", "TEST CLEANUP FAIL profile class=FileSystemException", "TEST CLEANUP PASS schema");
        assertThat(text).doesNotContain("secret", "C:\\");
        assertThatThrownBy(() -> cleanup.finish(null, "TEST assertion: "))
                .isInstanceOf(IllegalStateException.class).hasMessage("TEST assertion: cleanup FAIL components=[profile]")
                .satisfies(failure -> assertThat(failure.getSuppressed()).hasSize(1));
    }

    @Test void primaryFailureKeepsPropagatingAndCarriesCleanupFailure() {
        SmokeCleanup cleanup = new SmokeCleanup("TEST");
        cleanup.step("context", () -> { throw new IllegalStateException(new java.io.IOException("x")); });
        RuntimeException primary = new RuntimeException("primary");
        cleanup.finish(primary, "");
        assertThat(primary.getSuppressed()).hasSize(1);
        assertThat(SmokeCleanup.classes(primary.getSuppressed()[0])).isEqualTo("class=IllegalStateException cause=IOException");
    }

    @Test void allPassingStepsFinishSilently() {
        SmokeCleanup cleanup = new SmokeCleanup("TEST");
        cleanup.step("only", () -> { });
        cleanup.finish(null, "");
        assertThat(cleanup.passed()).isTrue();
    }
}
