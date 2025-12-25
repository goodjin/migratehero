package com.migratehero.repository;

import com.migratehero.model.MigrationJob;
import com.migratehero.model.MigrationLog;
import com.migratehero.model.enums.DataType;
import com.migratehero.model.enums.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 迁移日志数据访问层
 */
@Repository
public interface MigrationLogRepository extends JpaRepository<MigrationLog, Long> {

    List<MigrationLog> findByJobOrderByCreatedAtDesc(MigrationJob job);

    Page<MigrationLog> findByJob(MigrationJob job, Pageable pageable);

    Page<MigrationLog> findByJobAndLevel(MigrationJob job, LogLevel level, Pageable pageable);

    Page<MigrationLog> findByJobAndDataType(MigrationJob job, DataType dataType, Pageable pageable);

    @Query("SELECT l FROM MigrationLog l WHERE l.job = :job AND l.level = 'ERROR' ORDER BY l.createdAt DESC")
    List<MigrationLog> findErrorLogsByJob(@Param("job") MigrationJob job);

    @Query("SELECT l FROM MigrationLog l WHERE l.createdAt < :date")
    List<MigrationLog> findLogsOlderThan(@Param("date") LocalDateTime date);

    @Query("SELECT COUNT(l) FROM MigrationLog l WHERE l.job = :job AND l.level = 'ERROR'")
    long countErrorsByJob(@Param("job") MigrationJob job);

    @Modifying
    @Query("DELETE FROM MigrationLog l WHERE l.job = :job")
    void deleteByJob(@Param("job") MigrationJob job);

    @Modifying
    @Query("DELETE FROM MigrationLog l WHERE l.createdAt < :date")
    int deleteLogsOlderThan(@Param("date") LocalDateTime date);
}
