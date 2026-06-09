package dev.therealflo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Persists per-profile logical bindings and the currently active custom profile.
 * The saved binding files are now used as the software routing table above OpenXR.
 */
public class DefaultBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("request");
    private static final String BINDINGS_DIR = "interaction_profiles";
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "rebound_settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, String> UNIFIED_PROFILES = Map.of(
        "/interaction_profiles/oculus/touch_controller", "/interaction_profiles/oculus/touch_controller",
        "/interaction_profiles/bytedance/pico4_controller", "/interaction_profiles/oculus/touch_controller",
        "/interaction_profiles/bytedance/pico_neo3_controller", "/interaction_profiles/oculus/touch_controller",
        "/interaction_profiles/samsung/odyssey_controller", "/interaction_profiles/oculus/touch_controller"
    );

    private static volatile DefaultBindingManager instance;

    private final Path bindingsDirectory = Paths.get(BINDINGS_DIR);
    private final Path configDirectory = Paths.get(CONFIG_DIR);
    private final Object lock = new Object();

    private DefaultBindingManager() {}

    public static DefaultBindingManager getInstance() {
        if (instance == null) {
            synchronized (DefaultBindingManager.class) {
                if (instance == null) {
                    instance = new DefaultBindingManager();
                }
            }
        }
        return instance;
    }

    public static class ProfileBindingsData {
        public java.util.List<BindingEntry> bindings = new ArrayList<>();
    }

    public static class ConfigData {
        public BindingsConfig bindings = new BindingsConfig();

        public static class BindingsConfig {
            public String profile;
            public String active = "default";

            public BindingsConfig() {}

            public BindingsConfig(String profile, String active) {
                this.profile = profile;
                this.active = active;
            }
        }
    }

    public static class BindingEntry {
        public String action;
        public String inputPath;

        public BindingEntry() {}

        public BindingEntry(String action, String inputPath) {
            this.action = action;
            this.inputPath = inputPath;
        }

        public Pair<String, String> toPair() {
            return Pair.of(this.action, this.inputPath);
        }

        public static BindingEntry fromPair(Pair<String, String> pair) {
            return new BindingEntry(pair.getLeft(), pair.getRight());
        }
    }

    public String getUnifiedProfile(String interactionProfilePath) {
        return normalizeProfile(interactionProfilePath);
    }

    public boolean isUnifiedProfile(String interactionProfilePath) {
        return UNIFIED_PROFILES.containsKey(interactionProfilePath) &&
            !interactionProfilePath.equals(UNIFIED_PROFILES.get(interactionProfilePath));
    }

    public String getActiveProfile(String interactionProfilePath) {
        synchronized (this.lock) {
            String normalizedProfile = normalizeProfile(interactionProfilePath);
            ConfigData config = loadConfig();
            if (config.bindings != null && normalizedProfile.equals(config.bindings.profile)) {
                return config.bindings.active != null ? config.bindings.active : "default";
            }
            return "default";
        }
    }

    public void setActiveProfile(String interactionProfilePath, String activeProfile) {
        synchronized (this.lock) {
            String normalizedProfile = normalizeProfile(interactionProfilePath);
            ConfigData config = loadConfig();
            config.bindings = new ConfigData.BindingsConfig(normalizedProfile, activeProfile);
            saveConfig(config);
        }
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    public void clearActiveProfile() {
        synchronized (this.lock) {
            saveConfig(new ConfigData());
        }
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    public void saveDefaultBindingsIfNeeded(String headsetProfile, Collection<Pair<String, String>> bindings) {
        synchronized (this.lock) {
            String normalizedProfile = normalizeProfile(headsetProfile);
            Path profileFile = getProfileFilePath(normalizedProfile);
            if (Files.exists(profileFile)) {
                return;
            }

            ProfileBindingsData profileData = new ProfileBindingsData();
            for (Pair<String, String> binding : bindings) {
                profileData.bindings.add(BindingEntry.fromPair(binding));
            }
            saveProfileToFile(profileFile, profileData);
            LOGGER.info("Saved initial bindings for {}", normalizedProfile);
        }
    }

    public Collection<Pair<String, String>> loadDefaultBindings(String headsetProfile) {
        synchronized (this.lock) {
            return loadBindingsFile(headsetProfile);
        }
    }

    public Collection<Pair<String, String>> loadBindingsForActiveProfile(
        String interactionProfilePath,
        Collection<Pair<String, String>> fallbackBindings)
    {
        synchronized (this.lock) {
            String normalizedProfile = normalizeProfile(interactionProfilePath);
            String activeProfile = getActiveProfile(normalizedProfile);
            Collection<Pair<String, String>> savedBindings = loadBindingsForSet(normalizedProfile, activeProfile, null);
            if (savedBindings != null) {
                return savedBindings;
            }

            if (fallbackBindings != null) {
                saveDefaultBindingsIfNeeded(normalizedProfile, fallbackBindings);
                return java.util.List.copyOf(fallbackBindings);
            }

            return null;
        }
    }

    public boolean hasSavedBindings(String headsetProfile) {
        synchronized (this.lock) {
            return Files.exists(getProfileFilePath(headsetProfile));
        }
    }

    public Set<String> getAvailableProfiles() {
        synchronized (this.lock) {
            Set<String> profiles = new HashSet<>();
            if (!Files.exists(this.bindingsDirectory)) {
                return profiles;
            }

            try (Stream<Path> paths = Files.walk(this.bindingsDirectory)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> profiles.add(filePathToProfilePath(path)));
            } catch (IOException e) {
                LOGGER.error("Failed to enumerate binding profiles", e);
            }

            return profiles;
        }
    }

    public void clearSavedBindings() {
        synchronized (this.lock) {
            if (!Files.exists(this.bindingsDirectory)) {
                return;
            }

            try (Stream<Path> paths = Files.walk(this.bindingsDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.error("Failed to delete {}", path, e);
                    }
                });
            } catch (IOException e) {
                LOGGER.error("Failed to clear saved bindings", e);
            }
        }
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    public void saveBindingsForProfile(String headsetProfile, Collection<Pair<String, String>> bindings) {
        synchronized (this.lock) {
            Path profileFile = getProfileFilePath(headsetProfile);
            ProfileBindingsData profileData = new ProfileBindingsData();
            for (Pair<String, String> binding : bindings) {
                profileData.bindings.add(BindingEntry.fromPair(binding));
            }
            saveProfileToFile(profileFile, profileData);
        }
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    public Collection<Pair<String, String>> loadBindingsForSet(
        String interactionProfilePath,
        String setId,
        Collection<Pair<String, String>> fallbackBindings)
    {
        synchronized (this.lock) {
            String normalizedProfile = normalizeProfile(interactionProfilePath);
            String normalizedSetId = normalizeSetId(setId);
            Collection<Pair<String, String>> savedBindings = loadBindingsFileForSet(normalizedProfile, normalizedSetId);
            if (savedBindings != null) {
                return savedBindings;
            }

            if (fallbackBindings != null) {
                if ("default".equals(normalizedSetId)) {
                    saveDefaultBindingsIfNeeded(normalizedProfile, fallbackBindings);
                }
                return List.copyOf(fallbackBindings);
            }

            return null;
        }
    }

    public void saveBindingsForSet(String interactionProfilePath, String setId, Collection<Pair<String, String>> bindings) {
        synchronized (this.lock) {
            Path profileFile = getSetFilePath(interactionProfilePath, setId);
            ProfileBindingsData profileData = new ProfileBindingsData();
            for (Pair<String, String> binding : bindings) {
                profileData.bindings.add(BindingEntry.fromPair(binding));
            }
            saveProfileToFile(profileFile, profileData);
        }
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    public void deleteBindingsForSet(String interactionProfilePath, String setId) {
        synchronized (this.lock) {
            Path profileFile = getSetFilePath(interactionProfilePath, setId);
            try {
                Files.deleteIfExists(profileFile);
            } catch (IOException e) {
                LOGGER.error("Failed to delete bindings for set {}", setId, e);
            }
        }
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    public boolean swapJoystickBindings(String interactionProfilePath, String setId) {
        String normalizedProfile = normalizeProfile(interactionProfilePath);
        String normalizedSetId = normalizeSetId(setId);
        Collection<Pair<String, String>> currentBindings = loadBindingsForSet(normalizedProfile, normalizedSetId, null);
        if (currentBindings == null || currentBindings.isEmpty()) {
            return false;
        }

        String leftBasePath = joystickBasePath(normalizedProfile, true);
        String rightBasePath = joystickBasePath(normalizedProfile, false);

        List<Pair<String, String>> swappedBindings = currentBindings.stream()
            .map(binding -> Pair.of(binding.getLeft(), swapPath(binding.getRight(), leftBasePath, rightBasePath)))
            .toList();

        saveBindingsForSet(normalizedProfile, normalizedSetId, swappedBindings);
        return true;
    }

    private String normalizeProfile(String interactionProfilePath) {
        return UNIFIED_PROFILES.getOrDefault(interactionProfilePath, interactionProfilePath);
    }

    private String joystickBasePath(String profile, boolean left) {
        String hand = left ? "left" : "right";
        return switch (profile) {
            case "/interaction_profiles/oculus/touch_controller",
                 "/interaction_profiles/htc/vive_cosmos_controller" ->
                "/user/hand/" + hand + "/input/thumbstick";
            default -> "/user/hand/" + hand + "/input/trackpad";
        };
    }

    private String swapPath(String inputPath, String leftBasePath, String rightBasePath) {
        if (inputPath.startsWith(leftBasePath)) {
            return rightBasePath + inputPath.substring(leftBasePath.length());
        }
        if (inputPath.startsWith(rightBasePath)) {
            return leftBasePath + inputPath.substring(rightBasePath.length());
        }
        return inputPath;
    }

    private Path getProfileFilePath(String interactionProfilePath) {
        String normalizedProfile = normalizeProfile(interactionProfilePath);
        String normalizedPath = normalizedProfile.startsWith("/")
            ? normalizedProfile.substring(1)
            : normalizedProfile;
        return this.bindingsDirectory.resolve(normalizedPath + ".json");
    }

    private Path getSetFilePath(String interactionProfilePath, String setId) {
        String normalizedProfile = normalizeProfile(interactionProfilePath);
        String normalizedSetId = normalizeSetId(setId);
        if ("default".equals(normalizedSetId)) {
            return getProfileFilePath(normalizedProfile);
        }

        String normalizedPath = normalizedProfile.startsWith("/")
            ? normalizedProfile.substring(1)
            : normalizedProfile;
        return this.bindingsDirectory.resolve(normalizedPath).resolve(normalizedSetId + ".json");
    }

    private String filePathToProfilePath(Path filePath) {
        String relativePath = this.bindingsDirectory.relativize(filePath).toString().replace('\\', '/');
        if (relativePath.endsWith(".json")) {
            relativePath = relativePath.substring(0, relativePath.length() - 5);
        }
        return "/" + relativePath;
    }

    private Path getConfigFilePath() {
        return this.configDirectory.resolve(CONFIG_FILE);
    }

    private String normalizeSetId(String setId) {
        if (setId == null || setId.isBlank()) {
            return "default";
        }
        return setId;
    }

    private ConfigData loadConfig() {
        Path configFile = getConfigFilePath();
        if (!Files.exists(configFile)) {
            return new ConfigData();
        }

        try {
            String json = Files.readString(configFile);
            ConfigData config = GSON.fromJson(json, ConfigData.class);
            return config != null ? config : new ConfigData();
        } catch (IOException e) {
            LOGGER.error("Failed to load {}", configFile.toAbsolutePath(), e);
            return new ConfigData();
        }
    }

    private void saveConfig(ConfigData config) {
        try {
            Path configFile = getConfigFilePath();
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(config));
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private Collection<Pair<String, String>> loadBindingsFile(String headsetProfile) {
        Path profileFile = getProfileFilePath(headsetProfile);
        return loadBindingsFile(profileFile);
    }

    private Collection<Pair<String, String>> loadBindingsFileForSet(String interactionProfilePath, String setId) {
        return loadBindingsFile(getSetFilePath(interactionProfilePath, setId));
    }

    private Collection<Pair<String, String>> loadBindingsFile(Path profileFile) {
        if (!Files.exists(profileFile)) {
            return null;
        }

        try {
            String json = Files.readString(profileFile);
            Type type = new TypeToken<ProfileBindingsData>() {}.getType();
            ProfileBindingsData profileData = GSON.fromJson(json, type);
            if (profileData == null || profileData.bindings == null) {
                return null;
            }
            return profileData.bindings.stream().map(BindingEntry::toPair).toList();
        } catch (IOException e) {
            LOGGER.error("Failed to load bindings from {}", profileFile.toAbsolutePath(), e);
            return null;
        }
    }

    private void saveProfileToFile(Path profileFile, ProfileBindingsData profileData) {
        try {
            Files.createDirectories(profileFile.getParent());
            Files.writeString(profileFile, GSON.toJson(profileData));
        } catch (IOException e) {
            LOGGER.error("Failed to save bindings to {}", profileFile.toAbsolutePath(), e);
        }
    }
}
