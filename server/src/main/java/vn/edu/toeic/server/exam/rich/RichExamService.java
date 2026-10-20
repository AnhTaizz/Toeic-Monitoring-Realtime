package vn.edu.toeic.server.exam.rich;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;

@Service
public final class RichExamService {
    private final AuthorizationService authorization;
    private final RichExamValidator validator;
    private final RichMediaValidator media;
    private final RichExamRepository repository;
    private final TransactionTemplate transaction;

    public RichExamService(AuthorizationService authorization, RichExamValidator validator,
                           RichMediaValidator media, RichExamRepository repository, PlatformTransactionManager manager) {
        this.authorization=authorization; this.validator=validator; this.media=media; this.repository=repository;
        this.transaction=new TransactionTemplate(manager);
    }
    public RichExamImportResponse importExam(AuthenticatedUser proctor, RichExamImportRequest request) {
        authorization.requireRole(proctor,Role.PROCTOR);
        validator.validate(request);
        var stamps=media.verify(request);
        try {
            transaction.executeWithoutResult(status->{
                repository.insert(request,proctor.userId());
                media.recheck(request,stamps);
                repository.checkStored(request);
            });
        } catch (TransientDataAccessException | DataAccessResourceFailureException | CannotCreateTransactionException temporary) {
            throw new RichImportUnavailableException();
        } catch (TransactionException commitFailure) {
            for (Throwable cause=commitFailure;cause!=null;cause=cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && sql.getSQLState()!=null
                        && sql.getSQLState().matches("(08|40|53|57).*")) throw new RichImportUnavailableException();
            }
            throw commitFailure;
        }
        // TransactionTemplate đã COMMIT thành công mới được tạo response201.
        return new RichExamImportResponse(2,request.exam().examId(),"IMPORTED",ValidationStatus.VERIFIED,
                "VERIFIED_AT_IMPORT",200,request.groups().size(),request.media().size());
    }
}
