package vn.edu.toeic.protocol.admin;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ManualExamDraftTest {
    @Test void partOneNeedsImageAndAudioButDoesNotRequireTranscribingSpokenOptions() {
        var options=List.of(new ManualExamDraft.Option("A","",1),new ManualExamDraft.Option("B","",2),new ManualExamDraft.Option("C","",3),new ManualExamDraft.Option("D","",4));
        var complete=new ManualExamDraft.Question("q1","LISTENING",1,null,"","001-a.wav","","A",1,options,List.of("001-a.png"));
        assertThat(ManualExamDraft.complete(complete)).isTrue();
        assertThat(ManualExamDraft.complete(new ManualExamDraft.Question("q1","LISTENING",1,null,"","001-a.wav","","A",1,options))).isFalse();
        assertThat(ManualExamDraft.complete(new ManualExamDraft.Question("q1","LISTENING",1,null,"","","","A",1,options,List.of("001-a.png")))).isFalse();
    }
    @Test void templateAndSharedUnitBoundariesMatchToeicParts() {
        var draft=ManualExamDraft.empty();assertThat(draft.questions()).hasSize(200);assertThat(draft.completedQuestions()).isZero();
        assertThat(draft.questions().stream().filter(q->q.part()==1).count()).isEqualTo(6);
        assertThat(draft.questions().stream().filter(q->q.part()==2).count()).isEqualTo(25);
        assertThat(draft.questions().stream().filter(q->q.part()==3).count()).isEqualTo(39);
        assertThat(draft.questions().stream().filter(q->q.part()==4).count()).isEqualTo(30);
        assertThat(draft.questions().stream().filter(q->q.part()==5).count()).isEqualTo(30);
        assertThat(draft.questions().stream().filter(q->q.part()==6).count()).isEqualTo(16);
        assertThat(draft.questions().stream().filter(q->q.part()==7).count()).isEqualTo(54);
        assertThat(ManualExamDraft.groupStart(34)).isEqualTo(32);assertThat(ManualExamDraft.groupEnd(34)).isEqualTo(34);
        assertThat(ManualExamDraft.groupStart(73)).isEqualTo(71);assertThat(ManualExamDraft.groupEnd(134)).isEqualTo(134);
    }
}
