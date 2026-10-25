package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.Reading;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.Source;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.State;

/** MOCK last-recorded values: missing/partial/stale observations must not look healthy. */
class SignalSummaryViewTest {
    static final String ATTEMPT="MOCK-S01-ts001",RUN="MOCK-collector-1";
    static final Instant OBSERVED=Instant.parse("2026-10-24T15:00:00Z"),RECEIVED=OBSERVED.plusSeconds(2);
    static final Source UNKNOWN=new Source(State.UNKNOWN,"NO_DATA",null,null,null);

    static Reading usb(int count) {
        return new Reading("USB_STORAGE_CONNECTED",RUN,1,OBSERVED,RECEIVED,"PARTIAL","LIVE",count,null,null,null,"MOCK USB observation");
    }
    static CandidateSignals summary(Source usb,Source focus,Source window,boolean complete,boolean stale) {
        return new CandidateSignals(ATTEMPT,RUN,usb,focus,window,complete,stale,false,"",1,1,RECEIVED);
    }
    static CandidateSignals usbSummary(int count) {
        Reading reading=usb(count);
        return summary(new Source(State.OBSERVED,"READY",reading,reading,null),UNKNOWN,UNKNOWN,true,false);
    }

    @Test void missingDataAndReadyHealthAloneNeverInventZeroUsb() {
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.USB,null,false,false).text()).isEqualTo("USB ?");
        Reading ready=new Reading("OBSERVER_STATUS",RUN,1,OBSERVED,RECEIVED,"COMPLETE","LIVE",null,null,null,null,"MOCK READY");
        CandidateSignals value=summary(new Source(State.OBSERVED,"READY",null,null,ready),UNKNOWN,UNKNOWN,true,false);
        var badge=SignalSummaryView.badge(SignalSummaryView.Kind.USB,value,false,false);
        assertThat(badge.text()).isEqualTo("USB ?");
        assertThat(badge.colour()).isEqualTo("gray");
    }

    @Test void partialUsbObservationHasCountButNeverHealthyGreen() {
        var badge=SignalSummaryView.badge(SignalSummaryView.Kind.USB,usbSummary(1),false,false);
        assertThat(badge.text()).isEqualTo("USB 1");
        assertThat(badge.colour()).isEqualTo("amber");
        assertThat(badge.explanation()).contains("PARTIAL","không khẳng định định danh");
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.USB,usbSummary(0),false,false).colour()).isEqualTo("amber");
    }

    @Test void staleOrIncompleteHistoryMasksEvenAnObservedZero() {
        CandidateSignals value=usbSummary(0);
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.USB,value,true,false).text()).isEqualTo("USB ?");
        CandidateSignals partial=summary(value.usb(),UNKNOWN,UNKNOWN,false,false);
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.USB,partial,false,false).text()).isEqualTo("USB ?");
        CandidateSignals stale=summary(value.usb(),UNKNOWN,UNKNOWN,true,true);
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.USB,stale,false,false).colour()).isEqualTo("gray");
    }

    @Test void sourceErrorDoesNotPromoteTheRetainedObservationToCurrent() {
        CandidateSignals value=summary(new Source(State.ERROR,"SCAN_FAILED",usb(1),usb(1),null),UNKNOWN,UNKNOWN,true,false);
        var badge=SignalSummaryView.badge(SignalSummaryView.Kind.USB,value,false,false);
        assertThat(badge.text()).isEqualTo("USB ?");
        assertThat(badge.explanation()).contains("báo lỗi");
        assertThat(badge.colour()).isEqualTo("amber");
    }

    @Test void focusAndWindowStatesDescribeTheActualLastObservedBooleans() {
        Reading focus=new Reading("EXAM_FOCUS_CHANGED",RUN,2,OBSERVED,RECEIVED,"COMPLETE","LIVE",null,false,null,null,"MOCK focus lost");
        Reading window=new Reading("EXAM_WINDOW_STATE",RUN,3,OBSERVED,RECEIVED,"COMPLETE","LIVE",null,null,true,false,"MOCK minimized");
        CandidateSignals value=summary(UNKNOWN,new Source(State.OBSERVED,"READY",focus,null,null),
                new Source(State.OBSERVED,"READY",window,null,null),true,false);
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.FOCUS,value,false,false).text()).isEqualTo("Rời bài");
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.WINDOW,value,false,false).text()).isEqualTo("Thu nhỏ");
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.FOCUS,value,false,false).colour()).isEqualTo("amber");
        assertThat(SignalSummaryView.badge(SignalSummaryView.Kind.FOCUS,value,true,false).text()).isEqualTo("Focus ?");
    }
}
