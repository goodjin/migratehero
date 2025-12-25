package com.migratehero.repository;

import com.migratehero.model.MvpMigratedCalendarEvent;
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
public interface MvpMigratedCalendarEventRepository extends JpaRepository<MvpMigratedCalendarEvent, Long> {

    List<MvpMigratedCalendarEvent> findByTaskIdOrderByMigratedAtDesc(Long taskId);

    List<MvpMigratedCalendarEvent> findByTaskIdAndCalendarNameOrderByStartTimeDesc(Long taskId, String calendarName);

    Page<MvpMigratedCalendarEvent> findByTaskId(Long taskId, Pageable pageable);

    long countByTaskIdAndSuccess(Long taskId, Boolean success);

    long countByTaskIdAndCalendarNameAndSuccess(Long taskId, String calendarName, Boolean success);

    boolean existsByTaskIdAndSourceEventId(Long taskId, String sourceEventId);

    boolean existsByTaskIdAndSourceEventIdAndSuccess(Long taskId, String sourceEventId, Boolean success);

    @Query("SELECT DISTINCT e.calendarName FROM MvpMigratedCalendarEvent e WHERE e.taskId = :taskId")
    List<String> findDistinctCalendarNamesByTaskId(@Param("taskId") Long taskId);

    @Modifying
    @Transactional
    void deleteByTaskId(@Param("taskId") Long taskId);
}
