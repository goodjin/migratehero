package com.migratehero.service.connector.microsoft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.CalendarEvent;
import com.migratehero.service.connector.CalendarConnector;
import com.migratehero.service.oauth.MicrosoftOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Microsoft Graph 日历连接器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicrosoftCalendarConnector implements CalendarConnector {

    private final MicrosoftOAuthService microsoftOAuthService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";

    @Override
    public List<CalendarInfo> listCalendars(EmailAccount account) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/calendars",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            List<CalendarInfo> calendars = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    calendars.add(new CalendarInfo(
                            node.get("id").asText(),
                            node.has("name") ? node.get("name").asText() : "",
                            null,
                            null,
                            node.has("color") ? node.get("color").asText() : null,
                            node.has("isDefaultCalendar") && node.get("isDefaultCalendar").asBoolean(),
                            node.has("canEdit") && node.get("canEdit").asBoolean()
                    ));
                }
            }

            return calendars;
        } catch (Exception e) {
            log.error("Failed to list calendars for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list calendars", e);
        }
    }

    @Override
    public EventListResult listEvents(EmailAccount account, String calendarId, String pageToken, int maxResults) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = pageToken != null ? pageToken :
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events?$top=" + maxResults;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            List<CalendarEvent> events = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    events.add(convertToCalendarEvent(node, calendarId));
                }
            }

            String nextPageToken = root.has("@odata.nextLink") ?
                    root.get("@odata.nextLink").asText() : null;
            String deltaLink = root.has("@odata.deltaLink") ?
                    root.get("@odata.deltaLink").asText() : null;

            return new EventListResult(events, nextPageToken, deltaLink, events.size());
        } catch (Exception e) {
            log.error("Failed to list events for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list events", e);
        }
    }

    @Override
    public EventListResult listEvents(EmailAccount account, String calendarId, Instant startTime, Instant endTime, String pageToken, int maxResults) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String startStr = DateTimeFormatter.ISO_INSTANT.format(startTime);
            String endStr = DateTimeFormatter.ISO_INSTANT.format(endTime);

            String url = pageToken != null ? pageToken :
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/calendarView?startDateTime=" + startStr + "&endDateTime=" + endStr + "&$top=" + maxResults;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            List<CalendarEvent> events = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    events.add(convertToCalendarEvent(node, calendarId));
                }
            }

            String nextPageToken = root.has("@odata.nextLink") ?
                    root.get("@odata.nextLink").asText() : null;

            return new EventListResult(events, nextPageToken, null, events.size());
        } catch (Exception e) {
            log.error("Failed to list events for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list events", e);
        }
    }

    @Override
    public CalendarEvent getEvent(EmailAccount account, String calendarId, String eventId) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events/" + eventId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode node = objectMapper.readTree(response.getBody());
            return convertToCalendarEvent(node, calendarId);
        } catch (Exception e) {
            log.error("Failed to get event {} for account: {}", eventId, account.getEmail(), e);
            return null;
        }
    }

    @Override
    public String createEvent(EmailAccount account, String calendarId, CalendarEvent event) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = convertToMsEvent(event);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events",
                    request,
                    String.class);

            JsonNode result = objectMapper.readTree(response.getBody());
            return result.get("id").asText();
        } catch (Exception e) {
            log.error("Failed to create event for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create event", e);
        }
    }

    @Override
    public void updateEvent(EmailAccount account, String calendarId, String eventId, CalendarEvent event) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = convertToMsEvent(event);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.exchange(
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events/" + eventId,
                    HttpMethod.PATCH,
                    request,
                    String.class);
        } catch (Exception e) {
            log.error("Failed to update event {} for account: {}", eventId, account.getEmail(), e);
            throw new RuntimeException("Failed to update event", e);
        }
    }

    @Override
    public void deleteEvent(EmailAccount account, String calendarId, String eventId) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            restTemplate.exchange(
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events/" + eventId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class);
        } catch (Exception e) {
            log.error("Failed to delete event {} for account: {}", eventId, account.getEmail(), e);
            throw new RuntimeException("Failed to delete event", e);
        }
    }

    @Override
    public long getEventCount(EmailAccount account, String calendarId) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.add("ConsistencyLevel", "eventual");

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events/$count",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            return Long.parseLong(response.getBody());
        } catch (Exception e) {
            log.error("Failed to get event count for account: {}", account.getEmail(), e);
            return 0;
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String calendarId, String syncToken) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = syncToken != null ? syncToken :
                    GRAPH_API_URL + "/me/calendars/" + calendarId + "/events/delta";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            List<CalendarEvent> added = new ArrayList<>();
            List<CalendarEvent> modified = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    String id = node.get("id").asText();
                    if (node.has("@removed")) {
                        deletedIds.add(id);
                    } else {
                        modified.add(convertToCalendarEvent(node, calendarId));
                    }
                }
            }

            String newSyncToken = root.has("@odata.deltaLink") ?
                    root.get("@odata.deltaLink").asText() : syncToken;

            return new IncrementalChanges(added, modified, deletedIds, newSyncToken);
        } catch (Exception e) {
            log.error("Failed to get incremental changes for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to get incremental changes", e);
        }
    }

    private CalendarEvent convertToCalendarEvent(JsonNode node, String calendarId) {
        CalendarEvent.CalendarEventBuilder builder = CalendarEvent.builder()
                .id(node.get("id").asText())
                .calendarId(calendarId)
                .subject(node.has("subject") ? node.get("subject").asText() : "")
                .isCancelled(node.has("isCancelled") && node.get("isCancelled").asBoolean());

        if (node.has("body") && node.get("body").has("content")) {
            builder.description(node.get("body").get("content").asText());
        }

        if (node.has("location") && node.get("location").has("displayName")) {
            builder.location(node.get("location").get("displayName").asText());
        }

        // 时间
        if (node.has("start")) {
            JsonNode start = node.get("start");
            if (start.has("dateTime")) {
                try {
                    builder.startTime(OffsetDateTime.parse(start.get("dateTime").asText()).toInstant());
                } catch (Exception ignored) {}
            }
            if (start.has("timeZone")) {
                try {
                    builder.timeZone(ZoneId.of(start.get("timeZone").asText()));
                } catch (Exception ignored) {}
            }
        }

        if (node.has("end") && node.get("end").has("dateTime")) {
            try {
                builder.endTime(OffsetDateTime.parse(node.get("end").get("dateTime").asText()).toInstant());
            } catch (Exception ignored) {}
        }

        builder.isAllDay(node.has("isAllDay") && node.get("isAllDay").asBoolean());

        // 组织者
        if (node.has("organizer") && node.get("organizer").has("emailAddress")) {
            builder.organizer(node.get("organizer").get("emailAddress").get("address").asText());
        }

        // 参与者
        if (node.has("attendees")) {
            List<CalendarEvent.Attendee> attendees = new ArrayList<>();
            for (JsonNode attendee : node.get("attendees")) {
                CalendarEvent.Attendee.AttendeeBuilder ab = CalendarEvent.Attendee.builder();
                if (attendee.has("emailAddress")) {
                    ab.email(attendee.get("emailAddress").get("address").asText());
                    ab.displayName(attendee.get("emailAddress").has("name") ?
                            attendee.get("emailAddress").get("name").asText() : null);
                }
                if (attendee.has("status") && attendee.get("status").has("response")) {
                    ab.responseStatus(attendee.get("status").get("response").asText());
                }
                ab.isOptional(attendee.has("type") && "optional".equals(attendee.get("type").asText()));
                attendees.add(ab.build());
            }
            builder.attendees(attendees);
        }

        // 在线会议
        if (node.has("onlineMeeting") && node.get("onlineMeeting").has("joinUrl")) {
            builder.onlineMeetingUrl(node.get("onlineMeeting").get("joinUrl").asText());
        }

        return builder.build();
    }

    private Map<String, Object> convertToMsEvent(CalendarEvent event) {
        Map<String, Object> msEvent = new HashMap<>();
        msEvent.put("subject", event.getSubject());

        if (event.getDescription() != null) {
            Map<String, String> body = new HashMap<>();
            body.put("contentType", "html");
            body.put("content", event.getDescription());
            msEvent.put("body", body);
        }

        if (event.getLocation() != null) {
            Map<String, String> location = new HashMap<>();
            location.put("displayName", event.getLocation());
            msEvent.put("location", location);
        }

        // 时间
        String timeZone = event.getTimeZone() != null ? event.getTimeZone().getId() : "UTC";
        Map<String, String> start = new HashMap<>();
        start.put("dateTime", DateTimeFormatter.ISO_INSTANT.format(event.getStartTime()));
        start.put("timeZone", timeZone);
        msEvent.put("start", start);

        Map<String, String> end = new HashMap<>();
        end.put("dateTime", DateTimeFormatter.ISO_INSTANT.format(event.getEndTime()));
        end.put("timeZone", timeZone);
        msEvent.put("end", end);

        msEvent.put("isAllDay", event.isAllDay());

        // 参与者
        if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
            List<Map<String, Object>> attendees = new ArrayList<>();
            for (CalendarEvent.Attendee a : event.getAttendees()) {
                Map<String, Object> attendee = new HashMap<>();
                Map<String, String> emailAddress = new HashMap<>();
                emailAddress.put("address", a.getEmail());
                if (a.getDisplayName() != null) {
                    emailAddress.put("name", a.getDisplayName());
                }
                attendee.put("emailAddress", emailAddress);
                attendee.put("type", a.isOptional() ? "optional" : "required");
                attendees.add(attendee);
            }
            msEvent.put("attendees", attendees);
        }

        return msEvent;
    }
}
