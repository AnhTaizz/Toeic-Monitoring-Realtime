package vn.edu.toeic.server.exam.rich;

import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public final class RichImageService {
    private final AuthorizationService authorization;
    private final ObjectProvider<RichImageSessionAuthority> authorities;
    private final JdbcClient jdbc;
    private final RichMediaValidator files;
    private final PartOneVisualManifest manifests;
    private final TransactionTemplate transaction;
    public RichImageService(AuthorizationService authorization, ObjectProvider<RichImageSessionAuthority> authorities,
            JdbcClient jdbc,RichMediaValidator files,PartOneVisualManifest manifests,PlatformTransactionManager manager) {
        this.authorization=authorization;this.authorities=authorities;this.jdbc=jdbc;this.files=files;
        this.manifests=manifests;this.transaction=new TransactionTemplate(manager);
    }
    public record Download(byte[] bytes,String mimeType,String sha256) {}

    public Download download(AuthenticatedUser user,String attemptId,String mediaId,Map<String,String[]> query) {
        authorization.requireRole(user,Role.CANDIDATE);
        RichExamValidator.id(attemptId);RichExamValidator.id(mediaId);
        authorization.requireExamRead(user,attemptId);
        try { return transaction.execute(status -> {
            var authority=authorities.getIfAvailable();
            if(authority==null) throw invalidState();
            var c=authority.lockAndAuthorize(user,attemptId);
            if(!attemptId.equals(c.attemptId())) throw AccessDeniedException.forbidden();
            String manifestQuery=query(query);
            var media=jdbc.sql("""
                    SELECT m.media_id,m.local_file,m.mime_type,m.size_bytes,m.sha256,m.width_px,m.height_px
                    FROM exam_media_assets m WHERE m.exam_id=:exam AND m.media_id=:media
                      AND m.media_type='IMAGE' AND m.storage_status='LOCAL_VERIFIED'
                      AND EXISTS (SELECT 1 FROM exam_content_blocks b WHERE b.exam_id=m.exam_id
                                  AND b.media_id=m.media_id AND b.block_type='IMAGE')
                    """).param("exam",c.examId()).param("media",mediaId).query((rs,row)->new MediaImport(
                    rs.getString("media_id"),MediaType.IMAGE,null,null,StorageStatus.LOCAL_VERIFIED,
                    rs.getString("local_file"),rs.getString("mime_type"),rs.getLong("size_bytes"),rs.getString("sha256"),
                    (Integer)rs.getObject("width_px"),(Integer)rs.getObject("height_px"),null,null)).optional()
                    .orElseThrow(AccessDeniedException::forbidden);
            if("SCHEDULED".equals(c.sessionState()) || "IN_PROGRESS".equals(c.sessionState()) && c.committedVisualManifestId()==null) {
                var manifest=manifests.build(c);
                if(manifest.slots().stream().noneMatch(s->s.mediaId().equals(mediaId))) throw AccessDeniedException.forbidden();
                if(manifestQuery==null) throw ExamApiException.invalidInput("Thiếu visualManifestId");
                if(!manifest.manifestId().equals(manifestQuery)) throw conflict();
            } else if("IN_PROGRESS".equals(c.sessionState())) {
                if(c.committedVisualManifestId()==null) throw invalidState();
                if(manifestQuery!=null && !c.committedVisualManifestId().equals(manifestQuery)) throw conflict();
            } else throw invalidState();
            var limits=jdbc.sql("""
                    SELECT count(*) AS files,COALESCE(sum(size_bytes),0) AS bytes FROM exam_media_assets
                    WHERE exam_id=:exam AND media_type='IMAGE'
                    """).param("exam",c.examId()).query((rs,row)->new long[]{rs.getLong("files"),rs.getLong("bytes")}).single();
            if(limits[0]>100 || limits[1]>64L*1024*1024) throw invalidState();
            byte[] bytes=files.readImage(c.examId(),media);
            // File verification có thể tốn thời gian: quyền/deadline vẫn phải hợp lệ sau read.
            if(!c.equals(authority.lockAndAuthorize(user,attemptId))) throw conflict();
            return new Download(bytes,media.mimeType(),media.sha256());
        }); } catch(TransientDataAccessException | DataAccessResourceFailureException | CannotCreateTransactionException temporary) {
            throw new RichImportUnavailableException();
        } catch(TransactionException commitFailure) {
            for(Throwable cause=commitFailure;cause!=null;cause=cause.getCause()) {
                if(cause instanceof java.sql.SQLException sql && sql.getSQLState()!=null
                        && sql.getSQLState().matches("(08|40|53|57).*")) throw new RichImportUnavailableException();
            }
            throw commitFailure;
        }
    }
    private static String query(Map<String,String[]> query) {
        if(query==null || query.isEmpty()) return null;
        if(query.size()!=1 || !query.containsKey("visualManifestId")) throw ExamApiException.invalidInput("Query không hợp lệ");
        var values=query.get("visualManifestId");
        if(values==null || values.length!=1 || values[0]==null || !values[0].matches("[a-f0-9]{64}"))
            throw ExamApiException.invalidInput("visualManifestId không hợp lệ");
        return values[0];
    }
    static ExamApiException invalidState() { return new ExamApiException(HttpStatus.CONFLICT,ErrorCode.INVALID_STATE,"Nội dung chưa được phép truy cập",null); }
    private static ExamApiException conflict() { return new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,"Visual manifest đã thay đổi",null); }
}
