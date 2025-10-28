package dev.therealflo.mixin.client;

import dev.therealflo.client.DefaultBindingManager;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.provider.openxr.MCOpenXR;
import org.vivecraft.client_vr.provider.openxr.XRBindings;

import java.util.Collection;
import java.util.HashSet;

/**
 * Mixin for MCOpenXR to intercept the loadDefaultBindings method.
 * On first launch, saves the default bindings to a file.
 * On subsequent launches, loads the saved bindings from the file instead of using hardcoded ones.
 */
@Mixin(value = MCOpenXR.class, remap = false)
public class MCOpenXRMixin {

    /**
     * Redirects the XRBindings.getBinding() call to use our saved bindings if available.
     * This allows us to load custom bindings from file instead of the hardcoded defaults.
     */
    @Redirect(method = "loadDefaultBindings", at = @At(value = "INVOKE",
            target = "Lorg/vivecraft/client_vr/provider/openxr/XRBindings;getBinding(Ljava/lang/String;)Ljava/util/HashSet;"))
    private HashSet<Pair<String, String>> redirectGetBinding(String headset) {
        DefaultBindingManager manager = DefaultBindingManager.getInstance();

        System.out.println("[ReQuest] Processing headset profile: " + headset);
        System.out.println("[ReQuest] Available profiles: " + manager.getAvailableProfiles());
        System.out.println("[ReQuest] Has saved bindings for this profile: " + manager.hasSavedBindings(headset));

        // Try to load saved bindings first
        Collection<Pair<String, String>> savedBindings = manager.loadDefaultBindings(headset);
        if (savedBindings != null) {
            System.out.println("[ReQuest] Using saved bindings for " + headset);
            // Return saved bindings as HashSet
            return new HashSet<>(savedBindings);
        }

        // If no saved bindings, get the original defaults and save them
        System.out.println("[ReQuest] No saved bindings found for " + headset + ", saving defaults");
        HashSet<Pair<String, String>> originalBindings = XRBindings.getBinding(headset);
        manager.saveDefaultBindingsIfNeeded(headset, originalBindings);

        return originalBindings;
    }

    /**
     * Optional: Inject at the start of loadDefaultBindings to log what's happening.
     */
    @Inject(method = "loadDefaultBindings", at = @At("HEAD"))
    private void onLoadDefaultBindingsStart(CallbackInfo ci) {
        DefaultBindingManager manager = DefaultBindingManager.getInstance();

        // Log available profiles for debugging
        if (!manager.getAvailableProfiles().isEmpty()) {
            org.vivecraft.client_vr.settings.VRSettings.LOGGER.info("VivecraftRemapper: Found saved bindings for profiles: {}",
                    manager.getAvailableProfiles());
        } else {
            org.vivecraft.client_vr.settings.VRSettings.LOGGER.info("VivecraftRemapper: No saved bindings found, will save defaults on first use");
        }
    }
}