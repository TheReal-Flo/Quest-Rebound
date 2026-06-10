package dev.therealflo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class RemotePresetClient {
    private static final Pattern MOJANG_SERVER_ID_PATTERN = Pattern.compile("^-?[0-9a-f]{1,40}$");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type PRESET_LIST_TYPE = new TypeToken<PresetListResponse>() {}.getType();
    private static final Type DOWNLOAD_TYPE = new TypeToken<DownloadedPreset>() {}.getType();
    private static final Type AUTH_RESPONSE_TYPE = new TypeToken<AuthVerifyResponse>() {}.getType();
    private static final Type CHALLENGE_RESPONSE_TYPE = new TypeToken<AuthChallengeResponse>() {}.getType();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final RemotePresetConfig config = RemotePresetConfig.getInstance();

    public AuthIdentity ensureLinkedSession() {
        if (this.config.hasUsableToken()) {
            RemotePresetConfig.ConfigData existing = this.config.get();
            return new AuthIdentity(existing.linkedUuid, existing.linkedName, existing.authToken, existing.authTokenExpiresAt);
        }
        return linkCurrentSession();
    }

    public AuthIdentity linkCurrentSession() {
        RemotePresetConfig.ConfigData configData = this.config.get();
        RequestModClient.logInfo("Linking session via " + stripTrailingSlash(configData.baseUrl));

        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();
        if (session == null || session.getAccessToken() == null || session.getAccessToken().isBlank()) {
            throw new IllegalStateException("No Minecraft session is available");
        }
        if (session.getUuidOrNull() == null) {
            throw new IllegalStateException("Minecraft session UUID is unavailable");
        }

        RequestModClient.logInfo("Requesting auth challenge for " + session.getUsername());
        AuthChallengeResponse challengeResponse = request(
            "POST",
            "/api/auth/challenge",
            null,
            null,
            CHALLENGE_RESPONSE_TYPE
        );
        if (challengeResponse.challenge == null || challengeResponse.challenge.isBlank()) {
            throw new IllegalStateException("Preset server returned an invalid auth challenge");
        }
        String serverId = challengeResponse.challenge.trim().toLowerCase(Locale.ROOT);
        if (!isMojangServerId(serverId)) {
            throw new IllegalStateException(
                "Preset server returned an invalid auth challenge format. Update the preset server and try again."
            );
        }

        RequestModClient.logInfo("Joining Mojang session server for " + session.getUsername());
        try {
            client.getSessionService().joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);
        } catch (AuthenticationException e) {
            RequestModClient.logError("Minecraft joinServer failed for " + session.getUsername(), e);
            String detail = e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : e.getClass().getSimpleName();
            throw new IllegalStateException("Failed to link the current Minecraft session: " + detail, e);
        }

        JsonObject verifyBody = new JsonObject();
        verifyBody.addProperty("challenge", serverId);
        verifyBody.addProperty("username", session.getUsername());

        RequestModClient.logInfo("Verifying linked session for " + session.getUsername());
        AuthVerifyResponse response = request(
            "POST",
            "/api/auth/verify",
            null,
            verifyBody,
            AUTH_RESPONSE_TYPE
        );
        if (response.token == null || response.token.isBlank()) {
            throw new IllegalStateException("Preset server returned an invalid auth token");
        }
        if (response.user == null || response.user.minecraftUuid == null || response.user.currentName == null) {
            throw new IllegalStateException("Preset server returned an invalid auth response");
        }

        this.config.saveAuth(
            response.token,
            response.expiresAt,
            response.user.minecraftUuid,
            response.user.currentName
        );
        RequestModClient.logInfo("Linked session for " + response.user.currentName + " (" + response.user.minecraftUuid + ")");
        return new AuthIdentity(response.user.minecraftUuid, response.user.currentName, response.token, response.expiresAt);
    }

    public List<PresetSummary> listPresets(String query, int offset, int limit) {
        StringBuilder path = new StringBuilder("/api/presets?limit=")
            .append(limit)
            .append("&offset=")
            .append(offset);
        if (query != null && !query.isBlank()) {
            path.append("&q=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        }

        PresetListResponse response = request("GET", path.toString(), null, null, PRESET_LIST_TYPE);
        return response.items;
    }

    public DownloadedPreset downloadPreset(String presetId) {
        return request("GET", "/api/presets/" + presetId + "/download", null, null, DOWNLOAD_TYPE);
    }

    public DownloadedPreset uploadPreset(UploadPresetRequest uploadRequest) {
        AuthIdentity identity = ensureLinkedSession();
        return request(
            "POST",
            "/api/presets",
            identity.token,
            GSON.toJsonTree(uploadRequest),
            DOWNLOAD_TYPE
        );
    }

    private <T> T request(String method, String path, String bearerToken, JsonElement body, Type responseType) {
        try {
            RemotePresetConfig.ConfigData configData = this.config.get();
            URI uri = URI.create(stripTrailingSlash(configData.baseUrl) + path);

            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");

            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }

            if (body != null) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                RequestModClient.logError(
                    "Preset server " + method + " " + uri + " failed with HTTP " + response.statusCode() + ": " + response.body()
                );
                throw buildApiError(response);
            }

            if (responseType == null) {
                return null;
            }

            return GSON.fromJson(response.body(), responseType);
        } catch (IOException e) {
            RequestModClient.logError("Failed to reach preset server for " + method + " " + path, e);
            throw new UncheckedIOException("Failed to reach preset server: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Preset server request interrupted", e);
        }
    }

    private RuntimeException buildApiError(HttpResponse<String> response) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            this.config.clearAuth();
        }

        try {
            JsonObject body = GSON.fromJson(response.body(), JsonObject.class);
            String message = body != null && body.has("error")
                ? body.get("error").getAsString()
                : "Preset server request failed";
            return new IllegalStateException(message + " (" + response.statusCode() + ")");
        } catch (Exception ignored) {
            return new IllegalStateException("Preset server request failed (" + response.statusCode() + ")");
        }
    }

    private static boolean isMojangServerId(String value) {
        return value != null && MOJANG_SERVER_ID_PATTERN.matcher(value).matches();
    }

    private static String stripTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://127.0.0.1:3267";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record AuthIdentity(String minecraftUuid, String currentName, String token, String expiresAt) {}

    public record UploadPresetRequest(
        String title,
        String description,
        String minecraftVersion,
        String modLoader,
        String modVersion,
        List<UploadPresetFile> files
    ) {}

    public record UploadPresetFile(String path, JsonElement content) {}

    public static class PresetListResponse {
        public List<PresetSummary> items = List.of();
    }

    public static class PresetSummary {
        public String id;
        public String ownerUuid;
        public String ownerName;
        public String title;
        public String description;
        public String minecraftVersion;
        public String modLoader;
        public String modVersion;
        public String fileHash;
        public int totalBytes;
        public String createdAt;
    }

    public static class DownloadedPreset {
        public PresetSummary preset;
        public List<DownloadedPresetFile> files = List.of();
    }

    public static class DownloadedPresetFile {
        public String path;
        public JsonElement content;
        public int sizeBytes;
        public String sha256;
    }

    private static class AuthChallengeResponse {
        public String challenge;
    }

    private static class AuthVerifyResponse {
        public String token;
        public String expiresAt;
        public VerifiedUser user;
    }

    private static class VerifiedUser {
        public String minecraftUuid;
        public String currentName;
    }
}
