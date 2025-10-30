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
 * 
 * Includes namespace conflict detection to handle cases where multiple mods
 * try to bind the same VR action.
 */
public class DefaultBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VivecraftRemapper");
    private static final String BINDINGS_DIR = "interaction_profiles";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile DefaultBindingManager instance;
    private final Path bindingsDirectory;
    private final Object lock = new Object();
    
    // Track which mods own which actions to detect conflicts
    private final Map<String, NamespaceInfo> actionNamespaces = new HashMap<>();

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
     * Information about which namespace (mod) owns a specific action.
     */
    public static class NamespaceInfo {
        public String namespace; // The mod ID extracted from the keybind
        public String originalAction; // The full action path
        public int conflictCount; // How many times this action has been registered
        
        public NamespaceInfo(String namespace, String originalAction) {
            this.namespace = namespace;
            this.originalAction = originalAction;
            this.conflictCount = 1;
        }
    }

    /**
     * Container class for a single profile's binding data
     */
    public static class ProfileBindingsData {
        public List<BindingEntry> bindings = new ArrayList<>();
        public Map<String, NamespaceInfo> namespaces = new HashMap<>(); // Track ownership

        public ProfileBindingsData() {}
    }

    /**
     * Represents a single VR controller binding entry.
     */
    public static class BindingEntry {
        public String action;
        public String inputPath;
        public String namespace; // Which mod owns this binding

        public BindingEntry() {}

        public BindingEntry(String action, String inputPath) {
            this.action = action;
            this.inputPath = inputPath;
            this.namespace = extractNamespace(action);
        }

        public Pair<String, String> toPair() {
            return Pair.of(action, inputPath);
        }

        public static BindingEntry fromPair(Pair<String, String> pair) {
            return new BindingEntry(pair.getLeft(), pair.getRight());
        }
    }

    /**
     * Extracts the namespace (mod ID) from a keybind identifier.
     * Examples:
     *   "key.minecraft.forward" -> "minecraft"
     *   "key.vivecraft.menuButton" -> "vivecraft"
     *   "/user/hand/left/input/trigger/value" -> "vivecraft" (default for VR paths)
     */
    private static String extractNamespace(String action) {
        if (action == null) {
            return "unknown";
        }
        
        // Handle OpenXR input paths (start with /user/)
        if (action.startsWith("/user/")) {
            return "vivecraft";
        }
        
        // Handle Minecraft-style keybind identifiers (key.modid.action)
        if (action.startsWith("key.")) {
            String[] parts = action.split("\\.");
            if (parts.length >= 2) {
                return parts[1]; // Return the mod ID
            }
        }
        
        // Default to vivecraft if we can't determine
        return "vivecraft";
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
     * Detects and logs conflicts in action bindings.
     * Returns a deduplicated collection with conflicts resolved.
     */
    private Collection<BindingEntry> detectAndResolveConflicts(Collection<Pair<String, String>> bindings, String profileName) {
        Map<String, BindingEntry> actionToBinding = new LinkedHashMap<>();
        Map<String, List<String>> conflicts = new HashMap<>();
        
        for (Pair<String, String> binding : bindings) {
            BindingEntry entry = BindingEntry.fromPair(binding);
            String action = entry.action;
            
            if (actionToBinding.containsKey(action)) {
                // Conflict detected!
                BindingEntry existing = actionToBinding.get(action);
                
                conflicts.computeIfAbsent(action, k -> new ArrayList<>()).add(
                    String.format("%s (namespace: %s)", existing.inputPath, existing.namespace)
                );
                conflicts.get(action).add(
                    String.format("%s (namespace: %s)", entry.inputPath, entry.namespace)
                );
                
                // Resolution strategy: Keep the first one (you can customize this)
                LOGGER.warn("[ReQuest] Binding conflict detected for action '{}' in profile '{}'", action, profileName);
                LOGGER.warn("[ReQuest]   Keeping: {} (namespace: {})", existing.inputPath, existing.namespace);
                LOGGER.warn("[ReQuest]   Ignoring: {} (namespace: {})", entry.inputPath, entry.namespace);
            } else {
                actionToBinding.put(action, entry);
            }
        }
        
        // Log summary of conflicts
        if (!conflicts.isEmpty()) {
            LOGGER.warn("[ReQuest] ========================================");
            LOGGER.warn("[ReQuest] Found {} conflicting actions in profile '{}':", conflicts.size(), profileName);
            for (Map.Entry<String, List<String>> conflict : conflicts.entrySet()) {
                LOGGER.warn("[ReQuest]   Action '{}' has {} conflicting bindings:", conflict.getKey(), conflict.getValue().size());
                for (String binding : conflict.getValue()) {
                    LOGGER.warn("[ReQuest]     - {}", binding);
                }
            }
            LOGGER.warn("[ReQuest] ========================================");
        }
        
        return actionToBinding.values();
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

            // Detect and resolve conflicts
            Collection<BindingEntry> bindingEntries = detectAndResolveConflicts(bindings, headsetProfile);

            ProfileBindingsData profileData = new ProfileBindingsData();
            profileData.bindings = new ArrayList<>(bindingEntries);
            
            // Store namespace information
            for (BindingEntry entry : bindingEntries) {
                profileData.namespaces.put(entry.action, new NamespaceInfo(entry.namespace, entry.action));
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
                    
                    // Log namespace information if available
                    if (profileData.namespaces != null && !profileData.namespaces.isEmpty()) {
                        Map<String, Long> namespaceCount = profileData.bindings.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                entry -> entry.namespace != null ? entry.namespace : "unknown",
                                java.util.stream.Collectors.counting()
                            ));
                        
                        LOGGER.info("Loaded namespace distribution:");
                        namespaceCount.forEach((namespace, count) -> 
                            LOGGER.info("  - {}: {} bindings", namespace, count)
                        );
                    }
                    
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
            
            // Detect and resolve conflicts
            Collection<BindingEntry> bindingEntries = detectAndResolveConflicts(bindings, headsetProfile);

            ProfileBindingsData profileData = new ProfileBindingsData();
            profileData.bindings = new ArrayList<>(bindingEntries);
            
            // Store namespace information
            for (BindingEntry entry : bindingEntries) {
                profileData.namespaces.put(entry.action, new NamespaceInfo(entry.namespace, entry.action));
            }
            
            saveProfileToFile(profileFile, profileData);
            LOGGER.info("Saved {} VR controller bindings for {}", bindingEntries.size(), headsetProfile);
        }
    }

    /**
     * Gets namespace information for all bindings in a profile.
     * Useful for debugging and conflict resolution.
     */
    public Map<String, NamespaceInfo> getNamespaceInfo(String headsetProfile) {
        synchronized (lock) {
            Path profileFile = getProfileFilePath(headsetProfile);
            
            if (!Files.exists(profileFile)) {
                return new HashMap<>();
            }

            try {
                String json = Files.readString(profileFile);
                Type type = new TypeToken<ProfileBindingsData>(){}.getType();
                ProfileBindingsData profileData = GSON.fromJson(json, type);

                if (profileData != null && profileData.namespaces != null) {
                    return new HashMap<>(profileData.namespaces);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load namespace info for {}", headsetProfile, e);
            }
            
            return new HashMap<>();
        }
    }
}