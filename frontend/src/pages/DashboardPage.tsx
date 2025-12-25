import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import { RootState, AppDispatch } from '../store';
import { fetchMigrations } from '../store/migrationsSlice';
import { fetchAccounts } from '../store/accountsSlice';
import {
  ArrowPathIcon,
  CheckCircleIcon,
  ClockIcon,
  ExclamationCircleIcon,
  PauseCircleIcon,
} from '@heroicons/react/24/outline';

const statusIcons = {
  RUNNING: ArrowPathIcon,
  COMPLETED: CheckCircleIcon,
  PAUSED: PauseCircleIcon,
  FAILED: ExclamationCircleIcon,
  DRAFT: ClockIcon,
  SCHEDULED: ClockIcon,
  CANCELLED: ExclamationCircleIcon,
};

const statusColors = {
  RUNNING: 'text-blue-500',
  COMPLETED: 'text-green-500',
  PAUSED: 'text-yellow-500',
  FAILED: 'text-red-500',
  DRAFT: 'text-gray-400',
  SCHEDULED: 'text-purple-500',
  CANCELLED: 'text-gray-500',
};

export default function DashboardPage() {
  const dispatch = useDispatch<AppDispatch>();
  const { jobs, loading } = useSelector((state: RootState) => state.migrations);
  const { accounts } = useSelector((state: RootState) => state.accounts);

  useEffect(() => {
    dispatch(fetchMigrations(0));
    dispatch(fetchAccounts());
  }, [dispatch]);

  const runningJobs = jobs.filter((j) => j.status === 'RUNNING').length;
  const completedJobs = jobs.filter((j) => j.status === 'COMPLETED').length;
  const connectedAccounts = accounts.filter((a) => a.status === 'CONNECTED').length;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="mt-1 text-sm text-gray-500">
          Overview of your email migration activities
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="card">
          <div className="flex items-center">
            <div className="p-3 bg-blue-100 rounded-lg">
              <ArrowPathIcon className="w-6 h-6 text-blue-600" />
            </div>
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-500">Running Migrations</p>
              <p className="text-2xl font-semibold text-gray-900">{runningJobs}</p>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center">
            <div className="p-3 bg-green-100 rounded-lg">
              <CheckCircleIcon className="w-6 h-6 text-green-600" />
            </div>
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-500">Completed</p>
              <p className="text-2xl font-semibold text-gray-900">{completedJobs}</p>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center">
            <div className="p-3 bg-purple-100 rounded-lg">
              <svg className="w-6 h-6 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-500">Connected Accounts</p>
              <p className="text-2xl font-semibold text-gray-900">{connectedAccounts}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Quick actions */}
      <div className="flex gap-4">
        <Link to="/migrations/new" className="btn-primary">
          Start New Migration
        </Link>
        <Link to="/accounts" className="btn-secondary">
          Connect Account
        </Link>
      </div>

      {/* Recent migrations */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Recent Migrations</h2>

        {loading ? (
          <div className="py-8 text-center text-gray-500">Loading...</div>
        ) : jobs.length === 0 ? (
          <div className="py-8 text-center text-gray-500">
            <p>No migrations yet.</p>
            <Link to="/migrations/new" className="text-primary-600 hover:text-primary-700 mt-2 inline-block">
              Start your first migration
            </Link>
          </div>
        ) : (
          <div className="divide-y divide-gray-200">
            {jobs.slice(0, 5).map((job) => {
              const StatusIcon = statusIcons[job.status];
              return (
                <Link
                  key={job.id}
                  to={`/migrations/${job.id}`}
                  className="flex items-center py-4 hover:bg-gray-50 -mx-6 px-6 transition-colors"
                >
                  <StatusIcon className={`w-5 h-5 ${statusColors[job.status]}`} />
                  <div className="ml-4 flex-1">
                    <p className="text-sm font-medium text-gray-900">{job.name}</p>
                    <p className="text-sm text-gray-500">
                      {job.sourceAccount.email} → {job.targetAccount.email}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-medium text-gray-900">{job.overallProgressPercent}%</p>
                    <p className="text-xs text-gray-500">{job.status}</p>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
