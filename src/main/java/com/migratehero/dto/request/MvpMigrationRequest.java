package com.migratehero.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * MVP 迁移任务创建请求
 */
@Data
public class MvpMigrationRequest {

    // === 源端配置 (EWS) ===
    @NotBlank(message = "EWS URL is required")
    private String sourceEwsUrl;

    @NotBlank(message = "Source email is required")
    @Email(message = "Invalid source email format")
    private String sourceEmail;

    @NotBlank(message = "Source password is required")
    private String sourcePassword;

    // === 目标端配置 (IMAP) ===
    @NotBlank(message = "Target IMAP host is required")
    private String targetImapHost;

    @NotNull(message = "Target IMAP port is required")
    @Positive(message = "Port must be positive")
    private Integer targetImapPort;

    @NotNull(message = "SSL setting is required")
    private Boolean targetImapSsl;

    @NotBlank(message = "Target email is required")
    @Email(message = "Invalid target email format")
    private String targetEmail;

    @NotBlank(message = "Target password is required")
    private String targetPassword;

    // === 迁移类型控制 ===
    private Boolean migrateEmails = true;

    private Boolean migrateCalendar = false;

    private Boolean migrateContacts = false;

    // === 目标端 CalDAV/CardDAV 配置（可选，默认从 IMAP 主机推断） ===
    private String targetCalDavUrl;

    private String targetCardDavUrl;
}
