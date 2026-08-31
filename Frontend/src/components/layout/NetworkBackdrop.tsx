import { useEffect, useRef } from 'react'

type Particle = {
  x: number
  y: number
  vx: number
  vy: number
  r: number
  accent: boolean
}

type Packet = {
  from: number
  to: number
  t: number
  speed: number
  accent: boolean
}

const COPPER = { r: 212, g: 163, b: 92 }
const TEAL = { r: 94, g: 184, b: 168 }

/**
 * Light particle-network simulation for the page background: nodes drift, nearby pairs
 * link, and occasional packets travel along those edges. Decorative only — no pointer events.
 */
export function NetworkBackdrop() {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const node = canvasRef.current
    if (!node) return
    const gfx = node.getContext('2d', { alpha: true })
    if (!gfx) return
    const canvas: HTMLCanvasElement = node
    const ctx: CanvasRenderingContext2D = gfx

    const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    let reduceMotion = motionQuery.matches
    let raf = 0
    let width = 0
    let height = 0
    let dpr = 1
    let particles: Particle[] = []
    let packets: Packet[] = []
    let last = performance.now()
    let spawnCooldown = 0

    function nodeCount() {
      return Math.round(Math.min(52, Math.max(24, (width * height) / 26_000)))
    }

    function linkDist() {
      return Math.min(170, Math.max(96, Math.hypot(width, height) * 0.088))
    }

    function seed() {
      const n = nodeCount()
      particles = Array.from({ length: n }, (_, i) => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 22,
        vy: (Math.random() - 0.5) * 22,
        r: 1.15 + Math.random() * 1.7,
        accent: i % 5 === 0,
      }))
      packets = []
    }

    function resize() {
      const rect = canvas.getBoundingClientRect()
      width = Math.max(1, rect.width)
      height = Math.max(1, rect.height)
      dpr = Math.min(window.devicePixelRatio || 1, 1.5)
      canvas.width = Math.floor(width * dpr)
      canvas.height = Math.floor(height * dpr)
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      if (particles.length === 0) {
        seed()
        return
      }
      const target = nodeCount()
      if (Math.abs(particles.length - target) > 10) {
        seed()
        return
      }
      for (const p of particles) {
        p.x = Math.min(width, Math.max(0, p.x))
        p.y = Math.min(height, Math.max(0, p.y))
      }
    }

    function spawnPacket() {
      const max = linkDist()
      const candidates: Array<[number, number]> = []
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const d = Math.hypot(particles[i].x - particles[j].x, particles[i].y - particles[j].y)
          if (d < max * 0.92) candidates.push([i, j])
        }
      }
      if (candidates.length === 0) return
      const [from, to] = candidates[Math.floor(Math.random() * candidates.length)]
      packets.push({
        from,
        to,
        t: 0,
        speed: 0.42 + Math.random() * 0.5,
        accent: particles[from].accent || particles[to].accent,
      })
    }

    function draw() {
      ctx.clearRect(0, 0, width, height)
      const max = linkDist()

      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const a = particles[i]
          const b = particles[j]
          const d = Math.hypot(a.x - b.x, a.y - b.y)
          if (d >= max) continue
          const fade = 1 - d / max
          const c = a.accent || b.accent ? TEAL : COPPER
          ctx.strokeStyle = `rgba(${c.r},${c.g},${c.b},${0.14 + fade * 0.32})`
          ctx.lineWidth = 1
          ctx.beginPath()
          ctx.moveTo(a.x, a.y)
          ctx.lineTo(b.x, b.y)
          ctx.stroke()
        }
      }

      for (const pkt of packets) {
        const a = particles[pkt.from]
        const b = particles[pkt.to]
        if (!a || !b) continue
        const x = a.x + (b.x - a.x) * pkt.t
        const y = a.y + (b.y - a.y) * pkt.t
        const c = pkt.accent ? TEAL : COPPER
        ctx.fillStyle = `rgba(${c.r},${c.g},${c.b},0.22)`
        ctx.beginPath()
        ctx.arc(x, y, 5.5, 0, Math.PI * 2)
        ctx.fill()
        ctx.fillStyle = `rgba(${c.r},${c.g},${c.b},0.9)`
        ctx.beginPath()
        ctx.arc(x, y, 1.7, 0, Math.PI * 2)
        ctx.fill()
      }

      for (const p of particles) {
        const c = p.accent ? TEAL : COPPER
        ctx.fillStyle = `rgba(${c.r},${c.g},${c.b},0.2)`
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r * 3.4, 0, Math.PI * 2)
        ctx.fill()
        ctx.fillStyle = `rgba(${c.r},${c.g},${c.b},0.78)`
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fill()
      }
    }

    function tick(now: number) {
      const dt = Math.min(0.033, (now - last) / 1000)
      last = now

      if (!reduceMotion) {
        for (const p of particles) {
          p.x += p.vx * dt
          p.y += p.vy * dt
          if (p.x <= 0 || p.x >= width) p.vx *= -1
          if (p.y <= 0 || p.y >= height) p.vy *= -1
          p.x = Math.min(width, Math.max(0, p.x))
          p.y = Math.min(height, Math.max(0, p.y))
        }

        spawnCooldown -= dt
        if (packets.length < 7 && spawnCooldown <= 0) {
          spawnPacket()
          spawnCooldown = 0.35 + Math.random() * 0.7
        }
        for (let i = packets.length - 1; i >= 0; i--) {
          packets[i].t += packets[i].speed * dt
          if (packets[i].t >= 1) packets.splice(i, 1)
        }
      }

      draw()

      if (!reduceMotion && !document.hidden) {
        raf = requestAnimationFrame(tick)
      }
    }

    function startLoop() {
      cancelAnimationFrame(raf)
      last = performance.now()
      raf = requestAnimationFrame(tick)
    }

    const onMotion = () => {
      reduceMotion = motionQuery.matches
      if (reduceMotion) {
        cancelAnimationFrame(raf)
        packets = []
        draw()
      } else if (!document.hidden) {
        startLoop()
      }
    }

    const onVisibility = () => {
      if (document.hidden || reduceMotion) {
        cancelAnimationFrame(raf)
        return
      }
      startLoop()
    }

    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(canvas)
    motionQuery.addEventListener('change', onMotion)
    document.addEventListener('visibilitychange', onVisibility)
    startLoop()

    return () => {
      cancelAnimationFrame(raf)
      observer.disconnect()
      motionQuery.removeEventListener('change', onMotion)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [])

  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      <canvas ref={canvasRef} className="h-full w-full" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_18%,rgb(7_9_13_/_0.55)_78%,#07090d_100%)]" />
    </div>
  )
}
