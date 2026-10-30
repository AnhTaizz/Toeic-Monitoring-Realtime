package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import vn.edu.toeic.client.admin.AdminExamImport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Real HTTP authentication, migrations, rollback and normalized content round trips. */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
class AdminExamPostgresTest {
    @TempDir Path temporary;
    private Connection db,owner;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String schema,base,admin,proctor,candidate;
    private static final String API="/api/v2/admin/exams";
    @Test void manualBundlePersistsPendingAudioAndRollsBackOnStaleRevision() throws Exception {
        var draft=new Gson().toJsonTree(vn.edu.toeic.protocol.admin.ManualExamDraft.empty()).getAsJsonObject();
        draft.addProperty("examId","manual-audio");draft.addProperty("title","MOCK local audio");
        Path local=temporary.resolve("âm thanh.wav");Files.write(local,"RIFF1234WAVEtest".getBytes());
        try(var files=new vn.edu.toeic.client.admin.ManualExamFiles()) {
            files.select(draft.getAsJsonArray("questions").get(0).getAsJsonObject(),local);
            var zip=files.bundle(draft);
            var saved=tree(check(manualBundle("POST",API+"/manual/bundle",zip,admin),201));
            assertThat(saved.get("revision").getAsInt()).isEqualTo(1);
            String name=saved.getAsJsonArray("questions").get(0).getAsJsonObject().get("audioFile").getAsString();
            Path directory=temporary.resolve("audio/manual-audio");assertThat(Files.readAllBytes(directory.resolve(name))).isEqualTo(Files.readAllBytes(local));
            assertThat(tree(check(call("GET",API+"/manual?examId=manual-audio",null,admin),200))).isEqualTo(saved);
            assertThat(number("SELECT count(*) FROM exams WHERE authoring_status='PENDING'")).isEqualTo(1);
            Files.write(local,"RIFF5678WAVEchanged".getBytes());
            Path stale=files.bundle(draft);
            check(manualBundle("PUT",API+"/manual/bundle?examId=manual-audio",stale,admin),409);
            try(var paths=Files.list(directory)){assertThat(paths.count()).isEqualTo(1);}
            check(manualBundle("POST",API+"/manual/bundle",stale,proctor),403);
            Files.write(local,"invalid audio payload".getBytes());
            check(manualBundle("PUT",API+"/manual/bundle?examId=manual-audio",files.bundle(draft),admin),400);
        }
    }
    private HttpResponse<String> manualBundle(String method,String path,Path zip,String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+token)
            .header("Content-Type","application/zip").method(method,HttpRequest.BodyPublishers.ofFile(zip)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void manualDraftPartialReadyPendingAndUsedLifecycle() throws Exception {
        var draft=new Gson().toJsonTree(vn.edu.toeic.protocol.admin.ManualExamDraft.empty()).getAsJsonObject();
        draft.addProperty("examId","manual-200");draft.addProperty("title","MOCK đề soạn tay");
        draft.getAsJsonArray("questions").get(0).getAsJsonObject().addProperty("prompt","Partial first question");
        var saved=tree(check(call("POST",API+"/manual",draft.toString(),admin),201));
        assertThat(saved.get("revision").getAsLong()).isEqualTo(1);
        assertThat(saved.getAsJsonArray("questions").size()).isEqualTo(200);
        var list=tree(check(call("GET",API,null,admin),200)).getAsJsonArray("items").get(0).getAsJsonObject();
        assertThat(list.get("authoringStatus").getAsString()).isEqualTo("PENDING");
        assertThat(list.get("completedQuestions").getAsInt()).isZero();
        assertThat(tree(check(call("GET",API+"/manual?examId=manual-200",null,admin),200))).isEqualTo(saved);
        var session="""
                {"sessionId":"manual-session","examId":"manual-200","title":"MOCK ca thi","durationSeconds":120,
                 "candidateUsernames":["candidate1"],"proctorUsernames":[]}
                """;
        check(call("POST","/api/v1/sessions",session,proctor),400);
        assertThatThrownBy(()->{try(var s=db.createStatement()){
            s.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) SELECT 'bypass','manual-200','MOCK',60,id FROM user_accounts WHERE username='proctor1'");
        }}).isInstanceOf(SQLException.class);
        for(var element:saved.getAsJsonArray("questions")) {
            var q=element.getAsJsonObject();q.addProperty("prompt","MOCK question "+q.get("orderIndex"));
            q.addProperty("correctOption","B");q.addProperty("passageText","MOCK passage");
            for(var o:q.getAsJsonArray("options"))o.getAsJsonObject().addProperty("optionText","MOCK option "+o.getAsJsonObject().get("optionId"));
        }
        var stale=saved.deepCopy();
        saved=tree(check(call("PUT",API+"/manual?examId=manual-200",saved.toString(),admin),200));
        assertThat(number("SELECT count(*) FROM exam_questions WHERE exam_id='manual-200'")).isEqualTo(200);
        assertThat(number("SELECT count(*) FROM exam_options WHERE exam_id='manual-200'")).isEqualTo(775);
        list=tree(check(call("GET",API,null,admin),200)).getAsJsonArray("items").get(0).getAsJsonObject();
        assertThat(list.get("authoringStatus").getAsString()).isEqualTo("READY");
        assertThat(list.get("completedQuestions").getAsInt()).isEqualTo(200);
        check(call("PUT",API+"/manual?examId=manual-200",stale.toString(),admin),409);
        saved.getAsJsonArray("questions").get(199).getAsJsonObject().addProperty("correctOption","");
        saved=tree(check(call("PUT",API+"/manual?examId=manual-200",saved.toString(),admin),200));
        check(call("POST","/api/v1/sessions",session,proctor),400);
        saved.getAsJsonArray("questions").get(199).getAsJsonObject().addProperty("correctOption","C");
        saved=tree(check(call("PUT",API+"/manual?examId=manual-200",saved.toString(),admin),200));
        check(call("POST","/api/v1/sessions",session,proctor),200);
        check(call("PUT",API+"/manual?examId=manual-200",saved.toString(),admin),409);
    }
    @Test void manualDraftRejectsChangedTemplateAndProtectsAnswerKeys() throws Exception {
        var draft=new Gson().toJsonTree(vn.edu.toeic.protocol.admin.ManualExamDraft.empty()).getAsJsonObject();
        draft.addProperty("examId","manual-guard");draft.addProperty("title","MOCK");
        for(String token:List.of(proctor,candidate)) {
            check(call("POST",API+"/manual",draft.toString(),token),403);
            check(call("GET",API+"/manual?examId=manual-guard",null,token),403);
        }
        var broken=draft.deepCopy();broken.getAsJsonArray("questions").get(0).getAsJsonObject().addProperty("part",7);
        check(call("POST",API+"/manual",broken.toString(),admin),400);
        broken=draft.deepCopy();broken.getAsJsonArray("questions").remove(0);
        check(call("POST",API+"/manual",broken.toString(),admin),400);
        assertThat(number("SELECT count(*) FROM exams")).isZero();
        check(call("POST",API+"/manual",draft.toString(),admin),201);
        check(call("POST",API+"/manual",draft.toString(),admin),409);
        check(call("DELETE",item("manual-guard"),null,admin),204);
        assertThat(number("SELECT count(*) FROM exam_manual_drafts")).isZero();
    }
    @BeforeEach void start() throws Exception {
        String url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        schema="admin_exam_"+UUID.randomUUID().toString().replace("-","");
        owner=DriverManager.getConnection(url,env("DB_USER"),env("DB_PASSWORD"));
        try(var s=owner.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,env("DB_USER"),env("DB_PASSWORD"));
        Path audio=temporary.resolve("audio"),images=temporary.resolve("images");
        RichExamFixtures.files(audio,images,RichExamFixtures.request());
        var props=new HashMap<String,Object>();
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",env("DB_USER"));props.put("spring.datasource.password",env("DB_PASSWORD"));
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);
        props.put("toeic.seed.password",env("TOEIC_SEED_PASSWORD"));props.put("toeic.seed.demo-accounts","true");
        props.put("toeic.admin.bootstrap.username","admin-exam-test");props.put("toeic.admin.bootstrap.password",env("TOEIC_SEED_PASSWORD")+"-admin");
        props.put("toeic.audio.root",audio.toString());props.put("toeic.image.root",images.toString());
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled","false");props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("admin-exam-test",props)));
        app=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();http=HttpClient.newHttpClient();
        admin=login("admin-exam-test",env("TOEIC_SEED_PASSWORD")+"-admin");
        proctor=login("proctor1",env("TOEIC_SEED_PASSWORD"));candidate=login("candidate1",env("TOEIC_SEED_PASSWORD"));
    }
    @AfterEach void stop() throws Exception {
        try{if(http!=null)http.close();if(app!=null)app.close();if(db!=null)db.close();}
        finally{if(owner!=null){try(var s=owner.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}owner.close();}}
    }
    @Test void legacyCrudRoundTripAndValidationRollback() throws Exception {
        var exam=legacy("legacy/admin ? đề");String path=item("legacy/admin ? đề");
        check(call("POST",API+"?version=1",exam.toString(),admin),201);
        var read=tree(check(call("GET",path,null,admin),200));sameGraph(read,exam);
        exam.addProperty("title","Đề đã sửa");RichExamFixtures.question(exam,0).addProperty("prompt","Updated question");
        check(call("PUT",path+"&version=1",exam.toString(),admin),200);
        read=tree(check(call("GET",path,null,admin),200));sameGraph(read,exam);
        var invalid=exam.deepCopy();RichExamFixtures.question(invalid,0).addProperty("correctOption","Z");
        check(call("PUT",path+"&version=1",invalid.toString(),admin),400);
        sameGraph(tree(check(call("GET",path,null,admin),200)),exam);
        check(call("POST",API+"?version=1",exam.toString(),admin),409);
        check(call("DELETE",path,null,admin),204);check(call("GET",path,null,admin),404);
        assertThat(number("SELECT count(*) FROM exam_questions")).isZero();
    }
    @Test void richGraphCanBeReadEditedAndDeletedWithoutLosingBlocksOrKeys() throws Exception {
        var exam=RichExamFixtures.tree();String id=exam.getAsJsonObject("exam").get("examId").getAsString();
        check(call("POST",API,exam.toString(),admin),201);
        var read=tree(check(call("GET",item(id),null,admin),200));
        assertThat(read.getAsJsonArray("questions").size()).isEqualTo(200);
        assertThat(read.getAsJsonArray("media").size()).isEqualTo(exam.getAsJsonArray("media").size());
        read.getAsJsonObject("exam").addProperty("title","Đề TOEIC đã sửa");
        check(call("PUT",item(id),read.toString(),admin),200);
        sameGraph(tree(check(call("GET",item(id),null,admin),200)),read);
        var broken=read.deepCopy();RichExamFixtures.question(broken,0).getAsJsonObject("scoring").addProperty("correctOption","Z");
        check(call("PUT",item(id),broken.toString(),admin),400);
        sameGraph(tree(check(call("GET",item(id),null,admin),200)),read);
        // The schema is valid, but media verification fails after the old graph was deleted: rollback must restore it.
        var missingMedia=read.deepCopy();RichExamFixtures.media(missingMedia,0).addProperty("sha256","0".repeat(64));
        check(call("PUT",item(id),missingMedia.toString(),admin),409);
        sameGraph(tree(check(call("GET",item(id),null,admin),200)),read);
        check(call("DELETE",item(id),null,admin),204);
        for(String table:List.of("exams","exam_questions","exam_options","exam_sources","exam_media_assets","exam_content_blocks","exam_question_groups"))
            assertThat(number("SELECT count(*) FROM "+table)).as(table).isZero();
    }
    @Test void usedExamIsViewOnlyAndOtherRolesCannotAccessAdminKeysOrMutations() throws Exception {
        var exam=legacy("protected");check(call("POST",API+"?version=1",exam.toString(),admin),201);
        try(var s=db.createStatement()){s.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) SELECT 'protect','protected','Ca thi',60,id FROM user_accounts WHERE username='proctor1'");}
        check(call("PUT",item("protected")+"&version=1",exam.toString(),admin),409);
        check(call("DELETE",item("protected"),null,admin),409);
        assertThat(tree(check(call("GET",API,null,admin),200)).getAsJsonArray("items").get(0).getAsJsonObject().get("inUse").getAsBoolean()).isTrue();
        for(String token:List.of(proctor,candidate)) {
            check(call("GET",API,null,token),403);check(call("GET",item("protected"),null,token),403);
            check(call("POST",API,"{broken",token),403);check(call("PUT",item("protected"),"{broken",token),403);
            check(call("DELETE",item("protected"),null,token),403);
        }
        check(call("GET",API,null,null),401);
        sameGraph(tree(check(call("GET",item("protected"),null,admin),200)),exam);
    }
    @Test void paginationLiteralSearchAndStrictBodyErrors() throws Exception {
        for(int i=0;i<23;i++)check(call("POST",API+"?version=1",legacy("page-"+String.format("%02d",i)).toString(),admin),201);
        var first=tree(check(call("GET",API+"?search=page-",null,admin),200));
        assertThat(first.getAsJsonArray("items").size()).isEqualTo(20);
        var second=tree(check(call("GET",API+"?search=page-&afterExamId="+first.get("nextAfterExamId").getAsString(),null,admin),200));
        assertThat(second.getAsJsonArray("items").size()).isEqualTo(3);
        assertThat(tree(check(call("GET",API+"?search=%25",null,admin),200)).getAsJsonArray("items")).isEmpty();
        check(call("POST",API+"?version=1","null",admin),400);
        check(call("POST",API+"?version=1",legacy("page-00").toString().replace("\"title\":","\"unknown\":1,\"title\":"),admin),400);
        check(call("POST",API+"?version=3","{}",admin),400);
    }
    @Test void simultaneousCreatesReturnOneSuccessAndOneConflict() throws Exception {
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=workers.submit(()->{gate.await();return call("POST",API+"?version=1",legacy("same-id").toString(),admin).statusCode();});
            var b=workers.submit(()->{gate.await();return call("POST",API+"?version=1",legacy("same-id").toString(),admin).statusCode();});
            gate.countDown();assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(201,409);
        }
        assertThat(number("SELECT count(*) FROM exams WHERE exam_id='same-id'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM exam_questions WHERE exam_id='same-id'")).isEqualTo(1);
    }
    @Test void bundleUploadsMediaAndRollsBackBothFilesAndDatabaseOnBadReplacement() throws Exception {
        var graph=RichExamFixtures.tree();String id=graph.getAsJsonObject("exam").get("examId").getAsString();
        var mismatched=graph.deepCopy();mismatched.getAsJsonObject("exam").addProperty("examId","oicaidjt");
        assertThat(check(upload("POST",API+"/bundle",fixtureBundle(mismatched,false),admin),400)).contains("oicaidjt","Thiếu");
        var request=RichExamFixtures.request();
        for(var media:request.media())Files.delete(temporary.resolve(media.mediaType().name().equals("AUDIO")?"audio":"images").resolve(id).resolve(media.localFile()));
        check(upload("POST",API+"/bundle",fixtureBundle(graph,false),admin),201);
        var saved=tree(check(call("GET",item(id),null,admin),200));
        var first=request.media().getFirst();Path firstFile=temporary.resolve(first.mediaType().name().equals("AUDIO")?"audio":"images").resolve(id).resolve(first.localFile());
        byte[] original=Files.readAllBytes(firstFile);
        graph.getAsJsonObject("exam").addProperty("title","Changed upload");
        check(upload("PUT",API+"/bundle?examId="+id,fixtureBundle(graph,true),admin),409);
        assertThat(Files.readAllBytes(firstFile)).isEqualTo(original);
        sameGraph(tree(check(call("GET",item(id),null,admin),200)),saved);
        check(upload("PUT",API+"/bundle?examId="+id,fixtureBundle(graph,false),admin),200);
        assertThat(tree(check(call("GET",item(id),null,admin),200)).getAsJsonObject("exam").get("title").getAsString()).isEqualTo("Changed upload");
        check(upload("POST",API+"/bundle",fixtureBundle(graph,false),admin),409);
    }
    @Test void bundleRejectsTraversalAndNonAdminBeforeWritingAnyMedia() throws Exception {
        Path malicious=temporary.resolve("unsafe.zip");try(var zip=new ZipOutputStream(Files.newOutputStream(malicious))) {
            zip.putNextEntry(new ZipEntry("../outside.txt"));zip.write(new byte[]{1});zip.closeEntry();
        }
        check(upload("POST",API+"/bundle",malicious,proctor),403);
        check(upload("POST",API+"/bundle",malicious,candidate),403);
        check(upload("POST",API+"/bundle",malicious,admin),400);
        assertThat(Files.exists(temporary.resolve("outside.txt"))).isFalse();assertThat(number("SELECT count(*) FROM exams")).isZero();
    }
    @Test void usedExamMetadataCanBeEditedWhileItsQuestionGraphRemainsProtected() throws Exception {
        check(call("POST",API+"?version=1",legacy("metadata").toString(),admin),201);
        var originalQuestions=tree(check(call("GET",item("metadata"),null,admin),200)).getAsJsonArray("questions");
        try(var s=db.createStatement()){s.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) SELECT 'metadata-session','metadata','Ca thi',60,id FROM user_accounts WHERE username='proctor1'");}
        check(call("PATCH",item("metadata"),"{\"title\":\"Renamed\",\"description\":\"Updated description\"}",admin),200);
        var actual=tree(check(call("GET",item("metadata"),null,admin),200));assertThat(actual.get("title").getAsString()).isEqualTo("Renamed");
        assertThat(actual.getAsJsonArray("questions")).isEqualTo(originalQuestions);
        check(call("PATCH",item("metadata"),"{\"title\":\"Bad\",\"description\":\"\",\"questions\":[]}",admin),400);
        check(call("PUT",item("metadata")+"&version=1",legacy("metadata").toString(),admin),409);
        check(call("PATCH",item("metadata"),"{\"title\":\"Bad\",\"description\":\"\"}",candidate),403);
    }
    @Test void revisionCopiesVerifiedMediaAndEditsWithoutTouchingUsedSource() throws Exception {
        var original=RichExamFixtures.tree();String id="mock-ex02-200";
        check(call("POST",API,original.toString(),admin),201);
        try(var s=db.createStatement()){s.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) SELECT 'revision-session','mock-ex02-200','Ca thi',60,id FROM user_accounts WHERE username='proctor1'");}
        var before=tree(check(call("GET",item(id),null,admin),200));var changed=before.deepCopy();
        changed.getAsJsonObject("exam").addProperty("examId","revision-01");
        RichExamFixtures.question(changed,105).getAsJsonObject("scoring").addProperty("correctOption","B");
        RichExamFixtures.question(changed,105).getAsJsonArray("options").get(1).getAsJsonObject().addProperty("text","Revised choice");
        for(String token:List.of(candidate,proctor))check(call("POST",API+"/revision?examId="+id,changed.toString(),token),403);
        var bad=changed.deepCopy();RichExamFixtures.media(bad,0).addProperty("sha256","0".repeat(64));
        check(call("POST",API+"/revision?examId="+id,bad.toString(),admin),400);
        assertThat(Files.exists(temporary.resolve("audio/revision-01"))).isFalse();
        check(call("POST",API+"/revision?examId="+id,changed.toString(),admin),201);
        sameGraph(tree(check(call("GET",item("revision-01"),null,admin),200)),changed);
        sameGraph(tree(check(call("GET",item(id),null,admin),200)),before);
        for(var media:RichExamFixtures.request().media()) {
            Path root=temporary.resolve(media.mediaType().name().equals("AUDIO")?"audio":"images");
            assertThat(Files.readAllBytes(root.resolve("revision-01").resolve(media.localFile())))
                    .isEqualTo(Files.readAllBytes(root.resolve(id).resolve(media.localFile())));
        }
        check(call("POST",API+"/revision?examId="+id,changed.toString(),admin),409);
        sameGraph(tree(check(call("GET",item(id),null,admin),200)),before);
    }
    @Test @EnabledIfEnvironmentVariable(named="TOEIC_STUDY4_SOURCE",matches=".+")
    void actualStudy4JsonAndDesktopMediaReachTheServerThroughTheUploadEndpoint() throws Exception {
        try(var prepared=AdminExamImport.load(Path.of(env("TOEIC_STUDY4_SOURCE")))) {
            var graph=AdminExamImport.parse(prepared.json);String id=graph.getAsJsonObject("exam").get("examId").getAsString();
            check(upload("POST",API+"/bundle",prepared.bundle(prepared.json),admin),201);
            var actual=tree(check(call("GET",item(id),null,admin),200));sameGraph(actual,graph);
            assertThat(number("SELECT count(*) FROM exam_questions")).isEqualTo(200);
            graph.getAsJsonObject("exam").addProperty("examId","oicaidjt");
            check(upload("POST",API+"/bundle",prepared.bundle(graph.toString()),admin),201);
            sameGraph(tree(check(call("GET",item("oicaidjt"),null,admin),200)),graph);
            assertThat(number("SELECT count(*) FROM exam_questions WHERE exam_id='oicaidjt'")).isEqualTo(200);
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named="TOEIC_STUDY4_SOURCE",matches=".+")
    @EnabledIfEnvironmentVariable(named="TOEIC_CANDIDATE_GUI",matches="true")
    void actualAdminFormCanRenameAndImportTheCrawlIntoTheRealServer() throws Exception {
        try{javafx.application.Platform.startup(()->{});}catch(IllegalStateException alreadyStarted){}
        javafx.application.Platform.setImplicitExit(false);
        var login=new vn.edu.toeic.protocol.auth.LoginResponse("v0","TEST","TEST",admin,"Bearer","2099-01-01T00:00:00Z",
                new vn.edu.toeic.protocol.auth.LoginResponse.UserView(1,"admin-exam-test","Admin test","ADMIN"),List.of());
        var root=adminFx(()->new vn.edu.toeic.client.proctor.redesign.AdminAccountView(base,login,()->{},()->{}));
        var stage=adminFx(()->{var window=new javafx.stage.Stage();window.setScene(new javafx.scene.Scene(root,1366,820));window.show();
            ((javafx.scene.control.ToggleButton)root.lookup("#admin-exam-nav")).fire();return window;});
        try {
            awaitAdmin(()->root.lookup("#admin-exam-add")!=null&&!root.lookup("#admin-exam-add").isDisabled());
            adminFx(()->{var page=root.getCenter();var method=page.getClass().getDeclaredMethod("importFile",Path.class);method.setAccessible(true);
                method.invoke(page,Path.of(env("TOEIC_STUDY4_SOURCE")));return null;});
            awaitAdmin(()->adminControl("admin-exam-code")!=null);
            adminFx(()->{
                ((javafx.scene.control.TextField)adminControl("admin-exam-code")).setText("oicaidjt");
                ((javafx.scene.control.TextField)adminControl("admin-exam-title")).setText("oicaidjt");
                ((javafx.scene.control.Button)adminControl("admin-exam-save")).fire();return null;
            });
            awaitAdmin(()->adminControl("admin-exam-save")==null || !adminControl("admin-exam-save").isDisabled());
            String error=adminFx(()->adminControl("admin-exam-form-status")==null?"":((javafx.scene.control.Label)adminControl("admin-exam-form-status")).getText());
            assertThat(error).isEmpty();
            assertThat(number("SELECT count(*) FROM exam_questions WHERE exam_id='oicaidjt'")).isEqualTo(200);
            assertThat(number("SELECT count(*) FROM exam_media_assets WHERE exam_id='oicaidjt'")).isEqualTo(65);
            assertThat(tree(check(call("GET",item("oicaidjt"),null,admin),200)).getAsJsonObject("exam").get("title").getAsString()).isEqualTo("oicaidjt");
            awaitAdmin(()->root.lookup("#admin-exam-edit-oicaidjt")!=null&&!root.lookup("#admin-exam-find").isDisabled());
            adminFx(()->{((javafx.scene.control.Button)root.lookup("#admin-exam-edit-oicaidjt")).fire();return null;});
            awaitAdmin(()->adminControl("exam-question-list")!=null);
            adminFx(()->{
                ((javafx.scene.control.ListView<?>)adminControl("exam-question-list")).getSelectionModel().select(105);
                ((javafx.scene.control.TextArea)adminControl("exam-question-prompt")).setText("The library ____ its doors last week.");
                ((javafx.scene.control.TextArea)adminControl("exam-question-option-B")).setText("reopened");
                ((javafx.scene.control.ComboBox<String>)adminControl("exam-question-correct")).setValue("B");
                // Switch away and back: edits must survive navigation before saving.
                ((javafx.scene.control.ListView<?>)adminControl("exam-question-list")).getSelectionModel().select(106);
                ((javafx.scene.control.ListView<?>)adminControl("exam-question-list")).getSelectionModel().select(105);
                assertThat(((javafx.scene.control.TextArea)adminControl("exam-question-option-B")).getText()).isEqualTo("reopened");
                ((javafx.scene.control.Button)adminControl("admin-exam-save")).fire();return null;
            });
            awaitAdmin(()->adminControl("admin-exam-save")==null || !adminControl("admin-exam-save").isDisabled());
            assertThat(adminFx(()->adminControl("admin-exam-form-status")==null?"":((javafx.scene.control.Label)adminControl("admin-exam-form-status")).getText())).isEmpty();
            var edited=tree(check(call("GET",item("oicaidjt"),null,admin),200));var q=RichExamFixtures.question(edited,105);
            assertThat(q.getAsJsonObject("scoring").get("correctOption").getAsString()).isEqualTo("B");
            assertThat(q.getAsJsonArray("options").get(1).getAsJsonObject().get("text").getAsString()).isEqualTo("reopened");
            assertThat(q.getAsJsonArray("blocks").get(0).getAsJsonObject().get("text").getAsString()).isEqualTo("The library ____ its doors last week.");
            assertThat(edited.getAsJsonArray("questions").size()).isEqualTo(200);
            assertThat(edited.getAsJsonArray("media").size()).isEqualTo(65);
        } finally {adminFx(()->{root.close();stage.close();return null;});}
    }
    private static javafx.scene.Node adminControl(String id) {
        for(var window:javafx.stage.Window.getWindows())if(window.isShowing()&&window.getScene()!=null){var node=window.getScene().lookup("#"+id);if(node!=null)return node;}return null;
    }
    private static <T> T adminFx(java.util.concurrent.Callable<T> action) throws Exception {
        var future=new java.util.concurrent.CompletableFuture<T>();javafx.application.Platform.runLater(()->{try{future.complete(action.call());}catch(Throwable failure){future.completeExceptionally(failure);}});
        return future.get(20,java.util.concurrent.TimeUnit.SECONDS);
    }
    private static void awaitAdmin(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(40);
        while(System.nanoTime()<until){if(adminFx(condition))return;Thread.sleep(50);}fail("Admin form did not complete its operation");
    }
    private Path fixtureBundle(JsonObject graph,boolean corrupt) throws Exception {
        Path file=temporary.resolve("bundle-"+UUID.randomUUID()+".zip");
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("rich-import.json"));zip.write(graph.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            int index=0;
            for(var media:RichExamFixtures.request().media()) {
                String folder=media.mediaType().name().equals("AUDIO")?"audio":"images";
                byte[] bytes=Files.readAllBytes(RichExamFixtures.ROOT.resolve("assets").resolve(folder).resolve("mock-ex02-200").resolve(media.localFile()));
                if(corrupt && index++==0)bytes[bytes.length-1]^=1;
                zip.putNextEntry(new ZipEntry(folder+"/mock-ex02-200/"+media.localFile()));zip.write(bytes);zip.closeEntry();
            }
        }
        return file;
    }
    private HttpResponse<String> upload(String method,String path,Path zip,String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(60))
                .header("Authorization","Bearer "+token).header("Content-Type","application/zip").method(method,HttpRequest.BodyPublishers.ofFile(zip)).build(),HttpResponse.BodyHandlers.ofString());
    }
    static JsonObject legacy(String id) {
        var tree=JsonParser.parseString("""
                {"examId":"placeholder","title":"Đề mẫu","description":"Mô tả","questions":[{
                 "questionId":"q1","section":"READING","part":5,"prompt":"Choose the answer","correctOption":"A","orderIndex":1,
                 "options":[{"optionId":"A","optionText":"One","orderIndex":1},{"optionId":"B","optionText":"Two","orderIndex":2},
                 {"optionId":"C","optionText":"Three","orderIndex":3},{"optionId":"D","optionText":"Four","orderIndex":4}]}]}
                """).getAsJsonObject();tree.addProperty("examId",id);return tree;
    }
    private static void sameGraph(JsonObject actual,JsonObject expected) {
        assertThat(normalize(actual).equals(normalize(expected)))
                .as("Full content graph, ignoring null fields and database-generated source aliases").isTrue();
    }
    private static JsonElement normalize(JsonObject graph) {
        var copy=graph.deepCopy();var aliases=new HashMap<String,String>();
        if(copy.has("media")) {
            var sorted=new ArrayList<JsonElement>();copy.getAsJsonArray("media").forEach(sorted::add);
            sorted.sort(Comparator.comparing(m->m.getAsJsonObject().get("mediaId").getAsString()));
            var array=new JsonArray();sorted.forEach(array::add);copy.add("media",array);
        }
        if(copy.has("sources")){var sources=copy.getAsJsonArray("sources");for(int i=0;i<sources.size();i++)
            aliases.put(sources.get(i).getAsJsonObject().get("sourceRef").getAsString(),"source-"+i);}
        normalizeFields(copy,aliases);return copy;
    }
    private static void normalizeFields(JsonElement tree,Map<String,String> aliases) {
        if(tree.isJsonObject()){
            var object=tree.getAsJsonObject();object.entrySet().removeIf(e->e.getValue().isJsonNull());
            if(object.has("sourceRef"))object.addProperty("sourceRef",aliases.getOrDefault(object.get("sourceRef").getAsString(),object.get("sourceRef").getAsString()));
            for(var value:object.entrySet())normalizeFields(value.getValue(),aliases);
        }else if(tree.isJsonArray())for(var value:tree.getAsJsonArray())normalizeFields(value,aliases);
    }
    private String login(String name,String password) throws Exception {
        return tree(check(call("POST","/api/v1/auth/login",new Gson().toJson(Map.of("username",name,"password",password,"requestId",UUID.randomUUID().toString())),null),200)).get("token").getAsString();
    }
    private HttpResponse<String> call(String method,String path,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(20))
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        if(body!=null)request.header("Content-Type","application/json");if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static String check(HttpResponse<String> response,int status) {assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return response.body();}
    private static JsonObject tree(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static String item(String id){return API+"/item?examId="+URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);}
    private long number(String sql) throws Exception {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private static String env(String name){return Objects.requireNonNull(System.getenv(name),name);}
}
