import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

import type { PlaceSummary } from '@/types/api'

const ACCENT = '#d4a35c'

/** Dark OSM map of GPS-clustered places. Click a marker to open that place. */
export function PlacesMap({
  places,
  selectedId,
  className = 'h-80 w-full overflow-hidden rounded-xl border border-line/70',
}: {
  places: PlaceSummary[]
  selectedId?: string
  className?: string
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const signature = places.map((place) => `${place.id}:${place.latitude}:${place.longitude}:${place.displayName}`).join('|')

  useEffect(() => {
    const element = containerRef.current
    if (!element || places.length === 0) {
      return
    }

    const map = L.map(element, { scrollWheelZoom: false })
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
      maxZoom: 19,
    }).addTo(map)

    const markers = places.map((place) => {
      const marker = L.circleMarker([place.latitude, place.longitude], {
        radius: place.id === selectedId ? 11 : 8,
        color: ACCENT,
        weight: 2,
        fillColor: ACCENT,
        fillOpacity: place.id === selectedId ? 1 : 0.8,
      })
      marker.bindTooltip(place.displayName)
      marker.on('click', () => navigate(`/places/${place.id}`))
      return marker
    })
    const group = L.featureGroup(markers).addTo(map)
    const bounds = group.getBounds()
    if (bounds.isValid()) {
      map.fitBounds(bounds.pad(places.length === 1 ? 0.6 : 0.2))
    }

    const invalidate = () => map.invalidateSize()
    requestAnimationFrame(invalidate)

    return () => {
      map.remove()
    }
  }, [signature, selectedId, navigate])

  if (places.length === 0) {
    return null
  }

  return <div ref={containerRef} className={className} />
}
