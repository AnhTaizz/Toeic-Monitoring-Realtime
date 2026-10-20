package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;

public record AttemptPreparation(int schemaVersion, String attemptId, String sessionId, String examId, String title, String contractVersion, String sessionState, String attemptState, int durationMinutes, Instant startedAt, Instant deadlineAt, boolean listeningRequired, String audioManifestPath, VisualManifest visualManifest) {}
