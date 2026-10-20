package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record CreateRichSessionRequest(String examId, int durationMinutes, List<Long> candidateUserIds, List<Long> proctorUserIds) {
    public CreateRichSessionRequest { candidateUserIds = candidateUserIds == null ? null : List.copyOf(candidateUserIds); proctorUserIds = proctorUserIds == null ? null : List.copyOf(proctorUserIds); }
}
