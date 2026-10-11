package vn.edu.toeic.client.dashboard;

import java.util.ArrayList;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.realtime.ConnectionState;

/** Pure state, owned by one dashboard worker. No JavaFX, HTTP or socket writes here. */
public final class DashboardModel {
    public record Snapshot(List<Presence> attempts, String selected, List<Event> events, List<Interruption> interruptions,
            ConnectionState connection, boolean rosterLoading, boolean eventsLoading, boolean historyLoading,
            boolean rosterStale, boolean eventsStale, boolean historyStale,
            String rosterError, String eventsError, String historyError, boolean loginRequired,
            MonitoringStateView currentState,boolean stateLoading,boolean stateStale,String stateError,
            List<Gap> gaps,boolean gapsLoading,boolean gapsStale,String gapsError) {
        public Snapshot { attempts=List.copyOf(attempts); events=List.copyOf(events); interruptions=List.copyOf(interruptions);gaps=List.copyOf(gaps); }
    }
    private final int bufferLimit, rowLimit;
    private Set<String> scope;
    private Map<String,Presence> roster = new LinkedHashMap<>();
    private final Map<String,Presence> presenceBuffer = new LinkedHashMap<>();
    private Map<String,Event> events = new LinkedHashMap<>();
    private final Map<String,Event> eventBuffer = new LinkedHashMap<>();
    private List<Interruption> history = List.of();
    private String selected;
    private ConnectionState connection = ConnectionState.DISCONNECTED;
    private boolean rosterLoading, eventsLoading, historyLoading, rosterStale=true, eventsStale=true, historyStale=true;
    private boolean eventOverflow, presenceOverflow, loginRequired;
    private String rosterError="", eventsError="", historyError="";
    private MonitoringStateView currentState;
    private boolean stateLoading,stateStale=true;
    private String stateError="";
    private List<Gap> gaps=List.of();
    private boolean gapsLoading,gapsStale=true;
    private String gapsError="";
    public DashboardModel(Set<String> scope, int bufferLimit, int rowLimit) {
        if (bufferLimit<1 || rowLimit<1 || bufferLimit>100000 || rowLimit>100000) throw new IllegalArgumentException();
        this.scope=Set.copyOf(scope); this.bufferLimit=bufferLimit; this.rowLimit=rowLimit;
    }
    public Snapshot snapshot() { return new Snapshot(new ArrayList<>(roster.values()),selected,new ArrayList<>(events.values()),history,
            connection,rosterLoading,eventsLoading,historyLoading,rosterStale,eventsStale,historyStale,rosterError,eventsError,historyError,loginRequired,
            currentState,stateLoading,stateStale || rosterStale || connection!=ConnectionState.CONNECTED
                || (selected!=null && roster.containsKey(selected) && !roster.get(selected).status().equals("ONLINE")),stateError,
            gaps,gapsLoading,gapsStale || rosterStale || connection!=ConnectionState.CONNECTED,gapsError); }
    void connection(ConnectionState state) {
        connection=state;
        if (state==ConnectionState.CONNECTED) resetState();
        if (state != ConnectionState.CONNECTED) {
            gapsStale=true;gapsLoading=false;
            stateStale=true; stateLoading=false;
            rosterStale=eventsStale=historyStale=true;
            rosterLoading=eventsLoading=historyLoading=false;
        }
    }
    void scope(Set<String> fresh) {
        scope=Set.copyOf(fresh); roster.keySet().retainAll(scope); presenceBuffer.keySet().retainAll(scope);
        if (selected!=null && !scope.contains(selected)) select(null);
    }
    void beginRoster() { rosterLoading=true; rosterStale=true; rosterError=""; presenceOverflow=false; presenceBuffer.clear(); }
    private static Presence newer(Presence old, Presence next) {
        if (old==null || next.revision()>old.revision()) return next;
        if (next.revision()==old.revision() && !old.equals(next)) throw conflict();
        return old;
    }
    void roster(List<Presence> list) {
        if (list.size()>rowLimit) throw conflict();
        Map<String,Presence> next = new LinkedHashMap<>();
        for (Presence item : list) {
            if (!scope.contains(item.attemptId()) || next.containsKey(item.attemptId())) throw conflict();
            Presence value=newer(roster.get(item.attemptId()),item);
            value=newer(value,presenceBuffer.getOrDefault(item.attemptId(),value));
            next.put(item.attemptId(),value);
        }
        roster=next; presenceBuffer.clear(); rosterLoading=false; rosterStale=presenceOverflow;
        rosterError=presenceOverflow?"Bộ đệm đầy. Cần làm mới danh sách.":"";
        if (selected!=null && !roster.containsKey(selected)) select(null);
    }
    /** Returns true when a selected status transition needs a real history refresh. */
    boolean presence(Presence value) {
        if (!scope.contains(value.attemptId())) return false;
        Presence old=roster.get(value.attemptId());
        if (old==null) {
            if (rosterLoading && (presenceBuffer.containsKey(value.attemptId()) || presenceBuffer.size()<bufferLimit))
                presenceBuffer.put(value.attemptId(),newer(presenceBuffer.get(value.attemptId()),value));
            else { rosterStale=true; presenceOverflow=true; rosterError="Có cập nhật ngoài danh sách. Hãy làm mới quyền/danh sách."; }
            return false;
        }
        Presence accepted=newer(old,value); roster.put(value.attemptId(),accepted);
        return value.attemptId().equals(selected) && accepted!=old
                && (!old.status().equals(accepted.status()) || !old.reason().equals(accepted.reason()));
    }
    void select(String attempt) {
        if (attempt!=null && !roster.containsKey(attempt)) return;
        selected=attempt; events.clear(); eventBuffer.clear(); history=List.of();
        gaps=List.of();gapsLoading=false;gapsStale=true;gapsError="";
        resetState();
        eventsLoading=historyLoading=false; eventsStale=historyStale=true; eventsError=historyError="";
    }
    String selected() { return selected; }
    boolean allowed(String attempt) { return roster.containsKey(attempt); }
    void remove(String attempt) { roster.remove(attempt); presenceBuffer.remove(attempt); if (attempt.equals(selected)) select(null); }
    void beginEvents() { eventsLoading=true; eventsStale=true; eventsError=""; eventBuffer.clear(); eventOverflow=false; }
    private static void mergeEvent(Map<String,Event> target, Event value) {
        Event old=target.putIfAbsent(value.eventId(),value);
        if (old!=null && !old.equals(value)) throw conflict();
    }
    void warning(Event event) {
        if (!event.attemptId().equals(selected) || !roster.containsKey(event.attemptId())) return;
        if (events.containsKey(event.eventId()) && !events.get(event.eventId()).equals(event)) throw conflict();
        if (eventsLoading) {
            if (!eventBuffer.containsKey(event.eventId()) && eventBuffer.size()>=bufferLimit) {
                eventOverflow=true; eventsStale=true; eventsError="Bộ đệm cảnh báo đầy. Cần tải lại HTTP."; return;
            }
            mergeEvent(eventBuffer,event);
        }
        if (!events.containsKey(event.eventId()) && events.size()>=rowLimit) {
            eventOverflow=true; eventsStale=true; eventsError="Đạt giới hạn dòng. Cần tải lại HTTP."; return;
        }
        mergeEvent(events,event);
    }
    void events(List<Event> values) {
        if (values.size()>rowLimit) throw conflict();
        Map<String,Event> next=new LinkedHashMap<>();
        for (Event value : values) {
            if (!value.attemptId().equals(selected)) throw conflict();
            Event old=events.get(value.eventId()); if (old!=null && !old.equals(value)) throw conflict();
            mergeEvent(next,value);
        }
        for (Event value : eventBuffer.values()) {
            if (!next.containsKey(value.eventId()) && next.size()>=rowLimit) { eventOverflow=true; break; }
            mergeEvent(next,value);
        }
        events=next; eventBuffer.clear(); eventsLoading=false; eventsStale=eventOverflow;
        eventsError=eventOverflow?"Cảnh báo chưa đầy đủ. Cần tải lại HTTP.":"";
    }
    void beginHistory() { historyLoading=true; historyStale=true; historyError=""; }
    void history(List<Interruption> values) {
        if (values.size()>rowLimit) throw conflict();
        Map<String,Interruption> old=new LinkedHashMap<>(); history.forEach(value -> old.put(value.gapId(),value));
        Set<String> seen=new HashSet<>();
        for (Interruption value : values) {
            if (!value.attemptId().equals(selected) || !seen.add(value.gapId())) throw conflict();
            Interruption previous=old.get(value.gapId());
            if (previous!=null && (!previous.attemptId().equals(value.attemptId()) || !previous.collectorSessionId().equals(value.collectorSessionId())
                    || !previous.lastSeenAt().equals(value.lastSeenAt()) || !previous.timeoutDetectedAt().equals(value.timeoutDetectedAt())
                    || (previous.recoveredAt()!=null && !previous.recoveredAt().equals(value.recoveredAt())))) throw conflict();
        }
        history=List.copyOf(values); historyLoading=false; historyStale=false; historyError="";
    }
    void rosterFailed(String message) { rosterLoading=false; rosterStale=true; rosterError=message; }
    void eventsFailed(String message) { eventsLoading=false; eventsStale=true; eventsError=message; }
    void historyFailed(String message) { historyLoading=false; historyStale=true; historyError=message; }
    void beginState() { stateLoading=true; stateStale=true; stateError=""; }
    void beginGaps() { gapsLoading=true;gapsStale=true;gapsError=""; }
    void gaps(List<Gap> values) {
        if(values.size()>rowLimit) throw conflict();
        Set<String> seen=new HashSet<>();
        Map<String,Gap> previous=new LinkedHashMap<>();gaps.forEach(value -> previous.put(value.gapId(),value));
        for(Gap value:values) {
            if(!value.attemptId().equals(selected) || !seen.add(value.gapId())
                    || (previous.containsKey(value.gapId()) && !previous.get(value.gapId()).equals(value))) throw conflict();
        }
        gaps=List.copyOf(values);gapsLoading=false;gapsStale=false;gapsError="";
    }
    void gapsFailed(String message) { gapsLoading=false;gapsStale=true;gapsError=message; }
    void state(MonitoringStateView value,boolean http) {
        if (!value.attemptId().equals(selected) || !allowed(value.attemptId())) return;
        if (currentState!=null) {
            if (!value.serverInstanceId().equals(currentState.serverInstanceId())) throw conflict();
            if (value.revision()<currentState.revision()) { if(http) stateLoading=false; return; }
            if (value.revision()==currentState.revision() && !value.equals(currentState)) throw conflict();
        }
        currentState=value;
        if(http) stateLoading=false;
        stateStale=!value.status().equals("SYNCED"); stateError="";
    }
    private void resetState() { currentState=null; stateLoading=false; stateStale=true; stateError=""; }
    void stateFailed(String message) { stateLoading=false; stateStale=true; stateError=message; }
    void overflow() { rosterStale=eventsStale=historyStale=stateStale=gapsStale=true; rosterError="Bộ đệm cập nhật đầy. Cần đồng bộ lại HTTP."; }
    void expire() {
        roster.clear(); scope=Set.of(); presenceBuffer.clear(); select(null); loginRequired=true;
        rosterLoading=false; rosterStale=true; rosterError="Phiên hết hiệu lực. Hãy đăng nhập lại.";
    }
    private static IllegalStateException conflict() { return new IllegalStateException("Dữ liệu mâu thuẫn. Hãy làm mới để đối chiếu HTTP."); }
}
