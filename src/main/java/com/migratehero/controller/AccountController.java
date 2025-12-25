package com.migratehero.controller;

import com.migratehero.dto.response.ApiResponse;
import com.migratehero.dto.response.EmailAccountResponse;
import com.migratehero.security.CurrentUser;
import com.migratehero.security.UserPrincipal;
import com.migratehero.service.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 账户管理控制器
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account API", description = "邮箱账户管理接口")
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "获取所有已连接的账户")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EmailAccountResponse>>> getAllAccounts(
            @CurrentUser UserPrincipal currentUser) {
        List<EmailAccountResponse> accounts = accountService.getUserAccounts(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @Operation(summary = "获取账户详情")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmailAccountResponse>> getAccount(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        EmailAccountResponse account = accountService.getAccountById(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @Operation(summary = "断开账户连接")
    @PostMapping("/{id}/disconnect")
    public ResponseEntity<ApiResponse<Void>> disconnectAccount(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        accountService.disconnectAccount(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Account disconnected"));
    }

    @Operation(summary = "删除账户")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        accountService.deleteAccount(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Account deleted"));
    }

    @Operation(summary = "测试账户连接")
    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> testConnection(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable Long id) {
        boolean connected = accountService.testConnection(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("connected", connected)));
    }

    @Operation(summary = "获取已连接账户数量")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAccountCount(
            @CurrentUser UserPrincipal currentUser) {
        long count = accountService.getConnectedAccountCount(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }
}
