package com.migratehero.controller;

import com.migratehero.dto.request.MvpMigrationRequest;
import com.migratehero.dto.request.TestConnectionRequest;
import com.migratehero.dto.response.FolderProgressResponse;
import com.migratehero.dto.response.MigratedEmailResponse;
import com.migratehero.dto.response.MvpTaskResponse;
import com.migratehero.model.MvpMigrationTask;
import com.migratehero.model.enums.MigrationStatus;
import com.migratehero.service.MvpMigrationService;
import com.migratehero.service.connector.ews.MvpEwsConnector;
import com.migratehero.service.connector.imap.ImapConnector;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MVP 迁移 API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mvp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MvpMigrationController {

    private final MvpMigrationService migrationService;
    private final MvpEwsConnector ewsConnector;
    private final ImapConnector imapConnector;

    /**
     * 测试连接
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@Valid @RequestBody TestConnectionRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean success;
            if ("ews".equalsIgnoreCase(request.getType())) {
                success = ewsConnector.testConnection(
                        request.getEwsUrl(),
                        request.getEmail(),
                        request.getPassword()
                );
            } else if ("imap".equalsIgnoreCase(request.getType())) {
                success = imapConnector.testConnection(
                        request.getImapHost(),
                        request.getImapPort(),
                        request.getImapSsl(),
                        request.getEmail(),
                        request.getPassword()
                );
            } else {
                result.put("success", false);
                result.put("message", "Invalid connection type. Use 'ews' or 'imap'");
                return ResponseEntity.badRequest().body(result);
            }

            result.put("success", success);
            result.put("message", success ? "Connection successful" : "Connection failed");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 检查邮箱是否已有活跃任务
     */
    @GetMapping("/check-duplicate")
    public ResponseEntity<Map<String, Object>> checkDuplicate(@RequestParam String email) {
        Map<String, Object> result = new HashMap<>();
        boolean exists = migrationService.hasActiveTask(email);
        result.put("exists", exists);
        result.put("message", exists ? "该邮箱已有进行中的迁移任务" : "可以创建新任务");
        return ResponseEntity.ok(result);
    }

    /**
     * 创建迁移任务
     */
    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@Valid @RequestBody MvpMigrationRequest request) {
        // 检查是否有重复任务
        if (migrationService.hasActiveTask(request.getSourceEmail())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "该源邮箱已有进行中的迁移任务，请等待完成后再创建新任务");
            return ResponseEntity.badRequest().body(error);
        }

        MvpMigrationTask task = MvpMigrationTask.builder()
                .sourceEwsUrl(request.getSourceEwsUrl())
                .sourceEmail(request.getSourceEmail())
                .sourcePassword(request.getSourcePassword())
                .targetImapHost(request.getTargetImapHost())
                .targetImapPort(request.getTargetImapPort())
                .targetImapSsl(request.getTargetImapSsl())
                .targetEmail(request.getTargetEmail())
                .targetPassword(request.getTargetPassword())
                .build();

        MvpMigrationTask saved = migrationService.createTask(task);
        return ResponseEntity.ok(MvpTaskResponse.from(saved));
    }

    /**
     * 更新迁移任务
     */
    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable Long taskId, @Valid @RequestBody MvpMigrationRequest request) {
        try {
            MvpMigrationTask task = MvpMigrationTask.builder()
                    .sourceEwsUrl(request.getSourceEwsUrl())
                    .sourceEmail(request.getSourceEmail())
                    .sourcePassword(request.getSourcePassword())
                    .targetImapHost(request.getTargetImapHost())
                    .targetImapPort(request.getTargetImapPort())
                    .targetImapSsl(request.getTargetImapSsl())
                    .targetEmail(request.getTargetEmail())
                    .targetPassword(request.getTargetPassword())
                    .build();

            MvpMigrationTask updated = migrationService.updateTask(taskId, task);
            return ResponseEntity.ok(MvpTaskResponse.from(updated));
        } catch (Exception e) {
            log.error("Failed to update task: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取所有任务
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<MvpTaskResponse>> getAllTasks() {
        List<MvpTaskResponse> tasks = migrationService.getAllTasks().stream()
                .map(MvpTaskResponse::from)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<MvpTaskResponse> getTask(@PathVariable Long taskId) {
        return migrationService.getTask(taskId)
                .map(task -> ResponseEntity.ok(MvpTaskResponse.from(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 启动迁移任务
     */
    @PostMapping("/tasks/{taskId}/start")
    public ResponseEntity<Map<String, Object>> startMigration(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();

        return migrationService.getTask(taskId)
                .map(task -> {
                    if (task.getStatus() == MigrationStatus.RUNNING) {
                        result.put("success", false);
                        result.put("message", "Task is already running");
                        return ResponseEntity.badRequest().body(result);
                    }

                    // 异步启动迁移
                    migrationService.startMigration(taskId);

                    result.put("success", true);
                    result.put("message", "Migration started");
                    result.put("taskId", taskId);
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    result.put("success", false);
                    result.put("message", "Task not found");
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * 获取文件夹进度列表
     */
    @GetMapping("/tasks/{taskId}/folders")
    public ResponseEntity<List<FolderProgressResponse>> getFolderProgress(@PathVariable Long taskId) {
        List<FolderProgressResponse> folders = migrationService.getFolderProgress(taskId).stream()
                .map(FolderProgressResponse::from)
                .toList();
        return ResponseEntity.ok(folders);
    }

    /**
     * 获取文件夹中已迁移的邮件列表
     */
    @GetMapping("/tasks/{taskId}/folders/{folderName}/emails")
    public ResponseEntity<List<MigratedEmailResponse>> getMigratedEmails(
            @PathVariable Long taskId,
            @PathVariable String folderName) {
        List<MigratedEmailResponse> emails = migrationService.getMigratedEmails(taskId, folderName).stream()
                .map(MigratedEmailResponse::from)
                .toList();
        return ResponseEntity.ok(emails);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("service", "MigrateHero MVP");
        return ResponseEntity.ok(result);
    }

    /**
     * 删除迁移任务
     */
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            MvpMigrationTask task = migrationService.getTask(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found"));

            // 允许删除 DRAFT、PAUSED 和 FAILED 状态的任务
            if (task.getStatus() != MigrationStatus.DRAFT
                && task.getStatus() != MigrationStatus.PAUSED
                && task.getStatus() != MigrationStatus.FAILED) {
                result.put("success", false);
                result.put("message", "只能删除草稿、已暂停或失败状态的任务");
                return ResponseEntity.badRequest().body(result);
            }

            migrationService.deleteTask(taskId);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to delete task: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 暂停迁移任务
     */
    @PostMapping("/tasks/{taskId}/pause")
    public ResponseEntity<Map<String, Object>> pauseTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();

        return migrationService.getTask(taskId)
                .map(task -> {
                    if (task.getStatus() != MigrationStatus.RUNNING) {
                        result.put("success", false);
                        result.put("message", "只能暂停运行中的任务");
                        return ResponseEntity.badRequest().body(result);
                    }

                    migrationService.pauseTask(taskId);

                    result.put("success", true);
                    result.put("message", "任务已暂停");
                    result.put("taskId", taskId);
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    result.put("success", false);
                    result.put("message", "任务不存在");
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * 重试迁移任务
     */
    @PostMapping("/tasks/{taskId}/retry")
    public ResponseEntity<Map<String, Object>> retryTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            MvpMigrationTask task = migrationService.getTask(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found"));

            // 只允许重试 FAILED 状态的任务
            if (task.getStatus() != MigrationStatus.FAILED) {
                result.put("success", false);
                result.put("message", "只能重试失败状态的任务");
                return ResponseEntity.badRequest().body(result);
            }

            // 直接重启任务，而不是创建新任务
            migrationService.startMigration(task.getId());

            result.put("success", true);
            result.put("message", "任务已重试");
            result.put("taskId", task.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to retry task: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "重试失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
