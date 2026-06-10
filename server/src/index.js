import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import express from "express";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import { config, isDevelopmentSecret } from "./config.js";
import { db, cleanupExpiredChallenges, nowIso } from "./db.js";
import {
  ApiError,
  randomChallenge,
  requireBearerToken,
  signAuthToken,
  verifyMinecraftSession,
  sha256
} from "./auth.js";
import {
  validateChallengeVerify,
  validateDiscoveryQuery,
  validatePresetPayload,
  validateReportPayload
} from "./validation.js";

const app = express();

if (isDevelopmentSecret) {
  console.warn("[quest-rebound-server] Using the development JWT secret. Set JWT_SECRET before deploying.");
}

app.set("trust proxy", 1);

app.disable("x-powered-by");
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      ...helmet.contentSecurityPolicy.getDefaultDirectives(),
      "img-src": ["'self'", "data:", "https://cdn.jsdelivr.net"]
    }
  },
  crossOriginEmbedderPolicy: false
}));
app.use(express.json({ limit: "2mb" }));

app.use("/api/auth", rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false
}));

app.use("/api/presets", rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false
}));

function presetSummaryRow(row) {
  return {
    id: row.id,
    ownerUuid: row.owner_uuid,
    ownerName: row.current_name,
    title: row.title,
    description: row.description,
    minecraftVersion: row.minecraft_version,
    modLoader: row.mod_loader,
    modVersion: row.mod_version,
    fileHash: row.file_hash,
    totalBytes: row.total_bytes,
    createdAt: row.created_at
  };
}

function buildPresetPackage(presetId) {
  const preset = db.prepare(`
    SELECT p.*, u.current_name
    FROM presets p
    JOIN users u ON u.minecraft_uuid = p.owner_uuid
    WHERE p.id = ?
  `).get(presetId);

  if (!preset) {
    throw new ApiError(404, "Preset not found");
  }

  const files = db.prepare(`
    SELECT path, content, size_bytes, sha256
    FROM preset_files
    WHERE preset_id = ?
    ORDER BY path ASC
  `).all(presetId).map(file => ({
    path: file.path,
    content: JSON.parse(file.content),
    sizeBytes: file.size_bytes,
    sha256: file.sha256
  }));

  return {
    preset: presetSummaryRow(preset),
    files
  };
}

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/api/auth/challenge", (req, res) => {
  cleanupExpiredChallenges();

  const challenge = randomChallenge();
  const createdAt = nowIso();
  const expiresAt = new Date(Date.now() + config.authChallengeTtlSeconds * 1000).toISOString();

  db.prepare(`
    INSERT INTO login_challenges (challenge, created_at, expires_at, requested_ip)
    VALUES (?, ?, ?, ?)
  `).run(challenge, createdAt, expiresAt, req.ip ?? "");

  res.status(201).json({
    challenge,
    expiresAt
  });
});

app.post("/api/auth/verify", async (req, res, next) => {
  try {
    cleanupExpiredChallenges();
    const body = validateChallengeVerify(req.body);
    const challengeRow = db.prepare(`
      SELECT challenge, expires_at, consumed_at
      FROM login_challenges
      WHERE challenge = ?
    `).get(body.challenge);

    if (!challengeRow || challengeRow.consumed_at || challengeRow.expires_at <= nowIso()) {
      throw new ApiError(401, "Challenge is invalid or expired");
    }

    const verified = await verifyMinecraftSession(body);
    const verifiedAt = nowIso();

    const tx = db.transaction(() => {
      db.prepare(`
        INSERT INTO users (minecraft_uuid, current_name, linked_at, last_verified_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(minecraft_uuid) DO UPDATE SET
          current_name = excluded.current_name,
          last_verified_at = excluded.last_verified_at
      `).run(verified.minecraftUuid, verified.currentName, verifiedAt, verifiedAt);

      db.prepare(`
        UPDATE login_challenges
        SET consumed_at = ?
        WHERE challenge = ?
      `).run(verifiedAt, body.challenge);
    });
    tx();

    const user = db.prepare(`
      SELECT minecraft_uuid, current_name, linked_at, last_verified_at
      FROM users
      WHERE minecraft_uuid = ?
    `).get(verified.minecraftUuid);

    const token = signAuthToken(user);
    const expiresAt = new Date(Date.now() + config.authTokenTtlHours * 60 * 60 * 1000).toISOString();

    res.json({
      token,
      expiresAt,
      user: {
        minecraftUuid: user.minecraft_uuid,
        currentName: user.current_name,
        linkedAt: user.linked_at,
        lastVerifiedAt: user.last_verified_at
      }
    });
  } catch (error) {
    if (error instanceof ApiError) {
      console.warn(
        `[quest-rebound-server] Auth verify failed (${error.status}): ${error.message}`,
        error.details ?? ""
      );
    }
    next(error);
  }
});

app.get("/api/auth/me", requireBearerToken, (req, res) => {
  const user = db.prepare(`
    SELECT minecraft_uuid, current_name, linked_at, last_verified_at
    FROM users
    WHERE minecraft_uuid = ?
  `).get(req.auth.sub);

  if (!user) {
    throw new ApiError(401, "User session no longer exists");
  }

  res.json({
    minecraftUuid: user.minecraft_uuid,
    currentName: user.current_name,
    linkedAt: user.linked_at,
    lastVerifiedAt: user.last_verified_at
  });
});

