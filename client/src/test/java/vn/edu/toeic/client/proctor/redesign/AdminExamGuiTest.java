package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.protocol.auth.LoginResponse;

@EnabledIfEnvironmentVariable(named="TOEIC_CANDIDATE_GUI",matches="true")
class AdminExamGuiTest {
    private HttpServer server;
    private AdminAccountView view;
    private Stage stage;
    private final List<String> calls=new CopyOnWriteArrayList<>(),bodies=new CopyOnWriteArrayList<>();
    private final AtomicInteger status=new AtomicInteger(200),expired=new AtomicInteger();
    @BeforeAll static void toolkit(){FxSupport.start();}
    @BeforeEach void open() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v2/admin/accounts",e->{byte[] bytes="{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
        server.createContext("/api/v2/admin/exams",e->{
            calls.add(e.getRequestMethod()+" "+e.getRequestURI());
            String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);if(!body.isEmpty())bodies.add(body);
            int code=status.get();String text;
            if(code!=200)text="{\"error\":{\"message\":\"Mã đề đã tồn tại\"}}";
            else if(e.getRequestMethod().equals("DELETE")){code=204;text="";}
            else if(e.getRequestMethod().equals("POST")){code=201;text="{\"status\":\"CREATED\"}";}
            else if(e.getRequestMethod().equals("PUT")){text="{\"status\":\"UPDATED\"}";}
            else if(e.getRequestURI().getPath().endsWith("/item"))text=AdminExamView.sample().replace("\"examId\": \"\"","\"examId\": \"MOCK-01\"").replace("\"title\": \"\"","\"title\": \"Đề thi mẫu\"");
            else text="""
                    {"items":[{"examId":"MOCK-01","title":"Đề thi mẫu","description":"Đề luyện tập","totalQuestions":1,"contentVersion":1,"inUse":false},
                    {"examId":"MOCK-02","title":"Đề đã thi","totalQuestions":200,"contentVersion":2,"inUse":true}]}
                    """;
            e.getResponseHeaders().set("Content-Type","application/json");
            if(code==204)e.sendResponseHeaders(code,-1);
            else {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(code,bytes.length);e.getResponseBody().write(bytes);}
            e.close();
        });server.start();
        var login=new LoginResponse("v0","MOCK","MOCK","MOCK-token","Bearer","2099-01-01T00:00:00Z",new LoginResponse.UserView(1,"admin","Administrator","ADMIN"),List.of());
        view=fx(()->new AdminAccountView("http://127.0.0.1:"+server.getAddress().getPort(),login,()->{},expired::incrementAndGet));
        stage=fx(()->{var s=new Stage();s.setScene(new Scene(view,1366,820));s.show();return s;});
        fxRun(()->((ToggleButton)view.lookup("#admin-exam-nav")).fire());
        await("exams loaded",()->view.lookup("#admin-exam-edit-MOCK-01")!=null && !view.lookup("#admin-exam-find").isDisabled());
    }
    @AfterEach void stop() throws Exception {fxRun(()->{view.close();stage.close();});server.stop(0);}
    @Test void navigationAndProtectedActionsAndScreenshot() throws Exception {
        assertThat(fx(()->view.lookup("#admin-exam-delete-MOCK-02").isDisabled())).isTrue();
        assertThat(fx(()->((Button)view.lookup("#admin-exam-edit-MOCK-02")).getText())).isEqualTo("Xem");
        fxRun(()->{view.applyCss();view.layout();});
        var snapshot=fx(()->view.snapshot(null,null));
        var image=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
        var out=java.nio.file.Path.of("target","admin-exams.png");java.nio.file.Files.createDirectories(out.getParent());javax.imageio.ImageIO.write(image,"png",out.toFile());
    }
    @Test void createAndEditPersistMetadataAndKeepFormOnServerRejection() throws Exception {
        fxRun(()->((Button)view.lookup("#admin-exam-add")).fire());await("create form",()->dialog("admin-exam-code")!=null);
        fxRun(()->{((TextField)dialog("admin-exam-code")).setText("NEW-01");((TextField)dialog("admin-exam-title")).setText("Đề mới");
            ((Button)dialog("admin-exam-save")).fire();});
        await("created",()->dialog("admin-exam-code")==null && calls.stream().anyMatch(c->c.startsWith("POST")) && !view.lookup("#admin-exam-find").isDisabled());
        var body=JsonParser.parseString(bodies.getFirst()).getAsJsonObject();assertThat(body.get("examId").getAsString()).isEqualTo("NEW-01");
        assertThat(body.get("title").getAsString()).isEqualTo("Đề mới");
        fxRun(()->((Button)view.lookup("#admin-exam-edit-MOCK-01")).fire());await("edit form",()->dialog("admin-exam-code")!=null);
        assertThat(fx(()->dialog("admin-exam-code").isDisabled())).isTrue();
        status.set(409);fxRun(()->{((TextField)dialog("admin-exam-title")).setText("Tên đã sửa");((Button)dialog("admin-exam-save")).fire();});
        await("rejection shown",()->((Label)dialog("admin-exam-form-status")).getText().contains("đã tồn tại"));
        assertThat(fx(()->((TextField)dialog("admin-exam-title")).getText())).isEqualTo("Tên đã sửa");
        assertThat(calls.stream().filter(c->c.startsWith("PUT")).count()).isEqualTo(1);
    }
    @Test void deleteRequiresConfirmationAndSessionExpiryReturnsToLogin() throws Exception {
        fxRun(()->((Button)view.lookup("#admin-exam-delete-MOCK-01")).fire());
        await("delete confirmation",()->confirmation()!=null);
        assertThat(calls.stream().noneMatch(c->c.startsWith("DELETE"))).isTrue();
        fxRun(()->((Button)confirmation().lookupButton(ButtonType.OK)).fire());
        await("deleted",()->calls.stream().anyMatch(c->c.startsWith("DELETE")) && !view.lookup("#admin-exam-find").isDisabled());
        status.set(401);fxRun(()->((Button)view.lookup("#admin-exam-find")).fire());await("expired",()->expired.get()==1);
    }
    @Test void questionComposerEditsTheQuestionWithoutTypingJson() throws Exception {
        fxRun(()->((Button)view.lookup("#admin-exam-add")).fire());await("create form",()->dialog("admin-exam-compose")!=null);
        // showAndWait enters JavaFX's nested event loop, so queue this action without blocking the test thread.
        javafx.application.Platform.runLater(()->((Button)dialog("admin-exam-compose")).fire());
        await("question composer",()->dialog("admin-question-prompt")!=null);
        fxRun(()->{((TextArea)dialog("admin-question-prompt")).setText("Edited in the question form");
            ((Button)dialog("admin-question-save")).fire();((Button)dialog("admin-question-apply")).fire();});
        await("composer applied",()->dialog("admin-question-prompt")==null);
        assertThat(fx(()->((TextArea)dialog("admin-exam-content")).getText())).contains("Edited in the question form");
    }
    private static Node dialog(String id){for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){var n=w.getScene().lookup("#"+id);if(n!=null)return n;}return null;}
    private static DialogPane confirmation(){for(Window w:Window.getWindows())if(w.isShowing() && w.getScene()!=null && w.getScene().getRoot() instanceof DialogPane d)return d;return null;}
}
