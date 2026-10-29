package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.client.admin.AdminExamImport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named="TOEIC_CANDIDATE_GUI",matches="true")
class AdminExamGuiTest {
    @TempDir Path temporary;
    private HttpServer server;private AdminAccountView view;private Stage stage;
    private final List<String> calls=new CopyOnWriteArrayList<>(),bodies=new CopyOnWriteArrayList<>();
    private final AtomicInteger status=new AtomicInteger(200),expired=new AtomicInteger();
    @BeforeAll static void toolkit(){FxSupport.start();}
    public static String sample(String id){return """
            {"examId":"%s","title":"Đề mẫu","description":"Mô tả","questions":[{
              "questionId":"q1","section":"READING","part":5,"prompt":"Choose one","correctOption":"A","orderIndex":1,
              "options":[{"optionId":"A","optionText":"One","orderIndex":1},{"optionId":"B","optionText":"Two","orderIndex":2}]}]}
            """.formatted(id);}
    @BeforeEach void open() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v2/admin/accounts",e->{byte[] bytes="{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
        server.createContext("/api/v2/admin/exams",e->{
            calls.add(e.getRequestMethod()+" "+e.getRequestURI());String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);if(!body.isEmpty())bodies.add(body);
            int code=status.get();String text;
            if(code!=200)text="{\"error\":{\"message\":\"Mã đề đã tồn tại\"}}";
            else if(e.getRequestMethod().equals("POST")){code=201;text="{\"status\":\"CREATED\"}";}
            else if(!e.getRequestMethod().equals("GET"))text="{\"status\":\"UPDATED\"}";
            else if(e.getRequestURI().getPath().endsWith("/item"))text=sample(e.getRequestURI().getQuery().contains("MOCK-02")?"MOCK-02":"MOCK-01");
            else text="""
                    {"items":[{"examId":"MOCK-01","title":"Đề thi mẫu","totalQuestions":1,"contentVersion":1,"inUse":false},
                    {"examId":"MOCK-02","title":"Đề đã thi","totalQuestions":200,"contentVersion":2,"inUse":true}]}
                    """;
            e.getResponseHeaders().set("Content-Type","application/json");byte[] bytes=text.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(code,bytes.length);e.getResponseBody().write(bytes);e.close();
        });server.start();
        var login=new LoginResponse("v0","MOCK","MOCK","MOCK-token","Bearer","2099-01-01T00:00:00Z",new LoginResponse.UserView(1,"admin","Administrator","ADMIN"),List.of());
        view=fx(()->new AdminAccountView("http://127.0.0.1:"+server.getAddress().getPort(),login,()->{},expired::incrementAndGet));
        stage=fx(()->{var s=new Stage();s.setScene(new Scene(view,1366,820));s.show();return s;});
        fxRun(()->((ToggleButton)view.lookup("#admin-exam-nav")).fire());await("exams loaded",()->view.lookup("#admin-exam-edit-MOCK-01")!=null && !view.lookup("#admin-exam-find").isDisabled());
    }
    @AfterEach void stop() throws Exception {fxRun(()->{view.close();stage.close();});server.stop(0);}
    @Test void onlyImportAndEditAreOfferedAndPreviewHasNoDummyQuestionForm() throws Exception {
        assertThat(fx(()->((Button)view.lookup("#admin-exam-add")).getText())).isEqualTo("Import JSON");
        assertThat(fx(()->view.lookup("#admin-exam-delete-MOCK-01"))).isNull();
        screenshot(view,"admin-exams.png");
        Path file=temporary.resolve("exam.json");Files.writeString(file,"\uFEFF"+sample("NEW-01"));
        fxRun(()->((AdminExamView)view.getCenter()).importFile(file));await("JSON preview",()->dialog("admin-exam-code")!=null);
        assertThat(fx(()->((TextField)dialog("admin-exam-code")).getText())).isEqualTo("NEW-01");
        assertThat(fx(()->dialog("admin-exam-compose"))).isNull();
        assertThat(fx(()->((TextArea)dialog("admin-exam-content")).isEditable())).isFalse();
        screenshot(fx(()->dialog("admin-exam-code").getScene().getRoot()),"admin-exam-import.png");
        fxRun(()->{((TextField)dialog("admin-exam-code")).clear();((TextField)dialog("admin-exam-title")).clear();});
        assertThat(fx(()->dialog("admin-exam-save").isDisabled())).isTrue();
        fxRun(()->{((TextField)dialog("admin-exam-code")).setText("oicaidjt");((TextField)dialog("admin-exam-title")).setText("Đề vừa import");((Button)dialog("admin-exam-save")).fire();});
        await("created",()->dialog("admin-exam-code")==null && calls.stream().anyMatch(c->c.startsWith("POST")));
        assertThat(JsonParser.parseString(bodies.getFirst()).getAsJsonObject().get("title").getAsString()).isEqualTo("Đề vừa import");
        assertThat(JsonParser.parseString(bodies.getFirst()).getAsJsonObject().get("examId").getAsString()).isEqualTo("oicaidjt");
    }
    @Test void editingJsonUpdatesMetadataAndKeepsFormOnServerRejection() throws Exception {
        fxRun(()->((Button)view.lookup("#admin-exam-edit-MOCK-01")).fire());await("edit form",()->dialog("admin-exam-code")!=null);
        fxRun(()->{((TextArea)dialog("admin-exam-content")).setText(sample("MOCK-01").replace("Đề mẫu","Tên sửa từ JSON"));});
        assertThat(fx(()->((TextField)dialog("admin-exam-title")).getText())).isEqualTo("Tên sửa từ JSON");
        status.set(409);fxRun(()->((Button)dialog("admin-exam-save")).fire());
        await("error",()->((Label)dialog("admin-exam-form-status")).getText().contains("đã tồn tại"));
        assertThat(fx(()->((TextField)dialog("admin-exam-title")).getText())).isEqualTo("Tên sửa từ JSON");
        fxRun(()->((TextArea)dialog("admin-exam-content")).setText(sample("DIFFERENT-ID")));
        assertThat(fx(()->dialog("admin-exam-save").isDisabled())).isTrue();
    }
    @Test void usedExamsAllowMetadataEditsWithoutChangingTheirQuestions() throws Exception {
        fxRun(()->((Button)view.lookup("#admin-exam-edit-MOCK-02")).fire());await("edit used",()->dialog("admin-exam-title")!=null);
        assertThat(fx(()->dialog("admin-exam-content"))).isNull();
        fxRun(()->{((TextField)dialog("admin-exam-title")).setText("Đổi tên đề đã thi");((Button)dialog("admin-exam-save")).fire();});
        await("patched",()->calls.stream().anyMatch(c->c.startsWith("PATCH")) && dialog("admin-exam-title")==null);
        assertThat(bodies.getFirst()).contains("Đổi tên đề đã thi").doesNotContain("questions");
    }
    @Test void missingServerApiIsExplainedAndImportIsDisabledUntilReloadSucceeds() throws Exception {
        status.set(404);fxRun(()->((Button)view.lookup("#admin-exam-find")).fire());
        await("server mismatch",()->((Label)view.lookup("#admin-exam-status")).getText().contains("cập nhật server"));
        assertThat(fx(()->view.lookup("#admin-exam-add").isDisabled())).isTrue();
        status.set(200);fxRun(()->((Button)view.lookup("#admin-exam-find")).fire());await("restored",()->!view.lookup("#admin-exam-add").isDisabled());
        status.set(401);fxRun(()->((Button)view.lookup("#admin-exam-find")).fire());await("expired",()->expired.get()==1);
    }
    @ParameterizedTest @ValueSource(strings={"STUDY4_6852","STUDY4_6853","STUDY4_6854","STUDY4_6855"})
    @EnabledIfEnvironmentVariable(named="TOEIC_STUDY4_SOURCE",matches=".+")
    void actualCrawlOpensAValidImportPreviewWithoutEditingAnything(String exam) throws Exception {
        Path source=Path.of(System.getenv("TOEIC_STUDY4_SOURCE")).getParent().getParent().resolve(exam).resolve("server_exam.json");
        fxRun(()->((AdminExamView)view.getCenter()).importFile(source));await("actual JSON preview",()->dialog("admin-exam-code")!=null);
        String displayed=fx(()->((TextArea)dialog("admin-exam-content")).getText());
        try(var original=AdminExamImport.load(source)) {
            if(!displayed.equals(original.json)) {
                Files.writeString(Path.of("target","preview-original-"+exam+".json"),original.json);
                Files.writeString(Path.of("target","preview-displayed-"+exam+".json"),displayed);
            }
            try{AdminExamImport.parse(displayed);}
            catch(RuntimeException failure){throw new AssertionError("Invalid preview for "+exam+"; original length="+original.json.length()+"; displayed length="+displayed.length(),failure);}
        }
        assertThat(fx(()->dialog("admin-exam-save").isDisabled())).as(fx(()->((Label)dialog("admin-exam-form-status")).getText())).isFalse();
        fxRun(()->{
            ((TextField)dialog("admin-exam-code")).setText("oicaidjt");
            ((TextField)dialog("admin-exam-title")).setText("oicaidjt");
            // Import must be built from the parsed source, never from the preview control's text state.
            ((TextArea)dialog("admin-exam-content")).setText("incomplete preview");
            ((Button)dialog("admin-exam-save")).fire();
        });
        await("actual crawl uploaded or rejected",()->dialog("admin-exam-content")==null || !dialog("admin-exam-save").isDisabled());
        String failure=fx(()->dialog("admin-exam-form-status")==null?"":((Label)dialog("admin-exam-form-status")).getText());
        assertThat(failure).isEmpty();
        assertThat(calls).anyMatch(c->c.startsWith("POST /api/v2/admin/exams/bundle"));
    }
    private static Node dialog(String id){for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){var n=w.getScene().lookup("#"+id);if(n!=null)return n;}return null;}
    private static void screenshot(Node node,String filename) throws Exception {
        var snapshot=fx(()->{node.applyCss();if(node instanceof Parent parent)parent.layout();return node.snapshot(null,null);});
        var image=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(image,"png",Path.of("target",filename).toFile());
    }
}
