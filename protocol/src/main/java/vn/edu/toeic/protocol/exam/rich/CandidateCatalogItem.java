package vn.edu.toeic.protocol.exam.rich;

/** Minimal account identity needed by a proctor to assign an exam. */
public record CandidateCatalogItem(long userId, String username, String displayName) { }
