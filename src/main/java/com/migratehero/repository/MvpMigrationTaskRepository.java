package com.migratehero.repository;

import com.migratehero.model.MvpMigrationTask;
import com.migratehero.model.enums.MigrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MvpMigrationTaskRepository extends JpaRepository<MvpMigrationTask, Long> {

    List<MvpMigrationTask> findByStatusOrderByCreatedAtDesc(MigrationStatus status);

    List<MvpMigrationTask> findAllByOrderByCreatedAtDesc();

    // 检查是否存在活跃的任务（非完成/失败/取消状态）
    boolean existsBySourceEmailAndStatusNotIn(String sourceEmail, List<MigrationStatus> excludeStatuses);
}
