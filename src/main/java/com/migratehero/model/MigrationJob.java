package com.migratehero.model;

import com.migratehero.model.enums.MigrationPhase;
import com.migratehero.model.enums.MigrationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 迁移任务实体 - 管理邮件/联系人/日历的迁移作业
 */
@Entity
@Table(name = "migration_jobs", indexes = {
    @Index(name = "idx_migration_job_user", columnList = "user_id"),
    @Index(name = "idx_migration_job_status", columnList = "status"),
    @Index(name = "idx_migration_job_phase", columnList = "phase")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private EmailAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id", nullable = false)
    private EmailAccount targetAccount;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MigrationPhase phase = MigrationPhase.INITIAL_SYNC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MigrationStatus status = MigrationStatus.DRAFT;

    /**
     * 数据类型配置 JSON: {"emails":true,"contacts":true,"calendars":true}
     */
    @Column(name = "data_types_config", columnDefinition = "TEXT")
    @Builder.Default
    private String dataTypesConfig = "{\"emails\":true,\"contacts\":true,\"calendars\":true}";

    // 邮件统计
    @Column(name = "total_emails")
    @Builder.Default
    private Long totalEmails = 0L;

    @Column(name = "migrated_emails")
    @Builder.Default
    private Long migratedEmails = 0L;

    @Column(name = "failed_emails")
    @Builder.Default
    private Long failedEmails = 0L;

    // 联系人统计
    @Column(name = "total_contacts")
    @Builder.Default
    private Long totalContacts = 0L;

    @Column(name = "migrated_contacts")
    @Builder.Default
    private Long migratedContacts = 0L;

    @Column(name = "failed_contacts")
    @Builder.Default
    private Long failedContacts = 0L;

    // 日历事件统计
    @Column(name = "total_events")
    @Builder.Default
    private Long totalEvents = 0L;

    @Column(name = "migrated_events")
    @Builder.Default
    private Long migratedEvents = 0L;

    @Column(name = "failed_events")
    @Builder.Default
    private Long failedEvents = 0L;

    // 总体进度
    @Column(name = "overall_progress_percent")
    @Builder.Default
    private Integer overallProgressPercent = 0;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SyncCheckpoint> checkpoints = new ArrayList<>();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MigrationLog> logs = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (phase == null) {
            phase = MigrationPhase.INITIAL_SYNC;
        }
        if (status == null) {
            status = MigrationStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 计算总体进度百分比
     */
    public void calculateOverallProgress() {
        long totalItems = totalEmails + totalContacts + totalEvents;
        if (totalItems == 0) {
            overallProgressPercent = 0;
            return;
        }
        long migratedItems = migratedEmails + migratedContacts + migratedEvents;
        overallProgressPercent = (int) ((migratedItems * 100) / totalItems);
    }

    /**
     * 检查任务是否可以启动
     */
    public boolean canStart() {
        return status == MigrationStatus.DRAFT || status == MigrationStatus.SCHEDULED;
    }

    /**
     * 检查任务是否可以暂停
     */
    public boolean canPause() {
        return status == MigrationStatus.RUNNING;
    }

    /**
     * 检查任务是否可以恢复
     */
    public boolean canResume() {
        return status == MigrationStatus.PAUSED;
    }

    /**
     * 检查任务是否可以取消
     */
    public boolean canCancel() {
        return status == MigrationStatus.DRAFT
            || status == MigrationStatus.SCHEDULED
            || status == MigrationStatus.RUNNING
            || status == MigrationStatus.PAUSED;
    }
}
