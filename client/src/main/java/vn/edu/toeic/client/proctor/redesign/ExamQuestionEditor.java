package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.*;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.toeic.client.exam.rich.QuestionDisplay;
import vn.edu.toeic.protocol.exam.rich.CandidateQuestion;
import vn.edu.toeic.protocol.admin.ManualExamDraft;

/** Edits a private document; stable question/group/media IDs survive every field change. */
final class ExamQuestionEditor extends BorderPane {
    private final JsonObject document;
    private final ListView<JsonObject> questions=new ListView<>();
    private final VBox fields=new VBox(12);
    private final ComboBox<Integer> part=new ComboBox<>();
    private final TextField search=new TextField();
    private final boolean manual;
    private Runnable changed=()->{};
    ExamQuestionEditor(JsonObject source) {
        this(source,false);
    }
    ExamQuestionEditor(JsonObject source,boolean manual) {
        this.manual=manual;
        document=source.deepCopy();setId("exam-question-editor");
        // Crawl prompts can end in a repeated option list. Remove that exact suffix before choices
        // are edited, otherwise changing an option would make the old list reappear in the exam.
        if(document.has("exam"))for(var element:document.getAsJsonArray("questions")) {
            var q=element.getAsJsonObject();var texts=new ArrayList<JsonObject>();
            for(var block:q.getAsJsonArray("blocks"))if(text(block.getAsJsonObject(),"type").equals("TEXT"))texts.add(block.getAsJsonObject());
            if(texts.size()==1) {
                String prompt=QuestionDisplay.prompt(new Gson().fromJson(q,CandidateQuestion.class));
                if(prompt.isBlank())prompt=q.get("part").getAsInt()==6?"Chọn đáp án phù hợp cho chỗ trống "+q.get("orderIndex").getAsInt()+".":"Chọn đáp án đúng.";
                texts.getFirst().addProperty("text",prompt);
            }
        }
        part.getItems().addAll(0,1,2,3,4,5,6,7);part.setValue(0);part.setId("exam-question-part");
        part.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Integer p){return p==null||p==0?"Tất cả Part":"Part "+p;}
            public Integer fromString(String s){return 0;}
        });
        search.setPromptText("Số câu hoặc nội dung câu hỏi");search.setId("exam-question-search");
        questions.setId("exam-question-list");questions.setPrefWidth(235);
        questions.setCellFactory(v->new ListCell<>() {
            protected void updateItem(JsonObject q,boolean empty){super.updateItem(q,empty);
                setText(empty||q==null?null:"Câu "+q.get("orderIndex").getAsInt()+" · Part "+q.get("part").getAsInt()
                        +(manual?(ManualExamDraft.complete(new Gson().fromJson(q,ManualExamDraft.Question.class))?" ✓":" · Chưa đủ"):""));}
        });
        questions.getSelectionModel().selectedItemProperty().addListener((o,a,b)->show(b));
        part.valueProperty().addListener((o,a,b)->filter());search.textProperty().addListener((o,a,b)->filter());
        var left=new VBox(10,part,search,questions);left.setPadding(new Insets(0,16,0,0));VBox.setVgrow(questions,Priority.ALWAYS);
        fields.setPadding(new Insets(4,12,12,4));var scroll=new ScrollPane(fields);scroll.setFitToWidth(true);
        setLeft(left);setCenter(scroll);setPrefHeight(440);filter();
    }
    JsonObject document(){return document.deepCopy();}
    void onChange(Runnable listener){changed=listener;}
    private void edited(){if(manual)questions.refresh();changed.run();}
    void validate() {
        for(var item:document.getAsJsonArray("questions")) {
            var q=item.getAsJsonObject();var options=q.getAsJsonArray("options");
            String correct=document.has("exam")?text(q.getAsJsonObject("scoring"),"correctOption"):text(q,"correctOption");
            boolean found=false;
            for(var element:options) {
                var option=element.getAsJsonObject();if(text(option,"optionId").equals(correct))found=true;
                if(q.get("part").getAsInt()>2 && text(option,document.has("exam")?"text":"optionText").isBlank())
                    throw new IllegalArgumentException("Câu "+q.get("orderIndex")+": nhập nội dung lựa chọn "+text(option,"optionId")+".");
            }
            if(!found)throw new IllegalArgumentException("Câu "+q.get("orderIndex")+": chọn đáp án đúng.");
        }
    }
    private void filter() {
        var selected=questions.getSelectionModel().getSelectedItem();String term=search.getText().strip().toLowerCase(Locale.ROOT);
        var rows=new ArrayList<JsonObject>();
        for(var element:document.getAsJsonArray("questions")) {
            var q=element.getAsJsonObject();
            if((part.getValue()==0||q.get("part").getAsInt()==part.getValue()) && (term.isEmpty()
                    || q.get("orderIndex").getAsString().equals(term) || q.toString().toLowerCase(Locale.ROOT).contains(term)))rows.add(q);
        }
        rows.sort(Comparator.comparingInt(q->q.get("orderIndex").getAsInt()));
        questions.setItems(FXCollections.observableArrayList(rows));
        if(rows.contains(selected))questions.getSelectionModel().select(selected);else if(!rows.isEmpty())questions.getSelectionModel().selectFirst();
        else fields.getChildren().setAll(Ui.wrapping("Không có câu phù hợp."));
    }
    private void show(JsonObject q) {
        fields.getChildren().clear();if(q==null)return;
        fields.getChildren().add(Ui.label("Câu "+q.get("orderIndex").getAsInt()+" · Part "+q.get("part").getAsInt(),"section-title"));
        boolean rich=document.has("exam");
        if(rich) {
            blocks(q,true,"Nội dung câu hỏi","question");
        }else area(q,"prompt","Nội dung câu hỏi","exam-question-prompt",3);
        var ids=new ArrayList<String>();
        for(var element:q.getAsJsonArray("options")) {
            var option=element.getAsJsonObject();String id=text(option,"optionId");ids.add(id);
            area(option,rich?"text":"optionText","Lựa chọn "+id,"exam-question-option-"+id,2);
        }
        var scoring=rich?q.getAsJsonObject("scoring"):q;
        var correct=new ComboBox<String>(FXCollections.observableArrayList(ids));correct.setId("exam-question-correct");
        correct.setPromptText("Chọn đáp án đúng");
        correct.setValue(text(scoring,"correctOption").isBlank()?null:text(scoring,"correctOption"));correct.valueProperty().addListener((o,a,b)->{scoring.addProperty("correctOption",b);edited();});
        fields.getChildren().add(field("Đáp án đúng",correct));
        if(rich && q.has("groupId")&&!q.get("groupId").isJsonNull()) {
            for(var element:document.getAsJsonArray("groups")) {
                var group=element.getAsJsonObject();if(!text(group,"groupId").equals(text(q,"groupId")))continue;
                fields.getChildren().add(Ui.wrapping("Đoạn văn / nội dung chung — thay đổi áp dụng cho mọi câu trong nhóm này.","muted"));
                blocks(group,false,"Nội dung chung","group");
                if(group.has("documents"))for(var doc:group.getAsJsonArray("documents")) {
                    var d=doc.getAsJsonObject();area(d,"title","Tiêu đề đoạn văn","exam-document-title-"+text(d,"documentId"),1);
                    blocks(d,false,"Đoạn văn","document-"+text(d,"documentId"));
                }
            }
        }else if(!rich) {
            area(q,"passageText","Đoạn văn (bắt buộc với Part 6, 7)","exam-question-passage",4);
            if(manual && q.get("part").getAsInt()<=4)
                area(q,"audioFile","Tên tệp âm thanh trên server (tùy chọn)","exam-question-audio",1);
        }
    }
    private void blocks(JsonObject owner,boolean allowNew,String label,String prefix) {
        JsonArray blocks=owner.has("blocks")?owner.getAsJsonArray("blocks"):new JsonArray();
        if(!owner.has("blocks"))owner.add("blocks",blocks);
        boolean hasText=false;for(var block:blocks)if(text(block.getAsJsonObject(),"type").equals("TEXT"))hasText=true;
        if(allowNew&&!hasText) {
            // Add a TEXT block only when the user actually types; listening questions may have no prompt.
            var input=new TextArea();input.setPrefRowCount(3);input.setWrapText(true);input.setId("exam-question-prompt");
            var added=new JsonObject();added.addProperty("blockId","admin-text-"+text(owner,"questionId"));
            added.addProperty("type","TEXT");int order=1;for(var block:blocks)order=Math.max(order,block.getAsJsonObject().get("orderIndex").getAsInt()+1);
            added.addProperty("orderIndex",order);
            input.textProperty().addListener((o,a,b)->{if(b.isBlank())blocks.remove(added);else {if(!blocks.contains(added))blocks.add(added);added.addProperty("text",b);}});
            fields.getChildren().add(field(label,input));
        }
        int index=0;
        for(var element:blocks) {
            var b=element.getAsJsonObject();if(text(b,"type").equals("TEXT"))area(b,"text",label,prefix.equals("question")&&index++==0?"exam-question-prompt":"exam-"+prefix+"-text-"+text(b,"blockId"),4);
            else fields.getChildren().add(Ui.wrapping(text(b,"type")+": "+text(b,"mediaId")+" (giữ nguyên media)","muted"));
        }
    }
    private void area(JsonObject object,String key,String label,String id,int rows) {
        var input=new TextArea(text(object,key));input.setId(id);input.setWrapText(true);input.setPrefRowCount(rows);
        input.textProperty().addListener((o,a,b)->{object.addProperty(key,b);edited();});fields.getChildren().add(field(label,input));
    }
    private static String text(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static VBox field(String label,javafx.scene.Node input){return new VBox(5,Ui.label(label,"field-label"),input);}
}
