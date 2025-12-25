package com.migratehero.service.connector.google;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.EmailMessage;
import com.migratehero.service.EncryptionService;
import com.migratehero.service.connector.EmailConnector;
import com.migratehero.service.oauth.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Google Gmail 邮件连接器实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleEmailConnector implements EmailConnector {

    private final GoogleOAuthService googleOAuthService;
    private final EncryptionService encryptionService;

    private static final String APPLICATION_NAME = "MigrateHero";

    @Override
    public EmailListResult listEmails(EmailAccount account, String pageToken, int maxResults) {
        try {
            Gmail gmail = getGmailService(account);
            Gmail.Users.Messages.List request = gmail.users().messages()
                    .list("me")
                    .setMaxResults((long) maxResults);

            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            List<Message> messages = response.getMessages();

            if (messages == null || messages.isEmpty()) {
                return new EmailListResult(Collections.emptyList(), null, null, 0);
            }

            // 获取邮件详情
            List<EmailMessage> emails = messages.stream()
                    .map(m -> getEmail(account, m.getId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 获取 historyId
            Profile profile = gmail.users().getProfile("me").execute();
            String historyId = profile.getHistoryId().toString();

            return new EmailListResult(
                    emails,
                    response.getNextPageToken(),
                    historyId,
                    response.getResultSizeEstimate() != null ? response.getResultSizeEstimate() : emails.size()
            );
        } catch (IOException e) {
            log.error("Failed to list emails for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to list emails", e);
        }
    }

    @Override
    public EmailMessage getEmail(EmailAccount account, String emailId) {
        try {
            Gmail gmail = getGmailService(account);
            Message message = gmail.users().messages()
                    .get("me", emailId)
                    .setFormat("full")
                    .execute();

            return convertToEmailMessage(message);
        } catch (IOException e) {
            log.error("Failed to get email {} for account: {}", emailId, account.getEmail(), e);
            return null;
        }
    }

    @Override
    public String createEmail(EmailAccount account, EmailMessage email) {
        try {
            Gmail gmail = getGmailService(account);

            // 构建 MIME 消息
            String rawMessage = buildRawMimeMessage(email);
            Message message = new Message();
            message.setRaw(Base64.getUrlEncoder().encodeToString(rawMessage.getBytes()));

            // 设置标签
            if (email.getLabels() != null && !email.getLabels().isEmpty()) {
                message.setLabelIds(email.getLabels());
            }

            Message created = gmail.users().messages()
                    .insert("me", message)
                    .setInternalDateSource("dateHeader")
                    .execute();

            return created.getId();
        } catch (IOException e) {
            log.error("Failed to create email for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create email", e);
        }
    }

    @Override
    public String createDraft(EmailAccount account, EmailMessage email) {
        try {
            Gmail gmail = getGmailService(account);

            String rawMessage = buildRawMimeMessage(email);
            Message message = new Message();
            message.setRaw(Base64.getUrlEncoder().encodeToString(rawMessage.getBytes()));

            Draft draft = new Draft();
            draft.setMessage(message);

            Draft created = gmail.users().drafts()
                    .create("me", draft)
                    .execute();

            return created.getId();
        } catch (IOException e) {
            log.error("Failed to create draft for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to create draft", e);
        }
    }

    @Override
    public EmailStats getStats(EmailAccount account) {
        try {
            Gmail gmail = getGmailService(account);

            // 获取总邮件数
            ListMessagesResponse all = gmail.users().messages()
                    .list("me")
                    .setMaxResults(1L)
                    .execute();

            // 获取未读邮件数
            ListMessagesResponse unread = gmail.users().messages()
                    .list("me")
                    .setQ("is:unread")
                    .setMaxResults(1L)
                    .execute();

            // 获取草稿数
            ListDraftsResponse drafts = gmail.users().drafts()
                    .list("me")
                    .setMaxResults(1L)
                    .execute();

            return new EmailStats(
                    all.getResultSizeEstimate() != null ? all.getResultSizeEstimate() : 0,
                    unread.getResultSizeEstimate() != null ? unread.getResultSizeEstimate() : 0,
                    drafts.getResultSizeEstimate() != null ? drafts.getResultSizeEstimate() : 0
            );
        } catch (IOException e) {
            log.error("Failed to get email stats for account: {}", account.getEmail(), e);
            return new EmailStats(0, 0, 0);
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String historyId) {
        try {
            Gmail gmail = getGmailService(account);

            ListHistoryResponse response = gmail.users().history()
                    .list("me")
                    .setStartHistoryId(java.math.BigInteger.valueOf(Long.parseLong(historyId)))
                    .execute();

            List<String> addedIds = new ArrayList<>();
            List<String> modifiedIds = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            if (response.getHistory() != null) {
                for (History history : response.getHistory()) {
                    if (history.getMessagesAdded() != null) {
                        history.getMessagesAdded().forEach(m -> addedIds.add(m.getMessage().getId()));
                    }
                    if (history.getLabelsAdded() != null || history.getLabelsRemoved() != null) {
                        if (history.getLabelsAdded() != null) {
                            history.getLabelsAdded().forEach(m -> modifiedIds.add(m.getMessage().getId()));
                        }
                        if (history.getLabelsRemoved() != null) {
                            history.getLabelsRemoved().forEach(m -> modifiedIds.add(m.getMessage().getId()));
                        }
                    }
                    if (history.getMessagesDeleted() != null) {
                        history.getMessagesDeleted().forEach(m -> deletedIds.add(m.getMessage().getId()));
                    }
                }
            }

            return new IncrementalChanges(
                    addedIds,
                    modifiedIds.stream().distinct().collect(Collectors.toList()),
                    deletedIds,
                    response.getHistoryId() != null ? response.getHistoryId().toString() : historyId
            );
        } catch (IOException e) {
            log.error("Failed to get incremental changes for account: {}", account.getEmail(), e);
            throw new RuntimeException("Failed to get incremental changes", e);
        }
    }

    private Gmail getGmailService(EmailAccount account) {
        String accessToken = googleOAuthService.getAccessToken(account);

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, null)
        );

        return new Gmail.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName(APPLICATION_NAME).build();
    }

    private EmailMessage convertToEmailMessage(Message message) {
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .id(message.getId())
                .threadId(message.getThreadId());

        // 解析 headers
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            Map<String, String> headers = new HashMap<>();
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                headers.put(header.getName().toLowerCase(), header.getValue());
                switch (header.getName().toLowerCase()) {
                    case "subject" -> builder.subject(header.getValue());
                    case "from" -> builder.from(header.getValue());
                    case "to" -> builder.to(parseAddresses(header.getValue()));
                    case "cc" -> builder.cc(parseAddresses(header.getValue()));
                    case "bcc" -> builder.bcc(parseAddresses(header.getValue()));
                    case "date" -> {
                        try {
                            builder.sentAt(Instant.ofEpochMilli(message.getInternalDate()));
                        } catch (Exception ignored) {}
                    }
                }
            }
            builder.headers(headers);
        }

        // 解析标签
        if (message.getLabelIds() != null) {
            builder.labels(message.getLabelIds());
            builder.isRead(!message.getLabelIds().contains("UNREAD"));
            builder.isStarred(message.getLabelIds().contains("STARRED"));
            builder.isDraft(message.getLabelIds().contains("DRAFT"));
        }

        // 解析正文
        if (message.getPayload() != null) {
            extractBody(message.getPayload(), builder);
        }

        builder.receivedAt(Instant.ofEpochMilli(message.getInternalDate()));

        return builder.build();
    }

    private void extractBody(MessagePart part, EmailMessage.EmailMessageBuilder builder) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            String data = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            if ("text/plain".equals(part.getMimeType())) {
                builder.bodyText(data);
            } else if ("text/html".equals(part.getMimeType())) {
                builder.bodyHtml(data);
            }
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                extractBody(subPart, builder);
            }
        }
    }

    private List<String> parseAddresses(String addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(addresses.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String buildRawMimeMessage(EmailMessage email) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(email.getFrom()).append("\r\n");
        if (email.getTo() != null && !email.getTo().isEmpty()) {
            sb.append("To: ").append(String.join(", ", email.getTo())).append("\r\n");
        }
        if (email.getCc() != null && !email.getCc().isEmpty()) {
            sb.append("Cc: ").append(String.join(", ", email.getCc())).append("\r\n");
        }
        if (email.getSubject() != null) {
            sb.append("Subject: ").append(email.getSubject()).append("\r\n");
        }
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/html; charset=UTF-8\r\n");
        sb.append("\r\n");
        sb.append(email.getBodyHtml() != null ? email.getBodyHtml() : email.getBodyText());

        return sb.toString();
    }
}
