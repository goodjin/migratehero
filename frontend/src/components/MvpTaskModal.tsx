import React, { useState, useEffect } from 'react';
import { mvpApi, MvpMigrationRequest, MvpTask, TestConnectionRequest } from '../api/mvpApi';

interface Props {
  onClose: () => void;
  onCreated: (task: MvpTask) => void;
  task?: MvpTask;
}

interface DemoConfig {
  source: {
    ewsUrl: string;
    email: string;
    password: string;
  };
  target: {
    imapHost: string;
    imapPort: number;
    imapSsl: boolean;
    email: string;
    password: string;
  };
}

// Eye icon for showing password
const EyeIcon = () => (
  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
  </svg>
);

// Eye-off icon for hiding password
const EyeOffIcon = () => (
  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
  </svg>
);

const MvpTaskModal: React.FC<Props> = ({ onClose, onCreated, task }) => {
  const [loading, setLoading] = useState(false);
  const [testingEws, setTestingEws] = useState(false);
  const [testingImap, setTestingImap] = useState(false);
  const [ewsTestResult, setEwsTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [imapTestResult, setImapTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Password visibility states
  const [showSourcePassword, setShowSourcePassword] = useState(false);
  const [showTargetPassword, setShowTargetPassword] = useState(false);

  const [formData, setFormData] = useState<MvpMigrationRequest>({
    sourceEwsUrl: task?.sourceEwsUrl || 'https://outlook.office365.com/EWS/Exchange.asmx',
    sourceEmail: task?.sourceEmail || '',
    sourcePassword: task?.sourcePassword || '',
    targetImapHost: task?.targetImapHost || '',
    targetImapPort: task?.targetImapPort || 993,
    targetImapSsl: task?.targetImapSsl ?? true,
    targetEmail: task?.targetEmail || '',
    targetPassword: task?.targetPassword || '',
  });

  // Load demo config on mount
  useEffect(() => {
    const loadDemoConfig = async () => {
      try {
        const response = await fetch('/demo-config.json');
        if (response.ok) {
          const config: DemoConfig = await response.json();
          setFormData(prev => ({
            ...prev,
            sourceEwsUrl: config.source.ewsUrl || prev.sourceEwsUrl,
            sourceEmail: config.source.email || prev.sourceEmail,
            sourcePassword: config.source.password || prev.sourcePassword,
            targetImapHost: config.target.imapHost || prev.targetImapHost,
            targetImapPort: config.target.imapPort || prev.targetImapPort,
            targetImapSsl: config.target.imapSsl ?? prev.targetImapSsl,
            targetEmail: config.target.email || prev.targetEmail,
            targetPassword: config.target.password || prev.targetPassword,
          }));
        }
      } catch (err) {
        console.log('Demo config not found, using defaults');
      }
    };
    if (!task) {
      loadDemoConfig();
    }
  }, [task]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : type === 'number' ? parseInt(value, 10) : value,
    }));
  };

  const testEwsConnection = async () => {
    setTestingEws(true);
    setEwsTestResult(null);
    try {
      const request: TestConnectionRequest = {
        type: 'ews',
        ewsUrl: formData.sourceEwsUrl,
        email: formData.sourceEmail,
        password: formData.sourcePassword,
      };
      const result = await mvpApi.testConnection(request);
      setEwsTestResult(result);
    } catch (err: any) {
      setEwsTestResult({ success: false, message: err.message || '连接失败' });
    } finally {
      setTestingEws(false);
    }
  };

  const testImapConnection = async () => {
    setTestingImap(true);
    setImapTestResult(null);
    try {
      const request: TestConnectionRequest = {
        type: 'imap',
        imapHost: formData.targetImapHost,
        imapPort: formData.targetImapPort,
        imapSsl: formData.targetImapSsl,
        email: formData.targetEmail,
        password: formData.targetPassword,
      };
      const result = await mvpApi.testConnection(request);
      setImapTestResult(result);
    } catch (err: any) {
      setImapTestResult({ success: false, message: err.message || '连接失败' });
    } finally {
      setTestingImap(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (task) {
        // Editing existing task
        await mvpApi.updateTask(task.id, formData);
        onCreated({ ...task, ...formData });
        return;
      }

      // Check for duplicate
      const dupCheck = await mvpApi.checkDuplicate(formData.sourceEmail);
      if (dupCheck.exists) {
        setError(dupCheck.message);
        setLoading(false);
        return;
      }

      // Create task
      const newTask = await mvpApi.createTask(formData);

      // Start migration
      await mvpApi.startMigration(newTask.id);

      onCreated(newTask);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || '创建任务失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-xl font-semibold text-gray-900">
            {task ? '编辑迁移任务' : '新建迁移任务'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-4 space-y-6">
          {/* Source EWS */}
          <div className="bg-blue-50 rounded-lg p-4">
            <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
              <span className="w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center text-sm">1</span>
              源邮箱 (Microsoft Exchange / EWS)
            </h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm text-gray-600 mb-1">EWS 服务地址</label>
                <input
                  type="url"
                  name="sourceEwsUrl"
                  value={formData.sourceEwsUrl}
                  onChange={handleChange}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm text-gray-600 mb-1">邮箱地址</label>
                  <input
                    type="email"
                    name="sourceEmail"
                    value={formData.sourceEmail}
                    onChange={handleChange}
                    placeholder="user@company.com"
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm text-gray-600 mb-1">密码</label>
                  <div className="relative">
                    <input
                      type={showSourcePassword ? 'text' : 'password'}
                      name="sourcePassword"
                      value={formData.sourcePassword}
                      onChange={handleChange}
                      className="w-full px-3 py-2 pr-10 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowSourcePassword(!showSourcePassword)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showSourcePassword ? <EyeOffIcon /> : <EyeIcon />}
                    </button>
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={testEwsConnection}
                  disabled={testingEws || !formData.sourceEmail || !formData.sourcePassword}
                  className="px-3 py-1.5 bg-white border rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50"
                >
                  {testingEws ? '测试中...' : '测试连接'}
                </button>
                {ewsTestResult && (
                  <span className={`text-sm ${ewsTestResult.success ? 'text-green-600' : 'text-red-600'}`}>
                    {ewsTestResult.success ? '✓ 连接成功' : `✗ ${ewsTestResult.message}`}
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Target IMAP */}
          <div className="bg-green-50 rounded-lg p-4">
            <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
              <span className="w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center text-sm">2</span>
              目标邮箱 (IMAP)
            </h3>
            <div className="space-y-3">
              <div className="grid grid-cols-3 gap-3">
                <div className="col-span-2">
                  <label className="block text-sm text-gray-600 mb-1">IMAP 服务器</label>
                  <input
                    type="text"
                    name="targetImapHost"
                    value={formData.targetImapHost}
                    onChange={handleChange}
                    placeholder="imap.gmail.com"
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm text-gray-600 mb-1">端口</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      name="targetImapPort"
                      value={formData.targetImapPort}
                      onChange={handleChange}
                      className="w-20 px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                      required
                    />
                    <label className="flex items-center text-sm">
                      <input
                        type="checkbox"
                        name="targetImapSsl"
                        checked={formData.targetImapSsl}
                        onChange={handleChange}
                        className="mr-1"
                      />
                      SSL
                    </label>
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm text-gray-600 mb-1">邮箱地址</label>
                  <input
                    type="email"
                    name="targetEmail"
                    value={formData.targetEmail}
                    onChange={handleChange}
                    placeholder="user@gmail.com"
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm text-gray-600 mb-1">密码 / 应用密码</label>
                  <div className="relative">
                    <input
                      type={showTargetPassword ? 'text' : 'password'}
                      name="targetPassword"
                      value={formData.targetPassword}
                      onChange={handleChange}
                      className="w-full px-3 py-2 pr-10 border rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowTargetPassword(!showTargetPassword)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showTargetPassword ? <EyeOffIcon /> : <EyeIcon />}
                    </button>
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={testImapConnection}
                  disabled={testingImap || !formData.targetEmail || !formData.targetPassword || !formData.targetImapHost}
                  className="px-3 py-1.5 bg-white border rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50"
                >
                  {testingImap ? '测试中...' : '测试连接'}
                </button>
                {imapTestResult && (
                  <span className={`text-sm ${imapTestResult.success ? 'text-green-600' : 'text-red-600'}`}>
                    {imapTestResult.success ? '✓ 连接成功' : `✗ ${imapTestResult.message}`}
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Error */}
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-red-700 text-sm">
              {error}
            </div>
          )}

          {/* Actions */}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-600 hover:text-gray-800"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
            >
              {loading && (
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
              )}
              {task ? '保存' : loading ? '创建中...' : '创建并开始迁移'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default MvpTaskModal;
