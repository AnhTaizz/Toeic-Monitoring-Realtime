package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record VisualManifest(int schemaVersion, String manifestId, String attemptId,
        String sessionId, String examId, int contentVersion, long writerEpoch,
        String preparationEpoch, List<VisualImageSlot> slots) {
    public VisualManifest { slots = List.copyOf(slots); }
}
