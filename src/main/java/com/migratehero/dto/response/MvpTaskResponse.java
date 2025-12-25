package com.migratehero.dto.response;

import com.migratehero.model.MvpMigrationTask;
import com.migratehero.model.enums.MigrationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * MVP 迁移任务响应
 */
@Data
@Builder
public class MvpTaskResponse {

    private Long id;
    private String sourceEmail;
    private String targetEmail;
    private String status;
    private Integer progressPercent;
    private Long totalFolders;
    private Long migratedFolders;
    private Long totalEmails;
    private Long migratedEmails;
    private Long failedEmails;

    // === 日历统计 ===
    private Long totalCalendarEvents;
    private Long migratedCalendarEvents;
    private Long failedCalendarEvents;

    // === 联系人统计 ===
    private Long totalContacts;
    private Long migratedContacts;
    private Long failedContacts;

    // === 迁移类型 ===
    private Boolean migrateEmails;
    private Boolean migrateCalendar;
    private Boolean migrateContacts;

    private String currentFolder;
    private String errorMessage;
    private String failedEndpoint;
    private String failedRequest;
    private String failedResponse;
    private String sourceEwsUrl;
    private Integer targetImapPort;
    private Boolean targetImapSsl;
    private String targetImapHost;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    public static MvpTaskResponse from(MvpMigrationTask task) {
        return MvpTaskResponse.builder()
                .id(task.getId())
                .sourceEmail(task.getSourceEmail())
                .targetEmail(task.getTargetEmail())
                .status(task.getStatus().name())
                .progressPercent(task.getProgressPercent())
                .totalFolders(task.getTotalFolders())
                .migratedFolders(task.getMigratedFolders())
                .totalEmails(task.getTotalEmails())
                .migratedEmails(task.getMigratedEmails())
                .failedEmails(task.getFailedEmails())
                // 日历统计
                .totalCalendarEvents(task.getTotalCalendarEvents())
                .migratedCalendarEvents(task.getMigratedCalendarEvents())
                .failedCalendarEvents(task.getFailedCalendarEvents())
                // 联系人统计
                .totalContacts(task.getTotalContacts())
                .migratedContacts(task.getMigratedContacts())
                .failedContacts(task.getFailedContacts())
                // 迁移类型
                .migrateEmails(task.getMigrateEmails())
                .migrateCalendar(task.getMigrateCalendar())
                .migrateContacts(task.getMigrateContacts())
                .currentFolder(task.getCurrentFolder())
                .errorMessage(task.getErrorMessage())
                .failedEndpoint(task.getFailedEndpoint())
                .failedRequest(task.getFailedRequest())
                .failedResponse(task.getFailedResponse())
                .sourceEwsUrl(task.getSourceEwsUrl())
                .targetImapPort(task.getTargetImapPort())
                .targetImapSsl(task.getTargetImapSsl())
                .targetImapHost(task.getTargetImapHost())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }
}
