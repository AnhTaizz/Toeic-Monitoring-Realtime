package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vn.edu.toeic.client.admin.*;
import vn.edu.toeic.protocol.admin.*;

/** Exam management page owned and disposed by the Admin shell. */
public final class AdminExamView extends VBox implements AutoCloseable {
    private final AdminExamApiClient api;
    private final Runnable expired;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"toeic-admin-exams");t.setDaemon(true);return t;});
    private final TableView<ExamSummary> table=new TableView<>();
    private final TextField search=new TextField();
    private final Label status=Ui.wrapping("","muted");
    private final Button find=new Button("Tìm kiếm"),first=new Button("Trang đầu"),next=new Button("Trang tiếp"),add=new Button("Thêm đề thi");
    private String cursor;
    private boolean busy,closed;
    private Dialog<?> dialog;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();

    public AdminExamView(String url,String token,Runnable expired) {
        this.api=new AdminExamApiClient(url,token);this.expired=expired;
        setId("admin-exams");setSpacing(20);setPadding(new Insets(24));
        search.setId("admin-exam-search");search.setPromptText("Mã đề hoặc tên đề thi");search.setPrefColumnCount(28);
        find.setId("admin-exam-find");find.setOnAction(e->load(null));search.setOnAction(e->load(null));
        first.setOnAction(e->load(null));next.setOnAction(e->load(cursor));next.setDisable(true);
        add.setId("admin-exam-add");add.getStyleClass().add("primary");add.setOnAction(e->editor(null,sample()));
        HBox filters=new HBox(10,search,find);filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions=new HBox(10,add,Ui.grow(),first,next);actions.setAlignment(Pos.CENTER_LEFT);
        table.setId("admin-exam-table");Ui.plainTable(table,"Không tìm thấy đề thi.");
        table.getColumns().add(Ui.textColumn("Mã đề",180,ExamSummary::examId));
        table.getColumns().add(Ui.textColumn("Tên đề thi",300,ExamSummary::title));
        table.getColumns().add(Ui.textColumn("Số câu",90,e->String.valueOf(e.totalQuestions())));
        table.getColumns().add(Ui.nodeColumn("Trạng thái",160,e->Ui.badge(e.inUse()?"Đã dùng trong ca thi":"Chưa sử dụng",e.inUse()?"gray":"green")));
        table.getColumns().add(Ui.nodeColumn("Thao tác",180,exam->{
            Button edit=new Button(exam.inUse()?"Xem":"Sửa"),delete=new Button("Xóa");
            edit.setId("admin-exam-edit-"+exam.examId());delete.setId("admin-exam-delete-"+exam.examId());
            edit.setOnAction(e->submit(()->api.read(exam.examId()),text->editor(exam,text),null));
            delete.setDisable(exam.inUse());delete.setOnAction(e->confirmDelete(exam));
            return new HBox(8,edit,delete);
        }));
        status.setId("admin-exam-status");status.setMinHeight(32);
        VBox card=Ui.card(filters,actions,table,status);card.setSpacing(16);card.setPadding(new Insets(20));
        VBox.setVgrow(table,Priority.ALWAYS);VBox.setVgrow(card,Priority.ALWAYS);
        getChildren().setAll(Ui.pageHeader("Quản lý đề thi","Thêm, sửa và xóa đề thi. Đề đã dùng trong ca thi được giữ nguyên để bảo toàn kết quả."),card);
        load(null);
    }
    private void load(String after) {
        if(busy || closed)return;String term=search.getText();status.setText("Đang tải đề thi…");
        submit(()->api.list(term,after),page->{table.getItems().setAll(page.items());cursor=page.nextAfterExamId();controls();
            status.setText("Đã tải "+page.items().size()+" đề thi.");},null);
    }
    private void editor(ExamSummary exam,String content) {
        if(closed || dialog!=null)return;
        boolean readonly=exam!=null && exam.inUse();
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);
        form.setTitle(exam==null?"Thêm đề thi":readonly?"Xem đề thi":"Sửa đề thi");
        form.setHeaderText(exam==null?"Tạo đề mới hoặc nhập từ tệp JSON":exam.title());
        TextField code=new TextField(),title=new TextField();TextArea description=new TextArea();description.setPrefRowCount(2);
        code.setId("admin-exam-code");title.setId("admin-exam-title");description.setId("admin-exam-description");
        code.setPrefColumnCount(20);title.setPrefColumnCount(42);
        TextArea body=new TextArea(content);body.setId("admin-exam-content");body.setPrefRowCount(18);body.setWrapText(false);
        body.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        Label error=Ui.wrapping("");error.setId("admin-exam-form-status");
        Runnable fill=()->{try{var tree=JSON.fromJson(body.getText(),JsonObject.class);var meta=metadata(tree);
            code.setText(meta.get("examId").getAsString());title.setText(meta.get("title").getAsString());
            description.setText(meta.has("description")&&!meta.get("description").isJsonNull()?meta.get("description").getAsString():"");
            error.setText("");}catch(RuntimeException bad){error.setText("Tệp đề không hợp lệ: cần mã đề, tên đề và danh sách câu hỏi.");}};
        fill.run();
        Button importFile=new Button("Nhập tệp JSON"),sync=new Button("Đọc thông tin từ nội dung");
        importFile.setId("admin-exam-import");
        importFile.setOnAction(e->{var chooser=new FileChooser();chooser.setTitle("Chọn đề thi JSON");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Đề thi JSON","*.json"));
            var file=chooser.showOpenDialog(form.getDialogPane().getScene().getWindow());if(file==null)return;
            try(var input=Files.newInputStream(file.toPath())){var bytes=input.readNBytes(AdminExamApiClient.MAX_BYTES+1);
                if(bytes.length>AdminExamApiClient.MAX_BYTES)throw new IllegalArgumentException();
                body.setText(new String(bytes,StandardCharsets.UTF_8));fill.run();
                if(exam!=null)code.setText(exam.examId());
            }catch(Exception bad){error.setText("Không đọc được tệp JSON hoặc tệp vượt 2 MiB.");}});
        sync.setOnAction(e->{fill.run();if(exam!=null)code.setText(exam.examId());});
        Button compose=new Button("Biên soạn câu hỏi");compose.setId("admin-exam-compose");compose.setDisable(readonly);
        compose.setOnAction(e->{try{var tree=JSON.fromJson(body.getText(),JsonObject.class);
            if(tree.has("schemaVersion")){error.setText("Đề v2: sửa câu hỏi, nhóm nội dung và tài nguyên trực tiếp trong JSON hoặc nhập tệp đã chỉnh sửa.");return;}
            AdminQuestionEditor.show(form.getDialogPane().getScene().getWindow(),tree,updated->body.setText(JSON.toJson(updated)));
        }catch(RuntimeException bad){error.setText("Nội dung JSON không hợp lệ để biên soạn câu hỏi.");}});
        code.setDisable(exam!=null || readonly);title.setEditable(!readonly);description.setEditable(!readonly);body.setEditable(!readonly);
        importFile.setDisable(readonly);sync.setDisable(readonly);
        var fields=new VBox(8,new HBox(12,field("Mã đề",code),field("Tên đề thi",title)),field("Mô tả",description),
                new HBox(10,compose,importFile,sync),Ui.wrapping("Biên soạn câu hỏi cho đề v1 hoặc nhập tệp JSON v1/v2. Nội dung bên dưới gồm câu hỏi, lựa chọn, đáp án và tài nguyên nghe/đọc.","muted","small"),body,error);
        fields.setPrefWidth(860);VBox.setVgrow(body,Priority.ALWAYS);form.getDialogPane().setContent(fields);
        var saveType=new ButtonType("Lưu đề thi",ButtonBar.ButtonData.OK_DONE);
        if(readonly)form.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        else {
            form.getDialogPane().getButtonTypes().setAll(saveType,ButtonType.CANCEL);
            Button save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-exam-save");save.getStyleClass().add("primary");
            save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();if(busy)return;
                String payload;
                try {var tree=JSON.fromJson(body.getText(),JsonObject.class);var meta=metadata(tree);
                    String id=code.getText().strip(),name=title.getText().strip();
                    if(id.isEmpty() || id.length()>128 || name.isEmpty() || name.length()>255 || !tree.has("questions"))throw new IllegalArgumentException();
                    meta.addProperty("examId",exam==null?id:exam.examId());meta.addProperty("title",name);meta.addProperty("description",description.getText());payload=JSON.toJson(tree);
                }catch(RuntimeException bad){error.setText("Nhập mã đề, tên đề và nội dung JSON hợp lệ có danh sách câu hỏi.");return;}
                fields.setDisable(true);save.setDisable(true);form.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);error.setText("Đang lưu đề…");
                submit(()->{api.save(exam==null?null:exam.examId(),payload);return true;},ok->{form.close();load(null);},message->{
                    fields.setDisable(false);save.setDisable(false);form.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);error.setText(message);});
            });
        }
        form.setOnCloseRequest(e->{if(busy && !closed)e.consume();});form.setOnHidden(e->{if(dialog==form)dialog=null;});form.show();
    }
    private void confirmDelete(ExamSummary exam) {
        if(busy || dialog!=null || closed)return;
        var confirm=new Alert(Alert.AlertType.CONFIRMATION,"Xóa đề “"+exam.title()+"” ("+exam.examId()+")? Toàn bộ câu hỏi và đáp án của đề sẽ bị xóa.",ButtonType.OK,ButtonType.CANCEL);
        dialog=confirm;confirm.initOwner(getScene().getWindow());style(confirm);confirm.setHeaderText("Xóa đề thi");
        confirm.setOnHidden(e->{dialog=null;if(confirm.getResult()==ButtonType.OK && !closed)
            submit(()->{api.delete(exam.examId());return true;},ok->load(null),null);});confirm.show();
    }
    private <T> void submit(Callable<T> work,Consumer<T> success,Consumer<String> failure) {
        if(busy || closed)return;busy=true;controls();
        worker.submit(()->{try{T result=work.call();Platform.runLater(()->{if(closed)return;busy=false;controls();success.accept(result);});}
            catch(Exception problem){Platform.runLater(()->{if(closed)return;busy=false;controls();
                if(problem instanceof AdminAccountApiClient.Rejected rejected && rejected.status()==401){expired.run();return;}
                String message=problem instanceof AdminAccountApiClient.Rejected || problem instanceof IllegalArgumentException?problem.getMessage()
                        :"Không kết nối được server hoặc phản hồi không hợp lệ. Hãy tải lại danh sách để kiểm tra trước khi thử lại.";
                if(failure==null)status.setText(message);else failure.accept(message);
            });}});
    }
    private void controls(){find.setDisable(busy);search.setDisable(busy);first.setDisable(busy);next.setDisable(busy||cursor==null);add.setDisable(busy);table.setDisable(busy);}
    private static JsonObject metadata(JsonObject tree){return tree.has("exam")?tree.getAsJsonObject("exam"):tree;}
    private static VBox field(String label,javafx.scene.Node node){return new VBox(5,Ui.label(label,"field-label"),node);}
    private static void style(Dialog<?> dialog){dialog.getDialogPane().getStyleClass().add("proctor-redesign");
        dialog.getDialogPane().getStylesheets().add(AdminExamView.class.getResource(ProctorRedesignView.STYLESHEET).toExternalForm());}
    static String sample(){return """
            {
              "examId": "",
              "title": "",
              "description": "",
              "questions": [{
                "questionId": "q1", "section": "READING", "part": 5,
                "prompt": "The meeting _____ at nine o'clock.",
                "correctOption": "A", "orderIndex": 1,
                "options": [
                  {"optionId": "A", "optionText": "starts", "orderIndex": 1},
                  {"optionId": "B", "optionText": "starting", "orderIndex": 2},
                  {"optionId": "C", "optionText": "start", "orderIndex": 3},
                  {"optionId": "D", "optionText": "started", "orderIndex": 4}
                ]
              }]
            }
            """;}
    @Override public void close(){closed=true;if(dialog!=null){dialog.setOnCloseRequest(null);dialog.close();dialog=null;}worker.shutdownNow();api.close();}
}
