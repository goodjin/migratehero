package com.migratehero.service.transform;

import com.migratehero.model.dto.CalendarEvent;
import com.migratehero.model.enums.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日历数据转换器 - 处理 Google ↔ Microsoft 日历格式转换
 */
@Slf4j
@Component
public class CalendarTransformer {

    /**
     * 转换日历事件以适配目标平台
     */
    public CalendarEvent transform(CalendarEvent source, ProviderType targetProvider) {
        if (source == null) {
            return null;
        }

        CalendarEvent.CalendarEventBuilder builder = CalendarEvent.builder()
                .subject(source.getSubject())
                .description(source.getDescription())
                .location(source.getLocation())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .timeZone(source.getTimeZone())
                .isAllDay(source.isAllDay())
                .organizer(source.getOrganizer())
                .attendees(transformAttendees(source.getAttendees(), targetProvider))
                .recurrence(transformRecurrence(source.getRecurrence(), targetProvider))
                .reminder(transformReminder(source.getReminder(), targetProvider))
                .status(transformStatus(source.getStatus(), targetProvider))
                .visibility(transformVisibility(source.getVisibility(), targetProvider))
                .isCancelled(source.isCancelled())
                .onlineMeetingUrl(source.getOnlineMeetingUrl())
                .attachments(source.getAttachments());

        return builder.build();
    }

    /**
     * 转换参与者响应状态
     */
    private List<CalendarEvent.Attendee> transformAttendees(List<CalendarEvent.Attendee> attendees, ProviderType targetProvider) {
        if (attendees == null || attendees.isEmpty()) {
            return new ArrayList<>();
        }

        return attendees.stream()
                .map(a -> CalendarEvent.Attendee.builder()
                        .email(a.getEmail())
                        .displayName(a.getDisplayName())
                        .responseStatus(transformResponseStatus(a.getResponseStatus(), targetProvider))
                        .isOrganizer(a.isOrganizer())
                        .isOptional(a.isOptional())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 转换参与者响应状态
     */
    private String transformResponseStatus(String status, ProviderType targetProvider) {
        if (status == null) {
            return targetProvider == ProviderType.MICROSOFT ? "none" : "needsAction";
        }

        String lowerStatus = status.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerStatus) {
                case "accepted" -> "accepted";
                case "declined" -> "declined";
                case "tentative", "tentativelyaccepted" -> "tentativelyAccepted";
                case "needsaction" -> "none";
                default -> "none";
            };
        } else {
            return switch (lowerStatus) {
                case "accepted" -> "accepted";
                case "declined" -> "declined";
                case "tentativelyaccepted", "tentative" -> "tentative";
                case "none" -> "needsAction";
                default -> "needsAction";
            };
        }
    }

    /**
     * 转换重复规则
     */
    private CalendarEvent.Recurrence transformRecurrence(CalendarEvent.Recurrence recurrence, ProviderType targetProvider) {
        if (recurrence == null) {
            return null;
        }

        return CalendarEvent.Recurrence.builder()
                .pattern(recurrence.getPattern())
                .interval(recurrence.getInterval())
                .daysOfWeek(recurrence.getDaysOfWeek())
                .dayOfMonth(recurrence.getDayOfMonth())
                .monthOfYear(recurrence.getMonthOfYear())
                .endDate(recurrence.getEndDate())
                .occurrences(recurrence.getOccurrences())
                .build();
    }

    /**
     * 转换提醒设置
     */
    private CalendarEvent.Reminder transformReminder(CalendarEvent.Reminder reminder, ProviderType targetProvider) {
        if (reminder == null) {
            return null;
        }

        return CalendarEvent.Reminder.builder()
                .method(transformReminderMethod(reminder.getMethod(), targetProvider))
                .minutesBefore(reminder.getMinutesBefore())
                .build();
    }

    /**
     * 转换提醒方式
     */
    private String transformReminderMethod(String method, ProviderType targetProvider) {
        if (method == null) {
            return targetProvider == ProviderType.MICROSOFT ? "alert" : "popup";
        }

        String lowerMethod = method.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerMethod) {
                case "popup", "alert" -> "alert";
                case "email" -> "email";
                case "sms" -> "alert"; // MS 不支持 SMS
                default -> "alert";
            };
        } else {
            return switch (lowerMethod) {
                case "popup", "alert" -> "popup";
                case "email" -> "email";
                default -> "popup";
            };
        }
    }

    /**
     * 转换事件状态
     */
    private String transformStatus(String status, ProviderType targetProvider) {
        if (status == null) {
            return targetProvider == ProviderType.MICROSOFT ? "busy" : "confirmed";
        }

        String lowerStatus = status.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerStatus) {
                case "confirmed" -> "busy";
                case "tentative" -> "tentative";
                case "cancelled" -> "free";
                case "free" -> "free";
                case "busy" -> "busy";
                case "oof", "outofoffice" -> "oof";
                case "workingelsewhere" -> "workingElsewhere";
                default -> "busy";
            };
        } else {
            return switch (lowerStatus) {
                case "busy" -> "confirmed";
                case "tentative" -> "tentative";
                case "free" -> "cancelled";
                case "oof", "outofoffice" -> "confirmed";
                case "workingelsewhere" -> "confirmed";
                default -> "confirmed";
            };
        }
    }

    /**
     * 转换可见性/隐私设置
     */
    private String transformVisibility(String visibility, ProviderType targetProvider) {
        if (visibility == null) {
            return targetProvider == ProviderType.MICROSOFT ? "normal" : "default";
        }

        String lowerVisibility = visibility.toLowerCase();
        if (targetProvider == ProviderType.MICROSOFT) {
            return switch (lowerVisibility) {
                case "public", "default" -> "normal";
                case "private" -> "private";
                case "confidential" -> "confidential";
                default -> "normal";
            };
        } else {
            return switch (lowerVisibility) {
                case "normal" -> "default";
                case "private" -> "private";
                case "confidential" -> "confidential";
                default -> "default";
            };
        }
    }

    /**
     * 批量转换日历事件
     */
    public List<CalendarEvent> transformBatch(List<CalendarEvent> sources, ProviderType targetProvider) {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }

        List<CalendarEvent> results = new ArrayList<>(sources.size());
        for (CalendarEvent source : sources) {
            try {
                results.add(transform(source, targetProvider));
            } catch (Exception e) {
                log.error("Failed to transform calendar event: {}", source.getId(), e);
            }
        }
        return results;
    }
}
