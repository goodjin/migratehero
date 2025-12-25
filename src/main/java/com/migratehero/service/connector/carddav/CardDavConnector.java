package com.migratehero.service.connector.carddav;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * CardDAV 连接器 - 用于向目标联系人服务器写入联系人
 *
 * 常见 CardDAV 端点:
 * - 网易企业邮箱: https://carddav.qiye.163.com/
 * - 腾讯企业邮箱: https://carddav.exmail.qq.com/
 * - iCloud: https://contacts.icloud.com/
 */
@Slf4j
@Component
public class CardDavConnector {

    private static final String VCARD_CONTENT_TYPE = "text/vcard; charset=utf-8";

    /**
     * 测试 CardDAV 连接
     */
    public boolean testConnection(String cardDavUrl, String email, String password) {
        try (CloseableHttpClient client = createHttpClient(email, password)) {
            org.apache.hc.client5.http.classic.methods.HttpUriRequest request =
                    new org.apache.hc.client5.http.classic.methods.HttpGet(cardDavUrl);

            return client.execute(request, response -> {
                int statusCode = response.getCode();
                log.info("CardDAV connection test: status={}", statusCode);
                return statusCode == 200 || statusCode == 207 || statusCode == 401;
            });
        } catch (Exception e) {
            log.error("CardDAV connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建联系人
     *
     * @param cardDavUrl CardDAV 服务器 URL（包含地址簿路径）
     * @param email 用户邮箱
     * @param password 密码
     * @param vCardData vCard 格式的联系人数据
     * @return 创建的联系人 URL
     */
    public String createContact(String cardDavUrl, String email, String password, String vCardData) throws Exception {
        // 生成唯一联系人 ID
        String contactUid = UUID.randomUUID().toString();
        String contactUrl = normalizeUrl(cardDavUrl) + contactUid + ".vcf";

        try (CloseableHttpClient client = createHttpClient(email, password)) {
            HttpPut request = new HttpPut(contactUrl);
            request.setHeader("Content-Type", VCARD_CONTENT_TYPE);
            request.setEntity(new StringEntity(vCardData, ContentType.create("text/vcard", StandardCharsets.UTF_8)));

            return client.execute(request, response -> {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("CardDAV contact created successfully: {}", contactUrl);
                    return contactUrl;
                } else {
                    log.error("CardDAV create contact failed: status={}, response={}", statusCode, responseBody);
                    throw new RuntimeException("Failed to create CardDAV contact: " + statusCode);
                }
            });
        }
    }

    /**
     * 更新联系人
     */
    public void updateContact(String contactUrl, String email, String password, String vCardData) throws Exception {
        try (CloseableHttpClient client = createHttpClient(email, password)) {
            HttpPut request = new HttpPut(contactUrl);
            request.setHeader("Content-Type", VCARD_CONTENT_TYPE);
            request.setEntity(new StringEntity(vCardData, ContentType.create("text/vcard", StandardCharsets.UTF_8)));

            client.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("CardDAV contact updated successfully: {}", contactUrl);
                    return null;
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    log.error("CardDAV update contact failed: status={}, response={}", statusCode, responseBody);
                    throw new RuntimeException("Failed to update CardDAV contact: " + statusCode);
                }
            });
        }
    }

    /**
     * 删除联系人
     */
    public void deleteContact(String contactUrl, String email, String password) throws Exception {
        try (CloseableHttpClient client = createHttpClient(email, password)) {
            org.apache.hc.client5.http.classic.methods.HttpDelete request =
                    new org.apache.hc.client5.http.classic.methods.HttpDelete(contactUrl);

            client.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300 || statusCode == 404) {
                    log.debug("CardDAV contact deleted: {}", contactUrl);
                    return null;
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    log.error("CardDAV delete contact failed: status={}, response={}", statusCode, responseBody);
                    throw new RuntimeException("Failed to delete CardDAV contact: " + statusCode);
                }
            });
        }
    }

    /**
     * 根据 IMAP 主机推断 CardDAV URL
     */
    public String inferCardDavUrl(String imapHost, String email) {
        // 常见邮箱服务商的 CardDAV 端点映射
        if (imapHost.contains("163.com") || imapHost.contains("qiye.163")) {
            // 网易企业邮箱
            return "https://carddav.qiye.163.com/dav/" + email + "/addressbook/";
        } else if (imapHost.contains("qq.com") || imapHost.contains("exmail.qq")) {
            // 腾讯企业邮箱
            return "https://carddav.exmail.qq.com/dav/" + email + "/addressbook/";
        } else if (imapHost.contains("icloud.com")) {
            // iCloud
            return "https://contacts.icloud.com/";
        } else if (imapHost.contains("outlook") || imapHost.contains("office365")) {
            // Microsoft 365 不支持 CardDAV，需要用 Graph API
            log.warn("Microsoft 365 does not support CardDAV, use Graph API instead");
            return null;
        } else if (imapHost.contains("gmail") || imapHost.contains("google")) {
            // Google Contacts 不支持 CardDAV (已弃用)
            log.warn("Google Contacts CardDAV is deprecated, use Google People API instead");
            return null;
        }

        // 尝试通用模式
        String domain = imapHost.replace("imap.", "").replace("mail.", "");
        return "https://carddav." + domain + "/dav/" + email + "/addressbook/";
    }

    private CloseableHttpClient createHttpClient(String email, String password) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(email, password.toCharArray())
        );

        return HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
    }

    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}
