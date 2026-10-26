package vn.edu.toeic.client.proctor.signals;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.signals.CandidateSignalsControllerTest.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MOCK refused cancellation: old socket/scope futures must keep their two global permits. */
class CandidateSignalsPendingLimitTest {
    @Test void twoUncancellableOldRequestsCannotBeOverlappedByTwoNewScopeRequests()throws Exception{
        var api=new Api();api.auto=false;api.uncancellable=true;
        for(String id:List.of("A","B","C","D"))api.history.put(id,List.of());
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            f.controller.roster(List.of(presence("A","OLD-A",true),presence("B","OLD-B",true)),true);
            var oldA=api.next();var oldB=api.next();assertThat(api.active.get()).isEqualTo(2);
            var newRoster=List.of(presence("C","NEW-C",true),presence("D","NEW-D",true));
            f.controller.roster(newRoster,true);f.await(s->s.loading()&&s.candidates().containsKey("C"));
            // A second identical roster publication is a worker barrier after the new auth/pump callback.
            f.action(()->f.controller.roster(newRoster,true),s->s.loading()&&s.candidates().containsKey("D"));
            assertThat(api.queries).hasSize(2);assertThat(api.active.get()).isEqualTo(2);
            oldA.future().complete(api.answer(oldA));var newC=api.next();assertThat(newC.attempt()).isEqualTo("C");
            oldB.future().complete(api.answer(oldB));var newD=api.next();assertThat(newD.attempt()).isEqualTo("D");
            newC.future().complete(api.answer(newC));newD.future().complete(api.answer(newD));
            var done=f.await(s->!s.loading()&&s.candidates().containsKey("D"));
            assertThat(done.candidates()).containsOnlyKeys("C","D");assertThat(api.maximum.get()).isEqualTo(2);
        }
    }
}
