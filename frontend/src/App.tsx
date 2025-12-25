import { Routes, Route, Navigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { RootState } from './store';
import Layout from './components/common/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import AccountsPage from './pages/AccountsPage';
import MigrationWizardPage from './pages/MigrationWizardPage';
import MigrationProgressPage from './pages/MigrationProgressPage';
import MvpHomePage from './pages/MvpHomePage';
import MvpTaskDetailPage from './pages/MvpTaskDetailPage';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useSelector((state: RootState) => state.auth);
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function App() {
  return (
    <Routes>
      {/* Public Root Redirect */}
      <Route path="/" element={<Navigate to="/mvp" replace />} />

      {/* MVP Routes (no auth required) */}
      <Route path="/mvp" element={<MvpHomePage />} />
      <Route path="/mvp/task/:taskId" element={<MvpTaskDetailPage />} />

      {/* Auth Routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Protected Routes */}
      <Route
        element={
          <PrivateRoute>
            <Layout />
          </PrivateRoute>
        }
      >
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="migrations/new" element={<MigrationWizardPage />} />
        <Route path="migrations/:id" element={<MigrationProgressPage />} />
      </Route>
    </Routes>
  );
}

export default App;
