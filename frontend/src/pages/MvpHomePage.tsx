import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { mvpApi, MvpTask } from '../api/mvpApi';
import MvpTaskModal from '../components/MvpTaskModal';

// 使用相对路径，让 Vite 代理处理，支持内网 IP 访问
const api = axios.create({
  baseURL: '/api/v1/mvp',
  headers: {
    'Content-Type': 'application/json',
  },
});

const MvpHomePage: React.FC = () => {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<MvpTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingTask, setEditingTask] = useState<MvpTask | undefined>(undefined);

  const fetchTasks = async () => {
    try {
      const data = await mvpApi.getTasks();
      setTasks(data);
    } catch (err) {
      console.error('Failed to fetch tasks:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTasks();
    // Poll for updates every 3 seconds
    const interval = setInterval(fetchTasks, 3000);
    return () => clearInterval(interval);
  }, []);

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      DRAFT: 'bg-gray-100 text-gray-700',
      RUNNING: 'bg-blue-100 text-blue-700 animate-pulse',
      PAUSED: 'bg-yellow-100 text-yellow-700',
      COMPLETED: 'bg-green-100 text-green-700',
      FAILED: 'bg-red-100 text-red-700',
      CANCELLED: 'bg-gray-100 text-gray-500',
    };
    const labels: Record<string, string> = {
      DRAFT: '待开始',
      RUNNING: '迁移中',
      PAUSED: '已暂停',
      COMPLETED: '已完成',
      FAILED: '失败',
      CANCELLED: '已取消',
    };
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${styles[status] || 'bg-gray-100'}`}>
        {labels[status] || status}
      </span>
    );
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  const handleTaskCreated = (task: MvpTask) => {
    setShowModal(false);
    setEditingTask(undefined);
    fetchTasks();
    if (!editingTask) {
      // Navigate to task detail only for new tasks
      navigate(`/mvp/task/${task.id}`);
    }
  };

  const handleModifyTask = (task: MvpTask) => {
    setEditingTask(task);
    setShowModal(true);
  };

  const handlePauseTask = async (taskId: number) => {
    if (window.confirm('确定要暂停这个迁移任务吗？')) {
      try {
        await mvpApi.pauseTask(taskId);
        fetchTasks();
      } catch (err: any) {
        console.error('Failed to pause task:', err);
        alert(err.response?.data?.message || '暂停失败');
      }
    }
  };

  const handleStartTask = async (taskId: number) => {
    if (window.confirm('确定要开始这个迁移任务吗？')) {
      try {
        await mvpApi.startMigration(taskId);
        fetchTasks();
        navigate(`/mvp/task/${taskId}`);
      } catch (err: any) {
        console.error('Failed to start task:', err);
        alert(err.response?.data?.message || '开始失败');
      }
    }
  };

  const handleResumeTask = async (taskId: number) => {
    if (window.confirm('确定要继续这个迁移任务吗？')) {
      try {
        await mvpApi.startMigration(taskId);
        fetchTasks();
      } catch (err: any) {
        console.error('Failed to resume task:', err);
        alert(err.response?.data?.message || '继续失败');
      }
    }
  };

  const handleRetryTask = async (taskId: number) => {
    if (window.confirm('确定要重试这个失败的迁移任务吗？任务将从断点处继续执行。')) {
      try {
        const result = await mvpApi.retryTask(taskId);
        if (result.success && result.taskId) {
          fetchTasks();
          navigate(`/mvp/task/${result.taskId}`);
        } else {
          alert(result.message || '重试失败');
        }
      } catch (err: any) {
        console.error('Failed to retry task:', err);
        alert(err.response?.data?.message || '重试失败');
      }
    }
  };

  const handleDeleteTask = async (taskId: number) => {
    if (window.confirm('确定要删除这个迁移任务吗？删除后无法恢复。')) {
      try {
        await api.delete(`/tasks/${taskId}`);
        fetchTasks();
      } catch (err: any) {
        console.error('Failed to delete task:', err);
        alert(err.response?.data?.message || '删除失败');
      }
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">MigrateHero</h1>
            <p className="text-sm text-gray-500">邮箱迁移管理平台</p>
          </div>
          <button
            onClick={() => setShowModal(true)}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            新建迁移任务
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 py-6">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-blue-500"></div>
          </div>
        ) : tasks.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center">
            <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <h3 className="text-lg font-medium text-gray-900 mb-2">暂无迁移任务</h3>
            <p className="text-gray-500">点击右上角按钮创建您的第一个邮箱迁移任务</p>
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    源邮箱
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    目标邮箱
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    状态
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    进度
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    邮件统计
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    创建时间
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {tasks.map(task => (
                  <tr
                    key={task.id}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => navigate(`/mvp/task/${task.id}`)}
                  >
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{task.sourceEmail}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-600">{task.targetEmail}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {getStatusBadge(task.status)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-2">
                        <div className="w-24 bg-gray-200 rounded-full h-2">
                          <div
                            className={`h-2 rounded-full ${
                              task.status === 'FAILED' ? 'bg-red-500' :
                              task.status === 'COMPLETED' ? 'bg-green-500' : 'bg-blue-500'
                            }`}
                            style={{ width: `${task.progressPercent}%` }}
                          ></div>
                        </div>
                        <span className="text-sm text-gray-600">{task.progressPercent}%</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                      <span className="text-green-600">{task.migratedEmails}</span>
                      {task.failedEmails > 0 && (
                        <span className="text-red-600 ml-1">+{task.failedEmails}失败</span>
                      )}
                      <span className="text-gray-400">/{task.totalEmails}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {formatDate(task.createdAt)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right">
                      <div className="flex gap-3 justify-end">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/mvp/task/${task.id}`);
                          }}
                          className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                        >
                          查看详情
                        </button>
                        {task.status === 'DRAFT' && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleStartTask(task.id);
                            }}
                            className="text-green-600 hover:text-green-800 text-sm font-medium"
                          >
                            开始
                          </button>
                        )}
                        {task.status === 'RUNNING' && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handlePauseTask(task.id);
                            }}
                            className="text-yellow-600 hover:text-yellow-800 text-sm font-medium"
                          >
                            暂停
                          </button>
                        )}
                        {task.status === 'PAUSED' && (
                          <>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleModifyTask(task);
                              }}
                              className="text-gray-600 hover:text-gray-800 text-sm font-medium"
                            >
                              修改
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleResumeTask(task.id);
                              }}
                              className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                            >
                              继续
                            </button>
                          </>
                        )}
                        {task.status === 'FAILED' && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleRetryTask(task.id);
                            }}
                            className="text-green-600 hover:text-green-800 text-sm font-medium"
                          >
                            重试
                          </button>
                        )}
                        {(task.status === 'DRAFT' || task.status === 'PAUSED' || task.status === 'FAILED') && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDeleteTask(task.id);
                            }}
                            className="text-red-600 hover:text-red-800 text-sm font-medium"
                          >
                            删除
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>

      {/* Task Modal */}
      {showModal && (
        <MvpTaskModal
          onClose={() => {
            setShowModal(false);
            setEditingTask(undefined);
          }}
          onCreated={handleTaskCreated}
          task={editingTask}
        />
      )}
    </div>
  );
};

export default MvpHomePage;
