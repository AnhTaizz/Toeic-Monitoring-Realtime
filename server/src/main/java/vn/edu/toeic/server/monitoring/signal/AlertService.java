package vn.edu.toeic.server.monitoring.signal;

import com.google.gson.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/** No cache, mutations, observer, scheduler or persistent alert pipeline. */
@Service
@ConditionalOnProperty(name={"toeic.monitoring.signal-experiment-enabled","toeic.monitoring.alert-experiment-enabled"},havingValue="true")
public class AlertService {
    private final JdbcClient jdbc;private final SignalService signals;private final AlertRules.Policy policy;
    public AlertService(JdbcClient jdbc,SignalService signals,@Value("${toeic.monitoring.alert.bucket-seconds:60}") int bucket,
            @Value("${toeic.monitoring.alert.window-seconds:300}") int window,@Value("${toeic.monitoring.alert.review-count:3}") int review,
            @Value("${toeic.monitoring.alert.high-count:6}") int high,@Value("${toeic.monitoring.alert.source-cap:200}") int cap){this.jdbc=jdbc;this.signals=signals;policy=new AlertRules.Policy(bucket,window,review,high,cap);}
    @Transactional(timeout=10,isolation=Isolation.REPEATABLE_READ)
    public JsonObject read(AuthenticatedUser user,String hash,String attempt,Map<String,String[]> query){
        signals.authorizeHistory(user,hash,attempt); // same token/user/assignment/ACTIVE locks, before any data/query validation
        if(!Set.of("cutoff").containsAll(query.keySet())||query.values().stream().anyMatch(v->v.length!=1))throw new IllegalArgumentException();
        Instant checkedAt=jdbc.sql("SELECT clock_timestamp()").query((r,n)->r.getTimestamp(1).toInstant()).single();
        Instant cutoff=checkedAt;
        if(query.containsKey("cutoff")){String value=query.get("cutoff")[0];if(value.length()>40)throw new IllegalArgumentException();try{cutoff=Instant.parse(value);}catch(java.time.DateTimeException invalid){throw new IllegalArgumentException();}if(cutoff.isBefore(Instant.EPOCH)||cutoff.isAfter(checkedAt))throw new IllegalArgumentException();}
        Instant from=cutoff.minusSeconds(policy.windowSeconds());var inputs=new ArrayList<AlertRules.Input>();var limited=new JsonArray();int excluded=0;
        var typed=jdbc.sql("SELECT * FROM monitoring_signal_events WHERE attempt_id=:a AND received_at>=:f AND received_at<:c ORDER BY received_at DESC,timeline_sequence DESC LIMIT :n")
            .param("a",attempt).param("f",Timestamp.from(from)).param("c",Timestamp.from(cutoff)).param("n",policy.sourceCap()+1).query(SignalService::read).list();
        if(typed.size()>policy.sourceCap())limited.add("SIGNAL");
        for(var row:typed.stream().limit(policy.sourceCap()).toList())inputs.add(typed(row));
        var processes=jdbc.sql("SELECT event_id,collector_session_id,policy_version,process_name,metadata_quality,received_at FROM monitoring_events WHERE attempt_id=:a AND received_at>=:f AND received_at<:c ORDER BY received_at DESC,id DESC LIMIT :n")
            .param("a",attempt).param("f",Timestamp.from(from)).param("c",Timestamp.from(cutoff)).param("n",policy.sourceCap()+1).query((r,n)->{
                return process(attempt,r.getString("collector_session_id"),r.getString("event_id"),r.getString("policy_version"),r.getString("process_name"),r.getString("metadata_quality"),r.getTimestamp("received_at").toInstant());
            }).list();
        if(processes.size()>policy.sourceCap())limited.add("PROCESS");
        for(var row:processes.stream().limit(policy.sourceCap()).toList()){if(row==null)excluded++;else inputs.add(row);}
        var gaps=jdbc.sql("SELECT gap_id,collector_session_id,received_at,dropped_count FROM monitoring_gaps WHERE attempt_id=:a AND received_at>=:f AND received_at<:c ORDER BY received_at DESC,id DESC LIMIT :n")
            .param("a",attempt).param("f",Timestamp.from(from)).param("c",Timestamp.from(cutoff)).param("n",policy.sourceCap()+1).query((r,n)->new AlertRules.Input(attempt,r.getString("collector_session_id"),"process-gap:"+attempt+":"+r.getString("gap_id"),"PROCESS_QUEUE","MONITORING_GAP",r.getTimestamp("received_at").toInstant(),"DATA_GAP",false,r.getLong("dropped_count"))).list();
        if(gaps.size()>policy.sourceCap())limited.add("PROCESS_GAP");inputs.addAll(gaps.stream().limit(policy.sourceCap()).toList());
        var presence=jdbc.sql("SELECT gap_id,collector_session_id,timeout_detected_at FROM monitoring_interruptions WHERE attempt_id=:a AND timeout_detected_at>=:f AND timeout_detected_at<:c ORDER BY timeout_detected_at DESC,gap_id DESC LIMIT :n")
            .param("a",attempt).param("f",Timestamp.from(from)).param("c",Timestamp.from(cutoff)).param("n",policy.sourceCap()+1).query((r,n)->new AlertRules.Input(attempt,r.getString("collector_session_id"),"presence:"+attempt+":"+r.getString("gap_id"),"SERVER_PRESENCE","HEARTBEAT_INTERRUPTION",r.getTimestamp("timeout_detected_at").toInstant(),"PRESENCE_INTERRUPTION",false,0)).list();
        if(presence.size()>policy.sourceCap())limited.add("PRESENCE");inputs.addAll(presence.stream().limit(policy.sourceCap()).toList());
        var result=new AlertRules().evaluate(attempt,inputs,policy,cutoff);var p=new JsonObject();p.addProperty("protocolVersion","v0");p.addProperty("schemaVersion",1);p.addProperty("attemptId",attempt);p.addProperty("policyVersion",policy.version());p.addProperty("experimental",true);p.addProperty("from",from.toString());p.addProperty("cutoff",cutoff.toString());p.addProperty("coverage","WINDOW_ONLY");p.add("sourceLimited",limited);p.addProperty("summaryLimited",result.summaryLimited());p.addProperty("sourceCap",policy.sourceCap());p.addProperty("inputEvents",result.inputEvents());p.addProperty("gapEvents",result.gapEvents());p.addProperty("unavailableEvents",result.unavailableEvents());p.addProperty("excludedProcessEvents",excluded);
        p.addProperty("coverageNote","Chỉ cửa sổ server received/recorded time trong MVCC snapshot committed; không phải toàn history. Cap/gap/nguồn lỗi có thể làm thiếu dữ liệu. Không đánh giá Listening/current presence/current process state hoặc nội dung bị bỏ. Không có nhóm không chứng minh bình thường.");
        var items=new JsonArray();result.summaries().forEach(s->items.add(s.json()));p.add("items",items);SignalJson.size(p,512*1024);return p;
    }
    static AlertRules.Input process(String attempt,String run,String id,String version,String name,String quality,Instant received){
        if(!FullSnapshotPayload.POLICY.equals(version)||name==null||!FullSnapshotPayload.EXECUTABLES.contains(name.toLowerCase(Locale.ROOT)))return null;
        return new AlertRules.Input(attempt,run,"process:"+attempt+":"+id,"PROCESS","PROCESS_OBSERVED",received,"COMPLETE".equals(quality)?"OBSERVATION":"SOURCE_UNAVAILABLE",false,0);
    }
    static AlertRules.Input typed(SignalService.Stored row){Signal s=row.signal();String rule="OBSERVATION";long dropped=0;boolean repeat=false;
        if(s instanceof ObserverStatusSignal status&&status.status()!=ObserverStatusSignal.State.READY)rule="SOURCE_UNAVAILABLE";
        if(s instanceof SignalGap gap){rule="DATA_GAP";dropped=gap.droppedCount();}
        if(s instanceof FocusSignal f)repeat=!f.focused();if(s instanceof WindowSignal w)repeat=w.minimized();if(s instanceof UsbStorageSignal u)repeat=u.kind()==UsbStorageSignal.Kind.USB_STORAGE_CONNECTED;
        // Separate lost/restored and minimized/not-minimized before counting. Never combine different transitions.
        String type=s.eventType();if(s instanceof FocusSignal f)type+=f.focused()?"_RESTORED":"_LOST";if(s instanceof WindowSignal w)type+=w.minimized()?"_MINIMIZED":"_OTHER";
        return new AlertRules.Input(row.attemptId(),s.observerSessionId(),"signal:"+row.attemptId()+":"+s.eventId(),s.source(),type,row.receivedAt(),rule,repeat,dropped);
    }
}
