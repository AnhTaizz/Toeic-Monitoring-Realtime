package vn.edu.toeic.server.exam.rich;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CreateCandidateRequest;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class CandidateManagementService {
    private final AuthorizationService authorization;
    private final vn.edu.toeic.server.auth.AccountProvisioner accounts;

    public CandidateManagementService(AuthorizationService authorization, vn.edu.toeic.server.auth.AccountProvisioner accounts) {
        this.authorization=authorization;this.accounts=accounts;
    }

    @Transactional
    public CandidateCatalogItem create(AuthenticatedUser proctor, CreateCandidateRequest request) {
        authorization.requireRole(proctor, Role.PROCTOR);
        if(request==null)throw ExamApiException.invalidInput("Nhập mã thí sinh và họ tên.");
        var account=accounts.create(request.username(),request.displayName(),Role.CANDIDATE).account();
        return new CandidateCatalogItem(account.userId(),account.username(),account.displayName());
    }
}
