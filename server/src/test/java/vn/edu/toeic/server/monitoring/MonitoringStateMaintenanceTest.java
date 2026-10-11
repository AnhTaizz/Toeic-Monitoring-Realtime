package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** REAL executor lifecycle, MOCK state service; Spring/WS wiring is tested by FullSnapshotSmoke. */
class MonitoringStateMaintenanceTest {
    @Test void automaticallyCallsMaintainAndStopsOnClose() throws Exception {
        var state = mock(MonitoringStateService.class);
        var called = new CountDownLatch(1);
        doAnswer(call -> { called.countDown(); return null; }).when(state).maintain();
        var maintenance = new MonitoringStateMaintenance(state, 5);
        try { assertThat(called.await(5, TimeUnit.SECONDS)).isTrue(); }
        finally { maintenance.close(); }
        int calls = mockingDetails(state).getInvocations().size();
        maintenance.close();
        assertThat(mockingDetails(state).getInvocations()).hasSize(calls);
        assertThat(Thread.getAllStackTraces().keySet()).noneMatch(thread -> thread.isAlive()
                && thread.getName().equals("toeic-state-maintenance"));
    }

    @Test void transientFailureDoesNotCancelTheNextScan() throws Exception {
        var state = mock(MonitoringStateService.class);
        var recovered = new CountDownLatch(1);
        doThrow(new IllegalStateException("MOCK transient failure"))
                .doAnswer(call -> { recovered.countDown(); return null; }).when(state).maintain();
        try (var maintenance = new MonitoringStateMaintenance(state, 5)) {
            assertThat(recovered.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void closeInterruptsAndWaitsForAnInFlightScan() throws Exception {
        var state = mock(MonitoringStateService.class);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException stopped) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return null;
        }).when(state).maintain();
        var maintenance = new MonitoringStateMaintenance(state, 5);
        try { assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); }
        finally { maintenance.close(); }
        assertThat(interrupted.getCount()).isZero();
    }

    @Test void invalidIntervalsDoNotStartAWorker() {
        var state = mock(MonitoringStateService.class);
        for (long invalid : new long[] {-1, 0, Duration.ofDays(1).toMillis() + 1}) {
            assertThatThrownBy(() -> new MonitoringStateMaintenance(state, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
