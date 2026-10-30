package vn.edu.toeic.protocol.admin;

public record ExamSummary(String examId, String title, String description, int totalQuestions,
                          int contentVersion, boolean inUse, String authoringStatus,
                          Integer completedQuestions, boolean manual) {
    public ExamSummary(String examId,String title,String description,int totalQuestions,int contentVersion,boolean inUse) {
        this(examId,title,description,totalQuestions,contentVersion,inUse,"READY",null,false);
    }
}
