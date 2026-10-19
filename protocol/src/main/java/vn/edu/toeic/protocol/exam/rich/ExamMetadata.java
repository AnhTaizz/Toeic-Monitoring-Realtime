package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record ExamMetadata(String examId, String title, String description, String profile) {}
