package com.migratehero.service.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Microsoft OAuth 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicrosoftOAuthService {

    private final EmailAccountRepository emailAccountRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.microsoft.redirect-uri}")
    private String redirectUri;

    private static final String AUTHORIZATION_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";

    private static final List<String> SCOPES = Arrays.asList(
            "openid",
            "email",
            "profile",
            "offline_access",
            "Mail.Read",
            "Mail.ReadWrite",
            "Contacts.Read",
            "Contacts.ReadWrite",
            "Calendars.Read",
            "Calendars.ReadWrite"
    );

    // 临时存储 state -> userId 映射
    private final ConcurrentHashMap<String, Long> stateUserMap = new ConcurrentHashMap<>();

    /**
     * 生成 Microsoft OAuth 授权 URL
     */
    public String getAuthorizationUrl(Long userId) {
        String state = UUID.randomUUID().toString();
        stateUserMap.put(state, userId);

        String scopeParam = SCOPES.stream()
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
                .collect(Collectors.joining("%20"));

        return AUTHORIZATION_URL +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_mode=query" +
                "&scope=" + scopeParam +
                "&state=" + state;
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("code", code);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");
            params.add("scope", String.join(" ", SCOPES));

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, request, String.class);

            JsonNode tokenJson = objectMapper.readTree(response.getBody());
            String accessToken = tokenJson.get("access_token").asText();
            String refreshToken = tokenJson.has("refresh_token") ? tokenJson.get("refresh_token").asText() : null;
            long expiresIn = tokenJson.get("expires_in").asLong();

            // 获取用户信息
            String email = getUserEmail(accessToken);
            String displayName = getUserDisplayName(accessToken);

            // 检查账户是否已存在
            EmailAccount account = emailAccountRepository.findByUserAndEmail(user, email)
                    .orElse(EmailAccount.builder()
                            .user(user)
                            .email(email)
                            .provider(ProviderType.MICROSOFT)
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
            log.info("Microsoft account connected: {} for user: {}", email, user.getUsername());

            return account;

        } catch (Exception e) {
            log.error("Failed to exchange Microsoft authorization code", e);
            throw new BusinessException("OAUTH_ERROR", "Failed to connect Microsoft account: " + e.getMessage());
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", refreshToken);
            params.add("grant_type", "refresh_token");
            params.add("scope", String.join(" ", SCOPES));

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, request, String.class);

            JsonNode tokenJson = objectMapper.readTree(response.getBody());
            String newAccessToken = tokenJson.get("access_token").asText();
            String newRefreshToken = tokenJson.has("refresh_token") ? tokenJson.get("refresh_token").asText() : null;
            long expiresIn = tokenJson.get("expires_in").asLong();

            account.setAccessTokenEncrypted(encryptionService.encrypt(newAccessToken));
            if (newRefreshToken != null) {
                account.setRefreshTokenEncrypted(encryptionService.encrypt(newRefreshToken));
            }
            account.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
            account.setStatus(ConnectionStatus.CONNECTED);
            account.setErrorMessage(null);

            emailAccountRepository.save(account);
            log.info("Refreshed Microsoft token for account: {}", account.getEmail());

        } catch (Exception e) {
            log.error("Failed to refresh Microsoft token for account: {}", account.getEmail(), e);
            account.setStatus(ConnectionStatus.EXPIRED);
            account.setErrorMessage("Token refresh failed: " + e.getMessage());
            emailAccountRepository.save(account);
        }
    }

    /**
     * 获取用户邮箱
     */
    private String getUserEmail(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode userJson = objectMapper.readTree(response.getBody());
            if (userJson.has("mail") && !userJson.get("mail").isNull()) {
                return userJson.get("mail").asText();
            } else if (userJson.has("userPrincipalName")) {
                return userJson.get("userPrincipalName").asText();
            }
            return "unknown@microsoft.com";
        } catch (Exception e) {
            log.error("Failed to get user email from Microsoft Graph", e);
            return "unknown@microsoft.com";
        }
    }

    /**
     * 获取用户显示名称
     */
    private String getUserDisplayName(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode userJson = objectMapper.readTree(response.getBody());
            if (userJson.has("displayName")) {
                return userJson.get("displayName").asText();
            }
            return "Microsoft User";
        } catch (Exception e) {
            log.error("Failed to get display name from Microsoft Graph", e);
            return "Microsoft User";
        }
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
