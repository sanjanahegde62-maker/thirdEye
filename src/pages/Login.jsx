import { useState, useEffect } from "react"
import { Link, useNavigate } from "react-router-dom"
import { Eye as EyeIcon, EyeOff, ShieldCheck, Zap, GitBranch } from "lucide-react"
import { useAuth } from "../hooks/useAuth"

const features = [
  { icon: ShieldCheck, text: "Detect security vulnerabilities before merge" },
  { icon: Zap,         text: "Real-time static analysis on every diff" },
  { icon: GitBranch,   text: "Integrates with your existing workflow" },
]

export default function Login() {
  const navigate       = useNavigate()
  const { user, login, loading: authLoading } = useAuth()

  const [email,     setEmail]     = useState("")
  const [password,  setPassword]  = useState("")
  const [showPass,  setShowPass]  = useState(false)
  const [loading,   setLoading]   = useState(false)
  const [error,     setError]     = useState("")

  // Redirect if already authenticated
  useEffect(() => {
    if (!authLoading && user) navigate("/dashboard/overview", { replace: true })
  }, [user, authLoading, navigate])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError("")
    if (!email.trim() || !password) { setError("Please fill in all fields."); return }
    setLoading(true)
    try {
      await login(email.trim(), password)
      navigate("/dashboard/overview", { replace: true })
    } catch (err) {
      if (err?.name === 'NetworkError') {
        setError(err.message)
      } else {
        setError(err?.message || "Invalid email or password.")
      }
    } finally {
      setLoading(false)
    }
  }

  if (authLoading) return null

  return (
    <div className="flex min-h-screen">
      {/* Left panel */}
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between bg-plum p-12 text-ivory-100">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-coral/90">
            <EyeIcon size={18} strokeWidth={2.25} className="text-white" />
          </div>
          <span className="font-serif text-xl font-semibold tracking-tight">ThirdEye AI</span>
        </div>

        <div>
          <h1 className="font-serif text-4xl font-bold leading-tight">
            See the bugs<br />before they ship.
          </h1>
          <p className="mt-4 text-lg text-ivory-100/70">
            Automated code review powered by deterministic static analysis.
          </p>
          <ul className="mt-8 space-y-4">
            {features.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-ivory-100/80">
                <Icon size={18} className="shrink-0 text-coral" />
                <span>{text}</span>
              </li>
            ))}
          </ul>
        </div>

        <p className="text-xs text-ivory-100/30">© 2025 ThirdEye AI</p>
      </div>

      {/* Right panel */}
      <div className="flex flex-1 flex-col items-center justify-center bg-ivory-base px-6 py-12">
        <div className="w-full max-w-sm">
          {/* Mobile logo */}
          <div className="mb-8 flex items-center gap-2 lg:hidden">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-coral/90">
              <EyeIcon size={16} className="text-white" />
            </div>
            <span className="font-serif text-lg font-semibold text-plum">ThirdEye AI</span>
          </div>

          <h2 className="font-serif text-2xl font-bold text-plum">Welcome back, developer.</h2>
          <p className="mt-2 text-sm text-plum/60">Sign in to continue your code review session.</p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-4">
            <div>
              <label className="block text-xs font-medium text-plum/70 mb-1.5">Email</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoComplete="email"
                className="w-full rounded-lg border border-sand-line bg-ivory-card px-3.5 py-2.5 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 focus:ring-2 focus:ring-plum/10 transition"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-plum/70 mb-1.5">Password</label>
              <div className="relative">
                <input
                  type={showPass ? "text" : "password"}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  className="w-full rounded-lg border border-sand-line bg-ivory-card px-3.5 py-2.5 pr-10 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 focus:ring-2 focus:ring-plum/10 transition"
                />
                <button
                  type="button"
                  onClick={() => setShowPass(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-plum/40 hover:text-plum transition-colors"
                  aria-label={showPass ? "Hide password" : "Show password"}
                >
                  {showPass ? <EyeOff size={15} /> : <EyeIcon size={15} />}
                </button>
              </div>
            </div>

            {error && (
              <p className="rounded-lg bg-coral/10 px-3 py-2 text-xs text-coral">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-plum px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-plum/90 disabled:opacity-60"
            >
              {loading ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-plum/55">
            Don't have an account?{" "}
            <Link to="/register" className="font-medium text-plum hover:underline">Create one</Link>
          </p>
        </div>
      </div>
    </div>
  )
}

