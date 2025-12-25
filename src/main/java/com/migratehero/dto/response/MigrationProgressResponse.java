package com.migratehero.dto.response;

import com.migratehero.model.enums.MigrationPhase;
import com.migratehero.model.enums.MigrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 迁移进度实时响应 - 用于 WebSocket 推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationProgressResponse {

    private Long jobId;

    private MigrationPhase phase;

    private MigrationStatus status;

    private Integer overallProgressPercent;

    // 邮件进度
    private Long totalEmails;
    private Long migratedEmails;
    private Long failedEmails;
    private Integer emailProgressPercent;

    // 联系人进度
    private Long totalContacts;
    private Long migratedContacts;
    private Long failedContacts;
    private Integer contactProgressPercent;

    // 日历进度
    private Long totalEvents;
    private Long migratedEvents;
    private Long failedEvents;
    private Integer eventProgressPercent;

    // 速率与预计时间
    private Double itemsPerSecond;
    private Long estimatedSecondsRemaining;

    private String currentOperation;

    private Long timestamp;
}
