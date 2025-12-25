package com.migratehero.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MVP 已迁移联系人记录 - 保存每个联系人的迁移状态
 */
@Entity
@Table(name = "mvp_migrated_contact", indexes = {
        @Index(name = "idx_contact_task", columnList = "taskId"),
        @Index(name = "idx_contact_source_id", columnList = "sourceContactId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MvpMigratedContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    // 源联系人信息
    @Column(nullable = false)
    private String sourceContactId;

    @Column
    private String folderName;

    @Column(length = 200)
    private String displayName;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 200)
    private String company;

    @Column(length = 100)
    private String jobTitle;

    // 邮箱地址（可能有多个，用JSON存储）
    @Column(length = 1000)
    private String emailAddresses;

    // 电话号码（可能有多个，用JSON存储）
    @Column(length = 1000)
    private String phoneNumbers;

    // 地址信息
    @Column(length = 500)
    private String businessAddress;

    @Column(length = 500)
    private String homeAddress;

    // 备注
    @Column(length = 4000)
    private String notes;

    // 迁移状态
    @Column
    @Builder.Default
    private Boolean success = false;

    @Column(length = 1000)
    private String errorMessage;

    // 目标联系人ID（迁移成功后的ID）
    private String targetContactId;

    @Column
    private Instant migratedAt;

    @PrePersist
    protected void onCreate() {
        if (migratedAt == null) {
            migratedAt = Instant.now();
        }
    }
}
