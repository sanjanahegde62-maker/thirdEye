import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, GitBranch, Check, Bell, User, Settings, LogOut, Loader2, CheckCheck, Plus } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { getNotifications, getNotificationSummary, markNotificationRead, markAllNotificationsRead } from '../services/api/notifications'

// -- helpers ------------------------------------------------------------------

/** Derive two-character initials from a name string. */
function initials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/** Friendly relative timestamp. */
function timeAgo(isoString) {
  if (!isoString) return ''
  const diff = Date.now() - new Date(isoString).getTime()
  const mins  = Math.floor(diff / 60_000)
  const hours = Math.floor(diff / 3_600_000)
  const days  = Math.floor(diff / 86_400_000)
  if (mins  < 1)  return 'just now'
  if (mins  < 60) return `${mins}m ago`
  if (hours < 24) return `${hours}h ago`
  return `${days}d ago`
}

const EVENT_LABELS = {
  REVIEW_COMPLETED: 'Review completed',
  NEW_FINDINGS:     'New findings',
  TESTS_GENERATED:  'Tests generated',
  GENERAL:          'Notice',
}

// -- Component ----------------------------------------------------------------

export default function Topbar({ projects = [], activeProject, setActiveProject, onNewProject }) {
  const navigate         = useNavigate()
  const { user, logout } = useAuth()

  const [activeMenu, setActiveMenu] = useState(null)

  // Notification state
  const [notifications,  setNotifications]  = useState([])
  const [unreadCount,    setUnreadCount]    = useState(0)
  const [notifLoading,   setNotifLoading]   = useState(false)

  const projectRef      = useRef(null)
  const notificationsRef = useRef(null)
  const profileRef      = useRef(null)

  // -- load / refresh notifications whenever the panel opens -----------------
  const loadNotifications = useCallback(async () => {
    setNotifLoading(true)
    try {
      const [list, summary] = await Promise.all([
        getNotifications(),
        getNotificationSummary(),
      ])
      setNotifications(list || [])
      setUnreadCount(summary?.unread ?? 0)
    } catch {
      // silently skip — don't block the UI
    } finally {
      setNotifLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeMenu === 'notifications') loadNotifications()
  }, [activeMenu, loadNotifications])

  // Poll unread count every 30 s even when the panel is closed
  useEffect(() => {
    const tick = () =>
      getNotificationSummary()
        .then(s => setUnreadCount(s?.unread ?? 0))
        .catch(() => {})
    tick()
    const id = setInterval(tick, 30_000)
    return () => clearInterval(id)
  }, [])

  // -- click-outside / Escape to close --------------------------------------
  useEffect(() => {
    const onDown = (e) => {
      const inside = [projectRef, notificationsRef, profileRef]
        .some(r => r.current?.contains(e.target))
      if (!inside) setActiveMenu(null)
    }
    const onKey = (e) => { if (e.key === 'Escape') setActiveMenu(null) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  // -- mark a single notification read --------------------------------------
  const handleMarkRead = async (id) => {
    try {
      const updated = await markNotificationRead(id)
      setNotifications(prev => prev.map(n => n.id === id ? updated : n))
      setUnreadCount(c => Math.max(0, c - 1))
    } catch { /* ignore */ }
  }

  // -- mark all read ---------------------------------------------------------
  const handleMarkAllRead = async () => {
    try {
      const summary = await markAllNotificationsRead()
      setNotifications(prev => prev.map(n => ({ ...n, read: true })))
      setUnreadCount(summary?.unread ?? 0)
    } catch { /* ignore */ }
  }

  // -- logout ----------------------------------------------------------------
  const handleLogout = async () => {
    setActiveMenu(null)
    await logout()
    navigate('/login', { replace: true })
  }

  // -- render ----------------------------------------------------------------
  const displayName    = user?.fullName || 'ThirdEye User'
  const displayEmail   = user?.email   || ''
  const displayInitials = initials(displayName)

  return (
    <header className="flex items-center justify-between gap-4 border-b border-sand-line bg-peach-soft px-6 py-3.5">

      {/* Project selector */}
      <div className="relative" ref={projectRef}>
        <button
          onClick={() => setActiveMenu(m => m === 'project' ? null : 'project')}
          aria-expanded={activeMenu === 'project'}
          aria-haspopup="menu"
          className="flex items-center gap-3 rounded-xl border border-sand-line bg-ivory-card px-3.5 py-2 shadow-soft hover:border-plum/30 transition-colors"
        >
          <div className="flex flex-col items-start leading-tight">
            <span className="font-serif text-sm font-semibold text-plum">{activeProject.name}</span>
            <span className="flex items-center gap-1 text-xs text-plum/55">
              <GitBranch size={11} />
              {activeProject.repositoryUrl ? 'Repository connected' : 'Repository not configured'}
            </span>
          </div>
          <ChevronDown size={16} className={`text-plum/50 transition-transform ${activeMenu === 'project' ? 'rotate-180' : ''}`} />
        </button>

        {activeMenu === 'project' && (
          <div role="menu" className="absolute left-0 top-full z-20 mt-2 w-80 overflow-hidden rounded-xl border border-sand-line bg-ivory-card shadow-lifted">
            <div className="px-4 py-2.5 text-xs font-medium text-plum/50 border-b border-sand-line">Switch project</div>
            {projects.map(p => (
              <button key={p.id} role="menuitem"
                onClick={() => { setActiveProject(p); setActiveMenu(null) }}
                className="flex w-full items-center justify-between px-4 py-3 text-left hover:bg-sand/40 transition-colors"
              >
                <div className="flex flex-col">
                  <span className="text-sm font-medium text-plum">{p.name}</span>
                  <span className="text-xs text-plum/55">{p.repositoryUrl || 'Repository not configured'}</span>
                </div>
                {p.id === activeProject.id && <Check size={16} className="text-coral" />}
              </button>
            ))}
            {onNewProject && (
              <button role="menuitem"
                onClick={() => { setActiveMenu(null); onNewProject() }}
                className="flex w-full items-center gap-2.5 border-t border-sand-line px-4 py-3 text-left text-sm font-medium text-plum/60 transition-colors hover:bg-sand/40 hover:text-plum"
              >
                <Plus size={14} />
                New project
              </button>
            )}
          </div>
        )}
      </div>

      {/* Right-side controls */}
      <div className="flex items-center gap-3">

        {/* -- Notifications bell -- */}
        <div className="relative" ref={notificationsRef}>
          <button
            onClick={() => setActiveMenu(m => m === 'notifications' ? null : 'notifications')}
            aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
            aria-expanded={activeMenu === 'notifications'}
            aria-haspopup="dialog"
            className="relative flex h-9 w-9 items-center justify-center rounded-full border border-sand-line bg-ivory-card text-plum/60 transition-colors hover:text-plum"
          >
            <Bell size={16} />
            {unreadCount > 0 && (
              <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-coral text-[9px] font-bold text-white">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          {activeMenu === 'notifications' && (
            <div role="dialog" aria-label="Notifications"
              className="absolute right-0 top-full z-20 mt-2 w-96 overflow-hidden rounded-xl border border-sand-line bg-ivory-card shadow-lifted"
            >
              {/* Header */}
              <div className="flex items-center justify-between border-b border-sand-line px-4 py-3">
                <div>
                  <p className="text-sm font-semibold text-plum">Notifications</p>
                  {unreadCount > 0 && (
                    <p className="mt-0.5 text-xs text-plum/50">{unreadCount} unread</p>
                  )}
                </div>
                {unreadCount > 0 && (
                  <button onClick={handleMarkAllRead}
                    className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-plum/60 hover:bg-sand/60 hover:text-plum transition-colors"
                    title="Mark all as read"
                  >
                    <CheckCheck size={13} /> Mark all read
                  </button>
                )}
              </div>

              {/* Body */}
              <div className="max-h-80 overflow-y-auto divide-y divide-sand-line">
                {notifLoading ? (
                  <div className="flex items-center justify-center py-8">
                    <Loader2 size={18} className="animate-spin text-plum/40" />
                  </div>
                ) : notifications.length === 0 ? (
                  <div className="px-4 py-8 text-center">
                    <Bell size={22} className="mx-auto mb-2 text-plum/20" />
                    <p className="text-sm font-medium text-plum/50">No notifications yet</p>
                    <p className="mt-1 text-xs text-plum/35">
                      Notifications appear here when reviews complete, findings are detected,
                      or tests are generated.
                    </p>
                  </div>
                ) : (
                  notifications.map(n => (
                    <div key={n.id}
                      className={`px-4 py-3 transition-colors ${n.read ? 'opacity-60' : 'bg-plum/[0.02]'}`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            {!n.read && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-coral" />}
                            <p className="text-xs font-semibold text-coral-deep truncate">
                              {EVENT_LABELS[n.eventType] || n.eventType}
                            </p>
                          </div>
                          <p className="mt-0.5 text-sm text-plum leading-snug">{n.title}</p>
                          <p className="mt-0.5 text-xs text-plum/55 leading-snug">{n.message}</p>
                          <p className="mt-1 text-[10px] text-plum/35">{timeAgo(n.createdAt)}</p>
                        </div>
                        {!n.read && (
                          <button onClick={() => handleMarkRead(n.id)}
                            className="shrink-0 rounded-md px-2 py-1 text-[10px] font-medium text-plum/50 hover:bg-sand/60 hover:text-plum transition-colors"
                            title="Mark as read"
                          >
                            Read
                          </button>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

        {/* -- Profile avatar -- */}
        <div className="relative" ref={profileRef}>
          <button
            onClick={() => setActiveMenu(m => m === 'profile' ? null : 'profile')}
            aria-label={`${displayName} — profile`}
            aria-expanded={activeMenu === 'profile'}
            aria-haspopup="dialog"
            className="flex h-9 w-9 items-center justify-center rounded-full bg-plum font-serif text-sm font-semibold text-ivory-100 transition-colors hover:bg-plum/90"
          >
            {displayInitials}
          </button>

          {activeMenu === 'profile' && (
            <div role="dialog" aria-label="Profile"
              className="absolute right-0 top-full z-20 mt-2 w-64 rounded-xl border border-sand-line bg-ivory-card shadow-lifted overflow-hidden"
            >
              {/* Identity */}
              <div className="px-4 py-4 border-b border-sand-line">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-plum font-serif text-sm font-semibold text-ivory-100">
                    {displayInitials}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-plum">{displayName}</p>
                    {displayEmail && (
                      <p className="truncate text-xs text-plum/50">{displayEmail}</p>
                    )}
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="py-1.5">
                <button
                  onClick={() => { navigate('/dashboard/settings'); setActiveMenu(null) }}
                  className="flex w-full items-center gap-3 px-4 py-2.5 text-sm text-plum/70 hover:bg-sand/50 hover:text-plum transition-colors"
                >
                  <Settings size={15} /> Settings
                </button>
                <button
                  onClick={handleLogout}
                  className="flex w-full items-center gap-3 px-4 py-2.5 text-sm text-plum/70 hover:bg-sand/50 hover:text-plum transition-colors"
                >
                  <LogOut size={15} /> Log out
                </button>
              </div>
            </div>
          )}
        </div>

      </div>
    </header>
  )
}

