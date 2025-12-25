package com.migratehero.dto.response;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.enums.ConnectionStatus;
import com.migratehero.model.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邮箱账户响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAccountResponse {

    private Long id;

    private String email;

    private String displayName;

    private ProviderType provider;

    private ConnectionStatus status;

    private LocalDateTime lastSyncedAt;

    private LocalDateTime createdAt;

    private boolean usable;

    public static EmailAccountResponse fromEntity(EmailAccount account) {
        return EmailAccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .displayName(account.getDisplayName())
                .provider(account.getProvider())
                .status(account.getStatus())
                .lastSyncedAt(account.getLastSyncedAt())
                .createdAt(account.getCreatedAt())
                .usable(account.isUsable())
                .build();
    }
}
