package vn.edu.toeic.client.proctor.redesign;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * "Điều khiển ca" page body: the PROCTORED_V2 panel first, the existing V1 controls (B4) unchanged in a second tab.
 * Release default (see {@link vn.edu.toeic.client.LegacyUi}) shows only the ACCOUNT_PIN room tab.
 * Closing the page closes every pane it holds, so their timers and HTTP workers stop on logout.
 */
final class ProctorSessionControls extends TabPane implements AutoCloseable {
    private final AutoCloseable v2, legacy;
    private final ProctorV2SessionPane accountPin;

    <L extends Node & AutoCloseable> ProctorSessionControls(ProctorV2SessionPane v2, L legacy) {
        this(null,v2,legacy);
    }
    /** ACCOUNT_PIN only: the old V2 room and V1 panes are not created, so none of their timers start. */
    ProctorSessionControls(ProctorV2SessionPane accountPin) {
        this.v2=null;this.legacy=null;this.accountPin=accountPin;
        setId("session-controls-tabs");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getTabs().add(accountPinTab(accountPin));
    }
    <L extends Node & AutoCloseable> ProctorSessionControls(ProctorV2SessionPane accountPin,ProctorV2SessionPane v2,L legacy) {
        this.v2 = v2;
        this.legacy = legacy;
        this.accountPin=accountPin;
        setId("session-controls-tabs");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        Tab rich = new Tab("Ca PROCTORED_V2 (200 câu)", v2);
        rich.setId("tab-v2");
        rich.selectedProperty().addListener((observable, was, selected) -> v2.tabSelected(selected));
        Tab old = new Tab("Ca V1 / công cụ cũ", legacy);
        old.setId("tab-v1");
        if(accountPin!=null) {
            getTabs().add(accountPinTab(accountPin));rich.setText("Phòng / ca V2 cũ");v2.tabSelected(false);
        }
        getTabs().addAll(rich, old);
    }

    private static Tab accountPinTab(ProctorV2SessionPane accountPin) {
        Tab current=new Tab("Phòng thi TOEIC",accountPin);current.setId("tab-account-pin");
        current.selectedProperty().addListener((observable,was,selected)->accountPin.tabSelected(selected));
        return current;
    }

    @Override public void close() {
        if(accountPin!=null)accountPin.close();
        if(v2!=null)try { v2.close(); } catch (Exception ignored) { }
        if(legacy!=null)try { legacy.close(); } catch (Exception ignored) { }
    }
}
