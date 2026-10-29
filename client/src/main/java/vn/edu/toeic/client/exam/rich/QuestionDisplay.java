package vn.edu.toeic.client.exam.rich;

import java.util.Comparator;
import java.util.regex.Pattern;
import vn.edu.toeic.protocol.exam.rich.CandidateQuestion;
import vn.edu.toeic.protocol.exam.rich.OptionContent;

/** Presentation only: keep imported question data intact while hiding a repeated option suffix. */
public final class QuestionDisplay {
    private QuestionDisplay() {}

    public static String prompt(CandidateQuestion question) {
        String text=RichExamModel.text(question.blocks());
        var options=question.options().stream().sorted(Comparator.comparingInt(OptionContent::orderIndex)).toList();
        if(options.size()<2)return text;
        StringBuilder suffix=new StringBuilder("(?<!\\S)");
        for(var option:options) {
            String id=Pattern.quote(option.optionId());
            suffix.append("(?:\\(").append(id).append("\\)|").append(id).append("[.)])\\s*");
            String content=optionText(question,option);
            if(!content.isBlank()) {
                String[] words=content.strip().split("[\\s\\p{Z}]+");
                for(int i=0;i<words.length;i++) {
                    if(i>0)suffix.append("\\s+");
                    suffix.append(Pattern.quote(words[i]));
                }
            }
            suffix.append("\\s*");
        }
        suffix.append("$");
        var match=Pattern.compile(suffix.toString(),Pattern.UNICODE_CHARACTER_CLASS).matcher(text);
        return match.find()?text.substring(0,match.start()).stripTrailing():text;
    }

    static String optionText(CandidateQuestion question,OptionContent option) {
        String text=option.text()==null?"":option.text();
        if(question.part()<=2 && (text.isBlank() || text.strip().matches("(?:\\("+Pattern.quote(option.optionId())+"\\)|"+Pattern.quote(option.optionId())+"[.)]?)")))return "";
        return text.isBlank()?"Đáp án "+option.optionId():text;
    }
}
