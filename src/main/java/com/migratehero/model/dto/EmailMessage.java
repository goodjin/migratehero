package com.migratehero.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 统一邮件消息 DTO - 用于 Google 和 Microsoft 邮件格式转换
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    private String id;
    private String threadId;
    private String subject;
    private String from;
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private Instant sentAt;
    private Instant receivedAt;
    private String bodyHtml;
    private String bodyText;
    private boolean isRead;
    private boolean isStarred;
    private boolean isDraft;
    private List<String> labels;
    private List<Attachment> attachments;
    private Map<String, String> headers;
    private String rawMime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String filename;
        private String mimeType;
        private long size;
        private byte[] data;
        private String contentId;
        private boolean isInline;
    }
}
