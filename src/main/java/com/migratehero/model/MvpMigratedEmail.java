package com.migratehero.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MVP 已迁移邮件记录 - 保存每封邮件的迁移状态
 */
@Entity
@Table(name = "mvp_migrated_email", indexes = {
        @Index(name = "idx_task_folder", columnList = "taskId, folderName"),
        @Index(name = "idx_source_id", columnList = "sourceEmailId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MvpMigratedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long taskId;

    // 源邮件信息
    @Column
    private String sourceEmailId;

    @Column
    private String folderName;

    @Column(length = 500)
    private String subject;

    private String fromAddress;

    private Instant sentDate;

    private Long sizeBytes;

    // 迁移状态
    @Column
    @Builder.Default
    private Boolean success = false;

    @Column(length = 1000)
    private String errorMessage;

    // 目标邮件ID（迁移成功后的ID）
    private String targetEmailId;

    @Column
    private Instant migratedAt;

    @PrePersist
    protected void onCreate() {
        if (migratedAt == null) {
            migratedAt = Instant.now();
        }
    }
}
