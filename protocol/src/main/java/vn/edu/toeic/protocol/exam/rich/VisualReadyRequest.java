package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record VisualReadyRequest(String requestId, String attemptId, long writerEpoch, String preparationEpoch, String manifestId, VisualReadyAction action, String firstSlotId, List<VisualAssetVerification> verifiedAssets) {
    public VisualReadyRequest { verifiedAssets = verifiedAssets == null ? null : List.copyOf(verifiedAssets); }
}
