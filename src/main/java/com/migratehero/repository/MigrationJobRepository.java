package com.migratehero.repository;

import com.migratehero.model.MigrationJob;
import com.migratehero.model.User;
import com.migratehero.model.enums.MigrationPhase;
import com.migratehero.model.enums.MigrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 迁移任务数据访问层
 */
@Repository
public interface MigrationJobRepository extends JpaRepository<MigrationJob, Long> {

    List<MigrationJob> findByUser(User user);

    Page<MigrationJob> findByUser(User user, Pageable pageable);

    Page<MigrationJob> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Optional<MigrationJob> findByIdAndUser(Long id, User user);

    List<MigrationJob> findByUserAndStatus(User user, MigrationStatus status);

    List<MigrationJob> findByStatus(MigrationStatus status);

    List<MigrationJob> findByPhase(MigrationPhase phase);

    @Query("SELECT j FROM MigrationJob j WHERE j.status = 'RUNNING'")
    List<MigrationJob> findRunningJobs();

    @Query("SELECT j FROM MigrationJob j WHERE j.status = 'SCHEDULED' AND j.scheduledAt <= :now")
    List<MigrationJob> findJobsReadyToStart(@Param("now") LocalDateTime now);

    @Query("SELECT j FROM MigrationJob j WHERE j.user = :user AND j.status IN :statuses")
    List<MigrationJob> findByUserAndStatusIn(@Param("user") User user, @Param("statuses") List<MigrationStatus> statuses);

    @Query("SELECT COUNT(j) FROM MigrationJob j WHERE j.user = :user AND j.status = 'RUNNING'")
    long countRunningJobsByUser(@Param("user") User user);

    @Query("SELECT COUNT(j) FROM MigrationJob j WHERE j.user = :user AND j.status = 'COMPLETED'")
    long countCompletedJobsByUser(@Param("user") User user);

    @Query("SELECT j FROM MigrationJob j WHERE j.status = 'RUNNING' AND j.phase = 'INCREMENTAL_SYNC'")
    List<MigrationJob> findJobsInIncrementalSync();

    @Query("SELECT SUM(j.migratedEmails) FROM MigrationJob j WHERE j.user = :user AND j.status = 'COMPLETED'")
    Long sumMigratedEmailsByUser(@Param("user") User user);
}
