import { useEffect, useState } from 'react'

interface PresetSummary {
  id: string
  ownerUuid: string
  ownerName: string
  title: string
  description: string
  minecraftVersion: string
  modLoader: string
  modVersion: string
  totalBytes: number
  createdAt: string
}

const PAGE_SIZE = 20

export function PresetsPage() {
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [offset, setOffset] = useState(0)
  const [items, setItems] = useState<PresetSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    const params = new URLSearchParams({
      limit: String(PAGE_SIZE),
      offset: String(offset),
    })
    if (search) params.set('q', search)

    fetch(`/api/presets?${params}`, { signal: controller.signal })
      .then((res) => {
        if (!res.ok) throw new Error(`Server responded with ${res.status}`)
        return res.json()
      })
      .then((data: { items: PresetSummary[] }) => {
        setItems(data.items)
        setHasMore(data.items.length === PAGE_SIZE)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        setError(err instanceof Error ? err.message : 'Failed to load presets')
        setLoading(false)
      })

    return () => controller.abort()
  }, [search, offset])

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setOffset(0)
    setSearch(query.trim())
  }

  return (
    <div className="presets-page">
      <h1>Browse Presets</h1>
      <form className="search-bar" onSubmit={onSubmit}>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search by title, description or author…"
          aria-label="Search presets"
        />
        <button type="submit">Search</button>
      </form>

      {loading && <p className="status">Loading presets…</p>}
      {error && <p className="status error">Could not load presets: {error}</p>}
      {!loading && !error && items.length === 0 && (
        <p className="status">
          {search ? `No presets found for “${search}”.` : 'No presets have been shared yet.'}
        </p>
      )}

      <ul className="preset-list">
        {items.map((preset) => (
          <li key={preset.id} className="preset-card">
            <div className="preset-card-header">
              <h2>{preset.title}</h2>
              <span className="preset-owner">by {preset.ownerName}</span>
            </div>
            {preset.description && <p className="preset-description">{preset.description}</p>}
            <div className="preset-tags">
              <span className="tag">{preset.minecraftVersion}</span>
              <span className="tag">{preset.modLoader}</span>
              <span className="tag">mod {preset.modVersion}</span>
              <span className="tag muted">
                {new Date(preset.createdAt).toLocaleDateString()}
              </span>
            </div>
          </li>
        ))}
      </ul>

      {(offset > 0 || hasMore) && (
        <div className="pager">
          <button
            type="button"
            disabled={offset === 0 || loading}
            onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}
          >
            Previous
          </button>
          <button
            type="button"
            disabled={!hasMore || loading}
            onClick={() => setOffset(offset + PAGE_SIZE)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
