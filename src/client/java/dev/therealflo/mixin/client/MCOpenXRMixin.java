package dev.therealflo.mixin.client;

import dev.therealflo.client.DefaultBindingManager;
import dev.therealflo.client.RequestModClient;
import dev.therealflo.client.api.MCOpenXRReload;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
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
public abstract class MCOpenXRMixin implements MCOpenXRReload {

    /**
     * Invokes MCOpenXR.loadActionHandles(), which builds actions, suggests bindings, attaches action sets,
     * and sets up controller action spaces based on current XRBindings.
     */
    @Invoker("loadActionHandles")
    protected abstract void vivecraft$invokeLoadActionHandles();

    /**
     * Invokes MCOpenXR.loadDefaultBindings() to reload the bindings from files.
     */
    @Invoker("loadDefaultBindings")
    protected abstract void vivecraft$invokeLoadDefaultBindings();

    @Override
    public void reloadXRBindings() {
        // First, reload the bindings from the config files (this will trigger our redirect)
        this.vivecraft$invokeLoadDefaultBindings();
        
        // Then, rerun Vivecraft's binding setup pipeline on the current OpenXR session
        this.vivecraft$invokeLoadActionHandles();
    }

    /**
     * Redirects the XRBindings.getBinding() call to use our saved bindings if available.
     * This allows us to load custom bindings from file instead of the hardcoded defaults.
     */
    @Redirect(method = "loadDefaultBindings", at = @At(value = "INVOKE",
            target = "Lorg/vivecraft/client_vr/provider/openxr/XRBindings;getBinding(Ljava/lang/String;)Ljava/util/HashSet;"))
    private HashSet<Pair<String, String>> redirectGetBinding(String headset) {
        DefaultBindingManager manager = DefaultBindingManager.getInstance();
        
        // Check if there's an active custom profile set in the config
        String activeProfile = manager.getActiveProfile(headset);
        
        // If active profile is not "default", try to load the custom profile
        if (!"default".equals(activeProfile)) {
            // Build the custom profile path
            String customProfilePath = headset + "/" + activeProfile;
            Collection<Pair<String, String>> customBindings = manager.loadDefaultBindings(customProfilePath);
            
            if (customBindings != null) {
                RequestModClient.LOGGER.info("[ReQuest] Loading custom profile '{}' for {}", activeProfile, headset);
                return new HashSet<>(customBindings);
            } else {
                RequestModClient.LOGGER.warn("[ReQuest] Custom profile '{}' not found for {}, falling back to default",
                    activeProfile, headset);
            }
        }
        
        // Try to load saved default bindings
        Collection<Pair<String, String>> savedBindings = manager.loadDefaultBindings(headset);
        
        if (savedBindings != null) {
            // Return saved default bindings
            return new HashSet<>(savedBindings);
        }
        
        // If no saved bindings exist, get the default ones and save them
        HashSet<Pair<String, String>> defaultBindings = XRBindings.getBinding(headset);
        manager.saveDefaultBindingsIfNeeded(headset, defaultBindings);
        
        return defaultBindings;
    }

    /**
     * Optional: Inject at the start of loadDefaultBindings to log what's happening.
     */
    @Inject(method = "loadDefaultBindings", at = @At("HEAD"))
    private void onLoadDefaultBindingsStart(CallbackInfo ci) {
        DefaultBindingManager.getInstance();
    }
}