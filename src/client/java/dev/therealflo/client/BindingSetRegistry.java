package dev.therealflo.client;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivecraft.client_vr.provider.openxr.XRBindings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Stores the ordered, enabled, and active binding-set metadata for each interaction profile.
 */
public class BindingSetRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("request");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "rebound_sets.json";
    private static volatile BindingSetRegistry instance;

    private final Path configPath = Paths.get("config").resolve(CONFIG_FILE);
    private final Object lock = new Object();
    private RegistryData cachedData;

    private BindingSetRegistry() {}

    public static BindingSetRegistry getInstance() {
        if (instance == null) {
            synchronized (BindingSetRegistry.class) {
                if (instance == null) {
                    instance = new BindingSetRegistry();
                }
            }
        }
        return instance;
    }

    public record BindingSetEntry(String id, String displayName, boolean enabled, boolean active) {}

    public static class RegistryData {
        public Map<String, ProfileSetData> profiles = new LinkedHashMap<>();
    }

    public static class ProfileSetData {
        public String active = "default";
        public List<String> order = new ArrayList<>(List.of("default"));
        public Map<String, SetData> sets = new LinkedHashMap<>();
    }

    public static class SetData {
        public String name;
        public boolean enabled = true;

        public SetData() {}

        public SetData(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }
    }

    public String getActiveSetId(String interactionProfilePath) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            return profileData.active;
        }
    }

    public String getSetDisplayName(String interactionProfilePath, String setId) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            SetData setData = profileData.sets.get(setId);
            return setData != null ? setData.name : prettifySetName(setId);
        }
    }

    public List<BindingSetEntry> getOrderedSets(String interactionProfilePath) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);

            List<BindingSetEntry> entries = new ArrayList<>();
            for (String setId : profileData.order) {
                SetData setData = profileData.sets.get(setId);
                if (setData == null) {
                    continue;
                }
                entries.add(new BindingSetEntry(
                    setId,
                    setData.name,
                    setData.enabled,
                    setId.equals(profileData.active)
                ));
            }
            return entries;
        }
    }

    public String suggestNewSetName(String interactionProfilePath) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            return nextGeneratedSetName(profileData);
        }
    }

    public String createSetFromActive(String interactionProfilePath) {
        return createSet(interactionProfilePath, null, null);
    }

    public String createSet(String interactionProfilePath, String displayName, String sourceSetId) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);

            String effectiveSource = sourceSetId != null && profileData.sets.containsKey(sourceSetId)
                ? sourceSetId
                : profileData.active;
            String newSetName = normalizeDisplayName(displayName, nextGeneratedSetName(profileData));
            String newSetId = uniqueSetId(profileData, sanitizeSetId(newSetName));

            Collection<Pair<String, String>> sourceBindings = DefaultBindingManager.getInstance()
                .loadBindingsForSet(normalizedProfile, effectiveSource, XRBindings.getBinding(normalizedProfile));
            DefaultBindingManager.getInstance().saveBindingsForSet(normalizedProfile, newSetId, sourceBindings);

            profileData.sets.put(newSetId, new SetData(newSetName, true));
            profileData.order.add(newSetId);
            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
            return newSetId;
        }
    }

    public String duplicateSet(String interactionProfilePath, String sourceSetId) {
        String sourceName = getSetDisplayName(interactionProfilePath, sourceSetId);
        return duplicateSet(interactionProfilePath, sourceSetId, sourceName + " Copy");
    }

    public String duplicateSet(String interactionProfilePath, String sourceSetId, String displayName) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            String effectiveSource = profileData.sets.containsKey(sourceSetId) ? sourceSetId : profileData.active;

            String newSetName = normalizeDisplayName(displayName, nextGeneratedSetName(profileData));
            String newSetId = uniqueSetId(profileData, sanitizeSetId(newSetName));
            Collection<Pair<String, String>> sourceBindings = DefaultBindingManager.getInstance()
                .loadBindingsForSet(normalizedProfile, effectiveSource, XRBindings.getBinding(normalizedProfile));
            DefaultBindingManager.getInstance().saveBindingsForSet(normalizedProfile, newSetId, sourceBindings);

            int sourceIndex = Math.max(0, profileData.order.indexOf(effectiveSource));
            profileData.sets.put(newSetId, new SetData(newSetName, true));
            profileData.order.add(sourceIndex + 1, newSetId);
            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
            return newSetId;
        }
    }

    public void renameSet(String interactionProfilePath, String setId, String displayName) {
        synchronized (this.lock) {
            if ("default".equals(setId)) {
                return;
            }

            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            SetData setData = profileData.sets.get(setId);
            if (setData == null) {
                return;
            }

            setData.name = normalizeDisplayName(displayName, setData.name);
            saveRegistry(data);
        }
    }

    public String importSet(
        String interactionProfilePath,
        String displayName,
        Collection<Pair<String, String>> bindings,
        boolean activate)
    {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);

            String finalName = uniqueDisplayName(profileData, normalizeDisplayName(displayName, nextGeneratedSetName(profileData)));
            String setId = uniqueSetId(profileData, sanitizeSetId(finalName));

            DefaultBindingManager.getInstance().saveBindingsForSet(normalizedProfile, setId, bindings);
            profileData.sets.put(setId, new SetData(finalName, true));
            profileData.order.add(setId);
            if (activate) {
                profileData.active = setId;
            }
            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
            return setId;
        }
    }

    public void setActiveSet(String interactionProfilePath, String setId) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            if (!profileData.sets.containsKey(setId)) {
                return;
            }

            profileData.sets.get(setId).enabled = true;
            profileData.active = setId;
            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
        }
    }

    public void setEnabled(String interactionProfilePath, String setId, boolean enabled) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            SetData setData = profileData.sets.get(setId);
            if (setData == null) {
                return;
            }

            if ("default".equals(setId) && !enabled) {
                setData.enabled = true;
            } else {
                setData.enabled = enabled;
            }

            if (!setData.enabled && setId.equals(profileData.active)) {
                String next = nextEnabledSetId(profileData, setId);
                if (next == null) {
                    profileData.sets.computeIfAbsent("default", ignored -> new SetData("Default", true)).enabled = true;
                    profileData.active = "default";
                } else {
                    profileData.active = next;
                }
            }

            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
        }
    }

    public void moveSet(String interactionProfilePath, String setId, int delta) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            int index = profileData.order.indexOf(setId);
            if (index < 0) {
                return;
            }

            int target = index + delta;
            if (target < 0 || target >= profileData.order.size()) {
                return;
            }

            profileData.order.remove(index);
            profileData.order.add(target, setId);
            saveRegistry(data);
        }
    }

    public void deleteSet(String interactionProfilePath, String setId) {
        synchronized (this.lock) {
            if ("default".equals(setId)) {
                return;
            }

            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            if (!profileData.sets.containsKey(setId)) {
                return;
            }

            profileData.sets.remove(setId);
            profileData.order.remove(setId);
            DefaultBindingManager.getInstance().deleteBindingsForSet(normalizedProfile, setId);

            if (setId.equals(profileData.active)) {
                String next = nextEnabledSetId(profileData, setId);
                profileData.active = next != null ? next : "default";
            }

            if (!profileData.sets.containsKey("default")) {
                profileData.sets.put("default", new SetData("Default", true));
            }

            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
        }
    }

    public String cycleNextEnabledSet(String interactionProfilePath) {
        synchronized (this.lock) {
            String normalizedProfile = normalize(interactionProfilePath);
            RegistryData data = loadRegistry();
            ProfileSetData profileData = ensureProfileInitialized(data, normalizedProfile);
            String next = nextEnabledSetId(profileData, profileData.active);
            if (next == null) {
                return null;
            }

            profileData.active = next;
            saveRegistry(data);
            RuntimeBindingRouter.getInstance().reloadMappings();
            return next;
        }
    }

    private ProfileSetData ensureProfileInitialized(RegistryData data, String normalizedProfile) {
        DefaultBindingManager bindingManager = DefaultBindingManager.getInstance();
        bindingManager.saveDefaultBindingsIfNeeded(normalizedProfile, XRBindings.getBinding(normalizedProfile));

        boolean changed = false;
        ProfileSetData profileData = data.profiles.get(normalizedProfile);
        if (profileData == null) {
            profileData = new ProfileSetData();
            data.profiles.put(normalizedProfile, profileData);
            changed = true;
        }

        if (!profileData.sets.containsKey("default")) {
            profileData.sets.put("default", new SetData("Default", true));
            changed = true;
        }

        Set<String> discoveredSetIds = new LinkedHashSet<>();
        discoveredSetIds.add("default");
        for (String availableProfile : bindingManager.getAvailableProfiles()) {
            if (availableProfile.equals(normalizedProfile)) {
                continue;
            }
            String prefix = normalizedProfile + "/";
            if (availableProfile.startsWith(prefix)) {
                String setId = availableProfile.substring(prefix.length());
                if (!setId.isBlank() && !setId.contains("/")) {
                    discoveredSetIds.add(setId);
                }
            }
        }

        for (String setId : discoveredSetIds) {
            if (!profileData.sets.containsKey(setId)) {
                profileData.sets.put(setId, new SetData(prettifySetName(setId), true));
                changed = true;
            }
        }

        for (String setId : new ArrayList<>(profileData.sets.keySet())) {
            if (!discoveredSetIds.contains(setId) && !"default".equals(setId)) {
                profileData.sets.remove(setId);
                profileData.order.remove(setId);
                changed = true;
            }
        }

        if (!profileData.order.contains("default")) {
            profileData.order.add(0, "default");
            changed = true;
        }

        for (String setId : profileData.sets.keySet()) {
            if (!profileData.order.contains(setId)) {
                profileData.order.add(setId);
                changed = true;
            }
        }

        ProfileSetData currentProfileData = profileData;
        profileData.order.removeIf(setId -> !currentProfileData.sets.containsKey(setId));
        profileData.order = new ArrayList<>(profileData.order.stream().distinct().toList());

        String legacyActive = bindingManager.getActiveProfile(normalizedProfile);
        if (!"default".equals(legacyActive) && profileData.sets.containsKey(legacyActive)) {
            if (!legacyActive.equals(profileData.active)) {
                profileData.active = legacyActive;
                changed = true;
            }
        }

        if (!profileData.sets.containsKey(profileData.active)) {
            profileData.active = "default";
            changed = true;
        }

        if (!profileData.sets.get(profileData.active).enabled) {
            String next = nextEnabledSetId(profileData, profileData.active);
            if (next != null) {
                profileData.active = next;
            } else {
                profileData.sets.get("default").enabled = true;
                profileData.active = "default";
            }
            changed = true;
        }

        if (changed) {
            saveRegistry(data);
            LOGGER.info("Initialized binding sets for {}", normalizedProfile);
        }

        return profileData;
    }

    private String nextEnabledSetId(ProfileSetData profileData, String currentSetId) {
        List<String> enabledIds = profileData.order.stream()
            .filter(setId -> {
                SetData setData = profileData.sets.get(setId);
                return setData != null && setData.enabled;
            })
            .toList();

        if (enabledIds.isEmpty()) {
            return null;
        }

        int currentIndex = enabledIds.indexOf(currentSetId);
        if (currentIndex < 0) {
            return enabledIds.get(0);
        }

        return enabledIds.get((currentIndex + 1) % enabledIds.size());
    }

    private String nextGeneratedSetId(ProfileSetData profileData) {
        int next = 2;
        while (profileData.sets.containsKey("set-" + next)) {
            next++;
        }
        return "set-" + next;
    }

    private String nextGeneratedSetName(ProfileSetData profileData) {
        int next = 2;
        Set<String> usedNames = profileData.sets.values().stream()
            .map(set -> set.name)
            .collect(java.util.stream.Collectors.toSet());
        while (usedNames.contains("Set " + next)) {
            next++;
        }
        return "Set " + next;
    }

    private String normalizeDisplayName(String displayName, String fallback) {
        if (displayName == null || displayName.isBlank()) {
            return fallback;
        }
        return displayName.trim();
    }

    private String sanitizeSetId(String displayName) {
        String sanitized = displayName.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (sanitized.isBlank() || "default".equals(sanitized)) {
            return "set";
        }
        return sanitized;
    }

    private String uniqueSetId(ProfileSetData profileData, String baseId) {
        String candidate = baseId;
        int suffix = 2;
        while (profileData.sets.containsKey(candidate)) {
            candidate = baseId + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String uniqueDisplayName(ProfileSetData profileData, String baseName) {
        String candidate = baseName;
        int suffix = 2;

        while (hasDisplayName(profileData, candidate)) {
            candidate = baseName + " " + suffix;
            suffix++;
        }

        return candidate;
    }

    private boolean hasDisplayName(ProfileSetData profileData, String candidate) {
        for (SetData set : profileData.sets.values()) {
            if (candidate.equalsIgnoreCase(set.name)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String interactionProfilePath) {
        return DefaultBindingManager.getInstance().getUnifiedProfile(interactionProfilePath);
    }

    private String prettifySetName(String setId) {
        if ("default".equals(setId)) {
            return "Default";
        }

        String[] parts = setId.replace('_', ' ').replace('-', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? setId : builder.toString();
    }

    private RegistryData loadRegistry() {
        if (this.cachedData != null) {
            return this.cachedData;
        }

        if (!Files.exists(this.configPath)) {
            this.cachedData = new RegistryData();
            return this.cachedData;
        }

        try {
            String json = Files.readString(this.configPath);
            RegistryData data = GSON.fromJson(json, RegistryData.class);
            this.cachedData = data != null ? data : new RegistryData();
        } catch (IOException e) {
            LOGGER.error("Failed to load binding-set registry", e);
            this.cachedData = new RegistryData();
        }
        return this.cachedData;
    }

    private void saveRegistry(RegistryData data) {
        this.cachedData = data;
        try {
            Files.createDirectories(this.configPath.getParent());
            Files.writeString(this.configPath, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("Failed to save binding-set registry", e);
        }
    }
}
