import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { MigrationJob, MigrationProgress, CreateMigrationRequest, PageResponse } from '../types';
import api from '../api/axios';

interface MigrationsState {
  jobs: MigrationJob[];
  currentJob: MigrationJob | null;
  progress: MigrationProgress | null;
  loading: boolean;
  error: string | null;
  totalPages: number;
  currentPage: number;
}

const initialState: MigrationsState = {
  jobs: [],
  currentJob: null,
  progress: null,
  loading: false,
  error: null,
  totalPages: 0,
  currentPage: 0,
};

export const fetchMigrations = createAsyncThunk(
  'migrations/fetchMigrations',
  async (page: number = 0, { rejectWithValue }) => {
    try {
      const response = await api.get(`/api/v1/migrations?page=${page}&size=10`);
      return response.data.data as PageResponse<MigrationJob>;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to fetch migrations');
    }
  }
);

export const fetchMigration = createAsyncThunk(
  'migrations/fetchMigration',
  async (id: number, { rejectWithValue }) => {
    try {
      const response = await api.get(`/api/v1/migrations/${id}`);
      return response.data.data as MigrationJob;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to fetch migration');
    }
  }
);

export const createMigration = createAsyncThunk(
  'migrations/createMigration',
  async (data: CreateMigrationRequest, { rejectWithValue }) => {
    try {
      const response = await api.post('/api/v1/migrations', data);
      return response.data.data as MigrationJob;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to create migration');
    }
  }
);

export const startMigration = createAsyncThunk(
  'migrations/startMigration',
  async (id: number, { rejectWithValue }) => {
    try {
      await api.post(`/api/v1/migrations/${id}/start`);
      return id;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to start migration');
    }
  }
);

export const pauseMigration = createAsyncThunk(
  'migrations/pauseMigration',
  async (id: number, { rejectWithValue }) => {
    try {
      await api.post(`/api/v1/migrations/${id}/pause`);
      return id;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to pause migration');
    }
  }
);

export const resumeMigration = createAsyncThunk(
  'migrations/resumeMigration',
  async (id: number, { rejectWithValue }) => {
    try {
      await api.post(`/api/v1/migrations/${id}/resume`);
      return id;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to resume migration');
    }
  }
);

export const cancelMigration = createAsyncThunk(
  'migrations/cancelMigration',
  async (id: number, { rejectWithValue }) => {
    try {
      await api.post(`/api/v1/migrations/${id}/cancel`);
      return id;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to cancel migration');
    }
  }
);

const migrationsSlice = createSlice({
  name: 'migrations',
  initialState,
  reducers: {
    updateProgress: (state, action: PayloadAction<MigrationProgress>) => {
      state.progress = action.payload;
      if (state.currentJob && state.currentJob.id === action.payload.jobId) {
        state.currentJob.overallProgressPercent = action.payload.overallProgressPercent;
        state.currentJob.status = action.payload.status;
        state.currentJob.phase = action.payload.phase;
      }
    },
    clearCurrentJob: (state) => {
      state.currentJob = null;
      state.progress = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchMigrations.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchMigrations.fulfilled, (state, action: PayloadAction<PageResponse<MigrationJob>>) => {
        state.loading = false;
        state.jobs = action.payload.content;
        state.totalPages = action.payload.totalPages;
        state.currentPage = action.payload.number;
      })
      .addCase(fetchMigrations.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      .addCase(fetchMigration.fulfilled, (state, action: PayloadAction<MigrationJob>) => {
        state.currentJob = action.payload;
      })
      .addCase(createMigration.fulfilled, (state, action: PayloadAction<MigrationJob>) => {
        state.jobs.unshift(action.payload);
        state.currentJob = action.payload;
      })
      .addCase(startMigration.fulfilled, (state, action: PayloadAction<number>) => {
        const job = state.jobs.find((j) => j.id === action.payload);
        if (job) job.status = 'RUNNING';
        if (state.currentJob?.id === action.payload) {
          state.currentJob.status = 'RUNNING';
        }
      })
      .addCase(pauseMigration.fulfilled, (state, action: PayloadAction<number>) => {
        const job = state.jobs.find((j) => j.id === action.payload);
        if (job) job.status = 'PAUSED';
        if (state.currentJob?.id === action.payload) {
          state.currentJob.status = 'PAUSED';
        }
      })
      .addCase(resumeMigration.fulfilled, (state, action: PayloadAction<number>) => {
        const job = state.jobs.find((j) => j.id === action.payload);
        if (job) job.status = 'RUNNING';
        if (state.currentJob?.id === action.payload) {
          state.currentJob.status = 'RUNNING';
        }
      })
      .addCase(cancelMigration.fulfilled, (state, action: PayloadAction<number>) => {
        const job = state.jobs.find((j) => j.id === action.payload);
        if (job) job.status = 'CANCELLED';
        if (state.currentJob?.id === action.payload) {
          state.currentJob.status = 'CANCELLED';
        }
      });
  },
});

export const { updateProgress, clearCurrentJob } = migrationsSlice.actions;
export default migrationsSlice.reducer;
