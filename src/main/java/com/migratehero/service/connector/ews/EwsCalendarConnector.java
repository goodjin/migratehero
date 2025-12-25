package com.migratehero.service.connector.ews;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.CalendarEvent;
import com.migratehero.service.EncryptionService;
import com.migratehero.service.connector.CalendarConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.enumeration.service.ConflictResolutionMode;
import microsoft.exchange.webservices.data.core.enumeration.service.DeleteMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendCancellationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsOrCancellationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SyncFolderItemsScope;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.property.complex.AttendeeCollection;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.complex.MessageBody;
import microsoft.exchange.webservices.data.search.CalendarView;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.sync.ChangeCollection;
import microsoft.exchange.webservices.data.sync.ItemChange;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * EWS 日历连接器 - 使用 Exchange Web Services 访问 Microsoft 日历
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EwsCalendarConnector implements CalendarConnector {

    private final EncryptionService encryptionService;

    private static final String EWS_URL = "https://outlook.office365.com/EWS/Exchange.asmx";
    private static final int DEFAULT_PAGE_SIZE = 100;

    @Override
    public List<CalendarInfo> listCalendars(EmailAccount account) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            // EWS 主要支持默认日历，获取日历文件夹信息
            CalendarFolder calendarFolder = CalendarFolder.bind(service, WellKnownFolderName.Calendar);

            List<CalendarInfo> calendars = new ArrayList<>();
            calendars.add(new CalendarInfo(
                    calendarFolder.getId().getUniqueId(),
                    calendarFolder.getDisplayName(),
                    null,
                    ZoneId.systemDefault().getId(),
                    null,
                    true,
                    true
            ));

            return calendars;

        } catch (Exception e) {
            log.error("Failed to list calendars via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list calendars via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public EventListResult listEvents(EmailAccount account, String calendarId, String pageToken, int maxResults) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            int offset = pageToken != null ? Integer.parseInt(pageToken) : 0;
            int pageSize = maxResults > 0 ? Math.min(maxResults, DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

            ItemView view = new ItemView(pageSize, offset);
            view.getOrderBy().add(AppointmentSchema.Start, SortDirection.Descending);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FolderId folderId = calendarId != null ? new FolderId(calendarId) : new FolderId(WellKnownFolderName.Calendar);
            FindItemsResults<Item> findResults = service.findItems(folderId, view);

            List<CalendarEvent> events = new ArrayList<>();
            for (Item item : findResults) {
                if (item instanceof Appointment) {
                    Appointment appointment = (Appointment) item;
                    events.add(convertToCalendarEvent(appointment, calendarId));
                }
            }

            String nextPageToken = null;
            if (findResults.isMoreAvailable()) {
                nextPageToken = String.valueOf(offset + pageSize);
            }

            Folder folder = Folder.bind(service, folderId);
            long totalCount = folder.getTotalCount();

            return new EventListResult(events, nextPageToken, null, totalCount);

        } catch (Exception e) {
            log.error("Failed to list events via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list events via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public EventListResult listEvents(EmailAccount account, String calendarId, Instant startTime, Instant endTime,
                                       String pageToken, int maxResults) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            Date startDate = Date.from(startTime);
            Date endDate = Date.from(endTime);

            CalendarView calendarView = new CalendarView(startDate, endDate, maxResults > 0 ? maxResults : DEFAULT_PAGE_SIZE);
            calendarView.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FolderId folderId = calendarId != null ? new FolderId(calendarId) : new FolderId(WellKnownFolderName.Calendar);
            FindItemsResults<Appointment> findResults = service.findAppointments(folderId, calendarView);

            List<CalendarEvent> events = new ArrayList<>();
            for (Appointment appointment : findResults) {
                events.add(convertToCalendarEvent(appointment, calendarId));
            }

            return new EventListResult(events, null, null, events.size());

        } catch (Exception e) {
            log.error("Failed to list events by time range via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list events via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public CalendarEvent getEvent(EmailAccount account, String calendarId, String eventId) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            Appointment appointment = Appointment.bind(
                    service,
                    new ItemId(eventId),
                    new PropertySet(BasePropertySet.FirstClassProperties)
            );

            return convertToCalendarEvent(appointment, calendarId);

        } catch (Exception e) {
            log.error("Failed to get event {} via EWS: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to get event via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public String createEvent(EmailAccount account, String calendarId, CalendarEvent event) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            Appointment appointment = new Appointment(service);

            // 基本信息
            appointment.setSubject(event.getSubject());
            if (event.getDescription() != null) {
                appointment.setBody(new MessageBody(event.getDescription()));
            }
            if (event.getLocation() != null) {
                appointment.setLocation(event.getLocation());
            }

            // 时间
            appointment.setStart(Date.from(event.getStartTime()));
            appointment.setEnd(Date.from(event.getEndTime()));
            appointment.setIsAllDayEvent(event.isAllDay());

            // 参会者
            if (event.getAttendees() != null) {
                for (CalendarEvent.Attendee attendee : event.getAttendees()) {
                    if (!attendee.isOrganizer()) {
                        if (attendee.isOptional()) {
                            appointment.getOptionalAttendees().add(attendee.getEmail());
                        } else {
                            appointment.getRequiredAttendees().add(attendee.getEmail());
                        }
                    }
                }
            }

            // 提醒
            if (event.getReminder() != null) {
                appointment.setIsReminderSet(true);
                appointment.setReminderMinutesBeforeStart(event.getReminder().getMinutesBefore());
            }

            // 保存（如果有参会者则发送邀请）
            FolderId folderId = calendarId != null ? new FolderId(calendarId) : new FolderId(WellKnownFolderName.Calendar);
            if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
                appointment.save(folderId, SendInvitationsMode.SendToAllAndSaveCopy);
            } else {
                appointment.save(folderId, SendInvitationsMode.SendToNone);
            }

            String eventIdResult = appointment.getId().getUniqueId();
            log.debug("Created event via EWS: {}", eventIdResult);

            return eventIdResult;

        } catch (Exception e) {
            log.error("Failed to create event via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create event via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public void updateEvent(EmailAccount account, String calendarId, String eventId, CalendarEvent event) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            Appointment appointment = Appointment.bind(service, new ItemId(eventId));

            if (event.getSubject() != null) {
                appointment.setSubject(event.getSubject());
            }
            if (event.getDescription() != null) {
                appointment.setBody(new MessageBody(event.getDescription()));
            }
            if (event.getLocation() != null) {
                appointment.setLocation(event.getLocation());
            }
            if (event.getStartTime() != null) {
                appointment.setStart(Date.from(event.getStartTime()));
            }
            if (event.getEndTime() != null) {
                appointment.setEnd(Date.from(event.getEndTime()));
            }

            appointment.update(ConflictResolutionMode.AlwaysOverwrite,
                    SendInvitationsOrCancellationsMode.SendToNone);

            log.debug("Updated event via EWS: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to update event {} via EWS: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to update event via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public void deleteEvent(EmailAccount account, String calendarId, String eventId) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            Appointment appointment = Appointment.bind(service, new ItemId(eventId));
            appointment.delete(DeleteMode.MoveToDeletedItems, SendCancellationsMode.SendToNone);

            log.debug("Deleted event via EWS: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to delete event {} via EWS: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete event via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public long getEventCount(EmailAccount account, String calendarId) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);
            FolderId folderId = calendarId != null ? new FolderId(calendarId) : new FolderId(WellKnownFolderName.Calendar);
            Folder folder = Folder.bind(service, folderId);
            return folder.getTotalCount();
        } catch (Exception e) {
            log.error("Failed to get event count via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get event count via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String calendarId, String syncToken) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            FolderId folderId = calendarId != null ? new FolderId(calendarId) : new FolderId(WellKnownFolderName.Calendar);

            ChangeCollection<ItemChange> changes = service.syncFolderItems(
                    folderId,
                    new PropertySet(BasePropertySet.FirstClassProperties),
                    null,
                    DEFAULT_PAGE_SIZE,
                    SyncFolderItemsScope.NormalItems,
                    syncToken
            );

            List<CalendarEvent> added = new ArrayList<>();
            List<CalendarEvent> modified = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            for (ItemChange change : changes) {
                switch (change.getChangeType()) {
                    case Create:
                        CalendarEvent addedEvent = getEvent(account, calendarId, change.getItemId().getUniqueId());
                        added.add(addedEvent);
                        break;
                    case Update:
                        CalendarEvent modifiedEvent = getEvent(account, calendarId, change.getItemId().getUniqueId());
                        modified.add(modifiedEvent);
                        break;
                    case Delete:
                        deletedIds.add(change.getItemId().getUniqueId());
                        break;
                }
            }

            String newSyncToken = changes.getSyncState();

            return new IncrementalChanges(added, modified, deletedIds, newSyncToken);

        } catch (Exception e) {
            log.error("Failed to get incremental changes via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get incremental changes via EWS", e);
        } finally {
            closeService(service);
        }
    }

    private ExchangeService createExchangeService(EmailAccount account) throws Exception {
        ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
        String accessToken = encryptionService.decrypt(account.getAccessTokenEncrypted());
        service.getHttpHeaders().put("Authorization", "Bearer " + accessToken);
        service.setUrl(new URI(EWS_URL));
        return service;
    }

    private void closeService(ExchangeService service) {
        if (service != null) {
            try {
                service.close();
            } catch (Exception e) {
                log.warn("Failed to close ExchangeService: {}", e.getMessage());
            }
        }
    }

    private CalendarEvent convertToCalendarEvent(Appointment appointment, String calendarId) throws Exception {
        CalendarEvent.CalendarEventBuilder builder = CalendarEvent.builder()
                .id(appointment.getId().getUniqueId())
                .calendarId(calendarId)
                .subject(appointment.getSubject())
                .location(appointment.getLocation())
                .isAllDay(appointment.getIsAllDayEvent());

        // 时间
        if (appointment.getStart() != null) {
            builder.startTime(appointment.getStart().toInstant());
        }
        if (appointment.getEnd() != null) {
            builder.endTime(appointment.getEnd().toInstant());
        }

        // 描述
        if (appointment.getBody() != null) {
            builder.description(appointment.getBody().toString());
        }

        // 组织者
        if (appointment.getOrganizer() != null) {
            builder.organizer(appointment.getOrganizer().getAddress());
        }

        // 参会者
        List<CalendarEvent.Attendee> attendees = new ArrayList<>();
        addAttendees(attendees, appointment.getRequiredAttendees(), false);
        addAttendees(attendees, appointment.getOptionalAttendees(), true);
        builder.attendees(attendees);

        // 提醒
        if (appointment.getIsReminderSet()) {
            builder.reminder(CalendarEvent.Reminder.builder()
                    .minutesBefore(appointment.getReminderMinutesBeforeStart())
                    .method("popup")
                    .build());
        }

        // 状态
        if (appointment.getIsCancelled()) {
            builder.status("cancelled");
            builder.isCancelled(true);
        } else {
            builder.status("confirmed");
        }

        return builder.build();
    }

    private void addAttendees(List<CalendarEvent.Attendee> attendees, AttendeeCollection collection, boolean isOptional) {
        if (collection == null) return;
        for (microsoft.exchange.webservices.data.property.complex.Attendee ewsAttendee : collection) {
            String responseStatus = mapResponseStatus(ewsAttendee.getResponseType());
            attendees.add(CalendarEvent.Attendee.builder()
                    .email(ewsAttendee.getAddress())
                    .displayName(ewsAttendee.getName())
                    .responseStatus(responseStatus)
                    .isOptional(isOptional)
                    .isOrganizer(false)
                    .build());
        }
    }

    private String mapResponseStatus(microsoft.exchange.webservices.data.core.enumeration.property.MeetingResponseType responseType) {
        if (responseType == null) return "needsAction";
        return switch (responseType) {
            case Accept -> "accepted";
            case Decline -> "declined";
            case Tentative -> "tentative";
            default -> "needsAction";
        };
    }
}
