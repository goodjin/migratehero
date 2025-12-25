package com.migratehero.dto.response;

import com.migratehero.model.MigrationJob;
import com.migratehero.model.enums.MigrationPhase;
import com.migratehero.model.enums.MigrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 迁移任务响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationJobResponse {

    private Long id;

    private String name;

    private String description;

    private EmailAccountResponse sourceAccount;

    private EmailAccountResponse targetAccount;

    private MigrationPhase phase;

    private MigrationStatus status;

    private String dataTypesConfig;

    // 邮件统计
    private Long totalEmails;
    private Long migratedEmails;
    private Long failedEmails;

    // 联系人统计
    private Long totalContacts;
    private Long migratedContacts;
    private Long failedContacts;

    // 日历事件统计
    private Long totalEvents;
    private Long migratedEvents;
    private Long failedEvents;

    private Integer overallProgressPercent;

    private LocalDateTime scheduledAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static MigrationJobResponse fromEntity(MigrationJob job) {
        return MigrationJobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .description(job.getDescription())
                .sourceAccount(EmailAccountResponse.fromEntity(job.getSourceAccount()))
                .targetAccount(EmailAccountResponse.fromEntity(job.getTargetAccount()))
                .phase(job.getPhase())
                .status(job.getStatus())
                .dataTypesConfig(job.getDataTypesConfig())
                .totalEmails(job.getTotalEmails())
                .migratedEmails(job.getMigratedEmails())
                .failedEmails(job.getFailedEmails())
                .totalContacts(job.getTotalContacts())
                .migratedContacts(job.getMigratedContacts())
                .failedContacts(job.getFailedContacts())
                .totalEvents(job.getTotalEvents())
                .migratedEvents(job.getMigratedEvents())
                .failedEvents(job.getFailedEvents())
                .overallProgressPercent(job.getOverallProgressPercent())
                .scheduledAt(job.getScheduledAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .lastError(job.getLastError())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
