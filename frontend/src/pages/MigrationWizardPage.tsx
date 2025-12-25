import { useState, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { RootState, AppDispatch } from '../store';
import { fetchAccounts } from '../store/accountsSlice';
import { createMigration, startMigration } from '../store/migrationsSlice';
import { EmailAccount, CreateMigrationRequest } from '../types';
import { CheckIcon } from '@heroicons/react/24/outline';

const steps = [
  { id: 1, name: 'Source' },
  { id: 2, name: 'Target' },
  { id: 3, name: 'Data Types' },
  { id: 4, name: 'Review' },
];

export default function MigrationWizardPage() {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const { accounts, loading: accountsLoading } = useSelector((state: RootState) => state.accounts);
  const { loading: migrationLoading } = useSelector((state: RootState) => state.migrations);

  const [currentStep, setCurrentStep] = useState(1);
  const [sourceAccount, setSourceAccount] = useState<EmailAccount | null>(null);
  const [targetAccount, setTargetAccount] = useState<EmailAccount | null>(null);
  const [dataTypes, setDataTypes] = useState({
    emails: true,
    contacts: true,
    calendars: true,
  });
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  useEffect(() => {
    dispatch(fetchAccounts());
  }, [dispatch]);

  const connectedAccounts = accounts.filter((a) => a.status === 'CONNECTED');
  const availableTargets = connectedAccounts.filter((a) => a.id !== sourceAccount?.id);

  const handleNext = () => {
    if (currentStep < 4) {
      setCurrentStep(currentStep + 1);
    }
  };

  const handleBack = () => {
    if (currentStep > 1) {
      setCurrentStep(currentStep - 1);
    }
  };

  const handleCreate = async () => {
    if (!sourceAccount || !targetAccount) return;

    const request: CreateMigrationRequest = {
      name: name || `${sourceAccount.email} to ${targetAccount.email}`,
      description,
      sourceAccountId: sourceAccount.id,
      targetAccountId: targetAccount.id,
      migrateEmails: dataTypes.emails,
      migrateContacts: dataTypes.contacts,
      migrateCalendars: dataTypes.calendars,
    };

    const result = await dispatch(createMigration(request));
    if (createMigration.fulfilled.match(result)) {
      await dispatch(startMigration(result.payload.id));
      navigate(`/migrations/${result.payload.id}`);
    }
  };

  const canProceed = () => {
    switch (currentStep) {
      case 1:
        return !!sourceAccount;
      case 2:
        return !!targetAccount;
      case 3:
        return dataTypes.emails || dataTypes.contacts || dataTypes.calendars;
      case 4:
        return true;
      default:
        return false;
    }
  };

  if (accountsLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-gray-500">Loading accounts...</p>
      </div>
    );
  }

  if (connectedAccounts.length < 2) {
    return (
      <div className="space-y-8">
        <h1 className="text-2xl font-bold text-gray-900">New Migration</h1>
        <div className="card text-center py-12">
          <p className="text-gray-600 mb-4">
            You need at least 2 connected accounts to start a migration.
          </p>
          <button onClick={() => navigate('/accounts')} className="btn-primary">
            Connect Accounts
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold text-gray-900">New Migration</h1>

      {/* Steps indicator */}
      <nav className="flex items-center justify-center">
        {steps.map((step, index) => (
          <div key={step.id} className="flex items-center">
            <div
              className={`flex items-center justify-center w-10 h-10 rounded-full border-2 ${
                currentStep > step.id
                  ? 'bg-primary-600 border-primary-600 text-white'
                  : currentStep === step.id
                  ? 'border-primary-600 text-primary-600'
                  : 'border-gray-300 text-gray-400'
              }`}
            >
              {currentStep > step.id ? (
                <CheckIcon className="w-5 h-5" />
              ) : (
                <span className="text-sm font-medium">{step.id}</span>
              )}
            </div>
            <span
              className={`ml-2 text-sm font-medium ${
                currentStep >= step.id ? 'text-gray-900' : 'text-gray-400'
              }`}
            >
              {step.name}
            </span>
            {index < steps.length - 1 && (
              <div
                className={`w-16 h-0.5 mx-4 ${
                  currentStep > step.id ? 'bg-primary-600' : 'bg-gray-300'
                }`}
              />
            )}
          </div>
        ))}
      </nav>

      {/* Step content */}
      <div className="card">
        {currentStep === 1 && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Select Source Account</h2>
            <p className="text-sm text-gray-500 mb-6">
              Choose the account you want to migrate data from.
            </p>
            <div className="space-y-3">
              {connectedAccounts.map((account) => (
                <button
                  key={account.id}
                  onClick={() => setSourceAccount(account)}
                  className={`w-full flex items-center p-4 rounded-lg border-2 transition-colors ${
                    sourceAccount?.id === account.id
                      ? 'border-primary-600 bg-primary-50'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <div className="flex-1 text-left">
                    <p className="font-medium text-gray-900">{account.email}</p>
                    <p className="text-sm text-gray-500">
                      {account.provider === 'GOOGLE' ? 'Google Workspace' : 'Microsoft 365'}
                    </p>
                  </div>
                  {sourceAccount?.id === account.id && (
                    <CheckIcon className="w-5 h-5 text-primary-600" />
                  )}
                </button>
              ))}
            </div>
          </div>
        )}

        {currentStep === 2 && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Select Target Account</h2>
            <p className="text-sm text-gray-500 mb-6">
              Choose the account you want to migrate data to.
            </p>
            <div className="space-y-3">
              {availableTargets.map((account) => (
                <button
                  key={account.id}
                  onClick={() => setTargetAccount(account)}
                  className={`w-full flex items-center p-4 rounded-lg border-2 transition-colors ${
                    targetAccount?.id === account.id
                      ? 'border-primary-600 bg-primary-50'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <div className="flex-1 text-left">
                    <p className="font-medium text-gray-900">{account.email}</p>
                    <p className="text-sm text-gray-500">
                      {account.provider === 'GOOGLE' ? 'Google Workspace' : 'Microsoft 365'}
                    </p>
                  </div>
                  {targetAccount?.id === account.id && (
                    <CheckIcon className="w-5 h-5 text-primary-600" />
                  )}
                </button>
              ))}
            </div>
          </div>
        )}

        {currentStep === 3 && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Select Data Types</h2>
            <p className="text-sm text-gray-500 mb-6">
              Choose what data you want to migrate.
            </p>
            <div className="space-y-4">
              {[
                { key: 'emails', label: 'Emails', desc: 'Migrate all emails including attachments' },
                { key: 'contacts', label: 'Contacts', desc: 'Migrate all contacts and groups' },
                { key: 'calendars', label: 'Calendars', desc: 'Migrate calendar events and reminders' },
              ].map((item) => (
                <label
                  key={item.key}
                  className="flex items-center p-4 rounded-lg border border-gray-200 cursor-pointer hover:bg-gray-50"
                >
                  <input
                    type="checkbox"
                    checked={dataTypes[item.key as keyof typeof dataTypes]}
                    onChange={(e) =>
                      setDataTypes({ ...dataTypes, [item.key]: e.target.checked })
                    }
                    className="w-5 h-5 text-primary-600 rounded border-gray-300 focus:ring-primary-500"
                  />
                  <div className="ml-4">
                    <p className="font-medium text-gray-900">{item.label}</p>
                    <p className="text-sm text-gray-500">{item.desc}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>
        )}

        {currentStep === 4 && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Review & Start</h2>
            <div className="space-y-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Migration Name (optional)
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={`${sourceAccount?.email} to ${targetAccount?.email}`}
                  className="input-field"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Description (optional)
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={2}
                  className="input-field"
                />
              </div>

              <div className="bg-gray-50 rounded-lg p-4 space-y-3">
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Source</span>
                  <span className="text-sm font-medium">{sourceAccount?.email}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Target</span>
                  <span className="text-sm font-medium">{targetAccount?.email}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Data Types</span>
                  <span className="text-sm font-medium">
                    {[
                      dataTypes.emails && 'Emails',
                      dataTypes.contacts && 'Contacts',
                      dataTypes.calendars && 'Calendars',
                    ]
                      .filter(Boolean)
                      .join(', ')}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Navigation buttons */}
        <div className="flex justify-between mt-8 pt-6 border-t border-gray-200">
          <button
            onClick={handleBack}
            disabled={currentStep === 1}
            className="btn-secondary disabled:opacity-50"
          >
            Back
          </button>
          {currentStep < 4 ? (
            <button onClick={handleNext} disabled={!canProceed()} className="btn-primary">
              Next
            </button>
          ) : (
            <button
              onClick={handleCreate}
              disabled={migrationLoading}
              className="btn-primary"
            >
              {migrationLoading ? 'Starting...' : 'Start Migration'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
