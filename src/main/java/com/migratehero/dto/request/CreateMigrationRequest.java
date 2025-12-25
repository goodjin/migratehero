package com.migratehero.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建迁移任务请求
 */
@Data
public class CreateMigrationRequest {

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "源账户ID不能为空")
    private Long sourceAccountId;

    @NotNull(message = "目标账户ID不能为空")
    private Long targetAccountId;

    private boolean migrateEmails = true;

    private boolean migrateContacts = true;

    private boolean migrateCalendars = true;

    private LocalDateTime scheduledAt;
}
