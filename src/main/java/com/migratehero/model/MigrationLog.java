package com.migratehero.model;

import com.migratehero.model.enums.DataType;
import com.migratehero.model.enums.LogLevel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 迁移日志实体 - 记录迁移过程中的详细日志
 */
@Entity
@Table(name = "migration_logs", indexes = {
    @Index(name = "idx_migration_log_job", columnList = "job_id"),
    @Index(name = "idx_migration_log_level", columnList = "level"),
    @Index(name = "idx_migration_log_created", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private MigrationJob job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type")
    private DataType dataType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    /**
     * 相关项目的 ID（如邮件 ID、联系人 ID 等）
     */
    @Column(name = "item_id")
    private String itemId;

    /**
     * 处理的项目数量
     */
    @Column(name = "items_count")
    private Long itemsCount;

    /**
     * 执行耗时（毫秒）
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * 错误详情
     */
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    /**
     * 堆栈跟踪（仅错误日志）
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
