package com.migratehero.transform;

import com.migratehero.model.dto.CalendarEvent;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.service.transform.CalendarTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarTransformerTest {

    private CalendarTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CalendarTransformer();
    }

    @Test
    void transform_shouldReturnNullForNullInput() {
        assertNull(transformer.transform(null, ProviderType.MICROSOFT));
    }

    @Test
    void transform_shouldPreserveBasicFields() {
        Instant now = Instant.now();
        Instant later = now.plusSeconds(3600);

        CalendarEvent source = CalendarEvent.builder()
                .id("test-id")
                .subject("Team Meeting")
                .description("Weekly sync")
                .location("Conference Room A")
                .startTime(now)
                .endTime(later)
                .timeZone(ZoneId.of("UTC"))
                .isAllDay(false)
                .organizer("organizer@example.com")
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals("Team Meeting", result.getSubject());
        assertEquals("Weekly sync", result.getDescription());
        assertEquals("Conference Room A", result.getLocation());
        assertEquals(now, result.getStartTime());
        assertEquals(later, result.getEndTime());
        assertEquals(ZoneId.of("UTC"), result.getTimeZone());
        assertFalse(result.isAllDay());
        assertEquals("organizer@example.com", result.getOrganizer());
    }

    @Test
    void transform_shouldConvertAttendeeResponseStatus_GoogleToMicrosoft() {
        CalendarEvent source = CalendarEvent.builder()
                .attendees(Arrays.asList(
                        CalendarEvent.Attendee.builder()
                                .email("attendee@example.com")
                                .responseStatus("accepted")
                                .build(),
                        CalendarEvent.Attendee.builder()
                                .email("tentative@example.com")
                                .responseStatus("tentative")
                                .build(),
                        CalendarEvent.Attendee.builder()
                                .email("pending@example.com")
                                .responseStatus("needsAction")
                                .build()
                ))
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals(3, result.getAttendees().size());
        assertEquals("accepted", result.getAttendees().get(0).getResponseStatus());
        assertEquals("tentativelyAccepted", result.getAttendees().get(1).getResponseStatus());
        assertEquals("none", result.getAttendees().get(2).getResponseStatus());
    }

    @Test
    void transform_shouldConvertAttendeeResponseStatus_MicrosoftToGoogle() {
        CalendarEvent source = CalendarEvent.builder()
                .attendees(Arrays.asList(
                        CalendarEvent.Attendee.builder()
                                .email("accepted@example.com")
                                .responseStatus("accepted")
                                .build(),
                        CalendarEvent.Attendee.builder()
                                .email("tentative@example.com")
                                .responseStatus("tentativelyAccepted")
                                .build(),
                        CalendarEvent.Attendee.builder()
                                .email("pending@example.com")
                                .responseStatus("none")
                                .build()
                ))
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.GOOGLE);

        assertEquals(3, result.getAttendees().size());
        assertEquals("accepted", result.getAttendees().get(0).getResponseStatus());
        assertEquals("tentative", result.getAttendees().get(1).getResponseStatus());
        assertEquals("needsAction", result.getAttendees().get(2).getResponseStatus());
    }

    @Test
    void transform_shouldConvertStatus_GoogleToMicrosoft() {
        CalendarEvent source = CalendarEvent.builder()
                .status("confirmed")
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals("busy", result.getStatus());
    }

    @Test
    void transform_shouldConvertStatus_MicrosoftToGoogle() {
        CalendarEvent source = CalendarEvent.builder()
                .status("busy")
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.GOOGLE);

        assertEquals("confirmed", result.getStatus());
    }

    @Test
    void transform_shouldConvertVisibility_GoogleToMicrosoft() {
        CalendarEvent source = CalendarEvent.builder()
                .visibility("private")
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.MICROSOFT);

        assertEquals("private", result.getVisibility());
    }

    @Test
    void transform_shouldConvertReminderMethod_GoogleToMicrosoft() {
        CalendarEvent source = CalendarEvent.builder()
                .reminder(CalendarEvent.Reminder.builder()
                        .method("popup")
                        .minutesBefore(15)
                        .build())
                .build();

        CalendarEvent result = transformer.transform(source, ProviderType.MICROSOFT);

        assertNotNull(result.getReminder());
        assertEquals("alert", result.getReminder().getMethod());
        assertEquals(15, result.getReminder().getMinutesBefore());
    }

    @Test
    void transformBatch_shouldTransformMultipleEvents() {
        List<CalendarEvent> sources = Arrays.asList(
                CalendarEvent.builder().id("1").subject("Event 1").build(),
                CalendarEvent.builder().id("2").subject("Event 2").build()
        );

        List<CalendarEvent> results = transformer.transformBatch(sources, ProviderType.MICROSOFT);

        assertEquals(2, results.size());
    }
}
