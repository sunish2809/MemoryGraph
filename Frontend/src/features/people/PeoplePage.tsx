import { Link } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { EmptyState } from '@/components/ui/EmptyState'
import { usePeople } from '@/features/people/usePeople'

export function PeoplePage() {
  const { data: people, isPending, error } = usePeople()

  return (
    <PageShell
      title="People"
      description={
        <>
          Recognised from WhatsApp senders and face tags. Open someone to see their photos, review unlabeled faces in{' '}
          <Link to="/faces" className="text-accent hover:underline">
            Faces
          </Link>
          , or view the{' '}
          <Link to="/graph" className="text-accent hover:underline">
            memory graph
          </Link>
          .
        </>
      }
    >
      {isPending && <div className="h-48 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}
      {error && <Alert>People could not be loaded.</Alert>}

      {people && people.length === 0 && (
        <EmptyState
          icon="◉"
          title="No people yet"
          description="Import a WhatsApp chat to recognise the people you talk to."
          footnote={
            <Link to="/import" className="text-accent hover:underline">
              Import a chat
            </Link>
          }
        />
      )}

      {people && people.length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {people.map((person) => (
            <li key={person.id}>
              <Link
                to={`/people/${person.id}`}
                className="flex items-center justify-between gap-4 rounded-lg border border-line/70 bg-ink/30 px-4 py-3 transition-colors hover:border-accent/40 hover:bg-surface-raised/40"
              >
                <span className="text-sm font-medium text-fg">{person.displayName}</span>
                <span className="text-xs tabular-nums text-fg-muted">
                  {person.memoryCount} {person.memoryCount === 1 ? 'memory' : 'memories'}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </PageShell>
  )
}
