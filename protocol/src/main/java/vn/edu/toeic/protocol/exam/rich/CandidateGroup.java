package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record CandidateGroup(String groupId, Section section, int part, String title, int orderIndex, List<DocumentContent> documents, List<ContentBlock> blocks) {
    public CandidateGroup { documents = documents == null ? null : List.copyOf(documents); blocks = blocks == null ? null : List.copyOf(blocks); }
}
