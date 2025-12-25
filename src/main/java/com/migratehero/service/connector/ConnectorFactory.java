package com.migratehero.service.connector;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.service.connector.ews.EwsCalendarConnector;
import com.migratehero.service.connector.ews.EwsContactConnector;
import com.migratehero.service.connector.ews.EwsEmailConnector;
import com.migratehero.service.connector.google.GoogleCalendarConnector;
import com.migratehero.service.connector.google.GoogleContactConnector;
import com.migratehero.service.connector.google.GoogleEmailConnector;
import com.migratehero.service.connector.microsoft.MicrosoftCalendarConnector;
import com.migratehero.service.connector.microsoft.MicrosoftContactConnector;
import com.migratehero.service.connector.microsoft.MicrosoftEmailConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 连接器工厂 - 根据账户类型返回对应的连接器实现
 *
 * Microsoft 连接器策略：
 * - 默认使用 EWS（更成熟稳定，功能更完整）
 * - Graph API 作为备选方案（可通过配置切换）
 * - EWS 将于 2026年10月退役，届时可切换到 Graph API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorFactory {

    // Google 连接器
    private final GoogleEmailConnector googleEmailConnector;
    private final GoogleContactConnector googleContactConnector;
    private final GoogleCalendarConnector googleCalendarConnector;

    // Microsoft EWS 连接器（首选）
    private final EwsEmailConnector ewsEmailConnector;
    private final EwsContactConnector ewsContactConnector;
    private final EwsCalendarConnector ewsCalendarConnector;

    // Microsoft Graph API 连接器（备选）
    private final MicrosoftEmailConnector microsoftEmailConnector;
    private final MicrosoftContactConnector microsoftContactConnector;
    private final MicrosoftCalendarConnector microsoftCalendarConnector;

    /**
     * 是否使用 EWS 作为 Microsoft 连接器
     * 默认 true（推荐），设置为 false 则使用 Graph API
     */
    @Value("${migratehero.microsoft.use-ews:true}")
    private boolean useMicrosoftEws;

    /**
     * 获取邮件连接器
     */
    public EmailConnector getEmailConnector(EmailAccount account) {
        return getEmailConnector(account.getProvider());
    }

    /**
     * 获取邮件连接器
     */
    public EmailConnector getEmailConnector(ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> googleEmailConnector;
            case MICROSOFT -> {
                if (useMicrosoftEws) {
                    log.debug("Using EWS email connector for Microsoft");
                    yield ewsEmailConnector;
                } else {
                    log.debug("Using Graph API email connector for Microsoft");
                    yield microsoftEmailConnector;
                }
            }
        };
    }

    /**
     * 获取联系人连接器
     */
    public ContactConnector getContactConnector(EmailAccount account) {
        return getContactConnector(account.getProvider());
    }

    /**
     * 获取联系人连接器
     */
    public ContactConnector getContactConnector(ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> googleContactConnector;
            case MICROSOFT -> {
                if (useMicrosoftEws) {
                    log.debug("Using EWS contact connector for Microsoft");
                    yield ewsContactConnector;
                } else {
                    log.debug("Using Graph API contact connector for Microsoft");
                    yield microsoftContactConnector;
                }
            }
        };
    }

    /**
     * 获取日历连接器
     */
    public CalendarConnector getCalendarConnector(EmailAccount account) {
        return getCalendarConnector(account.getProvider());
    }

    /**
     * 获取日历连接器
     */
    public CalendarConnector getCalendarConnector(ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> googleCalendarConnector;
            case MICROSOFT -> {
                if (useMicrosoftEws) {
                    log.debug("Using EWS calendar connector for Microsoft");
                    yield ewsCalendarConnector;
                } else {
                    log.debug("Using Graph API calendar connector for Microsoft");
                    yield microsoftCalendarConnector;
                }
            }
        };
    }

    /**
     * 检查是否使用 EWS
     */
    public boolean isUsingEws() {
        return useMicrosoftEws;
    }

    /**
     * 获取当前 Microsoft 连接器类型描述
     */
    public String getMicrosoftConnectorType() {
        return useMicrosoftEws ? "EWS (Exchange Web Services)" : "Graph API";
    }
}
