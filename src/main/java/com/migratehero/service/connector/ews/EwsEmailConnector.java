package com.migratehero.service.connector.ews;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.dto.EmailMessage;
import com.migratehero.service.EncryptionService;
import com.migratehero.service.connector.EmailConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.BodyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.enumeration.service.ConflictResolutionMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SyncFolderItemsScope;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.EmailMessageSchema;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.property.complex.Attachment;
import microsoft.exchange.webservices.data.property.complex.EmailAddress;
import microsoft.exchange.webservices.data.property.complex.EmailAddressCollection;
import microsoft.exchange.webservices.data.property.complex.FileAttachment;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.complex.MessageBody;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.sync.ChangeCollection;
import microsoft.exchange.webservices.data.sync.ItemChange;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * EWS 邮件连接器 - 使用 Exchange Web Services 访问 Microsoft 邮箱
 * 比 Graph API 更成熟稳定，功能更完整
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EwsEmailConnector implements EmailConnector {

    private final EncryptionService encryptionService;

    private static final String EWS_URL = "https://outlook.office365.com/EWS/Exchange.asmx";
    private static final int DEFAULT_PAGE_SIZE = 100;

    @Override
    public EmailListResult listEmails(EmailAccount account, String pageToken, int maxResults) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            int offset = pageToken != null ? Integer.parseInt(pageToken) : 0;
            int pageSize = maxResults > 0 ? Math.min(maxResults, DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

            ItemView view = new ItemView(pageSize, offset);
            view.getOrderBy().add(ItemSchema.DateTimeReceived, SortDirection.Descending);

            // 设置要获取的属性
            PropertySet propertySet = new PropertySet(BasePropertySet.FirstClassProperties);
            view.setPropertySet(propertySet);

            // 从收件箱获取邮件
            FindItemsResults<Item> findResults = service.findItems(WellKnownFolderName.Inbox, view);

            List<EmailMessage> emails = new ArrayList<>();
            for (Item item : findResults) {
                if (item instanceof microsoft.exchange.webservices.data.core.service.item.EmailMessage) {
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage ewsMsg =
                            (microsoft.exchange.webservices.data.core.service.item.EmailMessage) item;
                    emails.add(convertToEmailMessage(ewsMsg));
                }
            }

            // 计算下一页 token
            String nextPageToken = null;
            if (findResults.isMoreAvailable()) {
                nextPageToken = String.valueOf(offset + pageSize);
            }

            // 获取总数
            Folder inbox = Folder.bind(service, WellKnownFolderName.Inbox);
            long totalCount = inbox.getTotalCount();

            return new EmailListResult(emails, nextPageToken, null, totalCount);

        } catch (Exception e) {
            log.error("Failed to list emails via EWS for account {}: {}", account.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to list emails via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public EmailMessage getEmail(EmailAccount account, String emailId) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            // 绑定邮件并加载完整属性
            PropertySet propertySet = new PropertySet(BasePropertySet.FirstClassProperties);
            propertySet.setRequestedBodyType(BodyType.HTML);

            microsoft.exchange.webservices.data.core.service.item.EmailMessage ewsMessage =
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage.bind(
                            service,
                            new ItemId(emailId),
                            propertySet
                    );

            // 加载附件
            ewsMessage.load(new PropertySet(BasePropertySet.FirstClassProperties,
                    EmailMessageSchema.Attachments));

            return convertToEmailMessage(ewsMessage);

        } catch (Exception e) {
            log.error("Failed to get email {} via EWS: {}", emailId, e.getMessage(), e);
            throw new RuntimeException("Failed to get email via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public String createEmail(EmailAccount account, EmailMessage email) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            microsoft.exchange.webservices.data.core.service.item.EmailMessage ewsMessage =
                    new microsoft.exchange.webservices.data.core.service.item.EmailMessage(service);
            ewsMessage.setSubject(email.getSubject());

            // 设置收件人
            if (email.getTo() != null) {
                for (String to : email.getTo()) {
                    ewsMessage.getToRecipients().add(to);
                }
            }
            if (email.getCc() != null) {
                for (String cc : email.getCc()) {
                    ewsMessage.getCcRecipients().add(cc);
                }
            }
            if (email.getBcc() != null) {
                for (String bcc : email.getBcc()) {
                    ewsMessage.getBccRecipients().add(bcc);
                }
            }

            // 设置正文
            if (email.getBodyHtml() != null) {
                MessageBody body = new MessageBody(BodyType.HTML, email.getBodyHtml());
                ewsMessage.setBody(body);
            } else if (email.getBodyText() != null) {
                MessageBody body = new MessageBody(BodyType.Text, email.getBodyText());
                ewsMessage.setBody(body);
            }

            // 添加附件
            if (email.getAttachments() != null) {
                for (EmailMessage.Attachment attachment : email.getAttachments()) {
                    if (attachment.getData() != null) {
                        ewsMessage.getAttachments().addFileAttachment(
                                attachment.getFilename(),
                                attachment.getData()
                        );
                    }
                }
            }

            // 保存到收件箱（模拟"导入"）
            ewsMessage.save(WellKnownFolderName.Inbox);

            // 标记为已读/未读
            if (email.isRead()) {
                ewsMessage.setIsRead(true);
                ewsMessage.update(ConflictResolutionMode.AlwaysOverwrite);
            }

            String messageId = ewsMessage.getId().getUniqueId();
            log.debug("Created email via EWS: {}", messageId);

            return messageId;

        } catch (Exception e) {
            log.error("Failed to create email via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create email via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public String createDraft(EmailAccount account, EmailMessage email) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            microsoft.exchange.webservices.data.core.service.item.EmailMessage ewsMessage =
                    new microsoft.exchange.webservices.data.core.service.item.EmailMessage(service);
            ewsMessage.setSubject(email.getSubject());

            if (email.getTo() != null) {
                for (String to : email.getTo()) {
                    ewsMessage.getToRecipients().add(to);
                }
            }

            if (email.getBodyHtml() != null) {
                MessageBody body = new MessageBody(BodyType.HTML, email.getBodyHtml());
                ewsMessage.setBody(body);
            }

            ewsMessage.save(WellKnownFolderName.Drafts);

            String draftId = ewsMessage.getId().getUniqueId();
            log.debug("Created draft via EWS: {}", draftId);

            return draftId;

        } catch (Exception e) {
            log.error("Failed to create draft via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create draft via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public EmailStats getStats(EmailAccount account) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            Folder inbox = Folder.bind(service, WellKnownFolderName.Inbox);
            Folder drafts = Folder.bind(service, WellKnownFolderName.Drafts);

            long totalCount = inbox.getTotalCount();
            long unreadCount = inbox.getUnreadCount();
            long draftCount = drafts.getTotalCount();

            return new EmailStats(totalCount, unreadCount, draftCount);

        } catch (Exception e) {
            log.error("Failed to get email stats via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get email stats via EWS", e);
        } finally {
            closeService(service);
        }
    }

    @Override
    public IncrementalChanges getIncrementalChanges(EmailAccount account, String syncState) {
        ExchangeService service = null;
        try {
            service = createExchangeService(account);

            // 使用 EWS 的 SyncFolderItems 进行增量同步
            FolderId inboxFolderId = new FolderId(WellKnownFolderName.Inbox);

            ChangeCollection<ItemChange> changes = service.syncFolderItems(
                    inboxFolderId,
                    new PropertySet(BasePropertySet.IdOnly),
                    null,  // 忽略的项目 ID
                    DEFAULT_PAGE_SIZE,
                    SyncFolderItemsScope.NormalItems,
                    syncState  // 上次同步状态
            );

            List<String> addedIds = new ArrayList<>();
            List<String> modifiedIds = new ArrayList<>();
            List<String> deletedIds = new ArrayList<>();

            for (ItemChange change : changes) {
                switch (change.getChangeType()) {
                    case Create:
                        addedIds.add(change.getItemId().getUniqueId());
                        break;
                    case Update:
                        modifiedIds.add(change.getItemId().getUniqueId());
                        break;
                    case Delete:
                        deletedIds.add(change.getItemId().getUniqueId());
                        break;
                    case ReadFlagChange:
                        modifiedIds.add(change.getItemId().getUniqueId());
                        break;
                }
            }

            String newSyncState = changes.getSyncState();

            return new IncrementalChanges(addedIds, modifiedIds, deletedIds, newSyncState);

        } catch (Exception e) {
            log.error("Failed to get incremental changes via EWS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get incremental changes via EWS", e);
        } finally {
            closeService(service);
        }
    }

    /**
     * 创建 Exchange 服务实例
     */
    private ExchangeService createExchangeService(EmailAccount account) throws Exception {
        ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);

        // 解密 OAuth token
        String accessToken = encryptionService.decrypt(account.getAccessTokenEncrypted());

        // 使用 OAuth 2.0 Bearer Token
        service.getHttpHeaders().put("Authorization", "Bearer " + accessToken);

        // 设置 EWS 端点
        service.setUrl(new URI(EWS_URL));

        return service;
    }

    /**
     * 关闭 Exchange 服务
     */
    private void closeService(ExchangeService service) {
        if (service != null) {
            try {
                service.close();
            } catch (Exception e) {
                log.warn("Failed to close ExchangeService: {}", e.getMessage());
            }
        }
    }

    /**
     * 转换 EWS 邮件为内部 DTO
     */
    private EmailMessage convertToEmailMessage(
            microsoft.exchange.webservices.data.core.service.item.EmailMessage ewsMessage) throws Exception {

        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .id(ewsMessage.getId().getUniqueId())
                .subject(ewsMessage.getSubject())
                .isRead(ewsMessage.getIsRead())
                .isDraft(ewsMessage.getIsDraft());

        // 发件人
        if (ewsMessage.getFrom() != null) {
            builder.from(ewsMessage.getFrom().getAddress());
        }

        // 收件人
        builder.to(extractEmailAddresses(ewsMessage.getToRecipients()));
        builder.cc(extractEmailAddresses(ewsMessage.getCcRecipients()));
        builder.bcc(extractEmailAddresses(ewsMessage.getBccRecipients()));

        // 时间
        if (ewsMessage.getDateTimeSent() != null) {
            builder.sentAt(ewsMessage.getDateTimeSent().toInstant());
        }
        if (ewsMessage.getDateTimeReceived() != null) {
            builder.receivedAt(ewsMessage.getDateTimeReceived().toInstant());
        }

        // 正文
        if (ewsMessage.getBody() != null) {
            String bodyContent = ewsMessage.getBody().toString();
            if (ewsMessage.getBody().getBodyType() == BodyType.HTML) {
                builder.bodyHtml(bodyContent);
            } else {
                builder.bodyText(bodyContent);
            }
        }

        // 附件
        if (ewsMessage.getHasAttachments()) {
            List<EmailMessage.Attachment> attachments = new ArrayList<>();
            for (Attachment attachment : ewsMessage.getAttachments()) {
                if (attachment instanceof FileAttachment) {
                    FileAttachment fileAttachment = (FileAttachment) attachment;
                    fileAttachment.load();
                    attachments.add(EmailMessage.Attachment.builder()
                            .id(fileAttachment.getId())
                            .filename(fileAttachment.getName())
                            .mimeType(fileAttachment.getContentType())
                            .size(fileAttachment.getSize())
                            .data(fileAttachment.getContent())
                            .build());
                }
            }
            builder.attachments(attachments);
        }

        // 标签/文件夹
        builder.labels(List.of(getFolderName(ewsMessage)));

        return builder.build();
    }

    private List<String> extractEmailAddresses(EmailAddressCollection addresses) {
        if (addresses == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (EmailAddress address : addresses) {
            result.add(address.getAddress());
        }
        return result;
    }

    private String getFolderName(
            microsoft.exchange.webservices.data.core.service.item.EmailMessage message) {
        try {
            Folder parentFolder = Folder.bind(
                    message.getService(),
                    message.getParentFolderId()
            );
            return parentFolder.getDisplayName();
        } catch (Exception e) {
            return "Inbox";
        }
    }
}
