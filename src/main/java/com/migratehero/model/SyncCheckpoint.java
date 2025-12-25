package com.migratehero.model;

import com.migratehero.model.enums.DataType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 同步检查点实体 - 用于支持断点续传和增量同步
 */
@Entity
@Table(name = "sync_checkpoints", indexes = {
    @Index(name = "idx_checkpoint_job", columnList = "job_id"),
    @Index(name = "idx_checkpoint_data_type", columnList = "data_type")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_job_data_type", columnNames = {"job_id", "data_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private MigrationJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType;

    /**
     * 最后同步时间 - 用于增量同步的时间点
     */
    @Column(name = "last_sync_time")
    private Instant lastSyncTime;

    /**
     * 分页 Token - 用于分页读取大量数据
     * Gmail: pageToken
     * MS Graph: @odata.nextLink
     */
    @Column(name = "next_page_token", columnDefinition = "TEXT")
    private String nextPageToken;

    /**
     * Gmail History ID - 用于 Gmail 增量同步
     * 参考: https://developers.google.com/gmail/api/guides/sync
     */
    @Column(name = "history_id")
    private String historyId;

    /**
     * MS Graph Delta Token - 用于 Microsoft Graph 增量同步
     * 参考: https://learn.microsoft.com/en-us/graph/delta-query-overview
     */
    @Column(name = "delta_token", columnDefinition = "TEXT")
    private String deltaToken;

    /**
     * 初始同步是否完成
     */
    @Column(name = "initial_sync_complete")
    @Builder.Default
    private boolean initialSyncComplete = false;

    /**
     * 当前批次中已处理的项目数
     */
    @Column(name = "processed_count")
    @Builder.Default
    private Long processedCount = 0L;

    /**
     * 当前批次的总项目数
     */
    @Column(name = "batch_total_count")
    private Long batchTotalCount;

    /**
     * 最后处理的项目 ID - 用于精确断点恢复
     */
    @Column(name = "last_item_id")
    private String lastItemId;

    /**
     * 检查点状态信息（JSON 格式，存储额外元数据）
     */
    @Column(name = "state_data", columnDefinition = "TEXT")
    private String stateData;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否有更多数据需要拉取
     */
    public boolean hasMoreData() {
        return nextPageToken != null && !nextPageToken.isEmpty();
    }

    /**
     * 检查是否有可用的增量同步 Token
     */
    public boolean hasIncrementalToken() {
        return (historyId != null && !historyId.isEmpty())
            || (deltaToken != null && !deltaToken.isEmpty());
    }

    /**
     * 重置检查点（用于重新开始同步）
     */
    public void reset() {
        lastSyncTime = null;
        nextPageToken = null;
        historyId = null;
        deltaToken = null;
        processedCount = 0L;
        batchTotalCount = null;
        lastItemId = null;
        stateData = null;
    }
}
