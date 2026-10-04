package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.realtime.RealtimeWebSocketHandler;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;
import vn.edu.toeic.server.monitoring.MonitoringEventService;
import vn.edu.toeic.server.monitoring.ProcessEvent;

/** MOCK session with controlled overlapping callers; no sleep/race-based assertion. */
class ConcurrentRealtimeSendTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void concurrentAckAndAckOrWarningNeverOverlapRawSessionWrites(boolean warning) throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        when(store.findByTokenHash(anyString())).thenReturn(Optional.of(new StoredSession(
                new AuthenticatedUser(1, "MOCK-proctor", Role.PROCTOR), Instant.now().plusSeconds(60), null, true)));
        var authentication = new SessionAuthenticationService(store, Clock.systemUTC());
        var authorization = new AuthorizationService((user, attempt) -> true);
        var registry = new RealtimeSessionRegistry(authentication, authorization, 5000, 65_536);
        RealtimeWebSocketHandler handler = new RealtimeWebSocketHandler(authentication, authorization, 65_536,
                registry, mock(MonitoringEventService.class));
        WebSocketSession socket = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(SessionAuthenticationService.TOKEN_HASH_ATTRIBUTE, "MOCK-hash");
        attributes.put(AuthenticatedUser.ATTRIBUTE, new AuthenticatedUser(1, "MOCK-proctor", Role.PROCTOR));
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.getId()).thenReturn("MOCK-socket");
        when(socket.isOpen()).thenReturn(true);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                if (writes.incrementAndGet() == 1) {
                    firstEntered.countDown();
                    if (!releaseFirst.await(3, TimeUnit.SECONDS)) throw new AssertionError("MOCK writer not released");
                }
            } finally { active.decrementAndGet(); }
            return null;
        }).when(socket).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(socket);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> {
                try { handler.handleMessage(socket, new TextMessage(AuthenticatedNetworkTest.heartbeat(null))); }
                catch (Exception error) { throw new AssertionError("MOCK first handler failed"); }
            });
            assertThat(firstEntered.await(3, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> {
                try {
                    if (warning) registry.warnAssignedProctors(new MonitoringEventService.StoredEvent(1, "MOCK-attempt",
                            new ProcessEvent("MOCK-event", "MOCK-collector", "v1", 1, "notepad.exe", null,
                                    "UNREADABLE", Instant.now()), Instant.now()), "MOCK-trace");
                    else handler.handleMessage(socket, new TextMessage(AuthenticatedNetworkTest.heartbeat(null)));
                }
                catch (Exception error) { throw new AssertionError("MOCK second handler failed"); }
            });
            second.get(3, TimeUnit.SECONDS); // decorator queues the second response without waiting for raw send
            assertThat(maximum.get()).isEqualTo(1);
            releaseFirst.countDown();
            first.get(3, TimeUnit.SECONDS);
            assertThat(writes.get()).isEqualTo(2);
            assertThat(maximum.get()).isEqualTo(1);
            verify(socket, times(2)).sendMessage(any(TextMessage.class));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
