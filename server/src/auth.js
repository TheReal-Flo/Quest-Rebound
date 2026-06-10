import crypto from "node:crypto";
import jwt from "jsonwebtoken";
import { config } from "./config.js";

export class ApiError extends Error {
  constructor(status, message, details = null) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

/** Mojang joinServer/hasJoined only accept signed SHA-1 hex ids (1-40 hex digits, optional leading minus). */
export const MOJANG_SERVER_ID_PATTERN = /^-?[0-9a-f]{1,40}$/;

export function toSignedHexSha1(source) {
  const digest = crypto.createHash("sha1").update(source).digest();
  let value = 0n;

  for (const byte of digest) {
    value = (value << 8n) | BigInt(byte);
  }

  if (value >= (1n << 159n)) {
    value -= 1n << 160n;
  }

  return value.toString(16).toLowerCase();
}

export function randomChallenge() {
  return toSignedHexSha1(crypto.randomBytes(16));
}

export function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

export function signAuthToken(user) {
  return jwt.sign(
    {
      sub: user.minecraft_uuid,
      name: user.current_name
    },
    config.jwtSecret,
    {
      algorithm: "HS256",
      expiresIn: `${config.authTokenTtlHours}h`
    }
  );
}

export function verifyAuthToken(token) {
  try {
    return jwt.verify(token, config.jwtSecret, { algorithms: ["HS256"] });
  } catch {
    throw new ApiError(401, "Invalid or expired bearer token");
  }
}

export function requireBearerToken(req, _res, next) {
  const header = req.headers.authorization || "";
  const [scheme, token] = header.split(" ");

  if (scheme !== "Bearer" || !token) {
    next(new ApiError(401, "Missing bearer token"));
    return;
  }

  req.auth = verifyAuthToken(token);
  next();
}

export function normalizeMinecraftUuid(rawUuid) {
  const compact = String(rawUuid || "").replace(/-/g, "").toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(compact)) {
    throw new ApiError(502, "Session server returned an invalid UUID");
  }

  return [
    compact.slice(0, 8),
    compact.slice(8, 12),
    compact.slice(12, 16),
    compact.slice(16, 20),
    compact.slice(20)
  ].join("-");
}

export async function verifyMinecraftSession({ challenge, username }) {
  const query = new URLSearchParams({
    username,
    serverId: challenge
  });

  const response = await fetch(`https://sessionserver.mojang.com/session/minecraft/hasJoined?${query.toString()}`, {
    method: "GET",
    headers: {
      "Accept": "application/json"
    }
  });

  if (response.status === 204 || response.status === 404) {
    throw new ApiError(401, "Minecraft session verification failed");
  }

  if (!response.ok) {
    throw new ApiError(502, "Minecraft session server error");
  }

  const payload = await response.json();
  if (!payload?.id || !payload?.name) {
    throw new ApiError(401, "Minecraft session verification returned no profile");
  }

  return {
    minecraftUuid: normalizeMinecraftUuid(payload.id),
    currentName: String(payload.name)
  };
}
