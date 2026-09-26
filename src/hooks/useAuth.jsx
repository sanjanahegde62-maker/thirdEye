import { createContext, useContext, useState, useEffect, useCallback } from "react"
import { login as apiLogin, register as apiRegister, logout as apiLogout, getMe } from "../services/api/auth"

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)

  const refreshUser = useCallback(async () => {
    const currentUser = await getMe()
    setUser(currentUser)
    return currentUser
  }, [])

  // Restore session on mount
  useEffect(() => {
    refreshUser()
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [refreshUser])

  const login = useCallback(async (email, password) => {
    setError(null)
    await apiLogin(email, password)
    return refreshUser()
  }, [refreshUser])

  const register = useCallback(async (fullName, email, password) => {
    setError(null)
    await apiRegister(fullName, email, password)
    // Registration may create the account without creating an authenticated session.
    await apiLogin(email, password)
    return refreshUser()
  }, [refreshUser])

  const logout = useCallback(async () => {
    await apiLogout().catch(() => {})
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
