import { Navigate, Outlet } from "react-router-dom"
import { useAuth } from "../hooks/useAuth"

/**
 * Wraps routes that require authentication.
 * - Loading: show nothing (avoids flash)
 * - Not authenticated: redirect to /login
 * - Authenticated: render children or nested routes
 */
export default function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-ivory-base">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-plum border-t-transparent" />
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />

  return children ?? <Outlet />
}
