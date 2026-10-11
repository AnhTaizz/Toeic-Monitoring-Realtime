package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.JsonObject;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** expectedSequence/epoch/source come from observation/lifecycle position, not encoded payload. */
public record ReplayFrame(long ordinal,TraceData.Sample source,int scan,int generation,long expectedSequence,
        MessageEnvelope<JsonObject> message) {
    public String expectedEpoch() {return "SIMULATED-epoch-"+generation;}
    public String connection() {return "SIMULATED-connection-"+generation;}
    public String label() {return message.type()+"@record"+source.index()+"/message"+ordinal;}
}
