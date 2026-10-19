package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record SourceImport(String sourceRef, String sourceName, String sourcePath,
        String sourceUrl, String copyrightNotice, PermissionStatus permissionStatus,
        LocalDate permissionConfirmedOn, String permissionNote, String licenseDocument,
        String sourceSha256, String manifestSha256, Verification answerKeyStatus,
        Verification groupMappingStatus, Verification optionContentStatus,
        String mediaValidationStatus) {}
