package com.migratehero.model.enums;

/**
 * 迁移任务状态
 */
public enum MigrationStatus {
    DRAFT,      // 草稿
    SCHEDULED,  // 已计划
    RUNNING,    // 运行中
    PAUSED,     // 已暂停
    COMPLETED,  // 已完成
    FAILED,     // 失败
    CANCELLED   // 已取消
}
