package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.AttemptRefresh.Next;
import vn.edu.toeic.client.AttemptRefresh.Outcome;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.protocol.Role;

/**
 * Regression cho lỗi làm mới danh sách lượt thi khi ô chọn đang rỗng (NullPointerException từ
 * Set.contains(null)). Scope được tạo qua ScopeView như production để là Set bất biến thật.
 * MOCK: không có server thật; test lỗi mạng dùng HTTP cục bộ trả 503 rồi 200.
 */
class AttemptRefreshTest {
    private static Set<String> scope(String... attempts) {
        return new ScopeView(Role.CANDIDATE, Set.of(attempts)).attemptScope();
    }

    @Test
    void emptyScopeWithNothingSelectedShowsNoAttemptInsteadOfThrowing() {
        Outcome outcome = AttemptRefresh.loaded(Role.CANDIDATE, scope(), null, false);

        assertThat(outcome.items()).isEmpty();
        assertThat(outcome.selected()).isNull();
        assertThat(outcome.canEnter()).isFalse();
        assertThat(outcome.next()).isEqualTo(Next.NO_ATTEMPT);
    }

    /** 1 và 2 phần tử là Set12, từ 3 trở lên là SetN: hai lớp này ném lỗi ở hai chỗ khác nhau với contains(null). */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5})
    void nothingSelectedPicksTheFirstAttemptForEveryImmutableSetSize(int size) {
        String[] attempts = new String[size];
        for (int i = 0; i < size; i++) attempts[i] = "MOCK-" + (char) ('E' - i);

        Outcome outcome = AttemptRefresh.loaded(Role.CANDIDATE, scope(attempts), null, false);

        assertThat(outcome.items()).hasSize(size).isSorted();
        assertThat(outcome.selected()).as("chưa chọn gì thì chọn lượt đầu theo thứ tự").isEqualTo(outcome.items().getFirst());
        assertThat(outcome.canEnter()).isTrue();
        assertThat(outcome.next()).isEqualTo(Next.CHOOSE);
    }

    @Test
    void refreshFromEmptyToANewAttemptLetsTheCandidateEnterWithoutLoggingInAgain() {
        Outcome before = AttemptRefresh.loaded(Role.CANDIDATE, scope(), null, false);
        assertThat(before.canEnter()).isFalse();

        // Giám thị vừa tạo ca mới; thí sinh bấm làm mới với ô chọn vẫn rỗng.
        Outcome after = AttemptRefresh.loaded(Role.CANDIDATE, scope("MOCK-NEW"), before.selected(), false);

        assertThat(after.items()).containsExactly("MOCK-NEW");
        assertThat(after.selected()).isEqualTo("MOCK-NEW");
        assertThat(after.canEnter()).isTrue();
        assertThat(after.next()).isEqualTo(Next.CHOOSE);
    }

    @Test
    void selectedAttemptThatIsNoLongerGrantedIsReplacedAndMonitoringIsNotStarted() {
        Outcome refreshed = AttemptRefresh.loaded(Role.CANDIDATE, scope("MOCK-B", "MOCK-C"), "MOCK-A", false);
        assertThat(refreshed.selected()).isEqualTo("MOCK-B");
        assertThat(refreshed.next()).isEqualTo(Next.CHOOSE);

        Outcome startRequested = AttemptRefresh.loaded(Role.CANDIDATE, scope("MOCK-B", "MOCK-C"), "MOCK-A", true);
        assertThat(startRequested.next()).as("không bật giám sát cho lượt đã mất quyền").isEqualTo(Next.SELECTION_REVOKED);
        assertThat(startRequested.selected()).isEqualTo("MOCK-B");

        Outcome allRevoked = AttemptRefresh.loaded(Role.CANDIDATE, scope(), "MOCK-A", true);
        assertThat(allRevoked.next()).isEqualTo(Next.NO_ATTEMPT);
        assertThat(allRevoked.selected()).isNull();
        assertThat(allRevoked.canEnter()).isFalse();
    }

    @Test
    void selectionStillGrantedIsKeptAndMayStartMonitoring() {
        Outcome refreshed = AttemptRefresh.loaded(Role.CANDIDATE, scope("MOCK-A", "MOCK-B"), "MOCK-B", false);
        assertThat(refreshed.selected()).isEqualTo("MOCK-B");
        assertThat(refreshed.next()).isEqualTo(Next.CHOOSE);

        Outcome start = AttemptRefresh.loaded(Role.CANDIDATE, scope("MOCK-A", "MOCK-B"), "MOCK-B", true);
        assertThat(start.next()).isEqualTo(Next.START_MONITORING);
        assertThat(start.selected()).isEqualTo("MOCK-B");
    }

    @Test
    void scopeOfAnotherRoleNeverEnablesEntry() {
        Outcome outcome = AttemptRefresh.loaded(Role.PROCTOR, scope("MOCK-A"), null, true);

        assertThat(outcome.canEnter()).isFalse();
        assertThat(outcome.next()).isEqualTo(Next.NO_ATTEMPT);
    }

    @Test
    void networkFailureKeepsTheListAndARetryRecovers() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/me", exchange -> {
            boolean first = calls.incrementAndGet() == 1;
            byte[] bytes = (first ? "{}" : "{\"protocolVersion\":\"v0\",\"user\":{\"role\":\"CANDIDATE\"},"
                    + "\"attemptScope\":[\"MOCK-A\",\"MOCK-B\"]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(first ? 503 : 200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (LoginApiClient api = new LoginApiClient()) {
            assertThatThrownBy(() -> api.scope(origin, "MOCK-token").get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(LoginFailedException.class);
            Outcome failed = AttemptRefresh.failed(List.of("MOCK-A"), "MOCK-A");
            assertThat(failed.next()).isEqualTo(Next.RETRY);
            assertThat(failed.items()).as("lỗi mạng không xóa danh sách đang có").containsExactly("MOCK-A");
            assertThat(failed.selected()).isEqualTo("MOCK-A");
            assertThat(failed.canEnter()).as("chưa hỏi được server thì chưa cho vào thi").isFalse();

            ScopeView fresh = api.scope(origin, "MOCK-token").get(3, TimeUnit.SECONDS);
            Outcome retried = AttemptRefresh.loaded(fresh.role(), fresh.attemptScope(), failed.selected(), false);
            assertThat(retried.items()).containsExactly("MOCK-A", "MOCK-B");
            assertThat(retried.selected()).isEqualTo("MOCK-A");
            assertThat(retried.canEnter()).isTrue();
            assertThat(retried.next()).isEqualTo(Next.CHOOSE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void networkFailureWithAnEmptyListAndNothingSelectedCanStillBeRetried() {
        Outcome failed = AttemptRefresh.failed(List.of(), null);
        assertThat(failed.items()).isEmpty();
        assertThat(failed.selected()).isNull();
        assertThat(failed.next()).isEqualTo(Next.RETRY);

        Outcome retried = AttemptRefresh.loaded(Role.CANDIDATE, scope("MOCK-A"), failed.selected(), false);
        assertThat(retried.selected()).isEqualTo("MOCK-A");
        assertThat(retried.canEnter()).isTrue();
    }

    /** Ghi lại hành vi của JDK đã gây lỗi, để ai bỏ kiểm null trong AttemptRefresh sẽ thấy lý do. */
    @Test
    void immutableSetsRejectNullLookups() {
        assertThatThrownBy(() -> scope().contains(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> scope("MOCK-A").contains(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> scope("MOCK-A", "MOCK-B", "MOCK-C").contains(null)).isInstanceOf(NullPointerException.class);
    }
}
