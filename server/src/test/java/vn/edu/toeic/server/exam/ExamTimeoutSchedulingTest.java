package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ExamTimeoutSchedulingTest {
    @Test void annotationRunsAutonomouslyAndSchedulerStopsWithContext() throws Exception {
        ExamService service = mock(ExamService.class);
        CountDownLatch runs = new CountDownLatch(2);
        when(service.findExpiredActiveAttemptIds()).thenReturn(List.of("expired"));
        when(service.timeoutAttempt("expired")).thenAnswer(call -> { runs.countDown(); return false; });
        ThreadPoolTaskScheduler scheduler;
        try (AnnotationConfigApplicationContext context = context(service, Map.of("exam.timeout.scan-interval-ms", "20"))) {
            scheduler = context.getBean("examTimeoutScheduler", ThreadPoolTaskScheduler.class);
            assertThat(runs.await(5, TimeUnit.SECONDS)).isTrue(); // Never calls processTimeouts directly.
            verify(service, atLeast(2)).timeoutAttempt("expired");
        }
        assertThat(scheduler.getScheduledThreadPoolExecutor().isTerminated()).isTrue();
    }

    @Test void explicitDisableDoesNotCreateJobOrScheduler() {
        ExamService service = mock(ExamService.class);
        try (AnnotationConfigApplicationContext context = context(service, Map.of("exam.timeout.scheduler.enabled", "false"))) {
            assertThat(context.getBeansOfType(ExamTimeoutService.class)).isEmpty();
            assertThat(context.containsBean("examTimeoutScheduler")).isFalse();
        }
        verifyNoInteractions(service);
    }

    private AnnotationConfigApplicationContext context(ExamService service, Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST", properties));
        context.registerBean(ExamService.class, () -> service);
        context.register(ExamSchedulingConfiguration.class, ExamTimeoutService.class);
        context.refresh();
        return context;
    }
}
