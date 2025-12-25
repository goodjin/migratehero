import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { mvpApi, MvpTask, FolderProgress, MigratedEmail } from '../api/mvpApi';

const MvpProgressPage: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<MvpTask | null>(null);
  const [folders, setFolders] = useState<FolderProgress[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<string | null>(null);
  const [emails, setEmails] = useState<MigratedEmail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTask = useCallback(async () => {
    if (!taskId) return;
    try {
      const taskData = await mvpApi.getTask(parseInt(taskId, 10));
      setTask(taskData);

      const foldersData = await mvpApi.getFolderProgress(parseInt(taskId, 10));
      setFolders(foldersData);

      // Auto-select INBOX or first folder
      if (!selectedFolder && foldersData.length > 0) {
        const inbox = foldersData.find(f => f.folderName.toLowerCase() === 'inbox');
        setSelectedFolder(inbox?.folderName || foldersData[0].folderName);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load task');
    } finally {
      setLoading(false);
    }
  }, [taskId, selectedFolder]);

  const fetchEmails = useCallback(async () => {
    if (!taskId || !selectedFolder) return;
    try {
      const emailsData = await mvpApi.getMigratedEmails(parseInt(taskId, 10), selectedFolder);
      setEmails(emailsData);
    } catch (err) {
      console.error('Failed to load emails:', err);
    }
  }, [taskId, selectedFolder]);

  // Poll for updates while task is running
  useEffect(() => {
    fetchTask();

    const interval = setInterval(() => {
      if (task?.status === 'RUNNING') {
        fetchTask();
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [fetchTask, task?.status]);

  // Fetch emails when folder changes
  useEffect(() => {
    fetchEmails();
  }, [selectedFolder, fetchEmails]);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
      case 'completed':
        return 'text-green-600 bg-green-100';
      case 'RUNNING':
      case 'in_progress':
        return 'text-blue-600 bg-blue-100';
      case 'FAILED':
      case 'failed':
        return 'text-red-600 bg-red-100';
      case 'pending':
        return 'text-gray-600 bg-gray-100';
      default:
        return 'text-gray-600 bg-gray-100';
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (dateString: string | null) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-t-4 border-blue-500 mx-auto"></div>
          <p className="mt-4 text-gray-600">Loading migration status...</p>
        </div>
      </div>
    );
  }

  if (error || !task) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 text-lg">{error || 'Task not found'}</p>
          <button
            onClick={() => navigate('/mvp')}
            className="mt-4 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
          >
            Back to Setup
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white shadow">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Migration Progress</h1>
              <p className="text-sm text-gray-500">
                {task.sourceEmail} → {task.targetEmail}
              </p>
            </div>
            <div className="flex items-center gap-4">
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${getStatusColor(task.status)}`}>
                {task.status}
              </span>
              <button
                onClick={() => navigate('/mvp')}
                className="px-4 py-2 text-gray-600 hover:text-gray-900"
              >
                New Migration
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Progress Overview */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="bg-white rounded-xl shadow-lg p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-800">Overall Progress</h2>
            <span className="text-2xl font-bold text-blue-600">{task.progressPercent}%</span>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-gray-200 rounded-full h-4 mb-4">
            <div
              className={`h-4 rounded-full transition-all duration-500 ${
                task.status === 'FAILED' ? 'bg-red-500' :
                task.status === 'COMPLETED' ? 'bg-green-500' : 'bg-blue-500'
              }`}
              style={{ width: `${task.progressPercent}%` }}
            ></div>
          </div>

          {/* Stats Grid */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div className="text-center p-3 bg-gray-50 rounded-lg">
              <div className="text-2xl font-bold text-gray-900">{task.totalFolders}</div>
              <div className="text-xs text-gray-500">Total Folders</div>
            </div>
            <div className="text-center p-3 bg-gray-50 rounded-lg">
              <div className="text-2xl font-bold text-gray-900">{task.migratedFolders}</div>
              <div className="text-xs text-gray-500">Completed Folders</div>
            </div>
            <div className="text-center p-3 bg-gray-50 rounded-lg">
              <div className="text-2xl font-bold text-gray-900">{task.totalEmails}</div>
              <div className="text-xs text-gray-500">Total Emails</div>
            </div>
            <div className="text-center p-3 bg-green-50 rounded-lg">
              <div className="text-2xl font-bold text-green-600">{task.migratedEmails}</div>
              <div className="text-xs text-gray-500">Migrated</div>
            </div>
            <div className="text-center p-3 bg-red-50 rounded-lg">
              <div className="text-2xl font-bold text-red-600">{task.failedEmails}</div>
              <div className="text-xs text-gray-500">Failed</div>
            </div>
          </div>

          {task.currentFolder && (
            <div className="mt-4 text-sm text-gray-600">
              Currently migrating: <span className="font-medium">{task.currentFolder}</span>
            </div>
          )}

          {task.errorMessage && (
            <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
              <p className="text-red-700 text-sm">{task.errorMessage}</p>
            </div>
          )}
        </div>

        {/* Two Column Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Folder List */}
          <div className="bg-white rounded-xl shadow-lg p-4">
            <h3 className="text-lg font-semibold text-gray-800 mb-4">Folders</h3>
            <div className="space-y-2 max-h-[600px] overflow-y-auto">
              {folders.map(folder => (
                <button
                  key={folder.id}
                  onClick={() => setSelectedFolder(folder.folderName)}
                  className={`w-full text-left p-3 rounded-lg transition-colors ${
                    selectedFolder === folder.folderName
                      ? 'bg-blue-50 border-2 border-blue-500'
                      : 'bg-gray-50 hover:bg-gray-100 border-2 border-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-gray-900 truncate">{folder.displayName}</span>
                    <span className={`px-2 py-0.5 rounded text-xs ${getStatusColor(folder.status)}`}>
                      {folder.status}
                    </span>
                  </div>
                  <div className="mt-1 text-xs text-gray-500">
                    {folder.migratedEmails}/{folder.totalEmails} emails
                    {folder.failedEmails > 0 && (
                      <span className="text-red-500 ml-2">({folder.failedEmails} failed)</span>
                    )}
                  </div>
                  {folder.totalEmails > 0 && (
                    <div className="mt-2 w-full bg-gray-200 rounded-full h-1.5">
                      <div
                        className={`h-1.5 rounded-full ${
                          folder.status === 'failed' ? 'bg-red-500' :
                          folder.status === 'completed' ? 'bg-green-500' : 'bg-blue-500'
                        }`}
                        style={{ width: `${folder.progressPercent}%` }}
                      ></div>
                    </div>
                  )}
                </button>
              ))}
            </div>
          </div>

          {/* Email List */}
          <div className="lg:col-span-2 bg-white rounded-xl shadow-lg p-4">
            <h3 className="text-lg font-semibold text-gray-800 mb-4">
              Emails in {selectedFolder || 'Select a folder'}
            </h3>
            <div className="overflow-x-auto">
              <table className="min-w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Subject</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">From</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Size</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Migrated At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {emails.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-8 text-center text-gray-500">
                        No emails migrated yet in this folder
                      </td>
                    </tr>
                  ) : (
                    emails.map(email => (
                      <tr key={email.id} className={email.success ? '' : 'bg-red-50'}>
                        <td className="px-4 py-2">
                          {email.success ? (
                            <span className="text-green-600">✓</span>
                          ) : (
                            <span className="text-red-600" title={email.errorMessage || 'Failed'}>✗</span>
                          )}
                        </td>
                        <td className="px-4 py-2 max-w-xs truncate" title={email.subject}>
                          {email.subject || '(No subject)'}
                        </td>
                        <td className="px-4 py-2 text-sm text-gray-600 truncate" title={email.fromAddress}>
                          {email.fromAddress || '-'}
                        </td>
                        <td className="px-4 py-2 text-sm text-gray-600">
                          {formatBytes(email.sizeBytes || 0)}
                        </td>
                        <td className="px-4 py-2 text-sm text-gray-600">
                          {formatDate(email.migratedAt)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MvpProgressPage;
