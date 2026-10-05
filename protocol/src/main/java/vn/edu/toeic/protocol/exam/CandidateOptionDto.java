package vn.edu.toeic.protocol.exam;

public record CandidateOptionDto(
        String optionId,
        String optionText,
        int orderIndex
) {
    public CandidateOptionDto {
        if (optionId == null || optionId.isBlank()) {
            throw new IllegalArgumentException("optionId cannot be blank");
        }
        if (optionText == null || optionText.isBlank()) {
            throw new IllegalArgumentException("optionText cannot be blank");
        }
    }
}
