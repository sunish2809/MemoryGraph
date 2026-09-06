import { Link, useNavigate } from 'react-router-dom'

import { ScrollPage } from '@/components/layout/PageShell'
import { Button } from '@/components/ui/Button'
import { useAuth } from '@/features/auth/useAuth'
import { markWelcomeSeen } from '@/lib/welcome'

const FEATURES = [
  {
    to: '/timeline',
    title: 'One timeline',
    text: 'Photos, notes and chats become memories with a real date — when it happened, not when you uploaded it.',
    visual: <TimelineVisual />,
  },
  {
    to: '/search',
    title: 'Search by words or meaning',
    text: 'Typed words hit a full-text index. Similar ideas hit vectors. The two rankings are fused so either path can find a memory.',
    visual: <SearchVisual />,
  },
  {
    to: '/ask',
    title: 'Ask about your past',
    text: 'A question retrieves your memories first, then answers only from those. Sources are always shown. No invented trips.',
    visual: <AskVisual />,
  },
  {
    to: '/graph',
    title: 'People as a graph',
    text: 'Who appears together is a link. Open the graph to see the people in your archive, not a social network of strangers.',
    visual: <GraphVisual />,
  },
  {
    to: '/faces',
    title: 'Faces stay on this server',
    text: 'InsightFace runs next to the app. Confirm a name once; later photos can suggest the same person. Nothing is sent to a face-recognition company.',
    visual: <FacesVisual />,
  },
  {
    to: '/places',
    title: 'Places and trips',
    text: 'GPS becomes a named place you can rename or merge. Stretch a few days into a trip and keep those memories together.',
    visual: <PlacesVisual />,
  },
  {
    to: '/import',
    title: 'Bring an archive in',
    text: 'WhatsApp exports and Google Photos land as the same memories — conversations by day, photos with their original time.',
    visual: <ImportVisual />,
  },
  {
    to: '/privacy',
    title: 'Private by account',
    text: 'Your rows and files are scoped to you. Other accounts on this server cannot see them. Export or delete from Privacy.',
    visual: <PrivacyVisual />,
  },
] as const

export function WelcomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const firstName = user?.displayName.split(/\s+/)[0] ?? 'there'

  function enterArchive(to: string) {
    if (user) {
      markWelcomeSeen(user.id)
    }
    navigate(to)
  }

  return (
    <ScrollPage>
      <header className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
        <div className="max-w-xl">
          <p className="text-[10px] tracking-[0.2em] text-accent uppercase">Start here</p>
          <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight text-fg sm:text-4xl">
            Welcome, {firstName}.
          </h1>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">
            MemoryGraph is a searchable archive of <em className="text-fg not-italic">your</em> life.
            Everything you add becomes a memory — with a time, people and a place — that you can
            search or ask about in plain language.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" onClick={() => enterArchive('/')}>
            Enter your archive
          </Button>
          <Button type="button" variant="secondary" onClick={() => enterArchive('/import')}>
            Import photos or chats
          </Button>
        </div>
      </header>

      <section
        aria-label="How memories connect"
        className="overflow-hidden rounded-panel border border-line/70 bg-ink/50 px-4 py-6 sm:px-8"
      >
        <p className="mb-4 text-center text-[10px] tracking-[0.16em] text-fg-muted uppercase">
          A memory is the centre
        </p>
        <MemoryConstellation />
        <p className="mx-auto mt-4 max-w-lg text-center text-xs leading-relaxed text-fg-muted">
          A photo, a WhatsApp day and a note are the same shape. Search, the map, faces and Ask all
          read that one model — they do not keep a second copy of your life.
        </p>
      </section>

      <section className="grid gap-3 sm:grid-cols-2">
        {FEATURES.map((feature) => (
          <article
            key={feature.to}
            className="flex flex-col overflow-hidden rounded-panel border border-line/70 bg-ink/35"
          >
            <div className="flex h-36 items-center justify-center border-b border-line/50 bg-surface/25 px-4">
              {feature.visual}
            </div>
            <div className="flex flex-1 flex-col gap-2 px-4 py-4">
              <h2 className="font-display text-lg font-semibold tracking-tight text-fg">{feature.title}</h2>
              <p className="flex-1 text-sm leading-relaxed text-fg-muted">{feature.text}</p>
              <Link
                to={feature.to}
                className="text-xs text-accent hover:underline"
                onClick={() => {
                  if (user) {
                    markWelcomeSeen(user.id)
                  }
                }}
              >
                Open {feature.title.toLowerCase()} →
              </Link>
            </div>
          </article>
        ))}
      </section>
    </ScrollPage>
  )
}

