package vn.edu.toeic.server.monitoring.signal;

import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.monitoring.EventConflictException;

/** Isolated experimental lane. No reducer, push, observer, retention job or operational capture hook. */
@Service
@ConditionalOnProperty(name="toeic.monitoring.signal-experiment-enabled",havingValue="true")
public class SignalService {
    private final JdbcClient jdbc;
    public SignalService(JdbcClient jdbc){this.jdbc=jdbc;}
    @Transactional(timeout=10)
    public Stored store(AuthenticatedUser user,String tokenHash,SignalMessage message,String connection){
        String attempt=message.attemptId();lockAttempt(attempt);
        jdbc.sql("INSERT INTO monitoring_signal_counters(attempt_id) VALUES (:a) ON CONFLICT DO NOTHING").param("a",attempt).update();
        long current=jdbc.sql("SELECT last_sequence FROM monitoring_signal_counters WHERE attempt_id=:a FOR UPDATE").param("a",attempt).query(Long.class).single();
        // Recheck after the potentially long counter wait, hold token/user/attempt locks through COMMIT.
        authorize(user,tokenHash,attempt,Role.CANDIDATE);
        var existing=jdbc.sql("SELECT * FROM monitoring_signal_events WHERE attempt_id=:a AND event_id=:e")
            .param("a",attempt).param("e",message.signal().eventId()).query(SignalService::read).optional();
        if(existing.isPresent()){
            if(!existing.get().signal().equals(message.signal()))throw new EventConflictException();
            return new Stored(existing.get().signal(),attempt,existing.get().sequence(),existing.get().receivedAt(),existing.get().deliveryStatus(),true);
        }
        if(current==SignalJson.MAX_INTEGER)throw new IllegalStateException("Signal sequence exhausted");
        DisplaySignal s=message.signal();
        Stored result=jdbc.sql("""
            INSERT INTO monitoring_signal_events(attempt_id,event_id,schema_version,observer_session_id,observation_sequence,event_type,source,metadata_quality,
              observed_at,observation_context,observation_connection_id,details,timeline_sequence,delivery_status)
            VALUES (:a,:e,1,:run,:seq,'DISPLAY_COUNT_OBSERVED','JAVAFX_DISPLAY','COMPLETE',:observed,:context,:connection,CAST(:details AS jsonb),:timeline,:delivery)
            RETURNING *
            """).param("a",attempt).param("e",s.eventId()).param("run",s.observerSessionId()).param("seq",s.observationSequence())
            .param("observed",Timestamp.from(s.observedAt())).param("context",s.origin().context()).param("connection",s.origin().connectionId(),Types.VARCHAR)
            .param("details",s.json().getAsJsonObject("details").toString()).param("timeline",current+1).param("delivery",s.origin().deliveryStatus(connection))
            .query(SignalService::read).single();
        jdbc.sql("UPDATE monitoring_signal_counters SET last_sequence=:n WHERE attempt_id=:a").param("n",current+1).param("a",attempt).update();
        return result; // Spring proxy commits BEFORE caller can emit ACK. No socket call in this service.
    }
    @Transactional(timeout=10)
    public JsonObject capabilities(AuthenticatedUser user,String tokenHash,String attempt){
        lockAttempt(attempt);authorize(user,tokenHash,attempt,null);
        JsonObject p=header(attempt);JsonArray types=new JsonArray();types.add(DisplaySignal.TYPE);p.add("supportedEventTypes",types);return p;
    }
    @Transactional(timeout=10)
    public JsonObject history(AuthenticatedUser user,String tokenHash,String attempt,Map<String,String[]> query){
        lockAttempt(attempt);authorize(user,tokenHash,attempt,Role.PROCTOR); // before cursor/counter lookup
        if(!Set.of("limit","after","through").containsAll(query.keySet())||query.values().stream().anyMatch(v->v.length!=1))throw new IllegalArgumentException();
        int limit=(int)number(query,"limit",100,1,100);long after=number(query,"after",0,0,SignalJson.MAX_INTEGER);
        long current=jdbc.sql("SELECT last_sequence FROM monitoring_signal_counters WHERE attempt_id=:a").param("a",attempt).query(Long.class).optional().orElse(0L);
        long through=number(query,"through",current,0,SignalJson.MAX_INTEGER);if(after>through||through>current)throw new IllegalArgumentException();
        var rows=jdbc.sql("SELECT * FROM monitoring_signal_events WHERE attempt_id=:a AND timeline_sequence>:after AND timeline_sequence<=:through ORDER BY timeline_sequence LIMIT :limit")
            .param("a",attempt).param("after",after).param("through",through).param("limit",limit+1).query(SignalService::read).list();
        boolean more=rows.size()>limit;JsonArray items=new JsonArray();for(Stored row:rows.subList(0,Math.min(limit,rows.size())))items.add(row.json());
        JsonObject p=header(attempt);p.add("items",items);p.addProperty("afterSequence",after);p.addProperty("throughSequence",through);
        p.add("nextAfterSequence",more?new JsonPrimitive(rows.get(limit-1).sequence()):JsonNull.INSTANCE);p.addProperty("hasMore",more);SignalJson.size(p,512*1024);return p;
    }
    private static long number(Map<String,String[]> q,String k,long fallback,long min,long max){
        if(!q.containsKey(k))return fallback;String s=q.get(k)[0];if(!s.matches("0|[1-9][0-9]{0,15}"))throw new IllegalArgumentException();long n=Long.parseLong(s);if(n<min||n>max)throw new IllegalArgumentException();return n;
    }
    private static JsonObject header(String a){JsonObject p=new JsonObject();p.addProperty("protocolVersion","v0");p.addProperty("schemaVersion",1);p.addProperty("traceId",UUID.randomUUID().toString());p.addProperty("attemptId",a);return p;}
    private void lockAttempt(String a){SignalJson.id(a);if(jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id=:a FOR SHARE").param("a",a).query(String.class).optional().isEmpty())throw AccessDeniedException.forbidden();}
    private void authorize(AuthenticatedUser user,String hash,String attempt,Role required){
        var identities=jdbc.sql("""
            SELECT u.id,u.username,u.role,u.enabled,s.revoked_at,s.expires_at
            FROM login_sessions s JOIN user_accounts u ON u.id=s.user_id WHERE s.token_hash=:hash FOR SHARE OF s,u
            """).param("hash",hash).query((r,n)->{
                return new LockedSession(new AuthenticatedUser(r.getLong("id"),r.getString("username"),Role.valueOf(r.getString("role"))),
                    r.getBoolean("enabled"),r.getTimestamp("revoked_at")!=null,r.getTimestamp("expires_at").toInstant());
            }).list();
        // SELECT target-list expressions can be evaluated BEFORE LockRows waits. Read the clock only AFTER the locks are held.
        Instant checkedAt=jdbc.sql("SELECT clock_timestamp()").query((r,n)->r.getTimestamp(1).toInstant()).single();
        if(identities.size()!=1)throw AccessDeniedException.unauthorized();
        LockedSession locked=identities.getFirst();
        if(!locked.user().equals(user)||!locked.enabled()||locked.revoked()||!locked.expiresAt().isAfter(checkedAt))throw AccessDeniedException.unauthorized();
        if(required!=null&&user.role()!=required)throw AccessDeniedException.forbidden();
        Long owner=jdbc.sql("SELECT candidate_user_id FROM monitoring_attempts WHERE attempt_id=:a AND state='ACTIVE'").param("a",attempt).query(Long.class).optional().orElse(null);
        if(owner==null)throw AccessDeniedException.forbidden();
        if(user.role()==Role.CANDIDATE){if(owner!=user.userId())throw AccessDeniedException.forbidden();}
        else if(user.role()==Role.PROCTOR){if(jdbc.sql("SELECT proctor_user_id FROM monitoring_proctor_assignments WHERE attempt_id=:a AND proctor_user_id=:u FOR SHARE")
            .param("a",attempt).param("u",user.userId()).query(Long.class).optional().isEmpty())throw AccessDeniedException.forbidden();}
        else throw AccessDeniedException.forbidden();
    }
    private record LockedSession(AuthenticatedUser user,boolean enabled,boolean revoked,Instant expiresAt) { }
    private static Stored read(ResultSet r,int n)throws SQLException{
        JsonObject d=JsonParser.parseString(r.getString("details")).getAsJsonObject();
        DisplaySignal s=new DisplaySignal(r.getString("event_id"),r.getString("observer_session_id"),r.getLong("observation_sequence"),r.getTimestamp("observed_at").toInstant(),
            new EventOrigin(r.getString("observation_context"),r.getString("observation_connection_id")),d.get("count").getAsInt(),d.get("reason").getAsString());
        return new Stored(s,r.getString("attempt_id"),r.getLong("timeline_sequence"),r.getTimestamp("received_at").toInstant(),r.getString("delivery_status"),false);
    }
    public record Stored(DisplaySignal signal,String attemptId,long sequence,Instant receivedAt,String deliveryStatus,boolean duplicate){
        public JsonObject json(){JsonObject p=signal.json();p.addProperty("attemptId",attemptId);p.addProperty("timelineSequence",sequence);p.addProperty("receivedAt",receivedAt.toString());p.addProperty("deliveryStatus",deliveryStatus);SignalJson.size(p,4608);return p;}
        public JsonObject ack(){JsonObject p=new JsonObject();p.addProperty("status","ACCEPTED");p.addProperty("acknowledgedType","MONITORING_SIGNAL");p.addProperty("eventId",signal.eventId());p.addProperty("schemaVersion",1);p.addProperty("timelineSequence",sequence);p.addProperty("receivedAt",receivedAt.toString());p.addProperty("deliveryStatus",deliveryStatus);p.addProperty("duplicate",duplicate);return p;}
    }
}
