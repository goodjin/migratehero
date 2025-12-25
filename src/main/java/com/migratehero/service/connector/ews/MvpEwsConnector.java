package com.migratehero.service.connector.ews;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.BodyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.SortDirection;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.complex.MimeContent;
import microsoft.exchange.webservices.data.search.FindFoldersResults;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.FolderView;
import microsoft.exchange.webservices.data.search.ItemView;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MVP 专用的简化 EWS 连接器 - 使用用户名密码认证
 */
@Slf4j
@Component
public class MvpEwsConnector {

    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 测试 EWS 连接
     */
    public boolean testConnection(String ewsUrl, String email, String password) {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);
            Folder inbox = Folder.bind(service, WellKnownFolderName.Inbox);
            log.info("EWS connection test successful. Inbox has {} items", inbox.getTotalCount());
            return true;
        } catch (Exception e) {
            log.error("EWS connection test failed: {}", e.getMessage());
            return false;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取所有邮件文件夹
     */
    public List<FolderInfo> listFolders(String ewsUrl, String email, String password) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);
            List<FolderInfo> folders = new ArrayList<>();

            // 获取根文件夹
            FolderView view = new FolderView(100);
            FindFoldersResults results = service.findFolders(WellKnownFolderName.MsgFolderRoot, view);

            for (Folder folder : results.getFolders()) {
                addFolderRecursive(service, folder, folders, "");
            }

            return folders;
        } finally {
            closeService(service);
        }
    }

    private void addFolderRecursive(ExchangeService service, Folder folder, List<FolderInfo> folders, String parentPath) {
        try {
            String folderPath = parentPath.isEmpty() ? folder.getDisplayName() : parentPath + "/" + folder.getDisplayName();

            FolderInfo info = new FolderInfo();
            info.setId(folder.getId().getUniqueId());
            info.setName(folder.getDisplayName());
            info.setPath(folderPath);
            info.setTotalCount(folder.getTotalCount());
            folders.add(info);

            // 递归获取子文件夹
            FolderView view = new FolderView(100);
            FindFoldersResults subFolders = service.findFolders(folder.getId(), view);
            for (Folder subFolder : subFolders.getFolders()) {
                addFolderRecursive(service, subFolder, folders, folderPath);
            }
        } catch (Exception e) {
            log.warn("Failed to process folder: {}", e.getMessage());
        }
    }

    /**
     * 获取文件夹中的邮件列表（分页）
     */
    public EmailListResult listEmails(String ewsUrl, String email, String password,
                                      String folderId, int offset, int pageSize) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            ItemView view = new ItemView(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE, offset);
            view.getOrderBy().add(ItemSchema.DateTimeReceived, SortDirection.Descending);
            view.setPropertySet(new PropertySet(BasePropertySet.FirstClassProperties));

            FolderId folderIdObj = new FolderId(folderId);
            FindItemsResults<Item> results = service.findItems(folderIdObj, view);

            List<EmailInfo> emails = new ArrayList<>();
            for (Item item : results.getItems()) {
                if (item instanceof microsoft.exchange.webservices.data.core.service.item.EmailMessage) {
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                            (microsoft.exchange.webservices.data.core.service.item.EmailMessage) item;

                    EmailInfo info = new EmailInfo();
                    info.setId(msg.getId().getUniqueId());
                    info.setSubject(msg.getSubject());
                    info.setFromAddress(msg.getFrom() != null ? msg.getFrom().getAddress() : null);
                    info.setReceivedDate(msg.getDateTimeReceived() != null ?
                            msg.getDateTimeReceived().toInstant() : null);
                    info.setSize(msg.getSize());
                    info.setRead(msg.getIsRead());
                    emails.add(info);
                }
            }

            Folder folder = Folder.bind(service, folderIdObj);

            EmailListResult result = new EmailListResult();
            result.setEmails(emails);
            result.setTotalCount(folder.getTotalCount());
            result.setHasMore(results.isMoreAvailable());

            return result;
        } finally {
            closeService(service);
        }
    }

    /**
     * 获取邮件的原始 MIME 内容（用于迁移）
     */
    public byte[] getEmailMimeContent(String ewsUrl, String email, String password, String emailId) throws Exception {
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            PropertySet propSet = new PropertySet(BasePropertySet.FirstClassProperties);
            propSet.setRequestedBodyType(BodyType.Text);

            microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage.bind(
                            service,
                            new ItemId(emailId),
                            new PropertySet(BasePropertySet.IdOnly, ItemSchema.MimeContent)
                    );

            MimeContent mimeContent = msg.getMimeContent();
            if (mimeContent != null) {
                return mimeContent.getContent();
            }
            return null;
        } finally {
            closeService(service);
        }
    }

    /**
     * 批量获取邮件 MIME 内容
     */
    public List<EmailMimeData> getEmailsMimeContent(String ewsUrl, String email, String password,
                                                     List<String> emailIds) throws Exception {
        List<EmailMimeData> results = new ArrayList<>();
        ExchangeService service = null;
        try {
            service = createExchangeService(ewsUrl, email, password);

            for (String emailId : emailIds) {
                try {
                    microsoft.exchange.webservices.data.core.service.item.EmailMessage msg =
                            microsoft.exchange.webservices.data.core.service.item.EmailMessage.bind(
                                    service,
                                    new ItemId(emailId),
                                    new PropertySet(BasePropertySet.FirstClassProperties, ItemSchema.MimeContent)
                            );

                    EmailMimeData data = new EmailMimeData();
                    data.setEmailId(emailId);
                    data.setSubject(msg.getSubject());
                    data.setFromAddress(msg.getFrom() != null ? msg.getFrom().getAddress() : null);
                    data.setReceivedDate(msg.getDateTimeReceived() != null ?
                            msg.getDateTimeReceived().toInstant() : null);

                    MimeContent mimeContent = msg.getMimeContent();
                    if (mimeContent != null) {
                        data.setMimeContent(mimeContent.getContent());
                        data.setSize((long) mimeContent.getContent().length);
                    }
                    results.add(data);
                } catch (Exception e) {
                    log.warn("Failed to get MIME content for email {}: {}", emailId, e.getMessage());
                    EmailMimeData data = new EmailMimeData();
                    data.setEmailId(emailId);
                    data.setError(e.getMessage());
                    results.add(data);
                }
            }
            return results;
        } finally {
            closeService(service);
        }
    }

    private ExchangeService createExchangeService(String ewsUrl, String email, String password) throws Exception {
        ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
        service.setCredentials(new WebCredentials(email, password));
        service.setUrl(new URI(ewsUrl));
        service.setTraceEnabled(false);
        return service;
    }

    private void closeService(ExchangeService service) {
        if (service != null) {
            try {
                service.close();
            } catch (Exception e) {
                log.warn("Failed to close ExchangeService: {}", e.getMessage());
            }
        }
    }

    // === DTOs ===

    @Data
    public static class FolderInfo {
        private String id;
        private String name;
        private String path;
        private int totalCount;
    }

    @Data
    public static class EmailInfo {
        private String id;
        private String subject;
        private String fromAddress;
        private Instant receivedDate;
        private int size;
        private boolean read;
    }

    @Data
    public static class EmailListResult {
        private List<EmailInfo> emails;
        private int totalCount;
        private boolean hasMore;
    }

    @Data
    public static class EmailMimeData {
        private String emailId;
        private String subject;
        private String fromAddress;
        private Instant receivedDate;
        private Long size;
        private byte[] mimeContent;
        private String error;
    }
}
