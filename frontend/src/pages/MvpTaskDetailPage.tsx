import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { mvpApi, MvpTask, FolderProgress, MigratedEmail } from '../api/mvpApi';

const MvpTaskDetailPage: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<MvpTask | null>(null);
  const [folders, setFolders] = useState<FolderProgress[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<FolderProgress | null>(null);
  const [emails, setEmails] = useState<MigratedEmail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTask = useCallback(async () => {
    if (!taskId) return;
    try {
      const [taskData, foldersData] = await Promise.all([
        mvpApi.getTask(parseInt(taskId, 10)),
        mvpApi.getFolderProgress(parseInt(taskId, 10))
      ]);
      setTask(taskData);
      setFolders(foldersData);

      // Auto-select first folder with emails or INBOX
      if (!selectedFolder && foldersData.length > 0) {
        const inbox = foldersData.find(f => f.folderName.toLowerCase() === 'inbox');
        const firstWithEmails = foldersData.find(f => f.totalEmails > 0);
        setSelectedFolder(inbox || firstWithEmails || foldersData[0]);
      }
    } catch (err: any) {
      setError(err.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [taskId, selectedFolder]);

  const fetchEmails = useCallback(async () => {
    if (!taskId || !selectedFolder) return;
    try {
      const data = await mvpApi.getMigratedEmails(parseInt(taskId, 10), selectedFolder.folderName);
      setEmails(data);
    } catch (err) {
      console.error('Failed to load emails:', err);
    }
  }, [taskId, selectedFolder]);

  useEffect(() => {
    fetchTask();
    const interval = setInterval(() => {
      if (task?.status === 'RUNNING') {
        fetchTask();
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [fetchTask, task?.status]);

  useEffect(() => {
    if (selectedFolder) {
      fetchEmails();
    }
  }, [selectedFolder, fetchEmails]);

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      DRAFT: 'bg-gray-100 text-gray-700',
      RUNNING: 'bg-blue-100 text-blue-700',
      COMPLETED: 'bg-green-100 text-green-700',
      FAILED: 'bg-red-100 text-red-700',
      pending: 'bg-gray-100 text-gray-600',
      in_progress: 'bg-blue-100 text-blue-700',
      completed: 'bg-green-100 text-green-700',
      failed: 'bg-red-100 text-red-700',
    };
    return styles[status] || 'bg-gray-100 text-gray-600';
  };

  const formatBytes = (bytes: number) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  // Calculate folder stats
  const folderStats = selectedFolder ? {
    total: selectedFolder.totalEmails,
    migrated: selectedFolder.migratedEmails,
    failed: selectedFolder.failedEmails,
    totalSize: emails.reduce((sum, e) => sum + (e.sizeBytes || 0), 0),
  } : null;

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-blue-500"></div>
      </div>
    );
  }

  if (error || !task) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 mb-4">{error || '任务不存在'}</p>
          <button onClick={() => navigate('/mvp')} className="text-blue-600 hover:underline">
            返回列表
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <button
                onClick={() => navigate('/mvp')}
                className="text-gray-500 hover:text-gray-700"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>
              <div>
                <h1 className="text-xl font-bold text-gray-900">迁移任务详情</h1>
                <p className="text-sm text-gray-500">{task.sourceEmail} → {task.targetEmail}</p>
              </div>
            </div>
            <span className={`px-3 py-1 rounded-full text-sm font-medium ${getStatusBadge(task.status)}`}>
              {task.status === 'RUNNING' && '迁移中'}
              {task.status === 'COMPLETED' && '已完成'}
              {task.status === 'FAILED' && '失败'}
              {task.status === 'DRAFT' && '待开始'}
            </span>
          </div>

          {/* Progress Bar */}
          <div className="mt-4">
            <div className="flex justify-between text-sm text-gray-600 mb-1">
              <span>总进度: {task.progressPercent}%</span>
              <span>
                {task.migratedEmails}/{task.totalEmails} 封邮件
                {task.failedEmails > 0 && <span className="text-red-500 ml-2">({task.failedEmails} 失败)</span>}
              </span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div
                className={`h-2 rounded-full transition-all ${
                  task.status === 'FAILED' ? 'bg-red-500' :
                  task.status === 'COMPLETED' ? 'bg-green-500' : 'bg-blue-500'
                }`}
                style={{ width: `${task.progressPercent}%` }}
              ></div>
            </div>
            {task.currentFolder && (
              <p className="text-xs text-gray-500 mt-1">正在迁移: {task.currentFolder}</p>
            )}
          </div>

          {/* Error Details */}
          {task.status === 'FAILED' && task.errorMessage && (
            <div className="mt-4 bg-red-50 border border-red-200 rounded-lg p-3">
              <h3 className="text-sm font-medium text-red-800 mb-2">失败原因: {task.errorMessage}</h3>
              {task.failedEndpoint && (
                <p className="text-xs text-red-700">连接地址: {task.failedEndpoint}</p>
              )}
              {task.failedRequest && (
                <details className="mt-2">
                  <summary className="text-xs text-red-700 cursor-pointer hover:underline">点击展开详细信息...</summary>
                  <div className="mt-2 space-y-2">
                    {task.failedRequest && (
                      <div>
                        <p className="text-xs font-medium text-red-800">请求参数:</p>
                        <pre className="text-xs text-red-700 bg-white p-2 rounded border border-red-200 overflow-auto">
                          {task.failedRequest}
                        </pre>
                      </div>
                    )}
                    {task.failedResponse && (
                      <div>
                        <p className="text-xs font-medium text-red-800">返回结果:</p>
                        <pre className="text-xs text-red-700 bg-white p-2 rounded border border-red-200 overflow-auto">
                          {task.failedResponse}
                        </pre>
                      </div>
                    )}
                  </div>
                </details>
              )}
            </div>
          )}
        </div>
      </header>

      {/* Main Content - Two Column Layout */}
      <main className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex gap-6">
          {/* Left: Folder List */}
          <div className="w-72 flex-shrink-0">
            <div className="bg-white rounded-xl shadow-sm">
              <div className="p-4 border-b">
                <h2 className="font-semibold text-gray-900">文件夹列表</h2>
                <p className="text-xs text-gray-500 mt-1">{folders.length} 个文件夹</p>
              </div>
              <div className="max-h-[calc(100vh-280px)] overflow-y-auto">
                {folders.map(folder => (
                  <button
                    key={folder.id}
                    onClick={() => setSelectedFolder(folder)}
                    className={`w-full text-left p-3 border-b border-gray-100 hover:bg-gray-50 transition-colors ${
                      selectedFolder?.id === folder.id ? 'bg-blue-50 border-l-4 border-l-blue-500' : ''
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className={`font-medium truncate ${
                        selectedFolder?.id === folder.id ? 'text-blue-700' : 'text-gray-900'
                      }`}>
                        {folder.displayName}
                      </span>
                      <span className={`text-xs px-1.5 py-0.5 rounded ${getStatusBadge(folder.status)}`}>
                        {folder.status === 'completed' ? '完成' :
                         folder.status === 'in_progress' ? '进行中' :
                         folder.status === 'failed' ? '失败' : '等待'}
                      </span>
                    </div>
                    <div className="text-xs text-gray-500 mt-1">
                      {folder.migratedEmails}/{folder.totalEmails} 封
                      {folder.failedEmails > 0 && (
                        <span className="text-red-500 ml-1">({folder.failedEmails} 失败)</span>
                      )}
                    </div>
                    {folder.totalEmails > 0 && (
                      <div className="mt-1.5 w-full bg-gray-200 rounded-full h-1">
                        <div
                          className={`h-1 rounded-full ${
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
          </div>

          {/* Right: Email List */}
          <div className="flex-1">
            <div className="bg-white rounded-xl shadow-sm">
              {/* Folder Stats Header */}
              {selectedFolder && (
                <div className="p-4 border-b bg-gray-50 rounded-t-xl">
                  <div className="flex items-center justify-between mb-3">
                    <h2 className="font-semibold text-gray-900">{selectedFolder.displayName}</h2>
                    <span className={`text-xs px-2 py-1 rounded-full ${getStatusBadge(selectedFolder.status)}`}>
                      {selectedFolder.status === 'completed' ? '已完成' :
                       selectedFolder.status === 'in_progress' ? '迁移中' :
                       selectedFolder.status === 'failed' ? '失败' : '等待中'}
                    </span>
                  </div>
                  <div className="grid grid-cols-4 gap-4">
                    <div className="bg-white rounded-lg p-3 text-center">
                      <div className="text-2xl font-bold text-gray-900">{folderStats?.total || 0}</div>
                      <div className="text-xs text-gray-500">总邮件数</div>
                    </div>
                    <div className="bg-white rounded-lg p-3 text-center">
                      <div className="text-2xl font-bold text-green-600">{folderStats?.migrated || 0}</div>
                      <div className="text-xs text-gray-500">已迁移</div>
                    </div>
                    <div className="bg-white rounded-lg p-3 text-center">
                      <div className="text-2xl font-bold text-red-600">{folderStats?.failed || 0}</div>
                      <div className="text-xs text-gray-500">失败</div>
                    </div>
                    <div className="bg-white rounded-lg p-3 text-center">
                      <div className="text-2xl font-bold text-gray-700">{formatBytes(folderStats?.totalSize || 0)}</div>
                      <div className="text-xs text-gray-500">已迁移大小</div>
                    </div>
                  </div>
                </div>
              )}

              {/* Email Table */}
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-12">状态</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">主题</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-40">发件人</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-20">大小</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-36">迁移时间</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {emails.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-12 text-center text-gray-500">
                          {selectedFolder ? '该文件夹暂无已迁移邮件' : '请选择一个文件夹查看邮件'}
                        </td>
                      </tr>
                    ) : (
                      emails.map(email => (
                        <tr key={email.id} className={`hover:bg-gray-50 ${!email.success ? 'bg-red-50' : ''}`}>
                          <td className="px-4 py-3 text-center">
                            {email.success ? (
                              <span className="text-green-500 text-lg">✓</span>
                            ) : (
                              <span className="text-red-500 text-lg" title={email.errorMessage || '失败'}>✗</span>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            <div className="text-sm text-gray-900 truncate max-w-md" title={email.subject}>
                              {email.subject || '(无主题)'}
                            </div>
                            {!email.success && email.errorMessage && (
                              <div className="text-xs text-red-500 truncate">{email.errorMessage}</div>
                            )}
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-600 truncate" title={email.fromAddress}>
                            {email.fromAddress || '-'}
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-600">
                            {formatBytes(email.sizeBytes)}
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-500">
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
      </main>
    </div>
  );
};

export default MvpTaskDetailPage;
