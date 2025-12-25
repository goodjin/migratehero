import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { mvpApi, MvpMigrationRequest, TestConnectionRequest } from '../api/mvpApi';

interface FormData extends MvpMigrationRequest {
  // All fields from MvpMigrationRequest
}

const MvpSetupPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [testingEws, setTestingEws] = useState(false);
  const [testingImap, setTestingImap] = useState(false);
  const [ewsTestResult, setEwsTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [imapTestResult, setImapTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [formData, setFormData] = useState<FormData>({
    sourceEwsUrl: 'https://outlook.office365.com/EWS/Exchange.asmx',
    sourceEmail: '',
    sourcePassword: '',
    targetImapHost: '',
    targetImapPort: 993,
    targetImapSsl: true,
    targetEmail: '',
    targetPassword: '',
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? (e.target as HTMLInputElement).checked :
               type === 'number' ? parseInt(value, 10) : value,
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
      setEwsTestResult({ success: false, message: err.message || 'Connection failed' });
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
      setImapTestResult({ success: false, message: err.message || 'Connection failed' });
    } finally {
      setTestingImap(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      // Create task
      const task = await mvpApi.createTask(formData);

      // Start migration
      await mvpApi.startMigration(task.id);

      // Navigate to progress page
      navigate(`/mvp/progress/${task.id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to start migration');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 py-12 px-4">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">
            MigrateHero
          </h1>
          <p className="text-lg text-gray-600">
            Email Migration Tool - EWS to IMAP
          </p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-8">
          {/* Source (EWS) Configuration */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-xl font-semibold text-gray-800 mb-4 flex items-center">
              <span className="bg-blue-500 text-white rounded-full w-8 h-8 flex items-center justify-center mr-3 text-sm">1</span>
              Source Mailbox (Microsoft Exchange / EWS)
            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  EWS URL
                </label>
                <input
                  type="url"
                  name="sourceEwsUrl"
                  value={formData.sourceEwsUrl}
                  onChange={handleChange}
                  placeholder="https://outlook.office365.com/EWS/Exchange.asmx"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Email Address
                </label>
                <input
                  type="email"
                  name="sourceEmail"
                  value={formData.sourceEmail}
                  onChange={handleChange}
                  placeholder="user@company.com"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password
                </label>
                <input
                  type="password"
                  name="sourcePassword"
                  value={formData.sourcePassword}
                  onChange={handleChange}
                  placeholder="********"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                />
              </div>
            </div>

            <div className="mt-4 flex items-center gap-4">
              <button
                type="button"
                onClick={testEwsConnection}
                disabled={testingEws || !formData.sourceEmail || !formData.sourcePassword}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {testingEws ? 'Testing...' : 'Test Connection'}
              </button>
              {ewsTestResult && (
                <span className={`text-sm ${ewsTestResult.success ? 'text-green-600' : 'text-red-600'}`}>
                  {ewsTestResult.success ? '✓ ' : '✗ '}{ewsTestResult.message}
                </span>
              )}
            </div>
          </div>

          {/* Target (IMAP) Configuration */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-xl font-semibold text-gray-800 mb-4 flex items-center">
              <span className="bg-green-500 text-white rounded-full w-8 h-8 flex items-center justify-center mr-3 text-sm">2</span>
              Target Mailbox (IMAP)
            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  IMAP Server Host
                </label>
                <input
                  type="text"
                  name="targetImapHost"
                  value={formData.targetImapHost}
                  onChange={handleChange}
                  placeholder="imap.gmail.com"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                  required
                />
              </div>

              <div className="flex gap-4">
                <div className="flex-1">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Port
                  </label>
                  <input
                    type="number"
                    name="targetImapPort"
                    value={formData.targetImapPort}
                    onChange={handleChange}
                    placeholder="993"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                    required
                  />
                </div>
                <div className="flex items-end pb-2">
                  <label className="flex items-center">
                    <input
                      type="checkbox"
                      name="targetImapSsl"
                      checked={formData.targetImapSsl}
                      onChange={handleChange}
                      className="w-4 h-4 text-green-600 border-gray-300 rounded focus:ring-green-500"
                    />
                    <span className="ml-2 text-sm text-gray-700">SSL/TLS</span>
                  </label>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Email Address
                </label>
                <input
                  type="email"
                  name="targetEmail"
                  value={formData.targetEmail}
                  onChange={handleChange}
                  placeholder="user@gmail.com"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password / App Password
                </label>
                <input
                  type="password"
                  name="targetPassword"
                  value={formData.targetPassword}
                  onChange={handleChange}
                  placeholder="********"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                  required
                />
              </div>
            </div>

            <div className="mt-4 flex items-center gap-4">
              <button
                type="button"
                onClick={testImapConnection}
                disabled={testingImap || !formData.targetEmail || !formData.targetPassword || !formData.targetImapHost}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {testingImap ? 'Testing...' : 'Test Connection'}
              </button>
              {imapTestResult && (
                <span className={`text-sm ${imapTestResult.success ? 'text-green-600' : 'text-red-600'}`}>
                  {imapTestResult.success ? '✓ ' : '✗ '}{imapTestResult.message}
                </span>
              )}
            </div>
          </div>

          {/* Error Message */}
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4">
              <p className="text-red-700">{error}</p>
            </div>
          )}

          {/* Submit Button */}
          <div className="flex justify-center">
            <button
              type="submit"
              disabled={loading}
              className="px-8 py-3 bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-semibold rounded-lg shadow-lg hover:from-blue-700 hover:to-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all transform hover:scale-105"
            >
              {loading ? (
                <span className="flex items-center">
                  <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Starting Migration...
                </span>
              ) : (
                'Start Migration'
              )}
            </button>
          </div>
        </form>

        {/* Help Text */}
        <div className="mt-8 text-center text-sm text-gray-500">
          <p>This tool migrates emails from Microsoft Exchange (EWS) to any IMAP-compatible email server.</p>
          <p className="mt-1">Make sure you have the correct credentials for both source and target accounts.</p>
        </div>
      </div>
    </div>
  );
};

export default MvpSetupPage;
