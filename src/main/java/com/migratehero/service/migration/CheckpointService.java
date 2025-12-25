package com.migratehero.service.migration;

import com.migratehero.model.MigrationJob;
import com.migratehero.model.SyncCheckpoint;
import com.migratehero.model.enums.DataType;
import com.migratehero.repository.SyncCheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 检查点服务 - 管理迁移进度检查点，支持断点续传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointService {

    private final SyncCheckpointRepository checkpointRepository;

    /**
     * 获取或创建检查点
     */
    @Transactional
    public SyncCheckpoint getOrCreateCheckpoint(MigrationJob job, DataType dataType) {
        Optional<SyncCheckpoint> existing = checkpointRepository.findByJobAndDataType(job, dataType);

        if (existing.isPresent()) {
            return existing.get();
        }

        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(dataType)
                .build();

        return checkpointRepository.save(checkpoint);
    }

    /**
     * 更新分页令牌
     */
    @Transactional
    public void updatePageToken(MigrationJob job, DataType dataType, String pageToken) {
        SyncCheckpoint checkpoint = getOrCreateCheckpoint(job, dataType);
        checkpoint.setNextPageToken(pageToken);
        checkpoint.setLastSyncTime(Instant.now());
        checkpointRepository.save(checkpoint);
        log.debug("Updated page token for job {} data type {}", job.getId(), dataType);
    }

    /**
     * 更新增量同步令牌 (Gmail History ID)
     */
    @Transactional
    public void updateHistoryId(MigrationJob job, DataType dataType, String historyId) {
        SyncCheckpoint checkpoint = getOrCreateCheckpoint(job, dataType);
        checkpoint.setHistoryId(historyId);
        checkpoint.setLastSyncTime(Instant.now());
        checkpointRepository.save(checkpoint);
        log.debug("Updated history ID for job {} data type {}", job.getId(), dataType);
    }

    /**
     * 更新增量同步令牌 (MS Graph Delta Token)
     */
    @Transactional
    public void updateDeltaToken(MigrationJob job, DataType dataType, String deltaToken) {
        SyncCheckpoint checkpoint = getOrCreateCheckpoint(job, dataType);
        checkpoint.setDeltaToken(deltaToken);
        checkpoint.setLastSyncTime(Instant.now());
        checkpointRepository.save(checkpoint);
        log.debug("Updated delta token for job {} data type {}", job.getId(), dataType);
    }

    /**
     * 更新同步令牌 (通用方法)
     */
    @Transactional
    public void updateSyncToken(MigrationJob job, DataType dataType, String syncToken) {
        SyncCheckpoint checkpoint = getOrCreateCheckpoint(job, dataType);
        // 根据源账户类型决定更新哪个字段
        if (job.getSourceAccount().getProvider() == com.migratehero.model.enums.ProviderType.GOOGLE) {
            checkpoint.setHistoryId(syncToken);
        } else {
            checkpoint.setDeltaToken(syncToken);
        }
        checkpoint.setLastSyncTime(Instant.now());
        checkpointRepository.save(checkpoint);
    }

    /**
     * 获取分页令牌
     */
    @Transactional(readOnly = true)
    public String getPageToken(MigrationJob job, DataType dataType) {
        return checkpointRepository.findByJobAndDataType(job, dataType)
                .map(SyncCheckpoint::getNextPageToken)
                .orElse(null);
    }

    /**
     * 获取同步令牌
     */
    @Transactional(readOnly = true)
    public String getSyncToken(MigrationJob job, DataType dataType) {
        return checkpointRepository.findByJobAndDataType(job, dataType)
                .map(cp -> {
                    if (job.getSourceAccount().getProvider() == com.migratehero.model.enums.ProviderType.GOOGLE) {
                        return cp.getHistoryId();
                    } else {
                        return cp.getDeltaToken();
                    }
                })
                .orElse(null);
    }

    /**
     * 清除检查点 (用于重新开始迁移)
     */
    @Transactional
    public void clearCheckpoints(MigrationJob job) {
        checkpointRepository.deleteByJob(job);
        log.info("Cleared all checkpoints for job {}", job.getId());
    }

    /**
     * 清除特定数据类型的检查点
     */
    @Transactional
    public void clearCheckpoint(MigrationJob job, DataType dataType) {
        checkpointRepository.findByJobAndDataType(job, dataType)
                .ifPresent(checkpointRepository::delete);
        log.info("Cleared checkpoint for job {} data type {}", job.getId(), dataType);
    }

    /**
     * 标记初始同步完成
     */
    @Transactional
    public void markInitialSyncComplete(MigrationJob job, DataType dataType, String syncToken) {
        SyncCheckpoint checkpoint = getOrCreateCheckpoint(job, dataType);
        checkpoint.setInitialSyncComplete(true);
        updateSyncToken(job, dataType, syncToken);
        checkpointRepository.save(checkpoint);
        log.info("Marked initial sync complete for job {} data type {}", job.getId(), dataType);
    }

    /**
     * 检查初始同步是否完成
     */
    @Transactional(readOnly = true)
    public boolean isInitialSyncComplete(MigrationJob job, DataType dataType) {
        return checkpointRepository.findByJobAndDataType(job, dataType)
                .map(SyncCheckpoint::isInitialSyncComplete)
                .orElse(false);
    }
}
