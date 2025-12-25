package com.migratehero.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * 统一日历事件 DTO - 用于 Google 和 Microsoft 日历格式转换
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    private String id;
    private String calendarId;
    private String subject;
    private String description;
    private String location;
    private Instant startTime;
    private Instant endTime;
    private ZoneId timeZone;
    private boolean isAllDay;
    private String organizer;
    private List<Attendee> attendees;
    private Recurrence recurrence;
    private Reminder reminder;
    private String status; // confirmed, tentative, cancelled
    private String visibility; // public, private, default
    private boolean isCancelled;
    private String onlineMeetingUrl;
    private List<Attachment> attachments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attendee {
        private String email;
        private String displayName;
        private String responseStatus; // accepted, declined, tentative, needsAction
        private boolean isOrganizer;
        private boolean isOptional;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recurrence {
        private String pattern; // daily, weekly, monthly, yearly
        private int interval;
        private List<String> daysOfWeek;
        private Integer dayOfMonth;
        private Integer monthOfYear;
        private Instant endDate;
        private Integer occurrences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reminder {
        private int minutesBefore;
        private String method; // email, popup
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String filename;
        private String mimeType;
        private String url;
        private long size;
    }
}
