package vn.edu.toeic.server.monitoring.signal;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Stateless EXPERIMENTAL read model. Priorities guide human review, never adjudicate an exam. */
public final class AlertRules {
    public static final int MAX_GROUPS=100, MAX_EVIDENCE=5;
    public record Policy(int bucketSeconds,int windowSeconds,int reviewCount,int highCount,int sourceCap) {
        public Policy {
            if(bucketSeconds<10||bucketSeconds>120||windowSeconds<bucketSeconds||windowSeconds>600||windowSeconds%bucketSeconds!=0
                    ||reviewCount<2||reviewCount>10||highCount<=reviewCount||highCount>20||sourceCap<20||sourceCap>200)throw new IllegalArgumentException("Invalid experimental alert bounds");
        }
        public static Policy defaults(){return new Policy(60,300,3,6,200);}
        public String version(){return "mon08-experimental-v1-b"+bucketSeconds+"-w"+windowSeconds+"-r"+reviewCount+"-h"+highCount+"-c"+sourceCap;}
    }
    public enum Priority { INFO, REVIEW, HIGH_ATTENTION }
    public record Input(String attempt,String run,String key,String source,String type,Instant receivedAt,String rule,boolean repeatable,long dropped) {
        public Input {
            if(attempt==null||!attempt.matches("[A-Za-z0-9_.:-]{1,128}")||run==null||run.length()>128||key==null||key.length()>400
                    ||source==null||source.length()>64||type==null||type.length()>64||receivedAt==null||!Set.of("OBSERVATION","SOURCE_UNAVAILABLE","DATA_GAP","PRESENCE_INTERRUPTION").contains(rule)||dropped<0)throw new IllegalArgumentException();
        }
    }
    public record Summary(String id,String attempt,String run,String source,String type,String ruleId,String policyVersion,Priority priority,
            int count,java.math.BigInteger dropped,Instant windowStart,Instant windowEnd,List<String> evidence,boolean evidenceLimited,String explanation) {
        public Summary { evidence=List.copyOf(evidence); }
        JsonObject json(){var p=new JsonObject();p.addProperty("id",id);p.addProperty("attemptId",attempt);p.addProperty("runId",run);p.addProperty("source",source);p.addProperty("eventType",type);p.addProperty("ruleId",ruleId);p.addProperty("policyVersion",policyVersion);p.addProperty("priority",priority.name());p.addProperty("eventCount",count);p.addProperty("droppedCount",dropped.toString());p.addProperty("windowStart",windowStart.toString());p.addProperty("windowEnd",windowEnd.toString());p.addProperty("evidenceLimited",evidenceLimited);p.addProperty("explanation",explanation);var e=new JsonArray();evidence.forEach(e::add);p.add("evidence",e);return p;}
    }
    public record Result(List<Summary> summaries,boolean summaryLimited,int inputEvents,int gapEvents,int unavailableEvents) {
        public Result { summaries=List.copyOf(summaries); }
    }
    private record Group(String run,String source,String type,String rule,long bucket) { }
    public Result evaluate(String attempt,List<Input> input,Policy policy,Instant cutoff){
        Objects.requireNonNull(cutoff);if(input.size()>4*policy.sourceCap())throw new IllegalArgumentException("Input cap exceeded");
        var unique=new HashMap<String,Input>();var from=cutoff.minusSeconds(policy.windowSeconds());
        for(var e:input){if(!attempt.equals(e.attempt()))throw new IllegalArgumentException("Cross attempt input");var old=unique.putIfAbsent(e.key(),e);if(old!=null&&!old.equals(e))throw new IllegalArgumentException("Conflicting identity");}
        var groups=new HashMap<Group,List<Input>>();int used=0,gaps=0,unavailable=0;
        for(var e:unique.values()){
            if(e.receivedAt().isBefore(from)||!e.receivedAt().isBefore(cutoff))continue;
            used++;if(e.rule().equals("DATA_GAP"))gaps++;if(e.rule().equals("SOURCE_UNAVAILABLE")||e.rule().equals("PRESENCE_INTERRUPTION"))unavailable++;
            var key=new Group(e.run(),e.source(),e.type(),e.rule(),Math.floorDiv(e.receivedAt().getEpochSecond(),policy.bucketSeconds())*policy.bucketSeconds());groups.computeIfAbsent(key,k->new ArrayList<>()).add(e);
        }
        var result=new ArrayList<Summary>();
        for(var entry:groups.entrySet()){
            var g=entry.getKey();var events=entry.getValue();events.sort(Comparator.comparing(Input::receivedAt).thenComparing(Input::key));
            boolean repeatable=events.stream().allMatch(Input::repeatable);Priority priority=g.rule().equals("OBSERVATION")?Priority.INFO:Priority.REVIEW;
            if(g.rule().equals("OBSERVATION")&&repeatable){if(events.size()>=policy.highCount())priority=Priority.HIGH_ATTENTION;else if(events.size()>=policy.reviewCount())priority=Priority.REVIEW;}
            String explanation=switch(g.rule()){
                case "DATA_GAP" -> "Client báo đã bỏ event; không biết nội dung bị mất. Count là số bản ghi gap, droppedCount là tổng số event client báo bỏ.";
                case "SOURCE_UNAVAILABLE" -> "Nguồn hoặc metadata chưa đủ/không khả dụng; đây không phải bằng chứng hành vi của thí sinh.";
                case "PRESENCE_INTERRUPTION" -> "Server ghi nhận gián đoạn liên lạc; khác dashboard stale và trạng thái observer.";
                default -> "Quan sát được ghi nhận; count là số event riêng biệt cùng loại/nguồn/run trong cửa sổ, không phải số hành động hoặc số lần vi phạm. Có thể có nguyên nhân vô hại.";
            };
            if(g.source().equals("WINDOWS_USB_STORAGE")&&g.rule().equals("OBSERVATION"))explanation+=" USB dùng định danh slot yếu; không suy số thiết bị duy nhất hay hành động vật lý.";
            if(repeatable&&priority!=Priority.INFO)explanation+=" Ngưỡng lặp EXPERIMENTAL chỉ tăng ưu tiên giám thị xem xét; chưa là policy APPROVED.";
            String identity=String.join("|",attempt,g.run(),g.source(),g.type(),g.rule(),Long.toString(g.bucket()),policy.version());
            Instant start=Instant.ofEpochSecond(g.bucket()),end=start.plusSeconds(policy.bucketSeconds());if(start.isBefore(from))start=from;if(end.isAfter(cutoff))end=cutoff;
            result.add(new Summary(id(identity),attempt,g.run(),g.source(),g.type(),g.rule(),policy.version(),priority,events.size(),events.stream().map(e->java.math.BigInteger.valueOf(e.dropped())).reduce(java.math.BigInteger.ZERO,java.math.BigInteger::add),start,end,events.stream().limit(MAX_EVIDENCE).map(Input::key).toList(),events.size()>MAX_EVIDENCE,explanation));
        }
        result.sort(Comparator.comparing(Summary::priority).reversed().thenComparing(Summary::windowStart,Comparator.reverseOrder()).thenComparing(Summary::id));
        return new Result(result.stream().limit(MAX_GROUPS).toList(),result.size()>MAX_GROUPS,used,gaps,unavailable);
    }
    private static String id(String value){try{return "mon08-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
