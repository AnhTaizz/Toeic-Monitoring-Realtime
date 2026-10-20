package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record CandidateQuestion(String questionId, Section section, int part, int orderIndex, String groupId, List<ContentBlock> blocks, List<OptionContent> options) {
    public CandidateQuestion { blocks = blocks == null ? null : List.copyOf(blocks); options = options == null ? null : List.copyOf(options); }
}