function MemoryConstellation() {
  return (
    <svg viewBox="0 0 520 200" className="mx-auto h-44 w-full max-w-xl" role="img" aria-hidden="true">
      <defs>
        <linearGradient id="mg-line" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#d4a35c" stopOpacity="0.85" />
          <stop offset="100%" stopColor="#5eb8a8" stopOpacity="0.75" />
        </linearGradient>
      </defs>
      {[
        [260, 100, 168, 36, 'Photo'],
        [260, 100, 352, 36, 'Chat'],
        [260, 100, 92, 118, 'Person'],
        [260, 100, 428, 118, 'Place'],
        [260, 100, 260, 176, 'Ask'],
      ].map(([x1, y1, x2, y2], i) => (
        <line
          key={i}
          x1={x1}
          y1={y1}
          x2={x2}
          y2={y2}
          stroke="url(#mg-line)"
          strokeWidth="1.25"
          opacity="0.7"
        />
      ))}
      <OrbitNode x={168} y={36} label="Photo" fill="#d4a35c" />
      <OrbitNode x={352} y={36} label="Chat" fill="#5eb8a8" />
      <OrbitNode x={92} y={118} label="Person" fill="#c48a3a" />
      <OrbitNode x={428} y={118} label="Place" fill="#5eb8a8" />
      <OrbitNode x={260} y={176} label="Ask" fill="#d4a35c" />
      <g>
        <circle cx="260" cy="100" r="28" fill="#2a2114" stroke="#d4a35c" strokeWidth="1.5" />
        <text
          x="260"
          y="104"
          textAnchor="middle"
          fill="#eef1f6"
          fontSize="11"
          fontFamily="Georgia, serif"
        >
          Memory
        </text>
      </g>
    </svg>
  )
}

function OrbitNode({
  x,
  y,
  label,
  fill,
}: {
  x: number
  y: number
  label: string
  fill: string
}) {
  return (
    <g>
      <circle cx={x} cy={y} r="15" fill="#171d28" stroke={fill} strokeWidth="1.25" />
      <text x={x} y={y + 32} textAnchor="middle" fill="#8b95a8" fontSize="10">
        {label}
      </text>
    </g>
  )
}

function TimelineVisual() {
  const years = ['2019', '2022', '2024', 'Now']
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <line x1="18" y1="40" x2="242" y2="40" stroke="#2a3344" strokeWidth="2" />
      {years.map((year, i) => {
        const x = 28 + i * 68
        return (
          <g key={year}>
            <circle cx={x} cy="40" r={i === 3 ? 6 : 4.5} fill={i === 3 ? '#d4a35c' : '#5eb8a8'} />
            <text x={x} y="68" textAnchor="middle" fill="#8b95a8" fontSize="10">
              {year}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

function SearchVisual() {
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <path d="M20 24 C80 24, 80 64, 140 64 L240 64" fill="none" stroke="#d4a35c" strokeWidth="1.6" />
      <path d="M20 64 C80 64, 80 24, 140 24 L240 24" fill="none" stroke="#5eb8a8" strokeWidth="1.6" />
      <text x="20" y="18" fill="#8b95a8" fontSize="9">
        words
      </text>
      <text x="20" y="82" fill="#8b95a8" fontSize="9">
        meaning
      </text>
      <circle cx="240" cy="44" r="7" fill="#d4a35c" />
    </svg>
  )
}

function AskVisual() {
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <rect x="16" y="16" width="150" height="28" rx="6" fill="#171d28" stroke="#2a3344" />
      <text x="28" y="34" fill="#eef1f6" fontSize="10">
        What happened in Sikkim?
      </text>
      <rect x="90" y="52" width="70" height="22" rx="4" fill="#2a2114" stroke="#d4a35c" />
      <rect x="168" y="52" width="70" height="22" rx="4" fill="#2a2114" stroke="#5eb8a8" />
      <text x="100" y="67" fill="#d4a35c" fontSize="9">
        source
      </text>
      <text x="180" y="67" fill="#5eb8a8" fontSize="9">
        source
      </text>
    </svg>
  )
}

function GraphVisual() {
  const nodes = [
    [40, 44],
    [100, 22],
    [160, 50],
    [210, 28],
    [130, 72],
  ] as const
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <line x1="40" y1="44" x2="100" y2="22" stroke="#d4a35c" strokeOpacity="0.55" />
      <line x1="100" y1="22" x2="160" y2="50" stroke="#5eb8a8" strokeOpacity="0.55" />
      <line x1="160" y1="50" x2="210" y2="28" stroke="#d4a35c" strokeOpacity="0.45" />
      <line x1="40" y1="44" x2="130" y2="72" stroke="#5eb8a8" strokeOpacity="0.45" />
      <line x1="130" y1="72" x2="160" y2="50" stroke="#d4a35c" strokeOpacity="0.4" />
      {nodes.map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r={i === 2 ? 7 : 5} fill={i % 2 ? '#5eb8a8' : '#d4a35c'} />
      ))}
    </svg>
  )
}

