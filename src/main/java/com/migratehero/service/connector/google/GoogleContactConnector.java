package com.migratehero.service.connector.google;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.Contact;
import com.migratehero.service.connector.ContactConnector;
import com.migratehero.service.oauth.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Google People API 联系人连接器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleContactConnector implements ContactConnector {

    private final GoogleOAuthService googleOAuthService;
    private static final String APPLICATION_NAME = "MigrateHero";
    private static final String PERSON_FIELDS = "names,emailAddresses,phoneNumbers,addresses,organizations,birthdays,photos,nicknames,biographies";

    @Override
    public ContactListResult listContacts(EmailAccount account, String pageToken, int maxResults) {
        try {
            PeopleService service = getPeopleService(account);

            ListConnectionsResponse response = service.people().connections()
                    .list("people/me")
                    .setPersonFields(PERSON_FIELDS)
                    .setPageSize(maxResults)
                    .setPageToken(pageToken)
                    .execute();

            List<Person> connections = response.getConnections();
            if (connections == null) {
                return new ContactListResult(Collections.emptyList(), null, response.getNextSyncToken(), 0);
            }

            List<Contact> contacts = connections.stream()
                    .map(this::convertToContact)
                    .collect(Collectors.toList());

            return new ContactListResult(
                    contacts,
                    response.getNextPageToken(),
                    response.getNextSyncToken(),
                    response.getTotalItems() != null ? response.getTotalItems() : contacts.size()
            );
        } catch (IOException e) {
            log.error("Failed to list contacts for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list contacts", e);
        }
    }

    @Override
    public Contact getContact(EmailAccount account, String contactId) {
        try {
            PeopleService service = getPeopleService(account);
            Person person = service.people().get(contactId)
                    .setPersonFields(PERSON_FIELDS)
                    .execute();
            return convertToContact(person);
        } catch (IOException e) {
            log.error("Failed to get contact {} for account: {}", contactId, account.getEmail(), e);
            return null;
        }
    }

    @Override
    public String createContact(EmailAccount account, Contact contact) {
        try {
            PeopleService service = getPeopleService(account);
            Person person = convertToPerson(contact);
            Person created = service.people().createContact(person).execute();
            return created.getResourceName();
        } catch (IOException e) {
            log.error("Failed to create contact for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create contact", e);
        }
    }

    @Override
    public void updateContact(EmailAccount account, String contactId, Contact contact) {
        try {
            PeopleService service = getPeopleService(account);
            Person person = convertToPerson(contact);
            person.setEtag(null);
            service.people().updateContact(contactId, person)
                    .setUpdatePersonFields(PERSON_FIELDS)
                    .execute();
        } catch (IOException e) {
            log.error("Failed to update contact {} for account: {}", contactId, account.getEmail(), e);
            throw new RuntimeException("Failed to update contact", e);
        }
    }

    @Override
    public void deleteContact(EmailAccount account, String contactId) {
        try {
            PeopleService service = getPeopleService(account);
            service.people().deleteContact(contactId).execute();
        } catch (IOException e) {
            log.error("Failed to delete contact {} for account: {}", contactId, account.getEmail(), e);
            throw new RuntimeException("Failed to delete contact", e);
        }
    }

    @Override
    public long getContactCount(EmailAccount account) {
        try {
            PeopleService service = getPeopleService(account);
            ListConnectionsResponse response = service.people().connections()
                    .list("people/me")
                    .setPersonFields("names")
                    .setPageSize(1)
                    .execute();
            return response.getTotalItems() != null ? response.getTotalItems() : 0;
        } catch (IOException e) {
            log.error("Failed to get contact count for account: {}", account.getEmail(), e);
            return 0;
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String syncToken) {
        try {
            PeopleService service = getPeopleService(account);

            ListConnectionsResponse response = service.people().connections()
                    .list("people/me")
                    .setPersonFields(PERSON_FIELDS)
                    .setSyncToken(syncToken)
                    .setRequestSyncToken(true)
                    .execute();

            List<Contact> added = new ArrayList<>();
            List<Contact> modified = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            if (response.getConnections() != null) {
                for (Person person : response.getConnections()) {
                    if (person.getMetadata() != null && Boolean.TRUE.equals(person.getMetadata().getDeleted())) {
                        deletedIds.add(person.getResourceName());
                    } else {
                        // 无法区分新增和修改
                        modified.add(convertToContact(person));
                    }
                }
            }

            return new IncrementalChanges(added, modified, deletedIds, response.getNextSyncToken());
        } catch (IOException e) {
            log.error("Failed to get incremental changes for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to get incremental changes", e);
        }
    }

    private PeopleService getPeopleService(EmailAccount account) {
        String accessToken = googleOAuthService.getAccessToken(account);
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));

        return new PeopleService.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName(APPLICATION_NAME).build();
    }

    private Contact convertToContact(Person person) {
        Contact.ContactBuilder builder = Contact.builder()
                .id(person.getResourceName());

        // 姓名
        if (person.getNames() != null && !person.getNames().isEmpty()) {
            Name name = person.getNames().get(0);
            builder.displayName(name.getDisplayName())
                    .givenName(name.getGivenName())
                    .middleName(name.getMiddleName())
                    .familyName(name.getFamilyName());
        }

        // 昵称
        if (person.getNicknames() != null && !person.getNicknames().isEmpty()) {
            builder.nickname(person.getNicknames().get(0).getValue());
        }

        // 邮箱
        if (person.getEmailAddresses() != null) {
            List<Contact.EmailAddress> emails = person.getEmailAddresses().stream()
                    .map(e -> Contact.EmailAddress.builder()
                            .email(e.getValue())
                            .type(e.getType())
                            .primary(e.getMetadata() != null && Boolean.TRUE.equals(e.getMetadata().getPrimary()))
                            .build())
                    .collect(Collectors.toList());
            builder.emailAddresses(emails);
        }

        // 电话
        if (person.getPhoneNumbers() != null) {
            List<Contact.PhoneNumber> phones = person.getPhoneNumbers().stream()
                    .map(p -> Contact.PhoneNumber.builder()
                            .number(p.getValue())
                            .type(p.getType())
                            .primary(p.getMetadata() != null && Boolean.TRUE.equals(p.getMetadata().getPrimary()))
                            .build())
                    .collect(Collectors.toList());
            builder.phoneNumbers(phones);
        }

        // 地址
        if (person.getAddresses() != null) {
            List<Contact.Address> addresses = person.getAddresses().stream()
                    .map(a -> Contact.Address.builder()
                            .street(a.getStreetAddress())
                            .city(a.getCity())
                            .state(a.getRegion())
                            .postalCode(a.getPostalCode())
                            .country(a.getCountry())
                            .type(a.getType())
                            .build())
                    .collect(Collectors.toList());
            builder.addresses(addresses);
        }

        // 公司
        if (person.getOrganizations() != null && !person.getOrganizations().isEmpty()) {
            Organization org = person.getOrganizations().get(0);
            builder.company(org.getName())
                    .jobTitle(org.getTitle())
                    .department(org.getDepartment());
        }

        // 生日
        if (person.getBirthdays() != null && !person.getBirthdays().isEmpty()) {
            Birthday birthday = person.getBirthdays().get(0);
            if (birthday.getDate() != null) {
                Date date = birthday.getDate();
                if (date.getYear() != null && date.getMonth() != null && date.getDay() != null) {
                    builder.birthday(LocalDate.of(date.getYear(), date.getMonth(), date.getDay()));
                }
            }
        }

        // 照片
        if (person.getPhotos() != null && !person.getPhotos().isEmpty()) {
            builder.photoUrl(person.getPhotos().get(0).getUrl());
        }

        // 备注
        if (person.getBiographies() != null && !person.getBiographies().isEmpty()) {
            builder.notes(person.getBiographies().get(0).getValue());
        }

        return builder.build();
    }

    private Person convertToPerson(Contact contact) {
        Person person = new Person();

        // 姓名
        if (contact.getDisplayName() != null || contact.getGivenName() != null) {
            Name name = new Name()
                    .setGivenName(contact.getGivenName())
                    .setMiddleName(contact.getMiddleName())
                    .setFamilyName(contact.getFamilyName());
            person.setNames(List.of(name));
        }

        // 邮箱
        if (contact.getEmailAddresses() != null && !contact.getEmailAddresses().isEmpty()) {
            List<EmailAddress> emails = contact.getEmailAddresses().stream()
                    .map(e -> new EmailAddress().setValue(e.getEmail()).setType(e.getType()))
                    .collect(Collectors.toList());
            person.setEmailAddresses(emails);
        }

        // 电话
        if (contact.getPhoneNumbers() != null && !contact.getPhoneNumbers().isEmpty()) {
            List<PhoneNumber> phones = contact.getPhoneNumbers().stream()
                    .map(p -> new PhoneNumber().setValue(p.getNumber()).setType(p.getType()))
                    .collect(Collectors.toList());
            person.setPhoneNumbers(phones);
        }

        // 公司
        if (contact.getCompany() != null || contact.getJobTitle() != null) {
            Organization org = new Organization()
                    .setName(contact.getCompany())
                    .setTitle(contact.getJobTitle())
                    .setDepartment(contact.getDepartment());
            person.setOrganizations(List.of(org));
        }

        return person;
    }
}
