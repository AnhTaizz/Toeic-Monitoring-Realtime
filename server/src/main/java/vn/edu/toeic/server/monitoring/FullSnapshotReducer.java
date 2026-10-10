package vn.edu.toeic.server.monitoring;

import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.FullStateReducer;

/** Server exception boundary; the pure C1 reducer is also exercised by replay. Store owns locking. */
public final class FullSnapshotReducer {
    private final FullStateReducer reducer = new FullStateReducer();
    public long sequence() { return reducer.sequence(); }
    public boolean accept(String messageId, FullSnapshotPayload full) {
        try { return reducer.accept(messageId,full); }
        catch (FullStateReducer.Rejected rejected) { throw new StateRejectedException(rejected.code()); }
    }
}
