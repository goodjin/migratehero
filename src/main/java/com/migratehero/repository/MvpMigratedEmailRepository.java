package com.migratehero.repository;

import com.migratehero.model.MvpMigratedEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MvpMigratedEmailRepository extends JpaRepository<MvpMigratedEmail, Long> {

    List<MvpMigratedEmail> findByTaskIdAndFolderNameOrderByMigratedAtDesc(Long taskId, String folderName);

    Page<MvpMigratedEmail> findByTaskIdAndFolderName(Long taskId, String folderName, Pageable pageable);

    long countByTaskIdAndSuccess(Long taskId, Boolean success);

    long countByTaskIdAndFolderNameAndSuccess(Long taskId, String folderName, Boolean success);

    boolean existsByTaskIdAndSourceEmailId(Long taskId, String sourceEmailId);

    boolean existsByTaskIdAndSourceEmailIdAndSuccess(Long taskId, String sourceEmailId, Boolean success);

    @Query("SELECT DISTINCT e.folderName FROM MvpMigratedEmail e WHERE e.taskId = :taskId")
    List<String> findDistinctFolderNamesByTaskId(@Param("taskId") Long taskId);

    @Modifying
    @Transactional
    void deleteByTaskId(@Param("taskId") Long taskId);
}
