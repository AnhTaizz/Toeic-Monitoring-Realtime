package vn.edu.toeic.protocol.exam.rich;

/** Proctor provisioning only. Role, enabled status and credentials are server controlled. */
public record CreateCandidateRequest(String username, String displayName) { }
