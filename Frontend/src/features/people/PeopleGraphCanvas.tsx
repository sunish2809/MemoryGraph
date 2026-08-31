import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import type { PeopleGraph } from '@/types/api'

type Vec3 = { x: number; y: number; z: number }

type SimNode = {
  id: string
  label: string
  memoryCount: number
  connected: boolean
  pos: Vec3
  vel: Vec3
}

const BASE_LINK = 78
const FOV = 520

/**
 * Minimal 3D co-occurrence graph: drag to orbit, scroll to zoom, expand/contract spacing.
 */
export function PeopleGraphCanvas({ data }: { data: PeopleGraph }) {
  const navigate = useNavigate()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const wrapRef = useRef<HTMLDivElement>(null)
  const [hoverId, setHoverId] = useState<string | null>(null)
  const [spacing, setSpacing] = useState(1)
  const [hint, setHint] = useState('Drag to rotate · scroll to zoom')

  const state = useRef({
    yaw: 0.35,
    pitch: 0.28,
    distance: 420,
    dragging: false,
    moved: false,
    lastX: 0,
    lastY: 0,
    nodes: [] as SimNode[],
    edges: [] as { a: string; b: string; w: number }[],
    spacing: 1,
    hoverId: null as string | null,
    raf: 0,
  })

  const neighborIds = useMemo(() => {
    if (!hoverId) return null
    const set = new Set<string>([hoverId])
    for (const edge of data.edges) {
      if (edge.fromPersonId === hoverId) set.add(edge.toPersonId)
      if (edge.toPersonId === hoverId) set.add(edge.fromPersonId)
    }
    return set
  }, [data.edges, hoverId])

  useEffect(() => {
    state.current.spacing = spacing
  }, [spacing])

  useEffect(() => {
    state.current.hoverId = hoverId
  }, [hoverId])

  useEffect(() => {
    const degree = new Map<string, number>()
    for (const n of data.nodes) degree.set(n.id, 0)
    for (const e of data.edges) {
      degree.set(e.fromPersonId, (degree.get(e.fromPersonId) ?? 0) + 1)
      degree.set(e.toPersonId, (degree.get(e.toPersonId) ?? 0) + 1)
    }

    const nodes: SimNode[] = data.nodes.map((n, i) => {
      const angle = (Math.PI * 2 * i) / Math.max(data.nodes.length, 1)
      const r = 90 + (i % 5) * 18
      return {
        id: n.id,
        label: shortName(n.displayName),
        memoryCount: n.memoryCount,
        connected: (degree.get(n.id) ?? 0) > 0,
        pos: {
          x: Math.cos(angle) * r,
          y: Math.sin(angle * 1.7) * 40,
          z: Math.sin(angle) * r,
        },
        vel: { x: 0, y: 0, z: 0 },
      }
    })

    state.current.nodes = nodes
    state.current.edges = data.edges.map((e) => ({
      a: e.fromPersonId,
      b: e.toPersonId,
      w: e.sharedMemories,
    }))
    state.current.distance = Math.max(280, 180 + nodes.length * 22)
  }, [data])

  useEffect(() => {
    const canvasEl = canvasRef.current
    const wrapEl = wrapRef.current
    const ctx2d = canvasEl?.getContext('2d')
    if (!canvasEl || !wrapEl || !ctx2d) return

    // Locals captured for nested handlers (refs.current is mutable for TS).
    const canvas = canvasEl
    const wrap = wrapEl
    const ctx = ctx2d

    const byId = () => {
      const map = new Map<string, SimNode>()
      for (const n of state.current.nodes) map.set(n.id, n)
      return map
    }

    function stepPhysics() {
      const nodes = state.current.nodes
      const linkDist = BASE_LINK * state.current.spacing
      const map = byId()

      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const a = nodes[i]
          const b = nodes[j]
          const dx = b.pos.x - a.pos.x
          const dy = b.pos.y - a.pos.y
          const dz = b.pos.z - a.pos.z
          const dist = Math.hypot(dx, dy, dz) || 0.01
          const force = (48 * state.current.spacing) / (dist * dist)
          const fx = (dx / dist) * force
          const fy = (dy / dist) * force
          const fz = (dz / dist) * force
          a.vel.x -= fx
          a.vel.y -= fy
          a.vel.z -= fz
          b.vel.x += fx
          b.vel.y += fy
          b.vel.z += fz
        }
      }

      for (const edge of state.current.edges) {
        const a = map.get(edge.a)
        const b = map.get(edge.b)
        if (!a || !b) continue
        const dx = b.pos.x - a.pos.x
        const dy = b.pos.y - a.pos.y
        const dz = b.pos.z - a.pos.z
        const dist = Math.hypot(dx, dy, dz) || 0.01
        const pull = ((dist - linkDist) / dist) * 0.035
        a.vel.x += dx * pull
        a.vel.y += dy * pull
        a.vel.z += dz * pull
        b.vel.x -= dx * pull
        b.vel.y -= dy * pull
        b.vel.z -= dz * pull
      }

      for (const n of nodes) {
        n.vel.x += -n.pos.x * 0.0015
        n.vel.y += -n.pos.y * 0.0025
        n.vel.z += -n.pos.z * 0.0015
        n.vel.x *= 0.86
        n.vel.y *= 0.86
        n.vel.z *= 0.86
        n.pos.x += n.vel.x
        n.pos.y += n.vel.y
        n.pos.z += n.vel.z
      }
    }

    function project(p: Vec3, w: number, h: number) {
      const { yaw, pitch, distance } = state.current
      const cosY = Math.cos(yaw)
      const sinY = Math.sin(yaw)
      const cosP = Math.cos(pitch)
      const sinP = Math.sin(pitch)

      let x = p.x * cosY - p.z * sinY
      let z = p.x * sinY + p.z * cosY
      let y = p.y * cosP - z * sinP
      z = p.y * sinP + z * cosP

      const depth = z + distance
      const scale = FOV / Math.max(depth, 40)
      return {
        x: w / 2 + x * scale,
        y: h / 2 + y * scale,
        scale,
        depth,
      }
    }

    function resize() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      const rect = wrap.getBoundingClientRect()
      const w = Math.max(320, Math.floor(rect.width))
      const h = Math.max(360, Math.floor(rect.height))
      canvas.width = Math.floor(w * dpr)
      canvas.height = Math.floor(h * dpr)
      canvas.style.width = `${w}px`
      canvas.style.height = `${h}px`
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      return { w, h }
    }

    let size = resize()
    const onResize = () => {
      size = resize()
    }
    window.addEventListener('resize', onResize)

    const hitTest = (mx: number, my: number): string | null => {
      let best: { id: string; d: number } | null = null
      for (const n of state.current.nodes) {
        const p = project(n.pos, size.w, size.h)
        const r = Math.max(5, 4 + Math.sqrt(n.memoryCount) * p.scale * 0.08)
        const d = Math.hypot(mx - p.x, my - p.y)
        if (d <= r + 8 && (!best || d < best.d)) best = { id: n.id, d }
      }
      return best?.id ?? null
    }

    const onPointerDown = (e: PointerEvent) => {
      state.current.dragging = true
      state.current.moved = false
      state.current.lastX = e.clientX
      state.current.lastY = e.clientY
      canvas.setPointerCapture(e.pointerId)
    }
    const onPointerMove = (e: PointerEvent) => {
      const rect = canvas.getBoundingClientRect()
      const mx = e.clientX - rect.left
      const my = e.clientY - rect.top
      if (state.current.dragging) {
        const dx = e.clientX - state.current.lastX
        const dy = e.clientY - state.current.lastY
        if (Math.hypot(dx, dy) > 2) state.current.moved = true
        state.current.yaw += dx * 0.008
        state.current.pitch = clamp(state.current.pitch + dy * 0.008, -1.2, 1.2)
        state.current.lastX = e.clientX
        state.current.lastY = e.clientY
        setHint('Rotating')
      } else {
        const id = hitTest(mx, my)
        setHoverId(id)
        canvas.style.cursor = id ? 'pointer' : 'grab'
      }
    }
    const onPointerUp = (e: PointerEvent) => {
      const clicked = !state.current.moved
      state.current.dragging = false
      setHint('Drag to rotate · scroll to zoom')
      if (clicked) {
        const rect = canvas.getBoundingClientRect()
        const id = hitTest(e.clientX - rect.left, e.clientY - rect.top)
        if (id) navigate(`/people/${id}`)
      }
      try {
        canvas.releasePointerCapture(e.pointerId)
      } catch {
        /* ignore */
      }
    }
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      state.current.distance = clamp(state.current.distance + e.deltaY * 0.35, 160, 900)
      setHint(e.deltaY > 0 ? 'Zooming out' : 'Zooming in')
    }

    canvas.addEventListener('pointerdown', onPointerDown)
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('pointerup', onPointerUp)
    canvas.addEventListener('wheel', onWheel, { passive: false })

    const draw = () => {
      stepPhysics()
      const { w, h } = size
      ctx.clearRect(0, 0, w, h)

      const grd = ctx.createRadialGradient(w * 0.5, h * 0.55, 20, w * 0.5, h * 0.5, Math.max(w, h) * 0.55)
      grd.addColorStop(0, 'rgb(23 29 40 / 0.35)')
      grd.addColorStop(1, 'rgb(7 9 13 / 0)')
      ctx.fillStyle = grd
      ctx.fillRect(0, 0, w, h)

      const map = byId()
      const hover = state.current.hoverId
      const focused = hover
        ? new Set(
            [hover].concat(
              state.current.edges
                .filter((e) => e.a === hover || e.b === hover)
                .flatMap((e) => [e.a, e.b]),
            ),
          )
        : null

      const projectedEdges = state.current.edges
        .map((edge) => {
          const a = map.get(edge.a)
          const b = map.get(edge.b)
          if (!a || !b) return null
          const pa = project(a.pos, w, h)
          const pb = project(b.pos, w, h)
          const midZ = (pa.depth + pb.depth) / 2
          const on = !focused || (focused.has(edge.a) && focused.has(edge.b))
          return { pa, pb, midZ, on, w: edge.w }
        })
        .filter(Boolean)
        .sort((a, b) => b!.midZ - a!.midZ)

      for (const edge of projectedEdges) {
        if (!edge) continue
        ctx.beginPath()
        ctx.moveTo(edge.pa.x, edge.pa.y)
        ctx.lineTo(edge.pb.x, edge.pb.y)
        ctx.strokeStyle = edge.on ? 'rgb(212 163 92 / 0.45)' : 'rgb(212 163 92 / 0.07)'
        ctx.lineWidth = edge.on ? Math.min(2.2, 0.5 + edge.w * 0.2) : 0.5
        ctx.stroke()
      }

      const projectedNodes = state.current.nodes
        .map((n) => ({ n, p: project(n.pos, w, h) }))
        .sort((a, b) => b.p.depth - a.p.depth)

      for (const { n, p } of projectedNodes) {
        const on = !focused || focused.has(n.id)
        const r =
          Math.max(4, (n.connected ? 5.5 : 3.5) + Math.sqrt(n.memoryCount) * 0.9) *
          (0.55 + p.scale * 0.012)
        ctx.globalAlpha = on ? 1 : 0.18
        ctx.beginPath()
        ctx.arc(p.x, p.y, r, 0, Math.PI * 2)
        ctx.fillStyle = n.connected ? 'rgb(212 163 92 / 0.92)' : 'rgb(148 163 184 / 0.5)'
        ctx.fill()
        if (on && (hover === n.id || !hover)) {
          ctx.fillStyle = 'rgb(238 241 246 / 0.92)'
          ctx.font = `${hover === n.id ? 12 : 11}px var(--font-sans, system-ui)`
          ctx.textAlign = 'center'
          ctx.fillText(n.label, p.x, p.y + r + 14)
        }
        ctx.globalAlpha = 1
      }

      state.current.raf = requestAnimationFrame(draw)
    }

    state.current.raf = requestAnimationFrame(draw)

    return () => {
      cancelAnimationFrame(state.current.raf)
      window.removeEventListener('resize', onResize)
      canvas.removeEventListener('pointerdown', onPointerDown)
      canvas.removeEventListener('pointermove', onPointerMove)
      canvas.removeEventListener('pointerup', onPointerUp)
      canvas.removeEventListener('wheel', onWheel)
    }
  }, [data, navigate])

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-fg-muted">
          {data.nodes.length} people · {data.edges.length} connections
          {neighborIds ? ' · focus' : ''}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            className="rounded-md border border-line/80 px-2.5 py-1 text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
            onClick={() => setSpacing((s) => clamp(s - 0.15, 0.55, 2.2))}
          >
            Contract
          </button>
          <button
            type="button"
            className="rounded-md border border-line/80 px-2.5 py-1 text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
            onClick={() => setSpacing((s) => clamp(s + 0.15, 0.55, 2.2))}
          >
            Expand
          </button>
          <button
            type="button"
            className="rounded-md border border-line/80 px-2.5 py-1 text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
            onClick={() => {
              state.current.yaw += Math.PI / 8
            }}
          >
            Rotate
          </button>
          <button
            type="button"
            className="rounded-md border border-line/80 px-2.5 py-1 text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
            onClick={() => {
              state.current.yaw = 0.35
              state.current.pitch = 0.28
              state.current.distance = Math.max(280, 180 + data.nodes.length * 22)
              setSpacing(1)
            }}
          >
            Reset
          </button>
        </div>
      </div>

      <div
        ref={wrapRef}
        className="relative h-[min(72vh,40rem)] w-full overflow-hidden rounded-panel border border-line/60 bg-ink/40"
      >
        <canvas ref={canvasRef} className="block h-full w-full touch-none" />
        <p className="pointer-events-none absolute bottom-3 left-3 text-[11px] tracking-wide text-fg-muted/80">
          {hint}
        </p>
      </div>
    </div>
  )
}

function shortName(name: string): string {
  return name.length > 18 ? `${name.slice(0, 16)}…` : name
}

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}
