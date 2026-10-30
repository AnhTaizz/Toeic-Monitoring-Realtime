package vn.edu.toeic.protocol.admin;

import java.util.ArrayList;
import java.util.List;

/** Incomplete fields are allowed only in the admin authoring contract. */
public record ManualExamDraft(String examId, String title, String description, long revision,
                              List<Question> questions) {
    public record Question(String questionId, String section, int part, String groupId,
                           String passageText, String audioFile, String prompt, String correctOption,
                           int orderIndex, List<Option> options) {}
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
        return q!=null && filled(q.prompt()) && q.options()!=null
                && q.options().stream().allMatch(o->o!=null && filled(o.optionText()))
                && q.options().stream().anyMatch(o->o!=null && o.optionId().equals(q.correctOption()))
                && (q.part()<6 || filled(q.passageText()));
    }
    public int completedQuestions() {return questions==null?0:(int)questions.stream().filter(ManualExamDraft::complete).count();}
    private static boolean filled(String value){return value!=null&&!value.isBlank();}
}
