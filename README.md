# MigrateHero

MigrateHero 是一个企业级邮箱迁移平台，支持在 Google Workspace 和 Microsoft 365 之间实现零停机迁移。

项目由 Spring Boot 后端（Java 17）和 React/TypeScript 前端组成。

## 主要特性

- **零停机迁移**：支持增量同步，确保迁移过程中用户业务不受影响
- **多协议支持**：支持 Google Workspace, Microsoft 365, EWS, IMAP 等多种协议
- **实时进度**：基于 WebSocket 的实时迁移进度展示
- **断点续传**：支持从中断点继续迁移
- **安全可靠**：OAuth 认证集成，敏感信息加密存储

## 环境要求

- JDK 17+
- Node.js 16+
- Maven 3.6+

## 快速开始

### 1. 后端启动 (Spring Boot)

后端运行在 `8081` 端口。

```bash
# 编译项目
./mvnw clean compile

# 运行服务
./mvnw spring-boot:run

# 运行测试
./mvnw test
```

### 2. 前端启动 (React)

前端运行在 `5173` 端口。

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 访问地址

- **Web 界面**: http://localhost:5173
- **API 文档 (Swagger)**: http://localhost:8081/swagger-ui.html
- **H2 控制台 (开发环境)**: http://localhost:8081/h2-console

## 配置说明

项目使用 H2 数据库进行开发，生产环境支持 PostgreSQL。

如需使用 OAuth 功能（Google/Microsoft），请在环境变量中配置以下参数：
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
- `MICROSOFT_CLIENT_ID` / `MICROSOFT_CLIENT_SECRET`
- `JWT_SECRET`
- `ENCRYPTION_KEY`
