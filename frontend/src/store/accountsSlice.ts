import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { EmailAccount } from '../types';
import api from '../api/axios';

interface AccountsState {
  accounts: EmailAccount[];
  loading: boolean;
  error: string | null;
}

const initialState: AccountsState = {
  accounts: [],
  loading: false,
  error: null,
};

export const fetchAccounts = createAsyncThunk(
  'accounts/fetchAccounts',
  async (_, { rejectWithValue }) => {
    try {
      const response = await api.get('/api/v1/accounts');
      return response.data.data as EmailAccount[];
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to fetch accounts');
    }
  }
);

export const disconnectAccount = createAsyncThunk(
  'accounts/disconnectAccount',
  async (accountId: number, { rejectWithValue }) => {
    try {
      await api.delete(`/api/v1/accounts/${accountId}`);
      return accountId;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message || 'Failed to disconnect account');
    }
  }
);

const accountsSlice = createSlice({
  name: 'accounts',
  initialState,
  reducers: {
    addAccount: (state, action: PayloadAction<EmailAccount>) => {
      state.accounts.push(action.payload);
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchAccounts.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchAccounts.fulfilled, (state, action: PayloadAction<EmailAccount[]>) => {
        state.loading = false;
        state.accounts = action.payload;
      })
      .addCase(fetchAccounts.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      .addCase(disconnectAccount.fulfilled, (state, action: PayloadAction<number>) => {
        state.accounts = state.accounts.filter((a) => a.id !== action.payload);
      });
  },
});

export const { addAccount } = accountsSlice.actions;
export default accountsSlice.reducer;
