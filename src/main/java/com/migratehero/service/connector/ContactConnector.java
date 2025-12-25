package com.migratehero.service.connector;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.Contact;

import java.util.List;

/**
 * 联系人连接器接口 - 定义联系人读写操作
 */
public interface ContactConnector {

    /**
     * 获取联系人列表
     */
    ContactListResult listContacts(EmailAccount account, String pageToken, int maxResults);

    /**
     * 获取单个联系人详情
     */
    Contact getContact(EmailAccount account, String contactId);

    /**
     * 创建联系人
     */
    String createContact(EmailAccount account, Contact contact);

    /**
     * 更新联系人
     */
    void updateContact(EmailAccount account, String contactId, Contact contact);

    /**
     * 删除联系人
     */
    void deleteContact(EmailAccount account, String contactId);

    /**
     * 获取联系人统计
     */
    long getContactCount(EmailAccount account);

    /**
     * 获取增量变化
     */
    IncrementalChanges getIncrementalChanges(EmailAccount account, String syncToken);

    /**
     * 联系人列表结果
     */
    record ContactListResult(
            List<Contact> contacts,
            String nextPageToken,
            String syncToken,
            long totalCount
    ) {}

    /**
     * 增量变化
     */
    record IncrementalChanges(
            List<Contact> added,
            List<Contact> modified,
            List<String> deletedIds,
            String newSyncToken
    ) {}
}
