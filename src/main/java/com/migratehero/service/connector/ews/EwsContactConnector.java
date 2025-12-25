package com.migratehero.service.connector.ews;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.Contact;
import com.migratehero.service.EncryptionService;
import com.migratehero.service.connector.ContactConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.PhysicalAddressKey;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.enumeration.service.ConflictResolutionMode;
import microsoft.exchange.webservices.data.core.enumeration.service.DeleteMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SyncFolderItemsScope;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.ContactSchema;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.property.complex.EmailAddressCollection;
import microsoft.exchange.webservices.data.property.complex.EmailAddressDictionary;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.complex.PhoneNumberDictionary;
import microsoft.exchange.webservices.data.property.complex.PhysicalAddressDictionary;
import microsoft.exchange.webservices.data.property.complex.PhysicalAddressEntry;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.sync.ChangeCollection;
import microsoft.exchange.webservices.data.sync.ItemChange;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * EWS 联系人连接器 - 使用 Exchange Web Services 访问 Microsoft 联系人
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EwsContactConnector implements ContactConnector {

    private final EncryptionService encryptionService;

    private static final String EWS_URL = "https://outlook.office365.com/EWS/Exchange.asmx";
    private static final int DEFAULT_PAGE_SIZE = 100;

    @Override
    public ContactListResult listContacts(EmailAccount account, String pageToken, int maxResults) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            int offset = pageToken != null ? Integer.parseInt(pageToken) : 0;
            int pageSize = maxResults > 0 ? Math.min(maxResults, DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

            ItemView view = new ItemView(pageSize, offset);
            view.getOrderBy().add(ContactSchema.DisplayName, SortDirection.Ascending);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FindItemsResults<Item> findResults = service.findItems(WellKnownFolderName.Contacts, view);

            List<Contact> contacts = new ArrayList<>();
            for (Item item : findResults) {
                if (item instanceof microsoft.exchange.webservices.data.core.service.item.Contact) {
                    microsoft.exchange.webservices.data.core.service.item.Contact ewsContact =
                            (microsoft.exchange.webservices.data.core.service.item.Contact) item;
                    contacts.add(convertToContact(ewsContact));
                }
            }

            String nextPageToken = null;
            if (findResults.isMoreAvailable()) {
                nextPageToken = String.valueOf(offset + pageSize);
            }

            Folder contactsFolder = Folder.bind(service, WellKnownFolderName.Contacts);
            long totalCount = contactsFolder.getTotalCount();

            return new ContactListResult(contacts, nextPageToken, null, totalCount);

        } catch (Exception e) {
            log.error("Failed to list contacts via EWS for account {}: {}", account.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to list contacts via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public Contact getContact(EmailAccount account, String contactId) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            microsoft.exchange.webservices.data.core.service.item.Contact ewsContact =
                    microsoft.exchange.webservices.data.core.service.item.Contact.bind(
                            service,
                            new ItemId(contactId),
                            new PropertySet(BasePropertySet.FirstClassProperties)
                    );

            return convertToContact(ewsContact);

        } catch (Exception e) {
            log.error("Failed to get contact {} via EWS: {}", contactId, e.getMessage(), e);
            throw new RuntimeException("Failed to get contact via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public String createContact(EmailAccount account, Contact contact) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            microsoft.exchange.webservices.data.core.service.item.Contact ewsContact =
                    new microsoft.exchange.webservices.data.core.service.item.Contact(service);

            // 基本信息
            ewsContact.setGivenName(contact.getGivenName());
            ewsContact.setSurname(contact.getFamilyName());
            ewsContact.setMiddleName(contact.getMiddleName());
            ewsContact.setDisplayName(contact.getDisplayName());
            ewsContact.setNickName(contact.getNickname());
            ewsContact.setCompanyName(contact.getCompany());
            ewsContact.setJobTitle(contact.getJobTitle());
            ewsContact.setDepartment(contact.getDepartment());

            if (contact.getNotes() != null) {
                ewsContact.setBody(new microsoft.exchange.webservices.data.property.complex.MessageBody(contact.getNotes()));
            }

            // 邮箱
            if (contact.getEmailAddresses() != null && !contact.getEmailAddresses().isEmpty()) {
                EmailAddressDictionary emails = ewsContact.getEmailAddresses();
                for (int i = 0; i < Math.min(contact.getEmailAddresses().size(), 3); i++) {
                    Contact.EmailAddress email = contact.getEmailAddresses().get(i);
                    switch (i) {
                        case 0:
                            emails.setEmailAddress(microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey.EmailAddress1,
                                    new microsoft.exchange.webservices.data.property.complex.EmailAddress(email.getEmail()));
                            break;
                        case 1:
                            emails.setEmailAddress(microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey.EmailAddress2,
                                    new microsoft.exchange.webservices.data.property.complex.EmailAddress(email.getEmail()));
                            break;
                        case 2:
                            emails.setEmailAddress(microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey.EmailAddress3,
                                    new microsoft.exchange.webservices.data.property.complex.EmailAddress(email.getEmail()));
                            break;
                    }
                }
            }

            // 电话
            if (contact.getPhoneNumbers() != null) {
                PhoneNumberDictionary phones = ewsContact.getPhoneNumbers();
                for (Contact.PhoneNumber phone : contact.getPhoneNumbers()) {
                    microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey key =
                            mapPhoneType(phone.getType());
                    phones.setPhoneNumber(key, phone.getNumber());
                }
            }

            // 地址
            if (contact.getAddresses() != null) {
                PhysicalAddressDictionary addresses = ewsContact.getPhysicalAddresses();
                for (Contact.Address address : contact.getAddresses()) {
                    PhysicalAddressKey key = mapAddressType(address.getType());
                    PhysicalAddressEntry entry = new PhysicalAddressEntry();
                    entry.setStreet(address.getStreet());
                    entry.setCity(address.getCity());
                    entry.setState(address.getState());
                    entry.setPostalCode(address.getPostalCode());
                    entry.setCountryOrRegion(address.getCountry());
                    addresses.setPhysicalAddress(key, entry);
                }
            }

            ewsContact.save(WellKnownFolderName.Contacts);

            String contactIdResult = ewsContact.getId().getUniqueId();
            log.debug("Created contact via EWS: {}", contactIdResult);

            return contactIdResult;

        } catch (Exception e) {
            log.error("Failed to create contact via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create contact via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public void updateContact(EmailAccount account, String contactId, Contact contact) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            microsoft.exchange.webservices.data.core.service.item.Contact ewsContact =
                    microsoft.exchange.webservices.data.core.service.item.Contact.bind(
                            service,
                            new ItemId(contactId)
                    );

            // 更新字段
            if (contact.getGivenName() != null) ewsContact.setGivenName(contact.getGivenName());
            if (contact.getFamilyName() != null) ewsContact.setSurname(contact.getFamilyName());
            if (contact.getDisplayName() != null) ewsContact.setDisplayName(contact.getDisplayName());
            if (contact.getCompany() != null) ewsContact.setCompanyName(contact.getCompany());
            if (contact.getJobTitle() != null) ewsContact.setJobTitle(contact.getJobTitle());

            ewsContact.update(ConflictResolutionMode.AlwaysOverwrite);

            log.debug("Updated contact via EWS: {}", contactId);

        } catch (Exception e) {
            log.error("Failed to update contact {} via EWS: {}", contactId, e.getMessage(), e);
            throw new RuntimeException("Failed to update contact via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public void deleteContact(EmailAccount account, String contactId) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            microsoft.exchange.webservices.data.core.service.item.Contact ewsContact =
                    microsoft.exchange.webservices.data.core.service.item.Contact.bind(
                            service,
                            new ItemId(contactId)
                    );

            ewsContact.delete(DeleteMode.MoveToDeletedItems);

            log.debug("Deleted contact via EWS: {}", contactId);

        } catch (Exception e) {
            log.error("Failed to delete contact {} via EWS: {}", contactId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete contact via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public long getContactCount(EmailAccount account) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);
            Folder contactsFolder = Folder.bind(service, WellKnownFolderName.Contacts);
            return contactsFolder.getTotalCount();
        } catch (Exception e) {
            log.error("Failed to get contact count via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get contact count via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String syncToken) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            FolderId contactsFolderId = new FolderId(WellKnownFolderName.Contacts);

            ChangeCollection<ItemChange> changes = service.syncFolderItems(
                    contactsFolderId,
                    new PropertySet(BasePropertySet.FirstClassProperties),
                    null,
                    DEFAULT_PAGE_SIZE,
                    SyncFolderItemsScope.NormalItems,
                    syncToken
            );

            List<Contact> added = new ArrayList<>();
            List<Contact> modified = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            for (ItemChange change : changes) {
                switch (change.getChangeType()) {
                    case Create:
                        Contact addedContact = getContact(account, change.getItemId().getUniqueId());
                        added.add(addedContact);
                        break;
                    case Update:
                        Contact modifiedContact = getContact(account, change.getItemId().getUniqueId());
                        modified.add(modifiedContact);
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

    private Contact convertToContact(
            microsoft.exchange.webservices.data.core.service.item.Contact ewsContact) throws Exception {

        Contact.ContactBuilder builder = Contact.builder()
                .id(ewsContact.getId().getUniqueId())
                .displayName(ewsContact.getDisplayName())
                .givenName(ewsContact.getGivenName())
                .middleName(ewsContact.getMiddleName())
                .familyName(ewsContact.getSurname())
                .nickname(ewsContact.getNickName())
                .company(ewsContact.getCompanyName())
                .jobTitle(ewsContact.getJobTitle())
                .department(ewsContact.getDepartment());

        // 邮箱
        List<Contact.EmailAddress> emails = new ArrayList<>();
        EmailAddressDictionary emailDict = ewsContact.getEmailAddresses();
        if (emailDict != null) {
            try {
                microsoft.exchange.webservices.data.property.complex.EmailAddress email1 =
                        emailDict.getEmailAddress(microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey.EmailAddress1);
                if (email1 != null && email1.getAddress() != null) {
                    emails.add(Contact.EmailAddress.builder()
                            .email(email1.getAddress())
                            .type("work")
                            .primary(true)
                            .build());
                }
            } catch (Exception ignored) {}

            try {
                microsoft.exchange.webservices.data.property.complex.EmailAddress email2 =
                        emailDict.getEmailAddress(microsoft.exchange.webservices.data.core.enumeration.property.EmailAddressKey.EmailAddress2);
                if (email2 != null && email2.getAddress() != null) {
                    emails.add(Contact.EmailAddress.builder()
                            .email(email2.getAddress())
                            .type("home")
                            .primary(false)
                            .build());
                }
            } catch (Exception ignored) {}
        }
        builder.emailAddresses(emails);

        // 电话
        List<Contact.PhoneNumber> phones = new ArrayList<>();
        PhoneNumberDictionary phoneDict = ewsContact.getPhoneNumbers();
        if (phoneDict != null) {
            addPhoneIfPresent(phones, phoneDict,
                    microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.MobilePhone, "mobile");
            addPhoneIfPresent(phones, phoneDict,
                    microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.BusinessPhone, "work");
            addPhoneIfPresent(phones, phoneDict,
                    microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.HomePhone, "home");
        }
        builder.phoneNumbers(phones);

        // 地址
        List<Contact.Address> addresses = new ArrayList<>();
        PhysicalAddressDictionary addrDict = ewsContact.getPhysicalAddresses();
        if (addrDict != null) {
            addAddressIfPresent(addresses, addrDict, PhysicalAddressKey.Business, "work");
            addAddressIfPresent(addresses, addrDict, PhysicalAddressKey.Home, "home");
        }
        builder.addresses(addresses);

        return builder.build();
    }

    private void addPhoneIfPresent(List<Contact.PhoneNumber> phones, PhoneNumberDictionary dict,
                                   microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey key, String type) {
        try {
            String number = dict.getPhoneNumber(key);
            if (number != null && !number.isEmpty()) {
                phones.add(Contact.PhoneNumber.builder()
                        .number(number)
                        .type(type)
                        .primary(phones.isEmpty())
                        .build());
            }
        } catch (Exception ignored) {}
    }

    private void addAddressIfPresent(List<Contact.Address> addresses, PhysicalAddressDictionary dict,
                                     PhysicalAddressKey key, String type) {
        try {
            PhysicalAddressEntry entry = dict.getPhysicalAddress(key);
            if (entry != null) {
                addresses.add(Contact.Address.builder()
                        .street(entry.getStreet())
                        .city(entry.getCity())
                        .state(entry.getState())
                        .postalCode(entry.getPostalCode())
                        .country(entry.getCountryOrRegion())
                        .type(type)
                        .primary(addresses.isEmpty())
                        .build());
            }
        } catch (Exception ignored) {}
    }

    private microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey mapPhoneType(String type) {
        if (type == null) return microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.BusinessPhone;
        return switch (type.toLowerCase()) {
            case "mobile" -> microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.MobilePhone;
            case "home" -> microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.HomePhone;
            case "work", "business" -> microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.BusinessPhone;
            default -> microsoft.exchange.webservices.data.core.enumeration.property.PhoneNumberKey.OtherTelephone;
        };
    }

    private PhysicalAddressKey mapAddressType(String type) {
        if (type == null) return PhysicalAddressKey.Business;
        return switch (type.toLowerCase()) {
            case "home" -> PhysicalAddressKey.Home;
            case "work", "business" -> PhysicalAddressKey.Business;
            default -> PhysicalAddressKey.Other;
        };
    }
}
