package com.migratehero.repository;

import com.migratehero.model.MvpFolderProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MvpFolderProgressRepository extends JpaRepository<MvpFolderProgress, Long> {

    List<MvpFolderProgress> findByTaskIdOrderByFolderNameAsc(Long taskId);

    Optional<MvpFolderProgress> findByTaskIdAndFolderName(Long taskId, String folderName);

    long countByTaskIdAndStatus(Long taskId, String status);

    @Modifying
    @Transactional
    void deleteByTaskId(@Param("taskId") Long taskId);
}
