package com.migratehero.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MVP 已迁移日历事件记录 - 保存每个日历事件的迁移状态
 */
@Entity
@Table(name = "mvp_migrated_calendar_event", indexes = {
        @Index(name = "idx_calendar_task", columnList = "taskId"),
        @Index(name = "idx_calendar_source_id", columnList = "sourceEventId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MvpMigratedCalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    // 源日历事件信息
    @Column(nullable = false)
    private String sourceEventId;

    @Column
    private String calendarName;

    @Column(length = 500)
    private String subject;

    @Column(length = 1000)
    private String location;

    private Instant startTime;

    private Instant endTime;

    @Column
    private Boolean isAllDay;

    @Column
    private Boolean isRecurring;

    @Column(length = 2000)
    private String organizer;

    @Column(length = 4000)
    private String attendees;

    // 迁移状态
    @Column
    @Builder.Default
    private Boolean success = false;

    @Column(length = 1000)
    private String errorMessage;

    // 目标事件ID（迁移成功后的ID）
    private String targetEventId;

    @Column
    private Instant migratedAt;

    @PrePersist
    protected void onCreate() {
        if (migratedAt == null) {
            migratedAt = Instant.now();
        }
    }
}
