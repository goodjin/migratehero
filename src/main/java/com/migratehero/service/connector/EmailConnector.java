package com.migratehero.service.connector;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.EmailMessage;

import java.util.List;

/**
 * 邮件连接器接口 - 定义邮件读写操作
 */
public interface EmailConnector {

    /**
     * 获取邮件列表
     * @param account 账户
     * @param pageToken 分页 Token
     * @param maxResults 最大结果数
     * @return 邮件列表结果
     */
    EmailListResult listEmails(EmailAccount account, String pageToken, int maxResults);

    /**
     * 获取单封邮件详情
     */
    EmailMessage getEmail(EmailAccount account, String emailId);

    /**
     * 创建邮件（发送到目标账户）
     */
    String createEmail(EmailAccount account, EmailMessage email);

    /**
     * 创建草稿
     */
    String createDraft(EmailAccount account, EmailMessage email);

    /**
     * 获取邮件数量统计
     */
    EmailStats getStats(EmailAccount account);

    /**
     * 获取增量变化（用于增量同步）
     * @param historyId Gmail History ID 或 MS Graph Delta Token
     */
    IncrementalChanges getIncrementalChanges(EmailAccount account, String historyId);

    /**
     * 邮件列表结果
     */
    record EmailListResult(
            List<EmailMessage> emails,
            String nextPageToken,
            String historyId,
            long totalCount
    ) {}

    /**
     * 邮件统计
     */
    record EmailStats(
            long totalCount,
            long unreadCount,
            long draftCount
    ) {}

    /**
     * 增量变化
     */
    record IncrementalChanges(
            List<String> addedIds,
            List<String> modifiedIds,
            List<String> deletedIds,
            String newHistoryId
    ) {}
}
