import { z } from "zod";
import { ApiError, sha256 } from "./auth.js";
import { config } from "./config.js";

const bindingEntrySchema = z.object({
  action: z.string().min(1).max(256),
  inputPath: z.string().min(1).max(256)
});

const profileBindingsSchema = z.object({
  bindings: z.array(bindingEntrySchema).max(2048)
});

const reboundSettingsSchema = z.object({
  bindings: z.object({
    profile: z.string().optional(),
    active: z.string().optional()
  }).optional()
});

const reboundSetSchema = z.object({
  profiles: z.record(z.object({
    active: z.string(),
    order: z.array(z.string()),
    sets: z.record(z.object({
      name: z.string(),
      enabled: z.boolean()
    }))
  }))
});

const presetFileInputSchema = z.object({
  path: z.string().min(1).max(255),
  content: z.unknown()
});

const presetInputSchema = z.object({
  title: z.string().trim().min(1).max(80),
  description: z.string().trim().max(500).optional().default(""),
  minecraftVersion: z.string().trim().min(1).max(32),
  modLoader: z.string().trim().min(1).max(32),
  modVersion: z.string().trim().min(1).max(32),
  files: z.array(presetFileInputSchema).min(1).max(config.maxPresetFiles)
});

const reportInputSchema = z.object({
  reason: z.string().trim().min(1).max(80),
  details: z.string().trim().max(1000).optional().default("")
});

const challengeVerifySchema = z.object({
  challenge: z.string().regex(/^[0-9a-f]{64}$/),
  username: z.string().trim().min(1).max(16)
});

const interactionProfilePathRegex = /^interaction_profiles\/[a-z0-9._-]+(?:\/[a-z0-9._-]+)*\.json$/;
const allowedConfigPaths = new Set([
  "config/rebound_settings.json",
  "config/rebound_sets.json"
]);

function normalizeFilePath(rawPath) {
  const path = String(rawPath || "").replaceAll("\\", "/").replace(/^\/+/, "");
  if (path.includes("..") || path.startsWith("./")) {
    throw new ApiError(400, `Illegal file path: ${rawPath}`);
  }
  return path;
}

function validatePath(path) {
  if (allowedConfigPaths.has(path)) {
    return;
  }
  if (interactionProfilePathRegex.test(path)) {
    return;
  }
  throw new ApiError(400, `File path is not allowed: ${path}`);
}

function validateStructuredContent(path, parsedContent) {
  if (path === "config/rebound_settings.json") {
    reboundSettingsSchema.parse(parsedContent);
    return;
  }
  if (path === "config/rebound_sets.json") {
    reboundSetSchema.parse(parsedContent);
    return;
  }
  profileBindingsSchema.parse(parsedContent);
}

function normalizeJsonContent(path, content) {
  validatePath(path);

  const parsed = typeof content === "string" ? JSON.parse(content) : content;
  validateStructuredContent(path, parsed);

  const normalized = `${JSON.stringify(parsed, null, 2)}\n`;
  const byteLength = Buffer.byteLength(normalized, "utf8");

  if (byteLength > config.maxFileBytes) {
    throw new ApiError(400, `File exceeds per-file size limit: ${path}`);
  }

  return {
    path,
    content: normalized,
    sizeBytes: byteLength,
    sha256: sha256(normalized)
  };
}

export function validateChallengeVerify(body) {
  const parsed = challengeVerifySchema.safeParse(body);
  if (!parsed.success) {
    throw new ApiError(400, "Invalid auth verification payload", parsed.error.flatten());
  }
  return parsed.data;
}

export function validatePresetPayload(body) {
  const parsed = presetInputSchema.safeParse(body);
  if (!parsed.success) {
    throw new ApiError(400, "Invalid preset payload", parsed.error.flatten());
  }

  const files = parsed.data.files.map(file => normalizeJsonContent(normalizeFilePath(file.path), file.content));
  const seen = new Set();
  let totalBytes = 0;

  for (const file of files) {
    if (seen.has(file.path)) {
      throw new ApiError(400, `Duplicate file path: ${file.path}`);
    }
    seen.add(file.path);
    totalBytes += file.sizeBytes;
  }

  if (totalBytes > config.maxTotalBytes) {
    throw new ApiError(400, "Preset exceeds total size limit");
  }

  return {
    title: parsed.data.title,
    description: parsed.data.description,
    minecraftVersion: parsed.data.minecraftVersion,
    modLoader: parsed.data.modLoader,
    modVersion: parsed.data.modVersion,
    totalBytes,
    files: files.sort((left, right) => left.path.localeCompare(right.path))
  };
}

export function validateReportPayload(body) {
  const parsed = reportInputSchema.safeParse(body);
  if (!parsed.success) {
    throw new ApiError(400, "Invalid report payload", parsed.error.flatten());
  }
  return parsed.data;
}

export function validateDiscoveryQuery(query) {
  const limit = Math.min(Number.parseInt(String(query.limit || "20"), 10) || 20, 50);
  const offset = Math.max(Number.parseInt(String(query.offset || "0"), 10) || 0, 0);

  return {
    q: typeof query.q === "string" ? query.q.trim() : "",
    ownerUuid: typeof query.ownerUuid === "string" ? query.ownerUuid.trim().toLowerCase() : "",
    minecraftVersion: typeof query.minecraftVersion === "string" ? query.minecraftVersion.trim() : "",
    modLoader: typeof query.modLoader === "string" ? query.modLoader.trim() : "",
    modVersion: typeof query.modVersion === "string" ? query.modVersion.trim() : "",
    limit,
    offset
  };
}
