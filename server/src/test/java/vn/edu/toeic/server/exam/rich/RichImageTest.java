package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.exam.ExamApiException;

/** Bytes/filesystem thật, DTO MOCK tự tạo; các auth/transaction fault dùng Mockito. */
class RichImageTest {
    @TempDir Path temp;
    private RichImageSessionAuthority.Context context(long writer,String epoch) {
        return new RichImageSessionAuthority.Context("mock-ex02-200","mock-session","mock-attempt","SCHEDULED",writer,epoch,null,null);
    }
    private VisualManifest fixture() throws Exception {
        return RichExamFixtures.JSON.fromJson(Files.readString(RichExamFixtures.ROOT.resolve("visual-manifest.mock.json")),VisualManifest.class);
    }
    @Test void canonicalManifestMatchesApprovedIndependentFixtureAndHasSixSlotsForOneAsset() throws Exception {
        var expected=fixture();var slots=new ArrayList<>(expected.slots());Collections.reverse(slots);
        var actual=PartOneVisualManifest.create(context(2,expected.preparationEpoch()),slots);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.slots().stream().map(VisualImageSlot::mediaId).distinct()).hasSize(1);
        assertThat(RichExamFixtures.JSON.toJson(actual)).doesNotContain("questionId","prompt","option","altText","sourceUrl","localFile","correctOption");
        assertThatThrownBy(()->actual.slots().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @ParameterizedTest @ValueSource(strings={"WRITER","EPOCH","SIZE","HASH","MIME","WIDTH","HEIGHT","MEDIA","ATTEMPT","SESSION","EXAM"})
    void everyAuthoritativeContextOrAssetChangeInvalidatesManifest(String change) throws Exception {
        var original=fixture();var c=context(2,original.preparationEpoch());var slots=new ArrayList<>(original.slots());var s=slots.getFirst();
        switch(change) {
            case "WRITER" -> c=context(3,c.preparationEpoch());
            case "EPOCH" -> c=context(2,UUID.randomUUID().toString());
            case "ATTEMPT","SESSION","EXAM" -> c=new RichImageSessionAuthority.Context(
                    change.equals("EXAM")?"other":c.examId(),change.equals("SESSION")?"other":c.sessionId(),
                    change.equals("ATTEMPT")?"other":c.attemptId(),c.sessionState(),c.writerEpoch(),c.preparationEpoch(),null,null);
            default -> slots.set(0,new VisualImageSlot(s.slotId(),s.position(),change.equals("MEDIA")?"other":s.mediaId(),null,
                    change.equals("HASH")?"a".repeat(64):s.sha256(),s.sizeBytes()+(change.equals("SIZE")?1:0),
                    change.equals("MIME")?"image/jpeg":s.mimeType(),s.widthPx()+(change.equals("WIDTH")?1:0),s.heightPx()+(change.equals("HEIGHT")?1:0)));
        }
        assertThat(PartOneVisualManifest.create(c,slots).manifestId()).isNotEqualTo(original.manifestId());
    }
    @Test void invalidOrDuplicateSlotPositionsCannotMintManifest() throws Exception {
        var f=fixture();var c=context(2,f.preparationEpoch());
        assertThatThrownBy(()->PartOneVisualManifest.create(c,f.slots().subList(0,5))).isInstanceOf(ExamApiException.class);
        var duplicate=new ArrayList<>(f.slots());duplicate.set(1,duplicate.getFirst());
        assertThatThrownBy(()->PartOneVisualManifest.create(c,duplicate)).isInstanceOf(ExamApiException.class);
    }
    @Test void imageResponseUsesPreciselyVerifiedBytesAndDoesNotFollowSubsequentDiskChanges() throws Exception {
        var request=RichExamFixtures.request();Path audio=temp.resolve("audio"),images=temp.resolve("images");
        RichExamFixtures.files(audio,images,request);var m=request.media().getFirst();
        var validator=new RichMediaValidator(audio.toString(),images.toString());
        byte[] original=Files.readAllBytes(images.resolve(request.exam().examId()).resolve(m.localFile()));
        byte[] verified=validator.readImage(request.exam().examId(),m);
        assertThat(verified).isEqualTo(original);
        Files.write(images.resolve(request.exam().examId()).resolve(m.localFile()),new byte[]{1});
        assertThat(verified).isEqualTo(original);
        assertThatThrownBy(()->validator.readImage(request.exam().examId(),m)).isInstanceOf(ExamApiException.class);
    }
    @ParameterizedTest @ValueSource(strings={"TRAVERSAL","DEVICE","TOO_LARGE","ZERO","DIMENSION","PIXEL_BOMB","MIME","REMOTE","AUDIO","HASH"})
    void badImageMetadataFailsBeforeMissingRootAccess(String fault) throws Exception {
        var tree=RichExamFixtures.tree();var m=RichExamFixtures.media(tree,0);
        switch(fault) {
            case "TRAVERSAL" -> m.addProperty("localFile","../escape.png");
            case "DEVICE" -> m.addProperty("localFile","CON.png");
            case "TOO_LARGE" -> m.addProperty("sizeBytes",8L*1024*1024+1);
            case "ZERO" -> m.addProperty("sizeBytes",0);
            case "DIMENSION" -> m.addProperty("widthPx",8193);
            case "PIXEL_BOMB" -> {m.addProperty("widthPx",8192);m.addProperty("heightPx",8192);}
            case "MIME" -> m.addProperty("mimeType","text/html");
            case "REMOTE" -> m.addProperty("storageStatus","REMOTE_REFERENCE");
            case "AUDIO" -> m.addProperty("mediaType","AUDIO");
            case "HASH" -> m.addProperty("sha256","INVALID");
        }
        var media=RichExamFixtures.parse(tree.toString()).media().getFirst();
        assertThatThrownBy(()->new RichMediaValidator(temp.resolve("missing").toString(),temp.resolve("missing").toString())
                .readImage("mock-ex02-200",media)).isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(409));
    }
    @Test void rolesAndOwnerCheckedBeforePolicyOrFileAndMissingEx07FailsClosed() {
        var scopes=mock(AttemptScopeAuthorizer.class);var auth=new AuthorizationService(scopes);
        @SuppressWarnings("unchecked") ObjectProvider<RichImageSessionAuthority> policies=mock(ObjectProvider.class);
        var files=mock(RichMediaValidator.class);var jdbc=mock(JdbcClient.class);var manifests=mock(PartOneVisualManifest.class);
        var tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service=new RichImageService(auth,policies,jdbc,files,manifests,tx);
        var owner=new AuthenticatedUser(1,"MOCK",Role.CANDIDATE);
        assertThatThrownBy(()->service.download(null,"a","m",Map.of())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.download(new AuthenticatedUser(2,"MOCK",Role.PROCTOR),"a","m",Map.of())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.download(owner,"a","m",Map.of())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(policies,files,jdbc,manifests,tx);
        when(scopes.canReadExam(owner,"a")).thenReturn(true);
        assertThatThrownBy(()->service.download(owner,"a","m",Map.of())).isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.code().name()).isEqualTo("INVALID_STATE"));
        verifyNoInteractions(files,jdbc,manifests);verify(tx).rollback(any());
    }
    @Test void temporaryDatabaseLockFailureProducesRedactedRetryable503BeforeFiles() {
        var scopes=mock(AttemptScopeAuthorizer.class);var owner=new AuthenticatedUser(1,"MOCK",Role.CANDIDATE);
        when(scopes.canReadExam(owner,"a")).thenReturn(true);
        @SuppressWarnings("unchecked") ObjectProvider<RichImageSessionAuthority> policies=mock(ObjectProvider.class);
        var policy=mock(RichImageSessionAuthority.class);when(policies.getIfAvailable()).thenReturn(policy);
        when(policy.lockAndAuthorize(owner,"a")).thenThrow(new org.springframework.dao.CannotAcquireLockException("MOCK private SQL"));
        var files=mock(RichMediaValidator.class);var tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service=new RichImageService(new AuthorizationService(scopes),policies,mock(JdbcClient.class),files,mock(PartOneVisualManifest.class),tx);
        var failure=catchThrowable(()->service.download(owner,"a","m",Map.of()));
        assertThat(failure).isInstanceOf(RichImportUnavailableException.class);verifyNoInteractions(files);
        var response=new RichImageController(service).unavailable((RichImportUnavailableException)failure);
        assertThat(response.getStatusCode().value()).isEqualTo(503);assertThat(response.getBody().error().retryable()).isTrue();
        assertThat(response.getBody().error().message()).doesNotContain("private SQL");
    }
}
