package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.*;

/** Unit fault injection MOCK; PostgreSQL thật có suite riêng. */
class RichExamBoundaryTest {
    private final AuthorizationService authorization=new AuthorizationService(mock(AttemptScopeAuthorizer.class));
    private final AuthenticatedUser proctor=new AuthenticatedUser(1,"MOCK-proctor",Role.PROCTOR);
    @Test void candidateAndAnonymousCannotReachBodyParserOrService() throws Exception {
        var parser=mock(RichExamJson.class);var service=mock(RichExamService.class);
        var controller=new RichExamController(authorization,parser,service);
        var request=new MockHttpServletRequest();
        assertThatThrownBy(()->controller.importExam(request)).isInstanceOf(AccessDeniedException.class);
        request.setUserPrincipal(new AuthenticatedUser(2,"MOCK-candidate",Role.CANDIDATE));
        assertThatThrownBy(()->controller.importExam(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(parser,service);
    }
    @Test void transientBodyIoMapsTo503RetryableWithoutPrivateDetails() throws Exception {
        var parser=mock(RichExamJson.class);when(parser.read(any())).thenThrow(new IOException("MOCK private path"));
        var controller=new RichExamController(authorization,parser,mock(RichExamService.class));
        var request=new MockHttpServletRequest();request.setUserPrincipal(proctor);
        var thrown=catchThrowable(()->controller.importExam(request));
        assertThat(thrown).isInstanceOf(RichImportUnavailableException.class);
        var response=controller.unavailable((RichImportUnavailableException)thrown);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().error().retryable()).isTrue();
        assertThat(response.getBody().error().message()).doesNotContain("private path");
    }
    @Test void commitFailureCannotReturnSuccessfulImportReceipt() throws Exception {
        var validator=mock(RichExamValidator.class);var media=mock(RichMediaValidator.class);
        var repository=mock(RichExamRepository.class);var request=RichExamFixtures.request();
        when(media.verify(request)).thenReturn(java.util.List.of());
        PlatformTransactionManager failingCommit=new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition){return new SimpleTransactionStatus();}
            public void commit(TransactionStatus status){throw new TransactionSystemException("MOCK commit failed",new SQLException("MOCK connection lost","08006"));}
            public void rollback(TransactionStatus status){}
        };
        var service=new RichExamService(authorization,validator,media,repository,failingCommit);
        assertThatThrownBy(()->service.importExam(proctor,request)).isInstanceOf(RichImportUnavailableException.class);
        verify(repository).insert(request,1);verify(repository).checkStored(request);verify(media).recheck(request,java.util.List.of());
    }
}
