package com.migratehero.model.enums;

/**
 * 迁移阶段
 */
public enum MigrationPhase {
    INITIAL_SYNC,      // 阶段1: 初始全量同步
    INCREMENTAL_SYNC,  // 阶段2: 增量同步
    GO_LIVE,           // 阶段3: 正式切换
    COMPLETED          // 已完成
}
