package com.migratehero.service.connector.ews;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.BodyType;
import microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey;
import microsoft.exchange.webservices.data.core.enumeration.property.PhysicalAddressKey;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.folder.ContactsFolder;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Contact;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.core.service.schema.ContactSchema;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.property.complex.EmailAddress;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.complex.MimeContent;
import microsoft.exchange.webservices.data.property.complex.PhoneNumberDictionary;
import microsoft.exchange.webservices.data.property.complex.PhysicalAddressDictionary;
import microsoft.exchange.webservices.data.property.complex.PhysicalAddressEntry;
import microsoft.exchange.webservices.data.search.FindFoldersResults;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.FolderView;
import microsoft.exchange.webservices.data.search.ItemView;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MVP 专用的简化 EWS 连接器 - 使用用户名密码认证
 */
@Slf4j
@Component
public class MvpEwsConnector {

    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 测试 EWS 连接
     */
    public boolean testConnection(String ewsUrl, String email, String password) {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);
            Folder inbox = Folder.bind(service, WellKnownFolderName.Inbox);
            log.info("EWS connection test successful. Inbox has {} items", inbox.getTotalCount());
            return true;
        } catch (Exception e) {
            log.error("EWS connection test failed: {}", e.getMessage());
            return false;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取所有邮件文件夹
     */
    public List<FolderInfo> listFolders(String ewsUrl, String email, String password) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);
            List<FolderInfo> folders = new ArrayList<>();

            // 获取根文件夹
            FolderView view = new FolderView(100);
            FindFoldersResults results = service.findFolders(WellKnownFolderName.MsgFolderRoot, view);

            for (Folder folder : results.getFolders()) {
                addFolderRecursive(service, folder, folders, "");
            }

            return folders;
        } finally {
            closeService(service);
        }
    }

    private void addFolderRecursive(ExchangeService service, Folder folder, List<FolderInfo> folders, String parentPath) {
        try {
            String folderPath = parentPath.isEmpty() ? folder.getDisplayName() : parentPath + "/" + folder.getDisplayName();

            FolderInfo info = new FolderInfo();
            info.setId(folder.getId().getUniqueId());
            info.setName(folder.getDisplayName());
            info.setPath(folderPath);
            info.setTotalCount(folder.getTotalCount());
            folders.add(info);

            // 递归获取子文件夹
            FolderView view = new FolderView(100);
            FindFoldersResults subFolders = service.findFolders(folder.getId(), view);
            for (Folder subFolder : subFolders.getFolders()) {
                addFolderRecursive(service, subFolder, folders, folderPath);
            }
        } catch (Exception e) {
            log.warn("Failed to process folder: {}", e.getMessage());
        }
    }

    /**
     * 获取文件夹中的邮件列表（分页）
     */
    public EmailListResult listEmails(String ewsUrl, String email, String password,
                                      String folderId, int offset, int pageSize) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            ItemView view = new ItemView(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE, offset);
            view.getOrderBy().add(ItemSchema.DateTimeReceived, SortDirection.Descending);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FolderId folderIdObj = new FolderId(folderId);
            FindItemsResults<Item> results = service.findItems(folderIdObj, view);

            List<EmailInfo> emails = new ArrayList<>();
            for (Item item : results.getItems()) {
                if (item instanceof microsoft.exchange.webservices.data.core.service.item.EmailMessage) {
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                            (microsoft.exchange.webservices.data.core.service.item.EmailMessage) item;

                    EmailInfo info = new EmailInfo();
                    info.setId(msg.getId().getUniqueId());
                    info.setSubject(msg.getSubject());
                    info.setFromAddress(msg.getFrom() != null ? msg.getFrom().getAddress() : null);
                    info.setReceivedDate(msg.getDateTimeReceived() != null ?
                            msg.getDateTimeReceived().toInstant() : null);
                    info.setSize(msg.getSize());
                    info.setRead(msg.getIsRead());
                    emails.add(info);
                }
            }

            Folder folder = Folder.bind(service, folderIdObj);

            EmailListResult result = new EmailListResult();
            result.setEmails(emails);
            result.setTotalCount(folder.getTotalCount());
            result.setHasMore(results.isMoreAvailable());

            return result;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取邮件的原始 MIME 内容（用于迁移）
     */
    public byte[] getEmailMimeContent(String ewsUrl, String email, String password, String emailId) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            PropertySet propSet = new PropertySet(BasePropertySet.FirstClassProperties);
            propSet.setRequestedBodyType(BodyType.Text);

            microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage.bind(
                            service,
                            new ItemId(emailId),
                            new PropertySet(BasePropertySet.IdOnly, ItemSchema.MimeContent)
                    );

            MimeContent mimeContent = msg.getMimeContent();
            if (mimeContent != null) {
                return mimeContent.getContent();
            }
            return null;
        } finally {
            closeService(service);
        }
    }

    /**
     * 批量获取邮件 MIME 内容
     */
    public List<EmailMimeData> getEmailsMimeContent(String ewsUrl, String email, String password,
                                                     List<String> emailIds) throws Exception {
        List<EmailMimeData> results = new ArrayList<>();
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            for (String emailId : emailIds) {
                try {
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                            microsoft.exchange.webservices.data.core.service.item.EmailMessage.bind(
                                    service,
                                    new ItemId(emailId),
                                    new PropertySet(BasePropertySet.FirstClassProperties, ItemSchema.MimeContent)
                            );

                    EmailMimeData data = new EmailMimeData();
                    data.setEmailId(emailId);
                    data.setSubject(msg.getSubject());
                    data.setFromAddress(msg.getFrom() != null ? msg.getFrom().getAddress() : null);
                    data.setReceivedDate(msg.getDateTimeReceived() != null ?
                            msg.getDateTimeReceived().toInstant() : null);

                    MimeContent mimeContent = msg.getMimeContent();
                    if (mimeContent != null) {
                        data.setMimeContent(mimeContent.getContent());
                        data.setSize((long) mimeContent.getContent().length);
                    }
                    results.add(data);
                } catch (Exception e) {
                    log.warn("Failed to get MIME content for email {}: {}", emailId, e.getMessage());
                    EmailMimeData data = new EmailMimeData();
                    data.setEmailId(emailId);
                    data.setError(e.getMessage());
                    results.add(data);
                }
            }
            return results;
        } finally {
            closeService(service);
        }
    }

    // ==================== 日历相关方法 ====================

    /**
     * 获取日历信息
     */
    public CalendarInfo getCalendarInfo(String ewsUrl, String email, String password) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);
            CalendarFolder calendar = CalendarFolder.bind(service, WellKnownFolderName.Calendar);

            CalendarInfo info = new CalendarInfo();
            info.setId(calendar.getId().getUniqueId());
            info.setName(calendar.getDisplayName());
            info.setTotalCount(calendar.getTotalCount());
            return info;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取日历事件列表（分页）
     */
    public CalendarEventListResult listCalendarEvents(String ewsUrl, String email, String password,
                                                        int offset, int pageSize) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            ItemView view = new ItemView(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE, offset);
            view.getOrderBy().add(AppointmentSchema.Start, SortDirection.Descending);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FindItemsResults<Item> results = service.findItems(WellKnownFolderName.Calendar, view);

            List<CalendarEventInfo> events = new ArrayList<>();
            for (Item item : results.getItems()) {
                if (item instanceof Appointment) {
                    Appointment apt = (Appointment) item;
                    CalendarEventInfo info = new CalendarEventInfo();
                    info.setId(apt.getId().getUniqueId());
                    info.setSubject(apt.getSubject());
                    info.setLocation(apt.getLocation());
                    info.setStartTime(apt.getStart() != null ? apt.getStart().toInstant() : null);
                    info.setEndTime(apt.getEnd() != null ? apt.getEnd().toInstant() : null);
                    info.setAllDay(apt.getIsAllDayEvent());
                    info.setOrganizer(apt.getOrganizer() != null ? apt.getOrganizer().getAddress() : null);
                    events.add(info);
                }
            }

            CalendarFolder calendar = CalendarFolder.bind(service, WellKnownFolderName.Calendar);

            CalendarEventListResult result = new CalendarEventListResult();
            result.setEvents(events);
            result.setTotalCount(calendar.getTotalCount());
            result.setHasMore(results.isMoreAvailable());

            return result;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取单个日历事件详情
     */
    public CalendarEventDetail getCalendarEventDetail(String ewsUrl, String email, String password,
                                                        String eventId) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            Appointment apt = Appointment.bind(
                    service,
                    new ItemId(eventId),
                    new PropertySet(BasePropertySet.FirstClassProperties)
            );

            CalendarEventDetail detail = new CalendarEventDetail();
            detail.setId(apt.getId().getUniqueId());
            detail.setSubject(apt.getSubject());
            detail.setLocation(apt.getLocation());
            detail.setStartTime(apt.getStart() != null ? apt.getStart().toInstant() : null);
            detail.setEndTime(apt.getEnd() != null ? apt.getEnd().toInstant() : null);
            detail.setAllDay(apt.getIsAllDayEvent());
            detail.setOrganizer(apt.getOrganizer() != null ? apt.getOrganizer().getAddress() : null);
            detail.setDescription(apt.getBody() != null ? apt.getBody().toString() : null);
            detail.setRecurring(apt.getIsRecurring());
            detail.setCancelled(apt.getIsCancelled());

            // 获取参与者
            List<String> attendees = new ArrayList<>();
            if (apt.getRequiredAttendees() != null) {
                for (var attendee : apt.getRequiredAttendees()) {
                    attendees.add(attendee.getAddress());
                }
            }
            if (apt.getOptionalAttendees() != null) {
                for (var attendee : apt.getOptionalAttendees()) {
                    attendees.add(attendee.getAddress());
                }
            }
            detail.setAttendees(attendees);

            // 提醒
            if (apt.getIsReminderSet()) {
                detail.setReminderMinutes(apt.getReminderMinutesBeforeStart());
            }

            return detail;
        } finally {
            closeService(service);
        }
    }

    // ==================== 联系人相关方法 ====================

    /**
     * 获取联系人文件夹信息
     */
    public ContactFolderInfo getContactFolderInfo(String ewsUrl, String email, String password) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);
            ContactsFolder contacts = ContactsFolder.bind(service, WellKnownFolderName.Contacts);

            ContactFolderInfo info = new ContactFolderInfo();
            info.setId(contacts.getId().getUniqueId());
            info.setName(contacts.getDisplayName());
            info.setTotalCount(contacts.getTotalCount());
            return info;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取联系人列表（分页）
     */
    public ContactListResult listContacts(String ewsUrl, String email, String password,
                                           int offset, int pageSize) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            ItemView view = new ItemView(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE, offset);
            view.getOrderBy().add(ContactSchema.DisplayName, SortDirection.Ascending);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FindItemsResults<Item> results = service.findItems(WellKnownFolderName.Contacts, view);

            List<ContactInfo> contacts = new ArrayList<>();
            for (Item item : results.getItems()) {
                if (item instanceof Contact) {
                    Contact contact = (Contact) item;
                    ContactInfo info = new ContactInfo();
                    info.setId(contact.getId().getUniqueId());
                    info.setDisplayName(contact.getDisplayName());
                    info.setFirstName(contact.getGivenName());
                    info.setLastName(contact.getSurname());
                    info.setCompany(contact.getCompanyName());
                    info.setJobTitle(contact.getJobTitle());
                    contacts.add(info);
                }
            }

            ContactsFolder folder = ContactsFolder.bind(service, WellKnownFolderName.Contacts);

            ContactListResult result = new ContactListResult();
            result.setContacts(contacts);
            result.setTotalCount(folder.getTotalCount());
            result.setHasMore(results.isMoreAvailable());

            return result;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取单个联系人详情
     */
    public ContactDetail getContactDetail(String ewsUrl, String email, String password,
                                           String contactId) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            Contact contact = Contact.bind(
                    service,
                    new ItemId(contactId),
                    new PropertySet(BasePropertySet.FirstClassProperties)
            );

            ContactDetail detail = new ContactDetail();
            detail.setId(contact.getId().getUniqueId());
            detail.setDisplayName(contact.getDisplayName());
            detail.setFirstName(contact.getGivenName());
            detail.setLastName(contact.getSurname());
            detail.setMiddleName(contact.getMiddleName());
            detail.setCompany(contact.getCompanyName());
            detail.setJobTitle(contact.getJobTitle());
            detail.setDepartment(contact.getDepartment());
            detail.setNotes(contact.getBody() != null ? contact.getBody().toString() : null);

            // 邮箱地址
            List<String> emailAddresses = new ArrayList<>();
            microsoft.exchange.webservices.data.property.complex.EmailAddressDictionary emails = contact.getEmailAddresses();
            if (emails != null) {
                try {
                    for (microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey key :
                            microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey.values()) {
                        try {
                            EmailAddress addr = emails.getEmailAddress(key);
                            if (addr != null && addr.getAddress() != null) {
                                emailAddresses.add(addr.getAddress());
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
            detail.setEmailAddresses(emailAddresses);

            // 电话号码
            List<String> phoneNumbers = new ArrayList<>();
            PhoneNumberDictionary phones = contact.getPhoneNumbers();
            if (phones != null) {
                try {
                    String mobile = phones.getPhoneNumber(PhoneNumberKey.MobilePhone);
                    if (mobile != null) phoneNumbers.add("Mobile: " + mobile);
                } catch (Exception ignored) {}
                try {
                    String business = phones.getPhoneNumber(PhoneNumberKey.BusinessPhone);
                    if (business != null) phoneNumbers.add("Business: " + business);
                } catch (Exception ignored) {}
                try {
                    String home = phones.getPhoneNumber(PhoneNumberKey.HomePhone);
                    if (home != null) phoneNumbers.add("Home: " + home);
                } catch (Exception ignored) {}
            }
            detail.setPhoneNumbers(phoneNumbers);

            // 地址
            PhysicalAddressDictionary addresses = contact.getPhysicalAddresses();
            if (addresses != null) {
                try {
                    PhysicalAddressEntry business = addresses.getPhysicalAddress(PhysicalAddressKey.Business);
                    if (business != null) {
                        detail.setBusinessAddress(formatAddress(business));
                    }
                } catch (Exception ignored) {}
                try {
                    PhysicalAddressEntry home = addresses.getPhysicalAddress(PhysicalAddressKey.Home);
                    if (home != null) {
                        detail.setHomeAddress(formatAddress(home));
                    }
                } catch (Exception ignored) {}
            }

            return detail;
        } finally {
            closeService(service);
        }
    }

    private String formatAddress(PhysicalAddressEntry entry) {
        StringBuilder sb = new StringBuilder();
        try {
            if (entry.getStreet() != null) sb.append(entry.getStreet()).append(", ");
            if (entry.getCity() != null) sb.append(entry.getCity()).append(", ");
            if (entry.getState() != null) sb.append(entry.getState()).append(" ");
            if (entry.getPostalCode() != null) sb.append(entry.getPostalCode()).append(", ");
            if (entry.getCountryOrRegion() != null) sb.append(entry.getCountryOrRegion());
        } catch (Exception ignored) {}
        return sb.toString().replaceAll(", $", "");
    }

    private ExchangeService createExchangeService(String ewsUrl, String email, String password) throws Exception {
        ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
        service.setCredentials(new WebCredentials(email, password));
        service.setUrl(new URI(ewsUrl));
        service.setTraceEnabled(false);
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

    // === DTOs ===

    @Data
    public static class FolderInfo {
        private String id;
        private String name;
        private String path;
        private int totalCount;
    }

    @Data
    public static class EmailInfo {
        private String id;
        private String subject;
        private String fromAddress;
        private Instant receivedDate;
        private int size;
        private boolean read;
    }

    @Data
    public static class EmailListResult {
        private List<EmailInfo> emails;
        private int totalCount;
        private boolean hasMore;
    }

    @Data
    public static class EmailMimeData {
        private String emailId;
        private String subject;
        private String fromAddress;
        private Instant receivedDate;
        private Long size;
        private byte[] mimeContent;
        private String error;
    }

    // === 日历相关 DTOs ===

    @Data
    public static class CalendarInfo {
        private String id;
        private String name;
        private int totalCount;
    }

    @Data
    public static class CalendarEventInfo {
        private String id;
        private String subject;
        private String location;
        private Instant startTime;
        private Instant endTime;
        private boolean allDay;
        private String organizer;
    }

    @Data
    public static class CalendarEventListResult {
        private List<CalendarEventInfo> events;
        private int totalCount;
        private boolean hasMore;
    }

    @Data
    public static class CalendarEventDetail {
        private String id;
        private String subject;
        private String location;
        private Instant startTime;
        private Instant endTime;
        private boolean allDay;
        private String organizer;
        private String description;
        private boolean recurring;
        private boolean cancelled;
        private List<String> attendees;
        private Integer reminderMinutes;
    }

    // === 联系人相关 DTOs ===

    @Data
    public static class ContactFolderInfo {
        private String id;
        private String name;
        private int totalCount;
    }

    @Data
    public static class ContactInfo {
        private String id;
        private String displayName;
        private String firstName;
        private String lastName;
        private String company;
        private String jobTitle;
    }

    @Data
    public static class ContactListResult {
        private List<ContactInfo> contacts;
        private int totalCount;
        private boolean hasMore;
    }

    @Data
    public static class ContactDetail {
        private String id;
        private String displayName;
        private String firstName;
        private String lastName;
        private String middleName;
        private String company;
        private String jobTitle;
        private String department;
        private String notes;
        private List<String> emailAddresses;
        private List<String> phoneNumbers;
        private String businessAddress;
        private String homeAddress;
    }
}
