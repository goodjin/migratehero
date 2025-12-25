package com.migratehero.service.connector.microsoft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.EmailMessage;
import com.migratehero.service.connector.EmailConnector;
import com.migratehero.service.oauth.MicrosoftOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Microsoft Graph 邮件连接器实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicrosoftEmailConnector implements EmailConnector {

    private final MicrosoftOAuthService microsoftOAuthService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";

    @Override
    public EmailListResult listEmails(EmailAccount account, String pageToken, int maxResults) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = pageToken != null ? pageToken :
                    GRAPH_API_URL + "/me/messages?$top=" + maxResults + "&$orderby=receivedDateTime desc";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            List<EmailMessage> emails = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    emails.add(convertToEmailMessage(node));
                }
            }

            String nextPageToken = root.has("@odata.nextLink") ?
                    root.get("@odata.nextLink").asText() : null;

            String deltaLink = root.has("@odata.deltaLink") ?
                    root.get("@odata.deltaLink").asText() : null;

            return new EmailListResult(emails, nextPageToken, deltaLink, emails.size());
        } catch (Exception e) {
            log.error("Failed to list emails for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list emails", e);
        }
    }

    @Override
    public EmailMessage getEmail(EmailAccount account, String emailId) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/messages/" + emailId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode node = objectMapper.readTree(response.getBody());
            return convertToEmailMessage(node);
        } catch (Exception e) {
            log.error("Failed to get email {} for account: {}", emailId, account.getEmail(), e);
            return null;
        }
    }

    @Override
    public String createEmail(EmailAccount account, EmailMessage email) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> messageBody = buildMessageBody(email);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(messageBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    GRAPH_API_URL + "/me/messages",
                    request,
                    String.class);

            JsonNode result = objectMapper.readTree(response.getBody());
            return result.get("id").asText();
        } catch (Exception e) {
            log.error("Failed to create email for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create email", e);
        }
    }

    @Override
    public String createDraft(EmailAccount account, EmailMessage email) {
        // 在 Microsoft Graph 中，创建消息默认就是草稿
        return createEmail(account, email);
    }

    @Override
    public EmailStats getStats(EmailAccount account) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            // 获取文件夹统计
            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/mailFolders/inbox",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode inbox = objectMapper.readTree(response.getBody());
            long totalCount = inbox.has("totalItemCount") ? inbox.get("totalItemCount").asLong() : 0;
            long unreadCount = inbox.has("unreadItemCount") ? inbox.get("unreadItemCount").asLong() : 0;

            // 获取草稿数
            ResponseEntity<String> draftsResponse = restTemplate.exchange(
                    GRAPH_API_URL + "/me/mailFolders/drafts",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode drafts = objectMapper.readTree(draftsResponse.getBody());
            long draftCount = drafts.has("totalItemCount") ? drafts.get("totalItemCount").asLong() : 0;

            return new EmailStats(totalCount, unreadCount, draftCount);
        } catch (Exception e) {
            log.error("Failed to get email stats for account: {}", account.getEmail(), e);
            return new EmailStats(0, 0, 0);
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String deltaToken) {
        try {
            String accessToken = microsoftOAuthService.getAccessToken(account);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = deltaToken != null ? deltaToken :
                    GRAPH_API_URL + "/me/mailFolders/inbox/messages/delta";

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            List<String> addedIds = new ArrayList<>();
            List<String> modifiedIds = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            if (root.has("value")) {
                for (JsonNode node : root.get("value")) {
                    String id = node.get("id").asText();
                    if (node.has("@removed")) {
                        deletedIds.add(id);
                    } else {
                        // 无法区分新增和修改，都视为修改
                        modifiedIds.add(id);
                    }
                }
            }

            String newDeltaToken = root.has("@odata.deltaLink") ?
                    root.get("@odata.deltaLink").asText() : deltaToken;

            return new IncrementalChanges(addedIds, modifiedIds, deletedIds, newDeltaToken);
        } catch (Exception e) {
            log.error("Failed to get incremental changes for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to get incremental changes", e);
        }
    }

    private EmailMessage convertToEmailMessage(JsonNode node) {
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .id(node.get("id").asText())
                .subject(node.has("subject") ? node.get("subject").asText() : "")
                .isRead(node.has("isRead") && node.get("isRead").asBoolean())
                .isDraft(node.has("isDraft") && node.get("isDraft").asBoolean());

        // 发件人
        if (node.has("from") && node.get("from").has("emailAddress")) {
            JsonNode from = node.get("from").get("emailAddress");
            builder.from(from.get("address").asText());
        }

        // 收件人
        if (node.has("toRecipients")) {
            List<String> to = new ArrayList<>();
            for (JsonNode recipient : node.get("toRecipients")) {
                if (recipient.has("emailAddress")) {
                    to.add(recipient.get("emailAddress").get("address").asText());
                }
            }
            builder.to(to);
        }

        // CC
        if (node.has("ccRecipients")) {
            List<String> cc = new ArrayList<>();
            for (JsonNode recipient : node.get("ccRecipients")) {
                if (recipient.has("emailAddress")) {
                    cc.add(recipient.get("emailAddress").get("address").asText());
                }
            }
            builder.cc(cc);
        }

        // 正文
        if (node.has("body")) {
            JsonNode body = node.get("body");
            String content = body.has("content") ? body.get("content").asText() : "";
            String contentType = body.has("contentType") ? body.get("contentType").asText() : "text";
            if ("html".equalsIgnoreCase(contentType)) {
                builder.bodyHtml(content);
            } else {
                builder.bodyText(content);
            }
        }

        // 时间
        if (node.has("sentDateTime")) {
            try {
                builder.sentAt(OffsetDateTime.parse(node.get("sentDateTime").asText()).toInstant());
            } catch (Exception ignored) {}
        }
        if (node.has("receivedDateTime")) {
            try {
                builder.receivedAt(OffsetDateTime.parse(node.get("receivedDateTime").asText()).toInstant());
            } catch (Exception ignored) {}
        }

        return builder.build();
    }

    private Map<String, Object> buildMessageBody(EmailMessage email) {
        Map<String, Object> message = new HashMap<>();
        message.put("subject", email.getSubject());

        // 正文
        Map<String, String> body = new HashMap<>();
        body.put("contentType", email.getBodyHtml() != null ? "html" : "text");
        body.put("content", email.getBodyHtml() != null ? email.getBodyHtml() : email.getBodyText());
        message.put("body", body);

        // 收件人
        if (email.getTo() != null && !email.getTo().isEmpty()) {
            List<Map<String, Object>> toRecipients = new ArrayList<>();
            for (String to : email.getTo()) {
                Map<String, Object> recipient = new HashMap<>();
                Map<String, String> emailAddress = new HashMap<>();
                emailAddress.put("address", to);
                recipient.put("emailAddress", emailAddress);
                toRecipients.add(recipient);
            }
            message.put("toRecipients", toRecipients);
        }

        return message;
    }
}
