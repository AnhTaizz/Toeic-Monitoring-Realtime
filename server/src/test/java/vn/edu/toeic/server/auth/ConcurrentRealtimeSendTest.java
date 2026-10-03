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
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.realtime.RealtimeWebSocketHandler;

/** MOCK session with controlled overlapping callers; no sleep/race-based assertion. */
class ConcurrentRealtimeSendTest {
    @Test void concurrentHandlerResponsesNeverOverlapRawSessionWrites() throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        when(store.findByTokenHash(anyString())).thenReturn(Optional.of(new StoredSession(
                new AuthenticatedUser(1, "MOCK-candidate", Role.CANDIDATE), Instant.now().plusSeconds(60), null, true)));
        RealtimeWebSocketHandler handler = new RealtimeWebSocketHandler(new SessionAuthenticationService(store, Clock.systemUTC()),
                new AuthorizationService((user, attempt) -> false), 65_536, 5000, 65_536);
        WebSocketSession socket = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(SessionAuthenticationService.TOKEN_HASH_ATTRIBUTE, "MOCK-hash");
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
                try { handler.handleMessage(socket, new TextMessage(AuthenticatedNetworkTest.heartbeat(null))); }
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
