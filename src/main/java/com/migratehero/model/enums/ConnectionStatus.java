package com.migratehero.model.enums;

/**
 * 账户连接状态
 */
public enum ConnectionStatus {
    PENDING,     // 等待连接
    CONNECTED,   // 已连接
    EXPIRED,     // Token已过期
    REVOKED,     // 授权已撤销
    ERROR        // 连接错误
}
