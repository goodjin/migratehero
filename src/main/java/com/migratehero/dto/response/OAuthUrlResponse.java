package com.migratehero.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth 授权 URL 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUrlResponse {

    private String authorizationUrl;

    private String state;
}
