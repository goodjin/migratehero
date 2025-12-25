package com.migratehero.service.auth;

import com.migratehero.dto.request.LoginRequest;
import com.migratehero.dto.request.RefreshTokenRequest;
import com.migratehero.dto.request.RegisterRequest;
import com.migratehero.dto.response.AuthResponse;
import com.migratehero.dto.response.UserResponse;
import com.migratehero.exception.AuthenticationException;
import com.migratehero.exception.BusinessException;
import com.migratehero.model.User;
import com.migratehero.model.enums.Role;
import com.migratehero.repository.UserRepository;
import com.migratehero.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("USERNAME_EXISTS", "Username is already taken");
        }

        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "Email is already registered");
        }

        // 创建新用户
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .company(request.getCompany())
                .role(Role.USER)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());

        return generateAuthResponse(user);
    }

    /**
     * 用户登录
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationException("Invalid username or password");
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AuthenticationException("ACCOUNT_DISABLED", "Account is disabled");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in: {}", user.getUsername());
        return generateAuthResponse(user);
    }

    /**
     * 刷新 Token
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthenticationException("INVALID_TOKEN", "Invalid refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new AuthenticationException("INVALID_TOKEN", "Invalid token type");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AuthenticationException("ACCOUNT_DISABLED", "Account is disabled");
        }

        log.info("Token refreshed for user: {}", user.getUsername());
        return generateAuthResponse(user);
    }

    /**
     * 获取当前用户信息
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));
        return UserResponse.fromEntity(user);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                UserResponse.fromEntity(user)
        );
    }
}