app.post("/api/presets", requireBearerToken, (req, res) => {
  const payload = validatePresetPayload(req.body);
  const createdAt = nowIso();
  const presetId = crypto.randomUUID();
  const packageHash = sha256(JSON.stringify({
    title: payload.title,
    description: payload.description,
    minecraftVersion: payload.minecraftVersion,
    modLoader: payload.modLoader,
    modVersion: payload.modVersion,
    files: payload.files.map(file => ({
      path: file.path,
      sha256: file.sha256
    }))
  }));

  const tx = db.transaction(() => {
    db.prepare(`
      INSERT INTO presets (
        id, owner_uuid, title, description, minecraft_version,
        mod_loader, mod_version, file_hash, total_bytes, created_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      presetId,
      req.auth.sub,
      payload.title,
      payload.description,
      payload.minecraftVersion,
      payload.modLoader,
      payload.modVersion,
      packageHash,
      payload.totalBytes,
      createdAt
    );

    const insertFile = db.prepare(`
      INSERT INTO preset_files (preset_id, path, content, size_bytes, sha256)
      VALUES (?, ?, ?, ?, ?)
    `);

    for (const file of payload.files) {
      insertFile.run(presetId, file.path, file.content, file.sizeBytes, file.sha256);
    }
  });
  tx();

  res.status(201).json(buildPresetPackage(presetId));
});

app.get("/api/presets", (req, res) => {
  const query = validateDiscoveryQuery(req.query);
  const where = [];
  const params = [];

  if (query.q) {
    where.push("(p.title LIKE ? OR p.description LIKE ? OR u.current_name LIKE ?)");
    const token = `%${query.q}%`;
    params.push(token, token, token);
  }
  if (query.ownerUuid) {
    where.push("p.owner_uuid = ?");
    params.push(query.ownerUuid);
  }
  if (query.minecraftVersion) {
    where.push("p.minecraft_version = ?");
    params.push(query.minecraftVersion);
  }
  if (query.modLoader) {
    where.push("p.mod_loader = ?");
    params.push(query.modLoader);
  }
  if (query.modVersion) {
    where.push("p.mod_version = ?");
    params.push(query.modVersion);
  }

  params.push(query.limit, query.offset);

  const sql = `
    SELECT p.*, u.current_name
    FROM presets p
    JOIN users u ON u.minecraft_uuid = p.owner_uuid
    ${where.length ? `WHERE ${where.join(" AND ")}` : ""}
    ORDER BY p.created_at DESC
    LIMIT ? OFFSET ?
  `;

  const rows = db.prepare(sql).all(...params);
  res.json({
    items: rows.map(presetSummaryRow),
    limit: query.limit,
    offset: query.offset
  });
});

app.get("/api/presets/:id", (req, res) => {
  const preset = db.prepare(`
    SELECT p.*, u.current_name
    FROM presets p
    JOIN users u ON u.minecraft_uuid = p.owner_uuid
    WHERE p.id = ?
  `).get(req.params.id);

  if (!preset) {
    throw new ApiError(404, "Preset not found");
  }

  const files = db.prepare(`
    SELECT path, size_bytes, sha256
    FROM preset_files
    WHERE preset_id = ?
    ORDER BY path ASC
  `).all(req.params.id).map(file => ({
    path: file.path,
    sizeBytes: file.size_bytes,
    sha256: file.sha256
  }));

  res.json({
    ...presetSummaryRow(preset),
    files
  });
});

app.get("/api/presets/:id/download", (req, res) => {
  res.json(buildPresetPackage(req.params.id));
});

app.get("/api/users/:uuid/presets", (req, res) => {
  const rows = db.prepare(`
    SELECT p.*, u.current_name
    FROM presets p
    JOIN users u ON u.minecraft_uuid = p.owner_uuid
    WHERE p.owner_uuid = ?
    ORDER BY p.created_at DESC
  `).all(String(req.params.uuid).toLowerCase());

  res.json({
    items: rows.map(presetSummaryRow)
  });
});

app.post("/api/presets/:id/report", requireBearerToken, (req, res) => {
  const report = validateReportPayload(req.body);
  const presetExists = db.prepare(`SELECT 1 FROM presets WHERE id = ?`).get(req.params.id);
  if (!presetExists) {
    throw new ApiError(404, "Preset not found");
  }

  db.prepare(`
    INSERT INTO preset_reports (preset_id, reporter_uuid, reason, details, created_at)
    VALUES (?, ?, ?, ?, ?)
  `).run(req.params.id, req.auth.sub, report.reason, report.details, nowIso());

  res.status(201).json({ ok: true });
});

const websiteIndexPath = path.join(config.websiteDistPath, "index.html");

if (fs.existsSync(websiteIndexPath)) {
  app.use(express.static(config.websiteDistPath));

  app.get(/^\/(?!api\/|health$).*/, (_req, res) => {
    res.sendFile(websiteIndexPath);
  });
} else {
  console.warn(
    `[quest-rebound-server] Website bundle not found at ${config.websiteDistPath}. ` +
    "Run \"npm run build\" in rebound-website to serve the homepage."
  );
}

app.use((error, _req, res, _next) => {
  if (error instanceof ApiError) {
    res.status(error.status).json({
      error: error.message,
      details: error.details
    });
    return;
  }

  console.error(error);
  res.status(500).json({
    error: "Internal server error"
  });
});

app.listen(config.port, config.host, () => {
  console.log(`Quest: Rebound server listening on http://${config.host}:${config.port}`);
});
