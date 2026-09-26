import { NavLink } from 'react-router-dom'
import {
  Eye,
  LayoutGrid,
  GitCompare,
  ShieldAlert,
  FlaskConical,
  FileText,
  Bot,
  Settings,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-react'

const navItems = [
  { to: '/dashboard/overview',  label: 'Overview',         icon: LayoutGrid  },
  { to: '/dashboard/diff',      label: 'Code diff',        icon: GitCompare  },
  { to: '/dashboard/findings',  label: 'Findings',         icon: ShieldAlert },
  { to: '/dashboard/tests',     label: 'Generated tests',  icon: FlaskConical },
  { to: '/dashboard/report',    label: 'Final report',     icon: FileText    },
  { to: '/dashboard/bob',       label: 'IBM Bob',          icon: Bot         },
  { to: '/dashboard/settings',  label: 'Settings',         icon: Settings    },
]

export default function Sidebar({ collapsed, onToggle }) {
  return (
    <aside
      className={`relative flex flex-col bg-plum text-ivory-100 transition-all duration-300 ease-out ${
        collapsed ? 'w-[76px]' : 'w-64'
      }`}
    >
      <div className="flex items-center gap-2.5 px-5 py-6">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-coral/90">
          <Eye size={18} strokeWidth={2.25} className="text-white" />
        </div>
        {!collapsed && (
          <span className="font-serif text-lg font-semibold tracking-tight whitespace-nowrap">
            ThirdEye AI
          </span>
        )}
      </div>

      <nav className="flex flex-1 flex-col gap-1 px-3">
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-white/10 text-white font-medium'
                  : 'text-ivory-100/65 hover:bg-white/5 hover:text-ivory-100'
              }`
            }
            title={collapsed ? label : undefined}
          >
            <Icon size={18} strokeWidth={2} className="shrink-0" />
            {!collapsed && <span className="whitespace-nowrap">{label}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-white/10 px-3 py-4">
        <button
          onClick={onToggle}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-ivory-100/60 hover:bg-white/5 hover:text-ivory-100 transition-colors"
        >
          {collapsed ? <ChevronsRight size={18} /> : <ChevronsLeft size={18} />}
          {!collapsed && <span>Collapse</span>}
        </button>
      </div>
    </aside>
  )
}
