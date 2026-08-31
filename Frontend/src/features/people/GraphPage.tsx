import { Link } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { EmptyState } from '@/components/ui/EmptyState'
import { PeopleGraphCanvas } from '@/features/people/PeopleGraphCanvas'
import { usePeopleGraph } from '@/features/people/usePeople'

/**
 * People co-occurrence graph — sparse 3D canvas: orbit, zoom, expand/contract.
 */
export function GraphPage() {
  const { data, isPending, error } = usePeopleGraph()

  return (
    <PageShell
      title="Memory graph"
      description="People linked when they share memories."
      actions={
        <Link to="/people" className="text-sm text-accent hover:underline">
          People list
        </Link>
      }
    >
      {isPending && <div className="h-64 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}
      {error && <Alert>The graph could not be loaded.</Alert>}

      {data && data.nodes.length === 0 && (
        <EmptyState
          icon="⧉"
          title="No people to graph yet"
          description="Import a WhatsApp chat so senders appear as nodes."
          footnote={
            <Link to="/import" className="text-accent hover:underline">
              Import a chat
            </Link>
          }
        />
      )}

      {data && data.nodes.length > 0 && <PeopleGraphCanvas data={data} />}
    </PageShell>
  )
}
