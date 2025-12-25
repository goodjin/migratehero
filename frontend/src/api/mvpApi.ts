import axios from 'axios';

// 使用相对路径，让 Vite 代理处理，支持内网 IP 访问
const api = axios.create({
  baseURL: '/api/v1/mvp',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Types
export interface MvpMigrationRequest {
  sourceEwsUrl: string;
  sourceEmail: string;
  sourcePassword: string;
  targetImapHost: string;
  targetImapPort: number;
  targetImapSsl: boolean;
  targetEmail: string;
  targetPassword: string;
  // 迁移类型选项
  migrateEmails?: boolean;
  migrateCalendar?: boolean;
  migrateContacts?: boolean;
  // CalDAV/CardDAV 配置（可选，默认从 IMAP 主机推断）
  targetCalDavUrl?: string;
  targetCardDavUrl?: string;
}

export interface TestConnectionRequest {
  type: 'ews' | 'imap';
  ewsUrl?: string;
  email: string;
  password: string;
  imapHost?: string;
  imapPort?: number;
  imapSsl?: boolean;
}

export interface MvpTask {
  id: number;
  sourceEmail: string;
  targetEmail: string;
  status: 'DRAFT' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED' | 'CANCELLED';
  progressPercent: number;
  totalFolders: number;
  migratedFolders: number;
  totalEmails: number;
  migratedEmails: number;
  failedEmails: number;
  // 日历统计
  totalCalendarEvents: number;
  migratedCalendarEvents: number;
  failedCalendarEvents: number;
  // 联系人统计
  totalContacts: number;
  migratedContacts: number;
  failedContacts: number;
  // 迁移类型
  migrateEmails: boolean;
  migrateCalendar: boolean;
  migrateContacts: boolean;
  currentFolder: string | null;
  errorMessage: string | null;
  failedEndpoint: string | null;
  failedRequest: string | null;
  failedResponse: string | null;
  sourceEwsUrl?: string;
  sourcePassword?: string;
  targetImapHost?: string;
  targetImapPort?: number;
  targetImapSsl?: boolean;
  targetPassword?: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface FolderProgress {
  id: number;
  folderName: string;
  displayName: string;
  folderPath: string;
  totalEmails: number;
  migratedEmails: number;
  failedEmails: number;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  progressPercent: number;
  startedAt: string | null;
  completedAt: string | null;
}

export interface MigratedEmail {
  id: number;
  sourceEmailId: string;
  folderName: string;
  subject: string;
  fromAddress: string;
  sentDate: string;
  sizeBytes: number;
  success: boolean;
  errorMessage: string | null;
  migratedAt: string;
}

// API functions
export const mvpApi = {
  // Test connection
  testConnection: async (request: TestConnectionRequest) => {
    const response = await api.post<{ success: boolean; message: string }>('/test-connection', request);
    return response.data;
  },

  // Check if email has active task
  checkDuplicate: async (email: string) => {
    const response = await api.get<{ exists: boolean; message: string }>('/check-duplicate', {
      params: { email }
    });
    return response.data;
  },

  // Create migration task
  createTask: async (request: MvpMigrationRequest) => {
    const response = await api.post<MvpTask>('/tasks', request);
    return response.data;
  },

  // Update migration task
  updateTask: async (taskId: number, request: MvpMigrationRequest) => {
    const response = await api.put<MvpTask>(`/tasks/${taskId}`, request);
    return response.data;
  },

  // Get all tasks
  getTasks: async () => {
    const response = await api.get<MvpTask[]>('/tasks');
    return response.data;
  },

  // Get task by ID
  getTask: async (taskId: number) => {
    const response = await api.get<MvpTask>(`/tasks/${taskId}`);
    return response.data;
  },

  // Start migration
  startMigration: async (taskId: number) => {
    const response = await api.post<{ success: boolean; message: string; taskId: number }>(`/tasks/${taskId}/start`);
    return response.data;
  },

  // Get folder progress
  getFolderProgress: async (taskId: number) => {
    const response = await api.get<FolderProgress[]>(`/tasks/${taskId}/folders`);
    return response.data;
  },

  // Get migrated emails in a folder
  getMigratedEmails: async (taskId: number, folderName: string) => {
    const response = await api.get<MigratedEmail[]>(`/tasks/${taskId}/folders/${encodeURIComponent(folderName)}/emails`);
    return response.data;
  },

  // Health check
  health: async () => {
    const response = await api.get<{ status: string; service: string }>('/health');
    return response.data;
  },

  // Pause task
  pauseTask: async (taskId: number) => {
    const response = await api.post<{ success: boolean; message: string; taskId: number }>(`/tasks/${taskId}/pause`);
    return response.data;
  },

  // Retry task
  retryTask: async (taskId: number) => {
    const response = await api.post<{ success: boolean; message: string; taskId: number }>(`/tasks/${taskId}/retry`);
    return response.data;
  },
};

export default mvpApi;
