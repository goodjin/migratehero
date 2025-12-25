# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MigrateHero is an enterprise email migration platform that enables zero-downtime migration between Google Workspace and Microsoft 365. The application consists of a Spring Boot backend (Java 17) and a React/TypeScript frontend.

## Build & Run Commands

### Backend (Spring Boot)
```bash
./mvnw clean compile          # Compile
./mvnw spring-boot:run        # Run (port 8081)
./mvnw test                   # Run tests
./mvnw test -Dtest=ClassName  # Run single test class
```

### Frontend (React/Vite)
```bash
cd frontend
npm install                   # Install dependencies
npm run dev                   # Development server (port 5173)
npm run build                 # Production build
npm run lint                  # ESLint
```

## Architecture

### Backend Structure

**Controllers** (`src/main/java/com/migratehero/controller/`):
- `MvpMigrationController` - MVP migration API (no auth, `/api/v1/mvp/*`)
- `AuthController` - Authentication endpoints
- `OAuthController` - Google/Microsoft OAuth flows
- `MigrationController` - Full migration API (requires auth)

**Migration Engine** (`src/main/java/com/migratehero/service/migration/`):
- `MigrationEngine` - Core orchestrator handling 3-phase migration (Initial Sync → Incremental Sync → Go Live)
- `MigrationJobService` - Job lifecycle management
- `CheckpointService` - Resume support via sync tokens/page tokens

**Connectors** (`src/main/java/com/migratehero/service/connector/`):
- `ConnectorFactory` - Creates appropriate connector based on provider type
- Provider implementations: `google/`, `microsoft/`, `ews/`, `imap/`
- Each provider has `EmailConnector`, `ContactConnector`, `CalendarConnector` implementations

**Transformers** (`src/main/java/com/migratehero/service/transform/`):
- `EmailTransformer`, `ContactTransformer`, `CalendarTransformer` - Format conversion between providers

### Frontend Structure

- State management: Redux Toolkit (`frontend/src/store/`)
- Routing: React Router with protected routes
- Real-time updates: WebSocket via STOMP (`useWebSocket` hook)
- Styling: Tailwind CSS

### Database

- Development: H2 file-based (`./data/migratehero`)
- Production: PostgreSQL (or MySQL/OceanBase)
- H2 console: http://localhost:8081/h2-console

## Key Patterns

- **Async migrations**: `@Async("migrationTaskExecutor")` on `MigrationEngine.executeMigration()`
- **Progress broadcasting**: `ProgressBroadcaster` sends real-time updates via WebSocket
- **Checkpointing**: Migrations can resume from last checkpoint after interruption
- **Batch processing**: Default batch size of 50 items per API call

## API Documentation

OpenAPI/Swagger UI available at http://localhost:8081/swagger-ui.html

## Environment Variables

Required for OAuth integration:
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`
- `JWT_SECRET`, `ENCRYPTION_KEY`
- `BASE_URL`, `FRONTEND_URL`
