import "dotenv/config";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");

function intFromEnv(name, fallback) {
  const raw = process.env[name];
  if (!raw) {
    return fallback;
  }

  const value = Number.parseInt(raw, 10);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`Invalid integer for ${name}`);
  }

  return value;
}

export const config = {
  host: process.env.HOST || "0.0.0.0",
  port: intFromEnv("PORT", 3267),
  databasePath: path.resolve(projectRoot, process.env.DATABASE_PATH || "./data/rebound.db"),
  jwtSecret: process.env.JWT_SECRET || "development-secret-change-me",
  authChallengeTtlSeconds: intFromEnv("AUTH_CHALLENGE_TTL_SECONDS", 300),
  authTokenTtlHours: intFromEnv("AUTH_TOKEN_TTL_HOURS", 168),
  maxPresetFiles: intFromEnv("MAX_PRESET_FILES", 32),
  maxFileBytes: intFromEnv("MAX_FILE_BYTES", 256 * 1024),
  maxTotalBytes: intFromEnv("MAX_TOTAL_BYTES", 1024 * 1024)
};

export const isDevelopmentSecret = config.jwtSecret === "development-secret-change-me";
