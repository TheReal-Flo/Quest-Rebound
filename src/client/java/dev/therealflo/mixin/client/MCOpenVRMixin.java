package dev.therealflo.mixin.client;

import com.google.common.collect.ImmutableMap;
import dev.therealflo.client.DefaultBindingManager;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.provider.openvr_lwjgl.MCOpenVR;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Mixin for MCOpenVR to intercept the generateActionManifest method.
 * This allows us to save and load default bindings for OpenVR controllers.
 */
@Mixin(value = MCOpenVR.class, remap = false)
public class MCOpenVRMixin {
    private static final String[] CONTROLLER_TYPES = {
            "vive_controller", "oculus_touch", "holographic_controller",
            "knuckles", "vive_cosmos_controller"
    };

    private static final String[] BINDING_FILES = {
            "vive_defaults.json", "oculus_defaults.json", "wmr_defaults.json",
            "knuckles_defaults.json", "cosmos_defaults.json"
    };

    /**
     * Intercepts the default bindings list creation in generateActionManifest.
     * This allows us to save the original bindings on first launch and load custom ones later.
     */
    @ModifyVariable(method = "generateActionManifest", at = @At("STORE"), ordinal = 0, name = "defaults")
    private List<Map<String, Object>> modifyDefaultBindings(List<Map<String, Object>> originalDefaults) {
        DefaultBindingManager manager = DefaultBindingManager.getInstance();

        // Save original bindings if this is the first launch
        for (int i = 0; i < CONTROLLER_TYPES.length && i < BINDING_FILES.length; i++) {
            String controllerType = CONTROLLER_TYPES[i];
            String bindingFile = BINDING_FILES[i];

            // Create a simple binding representation for OpenVR
            Collection<Pair<String, String>> bindings = List.of(
                    Pair.of("controller_type", controllerType),
                    Pair.of("binding_url", bindingFile)
            );

            manager.saveDefaultBindingsIfNeeded(controllerType, bindings);
        }

        // Try to load custom bindings if they exist
        List<Map<String, Object>> customDefaults = new ArrayList<>();
        boolean hasCustomBindings = false;

        for (String controllerType : CONTROLLER_TYPES) {
            Collection<Pair<String, String>> savedBindings = manager.loadDefaultBindings(controllerType);
            if (savedBindings != null) {
                hasCustomBindings = true;

                // Convert saved bindings back to the expected format
                String bindingUrl = null;
                for (Pair<String, String> binding : savedBindings) {
                    if ("binding_url".equals(binding.getLeft())) {
                        bindingUrl = binding.getRight();
                        break;
                    }
                }

                if (bindingUrl != null) {
                    customDefaults.add(ImmutableMap.<String, Object>builder()
                            .put("controller_type", controllerType)
                            .put("binding_url", bindingUrl)
                            .build());
                }
            }
        }

        if (hasCustomBindings) {
            VRSettings.LOGGER.info("[ReQuest] Using custom OpenVR bindings");
            return customDefaults;
        } else {
            VRSettings.LOGGER.info("[ReQuest] Using original OpenVR bindings");
            return originalDefaults;
        }
    }

    /**
     * Log when generateActionManifest starts to track binding management.
     */
    @Inject(method = "generateActionManifest", at = @At("HEAD"))
    private void onGenerateActionManifestStart(CallbackInfo ci) {
        DefaultBindingManager manager = DefaultBindingManager.getInstance();

        if (!manager.getAvailableProfiles().isEmpty()) {
            VRSettings.LOGGER.info("[ReQuest] Found saved OpenVR bindings for profiles: {}",
                    manager.getAvailableProfiles());
        } else {
            VRSettings.LOGGER.info("[ReQuest] No saved OpenVR bindings found, will save defaults");
        }
    }
}