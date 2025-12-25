package com.migratehero.dto.response;

import com.migratehero.model.MvpMigratedEmail;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 已迁移邮件响应
 */
@Data
@Builder
public class MigratedEmailResponse {

    private Long id;
    private String sourceEmailId;
    private String folderName;
    private String subject;
    private String fromAddress;
    private Instant sentDate;
    private Long sizeBytes;
    private Boolean success;
    private String errorMessage;
    private Instant migratedAt;

    public static MigratedEmailResponse from(MvpMigratedEmail email) {
        return MigratedEmailResponse.builder()
                .id(email.getId())
                .sourceEmailId(email.getSourceEmailId())
                .folderName(email.getFolderName())
                .subject(email.getSubject())
                .fromAddress(email.getFromAddress())
                .sentDate(email.getSentDate())
                .sizeBytes(email.getSizeBytes())
                .success(email.getSuccess())
                .errorMessage(email.getErrorMessage())
                .migratedAt(email.getMigratedAt())
                .build();
    }
}
