package com.migratehero.controller;

import com.migratehero.dto.response.ApiResponse;
import com.migratehero.dto.response.EmailAccountResponse;
import com.migratehero.dto.response.OAuthUrlResponse;
import com.migratehero.model.EmailAccount;
import com.migratehero.model.User;
import com.migratehero.repository.UserRepository;
import com.migratehero.security.CurrentUser;
import com.migratehero.security.UserPrincipal;
import com.migratehero.service.oauth.GoogleOAuthService;
import com.migratehero.service.oauth.MicrosoftOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth 控制器 - 处理 Google/Microsoft 授权
 */
@RestController
@RequestMapping("/api/v1/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth API", description = "OAuth 授权管理接口")
public class OAuthController {

    private final GoogleOAuthService googleOAuthService;
    private final MicrosoftOAuthService microsoftOAuthService;
    private final UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Operation(summary = "获取 Google OAuth 授权 URL")
    @GetMapping("/google/authorize")
    public ResponseEntity<ApiResponse<OAuthUrlResponse>> getGoogleAuthUrl(
            @CurrentUser UserPrincipal currentUser) {
        String authUrl = googleOAuthService.getAuthorizationUrl(currentUser.getId());
        OAuthUrlResponse response = OAuthUrlResponse.builder()
                .authorizationUrl(authUrl)
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Google OAuth 回调")
    @GetMapping("/google/callback")
    public ResponseEntity<String> googleCallback(
            @RequestParam String code,
            @RequestParam String state,
            @CurrentUser UserPrincipal currentUser) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        EmailAccount account = googleOAuthService.handleCallback(code, state, user);

        // 重定向到前端账户页面
        String redirectUrl = frontendUrl + "/accounts?connected=google&email=" + account.getEmail();
        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .body("Redirecting...");
    }

    @Operation(summary = "获取 Microsoft OAuth 授权 URL")
    @GetMapping("/microsoft/authorize")
    public ResponseEntity<ApiResponse<OAuthUrlResponse>> getMicrosoftAuthUrl(
            @CurrentUser UserPrincipal currentUser) {
        String authUrl = microsoftOAuthService.getAuthorizationUrl(currentUser.getId());
        OAuthUrlResponse response = OAuthUrlResponse.builder()
                .authorizationUrl(authUrl)
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Microsoft OAuth 回调")
    @GetMapping("/microsoft/callback")
    public ResponseEntity<String> microsoftCallback(
            @RequestParam String code,
            @RequestParam String state,
            @CurrentUser UserPrincipal currentUser) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        EmailAccount account = microsoftOAuthService.handleCallback(code, state, user);

        // 重定向到前端账户页面
        String redirectUrl = frontendUrl + "/accounts?connected=microsoft&email=" + account.getEmail();
        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .body("Redirecting...");
    }
}
