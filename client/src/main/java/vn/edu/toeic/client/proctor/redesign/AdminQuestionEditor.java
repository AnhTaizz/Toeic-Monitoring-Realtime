package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.*;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Friendly editor for the v1 question format, retaining shared passage/audio fields. */
final class AdminQuestionEditor {
    private AdminQuestionEditor() {}
    static void show(Window owner,JsonObject source,Consumer<JsonObject> saved) {
        var tree=source.deepCopy();var questions=tree.getAsJsonArray("questions");
        if(questions==null)throw new IllegalArgumentException("Đề thiếu danh sách câu hỏi.");
        var form=new Dialog<Void>();form.initOwner(owner);form.setTitle("Biên soạn câu hỏi");form.setHeaderText("Chọn câu hỏi để sửa hoặc thêm câu mới");
        form.getDialogPane().getStyleClass().add("proctor-redesign");
        form.getDialogPane().getStylesheets().add(AdminQuestionEditor.class.getResource(ProctorRedesignView.STYLESHEET).toExternalForm());
        ListView<String> list=new ListView<>();list.setPrefWidth(180);
        TextField id=new TextField(),group=new TextField(),audio=new TextField();
        TextArea prompt=new TextArea(),passage=new TextArea();prompt.setPrefRowCount(3);passage.setPrefRowCount(3);prompt.setWrapText(true);passage.setWrapText(true);
        ComboBox<Integer> part=new ComboBox<>();part.getItems().setAll(1,2,3,4,5,6,7);part.setValue(5);
        ComboBox<String> correct=new ComboBox<>();correct.getItems().setAll("A","B","C","D");correct.setValue("A");
        TextField[] options={new TextField(),new TextField(),new TextField(),new TextField()};
        Label error=Ui.wrapping("");int[] selected={-1};
        Runnable refresh=()->{list.getItems().clear();for(int i=0;i<questions.size();i++)list.getItems().add((i+1)+". "+text(questions.get(i).getAsJsonObject(),"questionId"));};
        Runnable reset=()->{selected[0]=-1;list.getSelectionModel().clearSelection();id.setText("q"+(questions.size()+1));group.clear();audio.clear();prompt.clear();passage.clear();
            for(var option:options)option.clear();part.setValue(5);correct.setValue("A");error.setText("Nhập nội dung rồi bấm Lưu câu hỏi.");};
        list.getSelectionModel().selectedIndexProperty().addListener((o,old,index)->{
            if(index.intValue()<0)return;selected[0]=index.intValue();var q=questions.get(selected[0]).getAsJsonObject();
            id.setText(text(q,"questionId"));group.setText(text(q,"groupId"));audio.setText(text(q,"audioFile"));prompt.setText(text(q,"prompt"));passage.setText(text(q,"passageText"));
            part.setValue(q.get("part").getAsInt());correct.setValue(text(q,"correctOption"));for(var option:options)option.clear();
            for(var item:q.getAsJsonArray("options")){var option=item.getAsJsonObject();String key=text(option,"optionId");
                if(key.matches("[A-D]"))options[key.charAt(0)-'A'].setText(text(option,"optionText"));}
            error.setText("");
        });
        Button add=new Button("Thêm câu hỏi"),remove=new Button("Xóa câu hỏi"),save=new Button("Lưu câu hỏi");
        add.setId("admin-question-add");remove.setId("admin-question-delete");save.setId("admin-question-save");
        id.setId("admin-question-code");prompt.setId("admin-question-prompt");options[0].setId("admin-question-option-a");
        add.setOnAction(e->reset.run());
        remove.setOnAction(e->{if(selected[0]<0)return;questions.remove(selected[0]);refresh.run();reset.run();});
        save.setOnAction(e->{
            if(id.getText().isBlank() || prompt.getText().isBlank() || part.getValue()==null || correct.getValue()==null){error.setText("Nhập mã câu, Part, nội dung và đáp án đúng.");return;}
            String code=id.getText().strip();for(int i=0;i<questions.size();i++)if(i!=selected[0] && code.equals(text(questions.get(i).getAsJsonObject(),"questionId"))){error.setText("Mã câu hỏi bị trùng.");return;}
            var choices=new JsonArray();for(int i=0;i<options.length;i++)if(!options[i].getText().isBlank()){
                var option=new JsonObject();option.addProperty("optionId",String.valueOf((char)('A'+i)));option.addProperty("optionText",options[i].getText());option.addProperty("orderIndex",i+1);choices.add(option);}
            int key=correct.getValue().charAt(0)-'A';if(choices.size()<2 || key<0 || key>3 || options[key].getText().isBlank()){
                error.setText("Nhập ít nhất hai lựa chọn và nội dung cho đáp án đúng.");return;}
            var q=selected[0]<0?new JsonObject():questions.get(selected[0]).getAsJsonObject().deepCopy();
            q.addProperty("questionId",code);q.addProperty("section",part.getValue()<=4?"LISTENING":"READING");q.addProperty("part",part.getValue());
            optional(q,"groupId",group.getText());optional(q,"audioFile",audio.getText());optional(q,"passageText",passage.getText());
            q.addProperty("prompt",prompt.getText());q.addProperty("correctOption",correct.getValue());q.add("options",choices);
            int index=selected[0]<0?questions.size():selected[0];q.addProperty("orderIndex",index+1);
            if(selected[0]<0)questions.add(q);else questions.set(index,q);
            refresh.run();list.getSelectionModel().select(index);error.setText("Đã lưu câu hỏi. Bấm Áp dụng để cập nhật nội dung đề.");
        });
        VBox fields=new VBox(8,new HBox(12,field("Mã câu",id),field("Part",part),field("Đáp án đúng",correct)),field("Nội dung câu hỏi",prompt),
                new HBox(10,field("A",options[0]),field("B",options[1])),new HBox(10,field("C",options[2]),field("D (để trống nếu chỉ có 3 lựa chọn)",options[3])),
                field("Mã nhóm (nếu dùng chung đoạn văn/âm thanh)",group),field("Đoạn văn",passage),field("Tên tệp âm thanh (đã có trên server)",audio),new HBox(10,add,save,remove),error);
        fields.setPrefWidth(610);var content=new HBox(16,list,fields);content.setPadding(new Insets(8));form.getDialogPane().setContent(content);
        var apply=new ButtonType("Áp dụng",ButtonBar.ButtonData.OK_DONE);form.getDialogPane().getButtonTypes().setAll(apply,ButtonType.CANCEL);
        form.getDialogPane().lookupButton(apply).setId("admin-question-apply");
        ((Button)form.getDialogPane().lookupButton(apply)).addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            if(questions.isEmpty()){e.consume();error.setText("Đề phải có ít nhất một câu hỏi.");return;}
            for(int i=0;i<questions.size();i++)questions.get(i).getAsJsonObject().addProperty("orderIndex",i+1);
            saved.accept(tree);
        });
        refresh.run();if(!questions.isEmpty())list.getSelectionModel().selectFirst();else reset.run();form.showAndWait();
    }
    private static String text(JsonObject object,String key){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";}
    private static void optional(JsonObject object,String key,String value){if(value.isBlank())object.remove(key);else object.addProperty(key,value);}
    private static VBox field(String label,javafx.scene.Node node){return new VBox(4,Ui.label(label,"field-label"),node);}
}
