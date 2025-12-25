import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { RootState, AppDispatch } from '../store';
import { fetchAccounts, disconnectAccount } from '../store/accountsSlice';
import { TrashIcon, CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/react/24/outline';

const GoogleIcon = () => (
  <svg className="w-6 h-6" viewBox="0 0 24 24">
    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
  </svg>
);

const MicrosoftIcon = () => (
  <svg className="w-6 h-6" viewBox="0 0 24 24">
    <path fill="#F25022" d="M1 1h10v10H1z"/>
    <path fill="#00A4EF" d="M1 13h10v10H1z"/>
    <path fill="#7FBA00" d="M13 1h10v10H13z"/>
    <path fill="#FFB900" d="M13 13h10v10H13z"/>
  </svg>
);

export default function AccountsPage() {
  const dispatch = useDispatch<AppDispatch>();
  const { accounts, loading } = useSelector((state: RootState) => state.accounts);

  useEffect(() => {
    dispatch(fetchAccounts());
  }, [dispatch]);

  const handleConnect = (provider: 'google' | 'microsoft') => {
    window.location.href = `/api/v1/oauth/${provider}/authorize`;
  };

  const handleDisconnect = (accountId: number) => {
    if (confirm('Are you sure you want to disconnect this account?')) {
      dispatch(disconnectAccount(accountId));
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Email Accounts</h1>
        <p className="mt-1 text-sm text-gray-500">
          Connect your email accounts to enable migration
        </p>
      </div>

      {/* Connect new account */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Connect New Account</h2>
        <div className="flex gap-4">
          <button
            onClick={() => handleConnect('google')}
            className="flex items-center px-6 py-3 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <GoogleIcon />
            <span className="ml-3 font-medium text-gray-700">Connect Google Workspace</span>
          </button>
          <button
            onClick={() => handleConnect('microsoft')}
            className="flex items-center px-6 py-3 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <MicrosoftIcon />
            <span className="ml-3 font-medium text-gray-700">Connect Microsoft 365</span>
          </button>
        </div>
      </div>

      {/* Connected accounts */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Connected Accounts</h2>

        {loading ? (
          <div className="py-8 text-center text-gray-500">Loading...</div>
        ) : accounts.length === 0 ? (
          <div className="py-8 text-center text-gray-500">
            No accounts connected yet. Connect an account above to get started.
          </div>
        ) : (
          <div className="divide-y divide-gray-200">
            {accounts.map((account) => (
              <div key={account.id} className="flex items-center py-4">
                <div className="flex-shrink-0">
                  {account.provider === 'GOOGLE' ? <GoogleIcon /> : <MicrosoftIcon />}
                </div>
                <div className="ml-4 flex-1">
                  <p className="text-sm font-medium text-gray-900">{account.email}</p>
                  <p className="text-xs text-gray-500">
                    {account.provider === 'GOOGLE' ? 'Google Workspace' : 'Microsoft 365'}
                  </p>
                </div>
                <div className="flex items-center gap-4">
                  {account.status === 'CONNECTED' ? (
                    <span className="flex items-center text-sm text-green-600">
                      <CheckCircleIcon className="w-4 h-4 mr-1" />
                      Connected
                    </span>
                  ) : (
                    <span className="flex items-center text-sm text-yellow-600">
                      <ExclamationCircleIcon className="w-4 h-4 mr-1" />
                      {account.status}
                    </span>
                  )}
                  <button
                    onClick={() => handleDisconnect(account.id)}
                    className="p-2 text-gray-400 hover:text-red-500 transition-colors"
                    title="Disconnect"
                  >
                    <TrashIcon className="w-5 h-5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
