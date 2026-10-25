package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.exam.ExamApiException;

class CandidateDirectoryServiceTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
    @SuppressWarnings("unchecked")
    private final JdbcClient.MappedQuerySpec<CandidateCatalogItem> mapped = mock(JdbcClient.MappedQuerySpec.class);
    private final CandidateDirectoryService directory = new CandidateDirectoryService(jdbc, new AuthorizationService((user, attempt) -> false));
    private final AuthenticatedUser proctor = new AuthenticatedUser(3, "proctor1", Role.PROCTOR);

    @BeforeEach @SuppressWarnings("unchecked") void query() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        doReturn(mapped).when(statement).query(any(RowMapper.class));
        when(mapped.list()).thenReturn(List.of());
    }

    @Test void onlyProctorsMayDiscoverCandidateAccounts() {
        assertThatThrownBy(() -> directory.candidates(null, Map.of())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> directory.candidates(new AuthenticatedUser(1, "candidate1", Role.CANDIDATE), Map.of()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void searchIsLiteralAndThePageExposesOnlyMinimalIdentity() {
        when(mapped.list()).thenReturn(List.of(new CandidateCatalogItem(4, "candidate4", "An"), new CandidateCatalogItem(5, "candidate5", "Anh")));
        var page = directory.candidates(proctor, Map.of("search", new String[]{"A_%!"}, "limit", new String[]{"1"}, "afterUserId", new String[]{"2"}));
        assertThat(page.items()).extracting(CandidateCatalogItem::userId).containsExactly(4L);
        assertThat(page.nextAfterUserId()).isEqualTo(4L);
        verify(statement).param("after", 2L);
        verify(statement).param("limit", 2);
        verify(statement).param("search", "%A!_!%!!%");
        verify(jdbc).sql(argThat(sql -> sql.contains("role = 'CANDIDATE' AND enabled = TRUE") && !sql.contains("password")));
    }

    @Test void rejectsUnknownRepeatedAndUnboundedQueriesBeforeReadingAccounts() {
        for (Map<String, String[]> query : List.of(
                Map.of("limit", new String[]{"0"}), Map.of("limit", new String[]{"101"}),
                Map.of("afterUserId", new String[]{"-1"}), Map.of("afterUserId", new String[]{"9223372036854775808"}),
                Map.of("search", new String[]{"a", "b"}), Map.of("search", new String[]{"x".repeat(101)}),
                Map.of("role", new String[]{"PROCTOR"}))) {
            assertThatThrownBy(() -> directory.candidates(proctor, query)).isInstanceOf(ExamApiException.class);
        }
        verifyNoInteractions(jdbc);
    }

    @Test void controllerPreventsCachingAccountSearchResults() {
        var request = mock(HttpServletRequest.class);
        when(request.getUserPrincipal()).thenReturn(proctor);
        when(request.getParameterMap()).thenReturn(Map.of());
        var response = new CandidateDirectoryController(directory, mock(CandidateManagementService.class),
                new AuthorizationService((u,a)->false)).candidates(request);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().items()).isEmpty();
        assertThat(response.getBody().nextAfterUserId()).isNull();
    }
}
