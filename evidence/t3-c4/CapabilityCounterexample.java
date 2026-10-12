import java.lang.reflect.*;
import java.time.Duration;
import java.util.List;
import com.google.gson.*;
import vn.edu.toeic.client.realtime.RealtimeClient;
public class CapabilityCounterexample {
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{var m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
 public static void main(String[] args)throws Exception {
  System.setProperty("toeic.realtime.delta-experimental","true");
  var type=Class.forName("vn.edu.toeic.client.realtime.RealtimeClientTest$Fixture");var c=type.getDeclaredConstructor();c.setAccessible(true);Object fixture=c.newInstance();
  try {
   call(fixture,"connect",new Class<?>[0]);call(field(fixture,"clock"),"advance",new Class<?>[]{Duration.class},Duration.ofSeconds(2));Object socket=call(fixture,"socket",new Class<?>[0]);
   var sent=(List<?>)field(socket,"sent");var hb=JsonParser.parseString((String)sent.getFirst()).getAsJsonObject();
   var response=new JsonObject();response.addProperty("protocolVersion","v0");response.addProperty("type","ACK");response.addProperty("messageId","MOCK-invalid");response.add("requestId",hb.get("requestId"));response.add("attemptId",hb.get("attemptId"));response.add("traceId",hb.get("traceId"));
   var body=new JsonObject();body.addProperty("status","ACCEPTED");body.addProperty("acknowledgedType","HEARTBEAT");body.addProperty("monitoringDelta",true);body.addProperty("connectionId",42);response.add("payload",body);
   call(socket,"text",new Class<?>[]{String.class,boolean.class},response.toString(),true);
   boolean actual=((RealtimeClient)field(fixture,"client")).supportsMonitoringDelta();System.out.println("CAPABILITY_COUNTEREXAMPLE malformed heartbeat ACK/connectionId numeric: expected=false actual="+actual+"; input MOCK, no network");
   if(actual)throw new AssertionError("INVALID_ACK_ENABLED_DELTA");
  }finally{((AutoCloseable)fixture).close();}
 }
}
