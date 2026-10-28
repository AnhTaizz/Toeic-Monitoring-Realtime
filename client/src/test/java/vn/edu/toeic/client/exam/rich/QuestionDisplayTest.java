package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.exam.rich.*;

class QuestionDisplayTest {
    private static final List<OptionContent> OPTIONS=List.of(new OptionContent("A",1,"finally"),new OptionContent("B",2,"promptly"),
            new OptionContent("C",3,"narrowly"),new OptionContent("D",4,"thoroughly"));
    private static CandidateQuestion question(int part,String prompt,List<OptionContent> options) {
        return new CandidateQuestion("q1",part<=4?Section.LISTENING:Section.READING,part,1,null,
                List.of(new ContentBlock("prompt",BlockType.TEXT,1,prompt,null,null)),options);
    }
    @Test void removesOnlyTheCompleteDuplicatedSuffixAndPreservesTheQuestion() {
        String prompt="The library _____ reopened last week.";
        assertThat(QuestionDisplay.prompt(question(5,prompt+" A. finally B. promptly C. narrowly D. thoroughly",OPTIONS))).isEqualTo(prompt);
        assertThat(QuestionDisplay.prompt(question(6,"A. finally B. promptly C. narrowly D. thoroughly",OPTIONS))).isEmpty();
        assertThat(QuestionDisplay.prompt(question(7,prompt+"\n(A) finally\n(B) promptly\n(C) narrowly\n(D) thoroughly",OPTIONS))).isEqualTo(prompt);
    }
    @Test void leavesRealProseAndPartialOrDifferentListsUntouched() {
        for(String prompt:List.of("Choose A. finally or B. promptly.","A. finally B. promptly C. narrowly D. differently", "Dad", "The library reopened."))
            assertThat(QuestionDisplay.prompt(question(5,prompt,OPTIONS))).isEqualTo(prompt);
    }
    @Test void handlesWhitespaceAndRegexCharactersLiterally() {
        var options=List.of(new OptionContent("A",1,"$5.00 (tax included)"),new OptionContent("B",2,"a+b?"));
        assertThat(QuestionDisplay.prompt(question(5,"Price?\nA. $5.00\u00a0(tax included)\nB. a+b?",options))).isEqualTo("Price?");
    }
    @Test void listeningShowsOneLetterPerOptionAndHidesOnlyPlaceholderTranscripts() {
        var placeholders=List.of(new OptionContent("A",1,"(A)"),new OptionContent("B",2,"B."),new OptionContent("C",3,null));
        var q=question(2,"A. B. C.",placeholders);
        assertThat(QuestionDisplay.prompt(q)).isEmpty();
        assertThat(placeholders).allSatisfy(o->assertThat(QuestionDisplay.optionText(q,o)).isEmpty());
        var real=new OptionContent("A",1,"A");
        assertThat(QuestionDisplay.optionText(question(5,"Article?",List.of(real)),real)).isEqualTo("A");
    }
}
