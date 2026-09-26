import { useState, useEffect } from "react"
import { apiRequest } from "../services/api/client"
import { useAuth } from "../hooks/useAuth"

function Toggle({ checked, onChange, disabled }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus:outline-none disabled:opacity-50 ${
        checked ? "bg-plum" : "bg-sand-line"
      }`}
    >
      <span
        className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition-transform ${
          checked ? "translate-x-5" : "translate-x-0"
        }`}
      />
    </button>
  )
}

export default function Settings() {
  const { user, refreshUser } = useAuth()

  // Profile state
  const [profileName,  setProfileName]  = useState("")
  const [profileEmail, setProfileEmail] = useState("")
  const [profSaving,   setProfSaving]   = useState(false)
  const [profSuccess,  setProfSuccess]  = useState(false)
  const [profError,    setProfError]    = useState("")

  // Prefs state
  const [reviewCompleted, setReviewCompleted] = useState(true)
  const [newFindings,     setNewFindings]     = useState(true)
  const [testsGenerated,  setTestsGenerated]  = useState(true)
  const [prefSaving,      setPrefSaving]      = useState(false)
  const [prefSuccess,     setPrefSuccess]     = useState(false)
  const [prefError,       setPrefError]       = useState("")

  const [loading, setLoading] = useState(true)

  // Load settings on mount
  useEffect(() => {
    apiRequest("/api/settings")
      .then(data => {
        setProfileName(data.profile?.name  || "")
        setProfileEmail(data.profile?.email || "")
        setReviewCompleted(data.preferences?.reviewCompleted ?? true)
        setNewFindings(data.preferences?.newFindings         ?? true)
        setTestsGenerated(data.preferences?.testsGenerated   ?? true)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const handleSaveProfile = async (e) => {
    e.preventDefault()
    setProfError("")
    setProfSuccess(false)
    if (!profileName.trim()) { setProfError("Name cannot be blank."); return }
    setProfSaving(true)
    try {
      await apiRequest("/api/settings/profile", {
        method: "PATCH",
        body: { name: profileName.trim(), email: profileEmail.trim() },
      })
      await refreshUser()
      setProfSuccess(true)
      setTimeout(() => setProfSuccess(false), 3000)
    } catch (err) {
      setProfError(err?.message || "Failed to save profile.")
    } finally {
      setProfSaving(false)
    }
  }

  const handleSavePrefs = async () => {
    setPrefError("")
    setPrefSuccess(false)
    setPrefSaving(true)
    try {
      await apiRequest("/api/settings/preferences", {
        method: "PATCH",
        body: { reviewCompleted, newFindings, testsGenerated },
      })
      setPrefSuccess(true)
      setTimeout(() => setPrefSuccess(false), 3000)
    } catch (err) {
      setPrefError(err?.message || "Failed to save preferences.")
    } finally {
      setPrefSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-48 text-plum/40 text-sm">
        Loading settings…
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-8 py-8 px-4">
      <h1 className="font-serif text-2xl font-bold text-plum">Settings</h1>

      {/* Profile section */}
      <section className="rounded-xl border border-sand-line bg-ivory-card p-6">
        <h2 className="mb-4 text-sm font-semibold text-plum">Profile</h2>
        {user && (
          <p className="mb-4 text-xs text-plum/50">
            Signed in as <span className="font-medium">{user.email}</span>
          </p>
        )}
        <form onSubmit={handleSaveProfile} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-plum/70 mb-1.5">Display name</label>
            <input
              value={profileName}
              onChange={e => setProfileName(e.target.value)}
              placeholder="Your name"
              className="w-full rounded-lg border border-sand-line bg-ivory-base px-3.5 py-2.5 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 transition"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-plum/70 mb-1.5">Email</label>
            <input
              type="email"
              value={profileEmail}
              onChange={e => setProfileEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full rounded-lg border border-sand-line bg-ivory-base px-3.5 py-2.5 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 transition"
            />
          </div>

          {profError   && <p className="text-xs text-coral">{profError}</p>}
          {profSuccess && <p className="text-xs text-green-600">Profile saved.</p>}

          <button
            type="submit"
            disabled={profSaving}
            className="rounded-lg bg-plum px-4 py-2 text-sm font-semibold text-white hover:bg-plum/90 disabled:opacity-60 transition"
          >
            {profSaving ? "Saving…" : "Save profile"}
          </button>
        </form>
      </section>

      {/* Notification preferences */}
      <section className="rounded-xl border border-sand-line bg-ivory-card p-6">
        <h2 className="mb-4 text-sm font-semibold text-plum">Notification Preferences</h2>
        <div className="space-y-4">
          {[
            { label: "Review completed",  value: reviewCompleted, setter: setReviewCompleted },
            { label: "New findings",      value: newFindings,     setter: setNewFindings     },
            { label: "Tests generated",   value: testsGenerated,  setter: setTestsGenerated  },
          ].map(({ label, value, setter }) => (
            <div key={label} className="flex items-center justify-between">
              <span className="text-sm text-plum/80">{label}</span>
              <Toggle checked={value} onChange={setter} disabled={prefSaving} />
            </div>
          ))}
        </div>

        {prefError   && <p className="mt-3 text-xs text-coral">{prefError}</p>}
        {prefSuccess && <p className="mt-3 text-xs text-green-600">Preferences saved.</p>}

        <button
          onClick={handleSavePrefs}
          disabled={prefSaving}
          className="mt-5 rounded-lg bg-plum px-4 py-2 text-sm font-semibold text-white hover:bg-plum/90 disabled:opacity-60 transition"
        >
          {prefSaving ? "Saving…" : "Save preferences"}
        </button>
      </section>
    </div>
  )
}
