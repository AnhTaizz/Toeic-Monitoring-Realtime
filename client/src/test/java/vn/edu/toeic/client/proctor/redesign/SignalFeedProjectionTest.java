package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.SignalHistory;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** MOCK committed observations: normal startup and technical availability are not cheating verdicts. */
class SignalFeedProjectionTest {
    static final EventOrigin ORIGIN=new EventOrigin("CONNECTED","MOCK-connection");
    static final String A=RedesignFixtures.attempt(1),RUN="MOCK-collector-1";
    static final Instant CLIENT=Instant.parse("2026-10-25T04:00:00Z"),SERVER=CLIENT.plusSeconds(9);
    static UsbStorageSignal usb(long n){return new UsbStorageSignal("MOCK-usb-"+n,RUN,n,CLIENT,ORIGIN,
            UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,UsbStorageSignal.Quality.PARTIAL,1,null);}
    static SignalHistory.Entry entry(long n,Signal s){return new SignalHistory.Entry(A,n,SERVER.plusMillis(n),"LIVE",s);}

    @Test void usbPresenceAtStartupAppearsWithoutTechnicalOrWindowBaselines(){
        var f=new FocusSignal("MOCK-focus",RUN,1,CLIENT,ORIGIN,true,"INITIAL",null);
        var w=new WindowSignal("MOCK-window",RUN,2,CLIENT,ORIGIN,false,false,"INITIAL");
        var present=new UsbStorageSignal("MOCK-present",RUN,3,CLIENT,ORIGIN,
                UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START,UsbStorageSignal.Quality.PARTIAL,1,null);
        var projected=SignalFeedProjection.project(List.of(entry(1,f),entry(2,w),entry(3,present),entry(4,usb(4))),"MOCK candidate");
        assertThat(projected.history()).hasSize(2);
        assertThat(projected.alerts()).hasSize(2).allMatch(a->a.kind()==RoomModel.Kind.USB);
        assertThat(projected.alerts()).extracting(RoomModel.Alert::typeCode).containsExactly("USB_STORAGE_PRESENT_AT_START","USB_STORAGE_CONNECTED");
        var baseline=projected.alerts().getFirst();
        assertThat(baseline.title()).isEqualTo("USB có sẵn khi bắt đầu giám sát");
        assertThat(baseline.description()).contains("1 USB lưu trữ khi bắt đầu giám sát","Metadata một phần");
        assertThat(baseline.statusColour()).isEqualTo("blue");
        assertThat(baseline.serverTime()).isEqualTo(SERVER.plusMillis(3));
    }
    @Test void changedSourcesKeepBothTimesAndDoNotInventAHandledState(){
        var f=new FocusSignal("MOCK-focus",RUN,2,CLIENT,ORIGIN,false,"OTHER_OR_UNKNOWN",null);
        var w=new WindowSignal("MOCK-window",RUN,3,CLIENT,ORIGIN,true,false,"CHANGED");
        var result=SignalFeedProjection.project(List.of(entry(1,usb(1)),entry(2,f),entry(3,w)),"MOCK candidate");
        assertThat(result.alerts()).extracting(RoomModel.Alert::kind).containsExactly(RoomModel.Kind.USB,RoomModel.Kind.FOCUS,RoomModel.Kind.WINDOW);
        assertThat(result.alerts()).allSatisfy(a->{assertThat(a.clientTime()).isEqualTo(CLIENT);assertThat(a.serverTime()).isAfter(CLIENT);assertThat(a.open()).isFalse();assertThat(a.status()).doesNotContain("xử lý");});
        assertThat(result.alerts().getFirst().description()).contains("Metadata một phần");
    }
    @Test void internalDialogDoesNotAppearInTheCommonLists(){
        var f=new FocusSignal("MOCK-dialog",RUN,1,CLIENT,ORIGIN,false,"EXAM_INTERNAL_DIALOG",null);
        var result=SignalFeedProjection.project(List.of(entry(1,f)),"MOCK candidate");
        assertThat(result.alerts()).isEmpty();assertThat(result.history()).isEmpty();
    }
    @Test void technicalHealthAndGapStayOutOfTheCommonActionLists(){
        var ready=new ObserverStatusSignal("MOCK-ready",RUN,1,CLIENT,ORIGIN,
                ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.READY,ObserverStatusSignal.Reason.INITIAL);
        var gap=new SignalGap("MOCK-gap",RUN,2,CLIENT,ORIGIN,3,CLIENT,CLIENT);
        var error=new ObserverStatusSignal("MOCK-error",RUN,3,CLIENT,ORIGIN,
                ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.ERROR,ObserverStatusSignal.Reason.SCAN_FAILED);
        var result=SignalFeedProjection.project(List.of(entry(1,ready),entry(2,gap),entry(3,error)),"MOCK candidate");
        assertThat(result.alerts()).isEmpty();assertThat(result.history()).isEmpty();
    }
    @Test void removalFocusRestoreAndFullscreenChangesRemainVisible(){
        var removed=new UsbStorageSignal("MOCK-removed",RUN,1,CLIENT,ORIGIN,
                UsbStorageSignal.Kind.USB_STORAGE_REMOVED,UsbStorageSignal.Quality.PARTIAL,0,null);
        var restored=new FocusSignal("MOCK-restored",RUN,2,CLIENT,ORIGIN,true,"OTHER_OR_UNKNOWN",1000L);
        var fullscreen=new WindowSignal("MOCK-fullscreen",RUN,3,CLIENT,ORIGIN,false,true,"CHANGED");
        var result=SignalFeedProjection.project(List.of(entry(1,removed),entry(2,restored),entry(3,fullscreen)),"MOCK candidate");
        assertThat(result.alerts()).extracting(RoomModel.Alert::title).containsExactly("Quan sát rút USB","Cửa sổ thí sinh nhận focus","Trạng thái cửa sổ thay đổi");
        assertThat(result.history()).hasSize(3);
        assertThat(result.alerts().getLast().description()).contains("Thu nhỏ: không","Toàn màn hình: có");
    }
    @Test void equalWireEventIdsOnDifferentAttemptsStillHaveDifferentRowIdentities(){
        var signal=usb(1);var one=entry(1,signal);
        var other=new SignalHistory.Entry("MOCK-other",1,SERVER,"LIVE",signal);
        assertThat(SignalFeedProjection.project(List.of(one),"A").alerts().getFirst().id())
                .isNotEqualTo(SignalFeedProjection.project(List.of(other),"B").alerts().getFirst().id());
    }
}
