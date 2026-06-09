package dev.therealflo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
public final class PresetPackageManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PresetPackageManager() {}

    public static RemotePresetClient.UploadPresetRequest buildUploadRequest(
        String interactionProfile,
        String setId,
        String title,
        String description)
    {
        String normalizedProfile = DefaultBindingManager.getInstance().getUnifiedProfile(interactionProfile);
        String displayName = BindingSetRegistry.getInstance().getSetDisplayName(normalizedProfile, setId);
        String effectiveTitle = title == null || title.isBlank() ? displayName : title.trim();
        String effectiveDescription = description == null ? "" : description.trim();

        Collection<Pair<String, String>> bindings = DefaultBindingManager.getInstance()
            .loadBindingsForSet(normalizedProfile, setId, null);
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalStateException("No bindings found for set " + setId);
        }

        DefaultBindingManager.ProfileBindingsData profileBindings = new DefaultBindingManager.ProfileBindingsData();
        for (Pair<String, String> binding : bindings) {
            profileBindings.bindings.add(DefaultBindingManager.BindingEntry.fromPair(binding));
        }

        String sharedSetId = sanitizeSharedSetId(displayName, setId);
        List<RemotePresetClient.UploadPresetFile> files = new ArrayList<>();
        files.add(new RemotePresetClient.UploadPresetFile(
            normalizedProfile.substring(1) + "/" + sharedSetId + ".json",
            GSON.toJsonTree(profileBindings)
        ));
        files.add(new RemotePresetClient.UploadPresetFile(
            "config/rebound_sets.json",
            buildSetRegistryMetadata(normalizedProfile, sharedSetId, displayName)
        ));

        return new RemotePresetClient.UploadPresetRequest(
            effectiveTitle,
            effectiveDescription,
            net.minecraft.SharedConstants.getGameVersion().getName(),
            "fabric",
            net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(RequestModClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"),
            files
        );
    }

    public static String importPreset(RemotePresetClient.DownloadedPreset presetPackage) {
        BindingFile bindingFile = findBindingFile(presetPackage);
        String interactionProfile = "/" + bindingFile.path.substring(0, bindingFile.path.lastIndexOf('/'));
        String preferredName = presetPackage.preset.title;

        DefaultBindingManager.ProfileBindingsData profileBindings = GSON.fromJson(
            bindingFile.content,
            DefaultBindingManager.ProfileBindingsData.class
        );
        if (profileBindings == null || profileBindings.bindings == null || profileBindings.bindings.isEmpty()) {
            throw new IllegalStateException("Preset does not contain any bindings");
        }

        Collection<Pair<String, String>> bindings = profileBindings.bindings.stream()
            .map(DefaultBindingManager.BindingEntry::toPair)
            .toList();

        String importedSetId = BindingSetRegistry.getInstance().importSet(interactionProfile, preferredName, bindings, true);
        return BindingSetRegistry.getInstance().getSetDisplayName(interactionProfile, importedSetId);
    }

    private static BindingFile findBindingFile(RemotePresetClient.DownloadedPreset presetPackage) {
        for (RemotePresetClient.DownloadedPresetFile file : presetPackage.files) {
            if (file.path.startsWith("interaction_profiles/") && file.path.endsWith(".json")) {
                return new BindingFile(file.path, file.content);
            }
        }
        throw new IllegalStateException("Preset package does not contain an interaction profile binding file");
    }

    private static JsonElement buildSetRegistryMetadata(String profile, String setId, String displayName) {
        JsonObject root = new JsonObject();
        JsonObject profiles = new JsonObject();
        JsonObject profileObject = new JsonObject();
        profileObject.addProperty("active", setId);

        var order = GSON.toJsonTree(List.of(setId));
        profileObject.add("order", order);

        JsonObject sets = new JsonObject();
        JsonObject set = new JsonObject();
        set.addProperty("name", displayName);
        set.addProperty("enabled", true);
        sets.add(setId, set);

        profileObject.add("sets", sets);
        profiles.add(profile, profileObject);
        root.add("profiles", profiles);
        return root;
    }

    private static String sanitizeSharedSetId(String displayName, String fallbackSetId) {
        String base = (displayName == null || displayName.isBlank() ? fallbackSetId : displayName)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        return base.isBlank() ? "shared-set" : base;
    }

    private record BindingFile(String path, JsonElement content) {}
}
