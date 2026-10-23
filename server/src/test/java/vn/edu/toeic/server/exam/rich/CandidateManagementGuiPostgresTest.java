package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.*;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import vn.edu.toeic.server.exam.ExamService;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.monitoring.JdbcAttemptScopeStore;
import vn.edu.toeic.server.monitoring.signal.SignalService;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import java.util.function.BooleanSupplier;

/**
 * Room Admission Phase 1: real PostgreSQL/Flyway/HTTP/WebSocket authentication and V2 invariants.
 * Đề MOCK200 CC0 tự tạo của fixtures EX-02, không phải dữ liệu TOEIC thật. GUI/physical NOT RUN ở đây.
 */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
@ExtendWith(OutputCaptureExtension.class)
@EnabledIfEnvironmentVariable(named="TOEIC_CANDIDATE_GUI",matches="true")
class CandidateManagementGuiPostgresTest {
    private static final String EXAM="mock-ex02-200";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String proctor,owner,other;

    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="candidate_gui_"+UUID.randomUUID().toString().replace("-","");
        admin=DriverManager.getConnection(url,user,password);
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
        start();
        proctor=login("proctor1");owner=login("candidate1");other=login("candidate2");
        check(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),proctor),201,null);
    }
    @AfterEach void cleanup() throws Exception {
        try {
            closeUi();
            if(http!=null)http.close();if(app!=null)app.close();if(db!=null)db.close();
        } finally {if(admin!=null){try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}admin.close();}}
    }

    private ProctorRedesignView view;private Stage stage;private RealtimeClient realtime;
    private String previousSettings;
    @BeforeAll static void toolkit() {try{Platform.startup(()->{});}catch(IllegalStateException alreadyStarted){}Platform.setImplicitExit(false);}

    @Test void productionProctorCreatesCandidateSelectsByNameAndExplicitlyAssignsExactlyOneAttempt() throws Exception {
        openUi();openForm("B23GUI-PG","Nguyễn Văn An");
        fx(()->{((Button)dialogNode("candidate-save")).fire();return null;});
        awaitFx(()->dialogNode("candidate-code")==null);
        awaitFx(()->candidateTable().getItems().stream().anyMatch(c->c.username().equals("B23GUI-PG")));
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isZero();
        assertThat(number("SELECT count(*) FROM exam_sessions")).isZero();
        assertThat(number("SELECT count(*) FROM exam_rooms")+number("SELECT count(*) FROM exam_room_members")).isZero();
        assertThat(text("SELECT role FROM user_accounts WHERE username='B23GUI-PG'")).isEqualTo("CANDIDATE");
        fx(()->{
            TableView<CandidateCatalogItem> table=candidateTable();table.applyCss();table.layout();
            CheckBox select=table.lookupAll(".check-box").stream().filter(n->n instanceof CheckBox && n.getAccessibleText()!=null && n.getAccessibleText().contains("B23GUI-PG"))
                    .map(n->(CheckBox)n).findFirst().orElseThrow();select.fire();
            @SuppressWarnings("unchecked") TableView<ExamCatalogItem> exams=(TableView<ExamCatalogItem>)view.lookup("#v2-exams");
            exams.getSelectionModel().select(0);return null;
        });
        awaitFx(()->!view.lookup("#v2-create").isDisabled());
        capture("candidate-created-selected");
        fx(()->{((Button)view.lookup("#v2-create")).fire();return null;});
        awaitFx(()->view.lookup("#v2-created-session")!=null);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM monitoring_attempts a JOIN user_accounts u ON u.id=a.candidate_user_id WHERE u.username='B23GUI-PG' AND a.deadline_at IS NULL AND a.state='ACTIVE'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM attempt_audio_readiness")+number("SELECT count(*) FROM attempt_visual_readiness")).isZero();
        assertThat(fx(()->view.lookup("#v2-start").isDisabled())).isTrue();
        capture("candidate-assigned-not-started");
    }

    @Test void duplicateCodeFromRealBackendStaysInFormAndCannotOverwriteExistingAccount() throws Exception {
        String before=text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='candidate1'");
        openUi();openForm("candidate1","Overwrite attempt");
        fx(()->{((Button)dialogNode("candidate-save")).fire();return null;});
        awaitFx(()->dialogNode("candidate-form-status") instanceof Label label && label.getText().contains("đã tồn tại"));
        assertThat(fx(()->((TextField)dialogNode("candidate-name")).getText())).isEqualTo("Overwrite attempt");
        assertThat(text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='candidate1'")).isEqualTo(before);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isZero();
    }

    private void openUi() throws Exception {
        previousSettings=System.getProperty("toeic.proctor.settingsFile");
        System.setProperty("toeic.proctor.settingsFile",temporary.resolve("proctor-ui.properties").toString());
        vn.edu.toeic.protocol.auth.LoginResponse login;
        try(var logins=new LoginApiClient()){login=logins.login(base,"proctor1",env("TOEIC_SEED_PASSWORD")).get(15,TimeUnit.SECONDS);}
        realtime=new RealtimeClient(base,login.token());
        fx(()->{
            view=ProctorRedesignView.forLogin(base,login,realtime,()->{},()->{});
            stage=new Stage();stage.setScene(new Scene(view,1366,1000));stage.show();
            ((ToggleButton)view.lookup("#nav-session-controls")).fire();return null;
        });
        realtime.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(10,TimeUnit.SECONDS);
        awaitFx(()->view.lookup("#v2-exams") instanceof TableView<?> table && !table.getItems().isEmpty());
    }

    private void openForm(String code,String name) throws Exception {
        fx(()->{MenuButton menu=(MenuButton)view.lookup("#candidate-management");menu.show();menu.getItems().getFirst().fire();menu.hide();return null;});
        awaitFx(()->dialogNode("candidate-code")!=null);
        fx(()->{((TextField)dialogNode("candidate-code")).setText(code);((TextField)dialogNode("candidate-name")).setText(name);return null;});
    }
    @SuppressWarnings("unchecked") private TableView<CandidateCatalogItem> candidateTable(){return (TableView<CandidateCatalogItem>)view.lookup("#v2-candidates");}
    private static Node dialogNode(String id){for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){Node n=w.getScene().getRoot().lookup("#"+id);if(n!=null)return n;}return null;}
    private static <T> T fx(Callable<T> action) throws Exception {
        var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable t){result.completeExceptionally(t);}});return result.get(15,TimeUnit.SECONDS);
    }
    private static void awaitFx(BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<end){if(fx(condition::getAsBoolean))return;Thread.sleep(25);}throw new AssertionError("Candidate management GUI condition timed out");
    }
    private void closeUi() throws Exception {
        fx(()->{if(view!=null)view.close();if(stage!=null)stage.close();return null;});
        if(realtime!=null)realtime.close();
        if(previousSettings==null)System.clearProperty("toeic.proctor.settingsFile");else System.setProperty("toeic.proctor.settingsFile",previousSettings);
    }
    private void capture(String name) throws Exception {
        var snapshot=fx(()->stage.getScene().snapshot(null));
        int width=(int)snapshot.getWidth(),height=(int)snapshot.getHeight();
        int[] pixels=new int[width*height];snapshot.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
        var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,width,height,pixels,0,width);
        Path out=Path.of("target","candidate-management-gui");Files.createDirectories(out);
        javax.imageio.ImageIO.write(image,"png",out.resolve(name+".png").toFile());
    }

    // ---- helpers ----
    private void assertNotStarted(String session) throws SQLException {
        assertThat(text("SELECT state FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("SCHEDULED");
        assertThat(number("SELECT count(*) FROM exam_sessions WHERE session_id='"+session+"' AND (listening_start_id IS NOT NULL OR started_at IS NOT NULL)")).isZero();
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session
                +"' AND (deadline_at IS NOT NULL OR listening_start_id IS NOT NULL OR committed_visual_manifest_id IS NOT NULL)")).isZero();
    }
    private JsonObject createSession() throws Exception {return createSession(List.of(id("candidate1"),id("candidate2")));}
    private JsonObject createSession(List<Long> candidates) throws Exception {return json(check(post("/api/v2/sessions",createBody(candidates),proctor),201,null));}
    private String createBody(List<Long> candidates) throws Exception {
        var body=new JsonObject();body.addProperty("examId",EXAM);body.addProperty("durationMinutes",120);
        var c=new JsonArray();candidates.forEach(c::add);body.add("candidateUserIds",c);
        var p=new JsonArray();p.add(id("proctor1"));body.add("proctorUserIds",p);return body.toString();
    }
    private String attemptOf(JsonObject created,String username) throws Exception {
        long candidate=id(username);
        for(var e:created.getAsJsonArray("attempts"))if(e.getAsJsonObject().get("candidateUserId").getAsLong()==candidate)return e.getAsJsonObject().get("attemptId").getAsString();
        throw new AssertionError("missing attempt");
    }
    private long takeover(String attempt,String token) throws Exception {
        return json(check(post("/api/v1/attempts/"+attempt+"/takeover",takeoverBody(attempt),token),200,null)).get("writerEpoch").getAsLong();
    }
    private static String takeoverBody(String attempt) {
        var b=new JsonObject();b.addProperty("requestId",UUID.randomUUID().toString());b.addProperty("attemptId",attempt);b.addProperty("clientSessionId","mock-client");return b.toString();
    }
    private JsonObject audioManifest(String attempt,String token) throws Exception {return json(check(get("/api/v1/attempts/"+attempt+"/audio/manifest",token),200,null));}
    private String audioBody(String attempt,String token,long writer,boolean ready) throws Exception {
        var m=audioManifest(attempt,token);
        var b=new JsonObject();
        b.addProperty("requestId",UUID.randomUUID().toString());b.addProperty("attemptId",attempt);b.addProperty("writerEpoch",writer);b.addProperty("ready",ready);
        b.addProperty("manifestId",m.get("manifestId").getAsString());var sums=new JsonObject();
        if(ready)for(var f:m.getAsJsonArray("files"))sums.addProperty(f.getAsJsonObject().get("fileName").getAsString(),f.getAsJsonObject().get("sha256").getAsString());
        b.add("checksums",sums);b.addProperty("speakerTestPassed",ready);
        return b.toString();
    }
    private void audioReady(String attempt,String token,long writer) throws Exception {
        assertThat(json(check(post("/api/v1/attempts/"+attempt+"/audio/ready",audioBody(attempt,token,writer,true),token),200,null)).get("ready").getAsBoolean()).isTrue();
    }
    private JsonObject preparation(String attempt,String token) throws Exception {return json(check(get("/api/v2/attempts/"+attempt+"/preparation",token),200,null));}
    private static String visualPath(String attempt) {return "/api/v2/attempts/"+attempt+"/visual/ready";}
    private static String attest(String attempt,long writer,JsonObject prep,String requestId) {
        var m=prep.getAsJsonObject("visualManifest");var b=new JsonObject();
        b.addProperty("requestId",requestId);b.addProperty("attemptId",attempt);b.addProperty("writerEpoch",writer);
        b.addProperty("preparationEpoch",m.get("preparationEpoch").getAsString());b.addProperty("manifestId",m.get("manifestId").getAsString());
        b.addProperty("action","ATTEST");b.addProperty("firstSlotId","p1-1");
        var assets=new TreeMap<String,JsonObject>();
        for(var s:m.getAsJsonArray("slots")) {
            var slot=s.getAsJsonObject();var a=new JsonObject();
            for(String k:List.of("mediaId","sha256","sizeBytes","widthPx","heightPx"))a.add(k,slot.get(k));
            assets.put(slot.get("mediaId").getAsString(),a);
        }
        var array=new JsonArray();assets.values().forEach(array::add);b.add("verifiedAssets",array);return b.toString();
    }
    private static String invalidate(String attempt,long writer,JsonObject prep,String requestId) {
        var m=prep.getAsJsonObject("visualManifest");var b=new JsonObject();
        b.addProperty("requestId",requestId);b.addProperty("attemptId",attempt);b.addProperty("writerEpoch",writer);
        b.addProperty("preparationEpoch",m.get("preparationEpoch").getAsString());b.addProperty("manifestId",m.get("manifestId").getAsString());
        b.addProperty("action","INVALIDATE");b.add("firstSlotId",JsonNull.INSTANCE);b.add("verifiedAssets",new JsonArray());return b.toString();
    }
    private String attestOk(String attempt,String token,long writer,String requestId) throws Exception {
        var prep=preparation(attempt,token);
        var r=json(check(post(visualPath(attempt),attest(attempt,writer,prep,requestId),token),200,null));
        assertThat(r.get("state").getAsString()).isEqualTo("READY");return r.get("manifestId").getAsString();
    }
    private static String startPath(String session) {return "/api/v1/sessions/"+session+"/listening/start";}
    private static String start(String requestId,String manifest) {var b=new JsonObject();b.addProperty("requestId",requestId);b.addProperty("manifestId",manifest);return b.toString();}
    private static String answers(String attempt,long writer,long revision,Map<String,String> answers) {
        var b=new JsonObject();b.addProperty("requestId",UUID.randomUUID().toString());b.addProperty("attemptId",attempt);
        b.addProperty("writerEpoch",writer);b.addProperty("answerRevision",revision);var a=new JsonObject();answers.forEach(a::addProperty);b.add("answers",a);return b.toString();
    }
    private void start() throws Exception {
        start(false);
    }
    private void start(boolean listeningMaintenance) throws Exception {
        Path audio=temporary.resolve("audio"),images=temporary.resolve("images");
        RichExamFixtures.files(audio,images,RichExamFixtures.request());
        var props=new HashMap<String,Object>();
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",user);props.put("spring.datasource.password",password);
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);props.put("toeic.seed.password",env("TOEIC_SEED_PASSWORD"));
        props.put("toeic.audio.root",audio.toString());props.put("toeic.image.root",images.toString());
        // Lease dài để test không phụ thuộc tốc độ máy; hết hạn được tạo tường minh bằng SQL.
        props.put("toeic.visual.lease-ms","60000");
        props.put("toeic.monitoring.signal-experiment-enabled","true");
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled",String.valueOf(listeningMaintenance));props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-ex07",props)));
        app=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    private String login(String name) throws Exception {
        var body=RichExamFixtures.JSON.toJson(Map.of("username",name,"password",env("TOEIC_SEED_PASSWORD"),"requestId",UUID.randomUUID().toString()));
        return json(check(post("/api/v1/auth/login",body,null),200,null)).get("token").getAsString();
    }
    private long id(String username) throws SQLException {return number("SELECT id FROM user_accounts WHERE username='"+username+"'");}
    private HttpResponse<String> get(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET();
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> check(HttpResponse<String> r,int status,String code) {
        assertThat(r.statusCode()).as("HTTP status for %s", r.uri().getPath()).isEqualTo(status);
        if(code!=null)assertThat(JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
        if(r.headers().firstValue("Content-Type").orElse("").contains("json"))
            assertThat(r.body()).doesNotContain("correctOption","sourcePath","sourceUrl","jdbc:postgresql");
        return r;
    }
    private static JsonObject json(HttpResponse<String> r) {return JsonParser.parseString(r.body()).getAsJsonObject();}
    private void sql(String sql) throws SQLException {try(var s=db.createStatement()){s.execute(sql);}}
    private long number(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private String text(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static String env(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("Missing test setting "+name);return v;}
}
