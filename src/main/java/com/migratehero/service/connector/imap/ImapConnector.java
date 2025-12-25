package com.migratehero.service.connector.imap;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * IMAP 连接器 - 用于向目标邮箱写入邮件
 */
@Slf4j
@Component
public class ImapConnector {

    /**
     * 测试 IMAP 连接
     */
    public boolean testConnection(String host, int port, boolean ssl, String email, String password) {
        Store store = null;
        try {
            store = connectToStore(host, port, ssl, email, password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int messageCount = inbox.getMessageCount();
            inbox.close(false);
            log.info("IMAP connection test successful. INBOX has {} messages", messageCount);
            return true;
        } catch (Exception e) {
            log.error("IMAP connection test failed: {}", e.getMessage());
            return false;
        } finally {
            closeStore(store);
        }
    }

    /**
     * 获取或创建文件夹
     */
    public Folder getOrCreateFolder(Store store, String folderName) throws MessagingException {
        Folder folder = store.getFolder(folderName);
        if (!folder.exists()) {
            log.info("Creating folder: {}", folderName);
            folder.create(Folder.HOLDS_MESSAGES);
        }
        return folder;
    }

    /**
     * 上传邮件到指定文件夹
     *
     * @param host       IMAP 服务器地址
     * @param port       端口
     * @param ssl        是否使用 SSL
     * @param email      邮箱账号
     * @param password   密码
     * @param folderName 目标文件夹
     * @param rawEmail   原始邮件内容 (RFC 822 格式)
     * @return 上传后的邮件 UID（如果支持）
     */
    public String uploadEmail(String host, int port, boolean ssl, String email, String password,
                              String folderName, byte[] rawEmail) throws Exception {
        Store store = null;
        Folder folder = null;
        try {
            store = connectToStore(host, port, ssl, email, password);
            folder = getOrCreateFolder(store, folderName);
            folder.open(Folder.READ_WRITE);

            // 从原始邮件数据创建 MimeMessage
            Session session = Session.getInstance(new Properties());
            InputStream is = new ByteArrayInputStream(rawEmail);
            MimeMessage message = new MimeMessage(session, is);

            // 追加到文件夹
            folder.appendMessages(new Message[]{message});

            log.debug("Email uploaded to folder: {}", folderName);
            return null; // IMAP 标准不返回 UID，需要额外查询

        } finally {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
            closeStore(store);
        }
    }

    /**
     * 批量上传邮件
     */
    public int uploadEmails(String host, int port, boolean ssl, String email, String password,
                            String folderName, byte[][] rawEmails) throws Exception {
        Store store = null;
        Folder folder = null;
        try {
            store = connectToStore(host, port, ssl, email, password);
            folder = getOrCreateFolder(store, folderName);
            folder.open(Folder.READ_WRITE);

            Session session = Session.getInstance(new Properties());
            Message[] messages = new Message[rawEmails.length];

            for (int i = 0; i < rawEmails.length; i++) {
                try {
                    InputStream is = new ByteArrayInputStream(rawEmails[i]);
                    messages[i] = new MimeMessage(session, is);
                } catch (Exception e) {
                    log.warn("Failed to parse email {}: {}", i, e.getMessage());
                    messages[i] = null;
                }
            }

            // 过滤掉 null 的消息
            Message[] validMessages = java.util.Arrays.stream(messages)
                    .filter(m -> m != null)
                    .toArray(Message[]::new);

            if (validMessages.length > 0) {
                folder.appendMessages(validMessages);
            }

            log.info("Uploaded {} emails to folder: {}", validMessages.length, folderName);
            return validMessages.length;

        } finally {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
            closeStore(store);
        }
    }

    /**
     * 列出所有文件夹
     */
    public String[] listFolders(String host, int port, boolean ssl, String email, String password) throws Exception {
        Store store = null;
        try {
            store = connectToStore(host, port, ssl, email, password);
            Folder defaultFolder = store.getDefaultFolder();
            Folder[] folders = defaultFolder.list("*");

            String[] folderNames = new String[folders.length];
            for (int i = 0; i < folders.length; i++) {
                folderNames[i] = folders[i].getFullName();
            }
            return folderNames;

        } finally {
            closeStore(store);
        }
    }

    /**
     * 创建 IMAP 连接
     */
    public Store connectToStore(String host, int port, boolean ssl, String email, String password)
            throws MessagingException {
        Properties props = new Properties();

        if (ssl) {
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", String.valueOf(port));
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.ssl.trust", "*");
            props.put("mail.imaps.connectiontimeout", "30000");
            props.put("mail.imaps.timeout", "60000");
        } else {
            props.put("mail.store.protocol", "imap");
            props.put("mail.imap.host", host);
            props.put("mail.imap.port", String.valueOf(port));
            props.put("mail.imap.connectiontimeout", "30000");
            props.put("mail.imap.timeout", "60000");
        }

        Session session = Session.getInstance(props);
        Store store = session.getStore(ssl ? "imaps" : "imap");
        store.connect(host, port, email, password);

        log.debug("Connected to IMAP server: {}:{}", host, port);
        return store;
    }

    private void closeStore(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                log.warn("Failed to close IMAP store: {}", e.getMessage());
            }
        }
    }
}
