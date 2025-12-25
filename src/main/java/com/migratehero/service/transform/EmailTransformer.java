package com.migratehero.service.transform;

import com.migratehero.model.dto.EmailMessage;
import com.migratehero.model.enums.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件数据转换器 - 处理 Google ↔ Microsoft 邮件格式转换
 */
@Slf4j
@Component
public class EmailTransformer {

    /**
     * 转换邮件以适配目标平台
     */
    public EmailMessage transform(EmailMessage source, ProviderType targetProvider) {
        if (source == null) {
            return null;
        }

        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .subject(source.getSubject())
                .from(source.getFrom())
                .to(source.getTo())
                .cc(source.getCc())
                .bcc(source.getBcc())
                .bodyHtml(source.getBodyHtml())
                .bodyText(source.getBodyText())
                .sentAt(source.getSentAt())
                .receivedAt(source.getReceivedAt())
                .isRead(source.isRead())
                .isStarred(source.isStarred())
                .isDraft(source.isDraft())
                .attachments(source.getAttachments())
                .headers(source.getHeaders())
                .threadId(source.getThreadId())
                .rawMime(source.getRawMime());

        // 转换标签/文件夹
        if (targetProvider == ProviderType.MICROSOFT) {
            builder.labels(transformGmailLabelsToOutlookFolders(source.getLabels()));
        } else {
            builder.labels(transformOutlookFoldersToGmailLabels(source.getLabels()));
        }

        return builder.build();
    }

    /**
     * Gmail 标签转换为 Outlook 文件夹
     */
    private List<String> transformGmailLabelsToOutlookFolders(List<String> gmailLabels) {
        if (gmailLabels == null || gmailLabels.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> outlookFolders = new ArrayList<>();
        for (String label : gmailLabels) {
            String folder = switch (label.toUpperCase()) {
                case "INBOX" -> "Inbox";
                case "SENT" -> "SentItems";
                case "DRAFT" -> "Drafts";
                case "TRASH" -> "DeletedItems";
                case "SPAM" -> "JunkEmail";
                case "STARRED" -> "Inbox"; // Outlook 使用 flag 而非文件夹
                case "IMPORTANT" -> "Inbox"; // 保留重要性但放入收件箱
                case "CATEGORY_PERSONAL" -> "Inbox";
                case "CATEGORY_SOCIAL" -> "Inbox";
                case "CATEGORY_PROMOTIONS" -> "Inbox";
                case "CATEGORY_UPDATES" -> "Inbox";
                case "CATEGORY_FORUMS" -> "Inbox";
                default -> {
                    // 自定义标签转换为 Outlook 类别
                    if (label.startsWith("CATEGORY_")) {
                        yield label.substring(9);
                    }
                    yield label;
                }
            };
            if (!outlookFolders.contains(folder)) {
                outlookFolders.add(folder);
            }
        }
        return outlookFolders;
    }

    /**
     * Outlook 文件夹转换为 Gmail 标签
     */
    private List<String> transformOutlookFoldersToGmailLabels(List<String> outlookFolders) {
        if (outlookFolders == null || outlookFolders.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> gmailLabels = new ArrayList<>();
        for (String folder : outlookFolders) {
            String label = switch (folder.toLowerCase()) {
                case "inbox" -> "INBOX";
                case "sentitems", "sent items" -> "SENT";
                case "drafts" -> "DRAFT";
                case "deleteditems", "deleted items" -> "TRASH";
                case "junkemail", "junk email", "junk" -> "SPAM";
                case "archive" -> "All Mail";
                default -> folder; // 保留原始文件夹名作为标签
            };
            if (!gmailLabels.contains(label)) {
                gmailLabels.add(label);
            }
        }
        return gmailLabels;
    }

    /**
     * 批量转换邮件
     */
    public List<EmailMessage> transformBatch(List<EmailMessage> sources, ProviderType targetProvider) {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }

        List<EmailMessage> results = new ArrayList<>(sources.size());
        for (EmailMessage source : sources) {
            try {
                results.add(transform(source, targetProvider));
            } catch (Exception e) {
                log.error("Failed to transform email: {}", source.getId(), e);
            }
        }
        return results;
    }
}
