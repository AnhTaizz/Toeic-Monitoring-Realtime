package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.toeic.client.dashboard.DashboardFixtures.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.client.LoginFailedException;

/** REAL local HTTP transport, MOCK response fixtures; not production server evidence. */
class MonitoringApiClientTest {
    @Test void endpointsHeadersAndUtcValuesFollowServerContract() throws Exception {
        try(Fixture f=new Fixture()) {
            JsonObject body=response(); body.addProperty("serverTime",TIME.toString()); JsonArray attempts=new JsonArray(); attempts.add(presenceJson("MOCK-A",3)); body.add("attempts",attempts);
            f.body.set(body.toString()); assertThat(f.api.roster().get(3,TimeUnit.SECONDS).attempts().getFirst().revision()).isEqualTo(3);
            assertThat(f.path.get()).isEqualTo("/api/v1/monitoring/attempts"); assertThat(f.authorization.get()).isEqualTo("Bearer MOCK-token-only");
            body=response(); body.addProperty("attemptId","MOCK-A"); JsonArray events=new JsonArray(); events.add(eventJson("MOCK-A","MOCK-e")); body.add("events",events); f.body.set(body.toString());
            assertThat(f.api.events("MOCK-A").get(3,TimeUnit.SECONDS)).hasSize(1); assertThat(f.path.get()).isEqualTo("/api/v1/monitoring/attempts/MOCK-A/events");
            body=response(); body.addProperty("attemptId","MOCK-A"); body.add("interruptions",new JsonArray()); f.body.set(body.toString()); assertThat(f.api.interruptions("MOCK-A").get(3,TimeUnit.SECONDS)).isEmpty();
            body=response(); JsonObject user=new JsonObject(); user.addProperty("role","PROCTOR"); body.add("user",user); JsonArray scopes=new JsonArray(); scopes.add("MOCK-A"); body.add("attemptScope",scopes); f.body.set(body.toString());
            assertThat(f.api.scope().get(3,TimeUnit.SECONDS).attemptScope()).containsExactly("MOCK-A");
            assertThat(f.path.get()).isEqualTo("/api/v1/auth/me");
        }
    }
    @ParameterizedTest @ValueSource(strings={"null","[]","{","{\"protocolVersion\":\"v0\",\"traceId\":\"MOCK\",\"serverTime\":\"bad\",\"attempts\":[]}"})
    void successfulStatusWithInvalidJsonIsNotAccepted(String body) throws Exception {
        try(Fixture f=new Fixture()) { f.body.set(body); assertThatThrownBy(() -> f.api.roster().join()).hasCauseInstanceOf(InvalidServerResponseException.class); }
    }
    @ParameterizedTest @ValueSource(ints={401,403,503,302})
    void errorsKeepStatusButNeverReflectRawBody(int status) throws Exception {
        try(Fixture f=new Fixture()) {
            f.status=status; f.body.set("MOCK untrusted token/password/path response");
            assertThatThrownBy(() -> f.api.roster().join()).hasCauseInstanceOf(LoginFailedException.class)
                    .satisfies(failure -> assertThat(((LoginFailedException)failure.getCause()).statusCode()).isEqualTo(status));
        }
    }
    @Test void timeoutAndCancellationUseConfiguredBoundAndCloseRejectsLateCall() throws Exception {
        try(Fixture f=new Fixture(Duration.ofMillis(300))) {
            f.gate=new CountDownLatch(1); var pending=f.api.roster();
            assertThatThrownBy(pending::join).isInstanceOf(CompletionException.class);
            f.gate.countDown(); f.gate=null; f.api.close(); assertThatThrownBy(() -> f.api.roster().join()).hasCauseInstanceOf(IllegalStateException.class);
        }
    }
    @Test void oversizedBodyRejectedBeforeRender() throws Exception {
        try(Fixture f=new Fixture()) { f.body.set(" ".repeat(4*1024*1024+1)); assertThatThrownBy(() -> f.api.roster().join()).hasCauseInstanceOf(InvalidServerResponseException.class); }
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer server; final ExecutorService executor=Executors.newSingleThreadExecutor(); final MonitoringApiClient api;
        final AtomicReference<String> body=new AtomicReference<>("{}"); final AtomicReference<String> path=new AtomicReference<>(),authorization=new AtomicReference<>();
        volatile int status=200; volatile CountDownLatch gate;
        Fixture() throws Exception { this(Duration.ofSeconds(3)); }
        Fixture(Duration timeout) throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(executor);
            server.createContext("/",exchange -> {
                path.set(exchange.getRequestURI().toString()); authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                try {
                    CountDownLatch current=gate; if(current!=null) current.await(2,TimeUnit.SECONDS);
                    byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes);
                } catch (Exception ignored) { } finally { exchange.close(); }
            });
            server.start(); api=new MonitoringApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"MOCK-token-only",timeout,10);
        }
        @Override public void close(){if(gate!=null)gate.countDown();api.close();server.stop(0);executor.shutdownNow();}
    }
}
