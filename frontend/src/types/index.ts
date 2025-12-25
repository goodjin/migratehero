// User types
export interface User {
  id: number;
  username: string;
  email: string;
  createdAt: string;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

// Account types
export type ProviderType = 'GOOGLE' | 'MICROSOFT';
export type ConnectionStatus = 'CONNECTED' | 'EXPIRED' | 'REVOKED' | 'PENDING';

export interface EmailAccount {
  id: number;
  email: string;
  provider: ProviderType;
  status: ConnectionStatus;
  createdAt: string;
}

// Migration types
export type MigrationPhase = 'INITIAL_SYNC' | 'INCREMENTAL_SYNC' | 'GO_LIVE';
export type MigrationStatus = 'DRAFT' | 'SCHEDULED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface MigrationJob {
  id: number;
  name: string;
  description?: string;
  sourceAccount: EmailAccount;
  targetAccount: EmailAccount;
  phase: MigrationPhase;
  status: MigrationStatus;
  overallProgressPercent: number;
  totalEmails: number;
  migratedEmails: number;
  failedEmails: number;
  totalContacts: number;
  migratedContacts: number;
  failedContacts: number;
  totalEvents: number;
  migratedEvents: number;
  failedEvents: number;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface MigrationProgress {
  jobId: number;
  phase: MigrationPhase;
  status: MigrationStatus;
  overallProgressPercent: number;
  totalEmails: number;
  migratedEmails: number;
  failedEmails: number;
  emailProgressPercent: number;
  totalContacts: number;
  migratedContacts: number;
  failedContacts: number;
  contactProgressPercent: number;
  totalEvents: number;
  migratedEvents: number;
  failedEvents: number;
  eventProgressPercent: number;
  timestamp: number;
}

export interface CreateMigrationRequest {
  name: string;
  description?: string;
  sourceAccountId: number;
  targetAccountId: number;
  migrateEmails: boolean;
  migrateContacts: boolean;
  migrateCalendars: boolean;
  scheduledAt?: string;
}

// API Response types
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
