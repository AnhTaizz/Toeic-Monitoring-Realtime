package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** PG/Flyway/HTTP thật; media roots tạm, nội dung 200 câu đều MOCK CC0. */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RichExamImportPostgresTest {
    @TempDir Path temporary;
    private String url,user,password,schema,base,token;
    private Connection admin,db;
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private Path audio,image;
    private RichExamImportRequest fixture;
    @BeforeAll void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");
        admin=DriverManager.getConnection(url,user,password);
    }
    @BeforeEach void ownedSchema() throws Exception {
        schema="ex03_"+UUID.randomUUID().toString().replace("-","");
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
        audio=temporary.resolve(schema).resolve("audio");image=temporary.resolve(schema).resolve("images");
        fixture=RichExamFixtures.request();
    }
    @AfterEach void cleanup() throws Exception {
        try {
            if(http!=null){http.close();http=null;}
            if(context!=null){context.close();context=null;}
            if(db!=null){if(!db.getAutoCommit())db.rollback();db.close();db=null;}
        } finally {try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}}
    }
    @AfterAll void close() throws Exception {if(admin!=null)admin.close();}

    @Test void freshV1ToV14HasValidatedHistoryAndOnlyEx03Markers() throws Exception {
        var f=flyway("14");assertThat(f.migrate().migrationsExecuted).isEqualTo(13);f.validate();
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE success")).isEqualTo(13);
        assertThat(strings("SELECT version FROM flyway_schema_history ORDER BY installed_rank"))
                .containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","14");
        assertThat(number("SELECT count(*) FROM information_schema.columns WHERE table_schema='"+schema+"' AND table_name='exam_sessions' AND column_name='contract_version'")).isZero();
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema='"+schema+"' AND table_name LIKE '%visual%'")).isZero();
    }

    @Test void upgradeV12ToV14PreservesEveryLegacyRowAndDefaults() throws Exception {
        flyway("12").migrate();
        execute("INSERT INTO user_accounts(id,username,display_name,role,password_hash) VALUES(100,'owner','MOCK','PROCTOR','MOCK unusable')");
        execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES('LEGACY','MOCK',1,100)");
        execute("INSERT INTO exam_sources(exam_id,source_name,source_path,permission_status,source_sha256) VALUES('LEGACY','MOCK','MOCK.json','UNKNOWN',repeat('a',64))");
        execute("BEGIN;INSERT INTO exam_questions(exam_id,question_id,section,part,prompt,correct_option,order_index) VALUES('LEGACY','Q1','READING',5,'MOCK','A',1);INSERT INTO exam_options(exam_id,question_id,option_id,option_text,order_index) VALUES('LEGACY','Q1','A','MOCK',1);COMMIT");
        execute("INSERT INTO exam_content_blocks(exam_id,block_id,question_id,block_type,order_index,text_content) VALUES('LEGACY','B1','Q1','TEXT',1,'MOCK')");
        execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) VALUES('S','LEGACY','MOCK',3600,100)");
        execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id,answers_json,writer_epoch,saved_revision,score) VALUES('A',100,'SUBMITTED','S','LEGACY','{\"Q1\":\"A\"}',7,3,1)");
        var before=snapshot();
        assertThat(flyway("14").migrate().migrationsExecuted).isEqualTo(1);flyway("14").validate();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(number("SELECT content_version FROM exams")).isEqualTo(1);
        assertThat(strings("SELECT option_content_status FROM exam_sources")).containsExactly("UNVERIFIED");
        assertThat(number("SELECT count(*) FROM exam_content_blocks WHERE document_id IS NULL AND document_order IS NULL AND document_title IS NULL")).isEqualTo(1);
        assertThatThrownBy(()->execute("UPDATE exam_content_blocks SET document_id='bad'")).isInstanceOf(SQLException.class);
    }

    @Test void mock200RoundTripsCompleteGraphAnd54AudioFilesAfterCommit() throws Exception {
        start(true);expect(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),token),201,null);
        // Connection độc lập nhìn thấy đầy đủ rows ngay khi nhận201: đã commit.
        assertThat(number("SELECT content_version FROM exams")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM exam_questions")).isEqualTo(200);
        assertThat(number("SELECT count(*) FROM exam_options")).isEqualTo(775);
        assertThat(number("SELECT count(*) FROM exam_question_groups")).isEqualTo(76);
        assertThat(number("SELECT count(*) FROM exam_media_assets")).isEqualTo(57);
        for(var m:fixture.media()) {
            assertThat(strings("SELECT media_type||'/'||local_file||'/'||mime_type||'/'||size_bytes||'/'||sha256||'/'||COALESCE(width_px::text,'NULL')||'/'||COALESCE(height_px::text,'NULL')||'/'||COALESCE(duration_ms::text,'NULL') FROM exam_media_assets WHERE media_id='"+m.mediaId()+"'"))
                    .containsExactly(m.mediaType()+"/"+m.localFile()+"/"+m.mimeType()+"/"+m.sizeBytes()+"/"+m.sha256()+"/"+nullable(m.widthPx())+"/"+nullable(m.heightPx())+"/"+nullable(m.durationMs()));
        }
        assertThat(strings("SELECT part||'/'||count(*) FROM exam_questions GROUP BY part ORDER BY part"))
                .containsExactly("1/6","2/25","3/39","4/30","5/30","6/16","7/54");
        for(var q:fixture.questions()) {
            String id=q.questionId();
            assertThat(strings("SELECT correct_option FROM exam_questions WHERE question_id='"+id+"'")).containsExactly(q.scoring().correctOption());
            assertThat(strings("SELECT option_id||'/'||option_text FROM exam_options WHERE question_id='"+id+"' ORDER BY order_index"))
                    .containsExactlyElementsOf(q.options().stream().map(o->o.optionId()+"/"+(o.text()==null?"":o.text())).toList());
        }
        for(var g:fixture.groups()) {
            assertThat(number("SELECT order_index FROM exam_question_groups WHERE group_id='"+g.groupId()+"'")).isEqualTo(g.orderIndex());
            for(var d:g.documents()) assertThat(strings("SELECT document_id||'/'||document_order||'/'||COALESCE(document_title,'')||'/'||block_id FROM exam_content_blocks WHERE group_id='"+g.groupId()+"' AND document_id='"+d.documentId()+"' ORDER BY order_index"))
                    .containsExactlyElementsOf(d.blocks().stream().map(b->d.documentId()+"/"+d.orderIndex()+"/"+(d.title()==null?"":d.title())+"/"+b.blockId()).toList());
        }
        assertThat(strings("SELECT DISTINCT document_title FROM exam_content_blocks WHERE group_id='g147' ORDER BY document_title"))
                .containsExactly("MOCK Email","MOCK Notice","MOCK Reply");
        assertThat(strings("SELECT document_id||'/'||order_index FROM exam_content_blocks WHERE group_id='g147' ORDER BY order_index"))
                .containsExactly("email/1","notice/2","notice/3","reply/4");
        var expected=fixture.groups().stream().filter(g->g.part()<=4).map(g->g.blocks().stream().filter(b->b.type()==BlockType.AUDIO).findFirst().orElseThrow().mediaId())
                .map(id->fixture.media().stream().filter(m->m.mediaId().equals(id)).findFirst().orElseThrow().localFile()).toList();
        assertThat(strings("SELECT DISTINCT audio_file FROM exam_questions WHERE section='LISTENING' ORDER BY audio_file")).containsExactlyElementsOf(expected);
        assertThat(expected).hasSize(54);
        assertThat(number("SELECT count(*) FROM exam_questions WHERE passage_text IS NOT NULL OR (section='READING' AND audio_file IS NOT NULL)")).isZero();
        assertThat(number("SELECT count(*) FROM exam_questions q JOIN exam_sources s ON s.exam_id=q.exam_id AND s.id=q.source_id WHERE s.option_content_status='VERIFIED'")).isEqualTo(200);
        expect(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),token),409,"CONFLICT");
        var create=new CreateSessionRequest("BLOCKED",fixture.exam().examId(),"MOCK",3600,List.of("proctor1"),List.of("candidate1"));
        expect(post("/api/v1/sessions",RichExamFixtures.JSON.toJson(create),token),409,"INVALID_STATE");
        assertThat(number("SELECT count(*) FROM exam_sessions")).isZero();
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isZero();
    }

    @Test void allInvalidGraphsRejectBeforeDatabaseMutationOnRealHttp() throws Exception {
        start(false);
        for(var mutation:RichExamImportTest.invalidGraphs().toList()) {
            var tree=RichExamFixtures.tree();mutation.change().accept(tree);
            expect(post("/api/v2/exams/import",tree.toString(),token),400,"INVALID_INPUT");
            emptyImport();
        }
        expect(post("/api/v2/exams/import"," ".repeat(RichExamJson.MAX_BYTES+1),token),413,"INVALID_INPUT");
        for(String body:RichExamImportTest.invalidJson().toList()) expect(post("/api/v2/exams/import",body,token),400,"INVALID_INPUT");
        for(String parameter:List.of("access_token","api_key","client_secret","X-Amz-Signature","refreshToken","%61ccess%5Ftoken")) {
            var tree=RichExamFixtures.tree();
            RichExamFixtures.at(tree,"sources",0).addProperty("sourceUrl","https://example.invalid/a?"+parameter+"=MOCK");
            expect(post("/api/v2/exams/import",tree.toString(),token),400,"INVALID_INPUT");
        }
        for(String escape:List.of("\\u0000","\\ud800","\\udc00")) {
            String body=RichExamFixtures.tree().toString().replace("\"MOCK EX02 200 questions\"","\"MOCK"+escape+"\"");
            expect(post("/api/v2/exams/import",body,token),400,"INVALID_INPUT");
        }
        emptyImport();
    }

    @Test void multipleSourceRefsMapToReturnedDatabaseIdsWithinExamScope() throws Exception {
        start(true);var tree=RichExamFixtures.tree();
        var other=RichExamFixtures.at(tree,"sources",0).deepCopy();
        other.addProperty("sourceRef","second");other.addProperty("sourceName","MOCK second source");
        other.addProperty("sourceSha256","b".repeat(64));tree.getAsJsonArray("sources").add(other);
        RichExamFixtures.question(tree,0).addProperty("sourceRef","second");
        RichExamFixtures.media(tree,0).addProperty("sourceRef","second");
        expect(post("/api/v2/exams/import",tree.toString(),token),201,null);
        assertThat(number("SELECT count(*) FROM exam_sources")).isEqualTo(2);
        assertThat(strings("SELECT s.source_name FROM exam_questions q JOIN exam_sources s ON s.exam_id=q.exam_id AND s.id=q.source_id WHERE q.question_id='q001'"))
                .containsExactly("MOCK second source");
        assertThat(strings("SELECT s.source_name FROM exam_media_assets m JOIN exam_sources s ON s.exam_id=m.exam_id AND s.id=m.source_id WHERE m.media_id='img-grid'"))
                .containsExactly("MOCK second source");
        assertThat(number("SELECT count(*) FROM exam_questions q JOIN exam_sources s ON s.exam_id=q.exam_id AND s.id=q.source_id WHERE s.source_sha256='"+fixture.sources().getFirst().sourceSha256()+"'")).isEqualTo(199);
    }

    @Test void authAndRolePrecedeMalformedBodyAndMissingMediaAndRichReadsFailClosed() throws Exception {
        start(false);String candidate=login("candidate1");
        expect(post("/api/v2/exams/import","{",null),401,"UNAUTHORIZED");
        expect(post("/api/v2/exams/import","{",candidate),403,"FORBIDDEN");
        expect(post("/api/v2/exams/import"," ".repeat(RichExamJson.MAX_BYTES+1),candidate),403,"FORBIDDEN");
        expect(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),candidate),403,"FORBIDDEN");
        expect(get("/api/v2/attempts/MOCK/images/img-grid",candidate),403,"FORBIDDEN");
        for(String route:List.of("/api/v2/attempts/MOCK/exam","/api/v2/attempts/MOCK/preparation"))
            expect(get(route,candidate),403,"FORBIDDEN");
        emptyImport();
    }

    @ParameterizedTest @ValueSource(strings={"MISSING","HASH","SIZE","PNG_DECODE","DIMENSION","WAV_SIGNATURE"})
    void missingCorruptOrMisdeclaredActualMediaReturns409AndNoRows(String fault) throws Exception {
        start(true);var tree=RichExamFixtures.tree();
        var m=fault.equals("WAV_SIGNATURE")?fixture.media().get(3):fixture.media().getFirst();
        Path file=(m.mediaType()==vn.edu.toeic.protocol.exam.rich.MediaType.AUDIO?audio:image).resolve(fixture.exam().examId()).resolve(m.localFile());
        switch(fault) {
            case "MISSING"->Files.delete(file);
            case "HASH"->{byte[] bytes=Files.readAllBytes(file);bytes[bytes.length-1]^=1;Files.write(file,bytes);}
            case "SIZE"->Files.write(file,new byte[]{1});
            case "DIMENSION"->RichExamFixtures.media(tree,0).addProperty("widthPx",7);
            case "PNG_DECODE","WAV_SIGNATURE"->{
                byte[] bytes=fault.equals("PNG_DECODE")?new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0}:new byte[204];
                Files.write(file,bytes);
                var json=RichExamFixtures.media(tree,fault.equals("WAV_SIGNATURE")?3:0);
                json.addProperty("sizeBytes",bytes.length);
                json.addProperty("sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
            default->throw new AssertionError();
        }
        expect(post("/api/v2/exams/import",tree.toString(),token),409,"INVALID_STATE");emptyImport();
    }

    @ParameterizedTest @ValueSource(strings={"MIDWAY","DEFERRED"})
    void databaseFailureRollsBackExamAndEveryDependentRow(String fault) throws Exception {
        start(true);
        if(fault.equals("MIDWAY")) {
            execute("CREATE FUNCTION fail_import() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'MOCK midway failure'; END $$");
            execute("CREATE TRIGGER fail_import BEFORE INSERT ON exam_content_blocks FOR EACH ROW EXECUTE FUNCTION fail_import()");
        } else {
            execute("CREATE FUNCTION fail_import() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.correct_option='Z'; RETURN NEW; END $$");
            execute("CREATE TRIGGER fail_import BEFORE INSERT ON exam_questions FOR EACH ROW EXECUTE FUNCTION fail_import()");
        }
        expect(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),token),500,"RETRYABLE_SERVER_ERROR");
        emptyImport();
    }

    @Test void fileChangesInsideTransactionAreRecheckedAndRollBack() throws Exception {
        start(true);var jdbc=context.getBean(JdbcClient.class);
        var changingRepository=new RichExamRepository(jdbc) {
            @Override void insert(RichExamImportRequest request,long userId) {
                super.insert(request,userId);
                try {Files.write(image.resolve(request.exam().examId()).resolve(request.media().getFirst().localFile()),new byte[]{1});}
                catch(java.io.IOException failure){throw new IllegalStateException(failure);}
            }
        };
        var service=new RichExamService(context.getBean(AuthorizationService.class),new RichExamValidator(),
                new RichMediaValidator(audio.toString(),image.toString()),changingRepository,context.getBean(PlatformTransactionManager.class));
        long owner=number("SELECT id FROM user_accounts WHERE username='proctor1'");
        assertThatThrownBy(()->service.importExam(new AuthenticatedUser(owner,"proctor1",Role.PROCTOR),fixture))
                .isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(409));
        emptyImport();
    }

    @Test void concurrentDuplicateUsesBarrierAndOnlyOneWriterCommits() throws Exception {
        start(true);
        CountDownLatch bothAtInsert=new CountDownLatch(2),release=new CountDownLatch(1);
        var repository=new RichExamRepository(context.getBean(JdbcClient.class)) {
            @Override void insert(RichExamImportRequest request,long userId) {
                bothAtInsert.countDown();
                try {if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("barrier timeout");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
                super.insert(request,userId);
            }
        };
        var service=new RichExamService(context.getBean(AuthorizationService.class),new RichExamValidator(),
                new RichMediaValidator(audio.toString(),image.toString()),repository,context.getBean(PlatformTransactionManager.class));
        var proctor=new AuthenticatedUser(number("SELECT id FROM user_accounts WHERE username='proctor1'"),"proctor1",Role.PROCTOR);
        Callable<Integer> importOnce=()->{try{service.importExam(proctor,fixture);return 201;}catch(ExamApiException e){return e.status().value();}};
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(importOnce);var second=pool.submit(importOnce);
            try {assertThat(bothAtInsert.await(15,TimeUnit.SECONDS)).isTrue();} finally {release.countDown();}
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(number("SELECT count(*) FROM exams")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM exam_questions")).isEqualTo(200);
        assertThat(number("SELECT count(*) FROM exam_options")).isEqualTo(775);
    }

    @Test void legacyV1ImportSessionReadAutosaveSubmitAndScoringKeepQd21() throws Exception {
        start(false);String candidate=login("candidate1");
        var question=new ExamQuestionImportDto("R1","READING",5,null,null,null,"MOCK prompt","B",1,
                List.of(new ExamOptionImportDto("A","MOCK A",1),new ExamOptionImportDto("B","MOCK B",2)));
        var request=new ExamImportRequest("LEGACY","MOCK","MOCK",List.of(question));
        expect(post("/api/v1/exams/import",RichExamFixtures.JSON.toJson(request),candidate),403,null);
        expect(post("/api/v1/exams/import",RichExamFixtures.JSON.toJson(request),token),200,null);
        expect(post("/api/v1/exams/import",RichExamFixtures.JSON.toJson(request),token),400,"INVALID_INPUT");
        assertThat(number("SELECT content_version FROM exams")).isEqualTo(1);
        var create=new CreateSessionRequest("S","LEGACY","MOCK",3600,List.of("proctor1"),List.of("candidate1"));
        expect(post("/api/v1/sessions",RichExamFixtures.JSON.toJson(create),token),200,null);
        String path="/api/v1/attempts/S-candidate1";
        var exam=expect(get(path+"/exam",candidate),200,null);
        assertThat(exam.body()).doesNotContain("correctOption","sourcePath","sourceUrl","licenseDocument","permissionNote");
        var writer=JsonParser.parseString(expect(post(path+"/takeover",RichExamFixtures.JSON.toJson(new TakeoverWriterRequest("MOCK-takeover","S-candidate1",null)),candidate),200,null).body()).getAsJsonObject();
        long epoch=writer.get("writerEpoch").getAsLong();
        expect(post(path+"/answers",RichExamFixtures.JSON.toJson(new AutosaveAnswersRequest("MOCK-save","S-candidate1",epoch,1,Map.of("R1","B"))),candidate),200,null);
        expect(post(path+"/submit",RichExamFixtures.JSON.toJson(new SubmitExamRequest("MOCK-submit","S-candidate1",epoch,2,Map.of("R1","B"))),candidate),200,null);
        assertThat(number("SELECT score FROM monitoring_attempts WHERE attempt_id='S-candidate1'")).isEqualTo(1);
        assertThat(strings("SELECT state FROM monitoring_attempts WHERE attempt_id='S-candidate1'")).containsExactly("SUBMITTED");
        assertThat(strings("SELECT state FROM exam_sessions WHERE session_id='S'")).containsExactly("SCHEDULED");
    }

    private List<String> snapshot() throws SQLException {
        var rows=new ArrayList<String>();
        for(String table:List.of("user_accounts","exams","exam_sources","exam_questions","exam_options","exam_content_blocks","exam_sessions","monitoring_attempts"))
            rows.addAll(strings("SELECT (to_jsonb(t)-'content_version'-'option_content_status'-'document_id'-'document_order'-'document_title')::text FROM "+table+" t ORDER BY 1"));
        return rows;
    }
    private void emptyImport() throws SQLException {
        for(String table:List.of("exams","exam_sources","exam_question_groups","exam_media_assets","exam_questions","exam_options","exam_content_blocks"))
            assertThat(number("SELECT count(*) FROM "+table)).as(table).isZero();
    }
    private Flyway flyway(String target) {
        var config=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true);
        if(target!=null)config.target(target);
        return config.load();
    }
    private void start(boolean media) throws Exception {
        if(media)RichExamFixtures.files(audio,image,fixture);
        var props=new HashMap<String,Object>();
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);
        props.put("spring.datasource.username",user);props.put("spring.datasource.password",password);
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);
        props.put("toeic.seed.password",env("TOEIC_SEED_PASSWORD"));
        props.put("toeic.audio.root",audio.toString());props.put("toeic.image.root",image.toString());
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled","false");props.put("logging.level.root","ERROR");
        var app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
        app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-ex03",props)));
        context=app.run();base="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();token=login("proctor1");
    }
    private String login(String name) throws Exception {
        var body=RichExamFixtures.JSON.toJson(Map.of("username",name,"password",env("TOEIC_SEED_PASSWORD"),"requestId",UUID.randomUUID().toString()));
        return JsonParser.parseString(expect(post("/api/v1/auth/login",body,null),200,null).body()).getAsJsonObject().get("token").getAsString();
    }
    private HttpResponse<String> post(String path,String body,String bearer) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(bearer!=null)request.header("Authorization","Bearer "+bearer);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path,String bearer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+bearer).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> expect(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).as("HTTP status (body không in vào log)").isEqualTo(status);
        if(code!=null)assertThat(JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("jdbc:postgresql","password_hash","StackTrace","java.nio.file",":\\");
        return response;
    }
    private void execute(String sql) throws SQLException {try(var s=db.createStatement()){s.execute(sql);}}
    private long number(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private List<String> strings(String sql) throws SQLException {var rows=new ArrayList<String>();try(var s=db.createStatement();var r=s.executeQuery(sql)){while(r.next())rows.add(r.getString(1));}return rows;}
    private static String env(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing test setting "+name);return value;}
    private static String nullable(Object value){return value==null?"NULL":value.toString();}
}
