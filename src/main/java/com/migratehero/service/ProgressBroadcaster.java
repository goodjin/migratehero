package com.migratehero.service;

import com.migratehero.dto.response.MigrationProgressResponse;
import com.migratehero.model.MigrationJob;
import com.migratehero.model.MigrationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 进度广播服务 - 通过 WebSocket 推送迁移进度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 广播迁移进度
     */
    public void broadcastProgress(MigrationJob job) {
        MigrationProgressResponse progress = buildProgressResponse(job);
        String destination = "/topic/migration/" + job.getId() + "/progress";

        messagingTemplate.convertAndSend(destination, progress);
        log.debug("Broadcasted progress for job {}: {}%", job.getId(), progress.getOverallProgressPercent());
    }

    /**
     * 广播日志消息
     */
    public void broadcastLog(MigrationJob job, MigrationLog logEntry) {
        String destination = "/topic/migration/" + job.getId() + "/log";

        LogMessage message = LogMessage.builder()
                .jobId(job.getId())
                .level(logEntry.getLevel().name())
                .dataType(logEntry.getDataType() != null ? logEntry.getDataType().name() : null)
                .message(logEntry.getMessage())
                .itemId(logEntry.getItemId())
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSend(destination, message);
        log.debug("Broadcasted log for job {}: {}", job.getId(), logEntry.getMessage());
    }

    /**
     * 广播任务状态变更
     */
    public void broadcastStatusChange(MigrationJob job) {
        String destination = "/topic/migration/" + job.getId() + "/status";

        StatusMessage message = StatusMessage.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .phase(job.getPhase().name())
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSend(destination, message);
        log.info("Broadcasted status change for job {}: {} / {}", job.getId(), job.getStatus(), job.getPhase());
    }

    /**
     * 向特定用户发送通知
     */
    public void sendUserNotification(Long userId, String message) {
        String destination = "/queue/notifications";
        NotificationMessage notification = NotificationMessage.builder()
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(String.valueOf(userId), destination, notification);
    }

    private MigrationProgressResponse buildProgressResponse(MigrationJob job) {
        return MigrationProgressResponse.builder()
                .jobId(job.getId())
                .phase(job.getPhase())
                .status(job.getStatus())
                .overallProgressPercent(job.getOverallProgressPercent())
                .totalEmails(job.getTotalEmails())
                .migratedEmails(job.getMigratedEmails())
                .failedEmails(job.getFailedEmails())
                .emailProgressPercent(calculatePercent(job.getMigratedEmails(), job.getTotalEmails()))
                .totalContacts(job.getTotalContacts())
                .migratedContacts(job.getMigratedContacts())
                .failedContacts(job.getFailedContacts())
                .contactProgressPercent(calculatePercent(job.getMigratedContacts(), job.getTotalContacts()))
                .totalEvents(job.getTotalEvents())
                .migratedEvents(job.getMigratedEvents())
                .failedEvents(job.getFailedEvents())
                .eventProgressPercent(calculatePercent(job.getMigratedEvents(), job.getTotalEvents()))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private Integer calculatePercent(Long current, Long total) {
        if (total == null || total == 0) {
            return 0;
        }
        return (int) ((current * 100) / total);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogMessage {
        private Long jobId;
        private String level;
        private String dataType;
        private String message;
        private String itemId;
        private Long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatusMessage {
        private Long jobId;
        private String status;
        private String phase;
        private Long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NotificationMessage {
        private String message;
        private Long timestamp;
    }
}
