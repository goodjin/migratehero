package com.migratehero.service.oauth;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.migratehero.exception.BusinessException;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.User;
import com.migratehero.model.enums.ConnectionStatus;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.repository.EmailAccountRepository;
import com.migratehero.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google OAuth 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final EmailAccountRepository emailAccountRepository;
    private final EncryptionService encryptionService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    private static final List<String> SCOPES = Arrays.asList(
            "email",
            "profile",
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/gmail.modify",
            "https://www.googleapis.com/auth/contacts.readonly",
            "https://www.googleapis.com/auth/contacts",
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/calendar"
    );

    // 临时存储 state -> userId 映射
    private final ConcurrentHashMap<String, Long> stateUserMap = new ConcurrentHashMap<>();

    /**
     * 生成 Google OAuth 授权 URL
     */
    public String getAuthorizationUrl(Long userId) {
        String state = UUID.randomUUID().toString();
        stateUserMap.put(state, userId);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                clientId,
                clientSecret,
                SCOPES
        ).setAccessType("offline")
         .setApprovalPrompt("force")
         .build();

        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(state)
                .build();
    }

    /**
     * 处理 OAuth 回调
     */
    @Transactional
    public EmailAccount handleCallback(String code, String state, User user) {
        Long expectedUserId = stateUserMap.remove(state);
        if (expectedUserId == null || !expectedUserId.equals(user.getId())) {
            throw new BusinessException("INVALID_STATE", "Invalid OAuth state");
        }

        try {
            // 交换授权码获取 Token
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    "https://oauth2.googleapis.com/token",
                    clientId,
                    clientSecret,
                    code,
                    redirectUri
            ).execute();

            // 获取用户信息
            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            Long expiresIn = tokenResponse.getExpiresInSeconds();

            // 使用 People API 获取用户邮箱 (简化实现)
            String email = getUserEmail(accessToken);
            String displayName = getUserDisplayName(accessToken);

            // 检查账户是否已存在
            EmailAccount account = emailAccountRepository.findByUserAndEmail(user, email)
                    .orElse(EmailAccount.builder()
                            .user(user)
                            .email(email)
                            .provider(ProviderType.GOOGLE)
                            .build());

            // 更新账户信息
            account.setDisplayName(displayName);
            account.setAccessTokenEncrypted(encryptionService.encrypt(accessToken));
            if (refreshToken != null) {
                account.setRefreshTokenEncrypted(encryptionService.encrypt(refreshToken));
            }
            account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
            account.setGrantedScopes(String.join(",", SCOPES));
            account.setStatus(ConnectionStatus.CONNECTED);
            account.setErrorMessage(null);

            account = emailAccountRepository.save(account);
            log.info("Google account connected: {} for user: {}", email, user.getUsername());

            return account;

        } catch (IOException e) {
            log.error("Failed to exchange Google authorization code", e);
            throw new BusinessException("OAUTH_ERROR", "Failed to connect Google account: " + e.getMessage());
        }
    }

    /**
     * 刷新 Access Token
     */
    @Transactional
    public void refreshAccessToken(EmailAccount account) {
        if (account.getRefreshTokenEncrypted() == null) {
            account.setStatus(ConnectionStatus.EXPIRED);
            account.setErrorMessage("No refresh token available");
            emailAccountRepository.save(account);
            return;
        }

        try {
            String refreshToken = encryptionService.decrypt(account.getRefreshTokenEncrypted());

            TokenResponse tokenResponse = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    SCOPES
            ).build()
             .newTokenRequest(refreshToken)
             .setGrantType("refresh_token")
             .execute();

            account.setAccessTokenEncrypted(encryptionService.encrypt(tokenResponse.getAccessToken()));
            account.setTokenExpiresAt(Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            account.setStatus(ConnectionStatus.CONNECTED);
            account.setErrorMessage(null);

            emailAccountRepository.save(account);
            log.info("Refreshed Google token for account: {}", account.getEmail());

        } catch (IOException e) {
            log.error("Failed to refresh Google token for account: {}", account.getEmail(), e);
            account.setStatus(ConnectionStatus.EXPIRED);
            account.setErrorMessage("Token refresh failed: " + e.getMessage());
            emailAccountRepository.save(account);
        }
    }

    /**
     * 获取用户邮箱 (简化实现)
     */
    private String getUserEmail(String accessToken) {
        // 在实际实现中，使用 Google OAuth2 API 获取用户信息
        // 这里返回占位符，实际项目中需要完善
        return "user@gmail.com";
    }

    /**
     * 获取用户显示名称 (简化实现)
     */
    private String getUserDisplayName(String accessToken) {
        return "Google User";
    }

    /**
     * 获取解密后的 Access Token
     */
    public String getAccessToken(EmailAccount account) {
        if (account.isTokenExpired()) {
            refreshAccessToken(account);
        }
        return encryptionService.decrypt(account.getAccessTokenEncrypted());
    }
}
