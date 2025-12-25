package com.migratehero.repository;

import com.migratehero.model.MigrationJob;
import com.migratehero.model.SyncCheckpoint;
import com.migratehero.model.enums.DataType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 同步检查点数据访问层
 */
@Repository
public interface SyncCheckpointRepository extends JpaRepository<SyncCheckpoint, Long> {

    List<SyncCheckpoint> findByJob(MigrationJob job);

    Optional<SyncCheckpoint> findByJobAndDataType(MigrationJob job, DataType dataType);

    @Query("SELECT c FROM SyncCheckpoint c WHERE c.job = :job AND c.nextPageToken IS NOT NULL")
    List<SyncCheckpoint> findCheckpointsWithPendingPages(@Param("job") MigrationJob job);

    @Query("SELECT c FROM SyncCheckpoint c WHERE c.job = :job AND " +
           "(c.historyId IS NOT NULL OR c.deltaToken IS NOT NULL)")
    List<SyncCheckpoint> findCheckpointsWithIncrementalTokens(@Param("job") MigrationJob job);

    @Modifying
    @Query("DELETE FROM SyncCheckpoint c WHERE c.job = :job")
    void deleteByJob(@Param("job") MigrationJob job);

    @Modifying
    @Query("UPDATE SyncCheckpoint c SET c.nextPageToken = NULL, c.processedCount = 0 WHERE c.job = :job")
    void resetPaginationByJob(@Param("job") MigrationJob job);
}
