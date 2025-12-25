package com.migratehero.service.connector.caldav;

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
 * CalDAV 连接器 - 用于向目标日历服务器写入日历事件
 *
 * 常见 CalDAV 端点:
 * - 网易企业邮箱: https://caldav.qiye.163.com/
 * - 腾讯企业邮箱: https://caldav.exmail.qq.com/
 * - iCloud: https://caldav.icloud.com/
 */
@Slf4j
@Component
public class CalDavConnector {

    private static final String CALDAV_CONTENT_TYPE = "text/calendar; charset=utf-8";

    /**
     * 测试 CalDAV 连接
     */
    public boolean testConnection(String calDavUrl, String email, String password) {
        try (CloseableHttpClient client = createHttpClient(email, password)) {
            // 尝试 PROPFIND 请求测试连接
            org.apache.hc.client5.http.classic.methods.HttpUriRequest request =
                    new org.apache.hc.client5.http.classic.methods.HttpGet(calDavUrl);

            return client.execute(request, response -> {
                int statusCode = response.getCode();
                log.info("CalDAV connection test: status={}", statusCode);
                // 200, 207 (Multi-Status), 401 (需要认证但服务可达) 都表示服务可用
                return statusCode == 200 || statusCode == 207 || statusCode == 401;
            });
        } catch (Exception e) {
            log.error("CalDAV connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 创建日历事件
     *
     * @param calDavUrl CalDAV 服务器 URL（包含日历路径）
     * @param email 用户邮箱
     * @param password 密码
     * @param iCalData iCalendar 格式的事件数据
     * @return 创建的事件 URL
     */
    public String createEvent(String calDavUrl, String email, String password, String iCalData) throws Exception {
        // 生成唯一事件 ID
        String eventUid = UUID.randomUUID().toString();
        String eventUrl = normalizeUrl(calDavUrl) + eventUid + ".ics";

        try (CloseableHttpClient client = createHttpClient(email, password)) {
            HttpPut request = new HttpPut(eventUrl);
            request.setHeader("Content-Type", CALDAV_CONTENT_TYPE);
            request.setEntity(new StringEntity(iCalData, ContentType.create("text/calendar", StandardCharsets.UTF_8)));

            return client.execute(request, response -> {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("CalDAV event created successfully: {}", eventUrl);
                    return eventUrl;
                } else {
                    log.error("CalDAV create event failed: status={}, response={}", statusCode, responseBody);
                    throw new RuntimeException("Failed to create CalDAV event: " + statusCode);
                }
            });
        }
    }

    /**
     * 更新日历事件
     */
    public void updateEvent(String eventUrl, String email, String password, String iCalData) throws Exception {
        try (CloseableHttpClient client = createHttpClient(email, password)) {
            HttpPut request = new HttpPut(eventUrl);
            request.setHeader("Content-Type", CALDAV_CONTENT_TYPE);
            request.setEntity(new StringEntity(iCalData, ContentType.create("text/calendar", StandardCharsets.UTF_8)));

            client.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("CalDAV event updated successfully: {}", eventUrl);
                    return null;
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    log.error("CalDAV update event failed: status={}, response={}", statusCode, responseBody);
                    throw new RuntimeException("Failed to update CalDAV event: " + statusCode);
                }
            });
        }
    }

    /**
     * 删除日历事件
     */
    public void deleteEvent(String eventUrl, String email, String password) throws Exception {
        try (CloseableHttpClient client = createHttpClient(email, password)) {
            org.apache.hc.client5.http.classic.methods.HttpDelete request =
                    new org.apache.hc.client5.http.classic.methods.HttpDelete(eventUrl);

            client.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300 || statusCode == 404) {
                    log.debug("CalDAV event deleted: {}", eventUrl);
                    return null;
                } else {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    log.error("CalDAV delete event failed: status={}, response={}", statusCode, responseBody);
                    throw new RuntimeException("Failed to delete CalDAV event: " + statusCode);
                }
            });
        }
    }

    /**
     * 根据 IMAP 主机推断 CalDAV URL
     */
    public String inferCalDavUrl(String imapHost, String email) {
        // 常见邮箱服务商的 CalDAV 端点映射
        if (imapHost.contains("163.com") || imapHost.contains("qiye.163")) {
            // 网易企业邮箱
            return "https://caldav.qiye.163.com/dav/" + email + "/calendar/";
        } else if (imapHost.contains("qq.com") || imapHost.contains("exmail.qq")) {
            // 腾讯企业邮箱
            return "https://caldav.exmail.qq.com/dav/" + email + "/calendar/";
        } else if (imapHost.contains("icloud.com")) {
            // iCloud
            return "https://caldav.icloud.com/";
        } else if (imapHost.contains("outlook") || imapHost.contains("office365")) {
            // Microsoft 365 不支持 CalDAV，需要用 Graph API
            log.warn("Microsoft 365 does not support CalDAV, use Graph API instead");
            return null;
        } else if (imapHost.contains("gmail") || imapHost.contains("google")) {
            // Google Calendar 不支持 CalDAV (已弃用)
            log.warn("Google Calendar CalDAV is deprecated, use Google Calendar API instead");
            return null;
        }

        // 尝试通用模式
        String domain = imapHost.replace("imap.", "").replace("mail.", "");
        return "https://caldav." + domain + "/dav/" + email + "/calendar/";
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
