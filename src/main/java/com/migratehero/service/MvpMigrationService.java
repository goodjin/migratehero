package com.migratehero.service;

import com.migratehero.model.MvpFolderProgress;
import com.migratehero.model.MvpMigratedCalendarEvent;
import com.migratehero.model.MvpMigratedContact;
import com.migratehero.model.MvpMigratedEmail;
import com.migratehero.model.MvpMigrationTask;
import com.migratehero.model.enums.MigrationStatus;
import com.migratehero.repository.MvpFolderProgressRepository;
import com.migratehero.repository.MvpMigratedCalendarEventRepository;
import com.migratehero.repository.MvpMigratedContactRepository;
import com.migratehero.repository.MvpMigratedEmailRepository;
import com.migratehero.repository.MvpMigrationTaskRepository;
import com.migratehero.service.connector.caldav.CalDavConnector;
import com.migratehero.service.connector.carddav.CardDavConnector;
import com.migratehero.service.connector.ews.MvpEwsConnector;
import com.migratehero.service.connector.imap.ImapConnector;
import com.migratehero.service.transform.MvpCalendarTransformer;
import com.migratehero.service.transform.MvpContactTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MVP 迁移服务 - 处理 EWS -> IMAP 邮箱迁移
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MvpMigrationService {

    private final MvpMigrationTaskRepository taskRepository;
    private final MvpFolderProgressRepository folderProgressRepository;
    private final MvpMigratedEmailRepository migratedEmailRepository;
    private final MvpMigratedCalendarEventRepository calendarEventRepository;
    private final MvpMigratedContactRepository contactRepository;
    private final MvpEwsConnector ewsConnector;
    private final ImapConnector imapConnector;
    private final CalDavConnector calDavConnector;
    private final CardDavConnector cardDavConnector;
    private final MvpCalendarTransformer calendarTransformer;
    private final MvpContactTransformer contactTransformer;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int BATCH_SIZE = 10;

    /**
     * 创建迁移任务
     */
    @Transactional
    public MvpMigrationTask createTask(MvpMigrationTask task) {
        task.setStatus(MigrationStatus.DRAFT);
        return taskRepository.save(task);
    }

    /**
     * 更新迁移任务
     */
    @Transactional
    public MvpMigrationTask updateTask(Long taskId, MvpMigrationTask updatedTask) {
        MvpMigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (task.getStatus() != MigrationStatus.PAUSED && task.getStatus() != MigrationStatus.DRAFT && task.getStatus() != MigrationStatus.FAILED) {
            throw new RuntimeException("Only PAUSED, DRAFT, or FAILED tasks can be updated");
        }

        task.setSourceEwsUrl(updatedTask.getSourceEwsUrl());
        task.setSourceEmail(updatedTask.getSourceEmail());
        task.setSourcePassword(updatedTask.getSourcePassword());
        task.setTargetImapHost(updatedTask.getTargetImapHost());
        task.setTargetImapPort(updatedTask.getTargetImapPort());
        task.setTargetImapSsl(updatedTask.getTargetImapSsl());
        task.setTargetEmail(updatedTask.getTargetEmail());
        task.setTargetPassword(updatedTask.getTargetPassword());

        return taskRepository.save(task);
    }

    /**
     * 获取任务详情
     */
    public Optional<MvpMigrationTask> getTask(Long taskId) {
        return taskRepository.findById(taskId);
    }

    /**
     * 获取所有任务
     */
    public List<MvpMigrationTask> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 检查邮箱是否有活跃任务
     */
    public boolean hasActiveTask(String sourceEmail) {
        List<MigrationStatus> completedStatuses = List.of(
                MigrationStatus.COMPLETED,
                MigrationStatus.COMPLETED_WITH_ERRORS,
                MigrationStatus.FAILED,
                MigrationStatus.CANCELLED
        );
        return taskRepository.existsBySourceEmailAndStatusNotIn(sourceEmail, completedStatuses);
    }

    /**
     * 测试源端 EWS 连接
     */
    public boolean testSourceConnection(MvpMigrationTask task) {
        return ewsConnector.testConnection(
                task.getSourceEwsUrl(),
                task.getSourceEmail(),
                task.getSourcePassword()
        );
    }

    /**
     * 测试目标 IMAP 连接
     */
    public boolean testTargetConnection(MvpMigrationTask task) {
        return imapConnector.testConnection(
                task.getTargetImapHost(),
                task.getTargetImapPort(),
                task.getTargetImapSsl(),
                task.getTargetEmail(),
                task.getTargetPassword()
        );
    }

    /**
     * 启动迁移任务（异步执行）
     */
    @Async
    public void startMigration(Long taskId) {
        MvpMigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        try {
            // 更新状态为运行中
            task.setStatus(MigrationStatus.RUNNING);
            task.setStartedAt(Instant.now());
            taskRepository.save(task);
            broadcastProgress(task);

            // 1. 获取源端文件夹列表
            log.info("Fetching folders from source...");
            List<MvpEwsConnector.FolderInfo> folders = ewsConnector.listFolders(
                    task.getSourceEwsUrl(),
                    task.getSourceEmail(),
                    task.getSourcePassword()
            );

            task.setTotalFolders((long) folders.size());
            taskRepository.save(task);

            // 2. 为每个文件夹创建进度记录
            for (MvpEwsConnector.FolderInfo folder : folders) {
                if (folderProgressRepository.findByTaskIdAndFolderName(taskId, folder.getName()).isPresent()) {
                    continue;
                }
                MvpFolderProgress progress = MvpFolderProgress.builder()
                        .taskId(taskId)
                        .folderName(folder.getName())
                        .folderPath(folder.getPath())
                        .displayName(folder.getName())
                        .totalEmails((long) folder.getTotalCount())
                        .status("pending")
                        .build();
                folderProgressRepository.save(progress);
            }

            // Recalculate initial counts (in case of retry)
            long currentMigrated = migratedEmailRepository.countByTaskIdAndSuccess(taskId, true);
            long currentFailed = migratedEmailRepository.countByTaskIdAndSuccess(taskId, false);
            task.setMigratedEmails(currentMigrated);
            task.setFailedEmails(currentFailed);
            taskRepository.save(task);

            broadcastProgress(task);

            // 3. 逐个文件夹迁移
            long totalEmails = 0;
            long migratedEmails = currentMigrated;
            long failedEmails = currentFailed;

            for (MvpEwsConnector.FolderInfo folder : folders) {
                if (folder.getTotalCount() == 0) {
                    // 跳过空文件夹，标记为完成
                    updateFolderStatus(taskId, folder.getName(), "completed");
                    task.setMigratedFolders(task.getMigratedFolders() + 1);
                    taskRepository.save(task);
                    continue;
                }

                totalEmails += folder.getTotalCount();
                task.setTotalEmails(totalEmails);
                task.setCurrentFolder(folder.getName());
                taskRepository.save(task);
                broadcastProgress(task);

                // 迁移该文件夹
                MigrationResult result = migrateFolder(task, folder);
                migratedEmails += result.success;
                failedEmails += result.failed;

                task.setMigratedEmails(migratedEmails);
                task.setFailedEmails(failedEmails);
                task.setMigratedFolders(task.getMigratedFolders() + 1);
                task.setProgressPercent(calculateProgress(task));
                taskRepository.save(task);
                broadcastProgress(task);
            }

            // 4. 迁移日历事件
            if (Boolean.TRUE.equals(task.getMigrateCalendar())) {
                task.setCurrentFolder("日历");
                taskRepository.save(task);
                broadcastProgress(task);
                migrateCalendar(task);
            }

            // 5. 迁移联系人
            if (Boolean.TRUE.equals(task.getMigrateContacts())) {
                task.setCurrentFolder("联系人");
                taskRepository.save(task);
                broadcastProgress(task);
                migrateContacts(task);
            }

            // 6. 完成 - 检查是否有失败项
            boolean hasFailures = (task.getFailedEmails() != null && task.getFailedEmails() > 0)
                    || (task.getFailedCalendarEvents() != null && task.getFailedCalendarEvents() > 0)
                    || (task.getFailedContacts() != null && task.getFailedContacts() > 0);

            if (hasFailures) {
                task.setStatus(MigrationStatus.COMPLETED_WITH_ERRORS);
                log.warn("Migration completed with errors. Failed: emails={}, calendar={}, contacts={}",
                        task.getFailedEmails(), task.getFailedCalendarEvents(), task.getFailedContacts());
            } else {
                task.setStatus(MigrationStatus.COMPLETED);
            }
            task.setCompletedAt(Instant.now());
            task.setCurrentFolder(null);
            task.setProgressPercent(100);
            taskRepository.save(task);
            broadcastProgress(task);

            log.info("Migration {}. Emails: {}/{}, Calendar: {}/{}, Contacts: {}/{}",
                    hasFailures ? "completed with errors" : "completed",
                    task.getMigratedEmails(), task.getTotalEmails(),
                    task.getMigratedCalendarEvents(), task.getTotalCalendarEvents(),
                    task.getMigratedContacts(), task.getTotalContacts());

        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            task.setStatus(MigrationStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(Instant.now());
            taskRepository.save(task);
            broadcastProgress(task);
        }
    }

    /**
     * 迁移单个文件夹
     */
    private MigrationResult migrateFolder(MvpMigrationTask task, MvpEwsConnector.FolderInfo folder) {
        MigrationResult result = new MigrationResult();
        int offset = 0;

        // 收集本次迁移中看到的所有邮件 ID（用于清理已删除邮件的失败记录）
        java.util.Set<String> seenEmailIds = new java.util.HashSet<>();

        updateFolderStatus(task.getId(), folder.getName(), "in_progress");

        try {
            while (true) {
                // 获取一批邮件
                MvpEwsConnector.EmailListResult emailList = ewsConnector.listEmails(
                        task.getSourceEwsUrl(),
                        task.getSourceEmail(),
                        task.getSourcePassword(),
                        folder.getId(),
                        offset,
                        BATCH_SIZE
                );

                if (emailList.getEmails().isEmpty()) {
                    break;
                }

                // 获取邮件 MIME 内容
                List<String> emailIds = emailList.getEmails().stream()
                        .map(MvpEwsConnector.EmailInfo::getId)
                        .toList();

                // 记录看到的邮件 ID（用于后续清理不存在的失败记录）
                seenEmailIds.addAll(emailIds);

                List<MvpEwsConnector.EmailMimeData> mimeDataList = ewsConnector.getEmailsMimeContent(
                        task.getSourceEwsUrl(),
                        task.getSourceEmail(),
                        task.getSourcePassword(),
                        emailIds
                );

                // 逐个上传到目标
                for (MvpEwsConnector.EmailMimeData mimeData : mimeDataList) {
                    if (mimeData.getError() != null) {
                        recordEmailFailure(task, folder.getName(), mimeData.getEmailId(),
                                mimeData.getSubject(), mimeData.getFromAddress(), mimeData.getReceivedDate(),
                                mimeData.getSize(), mimeData.getError(), result);
                        result.failed++;
                        continue;
                    }

                    // 检查是否已迁移过且成功（用于断点续传）
                    if (migratedEmailRepository.existsByTaskIdAndSourceEmailIdAndSuccess(task.getId(), mimeData.getEmailId(), true)) {
                        log.debug("Email already successfully migrated, skipping: {}", mimeData.getSubject());
                        continue;
                    }

                    if (mimeData.getMimeContent() == null) {
                        recordEmailFailure(task, folder.getName(), mimeData.getEmailId(),
                                mimeData.getSubject(), mimeData.getFromAddress(), mimeData.getReceivedDate(),
                                mimeData.getSize(), "No MIME content", result);
                        result.failed++;
                        continue;
                    }

                    try {
                        // 映射文件夹名称到 IMAP 格式
                        String targetFolder = mapFolderName(folder.getName());
                        imapConnector.uploadEmail(
                                task.getTargetImapHost(),
                                task.getTargetImapPort(),
                                task.getTargetImapSsl(),
                                task.getTargetEmail(),
                                task.getTargetPassword(),
                                targetFolder,
                                mimeData.getMimeContent()
                        );

                        // 如果之前有失败记录，先删除（用于重试成功的情况）
                        migratedEmailRepository.deleteByTaskIdAndSourceEmailId(task.getId(), mimeData.getEmailId());

                        MvpMigratedEmail record = createMigratedEmailRecord(
                                task.getId(), mimeData, folder.getName(), true, null);
                        migratedEmailRepository.save(record);
                        result.success++;
                        log.debug("Email migrated successfully: {}", mimeData.getSubject());
                    } catch (Exception e) {
                        recordEmailFailure(task, folder.getName(), mimeData.getEmailId(),
                                mimeData.getSubject(), mimeData.getFromAddress(), mimeData.getReceivedDate(),
                                mimeData.getSize(), e.getMessage(), result);
                        result.failed++;

                        // Update task-level error details for the first failure
                        if (task.getFailedEmails() == 0 || task.getFailedEmails() == null) {
                            task.setFailedEndpoint(formatEndpoint(task.getTargetImapHost(), task.getTargetImapPort()));
                            task.setFailedRequest(formatRequest("IMAP_UPLOAD", folder.getName(), mimeData.getEmailId()));
                            task.setFailedResponse(truncate(e.getMessage(), 2000));
                        }
                    }

                    // 更新文件夹进度
                    updateFolderProgress(task.getId(), folder.getName(), result.success > 0);

                    // 更新任务进度 (从数据库重新计算，确保准确)
                    long currentMigrated = migratedEmailRepository.countByTaskIdAndSuccess(task.getId(), true);
                    long currentFailed = migratedEmailRepository.countByTaskIdAndSuccess(task.getId(), false);
                    task.setMigratedEmails(currentMigrated);
                    task.setFailedEmails(currentFailed);
                    task.setProgressPercent(calculateProgress(task));
                    taskRepository.save(task);

                    // 每处理10封邮件广播一次进度
                    if ((result.success + result.failed) % 10 == 0) {
                        broadcastProgress(task);
                    }
                }

                offset += BATCH_SIZE;

                if (!emailList.isHasMore()) {
                    break;
                }
            }

            // 清理不存在的失败记录（源邮件已被删除/移动）
            cleanupOrphanedFailedRecords(task.getId(), folder.getName(), seenEmailIds);

            updateFolderStatus(task.getId(), folder.getName(), "completed");

        } catch (Exception e) {
            log.error("Failed to migrate folder {}: {}", folder.getName(), e.getMessage());
            updateFolderStatus(task.getId(), folder.getName(), "failed");

            // Store task-level error details
            task.setFailedEndpoint(formatEndpoint(task.getSourceEwsUrl(), null));
            task.setFailedRequest(formatRequest("EWS_LIST_EMAILS", folder.getName(), null));
            task.setFailedResponse(truncate(e.getMessage(), 2000));

            throw new RuntimeException("Migration failed in folder: " + folder.getName(), e);
        }

        return result;
    }

    /**
     * 删除迁移任务
     */
    @Transactional
    public void deleteTask(Long taskId) {
        // 删除相关的迁移邮件记录
        migratedEmailRepository.deleteByTaskId(taskId);

        // 删除相关的文件夹进度记录
        folderProgressRepository.deleteByTaskId(taskId);

        // 删除任务本身
        taskRepository.deleteById(taskId);

        log.info("Deleted migration task: {}", taskId);
    }

    /**
     * 暂停迁移任务
     */
    @Transactional
    public void pauseTask(Long taskId) {
        MvpMigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (task.getStatus() != MigrationStatus.RUNNING) {
            throw new RuntimeException("Task is not running");
        }

        // 更新状态为暂停
        task.setStatus(MigrationStatus.PAUSED);
        taskRepository.save(task);

        log.info("Paused migration task: {}", taskId);
    }

    /**
     * 清理不存在的失败记录（源邮件已被删除或移动到其他文件夹）
     */
    private void cleanupOrphanedFailedRecords(Long taskId, String folderName, java.util.Set<String> seenEmailIds) {
        List<MvpMigratedEmail> failedRecords = migratedEmailRepository.findByTaskIdAndFolderNameAndSuccess(
                taskId, folderName, false);

        int deletedCount = 0;
        for (MvpMigratedEmail failedRecord : failedRecords) {
            if (!seenEmailIds.contains(failedRecord.getSourceEmailId())) {
                // 源邮件已不存在，删除失败记录
                migratedEmailRepository.delete(failedRecord);
                deletedCount++;
                log.info("Deleted orphaned failed record: {} (source email no longer exists)",
                        failedRecord.getSourceEmailId());
            }
        }

        if (deletedCount > 0) {
            log.info("Cleaned up {} orphaned failed records in folder {}", deletedCount, folderName);
        }
    }

    /**
     * 记录邮件迁移失败
     */
    private void recordEmailFailure(MvpMigrationTask task, String folderName, String emailId,
                                  String subject, String fromAddress, Instant sentDate, Long sizeBytes,
                                  String errorMessage, MigrationResult result) {
        // 先删除旧的失败记录（如果存在），避免重复记录
        migratedEmailRepository.deleteByTaskIdAndSourceEmailId(task.getId(), emailId);

        MvpMigratedEmail record = createMigratedEmailRecord(
                task.getId(), emailId, subject, fromAddress, sentDate, sizeBytes, folderName,
                false, truncate(errorMessage, 1000));
        migratedEmailRepository.save(record);
    }

    /**
     * 创建迁移记录
     */
    private MvpMigratedEmail createMigratedEmailRecord(
            Long taskId, MvpEwsConnector.EmailMimeData mimeData, String folderName,
            boolean success, String errorMessage) {
        return createMigratedEmailRecord(
                taskId, mimeData.getEmailId(), mimeData.getSubject(), mimeData.getFromAddress(),
                mimeData.getReceivedDate(), mimeData.getSize(), folderName, success, errorMessage);
    }

    private MvpMigratedEmail createMigratedEmailRecord(
            Long taskId, String emailId, String subject, String fromAddress, Instant sentDate,
            Long sizeBytes, String folderName, boolean success, String errorMessage) {
        return MvpMigratedEmail.builder()
                .taskId(taskId)
                .sourceEmailId(emailId)
                .folderName(folderName)
                .subject(truncate(subject, 500))
                .fromAddress(fromAddress)
                .sentDate(sentDate)
                .sizeBytes(sizeBytes)
                .success(success)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 格式化端点信息
     */
    private String formatEndpoint(String host, Integer port) {
        if (host != null && host.startsWith("http")) {
            return host;  // URL format
        } else if (port != null) {
            return host + ":" + port;  // IP:Port format
        }
        return host;  // Just host
    }

    /**
     * 格式化请求信息
     */
    private String formatRequest(String operation, String folder, String emailId) {
        Map<String, String> request = new HashMap<>();
        request.put("operation", operation);
        if (folder != null) {
            request.put("folder", folder);
        }
        if (emailId != null) {
            request.put("emailId", emailId);
        }
        try {
            return truncate(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request), 4000);
        } catch (Exception e) {
            return operation;
        }
    }

    /**
     * 映射文件夹名称到 IMAP 标准名称
     */
    private String mapFolderName(String ewsFolderName) {
        // 常见的 EWS 文件夹名称映射到 IMAP
        return switch (ewsFolderName.toLowerCase()) {
            case "inbox", "收件箱" -> "INBOX";
            case "sent items", "已发送邮件", "sent" -> "Sent";
            case "drafts", "草稿" -> "Drafts";
            case "deleted items", "已删除邮件", "trash" -> "Trash";
            case "junk email", "垃圾邮件", "junk", "spam" -> "Junk";
            case "archive", "存档" -> "Archive";
            default -> ewsFolderName;
        };
    }

    private void updateFolderStatus(Long taskId, String folderName, String status) {
        folderProgressRepository.findByTaskIdAndFolderName(taskId, folderName)
                .ifPresent(progress -> {
                    progress.setStatus(status);
                    if ("in_progress".equals(status)) {
                        progress.setStartedAt(Instant.now());
                    } else if ("completed".equals(status) || "failed".equals(status)) {
                        progress.setCompletedAt(Instant.now());
                    }
                    folderProgressRepository.save(progress);
                });
    }

    private void updateFolderProgress(Long taskId, String folderName, boolean success) {
        folderProgressRepository.findByTaskIdAndFolderName(taskId, folderName)
                .ifPresent(progress -> {
                    // 从数据库重新计算文件夹统计，确保准确
                    long migrated = migratedEmailRepository.countByTaskIdAndFolderNameAndSuccess(taskId, folderName, true);
                    long failed = migratedEmailRepository.countByTaskIdAndFolderNameAndSuccess(taskId, folderName, false);

                    progress.setMigratedEmails(migrated);
                    progress.setFailedEmails(failed);
                    folderProgressRepository.save(progress);
                });
    }

    private int calculateProgress(MvpMigrationTask task) {
        if (task.getTotalEmails() == null || task.getTotalEmails() == 0) {
            return 0;
        }
        long processed = task.getMigratedEmails() + task.getFailedEmails();
        return (int) (processed * 100 / task.getTotalEmails());
    }

    /**
     * 获取文件夹进度列表
     */
    public List<MvpFolderProgress> getFolderProgress(Long taskId) {
        return folderProgressRepository.findByTaskIdOrderByFolderNameAsc(taskId);
    }

    /**
     * 获取文件夹中已迁移的邮件列表
     */
    public List<MvpMigratedEmail> getMigratedEmails(Long taskId, String folderName) {
        return migratedEmailRepository.findByTaskIdAndFolderNameOrderByMigratedAtDesc(taskId, folderName);
    }

    /**
     * 广播迁移进度
     */
    private void broadcastProgress(MvpMigrationTask task) {
        try {
            Map<String, Object> progress = new HashMap<>();
            progress.put("taskId", task.getId());
            progress.put("status", task.getStatus().name());
            progress.put("progressPercent", task.getProgressPercent());
            progress.put("totalFolders", task.getTotalFolders());
            progress.put("migratedFolders", task.getMigratedFolders());
            progress.put("totalEmails", task.getTotalEmails());
            progress.put("migratedEmails", task.getMigratedEmails());
            progress.put("failedEmails", task.getFailedEmails());
            progress.put("currentFolder", task.getCurrentFolder());
            progress.put("errorMessage", task.getErrorMessage());
            // 日历进度
            progress.put("totalCalendarEvents", task.getTotalCalendarEvents());
            progress.put("migratedCalendarEvents", task.getMigratedCalendarEvents());
            progress.put("failedCalendarEvents", task.getFailedCalendarEvents());
            // 联系人进度
            progress.put("totalContacts", task.getTotalContacts());
            progress.put("migratedContacts", task.getMigratedContacts());
            progress.put("failedContacts", task.getFailedContacts());

            messagingTemplate.convertAndSend("/topic/migration/" + task.getId() + "/progress", progress);
        } catch (Exception e) {
            log.warn("Failed to broadcast progress: {}", e.getMessage());
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    // ==================== 日历迁移 ====================

    /**
     * 迁移日历事件
     */
    private void migrateCalendar(MvpMigrationTask task) {
        log.info("Starting calendar migration for task {}", task.getId());

        try {
            // 获取日历信息
            MvpEwsConnector.CalendarInfo calendarInfo = ewsConnector.getCalendarInfo(
                    task.getSourceEwsUrl(),
                    task.getSourceEmail(),
                    task.getSourcePassword()
            );

            task.setTotalCalendarEvents((long) calendarInfo.getTotalCount());
            taskRepository.save(task);
            broadcastProgress(task);

            if (calendarInfo.getTotalCount() == 0) {
                log.info("No calendar events to migrate");
                return;
            }

            // 推断或使用配置的 CalDAV URL
            String calDavUrl = task.getTargetCalDavUrl();
            if (calDavUrl == null || calDavUrl.isEmpty()) {
                calDavUrl = calDavConnector.inferCalDavUrl(task.getTargetImapHost(), task.getTargetEmail());
            }

            if (calDavUrl == null) {
                log.warn("CalDAV URL not available, skipping calendar migration");
                return;
            }

            // 分批获取并迁移日历事件
            int offset = 0;
            long migratedEvents = 0;
            long failedEvents = 0;

            while (true) {
                MvpEwsConnector.CalendarEventListResult eventList = ewsConnector.listCalendarEvents(
                        task.getSourceEwsUrl(),
                        task.getSourceEmail(),
                        task.getSourcePassword(),
                        offset,
                        BATCH_SIZE
                );

                if (eventList.getEvents().isEmpty()) {
                    break;
                }

                for (MvpEwsConnector.CalendarEventInfo eventInfo : eventList.getEvents()) {
                    // 检查是否已迁移
                    if (calendarEventRepository.existsByTaskIdAndSourceEventIdAndSuccess(
                            task.getId(), eventInfo.getId(), true)) {
                        continue;
                    }

                    try {
                        // 获取事件详情
                        MvpEwsConnector.CalendarEventDetail eventDetail = ewsConnector.getCalendarEventDetail(
                                task.getSourceEwsUrl(),
                                task.getSourceEmail(),
                                task.getSourcePassword(),
                                eventInfo.getId()
                        );

                        // 转换为 iCalendar 格式
                        String iCalData = calendarTransformer.toICalendar(eventDetail);

                        // 上传到目标
                        String targetEventId = calDavConnector.createEvent(
                                calDavUrl,
                                task.getTargetEmail(),
                                task.getTargetPassword(),
                                iCalData
                        );

                        // 记录成功
                        MvpMigratedCalendarEvent record = MvpMigratedCalendarEvent.builder()
                                .taskId(task.getId())
                                .sourceEventId(eventInfo.getId())
                                .calendarName(calendarInfo.getName())
                                .subject(truncate(eventInfo.getSubject(), 500))
                                .location(truncate(eventInfo.getLocation(), 1000))
                                .startTime(eventInfo.getStartTime())
                                .endTime(eventInfo.getEndTime())
                                .isAllDay(eventInfo.isAllDay())
                                .organizer(eventInfo.getOrganizer())
                                .success(true)
                                .targetEventId(targetEventId)
                                .build();
                        calendarEventRepository.save(record);
                        migratedEvents++;

                    } catch (Exception e) {
                        log.warn("Failed to migrate calendar event {}: {}", eventInfo.getId(), e.getMessage());
                        MvpMigratedCalendarEvent record = MvpMigratedCalendarEvent.builder()
                                .taskId(task.getId())
                                .sourceEventId(eventInfo.getId())
                                .calendarName(calendarInfo.getName())
                                .subject(truncate(eventInfo.getSubject(), 500))
                                .startTime(eventInfo.getStartTime())
                                .endTime(eventInfo.getEndTime())
                                .success(false)
                                .errorMessage(truncate(e.getMessage(), 1000))
                                .build();
                        calendarEventRepository.save(record);
                        failedEvents++;
                    }

                    // 更新进度
                    task.setMigratedCalendarEvents(migratedEvents);
                    task.setFailedCalendarEvents(failedEvents);
                    taskRepository.save(task);

                    if ((migratedEvents + failedEvents) % 10 == 0) {
                        broadcastProgress(task);
                    }
                }

                offset += BATCH_SIZE;
                if (!eventList.isHasMore()) {
                    break;
                }
            }

            log.info("Calendar migration completed. Migrated: {}, Failed: {}", migratedEvents, failedEvents);

        } catch (Exception e) {
            log.error("Calendar migration failed: {}", e.getMessage(), e);
            task.setErrorMessage("Calendar migration failed: " + e.getMessage());
            taskRepository.save(task);
        }
    }

    // ==================== 联系人迁移 ====================

    /**
     * 迁移联系人
     */
    private void migrateContacts(MvpMigrationTask task) {
        log.info("Starting contacts migration for task {}", task.getId());

        try {
            // 获取联系人文件夹信息
            MvpEwsConnector.ContactFolderInfo contactFolder = ewsConnector.getContactFolderInfo(
                    task.getSourceEwsUrl(),
                    task.getSourceEmail(),
                    task.getSourcePassword()
            );

            task.setTotalContacts((long) contactFolder.getTotalCount());
            taskRepository.save(task);
            broadcastProgress(task);

            if (contactFolder.getTotalCount() == 0) {
                log.info("No contacts to migrate");
                return;
            }

            // 推断或使用配置的 CardDAV URL
            String cardDavUrl = task.getTargetCardDavUrl();
            if (cardDavUrl == null || cardDavUrl.isEmpty()) {
                cardDavUrl = cardDavConnector.inferCardDavUrl(task.getTargetImapHost(), task.getTargetEmail());
            }

            if (cardDavUrl == null) {
                log.warn("CardDAV URL not available, skipping contacts migration");
                return;
            }

            // 分批获取并迁移联系人
            int offset = 0;
            long migratedContacts = 0;
            long failedContacts = 0;

            while (true) {
                MvpEwsConnector.ContactListResult contactList = ewsConnector.listContacts(
                        task.getSourceEwsUrl(),
                        task.getSourceEmail(),
                        task.getSourcePassword(),
                        offset,
                        BATCH_SIZE
                );

                if (contactList.getContacts().isEmpty()) {
                    break;
                }

                for (MvpEwsConnector.ContactInfo contactInfo : contactList.getContacts()) {
                    // 检查是否已迁移
                    if (contactRepository.existsByTaskIdAndSourceContactIdAndSuccess(
                            task.getId(), contactInfo.getId(), true)) {
                        continue;
                    }

                    try {
                        // 获取联系人详情
                        MvpEwsConnector.ContactDetail contactDetail = ewsConnector.getContactDetail(
                                task.getSourceEwsUrl(),
                                task.getSourceEmail(),
                                task.getSourcePassword(),
                                contactInfo.getId()
                        );

                        // 转换为 vCard 格式
                        String vCardData = contactTransformer.toVCard(contactDetail);

                        // 上传到目标
                        String targetContactId = cardDavConnector.createContact(
                                cardDavUrl,
                                task.getTargetEmail(),
                                task.getTargetPassword(),
                                vCardData
                        );

                        // 记录成功
                        MvpMigratedContact record = MvpMigratedContact.builder()
                                .taskId(task.getId())
                                .sourceContactId(contactInfo.getId())
                                .folderName(contactFolder.getName())
                                .displayName(truncate(contactInfo.getDisplayName(), 200))
                                .firstName(truncate(contactInfo.getFirstName(), 100))
                                .lastName(truncate(contactInfo.getLastName(), 100))
                                .company(truncate(contactInfo.getCompany(), 200))
                                .jobTitle(truncate(contactInfo.getJobTitle(), 100))
                                .success(true)
                                .targetContactId(targetContactId)
                                .build();
                        contactRepository.save(record);
                        migratedContacts++;

                    } catch (Exception e) {
                        log.warn("Failed to migrate contact {}: {}", contactInfo.getId(), e.getMessage());
                        MvpMigratedContact record = MvpMigratedContact.builder()
                                .taskId(task.getId())
                                .sourceContactId(contactInfo.getId())
                                .folderName(contactFolder.getName())
                                .displayName(truncate(contactInfo.getDisplayName(), 200))
                                .success(false)
                                .errorMessage(truncate(e.getMessage(), 1000))
                                .build();
                        contactRepository.save(record);
                        failedContacts++;
                    }

                    // 更新进度
                    task.setMigratedContacts(migratedContacts);
                    task.setFailedContacts(failedContacts);
                    taskRepository.save(task);

                    if ((migratedContacts + failedContacts) % 10 == 0) {
                        broadcastProgress(task);
                    }
                }

                offset += BATCH_SIZE;
                if (!contactList.isHasMore()) {
                    break;
                }
            }

            log.info("Contacts migration completed. Migrated: {}, Failed: {}", migratedContacts, failedContacts);

        } catch (Exception e) {
            log.error("Contacts migration failed: {}", e.getMessage(), e);
            task.setErrorMessage("Contacts migration failed: " + e.getMessage());
            taskRepository.save(task);
        }
    }

    private static class MigrationResult {
        int success = 0;
        int failed = 0;
    }
}
