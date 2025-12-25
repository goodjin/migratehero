package com.migratehero.controller;

import com.migratehero.dto.request.CreateMigrationRequest;
import com.migratehero.dto.response.ApiResponse;
import com.migratehero.dto.response.MigrationJobResponse;
import com.migratehero.dto.response.MigrationProgressResponse;
import com.migratehero.security.CurrentUser;
import com.migratehero.security.UserPrincipal;
import com.migratehero.service.migration.MigrationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 迁移任务控制器 - 邮件迁移任务管理
 */
@RestController
@RequestMapping("/api/v1/migrations")
@RequiredArgsConstructor
@Tag(name = "Migration API", description = "邮件迁移任务管理接口")
public class MigrationController {

    private final MigrationJobService migrationJobService;

    @Operation(summary = "获取所有迁移任务")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<MigrationJobResponse>>> getAllJobs(
            @CurrentUser UserPrincipal currentUser,
            Pageable pageable) {
        Page<MigrationJobResponse> jobs = migrationJobService.getUserJobs(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @Operation(summary = "获取单个迁移任务")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MigrationJobResponse>> getJob(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        MigrationJobResponse job = migrationJobService.getJob(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    @Operation(summary = "创建迁移任务")
    @PostMapping
    public ResponseEntity<ApiResponse<MigrationJobResponse>> createJob(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody CreateMigrationRequest request) {
        MigrationJobResponse job = migrationJobService.createJob(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Migration job created", job));
    }

    @Operation(summary = "启动迁移任务")
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<Void>> startMigration(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        migrationJobService.startJob(currentUser.getId(), id);
        return ResponseEntity.accepted()
                .body(ApiResponse.success("Migration started"));
    }

    @Operation(summary = "暂停迁移任务")
    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<Void>> pauseMigration(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        migrationJobService.pauseJob(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Migration paused"));
    }

    @Operation(summary = "恢复迁移任务")
    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<Void>> resumeMigration(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        migrationJobService.resumeJob(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Migration resumed"));
    }

    @Operation(summary = "取消迁移任务")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMigration(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        migrationJobService.cancelJob(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Migration cancelled"));
    }

    @Operation(summary = "获取迁移进度")
    @GetMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<MigrationProgressResponse>> getProgress(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        MigrationProgressResponse progress = migrationJobService.getProgress(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(progress));
    }

    @Operation(summary = "删除迁移任务")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        migrationJobService.deleteJob(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Migration job deleted"));
    }
}
