import { createContext, useContext, useState, useEffect, useCallback, useRef } from "react"
import { login as apiLogin, register as apiRegister, logout as apiLogout, getMe } from "../services/api/auth"

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)
  // Prevent concurrent /me requests on mount
  const fetchingRef = useRef(false)

  const refreshUser = useCallback(async () => {
    if (fetchingRef.current) return null
    fetchingRef.current = true
    try {
      const currentUser = await getMe()
      setUser(currentUser)
      return currentUser
    } finally {
      fetchingRef.current = false
    }
  }, [])

  // Restore session on mount — a 401 is expected when not logged in and
  // is not an error; only genuine network failures are worth logging.
  useEffect(() => {
    refreshUser()
      .catch((err) => {
        // 401 = not authenticated (normal). Everything else may be a real error.
        if (err && err.status !== 401) {
          console.warn("[useAuth] Session restore failed:", err.message)
        }
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [refreshUser])

  const login = useCallback(async (email, password) => {
    setError(null)
    await apiLogin(email, password)
    return refreshUser()
  }, [refreshUser])

  /**
   * Register a new account.
   *
   * The backend creates an authenticated session as part of registration,
   * so we only need to call refreshUser() afterwards — no second login call.
   * If the backend returns 201 but the session is not set (e.g. an older
   * deployment), we fall back to an explicit login call.
   */
  const register = useCallback(async (fullName, email, password) => {
    setError(null)
    await apiRegister(fullName, email, password)
    try {
      return await refreshUser()
    } catch {
      // Session was not established by register — fall back to explicit login.
      await apiLogin(email, password)
      return refreshUser()
    }
  }, [refreshUser])

  const logout = useCallback(async () => {
    try {
      await apiLogout()
    } catch {
      // Ignore logout errors — if the session is already gone, that's fine.
    }
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, error, login, register, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>")
  return ctx
}
