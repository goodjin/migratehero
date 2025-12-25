package com.migratehero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MigrateHero - 智能数据库迁移平台
 *
 * 核心功能：
 * 1. 多数据库类型支持 (MySQL, PostgreSQL, Oracle, SQL Server, MongoDB)
 * 2. 可视化迁移配置
 * 3. 实时迁移进度监控
 * 4. 数据完整性验证
 * 5. 零停机迁移支持
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MigrateHeroApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrateHeroApplication.class, args);
    }
}
