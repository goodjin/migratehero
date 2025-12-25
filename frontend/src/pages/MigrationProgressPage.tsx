import { useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { RootState, AppDispatch } from '../store';
import {
  fetchMigration,
  pauseMigration,
  resumeMigration,
  cancelMigration,
  updateProgress,
  clearCurrentJob,
} from '../store/migrationsSlice';
import { useWebSocket } from '../hooks/useWebSocket';
import {
  ArrowPathIcon,
  CheckCircleIcon,
  PauseIcon,
  PlayIcon,
  XMarkIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';

const phaseLabels = {
  INITIAL_SYNC: 'Initial Sync',
  INCREMENTAL_SYNC: 'Incremental Sync',
  GO_LIVE: 'Go Live',
};

const statusColors = {
  RUNNING: 'bg-blue-500',
  COMPLETED: 'bg-green-500',
  PAUSED: 'bg-yellow-500',
  FAILED: 'bg-red-500',
  DRAFT: 'bg-gray-400',
  SCHEDULED: 'bg-purple-500',
  CANCELLED: 'bg-gray-500',
};

export default function MigrationProgressPage() {
  const { id } = useParams<{ id: string }>();
  const dispatch = useDispatch<AppDispatch>();
  const { currentJob, progress, loading } = useSelector((state: RootState) => state.migrations);

  useEffect(() => {
    if (id) {
      dispatch(fetchMigration(parseInt(id)));
    }
    return () => {
      dispatch(clearCurrentJob());
    };
  }, [id, dispatch]);

  // WebSocket for real-time progress
  useWebSocket(id ? parseInt(id) : null, (progressData) => {
    dispatch(updateProgress(progressData));
  });

  if (loading || !currentJob) {
    return (
      <div className="flex items-center justify-center h-64">
        <ArrowPathIcon className="w-8 h-8 text-gray-400 animate-spin" />
      </div>
    );
  }

  const job = currentJob;
  const displayProgress = progress || {
    overallProgressPercent: job.overallProgressPercent,
    totalEmails: job.totalEmails,
    migratedEmails: job.migratedEmails,
    failedEmails: job.failedEmails,
    emailProgressPercent: job.totalEmails ? Math.round((job.migratedEmails / job.totalEmails) * 100) : 0,
    totalContacts: job.totalContacts,
    migratedContacts: job.migratedContacts,
    failedContacts: job.failedContacts,
    contactProgressPercent: job.totalContacts ? Math.round((job.migratedContacts / job.totalContacts) * 100) : 0,
    totalEvents: job.totalEvents,
    migratedEvents: job.migratedEvents,
    failedEvents: job.failedEvents,
    eventProgressPercent: job.totalEvents ? Math.round((job.migratedEvents / job.totalEvents) * 100) : 0,
  };

  const handlePause = () => dispatch(pauseMigration(job.id));
  const handleResume = () => dispatch(resumeMigration(job.id));
  const handleCancel = () => {
    if (confirm('Are you sure you want to cancel this migration?')) {
      dispatch(cancelMigration(job.id));
    }
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/dashboard" className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
            <ArrowLeftIcon className="w-5 h-5 text-gray-500" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{job.name}</h1>
            <p className="text-sm text-gray-500">
              {job.sourceAccount.email} → {job.targetAccount.email}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {job.status === 'RUNNING' && (
            <button onClick={handlePause} className="btn-secondary flex items-center">
              <PauseIcon className="w-4 h-4 mr-2" />
              Pause
            </button>
          )}
          {job.status === 'PAUSED' && (
            <button onClick={handleResume} className="btn-primary flex items-center">
              <PlayIcon className="w-4 h-4 mr-2" />
              Resume
            </button>
          )}
          {(job.status === 'RUNNING' || job.status === 'PAUSED') && (
            <button onClick={handleCancel} className="btn-secondary text-red-600 flex items-center">
              <XMarkIcon className="w-4 h-4 mr-2" />
              Cancel
            </button>
          )}
        </div>
      </div>

      {/* Status and Phase */}
      <div className="card">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-4">
            <div className={`px-3 py-1 rounded-full text-white text-sm font-medium ${statusColors[job.status]}`}>
              {job.status}
            </div>
            <span className="text-gray-500">|</span>
            <span className="text-sm text-gray-600">{phaseLabels[job.phase]}</span>
          </div>
          {job.status === 'RUNNING' && (
            <ArrowPathIcon className="w-5 h-5 text-blue-500 animate-spin" />
          )}
          {job.status === 'COMPLETED' && (
            <CheckCircleIcon className="w-6 h-6 text-green-500" />
          )}
        </div>

        {/* Overall progress */}
        <div className="mb-8">
          <div className="flex justify-between items-center mb-2">
            <span className="text-sm font-medium text-gray-700">Overall Progress</span>
            <span className="text-2xl font-bold text-gray-900">
              {displayProgress.overallProgressPercent}%
            </span>
          </div>
          <div className="h-4 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-primary-600 rounded-full transition-all duration-500"
              style={{ width: `${displayProgress.overallProgressPercent}%` }}
            />
          </div>
        </div>

        {/* Data type progress */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Emails */}
          <div className="p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center justify-between mb-3">
              <span className="font-medium text-gray-900">Emails</span>
              <span className="text-sm text-gray-500">
                {displayProgress.migratedEmails?.toLocaleString()} / {displayProgress.totalEmails?.toLocaleString() || 0}
              </span>
            </div>
            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-500 rounded-full transition-all duration-300"
                style={{ width: `${displayProgress.emailProgressPercent || 0}%` }}
              />
            </div>
            {displayProgress.failedEmails > 0 && (
              <p className="text-xs text-red-500 mt-2">
                {displayProgress.failedEmails} failed
              </p>
            )}
          </div>

          {/* Contacts */}
          <div className="p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center justify-between mb-3">
              <span className="font-medium text-gray-900">Contacts</span>
              <span className="text-sm text-gray-500">
                {displayProgress.migratedContacts?.toLocaleString()} / {displayProgress.totalContacts?.toLocaleString() || 0}
              </span>
            </div>
            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-green-500 rounded-full transition-all duration-300"
                style={{ width: `${displayProgress.contactProgressPercent || 0}%` }}
              />
            </div>
            {displayProgress.failedContacts > 0 && (
              <p className="text-xs text-red-500 mt-2">
                {displayProgress.failedContacts} failed
              </p>
            )}
          </div>

          {/* Calendar Events */}
          <div className="p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center justify-between mb-3">
              <span className="font-medium text-gray-900">Calendar Events</span>
              <span className="text-sm text-gray-500">
                {displayProgress.migratedEvents?.toLocaleString()} / {displayProgress.totalEvents?.toLocaleString() || 0}
              </span>
            </div>
            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-purple-500 rounded-full transition-all duration-300"
                style={{ width: `${displayProgress.eventProgressPercent || 0}%` }}
              />
            </div>
            {displayProgress.failedEvents > 0 && (
              <p className="text-xs text-red-500 mt-2">
                {displayProgress.failedEvents} failed
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Job details */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Details</h2>
        <dl className="grid grid-cols-2 gap-4">
          <div>
            <dt className="text-sm text-gray-500">Created</dt>
            <dd className="text-sm font-medium text-gray-900">
              {new Date(job.createdAt).toLocaleString()}
            </dd>
          </div>
          {job.startedAt && (
            <div>
              <dt className="text-sm text-gray-500">Started</dt>
              <dd className="text-sm font-medium text-gray-900">
                {new Date(job.startedAt).toLocaleString()}
              </dd>
            </div>
          )}
          {job.completedAt && (
            <div>
              <dt className="text-sm text-gray-500">Completed</dt>
              <dd className="text-sm font-medium text-gray-900">
                {new Date(job.completedAt).toLocaleString()}
              </dd>
            </div>
          )}
          <div>
            <dt className="text-sm text-gray-500">Source Provider</dt>
            <dd className="text-sm font-medium text-gray-900">
              {job.sourceAccount.provider === 'GOOGLE' ? 'Google Workspace' : 'Microsoft 365'}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-gray-500">Target Provider</dt>
            <dd className="text-sm font-medium text-gray-900">
              {job.targetAccount.provider === 'GOOGLE' ? 'Google Workspace' : 'Microsoft 365'}
            </dd>
          </div>
        </dl>
      </div>
    </div>
  );
}
