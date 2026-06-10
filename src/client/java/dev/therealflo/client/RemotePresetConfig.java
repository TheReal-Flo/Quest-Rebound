package dev.therealflo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class RemotePresetConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("request");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config").resolve("rebound_server.json");
    private static volatile RemotePresetConfig instance;

    public static class ConfigData {
        public String baseUrl = "https://rebound.justfeli.dev";
        public String authToken = "";
        public String authTokenExpiresAt = "";
        public String linkedUuid = "";
        public String linkedName = "";
    }

    private final Object lock = new Object();

    private RemotePresetConfig() {}

    public static RemotePresetConfig getInstance() {
        if (instance == null) {
            synchronized (RemotePresetConfig.class) {
                if (instance == null) {
                    instance = new RemotePresetConfig();
                }
            }
        }
        return instance;
    }

    public ConfigData get() {
        synchronized (this.lock) {
            ConfigData data = load();
            save(data);
            return data;
        }
    }

    public void saveAuth(String token, String expiresAt, String linkedUuid, String linkedName) {
        synchronized (this.lock) {
            ConfigData data = load();
            data.authToken = token != null ? token : "";
            data.authTokenExpiresAt = expiresAt != null ? expiresAt : "";
            data.linkedUuid = linkedUuid != null ? linkedUuid : "";
            data.linkedName = linkedName != null ? linkedName : "";
            save(data);
        }
    }

    public void clearAuth() {
        saveAuth("", "", "", "");
    }

    public boolean hasUsableToken() {
        synchronized (this.lock) {
            ConfigData data = load();
            if (data.authToken == null || data.authToken.isBlank()) {
                return false;
            }
            if (data.authTokenExpiresAt == null || data.authTokenExpiresAt.isBlank()) {
                return true;
            }

            try {
                return Instant.parse(data.authTokenExpiresAt).isAfter(Instant.now().plusSeconds(30));
            } catch (Exception e) {
                return false;
            }
        }
    }

    private ConfigData load() {
        if (!Files.exists(CONFIG_PATH)) {
            return new ConfigData();
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            ConfigData data = GSON.fromJson(json, ConfigData.class);
            return data != null ? data : new ConfigData();
        } catch (IOException e) {
            LOGGER.error("Failed to load preset server config", e);
            return new ConfigData();
        }
    }

    private void save(ConfigData data) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("Failed to save preset server config", e);
        }
    }
}
