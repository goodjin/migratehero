package com.migratehero.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migratehero.dto.request.LoginRequest;
import com.migratehero.dto.request.RegisterRequest;
import com.migratehero.dto.response.AuthResponse;
import com.migratehero.dto.response.UserResponse;
import com.migratehero.model.enums.Role;
import com.migratehero.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_shouldReturnSuccess() throws Exception {
        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(Role.USER)
                .enabled(true)
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("jwt-token-123")
                .refreshToken("refresh-token-123")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(userResponse)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token-123"))
                .andExpect(jsonPath("$.data.user.username").value("testuser"));
    }

    @Test
    void login_shouldReturnToken() throws Exception {
        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(Role.USER)
                .enabled(true)
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("jwt-token-123")
                .refreshToken("refresh-token-123")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(userResponse)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("test@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token-123"));
    }

    @Test
    void register_shouldValidateRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");  // Invalid: empty
        request.setEmail("invalid-email");  // Invalid: not an email
        request.setPassword("short");  // Invalid: too short

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
