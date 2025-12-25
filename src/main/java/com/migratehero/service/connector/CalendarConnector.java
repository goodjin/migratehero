package com.migratehero.service.connector;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.CalendarEvent;

import java.time.Instant;
import java.util.List;

/**
 * 日历连接器接口 - 定义日历事件读写操作
 */
public interface CalendarConnector {

    /**
     * 获取日历列表
     */
    List<CalendarInfo> listCalendars(EmailAccount account);

    /**
     * 获取事件列表
     */
    EventListResult listEvents(EmailAccount account, String calendarId, String pageToken, int maxResults);

    /**
     * 获取指定时间范围的事件
     */
    EventListResult listEvents(EmailAccount account, String calendarId, Instant startTime, Instant endTime, String pageToken, int maxResults);

    /**
     * 获取单个事件详情
     */
    CalendarEvent getEvent(EmailAccount account, String calendarId, String eventId);

    /**
     * 创建事件
     */
    String createEvent(EmailAccount account, String calendarId, CalendarEvent event);

    /**
     * 更新事件
     */
    void updateEvent(EmailAccount account, String calendarId, String eventId, CalendarEvent event);

    /**
     * 删除事件
     */
    void deleteEvent(EmailAccount account, String calendarId, String eventId);

    /**
     * 获取事件统计
     */
    long getEventCount(EmailAccount account, String calendarId);

    /**
     * 获取增量变化
     */
    IncrementalChanges getIncrementalChanges(EmailAccount account, String calendarId, String syncToken);

    /**
     * 日历信息
     */
    record CalendarInfo(
            String id,
            String name,
            String description,
            String timeZone,
            String color,
            boolean isPrimary,
            boolean canEdit
    ) {}

    /**
     * 事件列表结果
     */
    record EventListResult(
            List<CalendarEvent> events,
            String nextPageToken,
            String syncToken,
            long totalCount
    ) {}

    /**
     * 增量变化
     */
    record IncrementalChanges(
            List<CalendarEvent> added,
            List<CalendarEvent> modified,
            List<String> deletedIds,
            String newSyncToken
    ) {}
}
