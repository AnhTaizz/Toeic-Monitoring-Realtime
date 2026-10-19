package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonParser;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** PG/HTTP thật; MOCK200 CC0. EX07 chưa có nên không giả authority để gọi download200. */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
class RichImagePostgresTest {
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="ex05_"+UUID.randomUUID().toString().replace("-","");
        admin=DriverManager.getConnection(url,user,password);
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
    }
    @AfterEach void cleanup() throws Exception {
        try {
            if(http!=null)http.close();if(app!=null)app.close();if(db!=null)db.close();
        } finally {if(admin!=null){try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}admin.close();}}
    }
    @Test void ownerForeignRoleAnonymousAndConditionalRequestsCannotBypassMissingV2Authority() throws Exception {
        start();String proctor=login("proctor1"),owner=login("candidate1"),foreign=login("candidate2");
        importMock(proctor);
        sql("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) SELECT 'MOCK-S','mock-ex02-200','MOCK',3600,id FROM user_accounts WHERE username='proctor1'");
        sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id) SELECT 'MOCK-A',id,'ACTIVE','MOCK-S','mock-ex02-200' FROM user_accounts WHERE username='candidate1'");
        String path="/api/v2/attempts/MOCK-A/images/img-grid";
        check(get(path,null),401,"UNAUTHORIZED");check(get(path,proctor),403,"FORBIDDEN");
        check(get(path,foreign),403,"FORBIDDEN");check(get("/api/v2/attempts/unknown/images/img-grid",owner),403,"FORBIDDEN");
        for(String query:List.of("","?visualManifestId="+"a".repeat(64),"?localFile=escape.png","?visualManifestId=a&visualManifestId=b")) {
            var response=check(get(path+query,owner),409,"INVALID_STATE");
            assertThat(response.headers().firstValue("X-Image-SHA256")).isEmpty();
            assertThat(response.headers().firstValue("ETag")).isEmpty();
        }
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE deadline_at IS NOT NULL")).isZero();
    }
    @Test void manifestUsesPersistedPartOneGraphWithoutExposingOtherImagesOrAnswerData() throws Exception {
        start();importMock(login("proctor1"));
        var expected=RichExamFixtures.JSON.fromJson(Files.readString(RichExamFixtures.ROOT.resolve("visual-manifest.mock.json")),vn.edu.toeic.protocol.exam.rich.VisualManifest.class);
        var c=new RichImageSessionAuthority.Context("mock-ex02-200","mock-session","mock-attempt","SCHEDULED",2,expected.preparationEpoch(),null,null);
        var actual=app.getBean(PartOneVisualManifest.class).build(c);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.slots().stream().map(v->v.mediaId()).distinct()).containsExactly("img-grid");
        assertThat(number("SELECT count(*) FROM exam_media_assets WHERE media_type='IMAGE'")).isEqualTo(3);
        assertThat(RichExamFixtures.JSON.toJson(actual)).doesNotContain("correctOption","sourcePath","sourceUrl","altText","questionId","prompt");
        sql("UPDATE exam_content_blocks SET media_id='img-p4-only' WHERE question_id='q001' AND block_type='IMAGE'");
        // Nếu tên fixture đổi, FK làm test FAIL thay vì silently bỏ bước này.
        var changed=app.getBean(PartOneVisualManifest.class).build(c);
        assertThat(changed.manifestId()).isNotEqualTo(actual.manifestId());
    }
    @Test
    @EnabledIfEnvironmentVariable(named="TOEIC_RESERVED_V13_SCRIPT",matches=".+")
    void realReservedV13CannotBeAddedAfterV14WithDefaultFlywayPolicy() throws Exception {
        Path migration=temporary.resolve("migrations");Files.createDirectory(migration);
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(Path file:files.filter(p->p.getFileName().toString().matches("V(?:[1-9]|1[0-24])__.*\\.sql")).toList())
                Files.copy(file,migration.resolve(file.getFileName()));
        }
        var first=flyway(migration);first.migrate();first.validate();
        assertThat(number("SELECT max(version::int) FROM flyway_schema_history")).isEqualTo(14);
        Path reserved=Path.of(env("TOEIC_RESERVED_V13_SCRIPT"));
        assertThat(reserved.getFileName().toString()).isEqualTo("V13__experimental_monitoring_signals.sql");
        Files.copy(reserved,migration.resolve(reserved.getFileName()));
        assertThatThrownBy(()->flyway(migration).migrate()).hasMessageContaining("13");
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version='13'")).isZero();
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE NOT success")).isZero();
    }
    private Flyway flyway(Path path) {
        return Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("filesystem:"+path.toAbsolutePath()).cleanDisabled(true).load();
    }
    private void start() throws Exception {
        Path audio=temporary.resolve("audio"),images=temporary.resolve("images");
        RichExamFixtures.files(audio,images,RichExamFixtures.request());
        var props=new HashMap<String,Object>();
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",user);props.put("spring.datasource.password",password);
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);props.put("toeic.seed.password",env("TOEIC_SEED_PASSWORD"));
        props.put("toeic.audio.root",audio.toString());props.put("toeic.image.root",images.toString());
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled","false");props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-ex05",props)));
        app=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    private void importMock(String token) throws Exception {check(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),token),201,null);}
    private String login(String name) throws Exception {
        var body=RichExamFixtures.JSON.toJson(Map.of("username",name,"password",env("TOEIC_SEED_PASSWORD"),"requestId",UUID.randomUUID().toString()));
        return JsonParser.parseString(check(post("/api/v1/auth/login",body,null),200,null).body()).getAsJsonObject().get("token").getAsString();
    }
    private HttpResponse<String> get(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20))
                .header("If-None-Match","*").header("If-Modified-Since","Wed, 21 Oct 2015 07:28:00 GMT").header("Range","bytes=0-1").GET();
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> check(HttpResponse<String> r,int status,String code) {
        assertThat(r.statusCode()).as("HTTP status, không in response/token").isEqualTo(status);
        if(code!=null)assertThat(JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
        assertThat(r.body()).doesNotContain("correctOption","sourcePath","sourceUrl","jdbc:postgresql",":\\");return r;
    }
    private void sql(String sql) throws SQLException {try(var s=db.createStatement()){s.execute(sql);}}
    private long number(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private static String env(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("Missing test setting "+name);return v;}
}
