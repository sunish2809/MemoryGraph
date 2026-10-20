/**
 * Drawn stand-ins so Faces/Timeline look populated without using photos of real people.
 */

function blob(parts: string[]): Blob {
  return new Blob(parts, { type: 'image/svg+xml' })
}

function person(cx: number, cy: number, scale: number, hair: string, skin: string, shirt: string): string {
  const s = scale
  return (
    '<g>' +
    '<ellipse cx="' + cx + '" cy="' + (cy + 118 * s) + '" rx="' + 52 * s + '" ry="' + 70 * s + '" fill="' + shirt + '"/>' +
    '<circle cx="' + cx + '" cy="' + cy + '" r="' + 38 * s + '" fill="' + skin + '"/>' +
    '<ellipse cx="' + cx + '" cy="' + (cy - 22 * s) + '" rx="' + 40 * s + '" ry="' + 28 * s + '" fill="' + hair + '"/>' +
    '<circle cx="' + (cx - 12 * s) + '" cy="' + (cy - 2 * s) + '" r="' + 4 * s + '" fill="#1a1510"/>' +
    '<circle cx="' + (cx + 12 * s) + '" cy="' + (cy - 2 * s) + '" r="' + 4 * s + '" fill="#1a1510"/>' +
    '</g>'
  )
}

const open = '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="720" viewBox="0 0 960 720">'
const close = '</svg>'

export function tigerNestPortrait(): Blob {
  return blob([
    open,
    '<rect width="960" height="720" fill="#1c241c"/>',
    '<polygon points="0,520 180,240 360,520" fill="#3d4a38"/>',
    '<polygon points="200,540 480,160 760,540" fill="#4a3d2e"/>',
    '<polygon points="560,560 780,220 960,560" fill="#2e3d32"/>',
    '<rect y="520" width="960" height="200" fill="#2a2318"/>',
    '<rect x="430" y="280" width="90" height="70" fill="#c4a574" opacity="0.85"/>',
    person(410, 210, 1.15, '#2a1810', '#c9a07a', '#4a6b8a'),
    close,
  ])
}

export function thimphuPortrait(): Blob {
  return blob([
    open,
    '<rect width="960" height="720" fill="#243044"/>',
    '<circle cx="720" cy="220" r="140" fill="#d4c4a0"/>',
    '<circle cx="720" cy="220" r="90" fill="#e8dcc0"/>',
    '<rect y="480" width="960" height="240" fill="#1a2230"/>',
    person(490, 220, 1.05, '#1a1210', '#d4b896', '#6b3a3a'),
    person(280, 340, 0.85, '#3a2418', '#c4a07a', '#2d4a3a'),
    close,
  ])
}

export function birthdayPortrait(): Blob {
  return blob([
    open,
    '<rect width="960" height="720" fill="#2a2118"/>',
    '<rect x="80" y="80" width="800" height="520" rx="12" fill="#3a3024"/>',
    '<ellipse cx="480" cy="560" rx="90" ry="28" fill="#f3e6c8"/>',
    '<rect x="430" y="430" width="100" height="130" fill="#e8d4a8"/>',
    '<rect x="455" y="400" width="50" height="40" fill="#d4a35c"/>',
    person(300, 280, 1.1, '#4a3020', '#e0c4a0', '#8a3a4a'),
    person(640, 300, 1.05, '#1a1210', '#c9a07a', '#3d5c6b'),
    close,
  ])
}

export function genericPortrait(): Blob {
  return blob([
    open,
    '<rect width="960" height="720" fill="#171d28"/>',
    person(480, 300, 1.4, '#2a1810', '#c9a07a', '#3a4a5c'),
    close,
  ])
}
