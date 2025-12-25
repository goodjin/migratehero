package com.migratehero.service.transform;

import com.migratehero.service.connector.ews.MvpEwsConnector.CalendarEventDetail;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * MVP 日历事件转换器 - 将 EWS 日历事件转换为 iCalendar 格式
 */
@Slf4j
@Component
public class MvpCalendarTransformer {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

    /**
     * 将 EWS 日历事件转换为 iCalendar 格式字符串
     */
    public String toICalendar(CalendarEventDetail event) {
        try {
            Calendar calendar = new Calendar();
            calendar.getProperties().add(new ProdId("-//MigrateHero//Calendar Migration//EN"));
            calendar.getProperties().add(Version.VERSION_2_0);
            calendar.getProperties().add(CalScale.GREGORIAN);

            VEvent vEvent = createVEvent(event);
            calendar.getComponents().add(vEvent);

            return calendar.toString();
        } catch (Exception e) {
            log.error("Failed to convert calendar event to iCalendar: {}", e.getMessage(), e);
            // 回退到手动构建
            return buildICalendarManually(event);
        }
    }

    private VEvent createVEvent(CalendarEventDetail event) throws Exception {
        DateTime startDate = new DateTime(Date.from(event.getStartTime()));
        startDate.setUtc(true);

        DateTime endDate = new DateTime(Date.from(event.getEndTime()));
        endDate.setUtc(true);

        VEvent vEvent = new VEvent(startDate, endDate, event.getSubject());

        // UID
        String uid = event.getId() != null ? event.getId() : UUID.randomUUID().toString();
        vEvent.getProperties().add(new Uid(uid + "@migratehero.com"));

        // 地点
        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            vEvent.getProperties().add(new Location(event.getLocation()));
        }

        // 描述
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            vEvent.getProperties().add(new Description(event.getDescription()));
        }

        // 组织者
        if (event.getOrganizer() != null) {
            vEvent.getProperties().add(new Organizer("mailto:" + event.getOrganizer()));
        }

        // 参与者
        if (event.getAttendees() != null) {
            for (String attendeeEmail : event.getAttendees()) {
                vEvent.getProperties().add(new Attendee("mailto:" + attendeeEmail));
            }
        }

        // 全天事件 - 通过使用 DATE 而非 DATE-TIME 来表示
        // ical4j 默认使用 DATE-TIME，全天事件需要特殊处理

        // 提醒
        if (event.getReminderMinutes() != null && event.getReminderMinutes() > 0) {
            // 创建 VALARM 组件
            java.time.Duration duration = java.time.Duration.ofMinutes(-event.getReminderMinutes());
            net.fortuna.ical4j.model.component.VAlarm alarm =
                    new net.fortuna.ical4j.model.component.VAlarm(duration);
            alarm.getProperties().add(Action.DISPLAY);
            alarm.getProperties().add(new Description("Reminder"));
            vEvent.getAlarms().add(alarm);
        }

        // 状态
        if (event.isCancelled()) {
            vEvent.getProperties().add(Status.VEVENT_CANCELLED);
        } else {
            vEvent.getProperties().add(Status.VEVENT_CONFIRMED);
        }

        return vEvent;
    }

    /**
     * 手动构建 iCalendar 格式（回退方案）
     */
    private String buildICalendarManually(CalendarEventDetail event) {
        StringBuilder sb = new StringBuilder();

        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//MigrateHero//Calendar Migration//EN\r\n");
        sb.append("BEGIN:VEVENT\r\n");

        // UID
        String uid = event.getId() != null ? event.getId() : UUID.randomUUID().toString();
        sb.append("UID:").append(uid).append("@migratehero.com\r\n");

        // 时间
        if (event.isAllDay()) {
            // 全天事件使用 DATE 格式
            sb.append("DTSTART;VALUE=DATE:").append(formatDate(event.getStartTime())).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(formatDate(event.getEndTime())).append("\r\n");
        } else {
            sb.append("DTSTART:").append(formatDateTime(event.getStartTime())).append("\r\n");
            sb.append("DTEND:").append(formatDateTime(event.getEndTime())).append("\r\n");
        }

        // 主题
        sb.append("SUMMARY:").append(escapeICalText(event.getSubject())).append("\r\n");

        // 地点
        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("LOCATION:").append(escapeICalText(event.getLocation())).append("\r\n");
        }

        // 描述
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            sb.append("DESCRIPTION:").append(escapeICalText(event.getDescription())).append("\r\n");
        }

        // 组织者
        if (event.getOrganizer() != null) {
            sb.append("ORGANIZER:mailto:").append(event.getOrganizer()).append("\r\n");
        }

        // 参与者
        if (event.getAttendees() != null) {
            for (String attendee : event.getAttendees()) {
                sb.append("ATTENDEE:mailto:").append(attendee).append("\r\n");
            }
        }

        // 状态
        sb.append("STATUS:").append(event.isCancelled() ? "CANCELLED" : "CONFIRMED").append("\r\n");

        // 时间戳
        sb.append("DTSTAMP:").append(formatDateTime(Instant.now())).append("\r\n");

        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString();
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) return "";
        return DATE_FORMATTER.format(instant);
    }

    private String formatDate(Instant instant) {
        if (instant == null) return "";
        return DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(instant);
    }

    private String escapeICalText(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
