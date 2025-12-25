package com.migratehero.service.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migratehero.dto.request.CreateMigrationRequest;
import com.migratehero.dto.response.MigrationJobResponse;
import com.migratehero.dto.response.MigrationProgressResponse;
import com.migratehero.exception.BusinessException;
import com.migratehero.exception.ResourceNotFoundException;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.MigrationJob;
import com.migratehero.model.MigrationLog;
import com.migratehero.model.User;
import com.migratehero.model.enums.LogLevel;
import com.migratehero.model.enums.MigrationPhase;
import com.migratehero.model.enums.MigrationStatus;
import com.migratehero.repository.EmailAccountRepository;
import com.migratehero.repository.MigrationJobRepository;
import com.migratehero.repository.MigrationLogRepository;
import com.migratehero.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 迁移任务服务 - 管理邮件/联系人/日历迁移任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationJobService {

    private final MigrationJobRepository migrationJobRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final UserRepository userRepository;
    private final MigrationLogRepository migrationLogRepository;
    private final ObjectMapper objectMapper;
    private final MigrationEngine migrationEngine;

    /**
     * 创建迁移任务
     */
    @Transactional
    public MigrationJobResponse createJob(Long userId, CreateMigrationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        EmailAccount sourceAccount = emailAccountRepository.findByIdAndUser(request.getSourceAccountId(), user)
                .orElseThrow(() -> new ResourceNotFoundException("Source Account", "id", request.getSourceAccountId()));

        EmailAccount targetAccount = emailAccountRepository.findByIdAndUser(request.getTargetAccountId(), user)
                .orElseThrow(() -> new ResourceNotFoundException("Target Account", "id", request.getTargetAccountId()));

        // 验证源和目标账户不同
        if (sourceAccount.getId().equals(targetAccount.getId())) {
            throw new BusinessException("INVALID_ACCOUNTS", "Source and target accounts must be different");
        }

        // 验证账户状态
        if (!sourceAccount.isUsable()) {
            throw new BusinessException("INVALID_SOURCE", "Source account is not connected or token expired");
        }
        if (!targetAccount.isUsable()) {
            throw new BusinessException("INVALID_TARGET", "Target account is not connected or token expired");
        }

        // 构建数据类型配置
        String dataTypesConfig = buildDataTypesConfig(request);

        MigrationJob job = MigrationJob.builder()
                .user(user)
                .sourceAccount(sourceAccount)
                .targetAccount(targetAccount)
                .name(request.getName())
                .description(request.getDescription())
                .phase(MigrationPhase.INITIAL_SYNC)
                .status(request.getScheduledAt() != null ? MigrationStatus.SCHEDULED : MigrationStatus.DRAFT)
                .dataTypesConfig(dataTypesConfig)
                .scheduledAt(request.getScheduledAt())
                .build();

        job = migrationJobRepository.save(job);
        logJobEvent(job, LogLevel.INFO, "Migration job created: " + job.getName());

        log.info("Created migration job: {} for user: {}", job.getId(), user.getUsername());
        return MigrationJobResponse.fromEntity(job);
    }

    /**
     * 获取用户的迁移任务列表
     */
    @Transactional(readOnly = true)
    public Page<MigrationJobResponse> getUserJobs(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return migrationJobRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(MigrationJobResponse::fromEntity);
    }

    /**
     * 获取单个迁移任务
     */
    @Transactional(readOnly = true)
    public MigrationJobResponse getJob(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);
        return MigrationJobResponse.fromEntity(job);
    }

    /**
     * 启动迁移任务
     */
    @Transactional
    public void startJob(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (!job.canStart()) {
            throw new BusinessException("CANNOT_START", "Migration job cannot be started in current state: " + job.getStatus());
        }

        job.setStatus(MigrationStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        migrationJobRepository.save(job);

        logJobEvent(job, LogLevel.INFO, "Migration job started");
        log.info("Started migration job: {}", jobId);

        // 异步执行迁移
        executeMigrationAsync(job);
    }

    /**
     * 暂停迁移任务
     */
    @Transactional
    public void pauseJob(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (!job.canPause()) {
            throw new BusinessException("CANNOT_PAUSE", "Migration job cannot be paused in current state: " + job.getStatus());
        }

        job.setStatus(MigrationStatus.PAUSED);
        migrationJobRepository.save(job);

        logJobEvent(job, LogLevel.INFO, "Migration job paused");
        log.info("Paused migration job: {}", jobId);
    }

    /**
     * 恢复迁移任务
     */
    @Transactional
    public void resumeJob(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (!job.canResume()) {
            throw new BusinessException("CANNOT_RESUME", "Migration job cannot be resumed in current state: " + job.getStatus());
        }

        job.setStatus(MigrationStatus.RUNNING);
        migrationJobRepository.save(job);

        logJobEvent(job, LogLevel.INFO, "Migration job resumed");
        log.info("Resumed migration job: {}", jobId);

        // 异步执行迁移
        executeMigrationAsync(job);
    }

    /**
     * 取消迁移任务
     */
    @Transactional
    public void cancelJob(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (!job.canCancel()) {
            throw new BusinessException("CANNOT_CANCEL", "Migration job cannot be cancelled in current state: " + job.getStatus());
        }

        job.setStatus(MigrationStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        migrationJobRepository.save(job);

        logJobEvent(job, LogLevel.INFO, "Migration job cancelled");
        log.info("Cancelled migration job: {}", jobId);
    }

    /**
     * 删除迁移任务
     */
    @Transactional
    public void deleteJob(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (job.getStatus() == MigrationStatus.RUNNING) {
            throw new BusinessException("CANNOT_DELETE", "Cannot delete a running migration job");
        }

        migrationJobRepository.delete(job);
        log.info("Deleted migration job: {}", jobId);
    }

    /**
     * 获取迁移进度
     */
    @Transactional(readOnly = true)
    public MigrationProgressResponse getProgress(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

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

    /**
     * 异步执行迁移
     */
    public void executeMigrationAsync(MigrationJob job) {
        migrationEngine.executeMigration(job);
    }

    /**
     * 触发增量同步
     */
    @Transactional
    public void triggerIncrementalSync(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (job.getPhase() != MigrationPhase.INCREMENTAL_SYNC) {
            throw new BusinessException("INVALID_PHASE", "Job is not in incremental sync phase");
        }

        if (job.getStatus() != MigrationStatus.RUNNING) {
            throw new BusinessException("NOT_RUNNING", "Job is not running");
        }

        migrationEngine.executeMigration(job);
    }

    /**
     * 触发 Go Live
     */
    @Transactional
    public void triggerGoLive(Long userId, Long jobId) {
        MigrationJob job = getJobEntity(userId, jobId);

        if (job.getPhase() != MigrationPhase.INCREMENTAL_SYNC) {
            throw new BusinessException("INVALID_PHASE", "Job must be in incremental sync phase to go live");
        }

        job.setPhase(MigrationPhase.GO_LIVE);
        migrationJobRepository.save(job);

        logJobEvent(job, LogLevel.INFO, "Go Live triggered");

        migrationEngine.executeMigration(job);
    }

    private MigrationJob getJobEntity(Long userId, Long jobId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return migrationJobRepository.findByIdAndUser(jobId, user)
                .orElseThrow(() -> new ResourceNotFoundException("MigrationJob", "id", jobId));
    }

    private String buildDataTypesConfig(CreateMigrationRequest request) {
        Map<String, Boolean> config = new HashMap<>();
        config.put("emails", request.isMigrateEmails());
        config.put("contacts", request.isMigrateContacts());
        config.put("calendars", request.isMigrateCalendars());

        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            return "{\"emails\":true,\"contacts\":true,\"calendars\":true}";
        }
    }

    private void logJobEvent(MigrationJob job, LogLevel level, String message) {
        MigrationLog logEntry = MigrationLog.builder()
                .job(job)
                .level(level)
                .message(message)
                .build();
        migrationLogRepository.save(logEntry);
    }

    private Integer calculatePercent(Long current, Long total) {
        if (total == null || total == 0) {
            return 0;
        }
        return (int) ((current * 100) / total);
    }
}
