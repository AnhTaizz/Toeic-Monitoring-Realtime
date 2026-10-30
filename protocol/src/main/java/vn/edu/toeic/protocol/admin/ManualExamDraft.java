package vn.edu.toeic.protocol.admin;

import java.util.ArrayList;
import java.util.List;

/** Incomplete fields are allowed only in the admin authoring contract. */
public record ManualExamDraft(String examId, String title, String description, long revision,
                              List<Question> questions) {
    public record Question(String questionId, String section, int part, String groupId,
                           String passageText, String audioFile, String prompt, String correctOption,
                           int orderIndex, List<Option> options, List<String> imageFiles) {
        public Question(String questionId,String section,int part,String groupId,String passageText,String audioFile,
                String prompt,String correctOption,int orderIndex,List<Option> options) {
            this(questionId,section,part,groupId,passageText,audioFile,prompt,correctOption,orderIndex,options,List.of());
        }
        public List<String> imageFiles(){return imageFiles==null?List.of():java.util.Collections.unmodifiableList(imageFiles);}
    }
    public record Option(String optionId, String optionText, int orderIndex) {}

    public static int partFor(int number) {
        if(number<=6)return 1;if(number<=31)return 2;if(number<=70)return 3;
        if(number<=100)return 4;if(number<=130)return 5;if(number<=146)return 6;return 7;
    }
    public static ManualExamDraft empty() {
        var questions=new ArrayList<Question>();
        for(int n=1;n<=200;n++) {
            int part=partFor(n);var options=new ArrayList<Option>();
            for(int o=0;o<(part==2?3:4);o++)options.add(new Option(""+(char)('A'+o),"",o+1));
            questions.add(new Question("q"+n,part<=4?"LISTENING":"READING",part,null,"","","","",n,options));
        }
        return new ManualExamDraft("","","",0,questions);
    }
    public static boolean complete(Question q) {
        return q!=null && (q.part()<=2 || filled(q.prompt())) && length(q.prompt())<=16000 && q.options()!=null
                && q.options().stream().allMatch(o->o!=null && (q.part()<=2 || filled(o.optionText())) && length(o.optionText())<=2000)
                && q.options().stream().anyMatch(o->o!=null && o.optionId().equals(q.correctOption()))
                && (q.part()>4 || filled(q.audioFile()))
                && (q.part()!=1 || q.imageFiles().size()==1)
                && (q.part()<6 || filled(q.passageText()) && length(q.passageText())<=16000);
    }
    /** Fixed shared units for conversations, talks and text completion; reading passages may stand alone. */
    public static int groupStart(int number) {
        int part=partFor(number);
        return switch(part){case 3->32+(number-32)/3*3;case 4->71+(number-71)/3*3;case 6->131+(number-131)/4*4;default->number;};
    }
    public static int groupEnd(int number){int start=groupStart(number);return start+switch(partFor(number)){case 3,4->2;case 6->3;default->0;};}
    public int completedQuestions() {
        if(questions==null)return 0;int count=0;
        for(var q:questions) {
            if(!complete(q))continue;
            int start=groupStart(q.orderIndex());
            if(start!=q.orderIndex()&&start<=questions.size()) {
                var first=questions.get(start-1);
                if(first==null||q.part()<=4&&!java.util.Objects.equals(q.audioFile(),first.audioFile())
                        ||q.part()==6&&!java.util.Objects.equals(q.passageText(),first.passageText()))continue;
            }
            count++;
        }
        return count;
    }
    private static int length(String value){return value==null?0:value.length();}
    private static boolean filled(String value){return value!=null&&!value.isBlank();}
}
