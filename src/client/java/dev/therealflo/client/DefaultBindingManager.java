package dev.therealflo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages saving and loading of default VR controller bindings and keybind bindings.
 * On first launch, saves the default bindings from Vivecraft to a file.
 * On subsequent launches, loads the saved bindings from the file.
 */
public class DefaultBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VivecraftRemapper");
    private static final String BINDINGS_DIR = "interaction_profiles";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile DefaultBindingManager instance;
    private final Path bindingsDirectory;
    private final Object lock = new Object();

    private DefaultBindingManager() {
        this.bindingsDirectory = Paths.get(BINDINGS_DIR);
    }

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

    /**
     * Container class for a single profile's binding data
     */
    public static class ProfileBindingsData {
        public List<BindingEntry> bindings = new ArrayList<>();

        public ProfileBindingsData() {}
    }

    /**
     * Represents a single VR controller binding entry.
     */
    public static class BindingEntry {
        public String action;
        public String inputPath;

        public BindingEntry() {}

        public BindingEntry(String action, String inputPath) {
            this.action = action;
            this.inputPath = inputPath;
        }

        public Pair<String, String> toPair() {
            return Pair.of(action, inputPath);
        }

        public static BindingEntry fromPair(Pair<String, String> pair) {
            return new BindingEntry(pair.getLeft(), pair.getRight());
        }
    }

    /**
     * Converts an interaction profile path to a file path.
     * Example: "/interaction_profiles/oculus/touch_controller" -> "interaction_profiles/oculus/touch_controller.json"
     */
    private Path getProfileFilePath(String interactionProfilePath) {
        // Remove leading slash if present
        String normalizedPath = interactionProfilePath.startsWith("/") ? 
            interactionProfilePath.substring(1) : interactionProfilePath;
        
        return bindingsDirectory.resolve(normalizedPath + ".json");
    }

    /**
     * Converts a file path back to an interaction profile path.
     * Example: "interaction_profiles/oculus/touch_controller.json" -> "/interaction_profiles/oculus/touch_controller"
     */
    private String filePathToProfilePath(Path filePath) {
        String relativePath = bindingsDirectory.relativize(filePath).toString();
        // Remove .json extension
        if (relativePath.endsWith(".json")) {
            relativePath = relativePath.substring(0, relativePath.length() - 5);
        }
        // Replace backslashes with forward slashes (for Windows)
        relativePath = relativePath.replace('\\', '/');
        // Add leading slash
        return "/" + relativePath;
    }

    /**
     * Saves default VR controller bindings to file if they don't exist yet.
     * Called from OpenXR mixin during loadDefaultBindings().
     */
    public void saveDefaultBindingsIfNeeded(String headsetProfile, Collection<Pair<String, String>> bindings) {
        synchronized (lock) {
            Path profileFile = getProfileFilePath(headsetProfile);
            
            // Check if we already have bindings for this specific headset profile
            if (Files.exists(profileFile)) {
                LOGGER.info("Default VR controller bindings for {} already exist, skipping save", headsetProfile);
                return;
            }

            LOGGER.info("First launch detected for {}, saving default VR controller bindings", headsetProfile);

            ProfileBindingsData profileData = new ProfileBindingsData();
            for (Pair<String, String> binding : bindings) {
                profileData.bindings.add(BindingEntry.fromPair(binding));
            }
            
            saveProfileToFile(profileFile, profileData);
        }
    }

    /**
     * Saves a profile's bindings to its JSON file.
     */
    private void saveProfileToFile(Path profileFile, ProfileBindingsData profileData) {
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(profileFile.getParent());

            String json = GSON.toJson(profileData);
            Files.writeString(profileFile, json);
            LOGGER.info("Saved bindings to {}", profileFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save bindings to file", e);
        }
    }

    /**
     * Loads VR controller bindings from file if it exists.
     * Returns the saved bindings for the specified headset profile, or null if not found.
     */
    public Collection<Pair<String, String>> loadDefaultBindings(String headsetProfile) {
        synchronized (lock) {
            Path profileFile = getProfileFilePath(headsetProfile);
            
            if (!Files.exists(profileFile)) {
                LOGGER.info("No saved VR controller bindings found for {}", headsetProfile);
                return null;
            }

            try {
                String json = Files.readString(profileFile);
                Type type = new TypeToken<ProfileBindingsData>(){}.getType();
                ProfileBindingsData profileData = GSON.fromJson(json, type);

                if (profileData != null && profileData.bindings != null) {
                    LOGGER.info("Loading {} saved VR controller bindings for {}", 
                        profileData.bindings.size(), headsetProfile);
                    
                    return profileData.bindings.stream().map(BindingEntry::toPair).toList();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load bindings from file for {}", headsetProfile, e);
            }
            
            return null;
        }
    }

    /**
     * Checks if we have saved VR controller bindings for the given headset profile.
     */
    public boolean hasSavedBindings(String headsetProfile) {
        synchronized (lock) {
            Path profileFile = getProfileFilePath(headsetProfile);
            return Files.exists(profileFile);
        }
    }

    /**
     * Gets all available VR controller binding profiles.
     */
    public Set<String> getAvailableProfiles() {
        synchronized (lock) {
            Set<String> profiles = new HashSet<>();

            if (!Files.exists(bindingsDirectory)) {
                return profiles;
            }

            try (Stream<Path> paths = Files.walk(bindingsDirectory)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         String profilePath = filePathToProfilePath(path);
                         profiles.add(profilePath);
                     });
            } catch (IOException e) {
                LOGGER.error("Failed to list available profiles", e);
            }

            return profiles;
        }
    }

    /**
     * Clears all saved bindings.
     */
    public void clearSavedBindings() {
        synchronized (lock) {
            try {
                if (Files.exists(bindingsDirectory)) {
                    // Delete all files in the directory recursively
                    try (Stream<Path> paths = Files.walk(bindingsDirectory)) {
                        paths.sorted(Comparator.reverseOrder())
                             .forEach(path -> {
                                 try {
                                     Files.delete(path);
                                 } catch (IOException e) {
                                     LOGGER.error("Failed to delete {}", path, e);
                                 }
                             });
                    }
                    LOGGER.info("Cleared all saved bindings");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to clear bindings directory", e);
            }
        }
    }

    /**
     * Saves VR controller bindings for a specific profile.
     */
    public void saveBindingsForProfile(String headsetProfile, Collection<Pair<String, String>> bindings) {
        synchronized (lock) {
            Path profileFile = getProfileFilePath(headsetProfile);

            ProfileBindingsData profileData = new ProfileBindingsData();
            for (Pair<String, String> binding : bindings) {
                profileData.bindings.add(BindingEntry.fromPair(binding));
            }
            
            saveProfileToFile(profileFile, profileData);
            LOGGER.info("Saved {} VR controller bindings for {}", profileData.bindings.size(), headsetProfile);
        }
    }
}