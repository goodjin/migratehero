package com.migratehero.service.connector.google;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.CalendarEvent;
import com.migratehero.service.connector.CalendarConnector;
import com.migratehero.service.oauth.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Google Calendar 连接器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleCalendarConnector implements CalendarConnector {

    private final GoogleOAuthService googleOAuthService;
    private static final String APPLICATION_NAME = "MigrateHero";

    @Override
    public List<CalendarInfo> listCalendars(EmailAccount account) {
        try {
            Calendar service = getCalendarService(account);
            CalendarList calendarList = service.calendarList().list().execute();

            if (calendarList.getItems() == null) {
                return Collections.emptyList();
            }

            return calendarList.getItems().stream()
                    .map(cal -> new CalendarInfo(
                            cal.getId(),
                            cal.getSummary(),
                            cal.getDescription(),
                            cal.getTimeZone(),
                            cal.getBackgroundColor(),
                            "owner".equals(cal.getAccessRole()),
                            !"reader".equals(cal.getAccessRole())
                    ))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list calendars for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list calendars", e);
        }
    }

    @Override
    public EventListResult listEvents(EmailAccount account, String calendarId, String pageToken, int maxResults) {
        try {
            Calendar service = getCalendarService(account);
            Calendar.Events.List request = service.events().list(calendarId)
                    .setMaxResults(maxResults)
                    .setSingleEvents(true)
                    .setOrderBy("startTime");

            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            Events events = request.execute();
            List<CalendarEvent> calendarEvents = events.getItems() != null ?
                    events.getItems().stream()
                            .map(e -> convertToCalendarEvent(e, calendarId))
                            .collect(Collectors.toList()) :
                    Collections.emptyList();

            return new EventListResult(
                    calendarEvents,
                    events.getNextPageToken(),
                    events.getNextSyncToken(),
                    calendarEvents.size()
            );
        } catch (IOException e) {
            log.error("Failed to list events for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list events", e);
        }
    }

    @Override
    public EventListResult listEvents(EmailAccount account, String calendarId, Instant startTime, Instant endTime, String pageToken, int maxResults) {
        try {
            Calendar service = getCalendarService(account);
            Calendar.Events.List request = service.events().list(calendarId)
                    .setMaxResults(maxResults)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setTimeMin(new DateTime(startTime.toEpochMilli()))
                    .setTimeMax(new DateTime(endTime.toEpochMilli()));

            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            Events events = request.execute();
            List<CalendarEvent> calendarEvents = events.getItems() != null ?
                    events.getItems().stream()
                            .map(e -> convertToCalendarEvent(e, calendarId))
                            .collect(Collectors.toList()) :
                    Collections.emptyList();

            return new EventListResult(
                    calendarEvents,
                    events.getNextPageToken(),
                    events.getNextSyncToken(),
                    calendarEvents.size()
            );
        } catch (IOException e) {
            log.error("Failed to list events for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list events", e);
        }
    }

    @Override
    public CalendarEvent getEvent(EmailAccount account, String calendarId, String eventId) {
        try {
            Calendar service = getCalendarService(account);
            Event event = service.events().get(calendarId, eventId).execute();
            return convertToCalendarEvent(event, calendarId);
        } catch (IOException e) {
            log.error("Failed to get event {} for account: {}", eventId, account.getEmail(), e);
            return null;
        }
    }

    @Override
    public String createEvent(EmailAccount account, String calendarId, CalendarEvent calendarEvent) {
        try {
            Calendar service = getCalendarService(account);
            Event event = convertToGoogleEvent(calendarEvent);
            Event created = service.events().insert(calendarId, event).execute();
            return created.getId();
        } catch (IOException e) {
            log.error("Failed to create event for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create event", e);
        }
    }

    @Override
    public void updateEvent(EmailAccount account, String calendarId, String eventId, CalendarEvent calendarEvent) {
        try {
            Calendar service = getCalendarService(account);
            Event event = convertToGoogleEvent(calendarEvent);
            service.events().update(calendarId, eventId, event).execute();
        } catch (IOException e) {
            log.error("Failed to update event {} for account: {}", eventId, account.getEmail(), e);
            throw new RuntimeException("Failed to update event", e);
        }
    }

    @Override
    public void deleteEvent(EmailAccount account, String calendarId, String eventId) {
        try {
            Calendar service = getCalendarService(account);
            service.events().delete(calendarId, eventId).execute();
        } catch (IOException e) {
            log.error("Failed to delete event {} for account: {}", eventId, account.getEmail(), e);
            throw new RuntimeException("Failed to delete event", e);
        }
    }

    @Override
    public long getEventCount(EmailAccount account, String calendarId) {
        try {
            Calendar service = getCalendarService(account);
            Events events = service.events().list(calendarId)
                    .setMaxResults(1)
                    .execute();
            // Google Calendar API 没有直接的计数 API
            return events.getItems() != null ? events.getItems().size() : 0;
        } catch (IOException e) {
            log.error("Failed to get event count for account: {}", account.getEmail(), e);
            return 0;
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String calendarId, String syncToken) {
        try {
            Calendar service = getCalendarService(account);
            Calendar.Events.List request = service.events().list(calendarId);

            if (syncToken != null) {
                request.setSyncToken(syncToken);
            }

            Events events = request.execute();

            List<CalendarEvent> added = new ArrayList<>();
            List<CalendarEvent> modified = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            if (events.getItems() != null) {
                for (Event event : events.getItems()) {
                    if ("cancelled".equals(event.getStatus())) {
                        deletedIds.add(event.getId());
                    } else {
                        modified.add(convertToCalendarEvent(event, calendarId));
                    }
                }
            }

            return new IncrementalChanges(added, modified, deletedIds, events.getNextSyncToken());
        } catch (IOException e) {
            log.error("Failed to get incremental changes for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to get incremental changes", e);
        }
    }

    private Calendar getCalendarService(EmailAccount account) {
        String accessToken = googleOAuthService.getAccessToken(account);
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));

        return new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName(APPLICATION_NAME).build();
    }

    private CalendarEvent convertToCalendarEvent(Event event, String calendarId) {
        CalendarEvent.CalendarEventBuilder builder = CalendarEvent.builder()
                .id(event.getId())
                .calendarId(calendarId)
                .subject(event.getSummary())
                .description(event.getDescription())
                .location(event.getLocation())
                .status(event.getStatus())
                .isCancelled("cancelled".equals(event.getStatus()));

        // 时间
        if (event.getStart() != null) {
            if (event.getStart().getDateTime() != null) {
                builder.startTime(Instant.ofEpochMilli(event.getStart().getDateTime().getValue()));
                builder.isAllDay(false);
            } else if (event.getStart().getDate() != null) {
                builder.startTime(Instant.ofEpochMilli(event.getStart().getDate().getValue()));
                builder.isAllDay(true);
            }
            if (event.getStart().getTimeZone() != null) {
                builder.timeZone(ZoneId.of(event.getStart().getTimeZone()));
            }
        }

        if (event.getEnd() != null) {
            if (event.getEnd().getDateTime() != null) {
                builder.endTime(Instant.ofEpochMilli(event.getEnd().getDateTime().getValue()));
            } else if (event.getEnd().getDate() != null) {
                builder.endTime(Instant.ofEpochMilli(event.getEnd().getDate().getValue()));
            }
        }

        // 组织者
        if (event.getOrganizer() != null) {
            builder.organizer(event.getOrganizer().getEmail());
        }

        // 参与者
        if (event.getAttendees() != null) {
            List<CalendarEvent.Attendee> attendees = event.getAttendees().stream()
                    .map(a -> CalendarEvent.Attendee.builder()
                            .email(a.getEmail())
                            .displayName(a.getDisplayName())
                            .responseStatus(a.getResponseStatus())
                            .isOrganizer(Boolean.TRUE.equals(a.getOrganizer()))
                            .isOptional(Boolean.TRUE.equals(a.getOptional()))
                            .build())
                    .collect(Collectors.toList());
            builder.attendees(attendees);
        }

        // 在线会议
        if (event.getHangoutLink() != null) {
            builder.onlineMeetingUrl(event.getHangoutLink());
        }

        return builder.build();
    }

    private Event convertToGoogleEvent(CalendarEvent calendarEvent) {
        Event event = new Event()
                .setSummary(calendarEvent.getSubject())
                .setDescription(calendarEvent.getDescription())
                .setLocation(calendarEvent.getLocation());

        // 时间
        if (calendarEvent.isAllDay()) {
            event.setStart(new EventDateTime().setDate(new DateTime(calendarEvent.getStartTime().toEpochMilli())));
            event.setEnd(new EventDateTime().setDate(new DateTime(calendarEvent.getEndTime().toEpochMilli())));
        } else {
            event.setStart(new EventDateTime().setDateTime(new DateTime(calendarEvent.getStartTime().toEpochMilli())));
            event.setEnd(new EventDateTime().setDateTime(new DateTime(calendarEvent.getEndTime().toEpochMilli())));
        }

        // 参与者
        if (calendarEvent.getAttendees() != null) {
            List<EventAttendee> attendees = calendarEvent.getAttendees().stream()
                    .map(a -> new EventAttendee()
                            .setEmail(a.getEmail())
                            .setDisplayName(a.getDisplayName())
                            .setOptional(a.isOptional()))
                    .collect(Collectors.toList());
            event.setAttendees(attendees);
        }

        return event;
    }
}
