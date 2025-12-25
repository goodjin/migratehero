package com.migratehero.service.connector.microsoft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.Contact;
import com.migratehero.service.connector.ContactConnector;
import com.migratehero.service.oauth.MicrosoftOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Microsoft Graph 联系人连接器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicrosoftContactConnector implements ContactConnector {

    private final MicrosoftOAuthService microsoftOAuthService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";

    @Override
    public ContactListResult listContacts(EmailAccount account, String pageToken, int maxResults) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = pageToken != null ? pageToken :
                    GRAPH_API_URL + "/me/contacts?$top=" + maxResults;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            List<Contact> contacts = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    contacts.add(convertToContact(node));
                }
            }

            String nextPageToken = root.has("@odata.nextLink") ?
                    root.get("@odata.nextLink").asText() : null;
            String deltaLink = root.has("@odata.deltaLink") ?
                    root.get("@odata.deltaLink").asText() : null;

            return new ContactListResult(contacts, nextPageToken, deltaLink, contacts.size());
        } catch (Exception e) {
            log.error("Failed to list contacts for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list contacts", e);
        }
    }

    @Override
    public Contact getContact(EmailAccount account, String contactId) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/contacts/" + contactId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode node = objectMapper.readTree(response.getBody());
            return convertToContact(node);
        } catch (Exception e) {
            log.error("Failed to get contact {} for account: {}", contactId, account.getEmail(), e);
            return null;
        }
    }

    @Override
    public String createContact(EmailAccount account, Contact contact) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = convertToMsContact(contact);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GRAPH_API_URL + "/me/contacts",
                    request,
                    String.class);

            JsonNode result = objectMapper.readTree(response.getBody());
            return result.get("id").asText();
        } catch (Exception e) {
            log.error("Failed to create contact for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create contact", e);
        }
    }

    @Override
    public void updateContact(EmailAccount account, String contactId, Contact contact) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = convertToMsContact(contact);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.exchange(
                    GRAPH_API_URL + "/me/contacts/" + contactId,
                    HttpMethod.PATCH,
                    request,
                    String.class);
        } catch (Exception e) {
            log.error("Failed to update contact {} for account: {}", contactId, account.getEmail(), e);
            throw new RuntimeException("Failed to update contact", e);
        }
    }

    @Override
    public void deleteContact(EmailAccount account, String contactId) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            restTemplate.exchange(
                    GRAPH_API_URL + "/me/contacts/" + contactId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class);
        } catch (Exception e) {
            log.error("Failed to delete contact {} for account: {}", contactId, account.getEmail(), e);
            throw new RuntimeException("Failed to delete contact", e);
        }
    }

    @Override
    public long getContactCount(EmailAccount account) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/contacts/$count",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            return Long.parseLong(response.getBody());
        } catch (Exception e) {
            log.error("Failed to get contact count for account: {}", account.getEmail(), e);
            return 0;
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String syncToken) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = syncToken != null ? syncToken :
                    GRAPH_API_URL + "/me/contacts/delta";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            List<Contact> added = new ArrayList<>();
            List<Contact> modified = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    String id = node.get("id").asText();
                    if (node.has("@removed")) {
                        deletedIds.add(id);
                    } else {
                        modified.add(convertToContact(node));
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

    private Contact convertToContact(JsonNode node) {
        Contact.ContactBuilder builder = Contact.builder()
                .id(node.get("id").asText());

        if (node.has("displayName")) {
            builder.displayName(node.get("displayName").asText());
        }
        if (node.has("givenName")) {
            builder.givenName(node.get("givenName").asText());
        }
        if (node.has("middleName")) {
            builder.middleName(node.get("middleName").asText());
        }
        if (node.has("surname")) {
            builder.familyName(node.get("surname").asText());
        }
        if (node.has("nickName")) {
            builder.nickname(node.get("nickName").asText());
        }
        if (node.has("companyName")) {
            builder.company(node.get("companyName").asText());
        }
        if (node.has("jobTitle")) {
            builder.jobTitle(node.get("jobTitle").asText());
        }
        if (node.has("department")) {
            builder.department(node.get("department").asText());
        }
        if (node.has("personalNotes")) {
            builder.notes(node.get("personalNotes").asText());
        }

        // 邮箱
        if (node.has("emailAddresses")) {
            List<Contact.EmailAddress> emails = new ArrayList<>();
            for (JsonNode email : node.get("emailAddresses")) {
                emails.add(Contact.EmailAddress.builder()
                        .email(email.has("address") ? email.get("address").asText() : "")
                        .type(email.has("name") ? email.get("name").asText() : "other")
                        .build());
            }
            builder.emailAddresses(emails);
        }

        // 电话
        if (node.has("mobilePhone") && !node.get("mobilePhone").isNull()) {
            builder.phoneNumbers(List.of(Contact.PhoneNumber.builder()
                    .number(node.get("mobilePhone").asText())
                    .type("mobile")
                    .primary(true)
                    .build()));
        }

        return builder.build();
    }

    private Map<String, Object> convertToMsContact(Contact contact) {
        Map<String, Object> msContact = new HashMap<>();

        if (contact.getGivenName() != null) {
            msContact.put("givenName", contact.getGivenName());
        }
        if (contact.getMiddleName() != null) {
            msContact.put("middleName", contact.getMiddleName());
        }
        if (contact.getFamilyName() != null) {
            msContact.put("surname", contact.getFamilyName());
        }
        if (contact.getNickname() != null) {
            msContact.put("nickName", contact.getNickname());
        }
        if (contact.getCompany() != null) {
            msContact.put("companyName", contact.getCompany());
        }
        if (contact.getJobTitle() != null) {
            msContact.put("jobTitle", contact.getJobTitle());
        }
        if (contact.getDepartment() != null) {
            msContact.put("department", contact.getDepartment());
        }
        if (contact.getNotes() != null) {
            msContact.put("personalNotes", contact.getNotes());
        }

        // 邮箱
        if (contact.getEmailAddresses() != null && !contact.getEmailAddresses().isEmpty()) {
            List<Map<String, String>> emails = new ArrayList<>();
            for (Contact.EmailAddress email : contact.getEmailAddresses()) {
                Map<String, String> e = new HashMap<>();
                e.put("address", email.getEmail());
                e.put("name", email.getType());
                emails.add(e);
            }
            msContact.put("emailAddresses", emails);
        }

        // 手机
        if (contact.getPhoneNumbers() != null && !contact.getPhoneNumbers().isEmpty()) {
            for (Contact.PhoneNumber phone : contact.getPhoneNumbers()) {
                if ("mobile".equalsIgnoreCase(phone.getType())) {
                    msContact.put("mobilePhone", phone.getNumber());
                    break;
                }
            }
        }

        return msContact;
    }
}
