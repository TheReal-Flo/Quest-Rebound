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

/**
 * Manages saving and loading of default VR controller bindings.
 * On first launch, saves the default bindings from Vivecraft to a file.
 * On subsequent launches, loads the saved bindings from the file.
 */
public class DefaultBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("request");
    private static final String BINDINGS_FILE = "vivecraft_default_bindings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static DefaultBindingManager instance;
    private final Path bindingsFilePath;
    private Map<String, List<BindingEntry>> savedBindings = new HashMap<>();
    private boolean bindingsLoaded = false;

    private DefaultBindingManager() {
        this.bindingsFilePath = Paths.get(BINDINGS_FILE);
    }

    public static DefaultBindingManager getInstance() {
        if (instance == null) {
            instance = new DefaultBindingManager();
        }
        return instance;
    }

    /**
     * Represents a single binding entry (action -> input path)
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
     * Saves default bindings to file if they don't exist yet.
     * Called from OpenXR mixin during loadDefaultBindings().
     */
    public void saveDefaultBindingsIfNeeded(String headsetProfile, Collection<Pair<String, String>> bindings) {
        // Load existing bindings first if file exists
        if (!bindingsLoaded) {
            loadBindingsFromFile();
            bindingsLoaded = true;
        }

        // Check if we already have bindings for this specific headset profile
        if (savedBindings.containsKey(headsetProfile)) {
            LOGGER.info("[ReQuest] Default bindings for {} already exist, skipping save", headsetProfile);
            return;
        }

        LOGGER.info("[ReQuest] First launch detected for {}, saving default bindings", headsetProfile);

        // Convert pairs to binding entries
        List<BindingEntry> bindingEntries = bindings.stream()
                .map(BindingEntry::fromPair)
                .toList();

        savedBindings.put(headsetProfile, bindingEntries);
        saveBindingsToFile();
    }

    /**
     * Saves all current bindings to the JSON file.
     */
    private void saveBindingsToFile() {
        try {
            String json = GSON.toJson(savedBindings);
            Files.writeString(bindingsFilePath, json);
            LOGGER.info("[ReQuest] Saved default bindings to {}", bindingsFilePath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[ReQuest] Failed to save default bindings to file", e);
        }
    }

    /**
     * Loads bindings from file if it exists.
     * Returns the saved bindings for the specified headset profile, or null if not found.
     */
    public Collection<Pair<String, String>> loadDefaultBindings(String headsetProfile) {
        if (!bindingsLoaded) {
            loadBindingsFromFile();
            bindingsLoaded = true;
        }

        List<BindingEntry> entries = savedBindings.get(headsetProfile);
        if (entries == null) {
            LOGGER.warn("[ReQuest] No saved bindings found for headset profile: {}", headsetProfile);
            return null;
        }

        LOGGER.info("[ReQuest] Loaded {} saved bindings for {}", entries.size(), headsetProfile);
        return entries.stream()
                .map(BindingEntry::toPair)
                .toList();
    }

    /**
     * Loads bindings from the JSON file.
     */
    private void loadBindingsFromFile() {
        if (!Files.exists(bindingsFilePath)) {
            LOGGER.info("[ReQuest] No saved bindings file found at {}", bindingsFilePath.toAbsolutePath());
            return;
        }

        try {
            String json = Files.readString(bindingsFilePath);
            Type type = new TypeToken<Map<String, List<BindingEntry>>>(){}.getType();
            savedBindings = GSON.fromJson(json, type);

            if (savedBindings == null) {
                savedBindings = new HashMap<>();
            }

            LOGGER.info("[ReQuest] Loaded saved bindings from {}", bindingsFilePath.toAbsolutePath());

            // Log what was loaded
            for (Map.Entry<String, List<BindingEntry>> entry : savedBindings.entrySet()) {
                LOGGER.info("[ReQuest]   {}: {} bindings", entry.getKey(), entry.getValue().size());
            }

        } catch (IOException e) {
            LOGGER.error("[ReQuest] Failed to load bindings from file", e);
            savedBindings = new HashMap<>();
        }
    }

    /**
     * Checks if saved bindings exist for the given headset profile.
     */
    public boolean hasSavedBindings(String headsetProfile) {
        if (!bindingsLoaded) {
            loadBindingsFromFile();
            bindingsLoaded = true;
        }
        return savedBindings.containsKey(headsetProfile);
    }

    /**
     * Gets all available headset profiles that have saved bindings.
     */
    public Set<String> getAvailableProfiles() {
        if (!bindingsLoaded) {
            loadBindingsFromFile();
            bindingsLoaded = true;
        }
        return new HashSet<>(savedBindings.keySet());
    }

    /**
     * Clears all saved bindings and deletes the file.
     * Useful for debugging or resetting to defaults.
     */
    public void clearSavedBindings() {
        savedBindings.clear();
        try {
            Files.deleteIfExists(bindingsFilePath);
            LOGGER.info("[ReQuest] Cleared all saved bindings");
        } catch (IOException e) {
            LOGGER.error("[ReQuest] Failed to delete bindings file", e);
        }
        bindingsLoaded = false;
    }

    /**
     * Manually saves bindings for a specific headset profile.
     * Useful for updating bindings programmatically.
     */
    public void saveBindingsForProfile(String headsetProfile, Collection<Pair<String, String>> bindings) {
        List<BindingEntry> bindingEntries = bindings.stream()
                .map(BindingEntry::fromPair)
                .toList();

        savedBindings.put(headsetProfile, bindingEntries);
        saveBindingsToFile();
        LOGGER.info("[ReQuest] Manually saved {} bindings for {}", bindings.size(), headsetProfile);
    }
}