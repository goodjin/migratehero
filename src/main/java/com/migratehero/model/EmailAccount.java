package com.migratehero.model;

import com.migratehero.model.enums.ConnectionStatus;
import com.migratehero.model.enums.ProviderType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 邮箱账户实体 - 存储用户连接的 Google/Microsoft 账户信息
 */
@Entity
@Table(name = "email_accounts", indexes = {
    @Index(name = "idx_email_account_user", columnList = "user_id"),
    @Index(name = "idx_email_account_email", columnList = "email"),
    @Index(name = "idx_email_account_provider", columnList = "provider")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderType provider;

    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "granted_scopes", columnDefinition = "TEXT")
    private String grantedScopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConnectionStatus status = ConnectionStatus.PENDING;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ConnectionStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 检查 Token 是否已过期
     */
    public boolean isTokenExpired() {
        return tokenExpiresAt != null && Instant.now().isAfter(tokenExpiresAt);
    }

    /**
     * 检查账户是否处于可用状态
     */
    public boolean isUsable() {
        return status == ConnectionStatus.CONNECTED && !isTokenExpired();
    }
}
