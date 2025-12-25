package com.migratehero.repository;

import com.migratehero.model.MvpMigratedContact;
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
public interface MvpMigratedContactRepository extends JpaRepository<MvpMigratedContact, Long> {

    List<MvpMigratedContact> findByTaskIdOrderByMigratedAtDesc(Long taskId);

    List<MvpMigratedContact> findByTaskIdAndFolderNameOrderByDisplayNameAsc(Long taskId, String folderName);

    Page<MvpMigratedContact> findByTaskId(Long taskId, Pageable pageable);

    long countByTaskIdAndSuccess(Long taskId, Boolean success);

    long countByTaskIdAndFolderNameAndSuccess(Long taskId, String folderName, Boolean success);

    boolean existsByTaskIdAndSourceContactId(Long taskId, String sourceContactId);

    boolean existsByTaskIdAndSourceContactIdAndSuccess(Long taskId, String sourceContactId, Boolean success);

    @Query("SELECT DISTINCT c.folderName FROM MvpMigratedContact c WHERE c.taskId = :taskId")
    List<String> findDistinctFolderNamesByTaskId(@Param("taskId") Long taskId);

    @Modifying
    @Transactional
    void deleteByTaskId(@Param("taskId") Long taskId);
}
