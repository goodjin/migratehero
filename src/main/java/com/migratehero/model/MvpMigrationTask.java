package com.migratehero.model;

import com.migratehero.model.enums.MigrationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MVP 迁移任务 - 简化版本，用于演示 EWS -> IMAP 邮箱迁移
 */
@Entity
@Table(name = "mvp_migration_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MvpMigrationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // === 源端配置 (EWS) ===
    @Column(nullable = false)
    private String sourceEwsUrl;

    @Column(nullable = false)
    private String sourceEmail;

    @Column(nullable = false)
    private String sourcePassword;

    // === 目标端配置 (IMAP) ===
    @Column(nullable = false)
    private String targetImapHost;

    @Column(nullable = false)
    private Integer targetImapPort;

    @Column(nullable = false)
    private Boolean targetImapSsl;

    @Column(nullable = false)
    private String targetEmail;

    @Column(nullable = false)
    private String targetPassword;

    // === 目标端 CalDAV/CardDAV 配置（可选） ===
    private String targetCalDavUrl;

    private String targetCardDavUrl;

    // === 迁移类型控制 ===
    @Builder.Default
    private Boolean migrateEmails = true;

    @Builder.Default
    private Boolean migrateCalendar = false;

    @Builder.Default
    private Boolean migrateContacts = false;

    // === 迁移状态 ===
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MigrationStatus status = MigrationStatus.DRAFT;

    // === 进度统计 ===
    @Builder.Default
    private Long totalFolders = 0L;

    @Builder.Default
    private Long migratedFolders = 0L;

    @Builder.Default
    private Long totalEmails = 0L;

    @Builder.Default
    private Long migratedEmails = 0L;

    @Builder.Default
    private Long failedEmails = 0L;

    // === 日历统计 ===
    @Builder.Default
    private Long totalCalendarEvents = 0L;

    @Builder.Default
    private Long migratedCalendarEvents = 0L;

    @Builder.Default
    private Long failedCalendarEvents = 0L;

    // === 联系人统计 ===
    @Builder.Default
    private Long totalContacts = 0L;

    @Builder.Default
    private Long migratedContacts = 0L;

    @Builder.Default
    private Long failedContacts = 0L;

    @Builder.Default
    private Integer progressPercent = 0;

    // 当前正在迁移的文件夹
    private String currentFolder;

    // 错误信息
    @Column(length = 2000)
    private String errorMessage;

    // 失败的请求地址或IP端口
    @Column(length = 1000)
    private String failedEndpoint;

    // 失败的请求参数（JSON格式）
    @Column(length = 4000)
    private String failedRequest;

    // 失败的返回结果
    @Lob
    private String failedResponse;

    // === 时间戳 ===
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