function FacesVisual() {
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      {[36, 108, 180].map((x, i) => (
        <g key={x}>
          <rect x={x} y="16" width="48" height="56" rx="10" fill="#171d28" stroke="#d4a35c" />
          <circle cx={x + 24} cy="38" r="8" fill="#2a2114" stroke="#d4a35c" />
          <rect x={x + 12} y="54" width="24" height="6" rx="3" fill="#2a3344" />
          {i === 0 && (
            <text x={x + 24} y="84" textAnchor="middle" fill="#8b95a8" fontSize="8">
              confirm
            </text>
          )}
        </g>
      ))}
    </svg>
  )
}

function PlacesVisual() {
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <rect x="20" y="14" width="220" height="60" rx="8" fill="#10141c" stroke="#2a3344" />
      {[0, 1, 2, 3].map((i) => (
        <line key={`v${i}`} x1={64 + i * 44} y1="14" x2={64 + i * 44} y2="74" stroke="#2a3344" />
      ))}
      <line x1="20" y1="44" x2="240" y2="44" stroke="#2a3344" />
      <path d="M130 28 C130 28, 118 48, 130 56 C142 48, 130 28, 130 28" fill="#d4a35c" />
      <circle cx="130" cy="36" r="3.5" fill="#07090d" />
      <path d="M48 62 C90 50, 150 70, 210 40" fill="none" stroke="#5eb8a8" strokeWidth="1.5" />
    </svg>
  )
}

function ImportVisual() {
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <rect x="16" y="22" width="70" height="44" rx="6" fill="#171d28" stroke="#2a3344" />
      <text x="51" y="48" textAnchor="middle" fill="#8b95a8" fontSize="9">
        WhatsApp
      </text>
      <rect x="16" y="8" width="70" height="18" rx="4" fill="#171d28" stroke="#2a3344" />
      <text x="51" y="21" textAnchor="middle" fill="#8b95a8" fontSize="8">
        Photos
      </text>
      <path d="M96 44 H150" stroke="#d4a35c" strokeWidth="1.5" markerEnd="url(#arr)" />
      <polygon points="150,40 162,44 150,48" fill="#d4a35c" />
      <rect x="168" y="20" width="78" height="48" rx="8" fill="#2a2114" stroke="#d4a35c" />
      <text x="207" y="48" textAnchor="middle" fill="#d4a35c" fontSize="10">
        Memory
      </text>
    </svg>
  )
}

function PrivacyVisual() {
  return (
    <svg viewBox="0 0 260 88" className="h-24 w-56" aria-hidden="true">
      <rect x="88" y="36" width="84" height="40" rx="8" fill="#171d28" stroke="#d4a35c" />
      <path
        d="M108 36 V26 a22 22 0 0 1 44 0 V36"
        fill="none"
        stroke="#d4a35c"
        strokeWidth="2"
      />
      <circle cx="130" cy="54" r="5" fill="#d4a35c" />
      <text x="130" y="84" textAnchor="middle" fill="#8b95a8" fontSize="9">
        this server
      </text>
    </svg>
  )
}
