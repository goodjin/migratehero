package com.migratehero.service.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.MigrationJob;
import com.migratehero.model.MigrationLog;
import com.migratehero.model.enums.DataType;
import com.migratehero.model.enums.LogLevel;
import com.migratehero.model.enums.MigrationPhase;
import com.migratehero.model.enums.MigrationStatus;
import com.migratehero.model.dto.CalendarEvent;
import com.migratehero.model.dto.Contact;
import com.migratehero.model.dto.EmailMessage;
import com.migratehero.repository.MigrationJobRepository;
import com.migratehero.repository.MigrationLogRepository;
import com.migratehero.service.ProgressBroadcaster;
import com.migratehero.service.connector.*;
import com.migratehero.service.transform.CalendarTransformer;
import com.migratehero.service.transform.ContactTransformer;
import com.migratehero.service.transform.EmailTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 迁移引擎 - 核心迁移逻辑编排器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationEngine {

    private final ConnectorFactory connectorFactory;
    private final EmailTransformer emailTransformer;
    private final ContactTransformer contactTransformer;
    private final CalendarTransformer calendarTransformer;
    private final CheckpointService checkpointService;
    private final MigrationJobRepository jobRepository;
    private final MigrationLogRepository logRepository;
    private final ProgressBroadcaster progressBroadcaster;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 50;

    /**
     * 异步执行迁移任务
     */
    @Async("migrationTaskExecutor")
    public void executeMigration(MigrationJob job) {
        log.info("Starting migration job: {}", job.getId());

        try {
            // 解析数据类型配置
            Map<String, Boolean> dataTypes = parseDataTypesConfig(job.getDataTypesConfig());

            // 根据阶段执行不同逻辑
            switch (job.getPhase()) {
                case INITIAL_SYNC -> executeInitialSync(job, dataTypes);
                case INCREMENTAL_SYNC -> executeIncrementalSync(job, dataTypes);
                case GO_LIVE -> executeGoLive(job, dataTypes);
            }

        } catch (Exception e) {
            log.error("Migration job {} failed", job.getId(), e);
            markJobFailed(job, e.getMessage());
        }
    }

    /**
     * 执行初始同步 - Phase 1
     */
    private void executeInitialSync(MigrationJob job, Map<String, Boolean> dataTypes) {
        log.info("Executing initial sync for job: {}", job.getId());
        logJobEvent(job, LogLevel.INFO, "Starting initial sync phase");

        try {
            // 迁移邮件
            if (Boolean.TRUE.equals(dataTypes.get("emails"))) {
                migrateEmails(job);
            }

            // 迁移联系人
            if (Boolean.TRUE.equals(dataTypes.get("contacts"))) {
                migrateContacts(job);
            }

            // 迁移日历
            if (Boolean.TRUE.equals(dataTypes.get("calendars"))) {
                migrateCalendars(job);
            }

            // 初始同步完成，进入增量同步阶段
            transitionToIncrementalSync(job);

        } catch (Exception e) {
            log.error("Initial sync failed for job: {}", job.getId(), e);
            throw e;
        }
    }

    /**
     * 迁移邮件
     */
    private void migrateEmails(MigrationJob job) {
        log.info("Migrating emails for job: {}", job.getId());
        logJobEvent(job, LogLevel.INFO, "Starting email migration");

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        EmailConnector sourceConnector = connectorFactory.getEmailConnector(source);
        EmailConnector targetConnector = connectorFactory.getEmailConnector(target);

        // 获取邮件统计
        EmailConnector.EmailStats stats = sourceConnector.getStats(source);
        updateJobProgress(job, DataType.EMAILS, 0L, stats.totalCount());

        String pageToken = checkpointService.getPageToken(job, DataType.EMAILS);
        long migratedCount = job.getMigratedEmails() != null ? job.getMigratedEmails() : 0;
        long failedCount = job.getFailedEmails() != null ? job.getFailedEmails() : 0;

        do {
            // 检查任务状态
            if (!isJobRunning(job.getId())) {
                log.info("Job {} is no longer running, stopping email migration", job.getId());
                return;
            }

            // 读取源邮件
            EmailConnector.EmailListResult result = sourceConnector.listEmails(source, pageToken, BATCH_SIZE);

            for (EmailMessage email : result.emails()) {
                try {
                    // 转换邮件格式
                    EmailMessage transformed = emailTransformer.transform(email, target.getProvider());

                    // 写入目标账户
                    targetConnector.createEmail(target, transformed);

                    migratedCount++;
                } catch (Exception e) {
                    log.error("Failed to migrate email: {}", email.getId(), e);
                    logJobEvent(job, LogLevel.ERROR, "Failed to migrate email: " + email.getId() + " - " + e.getMessage());
                    failedCount++;
                }

                // 更新进度
                if (migratedCount % 10 == 0) {
                    updateJobProgress(job, DataType.EMAILS, migratedCount, stats.totalCount());
                    broadcastProgress(job);
                }
            }

            // 更新检查点
            pageToken = result.nextPageToken();
            checkpointService.updatePageToken(job, DataType.EMAILS, pageToken);

            // 保存同步令牌用于增量同步
            if (result.historyId() != null) {
                checkpointService.updateSyncToken(job, DataType.EMAILS, result.historyId());
            }

        } while (pageToken != null);

        // 标记邮件迁移完成
        checkpointService.markInitialSyncComplete(job, DataType.EMAILS,
                checkpointService.getSyncToken(job, DataType.EMAILS));

        updateJobEmailCounts(job, migratedCount, failedCount, stats.totalCount());
        logJobEvent(job, LogLevel.INFO, "Email migration completed: " + migratedCount + " migrated, " + failedCount + " failed");
    }

    /**
     * 迁移联系人
     */
    private void migrateContacts(MigrationJob job) {
        log.info("Migrating contacts for job: {}", job.getId());
        logJobEvent(job, LogLevel.INFO, "Starting contact migration");

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        ContactConnector sourceConnector = connectorFactory.getContactConnector(source);
        ContactConnector targetConnector = connectorFactory.getContactConnector(target);

        // 获取联系人总数
        long totalCount = sourceConnector.getContactCount(source);
        updateJobProgress(job, DataType.CONTACTS, 0L, totalCount);

        String pageToken = checkpointService.getPageToken(job, DataType.CONTACTS);
        long migratedCount = job.getMigratedContacts() != null ? job.getMigratedContacts() : 0;
        long failedCount = job.getFailedContacts() != null ? job.getFailedContacts() : 0;

        do {
            if (!isJobRunning(job.getId())) {
                return;
            }

            ContactConnector.ContactListResult result = sourceConnector.listContacts(source, pageToken, BATCH_SIZE);

            for (Contact contact : result.contacts()) {
                try {
                    Contact transformed = contactTransformer.transform(contact, target.getProvider());
                    targetConnector.createContact(target, transformed);
                    migratedCount++;
                } catch (Exception e) {
                    log.error("Failed to migrate contact: {}", contact.getId(), e);
                    logJobEvent(job, LogLevel.ERROR, "Failed to migrate contact: " + contact.getId());
                    failedCount++;
                }

                if (migratedCount % 10 == 0) {
                    updateJobProgress(job, DataType.CONTACTS, migratedCount, totalCount);
                    broadcastProgress(job);
                }
            }

            pageToken = result.nextPageToken();
            checkpointService.updatePageToken(job, DataType.CONTACTS, pageToken);

            if (result.syncToken() != null) {
                checkpointService.updateSyncToken(job, DataType.CONTACTS, result.syncToken());
            }

        } while (pageToken != null);

        checkpointService.markInitialSyncComplete(job, DataType.CONTACTS,
                checkpointService.getSyncToken(job, DataType.CONTACTS));

        updateJobContactCounts(job, migratedCount, failedCount, totalCount);
        logJobEvent(job, LogLevel.INFO, "Contact migration completed: " + migratedCount + " migrated, " + failedCount + " failed");
    }

    /**
     * 迁移日历事件
     */
    private void migrateCalendars(MigrationJob job) {
        log.info("Migrating calendars for job: {}", job.getId());
        logJobEvent(job, LogLevel.INFO, "Starting calendar migration");

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        CalendarConnector sourceConnector = connectorFactory.getCalendarConnector(source);
        CalendarConnector targetConnector = connectorFactory.getCalendarConnector(target);

        // 获取源日历列表
        List<CalendarConnector.CalendarInfo> sourceCalendars = sourceConnector.listCalendars(source);
        List<CalendarConnector.CalendarInfo> targetCalendars = targetConnector.listCalendars(target);

        // 找到默认目标日历
        String targetCalendarId = targetCalendars.stream()
                .filter(CalendarConnector.CalendarInfo::isPrimary)
                .findFirst()
                .map(CalendarConnector.CalendarInfo::id)
                .orElse(targetCalendars.isEmpty() ? null : targetCalendars.get(0).id());

        if (targetCalendarId == null) {
            logJobEvent(job, LogLevel.ERROR, "No target calendar found");
            return;
        }

        long totalEvents = 0;
        long migratedCount = job.getMigratedEvents() != null ? job.getMigratedEvents() : 0;
        long failedCount = job.getFailedEvents() != null ? job.getFailedEvents() : 0;

        // 遍历源日历
        for (CalendarConnector.CalendarInfo calendar : sourceCalendars) {
            if (!isJobRunning(job.getId())) {
                return;
            }

            long calendarEventCount = sourceConnector.getEventCount(source, calendar.id());
            totalEvents += calendarEventCount;
            updateJobProgress(job, DataType.CALENDARS, migratedCount, totalEvents);

            String pageToken = null;

            do {
                CalendarConnector.EventListResult result = sourceConnector.listEvents(
                        source, calendar.id(), pageToken, BATCH_SIZE);

                for (CalendarEvent event : result.events()) {
                    try {
                        CalendarEvent transformed = calendarTransformer.transform(event, target.getProvider());
                        targetConnector.createEvent(target, targetCalendarId, transformed);
                        migratedCount++;
                    } catch (Exception e) {
                        log.error("Failed to migrate calendar event: {}", event.getId(), e);
                        logJobEvent(job, LogLevel.ERROR, "Failed to migrate event: " + event.getId());
                        failedCount++;
                    }

                    if (migratedCount % 10 == 0) {
                        updateJobProgress(job, DataType.CALENDARS, migratedCount, totalEvents);
                        broadcastProgress(job);
                    }
                }

                pageToken = result.nextPageToken();

                if (result.syncToken() != null) {
                    checkpointService.updateSyncToken(job, DataType.CALENDARS, result.syncToken());
                }

            } while (pageToken != null);
        }

        checkpointService.markInitialSyncComplete(job, DataType.CALENDARS,
                checkpointService.getSyncToken(job, DataType.CALENDARS));

        updateJobEventCounts(job, migratedCount, failedCount, totalEvents);
        logJobEvent(job, LogLevel.INFO, "Calendar migration completed: " + migratedCount + " migrated, " + failedCount + " failed");
    }

    /**
     * 执行增量同步 - Phase 2
     */
    private void executeIncrementalSync(MigrationJob job, Map<String, Boolean> dataTypes) {
        log.info("Executing incremental sync for job: {}", job.getId());
        logJobEvent(job, LogLevel.INFO, "Starting incremental sync phase");

        try {
            if (Boolean.TRUE.equals(dataTypes.get("emails"))) {
                syncIncrementalEmails(job);
            }

            if (Boolean.TRUE.equals(dataTypes.get("contacts"))) {
                syncIncrementalContacts(job);
            }

            if (Boolean.TRUE.equals(dataTypes.get("calendars"))) {
                syncIncrementalCalendars(job);
            }

            logJobEvent(job, LogLevel.INFO, "Incremental sync completed");
            broadcastProgress(job);

        } catch (Exception e) {
            log.error("Incremental sync failed for job: {}", job.getId(), e);
            throw e;
        }
    }

    /**
     * 增量同步邮件
     */
    private void syncIncrementalEmails(MigrationJob job) {
        String syncToken = checkpointService.getSyncToken(job, DataType.EMAILS);
        if (syncToken == null) {
            log.warn("No sync token for emails, skipping incremental sync");
            return;
        }

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        EmailConnector sourceConnector = connectorFactory.getEmailConnector(source);
        EmailConnector targetConnector = connectorFactory.getEmailConnector(target);

        EmailConnector.IncrementalChanges changes = sourceConnector.getIncrementalChanges(source, syncToken);

        // 处理新增邮件
        for (String emailId : changes.addedIds()) {
            try {
                EmailMessage email = sourceConnector.getEmail(source, emailId);
                if (email != null) {
                    EmailMessage transformed = emailTransformer.transform(email, target.getProvider());
                    targetConnector.createEmail(target, transformed);
                }
            } catch (Exception e) {
                log.error("Failed to sync added email: {}", emailId, e);
            }
        }

        // 更新同步令牌
        checkpointService.updateSyncToken(job, DataType.EMAILS, changes.newHistoryId());

        logJobEvent(job, LogLevel.INFO, "Email incremental sync: " + changes.addedIds().size() + " added, " +
                changes.modifiedIds().size() + " modified, " + changes.deletedIds().size() + " deleted");
    }

    /**
     * 增量同步联系人
     */
    private void syncIncrementalContacts(MigrationJob job) {
        String syncToken = checkpointService.getSyncToken(job, DataType.CONTACTS);
        if (syncToken == null) {
            return;
        }

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        ContactConnector sourceConnector = connectorFactory.getContactConnector(source);
        ContactConnector targetConnector = connectorFactory.getContactConnector(target);

        ContactConnector.IncrementalChanges changes = sourceConnector.getIncrementalChanges(source, syncToken);

        for (Contact contact : changes.added()) {
            try {
                Contact transformed = contactTransformer.transform(contact, target.getProvider());
                targetConnector.createContact(target, transformed);
            } catch (Exception e) {
                log.error("Failed to sync added contact: {}", contact.getId(), e);
            }
        }

        for (Contact contact : changes.modified()) {
            try {
                Contact transformed = contactTransformer.transform(contact, target.getProvider());
                targetConnector.updateContact(target, contact.getId(), transformed);
            } catch (Exception e) {
                log.error("Failed to sync modified contact: {}", contact.getId(), e);
            }
        }

        checkpointService.updateSyncToken(job, DataType.CONTACTS, changes.newSyncToken());
    }

    /**
     * 增量同步日历
     */
    private void syncIncrementalCalendars(MigrationJob job) {
        String syncToken = checkpointService.getSyncToken(job, DataType.CALENDARS);
        if (syncToken == null) {
            return;
        }

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        CalendarConnector sourceConnector = connectorFactory.getCalendarConnector(source);
        CalendarConnector targetConnector = connectorFactory.getCalendarConnector(target);

        // 获取目标默认日历
        List<CalendarConnector.CalendarInfo> targetCalendars = targetConnector.listCalendars(target);
        String targetCalendarId = targetCalendars.stream()
                .filter(CalendarConnector.CalendarInfo::isPrimary)
                .findFirst()
                .map(CalendarConnector.CalendarInfo::id)
                .orElse(targetCalendars.isEmpty() ? null : targetCalendars.get(0).id());

        if (targetCalendarId == null) {
            return;
        }

        // 获取源默认日历
        List<CalendarConnector.CalendarInfo> sourceCalendars = sourceConnector.listCalendars(source);
        String sourceCalendarId = sourceCalendars.stream()
                .filter(CalendarConnector.CalendarInfo::isPrimary)
                .findFirst()
                .map(CalendarConnector.CalendarInfo::id)
                .orElse(sourceCalendars.isEmpty() ? null : sourceCalendars.get(0).id());

        if (sourceCalendarId == null) {
            return;
        }

        CalendarConnector.IncrementalChanges changes = sourceConnector.getIncrementalChanges(
                source, sourceCalendarId, syncToken);

        for (CalendarEvent event : changes.modified()) {
            try {
                CalendarEvent transformed = calendarTransformer.transform(event, target.getProvider());
                targetConnector.createEvent(target, targetCalendarId, transformed);
            } catch (Exception e) {
                log.error("Failed to sync calendar event: {}", event.getId(), e);
            }
        }

        checkpointService.updateSyncToken(job, DataType.CALENDARS, changes.newSyncToken());
    }

    /**
     * 执行 Go Live - Phase 3
     */
    private void executeGoLive(MigrationJob job, Map<String, Boolean> dataTypes) {
        log.info("Executing Go Live for job: {}", job.getId());
        logJobEvent(job, LogLevel.INFO, "Starting Go Live phase");

        // 最终增量同步
        executeIncrementalSync(job, dataTypes);

        // 验证数据完整性
        boolean verified = verifyMigration(job, dataTypes);

        if (verified) {
            markJobCompleted(job);
            logJobEvent(job, LogLevel.INFO, "Migration completed successfully");
        } else {
            logJobEvent(job, LogLevel.WARN, "Migration completed with verification warnings");
            markJobCompleted(job);
        }
    }

    /**
     * 验证迁移结果
     */
    private boolean verifyMigration(MigrationJob job, Map<String, Boolean> dataTypes) {
        log.info("Verifying migration for job: {}", job.getId());

        EmailAccount source = job.getSourceAccount();
        EmailAccount target = job.getTargetAccount();

        boolean verified = true;

        if (Boolean.TRUE.equals(dataTypes.get("emails"))) {
            EmailConnector sourceConnector = connectorFactory.getEmailConnector(source);
            EmailConnector targetConnector = connectorFactory.getEmailConnector(target);

            long sourceCount = sourceConnector.getStats(source).totalCount();
            long targetCount = targetConnector.getStats(target).totalCount();

            // 允许一定误差
            if (Math.abs(sourceCount - targetCount) > sourceCount * 0.01) {
                logJobEvent(job, LogLevel.WARN, "Email count mismatch: source=" + sourceCount + ", target=" + targetCount);
                verified = false;
            }
        }

        return verified;
    }

    /**
     * 切换到增量同步阶段
     */
    @Transactional
    private void transitionToIncrementalSync(MigrationJob job) {
        job.setPhase(MigrationPhase.INCREMENTAL_SYNC);
        jobRepository.save(job);
        logJobEvent(job, LogLevel.INFO, "Transitioned to incremental sync phase");
    }

    /**
     * 标记任务完成
     */
    @Transactional
    private void markJobCompleted(MigrationJob job) {
        job.setStatus(MigrationStatus.COMPLETED);
        job.setPhase(MigrationPhase.GO_LIVE);
        job.setCompletedAt(LocalDateTime.now());
        job.setOverallProgressPercent(100);
        jobRepository.save(job);
        broadcastProgress(job);
    }

    /**
     * 标记任务失败
     */
    @Transactional
    private void markJobFailed(MigrationJob job, String errorMessage) {
        job.setStatus(MigrationStatus.FAILED);
        jobRepository.save(job);
        logJobEvent(job, LogLevel.ERROR, "Migration failed: " + errorMessage);
        broadcastProgress(job);
    }

    /**
     * 检查任务是否仍在运行
     */
    private boolean isJobRunning(Long jobId) {
        return jobRepository.findById(jobId)
                .map(j -> j.getStatus() == MigrationStatus.RUNNING)
                .orElse(false);
    }

    /**
     * 更新任务进度
     */
    @Transactional
    private void updateJobProgress(MigrationJob job, DataType dataType, long migrated, long total) {
        MigrationJob current = jobRepository.findById(job.getId()).orElse(job);

        switch (dataType) {
            case EMAILS -> {
                current.setMigratedEmails(migrated);
                current.setTotalEmails(total);
            }
            case CONTACTS -> {
                current.setMigratedContacts(migrated);
                current.setTotalContacts(total);
            }
            case CALENDARS -> {
                current.setMigratedEvents(migrated);
                current.setTotalEvents(total);
            }
        }

        // 计算总体进度
        int progress = calculateOverallProgress(current);
        current.setOverallProgressPercent(progress);

        jobRepository.save(current);
    }

    private void updateJobEmailCounts(MigrationJob job, long migrated, long failed, long total) {
        MigrationJob current = jobRepository.findById(job.getId()).orElse(job);
        current.setMigratedEmails(migrated);
        current.setFailedEmails(failed);
        current.setTotalEmails(total);
        jobRepository.save(current);
    }

    private void updateJobContactCounts(MigrationJob job, long migrated, long failed, long total) {
        MigrationJob current = jobRepository.findById(job.getId()).orElse(job);
        current.setMigratedContacts(migrated);
        current.setFailedContacts(failed);
        current.setTotalContacts(total);
        jobRepository.save(current);
    }

    private void updateJobEventCounts(MigrationJob job, long migrated, long failed, long total) {
        MigrationJob current = jobRepository.findById(job.getId()).orElse(job);
        current.setMigratedEvents(migrated);
        current.setFailedEvents(failed);
        current.setTotalEvents(total);
        jobRepository.save(current);
    }

    private int calculateOverallProgress(MigrationJob job) {
        long totalItems = (job.getTotalEmails() != null ? job.getTotalEmails() : 0) +
                (job.getTotalContacts() != null ? job.getTotalContacts() : 0) +
                (job.getTotalEvents() != null ? job.getTotalEvents() : 0);

        if (totalItems == 0) {
            return 0;
        }

        long migratedItems = (job.getMigratedEmails() != null ? job.getMigratedEmails() : 0) +
                (job.getMigratedContacts() != null ? job.getMigratedContacts() : 0) +
                (job.getMigratedEvents() != null ? job.getMigratedEvents() : 0);

        return (int) ((migratedItems * 100) / totalItems);
    }

    private void broadcastProgress(MigrationJob job) {
        progressBroadcaster.broadcastProgress(job);
    }

    private void logJobEvent(MigrationJob job, LogLevel level, String message) {
        MigrationLog logEntry = MigrationLog.builder()
                .job(job)
                .level(level)
                .message(message)
                .build();
        logRepository.save(logEntry);

        progressBroadcaster.broadcastLog(job, logEntry);
    }

    private Map<String, Boolean> parseDataTypesConfig(String config) {
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("emails", true, "contacts", true, "calendars", true);
        }
    }
}
