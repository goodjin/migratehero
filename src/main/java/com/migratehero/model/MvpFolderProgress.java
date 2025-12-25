package com.migratehero.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MVP 文件夹迁移进度 - 记录每个文件夹的迁移状态
 */
@Entity
@Table(name = "mvp_folder_progress", indexes = {
        @Index(name = "idx_task_id", columnList = "taskId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MvpFolderProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private String folderName;

    // 显示名称（可能与folderName不同）
    private String displayName;

    // 文件夹层级路径
    private String folderPath;

    @Builder.Default
    private Long totalEmails = 0L;

    @Builder.Default
    private Long migratedEmails = 0L;

    @Builder.Default
    private Long failedEmails = 0L;

    // 状态: pending, in_progress, completed, failed
    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public int getProgressPercent() {
        if (totalEmails == null || totalEmails == 0) {
            return 0;
        }
        return (int) ((migratedEmails + failedEmails) * 100 / totalEmails);
    }
}
