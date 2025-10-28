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
 * Manages saving and loading of default VR controller bindings and keybind bindings.
 * On first launch, saves the default bindings from Vivecraft to a file.
 * On subsequent launches, loads the saved bindings from the file.
 */
public class DefaultBindingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VivecraftRemapper");
    private static final String BINDINGS_FILE = "vivecraft_default_bindings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile DefaultBindingManager instance;
    private final Path bindingsFilePath;
    private BindingsData savedData = new BindingsData();
    private volatile boolean bindingsLoaded = false;
    private final Object lock = new Object();

    private DefaultBindingManager() {
        this.bindingsFilePath = Paths.get(BINDINGS_FILE);
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
     * Container class for all binding data
     */
    public static class BindingsData {
        public Map<String, List<BindingEntry>> vrControllerBindings = new HashMap<>();
        public Map<String, String> keybindBindings = new HashMap<>();

        public BindingsData() {}
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
     * Saves default VR controller bindings to file if they don't exist yet.
     * Called from OpenXR mixin during loadDefaultBindings().
     */
    public void saveDefaultBindingsIfNeeded(String headsetProfile, Collection<Pair<String, String>> bindings) {
        synchronized (lock) {
            // Load existing bindings first if file exists
            if (!bindingsLoaded) {
                loadBindingsFromFile();
                bindingsLoaded = true;
            }

            // Check if we already have bindings for this specific headset profile
            if (savedData.vrControllerBindings.containsKey(headsetProfile)) {
                LOGGER.info("Default VR controller bindings for {} already exist, skipping save", headsetProfile);
                return;
            }

            LOGGER.info("First launch detected for {}, saving default VR controller bindings", headsetProfile);

            // Convert pairs to binding entries
            List<BindingEntry> bindingEntries = bindings.stream()
                    .map(BindingEntry::fromPair)
                    .toList();

            savedData.vrControllerBindings.put(headsetProfile, bindingEntries);
            saveBindingsToFile();
        }
    }

    /**
     * Saves all current bindings to the JSON file.
     */
    private void saveBindingsToFile() {
        try {
            String json = GSON.toJson(savedData);
            Files.writeString(bindingsFilePath, json);
            LOGGER.info("Saved bindings to {}", bindingsFilePath.toAbsolutePath());
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
            if (!bindingsLoaded) {
                loadBindingsFromFile();
                bindingsLoaded = true;
            }

            List<BindingEntry> entries = savedData.vrControllerBindings.get(headsetProfile);
            if (entries != null) {
                LOGGER.info("Loading {} saved VR controller bindings for {}", entries.size(), headsetProfile);
                return entries.stream().map(BindingEntry::toPair).toList();
            } else {
                LOGGER.info("No saved VR controller bindings found for {}", headsetProfile);
                return null;
            }
        }
    }

    /**
     * Loads bindings from the JSON file.
     */
    private void loadBindingsFromFile() {
        if (!Files.exists(bindingsFilePath)) {
            LOGGER.info("No saved bindings file found at {}", bindingsFilePath.toAbsolutePath());
            return;
        }

        try {
            String json = Files.readString(bindingsFilePath);
            Type type = new TypeToken<BindingsData>(){}.getType();
            BindingsData loadedData = GSON.fromJson(json, type);

            if (loadedData != null) {
                savedData = loadedData;
                // Ensure maps are initialized
                if (savedData.vrControllerBindings == null) {
                    savedData.vrControllerBindings = new HashMap<>();
                }
                if (savedData.keybindBindings == null) {
                    savedData.keybindBindings = new HashMap<>();
                }
            }

            LOGGER.info("Loaded bindings from {}: {} VR profiles, {} keybind bindings",
                    bindingsFilePath.toAbsolutePath(),
                    savedData.vrControllerBindings.size(),
                    savedData.keybindBindings.size());

        } catch (IOException e) {
            LOGGER.error("Failed to load bindings from file", e);
            savedData = new BindingsData();
        }
    }

    /**
     * Checks if we have saved VR controller bindings for the given headset profile.
     */
    public boolean hasSavedBindings(String headsetProfile) {
        synchronized (lock) {
            if (!bindingsLoaded) {
                loadBindingsFromFile();
                bindingsLoaded = true;
            }
            return savedData.vrControllerBindings.containsKey(headsetProfile);
        }
    }

    /**
     * Gets all available VR controller binding profiles.
     */
    public Set<String> getAvailableProfiles() {
        synchronized (lock) {
            if (!bindingsLoaded) {
                loadBindingsFromFile();
                bindingsLoaded = true;
            }
            return new HashSet<>(savedData.vrControllerBindings.keySet());
        }
    }

    /**
     * Clears all saved bindings.
     */
    public void clearSavedBindings() {
        synchronized (lock) {
            savedData = new BindingsData();
            try {
                Files.deleteIfExists(bindingsFilePath);
                LOGGER.info("Cleared all saved bindings");
            } catch (IOException e) {
                LOGGER.error("Failed to delete bindings file", e);
            }
            bindingsLoaded = false;
        }
    }

    /**
     * Saves VR controller bindings for a specific profile.
     */
    public void saveBindingsForProfile(String headsetProfile, Collection<Pair<String, String>> bindings) {
        synchronized (lock) {
            if (!bindingsLoaded) {
                loadBindingsFromFile();
                bindingsLoaded = true;
            }

            List<BindingEntry> bindingEntries = bindings.stream()
                    .map(BindingEntry::fromPair)
                    .toList();

            savedData.vrControllerBindings.put(headsetProfile, bindingEntries);
            saveBindingsToFile();
            LOGGER.info("Saved {} VR controller bindings for {}", bindingEntries.size(), headsetProfile);
        }
    }


}