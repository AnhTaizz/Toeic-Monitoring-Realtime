package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.audio.*;

/** REAL loopback HTTP transport; responses are MOCK contract fixtures, no PostgreSQL here. */
class AudioApiClientTest {
    @TempDir Path directory;
    HttpServer server;
    AudioApiClient api;
    final List<String> calls = new CopyOnWriteArrayList<>();
    final Gson json = new Gson();
    final byte[] data = "MOCK byte stream".getBytes(StandardCharsets.UTF_8);
    volatile int status = 200;
    volatile String manifestOverride, writerLiteral = "9007199254740993", ackOverride;
    final AudioManifestResponse manifest = new AudioManifestResponse("v0", "ATTEMPT", "SESSION", "a".repeat(64),
            List.of(new AudioFileDto("đoạn nghe + 1.wav", data.length, AudioFiles.hash(data))));
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/attempts/", exchange -> {
            try (exchange) {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer TEST_ONLY_TOKEN");
                assertThat(exchange.getRequestURI().toString()).doesNotContain("TEST_ONLY_TOKEN");
                String path = exchange.getRequestURI().getPath(); calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response;
                if (path.endsWith("/takeover")) {
                    String request = JsonParser.parseString(payload).getAsJsonObject().get("requestId").getAsString();
                    response = ("{\"requestId\":\"" + request + "\",\"attemptId\":\"ATTEMPT\",\"writerEpoch\":" + writerLiteral + ",\"state\":\"ACTIVE\"}").getBytes(StandardCharsets.UTF_8);
                } else if (path.endsWith("/manifest")) response = (manifestOverride == null ? json.toJson(manifest) : manifestOverride).getBytes(StandardCharsets.UTF_8);
                else if (path.endsWith("/ready")) {
                    JsonObject request = JsonParser.parseString(payload).getAsJsonObject();
                    request.addProperty("protocolVersion", "v0");
                    if (request.get("ready").getAsBoolean()) request.addProperty("readyAt", "2026-10-14T01:00:00Z");
                    if (ackOverride != null) request.addProperty("requestId", ackOverride);
                    response = json.toJson(request).getBytes(StandardCharsets.UTF_8);
                } else {
                    assertThat(path).endsWith("/files/đoạn nghe + 1.wav");
                    assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("manifestId=" + manifest.manifestId()); response = data;
                }
                if (status == 302) exchange.getResponseHeaders().add("Location", "/credential-must-not-follow");
                exchange.sendResponseHeaders(status, response.length); exchange.getResponseBody().write(response);
            }
        });
        server.start(); api = new AudioApiClient("http://127.0.0.1:" + server.getAddress().getPort(), "TEST_ONLY_TOKEN");
    }
    @AfterEach void stop() { api.close(); server.stop(0); }
    @Test void exactWriterAndUnicodeFileDownloadWithBearerOnly() throws Exception {
        assertThat(api.acquireWriter("ATTEMPT")).isEqualTo(9007199254740993L);
        var received = api.manifest("ATTEMPT");
        var files = new AudioFiles(directory.resolve("Bộ nhớ có dấu")); files.download(api, received, ignored -> {});
        assertThat(files.verify(received)).containsEntry("đoạn nghe + 1.wav", AudioFiles.hash(data));
        assertThat(calls.getLast()).contains("%20", "%2B", "manifestId=");
    }
    @Test void readinessRequiresCorrelatedAckAndServerTimestamp() throws Exception {
        var request = new AudioReadyRequest("REQ", "ATTEMPT", 9007199254740993L, true, manifest.manifestId(), Map.of("đoạn nghe + 1.wav", AudioFiles.hash(data)), true);
        assertThat(api.ready(request).ready()).isTrue(); ackOverride = "OTHER";
        assertThatThrownBy(() -> api.ready(request)).isInstanceOf(IOException.class).hasMessageContaining("chưa xác nhận");
    }
    @Test void rejectsFractionalWriterMalformedJsonWrongAttemptAndDuplicateNames() {
        writerLiteral = "2.5";
        assertThatThrownBy(() -> api.acquireWriter("ATTEMPT")).isInstanceOf(IOException.class);
        for (String bad : List.of("{broken", json.toJson(new AudioManifestResponse("v0", "FOREIGN", "SESSION", "a".repeat(64), manifest.files())),
                json.toJson(new AudioManifestResponse("v0", "ATTEMPT", "SESSION", "a".repeat(64), List.of(manifest.files().getFirst(), manifest.files().getFirst()))))) {
            manifestOverride = bad; assertThatThrownBy(() -> api.manifest("ATTEMPT")).isInstanceOf(IOException.class);
        }
    }
    @Test void refusesRedirectAuthenticationFailureAndCallsAfterClose() {
        status = 302; assertThatThrownBy(() -> api.manifest("ATTEMPT")).isInstanceOf(IOException.class).hasMessageContaining("302");
        assertThat(calls).hasSize(1);
        status = 401; assertThatThrownBy(() -> api.manifest("ATTEMPT")).isInstanceOf(IOException.class).hasMessageContaining("đăng nhập lại");
        api.close(); assertThatThrownBy(() -> api.manifest("ATTEMPT")).isInstanceOf(IOException.class).hasMessageContaining("đóng");
    }
    @Test void invalidUrlCannotIncludeCredentialsQueryOrFragment() {
        for (String url : List.of("http://name:secret@localhost", "http://localhost?token=x", "http://localhost#x", "file:///tmp"))
            assertThatThrownBy(() -> new AudioApiClient(url, "TEST")).isInstanceOf(IllegalArgumentException.class);
    }
}
