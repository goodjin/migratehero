package com.migratehero.service.account;

import com.migratehero.dto.response.EmailAccountResponse;
import com.migratehero.exception.ResourceNotFoundException;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.User;
import com.migratehero.model.enums.ConnectionStatus;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.repository.EmailAccountRepository;
import com.migratehero.repository.UserRepository;
import com.migratehero.service.oauth.GoogleOAuthService;
import com.migratehero.service.oauth.MicrosoftOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 账户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final EmailAccountRepository emailAccountRepository;
    private final UserRepository userRepository;
    private final GoogleOAuthService googleOAuthService;
    private final MicrosoftOAuthService microsoftOAuthService;

    /**
     * 获取用户所有已连接的账户
     */
    @Transactional(readOnly = true)
    public List<EmailAccountResponse> getUserAccounts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return emailAccountRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(EmailAccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个账户详情
     */
    @Transactional(readOnly = true)
    public EmailAccountResponse getAccountById(Long userId, Long accountId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        EmailAccount account = emailAccountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", "id", accountId));

        return EmailAccountResponse.fromEntity(account);
    }

    /**
     * 断开账户连接
     */
    @Transactional
    public void disconnectAccount(Long userId, Long accountId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        EmailAccount account = emailAccountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", "id", accountId));

        // 撤销授权状态
        account.setStatus(ConnectionStatus.REVOKED);
        account.setAccessTokenEncrypted(null);
        account.setRefreshTokenEncrypted(null);
        account.setTokenExpiresAt(null);

        emailAccountRepository.save(account);
        log.info("Disconnected account: {} for user: {}", account.getEmail(), user.getUsername());
    }

    /**
     * 删除账户
     */
    @Transactional
    public void deleteAccount(Long userId, Long accountId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        EmailAccount account = emailAccountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", "id", accountId));

        emailAccountRepository.delete(account);
        log.info("Deleted account: {} for user: {}", account.getEmail(), user.getUsername());
    }

    /**
     * 测试账户连接
     */
    @Transactional
    public boolean testConnection(Long userId, Long accountId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        EmailAccount account = emailAccountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", "id", accountId));

        try {
            // 尝试刷新 Token 来测试连接
            if (account.getProvider() == ProviderType.GOOGLE) {
                googleOAuthService.refreshAccessToken(account);
            } else if (account.getProvider() == ProviderType.MICROSOFT) {
                microsoftOAuthService.refreshAccessToken(account);
            }

            return account.getStatus() == ConnectionStatus.CONNECTED;

        } catch (Exception e) {
            log.error("Connection test failed for account: {}", account.getEmail(), e);
            account.setStatus(ConnectionStatus.ERROR);
            account.setErrorMessage("Connection test failed: " + e.getMessage());
            emailAccountRepository.save(account);
            return false;
        }
    }

    /**
     * 获取用户已连接的账户数量
     */
    @Transactional(readOnly = true)
    public long getConnectedAccountCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return emailAccountRepository.countConnectedAccountsByUser(user);
    }

    /**
     * 获取账户实体
     */
    @Transactional(readOnly = true)
    public EmailAccount getAccountEntity(Long userId, Long accountId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return emailAccountRepository.findByIdAndUser(accountId, user)
                .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", "id", accountId));
    }
}
