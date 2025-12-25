package com.migratehero.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试连接请求
 */
@Data
public class TestConnectionRequest {

    @NotBlank(message = "Connection type is required")
    private String type; // "ews" or "imap"

    // EWS 参数
    private String ewsUrl;
    private String email;
    private String password;

    // IMAP 参数
    private String imapHost;
    private Integer imapPort;
    private Boolean imapSsl;
}
