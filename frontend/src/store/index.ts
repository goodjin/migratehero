import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import accountsReducer from './accountsSlice';
import migrationsReducer from './migrationsSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    accounts: accountsReducer,
    migrations: migrationsReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
