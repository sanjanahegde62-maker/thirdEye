import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './hooks/useAuth'
import ProtectedRoute from './components/ProtectedRoute'
import Landing from './pages/Landing'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Overview from './pages/Overview'
import CodeDiff from './pages/CodeDiff'
import Findings from './pages/Findings'
import Tests from './pages/Tests'
import Report from './pages/Report'
import BobIntegration from './pages/BobIntegration'
import Settings from './pages/Settings'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<Overview />} />
          <Route path="diff" element={<CodeDiff />} />
          <Route path="findings" element={<Findings />} />
          <Route path="tests" element={<Tests />} />
          <Route path="report" element={<Report />} />
          <Route path="bob" element={<BobIntegration />} />
          <Route path="settings" element={<Settings />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
