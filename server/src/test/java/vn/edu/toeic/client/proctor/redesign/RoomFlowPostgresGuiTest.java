package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.exam.rich.RichAttemptView;
import vn.edu.toeic.client.exam.rich.RichEntry;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.Banner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.toeic.client.ToeicClientApplication;
import vn.edu.toeic.protocol.exam.rich.*;
/** Real production Proctor + two independent Candidate applications; isolated PostgreSQL and MOCK CC0 media. */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
@EnabledIfEnvironmentVariable(named="TOEIC_ROOM_GUI",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomFlowPostgresGuiTest {
    private static final Path OUT=Path.of("target","room-flow-e2e");
    private static final String EXAM="mock-ex02-200";
    @TempDir static Path temporary;
    private String base,previousSettings,previousAudioCache,schema;
    private final String password=System.getenv("TOEIC_SEED_PASSWORD");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<String> log=new CopyOnWriteArrayList<>();
    private ConfigurableApplicationContext server;
    private Connection database;

    @BeforeAll void setup() throws Exception {
        FxSupport.start();Files.createDirectories(OUT);
        // Room-code entry and the old V2 room tab are hidden in the release default; this regression opts in.
        System.setProperty("toeic.ui.legacy","true");
        previousSettings=System.getProperty("toeic.proctor.settingsFile");
        previousAudioCache=System.getProperty("toeic.audio.cacheDirectory");
        System.setProperty("toeic.audio.cacheDirectory",temporary.resolve("client-audio-cache").toString());
        System.setProperty("toeic.proctor.settingsFile",temporary.resolve("proctor.properties").toString());
        String url="jdbc:postgresql://"+System.getenv("DB_HOST")+":"+System.getenv("DB_PORT")+"/"+System.getenv("DB_NAME");
        schema="room_gui_"+UUID.randomUUID().toString().replace("-","");
        database=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        try(var s=database.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        database.setSchema(schema);
        Path assets=Path.of("..","docs","plan-v2","fixtures","ex02","assets");
        for(String type:List.of("audio","images")){
            try(var paths=Files.walk(assets.resolve(type))){for(Path p:paths.filter(Files::isRegularFile).toList()){
                Path dest=temporary.resolve(type).resolve(assets.resolve(type).relativize(p));Files.createDirectories(dest.getParent());Files.copy(p,dest);
            }}
        }
        var props=new HashMap<String,Object>();props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",System.getenv("DB_USER"));props.put("spring.datasource.password",System.getenv("DB_PASSWORD"));
        props.put("toeic.admin.bootstrap.username","admin-e2e");props.put("toeic.admin.bootstrap.password",password+"-separate-admin");
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);props.put("toeic.seed.password",password);
        props.put("toeic.audio.root",temporary.resolve("audio").toString());props.put("toeic.image.root",temporary.resolve("images").toString());
        props.put("toeic.visual.lease-ms","60000");props.put("toeic.listening.maintenance-enabled","true");props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        // Logging initializes before context initializers; isolate this GUI fixture from earlier test contexts.
        spring.setDefaultProperties(Map.of("logging.level.root","ERROR","logging.level.org.springframework.jdbc","ERROR","logging.level.org.springframework.web","ERROR","debug","false"));
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-room-e2e",props)));
        server=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)server).getWebServer().getPort();
    }
    @AfterAll void cleanup() throws Exception {
        System.clearProperty("toeic.ui.legacy");
        http.close();if(server!=null)server.close();
        if(previousAudioCache==null)System.clearProperty("toeic.audio.cacheDirectory");else System.setProperty("toeic.audio.cacheDirectory",previousAudioCache);
        if(database!=null){try(var s=database.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}database.close();}
        if(previousSettings==null)System.clearProperty("toeic.proctor.settingsFile");else System.setProperty("toeic.proctor.settingsFile",previousSettings);
    }

    @Test void proctorCreatesRoomTwoCandidatesEnterPrepareListenAnswerAutosaveAndSubmit() throws Exception {
        LoginApiClient logins=new LoginApiClient();
        String suffix=UUID.randomUUID().toString().substring(0,8), proctorCode="P45-P-"+suffix,candidateCodeAdmin="P45-C-"+suffix;
        String proctorPassword=adminProvisionThroughUi(proctorCode,candidateCodeAdmin,logins);
        var proctor=logins.login(base,proctorCode,proctorPassword).get(15,TimeUnit.SECONDS);importFixture(proctor.token());
        long second=count("SELECT id FROM user_accounts WHERE username='"+candidateCodeAdmin+"'");
        var proctorApp=new ToeicClientApplication();var ps=FxSupport.fx(Stage::new);
        FxSupport.fxRun(()->{
            proctorApp.start(ps);((TextField)ps.getScene().lookup("#login-server-url")).setText(base);
            ((TextField)ps.getScene().lookup("#login-username")).setText(proctorCode);
            ((javafx.scene.control.PasswordField)ps.getScene().lookup("#login-password")).setText(proctorPassword);
            ((Button)ps.getScene().lookup("#login-submit")).fire();
        });
        // Old room-code/PIN tools live behind "Công cụ cũ" (toeic.ui.legacy=true); the default screen is the room view.
        await("production Proctor account login",()->FxSupport.fx(()->find(ps.getScene().getRoot(),ProctorRoomView.class)!=null),30);
        FxSupport.fxRun(()->((Button)ps.getScene().lookup("#proctor-legacy-tools")).fire());
        await("legacy tools window",()->FxSupport.fx(()->legacyView()!=null),30);
        var pv=FxSupport.fx(this::legacyView);
        FxSupport.fxRun(()->{ps.setWidth(1366);ps.setHeight(1000);});
        log.add("PASS: Proctor login through production application and real HTTP authentication");
        ToeicClientApplication[] clients=new ToeicClientApplication[2];Stage[] stages=new Stage[2];
        try {
            FxSupport.fxRun(()->((ToggleButton)pv.lookup("#nav-session-controls")).fire());
            var pane=legacyPane(pv);
            await("catalogs",()->FxSupport.fx(()->pane.controller().snapshot().catalog().stream().anyMatch(e->e.examId().equals(EXAM))
                && pane.controller().snapshot().candidates().size()>=2),20);
            String candidateCode="P4-E2E-"+UUID.randomUUID().toString().substring(0,8);
            FxSupport.fxRun(()->{var menu=(javafx.scene.control.MenuButton)pv.lookup("#candidate-management");menu.show();menu.getItems().getFirst().fire();menu.hide();});
            await("candidate form opened",()->FxSupport.fx(()->dialogNode("candidate-code")!=null),10);
            FxSupport.fxRun(()->{((TextField)dialogNode("candidate-code")).setText(candidateCode);((TextField)dialogNode("candidate-name")).setText("MOCK Phase 4 new candidate");((Button)dialogNode("candidate-save")).fire();});
            await("new candidate in catalog",()->FxSupport.fx(()->pane.controller().snapshot().candidates().stream().anyMatch(c->c.username().equals(candidateCode))),20);
            long first=FxSupport.fx(()->pane.controller().snapshot().candidates().stream().filter(c->c.username().equals(candidateCode)).findFirst().orElseThrow().userId());
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE candidate_user_id="+first)).isZero();
            log.add("PASS: candidate provisioned through production GUI before assignment; no automatic attempt");
            FxSupport.fxRun(()->{pane.controller().select(EXAM);pane.controller().toggleCandidate(first);
                ((TextField)pane.lookup("#v2-candidate-search")).clear();((Button)pane.lookup("#v2-candidate-find")).fire();});
            await("search restores other candidate",()->FxSupport.fx(()->pane.controller().snapshot().candidates().stream().anyMatch(c->c.userId()==second)),20);
            FxSupport.fxRun(()->pane.controller().toggleCandidate(second));
            assertThat(pane.controller().snapshot().selectedCandidates()).extracting(CandidateCatalogItem::userId).containsExactlyInAnyOrder(first,second);
            await("create enabled after selection rendered",()->FxSupport.fx(()->!pane.lookup("#v2-create").isDisabled()),10);
            FxSupport.fxRun(()->((Button)pane.lookup("#v2-create")).fire());
            await("room created",()->pane.controller().snapshot().room()!=null,20);
            var room=pane.controller().snapshot().room();String session=room.session().sessionId();
            assertThat(room.candidates()).hasSize(2);
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"' AND deadline_at IS NOT NULL")).isZero();
            assertThat(FxSupport.fx(()->pane.lookup("#v2-start").isDisabled())).isTrue();
            // Erase visible PINs before capturing evidence. Local tickets remain only in this test's memory.
            FxSupport.fxRun(()->pane.controller().forgetPins());screenshot(ps,"proctor-created-pins-hidden");
            String manifest=call("GET","/api/v1/sessions/"+session+"/audio/readiness",null,proctor.token(),200).get("manifestId").getAsString();
            String startBody="{\"requestId\":\"room-e2e-start\",\"manifestId\":\""+manifest+"\"}";
            assertStatus("START without READY",send("POST","/api/v1/sessions/"+session+"/listening/start",startBody,proctor.token()),409);
            List<LoginResponse> assertions=new ArrayList<>();
            for(int i=0;i<2;i++) {
                final int index=i;var ticket=room.candidates().get(i);
                FxSupport.fxRun(()->{clients[index]=new ToeicClientApplication();stages[index]=new Stage();clients[index].start(stages[index]);((Button)stages[index].getScene().lookup("#login-switch-mode")).fire();});
                FxSupport.fxRun(()->{
                    ((TextField)stages[index].getScene().lookup("#login-server-url")).setText(base);
                    ((TextField)stages[index].getScene().lookup("#room-entry-code")).setText(room.roomCode());
                    ((TextField)stages[index].getScene().lookup("#room-entry-candidate")).setText(ticket.username());
                    ((javafx.scene.control.PasswordField)stages[index].getScene().lookup("#room-entry-pin")).setText(ticket.pin());
                    ((Button)stages[index].getScene().lookup("#room-entry-submit")).fire();
                });
                awaitText(stages[i],"Bài thi đã sẵn sàng trên máy này",30);
                assertThat(FxSupport.fx(()->stages[index].getScene().lookup("#room-entry-pin"))).isNull();
                var joined=logins.joinRoom(base,room.roomCode(),ticket.username(),ticket.pin()).get(15,TimeUnit.SECONDS);
                assertThat(joined.attemptId()).isEqualTo(ticket.attemptId());assertions.add(joined.login());
                int audioFiles=call("GET","/api/v1/attempts/"+ticket.attemptId()+"/audio/manifest",null,joined.login().token(),200).getAsJsonArray("files").size();
                long preparing=System.nanoTime();
                clickWhenEnabled(stages[i],"Tải / chuẩn bị lại audio");clickWhenEnabled(stages[i],"Thử loa",120);
                log.add("Actual prepare-to-speaker-action: files="+audioFiles+", milliseconds="+(System.nanoTime()-preparing)/1_000_000);
                clickWhenEnabled(stages[i],"Tôi nghe được");clickWhenEnabled(stages[i],"Xác nhận sẵn sàng");
                awaitText(stages[i],"✓  Sẵn sàng — đang chờ giám thị bắt đầu",45);
                screenshot(stages[i],"candidate-"+i+"-ready");
            }
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isEqualTo(2);
            String a=room.candidates().getFirst().attemptId(),b=room.candidates().getLast().attemptId();
            assertStatus("cross-attempt scope",send("GET","/api/v2/attempts/"+b+"/preparation",null,assertions.getFirst().token()),403);
            await("both joined connected ready",()->FxSupport.fx(()->pane.controller().snapshot().presence()!=null
                && pane.controller().snapshot().presence().members().stream().allMatch(m->m.joinedAt()!=null && "ONLINE".equals(m.connectionStatus()))
                && !pane.lookup("#v2-start").isDisabled()),25);
            // Deterministic process observation over a separate real socket; dashboard must receive the warning.
            injectMonitoring(assertions.getFirst().token(),a);
            assertThat(count("SELECT count(*) FROM monitoring_events WHERE attempt_id='"+a+"' AND event_id='MOCK-room-e2e-event'")).isEqualTo(1);
            var events=call("GET","/api/v1/monitoring/attempts/"+a+"/events",null,proctor.token(),200).getAsJsonArray("events");
            String projectedId=java.util.stream.StreamSupport.stream(events.spliterator(),false).map(e->e.getAsJsonObject())
                    .filter(e->"chrome.exe".equalsIgnoreCase(e.get("processName").getAsString())).findFirst().orElseThrow().get("eventId").getAsString();
            await("monitoring reaches dashboard",()->FxSupport.fx(()->pv.room()!=null && pv.room().alerts().stream().anyMatch(e->projectedId.equals(e.id()))),20);
            screenshot(ps,"proctor-two-ready");
            Platform.runLater(()->((Button)pane.lookup("#v2-start")).fire());pressDialog(ButtonType.OK,10);
            await("START committed",()->pane.controller().snapshot().started()!=null,20);
            // Both players run concurrently; observe Part 1 before waiting for either full playlist.
            for(int i=0;i<2;i++) {
                final Stage startedStage=stages[i];
                await("exam opened",()->FxSupport.fx(()->startedStage.getScene().lookup("#room-pick-question")!=null),40);
            }
            for(int i=0;i<2;i++) {
                // Playback must actually complete. LOCKED is a failure, not a successful Listening run.
                try{awaitText(stages[i],"Phần nghe đã kết thúc",150);}catch(AssertionError failure){
                    var failed=call("GET","/api/v1/attempts/"+room.candidates().get(i).attemptId()+"/listening/status",null,assertions.get(i).token(),200);
                    log.add("Listening failure: phase="+failed.get("phase")+", reason="+failed.get("interruptionReason"));
                    throw failure;
                }
                pickQuestion(stages[i],1);clickRadio(stages[i],"option-q001-A");
                screenshot(stages[i],"candidate-"+i+"-listening-answered-no-replay");
                pickQuestion(stages[i],101);clickRadio(stages[i],"option-q101-A");
                pickQuestion(stages[i],147);clickRadio(stages[i],"option-q147-D");
                String attempt=room.candidates().get(i).attemptId(),token=assertions.get(i).token();
                await("autosave",()->{var status=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200);
                    return status.getAsJsonObject("answers").has("q001") && status.getAsJsonObject("answers").has("q101") && status.getAsJsonObject("answers").has("q147");},25);
                String deadline=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200).get("deadlineAt").getAsString();
                screenshot(stages[i],"candidate-"+i+"-reading-autosaved");
                long writer=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200).get("writerEpoch").getAsLong();
                var field=ToeicClientApplication.class.getDeclaredField("realtimeClient");field.setAccessible(true);
                var transport=(RealtimeClient)field.get(clients[i]);transport.disconnect();
                transport.connect(new RealtimeClient.Session(Set.of(attempt),null,null)).get(15,TimeUnit.SECONDS);
                var afterReconnect=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200);
                assertThat(afterReconnect.get("deadlineAt").getAsString()).isEqualTo(deadline);
                assertThat(afterReconnect.get("writerEpoch").getAsLong()).isEqualTo(writer);
                assertThat(call("GET","/api/v1/attempts/"+attempt+"/listening/status",null,token,200).get("phase").getAsString()).isEqualTo("COMPLETED");
                final Stage current=stages[i];
                // connect() completes at transport level. The FX listener and authoritative
                // reconnect probe must also finish before a user can press Submit.
                await("Submit enabled after reconnect",()->FxSupport.fx(()->{
                    var submit=(Button)current.getScene().lookup("#room-submit");return submit!=null&&!submit.isDisabled();
                }),20);
                Platform.runLater(()->((Button)current.getScene().lookup("#room-submit")).fire());pressDialog(ButtonType.YES,10);
                await("SUBMITTED",()->"SUBMITTED".equals(call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200).get("state").getAsString()),25);
                var result=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200);
                assertThat(result.get("deadlineAt").getAsString()).isEqualTo(deadline);assertThat(result.get("readingCorrect").getAsInt()).isEqualTo(1);
                screenshot(current,"candidate-"+i+"-submitted");closeDialogs();
            }
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"' AND state='SUBMITTED'")).isEqualTo(2);
            log.add("PASS: two independent PIN-entry applications, real media completion, Reading autosave, submit, monitoring dashboard");
        }finally{
            closeDialogs();FxSupport.fxRun(()->{for(int i=0;i<2;i++){if(clients[i]!=null)clients[i].stop();if(stages[i]!=null)stages[i].close();}proctorApp.stop();ps.close();});
            logins.close();Files.write(OUT.resolve("e2e-log.txt"),log,StandardCharsets.UTF_8);
        }
    }
    /** Full production Admin login, both account forms, real disable/enable and Proctor credential handoff. */
    private String adminProvisionThroughUi(String proctorCode,String candidateCode,LoginApiClient logins) throws Exception {
        var app=new ToeicClientApplication();var stage=FxSupport.fx(Stage::new);
        FxSupport.fxRun(()->{app.start(stage);((TextField)stage.getScene().lookup("#login-server-url")).setText(base);
            ((TextField)stage.getScene().lookup("#login-username")).setText("admin-e2e");
            ((javafx.scene.control.PasswordField)stage.getScene().lookup("#login-password")).setText(password+"-separate-admin");
            ((Button)stage.getScene().lookup("#login-submit")).fire();});
        try {
            await("Admin production navigation",()->FxSupport.fx(()->stage.getScene().lookup("#admin-accounts")!=null),30);
            await("Admin accounts loaded",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-find").isDisabled()),20);
            assertThat(FxSupport.fx(()->stage.getScene().lookup("#proctor-redesign"))).isNull();screenshot(stage,"admin-accounts");
            String initial=chosenPassword(proctorCode);
            adminForm(stage,proctorCode,"MOCK Giám thị Phase 4.5","PROCTOR",initial);
            String candidatePassword=chosenPassword(candidateCode);
            adminForm(stage,candidateCode,"MOCK Thí sinh Admin","CANDIDATE",candidatePassword);
            assertThat(logins.login(base,candidateCode,candidatePassword).get(15,TimeUnit.SECONDS).user().role()).isEqualTo("CANDIDATE");
            long candidateId=count("SELECT id FROM user_accounts WHERE username='"+candidateCode+"'");
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE candidate_user_id="+candidateId)).isZero();
            var credential=logins.login(base,proctorCode,initial).get(15,TimeUnit.SECONDS);
            // Filter to the idle new Proctor so its row action is actually rendered, then use confirmation dialogs.
            FxSupport.fxRun(()->{((TextField)stage.getScene().lookup("#admin-search")).setText(proctorCode);((Button)stage.getScene().lookup("#admin-find")).fire();});
            await("Admin filtered row",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-find").isDisabled()),20);
            long id=credential.user().id();
            await("Admin row button",()->FxSupport.fx(()->stage.getScene().lookup("#admin-toggle-"+id)!=null),15);
            FxSupport.fxRun(()->((Button)stage.getScene().lookup("#admin-toggle-"+id)).fire());pressDialog(ButtonType.OK,10);
            await("Proctor disabled",()->count("SELECT count(*) FROM user_accounts WHERE id="+id+" AND NOT enabled")==1,20);
            assertStatus("revoked Proctor token",send("GET","/api/v1/auth/me",null,credential.token()),401);
            assertStatus("disabled login",send("POST","/api/v1/auth/login",new com.google.gson.Gson().toJson(Map.of("username",proctorCode,"password",initial,"requestId",UUID.randomUUID().toString())),null),401);
            await("Admin disable refresh",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-find").isDisabled()),20);screenshot(stage,"admin-proctor-disabled");
            FxSupport.fxRun(()->((Button)stage.getScene().lookup("#admin-toggle-"+id)).fire());pressDialog(ButtonType.OK,10);
            await("Proctor enabled",()->count("SELECT count(*) FROM user_accounts WHERE id="+id+" AND enabled")==1,20);
            assertStatus("old token stays revoked",send("GET","/api/v1/auth/me",null,credential.token()),401);
            assertThat(logins.login(base,proctorCode,initial).get(15,TimeUnit.SECONDS).user().role()).isEqualTo("PROCTOR");
            await("Admin enable refresh",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-find").isDisabled()),20);screenshot(stage,"admin-proctor-enabled");
            log.add("PASS: production Admin login -> create Proctor/Candidate with Admin-typed passwords -> disable/enable idle Proctor -> real login revocation");
            return initial;
        } finally {closeDialogs();FxSupport.fxRun(()->{app.stop();stage.close();});}
    }
    /** The Admin types the password twice; the form closes once the server stored it. */
    private void adminForm(Stage stage,String code,String name,String role,String chosen) throws Exception {
        await("Admin add enabled",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-add").isDisabled()),20);
        FxSupport.fxRun(()->((Button)stage.getScene().lookup("#admin-add")).fire());
        await("Admin form open",()->FxSupport.fx(()->dialogNode("admin-create-code")!=null),10);
        FxSupport.fxRun(()->{((TextField)dialogNode("admin-create-code")).setText(code);((TextField)dialogNode("admin-create-name")).setText(name);
            @SuppressWarnings("unchecked") var roles=(javafx.scene.control.ComboBox<String>)dialogNode("admin-create-role");roles.setValue(role);});
        // Captured before the password is typed; the fields are masked anyway.
        screenshotAll("admin-create-"+role.toLowerCase());
        FxSupport.fxRun(()->{((javafx.scene.control.PasswordField)dialogNode("admin-create-password")).setText(chosen);
            ((javafx.scene.control.PasswordField)dialogNode("admin-create-password-confirm")).setText(chosen);
            ((Button)dialogNode("admin-create-save")).fire();});
        await("Admin form saved",()->FxSupport.fx(()->dialogNode("admin-create-code")==null&&!stage.getScene().lookup("#admin-find").isDisabled()),20);
    }
    private static String chosenPassword(String code) { return "MOCK-pass-"+code; }
    @Test void proctorRecoversExistingRoomAndRotatesThenRevokesPinFromRealUi() throws Exception {
        try(var logins=new LoginApiClient()) {
            var login=logins.login(base,"proctor1",password).get(15,TimeUnit.SECONDS);importFixture(login.token());
            long candidate=logins.login(base,"candidate1",password).get(15,TimeUnit.SECONDS).user().id();
            var request=new com.google.gson.Gson().toJson(new CreateRichSessionRequest(EXAM,120,List.of(candidate),List.of(login.user().id())));
            String session=call("POST","/api/v2/rooms",request,login.token(),201).getAsJsonObject("session").get("sessionId").getAsString();
            var rt=new RealtimeClient(base,login.token());
            var view=FxSupport.fx(()->ProctorRedesignView.forLogin(base,login,rt,()->{},()->{}));var stage=FxSupport.fx(()->stage(view,1366,1000,"MOCK PIN recovery"));
            try {
                rt.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null));
                FxSupport.fxRun(()->((ToggleButton)view.lookup("#nav-session-controls")).fire());
                var pane=legacyPane(view);
                await("room catalog recovery",()->FxSupport.fx(()->pane.controller().snapshot().rooms().stream().anyMatch(r->r.sessionId().equals(session))),20);
                FxSupport.fxRun(()->{ @SuppressWarnings("unchecked") var rooms=(TableView<RoomCatalogPage.Item>)pane.lookup("#v2-rooms");rooms.getSelectionModel().select(rooms.getItems().stream().filter(r->r.sessionId().equals(session)).findFirst().orElseThrow());});
                await("room members",()->pane.controller().snapshot().presence()!=null,20);
                FxSupport.fxRun(()->((TableView<?>)pane.lookup("#v2-room-members")).getSelectionModel().select(0));
                await("rotate enabled",()->FxSupport.fx(()->!pane.lookup("#v2-pin-rotate").isDisabled()),10);
                Platform.runLater(()->((Button)pane.lookup("#v2-pin-rotate")).fire());pressDialog(ButtonType.OK,10);
                await("new PIN returned",()->!pane.controller().snapshot().issuedPins().isEmpty(),20);
                var ticket=pane.controller().snapshot().issuedPins().getFirst();
                String code=pane.controller().snapshot().presence().roomCode();
                var joined=logins.joinRoom(base,code,ticket.username(),ticket.pin()).get(15,TimeUnit.SECONDS);
                assertThat(joined.attemptId()).isEqualTo(ticket.attemptId());
                await("revoke enabled",()->FxSupport.fx(()->!pane.lookup("#v2-pin-revoke").isDisabled()),10);
                Platform.runLater(()->((Button)pane.lookup("#v2-pin-revoke")).fire());pressDialog(ButtonType.OK,10);
                await("revoked",()->pane.controller().snapshot().pinMessage().startsWith("Đã thu hồi"),20);
                assertStatus("revoked token",send("GET","/api/v2/attempts/"+ticket.attemptId()+"/preparation",null,joined.login().token()),401);
                assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isEqualTo(1);
                assertThat(pane.controller().snapshot().issuedPins()).isEmpty();screenshot(stage,"pin-recovery-revoked-no-secret");
            }finally{FxSupport.fxRun(()->{view.close();stage.close();});rt.close();}
        }
    }

    private ProctorV2SessionPane legacyPane(Parent root) throws Exception {
        return FxSupport.fx(()->{var tabs=(javafx.scene.control.TabPane)root.lookup("#session-controls-tabs");
            var tab=tabs.getTabs().stream().filter(t->"tab-v2".equals(t.getId())).findFirst().orElseThrow();
            tabs.getSelectionModel().select(tab);return (ProctorV2SessionPane)tab.getContent();});
    }
    private ProctorRedesignView legacyView() {
        for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){var v=find(w.getScene().getRoot(),ProctorRedesignView.class);if(v!=null)return v;}
        return null;
    }
    private long count(String sql) throws Exception {try(var s=database.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private void injectMonitoring(String token,String attempt) throws Exception {
        String payload=new com.google.gson.Gson().toJson(Map.of("eventId","MOCK-room-e2e-event","collectorSessionId","MOCK-room-e2e-collector","policyVersion","process-policy-v1","pid",123,"processName","chrome.exe","startInstant","2026-10-23T00:00:00Z","metadataQuality","COMPLETE","observedAt","2026-10-23T00:00:00Z"));
        String envelope="{\"protocolVersion\":\"v0\",\"type\":\"PROCESS_OBSERVED\",\"traceId\":\"MOCK-room-e2e-trace\",\"messageId\":\"room-e2e-process\",\"requestId\":\"room-e2e-process\",\"attemptId\":\""+attempt+"\",\"payload\":"+payload+"}";
        var inbox=new LinkedBlockingQueue<String>();var pending=new StringBuilder();
        var ws=http.newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(URI.create(base.replace("http://","ws://")+"/ws/v1/realtime"),new java.net.http.WebSocket.Listener(){
            public void onOpen(java.net.http.WebSocket socket){socket.request(1);}
            public CompletionStage<?> onText(java.net.http.WebSocket socket,CharSequence data,boolean last){pending.append(data);if(last){inbox.add(pending.toString());pending.setLength(0);}socket.request(1);return CompletableFuture.completedFuture(null);}
        }).get(10,TimeUnit.SECONDS);
        try{ws.sendText(envelope,true).get(5,TimeUnit.SECONDS);String response=inbox.poll(10,TimeUnit.SECONDS);assertThat(response).contains("ACK");}finally{ws.abort();}
    }

    private void importFixture(String token) throws Exception {
        Path fixture = Path.of("..", "docs", "plan-v2", "fixtures", "ex02", "import.mock.json");
        HttpResponse<String> response = send("POST", "/api/v2/exams/import", Files.readString(fixture, StandardCharsets.UTF_8), token);
        log.add("import MOCK fixture -> HTTP " + response.statusCode());
        assertThat(response.statusCode()).as("import " + response.body()).isIn(201, 409);
    }

    private static Stage stage(Parent root, double width, double height, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        Scene scene = new Scene(root, width, height);
        var styles = RoomFlowPostgresGuiTest.class.getResource("/styles.css");
        if (styles != null) scene.getStylesheets().add(styles.toExternalForm());
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    private JsonObject call(String method, String path, String body, String token, int expected) throws Exception {
        HttpResponse<String> response = send(method, path, body, token);
        assertThat(response.statusCode()).as(method + " " + path + " " + response.body()).isEqualTo(expected);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void assertStatus(String what, HttpResponse<String> response, int expected) {
        log.add(what + " -> HTTP " + response.statusCode());
        assertThat(response.statusCode()).as(what + " " + response.body()).isEqualTo(expected);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        else request.method(method, HttpRequest.BodyPublishers.noBody());
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Finds the open dialog, records its text and presses the given button. */
    private String pressDialog(ButtonType type, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            String text = FxSupport.fx(() -> {
                for (Window window : Window.getWindows()) {
                    if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane dialog && dialog.lookupButton(type) != null) {
                        String content = dialog.getContentText();
                        ((Button) dialog.lookupButton(type)).fire();
                        return content==null?"":content;
                    }
                }
                return null;
            });
            if (text != null) return text;
            Thread.sleep(100);
        }
        fail("Không thấy hộp thoại có nút " + type.getText());
        return null;
    }

    private static void closeDialogs() {
        try {
            FxSupport.fxRun(() -> {
                for (Window window : new ArrayList<>(Window.getWindows()))
                    if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane) window.hide();
            });
        } catch (Exception ignored) { }
    }

    private static String save(String attemptId, long writer, long revision, String q101) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "e2e-save-" + UUID.randomUUID());
        body.addProperty("attemptId", attemptId);
        body.addProperty("writerEpoch", writer);
        body.addProperty("answerRevision", revision);
        JsonObject answers = new JsonObject();
        answers.addProperty("q101", q101);  // "C": a change the server must refuse
        answers.addProperty("q147", "D");
        body.add("answers", answers);
        return body.toString();
    }

    /** Opens the room's "Chọn câu" palette and clicks question {@code number}, like a candidate would. */
    private void pickQuestion(Stage stage, int number) throws Exception {
        FxSupport.fxRun(() -> ((Button) stage.getScene().getRoot().lookup("#room-pick-question")).fire());
        await("palette " + number, () -> FxSupport.fx(() -> paletteButton(number) != null), 10);
        FxSupport.fxRun(() -> paletteButton(number).fire());
        log.add("candidate picked question " + number);
    }

    private static Button paletteButton(int number) {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window.getScene() == null || !(window.getScene().getRoot() instanceof DialogPane dialog)) continue;
            List<Button> found = new ArrayList<>();
            collect(dialog, Button.class, b -> { if (("palette-" + number).equals(b.getId())) found.add(b); });
            if (!found.isEmpty()) return found.getFirst();
        }
        return null;
    }

    private void clickWhenEnabled(Stage stage, String text) throws Exception {clickWhenEnabled(stage,text,60);}
    private void clickWhenEnabled(Stage stage, String text,int seconds) throws Exception {
        try{await("button " + text, () -> FxSupport.fx(() -> { Button b = button(stage.getScene().getRoot(), text); return b != null && !b.isDisabled(); }), seconds);}
        catch(AssertionError failure){screenshot(stage,"timeout-button");log.add("button timeout visible texts: "+FxSupport.fx(()->texts(stage.getScene().getRoot())));throw failure;}
        FxSupport.fxRun(() -> button(stage.getScene().getRoot(), text).fire());
        log.add("candidate clicked " + text);
    }

    private static Button button(Node root, String text) {
        List<Button> found = new ArrayList<>();
        collect(root, Button.class, b -> { if (text.equals(b.getText())) found.add(b); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private void clickRadio(Stage stage, String id) throws Exception {
        await("radio " + id, () -> FxSupport.fx(() -> radio(stage.getScene().getRoot(), id) != null), 10);
        FxSupport.fxRun(() -> radio(stage.getScene().getRoot(), id).fire());
    }

    private static RadioButton radio(Node root, String id) {
        List<RadioButton> found = new ArrayList<>();
        collect(root, RadioButton.class, r -> { if (id.equals(r.getId())) found.add(r); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private static Node dialogNode(String id) {
        for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){Node n=w.getScene().getRoot().lookup("#"+id);if(n!=null)return n;}
        return null;
    }

    private void awaitText(Stage stage, String prefix, int seconds) throws Exception {
        try {
            await("text " + prefix, () -> FxSupport.fx(() -> texts(stage.getScene().getRoot()).lines().anyMatch(t -> t.startsWith(prefix))), seconds);
        } catch (AssertionError timeout) {
            screenshot(stage, "timeout");
            log.add("visible texts: " + FxSupport.fx(() -> texts(stage.getScene().getRoot())));
            Files.write(OUT.resolve("e2e-log.txt"), log, StandardCharsets.UTF_8);
            throw timeout;
        }
    }

    private static String texts(Node root) {
        StringBuilder out = new StringBuilder();
        collect(root, Labeled.class, l -> { if (l.getText() != null) out.append(l.getText()).append('\n'); });
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found::add);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Walks the scene graph including ScrollPane content (not a child in the Parent sense before skinning). */
    private static <T> void collect(Node node, Class<T> type, Consumer<T> sink) { collect(node, type, sink, new HashSet<>()); }

    private static <T> void collect(Node node, Class<T> type, Consumer<T> sink, Set<Node> seen) {
        if (node == null || !seen.add(node)) return;
        if (type.isInstance(node)) sink.accept(type.cast(node));
        if (node instanceof ScrollPane scroll) collect(scroll.getContent(), type, sink, seen);
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, type, sink, seen);
    }

    private static void await(String what, Callable<Boolean> condition, int seconds) throws Exception {
        if (!awaitQuietly(condition, seconds)) fail("Không đạt trong " + seconds + " s: " + what);
    }

    private static boolean awaitQuietly(Callable<Boolean> condition, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            try { if (Boolean.TRUE.equals(condition.call())) return true; } catch (AssertionError | Exception retry) { /* until deadline */ }
            Thread.sleep(200);
        }
        return false;
    }

    private void screenshot(Stage stage, String name) throws Exception {
        WritableImage image = FxSupport.fx(() -> stage.getScene().snapshot(null));
        write(image, name);
    }

    /** Captures every showing window (the result dialog is its own window). */
    private void screenshotAll(String name) throws Exception {
        List<WritableImage> images = FxSupport.fx(() -> {
            List<WritableImage> all = new ArrayList<>();
            for (Window window : Window.getWindows()) if (window.isShowing() && window.getScene() != null) all.add(window.getScene().snapshot(null));
            return all;
        });
        for (int i = 0; i < images.size(); i++) write(images.get(i), name + "-" + i);
    }

    private static void write(WritableImage image, String name) throws Exception {
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) out.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(out, "png", OUT.resolve(name + ".png").toFile());
    }
}
