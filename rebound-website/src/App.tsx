import './App.css'
import { Link, usePath } from './router'
import { PresetsPage } from './PresetsPage'

function HomePage() {
  return (
    <main className="hero">
      <h1>Quest: Rebound</h1>
      <p className="tagline">
        The keybind-solution for QuestCraft and other standalone MCVR launchers
      </p>
      <a
        href="https://modrinth.com/mod/quest-rebound"
        target="_blank"
        rel="noreferrer"
        className="modrinth-link"
      >
        <img
          src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png"
          alt="Available on Modrinth"
          height="64"
        />
      </a>
    </main>
  )
}

function App() {
  const path = usePath()

  return (
    <>
      <nav className="navbar">
        <Link to="/" className="brand">
          Quest: Rebound
        </Link>
        <div className="nav-links">
          <Link to="/" className={path === '/' ? 'active' : ''}>
            Home
          </Link>
          <Link to="/presets" className={path === '/presets' ? 'active' : ''}>
            Presets
          </Link>
          <a
            href="https://modrinth.com/mod/quest-rebound"
            target="_blank"
            rel="noreferrer"
          >
            Modrinth
          </a>
        </div>
      </nav>
      {path === '/presets' ? <PresetsPage /> : <HomePage />}
    </>
  )
}

export default App
