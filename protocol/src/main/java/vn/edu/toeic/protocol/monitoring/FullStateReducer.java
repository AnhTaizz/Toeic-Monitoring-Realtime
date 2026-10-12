package vn.edu.toeic.protocol.monitoring;

import vn.edu.toeic.protocol.ErrorCode;

/** Compatibility facade for C1 callers; full/delta now share one production core. */
public final class FullStateReducer {
    private final MonitoringStateReducer reducer=new MonitoringStateReducer();
    public long sequence(){return reducer.sequence();}
    public boolean accept(String messageId,FullSnapshotPayload full) {
        try{return reducer.full(messageId,full)==MonitoringStateReducer.Outcome.APPLIED;}
        catch(MonitoringStateReducer.Rejected error){throw new Rejected(error.code());}
    }
    public static final class Rejected extends RuntimeException {
        private final ErrorCode code;
        public Rejected(ErrorCode code){super(code.name());this.code=code;}
        public ErrorCode code(){return code;}
    }
}
