package vn.edu.toeic.server.monitoring;

import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.MonitoringStateReducer;
import vn.edu.toeic.protocol.monitoring.DeltaSnapshotPayload;
import java.util.List;

/** Server exception boundary; the shared full/delta reducer is also exercised by replay. Store owns locking. */
public final class FullSnapshotReducer {
    private final MonitoringStateReducer reducer = new MonitoringStateReducer();
    public long sequence() { return reducer.sequence(); }
    public boolean needsFull(){return reducer.needsFull();}
    public List<FullSnapshotPayload.Process> processes(){return reducer.processes();}
    public MonitoringStateReducer.Outcome delta(String id,DeltaSnapshotPayload delta) {
        try{return reducer.delta(id,delta);}catch(MonitoringStateReducer.Rejected rejected){throw new StateRejectedException(rejected.code());}
    }
    public boolean accept(String messageId, FullSnapshotPayload full) {
        try { return reducer.full(messageId,full)==MonitoringStateReducer.Outcome.APPLIED; }
        catch (MonitoringStateReducer.Rejected rejected) { throw new StateRejectedException(rejected.code()); }
    }
}
