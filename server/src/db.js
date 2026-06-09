import fs from "node:fs";
import path from "node:path";
import Database from "better-sqlite3";
import { config } from "./config.js";

fs.mkdirSync(path.dirname(config.databasePath), { recursive: true });

export const db = new Database(config.databasePath);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  minecraft_uuid TEXT NOT NULL UNIQUE,
  current_name TEXT NOT NULL,
  linked_at TEXT NOT NULL,
  last_verified_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS login_challenges (
  challenge TEXT PRIMARY KEY,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  consumed_at TEXT,
  requested_ip TEXT
);

CREATE TABLE IF NOT EXISTS presets (
  id TEXT PRIMARY KEY,
  owner_uuid TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  minecraft_version TEXT NOT NULL,
  mod_loader TEXT NOT NULL,
  mod_version TEXT NOT NULL,
  file_hash TEXT NOT NULL,
  total_bytes INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (owner_uuid) REFERENCES users (minecraft_uuid)
);

CREATE TABLE IF NOT EXISTS preset_files (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  preset_id TEXT NOT NULL,
  path TEXT NOT NULL,
  content TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  sha256 TEXT NOT NULL,
  UNIQUE (preset_id, path),
  FOREIGN KEY (preset_id) REFERENCES presets (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS preset_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  preset_id TEXT NOT NULL,
  reporter_uuid TEXT NOT NULL,
  reason TEXT NOT NULL,
  details TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (preset_id) REFERENCES presets (id) ON DELETE CASCADE,
  FOREIGN KEY (reporter_uuid) REFERENCES users (minecraft_uuid)
);

CREATE INDEX IF NOT EXISTS idx_presets_created_at ON presets (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_presets_owner_uuid ON presets (owner_uuid);
CREATE INDEX IF NOT EXISTS idx_preset_files_preset_id ON preset_files (preset_id);
CREATE INDEX IF NOT EXISTS idx_reports_preset_id ON preset_reports (preset_id);
`);

export function nowIso() {
  return new Date().toISOString();
}

export function cleanupExpiredChallenges() {
  db.prepare(`
    DELETE FROM login_challenges
    WHERE consumed_at IS NOT NULL OR expires_at <= ?
  `).run(nowIso());
}
