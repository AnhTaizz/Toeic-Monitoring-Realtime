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
class AccountPinFlowPostgresGuiTest {
    private static final Path OUT=Path.of("target","account-pin-e2e");
    private static final String EXAM="mock-ex02-200";
    @TempDir static Path temporary;
    private final Map<String,String> candidatePasswords=new HashMap<>();
    private String base,previousSettings,previousAudioCache,schema;
    private final String password=System.getenv("TOEIC_SEED_PASSWORD");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<String> log=new CopyOnWriteArrayList<>();
    private ConfigurableApplicationContext server;
    private Connection database;

    @BeforeAll void setup() throws Exception {
        FxSupport.start();Files.createDirectories(OUT);
        previousSettings=System.getProperty("toeic.proctor.settingsFile");
        previousAudioCache=System.getProperty("toeic.audio.cacheDirectory");
        System.setProperty("toeic.audio.cacheDirectory",temporary.resolve("client-audio-cache").toString());
        System.setProperty("toeic.proctor.settingsFile",temporary.resolve("proctor.properties").toString());
        String url="jdbc:postgresql://"+System.getenv("DB_HOST")+":"+System.getenv("DB_PORT")+"/"+System.getenv("DB_NAME");
        schema="account_pin_gui_"+UUID.randomUUID().toString().replace("-","");
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
        // Release launchers enable process + USB/focus/window signals, so this E2E runs the same configuration.
        // USB reads this machine's real Windows CIM provider: presence/status only, no device is changed here.
        System.setProperty("toeic.monitoring.signal-experiment-enabled","true");System.setProperty("toeic.monitoring.usb-enabled","true");
        props.put("toeic.monitoring.signal-experiment-enabled","true");
        props.put("toeic.room.pin-key",Base64.getUrlEncoder().withoutPadding().encodeToString("MOCK-32-byte-account-pin-test-key!".getBytes(StandardCharsets.US_ASCII)));props.put("toeic.visual.lease-ms","6000");props.put("toeic.listening.maintenance-enabled","true");props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        // Logging initializes before context initializers; isolate this GUI fixture from earlier test contexts.
        spring.setDefaultProperties(Map.of("logging.level.root","ERROR","logging.level.org.springframework.jdbc","ERROR","logging.level.org.springframework.web","ERROR","debug","false"));
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-room-e2e",props)));
        server=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)server).getWebServer().getPort();
    }
    @AfterAll void cleanup() throws Exception {
        System.clearProperty("toeic.monitoring.signal-experiment-enabled");System.clearProperty("toeic.monitoring.usb-enabled");
        http.close();if(server!=null)server.close();
        if(previousAudioCache==null)System.clearProperty("toeic.audio.cacheDirectory");else System.setProperty("toeic.audio.cacheDirectory",previousAudioCache);
        if(database!=null){try(var s=database.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}database.close();}
        if(previousSettings==null)System.clearProperty("toeic.proctor.settingsFile");else System.setProperty("toeic.proctor.settingsFile",previousSettings);
    }

    @org.junit.jupiter.params.ParameterizedTest(name="ACCOUNT_PIN ready={0}/2")
    @org.junit.jupiter.params.provider.ValueSource(ints={2,1,0})
    void accountLoginSharedPinReadySnapshotListeningReadingAndSubmit(int readyCount) throws Exception {
        log.clear();String suffix=UUID.randomUUID().toString().substring(0,8);
        String proctorCode="AP-P-"+suffix,candidateCode="AP-C-"+suffix;
        var logins=new LoginApiClient();String proctorPassword=adminProvisionThroughUi(proctorCode,candidateCode,logins);
        var proctor=logins.login(base,proctorCode,proctorPassword).get(15,TimeUnit.SECONDS);importFixture(proctor.token());
        var proctorApp=new ToeicClientApplication();var ps=FxSupport.fx(Stage::new);
        FxSupport.fxRun(()->{proctorApp.start(ps);((TextField)ps.getScene().lookup("#login-server-url")).setText(base);
            ((TextField)ps.getScene().lookup("#login-username")).setText(proctorCode);
            ((javafx.scene.control.PasswordField)ps.getScene().lookup("#login-password")).setText(proctorPassword);
            ((Button)ps.getScene().lookup("#login-submit")).fire();});
        await("production Proctor login",()->FxSupport.fx(()->find(ps.getScene().getRoot(),ProctorRedesignView.class)!=null),30);
        var pv=FxSupport.fx(()->find(ps.getScene().getRoot(),ProctorRedesignView.class));
        ToeicClientApplication[] clients=new ToeicClientApplication[2];Stage[] stages=new Stage[2];
        try {
            FxSupport.fxRun(()->{ps.setWidth(1366);ps.setHeight(1000);((ToggleButton)pv.lookup("#nav-session-controls")).fire();});
            var pane=FxSupport.fx(()->{
                var tabs=(javafx.scene.control.TabPane)pv.lookup("#session-controls-tabs");
                assertThat(tabs.getSelectionModel().getSelectedItem().getId()).isEqualTo("tab-account-pin");
                assertThat(tabs.getTabs()).as("release default hides old V2/V1 tabs").hasSize(1);
                return (ProctorV2SessionPane)tabs.getSelectionModel().getSelectedItem().getContent();
            });assertThat(pane.controller().accountPin()).isTrue();
            await("exam catalog",()->pane.controller().snapshot().catalog().stream().anyMatch(e->e.examId().equals(EXAM)),20);
            FxSupport.fxRun(()->pane.controller().select(EXAM));
            await("Create enabled after exam selection",()->FxSupport.fx(()->!pane.lookup("#v2-create").isDisabled()),10);
            FxSupport.fxRun(()->((Button)pane.lookup("#v2-create")).fire());
            await("empty room created",()->pane.controller().snapshot().sharedPin()!=null,20);
            String pin=pane.controller().snapshot().sharedPin(),session=pane.controller().snapshot().session();
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isZero();
            assertThat(FxSupport.fx(()->pane.lookup("#v2-start").isDisabled())).isTrue();
            FxSupport.fxRun(()->pane.controller().forgetPins());
            List<LoginResponse> scoped=new ArrayList<>();List<String> attempts=new ArrayList<>();
            for(int i=0;i<2;i++) {
                final int index=i;String code=i==0?candidateCode:candidateCode+"-2";String initial=candidatePasswords.get(code);
                var account=logins.login(base,code,initial).get(15,TimeUnit.SECONDS);
                FxSupport.fxRun(()->{clients[index]=new ToeicClientApplication();stages[index]=new Stage();clients[index].start(stages[index]);
                    ((TextField)stages[index].getScene().lookup("#login-server-url")).setText(base);
                    ((TextField)stages[index].getScene().lookup("#login-username")).setText(code);
                    ((javafx.scene.control.PasswordField)stages[index].getScene().lookup("#login-password")).setText(initial);
                    ((Button)stages[index].getScene().lookup("#login-submit")).fire();});
                await("account login opens PIN entry",()->FxSupport.fx(()->stages[index].getScene().lookup("#account-pin-input")!=null),30);
                if(index==0)uiScreens(stages[index],"c1-pin");
                FxSupport.fxRun(()->{((TextField)stages[index].getScene().lookup("#account-pin-input")).setText(pin);
                    ((Button)stages[index].getScene().lookup("#account-pin-enter")).fire();});
                awaitText(stages[index],"Bài thi đã sẵn sàng trên máy này",30);
                if(index==0)uiScreens(stages[index],"c2-lobby-"+readyCount);
                var joined=logins.joinAccountPin(base,account,pin).get(15,TimeUnit.SECONDS);
                scoped.add(joined.login());attempts.add(joined.attemptId());
                if(i<readyCount)prepare(stages[index]);
            }
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isEqualTo(2);
            await("joined roster and START available",()->FxSupport.fx(()->pane.controller().snapshot().presence()!=null
                && pane.controller().snapshot().presence().members().size()==2 && !pane.lookup("#v2-start").isDisabled()),30);
            await("expected individual READY count",()->FxSupport.fx(()->pane.controller().snapshot().readiness()!=null
                && !pane.controller().snapshot().readyStale()
                && pane.controller().snapshot().readiness().attempts().stream().filter(a->a.audioReady()&&a.visualReady()).count()==readyCount),30);
            uiScreens(stages[0],"c3-lobby-waiting-start-"+readyCount);
            if(readyCount>0) {
                String attempt=attempts.getFirst(),token=scoped.getFirst().token();
                var before=call("GET","/api/v2/attempts/"+attempt+"/preparation",null,token,200).getAsJsonObject("visualManifest");
                var field=ToeicClientApplication.class.getDeclaredField("realtimeClient");field.setAccessible(true);var rt=(RealtimeClient)field.get(clients[0]);
                rt.disconnect();rt.connect(new RealtimeClient.Session(Set.of(attempt),null,null)).get(15,TimeUnit.SECONDS);
                FxSupport.fxRun(()->{});
                long connectedAt=count("SELECT (EXTRACT(EPOCH FROM clock_timestamp())*1000)::bigint");
                await("READY lease renewed after transport reconnect",()->count("SELECT count(*) FROM attempt_visual_readiness WHERE attempt_id='"+attempt+"' AND EXTRACT(EPOCH FROM last_seen_at)*1000>"+connectedAt)==1,20);
                awaitText(stages[0],"✓  Sẵn sàng — đang chờ giám thị bắt đầu",30);
                var after=call("GET","/api/v2/attempts/"+attempt+"/preparation",null,token,200).getAsJsonObject("visualManifest");
                for(String key:List.of("writerEpoch","preparationEpoch","manifestId"))assertThat(after.get(key)).as("pre-START reconnect "+key).isEqualTo(before.get(key));
                assertThat(count("SELECT count(*) FROM attempt_audio_readiness WHERE attempt_id='"+attempt+"'")).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM attempt_visual_readiness WHERE attempt_id='"+attempt+"'")).isEqualTo(1);
            }
            injectMonitoring(scoped.getFirst().token(),attempts.getFirst());
            await("dynamic monitoring scope",()->FxSupport.fx(()->pv.room()!=null&&pv.room().alerts().stream().anyMatch(e->"MOCK-room-e2e-event".equals(e.id()))),25);
            assertStatus("cross-attempt denied",send("GET","/api/v2/attempts/"+attempts.get(1)+"/preparation",null,scoped.getFirst().token()),403);
            FxSupport.fxRun(()->pane.setVvalue(1));screenshot(ps,"account-pin-ready-"+readyCount+"-no-pin");
            Platform.runLater(()->((Button)pane.lookup("#v2-start")).fire());if(readyCount<2)pressDialog(ButtonType.OK,10);
            await("START committed",()->pane.controller().snapshot().started()!=null,25);
            for(int i=readyCount;i<2;i++) {
                awaitText(stages[i],"Phòng đã bắt đầu.",30);prepare(stages[i]);
            }
            for(int i=0;i<2;i++) {
                Stage current=stages[i];String attempt=attempts.get(i),token=scoped.get(i).token();
                await("personal exam access",()->FxSupport.fx(()->current.getScene().lookup("#room-pick-question")!=null),40);
                uiScreens(current,"c4-exam-start-"+readyCount+"-"+(i<readyCount?"ready":"late"));
                // USB in the ACCOUNT_PIN exam: the real CIM observer reaches this attempt's server history, and an
                // unchanged device state over further scans adds no new USB event (no repeated alerts).
                String usbRows="SELECT count(*) FROM monitoring_signal_events WHERE attempt_id='"+attempt+"' AND source='WINDOWS_USB_STORAGE'";
                await("USB observer committed for this attempt",()->count(usbRows)>0,60);
                long usbEvents=count(usbRows+" AND event_type<>'OBSERVER_STATUS'");
                var signalField=ToeicClientApplication.class.getDeclaredField("signalMonitoring");signalField.setAccessible(true);
                var app=clients[i];
                var signals=(vn.edu.toeic.client.monitoring.CandidateExamSignalMonitoring)FxSupport.fx(()->signalField.get(app));
                assertThat(signals).as("ACCOUNT_PIN exam starts the shared signal run").isNotNull();
                long scans=signals.usbStatus().scans();
                await("three more real USB scans",()->signals.usbStatus().scans()>=scans+3,60);
                assertThat(count(usbRows+" AND event_type<>'OBSERVER_STATUS'")).as("unchanged USB state is not re-reported").isEqualTo(usbEvents);
                if(i<readyCount){awaitText(current,"Phần nghe đã kết thúc",150);pickQuestion(current,1);clickRadio(current,"option-q001-A");}
                else assertThat(call("GET","/api/v1/attempts/"+attempt+"/listening/status",null,token,200).get("interruptionReason").getAsString()).isEqualTo("NOT_READY_AT_START");
                pickQuestion(current,101);clickRadio(current,"option-q101-A");
                await("Reading autosave",()->call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200).getAsJsonObject("answers").has("q101"),25);
                uiScreens(current,"c5-reading-"+readyCount+"-"+i);
                if(ui()&&i==0) {
                    // The OS close button raises WINDOW_CLOSE_REQUEST; the exam asks, and "Ở lại làm bài" keeps it open.
                    Platform.runLater(()->current.fireEvent(new javafx.stage.WindowEvent(current,javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
                    awaitDialog();uiScreens(current,"c6-close-confirm-"+readyCount);pressDialog(ButtonType.NO,10);
                    assertThat(FxSupport.fx(current::isShowing)).as("staying keeps the exam open").isTrue();
                }
                var before=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200);String deadline=before.get("deadlineAt").getAsString();
                var field=ToeicClientApplication.class.getDeclaredField("realtimeClient");field.setAccessible(true);var rt=(RealtimeClient)field.get(clients[i]);
                rt.disconnect();rt.connect(new RealtimeClient.Session(Set.of(attempt),null,null)).get(15,TimeUnit.SECONDS);
                await("Submit available after transport reconnect",()->FxSupport.fx(()->{var b=(Button)current.getScene().lookup("#room-submit");return b!=null&&!b.isDisabled();}),25);
                var after=call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200);assertThat(after.get("deadlineAt").getAsString()).isEqualTo(deadline);assertThat(after.get("writerEpoch")).isEqualTo(before.get("writerEpoch"));
                Platform.runLater(()->((Button)current.getScene().lookup("#room-submit")).fire());
                if(ui()){awaitDialog();uiScreens(current,"c7-submit-confirm-"+readyCount+"-"+i);}
                pressDialog(ButtonType.YES,10);
                await("submitted",()->"SUBMITTED".equals(call("GET","/api/v1/attempts/"+attempt+"/status",null,token,200).get("state").getAsString()),25);
                if(ui()){await("result dialog",()->FxSupport.fx(()->Window.getWindows().stream().anyMatch(w->w.isShowing()&&w instanceof Stage s&&"Kết Quả Bài Thi TOEIC".equals(s.getTitle()))),25);
                    uiScreens(current,"c8-result-"+readyCount+"-"+i);}
                screenshot(current,"account-pin-"+readyCount+"-candidate-"+i+"-submitted");closeDialogs();
            }
            log.add("PASS: production Admin/Proctor/two Candidate apps; ready="+readyCount+"/2; shared PIN; monitoring; actual media playback where authorized; late Reading; autosave/submit/reconnect. Physical hearing/LAN NOT TESTED.");
        } finally {
            closeDialogs();FxSupport.fxRun(()->{for(int i=0;i<2;i++){if(clients[i]!=null)clients[i].stop();if(stages[i]!=null)stages[i].close();}proctorApp.stop();ps.close();});
            logins.close();Files.write(OUT.resolve("e2e-ready-"+readyCount+".txt"),log,StandardCharsets.UTF_8);
        }
    }
    private void prepare(Stage stage) throws Exception {
        clickWhenEnabled(stage,"Tải / chuẩn bị lại audio");clickWhenEnabled(stage,"Thử loa",120);
        clickWhenEnabled(stage,"Tôi nghe được");clickWhenEnabled(stage,"Xác nhận sẵn sàng");
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
            adminForm(stage,proctorCode,"MOCK Giám thị Phase 4.5","PROCTOR");
            await("one-time Proctor credential",()->FxSupport.fx(()->dialogNode("admin-initial-password")!=null),20);
            String initial=FxSupport.fx(()->((TextField)dialogNode("admin-initial-password")).getText());
            assertThat(initial).matches("[A-Za-z0-9_-]{43}");
            // Do not photograph the one-time credential dialog.
            FxSupport.fxRun(()->{for(Window w:new ArrayList<>(Window.getWindows()))if(w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane d&&d.lookupButton(ButtonType.CLOSE)!=null)((Button)d.lookupButton(ButtonType.CLOSE)).fire();});
            await("Admin list refreshed",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-find").isDisabled()),20);
            adminForm(stage,candidateCode,"MOCK Thí sinh Admin","CANDIDATE");
            await("one-time Candidate credential",()->FxSupport.fx(()->dialogNode("admin-initial-password")!=null),20);
            String candidatePassword=FxSupport.fx(()->((TextField)dialogNode("admin-initial-password")).getText());
            var candidateLogin=logins.login(base,candidateCode,candidatePassword).get(15,TimeUnit.SECONDS);
            assertThat(candidateLogin.user().role()).isEqualTo("CANDIDATE");
            candidatePasswords.put(candidateCode,candidatePassword);pressDialog(ButtonType.CLOSE,10);
            adminForm(stage,candidateCode+"-2","MOCK Candidate two","CANDIDATE");
            await("second Candidate password",()->FxSupport.fx(()->dialogNode("admin-initial-password")!=null),20);
            candidatePasswords.put(candidateCode+"-2",FxSupport.fx(()->((TextField)dialogNode("admin-initial-password")).getText()));pressDialog(ButtonType.CLOSE,10);
            await("Candidate form saved",()->FxSupport.fx(()->dialogNode("admin-create-code")==null&&!stage.getScene().lookup("#admin-find").isDisabled()),20);
            long candidateId=count("SELECT id FROM user_accounts WHERE username='"+candidateCode+"'");
            assertThat(count("SELECT count(*) FROM monitoring_attempts WHERE candidate_user_id="+candidateId)).isZero();
            FxSupport.fxRun(()->{((TextField)stage.getScene().lookup("#admin-search")).setText(candidateCode);((Button)stage.getScene().lookup("#admin-find")).fire();});
            await("Candidate reset action",()->FxSupport.fx(()->stage.getScene().lookup("#admin-reset-"+candidateId)!=null&&!stage.getScene().lookup("#admin-find").isDisabled()),20);
            FxSupport.fxRun(()->((Button)stage.getScene().lookup("#admin-reset-"+candidateId)).fire());pressDialog(ButtonType.OK,10);
            await("reset one-time password",()->FxSupport.fx(()->dialogNode("admin-initial-password")!=null),20);
            String reset=FxSupport.fx(()->((TextField)dialogNode("admin-initial-password")).getText());
            assertThat(reset).isNotEqualTo(candidatePassword);candidatePasswords.put(candidateCode,reset);pressDialog(ButtonType.CLOSE,10);
            assertStatus("reset revokes previous Candidate token",send("GET","/api/v1/auth/me",null,candidateLogin.token()),401);
            assertStatus("reset rejects old Candidate password",send("POST","/api/v1/auth/login",new com.google.gson.Gson().toJson(Map.of("username",candidateCode,"password",candidatePassword,"requestId",UUID.randomUUID().toString())),null),401);
            assertThat(logins.login(base,candidateCode,reset).get(15,TimeUnit.SECONDS).user().role()).isEqualTo("CANDIDATE");
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
            log.add("PASS: production Admin login -> create Proctor/Candidate -> disable/enable idle Proctor -> real login revocation; one-time secret not captured");
            return initial;
        } finally {closeDialogs();FxSupport.fxRun(()->{app.stop();stage.close();});}
    }
    private void adminForm(Stage stage,String code,String name,String role) throws Exception {
        await("Admin add enabled",()->FxSupport.fx(()->!stage.getScene().lookup("#admin-add").isDisabled()),20);
        FxSupport.fxRun(()->((Button)stage.getScene().lookup("#admin-add")).fire());
        await("Admin form open",()->FxSupport.fx(()->dialogNode("admin-create-code")!=null),10);
        FxSupport.fxRun(()->{((TextField)dialogNode("admin-create-code")).setText(code);((TextField)dialogNode("admin-create-name")).setText(name);
            @SuppressWarnings("unchecked") var roles=(javafx.scene.control.ComboBox<String>)dialogNode("admin-create-role");roles.setValue(role);});
        // No credential exists yet: capture the real form/dialog as well as the parent window.
        screenshotAll("admin-create-"+role.toLowerCase());
        FxSupport.fxRun(()->((Button)dialogNode("admin-create-save")).fire());
    }
    private ProctorV2SessionPane legacyPane(Parent root) throws Exception {
        return FxSupport.fx(()->{var tabs=(javafx.scene.control.TabPane)root.lookup("#session-controls-tabs");
            var tab=tabs.getTabs().stream().filter(t->"tab-v2".equals(t.getId())).findFirst().orElseThrow();
            tabs.getSelectionModel().select(tab);return (ProctorV2SessionPane)tab.getContent();});
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
        var styles = AccountPinFlowPostgresGuiTest.class.getResource("/styles.css");
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
                    if (window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane dialog && dialog.lookupButton(type) != null) {
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

    /**
     * UI review captures, only with TOEIC_UI_SCREENSHOTS=true: the candidate window at 1920x1080 and 1366x768,
     * plus any dialog window that is showing. Regular runs never resize or capture here.
     */
    private void uiScreens(Stage stage, String name) throws Exception {
        if (!"true".equals(System.getenv("TOEIC_UI_SCREENSHOTS"))) return;
        Path dir = Files.createDirectories(OUT.resolve("ui-review"));
        for (int[] size : new int[][]{{1920, 1080}, {1366, 768}}) {
            FxSupport.fxRun(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); });
            FxSupport.fxRun(() -> { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); });
            Thread.sleep(400); // let wrapped labels and scroll panes settle after the resize
            WritableImage image = FxSupport.fx(() -> stage.getScene().snapshot(null));
            writeTo(dir, image, name + "-" + size[0] + "x" + size[1]);
        }
        List<WritableImage> dialogs = FxSupport.fx(() -> {
            List<WritableImage> all = new ArrayList<>();
            for (Window window : Window.getWindows())
                if (window.isShowing() && window != stage && window.getScene() != null && window instanceof Stage s && s.getOwner() == stage)
                    all.add(window.getScene().snapshot(null));
            return all;
        });
        for (int i = 0; i < dialogs.size(); i++) writeTo(dir, dialogs.get(i), name + "-dialog-" + i);
    }

    private static boolean ui() { return "true".equals(System.getenv("TOEIC_UI_SCREENSHOTS")); }
    private void awaitDialog() throws Exception {
        await("dialog showing",()->FxSupport.fx(()->Window.getWindows().stream().anyMatch(w->w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane)),10);
    }

    private static void writeTo(Path dir, WritableImage image, String name) throws Exception {
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) out.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(out, "png", dir.resolve(name + ".png").toFile());
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
