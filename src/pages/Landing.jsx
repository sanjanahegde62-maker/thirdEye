import { useNavigate } from 'react-router-dom'
import { Eye, ArrowRight, GitBranch, ShieldCheck, FlaskConical, Users } from 'lucide-react'
import Button from '../components/Button'

const capabilities = [
  {
    icon: ShieldCheck,
    title: 'Security & quality scan',
    body: "Every diff is checked against known vulnerability patterns, secrets, and your team's quality conventions.",
  },
  {
    icon: FlaskConical,
    title: 'Test generation',
    body: 'ThirdEye writes unit tests targeted at the exact lines that changed, then runs them for you.',
  },
  {
    icon: Users,
    title: 'Human in the loop',
    body: "Nothing merges on its own. Every finding and generated test waits for your approval.",
  },
]

export default function Landing() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-ivory-100">
      <header className="flex items-center justify-between px-8 py-6 md:px-14">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-plum">
            <Eye size={18} strokeWidth={2.25} className="text-coral" />
          </div>
          <span className="font-serif text-lg font-semibold text-plum">ThirdEye AI</span>
        </div>
      </header>

      <section className="grid grid-cols-1 items-center gap-12 px-8 pb-16 pt-8 md:grid-cols-2 md:px-14 md:pt-16">
        <div>
          <p className="mb-5 text-sm font-medium text-terracotta-deep">Code review that reads every line</p>
          <h1 className="font-serif text-4xl font-semibold leading-[1.1] text-plum sm:text-5xl">
            Your pull requests, reviewed like a senior engineer never sleeps.
          </h1>
          <p className="mt-6 max-w-md text-base leading-relaxed text-plum/70">
            {"ThirdEye AI reads the diff, flags what actually matters, writes the tests you didn't have time for, and hands you a report you can act on in minutes."}
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-4">
            <Button variant="coral" size="lg" icon={ArrowRight} onClick={() => navigate('/dashboard/overview')}>
              Start code review
            </Button>
            <Button variant="ghost" size="lg" onClick={() => navigate('/dashboard/report')}>
              View sample report
            </Button>
          </div>
          <div className="mt-10 flex items-center gap-2 text-sm text-plum/50">
            <GitBranch size={14} />
            Select a project in the dashboard to begin
          </div>
        </div>

        <div className="relative rounded-xl2 border border-sand-line bg-ivory-card p-6 shadow-lifted">
          <div className="absolute inset-x-6 top-6 bottom-6 overflow-hidden rounded-lg pointer-events-none">
            <div className="animate-scan h-24 w-full bg-gradient-to-b from-coral/0 via-coral/10 to-coral/0" />
          </div>
          <div className="mb-4 flex items-center justify-between">
            <span className="font-mono text-xs text-plum/50">bootstrap.ts</span>
            <span className="rounded-full bg-coral-soft px-2.5 py-1 text-xs font-medium text-coral-deep">
              Scanning
            </span>
          </div>
          <div className="space-y-2 font-mono text-[13px] leading-relaxed">
            <div className="rounded bg-coral-soft/60 px-2 py-1 text-terracotta-deep">
              {"- const token = process.env.SANDBOX_TOKEN || 'default-sbx-9f21'"}
            </div>
            <div className="rounded bg-plum-50 px-2 py-1 text-plum">
              + const token = process.env.SANDBOX_TOKEN
            </div>
            <div className="rounded bg-plum-50 px-2 py-1 text-plum">{'+ if (!token) {'}</div>
            <div className="rounded bg-plum-50 px-2 py-1 text-plum pl-4">
              throw new SandboxError(...)
            </div>
            <div className="rounded bg-plum-50 px-2 py-1 text-plum">{'+ }'}</div>
          </div>
          <div className="mt-5 flex items-center gap-2 rounded-lg border border-terracotta/25 bg-terracotta-soft/40 px-3 py-2.5 text-xs text-terracotta-deep">
            <ShieldCheck size={14} className="shrink-0" />
            Hardcoded credential fallback removed — 1 critical finding resolved in this diff
          </div>
        </div>
      </section>

      <section className="border-t border-sand-line bg-peach-soft px-8 py-14 md:px-14">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
          {capabilities.map(({ icon: Icon, title, body }) => (
            <div key={title} className="rounded-xl2 border border-sand-line bg-ivory-card p-6 shadow-soft">
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-plum-50">
                <Icon size={19} className="text-plum" />
              </div>
              <h3 className="mb-2 font-serif text-lg font-semibold text-plum">{title}</h3>
              <p className="text-sm leading-relaxed text-plum/65">{body}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
